package edu.stanford.nlp.pipeline;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.tokensregex.SequenceMatchResult;
import edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.util.*;

import java.io.*;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static edu.stanford.nlp.util.logging.Redwood.Util.*;

/**
 * This class creates a server that runs a new Java annotator in each thread.
 *
 * @author Gabor Angeli
 * @author Arun Chaganty
 */
public class StanfordCoreNLPServer implements Runnable {

  protected static int DEFAULT_PORT = 9000;
  protected static int DEFAULT_TIMEOUT = 5000;

  protected HttpServer server;
  protected final int serverPort;
  protected final int timeoutMilliseconds;
  protected final FileHandler staticPageHandle;
  protected final String shutdownKey;

  public static int HTTP_OK = 200;
  public static int HTTP_BAD_INPUT = 400;
  public static int HTTP_ERR = 500;
  public static int MAX_CHAR_LENGTH = 100000;
  public final Properties defaultProps;

  /**
   * The thread pool for the HTTP server.
   */
  private final ExecutorService serverExecutor = Executors.newFixedThreadPool(Execution.threads);
  /**
   * To prevent grossly wasteful over-creation of pipeline objects, cache the last
   * few we created, until the garbage collector decides we can kill them.
   */
  private final WeakHashMap<Properties, StanfordCoreNLP> pipelineCache = new WeakHashMap<>();
  /**
   * An executor to time out CoreNLP execution with.
   */
  private final ExecutorService corenlpExecutor = Executors.newFixedThreadPool(Execution.threads);


  /**
   * Create a new Stanford CoreNLP Server.
   * @param port The port to host the server from.
   * @param timeout The timeout (in milliseconds) for each command.
   * @throws IOException Thrown from the underlying socket implementation.
   */
  public StanfordCoreNLPServer(int port, int timeout) throws IOException {
    serverPort = port;
    timeoutMilliseconds = timeout;

    defaultProps = PropertiesUtils.asProperties(
            "annotators", "tokenize, ssplit, pos, lemma, ner, parse, depparse, dcoref, natlog, openie",
            "inputFormat", "text",
            "outputFormat", "json");

    // Generate and write a shutdown key
    String tmpDir = System.getProperty("java.io.tmpdir");
    File tmpFile = new File(tmpDir + File.separator + "corenlp.shutdown");
    tmpFile.deleteOnExit();
    if (tmpFile.exists()) {
      if (!tmpFile.delete()) {
        throw new IllegalStateException("Could not delete shutdown key file");
      }
    }
    this.shutdownKey = new BigInteger(130, new Random()).toString(32);
    IOUtils.writeStringToFile(shutdownKey, tmpFile.getPath(), "utf-8");

    // Set the static page handler
    this.staticPageHandle = new FileHandler("edu/stanford/nlp/pipeline/demo/corenlp-brat.html");
  }

  /**
   * Parse the URL parameters into a map of (key, value) pairs.
   *
   * @param uri The URL that was requested.
   *
   * @return A map of (key, value) pairs corresponding to the request parameters.
   *
   * @throws UnsupportedEncodingException Thrown if we could not decode the URL with utf8.
   */
  private static Map<String, String> getURLParams(URI uri) throws UnsupportedEncodingException {
    if (uri.getQuery() != null) {
      Map<String, String> urlParams = new HashMap<>();

      String query = uri.getQuery();
      String[] queryFields = query
          .replaceAll("\\\\&", "___AMP___")
          .replaceAll("\\\\+", "___PLUS___")
          .split("&");
      for (String queryField : queryFields) {
        int firstEq = queryField.indexOf('=');
        // Convention uses "+" for spaces.
        String key = URLDecoder.decode(queryField.substring(0, firstEq), "utf8").replaceAll("___AMP___", "&").replaceAll("___PLUS___", "+");
        String value = URLDecoder.decode(queryField.substring(firstEq + 1), "utf8").replaceAll("___AMP___", "&").replaceAll("___PLUS___", "+");
        urlParams.put(key, value);
      }
      return urlParams;
    } else {
      return Collections.emptyMap();
    }
  }

  /**
   * Reads the POST contents of the request and parses it into an Annotation object, ready to be annotated.
   * This method can also read a serialized document, if the input format is set to be serialized.
   *
   * @param props The properties we are annotating with. This is where the input format is retrieved from.
   * @param httpExchange The exchange we are reading POST data from.
   *
   * @return An Annotation representing the read document.
   *
   * @throws IOException Thrown if we cannot read the POST data.
   * @throws ClassNotFoundException Thrown if we cannot load the serializer.
   */
  private static Annotation getDocument(Properties props, HttpExchange httpExchange) throws IOException, ClassNotFoundException {
    String inputFormat = props.getProperty("inputFormat", "text");
    switch (inputFormat) {
      case "text":
        // Get the encoding
        Headers h = httpExchange.getRequestHeaders();
        String encoding;
        if (h.containsKey("Content-type")) {
          String[] charsetPair = Arrays.asList(h.getFirst("Content-type").split(";")).stream()
              .map(x -> x.split("="))
              .filter(x -> x.length > 0 && "charset".equals(x[0]))
              .findFirst().orElse(new String[]{"charset", "ISO-8859-1"});
          if (charsetPair.length == 2) {
            encoding = charsetPair[1];
          } else {
            encoding = "ISO-8859-1";  // default encoding for a form
          }
        } else {
          encoding = "ISO-8859-1";  // default encoding for a form
        }

        // Read the annotation
        return new Annotation(IOUtils.slurpInputStream(httpExchange.getRequestBody(), encoding));
      case "serialized":
        String inputSerializerName = props.getProperty("inputSerializer", ProtobufAnnotationSerializer.class.getName());
        AnnotationSerializer serializer = MetaClass.create(inputSerializerName).createInstance();
        Pair<Annotation, InputStream> pair = serializer.read(httpExchange.getRequestBody());
        return pair.first;
      default:
        throw new IOException("Could not parse input format: " + inputFormat);
    }
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

  /**
   * A helper function to respond to a request with an error.
   *
   * @param response The description of the error to send to the user.
   * @param httpExchange The exchange to send the error over.
   *
   * @throws IOException Thrown if the HttpExchange cannot communicate the error.
   */
  private static void respondError(String response, HttpExchange httpExchange) throws IOException {
    httpExchange.getResponseHeaders().add("Content-Type", "text/plain");
    httpExchange.sendResponseHeaders(HTTP_ERR, response.length());
    httpExchange.getResponseBody().write(response.getBytes());
    httpExchange.close();
  }

  /**
   * A helper function to respond to a request with an error specifically indicating
   * bad input from the user.
   *
   * @param response The description of the error to send to the user.
   * @param httpExchange The exchange to send the error over.
   *
   * @throws IOException Thrown if the HttpExchange cannot communicate the error.
   */
  private static void respondBadInput(String response, HttpExchange httpExchange) throws IOException {
    httpExchange.getResponseHeaders().add("Content-Type", "text/plain");
    httpExchange.sendResponseHeaders(HTTP_BAD_INPUT, response.length());
    httpExchange.getResponseBody().write(response.getBytes());
    httpExchange.close();
  }


  /**
   * A simple ping test. Responds with pong.
   */
  protected static class PingHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
      // Return a simple text message that says pong.
      httpExchange.getResponseHeaders().set("Content-Type", "text/plain");
      String response = "pong\n";
      httpExchange.sendResponseHeaders(HTTP_OK, response.getBytes().length);
      httpExchange.getResponseBody().write(response.getBytes());
      httpExchange.close();
    }
  }

  /**
   * Sending the appropriate shutdown key will gracefully shutdown the server.
   * This key is, by default, saved into the local file /tmp/corenlp.shutdown on the
   * machine the server was run from.
   */
  protected class ShutdownHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
      Map<String, String> urlParams = getURLParams(httpExchange.getRequestURI());
      httpExchange.getResponseHeaders().set("Content-Type", "text/plain");
      boolean doExit = false;
      String response = "Invalid shutdown key\n";
      if (urlParams.containsKey("key") && urlParams.get("key").equals(shutdownKey)) {
        response = "Shutdown successful!\n";
        doExit = true;
      }
      httpExchange.sendResponseHeaders(HTTP_OK, response.getBytes().length);
      httpExchange.getResponseBody().write(response.getBytes());
      httpExchange.close();
      if (doExit) {
        System.exit(0);
      }
    }
  }

  /**
   * Serve a file from the filesystem or classpath
   */
  protected static class FileHandler implements HttpHandler {
    private final String content;
    public FileHandler(String fileOrClasspath) throws IOException {
      this.content = IOUtils.slurpReader(IOUtils.readerFromString(fileOrClasspath));
    }
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
      httpExchange.getResponseHeaders().set("Content-Type", "text/html");
      httpExchange.sendResponseHeaders(HTTP_OK, content.getBytes().length);
      httpExchange.getResponseBody().write(content.getBytes());
      httpExchange.close();
    }
  }

  /**
   * The main handler for taking an annotation request, and annotating it.
   */
  protected class CoreNLPHandler implements HttpHandler {
    /**
     * The default properties to use in the absence of anything sent by the client.
     */
    public final Properties defaultProps;

    /**
     * Create a handler for accepting annotation requests.
     * @param props The properties file to use as the default if none were sent by the client.
     */
    public CoreNLPHandler(Properties props) {
      defaultProps = props;
    }

    /**
     * Get the response data type to send to the client, based off of the output format requested from
     * CoreNLP.
     *
     * @param props The properties being used by CoreNLP.
     * @param of The output format being output by CoreNLP.
     *
     * @return An identifier for the type of the HTTP response (e.g., 'text/json').
     */
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
          //noinspection fallthrough
        default:
          return "application/octet-stream";
      }
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
      // Set common response headers
      httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

      // Get sentence.
      Properties props;
      Annotation ann;
      StanfordCoreNLP.OutputFormat of;
      try {
        props = getProperties(httpExchange);
        ann = getDocument(props, httpExchange);
        of = StanfordCoreNLP.OutputFormat.valueOf(props.getProperty("outputFormat", "json").toUpperCase());

        if (ann.get(CoreAnnotations.TextAnnotation.class).isEmpty()) {
          // Handle direct browser connections (i.e., not a POST request).
          log("[" + httpExchange.getRemoteAddress() + "] Interactive connection");
          staticPageHandle.handle(httpExchange);
          return;
        } else {
          // Handle API request
          log("[" + httpExchange.getRemoteAddress() + "] API call w/annotators " + props.getProperty("annotators", "<unknown>"));
          String text = ann.get(CoreAnnotations.TextAnnotation.class).replace('\n', ' ');
          System.out.println(text);
          if (text.length() > MAX_CHAR_LENGTH) {
            respondBadInput("Request is too long to be handled by server: " + text.length() + " characters. Max length is " + MAX_CHAR_LENGTH + " characters.", httpExchange);
            return;
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
        respondError("Could not handle incoming annotation", httpExchange);
        return;
      }

      Future<Annotation> completedAnnotationFuture = null;
      try {
        // Annotate
        StanfordCoreNLP pipeline = mkStanfordCoreNLP(props);
        completedAnnotationFuture = corenlpExecutor.submit(() -> {
          pipeline.annotate(ann);
          return ann;
        });
        Annotation completedAnnotation;
        try {
          int timeoutMilliseconds = Integer.parseInt(props.getProperty("timeout",
                                                     Integer.toString(StanfordCoreNLPServer.this.timeoutMilliseconds)));
          // Check for too long a timeout from an unauthorized source
          if (timeoutMilliseconds > 10000) {
            // If two conditions:
            //   (1) The server is running on corenlp.run (i.e., corenlp.stanford.edu)
            //   (2) The request is not coming from a *.stanford.edu" email address
            // Then force the timeout to be 10 seconds
            if ("corenlp.stanford.edu".equals(InetAddress.getLocalHost().getHostName()) &&
                !httpExchange.getRemoteAddress().getHostName().toLowerCase().endsWith("stanford.edu")) {
              timeoutMilliseconds = 10000;
            }
          }
          completedAnnotation = completedAnnotationFuture.get(timeoutMilliseconds, TimeUnit.MILLISECONDS);
        } catch (NumberFormatException e) {
          completedAnnotation = completedAnnotationFuture.get(StanfordCoreNLPServer.this.timeoutMilliseconds, TimeUnit.MILLISECONDS);
        }
        completedAnnotationFuture = null;  // No longer any need for the future

        // Get output
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        StanfordCoreNLP.createOutputter(props, AnnotationOutputter.getOptions(pipeline)).accept(completedAnnotation, os);
        os.close();
        byte[] response = os.toByteArray();

        httpExchange.getResponseHeaders().add("Content-Type", getContentType(props, of));
        httpExchange.getResponseHeaders().add("Content-Length", Integer.toString(response.length));
        httpExchange.sendResponseHeaders(HTTP_OK, response.length);
        httpExchange.getResponseBody().write(response);
        httpExchange.close();
      } catch (TimeoutException e) {
        // Print the stack trace for debugging
        e.printStackTrace();
        // Return error message.
        respondError("CoreNLP request timed out. Your document may be too long.", httpExchange);
        // Cancel the future if it's alive
        //noinspection ConstantConditions
        if (completedAnnotationFuture != null) {
          completedAnnotationFuture.cancel(true);
        }
      } catch (Exception e) {
        // Print the stack trace for debugging
        e.printStackTrace();
        // Return error message.
        respondError(e.getClass().getName() + ": " + e.getMessage(), httpExchange);
        // Cancel the future if it's alive
        //noinspection ConstantConditions
        if (completedAnnotationFuture != null) {  // just in case...
          completedAnnotationFuture.cancel(true);
        }
      }
    }

    /**
     * Parse the parameters of a connection into a CoreNLP properties file that can be passed into
     * {@link StanfordCoreNLP}, and used in the I/O stages.
     *
     * @param httpExchange The http exchange; effectively, the request information.
     *
     * @return A {@link Properties} object corresponding to a combination of default and passed properties.
     *
     * @throws UnsupportedEncodingException Thrown if we could not decode the key/value pairs with UTF-8.
     */
    private Properties getProperties(HttpExchange httpExchange) throws UnsupportedEncodingException {
      // Load the default properties
      Properties props = new Properties();
      defaultProps.entrySet().stream()
          .forEach(entry -> props.setProperty(entry.getKey().toString(), entry.getValue().toString()));

      // Try to get more properties from query string.
      Map<String, String> urlParams = getURLParams(httpExchange.getRequestURI());
      if (urlParams.containsKey("properties")) {
        StringUtils.decodeMap(URLDecoder.decode(urlParams.get("properties"), "UTF-8")).entrySet()
            .forEach(entry -> props.setProperty(entry.getKey(), entry.getValue()));
      } else if (urlParams.containsKey("props")) {
        StringUtils.decodeMap(URLDecoder.decode(urlParams.get("properties"), "UTF-8")).entrySet()
            .forEach(entry -> props.setProperty(entry.getKey(), entry.getValue()));
      }

      // Get the annotators
      String annotators = StanfordCoreNLP.ensurePrerequisiteAnnotators(props.getProperty("annotators").split("[, \t]+"));

      // Tweak the default annotator behavior
      if (annotators.contains("coref") /* any coref */ && annotators.contains("ner") && annotators.contains("openie") &&
          !"false".equals(props.getProperty("openie.resolve_coref", "true"))) {
        props.setProperty("openie.resolve_coref", "true");
      }

      // Tweak the properties to play nicer with the server
      // (set the parser max length to 60)
      if (!"-1".equals(props.getProperty("parse.maxlen", "60"))) {
        props.put("parse.maxlen", "60");
      }
      if (!"-1".equals(props.getProperty("pos.maxlen", "500"))) {
        props.put("pos.maxlen", "500");
      }

      // Make sure the properties compile
      props.setProperty("annotators", annotators);

      return props;
    }
  }



  /**
   * A handler for matching TokensRegex patterns against text.
   */
  protected class TokensRegexHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
      // Set common response headers
      httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

      Future<String> json = corenlpExecutor.submit(() -> {
        try {
          // Get the document
          Properties props = PropertiesUtils.asProperties("annotators", "tokenize,ssplit,pos,lemma,ner");
          Annotation doc = getDocument(props, httpExchange);
          if (!doc.containsKey(CoreAnnotations.SentencesAnnotation.class)) {
            StanfordCoreNLP pipeline = mkStanfordCoreNLP(props);
            pipeline.annotate(doc);
          }

          // Construct the matcher
          Map<String, String> params = getURLParams(httpExchange.getRequestURI());
          // (get the pattern)
          if (!params.containsKey("pattern")) {
            respondBadInput("Missing required parameter 'pattern'", httpExchange);
            return "";
          }
          String pattern = params.get("pattern");
          // (get whether to filter / find)
          String filterStr = params.getOrDefault("filter", "false");
          final boolean filter = filterStr.trim().isEmpty() || "true".equalsIgnoreCase(filterStr.toLowerCase());
          // (create the matcher)
          final TokenSequencePattern regex = TokenSequencePattern.compile(pattern);

          // Run TokensRegex
          return JSONOutputter.JSONWriter.objectToJSON((docWriter) -> {
            if (filter) {
              // Case: just filter sentences
              docWriter.set("sentences", doc.get(CoreAnnotations.SentencesAnnotation.class).stream().map(sentence ->
                      regex.matcher(sentence.get(CoreAnnotations.TokensAnnotation.class)).matches()
              ).collect(Collectors.toList()));
            } else {
              // Case: find matches
              docWriter.set("sentences", doc.get(CoreAnnotations.SentencesAnnotation.class).stream().map(sentence -> (Consumer<JSONOutputter.Writer>) (JSONOutputter.Writer sentWriter) -> {
                List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
                TokenSequenceMatcher matcher = regex.matcher(tokens);
                int i = 0;
                while (matcher.find()) {
                  sentWriter.set(Integer.toString(i), (Consumer<JSONOutputter.Writer>) (JSONOutputter.Writer matchWriter) -> {
                    matchWriter.set("text", matcher.group());
                    matchWriter.set("begin", matcher.start());
                    matchWriter.set("end", matcher.end());
                    for (int groupI = 0; groupI < matcher.groupCount(); ++groupI) {
                      SequenceMatchResult.MatchedGroupInfo<CoreMap> info = matcher.groupInfo(groupI + 1);
                      matchWriter.set(info.varName == null ? Integer.toString(groupI + 1) : info.varName, (Consumer<JSONOutputter.Writer>) groupWriter -> {
                        groupWriter.set("text", info.text);
                        if (info.nodes.size() > 0) {
                          groupWriter.set("begin", info.nodes.get(0).get(CoreAnnotations.IndexAnnotation.class) - 1);
                          groupWriter.set("end", info.nodes.get(info.nodes.size() - 1).get(CoreAnnotations.IndexAnnotation.class));
                        }
                      });
                    }
                  });
                  i += 1;
                }
                sentWriter.set("length", i);
              }));
            }
          });
        } catch (Exception e) {
          e.printStackTrace();
          try {
            respondError(e.getClass().getName() + ": " + e.getMessage(), httpExchange);
          } catch (IOException ignored) {
          }
        }
        return "";
      });

      // Send response
      try {
        byte[] response = json.get(5, TimeUnit.SECONDS).getBytes();
        sendAndGetResponse(httpExchange, response);
      } catch (InterruptedException | ExecutionException | TimeoutException e) {
        respondError("Timeout when executing TokensRegex query", httpExchange);
      }
    }
  }



  /**
   * A handler for matching semgrex patterns against dependency trees.
   */
  protected class SemgrexHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
      // Set common response headers
      httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

      Future<String> json = corenlpExecutor.submit(() -> {
        try {
          // Get the document
          Properties props = PropertiesUtils.asProperties("annotators", "tokenize,ssplit,pos,lemma,ner,depparse");
          Annotation doc = getDocument(props, httpExchange);
          if (!doc.containsKey(CoreAnnotations.SentencesAnnotation.class)) {
            StanfordCoreNLP pipeline = mkStanfordCoreNLP(props);
            pipeline.annotate(doc);
          }

          // Construct the matcher
          Map<String, String> params = getURLParams(httpExchange.getRequestURI());
          // (get the pattern)
          if (!params.containsKey("pattern")) {
            respondBadInput("Missing required parameter 'pattern'", httpExchange);
            return "";
          }
          String pattern = params.get("pattern");
          // (get whether to filter / find)
          String filterStr = params.getOrDefault("filter", "false");
          final boolean filter = filterStr.trim().isEmpty() || "true".equalsIgnoreCase(filterStr.toLowerCase());
          // (create the matcher)
          final SemgrexPattern regex = SemgrexPattern.compile(pattern);

          // Run TokensRegex
          return JSONOutputter.JSONWriter.objectToJSON((docWriter) -> {
            if (filter) {
              // Case: just filter sentences
              docWriter.set("sentences", doc.get(CoreAnnotations.SentencesAnnotation.class).stream().map(sentence ->
                      regex.matcher(sentence.get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class)).matches()
              ).collect(Collectors.toList()));
            } else {
              // Case: find matches
              docWriter.set("sentences", doc.get(CoreAnnotations.SentencesAnnotation.class).stream().map(sentence -> (Consumer<JSONOutputter.Writer>) (JSONOutputter.Writer sentWriter) -> {
                SemgrexMatcher matcher = regex.matcher(sentence.get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class));
                int i = 0;
                while (matcher.find()) {
                  sentWriter.set(Integer.toString(i), (Consumer<JSONOutputter.Writer>) (JSONOutputter.Writer matchWriter) -> {
                    IndexedWord match = matcher.getMatch();
                    matchWriter.set("text", match.word());
                    matchWriter.set("begin", match.index() - 1);
                    matchWriter.set("end", match.index());
                    for (String capture : matcher.getNodeNames()) {
                      matchWriter.set("$" + capture, (Consumer<JSONOutputter.Writer>) groupWriter -> {
                        IndexedWord node = matcher.getNode(capture);
                        groupWriter.set("text", node.word());
                        groupWriter.set("begin", node.index() - 1);
                        groupWriter.set("end", node.index());
                      });
                    }
                  });
                  i += 1;
                }
                sentWriter.set("length", i);
              }));
            }
          });
        } catch (Exception e) {
          e.printStackTrace();
          try {
            respondError(e.getClass().getName() + ": " + e.getMessage(), httpExchange);
          } catch (IOException ignored) {
          }
        }
        return "";
      });

      // Send response
      try {
        byte[] response = json.get(5, TimeUnit.SECONDS).getBytes();
        sendAndGetResponse(httpExchange, response);
      } catch (InterruptedException | ExecutionException | TimeoutException e) {
        respondError("Timeout when executing Semgrex query", httpExchange);
      }
    }
  }

  private static void sendAndGetResponse(HttpExchange httpExchange, byte[] response) throws IOException {
    if (response.length > 0) {
      httpExchange.getResponseHeaders().add("Content-Type", "text/json");
      httpExchange.getResponseHeaders().add("Content-Length", Integer.toString(response.length));
      httpExchange.sendResponseHeaders(HTTP_OK, response.length);
      httpExchange.getResponseBody().write(response);
      httpExchange.close();
    }
  }


  /**
   * Run the server.
   * This method registers the handlers, and initializes the HTTP server.
   */
  @Override
  public void run() {
    try {
      server = HttpServer.create(new InetSocketAddress(serverPort), 0); // 0 is the default 'backlog'
      server.createContext("/", new CoreNLPHandler(defaultProps));
      server.createContext("/tokensregex", new TokensRegexHandler());
      server.createContext("/semgrex", new SemgrexHandler());
      server.createContext("/corenlp-brat.js", new FileHandler("edu/stanford/nlp/pipeline/demo/corenlp-brat.js"));
      server.createContext("/corenlp-brat.cs", new FileHandler("edu/stanford/nlp/pipeline/demo/corenlp-brat.css"));
      server.createContext("/ping", new PingHandler());
      server.createContext("/shutdown", new ShutdownHandler());
      server.setExecutor(serverExecutor);
      server.start();
      log("StanfordCoreNLPServer listening at " + server.getAddress());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Help output
   */
  protected static void printHelp(PrintStream os) {
    os.println("Usage: StanfordCoreNLPServer [port=9000] [timeout=5]");
    os.println("port\t\t\t\t Which port to use");
    os.println("timeout\t\t\t\t How long to wait before timing out");
  }

  /**
   * The main method.
   * Read the command line arguments and run the server.
   *
   * @param args The command line arguments
   *
   * @throws IOException Thrown if we could not start / run the server.
   */
  public static void main(String[] args) throws IOException {
    int port = DEFAULT_PORT;
    int timeout = DEFAULT_TIMEOUT;

    Properties props = new Properties();
    if (args.length > 0) {
      props = StringUtils.argsToProperties(args);
      boolean hasH = props.containsKey("h");
      boolean hasHelp = props.containsKey("help");
      if (hasH || hasHelp) {
        printHelp(System.err);
        return;
      }
    }
    props.list(System.err);
    if(props.containsKey("port")) {
      port = Integer.parseInt(props.getProperty("port"));
    }
    if(props.containsKey("timeout")) {
      timeout = Integer.parseInt(props.getProperty("timeout"));
    }
    log("Starting server on port " + port + " with timeout of " + timeout + " milliseconds.");

    // Run the server
    StanfordCoreNLPServer server = new StanfordCoreNLPServer(port, timeout);
    server.run();
  }

}
