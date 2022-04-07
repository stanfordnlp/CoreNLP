package edu.stanford.nlp.pipeline;

import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.*;

import edu.stanford.nlp.util.PropertiesUtils;

/**
 * Test some of the utility functions in {@link StanfordCoreNLP}.
 *
 * @author Gabor Angeli
 */
public class StanfordCoreNLPTest {

  @Test
  public void testPrereqAnnotatorsBasic() {
    assertEquals("tokenize,parse",
        StanfordCoreNLP.ensurePrerequisiteAnnotators(new String[]{"parse"}, new Properties()));
    assertEquals("tokenize,pos,depparse",
        StanfordCoreNLP.ensurePrerequisiteAnnotators(new String[]{"depparse"}, new Properties()));
    assertEquals("tokenize,pos,depparse",
        StanfordCoreNLP.ensurePrerequisiteAnnotators(new String[]{"depparse", "tokenize"}, new Properties()));
    assertEquals("tokenize,pos,lemma,depparse,natlog",
        StanfordCoreNLP.ensurePrerequisiteAnnotators(new String[]{"natlog", "tokenize"}, new Properties()));
  }

  @Test
  public void testPrereqAnnotatorsOrderPreserving() {
    assertEquals("tokenize,pos,lemma,depparse,natlog",
        StanfordCoreNLP.ensurePrerequisiteAnnotators(new String[]{"lemma", "depparse", "natlog"}, new Properties()));
    assertEquals("tokenize,pos,depparse,lemma,natlog",
        StanfordCoreNLP.ensurePrerequisiteAnnotators(new String[]{"depparse", "lemma", "natlog"}, new Properties()));
    assertEquals("tokenize,pos,lemma,ner,regexner",
        StanfordCoreNLP.ensurePrerequisiteAnnotators(new String[]{"ner", "regexner"}, new Properties()));
    assertEquals("tokenize,pos,lemma,ner,depparse",
        StanfordCoreNLP.ensurePrerequisiteAnnotators(new String[]{"ner", "depparse"}, new Properties()));
    assertEquals("tokenize,pos,lemma,depparse,ner",
        StanfordCoreNLP.ensurePrerequisiteAnnotators(new String[]{"depparse", "ner"}, new Properties()));
  }


  @Test
  public void testPrereqAnnotatorsRegexNERAfterNER() {
    assertEquals("tokenize,pos,lemma,ner,regexner",
        StanfordCoreNLP.ensurePrerequisiteAnnotators(new String[]{"regexner", "ner"}, new Properties()));
  }


  @Test
  public void testPrereqAnnotatorsCorefBeforeOpenIE() {
    assertEquals("tokenize,pos,lemma,depparse,natlog,ner,coref,openie",
        StanfordCoreNLP.ensurePrerequisiteAnnotators(new String[]{"openie", "coref"}, new Properties()));
    assertEquals("tokenize,pos,lemma,ner,depparse,natlog,coref,openie",
        StanfordCoreNLP.ensurePrerequisiteAnnotators(new String[]{"coref", "openie"}, new Properties()));
  }


  @Test
  public void testPrereqAnnotatorsCoref() {
    Properties props = new Properties();
    assertEquals("tokenize,pos,lemma,ner,depparse,coref",
        StanfordCoreNLP.ensurePrerequisiteAnnotators(new String[]{"coref"}, props));
    assertEquals("dep", props.getProperty("coref.md.type", ""));
  }


  @Test
  public void testPrereqAnnotatorsCorefWithParse() {
    Properties props = new Properties();
    assertEquals("tokenize,parse,lemma,ner,coref",
        StanfordCoreNLP.ensurePrerequisiteAnnotators(new String[]{"parse","coref"}, props));
    assertEquals("__empty__", props.getProperty("coref.md.type", "__empty__"));
  }

  // Test a couple use cases of removing the cleanxml annotator from
  // requested annotator lists
  @Test
  public void testUnifyTokenizer() {
    String[] inputs =   {"tokenize,cleanxml",
                         "tokenize",
                         "tokenize,cleanxml,",
                         "tokenize,cleanxml,pos",
                         "tokenize,cleanxml  ,pos",
                         "tokenize,   cleanxml  ,pos",
                         "cleanxml,pos"};
    String[] expected = {"tokenize",
                         "tokenize",
                         "tokenize,",
                         "tokenize,pos",
                         "tokenize,pos",
                         "tokenize,   pos",
                         "cleanxml,pos"};
    boolean[] option =  {true,
                         false,
                         true,
                         true,
                         true,
                         true,
                         false};
    assertEquals(inputs.length, expected.length);
    assertEquals(inputs.length, option.length);
    for (int i = 0; i < inputs.length; ++i) {
      Properties props = new Properties();
      props.setProperty("annotators", inputs[i]);
      StanfordCoreNLP.unifyTokenizeProperty(props, "cleanxml", "tokenize.cleanxml");
      assertEquals(expected[i], props.getProperty("annotators"));
      assertEquals(option[i], PropertiesUtils.getBool(props, "tokenize.cleanxml", false));
    }
  }
}
