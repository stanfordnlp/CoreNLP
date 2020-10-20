package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.ling.CoreLabel;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Some tests for the simpler methods in Util -- e.g., the ones manipulating NER spans.
 *
 * @author Gabor Angeli
 */
public class UtilTest {

  private CoreLabel mkLabel(String word, String ner) {
    CoreLabel label = new CoreLabel();
    label.setWord(word);
    label.setOriginalText(word);
    label.setNER(ner);
    return label;
  }

  private List<CoreLabel> mockLabels(String input) {
    return Arrays.stream(input.split(" ")).map(x -> x.indexOf("_") > 0 ? mkLabel(x.substring(0, x.indexOf("_")), x.substring(x.indexOf("_") + 1)) : mkLabel(x, "O")).collect(Collectors.toList());
  }

  @Test
  public void guessNERSpan() {
    assertEquals("O", Util.guessNER(mockLabels("the black cat"), new Span(0, 3)));
    assertEquals("PERSON", Util.guessNER(mockLabels("the president Obama_PERSON"), new Span(0, 3)));
    assertEquals("TITLE", Util.guessNER(mockLabels("the President_TITLE Obama_PERSON"), new Span(0, 2)));
    assertEquals("PERSON", Util.guessNER(mockLabels("the President_TITLE Obama_PERSON"), new Span(2, 3)));
    assertEquals("PERSON", Util.guessNER(mockLabels("the President_TITLE Barack_PERSON Obama_PERSON"), new Span(0, 4)));
  }

  @Test
  public void guessNER() {
    assertEquals("O", Util.guessNER(mockLabels("the black cat")));
    assertEquals("PERSON", Util.guessNER(mockLabels("the president Obama_PERSON")));
    assertEquals("PERSON", Util.guessNER(mockLabels("the President_TITLE Barack_PERSON Obama_PERSON")));
  }

  @Test
  public void extractNER() {
    assertEquals("O", Util.guessNER(mockLabels("the black cat")));
    assertEquals(new Span(2, 3), Util.extractNER(mockLabels("the president Obama_PERSON"), new Span(2, 3)));
    assertEquals(new Span(2, 3), Util.extractNER(mockLabels("the president Obama_PERSON"), new Span(1, 3)));
    assertEquals(new Span(2, 3), Util.extractNER(mockLabels("the president Obama_PERSON"), new Span(0, 3)));
    assertEquals(new Span(2, 4), Util.extractNER(mockLabels("the President_TITLE Barack_PERSON Obama_PERSON"), new Span(2, 4)));
    assertEquals(new Span(2, 4), Util.extractNER(mockLabels("the President_TITLE Barack_PERSON Obama_PERSON"), new Span(2, 3)));
    assertEquals(new Span(1, 2), Util.extractNER(mockLabels("the President_TITLE Barack_PERSON Obama_PERSON"), new Span(0, 2)));
    assertEquals(new Span(1, 2), Util.extractNER(mockLabels("the President_TITLE Barack_PERSON Obama_PERSON visited China_LOCATION"), new Span(0, 2)));
    assertEquals(new Span(2, 4), Util.extractNER(mockLabels("the President_TITLE Barack_PERSON Obama_PERSON visited China_LOCATION"), new Span(2, 5)));
    assertEquals(new Span(2, 4), Util.extractNER(mockLabels("the President_TITLE Barack_PERSON Obama_PERSON visited China_LOCATION"), new Span(2, 6)));
    assertEquals(new Span(5, 6), Util.extractNER(mockLabels("the President_TITLE Barack_PERSON Obama_PERSON visited China_LOCATION"), new Span(5, 6)));
    assertEquals(new Span(5, 6), Util.extractNER(mockLabels("the President_TITLE Barack_PERSON Obama_PERSON visited China_LOCATION"), new Span(4, 6)));
  }

  @Test
  public void extractNERDifferingTypes() {
    assertEquals(new Span(2,4), Util.extractNER(mockLabels("the President_TITLE Barack_PERSON Obama_PERSON visited China_LOCATION"), new Span(0, 5)));
    assertEquals(new Span(5,10), Util.extractNER(mockLabels("the President_TITLE Barack_PERSON Obama_PERSON visited The_LOCATION Peoples_LOCATION Republic_LOCATION of_LOCATION China_LOCATION"), new Span(0, 10)));
  }
  @Test
  public void extractNERNoNER() {
    assertEquals(new Span(0,1), Util.extractNER(mockLabels("the President_TITLE"), new Span(0, 1)));
    assertEquals(new Span(0,1), Util.extractNER(mockLabels("the honorable President_TITLE"), new Span(0, 1)));
    assertEquals(new Span(0,2), Util.extractNER(mockLabels("the honorable President_TITLE"), new Span(0, 2)));
    assertEquals(new Span(0,2), Util.extractNER(mockLabels("the honorable Mr. President_TITLE"), new Span(0, 2)));
    assertEquals(new Span(1,2), Util.extractNER(mockLabels("the honorable Mr. President_TITLE"), new Span(1, 2)));
  }

  @Test
  public void nerOverlap() {
    assertEquals(true, Util.nerOverlap(
        mockLabels("the President_TITLE Barack_PERSON Obama_PERSON visited China_LOCATION"),
        new Span(0, 1),
        new Span(0, 1)));
    assertEquals(true, Util.nerOverlap(
        mockLabels("the President_TITLE Barack_PERSON Obama_PERSON visited China_LOCATION"),
        new Span(1, 2),
        new Span(1, 2)));
    assertEquals(true, Util.nerOverlap(
        mockLabels("the President_TITLE Barack_PERSON Obama_PERSON visited China_LOCATION"),
        new Span(1, 2),
        new Span(0, 2)));

    assertEquals(false, Util.nerOverlap(
        mockLabels("the President_TITLE Barack_PERSON Obama_PERSON visited China_LOCATION"),
        new Span(1, 2),
        new Span(2, 4)));
    assertEquals(true, Util.nerOverlap(
        mockLabels("the President_TITLE Barack_PERSON Obama_PERSON visited China_LOCATION"),
        new Span(1, 4),
        new Span(2, 4)));
  }
}
