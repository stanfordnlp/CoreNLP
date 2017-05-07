package edu.stanford.nlp.pipeline;

import com.sun.net.httpserver.*;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.tokensregex.SequenceMatchResult;
import edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;

import javax.net.ssl.*;
import java.io.*;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static edu.stanford.nlp.util.logging.Redwood.Util.*;
import static java.net.HttpURLConnection.*;

/**
 * This class creates a server that runs a new Java annotator in each thread.
 *
 * @author Gabor Angeli
 * @author Arun Chaganty
 */
public class StanfordCoreNLPServer implements Runnable {

  protected HttpServer server;
  @ArgumentParser.Option(name="server_id", gloss="a name for this server")
  protected String serverID = null; // currently not used
  @ArgumentParser.Option(name="port", gloss="The port to run the server on")
  protected int serverPort = 9000;
  @ArgumentParser.Option(name="status_port", gloss="The port to serve the status check endpoints on. If different from the server port, this will run in a separate thread.")
  protected int statusPort = serverPort;
  @ArgumentParser.Option(name="timeout", gloss="The default timeout, in milliseconds")
  protected int timeoutMilliseconds = 15000;
  @ArgumentParser.Option(name="strict", gloss="If true, obey strict HTTP standards (e.g., with encoding)")
  protected boolean strict = false;
  @ArgumentParser.Option(name="quiet", gloss="If true, don't print to stdout")
  protected boolean quiet = false;
  @ArgumentParser.Option(name="ssl", gloss="If true, start the server with an [insecure!] SSL connection")
  protected boolean ssl = false;
  @ArgumentParser.Option(name="key", gloss="The *.jks key file to load, if -ssl is enabled. By default, it'll load the dummy key from the jar (but this is, of course, insecure!)")
  protected static String key = "edu/stanford/nlp/pipeline/corenlp.jks";
  @ArgumentParser.Option(name="username", gloss="The username component of a username/password basic auth credential")
  protected String username = null;
  @ArgumentParser.Option(name="password", gloss="The password component of a username/password basic auth credential")
  protected String password = null;
  @ArgumentParser.Option(name="annotators", gloss="The default annotators to run over a given sentence.")
  protected static String defaultAnnotators = "tokenize,ssplit,pos,lemma,ner,parse,depparse,mention,coref,natlog,openie,regexner,kbp";
  @ArgumentParser.Option(name="preload", gloss="Cache the following annotators on startup")
  protected static String preloadedAnnotators = "";
  @ArgumentParser.Option(name="serverProperties", gloss="Default properties file for server's StanfordCoreNLP instance")
  protected static String serverPropertiesPath = null;

  protected final String shutdownKey;

  public static int MAX_CHAR_LENGTH = 100000;
  public final Properties defaultProps;

  /**
   * The thread pool for the HTTP server.
   */
  private final ExecutorService serverExecutor;
  /**
   * To prevent grossly wasteful over-creation of pipeline objects, cache the last
   * few we created, until the garbage collector decides we can kill them.
   */
  private final WeakHashMap<Properties, StanfordCoreNLP> pipelineCache = new WeakHashMap<>();
  /**
   * An executor to time out CoreNLP execution with.
   */
  private final ExecutorService corenlpExecutor;

  /**
   * Create a new Stanford CoreNLP Server.
   * @param props A list of properties for the server (server_id ...)
   * @param port The port to host the server from.
   * @param timeout The timeout (in milliseconds) for each command.
   * @param strict If true, conform more strictly to the HTTP spec (e.g., for character encoding).
   * @throws IOException Thrown from the underlying socket implementation.
   */
  public StanfordCoreNLPServer(Properties props, int port, int timeout, boolean strict) throws IOException {
    this(props);
    this.serverPort = port;
    this.timeoutMilliseconds = timeout;
    this.strict = strict;
  }

  /**
   * Create a new Stanford CoreNLP Server.
   * @param port The port to host the server from.
   * @param timeout The timeout (in milliseconds) for each command.
   * @param strict If true, conform more strictly to the HTTP spec (e.g., for character encoding).
   * @throws IOException Thrown from the underlying socket implementation.
   */
  public StanfordCoreNLPServer(int port, int timeout, boolean strict) throws IOException {
    this(null, port, timeout, strict);
  }

  /**
   * Create a new Stanford CoreNLP Server, with the default parameters
   *
   * @throws IOException Thrown if we could not write the shutdown key to the a file.
   */
  public StanfordCoreNLPServer() throws IOException {
    this(null);
  }

  /**
   * Create a new Stanford CoreNLP Server with the default parameters and some
   *
   * pass in properties (server_id ...)
   *
   * @throws IOException Thrown if we could not write the shutdown key to the a file.
   */
  public StanfordCoreNLPServer(Properties props) throws IOException {
    // check if englishSR.ser.gz can be found (standard models jar doesn't have this)
    String defaultParserPath;
    ClassLoader classLoader = getClass().getClassLoader();
    URL srResource =
            classLoader.getResource("edu/stanford/nlp/models/srparser/englishSR.ser.gz");
    log("setting default constituency parser");
    if (srResource != null) {
      defaultParserPath = "edu/stanford/nlp/models/srparser/englishSR.ser.gz";
      log("using SR parser: edu/stanford/nlp/models/srparser/englishSR.ser.gz");
    } else {
      defaultParserPath = "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz";
      log("warning: cannot find edu/stanford/nlp/models/srparser/englishSR.ser.gz");
      log("using: edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz instead");
      log("to use shift reduce parser download English models jar from:");
      log("http://stanfordnlp.github.io/CoreNLP/download.html");
    }
    this.defaultProps = PropertiesUtils.asProperties(
        "annotators", defaultAnnotators,  // Run these annotators by default
        "mention.type", "dep",  // Use dependency trees with coref by default
        "coref.mode", "statistical",  // Use the new coref
        "coref.language", "en",  // We're English by default
        "inputFormat", "text",   // By default, treat the POST data like text
        "outputFormat", "json",  // By default, return in JSON -- this is a server, after all.
        "prettyPrint", "false",  // Don't bother pretty-printing
        "parse.model", defaultParserPath,  // SR scales linearly with sentence length. Good for a server!
        "parse.binaryTrees", "true",  // needed for the Sentiment annotator
        "openie.strip_entailments", "true");  // these are large to serialize, so ignore them

    // overwrite all default properties with provided server properties
    // for instance you might want to provide a default ner model
    if (serverPropertiesPath != null) {
      Properties serverProperties = StringUtils.argsToProperties(new String[]{"-props", serverPropertiesPath});
      PropertiesUtils.overWriteProperties(this.defaultProps, serverProperties);
    }

    this.serverExecutor = Executors.newFixedThreadPool(ArgumentParser.threads);
    this.corenlpExecutor = Executors.newFixedThreadPool(ArgumentParser.threads);

    // Generate and write a shutdown key, get optional server_id from passed in properties
    // this way if multiple servers running can shut them all down with different ids
    String shutdownKeyFileName;
    if (props != null && props.getProperty("server_id") != null)
      shutdownKeyFileName = "corenlp.shutdown."+props.getProperty("server_id");
    else
      shutdownKeyFileName = "corenlp.shutdown";
    String tmpDir = System.getProperty("java.io.tmpdir");
    File tmpFile = new File(tmpDir + File.separator + shutdownKeyFileName);
    tmpFile.deleteOnExit();
    if (tmpFile.exists()) {
      if (!tmpFile.delete()) {
        throw new IllegalStateException("Could not delete shutdown key file");
      }
    }
    this.shutdownKey = new BigInteger(130, new Random()).toString(32);
    IOUtils.writeStringToFile(shutdownKey, tmpFile.getPath(), "utf-8");
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
  private Annotation getDocument(Properties props, HttpExchange httpExchange) throws IOException, ClassNotFoundException {
    String inputFormat = props.getProperty("inputFormat", "text");
    String date = props.getProperty("date");
    switch (inputFormat) {
      case "text":
        // The default encoding by the HTTP standard is ISO-8859-1, but most
        // real users of CoreNLP would likely assume UTF-8 by default.
        String defaultEncoding = this.strict ? "ISO-8859-1" : "UTF-8";
        // Get the encoding
        Headers h = httpExchange.getRequestHeaders();
        String encoding;
        if (h.containsKey("Content-type")) {
          String[] charsetPair = Arrays.stream(h.getFirst("Content-type").split(";"))
              .map(x -> x.split("="))
              .filter(x -> x.length > 0 && "charset".equals(x[0]))
              .findFirst().orElse(new String[]{"charset", defaultEncoding});
          if (charsetPair.length == 2) {
            encoding = charsetPair[1];
          } else {
            encoding = defaultEncoding;
          }
        } else {
          encoding = defaultEncoding;
        }

        String text = IOUtils.slurpReader(IOUtils.encodedInputStreamReader(httpExchange.getRequestBody(), encoding));


        // Remove the \ and + characters that mess up the URL decoding.
        text = text.replaceAll("%(?![0-9a-fA-F]{2})", "%25");
        text = text.replaceAll("\\+", "%2B");
        text = URLDecoder.decode(text, encoding).trim();
        // Read the annotation
        Annotation annotation = new Annotation(text);
        // Set the date (if provided)
        if (date != null) {
          annotation.set(CoreAnnotations.DocDateAnnotation.class, date);
        }
        return annotation;
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
        AnnotatorPool pool = StanfordCoreNLP.constructAnnotatorPool(props, new AnnotatorImplementations());
        impl = new StanfordCoreNLP(props, pool);
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
    httpExchange.getResponseHeaders().add("Content-type", "text/plain");
    httpExchange.sendResponseHeaders(HTTP_INTERNAL_ERROR, response.length());
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
    httpExchange.getResponseHeaders().add("Content-type", "text/plain");
    httpExchange.sendResponseHeaders(HTTP_BAD_REQUEST, response.length());
    httpExchange.getResponseBody().write(response.getBytes());
    httpExchange.close();
  }


  /**
   * A helper function to respond to a request with an error stating that the user is not authorized
   * to make this request.
   *
   * @param httpExchange The exchange to send the error over.
   *
   * @throws IOException Thrown if the HttpExchange cannot communicate the error.
   */
  private static void respondUnauthorized(HttpExchange httpExchange) throws IOException {
    httpExchange.getResponseHeaders().add("Content-type", "application/javascript");
    byte[] content = "{\"message\": \"Unauthorized API request\"}".getBytes("utf-8");
    httpExchange.sendResponseHeaders(HTTP_UNAUTHORIZED, content.length);
    httpExchange.getResponseBody().write(content);
    httpExchange.close();
  }


  /**
   * A callback object that lets us hook into the result of an annotation request.
   */
  public static class FinishedRequest {
    public final Properties props;
    public final Annotation document;
    public final Optional<String> tokensregex;
    public final Optional<String> semgrex;

    public FinishedRequest(Properties props, Annotation document) {
      this.props = props;
      this.document = document;
      this.tokensregex = Optional.empty();
      this.semgrex = Optional.empty();
    }

    public FinishedRequest(Properties props, Annotation document, String tokensregex, String semgrex) {
      this.props = props;
      this.document = document;
      this.tokensregex = Optional.ofNullable(tokensregex);
      this.semgrex = Optional.ofNullable(semgrex);
    }
  }


  /**
   * A simple ping test. Responds with pong.
   */
  protected static class PingHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
      // Return a simple text message that says pong.
      httpExchange.getResponseHeaders().set("Content-type", "text/plain");
      String response = "pong\n";
      httpExchange.sendResponseHeaders(HTTP_OK, response.getBytes().length);
      httpExchange.getResponseBody().write(response.getBytes());
      httpExchange.close();
    }
  }


  /**
   * A handler to let the caller know if the server is alive AND ready to respond to requests.
   * The canonical use-case for this is for Kubernetes readiness checks.
   */
  protected static class ReadyHandler implements HttpHandler {
    /** If true, the server is runnning and ready for requets. */
    public final AtomicBoolean serverReady;
    /** The creation time of this handler. This is used to tell the caller how long we've been waiting for. */
    public final long startTime;

    /** The trivial constructor. */
    public ReadyHandler(AtomicBoolean serverReady) {
      this.serverReady = serverReady;
      this.startTime = System.currentTimeMillis();
    }

    /** {@inheritDoc} */
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
      // Return a simple text message that says pong.
      httpExchange.getResponseHeaders().set("Content-type", "text/plain");
      String response;
      int status;
      if (this.serverReady.get()) {
        response = "ready\n";
        status = HTTP_OK;
      } else {
        response = "server is not ready yet. uptime=" + Redwood.formatTimeDifference(System.currentTimeMillis() - this.startTime) + "\n";
        status = HTTP_UNAVAILABLE;
      }
      httpExchange.sendResponseHeaders(status, response.getBytes().length);
      httpExchange.getResponseBody().write(response.getBytes());
      httpExchange.close();
    }
  }


  /**
   * A handler to let the caller know if the server is alive,
   * but not necessarily ready to respond to requests.
   * The canonical use-case for this is for Kubernetes liveness checks.
   */
  protected static class LiveHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
      // Return a simple text message that says pong.
      httpExchange.getResponseHeaders().set("Content-type", "text/plain");
      String response = "live\n";
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
      httpExchange.getResponseHeaders().set("Content-type", "text/plain");
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
  public static class FileHandler implements HttpHandler {
    private final String content;
    private final String contentType;
    public FileHandler(String fileOrClasspath) throws IOException {
      this(fileOrClasspath, "text/html");
    }
    public FileHandler(String fileOrClasspath, String contentType) throws IOException {
      this.content = IOUtils.slurpReader(IOUtils.readerFromString(fileOrClasspath, "utf-8"));
      this.contentType = contentType + "; charset=utf-8";  // always encode in utf-8
    }
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
      httpExchange.getResponseHeaders().set("Content-type", this.contentType);
      ByteBuffer buffer = Charset.forName("UTF-8").encode(content);
      byte[] bytes = new byte[buffer.remaining()];
      buffer.get(bytes);
      httpExchange.sendResponseHeaders(HTTP_OK, bytes.length);
      httpExchange.getResponseBody().write(bytes);
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
     * An authenticator to determine if we can perform this API request.
     */
    private final Predicate<Properties> authenticator;

    /**
     * A callback to call when an annotation job has finished.
     */
    private final Consumer<FinishedRequest> callback;


    private final FileHandler homepage;

    /**
     * Create a handler for accepting annotation requests.
     * @param props The properties file to use as the default if none were sent by the client.
     */
    public CoreNLPHandler(Properties props, Predicate<Properties> authenticator,
                          Consumer<FinishedRequest> callback,
                          FileHandler homepage) {
      this.defaultProps = props;
      this.callback = callback;
      this.authenticator = authenticator;
      this.homepage = homepage;
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
          return "application/json";
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
      httpExchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
      httpExchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
      httpExchange.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");
      httpExchange.getResponseHeaders().add("Access-Control-Allow-Credentials-Header", "*");

      // Get sentence.
      Properties props;
      Annotation ann;
      StanfordCoreNLP.OutputFormat of;
      try {
        props = getProperties(httpExchange);

        if ("GET".equalsIgnoreCase(httpExchange.getRequestMethod())) {
          // Handle direct browser connections (i.e., not a POST request).
          homepage.handle(httpExchange);
          return;
        } else {
          // Handle API request
          if (authenticator != null && !authenticator.test(props)) {
            respondUnauthorized(httpExchange);
            return;
          }
          log("[" + httpExchange.getRemoteAddress() + "] API call w/annotators " + props.getProperty("annotators", "<unknown>"));
          ann = getDocument(props, httpExchange);
          of = StanfordCoreNLP.OutputFormat.valueOf(props.getProperty("outputFormat", "json").toUpperCase());
          String text = ann.get(CoreAnnotations.TextAnnotation.class).replace('\n', ' ');
          if (!quiet) {
            System.out.println(text);
          }
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
          if (timeoutMilliseconds > 15000) {
            // If two conditions:
            //   (1) The server is running on corenlp.run (i.e., corenlp.stanford.edu)
            //   (2) The request is not coming from a *.stanford.edu" email address
            // Then force the timeout to be 15 seconds
            if ("corenlp.stanford.edu".equals(InetAddress.getLocalHost().getHostName()) &&
                !httpExchange.getRemoteAddress().getHostName().toLowerCase().endsWith("stanford.edu")) {
              timeoutMilliseconds = 15000;
            }
          }
          completedAnnotation = completedAnnotationFuture.get(timeoutMilliseconds, TimeUnit.MILLISECONDS);
        } catch (NumberFormatException e) {
          completedAnnotation = completedAnnotationFuture.get(StanfordCoreNLPServer.this.timeoutMilliseconds, TimeUnit.MILLISECONDS);
        }
        completedAnnotationFuture = null;  // No longer any need for the future

        // Get output
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        AnnotationOutputter.Options options = AnnotationOutputter.getOptions(pipeline);
        StanfordCoreNLP.createOutputter(props, options).accept(completedAnnotation, os);
        os.close();
        byte[] response = os.toByteArray();

        String contentType = getContentType(props, of);
        if (contentType.equals("application/json") || contentType.startsWith("text/")) {
          contentType += ";charset=" + options.encoding;
        }
        httpExchange.getResponseHeaders().add("Content-type", contentType);
        httpExchange.getResponseHeaders().add("Content-length", Integer.toString(response.length));
        httpExchange.sendResponseHeaders(HTTP_OK, response.length);
        httpExchange.getResponseBody().write(response);
        httpExchange.close();
        if (completedAnnotation != null && props.getProperty("annotators") != null && !"".equals(props.getProperty("annotators"))) {
          callback.accept(new FinishedRequest(props, completedAnnotation));
        }
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
      Map<String, String> urlParams = getURLParams(httpExchange.getRequestURI());

      // Load the default properties
      Properties props = new Properties();
      defaultProps.entrySet().forEach(entry -> props.setProperty(entry.getKey().toString(), entry.getValue().toString()));

      // Add GET parameters as properties
      urlParams.entrySet().stream()
          .filter(entry ->
              !"properties".equalsIgnoreCase(entry.getKey()) &&
                  !"props".equalsIgnoreCase(entry.getKey()))
          .forEach(entry -> props.setProperty(entry.getKey(), entry.getValue()));

      // Try to get more properties from query string.
      // (get the properties from the URL params)
      Map<String, String> urlProperties = new HashMap<>();
      if (urlParams.containsKey("properties")) {
        urlProperties = StringUtils.decodeMap(URLDecoder.decode(urlParams.get("properties"), "UTF-8"));
      } else if (urlParams.containsKey("props")) {
        urlProperties = StringUtils.decodeMap(URLDecoder.decode(urlParams.get("props"), "UTF-8"));
      }

      // check to see if a specific language was set, use language specific properties
      String language = urlParams.getOrDefault("pipelineLanguage", urlProperties.getOrDefault("pipelineLanguage", "default"));
      if (language != null && !"default".equals(language)) {
        String languagePropertiesFile = LanguageInfo.getLanguagePropertiesFile(language);
        if (languagePropertiesFile != null) {
          Properties languageSpecificProperties = new Properties();
          try {
            languageSpecificProperties.load(
                    IOUtils.getInputStreamFromURLOrClasspathOrFileSystem(languagePropertiesFile));
            PropertiesUtils.overWriteProperties(props,languageSpecificProperties);
          } catch (IOException e) {
            err("Failure to load language specific properties.");
          }
        } else {
          try {
            respondError("Invalid language: '" + language + "'", httpExchange);
          } catch (IOException e) { warn(e); }
          return new Properties();
        }
      }

      // (tweak the default properties a bit)
      if (!props.containsKey("mention.type")) {
        // Set coref head to use dependencies
        props.setProperty("mention.type", "dep");
        if (urlProperties.containsKey("annotators") && urlProperties.get("annotators") != null &&
            ArrayUtils.contains(urlProperties.get("annotators").split(","), "parse")) {
          // (case: the properties have a parse annotator --
          //        we don't have to use the dependency mention finder)
          props.remove("mention.type");
        }
      }

      // (add new properties on top of the default properties)
      urlProperties.entrySet()
          .forEach(entry -> props.setProperty(entry.getKey(), entry.getValue()));

      // Get the annotators
      String annotators = props.getProperty("annotators");
      if (PropertiesUtils.getBool(props, "enforceRequirements", true)) {
        annotators = StanfordCoreNLP.ensurePrerequisiteAnnotators(props.getProperty("annotators").split("[, \t]+"), props);
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

    /**
     * A callback to call when an annotation job has finished.
     */
    private final Consumer<FinishedRequest> callback;

    /**
     * An authenticator to determine if we can perform this API request.
     */
    private final Predicate<Properties> authenticator;

    /**
     * Create a new TokensRegex Handler.
     * @param callback The callback to call when annotation has finished.
     */
    public TokensRegexHandler(Predicate<Properties> authenticator, Consumer<FinishedRequest> callback) {
      this.callback = callback;
      this.authenticator = authenticator;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
      // Set common response headers
      httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
      httpExchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
      httpExchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
      httpExchange.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");
      httpExchange.getResponseHeaders().add("Access-Control-Allow-Credentials-Header", "*");
      // Some common fields
      Properties props = PropertiesUtils.asProperties("annotators", "tokenize,ssplit,pos,lemma,ner");
      if (authenticator != null && !authenticator.test(props)) {
        respondUnauthorized(httpExchange);
        return;
      }
      Map<String, String> params = getURLParams(httpExchange.getRequestURI());

      Future<Pair<String, Annotation>> future = corenlpExecutor.submit(() -> {
        try {
          // Get the document
          Annotation doc = getDocument(props, httpExchange);
          if (!doc.containsKey(CoreAnnotations.SentencesAnnotation.class)) {
            StanfordCoreNLP pipeline = mkStanfordCoreNLP(props);
            pipeline.annotate(doc);
          }

          // Construct the matcher
          // (get the pattern)
          if (!params.containsKey("pattern")) {
            respondBadInput("Missing required parameter 'pattern'", httpExchange);
            return new Pair<>("", null);
          }
          String pattern = params.get("pattern");
          // (get whether to filter / find)
          String filterStr = params.getOrDefault("filter", "false");
          final boolean filter = filterStr.trim().isEmpty() || "true".equalsIgnoreCase(filterStr.toLowerCase());
          // (create the matcher)
          final TokenSequencePattern regex = TokenSequencePattern.compile(pattern);

          // Run TokensRegex
          return new Pair<>(JSONOutputter.JSONWriter.objectToJSON((docWriter) -> {
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
          }), doc);
        } catch (Exception e) {
          e.printStackTrace();
          try {
            respondError(e.getClass().getName() + ": " + e.getMessage(), httpExchange);
          } catch (IOException ignored) {
          }
        }
        return new Pair<>("", null);
      });

      // Send response
      try {
        Pair<String, Annotation> response = future.get(5, TimeUnit.SECONDS);
        byte[] content = response.first.getBytes();
        Annotation completedAnnotation = response.second;
        sendAndGetResponse(httpExchange, content);
        if (completedAnnotation != null && props.getProperty("annotators") != null && !"".equals(props.getProperty("annotators"))) {
          callback.accept(new FinishedRequest(props, completedAnnotation, params.get("pattern"), null));
        }
      } catch (InterruptedException | ExecutionException | TimeoutException e) {
        respondError("Timeout when executing TokensRegex query", httpExchange);
      }
    }
  }



  /**
   * A handler for matching semgrex patterns against dependency trees.
   */
  protected class SemgrexHandler implements HttpHandler {

    /**
     * A callback to call when an annotation job has finished.
     */
    private final Consumer<FinishedRequest> callback;

    /**
     * An authenticator to determine if we can perform this API request.
     */
    private final Predicate<Properties> authenticator;

    /**
     * Create a new Semgrex Handler.
     * @param callback The callback to call when annotation has finished.
     */
    public SemgrexHandler(Predicate<Properties> authenticator, Consumer<FinishedRequest> callback) {
      this.callback = callback;
      this.authenticator = authenticator;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {

      // Set common response headers
      httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
      httpExchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
      httpExchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
      httpExchange.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");
      httpExchange.getResponseHeaders().add("Access-Control-Allow-Credentials-Header", "*");

      // Some common properties
      Properties props = PropertiesUtils.asProperties("annotators", "tokenize,ssplit,pos,lemma,ner,depparse");
      if (authenticator != null && !authenticator.test(props)) {
        respondUnauthorized(httpExchange);
        return;
      }
      Map<String, String> params = getURLParams(httpExchange.getRequestURI());

      Future<Pair<String, Annotation>> response = corenlpExecutor.submit(() -> {
        try {
          // Get the document
          Annotation doc = getDocument(props, httpExchange);
          if (!doc.containsKey(CoreAnnotations.SentencesAnnotation.class)) {
            StanfordCoreNLP pipeline = mkStanfordCoreNLP(props);
            pipeline.annotate(doc);
          }

          // Construct the matcher
          // (get the pattern)
          if (!params.containsKey("pattern")) {
            respondBadInput("Missing required parameter 'pattern'", httpExchange);
            return Pair.makePair("", null);
          }
          String pattern = params.get("pattern");
          // (get whether to filter / find)
          String filterStr = params.getOrDefault("filter", "false");
          final boolean filter = filterStr.trim().isEmpty() || "true".equalsIgnoreCase(filterStr.toLowerCase());
          // (create the matcher)
          final SemgrexPattern regex = SemgrexPattern.compile(pattern);

          // Run TokensRegex
          return Pair.makePair(JSONOutputter.JSONWriter.objectToJSON((docWriter) -> {
            if (filter) {
              // Case: just filter sentences
              docWriter.set("sentences", doc.get(CoreAnnotations.SentencesAnnotation.class).stream().map(sentence ->
                      regex.matcher(sentence.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class)).matches()
              ).collect(Collectors.toList()));
            } else {
              // Case: find matches
              docWriter.set("sentences", doc.get(CoreAnnotations.SentencesAnnotation.class).stream().map(sentence -> (Consumer<JSONOutputter.Writer>) (JSONOutputter.Writer sentWriter) -> {
                SemgrexMatcher matcher = regex.matcher(sentence.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class));
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
          }), doc);
        } catch (Exception e) {
          e.printStackTrace();
          try {
            respondError(e.getClass().getName() + ": " + e.getMessage(), httpExchange);
          } catch (IOException ignored) {
          }
        }
        return Pair.makePair("", null);
      });

      // Send response
      try {
        Pair<String, Annotation> pair = response.get(5, TimeUnit.SECONDS);
        Annotation completedAnnotation = pair.second;
        byte[] content = pair.first.getBytes();
        sendAndGetResponse(httpExchange, content);
        if (completedAnnotation != null && props.getProperty("annotators") != null && !"".equals(props.getProperty("annotators"))) {
          callback.accept(new FinishedRequest(props, completedAnnotation, params.get("pattern"), null));
        }
      } catch (InterruptedException | ExecutionException | TimeoutException e) {
        respondError("Timeout when executing Semgrex query", httpExchange);
      }
    }
  }

  /**
   * A handler for matching tregrex patterns against dependency trees.
   */
  protected class TregexHandler implements HttpHandler {

    /**
     * A callback to call when an annotation job has finished.
     */
    private final Consumer<FinishedRequest> callback;

    /**
     * An authenticator to determine if we can perform this API request.
     */
    private final Predicate<Properties> authenticator;

    /**
     * Create a new Tregex Handler.
     * @param callback The callback to call when annotation has finished.
     */
    public TregexHandler(Predicate<Properties> authenticator, Consumer<FinishedRequest> callback) {
      this.callback = callback;
      this.authenticator = authenticator;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {

      // Set common response headers
      httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
      httpExchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
      httpExchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
      httpExchange.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");
      httpExchange.getResponseHeaders().add("Access-Control-Allow-Credentials-Header", "*");

      // Some common properties
      Properties props = PropertiesUtils.asProperties("annotators", "tokenize,ssplit,parse");
      if (authenticator != null && ! authenticator.test(props)) {
        respondUnauthorized(httpExchange);
        return;
      }
      Map<String, String> params = getURLParams(httpExchange.getRequestURI());

      Future<Pair<String, Annotation>> response = corenlpExecutor.submit(() -> {
        try {
          // Get the document
          Annotation doc = getDocument(props, httpExchange);
          if ( ! doc.containsKey(CoreAnnotations.SentencesAnnotation.class)) {
            StanfordCoreNLP pipeline = mkStanfordCoreNLP(props);
            pipeline.annotate(doc);
          }

          // Construct the matcher
          // (get the pattern)
          if ( ! params.containsKey("pattern")) {
            respondBadInput("Missing required parameter 'pattern'", httpExchange);
            return Pair.makePair("", null);
          }
          String pattern = params.get("pattern");

          // (create the matcher)
          TregexPattern p = TregexPattern.compile(pattern);

          // Run Tregex
          return Pair.makePair(JSONOutputter.JSONWriter.objectToJSON((docWriter) ->
            docWriter.set("sentences", doc.get(CoreAnnotations.SentencesAnnotation.class).stream().map(sentence -> (Consumer<JSONOutputter.Writer>) (JSONOutputter.Writer sentWriter) -> {
                Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
                //sentWriter.set("tree", tree.pennString());
                TregexMatcher matcher = p.matcher(tree);

                int i = 0;
                while (matcher.find()) {
                  sentWriter.set(Integer.toString(i++), (Consumer<JSONOutputter.Writer>) (JSONOutputter.Writer matchWriter) -> {
                    matchWriter.set("match", matcher.getMatch().pennString());
                    matchWriter.set("namedNodes", matcher.getNodeNames().stream().map(nodeName -> (Consumer<JSONOutputter.Writer>) (JSONOutputter.Writer namedNodeWriter) ->
                      namedNodeWriter.set(nodeName, matcher.getNode(nodeName).pennString())
                    ));
                  });

                }
            }))
          ), doc);
        } catch (Exception e) {
          e.printStackTrace();
          try {
            respondError(e.getClass().getName() + ": " + e.getMessage(), httpExchange);
          } catch (IOException ignored) {
          }
        }
        return Pair.makePair("", null);
      });

      // Send response
      try {
        Pair<String, Annotation> pair = response.get(5, TimeUnit.SECONDS);
        Annotation completedAnnotation = pair.second;
        byte[] content = pair.first.getBytes();
        sendAndGetResponse(httpExchange, content);
        if (completedAnnotation != null && ! StringUtils.isNullOrEmpty(props.getProperty("annotators"))) {
          callback.accept(new FinishedRequest(props, completedAnnotation, params.get("pattern"), null));
        }
      } catch (InterruptedException | ExecutionException | TimeoutException e) {
        respondError("Timeout when executing Tregex query", httpExchange);
      }
    }
  }

  private static void sendAndGetResponse(HttpExchange httpExchange, byte[] response) throws IOException {
    if (response.length > 0) {
      httpExchange.getResponseHeaders().add("Content-type", "application/json");
      httpExchange.getResponseHeaders().add("Content-length", Integer.toString(response.length));
      httpExchange.sendResponseHeaders(HTTP_OK, response.length);
      httpExchange.getResponseBody().write(response);
      httpExchange.close();
    }
  }


  private static HttpsServer addSSLContext(HttpsServer server) {
    log("Adding SSL context to server; key=" + StanfordCoreNLPServer.key);
    try {
      KeyStore ks = KeyStore.getInstance("JKS");
      if (StanfordCoreNLPServer.key != null && IOUtils.existsInClasspathOrFileSystem(StanfordCoreNLPServer.key)) {
        ks.load(IOUtils.getInputStreamFromURLOrClasspathOrFileSystem(key), "corenlp".toCharArray());
      } else {
        throw new IllegalArgumentException("Could not find SSL keystore at " + StanfordCoreNLPServer.key);
      }
      KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
      kmf.init(ks, "corenlp".toCharArray());
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(kmf.getKeyManagers(), null, null);

      // Add SSL support to the server
      server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
        public void configure(HttpsParameters params) {
          SSLContext context = getSSLContext();
          SSLEngine engine = context.createSSLEngine();
          params.setNeedClientAuth(false);
          params.setCipherSuites(engine.getEnabledCipherSuites());
          params.setProtocols(engine.getEnabledProtocols());
          params.setSSLParameters(context.getDefaultSSLParameters());
        }
      });

      // Return
      return server;
    } catch (CertificateException | IOException | KeyStoreException | NoSuchAlgorithmException | KeyManagementException | UnrecoverableKeyException e) {
      throw new RuntimeException(e);
    }
  }


  /**
   * If we have a separate liveness port, start a server on a separate thread pool whose only
   * job is to watch for when the CoreNLP server becomes ready.
   * This will also immediately signal liveness.
   *
   * @param live The boolean to track when CoreNLP has initialized and the server is ready
   *             to serve requests.
   */
  private void livenessServer(AtomicBoolean live) {
    if (this.serverPort != this.statusPort) {
      try {
        // Create the server
        if (this.ssl) {
          server = addSSLContext(HttpsServer.create(new InetSocketAddress(statusPort), 0)); // 0 is the default 'backlog'
        } else {
          server = HttpServer.create(new InetSocketAddress(statusPort), 0); // 0 is the default 'backlog'
        }
        // Add the two status endpoints
        withAuth(server.createContext("/live", new LiveHandler()), Optional.empty());
        withAuth(server.createContext("/ready", new ReadyHandler(live)), Optional.empty());
        // Start the server
        server.start();
        // Server started
        log("Liveness server started at " + server.getAddress());
      } catch (IOException e) {
        err("Could not start liveness server. This will probably result in very bad things happening soon.", e);
      }
    }
  }


  /**
   * Returns the implementing Http server.
   */
  public Optional<HttpServer> getServer() {
    return Optional.ofNullable(server);
  }



  /** @see StanfordCoreNLPServer#run(Optional, Predicate, Consumer, StanfordCoreNLPServer.FileHandler, boolean, AtomicBoolean) */
  @Override
  public void run() {
    // Set the static page handler
    try {
      AtomicBoolean live = new AtomicBoolean(false);
      this.livenessServer(live);
      FileHandler homepage = new FileHandler("edu/stanford/nlp/pipeline/demo/corenlp-brat.html");
      run(Optional.empty(), req -> true, obj -> {}, homepage, false, live);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }


  /**
   * Enable authentication for this endpoint
   *
   * @param context The context to enable authentication for.
   * @param credentials The optional credentials to enforce. This is a (key,value) pair
   */
  private void withAuth(HttpContext context, Optional<Pair<String,String>> credentials) {
    credentials.ifPresent(c -> context.setAuthenticator(new BasicAuthenticator("corenlp") {
      @Override
      public boolean checkCredentials(String user, String pwd) {
        return user.equals(c.first) && pwd.equals(c.second);
      }
    }));
  }


  /**
   * Run the server.
   * This method registers the handlers, and initializes the HTTP server.
   */
  public void run(Optional<Pair<String,String>> basicAuth,
                  Predicate<Properties> authenticator,
                  Consumer<FinishedRequest> callback,
                  FileHandler homepage,
                  boolean https,
                  AtomicBoolean live) {
    try {
      if (https) {
        server = addSSLContext(HttpsServer.create(new InetSocketAddress(serverPort), 0)); // 0 is the default 'backlog'
      } else {
        server = HttpServer.create(new InetSocketAddress(serverPort), 0); // 0 is the default 'backlog'
      }
      withAuth(server.createContext("/", new CoreNLPHandler(defaultProps, authenticator, callback, homepage)), basicAuth);
      withAuth(server.createContext("/tokensregex", new TokensRegexHandler(authenticator, callback)), basicAuth);
      withAuth(server.createContext("/semgrex", new SemgrexHandler(authenticator, callback)), basicAuth);
      withAuth(server.createContext("/tregex", new TregexHandler(authenticator, callback)), basicAuth);
      withAuth(server.createContext("/corenlp-brat.js", new FileHandler("edu/stanford/nlp/pipeline/demo/corenlp-brat.js", "application/javascript")), basicAuth);
      withAuth(server.createContext("/corenlp-brat.cs", new FileHandler("edu/stanford/nlp/pipeline/demo/corenlp-brat.css", "text/css")), basicAuth);
      withAuth(server.createContext("/corenlp-parseviewer.js", new FileHandler("edu/stanford/nlp/pipeline/demo/corenlp-parseviewer.js", "application/javascript")), basicAuth);
      withAuth(server.createContext("/ping", new PingHandler()), Optional.empty());
      withAuth(server.createContext("/shutdown", new ShutdownHandler()), basicAuth);
      if (this.serverPort == this.statusPort) {
        withAuth(server.createContext("/live", new LiveHandler()), Optional.empty());
        withAuth(server.createContext("/ready", new ReadyHandler(live)), Optional.empty());

      }
      server.setExecutor(serverExecutor);
      server.start();
      live.set(true);
      log("StanfordCoreNLPServer listening at " + server.getAddress());
    } catch (IOException e) {
      e.printStackTrace();
    }
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
    // Add a bit of logging
    log("--- " + StanfordCoreNLPServer.class.getSimpleName() + "#main() called ---");
    String build = System.getenv("BUILD");
    if (build != null) {
      log("    Build: " + build);
    }
    Runtime.getRuntime().addShutdownHook(new Thread(() -> log("CoreNLP Server is shutting down.")));

    // Fill arguments
    ArgumentParser.fillOptions(StanfordCoreNLPServer.class, args);
    // get server properties from command line, right now only property used is server_id
    Properties serverProperties = StringUtils.argsToProperties(args);
    StanfordCoreNLPServer server = new StanfordCoreNLPServer(serverProperties);  // must come after filling global options
    ArgumentParser.fillOptions(server, args);
    log("    Threads: " + ArgumentParser.threads);

    // Start the liveness server
    AtomicBoolean live = new AtomicBoolean(false);
    server.livenessServer(live);

    // Create the homepage
    FileHandler homepage;
    try {
      homepage = new FileHandler("edu/stanford/nlp/pipeline/demo/corenlp-brat.html");
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }

    // Pre-load the models
    if (StanfordCoreNLPServer.preloadedAnnotators != null && !"".equals(StanfordCoreNLPServer.preloadedAnnotators.trim())) {
      Properties props = new Properties();
      server.defaultProps.entrySet().forEach(entry -> props.setProperty(entry.getKey().toString(), entry.getValue().toString()));
      props.setProperty("annotators", StanfordCoreNLPServer.preloadedAnnotators);
      try {
        new StanfordCoreNLP(props);
      } catch (Throwable ignored) {
        err("Could not pre-load annotators in server; encountered exception:");
        ignored.printStackTrace();
      }
    }

    // Credentials
    Optional<Pair<String, String>> credentials = Optional.empty();
    if (server.username != null && server.password != null) {
      credentials = Optional.of(Pair.makePair(server.username, server.password));
    }

    // Run the server
    log("Starting server...");
    if (server.ssl) {
      server.run(credentials, req -> true, res -> {}, homepage, true, live);
    } else {
      server.run(credentials, req -> true, res -> {}, homepage, false, live);

    }
  }

}
