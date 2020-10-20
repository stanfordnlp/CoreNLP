package edu.stanford.nlp.process;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Some simple tests for the TSVUtils functionalities.
 *
 * @author Gabor Angeli
 */
public class TSVUtilsTest {

  @Test
  public void testParseArrayTrivial() {
    assertEquals(Arrays.asList("foo", "bar"),
            TSVUtils.parseArray("{foo,bar}"));
  }

  @Test
  public void testParseArrayQuote() {
    assertEquals(Arrays.asList("foo", ",", "a,b", "bar"),
            TSVUtils.parseArray("{foo,\",\",\"a,b\",bar}"));
  }

  @Test
  public void testParseArrayEscape() {
    assertEquals(Arrays.asList("foo", "\"", "a\"b", "bar"),
            TSVUtils.parseArray("{foo,\"\\\"\",\"a\\\"b\",bar}"));
    assertEquals(Arrays.asList("foo", "\"", "bar"),
            TSVUtils.parseArray("{foo,\\\",bar}"));
    assertEquals(Collections.singletonList("aa\\bb"),
            TSVUtils.parseArray("{\"aa\\\\\\\\bb\"}"));  // should really give 2 backslashes in answer but doesn't.
    assertEquals(Collections.singletonList("a\"b"),
            TSVUtils.parseArray("{\"a\"\"b\"}"));  // should really give 2 backslashes in answer but doesn't.
  }

  @Test
  public void testRealSentence() {
    String array = "{\"<ref name=\\\"Dr. Mohmmad Riaz Suddle, Director of the Paksat-IR programme and current executive member of the Suparco's plan and research division \\\"/>\",On,August,11th,\",\",Paksat-1R,|,'',Paksat-IR,'',was,launched,from,Xichang,Satellite,Launch,Center,by,Suparco,\",\",making,it,first,satellite,to,be,launched,under,this,programme,.}";
    assertEquals(31, TSVUtils.parseArray(array).size());
    assertEquals(Arrays.asList(
            "<ref name=\"Dr. Mohmmad Riaz Suddle, Director of the Paksat-IR programme and current executive member of the Suparco's plan and research division \"/>",
            "On", "August", "11th", ",", "Paksat-1R",
            "|", "''", "Paksat-IR", "''", "was",
            "launched", "from", "Xichang", "Satellite", "Launch",
            "Center", "by", "Suparco", ",", "making",
            "it", "first", "satellite", "to", "be",
            "launched", "under", "this", "programme", "."),
            TSVUtils.parseArray(array));
  }

  @Test
  public void testRealSentenceDoubleEscaped() {
    String array = "{\"<ref name=\\\\\"Dr. Mohmmad Riaz Suddle, Director of the Paksat-IR programme and current executive member of the Suparco's plan and research division \\\\\"/>\",On,August,11th,\",\",Paksat-1R,|,'',Paksat-IR,'',was,launched,from,Xichang,Satellite,Launch,Center,by,Suparco,\",\",making,it,first,satellite,to,be,launched,under,this,programme,.}";
    assertEquals(31, TSVUtils.parseArray(array).size());
    assertEquals(Arrays.asList(
            "<ref name=\"Dr. Mohmmad Riaz Suddle, Director of the Paksat-IR programme and current executive member of the Suparco's plan and research division \"/>",
            "On", "August", "11th", ",", "Paksat-1R",
            "|", "''", "Paksat-IR", "''", "was",
            "launched", "from", "Xichang", "Satellite", "Launch",
            "Center", "by", "Suparco", ",", "making",
            "it", "first", "satellite", "to", "be",
            "launched", "under", "this", "programme", "."),
            TSVUtils.parseArray(array));
  }

}
