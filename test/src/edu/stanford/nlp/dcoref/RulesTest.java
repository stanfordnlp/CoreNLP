package edu.stanford.nlp.dcoref;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.SentenceUtils;
import junit.framework.TestCase;

import java.util.List;

/**
 * Test some of the "rules" which compose the coref system
 *
 * @author John Bauer
 */
public class RulesTest extends TestCase {
  List<CoreLabel> IBM = SentenceUtils.toCoreLabelList("IBM");
  List<CoreLabel> IBM2 = SentenceUtils.toCoreLabelList("International", "Business", "Machines");
  List<CoreLabel> IBMM = SentenceUtils.toCoreLabelList("IBMM");
  List<CoreLabel> MIBM = SentenceUtils.toCoreLabelList("MIBM");

  public void testIsAcronym() {
    assertTrue("Acronym IMB -> International Business Machines", Rules.isAcronym(IBM, IBM2));
    assertTrue("Acronym International Business Machines -> IBM", Rules.isAcronym(IBM2, IBM));
    assertFalse("Not Acronym IBM -> IBMM", Rules.isAcronym(IBM, IBMM));
    assertFalse("Not Acronym International Business Machines -> IBMM", Rules.isAcronym(IBM2, IBMM));
    assertFalse("Not Acronym IBM -> MIBM", Rules.isAcronym(IBM, MIBM));
    assertFalse("Not acronym International Business Machines -> MIBM", Rules.isAcronym(IBM2, MIBM));
  }

  public void testMentionMatchesSpeakerAnnotation() {
    Mention g1 = new Mention(0, 0, 0, null);
    Mention m1 = new Mention(0, 0, 0, null);
    Mention m2 = new Mention(0, 0, 0, null);
    Mention m3 = new Mention(0, 0, 0, null);
    Mention m4 = new Mention(0, 0, 0, null);
    Mention m5 = new Mention(0, 0, 0, null);
    Mention m6 = new Mention(0, 0, 0, null);
    Mention m7 = new Mention(0, 0, 0, null);
    Mention m8 = new Mention(0, 0, 0, null);

    Mention g2 = new Mention(0, 0, 0, null);
    Mention g3 = new Mention(0, 0, 0, null);
    Mention g4 = new Mention(0, 0, 0, null);

    g1.headWord = new CoreLabel();
    g1.headWord.set(CoreAnnotations.SpeakerAnnotation.class, "john abraham bauer");
    m1.headString = "john";
    m2.headString = "bauer";
    m3.headString = "foo";
    m4.headString = "abraham";
    m5.headString = "braham";
    m6.headString = "zabraham";
    m7.headString = "abraha";
    m8.headString = "abrahamz";

    g2.headWord = new CoreLabel();
    g2.headWord.set(CoreAnnotations.SpeakerAnnotation.class, "john");

    g3.headWord = new CoreLabel();
    g3.headWord.set(CoreAnnotations.SpeakerAnnotation.class, "joh");

    g4.headWord = new CoreLabel();
    g4.headWord.set(CoreAnnotations.SpeakerAnnotation.class, "johnz");


    assertTrue(Rules.antecedentMatchesMentionSpeakerAnnotation(g1, m1));
    assertTrue(Rules.antecedentMatchesMentionSpeakerAnnotation(g1, m2));
    assertFalse(Rules.antecedentMatchesMentionSpeakerAnnotation(g1, m3));
    assertTrue(Rules.antecedentMatchesMentionSpeakerAnnotation(g1, m4));
    assertFalse(Rules.antecedentMatchesMentionSpeakerAnnotation(g1, m5));
    assertFalse(Rules.antecedentMatchesMentionSpeakerAnnotation(g1, m6));
    assertFalse(Rules.antecedentMatchesMentionSpeakerAnnotation(g1, m7));
    assertFalse(Rules.antecedentMatchesMentionSpeakerAnnotation(g1, m8));

    assertTrue(Rules.antecedentMatchesMentionSpeakerAnnotation(g2, m1));
    assertFalse(Rules.antecedentMatchesMentionSpeakerAnnotation(g3, m1));
    assertFalse(Rules.antecedentMatchesMentionSpeakerAnnotation(g4, m1));

    // not symmetrical
    // also, shouldn't blow up if the annotation isn't set
    assertFalse(Rules.antecedentMatchesMentionSpeakerAnnotation(m1, g1));
  }
}
