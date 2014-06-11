package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.Dictionaries;
import edu.stanford.nlp.ie.NumberNormalizer;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.LabeledScoredTreeNode;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.ling.CoreAnnotations.*;
import edu.stanford.nlp.trees.TreeCoreAnnotations.*;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.*;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

/**
 * A serializer using Google's protobuf format.
 * Note that this serializes only a subset of the possible annotations
 * that can be attached to a sentence. Nonetheless, it is guaranteed to be
 * lossless with the default set of named annotators you can create from a
 * {@link StanfordCoreNLP} pipeline.
 *
 * @author Gabor Angeli
 */
public class ProtobufAnnotationSerializer extends AnnotationSerializer {

  private static final Object globalLock = "I'm a lock :)";

  @Override
  public OutputStream append(Annotation corpus, OutputStream os) throws IOException {
    CoreMapProtos.Document serialized = toProto(corpus);
    serialized.writeTo(os);
    return os;
  }

  @Override
  public Pair<Annotation, InputStream> read(InputStream is) throws IOException, ClassNotFoundException, ClassCastException {
    CoreMapProtos.Document doc = CoreMapProtos.Document.parseFrom(is);
    return Pair.makePair( fromProto(doc), is );
  }

  /**
   * Create a CoreLabel proto from a CoreLabel instance
   * @param coreLabel The CoreLabel to convert
   * @return A protocol buffer message corresponding to this CoreLabel
   */
  public static CoreMapProtos.CoreLabel toProto(CoreLabel coreLabel) {
    CoreMapProtos.CoreLabel.Builder builder = CoreMapProtos.CoreLabel.newBuilder();
    // Required fields
    builder.setWord(coreLabel.word());
    // Optional fields
    if (coreLabel.tag() != null) { builder.setPos(coreLabel.tag()); }
    if (coreLabel.value() != null) { builder.setValue(coreLabel.value()); }
    if (coreLabel.category() != null) { builder.setCategory(coreLabel.category()); }
    if (coreLabel.before() != null) { builder.setBefore(coreLabel.before()); }
    if (coreLabel.after() != null) { builder.setAfter(coreLabel.after()); }
    if (coreLabel.originalText() != null) { builder.setOriginalText(coreLabel.originalText()); }
    if (coreLabel.ner() != null) { builder.setNer(coreLabel.ner()); }
    if (coreLabel.beginPosition() >= 0) { builder.setBeginChar(coreLabel.beginPosition()); }
    if (coreLabel.endPosition() >= 0) { builder.setEndChar(coreLabel.endPosition()); }
    if (coreLabel.lemma() != null) { builder.setLemma(coreLabel.lemma()); }
    if (coreLabel.containsKey(UtteranceAnnotation.class)) { builder.setUtterance(coreLabel.get(UtteranceAnnotation.class)); }
    if (coreLabel.containsKey(SpeakerAnnotation.class)) { builder.setSpeaker(coreLabel.get(SpeakerAnnotation.class)); }
    if (coreLabel.containsKey(BeginIndexAnnotation.class)) { builder.setBeginIndex(coreLabel.get(BeginIndexAnnotation.class)); }
    if (coreLabel.containsKey(EndIndexAnnotation.class)) { builder.setEndIndex(coreLabel.get(EndIndexAnnotation.class)); }
    if (coreLabel.containsKey(TokenBeginAnnotation.class)) { builder.setTokenBeginIndex(coreLabel.get(TokenBeginAnnotation.class)); }
    if (coreLabel.containsKey(TokenEndAnnotation.class)) { builder.setTokenEndIndex(coreLabel.get(TokenEndAnnotation.class)); }
    if (coreLabel.get(NormalizedNamedEntityTagAnnotation.class) != null) { builder.setNormalizedNER(coreLabel.get(NormalizedNamedEntityTagAnnotation.class)); }
    // Return
    return builder.build();
  }

  /**
   * Create a Sentence proto from a CoreMap instance
   * @param sentence The CoreMap to convert. Note that it should not be a CoreLabel or an Annotation,
   *                 and should represent a sentence.
   * @return A protocol buffer message corresponding to this sentence
   * @throws IllegalArgumentException If the sentence is not a valid sentence (e.g., is a document or a word).
   */
  public static CoreMapProtos.Sentence toProto(CoreMap sentence) {
    // Error checks
    if (sentence instanceof CoreLabel) { throw new IllegalArgumentException("CoreMap is actually a CoreLabel"); }
    CoreMapProtos.Sentence.Builder builder = CoreMapProtos.Sentence.newBuilder();
    // Required fields
    builder.setTokenOffsetBegin(sentence.get(TokenBeginAnnotation.class));
    builder.setTokenOffsetEnd(sentence.get(TokenEndAnnotation.class));
    // Optional fields
    if (sentence.containsKey(SentenceIndexAnnotation.class)) { builder.setSentenceIndex(sentence.get(SentenceIndexAnnotation.class)); }
    if (sentence.containsKey(CharacterOffsetBeginAnnotation.class)) { builder.setCharacterOffsetBegin(sentence.get(CharacterOffsetBeginAnnotation.class)); }
    if (sentence.containsKey(CharacterOffsetEndAnnotation.class)) { builder.setCharacterOffsetEnd(sentence.get(CharacterOffsetEndAnnotation.class)); }
    if (sentence.containsKey(TreeAnnotation.class)) { builder.setParseTree(toProto(sentence.get(TreeAnnotation.class))); }
    if (sentence.containsKey(BasicDependenciesAnnotation.class)) { builder.setBasicDependencies(toProto(sentence.get(BasicDependenciesAnnotation.class))); }
    if (sentence.containsKey(CollapsedDependenciesAnnotation.class)) { builder.setCollapsedDependencies(toProto(sentence.get(CollapsedDependenciesAnnotation.class))); }
    if (sentence.containsKey(CollapsedCCProcessedDependenciesAnnotation.class)) { builder.setCollapsedCCProcessedDependencies(toProto(sentence.get(CollapsedCCProcessedDependenciesAnnotation.class))); }
    if (sentence.containsKey(TokensAnnotation.class) && sentence.get(TokensAnnotation.class).size() > 0 &&
        sentence.get(TokensAnnotation.class).get(0).containsKey(ParagraphAnnotation.class)) {
      builder.setParagraph(sentence.get(TokensAnnotation.class).get(0).get(ParagraphAnnotation.class));
    }
    // Return
    return builder.build();
  }

  /**
   * Create a Document proto from a CoreMap instance
   * @param doc The Annotation to convert.
   * @return A protocol buffer message corresponding to this document
   */
  public static CoreMapProtos.Document toProto(Annotation doc) {
    CoreMapProtos.Document.Builder builder = CoreMapProtos.Document.newBuilder();
    // Required fields
    builder.setText(doc.get(TextAnnotation.class));
    // Optional fields
    if (doc.containsKey(TokensAnnotation.class)) {
      for (CoreLabel tok : doc.get(TokensAnnotation.class)) { builder.addToken(toProto(tok)); }
    }
    if (doc.containsKey(SentencesAnnotation.class)) {
      for (CoreMap sentence : doc.get(SentencesAnnotation.class)) { builder.addSentence(toProto(sentence)); }
    }
    if (doc.containsKey(DocIDAnnotation.class)) { builder.setDocID(doc.get(DocIDAnnotation.class)); }
    if (doc.containsKey(CorefChainAnnotation.class)) {
      for (Map.Entry<Integer, CorefChain> chain : doc.get(CorefChainAnnotation.class).entrySet()) {
       builder.addCorefChain(toProto(chain.getValue()));
      }
    }
    // Return
    return builder.build();
  }

  /**
   * Create a ParseTree proto from a Tree. If the Tree is a scored tree, the scores will
   * be preserved.
   * @param parseTree The parse tree to convert.
   * @return A protocol buffer message corresponding to this tree.
   */
  public static CoreMapProtos.ParseTree toProto(Tree parseTree) {
    CoreMapProtos.ParseTree.Builder builder = CoreMapProtos.ParseTree.newBuilder();
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
  public static CoreMapProtos.DependencyGraph toProto(SemanticGraph graph) {
    CoreMapProtos.DependencyGraph.Builder builder = CoreMapProtos.DependencyGraph.newBuilder();
    // Roots
    Set<IndexedWord> rootSet = new IdentityHashSet<IndexedWord>(graph.getRoots());
    // Nodes
    int nodeI = 0;
    for (IndexedWord node : graph.vertexSet()) {
      // Register node
      CoreMapProtos.DependencyGraph.Node.Builder nodeBuilder = CoreMapProtos.DependencyGraph.Node.newBuilder()
          .setSentenceIndex(node.get(SentenceIndexAnnotation.class))
          .setIndex(node.index());
      if (node.containsKey(CopyAnnotation.class)) {
        nodeBuilder.setCopyAnnotation(node.get(CopyAnnotation.class));
      }
      builder.addNode(nodeBuilder.build());
      // Register root
      if (rootSet.contains(node)) {
        builder.addRoot(nodeI);
      }
      nodeI += 1;
    }
    // Edges
    for (SemanticGraphEdge edge : graph.edgeIterable()) {
      builder.addEdge(CoreMapProtos.DependencyGraph.Edge.newBuilder()
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
  public static CoreMapProtos.CorefChain toProto(CorefChain chain) {
    CoreMapProtos.CorefChain.Builder builder = CoreMapProtos.CorefChain.newBuilder();
    // Set ID
    builder.setChainID(chain.getChainID());
    // Set mentions
    Map<CorefChain.CorefMention, Integer> mentionToIndex = new IdentityHashMap<CorefChain.CorefMention, Integer>();
    for (Map.Entry<IntPair, Set<CorefChain.CorefMention>> entry : chain.getMentionMap().entrySet()) {
      for (CorefChain.CorefMention mention : entry.getValue()) {
        mentionToIndex.put(mention, mentionToIndex.size());
        builder.addMention(CoreMapProtos.CorefChain.CorefMention.newBuilder()
            .setMentionID(mention.mentionID)
            .setMentionType(mention.mentionType.name())
            .setNumber(mention.number.name())
            .setGender(mention.gender.name())
            .setAnimacy(mention.animacy.name())
            .setStartIndex(mention.startIndex - 1)
            .setEndIndex(mention.endIndex - 1)
            .setHeadIndex(mention.headIndex - 1)
            .setSentenceIndex(mention.sentNum - 1)
            .setPosition(mention.position.get(1)));
      }
    }
    // Set representative mention
    builder.setRepresentative( mentionToIndex.get(chain.getRepresentativeMention()) );
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
  private static CoreLabel fromProto(CoreMapProtos.CoreLabel proto) {
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
    // Return
    return word;
  }

  /**
   * Create a CoreMap representing a sentence from this protocol buffer.
   * Note that the sentence is very lossy -- most glaringly, the tokens are missing, awaiting a document
   * to be filled in from.
   * @param proto The serialized protobuf to read the sentence from.
   * @return A CoreMap, representing a sentence as stored in the protocol buffer (and therefore missing some fields)
   */
  private static CoreMap fromProto(CoreMapProtos.Sentence proto) {
    CoreMap sentence = new ArrayCoreMap();
    // Required fields
    sentence.set(TokenBeginAnnotation.class, proto.getTokenOffsetBegin());
    sentence.set(TokenEndAnnotation.class, proto.getTokenOffsetEnd());
    // Optional fields
    if (proto.hasSentenceIndex()) { sentence.set(SentenceIndexAnnotation.class, proto.getSentenceIndex()); }
    if (proto.hasCharacterOffsetBegin()) { sentence.set(CharacterOffsetBeginAnnotation.class, proto.getCharacterOffsetBegin()); }
    if (proto.hasCharacterOffsetEnd()) { sentence.set(CharacterOffsetEndAnnotation.class, proto.getCharacterOffsetEnd()); }
    if (proto.hasParseTree()) { sentence.set(TreeAnnotation.class, fromProto(proto.getParseTree())); }
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
  public static Annotation fromProto(CoreMapProtos.Document proto) {
    // Set text
    Annotation ann = new Annotation(proto.getText());

    // Add tokens
    List<CoreLabel> tokens = new ArrayList<CoreLabel>(proto.getTokenCount());
    for (CoreMapProtos.CoreLabel token : proto.getTokenList()) {
      CoreLabel coreLabel = fromProto(token);
      // Set docid
      if (proto.hasDocID()) { coreLabel.setDocID(proto.getDocID()); }
      tokens.add(coreLabel);
    }
    if (!tokens.isEmpty()) { ann.set(TokensAnnotation.class, tokens); }

    // Add sentences
    List<CoreMap> sentences = new ArrayList<CoreMap>(proto.getSentenceCount());
    for (int sentIndex = 0; sentIndex < proto.getSentenceCount(); ++sentIndex) {
      CoreMapProtos.Sentence sentence = proto.getSentence(sentIndex);
      CoreMap map = fromProto(sentence);
      if (!tokens.isEmpty() && sentence.hasTokenOffsetBegin() && sentence.hasTokenOffsetEnd()) {
        // Set tokens for sentence
        map.set(TokensAnnotation.class, tokens.subList(sentence.getTokenOffsetBegin(), sentence.getTokenOffsetEnd()));
        // Set sentence index + token index + paragraph index
        for (int i = sentence.getTokenOffsetBegin(); i < sentence.getTokenOffsetEnd(); ++i) {
          tokens.get(i).setSentIndex(sentIndex);
          tokens.get(i).setIndex(i - sentence.getTokenOffsetBegin() + 1);
          if (sentence.hasParagraph()) { tokens.get(i).set(ParagraphAnnotation.class, sentence.getParagraph()); }
        }
        // Set text
        map.set(TextAnnotation.class, proto.getText().substring(sentence.getCharacterOffsetBegin(), sentence.getCharacterOffsetEnd()));
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
    for (CoreMapProtos.CorefChain chainProto : proto.getCorefChainList()) {
      CorefChain chain = fromProto(chainProto, ann);
      corefChains.put(chain.getChainID(), chain);
    }
    if (!corefChains.isEmpty()) { ann.set(CorefChainAnnotation.class, corefChains); }

    // Set dependency graphs
    // We need to wait until here, since this is the first time we see tokens
    for (int i = 0; i < proto.getSentenceCount(); ++i) {
      CoreMapProtos.Sentence sentence = proto.getSentenceList().get(i);
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
    }

    // Redo some light annotation
    for (CoreMap sentence : sentences) {
      if (sentence.containsKey(TokensAnnotation.class)) {
        sentence.set(NumerizedTokensAnnotation.class, NumberNormalizer.findAndMergeNumbers(sentence));
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
  public static Tree fromProto(CoreMapProtos.ParseTree proto) {
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
   * This method is intended to be called only from the {@link ProtobufAnnotationSerializer#fromProto(CoreMapProtos.Document)}
   * method.
   *
   * @param proto The serialized representation of the graph. This relies heavily on indexing into the original document.
   * @param sentence The raw sentence that this graph was saved from must be provided, as it is not saved in the serialized
   *                 representation.
   * @param docid A docid must be supplied, as it is not saved by the serialized representation.
   * @return A semantic graph corresponding to the saved object, on the provided sentence.
   */
  private static SemanticGraph fromProto(CoreMapProtos.DependencyGraph proto, List<CoreLabel> sentence, String docid) {
    SemanticGraph graph = new SemanticGraph();

    // first construct the actual nodes; keep them indexed by their index
    // This block is optimized as one of the places which take noticeable time
    // in datum caching
    int min = Integer.MAX_VALUE;
    int max = Integer.MIN_VALUE;
    for(CoreMapProtos.DependencyGraph.Node in: proto.getNodeList()){
      min = in.getIndex() < min ? in.getIndex() : min;
      max = in.getIndex() > max ? in.getIndex() : max;
    }
    IndexedWord[] nodes = new IndexedWord[max - min >= 0 ? max - min + 1 : 0];
    for(CoreMapProtos.DependencyGraph.Node in: proto.getNodeList()){
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
    for(CoreMapProtos.DependencyGraph.Edge ie: proto.getEdgeList()){
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
        roots.add(nodes[rootI]);
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
  private static CorefChain fromProto(CoreMapProtos.CorefChain proto, Annotation partialDocument) {
    // Get chain ID
    int cid = proto.getChainID();
    // Get mentions
    Map<IntPair, Set<CorefChain.CorefMention>> mentions = new HashMap<IntPair, Set<CorefChain.CorefMention>>();
    CorefChain.CorefMention representative = null;
    for (int i = 0; i < proto.getMentionCount(); ++i) {
      CoreMapProtos.CorefChain.CorefMention mentionProto = proto.getMention(i);
      // Create mention
      StringBuilder mentionSpan = new StringBuilder();
      List<CoreLabel> sentenceTokens = partialDocument.get(SentencesAnnotation.class).get(mentionProto.getSentenceIndex()).get(TokensAnnotation.class);
      for (int k = mentionProto.getStartIndex(); k < mentionProto.getEndIndex(); ++k) {
        mentionSpan.append(" ").append(sentenceTokens.get(k).word());
      }
      sentenceTokens.get(mentionProto.getHeadIndex()).set(CorefClusterIdAnnotation.class, cid);
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
          mentionSpan.substring(1));
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

}
