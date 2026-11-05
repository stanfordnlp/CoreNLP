package edu.stanford.nlp.semgraph.semgrex;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.CoNLLUReader;
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
    List<String> lines = new java.util.ArrayList<>();
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
    // the words between the marked words are always kept
    assertEquals(Collections.singletonList("id\t... quickly [[ate]] the very big [[cake]] in ..."),
                 text(0, "{} >obj {}=o", 1, 1));
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

  @Test
  public void sentenceId() throws IOException {
    List<CoreMap> sentences = readSentences();
    assertEquals("long-1", SemgrexUtils.sentenceId(sentences.get(0)));
    assertNull(SemgrexUtils.sentenceId(sentences.get(1)));
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
