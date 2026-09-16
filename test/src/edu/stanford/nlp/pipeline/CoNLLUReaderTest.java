package edu.stanford.nlp.pipeline;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.CoNLLUReader.LineType;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.CoreMap;

/**
 * Tests that CoNLL-U text is turned into the Annotations it describes:
 * the tokens and their character offsets, the MWTs, the empty words, the
 * basic and enhanced graphs, and the comments.
 *
 * @author John Bauer
 */
public class CoNLLUReaderTest {

  /**
   * The non breaking space, U+00A0.
   *<br>
   * Built from its code point rather than written as an escape, since a
   * \\u escape in a Java source file is turned into the character it names
   * before the file is even tokenized, which makes a test about escaping
   * hard to read.
   */
  private static final String NBSP = Character.toString((char) 0x00A0);

  /** Two sentences, no MWT and no enhanced dependencies */
  private static final String BASIC = String.join("\n",
      "# sent_id = 1",
      "# text = Hola mundo.",
      "1\tHola\thola\tINTJ\t_\t_\t2\tdiscourse\t_\t_",
      "2\tmundo\tmundo\tNOUN\t_\tGender=Masc|Number=Sing\t0\troot\t_\tSpaceAfter=No",
      "3\t.\t.\tPUNCT\t_\t_\t2\tpunct\t_\t_",
      "",
      "# sent_id = 2",
      "# text = Adiós.",
      "1\tAdiós\tadiós\tINTJ\t_\t_\t0\troot\t_\tSpaceAfter=No",
      "2\t.\t.\tPUNCT\t_\t_\t1\tpunct\t_\t_",
      "",
      "");

  /** "Vamos al parque", where "al" is the MWT "a" + "el" */
  private static final String MWT = String.join("\n",
      "# text = Vamos al parque",
      "1\tVamos\tir\tVERB\t_\t_\t0\troot\t_\t_",
      "2-3\tal\t_\t_\t_\t_\t_\t_\t_\t_",
      "2\ta\ta\tADP\t_\t_\t4\tcase\t_\t_",
      "3\tel\tel\tDET\t_\t_\t4\tdet\t_\t_",
      "4\tparque\tparque\tNOUN\t_\t_\t1\tobl\t_\tSpaceAfter=No",
      "",
      "");

  /** Gapping, so that the enhanced graph has an empty word in it */
  private static final String ENHANCED = String.join("\n",
      "# text = Ella come manzanas y él peras",
      "1\tElla\tella\tPRON\t_\t_\t2\tnsubj\t2:nsubj\t_",
      "2\tcome\tcomer\tVERB\t_\t_\t0\troot\t0:root\t_",
      "3\tmanzanas\tmanzana\tNOUN\t_\t_\t2\tobj\t2:obj\t_",
      "4\ty\ty\tCCONJ\t_\t_\t6\tcc\t6:cc\t_",
      "5\tél\tél\tPRON\t_\t_\t6\tnsubj\t6:nsubj\t_",
      "6\tperas\tpera\tNOUN\t_\t_\t2\tconj\t6.1:obj\tSpaceAfter=No",
      "6.1\tcome\tcomer\tVERB\t_\t_\t_\t_\t2:conj\t_",
      "",
      "");

  /**
   * Write the text to a temp file and read it back as the reader's Annotations
   */
  private static List<Annotation> readDocuments(String conllu) throws Exception {
    File file = IOUtils.writeStringToTempFile(conllu, "conllutest", "UTF-8");
    file.deleteOnExit();
    try {
      return new CoNLLUReader().readCoNLLUFile(file.getPath());
    } finally {
      file.delete();
    }
  }

  /** The sentences of text which is expected to hold exactly one document */
  private static List<CoreMap> readSentences(String conllu) throws Exception {
    List<Annotation> documents = readDocuments(conllu);
    assertEquals(1, documents.size());
    return documents.get(0).get(CoreAnnotations.SentencesAnnotation.class);
  }

  private static List<CoreLabel> tokens(CoreMap sentence) {
    return sentence.get(CoreAnnotations.TokensAnnotation.class);
  }

  /**
   * The edges of a graph as reln(gov-idx, dep-idx) strings, sorted.
   *<br>
   * Sorted here rather than trusting the graph's own iteration order, so
   * that a test which cares about the edges doesn't break on a change to
   * how they are stored.  An empty word shows up as come-6.1.
   */
  private static List<String> describeEdges(SemanticGraph graph) {
    List<String> described = new ArrayList<>();
    for (SemanticGraphEdge edge : graph.edgeIterable()) {
      described.add(edge.getRelation().toString() + "(" +
                    describeNode(edge.getGovernor()) + ", " +
                    describeNode(edge.getDependent()) + ")");
    }
    Collections.sort(described);
    return described;
  }

  private static String describeNode(IndexedWord word) {
    Integer emptyIndex = word.get(CoreAnnotations.EmptyIndexAnnotation.class);
    return word.word() + "-" + word.index() + (emptyIndex == null ? "" : "." + emptyIndex);
  }

  private static List<String> sorted(String... items) {
    List<String> list = new ArrayList<>(java.util.Arrays.asList(items));
    Collections.sort(list);
    return list;
  }

  // ------------------------------------------------------------------
  // line classification
  // ------------------------------------------------------------------

  @Test
  public void testClassifyComment() {
    assertEquals(LineType.COMMENT, CoNLLUReader.classifyLine("# text = Hola"));
    assertEquals(LineType.COMMENT, CoNLLUReader.classifyLine("#"));
    assertEquals(LineType.COMMENT, CoNLLUReader.classifyLine("#sent_id=1"));
  }

  @Test
  public void testClassifyToken() {
    assertEquals(LineType.TOKEN, CoNLLUReader.classifyLine("1\tHola\thola\tINTJ"));
    assertEquals(LineType.TOKEN, CoNLLUReader.classifyLine("142\tHola"));
    assertEquals(LineType.TOKEN, CoNLLUReader.classifyLine("1\t"));
  }

  @Test
  public void testClassifyMWT() {
    assertEquals(LineType.MWT, CoNLLUReader.classifyLine("2-3\tal\t_"));
    assertEquals(LineType.MWT, CoNLLUReader.classifyLine("10-12\tfoo"));
    // an MWT line puts no constraint on what follows the range
    assertEquals(LineType.MWT, CoNLLUReader.classifyLine("2-3"));
  }

  @Test
  public void testClassifyEmpty() {
    assertEquals(LineType.EMPTY, CoNLLUReader.classifyLine("6.1\tcome\tcomer"));
    assertEquals(LineType.EMPTY, CoNLLUReader.classifyLine("12.34\tfoo"));
  }

  @Test
  public void testClassifyOther() {
    assertEquals(LineType.OTHER, CoNLLUReader.classifyLine(""));
    assertEquals(LineType.OTHER, CoNLLUReader.classifyLine("1"));
    assertEquals(LineType.OTHER, CoNLLUReader.classifyLine("1-"));
    assertEquals(LineType.OTHER, CoNLLUReader.classifyLine("1."));
    // an empty word needs a tab after its index, unlike an MWT
    assertEquals(LineType.OTHER, CoNLLUReader.classifyLine("6.1"));
    assertEquals(LineType.OTHER, CoNLLUReader.classifyLine("6.1x\tfoo"));
    assertEquals(LineType.OTHER, CoNLLUReader.classifyLine(" 1\tfoo"));
    assertEquals(LineType.OTHER, CoNLLUReader.classifyLine("1x\tfoo"));
    assertEquals(LineType.OTHER, CoNLLUReader.classifyLine("-1\tfoo"));
  }

  // ------------------------------------------------------------------
  // misc / features key values
  // ------------------------------------------------------------------

  @Test
  public void testParseKeyValues() {
    assertEquals(Collections.emptyMap(), CoNLLUReader.parseKeyValues("_"));
    assertEquals(Collections.emptyMap(), CoNLLUReader.parseKeyValues(null));
    assertEquals(Collections.singletonMap("SpaceAfter", "No"),
                 CoNLLUReader.parseKeyValues("SpaceAfter=No"));

    Map<String, String> two = CoNLLUReader.parseKeyValues("Gender=Masc|Number=Sing");
    assertEquals(2, two.size());
    assertEquals("Masc", two.get("Gender"));
    assertEquals("Sing", two.get("Number"));
    // the order of the keys is kept, so that writing the misc back out
    // doesn't shuffle it
    assertEquals("[Gender, Number]", two.keySet().toString());
  }

  @Test
  public void testParseKeyValuesOddInput() {
    // a value may contain = and keeps it
    assertEquals(Collections.singletonMap("Gloss", "a=b"), CoNLLUReader.parseKeyValues("Gloss=a=b"));
    // a piece with no = at all is skipped rather than throwing
    assertEquals(Collections.emptyMap(), CoNLLUReader.parseKeyValues("novalue"));
    assertEquals(Collections.singletonMap("a", "b"), CoNLLUReader.parseKeyValues("a=b|novalue"));
    assertEquals(Collections.emptyMap(), CoNLLUReader.parseKeyValues(""));
    assertEquals(Collections.singletonMap("a", ""), CoNLLUReader.parseKeyValues("a="));
  }

  @Test
  public void testRebuildMisc() {
    assertNull(CoNLLUReader.rebuildMisc(Collections.emptyMap()));
    assertEquals("SpaceAfter=No", CoNLLUReader.rebuildMisc(CoNLLUReader.parseKeyValues("SpaceAfter=No")));
    assertEquals("Gender=Masc|Number=Sing",
                 CoNLLUReader.rebuildMisc(CoNLLUReader.parseKeyValues("Gender=Masc|Number=Sing")));
  }

  @Test
  public void testUnescapeSpacesAfter() {
    assertEquals(" ", CoNLLUReader.unescapeSpacesAfter("\\s"));
    assertEquals("\t", CoNLLUReader.unescapeSpacesAfter("\\t"));
    assertEquals("\n", CoNLLUReader.unescapeSpacesAfter("\\n"));
    assertEquals("|", CoNLLUReader.unescapeSpacesAfter("\\p"));
    assertEquals("\\", CoNLLUReader.unescapeSpacesAfter("\\\\"));
    // the escape names the non breaking space, and that is what comes
    // back: the original character, not a plain space standing in for it
    assertEquals(NBSP, CoNLLUReader.unescapeSpacesAfter("\\u00A0"));
    assertEquals("  ", CoNLLUReader.unescapeSpacesAfter("\\s\\s"));
    assertEquals("abc", CoNLLUReader.unescapeSpacesAfter("abc"));
    // a backslash which escapes nothing is kept as itself
    assertEquals("\\q", CoNLLUReader.unescapeSpacesAfter("\\q"));
  }

  @Test
  public void testMiscToSpaceAfter() {
    assertEquals("", CoNLLUReader.miscToSpaceAfter(CoNLLUReader.parseKeyValues("SpaceAfter=No")));
    assertEquals(" ", CoNLLUReader.miscToSpaceAfter(CoNLLUReader.parseKeyValues("SpaceAfter=Yes")));
    assertEquals("  ", CoNLLUReader.miscToSpaceAfter(CoNLLUReader.parseKeyValues("SpacesAfter=\\s\\s")));
    // nothing said means a single space
    assertEquals(" ", CoNLLUReader.miscToSpaceAfter(Collections.emptyMap()));
  }

  // ------------------------------------------------------------------
  // tokens, text, and offsets
  // ------------------------------------------------------------------

  @Test
  public void testSentenceCount() throws Exception {
    List<CoreMap> sentences = readSentences(BASIC);
    assertEquals(2, sentences.size());
    assertEquals(3, tokens(sentences.get(0)).size());
    assertEquals(2, tokens(sentences.get(1)).size());
  }

  @Test
  public void testSentenceText() throws Exception {
    List<CoreMap> sentences = readSentences(BASIC);
    assertEquals("Hola mundo.", sentences.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("Adiós.", sentences.get(1).get(CoreAnnotations.TextAnnotation.class));
  }

  @Test
  public void testDocumentText() throws Exception {
    List<Annotation> documents = readDocuments(BASIC);
    assertEquals(1, documents.size());
    // the document text is not trimmed, and the SpaceAfter=No is respected
    assertEquals("Hola mundo. Adiós. ",
                 documents.get(0).get(CoreAnnotations.TextAnnotation.class));
  }

  @Test
  public void testCharacterOffsets() throws Exception {
    List<CoreMap> sentences = readSentences(BASIC);
    // offsets run across the whole document, not per sentence
    int[][] expected = {{0, 4}, {5, 10}, {10, 11}, {12, 17}, {17, 18}};
    List<CoreLabel> all = new ArrayList<>();
    all.addAll(tokens(sentences.get(0)));
    all.addAll(tokens(sentences.get(1)));
    assertEquals(expected.length, all.size());
    String text = readDocuments(BASIC).get(0).get(CoreAnnotations.TextAnnotation.class);
    for (int i = 0; i < expected.length; i++) {
      CoreLabel token = all.get(i);
      assertEquals("begin of " + token.word(), expected[i][0], token.beginPosition());
      assertEquals("end of " + token.word(), expected[i][1], token.endPosition());
      // the offsets have to actually point at the token in the doc text
      assertEquals(token.word(), text.substring(token.beginPosition(), token.endPosition()));
    }
  }

  @Test
  public void testBeforeAndAfter() throws Exception {
    List<CoreMap> sentences = readSentences(BASIC);
    List<CoreLabel> first = tokens(sentences.get(0));
    assertEquals("", first.get(0).before());
    assertEquals(" ", first.get(0).after());
    // SpaceAfter=No on mundo
    assertEquals("", first.get(1).after());
    assertEquals(" ", first.get(1).before());
    // the first token of a later sentence picks up the after of the one before it
    assertEquals(" ", tokens(sentences.get(1)).get(0).before());
  }

  @Test
  public void testTokenFields() throws Exception {
    CoreLabel mundo = tokens(readSentences(BASIC).get(0)).get(1);
    assertEquals("mundo", mundo.word());
    assertEquals("mundo", mundo.value());
    assertEquals("mundo", mundo.originalText());
    assertEquals("mundo", mundo.lemma());
    assertEquals("NOUN", mundo.get(CoreAnnotations.CoarseTagAnnotation.class));
    assertEquals(2, mundo.index());
    assertEquals(0, (int) mundo.get(CoreAnnotations.SentenceIndexAnnotation.class));
    // an XPOS of _ leaves the tag unset
    assertNull(mundo.tag());
    assertNotNull(mundo.get(CoreAnnotations.CoNLLUFeats.class));
  }

  @Test
  public void testUnderscoreFieldsAreNotStored() throws Exception {
    CoreLabel punct = tokens(readSentences(BASIC).get(0)).get(2);
    assertEquals(".", punct.word());
    assertEquals(".", punct.lemma());
    assertNull(punct.tag());
    assertNull(punct.get(CoreAnnotations.CoNLLUFeats.class));
    // SpaceAfter is consumed into the after, so nothing is left of the misc
    assertNull(punct.get(CoreAnnotations.CoNLLUMisc.class));
  }

  @Test
  public void testDocumentTokens() throws Exception {
    Annotation document = readDocuments(BASIC).get(0);
    List<CoreLabel> tokens = document.get(CoreAnnotations.TokensAnnotation.class);
    assertEquals(5, tokens.size());
    for (int i = 0; i < tokens.size(); i++) {
      assertEquals(i, (int) tokens.get(i).get(CoreAnnotations.TokenBeginAnnotation.class));
      assertEquals(i + 1, (int) tokens.get(i).get(CoreAnnotations.TokenEndAnnotation.class));
    }
  }

  @Test
  public void testSentenceIndices() throws Exception {
    List<CoreMap> sentences = readSentences(BASIC);
    for (int i = 0; i < sentences.size(); i++) {
      assertEquals(i, (int) sentences.get(i).get(CoreAnnotations.SentenceIndexAnnotation.class));
      for (CoreLabel token : tokens(sentences.get(i))) {
        assertEquals(i, (int) token.get(CoreAnnotations.SentenceIndexAnnotation.class));
      }
    }
  }

  @Test
  public void testComments() throws Exception {
    List<CoreMap> sentences = readSentences(BASIC);
    assertEquals(java.util.Arrays.asList("# sent_id = 1", "# text = Hola mundo."),
                 sentences.get(0).get(CoreAnnotations.CommentsAnnotation.class));
    assertEquals(java.util.Arrays.asList("# sent_id = 2", "# text = Adiós."),
                 sentences.get(1).get(CoreAnnotations.CommentsAnnotation.class));
  }

  // ------------------------------------------------------------------
  // graphs
  // ------------------------------------------------------------------

  @Test
  public void testBasicGraph() throws Exception {
    CoreMap sentence = readSentences(BASIC).get(0);
    SemanticGraph graph = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    assertNotNull(graph);
    assertEquals(3, graph.size());
    assertEquals(1, graph.getRoots().size());
    assertEquals(2, graph.getFirstRoot().index());
    assertEquals(sorted("discourse(mundo-2, Hola-1)", "punct(mundo-2, .-3)"),
                 describeEdges(graph));
  }

  @Test
  public void testNoEnhancedGraphWhenNoneGiven() throws Exception {
    for (CoreMap sentence : readSentences(BASIC)) {
      assertNull(sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class));
      assertNull(sentence.get(CoreAnnotations.EmptyTokensAnnotation.class));
    }
  }

  @Test
  public void testEnhancedGraph() throws Exception {
    CoreMap sentence = readSentences(ENHANCED).get(0);
    SemanticGraph enhanced = sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
    assertNotNull(enhanced);
    // the six words plus the empty word
    assertEquals(7, enhanced.size());
    assertEquals(sorted("nsubj(come-2, Ella-1)",
                        "obj(come-2, manzanas-3)",
                        "cc(peras-6, y-4)",
                        "nsubj(peras-6, él-5)",
                        "obj(come-6.1, peras-6)",
                        "conj(come-2, come-6.1)"),
                 describeEdges(enhanced));
    // the basic graph is built from the token lines alone, so it has no
    // empty word in it and uses the plain gov/reln columns
    SemanticGraph basic = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    assertEquals(6, basic.size());
    assertEquals(2, basic.getFirstRoot().index());
  }

  @Test
  public void testEmptyWords() throws Exception {
    CoreMap sentence = readSentences(ENHANCED).get(0);
    List<CoreLabel> empties = sentence.get(CoreAnnotations.EmptyTokensAnnotation.class);
    assertNotNull(empties);
    assertEquals(1, empties.size());
    CoreLabel empty = empties.get(0);
    assertEquals("come", empty.word());
    assertEquals(6, empty.index());
    assertEquals(1, (int) empty.get(CoreAnnotations.EmptyIndexAnnotation.class));
    // an empty word contributes no text, so it is not among the tokens
    assertEquals(6, tokens(sentence).size());
    assertEquals("Ella come manzanas y él peras",
                 sentence.get(CoreAnnotations.TextAnnotation.class));
  }

  // ------------------------------------------------------------------
  // multi word tokens
  // ------------------------------------------------------------------

  @Test
  public void testMWTText() throws Exception {
    CoreMap sentence = readSentences(MWT).get(0);
    // the MWT surface form, not its pieces, is what goes into the text
    assertEquals("Vamos al parque", sentence.get(CoreAnnotations.TextAnnotation.class));
    assertEquals(4, tokens(sentence).size());
  }

  @Test
  public void testMWTFlagsAndOffsets() throws Exception {
    List<CoreLabel> tokens = tokens(readSentences(MWT).get(0));
    CoreLabel vamos = tokens.get(0);
    CoreLabel a = tokens.get(1);
    CoreLabel el = tokens.get(2);
    CoreLabel parque = tokens.get(3);

    assertFalse(vamos.isMWT());
    assertTrue(a.isMWT());
    assertTrue(el.isMWT());
    assertFalse(parque.isMWT());

    assertTrue(a.isMWTFirst());
    assertFalse(el.isMWTFirst());

    assertEquals("al", a.get(CoreAnnotations.MWTTokenTextAnnotation.class));
    assertEquals("al", el.get(CoreAnnotations.MWTTokenTextAnnotation.class));

    // both pieces of the MWT carry the offsets of the MWT itself
    assertEquals(6, a.beginPosition());
    assertEquals(8, a.endPosition());
    assertEquals(6, el.beginPosition());
    assertEquals(8, el.endPosition());

    // only the last piece of an MWT gets the after
    assertEquals("", a.after());
    assertEquals(" ", el.after());

    assertEquals(9, parque.beginPosition());
    assertEquals(15, parque.endPosition());
  }

  @Test
  public void testMWTGraph() throws Exception {
    // the MWT line itself is not a node in the graph
    SemanticGraph graph = readSentences(MWT).get(0)
        .get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    assertEquals(4, graph.size());
    assertEquals(sorted("case(parque-4, a-2)", "det(parque-4, el-3)", "obl(Vamos-1, parque-4)"),
                 describeEdges(graph));
  }

  // ------------------------------------------------------------------
  // documents and extra columns
  // ------------------------------------------------------------------

  @Test
  public void testMultipleDocuments() throws Exception {
    // the first document deliberately has no newdoc line of its own: the
    // reader starts with a document already open, so a newdoc at the top
    // of the file leaves that one behind empty.  See
    // testLeadingNewdocLeavesNoEmptyDocument below.
    String text = String.join("\n",
        "1\tHola\thola\tINTJ\t_\t_\t0\troot\t_\t_",
        "",
        "# newdoc",
        "1\tAdiós\tadiós\tINTJ\t_\t_\t0\troot\t_\t_",
        "",
        "");
    List<Annotation> documents = readDocuments(text);
    assertEquals(2, documents.size());
    for (Annotation document : documents) {
      assertEquals(1, document.get(CoreAnnotations.SentencesAnnotation.class).size());
    }
    // each document gets its own text, and its own offsets starting at zero
    assertEquals("Hola ", documents.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("Adiós ", documents.get(1).get(CoreAnnotations.TextAnnotation.class));
    for (Annotation document : documents) {
      assertEquals(0, document.get(CoreAnnotations.TokensAnnotation.class).get(0).beginPosition());
    }
  }

  @Test
  public void testExtraColumn() throws Exception {
    String text = String.join("\n",
        "1\tJuan\tJuan\tPROPN\t_\t_\t0\troot\t_\t_\tPERSON",
        "",
        "");
    CoreLabel token = tokens(readSentences(text).get(0)).get(0);
    assertEquals("PERSON", token.ner());
  }

  @Test
  public void testMiscSurvivesRoundTrip() throws Exception {
    String text = String.join("\n",
        "1\tJuan\tJuan\tPROPN\t_\t_\t0\troot\t_\tGloss=John|SpaceAfter=No|Translit=Juan",
        "",
        "");
    CoreLabel token = tokens(readSentences(text).get(0)).get(0);
    // SpaceAfter is pulled out into the after; the rest is kept in order
    assertEquals("", token.after());
    assertEquals("Gloss=John|Translit=Juan", token.get(CoreAnnotations.CoNLLUMisc.class));
  }

  @Test
  public void testSpacesAfter() throws Exception {
    String text = String.join("\n",
        "1\tHola\thola\tINTJ\t_\t_\t0\troot\t_\tSpacesAfter=\\s\\s",
        "2\tmundo\tmundo\tNOUN\t_\t_\t1\tdep\t_\t_",
        "",
        "");
    List<CoreLabel> tokens = tokens(readSentences(text).get(0));
    assertEquals("  ", tokens.get(0).after());
    assertEquals("  ", tokens.get(1).before());
    assertEquals(6, tokens.get(1).beginPosition());
  }

  // ------------------------------------------------------------------
  // known issues: these describe what the reader should do, and currently
  // do not pass.  Remove the @Ignore along with the fix.
  // ------------------------------------------------------------------

  /**
   * DOCUMENT_LINE is used with matches(), not lookingAt(), so only a line
   * which is exactly "# newdoc" starts a new document.  The usual UD form,
   * with an id after it, is treated as an ordinary comment.
   */
  @Ignore("# newdoc with an id after it does not currently start a new document")
  @Test
  public void testNewdocWithId() throws Exception {
    String text = String.join("\n",
        "# newdoc id = first",
        "1\tHola\thola\tINTJ\t_\t_\t0\troot\t_\t_",
        "",
        "# newdoc id = second",
        "1\tAdiós\tadiós\tINTJ\t_\t_\t0\troot\t_\t_",
        "",
        "");
    assertEquals(2, readDocuments(text).size());
  }

  /**
   * readCoNLLUFileCreateCoNLLUDocuments opens a document before it reads
   * anything, so a newdoc line at the top of the file starts a second one
   * and leaves the first with no sentences in it.  This is invisible today
   * only because the newdoc line is not recognized in the first place;
   * fixing that makes every ordinary UD file come back with an empty
   * Annotation in front of it.
   */
  @Ignore("a newdoc at the top of the file leaves an empty document in front of it")
  @Test
  public void testLeadingNewdocLeavesNoEmptyDocument() throws Exception {
    String text = String.join("\n",
        "# newdoc",
        "1\tHola\thola\tINTJ\t_\t_\t0\troot\t_\t_",
        "",
        "# newdoc",
        "1\tAdiós\tadiós\tINTJ\t_\t_\t0\troot\t_\t_",
        "",
        "");
    assertEquals(2, readDocuments(text).size());
  }

  /**
   * The last sentence of a document is dropped if the file does not end
   * with a blank line, since readCoNLLUFileCreateCoNLLUDocuments removes
   * the trailing sentence whether or not anything was read into it.
   */
  @Ignore("a file with no trailing blank line loses its last sentence")
  @Test
  public void testNoTrailingBlankLine() throws Exception {
    String text = "1\tHola\thola\tINTJ\t_\t_\t0\troot\t_\t_\n";
    assertEquals(1, readSentences(text).size());
  }
}
