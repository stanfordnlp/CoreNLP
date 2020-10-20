package edu.stanford.nlp.time;

import org.junit.Test;

import static edu.stanford.nlp.time.SUTimeSimpleParser.parse;
import static org.junit.Assert.assertEquals;

/** Test the SUTimeSimpleParser.
 *
 *  @author Christopher Manning
 */
public class SUTimeSimpleParserITest {

  @Test
  public void testWorking() throws SUTimeSimpleParser.SUTimeParsingError {
    String[] inputs = { "1972", "1972-07-05", "Jan 12, 1975 5:30", "7:12", "0712", "1972-04" };
    String[] outputs = { "1972-XX-XX", "1972-07-05", "1975-01-12T05:30", "T07:12", "712-XX-XX", "1972-04" };
    // todo: second last case is totally bad, but it's what it does at present. But I guess 1930 is ambiguous....
    assertEquals(inputs.length, outputs.length);

    for (int i = 0; i < inputs.length; i++) {
      // System.err.println("String: " + inputs[i]);
      SUTime.Temporal timeExpression = parse(inputs[i]);
      // System.err.println("Parsed: " + timeExpression);
      assertEquals(outputs[i], timeExpression.toString());
    }
  }

}
