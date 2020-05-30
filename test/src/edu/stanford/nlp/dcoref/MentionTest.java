package edu.stanford.nlp.dcoref;

import edu.stanford.nlp.semgraph.SemanticGraph;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class MentionTest {

  private Mention mention;

  @Before
  public void setUp() {
    SemanticGraph sg = new SemanticGraph();
    this.mention = new Mention(1, 500, 511, sg);
  }

  @Test
  public void testTypeNotNull() {
    Assert.assertNotNull(mention.getType());
  }

  @Test
  public void testIsNotPronominal() {
    Assert.assertFalse(this.mention.isPronominal());
  }

  @Test
  public void testIsNotOwnListMemberEmpty() {
    Assert.assertFalse(this.mention.isListMemberOf(this.mention));
  }

  @Test
  public void testIsNotOwnListMember() {
    this.mention.addListMember(this.mention);

    Assert.assertFalse(this.mention.isListMemberOf(this.mention));
  }

  @Test
  public void testAddBelongsToList() {
    this.mention.addBelongsToList(this.mention);

    Assert.assertFalse(this.mention.isListMemberOf(this.mention));
  }

  @Test
  public void testIsMemberOfSameList() {
    this.mention.addBelongsToList(this.mention);

    Assert.assertTrue(this.mention.isMemberOfSameList(this.mention));
  }

  @Test
  public void testIsSameSentence() {
    Assert.assertTrue(this.mention.sameSentence(this.mention));
  }
}