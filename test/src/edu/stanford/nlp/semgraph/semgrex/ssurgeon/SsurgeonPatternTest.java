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
import java.util.HashSet;
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
    assertTrue(basicOnly.iterate(basic).second());
  }

  @Test
  public void iterateRejectsAlignment() throws IOException {
    SemanticGraph basic = readGraph(ENHANCED_SENTENCE);
    SsurgeonPattern pattern = Ssurgeon.readFromEditText("{}=gov >nsubj {}\nEditNode -node gov -lemma WANT", "UniversalEnglish").get(0);
    SemgrexGraphs aligned = SemgrexGraphs.aligned(basic, new Alignment(new HashMap<>(), 0.0, ""), basic);
    assertThrows(IllegalArgumentException.class, () -> pattern.iterate(aligned));
  }

  /**
   * Every edit keeps the graphs describing the same words, so an edit
   * which deletes a word from the basic graph alone is a bug, and iterate
   * says which edit it was
   */
  @Test
  public void iterateThrowsWhenGraphsHaveDifferentWords() throws IOException {
    SsurgeonPattern pattern = new SsurgeonPattern("basic only", SemgrexPattern.compile("{word:/:/}=p"));
    pattern.addEdit(new BasicGraphOnly(new DeleteLeaf("p")));
    IllegalStateException e = assertThrows(IllegalStateException.class, () -> pattern.iterate(readGraphs(ENHANCED_SENTENCE)));
    assertTrue(e.getMessage(), e.getMessage().contains("deleteLeaf"));
    assertTrue(e.getMessage(), e.getMessage().contains("different words"));

    // with no change to the words, the enhanced graph stays
    SemgrexGraphs graphs = readGraphs(ENHANCED_SENTENCE);
    SsurgeonPattern noMatch = Ssurgeon.readFromEditText("{word:nothing}=p\nDeleteLeaf -node p", "UniversalEnglish").get(0);
    Pair<SemgrexGraphs, Boolean> result = noMatch.iterate(graphs);
    assertFalse(result.second());
    assertNotNull(result.first().get(SemgrexGraphName.ENHANCED));
  }

  /**
   * A copy of word 5 and the empty word 5.1 both print as 5.1, but they
   * are different words, so the graphs still share their words
   */
  @Test
  public void sharedWordsCopyAndEmptyWord() throws IOException {
    SemgrexGraphs graphs = readGraphs(EMPTY_WORD_SENTENCE);
    SemanticGraph basic = graphs.getDefault();
    IndexedWord bill = basic.getNodeByIndex(5);
    IndexedWord copy = bill.makeSoftCopy(1);
    basic.addEdge(basic.getNodeByIndex(2), copy, GrammaticalRelation.valueOf(Language.UniversalEnglish, "dep"), 1.0, false);
    IndexedWord empty = word(graphs.get(SemgrexGraphName.ENHANCED), 5, 1);
    assertEquals(copy.toCopyOrEmptyIndex(), empty.toCopyOrEmptyIndex());
    SsurgeonPattern.checkSharedWords(graphs);

    // a second copy of the copy in the enhanced graph is still caught
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    enhanced.addEdge(enhanced.getNodeByIndex(2), bill.makeSoftCopy(1), GrammaticalRelation.valueOf(Language.UniversalEnglish, "dep"), 1.0, false);
    assertThrows(IllegalArgumentException.class, () -> SsurgeonPattern.checkSharedWords(graphs));
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
   * An edit which runs another edit on the basic graph alone, so that it
   * changes the words of the basic graph without changing the other graphs
   * to match, which no edit should do
   */
  private static class BasicGraphOnly extends SsurgeonEdit {
    final SsurgeonEdit edit;

    BasicGraphOnly(SsurgeonEdit edit) {
      this.edit = edit;
    }

    @Override
    public boolean evaluate(SemgrexGraphs graphs, SemgrexMatcher sm) {
      return edit.evaluate(new SemgrexGraphs(graphs.getDefault()), sm);
    }

    @Override
    public String toEditString() {
      return edit.toEditString();
    }
  }

  /**
   * An edit which gives the enhanced graph its own copy of a word, once,
   * so that the pattern stops matching after it
   */
  private static class SeparateWordEdit extends SsurgeonEdit {
    boolean done = false;

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
    assertTrue(e.getMessage(), e.getMessage().contains("different IndexedWords for word 8 (ok)"));
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
    SemanticGraph basic = readGraph(ENHANCED_SENTENCE);
    IllegalStateException e = assertThrows(IllegalStateException.class, () -> pattern.iterate(new SemgrexGraphs(basic)));
    assertTrue(e.getMessage(), e.getMessage().contains("ENHANCED graph"));
  }

  /** The words of a graph in order, each as its index and text, such as {@code 5.1=likes} */
  private static String wordsOf(SemanticGraph graph) {
    List<String> words = new ArrayList<>();
    for (IndexedWord word : graph.vertexListSorted()) {
      words.add(word.toCopyOrEmptyIndex() + "=" + word.word());
    }
    return String.join(" ", words);
  }

  /** The word with the given index and empty index in a graph */
  private static IndexedWord word(SemanticGraph graph, int index, int emptyIndex) {
    for (IndexedWord word : graph.vertexSet()) {
      if (word.index() == index && word.getEmptyIndex() == emptyIndex) {
        return word;
      }
    }
    return null;
  }

  /** The relations from gov to dep in one graph, sorted */
  private static List<String> relations(SemanticGraph graph, IndexedWord gov, IndexedWord dep) {
    List<String> relations = new ArrayList<>();
    for (SemanticGraphEdge edge : graph.getAllEdges(gov, dep)) {
      relations.add(edge.getRelation().toString());
    }
    Collections.sort(relations);
    return relations;
  }

  /** Runs one edit block on a sentence, checking that it changed something and kept the graphs sharing their words */
  private static SemgrexGraphs editSentence(String conllu, String block) throws IOException {
    SsurgeonPattern pattern = Ssurgeon.readFromEditText(block, "UniversalEnglish").get(0);
    Pair<SemgrexGraphs, Boolean> result = pattern.iterate(readGraphs(conllu));
    assertTrue(result.second());
    SsurgeonPattern.checkSharedWords(result.first());
    return result.first();
  }

  /** Without a governor, addDep puts its word in both graphs with no edges */
  @Test
  public void addDepWithoutGovernor() throws IOException {
    SemgrexGraphs graphs = readGraphs(ENHANCED_SENTENCE);
    SemgrexMatcher matcher = SemgrexPattern.compile("{word:go}=g").matcher(graphs);
    assertTrue(matcher.find());
    AddDep edit = new AddDep(null, null, Collections.singletonMap("word", "quickly"), "+g", 0.0, "q", null);
    assertTrue(edit.evaluate(graphs, matcher));
    IndexedWord quickly = matcher.getNode("q");
    assertEquals(5, quickly.index());
    for (SemanticGraph sg : Arrays.asList(graphs.getDefault(), graphs.get(SemgrexGraphName.ENHANCED))) {
      assertTrue(sg.containsVertex(quickly));
      assertEquals(0, sg.inDegree(quickly));
      assertEquals(0, sg.outDegree(quickly));
    }
    SsurgeonPattern.checkSharedWords(graphs);
  }

  /** addDep puts the word in both graphs, and its edge in both, so the enhanced graph stays */
  @Test
  public void addDepToEveryGraph() throws IOException {
    SemgrexGraphs graphs = editSentence(ENHANCED_SENTENCE, "{word:go}=g !> {word:quickly}\naddDep -gov g -reln advmod -word quickly -position +g");
    SemanticGraph basic = graphs.getDefault();
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    assertNotNull(enhanced);
    assertEquals("1=I 2=want 3=to 4=go 5=quickly 6=: 7=now 8=: 9=ok", wordsOf(basic));
    assertEquals(wordsOf(basic), wordsOf(enhanced));
    assertEquals(Collections.singletonList("advmod"), relations(basic, 4, 5));
    assertEquals(Collections.singletonList("advmod"), relations(enhanced, 4, 5));
    // the edges of the words which moved are still there
    assertEquals(Collections.singletonList("nsubj:xsubj"), relations(enhanced, 4, 1));
    assertEquals(Collections.singletonList("discourse"), relations(enhanced, 2, 9));
    assertEquals(Collections.singletonList("discourse"), relations(basic, 2, 9));
  }

  /** With -graph, the word still goes in both graphs, but the edge only in the one named */
  @Test
  public void addDepEdgeToOneGraph() throws IOException {
    SemgrexGraphs graphs = editSentence(ENHANCED_SENTENCE, "{word:go}=g !>advmod@enhanced {word:quickly}\naddDep -gov g -reln advmod -word quickly -position +g -graph enhanced");
    SemanticGraph basic = graphs.getDefault();
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    assertEquals(wordsOf(basic), wordsOf(enhanced));
    assertEquals(Collections.singletonList("advmod"), relations(enhanced, 4, 5));
    assertEquals(0, basic.incomingEdgeList(basic.getNodeByIndex(5)).size());

    graphs = editSentence(ENHANCED_SENTENCE, "{word:go}=g !> {word:quickly}\naddDep -gov g -reln advmod -word quickly -position +g -graph basic");
    basic = graphs.getDefault();
    enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    assertEquals(wordsOf(basic), wordsOf(enhanced));
    assertEquals(Collections.singletonList("advmod"), relations(basic, 4, 5));
    assertEquals(0, enhanced.incomingEdgeList(enhanced.getNodeByIndex(5)).size());
  }

  /**
   * An empty word stays after the word it follows: a word put after Bill
   * goes after 5.1, and so does one put before tea
   */
  @Test
  public void addDepAroundEmptyWord() throws IOException {
    String expected = "1=Sue 2=likes 3=coffee 4=and 5=Bill 5.1=likes 6=green 7=tea";
    SemgrexGraphs graphs = editSentence(EMPTY_WORD_SENTENCE, "{word:Bill}=b !> {word:green}\naddDep -gov b -reln amod -word green -position +b");
    assertEquals(expected, wordsOf(graphs.get(SemgrexGraphName.ENHANCED)));
    assertEquals(expected.replace(" 5.1=likes", ""), wordsOf(graphs.getDefault()));

    graphs = editSentence(EMPTY_WORD_SENTENCE, "{word:tea}=t !> {word:green}\naddDep -gov t -reln amod -word green -position -t");
    assertEquals(expected, wordsOf(graphs.get(SemgrexGraphName.ENHANCED)));
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    // tea moved, and is still the object of the empty word
    assertEquals(Collections.singletonList("obj"), relations(enhanced, word(enhanced, 5, 1), word(enhanced, 7, 0)));

    // there is no place directly before an empty word
    SsurgeonPattern before = Ssurgeon.readFromEditText("{word:Bill}=b <nsubj@enhanced {}=x !> {word:green}\naddDep -gov b -reln amod -word green -position -x", "UniversalEnglish").get(0);
    assertThrows(SsurgeonRuntimeException.class, () -> before.iterate(readGraphs(EMPTY_WORD_SENTENCE)));
  }

  /**
   * An optional node found through the enhanced graph stops addDep -name
   * once the word is there, even though the basic graph never gets the edge
   */
  @Test
  public void addDepFillsOptionalEnhancedNode() throws IOException {
    SemgrexGraphs graphs = editSentence(ENHANCED_SENTENCE, "{word:go}=g ?>advmod@enhanced {word:quickly}=q\naddDep -gov g -reln advmod -word quickly -position +g -name q -graph enhanced");
    SemanticGraph basic = graphs.getDefault();
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    assertEquals("1=I 2=want 3=to 4=go 5=quickly 6=: 7=now 8=: 9=ok", wordsOf(enhanced));
    assertEquals(wordsOf(basic), wordsOf(enhanced));
    assertEquals(Collections.singletonList("advmod"), relations(enhanced, 4, 5));
    assertEquals(0, basic.incomingEdgeList(basic.getNodeByIndex(5)).size());
  }

  /** A dependent of an empty word gets its edge in the enhanced graph, the only one with its governor */
  @Test
  public void addDepToEmptyWord() throws IOException {
    SemgrexGraphs graphs = editSentence(EMPTY_WORD_SENTENCE, "{}=x >nsubj@enhanced {word:Bill} !>advmod@enhanced {word:also}\naddDep -gov x -reln advmod -word also -position +x");
    SemanticGraph basic = graphs.getDefault();
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    assertEquals("1=Sue 2=likes 3=coffee 4=and 5=Bill 5.1=likes 6=also 7=tea", wordsOf(enhanced));
    assertEquals(Collections.singletonList("advmod"), relations(enhanced, word(enhanced, 5, 1), word(enhanced, 6, 0)));
    IndexedWord also = word(basic, 6, 0);
    assertEquals("also", also.word());
    assertEquals(0, basic.incomingEdgeList(also).size());
  }

  /** deleteLeaf deletes the word from both graphs and renumbers both, so the enhanced graph stays */
  @Test
  public void deleteLeafFromEveryGraph() throws IOException {
    SemgrexGraphs graphs = editSentence(ENHANCED_SENTENCE, "{word:/:/}=p\ndeleteLeaf -node p");
    SemanticGraph basic = graphs.getDefault();
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    assertNotNull(enhanced);
    assertEquals("1=I 2=want 3=to 4=go 5=now 6=ok", wordsOf(basic));
    assertEquals(wordsOf(basic), wordsOf(enhanced));
    assertEquals(Collections.singletonList("nsubj:xsubj"), relations(enhanced, 4, 1));
    assertEquals(Collections.singletonList("discourse"), relations(enhanced, 2, 6));
    assertEquals(Collections.singletonList("discourse"), relations(basic, 2, 6));
  }

  /**
   * Bill has dependents in the basic graph and none in the enhanced graph.
   * He is only deleted with -graph enhanced, which takes his basic edges
   * with him, and the empty word after him follows the word before him.
   */
  @Test
  public void deleteLeafOfOneGraph() throws IOException {
    SsurgeonPattern everyGraph = Ssurgeon.readFromEditText("{word:Bill}=b\ndeleteLeaf -node b", "UniversalEnglish").get(0);
    assertFalse(everyGraph.iterate(readGraphs(EMPTY_WORD_SENTENCE)).second());
    SsurgeonPattern basicGraph = Ssurgeon.readFromEditText("{word:Bill}=b\ndeleteLeaf -node b -graph basic", "UniversalEnglish").get(0);
    assertFalse(basicGraph.iterate(readGraphs(EMPTY_WORD_SENTENCE)).second());

    SemgrexGraphs graphs = editSentence(EMPTY_WORD_SENTENCE, "{word:Bill}=b\ndeleteLeaf -node b -graph enhanced");
    SemanticGraph basic = graphs.getDefault();
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    assertEquals("1=Sue 2=likes 3=coffee 4=and 5=tea", wordsOf(basic));
    assertEquals("1=Sue 2=likes 3=coffee 4=and 4.1=likes 5=tea", wordsOf(enhanced));
    // the empty word keeps its edges
    IndexedWord empty = word(enhanced, 4, 1);
    assertEquals(Collections.singletonList("cc"), relations(enhanced, empty, word(enhanced, 4, 0)));
    assertEquals(Collections.singletonList("obj"), relations(enhanced, empty, word(enhanced, 5, 0)));
    assertEquals(Collections.singletonList("conj:and"), relations(enhanced, word(enhanced, 2, 0), empty));
    // the basic dependents of Bill went with him
    assertEquals(0, basic.incomingEdgeList(basic.getNodeByIndex(4)).size());
    assertEquals(0, basic.incomingEdgeList(basic.getNodeByIndex(5)).size());
  }

  /** "Sue sleeps very soundly", with three empty words which are leaves */
  private static final String EMPTY_LEAVES_SENTENCE = String.join("\n",
    "# text = Sue sleeps very soundly",
    "1\tSue\tSue\tPROPN\t_\t_\t2\tnsubj\t2:nsubj\t_",
    "2\tsleeps\tsleep\tVERB\t_\t_\t0\troot\t0:root\t_",
    "2.1\tsnores\tsnore\tVERB\t_\t_\t_\t_\t2:conj\t_",
    "2.2\tdreams\tdream\tVERB\t_\t_\t_\t_\t2:conj\t_",
    "3\tvery\tvery\tADV\t_\t_\t4\tadvmod\t4:advmod\t_",
    "3.1\tdeeply\tdeeply\tADV\t_\t_\t_\t_\t2:advmod\t_",
    "4\tsoundly\tsoundly\tADV\t_\t_\t2\tadvmod\t2:advmod\t_",
    "",
    "");

  /**
   * Deleting an empty word moves no word, and the empty words after it
   * close the gap.  Deleting a word moves its empty words to follow the
   * word before it, after the ones already there.
   */
  @Test
  public void deleteLeafWithEmptyWords() throws IOException {
    SemgrexGraphs graphs = editSentence(EMPTY_LEAVES_SENTENCE, "{word:dreams}=d\ndeleteLeaf -node d");
    assertEquals("1=Sue 2=sleeps 2.1=snores 3=very 3.1=deeply 4=soundly", wordsOf(graphs.get(SemgrexGraphName.ENHANCED)));
    assertEquals("1=Sue 2=sleeps 3=very 4=soundly", wordsOf(graphs.getDefault()));

    graphs = editSentence(EMPTY_LEAVES_SENTENCE, "{word:snores}=s\ndeleteLeaf -node s");
    assertEquals("1=Sue 2=sleeps 2.1=dreams 3=very 3.1=deeply 4=soundly", wordsOf(graphs.get(SemgrexGraphName.ENHANCED)));
    assertEquals("1=Sue 2=sleeps 3=very 4=soundly", wordsOf(graphs.getDefault()));

    graphs = editSentence(EMPTY_LEAVES_SENTENCE, "{word:very}=v\ndeleteLeaf -node v");
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    assertEquals("1=Sue 2=sleeps 2.1=snores 2.2=dreams 2.3=deeply 3=soundly", wordsOf(enhanced));
    assertEquals("1=Sue 2=sleeps 3=soundly", wordsOf(graphs.getDefault()));
    assertEquals(Collections.singletonList("advmod"), relations(enhanced, word(enhanced, 2, 0), word(enhanced, 2, 3)));
  }

  /** Deleting a copy of a word moves no other word, as the word itself still holds the index */
  @Test
  public void deleteLeafCopy() throws IOException {
    SemgrexGraphs graphs = readGraphs(ENHANCED_SENTENCE);
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    IndexedWord now = enhanced.getNodeByIndex(6);
    IndexedWord copy = now.makeSoftCopy(1);
    enhanced.addEdge(enhanced.getNodeByIndex(4), copy, GrammaticalRelation.valueOf(Language.UniversalEnglish, "advmod"), 1.0, false);
    SemgrexMatcher matcher = SemgrexPattern.compile("{word:go}").matcher(graphs);
    assertTrue(matcher.find());
    matcher.putNode("c", copy);
    assertTrue(new DeleteLeaf("c").evaluate(graphs, matcher));
    assertFalse(enhanced.containsVertex(copy));
    assertTrue(enhanced.containsVertex(now));
    assertEquals("1=I 2=want 3=to 4=go 5=: 6=now 7=: 8=ok", wordsOf(enhanced));
    assertEquals(wordsOf(enhanced), wordsOf(graphs.getDefault()));
    SsurgeonPattern.checkSharedWords(graphs);
  }

  /**
   * "ok" is a leaf in both graphs, but the enhanced graph's only root,
   * so deleting it would leave that graph with no root, and nothing is deleted
   */
  @Test
  public void deleteLeafKeepsOnlyRootOfAnyGraph() throws IOException {
    SemgrexGraphs graphs = readGraphs(ENHANCED_SENTENCE);
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    enhanced.setRoots(Collections.singleton(enhanced.getNodeByIndex(8)));
    SemgrexMatcher matcher = SemgrexPattern.compile("{word:ok}=o").matcher(graphs);
    assertTrue(matcher.find());
    assertFalse(new DeleteLeaf("o").evaluate(graphs, matcher));
    assertEquals(8, enhanced.size());
    assertEquals(8, graphs.getDefault().size());

    // with a second root, it goes, and stops being a root
    IndexedWord want = enhanced.getNodeByIndex(2);
    enhanced.setRoots(Arrays.asList(want, enhanced.getNodeByIndex(8)));
    assertTrue(new DeleteLeaf("o").evaluate(graphs, matcher));
    assertEquals(Collections.singleton(want), new HashSet<>(enhanced.getRoots()));
    assertEquals("1=I 2=want 3=to 4=go 5=: 6=now 7=:", wordsOf(enhanced));
  }

  /** "Sue likes coffee and BillBob tea", with an empty word 5.1 after the word to split */
  private static final String SPLIT_SENTENCE = String.join("\n",
    "# text = Sue likes coffee and BillBob tea",
    "1\tSue\tSue\tPROPN\t_\t_\t2\tnsubj\t2:nsubj\t_",
    "2\tlikes\tlike\tVERB\t_\t_\t0\troot\t0:root\t_",
    "3\tcoffee\tcoffee\tNOUN\t_\t_\t2\tobj\t2:obj\t_",
    "4\tand\tand\tCCONJ\t_\t_\t5\tcc\t5.1:cc\t_",
    "5\tBillBob\tBillBob\tPROPN\t_\t_\t2\tconj\t5.1:nsubj\t_",
    "5.1\tlikes\tlike\tVERB\t_\t_\t_\t_\t2:conj:and\t_",
    "6\ttea\ttea\tNOUN\t_\t_\t5\torphan\t5.1:obj\t_",
    "",
    "");

  /**
   * Splitting the word before an empty word puts the empty word after the
   * last piece, and the new piece gets its edge in both graphs
   */
  @Test
  public void splitWordBeforeEmptyWord() throws IOException {
    SemgrexGraphs graphs = editSentence(SPLIT_SENTENCE, "{word:BillBob}=b\nsplitWord -node b -exact Bill -exact Bob -reln flat -headIndex 0");
    SemanticGraph basic = graphs.getDefault();
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    assertNotNull(enhanced);
    assertEquals("1=Sue 2=likes 3=coffee 4=and 5=Bill 6=Bob 6.1=likes 7=tea", wordsOf(enhanced));
    assertEquals("1=Sue 2=likes 3=coffee 4=and 5=Bill 6=Bob 7=tea", wordsOf(basic));

    IndexedWord empty = word(enhanced, 6, 1);
    IndexedWord bill = word(basic, 5, 0);
    IndexedWord bob = word(basic, 6, 0);
    // the head piece keeps the edges of the word in each graph
    assertEquals(Collections.singletonList("conj"), relations(basic, word(basic, 2, 0), bill));
    assertEquals(Collections.singletonList("orphan"), relations(basic, bill, word(basic, 7, 0)));
    assertEquals(Collections.singletonList("nsubj"), relations(enhanced, empty, bill));
    // the new piece hangs from the head piece in both
    assertEquals(Collections.singletonList("flat"), relations(basic, bill, bob));
    assertEquals(Collections.singletonList("flat"), relations(enhanced, bill, bob));
    // the empty word kept its edges
    assertEquals(Collections.singletonList("cc"), relations(enhanced, empty, word(enhanced, 4, 0)));
    assertEquals(Collections.singletonList("obj"), relations(enhanced, empty, word(enhanced, 7, 0)));
    assertEquals(Collections.singletonList("conj:and"), relations(enhanced, word(enhanced, 2, 0), empty));
  }

  /** With the head as the last piece, the empty word still follows it */
  @Test
  public void splitWordHeadLastBeforeEmptyWord() throws IOException {
    SemgrexGraphs graphs = editSentence(SPLIT_SENTENCE, "{word:BillBob}=b\nsplitWord -node b -exact Bill -exact Bob -reln flat -headIndex 1");
    SemanticGraph basic = graphs.getDefault();
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    assertEquals("1=Sue 2=likes 3=coffee 4=and 5=Bill 6=Bob 6.1=likes 7=tea", wordsOf(enhanced));
    IndexedWord bill = word(basic, 5, 0);
    IndexedWord bob = word(basic, 6, 0);
    assertEquals(Collections.singletonList("conj"), relations(basic, word(basic, 2, 0), bob));
    assertEquals(Collections.singletonList("nsubj"), relations(enhanced, word(enhanced, 6, 1), bob));
    assertEquals(Collections.singletonList("flat"), relations(basic, bob, bill));
    assertEquals(Collections.singletonList("flat"), relations(enhanced, bob, bill));
  }

  /**
   * With -siblings, the new piece hangs from the governors the word has in
   * each graph, which differ here.  -edge names the edge in the basic graph.
   */
  @Test
  public void splitWordSiblingsInEachGraph() throws IOException {
    SemgrexGraphs graphs = editSentence(SPLIT_SENTENCE, "{word:BillBob}=b\nsplitWord -node b -exact Bill -exact Bob -reln 1=conj -headIndex 0 -siblings true -edge 1=e\nrelabelNamedEdge -edge e -reln appos");
    SemanticGraph basic = graphs.getDefault();
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    assertEquals("1=Sue 2=likes 3=coffee 4=and 5=Bill 6=Bob 6.1=likes 7=tea", wordsOf(enhanced));
    IndexedWord bob = word(basic, 6, 0);
    assertEquals(Collections.singletonList("appos"), relations(basic, word(basic, 2, 0), bob));
    assertEquals(Collections.singletonList("conj"), relations(enhanced, word(enhanced, 6, 1), bob));
    assertEquals(1, enhanced.incomingEdgeList(bob).size());
  }

  /**
   * "Sue likes coffee and Bill Bob tea", with an empty word after Bill and
   * another after Bob.  Bill heads Bob in both graphs.
   */
  private static final String MERGE_SENTENCE = String.join("\n",
    "# text = Sue likes coffee and Bill Bob tea",
    "1\tSue\tSue\tPROPN\t_\t_\t2\tnsubj\t2:nsubj\t_",
    "2\tlikes\tlike\tVERB\t_\t_\t0\troot\t0:root\t_",
    "3\tcoffee\tcoffee\tNOUN\t_\t_\t2\tobj\t2:obj\t_",
    "4\tand\tand\tCCONJ\t_\t_\t5\tcc\t5.1:cc\t_",
    "5\tBill\tBill\tPROPN\t_\t_\t2\tconj\t5.1:nsubj\t_",
    "5.1\tlikes\tlike\tVERB\t_\t_\t_\t_\t2:conj:and\t_",
    "6\tBob\tBob\tPROPN\t_\t_\t5\tflat\t5:flat\t_",
    "6.1\tmore\tmore\tADV\t_\t_\t_\t_\t5.1:advmod\t_",
    "7\ttea\ttea\tNOUN\t_\t_\t5\torphan\t5.1:obj\t_",
    "",
    "");

  /** Checks the merged sentence, which is the same whichever of Bill and Bob was the head */
  private static void checkMergedSentence(SemgrexGraphs graphs) {
    SemanticGraph basic = graphs.getDefault();
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    assertNotNull(enhanced);
    // both empty words follow the merged word, in the order they were in
    assertEquals("1=Sue 2=likes 3=coffee 4=and 5=BillBob 5.1=likes 5.2=more 6=tea", wordsOf(enhanced));
    assertEquals("1=Sue 2=likes 3=coffee 4=and 5=BillBob 6=tea", wordsOf(basic));

    IndexedWord merged = word(basic, 5, 0);
    IndexedWord likes = word(enhanced, 5, 1);
    assertEquals(Collections.singletonList("conj"), relations(basic, word(basic, 2, 0), merged));
    assertEquals(Collections.singletonList("cc"), relations(basic, merged, word(basic, 4, 0)));
    assertEquals(Collections.singletonList("orphan"), relations(basic, merged, word(basic, 6, 0)));
    assertEquals(Collections.singletonList("nsubj"), relations(enhanced, likes, merged));
    assertEquals(Collections.singletonList("obj"), relations(enhanced, likes, word(enhanced, 6, 0)));
    assertEquals(Collections.singletonList("advmod"), relations(enhanced, likes, word(enhanced, 5, 2)));
    assertEquals(Collections.singletonList("conj:and"), relations(enhanced, word(enhanced, 2, 0), likes));
    assertEquals(0, basic.outgoingEdgeList(merged).stream().filter(e -> e.getRelation().toString().equals("flat")).count());
    assertEquals(0, enhanced.outgoingEdgeList(merged).stream().filter(e -> e.getRelation().toString().equals("flat")).count());
  }

  /**
   * mergeNodes merges the words in both graphs, and the empty words after
   * either of them follow the merged word.  The name of Bob refers to the
   * merged word afterwards.
   */
  @Test
  public void mergeNodesWithEmptyWords() throws IOException {
    SemgrexGraphs graphs = editSentence(MERGE_SENTENCE, "{word:Bill}=b . {word:Bob}=c\nmergeNodes -node b -node c\neditNode -node c -lemma Bilbo");
    checkMergedSentence(graphs);
    assertEquals("Bilbo", word(graphs.getDefault(), 5, 0).lemma());
  }

  /** With the second word as the head, the merged word still takes the place of the first */
  @Test
  public void mergeNodesHeadLast() throws IOException {
    String sentence = MERGE_SENTENCE
      .replace("4\tand\tand\tCCONJ\t_\t_\t5\tcc", "4\tand\tand\tCCONJ\t_\t_\t6\tcc")
      .replace("5\tBill\tBill\tPROPN\t_\t_\t2\tconj\t5.1:nsubj", "5\tBill\tBill\tPROPN\t_\t_\t6\tflat\t6:flat")
      .replace("6\tBob\tBob\tPROPN\t_\t_\t5\tflat\t5:flat", "6\tBob\tBob\tPROPN\t_\t_\t2\tconj\t5.1:nsubj")
      .replace("7\ttea\ttea\tNOUN\t_\t_\t5\torphan", "7\ttea\ttea\tNOUN\t_\t_\t6\torphan");
    checkMergedSentence(editSentence(sentence, "{word:Bill}=b . {word:Bob}=c\nmergeNodes -node b -node c"));
  }

  /**
   * "test foo bar", where foo and bar are both objects of test in the basic
   * graph, so either could head the merged word, but in the enhanced graph
   * one hangs from the other, so only that one can
   */
  private static final String TWO_HEADS_MERGE_SENTENCE = String.join("\n",
    "# text = test foo bar",
    "1\ttest\ttest\tVERB\t_\t_\t0\troot\t0:root\t_",
    "2\tfoo\tfoo\tNOUN\t_\t_\t1\tobj\t3:compound\t_",
    "3\tbar\tbar\tPROPN\t_\t_\t1\tobj\t1:obj\t_",
    "",
    "");

  /** The head is the first word which every graph allows, rather than the first in each graph */
  @Test
  public void mergeNodesHeadEveryGraphAllows() throws IOException {
    String block = "{word:foo}=f . {word:bar}=b\nmergeNodes -node f -node b";
    // the enhanced graph only allows bar, the second word, as the head
    SemgrexGraphs graphs = editSentence(TWO_HEADS_MERGE_SENTENCE, block);
    SemanticGraph basic = graphs.getDefault();
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    assertEquals("1=test 2=foobar", wordsOf(basic));
    assertEquals("1=test 2=foobar", wordsOf(enhanced));
    // the merged word is bar, with its upos, at the place of foo
    assertEquals("PROPN", word(basic, 2, 0).get(CoreAnnotations.CoarseTagAnnotation.class));
    assertEquals(Collections.singletonList("obj"), relations(basic, 1, 2));
    assertEquals(Collections.singletonList("obj"), relations(enhanced, 1, 2));

    // and the other way around, where the enhanced graph only allows foo
    String other = TWO_HEADS_MERGE_SENTENCE.replace("\t3:compound\t", "\t1:obj\t").replace("\t1:obj\t_\n\n", "\t2:compound\t_\n\n");
    graphs = editSentence(other, block);
    assertEquals("1=test 2=foobar", wordsOf(graphs.get(SemgrexGraphName.ENHANCED)));
    assertEquals("NOUN", word(graphs.getDefault(), 2, 0).get(CoreAnnotations.CoarseTagAnnotation.class));
    assertEquals(Collections.singletonList("obj"), relations(graphs.get(SemgrexGraphName.ENHANCED), 1, 2));
  }

  /** If the enhanced graph does not allow the merge, nothing is merged, even though the basic graph does */
  @Test
  public void mergeNodesNeedsEveryGraph() throws IOException {
    // in the enhanced graph, Bob has a parent outside the phrase of his own
    String sentence = MERGE_SENTENCE.replace("\t5:flat\t", "\t5:flat|2:dep\t");
    SsurgeonPattern pattern = Ssurgeon.readFromEditText("{word:Bill}=b . {word:Bob}=c\nmergeNodes -node b -node c", "UniversalEnglish").get(0);
    Pair<SemgrexGraphs, Boolean> result = pattern.iterate(readGraphs(sentence));
    assertFalse(result.second());
    assertEquals("1=Sue 2=likes 3=coffee 4=and 5=Bill 5.1=likes 6=Bob 6.1=more 7=tea", wordsOf(result.first().get(SemgrexGraphName.ENHANCED)));
  }

  /** "Sue sleeps very soundly", with an empty word before the first word and two more after words */
  private static final String REINDEX_SENTENCE = String.join("\n",
    "# text = Sue sleeps very soundly",
    "0.1\tit\tit\tPRON\t_\t_\t_\t_\t2:expl\t_",
    "1\tSue\tSue\tPROPN\t_\t_\t2\tnsubj\t2:nsubj\t_",
    "2\tsleeps\tsleep\tVERB\t_\t_\t0\troot\t0:root\t_",
    "2.1\tsnores\tsnore\tVERB\t_\t_\t_\t_\t2:conj\t_",
    "3\tvery\tvery\tADV\t_\t_\t4\tadvmod\t4:advmod\t_",
    "3.1\tdeeply\tdeeply\tADV\t_\t_\t_\t_\t2:advmod\t_",
    "4\tsoundly\tsoundly\tADV\t_\t_\t2\tadvmod\t2:advmod\t_",
    "",
    "");

  /** Takes a word out of the given graphs, leaving a gap where it was */
  private static void remove(IndexedWord word, SemanticGraph... graphs) {
    for (SemanticGraph graph : graphs) {
      graph.removeVertex(word);
    }
  }

  /** Runs reindexGraph directly, so the graphs can have gaps which CoNLL-U cannot */
  private static boolean reindex(SemgrexGraphs graphs) {
    SemgrexMatcher matcher = SemgrexPattern.compile("{word:sleeps}").matcher(graphs);
    assertTrue(matcher.find());
    return new ReindexGraph().evaluate(graphs, matcher);
  }

  /**
   * With "very" gone from both graphs, the words close up, the empty word
   * which followed "very" follows "sleeps" after its own empty word, and
   * the empty word before the first word stays where it is
   */
  @Test
  public void reindexGraphWithEmptyWords() throws IOException {
    SemgrexGraphs graphs = readGraphs(REINDEX_SENTENCE);
    SemanticGraph basic = graphs.getDefault();
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    remove(word(basic, 3, 0), basic, enhanced);

    assertTrue(reindex(graphs));
    assertEquals("0.1=it 1=Sue 2=sleeps 2.1=snores 2.2=deeply 3=soundly", wordsOf(enhanced));
    assertEquals("1=Sue 2=sleeps 3=soundly", wordsOf(basic));
    SsurgeonPattern.checkSharedWords(graphs);
    IndexedWord sleeps = word(basic, 2, 0);
    assertEquals(Collections.singletonList("advmod"), relations(basic, sleeps, word(basic, 3, 0)));
    assertEquals(Collections.singletonList("advmod"), relations(enhanced, sleeps, word(enhanced, 3, 0)));
    assertEquals(Collections.singletonList("advmod"), relations(enhanced, sleeps, word(enhanced, 2, 2)));
    assertEquals(Collections.singletonList("expl"), relations(enhanced, sleeps, word(enhanced, 0, 1)));

    // nothing left to renumber
    assertFalse(reindex(graphs));
  }

  /** A word which only one graph has still gets its own place among the words */
  @Test
  public void reindexGraphWordInOneGraph() throws IOException {
    SemgrexGraphs graphs = readGraphs(REINDEX_SENTENCE);
    SemanticGraph basic = graphs.getDefault();
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    remove(word(basic, 1, 0), basic);
    remove(word(basic, 3, 0), basic, enhanced);

    assertTrue(reindex(graphs));
    assertEquals("0.1=it 1=Sue 2=sleeps 2.1=snores 2.2=deeply 3=soundly", wordsOf(enhanced));
    assertEquals("2=sleeps 3=soundly", wordsOf(basic));
    SsurgeonPattern.checkSharedWords(graphs);
  }

  /** Cuts "go" off from "want" in the basic graph alone, then kills the unrooted nodes */
  private static final String CUT_GO = WANT_I_GO + "\nremoveEdge -gov w -dep g -reln xcomp\nkillNonRooted";

  /**
   * After a cut in the basic graph alone, "to go" still hangs from "want"
   * in the enhanced graph, so by default nothing is deleted
   */
  @Test
  public void killNonRootedKeepsNodesRootedInAnyGraph() throws IOException {
    SemgrexGraphs graphs = editSentence(ENHANCED_SENTENCE, CUT_GO);
    SemanticGraph basic = graphs.getDefault();
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    assertEquals(8, basic.size());
    assertEquals(8, enhanced.size());
    assertEquals(Collections.emptyList(), relations(basic, 2, 4));
    assertEquals(Collections.singletonList("xcomp"), relations(enhanced, 2, 4));
  }

  /** With -graph basic, the piece cut off in the basic graph is deleted from both */
  @Test
  public void killNonRootedOneGraphDecides() throws IOException {
    SemgrexGraphs graphs = editSentence(ENHANCED_SENTENCE, CUT_GO + " -graph basic");
    SemanticGraph basic = graphs.getDefault();
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    // the words left keep their indices
    assertEquals("1=I 2=want 5=: 6=now 7=: 8=ok", wordsOf(basic));
    assertEquals(wordsOf(basic), wordsOf(enhanced));
    assertEquals(Collections.singletonList("nsubj"), relations(enhanced, 2, 1));
  }

  /** Cut in both graphs, the piece has no path to a root in either, so it goes by default */
  @Test
  public void killNonRootedAfterCutInEveryGraph() throws IOException {
    SemgrexGraphs graphs = editSentence(ENHANCED_SENTENCE, WANT_I_GO + "\nremoveEdge -gov w -dep g -reln xcomp\nremoveEdge -gov w -dep g -reln xcomp -graph enhanced\nkillNonRooted");
    assertEquals("1=I 2=want 5=: 6=now 7=: 8=ok", wordsOf(graphs.get(SemgrexGraphName.ENHANCED)));
    assertEquals("1=I 2=want 5=: 6=now 7=: 8=ok", wordsOf(graphs.getDefault()));
  }

  /**
   * A second root of the enhanced graph, cut off from "want" in the basic
   * graph, is deleted with the rest of its piece when the basic graph
   * decides, since "want" is still a root of the enhanced graph
   */
  @Test
  public void killNonRootedDeletesSecondRoot() throws IOException {
    SemgrexGraphs graphs = readGraphs(ENHANCED_SENTENCE);
    SemanticGraph basic = graphs.getDefault();
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    IndexedWord want = basic.getNodeByIndex(2);
    IndexedWord go = basic.getNodeByIndex(4);
    enhanced.setRoots(Arrays.asList(want, go));
    SemgrexMatcher matcher = SemgrexPattern.compile("{word:want}").matcher(graphs);
    assertTrue(matcher.find());
    assertTrue(basic.removeEdge(basic.getEdge(want, go)));
    assertTrue(new KillNonRootedNodes(SemgrexGraphName.BASIC).evaluate(graphs, matcher));
    assertEquals("1=I 2=want 5=: 6=now 7=: 8=ok", wordsOf(basic));
    assertEquals(wordsOf(basic), wordsOf(enhanced));
    assertEquals(Collections.singleton(want), new HashSet<>(enhanced.getRoots()));
  }

  /** The basic graph's only root is not deleted, even when the enhanced graph decides and gives it no path */
  @Test
  public void killNonRootedKeepsRoots() throws IOException {
    SemgrexGraphs graphs = readGraphs(ENHANCED_SENTENCE);
    SemanticGraph basic = graphs.getDefault();
    SemanticGraph enhanced = graphs.get(SemgrexGraphName.ENHANCED);
    IndexedWord want = basic.getNodeByIndex(2);
    IndexedWord ok = basic.getNodeByIndex(8);
    // in the enhanced graph, "ok" alone is the root, and nothing reaches it
    enhanced.setRoots(Collections.singleton(ok));
    SemgrexMatcher matcher = SemgrexPattern.compile("{word:want}").matcher(graphs);
    assertTrue(matcher.find());
    assertTrue(new KillNonRootedNodes(SemgrexGraphName.ENHANCED).evaluate(graphs, matcher));
    assertEquals("2=want 8=ok", wordsOf(enhanced));
    assertEquals("2=want 8=ok", wordsOf(basic));
    assertTrue(basic.isRoot(want));
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
