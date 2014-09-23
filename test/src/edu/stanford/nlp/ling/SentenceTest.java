package edu.stanford.nlp.ling;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;

/** 
 *  Tests the static methods that turn sentences (lists of Labels)
 *  into strings.
 *
 *  @author John Bauer
 */
public class SentenceTest extends TestCase {
  String words[] = {"This", "is", "a", "test", "."};
  String tags[] = {"A", "B", "C", "D", "E"};
  String expectedValueOnly = "This is a test .";
  String expectedTagged = "This_A is_B a_C test_D ._E";
  String separator = "_";

  @Override
  public void setUp() {
    assertEquals(words.length, tags.length);
  }
  
  public void testCoreLabelListToString() {
    List<CoreLabel> clWords = new ArrayList<CoreLabel>();
    List<CoreLabel> clValues = new ArrayList<CoreLabel>();
    List<CoreLabel> clWordTags = new ArrayList<CoreLabel>();
    List<CoreLabel> clValueTags = new ArrayList<CoreLabel>();
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

    assertEquals(expectedValueOnly, Sentence.listToString(clWords, true));
    assertEquals(expectedValueOnly, Sentence.listToString(clValues, true));

    assertEquals(expectedTagged, 
                 Sentence.listToString(clWordTags, false, separator));
    assertEquals(expectedTagged, 
                 Sentence.listToString(clValueTags, false, separator));
  }

  public void testTaggedWordListToString() {
    List<TaggedWord> tagged = new ArrayList<TaggedWord>();
    for (int i = 0; i < words.length; ++i) {
      tagged.add(new TaggedWord(words[i], tags[i]));
    }
    assertEquals(expectedValueOnly, Sentence.listToString(tagged, true));
    assertEquals(expectedTagged, 
                 Sentence.listToString(tagged, false, separator));
  }
}


