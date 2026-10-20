package edu.stanford.nlp.semgraph.semgrex;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.CoNLLUReader;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;

/**
 * Tests the ConlluEditor highlight comments SemgrexPattern writes with its CoNLL-U output.
 *
 * @author John Bauer
 */
public class SemgrexHighlightTest {

  /** "Sue likes coffee and Bill tea", where the enhanced graph has an empty "likes" for Bill */
  private static final String SENTENCE = String.join("\n",
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

  private static CoreMap readSentence() throws IOException {
    File file = File.createTempFile("semgrex", ".conllu");
    file.deleteOnExit();
    Files.write(file.toPath(), SENTENCE.getBytes(StandardCharsets.UTF_8));
    return new CoNLLUReader().readCoNLLUFile(file.getPath()).get(0).get(CoreAnnotations.SentencesAnnotation.class).get(0);
  }

  private static List<String> highlights(String pattern) throws IOException {
    List<Pair<CoreMap, List<SemgrexMatch>>> matches =
      SemgrexPattern.compile(pattern).matchSentences(Collections.singletonList(readSentence()), false);
    assertEquals(1, matches.size());
    return SemgrexPattern.highlightComments(matches.get(0).second());
  }

  @Test
  public void basic() throws IOException {
    assertEquals(Arrays.asList("# highlight tokens = 2 3", "# highlight deprels = 3"),
                 highlights("{}=v >obj=e {}=o"));
  }

  @Test
  public void emptyNodes() throws IOException {
    // both enhanced obj arcs, one of which starts at the empty word 5.1
    assertEquals(Arrays.asList("# highlight tokens = 2 3 5.1 6", "# highlight deprels = 3 6"),
                 highlights("{}=v >obj@enhanced=e {}=o"));
    // the conj arc points at the empty word, so its deprel is highlighted there
    assertEquals(Arrays.asList("# highlight tokens = 2 5.1", "# highlight deprels = 5.1"),
                 highlights("{}=v >/conj.*/@enhanced=e {}=c"));
  }

  @Test
  public void noNamedEdges() throws IOException {
    // without named edges there are no deprels to highlight
    assertEquals(Collections.singletonList("# highlight tokens = 2 3"),
                 highlights("{}=v >obj {}=o"));
  }
}
