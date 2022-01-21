package edu.stanford.nlp.simple;

import edu.stanford.nlp.ie.machinereading.structure.Span;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

/**
 * Tests of the various algorithms in the {@link edu.stanford.nlp.simple.SentenceAlgorithms} class.
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("AssertEqualsBetweenInconvertibleTypes")
public class SentenceAlgorithmsITest {

  @Test
  public void testKeyphrases() throws IOException {
    assertEquals(new ArrayList<Span>() {{
      add(new Span(0, 1));
      add(new Span(2, 3));
    }}, new Sentence("cats and dogs.").algorithms().keyphraseSpans());

    assertEquals(new ArrayList<Span>() {{
      add(new Span(0, 1));
      add(new Span(1, 2));
      add(new Span(3, 4));
    }}, new Sentence("cats playing with dogs.").algorithms().keyphraseSpans());

    assertEquals(new ArrayList<Span>() {{
      add(new Span(0, 2));
      add(new Span(3, 4));
    }}, new Sentence("black cats are furry.").algorithms().keyphraseSpans());

    assertEquals(new ArrayList<Span>() {{
      add(new Span(0, 1));
      add(new Span(1, 3));
      add(new Span(3, 4));
      add(new Span(6, 8));
      add(new Span(10, 12));
      add(new Span(13, 14));
      add(new Span(17, 20));
    }}, new Sentence("Melting involves changing water from its solid state to its liquid state (water) by the addition of heat.").algorithms().keyphraseSpans());

    assertEquals(new ArrayList<Span>() {{
      add(new Span(0, 1));
      add(new Span(1, 2));
      add(new Span(4, 5));
      add(new Span(7, 8));
      add(new Span(8, 9));
      add(new Span(11, 12));
    }}, new Sentence("Water freezing is an example of a liquid changing to a solid.").algorithms().keyphraseSpans());
  }

  @Test
  public void testKeyphrasesPP() throws IOException {
    assertEquals(new ArrayList<Span>(){{
      add(new Span(0, 3));
    }}, new Sentence("period of daylight.").algorithms().keyphraseSpans());
    assertEquals(new ArrayList<Span>(){{
      add(new Span(0, 1));
      add(new Span(3, 4));
    }}, new Sentence("frequency of the wave.").algorithms().keyphraseSpans());
    assertEquals(new ArrayList<Span>(){{
      add(new Span(0, 1));
    }}, new Sentence("period of.").algorithms().keyphraseSpans());
    assertEquals(new ArrayList<Span>(){{
      add(new Span(0, 2));
      add(new Span(4, 5));
    }}, new Sentence("Barack Obama of the USA.").algorithms().keyphraseSpans());
  }

  @Test
  public void testKeyphrasesRegressions() throws IOException {
    assertEquals(new ArrayList<Span>() {{
      add(new Span(0, 1));
      add(new Span(3, 4));
      add(new Span(5, 6));
      add(new Span(7, 10));
    }}, new Sentence("meters can be used to describe an object's length").algorithms().keyphraseSpans());
  }

  @Test
  public void testKeyphrasesAsString() throws IOException {
    assertEquals(new ArrayList<String>() {{
      add("Water");
      add("freezing");
      add("example");
      add("liquid");
      add("changing");
      add("solid");
    }}, new Sentence("Water freezing is an example of a liquid changing to a solid.").algorithms().keyphrases());
  }


  @Test
  public void testHeadOfSpan() throws IOException {
    Sentence s = new Sentence("Freezing involves changing water from its liquid state to its solid state (ice) by the removal of heat.");
    assertEquals(1, s.algorithms().headOfSpan(new Span(0, 3)));
    assertEquals(7, s.algorithms().headOfSpan(new Span(5, 8)));
    assertEquals(2, s.algorithms().headOfSpan(new Span(2, 8)));
  }

  @Test
  public void testAllSpans() throws IOException {
    Sentence s = new Sentence("a b c d");
    Iterator<List<String>> iter = s.algorithms().allSpans(Sentence::words).iterator();
    assertTrue(iter.hasNext()); assertEquals(new ArrayList<String>(){{add("a"); add("b"); add("c"); add("d"); }}, iter.next());
    assertTrue(iter.hasNext()); assertEquals(new ArrayList<String>(){{add("a"); add("b"); add("c"); }}, iter.next());
    assertTrue(iter.hasNext()); assertEquals(new ArrayList<String>(){{add("b"); add("c"); add("d"); }}, iter.next());
    assertTrue(iter.hasNext()); assertEquals(new ArrayList<String>(){{add("a"); add("b"); }}, iter.next());
    assertTrue(iter.hasNext()); assertEquals(new ArrayList<String>(){{add("b"); add("c"); }}, iter.next());
    assertTrue(iter.hasNext()); assertEquals(new ArrayList<String>(){{add("c"); add("d"); }}, iter.next());
    assertTrue(iter.hasNext()); assertEquals(new ArrayList<String>(){{add("a"); }}, iter.next());
    assertTrue(iter.hasNext()); assertEquals(new ArrayList<String>(){{add("b"); }}, iter.next());
    assertTrue(iter.hasNext()); assertEquals(new ArrayList<String>(){{add("c"); }}, iter.next());
    assertTrue(iter.hasNext()); assertEquals(new ArrayList<String>(){{add("d"); }}, iter.next());
    assertFalse(iter.hasNext());
  }

  @Test
  public void testAllSpansLimited() throws IOException {
    Sentence s = new Sentence("a b c d");
    Iterator<List<String>> iter = s.algorithms().allSpans(Sentence::words, 2).iterator();
    assertTrue(iter.hasNext()); assertEquals(new ArrayList<String>(){{add("a"); add("b"); }}, iter.next());
    assertTrue(iter.hasNext()); assertEquals(new ArrayList<String>(){{add("b"); add("c"); }}, iter.next());
    assertTrue(iter.hasNext()); assertEquals(new ArrayList<String>(){{add("c"); add("d"); }}, iter.next());
    assertTrue(iter.hasNext()); assertEquals(new ArrayList<String>(){{add("a"); }}, iter.next());
    assertTrue(iter.hasNext()); assertEquals(new ArrayList<String>(){{add("b"); }}, iter.next());
    assertTrue(iter.hasNext()); assertEquals(new ArrayList<String>(){{add("c"); }}, iter.next());
    assertTrue(iter.hasNext()); assertEquals(new ArrayList<String>(){{add("d"); }}, iter.next());
    assertFalse(iter.hasNext());
  }

  @Test
  public void testDependencyPathBetween() throws IOException {
    Sentence s = new Sentence("the blue cat sat on the green mat");
    assertEquals(new ArrayList<String>(){{ add("the"); add("<-det-"); add("cat"); }},
        s.algorithms().dependencyPathBetween(0, 2));

    assertEquals(new ArrayList<String>() {{
      add("the");
      add("<-det-");
      add("cat");
      add("-amod->");
      add("blue");
    }}, s.algorithms().dependencyPathBetween(0, 1));

    assertEquals(new ArrayList<String>(){{
        add("the");
        add("<-det-");
        add("mat");
        add("-amod->");
        add("green");
    }},s.algorithms().dependencyPathBetween(5, 6));

    assertEquals(new ArrayList<String>() {{
      add("the");
      add("<-det-");
      add("cat");
    }}, s.algorithms().dependencyPathBetween(0, 2));

    s = new Sentence("I visited River Road Asset Management of Louisville , Kentucky .");
    assertEquals(new ArrayList<String>(){{
      add("Management");
      add("-nmod:of->");
      add("Louisville");  // Note[gabor]: This is the wrong parse.
      add("-appos->");
      add("Kentucky");
    }},s.algorithms().dependencyPathBetween(5, 9));
  }


  @Test
  public void testLoopyDependencyPathBetween() throws IOException {
    Sentence s = new Sentence("the blue cat sat on the green mat");
    for (int start = 0; start < s.length(); ++start) {
      for (int end = 0; end < s.length(); ++end) {
        assertEquals(
            s.algorithms().dependencyPathBetween(start, end, Optional.of(Sentence::words)),
            s.algorithms().loopyDependencyPathBetween(start, end, Optional.of(Sentence::words))
        );
      }
    }
  }


  @Test
  public void testDependencyPathBetweenRegressions() throws IOException {
    Sentence s = new Sentence("In the Middle Ages, several powerful Somali empires dominated the regional trade including the Ajuran Sultanate, which excelled in hydraulic engineering and fortress building, the Sultanate of Adal, whose general Ahmad ibn Ibrahim al-Ghazi (Ahmed Gurey) was the first commander to use cannon warfare on the continent during Adal's conquest of the Ethiopian Empire, and the Sultanate of the Geledi, whose military dominance forced governors of the Omani empire north of the city of Lamu to pay tribute to the Somali Sultan Ahmed Yusuf.");
    s.dependencyGraph();
    assertEquals(new ArrayList<String>(){{
      add("forced");
      add("<-amod-");
      add("governors");
      add("<-dep-");
      add("dominance");
      add("<-appos-");
      add("Geledi");
      add("<-nmod:of-");
      add("Sultanate");
      add("<-acl:relcl-");
      add("Sultanate");
      add("<-appos-");
      add("Sultanate");
      add("<-nmod:including-");
      add("trade");
      add("<-obj-");
      add("dominated");
      add("-obl:in->");
      add("Ages");
      add("-compound->");
      add("Middle");
    }}, s.algorithms().dependencyPathBetween(74, 2));
  }
}
