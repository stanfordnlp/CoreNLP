package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import org.junit.Test;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.CoNLLUReader;
import edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.CoreMap;

import static edu.stanford.nlp.pipeline.CoreNLPProtos.DependencyGraph;
import static edu.stanford.nlp.pipeline.CoreNLPProtos.SemgrexRequest;
import static edu.stanford.nlp.pipeline.CoreNLPProtos.SsurgeonRequest;
import static edu.stanford.nlp.pipeline.CoreNLPProtos.SsurgeonResponse;

public class ProcessSsurgeonRequestTest {
  public static SsurgeonRequest buildRequest(List<SsurgeonRequest.Ssurgeon> operations,
                                             List<DependencyGraph> graphs) {
    SsurgeonRequest.Builder builder = SsurgeonRequest.newBuilder();
    for (SsurgeonRequest.Ssurgeon operation : operations) {
      builder.addSsurgeon(operation);
    }
    for (DependencyGraph graph : graphs) {
      builder.addGraph(graph);
    }
    return builder.build();
  }

  public static List<DependencyGraph> buildGraphs(String ... graphs) {
    ProtobufAnnotationSerializer serializer = new ProtobufAnnotationSerializer();
    List<SemanticGraph> semgraphs = Arrays.asList(graphs).stream().map(SemanticGraph::valueOf).collect(Collectors.toList());;
    List<DependencyGraph> dgraphs = semgraphs.stream().map((x) -> serializer.toProto(x, true)).collect(Collectors.toList());;
    return dgraphs;
  }

  public static List<SsurgeonRequest.Ssurgeon> buildOperationList(SsurgeonRequest.Ssurgeon ... operations) {
    return Arrays.asList(operations);
  }

  public SsurgeonRequest.Ssurgeon buildOperation(String uid,
                                                 String notes,
                                                 String semgrex,
                                                 String ... edits) {
    SsurgeonRequest.Ssurgeon.Builder builder = SsurgeonRequest.Ssurgeon.newBuilder();
    builder.setId(uid);
    builder.setNotes(notes);
    builder.setSemgrex(semgrex);
    for (String edit : edits) {
      builder.addOperation(edit);
    }
    return builder.build();
  }

  /**
   * Test one graph with one operation
   */
  @Test
  public void testSimpleRequest() {
    List<SsurgeonRequest.Ssurgeon> operations = buildOperationList(buildOperation("1",
                                                                                  "Test of addEdge",
                                                                                  "{}=a1 >obj {}=a2",
                                                                                  "addEdge -gov a1 -dep a2 -reln dep -weight 0.5"));
    ProtobufAnnotationSerializer serializer = new ProtobufAnnotationSerializer();

    List<DependencyGraph> graphs = buildGraphs("[A-1 obj> B-2 obj> C-3 nsubj> [D-4 obj> E-5]]");
    SsurgeonRequest request = buildRequest(operations, graphs);
    SsurgeonResponse response = ProcessSsurgeonRequest.processRequest(request);
    assertEquals(1, response.getResultList().size());
    assertTrue(response.getResultList().get(0).getChanged());

    DependencyGraph inputGraph = response.getResultList().get(0).getGraph();
    List<CoreLabel> tokens = inputGraph.getTokenList().stream().map(serializer::fromProto).collect(Collectors.toList());
    SemanticGraph resultGraph = ProtobufAnnotationSerializer.fromProto(inputGraph, tokens, null);
    SemanticGraph expected = SemanticGraph.valueOf("[A-1 obj> B-2 dep> B-2 obj> C-3 dep> C-3 nsubj> [D-4 obj> E-5 dep> E-5]]");
    assertEquals(expected, resultGraph);
  }

  /** "I want to go now", with an nsubj:xsubj from go to I in the enhanced graph */
  private static final String WANT_TO_GO = String.join("\n",
    "# text = I want to go now",
    "1\tI\tI\tPRON\t_\t_\t2\tnsubj\t2:nsubj|4:nsubj:xsubj\t_",
    "2\twant\twant\tVERB\t_\t_\t0\troot\t0:root\t_",
    "3\tto\tto\tPART\t_\t_\t4\tmark\t4:mark\t_",
    "4\tgo\tgo\tVERB\t_\t_\t2\txcomp\t2:xcomp\t_",
    "5\tnow\tnow\tADV\t_\t_\t2\tadvmod\t2:advmod\t_",
    "",
    "");

  /** "Sue likes coffee and Bill tea", with the empty word 5.1 for the second likes */
  private static final String EMPTY_WORD = String.join("\n",
    "# text = Sue likes coffee and Bill tea",
    "1\tSue\tSue\tPROPN\t_\t_\t2\tnsubj\t2:nsubj\t_",
    "2\tlikes\tlike\tVERB\t_\t_\t0\troot\t0:root\t_",
    "3\tcoffee\tcoffee\tNOUN\t_\t_\t2\tobj\t2:obj\t_",
    "4\tand\tand\tCCONJ\t_\t_\t5\tcc\t5.1:cc\t_",
    "5\tBill\tBill\tPROPN\t_\t_\t2\tconj\t5.1:nsubj\t_",
    "5.1\tlikes\tlike\tVERB\t_\t_\t_\t_\t2:conj:and\t_",
    "6\ttea\ttea\tNOUN\t_\t_\t5\torphan\t5.1:obj\t_",
    "",
    "");

  /**
   * A CoNLL-U sentence as a request sentence: the tokens of its words,
   * empty words included, its basic graph, and its enhanced graph if
   * withEnhanced is set
   */
  private static SemgrexRequest.Dependencies buildSentence(String conllu, String sentenceID, boolean withEnhanced) throws IOException {
    File file = File.createTempFile("ssurgeon", ".conllu");
    file.deleteOnExit();
    Files.write(file.toPath(), conllu.getBytes(StandardCharsets.UTF_8));
    List<Annotation> docs = new CoNLLUReader().readCoNLLUFile(file.getPath());
    CoreMap sentence = docs.get(0).get(CoreAnnotations.SentencesAnnotation.class).get(0);
    SemanticGraph basic = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    SemanticGraph enhanced = sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);

    // CoNLLUReader records the line number of each word, which a request has no need of
    ProtobufAnnotationSerializer serializer = new ProtobufAnnotationSerializer(false);
    SemgrexRequest.Dependencies.Builder builder = SemgrexRequest.Dependencies.newBuilder();
    for (IndexedWord word : enhanced.vertexListSorted()) {
      builder.addToken(serializer.toProto(word.backingLabel()));
    }
    builder.setGraph(serializer.toProto(basic, false));
    if (withEnhanced) {
      builder.setEnhancedGraph(serializer.toProto(enhanced, false));
    }
    if (sentenceID != null) {
      builder.setSentenceID(sentenceID);
    }
    return builder.build();
  }

  /** A request with the operations and the sentences */
  private static SsurgeonRequest buildSentenceRequest(List<SsurgeonRequest.Ssurgeon> operations, SemgrexRequest.Dependencies ... sentences) {
    SsurgeonRequest.Builder builder = SsurgeonRequest.newBuilder();
    builder.addAllSsurgeon(operations);
    builder.addAllSentence(Arrays.asList(sentences));
    return builder.build();
  }

  /** The tokens of a returned sentence, as a client would read them */
  private static List<CoreLabel> tokensOf(SemgrexRequest.Dependencies sentence) {
    ProtobufAnnotationSerializer serializer = new ProtobufAnnotationSerializer();
    return sentence.getTokenList().stream().map(serializer::fromProto).collect(Collectors.toList());
  }

  /** The words of a graph in order, each as its index and text, such as {@code 5.1=likes} */
  private static String wordsOf(SemanticGraph graph) {
    List<String> words = new ArrayList<>();
    for (IndexedWord word : graph.vertexListSorted()) {
      words.add(word.toCopyOrEmptyIndex() + "=" + word.word());
    }
    return String.join(" ", words);
  }

  /** The relations of the edges from gov to dep, each named by its toCopyOrEmptyIndex */
  private static List<String> relations(SemanticGraph graph, String gov, String dep) {
    List<String> relations = new ArrayList<>();
    for (SemanticGraphEdge edge : graph.edgeIterable()) {
      if (edge.getGovernor().toCopyOrEmptyIndex().equals(gov) && edge.getDependent().toCopyOrEmptyIndex().equals(dep)) {
        relations.add(edge.getRelation().toString());
      }
    }
    return relations;
  }

  /**
   * A sentence sent with its enhanced graph comes back with both graphs
   * edited: addDep puts its word in both, and the words after it move
   * up in both
   */
  @Test
  public void testSentenceAddDep() throws IOException {
    List<SsurgeonRequest.Ssurgeon> operations = buildOperationList(buildOperation("1", "addDep in both graphs",
                                                                                  "{word:go}=g !> {word:quickly}",
                                                                                  "addDep -gov g -reln advmod -word quickly -position +g"));
    SsurgeonRequest request = buildSentenceRequest(operations, buildSentence(WANT_TO_GO, "test-1", true));
    SsurgeonResponse response = ProcessSsurgeonRequest.processRequest(request);
    assertEquals(0, response.getResultCount());
    assertEquals(1, response.getSentenceResultCount());
    SsurgeonResponse.SsurgeonResult result = response.getSentenceResult(0);
    assertTrue(result.getChanged());
    assertFalse(result.hasGraph());

    SemgrexRequest.Dependencies sentence = result.getSentence();
    assertEquals("test-1", sentence.getSentenceID());
    assertTrue(sentence.hasEnhancedGraph());
    List<CoreLabel> tokens = tokensOf(sentence);
    assertEquals(6, tokens.size());
    SemanticGraph basic = ProtobufAnnotationSerializer.fromProto(sentence.getGraph(), tokens, null);
    SemanticGraph enhanced = ProtobufAnnotationSerializer.fromProto(sentence.getEnhancedGraph(), tokens, null);
    assertEquals("1=I 2=want 3=to 4=go 5=quickly 6=now", wordsOf(basic));
    assertEquals(wordsOf(basic), wordsOf(enhanced));
    assertEquals(Arrays.asList("advmod"), relations(basic, "4", "5"));
    assertEquals(Arrays.asList("advmod"), relations(enhanced, "4", "5"));
    assertEquals(Arrays.asList("nsubj:xsubj"), relations(enhanced, "4", "1"));
    assertEquals(Arrays.asList("advmod"), relations(enhanced, "2", "6"));
  }

  /**
   * Deleting a word moves the empty word after it to follow the word
   * before it, and the empty word comes back as a token of its own
   */
  @Test
  public void testSentenceEmptyWord() throws IOException {
    List<SsurgeonRequest.Ssurgeon> operations = buildOperationList(buildOperation("1", "delete and", "{word:and}=a", "deleteLeaf -node a"));
    SsurgeonRequest request = buildSentenceRequest(operations, buildSentence(EMPTY_WORD, "test-2", true));
    SsurgeonResponse response = ProcessSsurgeonRequest.processRequest(request);
    SemgrexRequest.Dependencies sentence = response.getSentenceResult(0).getSentence();
    List<CoreLabel> tokens = tokensOf(sentence);
    assertEquals(6, tokens.size());
    assertEquals(1, tokens.get(4).getEmptyIndex());
    assertEquals(4, tokens.get(4).index());

    SemanticGraph basic = ProtobufAnnotationSerializer.fromProto(sentence.getGraph(), tokens, null);
    SemanticGraph enhanced = ProtobufAnnotationSerializer.fromProto(sentence.getEnhancedGraph(), tokens, null);
    assertEquals("1=Sue 2=likes 3=coffee 4=Bill 5=tea", wordsOf(basic));
    assertEquals("1=Sue 2=likes 3=coffee 4=Bill 4.1=likes 5=tea", wordsOf(enhanced));
    assertEquals(Arrays.asList("nsubj"), relations(enhanced, "4.1", "4"));
    assertEquals(Arrays.asList("obj"), relations(enhanced, "4.1", "5"));
    assertEquals(Arrays.asList("conj:and"), relations(enhanced, "2", "4.1"));
  }

  /** The semgrex can search the enhanced graph of a request sentence */
  @Test
  public void testSentenceSearchesEnhanced() throws IOException {
    List<SsurgeonRequest.Ssurgeon> operations = buildOperationList(buildOperation("1", "edit the governor of an xsubj",
                                                                                  "{}=gov >/nsubj:xsubj/@enhanced {}",
                                                                                  "editNode -node gov -lemma GO"));
    SsurgeonResponse response = ProcessSsurgeonRequest.processRequest(buildSentenceRequest(operations, buildSentence(WANT_TO_GO, null, true)));
    assertTrue(response.getSentenceResult(0).getChanged());
    SemgrexRequest.Dependencies sentence = response.getSentenceResult(0).getSentence();
    assertFalse(sentence.hasSentenceID());
    assertEquals("GO", tokensOf(sentence).get(3).lemma());
  }

  /**
   * A request can send graphs and sentences together, and each comes
   * back in its own list.  A sentence without an enhanced graph comes
   * back without one.
   */
  @Test
  public void testGraphsAndSentences() throws IOException {
    List<SsurgeonRequest.Ssurgeon> operations = buildOperationList(buildOperation("1", "Test of addEdge",
                                                                                  "{}=a1 >nsubj {}=a2",
                                                                                  "addEdge -gov a1 -dep a2 -reln dep"));
    SsurgeonRequest.Builder builder = SsurgeonRequest.newBuilder();
    builder.addAllSsurgeon(operations);
    builder.addAllGraph(buildGraphs("[A-1 nsubj> B-2]"));
    builder.addSentence(buildSentence(WANT_TO_GO, "test-3", false));
    SsurgeonResponse response = ProcessSsurgeonRequest.processRequest(builder.build());
    assertEquals(1, response.getResultCount());
    assertEquals(1, response.getSentenceResultCount());

    ProtobufAnnotationSerializer serializer = new ProtobufAnnotationSerializer();
    DependencyGraph graph = response.getResult(0).getGraph();
    assertFalse(response.getResult(0).hasSentence());
    List<CoreLabel> graphTokens = graph.getTokenList().stream().map(serializer::fromProto).collect(Collectors.toList());
    assertEquals(SemanticGraph.valueOf("[A-1 nsubj> B-2 dep> B-2]"), ProtobufAnnotationSerializer.fromProto(graph, graphTokens, null));

    SemgrexRequest.Dependencies sentence = response.getSentenceResult(0).getSentence();
    assertFalse(sentence.hasEnhancedGraph());
    SemanticGraph basic = ProtobufAnnotationSerializer.fromProto(sentence.getGraph(), tokensOf(sentence), null);
    assertEquals(Arrays.asList("nsubj", "dep"), relations(basic, "2", "1"));
  }

  /** An edit which fails on a sentence says which sentence it was */
  @Test
  public void testSentenceFailureNamesSentence() throws IOException {
    List<SsurgeonRequest.Ssurgeon> operations = buildOperationList(buildOperation("before empty", "put a word before an empty word",
                                                                                  "{word:Bill}=b <nsubj@enhanced {}=e",
                                                                                  "addDep -gov b -reln dep -word x -position -e"));
    RuntimeException e = assertThrows(RuntimeException.class, () -> ProcessSsurgeonRequest.processRequest(buildSentenceRequest(operations, buildSentence(EMPTY_WORD, "test-4", true))));
    assertTrue(e.getMessage(), e.getMessage().contains("sent_id |test-4|"));
    assertTrue(e.getMessage(), e.getMessage().contains("before empty"));

    e = assertThrows(RuntimeException.class, () -> ProcessSsurgeonRequest.processRequest(buildSentenceRequest(operations, buildSentence(WANT_TO_GO, null, true), buildSentence(EMPTY_WORD, null, true))));
    assertTrue(e.getMessage(), e.getMessage().contains("sentence 1 of the request"));
  }

  /** "Juan estudió y comió", with the invented lemma estudir for the first verb */
  private static final String ESTUDIO = String.join("\n",
    "# text = Juan estudió y comió",
    "1\tJuan\tJuan\tPROPN\t_\t_\t2\tnsubj\t2:nsubj\t_",
    "2\testudió\testudir\tVERB\t_\t_\t0\troot\t0:root\t_",
    "3\ty\ty\tCCONJ\t_\t_\t4\tcc\t4:cc\t_",
    "4\tcomió\tcomer\tVERB\t_\t_\t2\tconj\t2:conj\t_",
    "");

  /** A table with the given name and entries, given as key, value, key, value... */
  private static SsurgeonRequest.Table buildTable(String name, String ... entries) {
    SsurgeonRequest.Table.Builder builder = SsurgeonRequest.Table.newBuilder();
    builder.setName(name);
    for (int i = 0; i < entries.length; i += 2) {
      builder.addEntry(SsurgeonRequest.Table.Entry.newBuilder().setKey(entries[i]).setValue(entries[i + 1]));
    }
    return builder.build();
  }

  /** The remap operations of a request use the request's tables */
  @Test
  public void testSentenceRemap() throws IOException {
    List<SsurgeonRequest.Ssurgeon> operations = buildOperationList(buildOperation("120", "fix the verb lemmas the table lists",
                                                                                  "{cpos:VERB;lemma:__#0%lemma}=verb",
                                                                                  "remap -var lemma -table lemma_mapping",
                                                                                  "editNode -node verb -lemma ${lemma}"));
    SsurgeonRequest request = buildSentenceRequest(operations, buildSentence(ESTUDIO, "gsd-1", true), buildSentence(WANT_TO_GO, "test-1", true))
      .toBuilder().addTable(buildTable("lemma_mapping", "estudir", "estudiar", "var", "variar")).build();
    SsurgeonResponse response = ProcessSsurgeonRequest.processRequest(request);
    assertEquals(2, response.getSentenceResultCount());

    SsurgeonResponse.SsurgeonResult result = response.getSentenceResult(0);
    assertTrue(result.getChanged());
    List<CoreLabel> tokens = tokensOf(result.getSentence());
    assertEquals("estudiar", tokens.get(1).lemma());
    assertEquals("comer", tokens.get(3).lemma());
    // the graphs are built over the same tokens
    SemanticGraph basic = ProtobufAnnotationSerializer.fromProto(result.getSentence().getGraph(), tokens, null);
    assertEquals("estudiar", basic.getNodeByIndex(2).lemma());

    // nothing in the other sentence is in the table
    assertFalse(response.getSentenceResult(1).getChanged());
  }

  /** A value with characters XML would otherwise change comes through as it was sent */
  @Test
  public void testTableCharacters() {
    String value = "a \"b\" 'c' <d> & e\tf\ng";
    List<SsurgeonRequest.Ssurgeon> operations = buildOperationList(buildOperation("1", "copy a word through a table into its lemma",
                                                                                  "{word:__#0%word}=w",
                                                                                  "remap -var word -table odd",
                                                                                  "editNode -node w -lemma ${word}"));
    SsurgeonRequest request = buildRequest(operations, buildGraphs("[A-1 obj> B-2]"))
      .toBuilder().addTable(buildTable("odd", "B", value)).build();
    SsurgeonResponse response = ProcessSsurgeonRequest.processRequest(request);
    assertTrue(response.getResult(0).getChanged());
    ProtobufAnnotationSerializer serializer = new ProtobufAnnotationSerializer();
    List<CoreLabel> tokens = response.getResult(0).getGraph().getTokenList().stream().map(serializer::fromProto).collect(Collectors.toList());
    assertEquals(value, tokens.get(1).lemma());
  }

  /** A table which cannot be read, or a remap of a table the request has not got, fails the request */
  @Test
  public void testTableErrors() {
    List<SsurgeonRequest.Ssurgeon> operations = buildOperationList(buildOperation("1", "copy a word through a table into its lemma",
                                                                                  "{word:__#0%word}=w",
                                                                                  "remap -var word -table t",
                                                                                  "editNode -node w -lemma ${word}"));
    SsurgeonRequest request = buildRequest(operations, buildGraphs("[A-1 obj> B-2]"));
    // no table, the wrong table, a table with no name, an entry with no value, two tables of one name
    assertThrows(SsurgeonParseException.class, () -> ProcessSsurgeonRequest.processRequest(request));
    assertThrows(SsurgeonParseException.class, () -> ProcessSsurgeonRequest.processRequest(request.toBuilder().addTable(buildTable("u", "A", "a")).build()));
    assertThrows(SsurgeonParseException.class, () -> ProcessSsurgeonRequest.processRequest(request.toBuilder().addTable(buildTable("t", "A", "a").toBuilder().clearName()).build()));
    SsurgeonRequest.Table noValue = buildTable("t").toBuilder().addEntry(SsurgeonRequest.Table.Entry.newBuilder().setKey("A")).build();
    assertThrows(SsurgeonParseException.class, () -> ProcessSsurgeonRequest.processRequest(request.toBuilder().addTable(noValue).build()));
    assertThrows(SsurgeonParseException.class, () -> ProcessSsurgeonRequest.processRequest(request.toBuilder().addTable(buildTable("t", "A", "a")).addTable(buildTable("t", "B", "b")).build()));
    // and with the table, it works
    assertTrue(ProcessSsurgeonRequest.processRequest(request.toBuilder().addTable(buildTable("t", "A", "a")).build()).getResult(0).getChanged());
  }
}
