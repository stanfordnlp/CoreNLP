package edu.stanford.nlp.simple;

import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.naturalli.OperatorSpec;
import edu.stanford.nlp.naturalli.Polarity;
import edu.stanford.nlp.naturalli.SentenceFragment;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.pipeline.CoreNLPProtos;
import edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;


/**
 * A representation of a single Sentence.
 * Although it is possible to create a sentence directly from text, it is advisable to
 * create a document instead and operate on the document directly.
 *
 * @author Gabor Angeli
 */
@SuppressWarnings({"UnusedDeclaration", "WeakerAccess"})
public class Sentence {

  /** A Properties object for creating a document from a single sentence. Used in the constructor {@link Sentence#Sentence(String)} */
  static Properties SINGLE_SENTENCE_DOCUMENT = PropertiesUtils.asProperties(
          "language", "english",
          "ssplit.isOneSentence", "true",
          "tokenize.class", "PTBTokenizer",
          "tokenize.language", "en",
          "mention.type", "dep",
          "coref.mode", "statistical",  // Use the new coref
          "coref.md.type", "dep"
  );

  /** A Properties object for creating a document from a single tokenized sentence. */
  private static Properties SINGLE_SENTENCE_TOKENIZED_DOCUMENT = PropertiesUtils.asProperties(
          "language", "english",
          "ssplit.isOneSentence", "true",
          "tokenize.class", "WhitespaceTokenizer",
          "tokenize.language", "en",
          "tokenize.whitespace", "true",
          "mention.type", "dep",
          "coref.mode", "statistical",  // Use the new coref
          "coref.md.type", "dep"
  );  // redundant?

  /**
   *  The protobuf representation of a Sentence.
   *  Note that this does not necessarily have up to date token information.
   */
  private final CoreNLPProtos.Sentence.Builder impl;
  /** The protobuf representation of the tokens of a sentence. This has up-to-date information on the tokens */
  private final List<CoreNLPProtos.Token.Builder> tokensBuilders;
  /** The document this sentence is derived from */
  public final Document document;
  /** The default properties to use for annotators. */
  private final Properties defaultProps;
  /** The function to use to create a new document. This is used for the cased() and caseless() functions. */
  private final BiFunction<Properties, String, Document> docFn;

  /**
   * Create a new sentence, using the specified properties as the default properties.
   * @param doc The document to link this sentence to.
   * @param props The properties to use for tokenizing the sentence.
   */
  protected Sentence(Document doc, Properties props) {
    // Set document
    this.document = doc;
    // Set sentence
    if (props.containsKey("ssplit.isOneSentence")) {
      this.impl = this.document.sentence(0, props).impl;
    } else {
      Properties modProps = new Properties(props);
      modProps.setProperty("ssplit.isOneSentence", "true");
      this.impl = this.document.sentence(0, modProps).impl;
    }
    // Set tokens
    this.tokensBuilders = document.sentence(0).tokensBuilders;
    // Asserts
    assert (this.document.sentence(0).impl == this.impl);
    assert (this.document.sentence(0).tokensBuilders == this.tokensBuilders);
    // Set the default properties
    if (props == SINGLE_SENTENCE_TOKENIZED_DOCUMENT) {
      this.defaultProps = SINGLE_SENTENCE_DOCUMENT;  // no longer care about tokenization
    } else {
      this.defaultProps = props;
    }
    this.docFn = Document::new;
  }

  /**
   * Create a new sentence from some text, and some properties.
   * @param text The text of the sentence.
   * @param props The properties to use for the annotators.
   */
  public Sentence(String text, Properties props) {
    this(new Document(props, text), props);
  }

  /**
   * Create a new sentence from the given text, assuming the entire text is just one sentence.
   * @param text The text of the sentence.
   */
  public Sentence(String text) {
    this(text, SINGLE_SENTENCE_DOCUMENT);
  }


  /** The actual implementation of a tokenized sentence constructor */
  protected Sentence(Function<String, Document> doc, List<String> tokens, Properties props) {
    this(doc.apply(StringUtils.join(tokens.stream().map(x -> x.replace(' ', 'ߝ' /* some random character */)), " ")), props);
    // Clean up whitespace
    for (int i = 0; i < impl.getTokenCount(); ++i) {
      this.impl.getTokenBuilder(i).setWord(this.impl.getTokenBuilder(i).getWord().replace('ߝ', ' '));
      this.impl.getTokenBuilder(i).setValue(this.impl.getTokenBuilder(i).getValue().replace('ߝ', ' '));
      this.tokensBuilders.get(i).setWord(this.tokensBuilders.get(i).getWord().replace('ߝ', ' '));
      this.tokensBuilders.get(i).setValue(this.tokensBuilders.get(i).getValue().replace('ߝ', ' '));
    }
  }


  /**
   * Create a new sentence from the given tokenized text, assuming the entire text is just one sentence.
   * WARNING: This method may in rare cases (mostly when tokens themselves have whitespace in them)
   *          produce strange results; it's a bit of a hack around the default tokenizer.
   *
   * @param tokens The text of the sentence.
   */
  public Sentence(List<String> tokens) {
    this(Document::new, tokens, SINGLE_SENTENCE_TOKENIZED_DOCUMENT);
  }

  /**
   * Create a sentence from a saved protocol buffer.
   */
  protected Sentence(BiFunction<Properties, String, Document> docFn, CoreNLPProtos.Sentence proto, Properties props) {
    this.impl = proto.toBuilder();
    // Set tokens
    tokensBuilders = new ArrayList<>(this.impl.getTokenCount());
    for (int i = 0; i < this.impl.getTokenCount(); ++i) {
      tokensBuilders.add(this.impl.getToken(i).toBuilder());
    }
    // Initialize document
    this.document = docFn.apply(props, proto.getText());
    this.document.forceSentences(Collections.singletonList(this));
    // Asserts
    assert (this.document.sentence(0).impl == this.impl);
    assert (this.document.sentence(0).tokensBuilders == this.tokensBuilders);
    // Set default props
    this.defaultProps = props;
    this.docFn = docFn;
  }

  /**
   * Create a sentence from a saved protocol buffer.
   */
  public Sentence(CoreNLPProtos.Sentence proto) {
    this(Document::new, proto, SINGLE_SENTENCE_DOCUMENT);

  }

  /** Helper for creating a sentence from a document at a given index */
  protected Sentence(Document doc, int sentenceIndex) {
    this.document = doc;
    this.impl = doc.sentence(sentenceIndex).impl;
    // Set tokens
    this.tokensBuilders = doc.sentence(sentenceIndex).tokensBuilders;
    // Asserts
    assert (this.document.sentence(sentenceIndex).impl == this.impl);
    assert (this.document.sentence(sentenceIndex).tokensBuilders == this.tokensBuilders);
    // Set default props
    this.defaultProps = Document.EMPTY_PROPS;
    this.docFn = doc.sentence(sentenceIndex).docFn;
  }

  /**
   * The canonical constructor of a sentence from a {@link edu.stanford.nlp.simple.Document}.
   * @param doc The document to link this sentence to.
   * @param proto The sentence implementation to use for this sentence.
   */
  protected Sentence(Document doc, CoreNLPProtos.Sentence.Builder proto, Properties defaultProps) {
    this.document = doc;
    this.impl = proto;
    this.defaultProps = defaultProps;
    // Set tokens
    // This is the _only_ place we are allowed to construct tokens builders
    tokensBuilders = new ArrayList<>(this.impl.getTokenCount());
    for (int i = 0; i < this.impl.getTokenCount(); ++i) {
      tokensBuilders.add(this.impl.getToken(i).toBuilder());
    }
    this.docFn = (props, text) -> MetaClass.create(doc.getClass().getName()).createInstance(props, text);
  }

  /**
   * Also sets the the text of the sentence. Used by {@link Document} internally
   *
   * @param doc The document to link this sentence to.
   * @param proto The sentence implementation to use for this sentence.
   * @param text The text for the sentence
   * @param defaultProps The default properties to use when annotating this sentence.
   */
  Sentence(Document doc, CoreNLPProtos.Sentence.Builder proto, String text, Properties defaultProps) {
    this(doc, proto, defaultProps);
    this.impl.setText(text);
  }

  /** Helper for creating a sentence from a document and a CoreMap representation */
  protected Sentence(Document doc, CoreMap sentence) {
    this.document = doc;
    assert ! doc.sentences().isEmpty();
    this.impl = doc.sentence(0).impl;
    this.tokensBuilders = doc.sentence(0).tokensBuilders;
    this.defaultProps = Document.EMPTY_PROPS;
    this.docFn = (props, text) -> MetaClass.create(doc.getClass().getName()).createInstance(props, text);
  }

  /**
   * Convert a CoreMap into a simple Sentence object.
   * Note that this is a copy operation -- the implementing CoreMap will not be updated, and all of its
   * contents are copied over to the protocol buffer format backing the {@link Sentence} object.
   *
   * @param sentence The CoreMap representation of the sentence.
   */
  public Sentence(CoreMap sentence) {
    this(new Document(new Annotation(sentence.get(CoreAnnotations.TextAnnotation.class)) {{
      set(CoreAnnotations.SentencesAnnotation.class, Collections.singletonList(sentence));
      if (sentence.containsKey(CoreAnnotations.DocIDAnnotation.class)) {
        set(CoreAnnotations.DocIDAnnotation.class, sentence.get(CoreAnnotations.DocIDAnnotation.class));
      }
    }}), sentence);
  }

  /**
   * Convert a sentence fragment (i.e., entailed sentence) into a simple sentence object.
   * Like {@link Sentence#Sentence(CoreMap)}, this copies the information in the fragment into the underlying
   * protobuf backed format.
   *
   * @param sentence The sentence fragment to convert.
   */
  public Sentence(SentenceFragment sentence) {
    this(new ArrayCoreMap(32) {{
      set(CoreAnnotations.TokensAnnotation.class, sentence.words);
      set(CoreAnnotations.TextAnnotation.class, StringUtils.join(sentence.words.stream().map(CoreLabel::originalText), " "));
      if (sentence.words.isEmpty()) {
        set(CoreAnnotations.TokenBeginAnnotation.class, 0);
        set(CoreAnnotations.TokenEndAnnotation.class, 0);
      } else {
        set(CoreAnnotations.TokenBeginAnnotation.class, sentence.words.get(0).get(CoreAnnotations.IndexAnnotation.class));
        set(CoreAnnotations.TokenEndAnnotation.class, sentence.words.get(sentence.words.size() - 1).get(CoreAnnotations.IndexAnnotation.class) + 1);
      }
      set(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class, sentence.parseTree);
      set(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class, sentence.parseTree);
      set(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class, sentence.parseTree);
    }});
  }


  /**
   * Make this sentence caseless. That is, from now on, run the caseless models
   * on the sentence by default rather than the standard CoreNLP models.
   *
   * @return A new sentence with the default properties swapped out.
   */
  public Sentence caseless() {
    return new Sentence(this.docFn, impl.build(), Document.CASELESS_PROPS);
  }

  /**
   * Make this sentence case sensitive.
   * A sentence is case sensitive by default; this only has an effect if you have previously
   * called {@link Sentence#caseless()}.
   *
   * @return A new sentence with the default properties swapped out.
   */
  public Sentence cased() {
    return new Sentence(this.docFn, impl.build(), Document.EMPTY_PROPS);
  }


  /**
   * Serialize the given sentence (but not the associated document!) into a Protocol Buffer.
   *
   * @return The Protocol Buffer representing this sentence.
   */
  public CoreNLPProtos.Sentence serialize() {
    synchronized (impl) {
      this.impl.clearToken();
      for (CoreNLPProtos.Token.Builder token : this.tokensBuilders) {
        this.impl.addToken(token.build());
      }
      return impl.build();
    }
  }

  /**
   * Write this sentence to an output stream.
   * Internally, this stores the sentence as a protocol buffer, and saves that buffer to the output stream.
   * This method does not close the stream after writing.
   *
   * @param out The output stream to write to. The stream is not closed after the method returns.
   * @throws IOException Thrown from the underlying write() implementation.
   */
  public void serialize(OutputStream out) throws IOException {
    serialize().writeDelimitedTo(out);
    out.flush();
  }

  /**
   * Read a sentence from an input stream.
   * This does not close the input stream.
   *
   * @param in The input stream to deserialize from.
   * @return The next sentence encoded in the input stream.
   * @throws IOException Thrown by the underlying parse() implementation.
   *
   * @see Document#serialize(java.io.OutputStream)
   */
  public static Sentence deserialize(InputStream in) throws IOException {
    return new Sentence(CoreNLPProtos.Sentence.parseDelimitedFrom(in));
  }

  /**
   * Return a class that can perform common algorithms on this sentence.
   */
  public SentenceAlgorithms algorithms() {
    return new SentenceAlgorithms(this);
  }

  /** The raw text of the sentence, as input by, e.g., {@link Sentence#Sentence(String)}. */
  public String text() {
    synchronized (impl) {
      return impl.getText();
    }
  }

  //
  // SET AXIOMATICALLY
  //

  /** The index of the sentence within the document. */
  public int sentenceIndex() {
    synchronized (impl) {
      return impl.getSentenceIndex();
    }
  }

  /** THe token offset of the sentence within the document. */
  public int sentenceTokenOffsetBegin() {
    synchronized (impl) {
      return impl.getTokenOffsetBegin();
    }
  }

  /** The token offset of the end of this sentence within the document. */
  public int sentenceTokenOffsetEnd() {
    synchronized (impl) {
      return impl.getTokenOffsetEnd();
    }
  }

  //
  // SET BY TOKENIZER
  //

  /** The words of the sentence, as per {@link edu.stanford.nlp.ling.CoreLabel#word()}. */
  public List<String> words() {
    synchronized (impl) {
      return lazyList(tokensBuilders, CoreNLPProtos.Token.Builder::getWord);
    }
  }

  /** The word at the given index of the sentence. @see Sentence#words() */
  public String word(int index) {
    return words().get(index);
  }

  /** The original (unprocessed) words of the sentence, as per {@link edu.stanford.nlp.ling.CoreLabel#originalText()}. */
  public List<String> originalTexts() {
    synchronized (impl) {
      return lazyList(tokensBuilders, CoreNLPProtos.Token.Builder::getOriginalText);
    }
  }

  /** The original word at the given index. @see Sentence#originalTexts() */
  public String originalText(int index) {
    return originalTexts().get(index);
  }

  /** The character offset of each token in the sentence, as per {@link edu.stanford.nlp.ling.CoreLabel#beginPosition()}. */
  public List<Integer> characterOffsetBegin() {
    synchronized (impl) {
      return lazyList(tokensBuilders, CoreNLPProtos.Token.Builder::getBeginChar);
    }
  }

  /** The character offset of the given index in the sentence. @see Sentence#characterOffsetBegin(). */
  public int characterOffsetBegin(int index) {
    return characterOffsetBegin().get(index);
  }

  /** The end character offset of each token in the sentence, as per {@link edu.stanford.nlp.ling.CoreLabel#endPosition()}. */
  public List<Integer> characterOffsetEnd() {
    synchronized (impl) {
      return lazyList(tokensBuilders, CoreNLPProtos.Token.Builder::getEndChar);
    }
  }

  /** The end character offset of the given index in the sentence. @see Sentence#characterOffsetEnd(). */
  public int characterOffsetEnd(int index) {
    return characterOffsetEnd().get(index);
  }


  /** The whitespace before each token in the sentence. This will match {@link #after()} of the previous token. */
  public List<String> before() {
    synchronized (impl) {
      return lazyList(tokensBuilders, CoreNLPProtos.Token.Builder::getBefore);
    }
  }


  /** The whitespace before this token in the sentence. This will match {@link #after()} of the previous token. */
  public String before(int index) {
    return before().get(index);
  }


  /** The whitespace after each token in the sentence. This will match {@link #before()} of the next token. */
  public List<String> after() {
    synchronized (impl) {
      return lazyList(tokensBuilders, CoreNLPProtos.Token.Builder::getAfter);
    }
  }


  /** The whitespace after this token in the sentence. This will match {@link #before()} of the next token. */
  public String after(int index) {
    return after().get(index);
  }


  /** The tokens in this sentence. Each token class is just a helper for the methods in this class. */
  public List<Token> tokens() {
    ArrayList<Token> tokens = new ArrayList<>(this.length());
    for (int i = 0; i < length(); ++i) {
      tokens.add(new Token(this, i));
    }
    return tokens;
  }


  //
  // SET BY ANNOTATORS
  //

  /**
   * The part of speech tags of the sentence.
   * @param props The properties to use for the {@link edu.stanford.nlp.pipeline.POSTaggerAnnotator}.
   * @return A list of part of speech tags, one for each token in the sentence.
   */
  public List<String> posTags(Properties props) {
    document.runPOS(props);
    synchronized (impl) {
      return lazyList(tokensBuilders, CoreNLPProtos.Token.Builder::getPos);
    }
  }

  /** @see Sentence#posTags(java.util.Properties) */
  public List<String> posTags() {
    return posTags(this.defaultProps);
  }

  /** @see Sentence#posTags(java.util.Properties) */
  public String posTag(int index) {
    return posTags().get(index);
  }

  /**
   * The lemmas of the sentence.
   * @param props The properties to use for the {@link edu.stanford.nlp.pipeline.MorphaAnnotator}.
   * @return A list of lemmatized words, one for each token in the sentence.
   */
  public List<String> lemmas(Properties props) {
    document.runLemma(props);
    synchronized (impl) {
      return lazyList(tokensBuilders, CoreNLPProtos.Token.Builder::getLemma);
    }
  }

  /** @see Sentence#lemmas(java.util.Properties) */
  public List<String> lemmas() {
    return lemmas(this.defaultProps);
  }

  /** @see Sentence#lemmas(java.util.Properties) */
  public String lemma(int index) {
    return lemmas().get(index);
  }

  /**
   * The named entity tags of the sentence.
   * @param props The properties to use for the {@link edu.stanford.nlp.pipeline.NERCombinerAnnotator}.
   * @return A list of named entity tags, one for each token in the sentence.
   */
  public List<String> nerTags(Properties props) {
    document.runNER(props);
    synchronized (impl) {
      return lazyList(tokensBuilders, CoreNLPProtos.Token.Builder::getNer);
    }
  }

  /** @see Sentence#nerTags(java.util.Properties) */
  public List<String> nerTags() {
    return nerTags(this.defaultProps);
  }

  /**
   * Run RegexNER over this sentence. Note that this is an in place operation, and simply
   * updates the NER tags.
   * Therefore, every time this function is called, it re-runs the annotator!
   *
   * @param mappingFile The regexner mapping file.
   * @param ignorecase If true, run a caseless match on the regexner file.
   */
  public void regexner(String mappingFile, boolean ignorecase) {
    Properties props = new Properties();
    for (Object prop : this.defaultProps.keySet()) {
      props.setProperty(prop.toString(), this.defaultProps.getProperty(prop.toString()));
    }
    props.setProperty(Annotator.STANFORD_REGEXNER + ".mapping", mappingFile);
    props.setProperty(Annotator.STANFORD_REGEXNER + ".ignorecase", Boolean.toString(ignorecase));
    this.document.runRegexner(props);
  }

  /** @see Sentence#nerTags(java.util.Properties) */
  public String nerTag(int index) {
    return nerTags().get(index);
  }

  /**
   * Get all mentions of the given NER tag, as a list of surface forms.
   * @param nerTag The ner tag to search for, case sensitive.
   * @return A list of surface forms of the entities of this tag. This is using the {@link Sentence#word(int)} function.
   */
  public List<String> mentions(String nerTag) {
    List<String> mentionsOfTag = new ArrayList<>();
    StringBuilder lastMention = new StringBuilder();
    String lastTag = "O";
    for (int i = 0; i < length(); ++i) {
      String ner = nerTag(i);
      if (ner.equals(nerTag) && !lastTag.equals(nerTag)) {
        // case: beginning of span
        lastMention.append(word(i)).append(' ');
      } else if (ner.equals(nerTag) && lastTag.equals(nerTag)) {
        // case: in span
        lastMention.append(word(i)).append(' ');
      } else if (!ner.equals(nerTag) && lastTag.equals(nerTag)) {
        // case: end of span
        if (lastMention.length() > 0) {
          mentionsOfTag.add(lastMention.toString().trim());
        }
        lastMention.setLength(0);
      }
      lastTag = ner;
    }
    if (lastMention.length() > 0) {
      mentionsOfTag.add(lastMention.toString().trim());
    }
    return mentionsOfTag;
  }

  /**
   * Get all mentions of any NER tag, as a list of surface forms.
   * @return A list of surface forms of the entities in this sentence. This is using the {@link Sentence#word(int)} function.
   */
  public List<String> mentions() {
    List<String> mentionsOfTag = new ArrayList<>();
    StringBuilder lastMention = new StringBuilder();
    String lastTag = "O";
    for (int i = 0; i < length(); ++i) {
      String ner = nerTag(i);
      if (!ner.equals("O") && !lastTag.equals(ner)) {
        // case: beginning of span
        if (lastMention.length() > 0) {
          mentionsOfTag.add(lastMention.toString().trim());
        }
        lastMention.setLength(0);
        lastMention.append(word(i)).append(' ');
      } else if (!ner.equals("O") && lastTag.equals(ner)) {
        // case: in span
        lastMention.append(word(i)).append(' ');
      } else if (ner.equals("O") && !lastTag.equals("O")) {
        // case: end of span
        if (lastMention.length() > 0) {
          mentionsOfTag.add(lastMention.toString().trim());
        }
        lastMention.setLength(0);
      }
      lastTag = ner;
    }
    if (lastMention.length() > 0) {
      mentionsOfTag.add(lastMention.toString().trim());
    }
    return mentionsOfTag;
  }

  /**
   * Returns the constituency parse of this sentence.
   *
   * @param props The properties to use in the parser annotator.
   * @return A parse tree object.
   */
  public Tree parse(Properties props) {
    document.runParse(props);
    synchronized (document.serializer) {
      return document.serializer.fromProto(impl.getParseTree());
    }
  }

  /** @see Sentence#parse(java.util.Properties) */
  public Tree parse() {
    return parse(this.defaultProps);
  }


  /** An internal helper to get the dependency tree of the given type. */
  private CoreNLPProtos.DependencyGraph dependencies(SemanticGraphFactory.Mode mode) {
    switch (mode) {
      case BASIC:
        return impl.getBasicDependencies();
      case ENHANCED:
        return impl.getEnhancedDependencies();
      case ENHANCED_PLUS_PLUS:
        return impl.getEnhancedPlusPlusDependencies();
      default:
        throw new IllegalArgumentException("Unsupported dependency type: " + mode);
    }
  }

  /**
   * Returns the governor of the given index, according to the passed dependency type.
   * The root has index -1.
   *
   * @param props The properties to use in the parser annotator.
   * @param index The index of the dependent word ZERO INDEXED. That is, the first word of the sentence
   *              is index 0, not 1 as it would be in the {@link edu.stanford.nlp.semgraph.SemanticGraph} framework.
   * @param mode  The type of dependency to use (e.g., basic, collapsed, collapsed cc processed).
   * @return The index of the governor, if one exists. A value of -1 indicates the root node.
   */
  public Optional<Integer> governor(Properties props, int index, SemanticGraphFactory.Mode mode) {
    document.runDepparse(props);
    for (CoreNLPProtos.DependencyGraph.Edge edge : dependencies(mode).getEdgeList()) {
      if (edge.getTarget() - 1 == index) {
        return Optional.of(edge.getSource() - 1);
      }
    }
    for (int root : impl.getBasicDependencies().getRootList()) {
      if (index == root - 1) { return Optional.of(-1); }
    }
    return Optional.empty();
  }

  /** @see Sentence#governor(java.util.Properties, int, SemanticGraphFactory.Mode) */
  public Optional<Integer> governor(Properties props, int index) {
    return governor(props, index, SemanticGraphFactory.Mode.ENHANCED);
  }

  /** @see Sentence#governor(java.util.Properties, int, SemanticGraphFactory.Mode) */
  public Optional<Integer> governor(int index, SemanticGraphFactory.Mode mode) {
    return governor(this.defaultProps, index, mode);
  }

  /** @see Sentence#governor(java.util.Properties, int) */
  public Optional<Integer> governor(int index) {
    return governor(this.defaultProps, index);
  }

  /**
   * Returns the governors of a sentence, according to the passed dependency type.
   * The resulting list is of the same size as the original sentence, with each element being either
   * the governor (index), or empty if the node has no known governor.
   * The root has index -1.
   *
   * @param props The properties to use in the parser annotator.
   * @param mode  The type of dependency to use (e.g., basic, collapsed, collapsed cc processed).
   * @return A list of the (optional) governors of each token in the sentence.
   */
  public List<Optional<Integer>> governors(Properties props, SemanticGraphFactory.Mode mode) {
    document.runDepparse(props);
    List<Optional<Integer>> governors = new ArrayList<>(this.length());
    for (int i = 0; i < this.length(); ++i) { governors.add(Optional.empty()); }
    for (CoreNLPProtos.DependencyGraph.Edge edge : dependencies(mode).getEdgeList()) {
      governors.set(edge.getTarget() - 1, Optional.of(edge.getSource() - 1));
    }
    for (int root : impl.getBasicDependencies().getRootList()) {
      governors.set(root - 1, Optional.of(-1));
    }
    return governors;
  }

  /** @see Sentence#governors(java.util.Properties, SemanticGraphFactory.Mode) */
  public List<Optional<Integer>> governors(Properties props) {
    return governors(props, SemanticGraphFactory.Mode.ENHANCED);
  }

  /** @see Sentence#governors(java.util.Properties, SemanticGraphFactory.Mode) */
  public List<Optional<Integer>> governors(SemanticGraphFactory.Mode mode) {
    return governors(this.defaultProps, mode);
  }

  /** @see Sentence#governors(java.util.Properties, SemanticGraphFactory.Mode) */
  public List<Optional<Integer>> governors() {
    return governors(this.defaultProps, SemanticGraphFactory.Mode.ENHANCED);
  }

  /**
   * Returns the incoming dependency label to a particular index, according to the Basic Dependencies.
   *
   * @param props The properties to use in the parser annotator.
   * @param index The index of the dependent word ZERO INDEXED. That is, the first word of the sentence
   *              is index 0, not 1 as it would be in the {@link edu.stanford.nlp.semgraph.SemanticGraph} framework.
   * @param mode  The type of dependency to use (e.g., basic, collapsed, collapsed cc processed).
   * @return The incoming dependency label, if it exists.
   */
  public Optional<String> incomingDependencyLabel(Properties props, int index, SemanticGraphFactory.Mode mode) {
    document.runDepparse(props);
    for (CoreNLPProtos.DependencyGraph.Edge edge : dependencies(mode).getEdgeList()) {
      if (edge.getTarget() - 1 == index) {
        return Optional.of(edge.getDep());
      }
    }
    for (int root : impl.getBasicDependencies().getRootList()) {
      if (index == root - 1) { return Optional.of("root"); }
    }
    return Optional.empty();
  }

  /** @see Sentence#incomingDependencyLabel(java.util.Properties, int, SemanticGraphFactory.Mode) */
  public Optional<String> incomingDependencyLabel(Properties props, int index) {
    return incomingDependencyLabel(props, index, SemanticGraphFactory.Mode.ENHANCED);
  }

  /** @see Sentence#incomingDependencyLabel(java.util.Properties, int, SemanticGraphFactory.Mode) */
  public Optional<String> incomingDependencyLabel(int index, SemanticGraphFactory.Mode mode) {
    return incomingDependencyLabel(this.defaultProps, index, mode);
  }

  /** @see Sentence#incomingDependencyLabel(java.util.Properties, int) */
  public Optional<String> incomingDependencyLabel(int index) {
    return incomingDependencyLabel(this.defaultProps, index);
  }

  /** @see Sentence#incomingDependencyLabel(java.util.Properties, int) */
  public List<Optional<String>> incomingDependencyLabels(Properties props, SemanticGraphFactory.Mode mode) {
    document.runDepparse(props);
    List<Optional<String>> labels = new ArrayList<>(this.length());
    for (int i = 0; i < this.length(); ++i) { labels.add(Optional.empty()); }
    for (CoreNLPProtos.DependencyGraph.Edge edge : dependencies(mode).getEdgeList()) {
      labels.set(edge.getTarget() - 1, Optional.of(edge.getDep()));
    }
    for (int root : impl.getBasicDependencies().getRootList()) {
      labels.set(root - 1, Optional.of("root"));
    }
    return labels;
  }

  /** @see Sentence#incomingDependencyLabels(java.util.Properties, SemanticGraphFactory.Mode) */
  public List<Optional<String>> incomingDependencyLabels(SemanticGraphFactory.Mode mode) {
    return incomingDependencyLabels(this.defaultProps, mode);
  }

  /** @see Sentence#incomingDependencyLabels(java.util.Properties, SemanticGraphFactory.Mode) */
  public List<Optional<String>> incomingDependencyLabels(Properties props) {
    return incomingDependencyLabels(props, SemanticGraphFactory.Mode.ENHANCED);
  }

  /** @see Sentence#incomingDependencyLabels(java.util.Properties, SemanticGraphFactory.Mode) */
  public List<Optional<String>> incomingDependencyLabels() {
    return incomingDependencyLabels(this.defaultProps, SemanticGraphFactory.Mode.ENHANCED);
  }


  /**
   * Returns the dependency graph of the sentence, as a raw {@link SemanticGraph} object.
   * Note that this method is slower than you may expect, as it has to convert the underlying protocol
   * buffer back into a list of CoreLabels with which to populate the {@link SemanticGraph}.
   *
   * @param props The properties to use for running the dependency parser annotator.
   * @param mode The type of graph to return (e.g., basic, collapsed, etc).
   *
   * @return The dependency graph of the sentence.
   */
  public SemanticGraph dependencyGraph(Properties props, SemanticGraphFactory.Mode mode) {
    document.runDepparse(props);
    return ProtobufAnnotationSerializer.fromProto(dependencies(mode), asCoreLabels(), document.docid().orElse(null));
  }

  /** @see Sentence#dependencyGraph(Properties, SemanticGraphFactory.Mode) */
  public SemanticGraph dependencyGraph(Properties props) {
    return dependencyGraph(props, SemanticGraphFactory.Mode.ENHANCED);
  }

  /** @see Sentence#dependencyGraph(Properties, SemanticGraphFactory.Mode) */
  public SemanticGraph dependencyGraph() {
    return dependencyGraph(this.defaultProps, SemanticGraphFactory.Mode.ENHANCED);
  }

  /** @see Sentence#dependencyGraph(Properties, SemanticGraphFactory.Mode) */
  public SemanticGraph dependencyGraph(SemanticGraphFactory.Mode mode) {
    return dependencyGraph(this.defaultProps, mode);
  }

  /** The length of the sentence, in tokens */
  public int length() {
    return impl.getTokenCount();
  }

  /**
   * Get a list of the (possible) Natural Logic operators on each node of the sentence.
   * At each index, the list contains an operator spec if that index is the head word of an operator in the
   * sentence.
   *
   * @param props The properties to pass to the natural logic annotator.
   * @return A list of Optionals, where each element corresponds to a token in the sentence, and the optional is nonempty
   *         if that index is an operator.
   */
  public List<Optional<OperatorSpec>> operators(Properties props) {
    document.runNatlog(props);
    synchronized (impl) {
      return lazyList(tokensBuilders, x -> x.hasOperator() ? Optional.of(ProtobufAnnotationSerializer.fromProto(x.getOperator())) : Optional.empty());
    }
  }

  /** @see Sentence#operators(Properties) */
  public List<Optional<OperatorSpec>> operators() {
    return operators(this.defaultProps);
  }

  /** @see Sentence#operators(Properties) */
  public Optional<OperatorSpec> operatorAt(Properties props, int i) {
    return operators(props).get(i);
  }


  /** @see Sentence#operators(Properties) */
  public Optional<OperatorSpec> operatorAt(int i) {
    return operators(this.defaultProps).get(i);
  }

  /**
   * Returns the list of non-empty Natural Logic operator specifications.
   * This amounts to the actual list of operators in the sentence.
   * Note that the spans of the operators can be retrieved with
   * {@link OperatorSpec#quantifierBegin} and
   * {@link OperatorSpec#quantifierEnd}.
   *
   * @param props The properties to use for the natlog annotator.
   * @return A list of operators in the sentence.
   */
  public List<OperatorSpec> operatorsNonempty(Properties props) {
    return operators(props).stream().filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList());
  }

  /** @see Sentence#operatorsNonempty(Properties) */
  public List<OperatorSpec> operatorsNonempty() {
    return operatorsNonempty(this.defaultProps);
  }

  /**
   * The Natural Logic notion of polarity for each token in a sentence.
   * @param props The properties to use for the natural logic annotator.
   * @return A list of Polarity objects, one for each token of the sentence.
   */
  public List<Polarity> natlogPolarities(Properties props) {
    document.runNatlog(props);
    synchronized (impl) {
      return lazyList(tokensBuilders, x -> ProtobufAnnotationSerializer.fromProto(x.getPolarity()));
    }
  }

  /** @see Sentence#natlogPolarities(Properties) */
  public List<Polarity> natlogPolarities() {
    return natlogPolarities(this.defaultProps);
  }

  /**
   * Get the polarity (the Natural Logic notion of polarity) for a given token in the sentence.
   * @param props The properties to use for the natural logic annotator.
   * @param index The index to return the polarity of.
   * @return A list of Polarity objects, one for each token of the sentence.
   */
  public Polarity natlogPolarity(Properties props, int index) {
    document.runNatlog(props);
    synchronized (impl) {
      return ProtobufAnnotationSerializer.fromProto(tokensBuilders.get(index).getPolarity());
    }
  }

  /** @see Sentence#natlogPolarity(Properties, int) */
  public Polarity natlogPolarity(int index) {
    return natlogPolarity(this.defaultProps, index);
  }


  /**
   * Get the OpenIE triples associated with this sentence.
   * Note that this function may be slower than you would expect, as it has to
   * convert the underlying Protobuf representation back into {@link CoreLabel}s.
   *
   * @param props The properties to use for the OpenIE annotator.
   * @return A collection of {@link RelationTriple} objects representing the OpenIE triples in the sentence.
   */
  public Collection<RelationTriple> openieTriples(Properties props) {
    document.runOpenie(props);
    synchronized (impl) {
      List<CoreLabel> tokens = asCoreLabels();
      Annotation doc = document.asAnnotation();
      return impl.getOpenieTripleList().stream().map(x -> ProtobufAnnotationSerializer.fromProto(x, doc, this.sentenceIndex())).collect(Collectors.toList());
    }
  }

  /** @see Sentence@openieTriples(Properties) */
  public Collection<RelationTriple> openieTriples() {
    return openieTriples(this.defaultProps);
  }

  /**
   * Get a list of Open IE triples as flat (subject, relation, object, confidence) quadruples.
   * This is substantially faster than returning {@link RelationTriple} objects, as it doesn't
   * require converting the underlying representation into {@link CoreLabel}s; but, it also contains
   * significantly less information about the sentence.
   *
   * @see Sentence@openieTriples(Properties)
   */
  public Collection<Quadruple<String, String, String, Double>> openie() {
    document.runOpenie(this.defaultProps);
    return impl.getOpenieTripleList().stream()
        .filter(proto -> proto.hasSubject() && proto.hasRelation() && proto.hasObject())
        .map(proto -> Quadruple.makeQuadruple(proto.getSubject(), proto.getRelation(), proto.getObject(),
            proto.hasConfidence() ? proto.getConfidence() : 1.0))
        .collect(Collectors.toList());
  }


  /**
   * Get the KBP triples associated with this sentence.
   * Note that this function may be slower than you would expect, as it has to
   * convert the underlying Protobuf representation back into {@link CoreLabel}s.
   *
   * @param props The properties to use for the KBP annotator.
   * @return A collection of {@link RelationTriple} objects representing the KBP triples in the sentence.
   */
  public Collection<RelationTriple> kbpTriples(Properties props) {
    document.runKBP(props);
    synchronized (impl) {
      List<CoreLabel> tokens = asCoreLabels();
      Annotation doc = document.asAnnotation();
      return impl.getKbpTripleList().stream().map(x -> ProtobufAnnotationSerializer.fromProto(x, doc, this.sentenceIndex())).collect(Collectors.toList());
    }
  }

  /** @see Sentence@kbpTriples(Properties) */
  public Collection<RelationTriple> kbpTriples() {
    return kbpTriples(this.defaultProps);
  }

  /**
   * Get a list of KBP triples as flat (subject, relation, object, confidence) quadruples.
   * This is substantially faster than returning {@link RelationTriple} objects, as it doesn't
   * require converting the underlying representation into {@link CoreLabel}s; but, it also contains
   * significantly less information about the sentence.
   *
   * @see Sentence@kbpTriples(Properties)
   */
  public Collection<Quadruple<String, String, String, Double>> kbp() {
    document.runKBP(this.defaultProps);
    return impl.getKbpTripleList().stream()
        .filter(proto -> proto.hasSubject() && proto.hasRelation() && proto.hasObject())
        .map(proto -> Quadruple.makeQuadruple(proto.getSubject(), proto.getRelation(), proto.getObject(),
            proto.hasConfidence() ? proto.getConfidence() : 1.0))
        .collect(Collectors.toList());
  }


  /**
   * The sentiment of this sentence (e.g., positive / negative).
   *
   * @return The {@link SentimentClass} of this sentence, as an enum value.
   */
  public SentimentClass sentiment() {
    return sentiment(this.defaultProps);
  }


  /**
   * The sentiment of this sentence (e.g., positive / negative).
   *
   * @param props The properties to pass to the sentiment classifier.
   *
   * @return The {@link SentimentClass} of this sentence, as an enum value.
   */
  public SentimentClass sentiment(Properties props) {
    document.runSentiment(props);
    switch (impl.getSentiment().toLowerCase()) {
      case "very positive":
        return SentimentClass.VERY_POSITIVE;
      case "positive":
        return SentimentClass.POSITIVE;
      case "negative":
        return SentimentClass.NEGATIVE;
      case "very negative":
        return SentimentClass.VERY_NEGATIVE;
      case "neutral":
        return SentimentClass.NEUTRAL;
      default:
        throw new IllegalStateException("Unknown sentiment class: " + impl.getSentiment());
    }
  }

  /**
   * Get the coreference chain for just this sentence.
   * Note that this method is actually fairly computationally expensive to call, as it constructs and prunes
   * the coreference data structure for the entire document.
   *
   * @return A coreference chain, but only for this sentence
   */
  public Map<Integer, CorefChain> coref() {
    // Get the raw coref structure
    Map<Integer, CorefChain> allCorefs = document.coref();
    // Delete coreference chains not in this sentence
    Set<Integer> toDeleteEntirely = new HashSet<>();
    for (Map.Entry<Integer, CorefChain> integerCorefChainEntry : allCorefs.entrySet()) {
      CorefChain chain = integerCorefChainEntry.getValue();
      List<CorefChain.CorefMention> mentions = new ArrayList<>(chain.getMentionsInTextualOrder());
      mentions.stream().filter(m -> m.sentNum != this.sentenceIndex() + 1).forEach(chain::deleteMention);
      if (chain.getMentionsInTextualOrder().isEmpty()) {
        toDeleteEntirely.add(integerCorefChainEntry.getKey());
      }
    }
    // Clean up dangling empty chains
    toDeleteEntirely.forEach(allCorefs::remove);
    // Return
    return allCorefs;
  }


  //
  // Helpers for CoreNLP interoperability
  //

  /**
   * Returns this sentence as a CoreNLP CoreMap object.
   * Note that, importantly, only the fields which have already been called will be populated in
   * the CoreMap!
   *
   * Therefore, this method is generally NOT recommended.
   *
   * @param functions A list of functions to call before populating the CoreMap.
   *                  For example, you can specify mySentence::posTags, and then posTags will
   *                  be populated.
   */
  @SuppressWarnings("TypeParameterExplicitlyExtendsObject")
  @SafeVarargs
  public final CoreMap asCoreMap(Function<Sentence,Object>... functions) {
    for (Function<Sentence, Object> function : functions) {
      function.apply(this);
    }
    return this.document.asAnnotation(true).get(CoreAnnotations.SentencesAnnotation.class).get(this.sentenceIndex());
  }

  /**
   * Returns this sentence as a list of CoreLabels representing the sentence.
   * Note that, importantly, only the fields which have already been called will be populated in
   * the CoreMap!
   *
   * Therefore, this method is generally NOT recommended.
   *
   * @param functions A list of functions to call before populating the CoreMap.
   *                  For example, you can specify mySentence::posTags, and then posTags will
   *                  be populated.
   */
  @SuppressWarnings("TypeParameterExplicitlyExtendsObject")
  @SafeVarargs
  public final List<CoreLabel> asCoreLabels(Function<Sentence,Object>... functions) {
    for (Function<Sentence, Object> function : functions) {
      function.apply(this);
    }
    return asCoreMap().get(CoreAnnotations.TokensAnnotation.class);
  }


  //
  // HELPERS FROM DOCUMENT
  //

  /**
   * A helper to get the raw Protobuf builder for a given token.
   * Primarily useful for cache checks.
   * @param i The index of the token to retrieve.
   * @return A Protobuf builder for that token.
   */
  public CoreNLPProtos.Token.Builder rawToken(int i) {
    return tokensBuilders.get(i);
  }

  /**
   * Get the backing protocol buffer for this sentence.
   * @return The raw backing protocol buffer builder for this sentence.
   */
  public CoreNLPProtos.Sentence.Builder rawSentence() {
    return this.impl;
  }

  /**
   * Update each token in the sentence with the given information.
   * @param tokens The CoreNLP tokens returned by the {@link edu.stanford.nlp.pipeline.Annotator}.
   * @param setter The function to set a Protobuf object with the given field.
   * @param getter The function to get the given field from the {@link CoreLabel}.
   * @param <E> The type of the given field we are setting in the protocol buffer and reading from the {@link CoreLabel}.
   */
  protected <E> void updateTokens(List<CoreLabel> tokens,
                              Consumer<Pair<CoreNLPProtos.Token.Builder, E>> setter,
                              Function<CoreLabel, E> getter) {
    synchronized (this.impl) {
      for (int i = 0; i < tokens.size(); ++i) {
        E value = getter.apply(tokens.get(i));
        if (value != null) {
          setter.accept(Pair.makePair(tokensBuilders.get(i), value));
        }
      }
    }
  }

  /**
   * Update the parse tree for this sentence.
   * @param parse The parse tree to update.
   * @param binary The binary parse tree to update.
   */
  protected void updateParse(
      CoreNLPProtos.ParseTree parse,
      CoreNLPProtos.ParseTree binary) {
    synchronized (this.impl) {
      this.impl.setParseTree(parse);
      if (binary != null) {
        this.impl.setBinarizedParseTree(binary);
      }
    }
  }

  /**
   * Update the dependencies of the sentence.
   *
   * @param basic The basic dependencies to update.
   * @param enhanced The enhanced dependencies to update.
   * @param enhancedPlusPlus The enhanced plus plus dependencies to update.
   */
  protected void updateDependencies(CoreNLPProtos.DependencyGraph basic,
                                    CoreNLPProtos.DependencyGraph enhanced,
                                    CoreNLPProtos.DependencyGraph enhancedPlusPlus) {
    synchronized (this.impl) {
      this.impl.setBasicDependencies(basic);
      this.impl.setEnhancedDependencies(enhanced);
      this.impl.setEnhancedPlusPlusDependencies(enhancedPlusPlus);
    }
  }

  /**
   * Update the Open IE relation triples for this sentence.
   *
   * @param triples The stream of relation triples to add to the sentence.
   */
  protected void updateOpenIE(Stream<CoreNLPProtos.RelationTriple> triples) {
    synchronized (this.impl) {
      triples.forEach(this.impl::addOpenieTriple);
    }
  }

  /**
   * Update the Open IE relation triples for this sentence.
   *
   * @param triples The stream of relation triples to add to the sentence.
   */
  protected void updateKBP(Stream<CoreNLPProtos.RelationTriple> triples) {
    synchronized (this.impl) {
      triples.forEach(this.impl::addKbpTriple);
    }
  }

  /**
   * Update the Sentiment class for this sentence.
   *
   * @param sentiment The sentiment of the sentence.
   */
  protected void updateSentiment(String sentiment) {
    synchronized (this.impl) {
      this.impl.setSentiment(sentiment);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Sentence)) return false;
    Sentence sentence = (Sentence) o;
    // Short circuit for fast equals check
    if (impl.hasText() && !impl.getText().equals(sentence.impl.getText())) {
      return false;
    }
    if (this.tokensBuilders.size() != sentence.tokensBuilders.size()) {
      return false;
    }
    // Check the implementation of the sentence
    if (!impl.build().equals(sentence.impl.build())) {
      return false;
    }
    // Check each token
    for (int i = 0, sz = tokensBuilders.size(); i < sz; ++i) {
      if (!tokensBuilders.get(i).build().equals(sentence.tokensBuilders.get(i).build())) {
        return false;
      }
    }
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    if (this.impl.hasText()) {
      return this.impl.getText().hashCode() * 31 +  this.tokensBuilders.size();
    } else {
      return impl.build().hashCode();
    }
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return impl.getText();
  }

  /**
   * @param start - inclusive
   * @param end - exclusive
   * @return - the text for the provided token span.
   */
  public String substring(int start, int end) {
    StringBuilder sb = new StringBuilder();
    for(CoreLabel word : asCoreLabels().subList(start, end)) {
      sb.append(word.word());
      sb.append(word.after());
    }
    return sb.toString();
  }


  private static <E> List<E> lazyList(final List<CoreNLPProtos.Token.Builder> tokens, final Function<CoreNLPProtos.Token.Builder,E> fn) {
    return new AbstractList<E>() {
      @Override
      public E get(int index) {
        return fn.apply(tokens.get(index));
      }
      @Override
      public int size() {
        return tokens.size();
      }
    };
  }

  /** Returns the sentence id of the sentence, if one was found */
  public Optional<String> sentenceid() {
    synchronized (impl) {
      if (impl.hasSentenceID()) {
        return Optional.of(impl.getSentenceID());
      } else {
        return Optional.empty();
      }
    }
  }

  /**
   * Apply a TokensRegex pattern to the sentence.
   *
   * @param pattern The TokensRegex pattern to match against.
   * @return the matcher.
   */
  public boolean matches(TokenSequencePattern pattern) {
    return pattern.getMatcher(asCoreLabels()).matches();
  }

  /**
   * Apply a TokensRegex pattern to the sentence.
   *
   * @param pattern The TokensRegex pattern to match against.
   * @return True if the tokensregex pattern matches.
   */
  public boolean matches(String pattern) {
    return matches(TokenSequencePattern.compile(pattern));
  }

  /**
   * Apply a TokensRegex pattern to the sentence.
   *
   * @param pattern The TokensRegex pattern to match against.
   * @param fn The action to do on each match.
   * @return the list of matches, after run through the function.
   */
  public <T> List<T> find(TokenSequencePattern pattern, Function<TokenSequenceMatcher, T> fn) {
    TokenSequenceMatcher matcher = pattern.matcher(asCoreLabels());
    List<T> lst = new ArrayList<>();
    while(matcher.find()) {
      lst.add(fn.apply(matcher));
    }
    return lst;
  }

  public <T> List<T>  find(String pattern, Function<TokenSequenceMatcher, T> fn) {
    return find(TokenSequencePattern.compile(pattern), fn);
  }

  /**
   * Apply a semgrex pattern to the sentence
   * @param pattern The Semgrex pattern to match against.
   * @param fn The action to do on each match.
   * @return the list of matches, after run through the function.
   */
  public <T> List<T> semgrex(SemgrexPattern pattern, Function<SemgrexMatcher, T> fn) {
    SemgrexMatcher matcher = pattern.matcher(dependencyGraph());
    List<T> lst = new ArrayList<>();
    while(matcher.findNextMatchingNode()) {
      lst.add(fn.apply(matcher));
    }
    return lst;
  }

  /**
   * Apply a semgrex pattern to the sentence
   * @param pattern The Semgrex pattern to match against.
   * @param fn The action to do on each match.
   * @return the list of matches, after run through the function.
   */
  public <T> List<T> semgrex(String pattern, Function<SemgrexMatcher, T> fn) {
    return semgrex(SemgrexPattern.compile(pattern), fn);
  }

}
