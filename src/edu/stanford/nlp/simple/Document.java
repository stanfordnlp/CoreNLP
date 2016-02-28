package edu.stanford.nlp.simple;

import edu.stanford.nlp.hcoref.CorefCoreAnnotations;
import edu.stanford.nlp.hcoref.data.CorefChain;
import edu.stanford.nlp.hcoref.data.Dictionaries;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.IntTuple;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A representation of a Document. Most blobs of raw text should become documents.
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("unused")
public class Document {

  /**
   * The empty {@link java.util.Properties} object, for use with creating default annotators.
   */
  protected static final Properties EMPTY_PROPS = new Properties() {{
    setProperty("annotators", "");
    setProperty("tokenize.class", "PTBTokenizer");
    setProperty("tokenize.language", "en");
  }};

  /**
   * The backend to use for constructing {@link edu.stanford.nlp.pipeline.AnnotatorFactory}s.
   */
  private static final AnnotatorImplementations backend = new AnnotatorImplementations();

  /**
   * The default {@link edu.stanford.nlp.pipeline.TokenizerAnnotator} implementation
   */
  private static final Annotator defaultTokenize = AnnotatorFactories.tokenize(EMPTY_PROPS, backend).create();
  /**
   * The default {@link edu.stanford.nlp.pipeline.WordsToSentencesAnnotator} implementation
   */
  private static final Annotator defaultSSplit = AnnotatorFactories.sentenceSplit(EMPTY_PROPS, backend).create();
  /**
   * The default {@link edu.stanford.nlp.pipeline.POSTaggerAnnotator} implementation
   */
  private static Supplier<Annotator> defaultPOS = new Supplier<Annotator>() {
    Annotator impl = null;

    @Override
    public synchronized Annotator get() {
      if (impl == null) {
        impl = AnnotatorFactories.posTag(EMPTY_PROPS, backend).create();
      }
      return impl;
    }
  };
  /**
   * The default {@link edu.stanford.nlp.pipeline.MorphaAnnotator} implementation
   */
  private static final Annotator defaultLemma = AnnotatorFactories.lemma(EMPTY_PROPS, backend).create();

  /**
   * The default {@link edu.stanford.nlp.pipeline.NERCombinerAnnotator} implementation
   */
  private static Supplier<Annotator> defaultNER = new Supplier<Annotator>() {
    Annotator impl = null;

    @Override
    public synchronized Annotator get() {
      if (impl == null) {
        impl = AnnotatorFactories.nerTag(EMPTY_PROPS, backend).create();
      }
      return impl;
    }
  };

  /**
   * The default {@link edu.stanford.nlp.pipeline.RegexNERAnnotator} implementation
   */
  private static Supplier<Annotator> defaultRegexner = new Supplier<Annotator>() {
    Annotator impl = null;

    @Override
    public synchronized Annotator get() {
      if (impl == null) {
        impl = AnnotatorFactories.regexNER(EMPTY_PROPS, backend).create();
      }
      return impl;
    }
  };

  /**
   * The default {@link edu.stanford.nlp.pipeline.ParserAnnotator} implementation
   */
  private static Supplier<Annotator> defaultParse = new Supplier<Annotator>() {
    Annotator impl = null;

    @Override
    public synchronized Annotator get() {
      if (impl == null) {
        impl = AnnotatorFactories.parse(EMPTY_PROPS, backend).create();
      }
      return impl;
    }
  };

  /**
   * The default {@link edu.stanford.nlp.pipeline.DependencyParseAnnotator} implementation
   */
  private static Supplier<Annotator> defaultDepparse = new Supplier<Annotator>() {
    Annotator impl = null;

    @Override
    public synchronized Annotator get() {
      if (impl == null) {
        impl = AnnotatorFactories.dependencies(EMPTY_PROPS, backend).create();
      }
      return impl;
    }
  };

  /**
   * The default {@link edu.stanford.nlp.naturalli.NaturalLogicAnnotator} implementation
   */
  private static Supplier<Annotator> defaultNatlog = new Supplier<Annotator>() {
    Annotator impl = null;

    @Override
    public synchronized Annotator get() {
      if (impl == null) {
        impl = AnnotatorFactories.natlog(EMPTY_PROPS, backend).create();
      }
      return impl;
    }
  };

  /**
   * The default {@link EntityMentionsAnnotator} implementation
   */
  private static Supplier<Annotator> defaultEntityMentions = new Supplier<Annotator>() {
    Annotator impl = null;

    @Override
    public synchronized Annotator get() {
      if (impl == null) {
        impl = AnnotatorFactories.entityMentions(EMPTY_PROPS, backend).create();
      }
      return impl;
    }
  };

  /**
   * The default {@link KBPAnnotator} implementation
   */
  private static Supplier<Annotator> defaultKBP = new Supplier<Annotator>() {
    Annotator impl = null;

    @Override
    public synchronized Annotator get() {
      if (impl == null) {
        impl = AnnotatorFactories.kbp(EMPTY_PROPS, backend).create();
      }
      return impl;
    }
  };


  /**
   * The default {@link edu.stanford.nlp.naturalli.OpenIE} implementation
   */
  private static Supplier<Annotator> defaultOpenie = new Supplier<Annotator>() {
    Annotator impl = null;

    @Override
    public synchronized Annotator get() {
      if (impl == null) {
        impl = AnnotatorFactories.openie(EMPTY_PROPS, backend).create();
      }
      return impl;
    }
  };

  /**
   * The default {@link edu.stanford.nlp.pipeline.MentionAnnotator} implementation
   */
  private static Supplier<Annotator> defaultMention = new Supplier<Annotator>() {
    Annotator impl = null;

    @Override
    public synchronized Annotator get() {
      if (impl == null) {
        impl = AnnotatorFactories.mention(EMPTY_PROPS, backend).create();
      }
      return impl;
    }
  };

  /**
   * The default {@link edu.stanford.nlp.pipeline.CorefAnnotator} implementation
   */
  private static Supplier<Annotator> defaultCoref = new Supplier<Annotator>() {
    Annotator impl = null;

    @Override
    public synchronized Annotator get() {
      if (impl == null) {
        impl = AnnotatorFactories.coref(EMPTY_PROPS, backend).create();
      }
      return impl;
    }
  };

  /**
   * Cache the most recently used custom annotators.
   */
  private static final LinkedHashMap<String,Annotator> customAnnotators = new LinkedHashMap<>();

  /**
   * Either get a custom annotator which was recently defined, or create it if it has never been defined.
   * This method is synchronized to avoid race conditions when loading the annotators.
   * @param factory The factory specifying the annotator.
   * @return An annotator created by that factory.
   */
  private synchronized static Annotator getOrCreate(AnnotatorFactory factory) {
    Annotator rtn = customAnnotators.get(factory.signature());
    if (rtn == null) {
      // Create the annotator
      rtn = factory.create();
      // Register the annotator
      customAnnotators.put(factory.signature(), factory.create());
      // Clean up memory if needed
      while (customAnnotators.size() > 10) { customAnnotators.keySet().iterator().remove(); }
    }
    return rtn;
  }

  /**
   * A collection of cached Annotation objects, in case we want to further annotate them.
   * Note that this awkwardly bypasses garbage collection -- if a document gets garbage collected, the
   * associated annotation will continue to live here and take up memory for a while.
   * There is an attempt to mitigate this in {@link edu.stanford.nlp.simple.Document#finalize()}}, but one
   * should never rely on the finalize method.
   */
  private static final IdentityHashMap<Document, SoftReference<Annotation>> annotationPool = new IdentityHashMap<>();

  /** The protocol buffer representing this document */
  private final CoreNLPProtos.Document.Builder impl;

  /** The list of sentences associated with this document */
  private List<Sentence> sentences = null;

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
   * Create a new document from the passed in text.
   * @param text The text of the document.
   */
  public Document(String text) {
    StanfordCoreNLP.getDefaultAnnotatorPool(EMPTY_PROPS, new AnnotatorImplementations());  // cache the annotator pool
    this.impl = CoreNLPProtos.Document.newBuilder().setText(text);
  }

  /**
   * Convert a CoreNLP Annotation object to a Document.
   * @param ann The CoreNLP Annotation object.
   */
  @SuppressWarnings("Convert2streamapi")
  public Document(Annotation ann) {
    StanfordCoreNLP.getDefaultAnnotatorPool(EMPTY_PROPS, new AnnotatorImplementations());  // cache the annotator pool
    this.impl = new ProtobufAnnotationSerializer(false).toProtoBuilder(ann);
    List<CoreMap> sentences = ann.get(CoreAnnotations.SentencesAnnotation.class);
    this.sentences = new ArrayList<>(sentences.size());
    for (CoreMap sentence : sentences) {
      this.sentences.add(new Sentence(this, this.serializer.toProtoBuilder(sentence), sentence.get(CoreAnnotations.TextAnnotation.class)));
    }
  }

  /**
   * Create a Document object from a read Protocol Buffer.
   * @see edu.stanford.nlp.simple.Document#serialize()
   * @param proto The protocol buffer representing this document.
   */
  @SuppressWarnings("Convert2streamapi")
  public Document(CoreNLPProtos.Document proto) {
    StanfordCoreNLP.getDefaultAnnotatorPool(EMPTY_PROPS, new AnnotatorImplementations());  // cache the annotator pool
    this.impl = proto.toBuilder();
    if (proto.getSentenceCount() > 0) {
      this.sentences = new ArrayList<>(proto.getSentenceCount());
      for (CoreNLPProtos.Sentence sentence : proto.getSentenceList()) {
        this.sentences.add(new Sentence(this, sentence.toBuilder()));
      }
    }
  }

  /**
   * Serialize this Document as a Protocol Buffer.
   * This can be deserialized with the constructor {@link Document#Document(edu.stanford.nlp.pipeline.CoreNLPProtos.Document)}.
   *
   * @return The document as represented by a Protocol Buffer.
   */
  public CoreNLPProtos.Document serialize() {
    synchronized (impl) {
      // Serialize sentences
      this.impl.clearSentence();
      for (Sentence sent : sentences()) {
        this.impl.addSentence(sent.serialize());
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
   *   String json = new Document("Lucy in the sky with diamonds").json(Document::parse, Document::ner);
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
      AnnotationOutputter.Options options = new AnnotationOutputter.Options();
      options.pretty = false;
      return new JSONOutputter().print(this.asAnnotation(), options);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  /**
   * <p>
   *  Write this annotation as an XML string.
   *  Optionally, you can also specify a number of operations to call on the document before
   *  dumping it to XML.
   *  This allows the user to ensure that certain annotations have been computed before the document
   *  is dumped.
   *  For example:
   * </p>
   *
   * <pre>{@code
   *   String xml = new Document("Lucy in the sky with diamonds").xml(Document::parse, Document::ner);
   * }</pre>
   *
   * <p>
   *   will create a XML dump of the document, ensuring that at least the parse tree and ner tags are populated.
   * </p>
   *
   * @param functions The (possibly empty) list of annotations to populate on the document before dumping it
   *                  to XML.
   * @return The XML String for this document.
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
      AnnotationOutputter.Options options = new AnnotationOutputter.Options();
      options.pretty = false;
      return new XMLOutputter().print(this.asAnnotation(), options);
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
    if (sentences == null) {
      // Get annotators
      Annotator tokenizer = props == EMPTY_PROPS ? defaultTokenize : AnnotatorFactories.tokenize(props, backend).create();
      Annotator ssplit = props == EMPTY_PROPS ? defaultSSplit : AnnotatorFactories.sentenceSplit(props, backend).create();
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
      for (CoreMap sentence : sentences) {
        //Sentence sent = new Sentence(this, sentence);
        Sentence sent = new Sentence(this, this.serializer.toProtoBuilder(sentence), sentence.get(CoreAnnotations.TextAnnotation.class));
        this.sentences.add(sent);
        this.impl.addSentence(sent.serialize());
      }
    }

    // Re-computing the sentences invalidates the cached Annotation
    synchronized (annotationPool) {
      annotationPool.remove(this);
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
        this.runLemma(props).runNER(props).runParse(props);  // default is rule mention annotator
        // Run mention
        Annotator mention = props == EMPTY_PROPS ? defaultMention.get() : getOrCreate(AnnotatorFactories.mention(props, backend));
        // Run coref
        Annotator coref = props == EMPTY_PROPS ? defaultCoref.get() : getOrCreate(AnnotatorFactories.coref(props, backend));
        Annotation ann = asAnnotation();
        mention.annotate(ann);
        coref.annotate(ann);
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
    return coref(EMPTY_PROPS);
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
  protected void forceSentences(List<Sentence> sentences) {
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

  protected Document runPOS(Properties props) {
    // Cached result
    if (this.sentences != null && this.sentences.size() > 0 && this.sentences.get(0).rawToken(0).hasPos()) {
      return this;
    }
    // Prerequisites
    sentences();
    // Run annotator
    Annotator pos = props == EMPTY_PROPS ? defaultPOS.get() : getOrCreate(AnnotatorFactories.posTag(props, backend));
    Annotation ann = asAnnotation();
    pos.annotate(ann);
    // Update data
    for (int i = 0; i < sentences.size(); ++i) {
      sentences.get(i).updateTokens(ann.get(CoreAnnotations.SentencesAnnotation.class).get(i).get(CoreAnnotations.TokensAnnotation.class), (pair) -> pair.first.setPos(pair.second), CoreLabel::tag);
    }
    return this;
  }

  protected Document runLemma(Properties props) {
    // Cached result
    if (this.sentences != null && this.sentences.size() > 0 && this.sentences.get(0).rawToken(0).hasLemma()) {
      return this;
    }
    // Prerequisites
    runPOS(props);
    // Run annotator
    Annotator lemma = props == EMPTY_PROPS ? defaultLemma : getOrCreate(AnnotatorFactories.lemma(props, backend));
    Annotation ann = asAnnotation();
    lemma.annotate(ann);
    // Update data
    for (int i = 0; i < sentences.size(); ++i) {
      sentences.get(i).updateTokens(ann.get(CoreAnnotations.SentencesAnnotation.class).get(i).get(CoreAnnotations.TokensAnnotation.class), (pair) -> pair.first.setLemma(pair.second), CoreLabel::lemma);
    }
    return this;
  }

  protected Document runNER(Properties props) {
    if (this.sentences != null && this.sentences.size() > 0 && this.sentences.get(0).rawToken(0).hasNer()) {
      return this;
    }
    // Run prerequisites
    runPOS(props);
    // Run annotator
    Annotator ner = props == EMPTY_PROPS ? defaultNER.get() : getOrCreate(AnnotatorFactories.nerTag(props, backend));
    Annotation ann = asAnnotation();
    ner.annotate(ann);
    // Update data
    for (int i = 0; i < sentences.size(); ++i) {
      sentences.get(i).updateTokens(ann.get(CoreAnnotations.SentencesAnnotation.class).get(i).get(CoreAnnotations.TokensAnnotation.class), (pair) -> pair.first.setNer(pair.second), CoreLabel::ner);
    }
    return this;
  }

  protected Document runRegexner(Properties props) {
    // Run prerequisites
    runNER(props);
    // Run annotator
    Annotator ner = props == EMPTY_PROPS ? defaultRegexner.get() : getOrCreate(AnnotatorFactories.regexNER(props, backend));
    Annotation ann = asAnnotation();
    ner.annotate(ann);
    // Update data
    for (int i = 0; i < sentences.size(); ++i) {
      sentences.get(i).updateTokens(ann.get(CoreAnnotations.SentencesAnnotation.class).get(i).get(CoreAnnotations.TokensAnnotation.class), (pair) -> pair.first.setNer(pair.second), CoreLabel::ner);
    }
    return this;
  }

  protected Document runParse(Properties props) {
    if (this.sentences != null && this.sentences.size() > 0 && this.sentences.get(0).rawSentence().hasParseTree()) {
      return this;
    }
    // Run annotator
    Annotator parse = props == EMPTY_PROPS ? defaultParse.get() : getOrCreate(AnnotatorFactories.parse(props, backend));
    if (parse.requires().contains(CoreAnnotations.PartOfSpeechAnnotation.class)) {
      runPOS(props);
    } else {
      sentences();
    }
    Annotation ann = asAnnotation();
    parse.annotate(ann);
    // Update data
    synchronized (serializer) {
      for (int i = 0; i < sentences.size(); ++i) {
        CoreMap sentence = ann.get(CoreAnnotations.SentencesAnnotation.class).get(i);
        Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
        sentences.get(i).updateParse(serializer.toProto(tree));
        sentences.get(i).updateDependencies(
            ProtobufAnnotationSerializer.toProto(sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class)),
            ProtobufAnnotationSerializer.toProto(sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class)),
            ProtobufAnnotationSerializer.toProto(sentence.get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class)));
      }
    }
    return this;
  }

  protected Document runDepparse(Properties props) {
    if (this.sentences != null && this.sentences.size() > 0 &&
        this.sentences.get(0).rawSentence().hasBasicDependencies()) {
      return this;
    }
    // Run prerequisites
    runPOS(props);
    // Run annotator
    Annotator depparse = props == EMPTY_PROPS ? defaultDepparse.get() : getOrCreate(AnnotatorFactories.dependencies(props, backend));
    Annotation ann = asAnnotation();
    depparse.annotate(ann);
    // Update data
    synchronized (serializer) {
      for (int i = 0; i < sentences.size(); ++i) {
        CoreMap sentence = ann.get(CoreAnnotations.SentencesAnnotation.class).get(i);
        sentences.get(i).updateDependencies(
            ProtobufAnnotationSerializer.toProto(sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class)),
            ProtobufAnnotationSerializer.toProto(sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class)),
            ProtobufAnnotationSerializer.toProto(sentence.get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class)));
      }
    }
    return this;
  }

  protected Document runNatlog(Properties props) {
    if (this.sentences != null && this.sentences.size() > 0 && this.sentences.get(0).rawToken(0).hasPolarity()) {
      return this;
    }
    // Run prerequisites
    runLemma(props);
    runDepparse(props);
    // Run annotator
    Annotator natlog = props == EMPTY_PROPS ? defaultNatlog.get() : getOrCreate(AnnotatorFactories.natlog(props, backend));
    Annotation ann = asAnnotation();
    natlog.annotate(ann);
    // Update data
    synchronized (serializer) {
      for (int i = 0; i < sentences.size(); ++i) {
        sentences.get(i).updateTokens(ann.get(CoreAnnotations.SentencesAnnotation.class).get(i).get(CoreAnnotations.TokensAnnotation.class), (pair) -> pair.first.setPolarity(ProtobufAnnotationSerializer.toProto(pair.second)), x -> x.get(NaturalLogicAnnotations.PolarityAnnotation.class));
        sentences.get(i).updateTokens(ann.get(CoreAnnotations.SentencesAnnotation.class).get(i).get(CoreAnnotations.TokensAnnotation.class), (pair) -> pair.first.setOperator(ProtobufAnnotationSerializer.toProto(pair.second)), x -> x.get(NaturalLogicAnnotations.OperatorAnnotation.class));
      }
    }
    return this;
  }

  protected Document runOpenie(Properties props) {
    if (haveRunOpenie) {
      return this;
    }
    // Run prerequisites
    runNatlog(props);
    // Run annotator
    Annotator openie = props == EMPTY_PROPS ? defaultOpenie.get() : getOrCreate(AnnotatorFactories.openie(props, backend));
    Annotation ann = asAnnotation();
    openie.annotate(ann);
    // Update data
    synchronized (serializer) {
      for (int i = 0; i < sentences.size(); ++i) {
        CoreMap sentence = ann.get(CoreAnnotations.SentencesAnnotation.class).get(i);
        Collection<RelationTriple> triples = sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
        sentences.get(i).updateOpenIE(triples.stream().map(ProtobufAnnotationSerializer::toProto));
      }
    }
    // Return
    haveRunOpenie = true;
    return this;
  }


  protected Document runKBP(Properties props) {
    if (haveRunOpenie) {
      return this;
    }
    // Run prerequisites
    coref(props);
    Annotator entityMention = props == EMPTY_PROPS ? defaultEntityMentions.get() : getOrCreate(AnnotatorFactories.entityMentions(props, backend));
    Annotation ann = asAnnotation();
    entityMention.annotate(ann);
    // Run annotator
    Annotator kbp = props == EMPTY_PROPS ? defaultKBP.get() : getOrCreate(AnnotatorFactories.kbp(props, backend));
    kbp.annotate(ann);
    // Update data
    synchronized (serializer) {
      for (int i = 0; i < sentences.size(); ++i) {
        CoreMap sentence = ann.get(CoreAnnotations.SentencesAnnotation.class).get(i);
        Collection<RelationTriple> triples = sentence.get(CoreAnnotations.KBPTriplesAnnotation.class);
        sentences.get(i).updateKBP(triples.stream().map(ProtobufAnnotationSerializer::toProto));
      }
    }
    // Return
    haveRunOpenie = true;
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
    synchronized (annotationPool) {
      Annotation candidate = annotationPool.get(this).get();
      if (candidate == null) {
        // Redo cache
        synchronized (serializer) {
          candidate = serializer.fromProto(serialize());
        }
        annotationPool.put(this, new SoftReference<>(candidate));
        // Clean up garbage collected annotations
        if (annotationPool.size() > 64) {
          new HashSet<>(annotationPool.keySet()).stream()
              .filter(key -> annotationPool.get(key).get() == null)
              .forEach(annotationPool::remove);
        }
        // Limit the size of the pool in general, if still too large
        // This shouldn't happen often...
        while (annotationPool.size() > 64) {
          annotationPool.remove(annotationPool.keySet().iterator().next());
        }
      }
      return candidate;
    }
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
        mentionSpan.append(" ").append(sentence.word(k));
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

  /**
   * Overwritten to clean up some memory from {@link Document#annotationPool}.
   * As always, don't actually rely on this being called ever.
   */
  @Override
  protected void finalize() throws Throwable {
    super.finalize();
    synchronized (annotationPool) {
      annotationPool.remove(this);
    }
  }

}
