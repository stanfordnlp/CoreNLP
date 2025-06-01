package edu.stanford.nlp.util;

import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.util.ArrayMap;
import edu.stanford.nlp.util.MapFactory;
import edu.stanford.nlp.util.MutableInteger;

import java.util.Map;

/** A class that takes care of the stuff necessary for variable strings.
 *
 *  @author Roger Levy (rog@nlp.stanford.edu)
 */
public class VariableStrings {
  private final Map<String, String> varsToStrings;
  private final IntCounter<String> numVarsSet;

  public VariableStrings() {
    varsToStrings = ArrayMap.newArrayMap();
    numVarsSet = new IntCounter<>(MapFactory.<String, MutableInteger>arrayMapFactory());
  }

  public VariableStrings(VariableStrings other) {
    varsToStrings = new ArrayMap<>(other.varsToStrings);
    numVarsSet = new IntCounter<>(other.numVarsSet);
  }

  public void reset() {
    numVarsSet.clear();
    varsToStrings.clear();
  }

  public boolean isSet(String o) {
    return numVarsSet.getCount(o) >= 1;
  }

  public void setVar(String var, String string) {
    String oldString = varsToStrings.put(var,string);
    if(oldString != null && ! oldString.equals(string))
      throw new RuntimeException("Error -- can't setVar to a different string -- old: " + oldString + " new: " + string);
    numVarsSet.incrementCount(var);
  }

  public void unsetVar(String var) {
    if(numVarsSet.getCount(var) > 0)
      numVarsSet.decrementCount(var);
    if(numVarsSet.getCount(var)==0)
      varsToStrings.put(var,null);
  }

  public String getString(String var) {
    return varsToStrings.get(var);
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    s.append("{");
    boolean appended = false;
    for (String key : varsToStrings.keySet()) {
      if (appended) {
        s.append(",");
      } else {
        appended = true;
      }
      s.append(key);
      s.append("=(");
      s.append(varsToStrings.get(key));
      s.append(":");
      s.append(numVarsSet.getCount(key));
      s.append(")");
    }
    s.append("}");
    return s.toString();
  }

}
