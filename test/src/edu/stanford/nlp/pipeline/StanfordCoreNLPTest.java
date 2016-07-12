package edu.stanford.nlp.pipeline;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test some of the utility functions in {@link StanfordCoreNLP}.
 *
 * @author Gabor Angeli
 */
public class StanfordCoreNLPTest {

  @Test
  public void testPrereqAnnotatorsBasic() {
    assertEquals("tokenize,ssplit,parse",
        StanfordCoreNLP.ensurePrerequisiteAnnotators(new String[]{"parse"}));
    assertEquals("tokenize,ssplit,pos,depparse",
        StanfordCoreNLP.ensurePrerequisiteAnnotators(new String[]{"depparse"}));
    assertEquals("tokenize,ssplit,pos,depparse",
        StanfordCoreNLP.ensurePrerequisiteAnnotators(new String[]{"depparse", "tokenize"}));
    assertEquals("tokenize,ssplit,pos,lemma,depparse,natlog",
        StanfordCoreNLP.ensurePrerequisiteAnnotators(new String[]{"natlog", "tokenize"}));
  }

  @Test
  public void testPrereqAnnotatorsOrderPreserving() {
    assertEquals("tokenize,ssplit,pos,lemma,depparse,natlog",
        StanfordCoreNLP.ensurePrerequisiteAnnotators(new String[]{"lemma", "depparse", "natlog"}));
    assertEquals("tokenize,ssplit,pos,depparse,lemma,natlog",
        StanfordCoreNLP.ensurePrerequisiteAnnotators(new String[]{"depparse", "lemma", "natlog"}));
    assertEquals("tokenize,ssplit,pos,lemma,ner,regexner",
        StanfordCoreNLP.ensurePrerequisiteAnnotators(new String[]{"ner", "regexner"}));
    assertEquals("tokenize,ssplit,pos,lemma,regexner,ner",
        StanfordCoreNLP.ensurePrerequisiteAnnotators(new String[]{"regexner", "ner"}));
  }


  @Test
  public void testPrereqAnnotatorsCoref() {
    assertEquals("tokenize,ssplit,pos,lemma,ner,depparse,mention,coref",
        StanfordCoreNLP.ensurePrerequisiteAnnotators(new String[]{"coref"}));
//    assertEquals("tokenize,ssplit,pos,lemma,ner,parse,mention,coref",
//        StanfordCoreNLP.ensurePrerequisiteAnnotators(new String[]{"parse", "coref"}));
  }

}
