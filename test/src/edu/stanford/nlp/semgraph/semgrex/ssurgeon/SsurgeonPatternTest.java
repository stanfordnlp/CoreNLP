package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.CoNLLUReader;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.semgrex.Alignment;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphName;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphs;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;

/** Tests the pieces of the {@link SsurgeonPattern} command line which can be tested without the command line */
public class SsurgeonPatternTest {

  private static final String MWT_SENTENCE = String.join("\n",
    "# text = Vamos al mercado, ¿no?",
    "1\tVamos\tir\tVERB\t_\t_\t0\troot\t_\t_",
    "2-3\tal\t_\t_\t_\t_\t_\t_\t_\t_",
    "2\ta\ta\tADP\t_\t_\t4\tcase\t_\t_",
    "3\tel\tel\tDET\t_\t_\t4\tdet\t_\t_",
    "4\tmercado\tmercado\tNOUN\t_\t_\t1\tobl\t_\tSpaceAfter=No",
    "5\t,\t,\tPUNCT\t_\t_\t7\tpunct\t_\t_",
    "6\t¿\t¿\tPUNCT\t_\t_\t7\tpunct\t_\tSpaceAfter=No",
    "7\tno\tno\tADV\t_\t_\t1\tadvmod\t_\tSpaceAfter=No",
    "8\t?\t?\tPUNCT\t_\t_\t1\tpunct\t_\t_",
    "",
    "");

  private static CoreMap readSentence(String conllu) throws IOException {
    File file = File.createTempFile("ssurgeon", ".conllu");
    file.deleteOnExit();
    Files.write(file.toPath(), conllu.getBytes(StandardCharsets.UTF_8));
    List<Annotation> docs = new CoNLLUReader().readCoNLLUFile(file.getPath());
    return docs.get(0).get(CoreAnnotations.SentencesAnnotation.class).get(0);
  }

  private static SemanticGraph readGraph(String conllu) throws IOException {
    return readSentence(conllu).get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
  }

  @Test
  public void sentenceText() throws IOException {
    SemanticGraph graph = readGraph(MWT_SENTENCE);
    assertEquals("Vamos al mercado, ¿no?", SsurgeonPattern.sentenceText(graph));
  }

  @Test
  public void sentenceTextAfterEdit() throws IOException {
    SemanticGraph graph = readGraph(MWT_SENTENCE);
    List<SsurgeonPattern> patterns = Ssurgeon.readFromEditText("{word:mercado}=m\nEditNode -node m -word supermercado", "UniversalEnglish");
    SemanticGraph edited = patterns.get(0).iterate(graph).first();
    assertEquals("Vamos al supermercado, ¿no?", SsurgeonPattern.sentenceText(edited));
  }

  /** "I" is the subject of "go" only in the enhanced graph */
  private static final String ENHANCED_SENTENCE = String.join("\n",
    "# text = I want to go : now : ok",
    "1\tI\tI\tPRON\t_\t_\t2\tnsubj\t2:nsubj|4:nsubj:xsubj\t_",
    "2\twant\twant\tVERB\t_\t_\t0\troot\t0:root\t_",
    "3\tto\tto\tPART\t_\t_\t4\tmark\t4:mark\t_",
    "4\tgo\tgo\tVERB\t_\t_\t2\txcomp\t2:xcomp\t_",
    "5\t:\t:\tPUNCT\t_\t_\t2\tpunct\t2:punct\t_",
    "6\tnow\tnow\tADV\t_\t_\t2\tadvmod\t2:advmod\t_",
    "7\t:\t:\tPUNCT\t_\t_\t2\tpunct\t2:punct\t_",
    "8\tok\tok\tINTJ\t_\t_\t2\tdiscourse\t2:discourse\t_",
    "",
    "");

  private static SemgrexGraphs readGraphs(String conllu) throws IOException {
    CoreMap sentence = readSentence(conllu);
    SemanticGraph basic = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    SemanticGraph enhanced = sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
    assertNotNull(enhanced);
    return SemgrexGraphs.of(basic, enhanced);
  }

  @Test
  public void iterateSearchesEnhanced() throws IOException {
    SemgrexGraphs graphs = readGraphs(ENHANCED_SENTENCE);
    SsurgeonPattern pattern = Ssurgeon.readFromEditText("{}=gov >/nsubj:xsubj/@enhanced {}\nEditNode -node gov -lemma GO", "UniversalEnglish").get(0);
    assertTrue(pattern.searchesBeyondBasic());

    Pair<SemgrexGraphs, Boolean> result = pattern.iterate(graphs);
    assertTrue(result.second());
    assertEquals("GO", result.first().getDefault().getNodeByIndex(4).lemma());
    // the words are shared, so both graphs, and the originals, see the edit
    assertEquals("GO", result.first().get(SemgrexGraphName.ENHANCED).getNodeByIndex(4).lemma());
    assertEquals("GO", graphs.get(SemgrexGraphName.ENHANCED).getNodeByIndex(4).lemma());
    // the edit only touched a word, so the structure of the graphs is unchanged
    assertEquals(graphs.getDefault(), result.first().getDefault());
    assertEquals(graphs.get(SemgrexGraphName.ENHANCED), result.first().get(SemgrexGraphName.ENHANCED));
  }

  @Test
  public void iterateCopiesGraphs() throws IOException {
    SemgrexGraphs graphs = readGraphs(ENHANCED_SENTENCE);
    SsurgeonPattern pattern = Ssurgeon.readFromEditText("{}=gov >nsubj {}\nEditNode -node gov -lemma WANT", "UniversalEnglish").get(0);
    Pair<SemgrexGraphs, Boolean> result = pattern.iterate(graphs);
    assertTrue(result.second());
    assertNotSame(graphs.getDefault(), result.first().getDefault());
    assertNotSame(graphs.get(SemgrexGraphName.ENHANCED), result.first().get(SemgrexGraphName.ENHANCED));
  }

  @Test
  public void iterateWithoutEnhanced() throws IOException {
    SemanticGraph basic = readGraph(ENHANCED_SENTENCE);
    SsurgeonPattern pattern = Ssurgeon.readFromEditText("{}=gov >/nsubj:xsubj/@enhanced {}\nEditNode -node gov -lemma GO", "UniversalEnglish").get(0);
    assertThrows(IllegalStateException.class, () -> pattern.iterate(basic));
    assertThrows(IllegalStateException.class, () -> pattern.iterate(new SemgrexGraphs(basic)));

    SsurgeonPattern basicOnly = Ssurgeon.readFromEditText("{}=gov >nsubj {}\nEditNode -node gov -lemma WANT", "UniversalEnglish").get(0);
    assertFalse(basicOnly.searchesBeyondBasic());
    assertTrue(basicOnly.iterate(basic).second());
  }

  @Test
  public void iterateRejectsAlignment() throws IOException {
    SemanticGraph basic = readGraph(ENHANCED_SENTENCE);
    SsurgeonPattern pattern = Ssurgeon.readFromEditText("{}=gov >nsubj {}\nEditNode -node gov -lemma WANT", "UniversalEnglish").get(0);
    SemgrexGraphs aligned = SemgrexGraphs.aligned(basic, new Alignment(new HashMap<>(), 0.0, ""), basic);
    assertThrows(IllegalArgumentException.class, () -> pattern.iterate(aligned));
  }

  @Test
  public void iterateStopsWhenEnhancedNoLongerFits() throws IOException {
    // deleting a word means the enhanced graph no longer describes the
    // words of the basic graph, so a pattern which searches the enhanced
    // graph stops after the first deletion, and the enhanced graph is
    // left out of the graphs returned
    SemgrexGraphs graphs = readGraphs(ENHANCED_SENTENCE);
    SsurgeonPattern pattern = Ssurgeon.readFromEditText("{word:/:/}=p <punct@enhanced {}\nDeleteLeaf -node p", "UniversalEnglish").get(0);
    Pair<SemgrexGraphs, Boolean> result = pattern.iterate(graphs);
    assertTrue(result.second());
    assertEquals(7, result.first().getDefault().size());
    assertNull(result.first().get(SemgrexGraphName.ENHANCED));

    // a pattern on the basic graph alone deletes both, even with the enhanced graph given.
    // renumbering the words changes their shared labels, so this needs a fresh sentence
    graphs = readGraphs(ENHANCED_SENTENCE);
    SsurgeonPattern basicOnly = Ssurgeon.readFromEditText("{word:/:/}=p <punct {}\nDeleteLeaf -node p", "UniversalEnglish").get(0);
    result = basicOnly.iterate(graphs);
    assertTrue(result.second());
    assertEquals(6, result.first().getDefault().size());
    assertNull(result.first().get(SemgrexGraphName.ENHANCED));

    // with no change to the words, the enhanced graph stays
    graphs = readGraphs(ENHANCED_SENTENCE);
    SsurgeonPattern noMatch = Ssurgeon.readFromEditText("{word:nothing}=p\nDeleteLeaf -node p", "UniversalEnglish").get(0);
    result = noMatch.iterate(graphs);
    assertFalse(result.second());
    assertNotNull(result.first().get(SemgrexGraphName.ENHANCED));
  }

  @Test
  public void sharedWordsFromConllu() throws IOException {
    SsurgeonPattern.checkSharedWords(readGraphs(ENHANCED_SENTENCE));
  }

  /**
   * Graphs built separately have separate copies of each word, so an edit
   * to a word in one would leave the other with a different answer
   */
  @Test
  public void separateWordsRejected() {
    SemanticGraph basic = SemanticGraph.valueOf("[want-2 nsubj> I-1 xcomp> go-3]");
    SemanticGraph enhanced = SemanticGraph.valueOf("[want-2 nsubj> I-1 xcomp> [go-3 nsubj:xsubj> I-1]]");
    SemgrexGraphs graphs = SemgrexGraphs.of(basic, enhanced);
    assertThrows(IllegalArgumentException.class, () -> SsurgeonPattern.checkSharedWords(graphs));

    SsurgeonPattern pattern = Ssurgeon.readFromEditText("{}=gov >nsubj {}\nEditNode -node gov -lemma WANT", "UniversalEnglish").get(0);
    assertThrows(IllegalArgumentException.class, () -> pattern.iterate(graphs));
  }

  /**
   * Sharing the CoreLabels is not enough: each word has to be the same
   * IndexedWord in every graph
   */
  @Test
  public void separateWrappersRejected() {
    SemanticGraph basic = SemanticGraph.valueOf("[want-2 nsubj> I-1 xcomp> go-3]");
    Map<IndexedWord, IndexedWord> wrapped = new HashMap<>();
    SemanticGraph enhanced = new SemanticGraph();
    for (IndexedWord word : basic.vertexSet()) {
      IndexedWord wrapper = new IndexedWord(word.backingLabel());
      wrapped.put(word, wrapper);
      enhanced.addVertex(wrapper);
    }
    for (SemanticGraphEdge edge : basic.edgeIterable()) {
      enhanced.addEdge(wrapped.get(edge.getGovernor()), wrapped.get(edge.getDependent()), edge.getRelation(), 1.0, false);
    }
    enhanced.addEdge(wrapped.get(basic.getNodeByIndex(3)), wrapped.get(basic.getNodeByIndex(1)),
                     GrammaticalRelation.valueOf(Language.UniversalEnglish, "nsubj:xsubj"), 1.0, false);
    enhanced.setRoot(wrapped.get(basic.getFirstRoot()));
    assertNotSame(basic.getNodeByIndex(1), enhanced.getNodeByIndex(1));
    assertSame(basic.getNodeByIndex(1).backingLabel(), enhanced.getNodeByIndex(1).backingLabel());

    SemgrexGraphs graphs = SemgrexGraphs.of(basic, enhanced);
    assertThrows(IllegalArgumentException.class, () -> SsurgeonPattern.checkSharedWords(graphs));
    SsurgeonPattern pattern = Ssurgeon.readFromEditText("{}=gov >nsubj {}\nEditNode -node gov -lemma WANT", "UniversalEnglish").get(0);
    assertThrows(IllegalArgumentException.class, () -> pattern.iterate(graphs));
  }

  /**
   * The same graphs built with one set of IndexedWords pass
   */
  @Test
  public void sharedWords() {
    SemanticGraph basic = SemanticGraph.valueOf("[want-2 nsubj> I-1 xcomp> go-3]");
    SemanticGraph enhanced = new SemanticGraph(basic);
    enhanced.addEdge(basic.getNodeByIndex(3), basic.getNodeByIndex(1),
                     GrammaticalRelation.valueOf(Language.UniversalEnglish, "nsubj:xsubj"), 1.0, false);
    SemgrexGraphs graphs = SemgrexGraphs.of(basic, enhanced);
    SsurgeonPattern.checkSharedWords(graphs);

    SsurgeonPattern pattern = Ssurgeon.readFromEditText("{}=gov >/nsubj:xsubj/@enhanced {}\nEditNode -node gov -lemma GO", "UniversalEnglish").get(0);
    Pair<SemgrexGraphs, Boolean> result = pattern.iterate(graphs);
    assertTrue(result.second());
    assertEquals("GO", basic.getNodeByIndex(3).lemma());
  }

  /**
   * An edit which gives the enhanced graph its own copy of a word, once,
   * so that the pattern stops matching after it
   */
  private static class SeparateWordEdit extends SsurgeonEdit {
    boolean done = false;

    @Override
    public boolean evaluate(SemanticGraph sg, SemgrexMatcher sm) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean evaluate(SemgrexGraphs graphs, SemgrexMatcher sm) {
      if (done) {
        return false;
      }
      done = true;
      SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
      IndexedWord ok = enhanced.getNodeByIndex(8);
      IndexedWord gov = enhanced.getParent(ok);
      GrammaticalRelation reln = enhanced.getEdge(gov, ok).getRelation();
      enhanced.removeVertex(ok);
      // the same CoreLabel in a new IndexedWord is still a second copy of the word
      enhanced.addEdge(gov, new IndexedWord(ok.backingLabel()), reln, 1.0, false);
      return true;
    }

    @Override
    public String toEditString() {
      return "separateWord";
    }
  }

  @Test
  public void editWhichSeparatesWords() throws IOException {
    SsurgeonPattern pattern = new SsurgeonPattern("separate", SemgrexPattern.compile("{word:ok}"));
    pattern.addEdit(new SeparateWordEdit());
    SemgrexGraphs graphs = readGraphs(ENHANCED_SENTENCE);
    IllegalStateException e = assertThrows(IllegalStateException.class, () -> pattern.iterate(graphs));
    assertTrue(e.getMessage(), e.getMessage().contains("different copies of word 8 (ok)"));
  }

  /** "Sue likes coffee and Bill tea", where the enhanced graph has an empty word 5.1 for the elided "likes" */
  private static final String EMPTY_WORD_SENTENCE = String.join("\n",
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

  /** EditNode reaches an empty word, which only the enhanced graph has */
  @Test
  public void editNodeOnEmptyWord() throws IOException {
    SemgrexGraphs graphs = readGraphs(EMPTY_WORD_SENTENCE);
    SsurgeonPattern pattern = Ssurgeon.readFromEditText("{}=e !< {} <@enhanced {}\nEditNode -node e -lemma ELIDED", "UniversalEnglish").get(0);
    Pair<SemgrexGraphs, Boolean> result = pattern.iterate(graphs);
    assertTrue(result.second());

    IndexedWord empty = null;
    for (IndexedWord word : result.first().get(SemgrexGraphName.ENHANCED).vertexSet()) {
      if (word.getEmptyIndex() == 1) {
        empty = word;
      }
    }
    assertNotNull(empty);
    assertEquals("ELIDED", empty.lemma());
    // the likes of the basic graph is a different word
    assertEquals("like", result.first().getDefault().getNodeByIndex(2).lemma());
  }

  /** Lemmatize works on a word found through the enhanced graph */
  @Test
  public void lemmatizeThroughEnhanced() throws IOException {
    SemgrexGraphs graphs = readGraphs(EMPTY_WORD_SENTENCE);
    IndexedWord tea = graphs.getDefault().getNodeByIndex(6);
    tea.setLemma("WRONG");
    SsurgeonPattern pattern = Ssurgeon.readFromEditText("{word:tea}=t <obj@enhanced {}\nLemmatize -node t", "UniversalEnglish").get(0);
    Pair<SemgrexGraphs, Boolean> result = pattern.iterate(graphs);
    assertTrue(result.second());
    assertEquals("tea", result.first().getDefault().getNodeByIndex(6).lemma());
    assertEquals("tea", result.first().get(SemgrexGraphName.ENHANCED).getNodeByIndex(6).lemma());
  }

  /**
   * An empty word belongs to no token.  5.1 has the index 5, so a match
   * naming it and "tea" would otherwise look like two words in a row
   */
  @Test
  public void combineMWTIgnoresEmptyWords() throws IOException {
    SemgrexGraphs graphs = readGraphs(EMPTY_WORD_SENTENCE);
    SsurgeonPattern pattern = Ssurgeon.readFromEditText("{word:tea}=t <obj@enhanced {}=e\nCombineMWT -node e -node t", "UniversalEnglish").get(0);
    Pair<SemgrexGraphs, Boolean> result = pattern.iterate(graphs);
    assertFalse(result.second());
    for (int index = 1; index <= 6; ++index) {
      assertNotEquals(Boolean.TRUE, result.first().getDefault().getNodeByIndex(index).get(CoreAnnotations.IsMultiWordTokenAnnotation.class));
    }
  }

  /** CombineMWT on real words works with both graphs given, and both see the MWT */
  @Test
  public void combineMWTWithEnhanced() throws IOException {
    SemgrexGraphs graphs = readGraphs(EMPTY_WORD_SENTENCE);
    SsurgeonPattern pattern = Ssurgeon.readFromEditText("{word:Bill}=b . {word:tea}=t\nCombineMWT -node b -node t -word Billtea", "UniversalEnglish").get(0);
    Pair<SemgrexGraphs, Boolean> result = pattern.iterate(graphs);
    assertTrue(result.second());
    assertEquals("Billtea", result.first().get(SemgrexGraphName.ENHANCED).getNodeByIndex(6).get(CoreAnnotations.MWTTokenTextAnnotation.class));
    assertNotNull(result.first().get(SemgrexGraphName.ENHANCED));
  }

  /** The relations from gov to dep in one graph, sorted */
  private static List<String> relations(SemanticGraph graph, int gov, int dep) {
    List<String> relations = new ArrayList<>();
    for (SemanticGraphEdge edge : graph.getAllEdges(graph.getNodeByIndex(gov), graph.getNodeByIndex(dep))) {
      relations.add(edge.getRelation().toString());
    }
    Collections.sort(relations);
    return relations;
  }

  /** Runs one edit block on the "I want to go" sentence, checking that it changed something */
  private static SemgrexGraphs editEnhancedSentence(String block) throws IOException {
    SsurgeonPattern pattern = Ssurgeon.readFromEditText(block, "UniversalEnglish").get(0);
    Pair<SemgrexGraphs, Boolean> result = pattern.iterate(readGraphs(ENHANCED_SENTENCE));
    assertTrue(result.second());
    assertNotNull(result.first().get(SemgrexGraphName.ENHANCED));
    return result.first();
  }

  @Test
  public void relabelEnhancedEdge() throws IOException {
    SemgrexGraphs graphs = editEnhancedSentence("{} >/nsubj:xsubj/@enhanced=e {}\nRelabelNamedEdge -edge e -reln nsubj");
    assertEquals(Collections.singletonList("nsubj"), relations(graphs.get(SemgrexGraphName.ENHANCED), 4, 1));
    assertEquals(Collections.singletonList("nsubj"), relations(graphs.getDefault(), 2, 1));
    assertEquals(Collections.emptyList(), relations(graphs.getDefault(), 4, 1));
  }

  /** nsubj(want, I) is in both graphs; relabeling the basic one leaves the enhanced one alone */
  @Test
  public void relabelBasicEdgeLeavesEnhanced() throws IOException {
    SemgrexGraphs graphs = editEnhancedSentence("{word:want} >nsubj=e {word:I}\nRelabelNamedEdge -edge e -reln expl");
    assertEquals(Collections.singletonList("expl"), relations(graphs.getDefault(), 2, 1));
    assertEquals(Collections.singletonList("nsubj"), relations(graphs.get(SemgrexGraphName.ENHANCED), 2, 1));
  }

  /** An enhanced edge can be moved to a node matched in the basic graph */
  @Test
  public void reattachEnhancedEdge() throws IOException {
    SemgrexGraphs graphs = editEnhancedSentence("{word:want}=w >xcomp ({word:go} >/nsubj:xsubj/@enhanced=e {})\nReattachNamedEdge -edge e -gov w");
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    assertEquals(Arrays.asList("nsubj", "nsubj:xsubj"), relations(enhanced, 2, 1));
    assertEquals(Collections.emptyList(), relations(enhanced, 4, 1));
    assertEquals(Collections.singletonList("nsubj"), relations(graphs.getDefault(), 2, 1));
  }

  @Test
  public void removeEnhancedEdge() throws IOException {
    SemgrexGraphs graphs = editEnhancedSentence("{} >/nsubj:xsubj/@enhanced=e {}\nRemoveNamedEdge -edge e");
    assertEquals(Collections.emptyList(), relations(graphs.get(SemgrexGraphName.ENHANCED), 4, 1));
    assertEquals(Collections.singletonList("nsubj"), relations(graphs.get(SemgrexGraphName.ENHANCED), 2, 1));
    assertEquals(Collections.singletonList("nsubj"), relations(graphs.getDefault(), 2, 1));
  }

  @Test
  public void removeBasicEdgeLeavesEnhanced() throws IOException {
    SemgrexGraphs graphs = editEnhancedSentence("{word:ok} <discourse=e {}\nRemoveNamedEdge -edge e");
    assertEquals(Collections.emptyList(), relations(graphs.getDefault(), 2, 8));
    assertEquals(Collections.singletonList("discourse"), relations(graphs.get(SemgrexGraphName.ENHANCED), 2, 8));
  }

  /**
   * A basic edge cannot be reattached to the empty word: the basic graph
   * has no such word, and adding the edge would add the word to it
   */
  @Test
  public void reattachBasicEdgeToEmptyWord() throws IOException {
    SemgrexGraphs graphs = readGraphs(EMPTY_WORD_SENTENCE);
    SsurgeonPattern pattern = Ssurgeon.readFromEditText("{word:tea} <orphan=e {} <obj@enhanced {}=x\nReattachNamedEdge -edge e -gov x", "UniversalEnglish").get(0);
    Pair<SemgrexGraphs, Boolean> result = pattern.iterate(graphs);
    assertFalse(result.second());

    SemanticGraph basic = result.first().getDefault();
    for (IndexedWord word : basic.vertexSet()) {
      assertEquals("The basic graph gained " + word.toCopyOrEmptyIndex(), 0, word.getEmptyIndex());
    }
    assertEquals(Collections.singletonList("orphan"), relations(basic, 5, 6));
    assertNotNull(result.first().get(SemgrexGraphName.ENHANCED));
  }

  /** want has I as its nsubj and go as its xcomp, in both graphs */
  private static final String WANT_I_GO = "{word:want}=w >nsubj {word:I}=i >xcomp {word:go}=g";

  @Test
  public void addEdgeToEnhanced() throws IOException {
    SemgrexGraphs graphs = editEnhancedSentence(WANT_I_GO + "\nAddEdge -gov g -dep i -reln nsubj:pass -graph enhanced");
    assertEquals(Arrays.asList("nsubj:pass", "nsubj:xsubj"), relations(graphs.get(SemgrexGraphName.ENHANCED), 4, 1));
    assertEquals(Collections.emptyList(), relations(graphs.getDefault(), 4, 1));
  }

  @Test
  public void addEdgeToBasicByDefault() throws IOException {
    SemgrexGraphs graphs = editEnhancedSentence(WANT_I_GO + "\nAddEdge -gov g -dep i -reln nsubj:pass");
    assertEquals(Collections.singletonList("nsubj:pass"), relations(graphs.getDefault(), 4, 1));
    assertEquals(Collections.singletonList("nsubj:xsubj"), relations(graphs.get(SemgrexGraphName.ENHANCED), 4, 1));
  }

  /** An empty word can be joined to the enhanced graph, but not the basic graph, which does not have it */
  @Test
  public void addEdgeWithEmptyWord() throws IOException {
    SsurgeonPattern basic = Ssurgeon.readFromEditText("{word:Bill}=b <nsubj@enhanced {}=x\nAddEdge -gov x -dep b -reln dep", "UniversalEnglish").get(0);
    Pair<SemgrexGraphs, Boolean> result = basic.iterate(readGraphs(EMPTY_WORD_SENTENCE));
    assertFalse(result.second());
    for (IndexedWord word : result.first().getDefault().vertexSet()) {
      assertEquals(0, word.getEmptyIndex());
    }

    SsurgeonPattern enhanced = Ssurgeon.readFromEditText("{word:Bill}=b <nsubj@enhanced {}=x\nAddEdge -gov x -dep b -reln dep -graph enhanced", "UniversalEnglish").get(0);
    result = enhanced.iterate(readGraphs(EMPTY_WORD_SENTENCE));
    assertTrue(result.second());
    SemanticGraph enhancedGraph = result.first().get(SemgrexGraphName.ENHANCED);
    IndexedWord empty = null;
    for (IndexedWord word : enhancedGraph.vertexSet()) {
      if (word.getEmptyIndex() == 1) {
        empty = word;
      }
    }
    List<String> fromEmpty = new ArrayList<>();
    // in the enhanced graph, both Bill and the empty word have the index 5,
    // so Bill comes from the basic graph, which shares him
    IndexedWord bill = result.first().getDefault().getNodeByIndex(5);
    for (SemanticGraphEdge edge : enhancedGraph.getAllEdges(empty, bill)) {
      fromEmpty.add(edge.getRelation().toString());
    }
    Collections.sort(fromEmpty);
    assertEquals(Arrays.asList("dep", "nsubj"), fromEmpty);
  }

  @Test
  public void removeEdgeFromEnhanced() throws IOException {
    SemgrexGraphs graphs = editEnhancedSentence(WANT_I_GO + "\nRemoveEdge -gov g -dep i -graph enhanced");
    assertEquals(Collections.emptyList(), relations(graphs.get(SemgrexGraphName.ENHANCED), 4, 1));
    assertEquals(Collections.singletonList("nsubj"), relations(graphs.get(SemgrexGraphName.ENHANCED), 2, 1));
    assertEquals(Collections.singletonList("nsubj"), relations(graphs.getDefault(), 2, 1));
  }

  @Test
  public void removeEdgeFromBasicByDefault() throws IOException {
    SemgrexGraphs graphs = editEnhancedSentence(WANT_I_GO + "\nRemoveEdge -gov w -dep i -reln nsubj");
    assertEquals(Collections.emptyList(), relations(graphs.getDefault(), 2, 1));
    assertEquals(Collections.singletonList("nsubj"), relations(graphs.get(SemgrexGraphName.ENHANCED), 2, 1));
  }

  @Test
  public void killAllIncomingEdgesOfEnhanced() throws IOException {
    SemgrexGraphs graphs = editEnhancedSentence(WANT_I_GO + "\nKillAllIncomingEdges -node i -graph enhanced");
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    assertEquals(0, enhanced.incomingEdgeList(enhanced.getNodeByIndex(1)).size());
    assertEquals(Collections.singletonList("nsubj"), relations(graphs.getDefault(), 2, 1));
  }

  /** An edit which names a graph the sentence has not got is an error, as a pattern which searches it is */
  @Test
  public void editMissingGraph() throws IOException {
    SsurgeonPattern pattern = Ssurgeon.readFromEditText(WANT_I_GO + "\nAddEdge -gov g -dep i -reln nsubj:pass -graph enhanced", "UniversalEnglish").get(0);
    assertFalse(pattern.searchesBeyondBasic());
    assertTrue(pattern.usesGraphsBeyondBasic());
    SemanticGraph basic = readGraph(ENHANCED_SENTENCE);
    IllegalStateException e = assertThrows(IllegalStateException.class, () -> pattern.iterate(new SemgrexGraphs(basic)));
    assertTrue(e.getMessage(), e.getMessage().contains("ENHANCED graph"));
  }

  /**
   * Once the words change, the enhanced graph is left out, so a pattern
   * which edits it stops, as one which searches it does
   */
  @Test
  public void enhancedEditStopsWhenWordsChange() throws IOException {
    SsurgeonPattern pattern = Ssurgeon.readFromEditText("{word:/:/}=p\nDeleteLeaf -node p\nKillAllIncomingEdges -node p -graph enhanced", "UniversalEnglish").get(0);
    Pair<SemgrexGraphs, Boolean> result = pattern.iterate(readGraphs(ENHANCED_SENTENCE));
    assertTrue(result.second());
    assertEquals(7, result.first().getDefault().size());
    assertNull(result.first().get(SemgrexGraphName.ENHANCED));
  }

  /** The enhanced graph's root, written as 0:root in DEPS, can be set apart from the basic graph's */
  @Test
  public void setRootsOfEnhanced() throws IOException {
    SemgrexGraphs graphs = editEnhancedSentence(WANT_I_GO + "\nSetRoots g -graph enhanced");
    assertEquals(Collections.singleton(graphs.getDefault().getNodeByIndex(4)), graphs.get(SemgrexGraphName.ENHANCED).getRoots());
    assertEquals(Collections.singleton(graphs.getDefault().getNodeByIndex(2)), graphs.getDefault().getRoots());
  }

  /** The empty word cannot be a root of the basic graph, which does not have it */
  @Test
  public void setRootsToEmptyWord() throws IOException {
    SsurgeonPattern pattern = Ssurgeon.readFromEditText("{word:Bill}=b <nsubj@enhanced {}=x\nSetRoots x", "UniversalEnglish").get(0);
    Pair<SemgrexGraphs, Boolean> result = pattern.iterate(readGraphs(EMPTY_WORD_SENTENCE));
    assertFalse(result.second());
    assertEquals(2, result.first().getDefault().getFirstRoot().index());
  }

  /** "John Bauer works", with the same edges in both graphs */
  private static final String NAME_SENTENCE = String.join("\n",
    "# text = John Bauer works",
    "1\tJohn\tJohn\tPROPN\t_\t_\t3\tnsubj\t3:nsubj\t_",
    "2\tBauer\tBauer\tPROPN\t_\t_\t1\tflat\t1:flat\t_",
    "3\tworks\twork\tVERB\t_\t_\t0\troot\t0:root\t_",
    "",
    "");

  private static final String NAME_PHRASE = "{word:John}=n1 . {word:Bauer}=n2\nsetPhraseHead -node n1 -node n2 -headIndex 1 -reln flat";

  @Test
  public void setPhraseHeadOfEnhanced() throws IOException {
    SsurgeonPattern pattern = Ssurgeon.readFromEditText(NAME_PHRASE + " -graph enhanced", "UniversalEnglish").get(0);
    Pair<SemgrexGraphs, Boolean> result = pattern.iterate(readGraphs(NAME_SENTENCE));
    assertTrue(result.second());
    SemanticGraph enhanced = result.first().get(SemgrexGraphName.ENHANCED);
    assertEquals(Collections.singletonList("nsubj"), relations(enhanced, 3, 2));
    assertEquals(Collections.singletonList("flat"), relations(enhanced, 2, 1));
    assertEquals(Collections.emptyList(), relations(enhanced, 3, 1));
    SemanticGraph basic = result.first().getDefault();
    assertEquals(Collections.singletonList("nsubj"), relations(basic, 3, 1));
    assertEquals(Collections.singletonList("flat"), relations(basic, 1, 2));
  }

  @Test
  public void setPhraseHeadOfBasicByDefault() throws IOException {
    SsurgeonPattern pattern = Ssurgeon.readFromEditText(NAME_PHRASE, "UniversalEnglish").get(0);
    Pair<SemgrexGraphs, Boolean> result = pattern.iterate(readGraphs(NAME_SENTENCE));
    assertTrue(result.second());
    assertEquals(Collections.singletonList("nsubj"), relations(result.first().getDefault(), 3, 2));
    assertEquals(Collections.singletonList("nsubj"), relations(result.first().get(SemgrexGraphName.ENHANCED), 3, 1));
  }

  /** "John Bauer came and saw", where the enhanced graph makes John the subject of both verbs */
  private static final String TWO_HEADS_SENTENCE = String.join("\n",
    "# text = John Bauer came and saw",
    "1\tJohn\tJohn\tPROPN\t_\t_\t3\tnsubj\t3:nsubj|5:nsubj\t_",
    "2\tBauer\tBauer\tPROPN\t_\t_\t1\tflat\t1:flat\t_",
    "3\tcame\tcome\tVERB\t_\t_\t0\troot\t0:root\t_",
    "4\tand\tand\tCCONJ\t_\t_\t5\tcc\t5:cc\t_",
    "5\tsaw\tsee\tVERB\t_\t_\t3\tconj\t3:conj:and\t_",
    "",
    "");

  /**
   * A phrase with two heads outside of it is not rearranged.  In the
   * enhanced graph, John Bauer has both came and saw as heads; in the basic
   * graph it only has came, so there the head can move
   */
  @Test
  public void setPhraseHeadTwoOutsideHeads() throws IOException {
    SsurgeonPattern enhanced = Ssurgeon.readFromEditText(NAME_PHRASE + " -graph enhanced", "UniversalEnglish").get(0);
    Pair<SemgrexGraphs, Boolean> result = enhanced.iterate(readGraphs(TWO_HEADS_SENTENCE));
    assertFalse(result.second());
    assertEquals(Collections.singletonList("flat"), relations(result.first().get(SemgrexGraphName.ENHANCED), 1, 2));

    SsurgeonPattern basic = Ssurgeon.readFromEditText(NAME_PHRASE, "UniversalEnglish").get(0);
    result = basic.iterate(readGraphs(TWO_HEADS_SENTENCE));
    assertTrue(result.second());
    assertEquals(Collections.singletonList("flat"), relations(result.first().getDefault(), 2, 1));
    assertEquals(Collections.singletonList("nsubj"), relations(result.first().getDefault(), 3, 2));
  }
}
