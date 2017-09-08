/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 */
package old.edu.stanford.nlp.tagger.maxent;

import old.edu.stanford.nlp.io.InDataStreamFile;
import old.edu.stanford.nlp.io.OutDataStreamFile;

import java.io.IOException;
import java.io.DataInputStream;
import java.util.HashMap;


/** Maintains a map from words to tags and their counts.
 *
 *  @author Kristina Toutanova
 *  @version 1.0
 */
public class Dictionary {

  private final HashMap<String,TagCount> dict = new HashMap<String,TagCount>();
  private final HashMap<Integer,CountWrapper> partTakingVerbs = new HashMap<Integer,CountWrapper>();
  private static final String naWord = "NA";

  public Dictionary() {
  }

  protected void add(String word, String tag) {
    if (dict.containsKey(word)) {
      TagCount cT = dict.get(word);
      cT.add(tag);
      return;
    }
    TagCount cT = new TagCount();
    cT.add(tag);
    dict.put(word, cT);
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
    TagCount tc = dict.get(word);
    if (tc == null) {
      return 0;
    } else {
      return tc.get(tag);
    }
  }


  protected String[] getTags(String word) {
    TagCount tC = get(word);
    if (tC == null) {
      return null;
    }
    return tC.getTags();
  }


  protected TagCount get(String word) {
    return dict.get(word);
  }


  String getFirstTag(String word) {
    if (dict.containsKey(word)) {
      return dict.get(word).getFirstTag();
    }
    return null;
  }


  protected int sum(String word) {
    if (dict.containsKey(word)) {
      return dict.get(word).sum();
    }
    return 0;
  }

  boolean isUnknown(String word) {
    return ! dict.containsKey(word);
  }


  /*
  public void save(String filename) {
    try {
      OutDataStreamFile rf = new OutDataStreamFile(filename);
      save(rf);
      rf.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  */

  void save(OutDataStreamFile file) {
    String[] arr = dict.keySet().toArray(new String[dict.keySet().size()]);
    try {
      file.writeInt(arr.length);
      System.err.println("Saving dictionary of " + arr.length + " words ...");
      for (String word : arr) {
        TagCount tC = get(word);
        file.writeUTF(word);
        tC.save(file);
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
    if (TestSentence.VERBOSE) {
      System.err.println("Reading Dictionary of " + len + " words from " + filename + '.');
    }

    for (int i = 0; i < len; i++) {
      String word = rf.readUTF();
      TagCount tC = new TagCount();
      tC.read(rf);
      int numTags = tC.numTags();
      if (numTags > maxNumTags) {
        maxNumTags = numTags;
      }
      this.dict.put(word, tC);
      if (TestSentence.VERBOSE) {
        System.err.println("  " + word + " [idx=" + i + "]: " + tC);
      }
    }
    if (TestSentence.VERBOSE) {
      System.err.println("Read dictionary of " + len + " words; max tags for word was " + maxNumTags + '.');
    }
  }

  private void readTags(DataInputStream rf) throws IOException {
    // Object[] arr=dict.keySet().toArray();

    int maxNumTags = 0;
    int len = rf.readInt();
    if (TestSentence.VERBOSE) {
      System.err.println("Reading Dictionary of " + len + " words.");
    }

    for (int i = 0; i < len; i++) {
      String word = rf.readUTF();
      TagCount tC = new TagCount();
      tC.read(rf);
      int numTags = tC.numTags();
      if (numTags > maxNumTags) {
        maxNumTags = numTags;
      }
      this.dict.put(word, tC);
      if (TestSentence.VERBOSE) {
        System.err.println("  " + word + " [idx=" + i + "]: " + tC);
      }
    }
    if (TestSentence.VERBOSE) {
      System.err.println("Read dictionary of " + len + " words; max tags for word was " + maxNumTags + '.');
    }
  }

  protected void read(String filename) {
    try {
      InDataStreamFile rf = new InDataStreamFile(filename);
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
        TagCount tC = get(word);
        if (tC.numTags() > 1) {
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

  protected void setAmbClasses() {
    String[] arr = dict.keySet().toArray(new String[dict.keySet().size()]);
    for (String w : arr) {
      int ambClassId = GlobalHolder.ambClasses.getClass(w);
      dict.get(w).setAmbClassId(ambClassId);
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
