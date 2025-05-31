package edu.stanford.nlp.pipeline;

import com.sun.net.httpserver.*;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.tokensregex.SequenceMatchResult;
import edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.scenegraph.RuleBasedParser;
import edu.stanford.nlp.scenegraph.SceneGraph;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.semgrex.ProcessSemgrexRequest;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.lang.ref.SoftReference;
import java.math.BigInteger;
import java.net.*;
import javax.net.ssl.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static edu.stanford.nlp.pipeline.StanfordCoreNLP.CUSTOM_ANNOTATOR_PREFIX;
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
  @SuppressWarnings("unused")
  @ArgumentParser.Option(name="server_id", gloss="a name for this server")
  protected String serverID; // = null; // currently not used
  @ArgumentParser.Option(name="port", gloss="The port to run the server on")
  protected int serverPort = 9000;
  @ArgumentParser.Option(name="status_port", gloss="The port to serve the status check endpoints on. If different from the server port, this will run in a separate thread.")
  protected int statusPort = serverPort;
  @ArgumentParser.Option(name="uriContext", gloss="The URI context")
  protected String uriContext = "";
  @ArgumentParser.Option(name="timeout", gloss="The default timeout, in milliseconds")
  protected int timeoutMilliseconds = 15000;
  @ArgumentParser.Option(name="strict", gloss="If true, obey strict HTTP standards (e.g., with encoding)")
  protected boolean strict = false;
  @ArgumentParser.Option(name="quiet", gloss="If true, don't print to stdout and don't log every API POST")
  protected boolean quiet = false;
  @ArgumentParser.Option(name="ssl", gloss="If true, start the server with an [insecure!] SSL connection")
  protected boolean ssl = false;
  @ArgumentParser.Option(name="key", gloss="The *.jks key file to load, if -ssl is enabled. By default, it'll load the dummy key from the jar (but this is, of course, insecure!)")
  protected static String key = "edu/stanford/nlp/pipeline/corenlp.jks";
  @ArgumentParser.Option(name="username", gloss="The username component of a username/password basic auth credential")
  protected String username = null;
  @ArgumentParser.Option(name="password", gloss="The password component of a username/password basic auth credential")
  protected String password = null;
  @ArgumentParser.Option(name="preload", gloss="Cache all default annotators (if no list provided), or optionally " +
      "provide comma separated list of annotators to preload (e.g. tokenize,ssplit,pos)")
  protected static String preloadedAnnotators = null;
  @ArgumentParser.Option(name="serverProperties", gloss="Default properties file for server's StanfordCoreNLP instance")
  protected static String serverPropertiesPath = null;
  @ArgumentParser.Option(name="maxCharLength", gloss="Max length string that will be processed (non-positive means no limit)")
  protected static int maxCharLength = 100000;
  @ArgumentParser.Option(name="blockList", gloss="A file containing subnets that should be forbidden from accessing the server. Each line is a subnet. They are specified as an IPv4 address followed by a slash followed by how many leading bits to maintain as the subnet mask. E.g., '54.240.225.0/24'.")
  protected static String blockList = null;
  @ArgumentParser.Option(name="stanford", gloss="If true, do special options (domain blockList, timeout modifications) for public Stanford server")
  protected boolean stanford = false;
  @ArgumentParser.Option(name="srparser", gloss="If true, use the srparser by default if possible.  Should save speed & memory on large queries")
  protected boolean srparser = false;

  /** List of server specific properties **/
  private static final List<String> serverSpecificProperties = ArgumentParser.listOptions(StanfordCoreNLPServer.class);

  private final String shutdownKey;

  private final Properties serverIOProps;

  private final Properties defaultProps;

  /**
   * The thread pool for the HTTP server.
   */
  private final ExecutorService serverExecutor;

  /**
   * To prevent grossly wasteful over-creation of pipeline objects, cache the last
   *  one we created.
   */
  private SoftReference<Pair<String, StanfordCoreNLP>> lastPipeline = new SoftReference<>(null);

  private RuleBasedParser sceneParser = null;

  /**
   * An executor to time out CoreNLP execution with.
   */
  private final ExecutorService corenlpExecutor;


  /**
   * A list of blocked subnets -- these cannot call the server.
   */
  private final List<Pair<Inet4Address, Integer>> blockListSubnets;


  /**
   * Create a new Stanford CoreNLP Server.
   *
   * @param props A list of properties for the server (server_id, ...)
   * @param port The port to host the server from.
   * @param timeout The timeout (in milliseconds) for each command.
   * @param strict If true, conform more strictly to the HTTP spec (e.g., for character encoding).
   * @throws IOException Thrown from the underlying socket implementation.
   */
  public StanfordCoreNLPServer(Properties props, int port, int timeout, boolean strict) throws IOException {
    this(props);
    this.serverPort = port;
    if (props != null && !props.containsKey("status_port")) {
      this.statusPort = port;
    }
    this.timeoutMilliseconds = timeout;
    this.strict = strict;
  }

  /**
   * Create a new Stanford CoreNLP Server.
   *
   * @param port The port to host the server from.
   * @param timeout The timeout (in milliseconds) for each command.
   * @param strict If true, conform more strictly to the HTTP spec (e.g., for character encoding).
   * @throws IOException Thrown from the underlying socket implementation.
   */
  public StanfordCoreNLPServer(int port, int timeout, boolean strict) throws IOException {
    this(null, port, timeout, strict);
  }

  /**
   * Create a new Stanford CoreNLP Server, with the default parameters.
   *
   * @throws IOException Thrown if we could not write the shutdown key to a file.
   */
  public StanfordCoreNLPServer() throws IOException {
    this(null);
  }

  /**
   * Create a new Stanford CoreNLP Server with the default parameters and
   * pass in properties (server_id, ...).
   *
   * @throws IOException Thrown if we could not write the shutdown key to a file.
   */
  public StanfordCoreNLPServer(Properties props) throws IOException {
    // set up default IO properties
    this.serverIOProps = new Properties();
    this.serverIOProps.setProperty("inputFormat", "text");
    this.serverIOProps.setProperty("outputFormat", "json");
    this.serverIOProps.setProperty("prettyPrint", "false");

    // set up default properties
    this.defaultProps = new Properties();
    this.defaultProps.putAll(this.serverIOProps);

    // overwrite with any properties provided by a props file
    if (serverPropertiesPath != null) {
      this.defaultProps.putAll(StringUtils.argsToProperties("-props", serverPropertiesPath));
    }

    // extract pipeline specific properties from command line and overwrite server and file provided properties
    Properties pipelinePropsFromCL = new Properties();
    if (props != null) {
      for (String key : props.stringPropertyNames()) {
        if (!serverSpecificProperties.contains(key)) {
          pipelinePropsFromCL.setProperty(key, props.getProperty(key));
        }
      }
    }
    PropertiesUtils.overWriteProperties(this.defaultProps, pipelinePropsFromCL);

    // log server's default properties
    TreeSet<String> defaultPropertyKeys = new TreeSet<>(this.defaultProps.stringPropertyNames());
    log("Server default properties:\n\t\t\t(Note: unspecified annotator properties are English defaults)\n" +
            defaultPropertyKeys.stream().map(
                k -> String.format("\t\t\t%s = %s", k, this.defaultProps.get(k))).collect(Collectors.joining("\n")));

    this.serverExecutor = Executors.newFixedThreadPool(ArgumentParser.threads);
    this.corenlpExecutor = Executors.newFixedThreadPool(ArgumentParser.threads);

    // Generate and write a shutdown key, get optional server_id from passed in properties
    // this way if multiple servers running can shut them all down with different ids
    String shutdownKeyFileName;
    if (props != null && props.getProperty("server_id") != null) {
      shutdownKeyFileName = "corenlp.shutdown." + props.getProperty("server_id");
    } else {
      shutdownKeyFileName = "corenlp.shutdown";
    }
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
    // set status port
    if (props != null && props.containsKey("status_port")) {
      this.statusPort = Integer.parseInt(props.getProperty("status_port"));
    } else if (props != null && props.containsKey("port")) {
      this.statusPort = Integer.parseInt(props.getProperty("port"));
    }
    // parse blockList
    if (blockList == null) {
      this.blockListSubnets = Collections.emptyList();
    } else {
      this.blockListSubnets = new ArrayList<>();
      for (String subnet : IOUtils.readLines(blockList)) {
        try {
          this.blockListSubnets.add(parseSubnet(subnet));
        } catch (IllegalArgumentException e) {
          warn("Could not parse subnet: " + subnet);
        }
      }
    }
  }

  /**
   * Parse the URL parameters into a map of (key, value) pairs. <br>
   * https://codereview.stackexchange.com/questions/175332/splitting-url-query-string-to-key-value-pairs
   *
   * @param uri The URL that was requested.
   *
   * @return A map of (key, value) pairs corresponding to the request parameters.
   *
   * @throws IllegalStateException Thrown if we could not decode the URL with utf8.
   */
  private static Map<String, String> getURLParams(URI uri) {
    String query = uri.getRawQuery();
    if (query != null) {
      try {
        Map<String, String> params = new HashMap<>();
        for (String param : query.split("&")) {
          String[] keyValue = param.split("=", 2);
          String key = URLDecoder.decode(keyValue[0], "UTF-8");
          String value = keyValue.length > 1 ? URLDecoder.decode(keyValue[1], "UTF-8") : "";
          if (!key.isEmpty()) {
            params.put(key, value);
          }
        }
        return params;
      } catch (UnsupportedEncodingException e) {
        throw new IllegalStateException(e); // Cannot happen with UTF-8 encoding.
      }
    } else {
      return Collections.emptyMap();
    }
  }

  // TODO(AngledLuffa): this must be a constant somewhere, but I couldn't find it
  static final String URL_ENCODED = "application/x-www-form-urlencoded";

  /**
   * Reads the POST contents of the request and parses it into an Annotation object, ready to be annotated.
   * This method can also read a serialized document, if the input format is set to be serialized.
   *
   * The POST contents will be treated as UTF-8 unless the strict property is set to true, in which case they will
   * be treated as ISO-8859-1. They should be application/x-www-form-urlencoded, and decoding will be done via the
   * java.net.URLDecoder.decode(String, String) function.  It is also allowed to send in text/plain requests,
   * which will not be decoded.  In fact, nothing other than x-www-form-urlencoded will be decoded.
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
    final String inputFormat = props.getProperty("inputFormat", "text");
    String date = props.getProperty("date");
    switch (inputFormat) {
      case "text":
        Headers headers = httpExchange.getRequestHeaders();
        // the original default behavior of the server was to
        // unescape, so let's assume by default that the input text is
        // escaped.  if the Content-type is set to text we will know
        // we shouldn't unescape after all
        final String contentType = getContentType(headers);
        // Get the encoding
        final String encoding = getEncoding(headers);

        String text = IOUtils.slurpReader(IOUtils.encodedInputStreamReader(httpExchange.getRequestBody(), encoding));
        if (contentType.equals(URL_ENCODED)) {
          try {
            text = URLDecoder.decode(text, encoding);
          } catch (IllegalArgumentException e) {
            // ignore decoding errors so that libraries which don't specify a content type might not fail
          }
        }
        // We use to trim. But now we don't. It seems like doing that is illegitimate. text = text.trim();

        // Read the annotation
        Annotation annotation = new Annotation(text);
        // Set the date (if provided)
        if (date != null) {
          annotation.set(CoreAnnotations.DocDateAnnotation.class, date);
        }
        return annotation;
      case "serialized":
        String inputSerializerName = props.getProperty("inputSerializer", ProtobufAnnotationSerializer.class.getName());
        if (!inputSerializerName.equals(ProtobufAnnotationSerializer.class.getName())) {
          throw new IOException("Specifying an inputSerializer other than ProtobufAnnotationSerializer is now deprecated for security reasons.  See https://github.com/stanfordnlp/CoreNLP/security/advisories/GHSA-wv35-hv9v-526p  If you have need for a different class, please post about your use case on the CoreNLP github.");
        }
        AnnotationSerializer serializer = new ProtobufAnnotationSerializer();
        Pair<Annotation, InputStream> pair = serializer.read(httpExchange.getRequestBody());
        return pair.first;
      default:
        throw new IOException("Could not parse input format: " + inputFormat);
    }
  }

  private String getContentType(Headers headers) {
    String contentType = URL_ENCODED;
    if (headers.containsKey("Content-type")) {
      contentType = headers.getFirst("Content-type").split(";")[0].trim();
    }
    return contentType;
  }

  private String getEncoding(Headers headers) {
    // The default encoding by the HTTP standard is ISO-8859-1, but most
    // real users of CoreNLP would likely assume UTF-8 by default.
    String defaultEncoding = this.strict ? "ISO-8859-1" : "UTF-8";
    if (headers.containsKey("Content-type")) {
      String[] charsetPair = Arrays.stream(headers.getFirst("Content-type").split(";"))
          .map(x -> x.split("="))
          .filter(x -> x.length > 0 && "charset".equals(x[0]))
          .findFirst().orElse(new String[]{"charset", defaultEncoding});
      if (charsetPair.length == 2) {
        return charsetPair[1];
      } else {
        return defaultEncoding;
      }
    } else {
      return defaultEncoding;
    }
  }

  /**
   * Get a SceneGraph request from the query, either from a query parameter (q)
   * or from the body of the request
   * <br>
   * TODO: don't actually know if the scenegraph parser is threadsafe.
   *
   * @return query
   */
  private String getSceneGraphRequest(Properties props, HttpExchange httpExchange) throws IOException, ClassNotFoundException {
    final String inputFormat = props.getProperty("inputFormat", "text");
    if (!inputFormat.equals("text")) {
      throw new IOException("Unhandled input format for scenegraph: " + inputFormat);
    }
    String query = props.getProperty("q", null);
    if (query != null) {
      return query;
    }

    Headers headers = httpExchange.getRequestHeaders();
    // the original default behavior of the server was to
    // unescape, so let's assume by default that the input text is
    // escaped.  if the Content-type is set to text we will know
    // we shouldn't unescape after all
    final String contentType = getContentType(headers);
    // Get the encoding
    final String encoding = getEncoding(headers);

    String text = IOUtils.slurpReader(IOUtils.encodedInputStreamReader(httpExchange.getRequestBody(), encoding));
    if (contentType.equals(URL_ENCODED)) {
      try {
        text = URLDecoder.decode(text, encoding);
      } catch (IllegalArgumentException e) {
        // ignore decoding errors so that libraries which don't specify a content type might not fail
      }
    }

    return text;
  }

  /**
   * Create (or retrieve) a StanfordCoreNLP object corresponding to these properties.
   *
   * @param props The properties to create the object with.
   * @return A pipeline parameterized by these properties.
   */
  private StanfordCoreNLP mkStanfordCoreNLP(Properties props) {
    StanfordCoreNLP impl;

    StringBuilder sb = new StringBuilder();
    props.stringPropertyNames().stream().filter(key -> !key.equalsIgnoreCase("date")).forEach(key -> {
      String pvalue = props.getProperty(key);
      sb.append(key).append(':').append(pvalue).append(';');
    });
    String cacheKey = sb.toString();

    synchronized (this) {
      Pair<String, StanfordCoreNLP> lastPipeline = this.lastPipeline.get();
      if (lastPipeline != null && Objects.equals(lastPipeline.first, cacheKey)) {
        return lastPipeline.second;
      } else {
        // Do some housekeeping on the global cache
        for (Iterator<Map.Entry<StanfordCoreNLP.AnnotatorSignature, Lazy<Annotator>>> iter = StanfordCoreNLP.GLOBAL_ANNOTATOR_CACHE.entrySet().iterator();
             iter.hasNext(); ) {
          Map.Entry<StanfordCoreNLP.AnnotatorSignature, Lazy<Annotator>> entry = iter.next();
          if ( ! entry.getValue().isCache()) {
            error("Entry in global cache is not garbage collectable!");
            iter.remove();
          } else if (entry.getValue().isGarbageCollected()) {
            iter.remove();
          }
        }
        // Create a CoreNLP
        impl = new StanfordCoreNLP(props);
        this.lastPipeline = new SoftReference<>(Pair.makePair(cacheKey, impl));
      }
    }

    return impl;
  }

  /**
   * This server has at most one SceneGraph parser, and it is not created at startup time
   * as most applications will not use it.
   * <br>
   * This function call creates it in a synchronized manner, so at most one is ever created.
   * <br>
   * @return RuleBasedParser
   */
  private RuleBasedParser mkSceneGraphParser() {
    if (sceneParser != null) {
      return sceneParser;
    }
    synchronized (this) {
      // in case it got created in another thread
      if (sceneParser != null) {
        return sceneParser;
      }
      RuleBasedParser parser = new RuleBasedParser();
      sceneParser = parser;
      return parser;
    }
  }

  /**
   * Parse the parameters of a connection into a CoreNLP properties file that can be passed into
   * {@link StanfordCoreNLP}, and used in the I/O stages.
   *
   * @param httpExchange The http exchange; effectively, the request information.
   * @return A {@link Properties} object corresponding to a combination of default and passed properties.
   *
   * @throws UnsupportedEncodingException Thrown if we could not decode the key/value pairs with UTF-8.
   */
  private Properties getProperties(HttpExchange httpExchange) throws UnsupportedEncodingException {
    Map<String, String> urlParams = getURLParams(httpExchange.getRequestURI());

    // Load the default properties if resetDefault is false
    // If resetDefault is true, ignore server properties this server was started with,
    // except the keys in serverIOProperties (i.e., don't reset IO properties)
    Properties props = new Properties();
    if ( ! urlParams.getOrDefault("resetDefault", "false").equalsIgnoreCase("true"))
      defaultProps.forEach((key1, value) -> props.setProperty(key1.toString(), value.toString()));
    else {
      // if resetDefault is called, still maintain the serverIO properties (e.g. inputFormat, outputFormat, prettyPrint)
      for (String ioKey : serverIOProps.stringPropertyNames())
        props.setProperty(ioKey, defaultProps.getProperty(ioKey));
    }

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
        try (BufferedReader is = IOUtils.readerFromString(languagePropertiesFile)){
          Properties languageSpecificProperties = new Properties();
          languageSpecificProperties.load(is);
          PropertiesUtils.overWriteProperties(props,languageSpecificProperties);
          // don't enforce requirements for non-English
          if ( ! LanguageInfo.HumanLanguage.ENGLISH.equals(LanguageInfo.getLanguageFromString(language))) {
            props.setProperty("enforceRequirements", "false");
          }
          // check if the server is set to use the srparser, and if so,
          // set the parse.model to be srparser.model
          // also, check properties for the srparser.model prop
          // perhaps some languages don't have a default set for that
          if (srparser && languageSpecificProperties.containsKey("srparser.model")) {
            props.setProperty("parse.model", languageSpecificProperties.getProperty("srparser.model"));
          }
        } catch (IOException e) {
          err("Failure to load language specific properties: " + languagePropertiesFile + " for " + language);
        }
      } else {
        try {
          respondError("Invalid language: '" + language + '\'', httpExchange);
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
    urlProperties.forEach(props::setProperty);

    // Get the annotators
    StanfordCoreNLP.normalizeAnnotators(props);
    String annotators = props.getProperty("annotators");
    // If the properties contains a custom annotator, then do not enforceRequirements.
    if (annotators != null && !PropertiesUtils.hasPropertyPrefix(props, CUSTOM_ANNOTATOR_PREFIX) && PropertiesUtils.getBool(props, "enforceRequirements", true)) {
      annotators = StanfordCoreNLP.ensurePrerequisiteAnnotators(props.getProperty("annotators").split("[, \t]+"), props);
    }

    // Make sure the properties compile
    if (annotators != null)
      props.setProperty("annotators", annotators);

    return props;
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
    log("Responding unauthorized to " + httpExchange.getRemoteAddress());
    httpExchange.getResponseHeaders().add("Content-type", "application/javascript");
    byte[] content = "{\"message\": \"Unauthorized API request\"}".getBytes(StandardCharsets.UTF_8);
    httpExchange.sendResponseHeaders(HTTP_UNAUTHORIZED, content.length);
    httpExchange.getResponseBody().write(content);
    httpExchange.close();
  }

  private static void setHttpExchangeResponseHeaders(HttpExchange httpExchange) {
    // Set common response headers
    httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
    httpExchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
    httpExchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
    httpExchange.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");
    httpExchange.getResponseHeaders().add("Access-Control-Allow-Credentials-Header", "*");
  }


  /**
   * Adapted from: https://stackoverflow.com/questions/4209760/validate-an-ip-address-with-mask
   */
  private static Pair<Inet4Address, Integer> parseSubnet(String subnet) {
    String[] parts = subnet.split("/");
    String ip = parts[0];
    int prefix;

    if (parts.length < 2) {
      prefix = 0;
    } else {
      prefix = Integer.parseInt(parts[1]);
    }
    try {
      return Pair.makePair((Inet4Address) InetAddress.getByName(ip), prefix);
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("Invalid subnet: " + subnet);
    }
  }


  /**
   * Adapted from: https://stackoverflow.com/questions/4209760/validate-an-ip-address-with-mask
   */
  @SuppressWarnings("PointlessBitwiseExpression")
  private static boolean netMatch(Pair<Inet4Address, Integer> subnet, Inet4Address addr ){
    byte[] b = subnet.first.getAddress();
    int ipInt = ((b[0] & 0xFF) << 24) |
        ((b[1] & 0xFF) << 16) |
        ((b[2] & 0xFF) << 8)  |
        ((b[3] & 0xFF) << 0);
    byte[] b1 = addr.getAddress();
    int ipInt1 = ((b1[0] & 0xFF) << 24) |
        ((b1[1] & 0xFF) << 16) |
        ((b1[2] & 0xFF) << 8)  |
        ((b1[3] & 0xFF) << 0);
    int mask = ~((1 << (32 - subnet.second)) - 1);
    return (ipInt & mask) == (ipInt1 & mask);
  }

  /**
   * Check that the given address is not in the subnet
   *
   * @param addr The address to check.
   *
   * @return True if the address is <b>not</b> in any forbidden subnet. That is, we can accept connections from it.
   */
  private boolean onBlockList(Inet4Address addr) {
    for (Pair<Inet4Address, Integer> subnet : blockListSubnets) {
      if (netMatch(subnet, addr)) {
        return true;
      }
    }
    return false;
  }

  /** @see #onBlockList(Inet4Address) */
  private boolean onBlockList(HttpExchange exchange) {
    if ( ! stanford) {
      return false;
    }
    InetAddress addr = exchange.getRemoteAddress().getAddress();
    if (addr instanceof Inet4Address) {
      return onBlockList((Inet4Address) addr);
    } else {
      log("Not checking IPv6 address against blockList: " + addr);
      return false;  // TODO(gabor) we should eventually check ipv6 addresses too
    }
  }


  /**
   * A callback object that lets us hook into the result of an annotation request.
   */
  public static class FinishedRequest {
    public final Properties props;
    public final Annotation document;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public final Optional<String> tokensregex;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
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
    /** If true, the server is running and ready for requests. */
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
        response = "server is not ready yet. uptime=" + Redwood.formatTimeDifference(System.currentTimeMillis() - this.startTime) + '\n';
        status = HTTP_UNAVAILABLE;
      }
      httpExchange.sendResponseHeaders(status, response.getBytes().length);
      httpExchange.getResponseBody().write(response.getBytes());
      httpExchange.close();
    }
  } // end static class ReadyHandler


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
  } // end static class LiveHandler


  /**
   * Sending the appropriate shutdown key will gracefully shut down the server.
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
  } // end static class ShutdownHandler

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
      try (BufferedReader r = IOUtils.readerFromString(fileOrClasspath, "utf-8")) {
        this.content = IOUtils.slurpReader(r);
      }
      this.contentType = contentType + "; charset=utf-8";  // always encode in utf-8
    }
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
      httpExchange.getResponseHeaders().set("Content-type", this.contentType);
      ByteBuffer buffer = StandardCharsets.UTF_8.encode(content);
      byte[] bytes = new byte[buffer.remaining()];
      buffer.get(bytes);
      httpExchange.sendResponseHeaders(HTTP_OK, bytes.length);
      httpExchange.getResponseBody().write(bytes);
      httpExchange.close();
    }
  } // end static class FileHandler

  /**
   * Serve a content file (image, font, etc) from the filesystem or classpath
   */
  public static class BytesFileHandler implements HttpHandler {
    private final byte[] content;
    private final String contentType;
    public BytesFileHandler(String fileOrClasspath, String contentType) throws IOException {
      try (InputStream is = IOUtils.getInputStreamFromURLOrClasspathOrFileSystem(fileOrClasspath)) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int available = is.available();
        while (available > 0) {
          byte next[] = new byte[available];
          is.read(next);
          bos.write(next);
          available = is.available();
        }
        this.content = bos.toByteArray();
      }
      this.contentType = contentType + "; charset=utf-8";  // always encode in utf-8
    }
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
      httpExchange.getResponseHeaders().set("Content-type", this.contentType);
      httpExchange.sendResponseHeaders(HTTP_OK, content.length);
      httpExchange.getResponseBody().write(content);
      httpExchange.close();
    }
  } // end static class FileHandler

  private int maybeAlterStanfordTimeout(HttpExchange httpExchange, int timeoutMilliseconds) {
    if ( ! stanford) {
      return timeoutMilliseconds;
    }
    try {
      // Check for too long a timeout from an unauthorized source
      if (timeoutMilliseconds > 15000) {
        // If two conditions:
        //   (1) The server is running on corenlp.run (i.e., corenlp.stanford.edu)
        //   (2) The request is not coming from a *.stanford.edu" email address
        // Then force the timeout to be 15 seconds
        if ("corenlp.stanford.edu".equals(InetAddress.getLocalHost().getHostName()) &&
            ! httpExchange.getRemoteAddress().getHostName().toLowerCase().endsWith("stanford.edu")) {
          timeoutMilliseconds = 15000;
        }
      }
      return timeoutMilliseconds;
    } catch (UnknownHostException uhe) {
      return timeoutMilliseconds;
    }
  }

  protected int getTimeout(Properties props, HttpExchange httpExchange) {
    int timeoutMilliseconds;
    try {
      timeoutMilliseconds = Integer.parseInt(props.getProperty("timeout",
                                                               Integer.toString(StanfordCoreNLPServer.this.timeoutMilliseconds)));
      timeoutMilliseconds = maybeAlterStanfordTimeout(httpExchange, timeoutMilliseconds);
      
    } catch (NumberFormatException e) {
      timeoutMilliseconds = StanfordCoreNLPServer.this.timeoutMilliseconds;
    }
    return timeoutMilliseconds;
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

    private final String contextRoot;

    /**
     * Create a handler for accepting annotation requests.
     * @param props The properties file to use as the default if none were sent by the client.
     */
    public CoreNLPHandler(Properties props, Predicate<Properties> authenticator,
                          Consumer<FinishedRequest> callback,
                          FileHandler homepage,
                          String contextRoot) {
      this.defaultProps = props;
      this.callback = callback;
      this.authenticator = authenticator;
      this.homepage = homepage;
      this.contextRoot = contextRoot;
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
      if (onBlockList(httpExchange)) {
        respondUnauthorized(httpExchange);
        return;
      }
      setHttpExchangeResponseHeaders(httpExchange);

      if (!this.contextRoot.equals(httpExchange.getRequestURI().getRawPath())) {
        System.out.println("Can't find " + httpExchange.getRequestURI().getRawPath());
        String response = "URI " + httpExchange.getRequestURI().getRawPath() + " not handled";
        httpExchange.getResponseHeaders().add("Content-type", "text/plain");
        httpExchange.sendResponseHeaders(HTTP_NOT_FOUND, response.length());
        httpExchange.getResponseBody().write(response.getBytes());
        httpExchange.close();
        return;
      }
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
        } else if (httpExchange.getRequestMethod().equals("HEAD")) {
          // attempt to handle issue #368; see http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6886723
          httpExchange.getRequestBody().close();
          httpExchange.getResponseHeaders().add("Transfer-encoding", "chunked");
          httpExchange.sendResponseHeaders(200, -1);
          httpExchange.close();
          return;
        } else {
          // Handle API request
          if (authenticator != null && !authenticator.test(props)) {
            respondUnauthorized(httpExchange);
            return;
          }
          if ( ! quiet) {
            log("[" + httpExchange.getRemoteAddress() + "] API call w/annotators " + props.getProperty("annotators", "<unknown>"));
          }
          ann = getDocument(props, httpExchange);
          of = StanfordCoreNLP.OutputFormat.valueOf(props.getProperty("outputFormat", "json").toUpperCase(Locale.ROOT));
          String text = ann.get(CoreAnnotations.TextAnnotation.class).replace('\n', ' ');
          if ( ! quiet) {
            System.out.println(text);
          }
          if (maxCharLength > 0 && text.length() > maxCharLength) {
            respondBadInput("Request is too long to be handled by server: " + text.length() + " characters. Max length is " + maxCharLength + " characters.", httpExchange);
            return;
          }
        }
      } catch (Exception e) {
        warn(e);
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
        int timeoutMilliseconds = getTimeout(props, httpExchange);
        completedAnnotation = completedAnnotationFuture.get(timeoutMilliseconds, TimeUnit.MILLISECONDS);
        completedAnnotationFuture = null;  // No longer any need for the future

        // Get output
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        AnnotationOutputter.Options options = AnnotationOutputter.getOptions(pipeline.getProperties());
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
        if (completedAnnotation != null && ! StringUtils.isNullOrEmpty(props.getProperty("annotators"))) {
          callback.accept(new FinishedRequest(props, completedAnnotation));
        }
      } catch (TimeoutException e) {
        // Print the stack trace for debugging
        warn(e);
        // Return error message.
        respondError("CoreNLP request timed out. Your document may be too long.", httpExchange);
        // Cancel the future if it's alive
        //noinspection ConstantConditions
        if (completedAnnotationFuture != null) {
          completedAnnotationFuture.cancel(true);
        }
      } catch (Exception e) {
        // Print the stack trace for debugging
        warn(e);
        // Return error message.
        respondError(e.getClass().getName() + ": " + e.getMessage(), httpExchange);
        // Cancel the future if it's alive
        if (completedAnnotationFuture != null) {  // just in case...
          completedAnnotationFuture.cancel(true);
        }
      }
    }

  } // end class CoreNLPHandler



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
      if (onBlockList(httpExchange)) {
        respondUnauthorized(httpExchange);
        return;
      }
      setHttpExchangeResponseHeaders(httpExchange);

      Properties props = getProperties(httpExchange);

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
          final boolean filter = filterStr.trim().isEmpty() || "true".equalsIgnoreCase(filterStr);
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
                        if ( ! info.nodes.isEmpty()) {
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
          warn(e);
          try {
            respondError(e.getClass().getName() + ": " + e.getMessage(), httpExchange);
          } catch (IOException ignored) {
          }
        }
        return new Pair<>("", null);
      });

      // Send response
      try {
        int timeout = getTimeout(props, httpExchange);
        if (lastPipeline.get() == null) {
          timeout = timeout + 60000; // add 60 seconds for loading a pipeline if needed
        }
        Pair<String, Annotation> response = future.get(timeout, TimeUnit.MILLISECONDS);
        Annotation completedAnnotation = response.second;
        byte[] content = response.first.getBytes();
        sendAndGetResponse(httpExchange, content);
        if (completedAnnotation != null && ! StringUtils.isNullOrEmpty(props.getProperty("annotators"))) {
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
      if (onBlockList(httpExchange)) {
        respondUnauthorized(httpExchange);
        return;
      }
      setHttpExchangeResponseHeaders(httpExchange);

      Properties props = getProperties(httpExchange);

      if (authenticator != null && !authenticator.test(props)) {
        respondUnauthorized(httpExchange);
        return;
      }
      Map<String, String> params = getURLParams(httpExchange.getRequestURI());

      Future<Pair<byte[], Annotation>> response = corenlpExecutor.submit(() -> {
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
            return Pair.makePair("".getBytes(), null);
          }
          String pattern = params.get("pattern");
          // (get whether to filter / find)
          String filterStr = params.getOrDefault("filter", "false");
          final boolean filter = filterStr.trim().isEmpty() || "true".equalsIgnoreCase(filterStr);
          // (in case of find, get whether to only keep unique matches)
          String uniqueStr = params.getOrDefault("unique", "false");
          final boolean unique = uniqueStr.trim().isEmpty() || "true".equalsIgnoreCase(uniqueStr);
          // (create the matcher)
          final SemgrexPattern regex = SemgrexPattern.compile(pattern);
          final SemanticGraphCoreAnnotations.DependenciesType dependenciesType =
            SemanticGraphCoreAnnotations.DependenciesType.valueOf(params.getOrDefault("dependenciesType", "enhancedPlusPlus").toUpperCase(Locale.ROOT));

          StanfordCoreNLP.OutputFormat of = StanfordCoreNLP.OutputFormat.valueOf(props.getProperty("outputFormat", "json").toUpperCase(Locale.ROOT));

          switch(of) {
          case JSON:
            // Run Semgrex
            String content = JSONOutputter.JSONWriter.objectToJSON((docWriter) -> {
            if (filter) {
              // Case: just filter sentences
              docWriter.set("sentences", doc.get(CoreAnnotations.SentencesAnnotation.class).stream().map(sentence ->
                      regex.matcher(sentence.get(dependenciesType.annotation())).matches()
              ).collect(Collectors.toList()));
            } else {
              // Case: find matches
              docWriter.set("sentences", doc.get(CoreAnnotations.SentencesAnnotation.class).stream().map(sentence -> (Consumer<JSONOutputter.Writer>) (JSONOutputter.Writer sentWriter) -> {
                SemgrexMatcher matcher = regex.matcher(sentence.get(dependenciesType.annotation()));
                int i = 0;
                // Case: find either next node or next unique node
                while (unique ? matcher.findNextMatchingNode() : matcher.find()) {
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
            return Pair.makePair(content.getBytes(), doc);
          case SERIALIZED:
            if (filter) {
              respondBadInput("Interface semgrex does not support 'filter' for output format " + of, httpExchange);
              return Pair.makePair("".getBytes(), null);
            }

            List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
            List<SemgrexPattern> patterns = Collections.singletonList(regex);
            CoreNLPProtos.SemgrexResponse semgrexResponse = ProcessSemgrexRequest.processRequest(sentences, patterns);

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            semgrexResponse.writeTo(os);
            os.close();

            return Pair.makePair(os.toByteArray(), doc);
          default:
            respondBadInput("Interface semgrex does not handle output format " + of, httpExchange);
            return Pair.makePair("".getBytes(), null);
          }
        } catch (Exception e) {
          warn(e);
          try {
            respondError(e.getClass().getName() + ": " + e.getMessage(), httpExchange);
          } catch (IOException ignored) {
          }
        }
        return Pair.makePair("".getBytes(), null);
      });

      // Send response
      try {
        int timeout = getTimeout(props, httpExchange);
        if (lastPipeline.get() == null) {
          timeout = timeout + 60000; // add 60 seconds for loading a pipeline if needed
        }
        Pair<byte[], Annotation> pair = response.get(timeout, TimeUnit.MILLISECONDS);
        Annotation completedAnnotation = pair.second;
        byte[] content = pair.first;
        sendAndGetResponse(httpExchange, content);
        if (completedAnnotation != null && ! StringUtils.isNullOrEmpty(props.getProperty("annotators"))) {
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

    public void setTregexOffsets(JSONOutputter.Writer writer, Tree match) {
      List<Tree> leaves = match.getLeaves();
      Label label = leaves.get(0).label();
      if (label instanceof CoreLabel) {
        CoreLabel core = (CoreLabel) label;
        writer.set("characterOffsetBegin", core.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class));
        if (core.containsKey(CoreAnnotations.CodepointOffsetBeginAnnotation.class)) {
          writer.set("codepointOffsetBegin", core.get(CoreAnnotations.CodepointOffsetBeginAnnotation.class));
        }
      }
      label = leaves.get(leaves.size() - 1).label();
      if (label instanceof CoreLabel) {
        CoreLabel core = (CoreLabel) label;
        writer.set("characterOffsetEnd", core.get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
        if (core.containsKey(CoreAnnotations.CodepointOffsetEndAnnotation.class)) {
          writer.set("codepointOffsetEnd", core.get(CoreAnnotations.CodepointOffsetEndAnnotation.class));
        }
      }
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
      if (onBlockList(httpExchange)) {
        respondUnauthorized(httpExchange);
        return;
      }
      setHttpExchangeResponseHeaders(httpExchange);

      Properties props = getProperties(httpExchange);

      if (authenticator != null && ! authenticator.test(props)) {
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
          if ( ! params.containsKey("pattern")) {
            respondBadInput("Missing required parameter 'pattern'", httpExchange);
            return Pair.makePair("", null);
          }
          String rawPattern = params.get("pattern");

          // (create the matcher)
          TregexPattern pattern = TregexPattern.compile(rawPattern);

          // Run Tregex
          return Pair.makePair(JSONOutputter.JSONWriter.objectToJSON((docWriter) ->
            docWriter.set("sentences", doc.get(CoreAnnotations.SentencesAnnotation.class).stream().map(sentence -> (Consumer<JSONOutputter.Writer>) (JSONOutputter.Writer sentWriter) -> {
                int sentIndex = sentence.get(CoreAnnotations.SentenceIndexAnnotation.class);
                Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
                if (tree == null) {
                  throw new IllegalStateException("Error: cannot process tregex operations with no constituency tree annotations.  Perhaps need to reinitialize the server with the parse annotator");
                }
                //sentWriter.set("tree", tree.pennString());
                TregexMatcher matcher = pattern.matcher(tree);

                int i = 0;
                while (matcher.find()) {
                  sentWriter.set(Integer.toString(i++), (Consumer<JSONOutputter.Writer>) (JSONOutputter.Writer matchWriter) -> {
                    matchWriter.set("sentIndex", sentIndex);
                    setTregexOffsets(matchWriter, matcher.getMatch());
                    matchWriter.set("match", matcher.getMatch().pennString());
                    matchWriter.set("spanString", matcher.getMatch().spanString());
                    matchWriter.set("namedNodes", matcher.getNodeNames().stream().map(nodeName -> (Consumer<JSONOutputter.Writer>) (JSONOutputter.Writer namedNodeWriter) -> 
                      namedNodeWriter.set(nodeName, (Consumer<JSONOutputter.Writer>) (JSONOutputter.Writer namedNodeSubWriter) -> {
                        setTregexOffsets(namedNodeSubWriter, matcher.getNode(nodeName));
                        namedNodeSubWriter.set("match", matcher.getNode(nodeName).pennString());
                        namedNodeSubWriter.set("spanString", matcher.getNode(nodeName).spanString());
                      })
                    ));
                  });
                }
            }))
          ), doc);
        } catch (Exception e) {
          warn(e);
          try {
            respondError(e.getClass().getName() + ": " + e.getMessage(), httpExchange);
          } catch (IOException ignored) {
          }
        }
        return Pair.makePair("", null);
      });

      // Send response
      try {
        int timeout = getTimeout(props, httpExchange);
        if (lastPipeline.get() == null) {
          timeout = timeout + 60000; // add 60 seconds for loading a pipeline if needed
        }
        Pair<String, Annotation> pair = response.get(timeout, TimeUnit.MILLISECONDS);
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

  /**
   * A handler for executing scenegraph on text
   */
  protected class SceneGraphHandler implements HttpHandler {

    /**
     * An authenticator to determine if we can perform this API request.
     */
    private final Predicate<Properties> authenticator;

    /**
     * Create a new SceneGraphHandler.
     * <br>
     * It's not clear what a callback would do with this, since there's no Annotation at the end of a SceneGraph call,
     * so we just skip it.
     *
     * @param authenticator The callback to call when annotation has finished.
     */
    public SceneGraphHandler(Predicate<Properties> authenticator) {
      this.authenticator = authenticator;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
      if (onBlockList(httpExchange)) {
        respondUnauthorized(httpExchange);
        return;
      }
      setHttpExchangeResponseHeaders(httpExchange);

      Properties props = getProperties(httpExchange);

      if (authenticator != null && ! authenticator.test(props)) {
        respondUnauthorized(httpExchange);
        return;
      }
      Map<String, String> params = getURLParams(httpExchange.getRequestURI());

      Future<Pair<String, SceneGraph>> response = corenlpExecutor.submit(() -> {
          try {
            // Get the document
            String request = getSceneGraphRequest(props, httpExchange);
            if (request == null || request.equals("")) {
              respondBadInput("Blank input in scenegraph", httpExchange);
              return Pair.makePair("", null);
            }
            RuleBasedParser parser = mkSceneGraphParser();

            SceneGraph graph = parser.parse(request);
            if (graph == null) {
              respondError("Something weird happened and the text could not be parsed!", httpExchange);
            }
            return Pair.makePair(request, graph);
          } catch (RuntimeException e) {
            warn(e);
            try {
              respondError(e.getClass().getName() + ": " + e.getMessage(), httpExchange);
            } catch (IOException ignored) {
            }
          }
          return Pair.makePair("", null);
        });

      // Send response
      try {
        int timeout = getTimeout(props, httpExchange);
        if (sceneParser == null) {
          timeout = timeout + 60000; // add 60 seconds for loading a pipeline if needed
        }
        Pair<String, SceneGraph> pair = response.get(timeout, TimeUnit.MILLISECONDS);
        SceneGraph graph = pair.second;
        if (graph == null) {
          // already responded with an error
          return;
        }

        final StanfordCoreNLP.OutputFormat of;
        try {
          of = StanfordCoreNLP.OutputFormat.valueOf(props.getProperty("outputFormat", "json").toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
          String badFormat = props.getProperty("outputFormat");
          log("Received bad output format in scenegraph '" + badFormat + "'");
          respondBadInput("Interface scenegraph does not handle output format '" + badFormat + "'", httpExchange);
          return;
        }

        final String result;
        switch(of) {
        case JSON:
          int id = PropertiesUtils.getInt(props, "id", -1);
          String url = props.getProperty("url", "");
          String phrase = pair.first;
          result = graph.toJSON(id, url, phrase);
          break;
        case TEXT:
          result = graph.toReadableString();
          break;
        default:
          log("Received unhanded output format in scenegraph '" + of + "'");
          respondBadInput("Interface scenegraph does not handle output format " + of, httpExchange);
          return;
        }

        byte[] content = result.getBytes();
        sendAndGetResponse(httpExchange, content);
      } catch (InterruptedException | ExecutionException | TimeoutException e) {
        respondError("Timeout when executing scenegraph query", httpExchange);
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
    try (InputStream is = IOUtils.getInputStreamFromURLOrClasspathOrFileSystem(key)) {
      KeyStore ks = KeyStore.getInstance("JKS");
      if (StanfordCoreNLPServer.key != null && IOUtils.existsInClasspathOrFileSystem(StanfordCoreNLPServer.key)) {
        ks.load(is, "corenlp".toCharArray());
      } else {
        throw new IllegalArgumentException("Could not find SSL keystore at " + StanfordCoreNLPServer.key);
      }
      KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
      kmf.init(ks, "corenlp".toCharArray());
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(kmf.getKeyManagers(), null, null);

      // Add SSL support to the server
      server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
        @Override
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
  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  private static void withAuth(HttpContext context, Optional<Pair<String,String>> credentials) {
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
  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
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
      String contextRoot = uriContext;
      if (contextRoot.isEmpty()) {
        contextRoot = "/";
      }
      withAuth(server.createContext(contextRoot, new CoreNLPHandler(defaultProps, authenticator, callback, homepage, contextRoot)), basicAuth);
      withAuth(server.createContext(uriContext+"/tokensregex", new TokensRegexHandler(authenticator, callback)), basicAuth);
      withAuth(server.createContext(uriContext+"/semgrex", new SemgrexHandler(authenticator, callback)), basicAuth);
      withAuth(server.createContext(uriContext+"/tregex", new TregexHandler(authenticator, callback)), basicAuth);
      withAuth(server.createContext(uriContext+"/scenegraph", new SceneGraphHandler(authenticator)), basicAuth);

      withAuth(server.createContext(uriContext+"/corenlp-brat.js", new FileHandler("edu/stanford/nlp/pipeline/demo/corenlp-brat.js", "application/javascript")), basicAuth);
      withAuth(server.createContext(uriContext+"/corenlp-brat.cs", new FileHandler("edu/stanford/nlp/pipeline/demo/corenlp-brat.css", "text/css")), basicAuth);
      withAuth(server.createContext(uriContext+"/corenlp-parseviewer.js", new FileHandler("edu/stanford/nlp/pipeline/demo/corenlp-parseviewer.js", "application/javascript")), basicAuth);

      withAuth(server.createContext(uriContext+"/style-vis.css", new FileHandler("edu/stanford/nlp/pipeline/demo/style-vis.css", "text/css")), basicAuth);

      withAuth(server.createContext(uriContext+"/static/fonts/Astloch-Bold.ttf", new BytesFileHandler("edu/stanford/nlp/pipeline/demo/Astloch-Bold.ttf", "font/ttfx")), basicAuth);
      withAuth(server.createContext(uriContext+"/static/fonts/Liberation_Sans-Regular.ttf", new BytesFileHandler("edu/stanford/nlp/pipeline/demo/LiberationSans-Regular.ttf", "font/ttf")), basicAuth);
      withAuth(server.createContext(uriContext+"/static/fonts/PT_Sans-Caption-Web-Regular.ttf", new BytesFileHandler("edu/stanford/nlp/pipeline/demo/PTSansCaption-Regular.ttf", "font/ttf")), basicAuth);

      withAuth(server.createContext(uriContext+"/annotation_log.js", new BytesFileHandler("edu/stanford/nlp/pipeline/demo/annotation_log.js", "application/javascript")), basicAuth);
      withAuth(server.createContext(uriContext+"/configuration.js", new BytesFileHandler("edu/stanford/nlp/pipeline/demo/configuration.js", "application/javascript")), basicAuth);
      withAuth(server.createContext(uriContext+"/dispatcher.js", new BytesFileHandler("edu/stanford/nlp/pipeline/demo/dispatcher.js", "application/javascript")), basicAuth);
      withAuth(server.createContext(uriContext+"/head.load.min.js", new BytesFileHandler("edu/stanford/nlp/pipeline/demo/head.load.min.js", "application/javascript")), basicAuth);
      withAuth(server.createContext(uriContext+"/jquery.svg.min.js", new BytesFileHandler("edu/stanford/nlp/pipeline/demo/jquery.svg.min.js", "application/javascript")), basicAuth);
      withAuth(server.createContext(uriContext+"/jquery.svgdom.min.js", new BytesFileHandler("edu/stanford/nlp/pipeline/demo/jquery.svg.min.js", "application/javascript")), basicAuth);
      withAuth(server.createContext(uriContext+"/url_monitor.js", new BytesFileHandler("edu/stanford/nlp/pipeline/demo/url_monitor.js", "application/javascript")), basicAuth);
      withAuth(server.createContext(uriContext+"/util.js", new BytesFileHandler("edu/stanford/nlp/pipeline/demo/util.js", "application/javascript")), basicAuth);
      withAuth(server.createContext(uriContext+"/visualizer.js", new BytesFileHandler("edu/stanford/nlp/pipeline/demo/visualizer.js", "application/javascript")), basicAuth);
      withAuth(server.createContext(uriContext+"/webfont.js", new BytesFileHandler("edu/stanford/nlp/pipeline/demo/webfont.js", "application/javascript")), basicAuth);

      withAuth(server.createContext(uriContext+"/img/corenlp-title.png", new BytesFileHandler("edu/stanford/nlp/pipeline/demo/corenlp-title.png", "image/png")), basicAuth);
      withAuth(server.createContext(uriContext+"/img/loading.gif", new BytesFileHandler("edu/stanford/nlp/pipeline/demo/loading.gif", "image/gif")), basicAuth);

      withAuth(server.createContext(uriContext+"/ping", new PingHandler()), Optional.empty());
      withAuth(server.createContext(uriContext+"/shutdown", new ShutdownHandler()), basicAuth);
      if (this.serverPort == this.statusPort) {
        withAuth(server.createContext(uriContext+"/live", new LiveHandler()), Optional.empty());
        withAuth(server.createContext(uriContext+"/ready", new ReadyHandler(live)), Optional.empty());

      }
      server.setExecutor(serverExecutor);
      server.start();
      live.set(true);
      log("StanfordCoreNLPServer listening at " + server.getAddress());
    } catch (IOException e) {
      warn(e);
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
  public static StanfordCoreNLPServer launchServer(String[] args) throws IOException {
    // Add a bit of logging
    log("--- " + StanfordCoreNLPServer.class.getSimpleName() + "#main() called ---");
    String build = System.getenv("BUILD");
    if (build != null) {
      log("    Build: " + build);
    }
    Runtime.getRuntime().addShutdownHook(new Thread(() -> log("CoreNLP Server is shutting down.")));

    // Fill arguments
    ArgumentParser.fillOptions(StanfordCoreNLPServer.class, args);
    // get server properties from command line
    Properties serverProperties = StringUtils.argsToProperties(args);
    StanfordCoreNLPServer server = new StanfordCoreNLPServer(serverProperties);  // must come after filling global options
    ArgumentParser.fillOptions(server, args);
    // align status port and server port in case status port hasn't been set and
    // server port is not the default 9000
    if ( ! serverProperties.containsKey("status_port") && serverProperties.containsKey("port")) {
      server.statusPort = Integer.parseInt(serverProperties.getProperty("port"));
    }
    log("Threads: " + ArgumentParser.threads);

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
    if (StanfordCoreNLPServer.preloadedAnnotators != null) {
      Properties props = new Properties();
      server.defaultProps.forEach((key1, value) -> props.setProperty(key1.toString(), value.toString()));
      // -preload flag alone means to load all default annotators
      // -preload flag with a list of annotators means to preload just that list (e.g. tokenize,ssplit,pos)
      String annotatorsToLoad = (StanfordCoreNLPServer.preloadedAnnotators.trim().equals("true")) ?
          server.defaultProps.getProperty("annotators") : StanfordCoreNLPServer.preloadedAnnotators;
      if (annotatorsToLoad != null)
        props.setProperty("annotators", annotatorsToLoad);
      try {
        new StanfordCoreNLP(props);
      } catch (Throwable throwable) {
        err("Could not pre-load annotators in server; encountered exception:");
        err(throwable);
      }
    }

    // Credentials
    Optional<Pair<String, String>> credentials = Optional.empty();
    if (server.username != null && server.password != null) {
      credentials = Optional.of(Pair.makePair(server.username, server.password));
    }

    // Run the server
    log("Starting server...");
    server.run(credentials, req -> true, res -> {}, homepage, server.ssl, live);

    return server;
  } // end launchServer


  public static void main(String[] args) throws IOException {
    launchServer(args);
  }
}
