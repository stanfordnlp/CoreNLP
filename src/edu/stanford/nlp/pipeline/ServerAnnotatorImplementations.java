package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.CoreAnnotation;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * A set of annotator implementations, backed by the server
 * ({@link StanfordCoreNLPServer}).
 *
 * @author <a href="mailto:gabor@eloquent.ai">Gabor Angeli</a>
 */
public class ServerAnnotatorImplementations extends AnnotatorImplementations {

  /*
   * An SLF4J Logger for this class.
   */
  // private static final Logger log = LoggerFactory.getLogger(ServerAnnotatorImplementations.class);


  /**
   * The hostname of the server to hit
   */
  public final String host;
  /**
   * The port to hit on the server
   */
  public final int port;


  /**
   * Create a new annotator implementation backed by {@link StanfordCoreNLPServer}.
   *
   * @param host The hostname of the server.
   * @param port The port of the server.
   */
  public ServerAnnotatorImplementations(String host, int port) {
    this.host = host;
    this.port = port;
  }

  /**
   *
   */
  private static class SingletonAnnotator implements Annotator {

    private final StanfordCoreNLPClient client;

    public SingletonAnnotator(String host, int port,
                              Properties properties,
                              String annotator) {
      Properties forClient = new Properties();
      for (Object o : properties.keySet()) {
        String key = o.toString();
        String value = properties.getProperty(key);
        forClient.setProperty(key, value);
        forClient.setProperty(annotator + '.' + key, value);
      }
      forClient.setProperty("annotators", annotator);
      forClient.setProperty("enforceRequirements", "false");
      this.client = new StanfordCoreNLPClient(forClient, host, port);
    }

    @Override
    public void annotate(Annotation annotation) {
      client.annotate(annotation);
    }

    @Override
    public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
      return Collections.emptySet();
    }

    @Override
    public Set<Class<? extends CoreAnnotation>> requires() {
      return Collections.emptySet();
    }
  }


  /** {@inheritDoc} */
  @Override
  public Annotator posTagger(Properties properties) {
    return new SingletonAnnotator(host, port, properties, Annotator.STANFORD_POS);
  }


  /** {@inheritDoc} */
  @Override
  public Annotator ner(Properties properties) throws IOException {
    return new SingletonAnnotator(host, port, properties, Annotator.STANFORD_NER);
  }


  /** {@inheritDoc} */
  @Override
  public Annotator tokensRegexNER(Properties properties, String name) {
    return new SingletonAnnotator(host, port, properties, Annotator.STANFORD_REGEXNER);
  }


  /** {@inheritDoc} */
  @Override
  public Annotator gender(Properties properties, boolean verbose) {
    return new SingletonAnnotator(host, port, properties, Annotator.STANFORD_GENDER);
  }


  /** {@inheritDoc} */
  @Override
  public Annotator parse(Properties properties) {
    return new SingletonAnnotator(host, port, properties, Annotator.STANFORD_PARSE);
  }


  /** {@inheritDoc} */
  @Override
  public Annotator trueCase(Properties properties) {
    return new SingletonAnnotator(host, port, properties, Annotator.STANFORD_TRUECASE);
  }


  /** {@inheritDoc} */
  @Override
  public Annotator mention(Properties properties) {
    return new SingletonAnnotator(host, port, properties, Annotator.STANFORD_MENTION);
  }


  /** {@inheritDoc} */
  @Override
  public Annotator coref(Properties properties) {
    return new SingletonAnnotator(host, port, properties, Annotator.STANFORD_COREF);
  }


  /** {@inheritDoc} */
  @Override
  public Annotator dcoref(Properties properties) {
    return new SingletonAnnotator(host, port, properties, Annotator.STANFORD_DETERMINISTIC_COREF);
  }


  /** {@inheritDoc} */
  @Override
  public Annotator relations(Properties properties) {
    return new SingletonAnnotator(host, port, properties, Annotator.STANFORD_RELATION);
  }


  /** {@inheritDoc} */
  @Override
  public Annotator sentiment(Properties properties, String name) {
    return new SingletonAnnotator(host, port, properties, Annotator.STANFORD_SENTIMENT);
  }


  /** {@inheritDoc} */
  @Override
  public Annotator dependencies(Properties properties) {
    return new SingletonAnnotator(host, port, properties, Annotator.STANFORD_DEPENDENCIES);
  }


  /** {@inheritDoc} */
  @Override
  public Annotator openie(Properties properties) {
    return new SingletonAnnotator(host, port, properties, Annotator.STANFORD_OPENIE);
  }


  /** {@inheritDoc} */
  @Override
  public Annotator kbp(Properties properties) {
    return new SingletonAnnotator(host, port, properties, Annotator.STANFORD_KBP);
  }


  /** {@inheritDoc} */
  @Override
  public Annotator link(Properties properties) {
    return new SingletonAnnotator(host, port, properties, Annotator.STANFORD_LINK);
  }

}
