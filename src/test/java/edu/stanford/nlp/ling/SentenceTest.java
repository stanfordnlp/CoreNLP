package edu.stanford.nlp.ling;

import edu.stanford.nlp.simple.Sentence;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests the static methods that turn sentences (lists of Labels)
 * into strings.
 *
 * @author <a href=horatio@gmail.com>John Bauer</a>
 */
public class SentenceTest {

  private static final String[] words = {"This", "is", "a", "test", "."};
  private static final String[] tags = {"A", "B", "C", "D", "E"};
  private static final String expectedValueOnly = "This is a test .";
  private static final String expectedTagged = "This_A is_B a_C test_D ._E";
  private static final String separator = "_";

  @Before
  public void setUp() {
    Assert.assertEquals(words.length, tags.length);
  }

  @Test
  public void testCoreLabelListToString() {
    List<CoreLabel> clWords = new ArrayList<>();
    List<CoreLabel> clValues = new ArrayList<>();
    List<CoreLabel> clWordTags = new ArrayList<>();
    List<CoreLabel> clValueTags = new ArrayList<>();
    for (int i = 0; i < words.length; ++i) {
      CoreLabel cl = new CoreLabel();
      cl.setWord(words[i]);
      clWords.add(cl);

      cl = new CoreLabel();
      cl.setValue(words[i]);
      clValues.add(cl);

      cl = new CoreLabel();
      cl.setWord(words[i]);
      cl.setTag(tags[i]);
      clWordTags.add(cl);

      cl = new CoreLabel();
      cl.setValue(words[i]);
      cl.setTag(tags[i]);
      clValueTags.add(cl);
    }

    Assert.assertEquals(expectedValueOnly, SentenceUtils.listToString(clWords, true));
    Assert.assertEquals(expectedValueOnly, SentenceUtils.listToString(clValues, true));

    Assert.assertEquals(expectedTagged,
            SentenceUtils.listToString(clWordTags, false, separator));
    Assert.assertEquals(expectedTagged,
            SentenceUtils.listToString(clValueTags, false, separator));
  }

  @SuppressWarnings("Convert2streamapi")
  @Test
  public void testTaggedWordListToString() {
    List<TaggedWord> tagged = new ArrayList<>();
    for (int i = 0; i < words.length; ++i) {
      tagged.add(new TaggedWord(words[i], tags[i]));
    }
    Assert.assertEquals(expectedValueOnly, SentenceUtils.listToString(tagged, true));
    Assert.assertEquals(expectedTagged,
            SentenceUtils.listToString(tagged, false, separator));
  }

  /**
   * Serializing a raw sentence shouldn't make it an order of magnitude larger than
   * the raw text.
   */
  @Test
  public void testTokenizedSentenceSize() {
    String text = "one two three four five";
    byte[] sentenceArray = new Sentence(text).serialize().toByteArray();
    byte[] textArray = text.getBytes();
    Assert.assertTrue(
            String.format("Sentence size (%d bytes) shouldn't be more than %d times bigger than text size (%d bytes)",
                    sentenceArray.length, 11, textArray.length),
            sentenceArray.length < textArray.length * 11);
  }

}
