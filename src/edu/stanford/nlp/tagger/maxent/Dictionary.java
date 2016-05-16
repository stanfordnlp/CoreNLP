/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 */
package edu.stanford.nlp.tagger.maxent; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.util.Generics;

import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Map;


/** Maintains a map from words to tags and their counts.
 *
 *  @author Kristina Toutanova
 *  @version 1.0
 */
public class Dictionary  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(Dictionary.class);

  private final Map<String,TagCount> dict = Generics.newHashMap();
  private final Map<Integer,CountWrapper> partTakingVerbs = Generics.newHashMap();
  private static final String naWord = "NA";
  private static final boolean VERBOSE = false;

  public Dictionary() {
  }

  void fillWordTagCounts(Map<String, IntCounter<String>> wordTagCounts) {
    for (String word : wordTagCounts.keySet()) {
      TagCount count = new TagCount(wordTagCounts.get(word));
      dict.put(word, count);
    }
  }

  /*
  public void release() {
    dict.clear();
  }

  public void addVPTaking(String verb, String tag, String partWord) {
    int h = verb.hashCode();
    Integer i = Integer.valueOf(h);
    if (tag.startsWith("RP")) {
      if (this.partTakingVerbs.containsKey(i)) {
        this.partTakingVerbs.get(i).incPart(partWord);
      } else {
        this.partTakingVerbs.put(i, new CountWrapper(verb, 0, 0, 0, 0));
        this.partTakingVerbs.get(i).incPart(partWord);
      }
    } else if (tag.startsWith("RB")) {
      if (this.partTakingVerbs.containsKey(i)) {
        this.partTakingVerbs.get(i).incRB(partWord);
      } else {
        this.partTakingVerbs.put(i, new CountWrapper(verb, 0, 0, 0, 0));
        this.partTakingVerbs.get(i).incRB(partWord);
      }
    } else if (tag.startsWith("IN")) {
      if (this.partTakingVerbs.containsKey(i)) {
        this.partTakingVerbs.get(i).incIn(partWord);
      } else {
        this.partTakingVerbs.put(i, new CountWrapper(verb, 0, 0, 0, 0));
        this.partTakingVerbs.get(i).incIn(partWord);
      }
    }
  }
  */

  protected void addVThatTaking(String verb) {
    int i = verb.hashCode();
    if (this.partTakingVerbs.containsKey(i)) {
      this.partTakingVerbs.get(i).incThat();
    } else {
      this.partTakingVerbs.put(i, new CountWrapper(verb, 0, 1, 0, 0));
    }
  }

  protected int getCountPart(String verb) {
    int i = verb.hashCode();
    if (this.partTakingVerbs.containsKey(i)) {
      return this.partTakingVerbs.get(i).getCountPart();
    }
    return 0;
  }


  protected int getCountThat(String verb) {
    int i = verb.hashCode();
    if (this.partTakingVerbs.containsKey(i)) {
      return this.partTakingVerbs.get(i).getCountThat();
    }
    return 0;
  }


  protected int getCountIn(String verb) {
    int i = verb.hashCode();
    if (this.partTakingVerbs.containsKey(i)) {
      return this.partTakingVerbs.get(i).getCountIn();
    }
    return 0;
  }


  protected int getCountRB(String verb) {
    int i = verb.hashCode();
    if (this.partTakingVerbs.containsKey(i)) {
      return this.partTakingVerbs.get(i).getCountRB();
    }
    return 0;
  }


  protected int getCount(String word, String tag) {
    TagCount count = dict.get(word);
    if (count == null) {
      return 0;
    } else {
      return count.get(tag);
    }
  }


  protected String[] getTags(String word) {
    TagCount count = get(word);
    if (count == null) {
      return null;
    }
    return count.getTags();
  }


  protected TagCount get(String word) {
    return dict.get(word);
  }


  String getFirstTag(String word) {
    TagCount count = dict.get(word);
    if (count != null) {
      return count.getFirstTag();
    }
    return null;
  }


  protected int sum(String word) {
    TagCount count = dict.get(word);
    if (count != null) {
      return count.sum();
    }
    return 0;
  }

  boolean isUnknown(String word) {
    return ! dict.containsKey(word);
  }


  /*
  public void save(String filename) {
    try {
      DataOutputStream rf = IOUtils.getDataOutputStream(filename);
      save(rf);
      rf.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  */

  void save(DataOutputStream file) {
    String[] arr = dict.keySet().toArray(new String[dict.keySet().size()]);
    try {
      file.writeInt(arr.length);
      log.info("Saving dictionary of " + arr.length + " words ...");
      for (String word : arr) {
        TagCount count = get(word);
        file.writeUTF(word);
        count.save(file);
      }
      Integer[] arrverbs = this.partTakingVerbs.keySet().toArray(new Integer[partTakingVerbs.keySet().size()]);
      file.writeInt(arrverbs.length);
      for (Integer iO : arrverbs) {
        CountWrapper tC = this.partTakingVerbs.get(iO);
        file.writeInt(iO.intValue());
        tC.save(file);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void read(DataInputStream rf, String filename) throws IOException {
    // Object[] arr=dict.keySet().toArray();

    int maxNumTags = 0;
    int len = rf.readInt();
    if (VERBOSE) {
      log.info("Reading Dictionary of " + len + " words from " + filename + '.');
    }

    for (int i = 0; i < len; i++) {
      String word = rf.readUTF();
      TagCount count = TagCount.readTagCount(rf);
      int numTags = count.numTags();
      if (numTags > maxNumTags) {
        maxNumTags = numTags;
      }
      this.dict.put(word, count);
      if (VERBOSE) {
        log.info("  " + word + " [idx=" + i + "]: " + count);
      }
    }
    if (VERBOSE) {
      log.info("Read dictionary of " + len + " words; max tags for word was " + maxNumTags + '.');
    }
  }

  private void readTags(DataInputStream rf) throws IOException {
    // Object[] arr=dict.keySet().toArray();

    int maxNumTags = 0;
    int len = rf.readInt();
    if (VERBOSE) {
      log.info("Reading Dictionary of " + len + " words.");
    }

    for (int i = 0; i < len; i++) {
      String word = rf.readUTF();
      TagCount count = TagCount.readTagCount(rf);
      int numTags = count.numTags();
      if (numTags > maxNumTags) {
        maxNumTags = numTags;
      }
      this.dict.put(word, count);
      if (VERBOSE) {
        log.info("  " + word + " [idx=" + i + "]: " + count);
      }
    }
    if (VERBOSE) {
      log.info("Read dictionary of " + len + " words; max tags for word was " + maxNumTags + '.');
    }
  }

  protected void read(String filename) {
    try {
      DataInputStream rf = IOUtils.getDataInputStream(filename);
      read(rf, filename);

      int len1 = rf.readInt();
      for (int i = 0; i < len1; i++) {
        int iO = rf.readInt();
        CountWrapper tC = new CountWrapper();
        tC.read(rf);

        this.partTakingVerbs.put(iO, tC);
      }
      rf.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  protected void read(DataInputStream file) {
    try {
      readTags(file);

      int len1 = file.readInt();
      for (int i = 0; i < len1; i++) {
        int iO = file.readInt();
        CountWrapper tC = new CountWrapper();
        tC.read(file);

        this.partTakingVerbs.put(iO, tC);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /*
  public void printAmbiguous() {
    String[] arr = dict.keySet().toArray(new String[dict.keySet().size()]);
    try {
      int countAmbiguous = 0;
      int countUnAmbiguous = 0;
      int countAmbDisamb = 0;
      for (String word : arr) {
        if (word.indexOf('|') == -1) {
          continue;
        }
        TagCount count = get(word);
        if (count.numTags() > 1) {
          System.out.print(word);
          countAmbiguous++;
          tC.print();
          System.out.println();
        } else {
          String wordA = word.substring(0, word.indexOf('|'));
          if (get(wordA).numTags() > 1) {
            System.out.print(word);
            countAmbDisamb++;
            countUnAmbiguous++;
            tC.print();
            System.out.println();
          } else {
            countUnAmbiguous++;
          }
        }// else
      }
      System.out.println(" ambg " + countAmbiguous + " unambg " + countUnAmbiguous + " disamb " + countAmbDisamb);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  */

  /**
   * This makes ambiguity classes from all words in the dictionary and remembers
   * their classes in the TagCounts
   */
  protected void setAmbClasses(AmbiguityClasses ambClasses, int veryCommonWordThresh, TTags ttags) {
    for (Map.Entry<String,TagCount> entry : dict.entrySet()) {
      String w = entry.getKey();
      TagCount count = entry.getValue();
      int ambClassId = ambClasses.getClass(w, this, veryCommonWordThresh, ttags);
      count.setAmbClassId(ambClassId);
    }
  }

  protected int getAmbClass(String word) {
    if (word.equals(naWord)) {
      return -2;
    }
    if (get(word) == null) {
      return -1;
    }
    return get(word).getAmbClassId();
  }

  public static void main(String[] args) {
    String s = "word";
    String tag = "tag";
    Dictionary d = new Dictionary();

    System.out.println(d.getCount(s, tag));
    System.out.println(d.getFirstTag(s));
  }

}
