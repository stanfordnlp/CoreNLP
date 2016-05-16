package edu.stanford.nlp.pipeline;
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.io.FileSequentialCollection;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.StanfordRedwoodConfiguration;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static edu.stanford.nlp.util.logging.Redwood.Util.*;

/**
 * An annotation pipeline in spirit identical to {@link StanfordCoreNLP}, but
 * with the backend supported by a web server.
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("FieldCanBeLocal")
public class StanfordCoreNLPClient extends AnnotationPipeline  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(StanfordCoreNLPClient.class);

  /**
   * Information on how to connect to a backend.
   * The semantics of one of these objects is as follows:
   * <ul>
   *   <li>It should define a hostname and port to connect to.</li>
   *   <li>This represents ONE thread on the remote server. The client should
   *       treat it as such.</li>
   *   <li>Two backends that are .equals() point to the same endpoint, but there can be
   *       multiple of them if we want to run multiple threads on that endpoint.</li>
   * </ul>
   */
  private static class Backend {
    /** The protocol to connect to the server with. */
    public final String protocol;
    /** The hostname of the server running the CoreNLP annotators */
    public final String host;
    /** The port of the server running the CoreNLP annotators */
    public final int port;
    public Backend(String protocol, String host, int port) {
      this.protocol = protocol;
      this.host = host;
      this.port = port;
    }
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Backend)) return false;
      Backend backend = (Backend) o;
      return port == backend.port && protocol.equals(backend.protocol) && host.equals(backend.host);
    }
    @Override
    public int hashCode() {
      throw new IllegalStateException("Hashing backends is dangerous!");
    }
  }

  /**
   * A special type of {@link Thread}, which is responsible for scheduling jobs
   * on the backend.
   */
  private static class BackendScheduler extends Thread {
    /**
     * The list of backends that we can schedule on.
     * This should not generally be called directly from anywhere
     */
    public final List<Backend> backends;

    /**
     * The queue of annotators (backends) that are free to be run on.
     * Remember to lock access to this object with {@link BackendScheduler#freeAnnotatorsLock}.
     */
    private final Queue<Backend> freeAnnotators;
    /**
     * The lock on access to {@link BackendScheduler#freeAnnotators}.
     */
    private final Lock freeAnnotatorsLock = new ReentrantLock();
    /**
     * Represents the event that an annotator has freed up and is available for
     * work on the {@link BackendScheduler#freeAnnotators} queue.
     * Linked to {@link BackendScheduler#freeAnnotatorsLock}.
     */
    private final Condition newlyFree = freeAnnotatorsLock.newCondition();

    /**
     * The queue on requests for the scheduler to handle.
     * Each element of this queue is a function: calling the function signals
     * that this backend is available to perform a task on the passed backend.
     * It is then obligated to call the passed Consumer to signal that it has
     * released control of the backend, and it can be used for other things.
     * Remember to lock access to this object with {@link BackendScheduler#queueLock}.
     */
    private final Queue<BiConsumer<Backend, Consumer<Backend>>> queue;
    /**
     * The lock on access to {@link BackendScheduler#queue}.
     */
    private final Lock queueLock = new ReentrantLock();
    /**
     * Represents the event that an item has been added to the work queue.
     * Linked to {@link BackendScheduler#queueLock}.
     */
    private final Condition enqueued = queueLock.newCondition();
    /**
     * Represents the event that the queue has become empty, and this schedule is no
     * longer needed.
     */
    public final Condition queueEmpty = queueLock.newCondition();

    /**
     * While this is true, continue running the scheduler.
     */
    private boolean doRun = true;

    /**
     * Create a new scheduler from a list of backends.
     * These can contain duplicates -- in that case, that many concurrent
     * calls can be made to that backend.
     */
    public BackendScheduler(List<Backend> backends) {
      super();
      setDaemon(true);
      this.backends = backends;
      this.freeAnnotators = new LinkedList<>(backends);
      this.queue = new LinkedList<>();
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
      try {
        while (doRun) {
          // Wait for a request
          queueLock.lock();
          while (queue.isEmpty()) {
            enqueued.await();
            if (!doRun) { return; }
          }
          // Signal if the queue is empty
          if (queue.isEmpty()) {
            queueEmpty.signalAll();
          }
          // Get the actual request
          BiConsumer<Backend, Consumer<Backend>> request = queue.poll();
          // We have a request
          queueLock.unlock();

          // Find a free annotator
          freeAnnotatorsLock.lock();
          while (freeAnnotators.isEmpty()) {
            newlyFree.await();
          }
          Backend annotator = freeAnnotators.poll();
          freeAnnotatorsLock.unlock();
          // We have an annotator

          // Run the annotation
          request.accept(annotator, freedAnnotator -> {
            // ASYNC: we've freed this annotator
            // add it back to the queue and register it as available
            freeAnnotatorsLock.lock();
            try {
              freeAnnotators.add(freedAnnotator);
              newlyFree.signal();
            } finally {
              freeAnnotatorsLock.unlock();
            }
          });
          // Annotator is running (in parallel, most likely)
        }
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    /**
     * Schedule a new job on the backend
     * @param annotate A callback, which will be called when a backend is free
     *                 to do some processing. The implementation of this callback
     *                 MUST CALL the second argument when it is done processing,
     *                 to register the backend as free for further work.
     */
    public void schedule(BiConsumer<Backend, Consumer<Backend>> annotate) {
      queueLock.lock();
      try {
        queue.add(annotate);
        enqueued.signal();
      } finally {
        queueLock.unlock();
      }
    }
  }

  /** The path on the server to connect to. */
  private final String path = "";
  /** The Properties file to annotate with. */
  private final Properties properties;

  /** The Properties file to send to the server, serialized as JSON. */
  private final String propsAsJSON;

  /** The scheduler to use when running on multiple backends at a time */
  private final BackendScheduler scheduler;

  /**
   * The annotation serializer responsible for translating between the wire format
   * (protocol buffers) and the {@link Annotation} classes.
   */
  private final ProtobufAnnotationSerializer serializer = new ProtobufAnnotationSerializer(true);

  /**
   * The main constructor. Create a client from a properties file and a list of backends.
   * Note that this creates at least one Daemon thread.
   *
   * @param properties The properties file, as would be passed to {@link StanfordCoreNLP}.
   * @param backends The backends to run on.
   */
  private StanfordCoreNLPClient(Properties properties, List<Backend> backends) {
    // Save the constructor variables
    this.properties = properties;
    Properties serverProperties = new Properties();
    Enumeration<?> keys = properties.propertyNames();
    while (keys.hasMoreElements()) {
      String key = keys.nextElement().toString();
      serverProperties.setProperty(key, properties.getProperty(key));
    }
    Collections.shuffle(backends, new Random(System.currentTimeMillis()));
    this.scheduler = new BackendScheduler(backends);

    // Set required serverProperties
    serverProperties.setProperty("inputFormat", "serialized");
    serverProperties.setProperty("outputFormat", "serialized");
    serverProperties.setProperty("inputSerializer", ProtobufAnnotationSerializer.class.getName());
    serverProperties.setProperty("outputSerializer", ProtobufAnnotationSerializer.class.getName());

    // Create a list of all the properties, as JSON map elements
    List<String> jsonProperties = new ArrayList<>();
    keys = serverProperties.propertyNames();
    while (keys.hasMoreElements()) {
      Object key = keys.nextElement();
      jsonProperties.add(
          "\"" + JSONOutputter.cleanJSON(key.toString()) + "\": \"" +
              JSONOutputter.cleanJSON(serverProperties.getProperty(key.toString())) + "\"");
    }
    // Create the JSON object
    this.propsAsJSON = "{ " + StringUtils.join(jsonProperties, ", ") + " }";

    // Start 'er up
    this.scheduler.start();
  }

  /**
   * Run on a single backend.
   *
   * @see StanfordCoreNLPClient (Properties, List)
   */
  @SuppressWarnings("unused")
  public StanfordCoreNLPClient(Properties properties, String host, int port) {
    this(properties, Collections.singletonList(
        new Backend(host.startsWith("https://") ? "https" : "http",
            host.startsWith("http://") ? host.substring("http://".length()) : (host.startsWith("https://") ? host.substring("https://".length()) : host),
            port)));
  }

  /**
   * Run on a single backend, but with k threads on each backend.
   *
   * @see StanfordCoreNLPClient (Properties, List)
   */
  @SuppressWarnings("unused")
  public StanfordCoreNLPClient(Properties properties, String host, int port, int threads) {
    this(properties, new ArrayList<Backend>() {{
      for (int i = 0; i < threads; ++i) {
        add(new Backend(host.startsWith("https://") ? "https" : "http",
            host.startsWith("http://") ? host.substring("http://".length()) : (host.startsWith("https://") ? host.substring("https://".length()) : host),
            port));
      }
    }});
  }

  /**
   * {@inheritDoc}
   *
   * This method creates an async call to the server, and blocks until the server
   * has finished annotating the object.
   */
  @Override
  public void annotate(Annotation annotation) {
    final Lock lock = new ReentrantLock();
    final Condition annotationDone = lock.newCondition();
    annotate(Collections.singleton(annotation), 1, (Annotation annInput) -> {
      try {
        lock.lock();
        annotationDone.signal();
      } finally {
        lock.unlock();
      }
    });
    try {
      lock.lock();
      annotationDone.await();  // Only wait for one callback to complete; only annotating one document
    } catch (InterruptedException e) {
      log.info("Interrupt while waiting for annotation to return");
    } finally {
      lock.unlock();
    }
  }

  /**
   * This method fires off a request to the server. Upon returning, it calls the provided
   * callback method.
   *
   * @param annotations The input annotations to process
   * @param numThreads The number of threads to run on. IGNORED in this class.
   * @param callback A function to be called when an annotation finishes.
   */
  @Override
  public void annotate(final Iterable<Annotation> annotations, int numThreads, final Consumer<Annotation> callback){
    for (Annotation annotation : annotations) {
      annotate(annotation, callback);
    }
  }

  /**
   * The canonical entry point of the client annotator.
   * Create an HTTP request, send this annotation to the server, and await a response.
   *
   * @param annotation The annotation to annotate.
   * @param callback Called when the server has returned an annotated document.
   *                 The input to this callback is the same as the passed Annotation object.
   */
  @SuppressWarnings("unchecked")
  public void annotate(final Annotation annotation, final Consumer<Annotation> callback){
    scheduler.schedule((Backend backend, Consumer<Backend> isFinishedCallback) -> new Thread() {
      @Override
      public void run() {
        try {
          // 1. Create the input
          // 1.1 Create a protocol buffer
          ByteArrayOutputStream os = new ByteArrayOutputStream();
          serializer.write(annotation, os);
          os.close();
          byte[] message = os.toByteArray();
          // 1.2 Create the query params

          String queryParams = String.format(
              "properties=%s",
              URLEncoder.encode(StanfordCoreNLPClient.this.propsAsJSON, "utf-8"));

          // 2. Create a connection
          // 2.1 Open a connection
          URL serverURL = new URL(backend.protocol, backend.host,
              backend.port,
              StanfordCoreNLPClient.this.path + "?" + queryParams);
          URLConnection connection = serverURL.openConnection();
          // 2.2 Set some protocol-independent properties
          connection.setDoOutput(true);
          connection.setRequestProperty("Content-Type", "application/x-protobuf");
          connection.setRequestProperty("Content-Length", Integer.toString(message.length));
          connection.setRequestProperty("Accept-Charset", "utf-8");
          connection.setRequestProperty("User-Agent", StanfordCoreNLPClient.class.getName());
          // 2.3 Set some protocol-dependent properties
          switch (backend.protocol) {
            case "http":
            case "https":
              ((HttpURLConnection) connection).setRequestMethod("POST");
              break;
            default:
              throw new IllegalStateException("Haven't implemented protocol: " + backend.protocol);
          }
          // 3. Fire off the request
          connection.getOutputStream().write(message);
//          log.info("Wrote " + message.length + " bytes to " + backend.host + ":" + backend.port);
          os.close();

          // 4. Await a response
          // 4.1 Read the response
          // -- It might be possible to send more than one message, but we are not going to do that.
          Annotation response = serializer.read(connection.getInputStream()).first;
          // 4.2 Release the backend
          isFinishedCallback.accept(backend);

          // 5. Copy response over to original annotation
          for (Class key : response.keySet()) {
            annotation.set(key, response.get(key));
          }

          // 6. Call the callback
          callback.accept(annotation);
        } catch (IOException | ClassNotFoundException e) {
          e.printStackTrace();
          callback.accept(null);
        }
      }
    }.start());
  }

  /**
   * Runs the entire pipeline on the content of the given text passed in.
   * @param text The text to process
   * @return An Annotation object containing the output of all annotators
   */
  public Annotation process(String text) {
    Annotation annotation = new Annotation(text);
    annotate(annotation);
    return annotation;
  }


  /**
   * Runs an interactive shell where input text is processed with the given pipeline.
   *
   * @param pipeline The pipeline to be used
   * @throws IOException If IO problem with stdin
   */
  private static void shell(StanfordCoreNLPClient pipeline) throws IOException {
    log.info("Entering interactive shell. Type q RETURN or EOF to quit.");
    final StanfordCoreNLP.OutputFormat outputFormat = StanfordCoreNLP.OutputFormat.valueOf(pipeline.properties.getProperty("outputFormat", "text").toUpperCase());
    IOUtils.console("NLP> ", line -> {
      if (line.length() > 0) {
        Annotation anno = pipeline.process(line);
        try {
          switch (outputFormat) {
            case XML:
              new XMLOutputter().print(anno, System.out);
              break;
            case JSON:
              new JSONOutputter().print(anno, System.out);
              System.out.println();
              break;
            case CONLL:
              new CoNLLOutputter().print(anno, System.out);
              System.out.println();
              break;
            case TEXT:
              new TextOutputter().print(anno, System.out);
              break;
            case SERIALIZED:
              warn("You probably cannot read the serialized output, so printing in text instead");
              new TextOutputter().print(anno, System.out);
              break;
            default:
              throw new IllegalArgumentException("Cannot output in format " + outputFormat + " from the interactive shell");
          }
        } catch (IOException e) {
          throw new RuntimeIOException(e);
        }
      }
    });
  }

  /**
   * The implementation of what to run on a command-line call of CoreNLPWebClient
   *
   * @throws IOException
   */
  public void run() throws IOException {
    StanfordRedwoodConfiguration.minimalSetup();
    StanfordCoreNLP.OutputFormat outputFormat = StanfordCoreNLP.OutputFormat.valueOf(properties.getProperty("outputFormat", "text").toUpperCase());

    //
    // Process one file or a directory of files
    //
    if (properties.containsKey("file") || properties.containsKey("textFile")) {
      String fileName = properties.getProperty("file");
      if (fileName == null) {
        fileName = properties.getProperty("textFile");
      }
      Collection<File> files = new FileSequentialCollection(new File(fileName), properties.getProperty("extension"), true);
      StanfordCoreNLP.processFiles(null, files, 1, properties, this::annotate,
          StanfordCoreNLP.createOutputter(properties, new AnnotationOutputter.Options()), outputFormat);
    }

    //
    // Process a list of files
    //
    else if (properties.containsKey("filelist")){
      String fileName = properties.getProperty("filelist");
      Collection<File> inputfiles = StanfordCoreNLP.readFileList(fileName);
      Collection<File> files = new ArrayList<>(inputfiles.size());
      for (File file:inputfiles) {
        if (file.isDirectory()) {
          files.addAll(new FileSequentialCollection(new File(fileName), properties.getProperty("extension"), true));
        } else {
          files.add(file);
        }
      }
      StanfordCoreNLP.processFiles(null, files, 1, properties, this::annotate,
          StanfordCoreNLP.createOutputter(properties, new AnnotationOutputter.Options()), outputFormat);
    }

    //
    // Run the interactive shell
    //
    else {
      shell(this);
    }
  }

  /**
   * <p>
   *   Good practice to call after you are done with this object.
   *   Shuts down the queue of annotations to run and the associated threads.
   * </p>
   *
   * <p>
   *   If this is not called, any job which has been scheduled but not run will be
   *   cancelled.
   * </p>
   */
  public void shutdown() throws InterruptedException {
    scheduler.queueLock.lock();
    try {
      while (!scheduler.queue.isEmpty()) {
        scheduler.queueEmpty.await();
      }
      scheduler.doRun = false;
      scheduler.enqueued.signalAll();  // In case the thread's waiting on this condition
    } finally {
      scheduler.queueLock.unlock();
    }
  }


  /**
   * This can be used just for testing or for command-line text processing.
   * This runs the pipeline you specify on the
   * text in the file that you specify and sends some results to stdout.
   * The current code in this main method assumes that each line of the file
   * is to be processed separately as a single sentence.
   * <p>
   * Example usage:<br>
   * java -mx6g edu.stanford.nlp.pipeline.StanfordCoreNLP -props properties
   *
   * @param args List of required properties
   * @throws java.io.IOException If IO problem
   * @throws ClassNotFoundException If class loading problem
   */
  public static void main(String[] args) throws IOException, ClassNotFoundException {
    //
    // process the arguments
    //
    // extract all the properties from the command line
    // if cmd line is empty, set the properties to null. The processor will search for the properties file in the classpath
//    if (args.length < 2) {
//      log.info("Usage: " + StanfordCoreNLPClient.class.getSimpleName() + " -host <hostname> -port <port> ...");
//      System.exit(1);
//    }
    Properties props = StringUtils.argsToProperties(args);
    boolean hasH = props.containsKey("h");
    boolean hasHelp = props.containsKey("help");
    if (hasH || hasHelp) {
      String helpValue = hasH ? props.getProperty("h") : props.getProperty("help");
      StanfordCoreNLP.printHelp(System.err, helpValue);
      return;
    }

    // Create the backends
    List<Backend> backends = new ArrayList<>();
    for (String spec : props.getProperty("backends", "corenlp.run").split(",")) {
      if (spec.contains(":")) {
        String host = spec.substring(0, spec.indexOf(":"));
        int port = Integer.parseInt(spec.substring(spec.indexOf(":") + 1));
        backends.add(new Backend(host.startsWith("https://") ? "https" : "http",
            host.startsWith("http://") ? host.substring("http://".length()) : (host.startsWith("https://") ? host.substring("https://".length()) : host),
            port));
      } else {
        backends.add(new Backend("http", spec, 80));
      }
    }

    // Run the pipeline
    StanfordCoreNLPClient client = new StanfordCoreNLPClient(props, backends);
    client.run();
    try {
      client.shutdown();  // In case anything is pending on the server
    } catch (InterruptedException ignored) { }
  }
}
