/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 * @author Kristina Toutanova
 * @version 1.0
 */


package old.edu.stanford.nlp.maxent;

/**
 * A class representing a data item with an array of inputs X and a String classification Y
 */
public abstract class DataGeneric {

  public abstract void setX(Object[] x);

  public abstract String getY();

  public abstract void setY(String y);

}
