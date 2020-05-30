package edu.stanford.nlp.dcoref;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import java.util.Set;

public class SpeakerInfoTest {

  private SpeakerInfo speakerInfo;

  @Before
  public void setUp() {
    this.speakerInfo = new SpeakerInfo("The quick brown fox jumped over the lazy dog.");
  }

  @Test
  public void testHasRealSpeakerName() {
    boolean hasRealSpeakerName = speakerInfo.hasRealSpeakerName();

    Assert.assertTrue(hasRealSpeakerName);
  }

  @Test
  public void testGetSpeakerName() {
    String speakerName = this.speakerInfo.getSpeakerName();

    Assert.assertEquals("The quick brown fox jumped over the lazy dog.", speakerName);
  }

  @Test
  public void testSpeakerDescNotSet() {
    String speakerDescription = this.speakerInfo.getSpeakerDesc();

    Assert.assertNull(speakerDescription);
  }

  @Test
  public void testGetSpeakerNameStrings() {
    String sentence = "The quick brown fox jumped over the lazy dog.";
    String[] words = sentence.split(" ");

    String[] speakerNames = this.speakerInfo.getSpeakerNameStrings();

    Assert.assertEquals(words, speakerNames);
  }

  @Test
  public void testMentionsIsEmpty() {
    Set<Mention> mentions = this.speakerInfo.getMentions();

    Assert.assertTrue(mentions.isEmpty());
  }

  @Test
  public void testContainsMention() {
    Mention m = new Mention();
    this.speakerInfo.addMention(m);

    Assert.assertTrue(this.speakerInfo.containsMention(m));
  }

  @Test
  public void testMentionsNotEmpty() {
    Mention m = new Mention();
    this.speakerInfo.addMention(m);

    Set<Mention> mentions = this.speakerInfo.getMentions();

    Assert.assertTrue(!mentions.isEmpty());
  }

  @Test
  public void testToString() {
    String string = this.speakerInfo.toString();

    Assert.assertEquals("The quick brown fox jumped over the lazy dog.", string);
  }
}