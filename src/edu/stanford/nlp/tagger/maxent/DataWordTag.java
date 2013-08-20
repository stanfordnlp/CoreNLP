/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
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
  private final TTags ttags;


  DataWordTag(History h, int y, TTags ttags) {
    this.h = h;
    this.yNum = y;
    this.ttags = ttags;
  }


  public History getHistory() {
    return h;
  }

  // fill that with appropriate body
  @Override
  public String getY() {
    return ttags.getTag(yNum);
  }

  public int getYInd() {
    return yNum;
  }

}
