package edu.stanford.nlp.semgraph.semgrex;

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
import java.util.stream.Collectors;

import org.junit.Test;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.CoNLLUReader;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.ud.CoNLLUDocumentWriter;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;

/**
 * Tests the text SemgrexPattern writes for a match: the words of the
 * sentence with the match marked, and only some of them with -C, -A and -B.
 *
 * @author John Bauer
 */
public class SemgrexTextTest {

  /** A sentence with an id and enough words to cut, and one with an empty word and no id */
  private static final String SENTENCES = String.join("\n",
    "# sent_id = long-1",
    "# text = The old man quickly ate the very big cake in the kitchen .",
    "1\tThe\tthe\tDET\t_\t_\t3\tdet\t3:det\t_",
    "2\told\told\tADJ\t_\t_\t3\tamod\t3:amod\t_",
    "3\tman\tman\tNOUN\t_\t_\t5\tnsubj\t5:nsubj\t_",
    "4\tquickly\tquickly\tADV\t_\t_\t5\tadvmod\t5:advmod\t_",
    "5\tate\teat\tVERB\t_\t_\t0\troot\t0:root\t_",
    "6\tthe\tthe\tDET\t_\t_\t9\tdet\t9:det\t_",
    "7\tvery\tvery\tADV\t_\t_\t8\tadvmod\t8:advmod\t_",
    "8\tbig\tbig\tADJ\t_\t_\t9\tamod\t9:amod\t_",
    "9\tcake\tcake\tNOUN\t_\t_\t5\tobj\t5:obj\t_",
    "10\tin\tin\tADP\t_\t_\t12\tcase\t12:case\t_",
    "11\tthe\tthe\tDET\t_\t_\t12\tdet\t12:det\t_",
    "12\tkitchen\tkitchen\tNOUN\t_\t_\t5\tobl\t5:obl\t_",
    "13\t.\t.\tPUNCT\t_\t_\t5\tpunct\t5:punct\t_",
    "",
    "# text = Sue likes coffee and Bill tea",
    "1\tSue\tSue\tPROPN\t_\t_\t2\tnsubj\t2:nsubj\t_",
    "2\tlikes\tlike\tVERB\t_\t_\t0\troot\t0:root\t_",
    "3\tcoffee\tcoffee\tNOUN\t_\t_\t2\tobj\t2:obj\t_",
    "4\tand\tand\tCCONJ\t_\t_\t5\tcc\t5.1:cc\t_",
    "5\tBill\tBill\tPROPN\t_\t_\t2\tconj\t5.1:nsubj\t_",
    "5.1\tlikes\tlike\tVERB\t_\t_\t_\t_\t2:conj:and\t_",
    "6\ttea\ttea\tNOUN\t_\t_\t5\torphan\t5.1:obj\t_",
    "",
    "# sent_id = mwt-1",
    "# text = Fue al parque de la ciudad ayer .",
    "1\tFue\tir\tVERB\t_\t_\t0\troot\t0:root\t_",
    "2-3\tal\t_\t_\t_\t_\t_\t_\t_\t_",
    "2\ta\ta\tADP\t_\t_\t4\tcase\t4:case\t_",
    "3\tel\tel\tDET\t_\t_\t4\tdet\t4:det\t_",
    "4\tparque\tparque\tNOUN\t_\t_\t1\tobl\t1:obl\t_",
    "5\tde\tde\tADP\t_\t_\t7\tcase\t7:case\t_",
    "6\tla\tel\tDET\t_\t_\t7\tdet\t7:det\t_",
    "7\tciudad\tciudad\tNOUN\t_\t_\t4\tnmod\t4:nmod\t_",
    "8\tayer\tayer\tADV\t_\t_\t1\tadvmod\t1:advmod\t_",
    "9\t.\t.\tPUNCT\t_\t_\t1\tpunct\t1:punct\t_",
    "",
    "");

  private static List<CoreMap> readSentences() throws IOException {
    File file = File.createTempFile("semgrex", ".conllu");
    file.deleteOnExit();
    Files.write(file.toPath(), SENTENCES.getBytes(StandardCharsets.UTF_8));
    return new CoNLLUReader().readCoNLLUFile(file.getPath()).get(0).get(CoreAnnotations.SentencesAnnotation.class);
  }

  /** The text of each match of the pattern in one sentence */
  private static List<String> text(int sentence, String pattern, int before, int after) throws IOException {
    CoreMap read = readSentences().get(sentence);
    List<Pair<CoreMap, List<SemgrexMatch>>> matches =
      SemgrexPattern.compile(pattern).matchSentences(Collections.singletonList(read), false);
    assertEquals(1, matches.size());
    List<String> lines = new ArrayList<>();
    for (SemgrexMatch match : matches.get(0).second()) {
      lines.add(SemgrexPattern.matchText("id", read, match, before, after));
    }
    return lines;
  }

  @Test
  public void wholeSentence() throws IOException {
    assertEquals(Collections.singletonList("id\tThe old man quickly ate the very big [[cake]] in the kitchen ."),
                 text(0, "{word:cake}", -1, -1));
    // a named node is marked along with the word the match is at
    assertEquals(Collections.singletonList("id\tThe old man quickly [[ate]] the very big [[cake]] in the kitchen ."),
                 text(0, "{} >obj {}=o", -1, -1));
  }

  @Test
  public void context() throws IOException {
    assertEquals(Collections.singletonList("id\t... very big [[cake]] in the ..."),
                 text(0, "{word:cake}", 2, 2));
    assertEquals(Collections.singletonList("id\t... [[cake]] in ..."),
                 text(0, "{word:cake}", 0, 1));
    // each marked word has its own window, with ... between them
    assertEquals(Collections.singletonList("id\t... quickly [[ate]] the ... big [[cake]] in ..."),
                 text(0, "{} >obj {}=o", 1, 1));
    // windows which overlap are merged
    assertEquals(Collections.singletonList("id\t... man quickly [[ate]] the very big [[cake]] in the ..."),
                 text(0, "{} >obj {}=o", 2, 2));
    // as are windows which only touch, 4-6 and 7-9 here
    assertEquals(Collections.singletonList("id\t... quickly [[ate]] the very [[big]] cake ..."),
                 text(0, "{word:ate} >obj ({} >amod {}=a)", 1, 1));
    // no ... where the context reaches the end of the sentence
    assertEquals(Collections.singletonList("id\tThe old [[man]] quickly ..."),
                 text(0, "{word:man}", 5, 1));
    assertEquals(Collections.singletonList("id\t... the [[kitchen]] ."),
                 text(0, "{word:kitchen}", 1, 3));
  }

  @Test
  public void eachMatch() throws IOException {
    // a line per match, each with its own words marked
    assertEquals(Arrays.asList("id\t[[The]] old ...", "id\t... ate [[the]] very ...", "id\t... in [[the]] kitchen ..."),
                 text(0, "{lemma:the}", 1, 1));
  }

  @Test
  public void emptyWord() throws IOException {
    // an empty word is only in the text when it is marked, as it is not in the basic graph
    assertEquals(Collections.singletonList("id\tSue likes coffee and Bill [[likes]] [[tea]]"),
                 text(1, "{}=v >obj@enhanced {word:tea}=o", -1, -1));
    assertEquals(Collections.singletonList("id\tSue likes coffee and Bill [[tea]]"),
                 text(1, "{word:tea}", -1, -1));
  }

  /** Each marked word has its own window: 8-18 and 33-43 around 13 and 38 */
  @Test
  public void contextRanges() {
    List<IndexedWord> marked = Arrays.asList(word(13), word(38));
    assertEquals("[8-18, 33-43]", ranges(SemgrexUtils.contextRanges(marked, null, 5, 5)));
    assertEquals("[13-15, 38-40]", ranges(SemgrexUtils.contextRanges(marked, null, 0, 2)));
    // merged when they overlap, cut off at the start of the sentence
    assertEquals("[0-50]", ranges(SemgrexUtils.contextRanges(marked, null, 20, 12)));
    // the whole sentence, without context
    assertEquals("[0-" + Integer.MAX_VALUE + "]", ranges(SemgrexUtils.contextRanges(marked, null, -1, -1)));
    // everything before, so both windows start at the start and merge
    assertEquals("[0-43]", ranges(SemgrexUtils.contextRanges(marked, null, -1, 5)));
  }

  private static IndexedWord word(int index) {
    IndexedWord word = new IndexedWord(new CoreLabel());
    word.setIndex(index);
    return word;
  }

  private static String ranges(List<int[]> ranges) {
    return ranges.stream().map(range -> range[0] + "-" + range[1]).collect(Collectors.toList()).toString();
  }

  /** A window which would cut a multi-word token in half takes in the whole of it */
  @Test
  public void multiWordTokens() throws IOException {
    // one word before parque is el, the second word of al, so a is kept too
    assertEquals(Collections.singletonList("id\t... a el [[parque]] ..."), text(2, "{word:parque}", 1, 0));
    // one word after Fue is a, the first word of al, so el is kept too
    assertEquals(Collections.singletonList("id\t[[Fue]] a el ..."), text(2, "{word:Fue}", 0, 1));
    // a window which takes in all of it is unchanged
    assertEquals(Collections.singletonList("id\t... [[a]] el parque ..."), text(2, "{word:a}", 0, 2));
  }

  /**
   * In CoNLL-U, the comments are kept, with the rows of the words in the
   * windows, a range line with its words
   */
  @Test
  public void conlluContext() throws IOException {
    CoreMap sentence = readSentences().get(2);
    SemanticGraph graph = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    String conllu = new CoNLLUDocumentWriter().printSemanticGraph(graph, null, false,
                                                                 sentence.get(CoreAnnotations.CommentsAnnotation.class));
    List<IndexedWord> marked = Collections.singletonList(graph.getNodeByIndex(4));
    String cut = SemgrexPattern.conlluContext(conllu, SemgrexUtils.contextRanges(marked, graph, 1, 1));
    List<String> ids = new ArrayList<>();
    for (String line : cut.split("\\R")) {
      ids.add(line.startsWith("#") || line.isEmpty() ? line : line.split("\t")[0]);
    }
    assertEquals(Arrays.asList("# sent_id = mwt-1", "# text = Fue al parque de la ciudad ayer .", "2-3", "2", "3", "4", "5"), ids);
    assertTrue(cut, cut.endsWith(System.lineSeparator() + System.lineSeparator()));

    // with every word in a window, nothing is cut
    assertEquals(conllu, SemgrexPattern.conlluContext(conllu, SemgrexUtils.contextRanges(marked, graph, 5, 5)));
  }

  @Test
  public void sentenceId() throws IOException {
    List<CoreMap> sentences = readSentences();
    assertEquals("long-1", SemgrexUtils.sentenceId(sentences.get(0)));
    assertNull(SemgrexUtils.sentenceId(sentences.get(1)));
  }

  /** -A5 is -A 5, as in grep, and nothing else is split */
  @Test
  public void splitContextArgs() {
    assertArrayEquals(new String[] { "-A", "5", "-B", "12", "-C", "0", "-pattern", "{}" },
                      SemgrexPattern.splitContextArgs(new String[] { "-A5", "-B12", "-C0", "-pattern", "{}" }));
    assertArrayEquals(new String[] { "-A", "5", "-Ax", "-D5", "x-A5", "-A-5" },
                      SemgrexPattern.splitContextArgs(new String[] { "-A", "5", "-Ax", "-D5", "x-A5", "-A-5" }));
  }

  @Test
  public void contextWords() {
    Map<String, List<String>> args = new HashMap<>();
    assertEquals(-1, SemgrexPattern.contextWords(args, "-B"));
    args.put("-C", Collections.singletonList("3"));
    assertEquals(3, SemgrexPattern.contextWords(args, "-B"));
    assertEquals(3, SemgrexPattern.contextWords(args, "-A"));
    // -A and -B take precedence over -C on their side
    args.put("-A", Collections.singletonList("0"));
    assertEquals(3, SemgrexPattern.contextWords(args, "-B"));
    assertEquals(0, SemgrexPattern.contextWords(args, "-A"));

    args.put("-B", Collections.singletonList("-1"));
    assertThrows(IllegalArgumentException.class, () -> SemgrexPattern.contextWords(args, "-B"));
    args.put("-B", Collections.singletonList("five"));
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> SemgrexPattern.contextWords(args, "-B"));
    assertTrue(e.getMessage(), e.getMessage().startsWith("-B "));
  }
}
