/*
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Stanford University<p>
 */

package edu.stanford.nlp.tagger.maxent;

import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.HashIndex;

/**
 * This maintains a two way lookup between a History and
 * an Integer index.
 *
 * @author Kristina Toutanova
 * @version 1.0
 */
public class HistoryTable {

  // todo cdm: just remove this class and use the Index<History> directly where uses of it appears?

  private static final int capacity = 1000000;
  private final Index<History> idx;

  public HistoryTable() {
    idx = new HashIndex<>(capacity);
  }

  void release() {
    idx.clear();
  }

  int add(History h) {
    return idx.addToIndex(h);
  }

  History getHistory(int index) {
    return idx.get(index);
  }

  int getIndex(History h) {
    return idx.indexOf(h);
  }

  int size() {
    return idx.size();
  }

}
