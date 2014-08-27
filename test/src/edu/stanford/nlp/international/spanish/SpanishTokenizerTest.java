package edu.stanford.nlp.international.spanish;

import edu.stanford.nlp.international.spanish.process.SpanishTokenizer;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.TokenizerFactory;
import junit.framework.TestCase;

import java.io.StringReader;

/**
 * @author Ishita Prasad
 */
public class SpanishTokenizerTest extends TestCase {

  private final String[] ptbInputs = {
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
      "Los hombres sentados están muy guapos."
  };

  private final String[][] ptbGold = {
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
      { "Los", "hombres", "sentados", "están", "muy", "guapos", "." }
  };

  public void testSpanishTokenizerWord() {
    assert (ptbInputs.length == ptbGold.length);
    final TokenizerFactory<CoreLabel> tf = SpanishTokenizer.coreLabelFactory();
    tf.setOptions("");
    tf.setOptions("tokenizeNLs");

    for (int sent = 0; sent < ptbInputs.length; sent++) {
      Tokenizer<CoreLabel> spanishTokenizer = tf.getTokenizer(new StringReader(ptbInputs[sent]));
      int i = 0;
      while (spanishTokenizer.hasNext()) {
        String w = spanishTokenizer.next().word();
        try {
          assertEquals("SpanishTokenizer problem", ptbGold[sent][i], w);
        } catch (ArrayIndexOutOfBoundsException aioobe) {
          // the assertion below outside the loop will fail
        }
        i++;
      }
      assertEquals("SpanishTokenizer num tokens problem", i, ptbGold[sent].length);
    }
  }
}