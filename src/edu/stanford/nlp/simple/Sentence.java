package edu.stanford.nlp.simple;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.naturalli.OperatorSpec;
import edu.stanford.nlp.naturalli.Polarity;
import edu.stanford.nlp.pipeline.CoreNLPProtos;
import edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static edu.stanford.nlp.simple.Document.EMPTY_PROPS;

/**
 * A representation of a single Sentence.
 * Although it is possible to create a sentence directly from text, it is advisable to
 * create a document instead and operate on the document directly.
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("UnusedDeclaration")
public class Sentence {
  /** A properties object for creating a document from a single sentence. Used in the constructor {@link Sentence#Sentence(String)} */
  private static Properties SINGLE_SENTENCE_DOCUMENT = new Properties() {{
    setProperty("ssplit.isOneSentence", "true");
    setProperty("tokenize.class", "PTBTokenizer");
    setProperty("tokenize.language", "en");
  }};

  /**
   * <p>
   *  The protobuf representation of a Sentence.
   *  Note that this does not necessarily have up to date token information.
   * </p>
   * <p>
   *
   * </p>
   * */
  private final CoreNLPProtos.Sentence.Builder impl;
  /** The protobuf representation of the tokens of a sentence. This has up-to-date information on the tokens */
  private final List<CoreNLPProtos.Token.Builder> tokensBuilders;
  /** The document this sentence is derived from */
  public final Document document;

  /**
   * Create a new sentence, using the specified properties ONLY FOR TOKENIZATION.
   * @param text The text of the sentence.
   * @param props The properties to use for tokenizing the sentence.
   */
  public Sentence(String text, Properties props) {
    // Set document
    this.document = new Document(text);
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
  }

  /**
   * Create a new sentence from the given text, assuming the entire text is just one sentence.
   * @param text The text of the sentence.
   */
  public Sentence(String text) {
    this(text, SINGLE_SENTENCE_DOCUMENT);
  }

  public Sentence(CoreNLPProtos.Sentence proto) {
    this.impl = proto.toBuilder();
    // Set tokens
    tokensBuilders = new ArrayList<>(this.impl.getTokenCount());
    for (int i = 0; i < this.impl.getTokenCount(); ++i) {
      tokensBuilders.add(this.impl.getToken(i).toBuilder());
    }
    // Initialize document
    this.document = new Document(proto.getText());
    this.document.forceSentences(Collections.singletonList(this));
    // Asserts
    assert (this.document.sentence(0).impl == this.impl);
    assert (this.document.sentence(0).tokensBuilders == this.tokensBuilders);
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
  }

  /**
   * The canonical constructor of a sentence from a {@link edu.stanford.nlp.simple.Document}.
   * @param doc The document to link this sentence to.
   * @param proto The sentence implementation to use for this sentence.
   */
  protected Sentence(Document doc, CoreNLPProtos.Sentence.Builder proto) {
    this.document = doc;
    this.impl = proto;
    // Set tokens
    // This is the _only_ place we are allowed to construct tokens builders
    tokensBuilders = new ArrayList<>(this.impl.getTokenCount());
    for (int i = 0; i < this.impl.getTokenCount(); ++i) {
      tokensBuilders.add(this.impl.getToken(i).toBuilder());
    }
  }

  /** Helper for creating a sentence from a document and a CoreMap representation */
  protected Sentence(Document doc, CoreMap sentence) {
    this(doc, doc.serializer.toProto(sentence).toBuilder());
    this.impl.setText(sentence.get(CoreAnnotations.TextAnnotation.class));
  }

  /**
   * Serialize the given sentence (but not the associated document!) into a Protocol Buffer.
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
    return posTags(EMPTY_PROPS);
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
    return lemmas(EMPTY_PROPS);
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
  public List<String> ners(Properties props) {
    document.runNER(props);
    synchronized (impl) {
      return lazyList(tokensBuilders, CoreNLPProtos.Token.Builder::getNer);
    }
  }

  /** @see Sentence#ners(java.util.Properties) */
  public List<String> ners() {
    return ners(EMPTY_PROPS);
  }

  /** @see Sentence#ners(java.util.Properties) */
  public String ner(int index) {
    return ners().get(index);
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
    return parse(EMPTY_PROPS);
  }

  /** An internal helper to get the dependency tree of the given type. */
  private CoreNLPProtos.DependencyGraph dependencies(SemanticGraphFactory.Mode mode) {
    switch (mode) {
      case COLLAPSED_TREE:
        return impl.getCollapsedDependencies();
      case COLLAPSED:
        return impl.getCollapsedDependencies();
      case CCPROCESSED:
        return impl.getCollapsedCCProcessedDependencies();
      case BASIC:
        return impl.getBasicDependencies();
      default:
        throw new IllegalArgumentException("Unknown dependency type: " + mode);
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
    return governor(props, index, SemanticGraphFactory.Mode.BASIC);
  }

  /** @see Sentence#governor(java.util.Properties, int, SemanticGraphFactory.Mode) */
  public Optional<Integer> governor(int index, SemanticGraphFactory.Mode mode) {
    return governor(EMPTY_PROPS, index, mode);
  }

  /** @see Sentence#governor(java.util.Properties, int) */
  public Optional<Integer> governor(int index) {
    return governor(EMPTY_PROPS, index);
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
    return governors(props, SemanticGraphFactory.Mode.BASIC);
  }

  /** @see Sentence#governors(java.util.Properties, SemanticGraphFactory.Mode) */
  public List<Optional<Integer>> governors(SemanticGraphFactory.Mode mode) {
    return governors(EMPTY_PROPS, mode);
  }

  /** @see Sentence#governors(java.util.Properties, SemanticGraphFactory.Mode) */
  public List<Optional<Integer>> governors() {
    return governors(EMPTY_PROPS, SemanticGraphFactory.Mode.BASIC);
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
    return incomingDependencyLabel(props, index, SemanticGraphFactory.Mode.BASIC);
  }

  /** @see Sentence#incomingDependencyLabel(java.util.Properties, int, SemanticGraphFactory.Mode) */
  public Optional<String> incomingDependencyLabel(int index, SemanticGraphFactory.Mode mode) {
    return incomingDependencyLabel(EMPTY_PROPS, index, mode);
  }

  /** @see Sentence#incomingDependencyLabel(java.util.Properties, int) */
  public Optional<String> incomingDependencyLabel(int index) {
    return incomingDependencyLabel(EMPTY_PROPS, index);
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
    return incomingDependencyLabels(EMPTY_PROPS, mode);
  }

  /** @see Sentence#incomingDependencyLabels(java.util.Properties, SemanticGraphFactory.Mode) */
  public List<Optional<String>> incomingDependencyLabels(Properties props) {
    return incomingDependencyLabels(props, SemanticGraphFactory.Mode.BASIC);
  }

  /** @see Sentence#incomingDependencyLabels(java.util.Properties, SemanticGraphFactory.Mode) */
  public List<Optional<String>> incomingDependencyLabels() {
    return incomingDependencyLabels(EMPTY_PROPS, SemanticGraphFactory.Mode.BASIC);
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
    return operators(EMPTY_PROPS);
  }

  /** @see Sentence#operators(Properties) */
  public Optional<OperatorSpec> operatorAt(Properties props, int i) {
    return operators(props).get(i);
  }


  /** @see Sentence#operators(Properties) */
  public Optional<OperatorSpec> operatorAt(int i) {
    return operators(EMPTY_PROPS).get(i);
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
    return operatorsNonempty(EMPTY_PROPS);
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
    return natlogPolarities(EMPTY_PROPS);
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
    return natlogPolarity(EMPTY_PROPS, index);
  }

  //
  // Helpers for CoreNLP interoperability
  //

  /**
   * Returns this sentence as a CoreNLP CoreMap object.
   * Note that, importantly, only the fields which have already been called will be populated in
   * the CoreMap!
   *
   * <p>Therefore, this method is generally NOT recommended.</p>
   *
   * @param functions A list of functions to call before populating the CoreMap.
   *                  For example, you can specify mySentence::posTags, and then posTags will
   *                  be populated.
   */
  @SuppressWarnings("TypeParameterExplicitlyExtendsObject")
  @SafeVarargs
  public final CoreMap asCoreMap(Supplier<? extends Object>... functions) {
    for (Supplier<?> function : functions) {
      function.get();
    }
    return this.document.asAnnotation().get(CoreAnnotations.SentencesAnnotation.class).get(this.sentenceIndex());
  }

  /**
   * Returns this sentence as a list of CoreLabels representing the sentence.
   * Note that, importantly, only the fields which have already been called will be populated in
   * the CoreMap!
   *
   * <p>Therefore, this method is generally NOT recommended.</p>
   *
   * @param functions A list of functions to call before populating the CoreMap.
   *                  For example, you can specify mySentence::posTags, and then posTags will
   *                  be populated.
   */
  @SuppressWarnings("TypeParameterExplicitlyExtendsObject")
  @SafeVarargs
  public final List<CoreLabel> asCoreLabels(Supplier<? extends Object>... functions) {
    for (Supplier<?> function : functions) {
      function.get();
    }
    return asCoreMap().get(CoreAnnotations.TokensAnnotation.class);
  }


  //
  // HELPERS FROM DOCUMENT
  //

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

  protected void updateParse(CoreNLPProtos.ParseTree parse) {
    synchronized (this.impl) {
      this.impl.setParseTree(parse);
    }
  }

  protected void updateDependencies(CoreNLPProtos.DependencyGraph basic,
                                    CoreNLPProtos.DependencyGraph collapsed,
                                    CoreNLPProtos.DependencyGraph ccProcessed) {
    synchronized (this.impl) {
      this.impl.setBasicDependencies(basic);
      this.impl.setCollapsedDependencies(collapsed);
      this.impl.setCollapsedCCProcessedDependencies(ccProcessed);
    }
  }

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
    for (int i = 0; i < tokensBuilders.size(); ++i) {
      if (!tokensBuilders.get(i).build().equals(sentence.tokensBuilders.get(i).build())) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    if (this.impl.hasText()) {
      return this.impl.getText().hashCode() * 31 +  this.tokensBuilders.size();
    } else {
      return impl.build().hashCode();
    }
  }

  @Override
  public String toString() {
    return impl.getText();
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
}
