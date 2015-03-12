package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.Dictionaries;
import edu.stanford.nlp.ie.NumberNormalizer;
import edu.stanford.nlp.ie.machinereading.structure.EntityMention;
import edu.stanford.nlp.ie.machinereading.structure.ExtractionObject;
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations.*;
import edu.stanford.nlp.ie.machinereading.structure.RelationMention;
import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.time.Timex;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.LabeledScoredTreeNode;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.ling.CoreAnnotations.*;
import edu.stanford.nlp.trees.TreeCoreAnnotations.*;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.*;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations.*;
import edu.stanford.nlp.time.TimeAnnotations.*;

import java.io.*;
import java.util.*;

/**
 * <p>
 *   A serializer using Google's protocol buffer format.
 *   The files produced by this serializer, in addition to being language-independent,
 *   are a little over 10% the size and 4x faster to read+write versus the default Java serialization
 *   (see {@link GenericAnnotationSerializer}), when both files are compressed with gzip.
 * </p>
 *
 * <p>
 *   Note that this handles only a subset of the possible annotations
 *   that can be attached to a sentence. Nonetheless, it is guaranteed to be
 *   lossless with the default set of named annotators you can create from a
 *   {@link StanfordCoreNLP} pipeline, with default properties defined for each annotator.
 *   Note that the serializer does not gzip automatically -- this must be done by passing in a GZipOutputStream
 *   and calling a GZipInputStream manually. For most Annotations, gzipping provides a notable decrease in size (~2.5x)
 *   due to most of the data being raw Strings.
 * </p>
 *
 * <p>
 *   To allow lossy serialization, use {@link ProtobufAnnotationSerializer#ProtobufAnnotationSerializer(boolean)}.
 *   Otherwise, an exception is thrown if an unknown key appears in the annotation which would not be saved to th
 *   protocol buffer.
 *   If such keys exist, and are a part of the standard CoreNLP pipeline, please let us know!
 *   If you would like to serialize keys in addition to those serialized by default (e.g., you are attaching
 *   your own annotations), then you should do the following:
 * </p>
 *
 * <ol>
 *   <li>
 *     Create a .proto file which extends one or more of Document, Sentence, or Token. Each of these have fields
 *     100-255 left open for user extensions. An example of such an extension is:
 *     <pre>
 *       package edu.stanford.nlp.pipeline;
 *
 *       option java_package = "com.example.my.awesome.nlp.app";
 *       option java_outer_classname = "MyAppProtos";
 *
 *       import "CoreNLP.proto";
 *
 *       extend Sentence {
 *         optional uint32 myNewField    = 101;
 *       }
 *     </pre>
 *   </li>
 *
 *   <li>
 *     Compile your .proto file with protoc. For example (from CORENLP_HOME):
 *     <pre>
 *        protoc -I=src/edu/stanford/nlp/pipeline/:/path/to/folder/contining/your/proto/file --java_out=/path/to/output/src/folder/  /path/to/proto/file
 *     </pre>
 *   </li>
 *
 *   <li>
 *     <p>
 *     Extend {@link ProtobufAnnotationSerializer} to serialize and deserialize your field.
 *     Generally, this entail overriding two functions -- one to write the proto and one to read it.
 *     In both cases, you usually want to call the superclass' implementation of the function, and add on to it
 *     from there.
 *     In our running example, adding a field to the {@link CoreNLPProtos.Sentence} proto, you would overwrite:
 *     </p>
 *
 *     <ul>
 *       <li>{@link ProtobufAnnotationSerializer#toProtoBuilder(edu.stanford.nlp.util.CoreMap, java.util.Set)}</li>
 *       <li>{@link ProtobufAnnotationSerializer#fromProtoNoTokens(edu.stanford.nlp.pipeline.CoreNLPProtos.Sentence)}</li>
 *     </ul>
 *
 *     <p>
 *     Note, importantly, that for the serializer to be able to check for lossless serialization, all annotations added
 *     to the proto must be registered as added by being removed from the set passed to
 *     {@link ProtobufAnnotationSerializer#toProtoBuilder(edu.stanford.nlp.util.CoreMap, java.util.Set)} (and the analogous
 *     functions for documents and tokens).
 *     </p>
 *
 *     <p>
 *       Lastly, the new annotations must be registered in the original .proto file; this can be achieved by including
 *       a static block in the overwritten class:
 *     </p>
 *     <pre>
 *       static {
 *         ExtensionRegistry registry = ExtensionRegistry.newInstance();
 *         registry.add(MyAppProtos.myNewField);
 *         CoreNLPProtos.registerAllExtensions(registry);
 *       }
 *     </pre>
 *   </li>
 * </ol>
 *
 * @author Gabor Angeli
 */
public class ProtobufAnnotationSerializer extends AnnotationSerializer {

  /** A global lock; necessary since dependency tree creation is not threadsafe */
  private static final Object globalLock = "I'm a lock :)";

  /**
   * An exception to denote that the serialization would be lossy.
   * This exception is thrown at serialization time.
   *
   * @see ProtobufAnnotationSerializer#enforceLosslessSerialization
   * @see ProtobufAnnotationSerializer#ProtobufAnnotationSerializer(boolean)
   */
  public static class LossySerializationException extends RuntimeException {
    private LossySerializationException(String msg) { super(msg); }
  }

  /**
   * If true, serialization is guaranteed to be lossless or else a runtime exception is thrown
   * at serialization time.
   */
  public final boolean enforceLosslessSerialization;

  /**
   * Create a new Annotation serializer outputting to a protocol buffer format.
   * This is guaranteed to either be a lossless compression, or throw an exception at
   * serialization time.
   */
  public ProtobufAnnotationSerializer() { this(true); }

  /**
   * Create a new Annotation serializer outputting to a protocol buffer format.
   *
   * @param enforceLosslessSerialization If set to true, a {@link ProtobufAnnotationSerializer.LossySerializationException}
   *                                     is thrown at serialization
   *                                     time if the serialization would be lossy. If set to false,
   *                                     these exceptions are ignored.
   *
   */
  public ProtobufAnnotationSerializer(boolean enforceLosslessSerialization) { this.enforceLosslessSerialization = enforceLosslessSerialization; }

  /** {@inheritDoc} */
  @Override
  public OutputStream write(Annotation corpus, OutputStream os) throws IOException {
    CoreNLPProtos.Document serialized = toProto(corpus);
    serialized.writeDelimitedTo(os);
    os.flush();
    return os;
  }

  /** {@inheritDoc} */
  @Override
  public Pair<Annotation, InputStream> read(InputStream is) throws IOException, ClassNotFoundException, ClassCastException {
    CoreNLPProtos.Document doc = CoreNLPProtos.Document.parseDelimitedFrom(is);
    return Pair.makePair( fromProto(doc), is );
  }

  /**
   * Read a single protocol buffer, which constitutes the entire stream.
   * This is in contrast to the default, where mutliple buffers may come out of the stream,
   * and therefore each one is prepended by the length of the buffer to follow.
   *
   * @param in The file to read.
   * @return A parsed Annotation.
   * @throws IOException In case the stream cannot be read from.
   */
  @SuppressWarnings("UnusedDeclaration")
  public Annotation readUndelimited(File in) throws IOException {
    FileInputStream delimited = new FileInputStream(in);
    FileInputStream undelimited = new FileInputStream(in);
    CoreNLPProtos.Document doc;
    try {
      doc = CoreNLPProtos.Document.parseFrom(delimited);
    } catch (Exception e) {
      doc = CoreNLPProtos.Document.parseDelimitedFrom(undelimited);
    } finally {
      delimited.close();
      undelimited.close();
    }
    return fromProto(doc);
  }

  /**
   * Get a particular key from a CoreMap, registering it as being retrieved.
   * @param map The CoreMap to retrieve the key from.
   * @param keysToRegister A set of keys to remove this key from, representing to keys which should be retrieved by the serializer.
   * @param key The key key to retrieve.
   * @param <E> The class of the item which is being retrieved.
   * @return CoreMap.get(key)
   */
  private static <E> E getAndRegister(CoreMap map, Set<Class<?>> keysToRegister, Class<? extends CoreAnnotation<E>> key) {
    keysToRegister.remove(key);
    return map.get(key);
  }

  /**
   * Create a CoreLabel proto from a CoreLabel instance.
   * This is not static, as it optionally throws an exception if the serialization is lossy.
   * @param coreLabel The CoreLabel to convert
   * @return A protocol buffer message corresponding to this CoreLabel
   */
  public CoreNLPProtos.Token toProto(CoreLabel coreLabel) {
    Set<Class<?>> keysToSerialize = new HashSet<Class<?>>(coreLabel.keySet());
    CoreNLPProtos.Token.Builder builder = toProtoBuilder(coreLabel, keysToSerialize);
    // Completeness check
    if (enforceLosslessSerialization && !keysToSerialize.isEmpty()) {
      throw new LossySerializationException("Keys are not being serialized: " + StringUtils.join(keysToSerialize));
    }
    return builder.build();
  }

  /**
   * <p>
   *   The method to extend by subclasses of the Protobuf Annotator if custom additions are added to Tokens.
   *   In contrast to {@link ProtobufAnnotationSerializer#toProto(edu.stanford.nlp.ling.CoreLabel)}, this function
   *   returns a builder that can be extended.
   * </p>
   *
   * @param coreLabel The sentence to save to a protocol buffer
   * @param keysToSerialize A set tracking which keys have been saved. It's important to remove any keys added to the proto
   *                        from this set, as the code tracks annotations to ensure lossless serializationA set tracking which keys have been saved. It's important to remove any keys added to the proto*
   *                        from this set, as the code tracks annotations to ensure lossless serialization.
   */
  protected CoreNLPProtos.Token.Builder toProtoBuilder(CoreLabel coreLabel, Set<Class<?>> keysToSerialize) {
    CoreNLPProtos.Token.Builder builder = CoreNLPProtos.Token.newBuilder();
    // Remove items serialized elsewhere from the required list
    keysToSerialize.remove(TextAnnotation.class);
    keysToSerialize.remove(SentenceIndexAnnotation.class);
    keysToSerialize.remove(DocIDAnnotation.class);
    keysToSerialize.remove(IndexAnnotation.class);
    keysToSerialize.remove(ParagraphAnnotation.class);
    // Remove items populated by number normalizer
    keysToSerialize.remove(NumericCompositeObjectAnnotation.class);
    keysToSerialize.remove(NumericCompositeTypeAnnotation.class);
    keysToSerialize.remove(NumericCompositeValueAnnotation.class);
    keysToSerialize.remove(NumericTypeAnnotation.class);
    keysToSerialize.remove(NumericValueAnnotation.class);
    // Remove items which were never supposed to be there in the first place
    keysToSerialize.remove(ForcedSentenceUntilEndAnnotation.class);
    keysToSerialize.remove(ForcedSentenceEndAnnotation.class);
    // Required fields
    builder.setWord(coreLabel.word());
    // Optional fields
    if (coreLabel.tag() != null) { builder.setPos(coreLabel.tag()); keysToSerialize.remove(PartOfSpeechAnnotation.class); }
    if (coreLabel.value() != null) { builder.setValue(coreLabel.value()); keysToSerialize.remove(ValueAnnotation.class); }
    if (coreLabel.category() != null) { builder.setCategory(coreLabel.category()); keysToSerialize.remove(CategoryAnnotation.class); }
    if (coreLabel.before() != null) { builder.setBefore(coreLabel.before()); keysToSerialize.remove(BeforeAnnotation.class); }
    if (coreLabel.after() != null) { builder.setAfter(coreLabel.after()); keysToSerialize.remove(AfterAnnotation.class); }
    if (coreLabel.originalText() != null) { builder.setOriginalText(coreLabel.originalText()); keysToSerialize.remove(OriginalTextAnnotation.class); }
    if (coreLabel.ner() != null) { builder.setNer(coreLabel.ner()); keysToSerialize.remove(NamedEntityTagAnnotation.class); }
    if (coreLabel.beginPosition() >= 0) { builder.setBeginChar(coreLabel.beginPosition()); keysToSerialize.remove(CharacterOffsetBeginAnnotation.class); }
    if (coreLabel.endPosition() >= 0) { builder.setEndChar(coreLabel.endPosition()); keysToSerialize.remove(CharacterOffsetEndAnnotation.class); }
    if (coreLabel.lemma() != null) { builder.setLemma(coreLabel.lemma()); keysToSerialize.remove(LemmaAnnotation.class); }
    if (coreLabel.containsKey(UtteranceAnnotation.class)) { builder.setUtterance(getAndRegister(coreLabel, keysToSerialize, UtteranceAnnotation.class)); }
    if (coreLabel.containsKey(SpeakerAnnotation.class)) { builder.setSpeaker(getAndRegister(coreLabel, keysToSerialize, SpeakerAnnotation.class)); }
    if (coreLabel.containsKey(BeginIndexAnnotation.class)) { builder.setBeginIndex(getAndRegister(coreLabel, keysToSerialize, BeginIndexAnnotation.class)); }
    if (coreLabel.containsKey(EndIndexAnnotation.class)) { builder.setEndIndex(getAndRegister(coreLabel, keysToSerialize, EndIndexAnnotation.class)); }
    if (coreLabel.containsKey(TokenBeginAnnotation.class)) { builder.setTokenBeginIndex(getAndRegister(coreLabel, keysToSerialize, TokenBeginAnnotation.class)); }
    if (coreLabel.containsKey(TokenEndAnnotation.class)) { builder.setTokenEndIndex(getAndRegister(coreLabel, keysToSerialize, TokenEndAnnotation.class)); }
    if (getAndRegister(coreLabel, keysToSerialize, NormalizedNamedEntityTagAnnotation.class) != null) { builder.setNormalizedNER(getAndRegister(coreLabel, keysToSerialize, NormalizedNamedEntityTagAnnotation.class)); }
    if (coreLabel.containsKey(TimexAnnotation.class)) { builder.setTimexValue(toProto(getAndRegister(coreLabel, keysToSerialize, TimexAnnotation.class))); }
    if (coreLabel.containsKey(AnswerAnnotation.class)) { builder.setAnswer(getAndRegister(coreLabel, keysToSerialize, AnswerAnnotation.class)); }
    if (coreLabel.containsKey(XmlContextAnnotation.class)) {
      builder.setHasXmlContext(true);
      builder.addAllXmlContext(getAndRegister(coreLabel, keysToSerialize, XmlContextAnnotation.class));
    } else {
      builder.setHasXmlContext(false);
    }
    if (coreLabel.containsKey(CorefClusterIdAnnotation.class)) { builder.setCorefClusterID(getAndRegister(coreLabel, keysToSerialize, CorefClusterIdAnnotation.class)); }
    // Non-default annotators
    if (getAndRegister(coreLabel, keysToSerialize, GenderAnnotation.class) != null) { builder.setGender(getAndRegister(coreLabel, keysToSerialize, GenderAnnotation.class)); }
    if (coreLabel.containsKey(TrueCaseAnnotation.class)) { builder.setTrueCase(getAndRegister(coreLabel, keysToSerialize, TrueCaseAnnotation.class)); }
    if (coreLabel.containsKey(TrueCaseTextAnnotation.class)) { builder.setTrueCaseText(getAndRegister(coreLabel, keysToSerialize, TrueCaseTextAnnotation.class)); }
    // Return
    return builder;
  }

  /**
   * Create a Sentence proto from a CoreMap instance.
   * This is not static, as it optionally throws an exception if the serialization is lossy.
   * @param sentence The CoreMap to convert. Note that it should not be a CoreLabel or an Annotation,
   *                 and should represent a sentence.
   * @return A protocol buffer message corresponding to this sentence
   * @throws IllegalArgumentException If the sentence is not a valid sentence (e.g., is a document or a word).
   */
  public CoreNLPProtos.Sentence toProto(CoreMap sentence) {
    Set<Class<?>> keysToSerialize = new HashSet<Class<?>>(sentence.keySet());
    CoreNLPProtos.Sentence.Builder builder = toProtoBuilder(sentence, keysToSerialize);
    // Completeness check
    if (enforceLosslessSerialization && !keysToSerialize.isEmpty()) {
      throw new LossySerializationException("Keys are not being serialized: " + StringUtils.join(keysToSerialize));
    }
    return builder.build();
  }

  /**
   * <p>
   *   The method to extend by subclasses of the Protobuf Annotator if custom additions are added to Tokens.
   *   In contrast to {@link ProtobufAnnotationSerializer#toProto(edu.stanford.nlp.ling.CoreLabel)}, this function
   *   returns a builder that can be extended.
   * </p>
   *
   * @param sentence The sentence to save to a protocol buffer
   * @param keysToSerialize A set tracking which keys have been saved. It's important to remove any keys added to the proto
   *                        from this set, as the code tracks annotations to ensure lossless serializationA set tracking which keys have been saved. It's important to remove any keys added to the proto*
   *                        from this set, as the code tracks annotations to ensure lossless serialization.
   */
  protected CoreNLPProtos.Sentence.Builder toProtoBuilder(CoreMap sentence, Set<Class<?>> keysToSerialize) {
    // Error checks
    if (sentence instanceof CoreLabel) { throw new IllegalArgumentException("CoreMap is actually a CoreLabel"); }
    CoreNLPProtos.Sentence.Builder builder = CoreNLPProtos.Sentence.newBuilder();
    // Remove items serialized elsewhere from the required list
    keysToSerialize.remove(TextAnnotation.class);
    keysToSerialize.remove(NumerizedTokensAnnotation.class);
    // Required fields
    builder.setTokenOffsetBegin(getAndRegister(sentence, keysToSerialize, TokenBeginAnnotation.class));
    builder.setTokenOffsetEnd(getAndRegister(sentence, keysToSerialize, TokenEndAnnotation.class));
    // Tokens
    if (sentence.containsKey(TokensAnnotation.class)) {
      for (CoreLabel tok : sentence.get(TokensAnnotation.class)) { builder.addToken(toProto(tok)); }
      keysToSerialize.remove(TokensAnnotation.class);
    }
    // Optional fields
    if (sentence.containsKey(SentenceIndexAnnotation.class)) { builder.setSentenceIndex(getAndRegister(sentence, keysToSerialize, SentenceIndexAnnotation.class)); }
    if (sentence.containsKey(CharacterOffsetBeginAnnotation.class)) { builder.setCharacterOffsetBegin(getAndRegister(sentence, keysToSerialize, CharacterOffsetBeginAnnotation.class)); }
    if (sentence.containsKey(CharacterOffsetEndAnnotation.class)) { builder.setCharacterOffsetEnd(getAndRegister(sentence, keysToSerialize, CharacterOffsetEndAnnotation.class)); }
    if (sentence.containsKey(TreeAnnotation.class)) { builder.setParseTree(toProto(getAndRegister(sentence, keysToSerialize, TreeAnnotation.class))); }
    if (sentence.containsKey(BasicDependenciesAnnotation.class)) { builder.setBasicDependencies(toProto(getAndRegister(sentence, keysToSerialize, BasicDependenciesAnnotation.class))); }
    if (sentence.containsKey(CollapsedDependenciesAnnotation.class)) { builder.setCollapsedDependencies(toProto(getAndRegister(sentence, keysToSerialize, CollapsedDependenciesAnnotation.class))); }
    if (sentence.containsKey(CollapsedCCProcessedDependenciesAnnotation.class)) { builder.setCollapsedCCProcessedDependencies(toProto(getAndRegister(sentence, keysToSerialize, CollapsedCCProcessedDependenciesAnnotation.class))); }
    if (sentence.containsKey(TokensAnnotation.class) && getAndRegister(sentence, keysToSerialize, TokensAnnotation.class).size() > 0 &&
        getAndRegister(sentence, keysToSerialize, TokensAnnotation.class).get(0).containsKey(ParagraphAnnotation.class)) {
      builder.setParagraph(getAndRegister(sentence, keysToSerialize, TokensAnnotation.class).get(0).get(ParagraphAnnotation.class));
    }
    if (sentence.containsKey(NumerizedTokensAnnotation.class)) { builder.setHasNumerizedTokensAnnotation(true); } else { builder.setHasNumerizedTokensAnnotation(false); }
    // Non-default annotators
    if (sentence.containsKey(EntityMentionsAnnotation.class)) {
      builder.setHasRelationAnnotations(true);
      for (EntityMention entity : getAndRegister(sentence, keysToSerialize, EntityMentionsAnnotation.class)) {
        builder.addEntity(toProto(entity));
      }
    } else {
      builder.setHasRelationAnnotations(false);
    }
    if (sentence.containsKey(RelationMentionsAnnotation.class)) {
      if (!builder.getHasRelationAnnotations()) { throw new IllegalStateException("Registered entity mentions without relation mentions"); }
      for (RelationMention relation : getAndRegister(sentence, keysToSerialize, RelationMentionsAnnotation.class)) {
        builder.addRelation(toProto(relation));
      }
    }
    // Return
    return builder;
  }

  /**
   * Create a Document proto from a CoreMap instance.
   * This is not static, as it optionally throws an exception if the serialization is lossy.
   * @param doc The Annotation to convert.
   * @return A protocol buffer message corresponding to this document
   */
  public CoreNLPProtos.Document toProto(Annotation doc) {
    Set<Class<?>> keysToSerialize = new HashSet<Class<?>>(doc.keySet());
    keysToSerialize.remove(TokensAnnotation.class);  // note(gabor): tokens are saved in the sentence
    CoreNLPProtos.Document.Builder builder = toProtoBuilder(doc, keysToSerialize);
    // Completeness Check
    if (enforceLosslessSerialization && !keysToSerialize.isEmpty()) {
      throw new LossySerializationException("Keys are not being serialized: " + StringUtils.join(keysToSerialize));
    }
    return builder.build();
  }

  /**
   * <p>
   *   The method to extend by subclasses of the Protobuf Annotator if custom additions are added to Tokens.
   *   In contrast to {@link ProtobufAnnotationSerializer#toProto(edu.stanford.nlp.ling.CoreLabel)}, this function
   *   returns a builder that can be extended.
   * </p>
   *
   * @param doc The sentence to save to a protocol buffer
   * @param keysToSerialize A set tracking which keys have been saved. It's important to remove any keys added to the proto
   *                        from this set, as the code tracks annotations to ensure lossless serializationA set tracking which keys have been saved. It's important to remove any keys added to the proto*
   *                        from this set, as the code tracks annotations to ensure lossless serialization.
   */
  protected CoreNLPProtos.Document.Builder toProtoBuilder(Annotation doc, Set<Class<?>> keysToSerialize) {
    CoreNLPProtos.Document.Builder builder = CoreNLPProtos.Document.newBuilder();
    // Required fields
    builder.setText(doc.get(TextAnnotation.class));
    keysToSerialize.remove(TextAnnotation.class);
    // Optional fields
    if (doc.containsKey(SentencesAnnotation.class)) {
      for (CoreMap sentence : doc.get(SentencesAnnotation.class)) { builder.addSentence(toProto(sentence)); }
      keysToSerialize.remove(SentencesAnnotation.class);
    } else if (doc.containsKey(TokensAnnotation.class)) {
      for (CoreLabel token : doc.get(TokensAnnotation.class)) { builder.addSentencelessToken(toProto(token)); }
    }
    if (doc.containsKey(DocIDAnnotation.class)) {
      builder.setDocID(doc.get(DocIDAnnotation.class));
      keysToSerialize.remove(DocIDAnnotation.class);
    }
    if (doc.containsKey(CorefChainAnnotation.class)) {
      for (Map.Entry<Integer, CorefChain> chain : doc.get(CorefChainAnnotation.class).entrySet()) {
       builder.addCorefChain(toProto(chain.getValue()));
      }
      keysToSerialize.remove(CorefChainAnnotation.class);
    }
    // Return
    return builder;
  }

  /**
   * Create a ParseTree proto from a Tree. If the Tree is a scored tree, the scores will
   * be preserved.
   * @param parseTree The parse tree to convert.
   * @return A protocol buffer message corresponding to this tree.
   */
  public CoreNLPProtos.ParseTree toProto(Tree parseTree) {
    CoreNLPProtos.ParseTree.Builder builder = CoreNLPProtos.ParseTree.newBuilder();
    // Required fields
    for (Tree child : parseTree.children()) { builder.addChild(toProto(child)); }
    // Optional fields
    IntPair span = parseTree.getSpan();
    if (span != null) {
      builder.setYieldBeginIndex(span.getSource());
      builder.setYieldEndIndex(span.getTarget());
    }
    if (parseTree.label() != null) {
      builder.setValue(parseTree.label().value());
    }
    if (!Double.isNaN(parseTree.score())) {
      builder.setScore(parseTree.score());
    }
    // Return
    return builder.build();
  }

  /**
   * Create a compact representation of the semantic graph for this dependency parse.
   * @param graph The dependency graph to save.
   * @return A protocol buffer message corresponding to this parse.
   */
  public CoreNLPProtos.DependencyGraph toProto(SemanticGraph graph) {
    CoreNLPProtos.DependencyGraph.Builder builder = CoreNLPProtos.DependencyGraph.newBuilder();
    // Roots
    Set<IndexedWord> rootSet = new IdentityHashSet<IndexedWord>(graph.getRoots());
    // Nodes
    for (IndexedWord node : graph.vertexSet()) {
      // Register node
      CoreNLPProtos.DependencyGraph.Node.Builder nodeBuilder = CoreNLPProtos.DependencyGraph.Node.newBuilder()
          .setSentenceIndex(node.get(SentenceIndexAnnotation.class))
          .setIndex(node.index());
      if (node.containsKey(CopyAnnotation.class)) {
        nodeBuilder.setCopyAnnotation(node.get(CopyAnnotation.class));
      }
      builder.addNode(nodeBuilder.build());
      // Register root
      if (rootSet.contains(node)) {
        builder.addRoot(node.index());
      }
    }
    // Edges
    for (SemanticGraphEdge edge : graph.edgeIterable()) {
      builder.addEdge(CoreNLPProtos.DependencyGraph.Edge.newBuilder()
          .setSource(edge.getSource().index())
          .setTarget(edge.getTarget().index())
          .setDep(edge.getRelation().toString())
          .setIsExtra(edge.isExtra()));
    }
    // Return
    return builder.build();
  }
  /**
   * Create a CorefChain protocol buffer from the given coref chain.
   * @param chain The coref chain to convert.
   * @return A protocol buffer message corresponding to this chain.
   */
  public CoreNLPProtos.CorefChain toProto(CorefChain chain) {
    CoreNLPProtos.CorefChain.Builder builder = CoreNLPProtos.CorefChain.newBuilder();
    // Set ID
    builder.setChainID(chain.getChainID());
    // Set mentions
    Map<CorefChain.CorefMention, Integer> mentionToIndex = new IdentityHashMap<CorefChain.CorefMention, Integer>();
    for (Map.Entry<IntPair, Set<CorefChain.CorefMention>> entry : chain.getMentionMap().entrySet()) {
      for (CorefChain.CorefMention mention : entry.getValue()) {
        mentionToIndex.put(mention, mentionToIndex.size());
        builder.addMention(CoreNLPProtos.CorefChain.CorefMention.newBuilder()
            .setMentionID(mention.mentionID)
            .setMentionType(mention.mentionType.name())
            .setNumber(mention.number.name())
            .setGender(mention.gender.name())
            .setAnimacy(mention.animacy.name())
            .setStartIndex(mention.startIndex - 1)
            .setEndIndex(mention.endIndex - 1)
            .setHeadIndex(mention.headIndex - 1)
            .setSentenceIndex(mention.sentNum - 1)
            .setPosition(mention.position.get(1)) );
      }
    }
    // Set representative mention
    builder.setRepresentative( mentionToIndex.get(chain.getRepresentativeMention()) );
    // Return
    return builder.build();
  }

  /**
   * Convert the given Timex object to a protocol buffer.
   * @param timex The timex to convert.
   * @return A protocol buffer corresponding to this Timex object.
   */
  public CoreNLPProtos.Timex toProto(Timex timex) {
    CoreNLPProtos.Timex.Builder builder = CoreNLPProtos.Timex.newBuilder();
    if (timex.value() != null) { builder.setValue(timex.value()); }
    if (timex.altVal() != null) { builder.setAltValue(timex.altVal()); }
    if (timex.text() != null) { builder.setText(timex.text()); }
    if (timex.timexType() != null) { builder.setType(timex.timexType()); }
    if (timex.tid() != null) { builder.setTid(timex.tid()); }
    if (timex.beginPoint() >= 0) { builder.setBeginPoint(timex.beginPoint()); }
    if (timex.endPoint() >= 0) { builder.setEndPoint(timex.endPoint()); }
    return builder.build();
  }

  /**
   * Serialize the given entity mention to the corresponding protocol buffer.
   * @param ent The entity mention to serialize.
   * @return A protocol buffer corresponding to the serialized entity mention.
   */
  public CoreNLPProtos.Entity toProto(EntityMention ent) {
    CoreNLPProtos.Entity.Builder builder = CoreNLPProtos.Entity.newBuilder();
    // From ExtractionObject
    if (ent.getObjectId() != null) { builder.setObjectID(ent.getObjectId()); }
    if (ent.getExtent() != null) { builder.setExtentStart(ent.getExtent().start()).setExtentEnd(ent.getExtent().end()); }
    if (ent.getType() != null) { builder.setType(ent.getType()); }
    if (ent.getSubType() != null) { builder.setSubtype(ent.getSubType()); }
    // From Entity
    if (ent.getHead() != null) { builder.setHeadStart(ent.getHead().start()); builder.setHeadEnd(ent.getHead().end()); }
    if (ent.getMentionType() != null) { builder.setMentionType(ent.getMentionType()); }
    if (ent.getNormalizedName() != null) { builder.setNormalizedName(ent.getNormalizedName()); }
    if (ent.getSyntacticHeadTokenPosition() >= 0) { builder.setHeadTokenIndex(ent.getSyntacticHeadTokenPosition()); }
    if (ent.getCorefID() != null) { builder.setCorefID(ent.getCorefID()); }
    // Return
    return builder.build();

  }

  /**
   * Serialize the given relation mention to the corresponding protocol buffer.
   * @param rel The relation mention to serialize.
   * @return A protocol buffer corresponding to the serialized relation mention.
   */
  public CoreNLPProtos.Relation toProto(RelationMention rel) {
    CoreNLPProtos.Relation.Builder builder = CoreNLPProtos.Relation.newBuilder();
    // From ExtractionObject
    if (rel.getObjectId() != null) { builder.setObjectID(rel.getObjectId()); }
    if (rel.getExtent() != null) { builder.setExtentStart(rel.getExtent().start()).setExtentEnd(rel.getExtent().end()); }
    if (rel.getType() != null) { builder.setType(rel.getType()); }
    if (rel.getSubType() != null) { builder.setSubtype(rel.getSubType()); }
    // From Relation
    if (rel.getArgNames() != null) { for (String name : rel.getArgNames()) { builder.addArgName(name); } }
    if (rel.getArgs() != null) { for (ExtractionObject arg : rel.getArgs()) { builder.addArg(toProto((EntityMention) arg)); } }
    // Return
    return builder.build();
  }

  /**
   * Create a CoreLabel from its serialized counterpart.
   * Note that this is, by itself, a lossy operation. Fields like the docid (sentence index, etc.) are only known
   * from the enclosing document, and are not tracked in the protobuf.
   * @param proto The serialized protobuf to read the CoreLabel from.
   * @return A CoreLabel, missing the fields that are not stored in the CoreLabel protobuf.
   */
  protected CoreLabel fromProto(CoreNLPProtos.Token proto) {
    CoreLabel word = new CoreLabel();
    // Required fields
    word.setWord(proto.getWord());
    // Optional fields
    if (proto.hasPos()) { word.setTag(proto.getPos()); }
    if (proto.hasValue()) { word.setValue(proto.getValue()); }
    if (proto.hasCategory()) { word.setCategory(proto.getCategory()); }
    if (proto.hasBefore()) { word.setBefore(proto.getBefore()); }
    if (proto.hasAfter()) { word.setAfter(proto.getAfter()); }
    if (proto.hasOriginalText()) { word.setOriginalText(proto.getOriginalText()); }
    if (proto.hasNer()) { word.setNER(proto.getNer()); }
    if (proto.hasLemma()) { word.setLemma(proto.getLemma()); }
    if (proto.hasBeginChar()) { word.setBeginPosition(proto.getBeginChar()); }
    if (proto.hasEndChar()) { word.setEndPosition(proto.getEndChar()); }
    if (proto.hasSpeaker()) { word.set(SpeakerAnnotation.class, proto.getSpeaker()); }
    if (proto.hasUtterance()) { word.set(UtteranceAnnotation.class, proto.getUtterance()); }
    if (proto.hasBeginIndex()) { word.set(BeginIndexAnnotation.class, proto.getBeginIndex()); }
    if (proto.hasEndIndex()) { word.set(EndIndexAnnotation.class, proto.getEndIndex()); }
    if (proto.hasTokenBeginIndex()) { word.set(TokenBeginAnnotation.class, proto.getTokenBeginIndex()); }
    if (proto.hasTokenEndIndex()) { word.set(TokenEndAnnotation.class, proto.getTokenEndIndex()); }
    if (proto.hasNormalizedNER()) { word.set(NormalizedNamedEntityTagAnnotation.class, proto.getNormalizedNER()); }
    if (proto.hasTimexValue()) { word.set(TimexAnnotation.class, fromProto(proto.getTimexValue())); }
    if (proto.hasHasXmlContext() && proto.getHasXmlContext()) { word.set(XmlContextAnnotation.class, proto.getXmlContextList()); }
    if (proto.hasCorefClusterID()) { word.set(CorefClusterIdAnnotation.class, proto.getCorefClusterID()); }
    if (proto.hasAnswer()) { word.set(AnswerAnnotation.class, proto.getAnswer()); }
    // Non-default annotators
    if (proto.hasGender()) { word.set(GenderAnnotation.class, proto.getGender()); }
    if (proto.hasTrueCase()) { word.set(TrueCaseAnnotation.class, proto.getTrueCase()); }
    if (proto.hasTrueCaseText()) { word.set(TrueCaseTextAnnotation.class, proto.getTrueCaseText()); }
    // Return
    return word;
  }

  /**
   * Create a CoreMap representing a sentence from this protocol buffer.
   * This should not be used if you are reading a whole document, as it populates the tokens independent of the
   * document tokens, which is not the behavior an {@link edu.stanford.nlp.pipeline.Annotation} expects.
   *
   * @param proto The protocol buffer to read from.
   * @return A CoreMap representing the sentence.
   */
  public CoreMap fromProto(CoreNLPProtos.Sentence proto) {
    CoreMap lossySentence = fromProtoNoTokens(proto);
    // Add tokens -- missing by default as they're populated as sublists of the
    // document tokens
    List<CoreLabel> tokens = new ArrayList<CoreLabel>();
    for (CoreNLPProtos.Token token : proto.getTokenList()) {
      tokens.add(fromProto(token));
    }
    lossySentence.set(TokensAnnotation.class, tokens);
    // Add text -- missing by default as it's populated from the Document
    lossySentence.set(TextAnnotation.class, recoverOriginalText(tokens, proto));
    // Return
    return lossySentence;
  }

  /**
   * Create a CoreMap representing a sentence from this protocol buffer.
   * Note that the sentence is very lossy -- most glaringly, the tokens are missing, awaiting a document
   * to be filled in from.
   * @param proto The serialized protobuf to read the sentence from.
   * @return A CoreMap, representing a sentence as stored in the protocol buffer (and therefore missing some fields)
   */
  protected CoreMap fromProtoNoTokens(CoreNLPProtos.Sentence proto) {
    CoreMap sentence = new ArrayCoreMap();
    // Required fields
    sentence.set(TokenBeginAnnotation.class, proto.getTokenOffsetBegin());
    sentence.set(TokenEndAnnotation.class, proto.getTokenOffsetEnd());
    // Optional fields
    if (proto.hasSentenceIndex()) { sentence.set(SentenceIndexAnnotation.class, proto.getSentenceIndex()); }
    if (proto.hasCharacterOffsetBegin()) { sentence.set(CharacterOffsetBeginAnnotation.class, proto.getCharacterOffsetBegin()); }
    if (proto.hasCharacterOffsetEnd()) { sentence.set(CharacterOffsetEndAnnotation.class, proto.getCharacterOffsetEnd()); }
    if (proto.hasParseTree()) { sentence.set(TreeAnnotation.class, fromProto(proto.getParseTree())); }
    // Non-default fields
    if (proto.hasHasRelationAnnotations() && proto.getHasRelationAnnotations()) {
      // set entities
      List<EntityMention> entities = new ArrayList<EntityMention>();
      for (CoreNLPProtos.Entity entity : proto.getEntityList()) { entities.add(fromProto(entity, sentence)); }
      sentence.set(EntityMentionsAnnotation.class, entities);
      // set relations
      List<RelationMention> relations = new ArrayList<RelationMention>();
      for (CoreNLPProtos.Relation relation : proto.getRelationList()) { relations.add(fromProto(relation, sentence)); }
      sentence.set(RelationMentionsAnnotation.class, relations);
    }
    // Return
    return sentence;
  }

  /**
   * Returns a complete document, intended to mimic a document passes as input to
   * {@link ProtobufAnnotationSerializer#toProto(Annotation)} as closely as possible.
   * That is, most common fields are serialized, but there is not guarantee that custom additions
   * will be saved and retrieved.
   *
   * @param proto The protocol buffer to read the document from.
   * @return An Annotation corresponding to the read protobuf.
   */
  public Annotation fromProto(CoreNLPProtos.Document proto) {
    // Set text
    Annotation ann = new Annotation(proto.getText());

    // Add tokens
    List<CoreLabel> tokens = new ArrayList<CoreLabel>();
    if (proto.getSentenceCount() > 0) {
      // Populate the tokens from the sentence
      for (CoreNLPProtos.Sentence sentence : proto.getSentenceList()) {
        // It's conceivable that the sentences are not contiguous -- pad this with nulls
        while (sentence.hasTokenOffsetBegin() && tokens.size() < sentence.getTokenOffsetEnd()) {
          tokens.add(null);
        }
        // Read the sentence
        for (CoreNLPProtos.Token token : sentence.getTokenList()) {
          CoreLabel coreLabel = fromProto(token);
          // Set docid
          if (proto.hasDocID()) { coreLabel.setDocID(proto.getDocID()); }
          for (int i = token.getTokenBeginIndex(); i < token.getTokenEndIndex(); ++i) {
            tokens.set(token.getTokenBeginIndex(), coreLabel);
          }
        }
      }
    } else if (proto.getSentencelessTokenCount() > 0) {
      // Eek -- no sentences. Try to recover tokens directly
      if (proto.getSentencelessTokenCount() > 0) {
        for (CoreNLPProtos.Token token : proto.getSentencelessTokenList()) {
          CoreLabel coreLabel = fromProto(token);
          // Set docid
          if (proto.hasDocID()) { coreLabel.setDocID(proto.getDocID()); }
          tokens.add(coreLabel);
        }
      }
    }
    if (!tokens.isEmpty()) { ann.set(TokensAnnotation.class, tokens); }

    // Add sentences
    List<CoreMap> sentences = new ArrayList<CoreMap>(proto.getSentenceCount());
    for (int sentIndex = 0; sentIndex < proto.getSentenceCount(); ++sentIndex) {
      CoreNLPProtos.Sentence sentence = proto.getSentence(sentIndex);
      CoreMap map = fromProtoNoTokens(sentence);
      if (!tokens.isEmpty() && sentence.hasTokenOffsetBegin() && sentence.hasTokenOffsetEnd() &&
          map.get(TokensAnnotation.class) == null) {
        // Set tokens for sentence
        int tokenBegin = sentence.getTokenOffsetBegin();
        int tokenEnd = sentence.getTokenOffsetEnd();
        assert tokenBegin <= tokens.size() && tokenBegin <= tokenEnd;
        assert tokenEnd <= tokens.size();
        map.set(TokensAnnotation.class, tokens.subList(tokenBegin, tokenEnd));
        // Set sentence index + token index + paragraph index
        for (int i = tokenBegin; i < tokenEnd; ++i) {
          tokens.get(i).setSentIndex(sentIndex);
          tokens.get(i).setIndex(i - sentence.getTokenOffsetBegin() + 1);
          if (sentence.hasParagraph()) { tokens.get(i).set(ParagraphAnnotation.class, sentence.getParagraph()); }
        }
        // Set text
        int characterBegin = sentence.getCharacterOffsetBegin();
        int characterEnd = sentence.getCharacterOffsetEnd();
        if (characterEnd <= proto.getText().length()) {
          // The usual case -- get the text from the document text
          map.set(TextAnnotation.class, proto.getText().substring(characterBegin, characterEnd));
        } else {
          // The document text is wrong -- guess the text from the tokens
          map.set(TextAnnotation.class, recoverOriginalText(tokens.subList(tokenBegin, tokenEnd), sentence));
        }
      }
      // End iteration
      sentences.add(map);
    }
    if (!sentences.isEmpty()) { ann.set(SentencesAnnotation.class, sentences); }

    // Set DocID
    String docid = null;
    if (proto.hasDocID()) {
      docid = proto.getDocID();
      ann.set(DocIDAnnotation.class, docid);
    }

    // Set coref chain
    Map<Integer, CorefChain> corefChains = new HashMap<Integer, CorefChain>();
    for (CoreNLPProtos.CorefChain chainProto : proto.getCorefChainList()) {
      CorefChain chain = fromProto(chainProto, ann);
      corefChains.put(chain.getChainID(), chain);
    }
    if (!corefChains.isEmpty()) { ann.set(CorefChainAnnotation.class, corefChains); }

    // Set dependency graphs
    // We need to wait until here, since this is the first time we see tokens
    for (int i = 0; i < proto.getSentenceCount(); ++i) {
      CoreNLPProtos.Sentence sentence = proto.getSentenceList().get(i);
      CoreMap map = sentences.get(i);
      List<CoreLabel> sentenceTokens = map.get(TokensAnnotation.class);
      if (sentence.hasBasicDependencies()) {
        map.set(BasicDependenciesAnnotation.class, fromProto(sentence.getBasicDependencies(), sentenceTokens, docid));
      }
      if (sentence.hasCollapsedDependencies()) {
        map.set(CollapsedDependenciesAnnotation.class, fromProto(sentence.getCollapsedDependencies(), sentenceTokens, docid));
      }
      if (sentence.hasCollapsedCCProcessedDependencies()) {
        map.set(CollapsedCCProcessedDependenciesAnnotation.class, fromProto(sentence.getCollapsedCCProcessedDependencies(), sentenceTokens, docid));
      }
      // Redo some light annotation
      if ( map.containsKey(TokensAnnotation.class) &&
          (!sentence.hasHasNumerizedTokensAnnotation() || sentence.getHasNumerizedTokensAnnotation())) {
        map.set(NumerizedTokensAnnotation.class, NumberNormalizer.findAndMergeNumbers(map));
      }
    }

    // Return
    return ann;
  }

  /**
   * Retrieve a Tree object from a saved protobuf.
   * This is not intended to be used on its own, but it is safe (lossless) to do so and therefore it is
   * left visible.
   *
   * @param proto The serialized tree.
   * @return A Tree object corresponding to the saved tree. This will always be a {@link LabeledScoredTreeNode}.
   */
  public Tree fromProto(CoreNLPProtos.ParseTree proto) {
    LabeledScoredTreeNode node = new LabeledScoredTreeNode();
    // Set label
    if (proto.hasValue()) {
      CoreLabel value = new CoreLabel();
      value.setCategory(proto.getValue());
      value.setValue(proto.getValue());
      node.setLabel(value);
      // Set span
      if (proto.hasYieldBeginIndex() && proto.hasYieldEndIndex()) {
        IntPair span = new IntPair(proto.getYieldBeginIndex(), proto.getYieldEndIndex());
        value.set(SpanAnnotation.class, span);
      }
    }
    // Set score
    if (proto.hasScore()) { node.setScore(proto.getScore()); }
    // Set children
    Tree[] children = new LabeledScoredTreeNode[proto.getChildCount()];
    for (int i = 0; i < children.length; ++i) {
      children[i] = fromProto(proto.getChild(i));
    }
    node.setChildren(children);
    // Return
    return node;
  }

  /**
   * Voodoo magic to convert a serialized dependency graph into a {@link SemanticGraph}.
   * Taken originally from {@link CustomAnnotationSerializer#convertIntermediateGraph(CustomAnnotationSerializer.IntermediateSemanticGraph, java.util.List)}.
   * This method is intended to be called only from the {@link ProtobufAnnotationSerializer#fromProto(CoreNLPProtos.Document)}
   * method.
   *
   * @param proto The serialized representation of the graph. This relies heavily on indexing into the original document.
   * @param sentence The raw sentence that this graph was saved from must be provided, as it is not saved in the serialized
   *                 representation.
   * @param docid A docid must be supplied, as it is not saved by the serialized representation.
   * @return A semantic graph corresponding to the saved object, on the provided sentence.
   */
  private SemanticGraph fromProto(CoreNLPProtos.DependencyGraph proto, List<CoreLabel> sentence, String docid) {
    SemanticGraph graph = new SemanticGraph();

    // first construct the actual nodes; keep them indexed by their index
    // This block is optimized as one of the places which take noticeable time
    // in datum caching
    int min = Integer.MAX_VALUE;
    int max = Integer.MIN_VALUE;
    for(CoreNLPProtos.DependencyGraph.Node in: proto.getNodeList()){
      min = in.getIndex() < min ? in.getIndex() : min;
      max = in.getIndex() > max ? in.getIndex() : max;
    }
    IndexedWord[] nodes = new IndexedWord[max - min >= 0 ? max - min + 1 : 0];
    for(CoreNLPProtos.DependencyGraph.Node in: proto.getNodeList()){
      CoreLabel token = sentence.get(in.getIndex() - 1); // index starts at 1!
      IndexedWord word = new IndexedWord(docid, in.getSentenceIndex(), in.getIndex(), token);
      word.set(ValueAnnotation.class, word.get(TextAnnotation.class));
      if(in.hasCopyAnnotation()){ word.set(CopyAnnotation.class, in.getCopyAnnotation()); }
      assert in.getIndex() == word.index();
      nodes[in.getIndex() - min] = word;
    }
    for (IndexedWord node : nodes) {
      if (node != null) { graph.addVertex(node); }
    }

    // add all edges to the actual graph
    for(CoreNLPProtos.DependencyGraph.Edge ie: proto.getEdgeList()){
      IndexedWord source = nodes[ie.getSource() - min];
      assert(source != null);
      IndexedWord target = nodes[ie.getTarget() - min];
      assert(target != null);
      synchronized (globalLock) {
        // this is not thread-safe: there are static fields in GrammaticalRelation
        assert ie.hasDep();
        GrammaticalRelation rel = GrammaticalRelation.valueOf(ie.getDep());
        graph.addEdge(source, target, rel, 1.0, ie.hasIsExtra() && ie.getIsExtra());
      }
    }

    if (proto.getRootCount() > 0) {
      Collection<IndexedWord> roots = new ArrayList<IndexedWord>();
      for(int rootI : proto.getRootList()){
        roots.add(nodes[rootI - min]);
      }
      graph.setRoots(roots);
    } else {
      // Roots were not saved away
      // compute root nodes if non-empty
      if(!graph.isEmpty()){
        graph.resetRoots();
      }
    }
    return graph;
  }

  /**
   * Read a CorefChain from its serialized representation.
   * This is private due to the need for an additional partial document. Also, why on Earth are you trying to use
   * this on its own anyways?
   * @param proto The serialized representation of the coref chain, missing information on its mention span string.
   * @param partialDocument A partial document, which must contain {@link SentencesAnnotation} and {@link TokensAnnotation} in
   *                        order to fill in the mention span strings.
   * @return A coreference chain.
   */
  private CorefChain fromProto(CoreNLPProtos.CorefChain proto, Annotation partialDocument) {
    // Get chain ID
    int cid = proto.getChainID();
    // Get mentions
    Map<IntPair, Set<CorefChain.CorefMention>> mentions = new HashMap<IntPair, Set<CorefChain.CorefMention>>();
    CorefChain.CorefMention representative = null;
    for (int i = 0; i < proto.getMentionCount(); ++i) {
      CoreNLPProtos.CorefChain.CorefMention mentionProto = proto.getMention(i);
      // Create mention
      StringBuilder mentionSpan = new StringBuilder();
      List<CoreLabel> sentenceTokens = partialDocument.get(SentencesAnnotation.class).get(mentionProto.getSentenceIndex()).get(TokensAnnotation.class);
      for (int k = mentionProto.getStartIndex(); k < mentionProto.getEndIndex(); ++k) {
        mentionSpan.append(" ").append(sentenceTokens.get(k).word());
      }
      // Set the coref cluster id for the token
      CorefChain.CorefMention mention = new CorefChain.CorefMention(
          Dictionaries.MentionType.valueOf(mentionProto.getMentionType()),
          Dictionaries.Number.valueOf(mentionProto.getNumber()),
          Dictionaries.Gender.valueOf(mentionProto.getGender()),
          Dictionaries.Animacy.valueOf(mentionProto.getAnimacy()),
          mentionProto.getStartIndex() + 1,
          mentionProto.getEndIndex() + 1,
          mentionProto.getHeadIndex() + 1,
          cid,
          mentionProto.getMentionID(),
          mentionProto.getSentenceIndex() + 1,
          new IntTuple(new int[]{ mentionProto.getSentenceIndex() + 1, mentionProto.getPosition() }),
          mentionSpan.substring(mentionSpan.length() > 0 ? 1 : 0));
      // Register mention
      IntPair key = new IntPair(mentionProto.getSentenceIndex() - 1, mentionProto.getHeadIndex() - 1);
      if (!mentions.containsKey(key)) { mentions.put(key, new HashSet<CorefChain.CorefMention>()); }
      mentions.get(key).add(mention);
      // Check for representative
      if (proto.hasRepresentative() && i == proto.getRepresentative()) {
        representative = mention;
      }
    }
    // Return
    return new CorefChain(cid, mentions, representative);
  }

  /**
   * Create an internal Timex object from the serialized protocol buffer.
   * @param proto The serialized protocol buffer to read from.
   * @return A timex, with as much information filled in as was gleaned from the protocol buffer.
   */
  private Timex fromProto(CoreNLPProtos.Timex proto) {
    return new Timex(
        proto.hasType() ? proto.getType() : null,
        proto.hasValue() ? proto.getValue() : null,
        proto.hasAltValue() ? proto.getAltValue() : null,
        proto.hasTid() ? proto.getTid() : null,
        proto.hasText() ? proto.getText() : null,
        proto.hasBeginPoint() ? proto.getBeginPoint() : -1,
        proto.hasEndPoint() ? proto.getEndPoint() : -1);
  }

  /**
   * Read a entity mention from its serialized form. Requires the containing sentence to be
   * passed in along with the protocol buffer.
   * @param proto The serialized entity mention.
   * @param sentence The sentence this mention is attached to.
   * @return The entity mention corresponding to the serialized object.
   */
  private EntityMention fromProto(CoreNLPProtos.Entity proto, CoreMap sentence) {
    EntityMention rtn = new EntityMention(
        proto.hasObjectID() ? proto.getObjectID() : null,
        sentence,
        proto.hasHeadStart() ? new Span(proto.getHeadStart(), proto.getHeadEnd()) : null,
        proto.hasHeadEnd() ? new Span(proto.getExtentStart(), proto.getExtentEnd()) : null,
        proto.hasType() ? proto.getType() : null,
        proto.hasSubtype() ? proto.getSubtype() : null,
        proto.hasMentionType() ? proto.getMentionType() : null );
    if (proto.hasNormalizedName()) { rtn.setNormalizedName(proto.getNormalizedName()); }
    if (proto.hasHeadTokenIndex()) { rtn.setHeadTokenPosition(proto.getHeadTokenIndex()); }
    if (proto.hasCorefID()) { rtn.setCorefID(proto.getCorefID()); }
    return rtn;
  }

  /**
   * Read a relation mention from its serialized form. Requires the containing sentence to be
   * passed in along with the protocol buffer.
   * @param proto The serialized relation mention.
   * @param sentence The sentence this mention is attached to.
   * @return The relation mention corresponding to the serialized object.
   */
  private RelationMention fromProto(CoreNLPProtos.Relation proto, CoreMap sentence) {
    List<ExtractionObject> args = new ArrayList<ExtractionObject>();
    for (CoreNLPProtos.Entity arg : proto.getArgList()) {
      args.add(fromProto(arg, sentence));
    }
    RelationMention rtn = new RelationMention(
        proto.hasObjectID() ? proto.getObjectID() : null,
        sentence,
        proto.hasExtentStart() ? new Span(proto.getExtentStart(), proto.getExtentEnd()) : null,
        proto.hasType() ? proto.getType() : null,
        proto.hasSubtype() ? proto.getSubtype() : null,
        args);
    if (proto.hasSignature()) { rtn.setSignature(proto.getSignature()); }
    if (proto.getArgNameCount() > 0 || proto.getArgCount() == 0) {
      rtn.setArgNames(proto.getArgNameList());
    }
    return rtn;
  }

  /**
   * Recover the {@link edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation} field of a sentence
   * from the tokens. This is useful if the text was not set in the protocol buffer, and therefore
   * needs to be reconstructed from tokens.
   *
   * @param tokens The list of tokens representing this sentence.
   * @return The original text of the sentence.
   */
  protected String recoverOriginalText(List<CoreLabel> tokens, CoreNLPProtos.Sentence sentence) {
    StringBuilder text = new StringBuilder();
    CoreLabel last = null;
    if (tokens.size() > 0) {
      CoreLabel token = tokens.get(0);
      if (token.originalText() != null) { text.append(token.originalText()); } else { text.append(token.word()); }
      last = tokens.get(0);
    }
    for (int i = 1; i < tokens.size(); ++i) {
      CoreLabel token = tokens.get(i);
      if (token.before() != null) {
        text.append(token.before());
        assert last != null;
        int missingWhitespace = (token.beginPosition() - last.endPosition()) - token.before().length();
        while (missingWhitespace > 0) {
          text.append(' ');
          missingWhitespace -= 1;
        }
      }
      if (token.originalText() != null) { text.append(token.originalText()); } else { text.append(token.word()); }
      last = token;
    }
    return text.toString();
  }
}
