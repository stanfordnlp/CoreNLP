package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;

import java.io.*;
import java.util.*;

/**
 * Maintains efficient indexing of unary grammar rules.
 *
 * @author Dan Klein
 * @author Christopher Manning
 */
public class UnaryGrammar implements Serializable, Iterable<UnaryRule> {

  private final Index<String> index;

  private transient List<UnaryRule>[] rulesWithParent; // = null;
  private transient List<UnaryRule>[] rulesWithChild; // = null;

  private transient List<UnaryRule>[] closedRulesWithParent; // = null;
  private transient List<UnaryRule>[] closedRulesWithChild; // = null;

  private transient UnaryRule[][] closedRulesWithP; // = null;
  private transient UnaryRule[][] closedRulesWithC; // = null;

  /** The basic list of UnaryRules.  Really this is treated as a set */
  private Map<UnaryRule,UnaryRule> coreRules; // = null;
  /** The closure of the basic list of UnaryRules.  Treated as a set */
  private transient Map<UnaryRule,UnaryRule> bestRulesUnderMax; // = null;

  // private transient Map<UnaryRule,Integer> backTrace = null;

  public int numClosedRules() {
    return bestRulesUnderMax.keySet().size();
  }

  public UnaryRule getRule(UnaryRule ur) {
    return coreRules.get(ur);
  }

  public Iterator<UnaryRule> closedRuleIterator() {
    return bestRulesUnderMax.keySet().iterator();
  }

  public int numRules() {
    return coreRules.keySet().size();
  }

  public Iterator<UnaryRule> iterator() {
    return ruleIterator();
  }

  public Iterator<UnaryRule> ruleIterator() {
    return coreRules.keySet().iterator();
  }

  public List<UnaryRule> rules() {
    return new ArrayList<>(coreRules.keySet());
  }

  /** Remove A -&gt; A UnaryRules from bestRulesUnderMax. */
  public final void purgeRules() {
    Map<UnaryRule,UnaryRule> bR = Generics.newHashMap();
    for (UnaryRule ur : bestRulesUnderMax.keySet()) {
      if (ur.parent != ur.child) {
        bR.put(ur, ur);
      } else {
        closedRulesWithParent[ur.parent].remove(ur);
        closedRulesWithChild[ur.child].remove(ur);
      }
    }
    bestRulesUnderMax = bR;
    makeCRArrays();
  }

  /* -----------------
  // Not needed any more as we reconstruct unaries in extractBestParse
  public List<Integer> getBestPath(int parent, int child) {
    List<Integer> path = new ArrayList<Integer>();
    UnaryRule tempR = new UnaryRule();
    tempR.parent = parent;
    tempR.child = child;
    //System.out.println("Building path...");
    int loc = parent;
    while (loc != child) {
      path.add(new Integer(loc));
      //System.out.println("Path is "+path);
      tempR.parent = loc;
      Integer nextInt = backTrace.get(tempR);
      if (nextInt == null) {
        loc = child;
      } else {
        loc = nextInt.intValue();
      }
      //System.out.println(Numberer.getGlobalNumberer(stateSpace).object(parent)+"->"+Numberer.getGlobalNumberer(stateSpace).object(child)+" went via "+Numberer.getGlobalNumberer(stateSpace).object(loc));
      if (path.size() > 10) {
        throw new RuntimeException("UnaryGrammar path > 10");
      }
    }
    path.add(new Integer(child));
    return path;
  }
  --------------------------- */

  private void closeRulesUnderMax(UnaryRule ur) {
    for (int i = 0, isz = closedRulesWithChild[ur.parent].size(); i < isz; i++) {
      UnaryRule pr = closedRulesWithChild[ur.parent].get(i);
      for (int j = 0, jsz = closedRulesWithParent[ur.child].size(); j < jsz; j++) {
        UnaryRule cr = closedRulesWithParent[ur.child].get(j);
        UnaryRule resultR = new UnaryRule(pr.parent, cr.child,
                                          pr.score + cr.score + ur.score);
        relaxRule(resultR);
        /* ----- No longer need to maintain unary rule backpointers
        if (relaxRule(resultR)) {
          if (resultR.parent != ur.parent) {
            backTrace.put(resultR, new Integer(ur.parent));
          } else {
            backTrace.put(resultR, new Integer(ur.child));
          }
        }
        -------- */
      }
    }
  }

  /** Possibly update the best way to make this UnaryRule in the
   *  bestRulesUnderMax hash and closedRulesWithX lists.
   *
   *  @param ur A UnaryRule with a score
   *  @return true if ur is the new best scoring case of that unary rule.
   */
  private boolean relaxRule(UnaryRule ur) {
    UnaryRule bestR = bestRulesUnderMax.get(ur);
    if (bestR == null) {
      bestRulesUnderMax.put(ur, ur);
      closedRulesWithParent[ur.parent].add(ur);
      closedRulesWithChild[ur.child].add(ur);
      return true;
    } else {
      if (bestR.score < ur.score) {
        bestR.score = ur.score;
        return true;
      }
      return false;
    }
  }

  public double scoreRule(UnaryRule ur) {
    UnaryRule bestR = bestRulesUnderMax.get(ur);
    return (bestR != null ? bestR.score : Double.NEGATIVE_INFINITY);
  }

  public final void addRule(UnaryRule ur) {
    // add rules' closure
    closeRulesUnderMax(ur);
    coreRules.put(ur, ur);
    rulesWithParent[ur.parent].add(ur);
    rulesWithChild[ur.child].add(ur);
  }

  //public Iterator closedRuleIterator() {
  //  return bestRulesUnderMax.keySet().iterator();
  //}

  private static final UnaryRule[] EMPTY_UNARY_RULE_ARRAY = new UnaryRule[0];

  void makeCRArrays() {
    int numStates = index.size();
    closedRulesWithP = new UnaryRule[numStates][];
    closedRulesWithC = new UnaryRule[numStates][];
    for (int i = 0; i < numStates; i++) {
      // cdm [2012]: Would it be faster to use same EMPTY_UNARY_RULE_ARRAY when of size zero?  It must be!
      closedRulesWithP[i] = closedRulesWithParent[i].toArray(new UnaryRule[closedRulesWithParent[i].size()]);
      closedRulesWithC[i] = closedRulesWithChild[i].toArray(new UnaryRule[closedRulesWithChild[i].size()]);
    }
  }

  public UnaryRule[] closedRulesByParent(int state) {
    if (state >= closedRulesWithP.length) {  // cdm [2012]: This check shouldn't be needed; delete
      return EMPTY_UNARY_RULE_ARRAY;
    }
    return closedRulesWithP[state];
  }

  public UnaryRule[] closedRulesByChild(int state) {
    if (state >= closedRulesWithC.length) {  // cdm [2012]: This check shouldn't be needed; delete
      return EMPTY_UNARY_RULE_ARRAY;
    }
    return closedRulesWithC[state];
  }

  public Iterator<UnaryRule> closedRuleIteratorByParent(int state) {
    if (state >= closedRulesWithParent.length) {
      List<UnaryRule> lur = Collections.emptyList();
      return lur.iterator();
    }
    return closedRulesWithParent[state].iterator();
  }

  public Iterator<UnaryRule> closedRuleIteratorByChild(int state) {
    if (state >= closedRulesWithChild.length) {
      List<UnaryRule> lur = Collections.emptyList();
      return lur.iterator();
    }
    return closedRulesWithChild[state].iterator();
  }

  public Iterator<UnaryRule> ruleIteratorByParent(int state) {
    if (state >= rulesWithParent.length) {
      List<UnaryRule> lur = Collections.emptyList();
      return lur.iterator();
    }
    return rulesWithParent[state].iterator();
  }

  public Iterator<UnaryRule> ruleIteratorByChild(int state) {
    if (state >= rulesWithChild.length) {
      List<UnaryRule> lur = Collections.emptyList();
      return lur.iterator();
    }
    return rulesWithChild[state].iterator();
  }

  public List<UnaryRule> rulesByParent(int state) {
    if (state >= rulesWithParent.length) {
      return Collections.emptyList();
    }
    return rulesWithParent[state];
  }

  public List<UnaryRule> rulesByChild(int state) {
    if (state >= rulesWithChild.length) {
      return Collections.emptyList();
    }
    return rulesWithChild[state];
  }

  public List<UnaryRule>[] rulesWithParent() {
    return rulesWithParent;
  }

  private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
    stream.defaultReadObject();
    Set<UnaryRule> allRules = Generics.newHashSet(coreRules.keySet());
    init();
    for (UnaryRule ur : allRules) {
      addRule(ur);
    }
    purgeRules();
  }

  /** Create all the array variables, and put in A -&gt; A UnaryRules to feed
   *  the closure algorithm. They then get deleted later.
   */
  @SuppressWarnings("unchecked")
  private void init() {
    int numStates = index.size();
    coreRules = Generics.newHashMap();
    rulesWithParent = new List[numStates];
    rulesWithChild = new List[numStates];
    closedRulesWithParent = new List[numStates];
    closedRulesWithChild = new List[numStates];
    bestRulesUnderMax = Generics.newHashMap();
    // backTrace = Generics.newHashMap();
    for (int s = 0; s < numStates; s++) {
      rulesWithParent[s] = new ArrayList<>();
      rulesWithChild[s] = new ArrayList<>();
      closedRulesWithParent[s] = new ArrayList<>();
      closedRulesWithChild[s] = new ArrayList<>();
      UnaryRule selfR = new UnaryRule(s, s, 0.0);
      relaxRule(selfR);
    }
  }

  public UnaryGrammar(Index<String> stateIndex) {
    this.index = stateIndex;
    init();
  }

  /**
   * Populates data in this UnaryGrammar from a character stream.
   *
   * @param in The Reader the grammar is read from.
   * @throws IOException If there is a reading problem
   */
  public void readData(BufferedReader in) throws IOException {
    String line;
    int lineNum = 1;
    // all lines have one rule per line
    line = in.readLine();
    while (line != null && line.length() > 0) {
      try {
        addRule(new UnaryRule(line, index));
      } catch (Exception e) {
        throw new IOException("Error on line " + lineNum);
      }
      lineNum++;
      line = in.readLine();
    }
    purgeRules();
  }

  /**
   * Writes out data from this Object.
   * @param w Data is written to this Writer
   */
  public void writeData(Writer w) {
    PrintWriter out = new PrintWriter(w);
    // all lines have one rule per line
    for (UnaryRule ur : this) {
      out.println(ur.toString(index));
    }
    out.flush();
  }

  /**
   * Writes out a lot of redundant data from this Object to the Writer w.
   * @param w Data is written to this Writer
   */
  public void writeAllData(Writer w) {
    int numStates = index.size();
    PrintWriter out = new PrintWriter(w);
    // all lines have one rule per line
    out.println("Unary ruleIterator");
    for (Iterator<UnaryRule> rI = ruleIterator(); rI.hasNext(); ) {
      out.println(rI.next().toString(index));
    }
    out.println("Unary closedRuleIterator");
    for (Iterator<UnaryRule> rI = closedRuleIterator(); rI.hasNext(); ) {
      out.println(rI.next().toString(index));
    }
    out.println("Unary rulesWithParentIterator");
    for (int i = 0; i < numStates; i++) {
      out.println(index.get(i));
      for (Iterator<UnaryRule> rI = ruleIteratorByParent(i); rI.hasNext(); ) {
        out.print("  ");
        out.println(rI.next().toString(index));
      }
    }
    out.println("Unary closedRulesWithParentIterator");
    for (int i = 0; i < numStates; i++) {
      out.println(index.get(i));
      for (Iterator<UnaryRule> rI = closedRuleIteratorByParent(i); rI.hasNext(); ) {
        out.print("  ");
        out.println(rI.next().toString(index));
      }
    }
    out.flush();
  }

  @Override
  public String toString() {
    Writer w = new StringWriter();
    writeData(w);
    return w.toString();
  }

  private static final long serialVersionUID = 1L;

} // end class UnaryGrammar
