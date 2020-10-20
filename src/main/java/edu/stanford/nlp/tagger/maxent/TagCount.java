
/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 */

package edu.stanford.nlp.tagger.maxent;

import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Interner;

import java.io.IOException;
import java.util.Map;
import java.io.DataInputStream;
import java.io.DataOutputStream;


/**
 * This class was created to store the possible tags of a word along with how many times
 * the word appeared with each tag.
 *
 * @author Kristina Toutanova
 * @version 1.0
 */
class TagCount {

  private Map<String, Integer> map = Generics.newHashMap();
  private int ambClassId = -1; /* This is a numeric ID shared by all words that have the same set of possible tags. */

  private String[] getTagsCache; // = null;
  private int sumCache;

  private TagCount() { } // used internally

  TagCount(IntCounter<String> tagCounts) {
    for (String tag : tagCounts.keySet()) {
      map.put(tag, tagCounts.getIntCount(tag));
    }

    getTagsCache = map.keySet().toArray(new String[map.keySet().size()]);
    sumCache = calculateSumCache();
  }

  private static final String NULL_SYMBOL = "<<NULL>>";

  /**
   * Saves the object to the file.
   *
   * @param rf is a file handle
   *           Supposedly other objects will be written after this one in the file. The method does not close the file. The TagCount is saved at the current position.
   */
  protected void save(DataOutputStream rf) {
    try {
      rf.writeInt(map.size());
      for (String tag : map.keySet()) {
        if (tag == null) {
          rf.writeUTF(NULL_SYMBOL);
        } else {
          rf.writeUTF(tag);
        }
        rf.writeInt(map.get(tag));
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  public void setAmbClassId(int ambClassId) {
    this.ambClassId = ambClassId;
  }

  public int getAmbClassId() {
    return ambClassId;
  }

  /** A TagCount object's fields are read from the file. They are read from
   *  the current position and the file is not closed afterwards.
   */
  public static TagCount readTagCount(DataInputStream rf, Interner<String> interner) {
    try {
      TagCount tc = new TagCount();
      int numTags = rf.readInt();
      tc.map = Generics.newHashMap(numTags);

      for (int i = 0; i < numTags; i++) {
	String tag = rf.readUTF();
        int count = rf.readInt();

	if (tag.equals(NULL_SYMBOL)) 
          tag = null;
        else
          tag = interner.intern(tag);
	tc.map.put(tag, count);
      }

      tc.getTagsCache = tc.map.keySet().toArray(new String[tc.map.keySet().size()]);
      tc.sumCache = tc.calculateSumCache();
      return tc;
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  /**
   * @return the number of total occurrences of the word .
   */
  protected int sum() {
    return sumCache;
  }

  // Returns the number of occurrence of a particular tag.
  protected int get(String tag) {
    Integer count = map.get(tag);
    if (count == null) {
      return 0;
    }
    return count;
  }

  private int calculateSumCache() {
    int s = 0;
    for (Integer i : map.values()) {
      s += i;
    }
    return s;
  }

  /**
   * @return an array of the tags the word has had.
   */
  public String[] getTags() {
    return getTagsCache; //map.keySet().toArray(new String[0]);
  }


  protected int numTags() { return map.size(); }


  /**
   * @return the most frequent tag.
   */
  public String getFirstTag() {
    String maxTag = null;
    int max = 0;
    for (String tag : map.keySet()) {
      int count = map.get(tag);
      if (count > max) {
        maxTag = tag;
        max = count;
      }
    }
    return maxTag;
  }

  @Override
  public String toString() {
    return map.toString();
  }

}

