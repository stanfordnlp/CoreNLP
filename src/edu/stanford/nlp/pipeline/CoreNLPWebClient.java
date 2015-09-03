package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.util.StringUtils;

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
import java.util.function.Function;

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
  private final Properties props;
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

  public CoreNLPWebClient(Properties props, String host, int port) {
    // Save the constructor variables
    this.props = new Properties(props);
    this.host = host;
    this.port = port;

    // Set required properties
    this.props.setProperty("inputFormat", "serialized");
    this.props.setProperty("outputFormat", "serialized");
    this.props.setProperty("inputSerializer", ProtobufAnnotationSerializer.class.getName());
    this.props.setProperty("outputSerializer", ProtobufAnnotationSerializer.class.getName());

    // Create a list of all the properties, as JSON map elements
    List<String> jsonProperties = new ArrayList<>();
    Enumeration<?> keys = this.props.propertyNames();
    while (keys.hasMoreElements()) {
      Object key = keys.nextElement();
      jsonProperties.add(
          "\"" + JSONOutputter.cleanJSON(key.toString()) + "\": \"" +
              JSONOutputter.cleanJSON(this.props.get(key).toString()) + "\"");
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
      return null;
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
  public void annotate(final Iterable<Annotation> annotations, int numThreads, final Function<Annotation,Object> callback){
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
  public void annotate(final Annotation annotation, final Function<Annotation,Object> callback){
    try {
      // 1. Create the input
      // 1.1 Create a protocol buffer
      CoreNLPProtos.Document unannotatedProto = serializer.toProto(annotation);
      int protoSize = unannotatedProto.getSerializedSize();
      // 1.2 Create the query params
      String queryParams = String.format(
          "properties=%s",
          URLEncoder.encode(this.propsAsJSON, "utf-8"));

      // 2. Create a connection
      // 2.1 Open a connection
      URL serverURL = new URL(this.protocol, this.host, this.port, this.path + "?" + queryParams);
      URLConnection connection = serverURL.openConnection();
      // 2.2 Set some protocol-independent properties
      connection.setDoInput(true);
      connection.setRequestProperty("Content-Type", "application/x-protobuf");
      connection.setRequestProperty("Content-Length", Integer.toString(protoSize));
      connection.setRequestProperty("Accept-Charset", "utf-8");
      connection.setRequestProperty("User-Agent", CoreNLPWebClient.class.getName());
      // 2.3 Set some protocol-dependent properties
      switch (this.protocol) {
        case "http":
          ((HttpURLConnection) connection).setRequestMethod("POST");
          break;
        default:
          throw new IllegalStateException("Haven't implemented protocol: " + this.protocol);
      }

      // 3. Fire off the request
      OutputStream os = connection.getOutputStream();
      os.write(unannotatedProto.toByteArray());
      System.err.println("Wrote " + protoSize + " bytes to " + this.host + ":" + this.port);

      // 4. Await a response
      InputStream input = connection.getInputStream();
      CoreNLPProtos.Document annotatedProto = CoreNLPProtos.Document.parseFrom(input);
      Annotation response = serializer.fromProto(annotatedProto);

      // 5. Copy response over to original annotation
      for (Class key : response.keySet()) {
        annotation.set(key, response.get(key));
      }

      // 6. Call the callback
      callback.apply(annotation);
    } catch (IOException e) {
      throw new RuntimeIOException("Could not connect to server: " + host + ":" + port, e);
    }

  }
}


