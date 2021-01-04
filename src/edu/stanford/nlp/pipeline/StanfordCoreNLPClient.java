package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.FileSequentialCollection;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;
import edu.stanford.nlp.util.logging.StanfordRedwoodConfiguration;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

  /** A simple URL spec, for parsing backend URLs */
  private static final Pattern URL_PATTERN = Pattern.compile("(?:(https?)://)?([^:]+)(?::([0-9]+))?");

  /**
   * Information on how to connect to a backend.
   * The semantics of one of these objects is as follows:
   *
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

    @Override
    public String toString() {
      return protocol + "://" + host + ':' + port;
    }
  } // end static class Backend

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
     * The queue on requests for the scheduler to handle.
     * Each element of this queue is a function: calling the function signals
     * that this backend is available to perform a task on the passed backend.
     * It is then obligated to call the passed Consumer to signal that it has
     * released control of the backend, and it can be used for other things.
     * Remember to lock access to this object with {@link BackendScheduler#stateLock}.
     */
    private final Queue<BiConsumer<Backend, Consumer<Backend>>> queue;
    /**
     * The lock on access to {@link BackendScheduler#queue}.
     */
    private final Lock stateLock = new ReentrantLock();
    /**
     * Represents the event that an item has been added to the work queue.
     * Linked to {@link BackendScheduler#stateLock}.
     */
    private final Condition enqueued = stateLock.newCondition();
    /**
     * Represents the event that the queue has become empty, and this schedule is no
     * longer needed.
     */
    public final Condition shouldShutdown = stateLock.newCondition();

    /**
     * The queue of annotators (backends) that are free to be run on.
     * Remember to lock access to this object with {@link BackendScheduler#stateLock}.
     */
    private final Queue<Backend> freeAnnotators;
    /**
     * Represents the event that an annotator has freed up and is available for
     * work on the {@link BackendScheduler#freeAnnotators} queue.
     * Linked to {@link BackendScheduler#stateLock}.
     */
    private final Condition newlyFree = stateLock.newCondition();

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
          BiConsumer<Backend, Consumer<Backend>> request;
          Backend annotator;
          stateLock.lock();
          try {
            while (queue.isEmpty()) {
              enqueued.await();
              if (!doRun) {
                return;
              }
            }
            // Get the actual request
            request = queue.poll();
            // We have a request

            // Find a free annotator
            while (freeAnnotators.isEmpty()) {
              newlyFree.await();
            }
            annotator = freeAnnotators.poll();
          } finally {
            stateLock.unlock();
          }
          // We have an annotator

          // Run the annotation
          request.accept(annotator, freedAnnotator -> {
            // ASYNC: we've freed this annotator
            // add it back to the queue and register it as available
            stateLock.lock();
            try {
              freeAnnotators.add(freedAnnotator);

              // If the queue is empty, and all the annotators have returned, we're done
              if (queue.isEmpty() && freeAnnotators.size() == backends.size()) {
                log.debug("All annotations completed. Signaling for shutdown");
                shouldShutdown.signalAll();
              }

              newlyFree.signal();
            } finally {
              stateLock.unlock();
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
      stateLock.lock();
      try {
        queue.add(annotate);
        enqueued.signal();
      } finally {
        stateLock.unlock();
      }
    }
  } // end static class BackEndScheduler

  /** The path on the server to connect to. */
  private final String path = "";
  /** The Properties file to annotate with. */
  private final Properties properties;

  /** The Properties file to send to the server, serialized as JSON. */
  private final String propsAsJSON;

  /** The API key to authenticate with, or null */
  private final String apiKey;
  /** The API secret to authenticate with, or null */
  private final String apiSecret;

  /** The scheduler to use when running on multiple backends at a time */
  private final BackendScheduler scheduler;

  /**
   * The annotation serializer responsible for translating between the wire format
   * (protocol buffers) and the {@link Annotation} classes.
   */
  private final ProtobufAnnotationSerializer serializer = new ProtobufAnnotationSerializer(true);

  private boolean fallbackToLocalPipeline;

  private int timeoutMilliseconds=0;

  /**
   * The main constructor. Create a client from a properties file and a list of backends.
   * Note that this creates at least one Daemon thread.
   *
   * @param properties The properties file, as would be passed to {@link StanfordCoreNLP}.
   * @param backends The backends to run on.
   * @param apiKey The key to authenticate with as a username
   * @param apiSecret The key to authenticate with as a password
   */
  private StanfordCoreNLPClient(Properties properties, List<Backend> backends,
                                String apiKey, String apiSecret) {
    // Save the constructor variables
    this.properties = properties;
    Properties serverProperties = new Properties();
    for (String key : properties.stringPropertyNames()) {
      serverProperties.setProperty(key, properties.getProperty(key));
    }
    Collections.shuffle(backends, new Random(System.currentTimeMillis()));
    this.scheduler = new BackendScheduler(backends);
    this.apiKey = apiKey;
    this.apiSecret = apiSecret;

    // Set required serverProperties
    serverProperties.setProperty("inputFormat", "serialized");
    serverProperties.setProperty("outputFormat", "serialized");
    serverProperties.setProperty("inputSerializer", ProtobufAnnotationSerializer.class.getName());
    serverProperties.setProperty("outputSerializer", ProtobufAnnotationSerializer.class.getName());

    this.propsAsJSON = PropertiesUtils.propsAsJsonString(serverProperties);

    // Start 'er up
    this.scheduler.start();
  }


  /**
   * The main constructor without credentials.
   *
   * @see StanfordCoreNLPClient#StanfordCoreNLPClient(Properties, List, String, String)
   */
  private StanfordCoreNLPClient(Properties properties, List<Backend> backends) {
    this(properties, backends, null, null);
  }


  /**
   * Run the client, pulling credentials from the environment.
   * Throws an IllegalStateException if the required environment variables aren't set.
   * These are:
   *
   * <ul>
   *   <li>CORENLP_HOST</li>
   *   <li>CORENLP_KEY</li>
   *   <li>CORENLP_SECRET</li>
   * </ul>
   *
   * @throws IllegalStateException Thrown if we could not read the required environment variables.
   */
  @SuppressWarnings("unused")
  public StanfordCoreNLPClient(Properties properties) throws IllegalStateException {
    this(properties,
        Optional.ofNullable(System.getenv("CORENLP_HOST")).orElseThrow(() -> new IllegalStateException("Environment variable CORENLP_HOST not specified")),
        Optional.ofNullable(System.getenv("CORENLP_HOST")).map(x -> x.startsWith("http://") ? 80 : 443).orElse(443),
        1,
        Optional.ofNullable(System.getenv("CORENLP_KEY")).orElse(null),
        Optional.ofNullable(System.getenv("CORENLP_SECRET")).orElse(null)
      );
  }


  /**
   * Run on a single backend.
   *
   * @see StanfordCoreNLPClient (Properties, List)
   */
  @SuppressWarnings("unused")
  public StanfordCoreNLPClient(Properties properties, String host, int port) {
    this(properties, host, port, 1);
  }

  /**
   * Run on a single backend, with authentication
   *
   * @see StanfordCoreNLPClient (Properties, List)
   */
  @SuppressWarnings("unused")
  public StanfordCoreNLPClient(Properties properties, String host, int port,
                               String apiKey, String apiSecret) {
    this(properties, host, port, 1, apiKey, apiSecret);
  }


  /**
   * Run on a single backend, with authentication
   *
   * @see StanfordCoreNLPClient (Properties, List)
   */
  @SuppressWarnings("unused")
  public StanfordCoreNLPClient(Properties properties, String host,
                               String apiKey, String apiSecret) {
    this(properties, host, host.startsWith("http://") ? 80 : 443, 1, apiKey, apiSecret);
  }

  /**
   * Run on a single backend, but with k threads on each backend.
   *
   * @see StanfordCoreNLPClient (Properties, List)
   */
  @SuppressWarnings("unused")
  public StanfordCoreNLPClient(Properties properties, String host, int port, int threads) {
    this(properties, host, port, threads, null, null);
  }


  /**
   * Run on a single backend, but with k threads on each backend, and with authentication
   *
   * @see StanfordCoreNLPClient (Properties, List)
   */
  public StanfordCoreNLPClient(Properties properties, String host, int port, int threads,
                               String apiKey, String apiSecret) {
    this(properties, getBackends(host, port, threads), apiKey, apiSecret);
  }

  private static List<Backend> getBackends(String host, int port, int threads) {
    List<Backend> backends = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      backends.add(new Backend(host.startsWith("http://") ? "http" : "https",
              host.startsWith("http://") ? host.substring("http://".length()) : (host.startsWith("https://") ? host.substring("https://".length()) : host),
              port));
    }
    return backends;
  }

  public void setTimeoutMilliseconds(int timeout) {
    this.timeoutMilliseconds = timeout;
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
  public void annotate(final Annotation annotation, final Consumer<Annotation> callback) {
    scheduler.schedule((Backend backend, Consumer<Backend> isFinishedCallback) -> new Thread(() -> {
      try {
        // 1. Create the input
        // 1.1 Create a protocol buffer
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        serializer.write(annotation, os);
        os.close();
        byte[] message = os.toByteArray();
        // 1.2 Create the query params

        String queryParams = String.format("properties=%s",
                                           URLEncoder.encode(StanfordCoreNLPClient.this.propsAsJSON, "utf-8"));

        // 2. Create a connection
        URL serverURL = new URL(backend.protocol, backend.host, backend.port,
                                StanfordCoreNLPClient.this.path + '?' + queryParams);

        // 3. Do the annotation
        //    This method has two contracts:
        //    1. It should call the two relevant callbacks
        //    2. It must not throw an exception
        doAnnotation(annotation, backend, serverURL, message);
      } catch (Throwable t) {
        log.err("Could not annotate via server!", t);
        if (fallbackToLocalPipeline) {
          log.info("Trying to annotate locally...");
          StanfordCoreNLP corenlp = new StanfordCoreNLP(properties);
          corenlp.annotate(annotation);
        } else {
          annotation.set(CoreAnnotations.ExceptionAnnotation.class, t);
        }
      } finally {
        callback.accept(annotation);
        isFinishedCallback.accept(backend);
      }
    }).start());
  }

  static final int MAX_TRIES=3;

  /**
   * Actually try to perform the annotation on the server side.
   * Tries up to 3 times if the first couple don't succeed.
   *
   * @param annotation The annotation we need to fill.
   * @param backend The backend we are querying against.
   * @param serverURL The URL of the server we are hitting.
   * @param message The message we are sending the server (don't need to recompute each retry).
   * @param tries The number of times we've tried already.
   */
  @SuppressWarnings("unchecked")
  private void doAnnotation(Annotation annotation, Backend backend, URL serverURL, byte[] message) {
    for (int tries = 0; tries < MAX_TRIES; ++tries) {
      try {
        // 1. Set up the connection
        URLConnection connection = serverURL.openConnection();
        // 1.1 Set authentication
        if (apiKey != null && apiSecret != null) {
          String userpass = apiKey + ':' + apiSecret;
          String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userpass.getBytes()));
          connection.setRequestProperty("Authorization", basicAuth);
        }
        // 1.2 Set some protocol-independent properties
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-protobuf");
        connection.setRequestProperty("Content-Length", Integer.toString(message.length));
        connection.setRequestProperty("Accept-Charset", "utf-8");
        connection.setRequestProperty("User-Agent", StanfordCoreNLPClient.class.getName());
        if (timeoutMilliseconds > 0) {
          connection.setConnectTimeout(timeoutMilliseconds);
          connection.setReadTimeout(timeoutMilliseconds);
        }
        // 1.3 Set some protocol-dependent properties
        switch (backend.protocol) {
          case "https":
          case "http":
            ((HttpURLConnection) connection).setRequestMethod("POST");
            break;
          default:
            throw new IllegalStateException("Haven't implemented protocol: " + backend.protocol);
        }

        // 2. Annotate
        // 2.1. Fire off the request
        connection.connect();
        connection.getOutputStream().write(message);
        connection.getOutputStream().flush();
        // 2.2 Await a response
        // -- It might be possible to send more than one message, but we are not going to do that.
        Annotation response = serializer.read(connection.getInputStream()).first;
        // 2.3. Copy response over to original annotation
        for (Class key : response.keySet()) {
          annotation.set(key, response.get(key));
        }

        //Succeeded!  Can break out of the loop now
        return;
      } catch (Throwable t) {
        // 3. We encountered an error -- retry
        if (tries == MAX_TRIES - 1) {
          throw new RuntimeException(t);
        }

        log.warn(t);
      }
    }
  }

  /** Return true if the referenced server is alive and returns a non-error response code.
   *
   * @param serverURL The server (running CoreNLP) to check
   * @return true if the server is alive and returns a response code between 200 and 400 inclusive
   */
  @SuppressWarnings("unused")
  public boolean checkStatus(URL serverURL) {
    try {
      // 1. Set up the connection
      HttpURLConnection connection = (HttpURLConnection) serverURL.openConnection();
      // 1.1 Set authentication
      if (apiKey != null && apiSecret != null) {
        String userpass = apiKey + ':' + apiSecret;
        String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userpass.getBytes()));
        connection.setRequestProperty("Authorization", basicAuth);
      }

      connection.setRequestMethod("GET");
      connection.connect();
      return connection.getResponseCode() >= 200 && connection.getResponseCode() <= 400;
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
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
    final StanfordCoreNLP.OutputFormat outputFormat = StanfordCoreNLP.OutputFormat.valueOf(pipeline.properties.getProperty("outputFormat", "text").toUpperCase(Locale.ROOT));
    IOUtils.console("NLP> ", line -> {
      if ( ! line.isEmpty()) {
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
   * @throws IOException If any IO problem
   */
  public void run() throws IOException {
    StanfordRedwoodConfiguration.minimalSetup();
    StanfordCoreNLP.OutputFormat outputFormat = StanfordCoreNLP.OutputFormat.valueOf(properties.getProperty("outputFormat", "text").toUpperCase(Locale.ROOT));

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
          StanfordCoreNLP.createOutputter(properties, new AnnotationOutputter.Options()), outputFormat, false);
    }

    //
    // Process a list of files
    //
    else if (properties.containsKey("filelist")){
      String fileName = properties.getProperty("filelist");
      Collection<File> inputFiles = StanfordCoreNLP.readFileList(fileName);
      Collection<File> files = new ArrayList<>(inputFiles.size());
      for (File file : inputFiles) {
        if (file.isDirectory()) {
          files.addAll(new FileSequentialCollection(new File(fileName), properties.getProperty("extension"), true));
        } else {
          files.add(file);
        }
      }
      StanfordCoreNLP.processFiles(null, files, 1, properties, this::annotate,
          StanfordCoreNLP.createOutputter(properties, new AnnotationOutputter.Options()), outputFormat, false);
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
    scheduler.stateLock.lock();
    try {
      while (!scheduler.queue.isEmpty() || scheduler.freeAnnotators.size() != scheduler.backends.size()) {
        scheduler.shouldShutdown.await(5, TimeUnit.SECONDS);
      }
      scheduler.doRun = false;
      scheduler.enqueued.signalAll();  // In case the thread's waiting on this condition
    } finally {
      scheduler.stateLock.unlock();
    }
  }


  /**
   * Client that runs data through a StanfordCoreNLPServer either just for testing or for command-line text processing.
   * This runs the pipeline you specify on the
   * text in the file(s) that you specify (with -file or -filelist) and sends some results to stdout.
   * The current code in this main method assumes that each line of the file
   * is to be processed separately as a single sentence.
   * A site must be specified with a protocol like "https:" in front of it.
   * <p>
   * Options:
   * <ul>
   *   <li>-h or -help: print a help message</li>
   *   <li>-backends: Specify the URL of backends to use (default is: http://localhost:9000)</li>
   *   <li>-host and -port: Legacy alternative to -backends</li>
   *   <li>-fallbackToLocalPipeline: If processing via the server fails, try to process a text with a local pipeline</li>
   * </ul>
   *
   * Example usage:<br>
   * java -mx6g edu.stanford.nlp.pipeline.StanfordCoreNLP -props properties -backends site1:port1,site2:port2 <br>
   *    or just -host https://foo.bar.com [-port 9000]
   *
   * @param args List of required properties
   * @throws java.io.IOException If IO problem
   */
  public static void main(String[] args) throws IOException {
    //
    // process the arguments
    //
    // extract all the properties from the command line
    // if cmd line is empty, set the properties to null. The processor will search for the properties file in the classpath
    // if (args.length < 2) {
    //   log.info("Usage: " + StanfordCoreNLPClient.class.getSimpleName() + " -host <hostname> -port <port> ...");
    //   System.exit(1);
    // }
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
    String defaultBack = "http://localhost:9000";
    String backStr = props.getProperty("backends");
    if (backStr == null) {
      String host = props.getProperty("host");
      String port = props.getProperty("port");
      if (host != null) {
        if (port != null) {
          defaultBack = host + ':' + port;
        } else {
          defaultBack = host;
        }
      }
    }

    for (String spec : props.getProperty("backends", defaultBack).split(",")) {
      Matcher matcher = URL_PATTERN.matcher(spec.trim());
      if (matcher.matches()) {
        String protocol = matcher.group(1);
        if (protocol == null) {
          protocol = "http";
        }
        String host = matcher.group(2);
        int port = 80;
        String portStr = matcher.group(3);
        if (portStr != null) {
          port = Integer.parseInt(portStr);
        }
        backends.add(new Backend(protocol, host, port));
      }
    }
    log.info("Using backends: " + backends);

    // Run the pipeline
    StanfordCoreNLPClient client = new StanfordCoreNLPClient(props, backends);
    client.fallbackToLocalPipeline = props.containsKey("fallbackToLocalPipeline");
    client.run();
    try {
      client.shutdown();  // In case anything is pending on the server
    } catch (InterruptedException ignored) { }
  } // end main()

}
