package edu.stanford.nlp.pipeline;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
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
    return readDocuments(new CoNLLUReader(), conllu);
  }

  private static List<Annotation> readDocuments(CoNLLUReader reader, String conllu) throws Exception {
    File file = writeTempFile(conllu);
    try {
      return reader.readCoNLLUFile(file.getPath());
    } finally {
      file.delete();
    }
  }

  private static File writeTempFile(String conllu) throws Exception {
    File file = IOUtils.writeStringToTempFile(conllu, "conllutest", "UTF-8");
    file.deleteOnExit();
    return file;
  }

  /**
   * The CoNLLUDocuments, before they are turned into Annotations
   *<br>
   * sentenceData lives on the CoNLLUSentence and is not carried onto the
   * CoreMap, so a test of it has to read at this level.
   */
  private static List<CoNLLUReader.CoNLLUDocument> readRawDocuments(String conllu) throws Exception {
    File file = IOUtils.writeStringToTempFile(conllu, "conllutest", "UTF-8");
    file.deleteOnExit();
    try {
      return new CoNLLUReader().readCoNLLUFileCreateCoNLLUDocuments(file.getPath());
    } finally {
      file.delete();
    }
  }

  /** The sentences of text which is expected to hold exactly one document */
  private static List<CoreMap> readSentences(String conllu) throws Exception {
    return readSentences(new CoNLLUReader(), conllu);
  }

  private static List<CoreMap> readSentences(CoNLLUReader reader, String conllu) throws Exception {
    List<Annotation> documents = readDocuments(reader, conllu);
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

  @Test
  public void testDocumentsAndSentencesAreStandalone() {
    // the nested classes keep no reference to a reader, so they can be
    // built without one.  this will not compile if they stop being static
    CoNLLUReader.CoNLLUDocument document = new CoNLLUReader.CoNLLUDocument();
    assertTrue(document.isEmpty());
    assertFalse(document.lastSentence().hasWords());
    document.lastSentence().processLine("1\tHola\thola", LineType.TOKEN);
    assertFalse(document.isEmpty());
    assertTrue(document.lastSentence().hasWords());
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
  // counting what was read
  //
  // a sentence quietly going missing does not fail any test which only
  // looks at the sentences it does get back, so these count instead
  // ------------------------------------------------------------------

  /** One sentence: a sent_id and a chain of words hanging off the first */
  private static String sentenceLines(int sentenceNumber, int words) {
    StringBuilder lines = new StringBuilder();
    lines.append("# sent_id = ").append(sentenceNumber).append("\n");
    for (int i = 1; i <= words; i++) {
      lines.append(i).append("\tw").append(sentenceNumber).append("_").append(i);
      lines.append("\tw\tNOUN\t_\t_\t").append(i == 1 ? 0 : i - 1);
      lines.append(i == 1 ? "\troot" : "\tdep").append("\t_\t_\n");
    }
    return lines.toString();
  }

  /** Several sentences, each followed by the blank line which ends it */
  private static String sentenceBlock(int firstSentenceNumber, int count, int words) {
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < count; i++) {
      text.append(sentenceLines(firstSentenceNumber + i, words));
      text.append("\n");
    }
    return text.toString();
  }

  /** The same text with the blank line at the end of the file taken off */
  private static String withoutFinalBlankLine(String conllu) {
    return conllu.substring(0, conllu.length() - 1);
  }

  private static int countSentences(List<Annotation> documents) {
    int count = 0;
    for (Annotation document : documents) {
      count += document.get(CoreAnnotations.SentencesAnnotation.class).size();
    }
    return count;
  }

  private static int countTokens(List<Annotation> documents) {
    int count = 0;
    for (Annotation document : documents) {
      count += document.get(CoreAnnotations.TokensAnnotation.class).size();
    }
    return count;
  }

  /**
   * Every sentence written into the text comes back out of the reader.
   *<br>
   * The expected number is counted from the sent_id comments in the text
   * itself, the way a treebank names its sentences, rather than written
   * out beside it where it can drift away from the data.
   */
  private static void assertEverySentenceIsRead(String conllu) throws Exception {
    int expected = 0;
    for (String line : conllu.split("\n", -1)) {
      if (line.startsWith("# sent_id")) {
        expected++;
      }
    }
    assertEquals(expected, countSentences(readDocuments(conllu)));
  }

  @Test
  public void testCountsForAWholeFile() throws Exception {
    String text = sentenceBlock(1, 50, 7);
    List<Annotation> documents = readDocuments(text);
    assertEquals(1, documents.size());
    assertEquals(50, countSentences(documents));
    assertEquals(350, countTokens(documents));
    assertEverySentenceIsRead(text);
  }

  @Test
  public void testCountsWithNoBlankLineAtTheEnd() throws Exception {
    // a treebank which was hand edited, truncated, or built by
    // concatenation can be missing the blank line after its last
    // sentence.  that sentence is still a sentence
    String text = sentenceBlock(1, 50, 7);
    String truncated = withoutFinalBlankLine(text);
    assertEquals(50, countSentences(readDocuments(truncated)));
    assertEquals(350, countTokens(readDocuments(truncated)));
    assertEverySentenceIsRead(truncated);
    // and the two read to the same text, not merely the same counts
    assertEquals(readDocuments(text).get(0).get(CoreAnnotations.TextAnnotation.class),
                 readDocuments(truncated).get(0).get(CoreAnnotations.TextAnnotation.class));
  }

  @Test
  public void testDocumentAndSentenceCounts() throws Exception {
    StringBuilder text = new StringBuilder();
    int sentenceNumber = 1;
    for (int doc = 1; doc <= 4; doc++) {
      text.append("# newdoc id = doc").append(doc).append("\n");
      text.append(sentenceBlock(sentenceNumber, 3, 5));
      sentenceNumber += 3;
    }
    List<Annotation> documents = readDocuments(text.toString());
    assertEquals(4, documents.size());
    for (Annotation document : documents) {
      assertEquals(3, document.get(CoreAnnotations.SentencesAnnotation.class).size());
      assertEquals(15, document.get(CoreAnnotations.TokensAnnotation.class).size());
    }
    assertEquals(12, countSentences(documents));
    assertEverySentenceIsRead(text.toString());
  }

  @Test
  public void testDocumentAndSentenceCountsWithNoBlankLineAtTheEnd() throws Exception {
    StringBuilder text = new StringBuilder();
    int sentenceNumber = 1;
    for (int doc = 1; doc <= 4; doc++) {
      text.append("# newdoc id = doc").append(doc).append("\n");
      text.append(sentenceBlock(sentenceNumber, 3, 5));
      sentenceNumber += 3;
    }
    String truncated = withoutFinalBlankLine(text.toString());
    List<Annotation> documents = readDocuments(truncated);
    assertEquals(4, documents.size());
    assertEquals(12, countSentences(documents));
    assertEquals(60, countTokens(documents));
    assertEverySentenceIsRead(truncated);
  }

  @Test
  public void testCountsWithStrayBlankLines() throws Exception {
    // extra blank lines between sentences, and around a newdoc, are not
    // sentences of their own
    String text = sentenceBlock(1, 3, 4) + "\n\n" +
                  "# newdoc id = second\n" + "\n" +
                  sentenceBlock(4, 2, 4) + "\n";
    List<Annotation> documents = readDocuments(text);
    assertEquals(2, documents.size());
    assertEquals(3, documents.get(0).get(CoreAnnotations.SentencesAnnotation.class).size());
    assertEquals(2, documents.get(1).get(CoreAnnotations.SentencesAnnotation.class).size());
    assertEquals(5, countSentences(documents));
    assertEquals(20, countTokens(documents));
    assertEverySentenceIsRead(text);
  }

  @Test
  public void testCountsWithMultiWordTokens() throws Exception {
    // the MWT lines are not words, so they are not counted as tokens
    String text = String.join("\n",
        "# sent_id = 1",
        "1\tVamos\tir\tVERB\t_\t_\t0\troot\t_\t_",
        "2-3\tal\t_\t_\t_\t_\t_\t_\t_\t_",
        "2\ta\ta\tADP\t_\t_\t4\tcase\t_\t_",
        "3\tel\tel\tDET\t_\t_\t4\tdet\t_\t_",
        "4\tparque\tparque\tNOUN\t_\t_\t1\tobl\t_\t_",
        "",
        "# sent_id = 2",
        "1\tHola\thola\tINTJ\t_\t_\t0\troot\t_\t_",
        "",
        "");
    List<Annotation> documents = readDocuments(text);
    assertEquals(1, documents.size());
    assertEquals(2, countSentences(documents));
    assertEquals(5, countTokens(documents));
    assertEverySentenceIsRead(text);
    assertEverySentenceIsRead(withoutFinalBlankLine(text));
  }

  // ------------------------------------------------------------------
  // reading one sentence at a time
  // ------------------------------------------------------------------

  /** Two documents with an MWT, an empty word, enhanced arcs and odd spacing in them */
  private static final String RICH = String.join("\n",
      "# newdoc id = first",
      "# sent_id = 1",
      "# text = Vamos al parque.",
      "1\tVamos\tir\tVERB\tVMIP1P0\tNumber=Plur\t0\troot\t0:root\t_",
      "2-3\tal\t_\t_\t_\t_\t_\t_\t_\tGloss=to+the",
      "2\ta\ta\tADP\tSPS00\t_\t4\tcase\t4:case\t_",
      "3\tel\tel\tDET\tDA0MS0\tNumber=Sing\t4\tdet\t4:det\t_",
      "4\tparque\tparque\tNOUN\tNCMS000\tNumber=Sing\t1\tobl\t1:obl\tSpaceAfter=No",
      "5\t.\t.\tPUNCT\tFp\t_\t1\tpunct\t1:punct\t_",
      "",
      "",
      "# sent_id = 2",
      "1\tElla\tella\tPRON\t_\t_\t2\tnsubj\t2:nsubj\tSpacesAfter=\\s\\s",
      "2\tcome\tcomer\tVERB\t_\t_\t0\troot\t0:root\t_",
      "3\tperas\tpera\tNOUN\t_\t_\t2\tobj\t3.1:obj\tSpaceAfter=No",
      "3.1\tcome\tcomer\tVERB\t_\t_\t_\t_\t2:conj\t_",
      "",
      "# newdoc id = second",
      "# sent_id = 3",
      "1\tHola\thola\tINTJ\t_\t_\t0\troot\t_\t_",
      "2\tmundo\tmundo\tNOUN\t_\t_\t1\tdep\t_\t_",
      "",
      "");

  /** Everything a sentence carries, rendered so two readings can be compared */
  private static String describeSentenceFully(CoreMap sentence) {
    StringBuilder described = new StringBuilder();
    described.append("text |").append(sentence.get(CoreAnnotations.TextAnnotation.class)).append("|\n");
    described.append("index ").append(sentence.get(CoreAnnotations.SentenceIndexAnnotation.class)).append("\n");
    described.append("comments ").append(sentence.get(CoreAnnotations.CommentsAnnotation.class)).append("\n");
    for (CoreLabel token : tokens(sentence)) {
      described.append("token ").append(describeTokenFully(token)).append("\n");
    }
    List<CoreLabel> empties = sentence.get(CoreAnnotations.EmptyTokensAnnotation.class);
    if (empties != null) {
      for (CoreLabel empty : empties) {
        described.append("empty ").append(describeTokenFully(empty)).append("\n");
      }
    }
    described.append("basic ").append(describeEdges(
        sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class))).append("\n");
    SemanticGraph enhanced = sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
    described.append("enhanced ").append(enhanced == null ? "none" : describeEdges(enhanced)).append("\n");
    return described.toString();
  }

  private static String describeTokenFully(CoreLabel token) {
    return token.index() + " " + token.word() + " " + token.lemma() + " " + token.tag() + " " +
        token.get(CoreAnnotations.CoarseTagAnnotation.class) +
        " before |" + token.before() + "| after |" + token.after() + "|" +
        " chars " + token.beginPosition() + "-" + token.endPosition() +
        " tokens " + token.get(CoreAnnotations.TokenBeginAnnotation.class) +
        "-" + token.get(CoreAnnotations.TokenEndAnnotation.class) +
        " line " + token.get(CoreAnnotations.LineNumberAnnotation.class) +
        " sentence " + token.get(CoreAnnotations.SentenceIndexAnnotation.class) +
        " empty " + token.get(CoreAnnotations.EmptyIndexAnnotation.class) +
        " mwt " + token.isMWT() + " first " + token.isMWTFirst() +
        " mwtText " + token.get(CoreAnnotations.MWTTokenTextAnnotation.class) +
        " mwtMisc " + token.get(CoreAnnotations.MWTTokenMiscAnnotation.class) +
        " misc " + token.get(CoreAnnotations.CoNLLUMisc.class) +
        " feats " + token.get(CoreAnnotations.CoNLLUFeats.class);
  }

  private static List<String> describeByReadingWholeFile(String conllu) throws Exception {
    List<String> described = new ArrayList<>();
    for (Annotation document : readDocuments(conllu)) {
      for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
        described.add(describeSentenceFully(sentence));
      }
    }
    return described;
  }

  private static List<String> describeByStreaming(String conllu) throws Exception {
    File file = writeTempFile(conllu);
    List<String> described = new ArrayList<>();
    try (CoNLLUReader.SentenceIterator sentences = new CoNLLUReader().sentenceIterator(file.getPath())) {
      while (sentences.hasNext()) {
        described.add(describeSentenceFully(sentences.next()));
      }
    } finally {
      file.delete();
    }
    return described;
  }

  @Test
  public void testStreamingMatchesReadingTheWholeFile() throws Exception {
    // the sentences have to be identical down to the character offsets and
    // the document wide token indices, or the two ways of reading a file
    // are not two ways of reading the same file
    assertEquals(describeByReadingWholeFile(RICH), describeByStreaming(RICH));
  }

  @Test
  public void testStreamingMatchesWithNoBlankLineAtTheEnd() throws Exception {
    String truncated = withoutFinalBlankLine(RICH);
    assertEquals(describeByReadingWholeFile(truncated), describeByStreaming(truncated));
  }

  @Test
  public void testStreamingMatchesAWholeTreebank() throws Exception {
    String text = sentenceBlock(1, 50, 7);
    assertEquals(50, describeByStreaming(text).size());
    assertEquals(describeByReadingWholeFile(text), describeByStreaming(text));
  }

  @Test
  public void testStreamingMatchesWithADanglingComment() throws Exception {
    // a comment after the last sentence has no words to go with, so it is
    // not a sentence, either way the file is read
    String text = sentenceBlock(1, 2, 3) + "# newdoc id = second\n";
    assertEquals(2, describeByStreaming(text).size());
    assertEquals(describeByReadingWholeFile(text), describeByStreaming(text));
  }

  @Test
  public void testStreamingClosedBeforeItRunsOut() throws Exception {
    // the case Closeable is here for: a caller which stops reading partway
    // through and closes the iterator itself.  no try with resources,
    // since the point is the explicit close
    File file = writeTempFile(sentenceBlock(1, 50, 7));
    try {
      CoNLLUReader.SentenceIterator sentences = new CoNLLUReader().sentenceIterator(file.getPath());
      assertNotNull(sentences.next());
      sentences.close();
      // an iterator which has been closed has no more sentences to give,
      // and closing it again is not an error
      assertFalse(sentences.hasNext());
      sentences.close();
    } finally {
      file.delete();
    }
  }

  @Test
  public void testStreamingAnEmptyFile() throws Exception {
    assertEquals(Collections.emptyList(), describeByStreaming(""));
    assertEquals(Collections.emptyList(), describeByStreaming("\n\n\n"));
  }

  @Test
  public void testStreamingRunsOut() throws Exception {
    File file = writeTempFile(sentenceBlock(1, 2, 3));
    try (CoNLLUReader.SentenceIterator sentences = new CoNLLUReader().sentenceIterator(file.getPath())) {
      assertTrue(sentences.hasNext());
      sentences.next();
      assertTrue(sentences.hasNext());
      sentences.next();
      assertFalse(sentences.hasNext());
      try {
        sentences.next();
        fail("Expected the iterator to be out of sentences");
      } catch (NoSuchElementException e) {
        // expected
      }
    } finally {
      file.delete();
    }
  }

  // ------------------------------------------------------------------
  // line numbers
  // ------------------------------------------------------------------

  /** The line number of every token of every sentence, in order */
  private static List<Integer> lineNumbers(List<CoreMap> sentences) {
    List<Integer> found = new ArrayList<>();
    for (CoreMap sentence : sentences) {
      for (CoreLabel token : tokens(sentence)) {
        found.add(token.get(CoreAnnotations.LineNumberAnnotation.class));
      }
    }
    return found;
  }

  @Test
  public void testLineNumbers() throws Exception {
    // written out with the line each word sits on, counting from 1 the way
    // an editor does, so that the expected numbers can be read off
    String text = String.join("\n",
        /*  1 */ "# sent_id = 1",
        /*  2 */ "# text = Hola mundo.",
        /*  3 */ "1\tHola\thola\tINTJ\t_\t_\t2\tdiscourse\t_\t_",
        /*  4 */ "2\tmundo\tmundo\tNOUN\t_\t_\t0\troot\t_\tSpaceAfter=No",
        /*  5 */ "3\t.\t.\tPUNCT\t_\t_\t2\tpunct\t_\t_",
        /*  6 */ "",
        /*  7 */ "# sent_id = 2",
        /*  8 */ "1\tAdiós\tadiós\tINTJ\t_\t_\t0\troot\t_\t_",
        /*  9 */ "",
                 "");
    assertEquals(java.util.Arrays.asList(3, 4, 5, 8), lineNumbers(readSentences(text)));
  }

  @Test
  public void testLineNumbersCountBlankLinesAndComments() throws Exception {
    // the extra blank lines and the comment are still lines of the file,
    // so the words after them move down
    String text = String.join("\n",
        /*  1 */ "1\tHola\thola\tINTJ\t_\t_\t0\troot\t_\t_",
        /*  2 */ "",
        /*  3 */ "",
        /*  4 */ "",
        /*  5 */ "# sent_id = 2",
        /*  6 */ "1\tAdiós\tadiós\tINTJ\t_\t_\t0\troot\t_\t_",
        /*  7 */ "",
                 "");
    assertEquals(java.util.Arrays.asList(1, 6), lineNumbers(readSentences(text)));
  }

  @Test
  public void testLineNumbersWithMWTAndEmptyWords() throws Exception {
    // the MWT line is not a word and gets no CoreLabel, but it does take
    // up a line, so the words after it are numbered past it
    String text = String.join("\n",
        /*  1 */ "1\tElla\tella\tPRON\t_\t_\t2\tnsubj\t2:nsubj\t_",
        /*  2 */ "2\tcome\tcomer\tVERB\t_\t_\t0\troot\t0:root\t_",
        /*  3 */ "3-4\tdel\t_\t_\t_\t_\t_\t_\t_\t_",
        /*  4 */ "3\tde\tde\tADP\t_\t_\t5\tcase\t5:case\t_",
        /*  5 */ "4\tel\tel\tDET\t_\t_\t5\tdet\t5:det\t_",
        /*  6 */ "5\tplato\tplato\tNOUN\t_\t_\t2\tobl\t2:obl\tSpaceAfter=No",
        /*  7 */ "5.1\tcome\tcomer\tVERB\t_\t_\t_\t_\t2:conj\t_",
        /*  8 */ "",
                 "");
    List<CoreMap> sentences = readSentences(text);
    assertEquals(java.util.Arrays.asList(1, 2, 4, 5, 6), lineNumbers(sentences));
    // the empty word is numbered too, though it is not among the tokens
    List<CoreLabel> empties = sentences.get(0).get(CoreAnnotations.EmptyTokensAnnotation.class);
    assertEquals(1, empties.size());
    assertEquals(Integer.valueOf(7), empties.get(0).get(CoreAnnotations.LineNumberAnnotation.class));
  }

  @Test
  public void testLineNumbersRestartForEachDocument() throws Exception {
    // the number is the line of the file, not of the document
    String text = String.join("\n",
        /*  1 */ "# newdoc id = first",
        /*  2 */ "1\tHola\thola\tINTJ\t_\t_\t0\troot\t_\t_",
        /*  3 */ "",
        /*  4 */ "# newdoc id = second",
        /*  5 */ "1\tAdiós\tadiós\tINTJ\t_\t_\t0\troot\t_\t_",
        /*  6 */ "",
                 "");
    List<Annotation> documents = readDocuments(text);
    assertEquals(2, documents.size());
    assertEquals(Integer.valueOf(2), documents.get(0).get(CoreAnnotations.TokensAnnotation.class)
                 .get(0).get(CoreAnnotations.LineNumberAnnotation.class));
    assertEquals(Integer.valueOf(5), documents.get(1).get(CoreAnnotations.TokensAnnotation.class)
                 .get(0).get(CoreAnnotations.LineNumberAnnotation.class));
  }

  @Test
  public void testNoLineNumberWhenThereIsNoneToGive() throws Exception {
    // a sentence built by hand has no file to have come from
    CoNLLUReader.CoNLLUDocument document = new CoNLLUReader.CoNLLUDocument();
    document.lastSentence().processLine("1\tHola\thola\tINTJ\t_\t_\t0\troot\t_\t_", LineType.TOKEN);
    CoreLabel token = new CoNLLUReader().convertCoNLLUSentenceToCoreMap(document, document.lastSentence(), 0)
        .get(CoreAnnotations.TokensAnnotation.class).get(0);
    assertNull(token.get(CoreAnnotations.LineNumberAnnotation.class));
  }

  @Test
  public void testMalformedLineNamesItsLineNumber() throws Exception {
    String message = messageFromReading(String.join("\n",
        "# sent_id = broken-1",
        "1\tHola\thola\tINTJ\t_\t_\t0\troot\t_\t_",
        "2\tmundo",
        "",
        ""));
    assertTrue(message, message.contains("line 3"));
  }

  // ------------------------------------------------------------------
  // malformed lines say what is wrong with them
  // ------------------------------------------------------------------

  /** The message of the IllegalArgumentException the text provokes */
  private static String messageFromReading(String conllu) throws Exception {
    try {
      readSentences(conllu);
    } catch (IllegalArgumentException e) {
      return e.getMessage();
    }
    fail("Expected the text to be rejected");
    return null;
  }

  @Test
  public void testShortTokenLine() throws Exception {
    // a line which starts like a token but stops early used to fall over
    // on whichever column it ran out of
    String message = messageFromReading(String.join("\n",
        "# sent_id = broken-1",
        "1\tHola\thola",
        "",
        ""));
    assertTrue(message, message.contains("3 columns"));
    assertTrue(message, message.contains("broken-1"));
    assertTrue(message, message.contains("1\\tHola\\thola"));
  }

  @Test
  public void testShortMWTLine() throws Exception {
    String message = messageFromReading(String.join("\n",
        "# sent_id = broken-2",
        "1\tVamos\tir\tVERB\t_\t_\t0\troot\t_\t_",
        "2-3\tal",
        "2\ta\ta\tADP\t_\t_\t1\tcase\t_\t_",
        "3\tel\tel\tDET\t_\t_\t1\tdet\t_\t_",
        "",
        ""));
    assertTrue(message, message.contains("2 columns"));
    assertTrue(message, message.contains("broken-2"));
  }

  @Test
  public void testBasicHeadWhichIsNotAWord() throws Exception {
    // the same check the enhanced graph does, which the basic graph used
    // to skip: the edge was built with a null governor
    String message = messageFromReading(String.join("\n",
        "# sent_id = broken-3",
        "1\tHola\thola\tINTJ\t_\t_\t9\tdep\t_\t_",
        "2\tmundo\tmundo\tNOUN\t_\t_\t0\troot\t_\t_",
        "",
        ""));
    assertTrue(message, message.contains("head 9"));
    assertTrue(message, message.contains("broken-3"));
  }

  @Test
  public void testSentenceWithNoSentIdIsStillNamed() throws Exception {
    String message = messageFromReading("1\tHola\thola\n\n");
    assertTrue(message, message.contains("no sent_id"));
  }

  // ------------------------------------------------------------------
  // the reader's own state cannot be scribbled on
  // ------------------------------------------------------------------

  @Test
  public void testClassShorthandsAreReadOnly() {
    try {
      CoNLLUReader.classShorthandToFull.put("Nope", "com.example.");
      fail("Expected the shorthands to be unmodifiable");
    } catch (UnsupportedOperationException e) {
      // expected
    }
  }

  @Test
  public void testExtraColumnMustBeACoreAnnotation() throws Exception {
    Properties props = new Properties();
    props.setProperty("conllu.extraColumns", "java.lang.String");
    try {
      new CoNLLUReader(props);
      fail("Expected a class which is not a CoreAnnotation to be rejected");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage(), e.getMessage().contains("not a CoreAnnotation"));
    }
  }

  // ------------------------------------------------------------------
  // sentence data from the comments
  // ------------------------------------------------------------------

  @Test
  public void testSentenceData() throws Exception {
    String text = String.join("\n",
        "# sent_id = weblog-0003",
        "# text = Hola mundo.",
        "# a comment with no equals sign in it",
        "1\tHola\thola\tINTJ\t_\t_\t0\troot\t_\t_",
        "",
        "");
    CoNLLUReader.CoNLLUSentence sentence = readRawDocuments(text).get(0).sentences.get(0);
    // neither the # nor the spaces around the = belong to the key or the value
    assertEquals("weblog-0003", sentence.sentenceData.get("sent_id"));
    assertEquals("Hola mundo.", sentence.sentenceData.get("text"));
    // a comment with no = has no key and no value, but is still a comment
    assertEquals(2, sentence.sentenceData.size());
    assertEquals(3, sentence.comments.size());
  }

  @Test
  public void testSentenceDataValueWithAnEqualsSign() throws Exception {
    // only the first = separates the key from the value
    String text = String.join("\n",
        "# text = f(x) = y",
        "1\tHola\thola\tINTJ\t_\t_\t0\troot\t_\t_",
        "",
        "");
    CoNLLUReader.CoNLLUSentence sentence = readRawDocuments(text).get(0).sentences.get(0);
    assertEquals("f(x) = y", sentence.sentenceData.get("text"));
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

  @Test
  public void testPartiallyEnhanced() throws Exception {
    // manzanas has no enhanced dependency of its own.  the rest of the
    // sentence still gets an enhanced graph, and manzanas is simply not
    // attached in it
    String text = String.join("\n",
        "1\tElla\tella\tPRON\t_\t_\t2\tnsubj\t2:nsubj\t_",
        "2\tcome\tcomer\tVERB\t_\t_\t0\troot\t0:root\t_",
        "3\tmanzanas\tmanzana\tNOUN\t_\t_\t2\tobj\t_\tSpaceAfter=No",
        "",
        "");
    CoreMap sentence = readSentences(text).get(0);
    SemanticGraph enhanced = sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
    assertNotNull(enhanced);
    assertEquals(sorted("nsubj(come-2, Ella-1)"), describeEdges(enhanced));
    assertEquals(1, enhanced.getRoots().size());
    assertEquals(2, enhanced.getFirstRoot().index());

    // the word is still a token, and the basic graph is untouched by any
    // of this
    assertEquals(3, tokens(sentence).size());
    SemanticGraph basic = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    assertEquals(3, basic.size());
    assertEquals(sorted("nsubj(come-2, Ella-1)", "obj(come-2, manzanas-3)"), describeEdges(basic));
  }

  @Test
  public void testEnhancedHeadWhichIsNotAWord() throws Exception {
    String text = String.join("\n",
        "1\tElla\tella\tPRON\t_\t_\t2\tnsubj\t9:nsubj\t_",
        "2\tcome\tcomer\tVERB\t_\t_\t0\troot\t0:root\tSpaceAfter=No",
        "",
        "");
    try {
      readSentences(text);
      fail("Expected a dangling enhanced head to be reported");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("9:nsubj"));
    }
  }

  @Test
  public void testEnhancedArcWithNoRelation() throws Exception {
    String text = String.join("\n",
        "1\tElla\tella\tPRON\t_\t_\t2\tnsubj\t2\t_",
        "2\tcome\tcomer\tVERB\t_\t_\t0\troot\t0:root\tSpaceAfter=No",
        "",
        "");
    try {
      readSentences(text);
      fail("Expected an enhanced dependency with no relation to be reported");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("expected a head and a relation"));
    }
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
  // extra columns and misc
  // ------------------------------------------------------------------

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
  public void testDefaultExtraColumnCount() throws Exception {
    assertEquals(11, new CoNLLUReader().columnCount);
  }

  @Test
  public void testSeveralExtraColumns() throws Exception {
    // every named column gets its own index, not just the last one
    Properties props = new Properties();
    props.setProperty("conllu.extraColumns",
                      "CoreAnnotations.NamedEntityTagAnnotation,CoreAnnotations.TrueCaseAnnotation");
    CoNLLUReader reader = new CoNLLUReader(props);
    assertEquals(12, reader.columnCount);

    String text = String.join("\n",
        "1\tJuan\tJuan\tPROPN\t_\t_\t0\troot\t_\t_\tPERSON\tINIT_UPPER",
        "",
        "");
    CoreLabel token = tokens(readSentences(reader, text).get(0)).get(0);
    assertEquals("PERSON", token.ner());
    assertEquals("INIT_UPPER", token.get(CoreAnnotations.TrueCaseAnnotation.class));
  }

  @Test
  public void testExtraColumnsIgnoreSpaces() throws Exception {
    Properties props = new Properties();
    props.setProperty("conllu.extraColumns",
                      "CoreAnnotations.NamedEntityTagAnnotation, CoreAnnotations.CategoryAnnotation");
    assertEquals(12, new CoNLLUReader(props).columnCount);
  }

  @Test
  public void testFindExtraColumnClass() throws Exception {
    // the shorthand names a class nested in CoreAnnotations
    assertEquals(CoreAnnotations.TrueCaseAnnotation.class,
                 CoNLLUReader.findExtraColumnClass("CoreAnnotations.TrueCaseAnnotation"));
    // a full class name works written either way a person might write it
    assertEquals(CoreAnnotations.TrueCaseAnnotation.class,
                 CoNLLUReader.findExtraColumnClass("edu.stanford.nlp.ling.CoreAnnotations$TrueCaseAnnotation"));
    assertEquals(CoreAnnotations.TrueCaseAnnotation.class,
                 CoNLLUReader.findExtraColumnClass("edu.stanford.nlp.ling.CoreAnnotations.TrueCaseAnnotation"));
  }

  @Test
  public void testUnknownExtraColumnClass() throws Exception {
    Properties props = new Properties();
    props.setProperty("conllu.extraColumns", "CoreAnnotations.NoSuchAnnotation");
    try {
      new CoNLLUReader(props);
      fail("Expected an unknown annotation to be reported");
    } catch (ClassNotFoundException e) {
      // expected
    }
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
  // documents
  // ------------------------------------------------------------------

  @Test
  public void testNewdocWithId() throws Exception {
    // the form an actual UD file uses
    String text = String.join("\n",
        "# newdoc id = first",
        "# sent_id = first-1",
        "1\tHola\thola\tINTJ\t_\t_\t0\troot\t_\t_",
        "",
        "# newdoc id = second",
        "# sent_id = second-1",
        "1\tAdiós\tadiós\tINTJ\t_\t_\t0\troot\t_\t_",
        "",
        "");
    List<Annotation> documents = readDocuments(text);
    assertEquals(2, documents.size());
    for (Annotation document : documents) {
      assertEquals(1, document.get(CoreAnnotations.SentencesAnnotation.class).size());
    }
    assertEquals("Hola ", documents.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("Adiós ", documents.get(1).get(CoreAnnotations.TextAnnotation.class));
  }

  @Test
  public void testLeadingNewdocLeavesNoEmptyDocument() throws Exception {
    // a newdoc on the first line names the document about to be read, and
    // does not ask for an empty one in front of it
    String text = String.join("\n",
        "# newdoc",
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
      assertEquals(0, document.get(CoreAnnotations.TokensAnnotation.class).get(0).beginPosition());
    }
  }

  @Test
  public void testNewdocKeepsItsComment() throws Exception {
    String text = String.join("\n",
        "# newdoc id = first",
        "1\tHola\thola\tINTJ\t_\t_\t0\troot\t_\t_",
        "",
        "");
    CoreMap sentence = readSentences(text).get(0);
    assertEquals(Collections.singletonList("# newdoc id = first"),
                 sentence.get(CoreAnnotations.CommentsAnnotation.class));
  }

  @Test
  public void testNewdocNeedsAWordBoundary() throws Exception {
    // a comment which merely starts with the letters of newdoc is a
    // comment, not the start of a document
    String text = String.join("\n",
        "1\tHola\thola\tINTJ\t_\t_\t0\troot\t_\t_",
        "",
        "# newdocument of some kind",
        "1\tAdiós\tadiós\tINTJ\t_\t_\t0\troot\t_\t_",
        "",
        "");
    assertEquals(1, readDocuments(text).size());
    assertEquals(2, readSentences(text).size());
  }

  @Test
  public void testNewdocAfterAnUnterminatedSentence() throws Exception {
    // no blank line before the newdoc, so the sentence before it has no
    // trailing empty sentence to drop and must not be dropped itself
    String text = String.join("\n",
        "1\tHola\thola\tINTJ\t_\t_\t0\troot\t_\t_",
        "# newdoc id = second",
        "1\tAdiós\tadiós\tINTJ\t_\t_\t0\troot\t_\t_",
        "",
        "");
    List<Annotation> documents = readDocuments(text);
    assertEquals(2, documents.size());
    assertEquals("Hola ", documents.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("Adiós ", documents.get(1).get(CoreAnnotations.TextAnnotation.class));
  }

  @Test
  public void testNoTrailingBlankLine() throws Exception {
    // the last sentence of a file which stops without a blank line is real
    // and is kept
    String text = "1\tHola\thola\tINTJ\t_\t_\t0\troot\t_\t_\n";
    List<CoreMap> sentences = readSentences(text);
    assertEquals(1, sentences.size());
    assertEquals("Hola", sentences.get(0).get(CoreAnnotations.TextAnnotation.class));
  }

  // ------------------------------------------------------------------
  // blank lines and stray comments
  // ------------------------------------------------------------------

  @Test
  public void testDoubledBlankLine() throws Exception {
    // a second blank line does not start an extra sentence with no words
    // in it, which nothing downstream is prepared for
    String text = String.join("\n",
        "1\tHola\thola\tINTJ\t_\t_\t0\troot\t_\tSpaceAfter=No",
        "",
        "",
        "1\tAdiós\tadiós\tINTJ\t_\t_\t0\troot\t_\t_",
        "",
        "");
    List<CoreMap> sentences = readSentences(text);
    assertEquals(2, sentences.size());
    assertEquals("Hola", sentences.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("Adiós", sentences.get(1).get(CoreAnnotations.TextAnnotation.class));
    // the handoff between the sentences still happens, and the blank lines
    // add nothing to the text: Hola has SpaceAfter=No, so Adiós starts at
    // 4, immediately after it
    assertEquals("", tokens(sentences.get(1)).get(0).before());
    assertEquals(4, tokens(sentences.get(1)).get(0).beginPosition());
    assertEquals("HolaAdiós ",
                 readDocuments(text).get(0).get(CoreAnnotations.TextAnnotation.class));
  }

  @Test
  public void testManyBlankLines() throws Exception {
    String text = String.join("\n",
        "",
        "",
        "1\tHola\thola\tINTJ\t_\t_\t0\troot\t_\t_",
        "",
        "",
        "",
        "1\tAdiós\tadiós\tINTJ\t_\t_\t0\troot\t_\t_",
        "",
        "",
        "");
    List<CoreMap> sentences = readSentences(text);
    assertEquals(2, sentences.size());
    assertEquals(1, tokens(sentences.get(0)).size());
    assertEquals(1, tokens(sentences.get(1)).size());
  }

  @Test
  public void testCommentsWithNoWordsAfterThem() throws Exception {
    // a header comment cut off by a blank line is not a sentence of its
    // own; it belongs to the next sentence which does have words
    String text = String.join("\n",
        "# header",
        "",
        "# sent_id = 1",
        "1\tHola\thola\tINTJ\t_\t_\t0\troot\t_\t_",
        "",
        "");
    List<CoreMap> sentences = readSentences(text);
    assertEquals(1, sentences.size());
    assertEquals(java.util.Arrays.asList("# header", "# sent_id = 1"),
                 sentences.get(0).get(CoreAnnotations.CommentsAnnotation.class));
  }

  @Test
  public void testBlankLinesAroundNewdoc() throws Exception {
    String text = String.join("\n",
        "1\tHola\thola\tINTJ\t_\t_\t0\troot\t_\t_",
        "",
        "",
        "# newdoc id = second",
        "",
        "1\tAdiós\tadiós\tINTJ\t_\t_\t0\troot\t_\t_",
        "",
        "");
    List<Annotation> documents = readDocuments(text);
    assertEquals(2, documents.size());
    assertEquals("Hola ", documents.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("Adiós ", documents.get(1).get(CoreAnnotations.TextAnnotation.class));
  }

  @Test
  public void testNothingButBlankLines() throws Exception {
    List<Annotation> documents = readDocuments("\n\n\n");
    assertEquals(1, documents.size());
    assertEquals(0, documents.get(0).get(CoreAnnotations.SentencesAnnotation.class).size());
  }

  @Test
  public void testEmptyFile() throws Exception {
    List<Annotation> documents = readDocuments("");
    assertEquals(1, documents.size());
    assertEquals(0, documents.get(0).get(CoreAnnotations.SentencesAnnotation.class).size());
  }
}
