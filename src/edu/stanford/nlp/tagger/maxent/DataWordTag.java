/*
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Stanford University<p>
 */

package edu.stanford.nlp.tagger.maxent;

import edu.stanford.nlp.maxent.DataGeneric;


/**
 * @author Kristina Toutanova
 * @version 1.0
 */
public class DataWordTag extends DataGeneric {

  private final History h;
  private final int yNum;
  private final String tag;

  DataWordTag(History h, int y, String tag) {
    this.h = h;
    this.yNum = y;
    this.tag = tag;
  }


  public History getHistory() {
    return h;
  }

  @Override
  public String getY() {
    return tag;
  }

  public int getYInd() {
    return yNum;
  }

}
