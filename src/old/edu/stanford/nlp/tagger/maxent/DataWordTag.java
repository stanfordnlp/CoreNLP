
/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 * @author Kristina Toutanova
 * @version 1.0
 */


package old.edu.stanford.nlp.tagger.maxent;

import old.edu.stanford.nlp.maxent.DataGeneric;


public class DataWordTag extends DataGeneric {

  private final History h;
  private int yNum;


  public History getHistory() {
    return h;
  }

  @Override
  public void setX(Object[] x) {
  }


  // fill that with appropriate body
  @Override
  public String getY() {
    return GlobalHolder.tags.getTag(yNum);
  }

  public int getYInd() {
    return yNum;
  }

  //fill that as well;
  @Override
  public final void setY(String y) {
    this.yNum = GlobalHolder.tags.getIndex(y);
  }


  DataWordTag(History h, int y) {
    this.h = h;
    yNum = y;

  }

}
