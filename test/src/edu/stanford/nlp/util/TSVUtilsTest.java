package edu.stanford.nlp.util;

import java.util.ArrayList;
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
    assertEquals(new ArrayList<String>() {{
      add("foo");
      add("bar");
    }}, TSVUtils.parseArray("{foo,bar}"));
  }

  @Test
  public void testParseArrayQuote() {
    assertEquals(new ArrayList<String>() {{
      add("foo");
      add(",");
      add("a,b");
      add("bar");
    }}, TSVUtils.parseArray("{foo,\",\",\"a,b\",bar}"));
  }

  @Test
  public void testParseArrayEscape() {
    assertEquals(new ArrayList<String>() {{
      add("foo");
      add("\"");
      add("a\"b");
      add("bar");
    }}, TSVUtils.parseArray("{foo,\"\\\"\",\"a\\\"b\",bar}"));
    assertEquals(new ArrayList<String>() {{
      add("foo");
      add("\"");
      add("bar");
    }}, TSVUtils.parseArray("{foo,\\\",bar}"));
    assertEquals(Collections.singletonList("aa\\bb"),
            TSVUtils.parseArray("{\"aa\\\\\\\\bb\"}"));  // should really give 2 backslashes in answer but doesn't.
    assertEquals(Collections.singletonList("a\"b"),
            TSVUtils.parseArray("{\"a\"\"b\"}"));  // should really give 2 backslashes in answer but doesn't.
  }

  @Test
  public void testRealSentence() {
    String array = "{\"<ref name=\\\"Dr. Mohmmad Riaz Suddle, Director of the Paksat-IR programme and current executive member of the Suparco's plan and research division \\\"/>\",On,August,11th,\",\",Paksat-1R,|,'',Paksat-IR,'',was,launched,from,Xichang,Satellite,Launch,Center,by,Suparco,\",\",making,it,first,satellite,to,be,launched,under,this,programme,.}";
    assertEquals(31, TSVUtils.parseArray(array).size());
    assertEquals(new ArrayList<String>() {{
      add("<ref name=\"Dr. Mohmmad Riaz Suddle, Director of the Paksat-IR programme and current executive member of the Suparco's plan and research division \"/>");
      add("On");
      add("August");
      add("11th");
      add(",");
      add("Paksat-1R");
      add("|");
      add("''");
      add("Paksat-IR");
      add("''");
      add("was");
      add("launched");
      add("from");
      add("Xichang");
      add("Satellite");
      add("Launch");
      add("Center");
      add("by");
      add("Suparco");
      add(",");
      add("making");
      add("it");
      add("first");
      add("satellite");
      add("to");
      add("be");
      add("launched");
      add("under");
      add("this");
      add("programme");
      add(".");
    }}, TSVUtils.parseArray(array));
  }

  @Test
  public void testRealSentenceDoubleEscaped() {
    String array = "{\"<ref name=\\\\\"Dr. Mohmmad Riaz Suddle, Director of the Paksat-IR programme and current executive member of the Suparco's plan and research division \\\\\"/>\",On,August,11th,\",\",Paksat-1R,|,'',Paksat-IR,'',was,launched,from,Xichang,Satellite,Launch,Center,by,Suparco,\",\",making,it,first,satellite,to,be,launched,under,this,programme,.}";
    assertEquals(31, TSVUtils.parseArray(array).size());
    assertEquals(new ArrayList<String>() {{
      add("<ref name=\"Dr. Mohmmad Riaz Suddle, Director of the Paksat-IR programme and current executive member of the Suparco's plan and research division \"/>");
      add("On");
      add("August");
      add("11th");
      add(",");
      add("Paksat-1R");
      add("|");
      add("''");
      add("Paksat-IR");
      add("''");
      add("was");
      add("launched");
      add("from");
      add("Xichang");
      add("Satellite");
      add("Launch");
      add("Center");
      add("by");
      add("Suparco");
      add(",");
      add("making");
      add("it");
      add("first");
      add("satellite");
      add("to");
      add("be");
      add("launched");
      add("under");
      add("this");
      add("programme");
      add(".");
    }}, TSVUtils.parseArray(array));
  }

}
