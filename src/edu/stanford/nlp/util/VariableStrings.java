package edu.stanford.nlp.util;

import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.util.ArrayMap;
import edu.stanford.nlp.util.MapFactory;
import edu.stanford.nlp.util.MutableInteger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** A class that takes care of the stuff necessary for variable strings.
 *
 *  @author Roger Levy (rog@nlp.stanford.edu)
 */
public class VariableStrings {
  private final Map<String, String> varsToStrings;
  private final IntCounter<String> numVarsSet;

  /** Creates a VariableStrings with no variables set. */
  public VariableStrings() {
    varsToStrings = ArrayMap.newArrayMap();
    numVarsSet = new IntCounter<>(MapFactory.<String, MutableInteger>arrayMapFactory());
  }

  /**
   * Creates a copy of the given VariableStrings, with the same strings and set counts.
   *
   * @param other the VariableStrings to copy
   */
  public VariableStrings(VariableStrings other) {
    varsToStrings = new ArrayMap<>(other.varsToStrings);
    numVarsSet = new IntCounter<>(other.numVarsSet);
  }

  /** Unsets all variables, removing their strings and counts. */
  public void reset() {
    numVarsSet.clear();
    varsToStrings.clear();
  }

  /**
   * Returns whether the variable is currently set, that is, has a positive set count.
   *
   * @param o the variable name
   * @return true if the variable is set
   */
  public boolean isSet(String o) {
    return numVarsSet.getCount(o) >= 1;
  }

  /**
   * Sets the variable to the given string and increments its set count.
   * A variable may be set again only to an equal string.
   *
   * @param var the variable name
   * @param string the value of the variable
   * @throws RuntimeException if the variable already has a different string
   */
  public void setVar(String var, String string) {
    String oldString = varsToStrings.put(var,string);
    if(oldString != null && ! oldString.equals(string))
      throw new RuntimeException("Error -- can't setVar to a different string -- old: " + oldString + " new: " + string);
    numVarsSet.incrementCount(var);
  }

  /**
   * Sets every variable which is set in {@code other} to its string there,
   * adding {@code other}'s set count to this one's.
   *
   * @param other the VariableStrings whose set variables are copied
   * @throws RuntimeException if a variable already has a different string here
   */
  public void setVars(VariableStrings other) {
    for (String var : other.numVarsSet.keySet()) {
      int count = other.numVarsSet.getIntCount(var);
      if (count <= 0) {
        continue;
      }
      String newString = other.varsToStrings.get(var);
      String oldString = varsToStrings.put(var, newString);
      if (oldString != null && !oldString.equals(newString)) {
        throw new RuntimeException("Error -- can't setVars to a different string -- old: " + oldString + " new: " + newString);
      }
      numVarsSet.incrementCount(var, count);
    }
  }

  /**
   * Decrements the variable's set count (if positive), and removes its string
   * when the count reaches 0.
   *
   * @param var the variable name
   */
  public void unsetVar(String var) {
    if(numVarsSet.getCount(var) > 0)
      numVarsSet.decrementCount(var);
    if(numVarsSet.getCount(var)==0)
      varsToStrings.put(var,null);
  }

  /**
   * For every variable which is set in {@code other}, subtracts {@code other}'s
   * set count from this one's, removing the string if the count drops to 0 or below.
   *
   * @param other the VariableStrings whose set variables are unset
   */
  public void unsetVars(VariableStrings other) {
    for (String var : other.numVarsSet.keySet()) {
      int count = other.numVarsSet.getIntCount(var);
      if (count <= 0) {
        continue;
      }
      int newCount = numVarsSet.decrementCount(var, count);
      if (newCount <= 0) {
        varsToStrings.put(var, null);
      }
    }
  }

  /**
   * Returns the string of the given variable.
   *
   * @param var the variable name
   * @return the variable's string, or null if it is not set
   */
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

  /**
   * Return a Collection of all the variables which are currently set
   * (unset, null variables are skipped)
   *
   * @return a new sorted list of the names of the set variables
   */
  public Collection<String> getVariableNames() {
    List<String> vars = new ArrayList<>();
    for (String key : varsToStrings.keySet()) {
      if (isSet(key)) {
        vars.add(key);
      }
    }
    Collections.sort(vars);
    return vars;
  }
}
