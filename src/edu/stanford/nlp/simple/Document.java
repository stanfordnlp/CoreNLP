package edu.stanford.nlp.simple;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.CorefProperties;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.naturalli.OperatorSpec;
import edu.stanford.nlp.naturalli.Polarity;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static edu.stanford.nlp.simple.Sentence.SINGLE_SENTENCE_DOCUMENT;
import static edu.stanford.nlp.pipeline.Annotator.*;

/**
 * A representation of a Document. Most blobs of raw text should become documents.
 *
 * @author Gabor Angeli
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class Document {

  /**
   * The empty {@link java.util.Properties} object, for use with creating default annotators.
   */
  static final Properties EMPTY_PROPS = PropertiesUtils.asProperties(
      "language", "english",
      "annotators", "",
      "tokenize.class", "PTBTokenizer",
      "tokenize.language", "en",
      "parse.binaryTrees", "true",
      "mention.type", "dep",
      "coref.mode", "statistical",  // Use the new coref
      "coref.md.type", "dep"
  );


  /**
   * The caseless {@link java.util.Properties} object.
   *
   * @see Document#caseless()
   * @see Sentence#caseless()
   */
  static final Properties CASELESS_PROPS = PropertiesUtils.asProperties(
        "language", "english",
        "annotators", "",
        "tokenize.class", "PTBTokenizer",
        "tokenize.language", "en",
        "parse.binaryTrees", "true",
        "pos.model", "edu/stanford/nlp/models/pos-tagger/wsj-0-18-caseless-left3words-distsim.tagger",
        "parse.model", "edu/stanford/nlp/models/lexparser/englishPCFG.caseless.ser.gz",
        "ner.model", "edu/stanford/nlp/models/ner/english.muc.7class.caseless.distsim.crf.ser.gz," +
                             "edu/stanford/nlp/models/ner/english.conll.4class.caseless.distsim.crf.ser.gz," +
                             "edu/stanford/nlp/models/ner/english.all.3class.caseless.distsim.crf.ser.gz");


  /**
   * The backend to use for constructing {@link edu.stanford.nlp.pipeline.Annotator}s.
   */
  private static AnnotatorImplementations backend = new AnnotatorImplementations();

  /**
   * The default {@link edu.stanford.nlp.pipeline.TokenizerAnnotator} implementation
   */
  private static final Annotator defaultTokenize = backend.tokenizer(EMPTY_PROPS);
  /**
   * The default {@link edu.stanford.nlp.pipeline.WordsToSentencesAnnotator} implementation
   */
  private static final Annotator defaultSSplit = backend.wordToSentences(EMPTY_PROPS);
  /**
   * The default {@link edu.stanford.nlp.pipeline.POSTaggerAnnotator} implementation
   */
  private static Supplier<Annotator> defaultPOS = new Supplier<Annotator>() {
    private StanfordCoreNLP.AnnotatorSignature key = new StanfordCoreNLP.AnnotatorSignature(STANFORD_POS,
        PropertiesUtils.getSignature(STANFORD_POS, EMPTY_PROPS));
    @Override
    public synchronized Annotator get() {
      return StanfordCoreNLP.GLOBAL_ANNOTATOR_CACHE.computeIfAbsent(key, (x) -> Lazy.of(() -> backend.posTagger(EMPTY_PROPS))).get();
    }
  };
  /**
   * The default {@link edu.stanford.nlp.pipeline.MorphaAnnotator} implementation
   */
  private static final Supplier<Annotator> defaultLemma = () -> backend.morpha(EMPTY_PROPS, false);

  /**
   * The default {@link edu.stanford.nlp.pipeline.NERCombinerAnnotator} implementation
   */
  private static Supplier<Annotator> defaultNER = new Supplier<Annotator>() {
    private StanfordCoreNLP.AnnotatorSignature key = new StanfordCoreNLP.AnnotatorSignature(STANFORD_NER,
        PropertiesUtils.getSignature(STANFORD_NER, EMPTY_PROPS));
    @Override
    public synchronized Annotator get() {
      return StanfordCoreNLP.GLOBAL_ANNOTATOR_CACHE.computeIfAbsent(key, (x) -> Lazy.of(() -> backend.ner(EMPTY_PROPS))).get();
    }
  };

  /**
   * The default {@link edu.stanford.nlp.pipeline.RegexNERAnnotator} implementation
   */
  private static Supplier<Annotator> defaultRegexner = new Supplier<Annotator>() {
    private StanfordCoreNLP.AnnotatorSignature key = new StanfordCoreNLP.AnnotatorSignature(STANFORD_REGEXNER,
        PropertiesUtils.getSignature(STANFORD_REGEXNER, EMPTY_PROPS));
    @Override
    public synchronized Annotator get() {
      return StanfordCoreNLP.GLOBAL_ANNOTATOR_CACHE.computeIfAbsent(key, (x) -> Lazy.of(() -> backend.tokensRegexNER(EMPTY_PROPS, STANFORD_REGEXNER))).get();
    }
  };

  /**
   * The default {@link edu.stanford.nlp.pipeline.ParserAnnotator} implementation
   */
  private static Supplier<Annotator> defaultParse = new Supplier<Annotator>() {
    private StanfordCoreNLP.AnnotatorSignature key = new StanfordCoreNLP.AnnotatorSignature(STANFORD_PARSE,
        PropertiesUtils.getSignature(STANFORD_PARSE, EMPTY_PROPS));
    @Override
    public synchronized Annotator get() {
      return StanfordCoreNLP.GLOBAL_ANNOTATOR_CACHE.computeIfAbsent(key, (x) -> Lazy.of(() -> backend.parse(EMPTY_PROPS))).get();
    }
  };

  /**
   * The default {@link edu.stanford.nlp.pipeline.DependencyParseAnnotator} implementation
   */
  private static Supplier<Annotator> defaultDepparse = new Supplier<Annotator>() {
    private StanfordCoreNLP.AnnotatorSignature key = new StanfordCoreNLP.AnnotatorSignature(STANFORD_DEPENDENCIES,
        PropertiesUtils.getSignature(STANFORD_DEPENDENCIES, EMPTY_PROPS));
    @Override
    public synchronized Annotator get() {
      return StanfordCoreNLP.GLOBAL_ANNOTATOR_CACHE.computeIfAbsent(key, (x) -> Lazy.of(() -> backend.dependencies(EMPTY_PROPS))).get();
    }
  };

  /**
   * The default {@link edu.stanford.nlp.naturalli.NaturalLogicAnnotator} implementation
   */
  private static Supplier<Annotator> defaultNatlog = new Supplier<Annotator>() {
    private StanfordCoreNLP.AnnotatorSignature key = new StanfordCoreNLP.AnnotatorSignature(STANFORD_NATLOG,
        PropertiesUtils.getSignature(STANFORD_NATLOG, EMPTY_PROPS));
    @Override
    public synchronized Annotator get() {
      return StanfordCoreNLP.GLOBAL_ANNOTATOR_CACHE.computeIfAbsent(key, (x) -> Lazy.of(() -> backend.natlog(EMPTY_PROPS))).get();
    }
  };

  /**
   * The default {@link EntityMentionsAnnotator} implementation
   */
  private static Supplier<Annotator> defaultEntityMentions = new Supplier<Annotator>() {
    private StanfordCoreNLP.AnnotatorSignature key = new StanfordCoreNLP.AnnotatorSignature(STANFORD_ENTITY_MENTIONS,
        PropertiesUtils.getSignature(STANFORD_ENTITY_MENTIONS, EMPTY_PROPS));
    @Override
    public synchronized Annotator get() {
      return StanfordCoreNLP.GLOBAL_ANNOTATOR_CACHE.computeIfAbsent(key, (x) -> Lazy.of(() -> backend.entityMentions(EMPTY_PROPS, STANFORD_ENTITY_MENTIONS))).get();
    }
  };

  /**
   * The default {@link KBPAnnotator} implementation
   */
  private static Supplier<Annotator> defaultKBP = new Supplier<Annotator>() {
    private StanfordCoreNLP.AnnotatorSignature key = new StanfordCoreNLP.AnnotatorSignature(STANFORD_KBP,
        PropertiesUtils.getSignature(STANFORD_KBP, EMPTY_PROPS));
    @Override
    public synchronized Annotator get() {
      return StanfordCoreNLP.GLOBAL_ANNOTATOR_CACHE.computeIfAbsent(key, (x) -> Lazy.of(() -> backend.kbp(EMPTY_PROPS))).get();
    }
  };


  /**
   * The default {@link edu.stanford.nlp.naturalli.OpenIE} implementation
   */
  private static Supplier<Annotator> defaultOpenie = new Supplier<Annotator>() {
    private StanfordCoreNLP.AnnotatorSignature key = new StanfordCoreNLP.AnnotatorSignature(STANFORD_OPENIE,
        PropertiesUtils.getSignature(STANFORD_OPENIE, EMPTY_PROPS));
    @Override
    public synchronized Annotator get() {
      return StanfordCoreNLP.GLOBAL_ANNOTATOR_CACHE.computeIfAbsent(key, (x) -> Lazy.of(() -> backend.openie(EMPTY_PROPS))).get();
    }
  };

  /**
   * The default {@link edu.stanford.nlp.pipeline.CorefMentionAnnotator} implementation
   */
  private static Supplier<Annotator> defaultMention = new Supplier<Annotator>() {
    private StanfordCoreNLP.AnnotatorSignature key = new StanfordCoreNLP.AnnotatorSignature(STANFORD_COREF_MENTION,
        PropertiesUtils.getSignature(STANFORD_COREF_MENTION, EMPTY_PROPS));
    @Override
    public synchronized Annotator get() {
      return StanfordCoreNLP.GLOBAL_ANNOTATOR_CACHE.computeIfAbsent(key, (x) -> Lazy.of(() -> backend.corefMention(EMPTY_PROPS))).get();
    }
  };

  /**
   * The default {@link edu.stanford.nlp.pipeline.CorefAnnotator} implementation
   */
  private static Supplier<Annotator> defaultCoref = new Supplier<Annotator>() {
    private StanfordCoreNLP.AnnotatorSignature key = new StanfordCoreNLP.AnnotatorSignature(STANFORD_COREF,
        PropertiesUtils.getSignature(STANFORD_COREF, EMPTY_PROPS));
    @Override
    public synchronized Annotator get() {
      return StanfordCoreNLP.GLOBAL_ANNOTATOR_CACHE.computeIfAbsent(key, (x) -> Lazy.of(() -> backend.coref(EMPTY_PROPS))).get();
    }
  };

  /**
   * The default {@link edu.stanford.nlp.pipeline.SentimentAnnotator} implementation
   */
  private static Supplier<Annotator> defaultSentiment = new Supplier<Annotator>() {
    private StanfordCoreNLP.AnnotatorSignature key = new StanfordCoreNLP.AnnotatorSignature(STANFORD_SENTIMENT,
        PropertiesUtils.getSignature(STANFORD_SENTIMENT, EMPTY_PROPS));
    @Override
    public synchronized Annotator get() {
      return StanfordCoreNLP.GLOBAL_ANNOTATOR_CACHE.computeIfAbsent(key, (x) -> Lazy.of(() -> backend.sentiment(EMPTY_PROPS, STANFORD_SENTIMENT))).get();
    }
  };


  /**
   * Cache the most recently used custom annotators.
   */
  private static final AnnotatorPool customAnnotators = new AnnotatorPool();


  /**
   * Either get a custom annotator which was recently defined, or create it if it has never been defined.
   * This method is synchronized to avoid race conditions when loading the annotators.
   *
   * @param name The name of the annotator.
   * @param props The properties used to create the annotator, if we need to create it.
   * @param annotator The actual function used to make the annotator, if needed.
   *
   * @return An annotator as specified by the given name and properties.
   */
  private static synchronized Supplier<Annotator> getOrCreate(String name, Properties props, Supplier<Annotator> annotator) {
    StanfordCoreNLP.AnnotatorSignature key = new StanfordCoreNLP.AnnotatorSignature(name, PropertiesUtils.getSignature(name, props));
    customAnnotators.register(name, props, StanfordCoreNLP.GLOBAL_ANNOTATOR_CACHE.computeIfAbsent(key, (sig) -> Lazy.cache(annotator)));
    return () -> customAnnotators.get(name);
  }

  /** The protocol buffer representing this document */
  protected final CoreNLPProtos.Document.Builder impl;

  /** The list of sentences associated with this document */
  protected List<Sentence> sentences = null;

  /** A serializer to assist in serializing and deserializing from Protocol buffers */
  protected final ProtobufAnnotationSerializer serializer = new ProtobufAnnotationSerializer(false );

  /**
   * THIS IS NONSTANDARD.
   * An indicator of whether we have run the OpenIE annotator.
   * Unlike most other annotators, it's quite common for a sentence to not have any extracted triples,
   * and therefore it's hard to determine whether we should rerun the annotator based solely on the saved
   * annotation.
   * At the same time, the proto file should not have this flag in it.
   * So, here it is.
   */
  private boolean haveRunOpenie = false;

  /**
   * THIS IS NONSTANDARD.
   * An indicator of whether we have run the KBP annotator.
   * Unlike most other annotators, it's quite common for a sentence to not have any extracted triples,
   * and therefore it's hard to determine whether we should rerun the annotator based solely on the saved
   * annotation.
   * At the same time, the proto file should not have this flag in it.
   * So, here it is.
   */
  private boolean haveRunKBP = false;

  /** The default properties to use for annotating things (e.g., coref for the document level) */
  private Properties defaultProps;


  /**
   * Set the backend implementations for our CoreNLP pipeline.
   * For example, to a {@link ServerAnnotatorImplementations}.
   *
   * @param backend The backend to use from now on for annotating
   *                documents.
   */
  public static void setBackend(AnnotatorImplementations backend) {
    Document.backend = backend;
  }


  /**
   * Use the CoreNLP Server ({@link StanfordCoreNLPServer}) for the
   * heavyweight backend annotation job.
   *
   * @param host The hostname of the server.
   * @param port The port the server is running on.
   */
  public static void useServer(String host, int port) {
    backend = new ServerAnnotatorImplementations(host, port);
  }


  /**
   * Use the CoreNLP Server ({@link StanfordCoreNLPServer}) for the
   * heavyweight backend annotation job, authenticating with the given
   * credentials.
   *
   * @param host The hostname of the server.
   * @param port The port the server is running on.
   * @param apiKey The api key to use as the username for authentication
   * @param apiSecret The api secrete to use as the password for authentication
   * @param lazy Only run the annotations that are required at this time. If this is
   *             false, we will also run a bunch of standard annotations, to cut down on
   *             expected number of round-trips.
   */
  public static void useServer(String host, int port,
                               String apiKey, String apiSecret,
                               boolean lazy) {
    backend = new ServerAnnotatorImplementations(host, port, apiKey, apiSecret, lazy);
  }


  /** @see Document#useServer(String, int, String, String, boolean) */
  public static void useServer(String host,
                               String apiKey, String apiSecret,
                               boolean lazy) {
    useServer(host, host.startsWith("http://") ? 80 : 443, apiKey, apiSecret, lazy);
  }

  /** @see Document#useServer(String, int, String, String, boolean) */
  public static void useServer(String host,
                               String apiKey, String apiSecret) {
    useServer(host, host.startsWith("http://") ? 80 : 443, apiKey, apiSecret, true);
  }


  /*
   * A static block that'll automatically fault in the CoreNLP server, if the appropriate environment
   * variables are set.
   * These are:
   *
   * <ul>
   *     <li>CORENLP_HOST</li> -- this is already sufficient to trigger creating a server
   *     <li>CORENLP_PORT</li>
   *     <li>CORENLP_KEY</li>
   *     <li>CORENLP_SECRET</li>
   *     <li>CORENLP_LAZY</li>  (if true, do as much annotation on a single round-trip as possible)
   * </ul>
   */
  static {
    String host    = System.getenv("CORENLP_HOST");
    String portStr = System.getenv("CORENLP_PORT");
    String key     = System.getenv("CORENLP_KEY");
    String secret  = System.getenv("CORENLP_SECRET");
    String lazystr = System.getenv("CORENLP_LAZY");
    if (host != null) {
      int port = 443;
      if (portStr == null) {
        if (host.startsWith("http://")) {
          port = 80;
        }
      } else {
        port = Integer.parseInt(portStr);
      }
      boolean lazy = true;
      if (lazystr != null) {
        lazy = Boolean.parseBoolean(lazystr);
      }
      if (key != null && secret != null) {
        useServer(host, port, key, secret, lazy);
      } else {
        useServer(host, port);
      }
    }
  }


  /**
   * Create a new document from the passed in text and the given properties.
   * @param text The text of the document.
   */
  public Document(Properties props, String text) {
    this.defaultProps = props;
    this.impl = CoreNLPProtos.Document.newBuilder().setText(text);
  }


  /**
   * Create a new document from the passed in text.
   * @param text The text of the document.
   */
  public Document(String text) {
    this(EMPTY_PROPS, text);
  }

  /**
   * Convert a CoreNLP Annotation object to a Document.
   * @param ann The CoreNLP Annotation object.
   */
  @SuppressWarnings("Convert2streamapi")
  public Document(Properties props, Annotation ann) {
    this.defaultProps = props;
    StanfordCoreNLP.getDefaultAnnotatorPool(props, new AnnotatorImplementations());  // cache the annotator pool
    this.impl = new ProtobufAnnotationSerializer(false).toProtoBuilder(ann);
    List<CoreMap> sentences = ann.get(CoreAnnotations.SentencesAnnotation.class);
    this.sentences = new ArrayList<>(sentences.size());
    for (CoreMap sentence : sentences) {
      this.sentences.add(new Sentence(this, this.serializer.toProtoBuilder(sentence), sentence.get(CoreAnnotations.TextAnnotation.class), this.defaultProps));
    }
  }


  /** @see Document#Document(Properties, Annotation) */
  public Document(Annotation ann) {
    this(Document.EMPTY_PROPS, ann);
  }

  /**
   * Create a Document object from a read Protocol Buffer.
   * @see edu.stanford.nlp.simple.Document#serialize()
   * @param proto The protocol buffer representing this document.
   */
  @SuppressWarnings("Convert2streamapi")
  public Document(Properties props, CoreNLPProtos.Document proto) {
    this.defaultProps = props;
    StanfordCoreNLP.getDefaultAnnotatorPool(props, new AnnotatorImplementations());  // cache the annotator pool
    this.impl = proto.toBuilder();
    if (proto.getSentenceCount() > 0) {
      this.sentences = new ArrayList<>(proto.getSentenceCount());
      for (CoreNLPProtos.Sentence sentence : proto.getSentenceList()) {
        this.sentences.add(new Sentence(this, sentence.toBuilder(), this.defaultProps));
      }
    }
  }


  /** @see Document#Document(Properties, CoreNLPProtos.Document)  */
  public Document(CoreNLPProtos.Document proto) {
    this(Document.EMPTY_PROPS, proto);
  }


  /**
   * Make this document caseless. That is, from now on, run the caseless models
   * on the document by default rather than the standard CoreNLP models.
   *
   * @return This same document, but with the default properties swapped out.
   */
  public Document caseless() {
    this.defaultProps = CASELESS_PROPS;
    return this;
  }

  /**
   * Make this document case sensitive.
   * A document is case sensitive by default; this only has an effect if you have previously
   * called {@link Sentence#caseless()}.
   *
   * @return This same document, but with the default properties swapped out.
   */
  public Document cased() {
    this.defaultProps = EMPTY_PROPS;
    return this;
  }

  /**
   * Serialize this Document as a Protocol Buffer.
   * This can be deserialized with the constructor {@link Document#Document(edu.stanford.nlp.pipeline.CoreNLPProtos.Document)}.
   *
   * @return The document as represented by a Protocol Buffer.
   */
  public CoreNLPProtos.Document serialize() {
    synchronized (impl) {
      // Ensure we have sentences
      List<Sentence> sentences = sentences();
      // Ensure we're saving the newest sentences
      // IMPORTANT NOTE: the clear below must come after we call #sentences()
      this.impl.clearSentence();
      for (Sentence s : sentences) {
        this.impl.addSentence(s.serialize());
      }
      // Serialize document
      return impl.build();
    }
  }

  /**
   * Write this document to an output stream.
   * Internally, this stores the document as a protocol buffer, and saves that buffer to the output stream.
   * This method does not close the stream after writing.
   *
   * @param out The output stream to write to. The stream is not closed after the method returns.
   * @throws IOException Thrown from the underlying write() implementation.
   *
   * @see Document#deserialize(InputStream)
   */
  public void serialize(OutputStream out) throws IOException {
    serialize().writeDelimitedTo(out);
    out.flush();
  }

  /**
   * Read a document from an input stream.
   * This does not close the input stream.
   *
   * @param in The input stream to deserialize from.
   * @return The next document encoded in the input stream.
   * @throws IOException Thrown by the underlying parse() implementation.
   *
   * @see Document#serialize(java.io.OutputStream)
   */
  public static Document deserialize(InputStream in) throws IOException {
    return new Document(CoreNLPProtos.Document.parseDelimitedFrom(in));
  }

  /**
   * <p>
   *  Write this annotation as a JSON string.
   *  Optionally, you can also specify a number of operations to call on the document before
   *  dumping it to JSON.
   *  This allows the user to ensure that certain annotations have been computed before the document
   *  is dumped.
   *  For example:
   * </p>
   *
   * <pre>{@code
   *   String json = new Document("Lucy in the sky with diamonds").json(Sentence::parse, Sentence::ner);
   * }</pre>
   *
   * <p>
   *   will create a JSON dump of the document, ensuring that at least the parse tree and ner tags are populated.
   * </p>
   *
   * @param functions The (possibly empty) list of annotations to populate on the document before dumping it
   *                  to JSON.
   * @return The JSON String for this document.
   */
  @SafeVarargs
  public final String json(Function<Sentence, Object>... functions) {
    for (Function<Sentence, Object> f : functions) {
      f.apply(this.sentence(0));
    }
    try {
      return new JSONOutputter().print(this.asAnnotation());
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  /**
   * Like the {@link Document@json(Function...)} function, but with minified JSON more suitable
   * for sending over the wire.
   *
   * @param functions The (possibly empty) list of annotations to populate on the document before dumping it
   *                  to JSON.
   * @return The JSON String for this document, without unnecessary whitespace.
   *
   */
  @SafeVarargs
  public final String jsonMinified(Function<Sentence, Object>... functions) {
    for (Function<Sentence, Object> f : functions) {
      f.apply(this.sentence(0));
    }
    try {
      AnnotationOutputter.Options options = new AnnotationOutputter.Options(false);
      return new JSONOutputter().print(this.asAnnotation(), options);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  /** Write this annotation as an XML string.
   *  Optionally, you can also specify a number of operations to call on the document before
   *  dumping it to XML.
   *  This allows the user to ensure that certain annotations have been computed before the document
   *  is dumped.
   *  For example:
   *  <p>
   *  {@code String xml = new Document("Lucy in the sky with diamonds").xml(Document::parse, Document::ner); }
   *  <p>
   *  will create a XML dump of the document, ensuring that at least the parse tree and ner tags are populated.
   *
   *  @param functions The (possibly empty) list of annotations to populate on the document before dumping it
   *                   to XML.
   *  @return The XML String for this document.
   */
  @SafeVarargs
  public final String xml(Function<Sentence, Object>... functions) {
    for (Function<Sentence, Object> f : functions) {
      f.apply(this.sentence(0));
    }
    try {
      return new XMLOutputter().print(this.asAnnotation());
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  /**
   * Like the {@link Document@xml(Function...)} function, but with minified XML more suitable
   * for sending over the wire.
   *
   * @param functions The (possibly empty) list of annotations to populate on the document before dumping it
   *                  to XML.
   * @return The XML String for this document, without unecessary whitespace.
   *
   */
  @SafeVarargs
  public final String xmlMinified(Function<Sentence, Object>... functions) {
    for (Function<Sentence, Object> f : functions) {
      f.apply(this.sentence(0));
    }
    try {
      AnnotationOutputter.Options options = new AnnotationOutputter.Options(false);
      return new XMLOutputter().print(this.asAnnotation(false), options);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  /**
   * Get the sentences in this document, as a list.
   * @param props The properties to use in the {@link edu.stanford.nlp.pipeline.WordsToSentencesAnnotator}.
   * @return A list of Sentence objects representing the sentences in the document.
   */
  public List<Sentence> sentences(Properties props) {
    return this.sentences(props,
        props == EMPTY_PROPS ? defaultTokenize : getOrCreate(Annotator.STANFORD_TOKENIZE, props, () -> backend.tokenizer(props)).get());
  }

  /**
   * Get the sentences in this document, as a list.
   * @param props The properties to use in the {@link edu.stanford.nlp.pipeline.WordsToSentencesAnnotator}.
   * @return A list of Sentence objects representing the sentences in the document.
   */
  protected List<Sentence> sentences(Properties props, Annotator tokenizer) {
    if (sentences == null) {
      synchronized (this) {
        if (sentences == null) {
          Annotator ssplit = props == EMPTY_PROPS ? defaultSSplit : getOrCreate(STANFORD_SSPLIT, props, () -> backend.wordToSentences(props)).get();
          // Annotate
          Annotation ann = new Annotation(this.impl.getText());
          tokenizer.annotate(ann);
          ssplit.annotate(ann);
          // Grok results
          // (docid)
          if (ann.containsKey(CoreAnnotations.DocIDAnnotation.class)) {
            impl.setDocID(ann.get(CoreAnnotations.DocIDAnnotation.class));
          }
          // (sentences)
          List<CoreMap> sentences = ann.get(CoreAnnotations.SentencesAnnotation.class);
          this.sentences = new ArrayList<>(sentences.size());
          this.impl.clearSentence();
          for (CoreMap sentence : sentences) {
            //Sentence sent = new Sentence(this, sentence);
            Sentence sent = new Sentence(this, this.serializer.toProtoBuilder(sentence), sentence.get(CoreAnnotations.TextAnnotation.class), defaultProps);
            this.sentences.add(sent);
            this.impl.addSentence(sent.serialize());
          }
        }
      }
    }

    return sentences;
  }

  /** @see Document#sentences(java.util.Properties) */
  public List<Sentence> sentences() {
    return sentences(EMPTY_PROPS);
  }

  /** @see Document#sentences(java.util.Properties) */
  public Sentence sentence(int sentenceIndex, Properties props) {
    return sentences(props).get(sentenceIndex);
  }

  /** @see Document#sentences(java.util.Properties) */
  public Sentence sentence(int sentenceIndex) {
    return sentences().get(sentenceIndex);
  }

  /** Get the raw text of the document, as input by, e.g., {@link Document#Document(String)}. */
  public String text() {
    synchronized (impl) {
      return impl.getText();
    }
  }

  /**
   * Returns the coref chains in the document. This is a map from coref cluster IDs, to the coref chain
   * with that ID.
   * @param props The properties to use in the {@link edu.stanford.nlp.pipeline.DeterministicCorefAnnotator}.
   */
  public Map<Integer, CorefChain> coref(Properties props) {
    synchronized (this.impl) {
      if (impl.getCorefChainCount() == 0) {
        // Run prerequisites
        this.runLemma(props).runNER(props);
        if (CorefProperties.mdType(props) != CorefProperties.MentionDetectionType.DEPENDENCY) {
          this.runParse(props);
        } else {
          this.runDepparse(props);
        }
        // Run mention
        Supplier<Annotator> mention = (props == EMPTY_PROPS || props == SINGLE_SENTENCE_DOCUMENT) ? defaultMention : getOrCreate(STANFORD_COREF_MENTION, props, () -> backend.corefMention(props));
        // Run coref
        Supplier<Annotator> coref = (props == EMPTY_PROPS || props == SINGLE_SENTENCE_DOCUMENT) ? defaultCoref : getOrCreate(STANFORD_COREF, props, () -> backend.coref(props));
        Annotation ann = asAnnotation(true);
        mention.get().annotate(ann);
        coref.get().annotate(ann);
        // Convert to proto
        synchronized (serializer) {
          for (CorefChain chain : ann.get(CorefCoreAnnotations.CorefChainAnnotation.class).values()) {
            impl.addCorefChain(serializer.toProto(chain));
          }
        }
      }
      Map<Integer, CorefChain> corefs = Generics.newHashMap();
      for (CoreNLPProtos.CorefChain chain : impl.getCorefChainList()) {
        corefs.put(chain.getChainID(), fromProto(chain));
      }
      return corefs;
    }
  }

  /** @see Document#coref(java.util.Properties) */
  public Map<Integer, CorefChain> coref() {
    return coref(defaultProps);
  }

  /** Returns the document id of the document, if one was found */
  public Optional<String> docid() {
    synchronized (impl) {
      if (impl.hasDocID()) {
        return Optional.of(impl.getDocID());
      } else {
        return Optional.empty();
      }
    }
  }

  /** Sets the document id of the document, returning this. */
  public Document setDocid(String docid) {
    synchronized (impl) {
      this.impl.setDocID(docid);
    }
    return this;
  }


  /**
   * <p>
   *   Bypass the tokenizer and sentence splitter -- axiomatically set the sentences for this document.
   *   This is a VERY dangerous method to call if you don't know what you're doing.
   *   The primary use case is for forcing single-sentence documents, where most of the fields in the document
   *   do not matter.
   * </p>
   *
   * @param sentences The sentences to force for the sentence list of this document.
   */
  void forceSentences(List<Sentence> sentences) {
    this.sentences = sentences;
    synchronized (impl) {
      this.impl.clearSentence();
      for (Sentence sent : sentences) {
        this.impl.addSentence(sent.serialize());
      }
    }
  }



  //
  // Begin helpers
  //

  synchronized Document runPOS(Properties props) {
    // Cached result
    if (this.sentences != null && ! this.sentences.isEmpty() && this.sentences.get(0).rawToken(0).hasPos()) {
      return this;
    }
    // Prerequisites
    sentences();
    // Run annotator
    Supplier<Annotator> pos = (props == EMPTY_PROPS || props == SINGLE_SENTENCE_DOCUMENT) ? defaultPOS : getOrCreate(STANFORD_POS, props, () -> backend.posTagger(props));
    Annotation ann = asAnnotation(false);
    pos.get().annotate(ann);
    // Update data
    for (int i = 0; i < sentences.size(); ++i) {
      sentences.get(i).updateTokens(ann.get(CoreAnnotations.SentencesAnnotation.class).get(i).get(CoreAnnotations.TokensAnnotation.class), (pair) -> pair.first.setPos(pair.second), CoreLabel::tag);
    }
    return this;
  }

  synchronized Document runLemma(Properties props) {
    // Cached result
    if (this.sentences != null && ! this.sentences.isEmpty() && this.sentences.get(0).rawToken(0).hasLemma()) {
      return this;
    }
    // Prerequisites
    runPOS(props);
    // Run annotator
    Supplier<Annotator> lemma = (props == EMPTY_PROPS || props == SINGLE_SENTENCE_DOCUMENT) ? defaultLemma : getOrCreate(STANFORD_LEMMA, props, () -> backend.morpha(props, false));
    Annotation ann = asAnnotation(true);
    lemma.get().annotate(ann);
    // Update data
    for (int i = 0; i < sentences.size(); ++i) {
      sentences.get(i).updateTokens(ann.get(CoreAnnotations.SentencesAnnotation.class).get(i).get(CoreAnnotations.TokensAnnotation.class), (pair) -> pair.first.setLemma(pair.second), CoreLabel::lemma);
    }
    return this;
  }

  synchronized Document mockLemma(Properties props) {
    // Cached result
    if (this.sentences != null && ! this.sentences.isEmpty() && this.sentences.get(0).rawToken(0).hasLemma()) {
      return this;
    }
    // Prerequisites
    runPOS(props);
    // Mock lemma with word
    Annotation ann = asAnnotation(true);
    for (int i = 0; i < sentences.size(); ++i) {
      sentences.get(i).updateTokens(ann.get(CoreAnnotations.SentencesAnnotation.class).get(i).get(CoreAnnotations.TokensAnnotation.class), (pair) -> pair.first.setLemma(pair.second), CoreLabel::word);
    }
    return this;

  }

  synchronized Document runNER(Properties props) {
    if (this.sentences != null && ! this.sentences.isEmpty() && this.sentences.get(0).rawToken(0).hasNer()) {
      return this;
    }
    // Run prerequisites
    runPOS(props);
    // Run annotator
    Supplier<Annotator> ner = (props == EMPTY_PROPS || props == SINGLE_SENTENCE_DOCUMENT) ? defaultNER : getOrCreate(STANFORD_NER, props, () -> backend.ner(props));
    Annotation ann = asAnnotation(true);
    ner.get().annotate(ann);
    // Update data
    for (int i = 0; i < sentences.size(); ++i) {
      sentences.get(i).updateTokens(ann.get(CoreAnnotations.SentencesAnnotation.class).get(i).get(CoreAnnotations.TokensAnnotation.class), (pair) -> pair.first.setNer(pair.second), CoreLabel::ner);
    }
    return this;
  }

  synchronized Document runRegexner(Properties props) {
    // Run prerequisites
    runNER(props);
    // Run annotator
    Supplier<Annotator> ner = (props == EMPTY_PROPS || props == SINGLE_SENTENCE_DOCUMENT) ? defaultRegexner : getOrCreate(STANFORD_REGEXNER, props, () -> backend.tokensRegexNER(props, STANFORD_REGEXNER));
    Annotation ann = asAnnotation(true);
    ner.get().annotate(ann);
    // Update data
    for (int i = 0; i < sentences.size(); ++i) {
      sentences.get(i).updateTokens(ann.get(CoreAnnotations.SentencesAnnotation.class).get(i).get(CoreAnnotations.TokensAnnotation.class), (pair) -> pair.first.setNer(pair.second), CoreLabel::ner);
    }
    return this;
  }

  synchronized Document runParse(Properties props) {
    if (this.sentences != null && ! this.sentences.isEmpty() && this.sentences.get(0).rawSentence().hasParseTree()) {
      return this;
    }
    // Run annotator
    boolean cacheAnnotation = false;
    Annotator parse = ((props == EMPTY_PROPS || props == SINGLE_SENTENCE_DOCUMENT) ? defaultParse : getOrCreate(STANFORD_PARSE, props, () -> backend.parse(props))).get();
    if (parse.requires().contains(CoreAnnotations.PartOfSpeechAnnotation.class) || System.getenv("CORENLP_HOST") != null) {
      // Run the POS tagger if we are (or may be) using the shift reduce parser
      runPOS(props);
      cacheAnnotation = true;
    } else {
      sentences();
    }
    Annotation ann = asAnnotation(cacheAnnotation);
    parse.annotate(ann);
    // Update data
    synchronized (serializer) {
      for (int i = 0; i < sentences.size(); ++i) {
        CoreMap sentence = ann.get(CoreAnnotations.SentencesAnnotation.class).get(i);
        Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
        Tree binaryTree = sentence.get(TreeCoreAnnotations.BinarizedTreeAnnotation.class);
        sentences.get(i).updateParse(serializer.toProto(tree),
                binaryTree == null ? null : serializer.toProto(binaryTree));
        // check this sentence has dependency annotations and update
        SemanticGraph basicDependencies =
                sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
        SemanticGraph enhancedDependencies =
                sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
        SemanticGraph enhancedPlusPlusDependencies =
                sentence.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);
        if (basicDependencies != null && enhancedDependencies != null && enhancedPlusPlusDependencies != null) {
          sentences.get(i).updateDependencies(serializer.toProto(basicDependencies),
                                              serializer.toProto(enhancedDependencies),
                                              serializer.toProto(enhancedPlusPlusDependencies));
        }
      }
    }
    return this;
  }

  synchronized Document runDepparse(Properties props) {
    if (this.sentences != null && ! this.sentences.isEmpty() &&
        this.sentences.get(0).rawSentence().hasBasicDependencies()) {
      return this;
    }
    // Run prerequisites
    runPOS(props);
    // Run annotator
    Supplier<Annotator> depparse = (props == EMPTY_PROPS || props == SINGLE_SENTENCE_DOCUMENT) ? defaultDepparse : getOrCreate(STANFORD_DEPENDENCIES, props, () -> backend.dependencies(props));
    Annotation ann = asAnnotation(true);
    depparse.get().annotate(ann);
    // Update data
    synchronized (serializer) {
      for (int i = 0; i < sentences.size(); ++i) {
        CoreMap sentence = ann.get(CoreAnnotations.SentencesAnnotation.class).get(i);
        sentences.get(i).updateDependencies(
            serializer.toProto(sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class)),
            serializer.toProto(sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class)),
            serializer.toProto(sentence.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class)));
      }
    }
    return this;
  }

  synchronized Document runNatlog(Properties props) {
    if (this.sentences != null && ! this.sentences.isEmpty() && this.sentences.get(0).rawToken(0).hasPolarity()) {
      return this;
    }
    // Run prerequisites
    runLemma(props);
    runDepparse(props);
    // Run annotator
    Supplier<Annotator> natlog = (props == EMPTY_PROPS || props == SINGLE_SENTENCE_DOCUMENT) ? defaultNatlog : getOrCreate(STANFORD_NATLOG, props, () -> backend.natlog(props));
    Annotation ann = asAnnotation(true);
    natlog.get().annotate(ann);
    // Update data
    synchronized (serializer) {
      for (int i = 0; i < sentences.size(); ++i) {
        sentences.get(i).updateTokens(ann.get(CoreAnnotations.SentencesAnnotation.class).get(i).get(CoreAnnotations.TokensAnnotation.class), (Pair<CoreNLPProtos.Token.Builder, Polarity> pair) -> pair.first().setPolarity(serializer.toProto(pair.second())), x -> x.get(NaturalLogicAnnotations.PolarityAnnotation.class));
        sentences.get(i).updateTokens(ann.get(CoreAnnotations.SentencesAnnotation.class).get(i).get(CoreAnnotations.TokensAnnotation.class), (Pair<CoreNLPProtos.Token.Builder, String> pair) -> pair.first().setPolarityDir(pair.second()), x -> x.get(NaturalLogicAnnotations.PolarityDirectionAnnotation.class));
        sentences.get(i).updateTokens(ann.get(CoreAnnotations.SentencesAnnotation.class).get(i).get(CoreAnnotations.TokensAnnotation.class), (Pair<CoreNLPProtos.Token.Builder, OperatorSpec> pair) -> pair.first().setOperator(serializer.toProto(pair.second())), x -> x.get(NaturalLogicAnnotations.OperatorAnnotation.class));
      }
    }
    return this;
  }

  synchronized Document runOpenie(Properties props) {
    if (haveRunOpenie) {
      return this;
    }
    // Run prerequisites
    runNatlog(props);
    // Run annotator
    Supplier<Annotator> openie = (props == EMPTY_PROPS || props == SINGLE_SENTENCE_DOCUMENT) ? defaultOpenie : getOrCreate(STANFORD_OPENIE, props, () -> backend.openie(props));
    Annotation ann = asAnnotation(true);
    openie.get().annotate(ann);
    // Update data
    synchronized (serializer) {
      for (int i = 0; i < sentences.size(); ++i) {
        CoreMap sentence = ann.get(CoreAnnotations.SentencesAnnotation.class).get(i);
        Collection<RelationTriple> triples = sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
        sentences.get(i).updateOpenIE(triples.stream().map(serializer::toProto));
      }
    }
    // Return
    haveRunOpenie = true;
    return this;
  }


  synchronized Document runKBP(Properties props) {
    if (haveRunKBP) {
      return this;
    }
    // Run prerequisites
    coref(props);
    Supplier<Annotator> entityMention = (props == EMPTY_PROPS || props == SINGLE_SENTENCE_DOCUMENT) ? defaultEntityMentions : getOrCreate(STANFORD_ENTITY_MENTIONS, props, () -> backend.entityMentions(props, STANFORD_ENTITY_MENTIONS));
    Annotation ann = asAnnotation(true);
    entityMention.get().annotate(ann);
    // Run annotator
    Supplier<Annotator> kbp = (props == EMPTY_PROPS || props == SINGLE_SENTENCE_DOCUMENT) ? defaultKBP : getOrCreate(STANFORD_KBP, props, () -> backend.kbp(props));
    kbp.get().annotate(ann);
    // Update data
    synchronized (serializer) {
      for (int i = 0; i < sentences.size(); ++i) {
        CoreMap sentence = ann.get(CoreAnnotations.SentencesAnnotation.class).get(i);
        Collection<RelationTriple> triples = sentence.get(CoreAnnotations.KBPTriplesAnnotation.class);
        sentences.get(i).updateKBP(triples.stream().map(serializer::toProto));
      }
    }
    // Return
    haveRunKBP = true;
    return this;
  }


  synchronized Document runSentiment(Properties props) {
    if (this.sentences != null && ! this.sentences.isEmpty() && this.sentences.get(0).rawSentence().hasSentiment()) {
        return this;
    }
    // Run prerequisites
    runParse(props);
    if (this.sentences != null && ! this.sentences.isEmpty() && ! this.sentences.get(0).rawSentence().hasBinarizedParseTree()) {
      throw new IllegalStateException("No binarized parse tree (perhaps it's not supported in this language?)");
    }
    // Run annotator
    Annotation ann = asAnnotation(true);
    Supplier<Annotator> sentiment = (props == EMPTY_PROPS || props == SINGLE_SENTENCE_DOCUMENT) ? defaultSentiment : getOrCreate(STANFORD_SENTIMENT, props, () -> backend.sentiment(props, STANFORD_SENTIMENT));
    sentiment.get().annotate(ann);
    // Update data
    synchronized (serializer) {
      for (int i = 0; i < sentences.size(); ++i) {
        CoreMap sentence = ann.get(CoreAnnotations.SentencesAnnotation.class).get(i);
        String sentimentClass = sentence.get(SentimentCoreAnnotations.SentimentClass.class);
        sentences.get(i).updateSentiment(sentimentClass);
      }
    }
    // Return
    return this;
  }

  /**
   * Return this Document as an Annotation object.
   * Note that, importantly, only the fields which have already been called will be populated in
   * the Annotation!
   *
   * <p>Therefore, this method is generally NOT recommended.</p>
   */
  public Annotation asAnnotation() {
    return asAnnotation(false);
  }


  /**
   * A cached version of this document as an Annotation.
   * This will get garbage collected when necessary.
   */
  private SoftReference<Annotation> cachedAnnotation = null;

  /**
   * Return this Document as an Annotation object.
   * Note that, importantly, only the fields which have already been called will be populated in
   * the Annotation!
   *
   * <p>Therefore, this method is generally NOT recommended.</p>
   *
   * @param cache If true, allow retrieving this object from the cache.
   */
  Annotation asAnnotation(boolean cache) {
    Annotation ann = cachedAnnotation == null ? null : cachedAnnotation.get();
    if (!cache || ann == null) {
      ann = serializer.fromProto(serialize());
    }
    cachedAnnotation = new SoftReference<>(ann);
    return ann;
  }


  /**
   * Read a CorefChain from its serialized representation.
   * This is private due to the need for an additional partial document. Also, why on Earth are you trying to use
   * this on its own anyways?
   *
   * @see edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer#fromProto(edu.stanford.nlp.pipeline.CoreNLPProtos.CorefChain, edu.stanford.nlp.pipeline.Annotation)
   *
   * @param proto The serialized representation of the coref chain, missing information on its mention span string.
   *
   * @return A coreference chain.
   */
  private CorefChain fromProto(CoreNLPProtos.CorefChain proto) {
    // Get chain ID
    int cid = proto.getChainID();
    // Get mentions
    Map<IntPair, Set<CorefChain.CorefMention>> mentions = new HashMap<>();
    CorefChain.CorefMention representative = null;
    for (int i = 0; i < proto.getMentionCount(); ++i) {
      CoreNLPProtos.CorefChain.CorefMention mentionProto = proto.getMention(i);
      // Create mention
      StringBuilder mentionSpan = new StringBuilder();
      Sentence sentence = sentence(mentionProto.getSentenceIndex());
      for (int k = mentionProto.getBeginIndex(); k < mentionProto.getEndIndex(); ++k) {
        mentionSpan.append(' ').append(sentence.word(k));
      }
      // Set the coref cluster id for the token
      CorefChain.CorefMention mention = new CorefChain.CorefMention(
          Dictionaries.MentionType.valueOf(mentionProto.getMentionType()),
          Dictionaries.Number.valueOf(mentionProto.getNumber()),
          Dictionaries.Gender.valueOf(mentionProto.getGender()),
          Dictionaries.Animacy.valueOf(mentionProto.getAnimacy()),
          mentionProto.getBeginIndex() + 1,
          mentionProto.getEndIndex() + 1,
          mentionProto.getHeadIndex() + 1,
          cid,
          mentionProto.getMentionID(),
          mentionProto.getSentenceIndex() + 1,
          new IntTuple(new int[]{ mentionProto.getSentenceIndex() + 1, mentionProto.getPosition() }),
          mentionSpan.substring(mentionSpan.length() > 0 ? 1 : 0));
      // Register mention
      IntPair key = new IntPair(mentionProto.getSentenceIndex() - 1, mentionProto.getHeadIndex() - 1);
      if (!mentions.containsKey(key)) { mentions.put(key, new HashSet<>()); }
      mentions.get(key).add(mention);
      // Check for representative
      if (proto.hasRepresentative() && i == proto.getRepresentative()) {
        representative = mention;
      }
    }
    // Return
    return new CorefChain(cid, mentions, representative);
  }


  @SuppressWarnings("SimplifiableIfStatement")
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Document)) return false;
    Document document = (Document) o;
    if (impl.hasText() && !impl.getText().equals(document.impl.getText())) {
      return false;
    }
    return impl.build().equals(document.impl.build()) && sentences.equals(document.sentences);
  }

  @Override
  public int hashCode() {
    if (impl.hasText()) {
      return impl.getText().hashCode();
    } else {
      return impl.build().hashCode();
    }
  }

  @Override
  public String toString() {
    return impl.getText();
  }


}
