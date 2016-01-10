package edu.stanford.nlp.international.spanish;

import edu.stanford.nlp.international.spanish.process.SpanishTokenizer;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.TokenizerFactory;
import junit.framework.TestCase;

import java.io.StringReader;

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

}
