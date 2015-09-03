package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.FileSequentialCollection;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.StanfordRedwoodConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * An annotation pipeline in spirit identical to {@link StanfordCoreNLP}, but
 * with the backend supported by a web server.
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("FieldCanBeLocal")
public class CoreNLPWebClient extends AnnotationPipeline {

  /** The protocol to connect to the server with. */
  private final String protocol = "http";
  /** The path on the server to connect to. */
  private final String path = "";
  /** The properties file to annotate with. */
  private final Properties properties;

  /** The properties file, serialized as JSON. */
  private final String propsAsJSON;

  /** The hostname of the server running the CoreNLP annotators */
  public final String host;
  /** The port of the server running the CoreNLP annotators */
  public final int port;

  /**
   * The annotation serializer responsible for translating between the wire format
   * (protocol buffers) and the {@link Annotation} classes.
   */
  private final ProtobufAnnotationSerializer serializer = new ProtobufAnnotationSerializer(true);

  public CoreNLPWebClient(Properties properties, String host, int port) {
    // Save the constructor variables
    this.properties = new Properties(properties);
    this.host = host;
    this.port = port;

    // Set required properties
    this.properties.setProperty("inputFormat", "serialized");
    this.properties.setProperty("outputFormat", "serialized");
    this.properties.setProperty("inputSerializer", ProtobufAnnotationSerializer.class.getName());
    this.properties.setProperty("outputSerializer", ProtobufAnnotationSerializer.class.getName());

    // Create a list of all the properties, as JSON map elements
    List<String> jsonProperties = new ArrayList<>();
    Enumeration<?> keys = this.properties.propertyNames();
    while (keys.hasMoreElements()) {
      Object key = keys.nextElement();
      jsonProperties.add(
          "\"" + JSONOutputter.cleanJSON(key.toString()) + "\": \"" +
              JSONOutputter.cleanJSON(this.properties.getProperty(key.toString())) + "\"");
    }
    // Create the JSON object
    this.propsAsJSON = "{ " + StringUtils.join(jsonProperties, ", ") + " }";
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
      System.err.println("Interrupt while waiting for annotation to return");
    } finally {
      lock.unlock();
    }
  }

  /**
   * This method fires off a request to the server. Upon returning,
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
    new Thread() {
      @Override
      public void run() {
        try {
          // 1. Create the input
          // 1.1 Create a protocol buffer
          CoreNLPProtos.Document unannotatedProto = serializer.toProto(annotation);
          int protoSize = unannotatedProto.getSerializedSize();
          // 1.2 Create the query params
          String queryParams = String.format(
              "properties=%s",
              URLEncoder.encode(CoreNLPWebClient.this.propsAsJSON, "utf-8"));

          // 2. Create a connection
          // 2.1 Open a connection
          URL serverURL = new URL(CoreNLPWebClient.this.protocol, CoreNLPWebClient.this.host,
              CoreNLPWebClient.this.port,
              CoreNLPWebClient.this.path + "?" + queryParams);
          URLConnection connection = serverURL.openConnection();
          // 2.2 Set some protocol-independent properties
          connection.setDoInput(true);
          connection.setRequestProperty("Content-Type", "application/x-protobuf");
          connection.setRequestProperty("Content-Length", Integer.toString(protoSize));
          connection.setRequestProperty("Accept-Charset", "utf-8");
          connection.setRequestProperty("User-Agent", CoreNLPWebClient.class.getName());
          // 2.3 Set some protocol-dependent properties
          switch (CoreNLPWebClient.this.protocol) {
            case "http":
              ((HttpURLConnection) connection).setRequestMethod("POST");
              break;
            default:
              throw new IllegalStateException("Haven't implemented protocol: " + CoreNLPWebClient.this.protocol);
          }

          // 3. Fire off the request
          OutputStream os = connection.getOutputStream();
          os.write(unannotatedProto.toByteArray());
          System.err.println("Wrote " + protoSize + " bytes to " + CoreNLPWebClient.this.host + ":" + CoreNLPWebClient.this.port);

          // 4. Await a response
          InputStream input = connection.getInputStream();
          CoreNLPProtos.Document annotatedProto = CoreNLPProtos.Document.parseFrom(input);
          Annotation response = serializer.fromProto(annotatedProto);

          // 5. Copy response over to original annotation
          for (Class key : response.keySet()) {
            annotation.set(key, response.get(key));
          }

          // 6. Call the callback
          callback.accept(annotation);
        } catch (IOException e) {
          throw new RuntimeIOException("Could not connect to server: " + host + ":" + port, e);
        }
      }
    }.start();
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
  private static void shell(CoreNLPWebClient pipeline) throws IOException {
    System.err.println("Entering interactive shell. Type q RETURN or EOF to quit.");
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
          StanfordCoreNLP.createOutputter(properties, AnnotationOutputter.Options::new));
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
          StanfordCoreNLP.createOutputter(properties, AnnotationOutputter.Options::new));
    }

    //
    // Run the interactive shell
    //
    else {
      shell(this);
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
   * java -mx6g edu.stanford.nlp.pipeline.StanfordCoreNLP properties
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
    if (args.length < 4) {
      System.err.println("Usage: " + CoreNLPWebClient.class.getSimpleName() + " -host <hostname> -port <port> ...");
      System.exit(1);
    }
    Properties props = StringUtils.argsToProperties(args);
    boolean hasH = props.containsKey("h");
    boolean hasHelp = props.containsKey("help");
    if (hasH || hasHelp) {
      String helpValue = hasH ? props.getProperty("h") : props.getProperty("help");
      StanfordCoreNLP.printHelp(System.err, helpValue);
      return;
    }

    // Check required properties
    /*
    if (props.getProperty("backend") == null) {
      System.err.println("Usage: " + CoreNLPWebClient.class.getSimpleName() + " -backend <hostname:port,...> ...");
      System.err.println("Missing required option: -backend <hostname:port,...>");
      System.exit(1);
    }
    */

    // Run the pipeline
    new CoreNLPWebClient(props, props.getProperty("host"), Integer.parseInt(props.getProperty("port"))).run();
  }
}


