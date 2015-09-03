package edu.stanford.nlp.pipeline;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.MetaClass;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.util.*;

import static edu.stanford.nlp.util.logging.Redwood.Util.*;

/**
 * This class creates a server that runs a new Java annotator in each thread.
 *
 */
public class StanfordCoreNLPServer implements Runnable {
  protected static int DEFAULT_PORT = 9000;

  protected HttpServer server;
  protected int serverPort;

  public static int HTTP_OK = 200;
  public static int HTTP_BAD_INPUT = 400;
  public static int HTTP_ERR = 500;
  public final Properties defaultProps;


  public StanfordCoreNLPServer(int port) {
    serverPort = port;

    defaultProps = new Properties();
    defaultProps.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
    defaultProps.setProperty("inputFormat", "text");
    defaultProps.setProperty("outputFormat", "json");
  }

  /**
   *
   */
  protected static class PingHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
      // Return a simple text message that says pong.
      httpExchange.getResponseHeaders().set("Content-Type", "text/plain");
      String response = "pong\n";
      httpExchange.sendResponseHeaders(HTTP_OK, response.length());
      httpExchange.getResponseBody().write(response.getBytes());
      httpExchange.close();
    }
  }

  /**
   * The main handler for taking an annotation request, and annotating it.
   */
  protected static class SimpleAnnotateHandler implements HttpHandler {
    /**
     * The default properties to use in the absence of anything sent by the client.
     */
    public final Properties defaultProps;
    /**
     * To prevent grossly wasteful over-creation of pipeline objects, cache the last
     * few we created, until the garbage collector decides we can kill them.
     */
    private final WeakHashMap<Properties, StanfordCoreNLP> pipelineCache = new WeakHashMap<>();

    /**
     * Create a handler for accepting annotation requests.
     * @param props The properties file to use as the default if none were sent by the client.
     */
    public SimpleAnnotateHandler(Properties props) {
      defaultProps = props;
    }

    /**
     * Create (or retrieve) a StanfordCoreNLP object corresponding to these properties.
     * @param props The properties to create the object with.
     * @return A pipeline parameterized by these properties.
     */
    private StanfordCoreNLP mkStanfordCoreNLP(Properties props) {
      StanfordCoreNLP impl;
      synchronized (pipelineCache) {
        impl = pipelineCache.get(props);
        if (impl == null) {
          impl = new StanfordCoreNLP(props);
          pipelineCache.put(props, impl);
        }
      }
      return impl;
    }

    public String getContentType(Properties props, StanfordCoreNLP.OutputFormat of) {
      switch(of) {
        case JSON:
          return "text/json";
        case TEXT:
        case CONLL:
          return "text/plain";
        case XML:
          return "text/xml";
        case SERIALIZED:
          String outputSerializerName = props.getProperty("outputSerializer");
          if (outputSerializerName != null &&
              outputSerializerName.equals(ProtobufAnnotationSerializer.class.getName())) {
            return "application/x-protobuf";
          }
        default:
          return "application/octet-stream";
      }
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
      // Get sentence.
      Properties props;
      Annotation ann;
      StanfordCoreNLP.OutputFormat of;
      log("Received message from " + httpExchange.getRemoteAddress());
      try {
        props = getProperties(httpExchange);
        ann = getDocument(props, httpExchange);
        of = StanfordCoreNLP.OutputFormat.valueOf(props.getProperty("outputFormat").toUpperCase());
      } catch (IOException | ClassNotFoundException e) {
        // Return error message.
        e.printStackTrace();
        String response = e.getMessage();
        httpExchange.getResponseHeaders().add("Content-Type", "text/plain");
        httpExchange.sendResponseHeaders(HTTP_BAD_INPUT, response.length());
        httpExchange.getResponseBody().write(response.getBytes());
        httpExchange.close();
        return;
      }

      try {
        // Annoatate
        StanfordCoreNLP pipeline = mkStanfordCoreNLP(props);
        pipeline.annotate(ann);

        // Get output
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        StanfordCoreNLP.createOutputter(props, AnnotationOutputter.getOptions(pipeline)).accept(ann, os);
        os.close();
        byte[] response = os.toByteArray();

        httpExchange.getResponseHeaders().add("Content-Type", getContentType(props, of));
        httpExchange.getResponseHeaders().add("Content-Length", Integer.toString(response.length));
        httpExchange.sendResponseHeaders(HTTP_OK, response.length);
        httpExchange.getResponseBody().write(response);
        httpExchange.close();
      } catch (Exception e) {
        // Return error message.
        e.printStackTrace();
        String response = e.getMessage();
        httpExchange.getResponseHeaders().add("Content-Type", "text/plain");
        httpExchange.sendResponseHeaders(HTTP_ERR, response.length());
        httpExchange.getResponseBody().write(response.getBytes());
        httpExchange.close();
      }
    }

    Map<String, String> getURLParams(URI uri) {
      if (uri.getQuery() != null) {
        Map<String, String> urlParams = new HashMap<>();

        String query = uri.getQuery();
        String[] queryFields = query.split("&");
        for (String queryField : queryFields) {
          String[] keyValue = queryField.split("=");
          urlParams.put(keyValue[0], keyValue[1]);
        }
        return urlParams;
      } else {
        return Collections.emptyMap();
      }
    }

    private Properties getProperties(HttpExchange httpExchange) throws UnsupportedEncodingException {
      // Load the default properties
      Properties props = new Properties();
      defaultProps.entrySet().stream()
          .forEach(entry -> props.setProperty(entry.getKey().toString(), entry.getValue().toString()));

      // Try to get more properties from query string.
      Map<String, String> urlParams = getURLParams(httpExchange.getRequestURI());
      if (urlParams.containsKey("properties")) {
        // Parse properties
        StringUtils.decodeMap(URLDecoder.decode(urlParams.get("properties"), "UTF-8")).entrySet()
            .forEach(entry -> props.setProperty(entry.getKey(), entry.getValue()));
      }

      return props;
    }

    private Annotation getDocument(Properties props, HttpExchange httpExchange) throws IOException, ClassNotFoundException {
      String inputFormat = props.getProperty("inputFormat");
      switch (inputFormat) {
        case "text":
          return new Annotation(IOUtils.slurpReader(new InputStreamReader(httpExchange.getRequestBody())));
        case "serialized":
          String inputSerializerName = props.getProperty("inputSerializer");
          AnnotationSerializer serializer = MetaClass.create(inputSerializerName).createInstance();
          Pair<Annotation, InputStream> pair = serializer.read(httpExchange.getRequestBody());
          return pair.first;
        default:
          throw new IOException("Could not parse input format: " + inputFormat);
      }
    }
  }

  @Override
  public void run() {
    try {

      server = HttpServer.create(new InetSocketAddress(serverPort), 0); // 0 is the default 'backlog'
      server.createContext("/ping", new PingHandler());
      server.createContext("/", new SimpleAnnotateHandler(defaultProps));
//            server.createContext("/protobuf", new PingHandler());
      server.start();
      log("StanfordCoreNLPServer listening at " + server.getAddress());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void main(String[] args) {
    int port = DEFAULT_PORT;
    if(args.length > 0) {
      port = Integer.parseInt(args[0]);
    }
    StanfordCoreNLPServer server = new StanfordCoreNLPServer(port);
    server.run();
  }
}
