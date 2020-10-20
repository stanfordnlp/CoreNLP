package edu.stanford.nlp.ie.machinereading.common; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.Generics;

public class StringDictionary  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(StringDictionary.class);

  public static class IndexAndCount {

    public final int mIndex;
    public int mCount;

    IndexAndCount(int i, int c) {
      mIndex = i;
      mCount = c;
    }
  }

  /** Name of this dictionary */
  private final String mName;

  /**
   * Access type: If true, create a dictionary entry if the entry does not exist
   * in get Otherwise, return -1 if the entry does not exist in get
   */
  private boolean mCreate;

  /** The actual dictionary */
  private Map<String, IndexAndCount> mDict;

  /** Inverse mapping from integer keys to the string values */
  private Map<Integer, String> mInverse;

  public StringDictionary(String name) {
    mName = name;
    mCreate = false;
    mDict = Generics.newHashMap();
    mInverse = Generics.newHashMap();
  }

  public void setMode(boolean mode) {
    mCreate = mode;
  }

  public int size() {
    return mDict.size();
  }

  public int get(String s) {
    return get(s, true);
  }

  public IndexAndCount getIndexAndCount(String s) {
    IndexAndCount ic = mDict.get(s);
    if (mCreate) {
      if (ic == null) {
        ic = new IndexAndCount(mDict.size(), 0);
        mDict.put(s, ic);
        mInverse.put(Integer.valueOf(ic.mIndex), s);
      }
      ic.mCount++;
    }
    return ic;
  }

  /**
   * Fetches the index of this string If mCreate is true, the entry is created
   * if it does not exist. If mCreate is true, the count of the entry is
   * incremented for every get If no entry found throws an exception if
   * shouldThrow == true
   */
  public int get(String s, boolean shouldThrow) {
    IndexAndCount ic = mDict.get(s);
    if (mCreate) {
      if (ic == null) {
        ic = new IndexAndCount(mDict.size(), 0);
        mDict.put(s, ic);
        mInverse.put(Integer.valueOf(ic.mIndex), s);
      }
      ic.mCount++;
    }
    if (ic != null)
      return ic.mIndex;

    if (shouldThrow) {
      throw new RuntimeException("Unknown entry \"" + s + "\" in dictionary \"" + mName + "\"!");
    } else {
      return -1;
    }
  }

  public static final String NIL_VALUE = "nil";

  /**
   * Reverse mapping from integer key to string value
   */
  public String get(int idx) {
    if (idx == -1)
      return NIL_VALUE;

    String s = mInverse.get(idx);
    if (s == null)
      throw new RuntimeException("Unknown index \"" + idx + "\" in dictionary \"" + mName + "\"!");
    return s;
  }

  public int getCount(int idx) {
    if (idx == -1)
      return 0;

    String s = mInverse.get(idx);
    if (s == null)
      throw new RuntimeException("Unknown index \"" + idx + "\" in dictionary \"" + mName + "\"!");

    return getIndexAndCount(s).mCount;
  }

  /**
   * Saves all dictionary entries that appeared {@literal >} threshold times Note: feature
   * indices are changed to contiguous values starting at 0. This is needed in
   * order to minimize the memory allocated for the expanded feature vectors
   * (average perceptron).
   */
  public void save(String path, String prefix, int threshold) throws java.io.IOException {

    String fileName = path + java.io.File.separator + prefix + "." + mName;
    java.io.PrintStream os = new java.io.PrintStream(new java.io.FileOutputStream(fileName));

    int index = 0;
    for (Map.Entry<String, IndexAndCount> entry : mDict.entrySet()) {
      IndexAndCount ic = entry.getValue();
      if (ic.mCount > threshold) {
        os.println(entry.getKey() + ' ' + index + ' ' + ic.mCount);
        index++;
      }
    }

    os.close();
    log.info("Saved " + index + "/" + mDict.size() + " entries for dictionary \"" + mName + "\".");
  }

  public void clear() {
    mDict.clear();
    mInverse.clear();
  }

  public Set<String> keySet() {
    return mDict.keySet();
  }

  /** Loads all saved dictionary entries from disk */
  public void load(String path, String prefix) throws java.io.IOException {

    String fileName = path + java.io.File.separator + prefix + "." + mName;
    try (BufferedReader is = IOUtils.readerFromString(fileName)) {

      for (String line; (line = is.readLine()) != null; ) {
        ArrayList<String> tokens = SimpleTokenize.tokenize(line);
        if (tokens.size() != 3) {
          throw new RuntimeException("Invalid dictionary line: " + line);
        }
        int index = Integer.parseInt(tokens.get(1));
        int count = Integer.parseInt(tokens.get(2));
        if (index < 0 || count <= 0) {
          throw new RuntimeException("Invalid dictionary line: " + line);
        }

        IndexAndCount ic = new IndexAndCount(index, count);
        mDict.put(tokens.get(0), ic);
        mInverse.put(Integer.valueOf(index), tokens.get(0));
      }

      log.info("Loaded " + mDict.size() + " entries for dictionary \"" + mName + "\".");
    }
  }

  public java.util.Set<String> keys() {
    return mDict.keySet();
  }

}
