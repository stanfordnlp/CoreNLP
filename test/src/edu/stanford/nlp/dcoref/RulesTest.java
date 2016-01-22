package edu.stanford.nlp.dcoref;

import junit.framework.TestCase;

import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Sentence;

/**
 * Test some of the "rules" which compose the coref system
 *
 * @author John Bauer
 */
public class RulesTest extends TestCase {
  List<CoreLabel> IBM = Sentence.toCoreLabelList("IBM");
  List<CoreLabel> IBM2 = Sentence.toCoreLabelList("International", "Business", "Machines");
  List<CoreLabel> IBMM = Sentence.toCoreLabelList("IBMM");
  List<CoreLabel> MIBM = Sentence.toCoreLabelList("MIBM");

  public void testIsAcronym() {
    assertTrue(Rules.isAcronym(IBM, IBM2));
    assertTrue(Rules.isAcronym(IBM2, IBM));
    assertFalse(Rules.isAcronym(IBM, IBMM));
    assertFalse(Rules.isAcronym(IBM2, IBMM));
    assertFalse(Rules.isAcronym(IBM, MIBM));
    assertFalse(Rules.isAcronym(IBM2, MIBM));
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
    

    assertTrue(Rules.mentionMatchesSpeakerAnnotation(g1, m1));
    assertTrue(Rules.mentionMatchesSpeakerAnnotation(g1, m2));
    assertFalse(Rules.mentionMatchesSpeakerAnnotation(g1, m3));
    assertTrue(Rules.mentionMatchesSpeakerAnnotation(g1, m4));
    assertFalse(Rules.mentionMatchesSpeakerAnnotation(g1, m5));
    assertFalse(Rules.mentionMatchesSpeakerAnnotation(g1, m6));
    assertFalse(Rules.mentionMatchesSpeakerAnnotation(g1, m7));
    assertFalse(Rules.mentionMatchesSpeakerAnnotation(g1, m8));

    assertTrue(Rules.mentionMatchesSpeakerAnnotation(g2, m1));
    assertFalse(Rules.mentionMatchesSpeakerAnnotation(g3, m1));
    assertFalse(Rules.mentionMatchesSpeakerAnnotation(g4, m1));
    
    // not symmetrical
    // also, shouldn't blow up if the annotation isn't set
    assertFalse(Rules.mentionMatchesSpeakerAnnotation(m1, g1));
  }
}