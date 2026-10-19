package edu.stanford.nlp.semgraph.semgrex;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

/** Tests the command line helpers in {@link SemgrexUtils} */
public class SemgrexUtilsTest {

  private static final Set<String> SINGLE = new HashSet<>(Arrays.asList("-pattern", "-edit"));

  @Test
  public void parseArgs() {
    String[] args = { "-pattern", "{}=a", "a.conllu", "-conlluFile", "b.conllu", "c.conllu" };
    Map<String, List<String>> parsed = SemgrexUtils.parseArgs(args, SINGLE);
    assertEquals(Collections.singletonList("{}=a"), parsed.get("-pattern"));
    assertEquals(Collections.singletonList("a.conllu"), parsed.get(null));
    assertEquals(Arrays.asList("b.conllu", "c.conllu"), parsed.get("-conlluFile"));
  }

  @Test
  public void parseArgsRepeated() {
    String[] args = { "-edit", "EditNode -node x -lemma y", "-edit", "EditNode -node x -word z" };
    Map<String, List<String>> parsed = SemgrexUtils.parseArgs(args, SINGLE);
    assertEquals(Arrays.asList("EditNode -node x -lemma y", "EditNode -node x -word z"), parsed.get("-edit"));
  }

  @Test
  public void parseArgsNoValue() {
    String[] args = { "-pattern", "{}=a", "-inPlace", "a.conllu", "b.conllu" };
    Map<String, List<String>> parsed = SemgrexUtils.parseArgs(args, SINGLE, Collections.singleton("-inPlace"));
    assertEquals(Collections.emptyList(), parsed.get("-inPlace"));
    assertEquals(Arrays.asList("a.conllu", "b.conllu"), parsed.get(null));

    // without -inPlace in noValue, it takes the files
    parsed = SemgrexUtils.parseArgs(args, SINGLE);
    assertEquals(Arrays.asList("a.conllu", "b.conllu"), parsed.get("-inPlace"));
    assertEquals(Collections.emptyList(), parsed.get(null));
  }
}
