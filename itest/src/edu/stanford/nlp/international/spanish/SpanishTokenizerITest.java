package edu.stanford.nlp.international.spanish;

import edu.stanford.nlp.international.spanish.process.SpanishTokenizer;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.TokenizerFactory;
import junit.framework.TestCase;

import java.io.StringReader;
import java.util.List;

/**
 * Needs to be an "itest" because the VerbStripper loads data from the models jar.
 *
 * @author Ishita Prasad
 */
public class SpanishTokenizerITest extends TestCase {

  private final String[] spanishInputs = {
      "Esta es una oración.",
      "¡Dímelo!",
      "Hazlo.",
      "Este es un címbalo.",
      "Metelo.",
      "Sentémonos.",
      "Escribámosela.",
      "No comamos allí.",
      "Comamosla.",
      "sub-20",
      "un teléfono (902.400.345).",
      "Port-au-Prince",
      "McLaren/Mercedes",
      "10/12",
      "4X4",
      "3G",
      "3g",
      "sp3",
      "12km",
      "12km/h",
      "Los hombres sentados están muy guapos.",
      "Hizo abrirlos.",
      "salos ) ( 1 de",
  };

  private final String[][] spanishGold = {
      { "Esta", "es", "una", "oración", "." },
      { "¡", "Di", "me", "lo", "!" },
      { "Haz", "lo", "." },
      { "Este", "es", "un", "címbalo", "." },
      { "Mete", "lo", "." },
      { "Sentemos", "nos", "." },
      { "Escribamos", "se", "la", "." },
      { "No", "comamos", "allí", "." },
      { "Comamos", "la", "." },
      { "sub-20" },
      { "un", "teléfono", "=LRB=", "902.400.345", "=RRB=", "." },
      { "Port", "-", "au", "-", "Prince" },
      { "McLaren", "/", "Mercedes" },
      { "10/12" },
      { "4X4" },
      { "3G" },
      { "3g" },
      { "sp3" },
      { "12", "km" },
      { "12", "km", "/", "h" },
      { "Los", "hombres", "sentados", "están", "muy", "guapos", "." },
      { "Hizo", "abrir", "los", "." },
      { "salos", "=RRB=", "=LRB=", "1", "de" },
  };


  private static void runSpanish(TokenizerFactory<CoreLabel> tf, String[] inputs, String[][] gold) {
    for (int sent = 0; sent < inputs.length; sent++) {
      Tokenizer<CoreLabel> spanishTokenizer = tf.getTokenizer(new StringReader(inputs[sent]));
      int i = 0;
      while (spanishTokenizer.hasNext()) {
        String w = spanishTokenizer.next().word();
        try {
          assertEquals("SpanishTokenizer problem", gold[sent][i], w);
        } catch (ArrayIndexOutOfBoundsException aioobe) {
          // the assertion below outside the loop will fail
        }
        i++;
      }
      assertEquals("SpanishTokenizer num tokens problem", i, gold[sent].length);
    }
  }

  public void testSpanishTokenizerWord() {
     assert spanishInputs.length == spanishGold.length;
     final TokenizerFactory<CoreLabel> tf = SpanishTokenizer.ancoraFactory();
     tf.setOptions("");
     tf.setOptions("tokenizeNLs");

     runSpanish(tf, spanishInputs, spanishGold);
   }

  /** Makes a Spanish tokenizer with the options that CoreNLP uses. Results actually no different.... */
  public void testSpanishTokenizerCoreNLP() {
    assert spanishInputs.length == spanishGold.length;
    final TokenizerFactory<CoreLabel> tf = SpanishTokenizer.coreLabelFactory();
    tf.setOptions("");
    tf.setOptions("invertible,ptb3Escaping=true,splitAll=true");

    runSpanish(tf, spanishInputs, spanishGold);
  }

  public void testOffsetsSpacing() {
    String text = "escribámosela.";
    // String text = "  La   combinación consonántica ss es ajena a la\tortografía    castellana:   \n\n traigámosela, mandémoselos, escribámosela, comprémoselo.";
    // final TokenizerFactory<CoreLabel> tf = SpanishTokenizer.ancoraFactory();
    // tf.setOptions("");
    // tf.setOptions("tokenizeNLs,invertible");
    final TokenizerFactory<CoreLabel> tf = SpanishTokenizer.coreLabelFactory();
    tf.setOptions("");
    tf.setOptions("splitAll=true");
    Tokenizer<CoreLabel> spanishTokenizer = tf.getTokenizer(new StringReader(text));
    List<CoreLabel> tokens = spanishTokenizer.tokenize();
    System.err.println(tokens);
    assertEquals(27, tokens.size());
    assertEquals("  ", tokens.get(0).get(CoreAnnotations.BeforeAnnotation.class));
    assertEquals("\t", tokens.get(8).get(CoreAnnotations.AfterAnnotation.class));
    assertEquals("Wrong begin char offset", 2, (int) tokens.get(0).get(CoreAnnotations.CharacterOffsetBeginAnnotation.class));
    assertEquals("Wrong end char offset", 4, (int) tokens.get(0).get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
    assertEquals("La", tokens.get(0).get(CoreAnnotations.OriginalTextAnnotation.class));
    // note: after(x) and before(x+1) are the same
    assertEquals("   ", tokens.get(0).get(CoreAnnotations.AfterAnnotation.class));
    assertEquals("   ", tokens.get(1).get(CoreAnnotations.BeforeAnnotation.class));
    // americanize is now off by default
    assertEquals("colourful", tokens.get(3).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("colourful", tokens.get(3).get(CoreAnnotations.OriginalTextAnnotation.class));
    assertEquals("", tokens.get(4).after());
    assertEquals("", tokens.get(5).before());
    assertEquals("    ", tokens.get(5).get(CoreAnnotations.AfterAnnotation.class));

    StringBuilder result = new StringBuilder();
    result.append(tokens.get(0).get(CoreAnnotations.BeforeAnnotation.class));
    for (CoreLabel token : tokens) {
      result.append(token.get(CoreAnnotations.OriginalTextAnnotation.class));
      String after = token.get(CoreAnnotations.AfterAnnotation.class);
      if (after != null)
        result.append(after);
    }
    assertEquals(text, result.toString());

    for (int i = 0; i < tokens.size() - 1; ++i) {
      assertEquals(tokens.get(i).get(CoreAnnotations.AfterAnnotation.class),
                   tokens.get(i + 1).get(CoreAnnotations.BeforeAnnotation.class));
    }
  }




}
