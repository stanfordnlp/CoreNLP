package edu.stanford.nlp.semgraph.semgrex;

import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.util.Generics;

import java.util.Map;

/** a class that takes care of the stuff necessary for variable strings.
 *
 * // todo: if this is just a copy of the tregex one, use same for both?
 *
 * @author Roger Levy (rog@nlp.stanford.edu)
 */
class VariableStrings {
  private Map<Object, String> varsToStrings;
  private IntCounter<Object> numVarsSet;

  public VariableStrings() {
    varsToStrings = Generics.newHashMap();
    numVarsSet = new IntCounter<>();
  }

  public boolean isSet(Object o) {
    return numVarsSet.getCount(o) == 1;
  }

  public void setVar(Object var, String string) {
    String oldString = varsToStrings.put(var,string);
    if(oldString != null && ! oldString.equals(string))
      throw new RuntimeException("Error -- can't setVar to a different string -- old: " + oldString + " new: " + string);
    numVarsSet.incrementCount(var);
  }

  public void unsetVar(Object var) {
    if(numVarsSet.getCount(var) > 0)
      numVarsSet.decrementCount(var);
    if(numVarsSet.getCount(var)==0)
      varsToStrings.put(var,null);
  }

  public String getString(Object var) {
    return varsToStrings.get(var);
  }

}
