package edu.stanford.nlp.ie.hmm;

import edu.stanford.nlp.ie.Corpus;
import edu.stanford.nlp.ie.TypedTaggedDocument;
import edu.stanford.nlp.ling.TypedTaggedWord;
import edu.stanford.nlp.math.ArrayMath;

import java.util.ArrayList;
import java.util.List;

/**
 * Class to model an HMM context structure.
 *
 * @author Huy Nguyen
 */
public class MultiStructure extends Structure {

  /**
   * 
   */
  private static final long serialVersionUID = -1840441221277563983L;

  private static final int INFINITY = 1000000;

  public static final int BASIC_INIT = 0;
  public static final int LEARNING_INIT = 1;

  //protected int[] basePrefixes;
  //protected int[] baseSuffixes;

  //the context states in the initial structure which we split off of
  protected List<Index> baseContexts; 
  //the state numbers for the first state in each context chain
  protected List<Integer> contexts; 

  private static final int BASE_TARGET_STATE = 3;

  private static class Index {
    public int i;
    public int j;

    public Index() {
    }

    public Index(int i, int j) {
      this.i = i;
      this.j = j;
    }
  }

  /**
   * copy constructor
   */
  public MultiStructure(MultiStructure ms) {
    // these arrays are only initialized in the structure learning constructor
    if (ms.prefixes != null) {
      prefixes = new ArrayList<Integer>(ms.prefixes);
    }
    /*if(ms.basePrefixes != null) {
        basePrefixes = new int[ms.basePrefixes.length];
        for(int i=0; i<ms.basePrefixes.length; i++) basePrefixes[i]=ms.basePrefixes[i];
    }*/

    if (ms.suffixes != null) {
      suffixes = new ArrayList<Integer>(ms.suffixes);
    }
    /*if(ms.baseSuffixes != null) {
        baseSuffixes = new int[ms.baseSuffixes.length];
        for(int i=0; i<ms.baseSuffixes.length; i++) baseSuffixes[i]=ms.baseSuffixes[i];
    }*/

    if (ms.baseContexts != null) {
      baseContexts = new ArrayList<Index>(ms.baseContexts);
    }
    if (ms.contexts != null) {
      contexts = new ArrayList<Integer>(ms.contexts);
    }

    transitions = copyList(ms.transitions);
    stateTypes = new ArrayList<Integer>(ms.stateTypes);
  }

  public MultiStructure(String[] targetFields) {
    this(targetFields, 0);
  }

  public MultiStructure(String[] targetFields, int numContextStates) {
    super();
    giveEmpty();
    int backgroundState = insertStateAfter(State.STARTIDX, BACKGROUND_TYPE);

    // because of the operations available for modifying the
    // structure, we must first insert a "seed" target from which we
    // split to create the other targets
    // Note: target types began at 1
    int firstTargetState = insertStateAfter(backgroundState, 1);
    // allow all the targets to transition back to the background state
    setWeight(firstTargetState, backgroundState, 1.0);

    for (int i = 1; i < targetFields.length; i++) {
      // create another target state by splitting off the "seed" target
      int targetState = splitState(firstTargetState, i + 1);
      // add context states surrounding the target
      for (int j = 0; j < numContextStates; j++) {
        insertStateBefore(targetState, PREFIX_TYPE);
        insertStateAfter(targetState, SUFFIX_TYPE);
      }
    }
    // we insert the context for the seed target afterwards so they
    // don't get connected to the other targets
    for (int i = 0; i < numContextStates; i++) {
      insertStateBefore(firstTargetState, PREFIX_TYPE);
      insertStateAfter(firstTargetState, SUFFIX_TYPE);
    }

    // allow every state to transition to end state
    // HN TODO: maybe we only want certain states to go to the end
    /*for(int i=0; i<transitions.size(); i++) {
        setWeight(i, END_STATE, 1.0);
        }*/
    // only background state can transition to end state
    setWeight(backgroundState, State.FINISHIDX, 1.0);
    // allow backgroundState to loop
    setWeight(backgroundState, backgroundState, 1.0);
  }

  /**
   * Constructor used to initialize a context structure for structure
   * learning.  Processes trainCorpus to determine the minimum distance
   * between each pair of target types.  If the distance is zero, the
   * two targets are connected. Else if the distance is less than THRESHOLD
   * (a private variable), then a context state is inserted between the
   * two targets.  Distances greater than THRESHOLD are generated
   * through a global background state
   */
  public MultiStructure(String[] targetFields, Corpus trainCorpus, int threshold) {
    this(targetFields, 0);

    // initialize structure learning vars
    int numTargets = targetFields.length;
    //basePrefixes = new int[numTargets];
    //baseSuffixes = new int[numTargets];
    baseContexts = new ArrayList<Index>();
    contexts = new ArrayList<Integer>();
    prefixes = new ArrayList<Integer>();
    suffixes = new ArrayList<Integer>();

    int[][] minDistances = minTypeDistances(targetFields, trainCorpus);
    int firstTarget = 3;

    for (int i = 0; i < minDistances.length; i++) {
      for (int j = 0; j < minDistances[i].length; j++) {
        int dist = minDistances[i][j];
        if (dist < threshold) {
          int newState = insertStateBetween(i + firstTarget, j + firstTarget, BACKGROUND_TYPE);
          // add self-transition for new state
          setWeight(newState, newState, 0.2);
          baseContexts.add(new Index(i, j));
          contexts.add(Integer.valueOf(newState));
        }
        // connect the two states if the min distance is 0
        if (dist == 0) {
          setWeight(i + firstTarget, j + firstTarget, 1.0);
        }
      }
    }
  }

  /* helper method that determines the minimum distance (directional)
     between two targets.  index 0 refers to the first target type
     (usually type 1) */
  private int[][] minTypeDistances(String[] targetFields, Corpus trainCorpus) {
    int numTargets = targetFields.length;
    int[][] minDists = new int[numTargets][numTargets];
    for (int i = 0; i < minDists.length; i++) {
      for (int j = 0; j < minDists.length; j++) {
        minDists[i][j] = INFINITY;
      }
    }

    for (int i = 0; i < trainCorpus.size(); i++) {
      TypedTaggedDocument doc = (TypedTaggedDocument) trainCorpus.get(i);
      int curType = 0; // the last target type we saw
      int curDist = 0; // the number of tokens since the last target type

      for (int j = 0; j < doc.size(); j++) {
        TypedTaggedWord word = (TypedTaggedWord) doc.get(j);
        int type = word.type();
        if (type > 0 && type != curType) {
          if (curType > 0)
          // if the new distance is less than previously recorded distance
          {
            if (curDist < minDists[curType - 1][type - 1]) {
              minDists[curType - 1][type - 1] = curDist;
            }
          }
          // reset count and type
          curDist = 0;
          curType = type;
        } else {
          curDist++;
        }
      }
    }

    return minDists;
  }

  /**
   * this version of addPrefix takes the target which you want to add a
   * prefix to.  Adds the prefix state between the target and the
   * global background stae
   */
  @Override
  public void addPrefix(int target) {
    int newPrefix;
    /*if(basePrefixes[target] != 0) {
        // just split of the existing base prefix
        newPrefix = splitState(basePrefixes[target]);
    } else {
        // insert a new prefix which is used as the base prefix for
        // this target*/
    newPrefix = insertStateBetween(State.BKGRNDIDX, BASE_TARGET_STATE + target, PREFIX_TYPE);
    //basePrefixes[target] = newPrefix;
    //}
    prefixes.add(Integer.valueOf(newPrefix));
  }

  /**
   * this version of addSuffix takes the target which you want to add a
   * suffix to.  Adds the suffix state between the target and the
   * global background state.
   */
  @Override
  public void addSuffix(int target) {
    int newSuffix;
    /*if(baseSuffixes[target] != 0) {
        // just split the existing base suffix
        newSuffix = splitState(baseSuffixes[target]);
    } else {
        // insert a new suffix which is used as the base suffix for
        // this target*/
    newSuffix = insertStateBetween(BASE_TARGET_STATE + target, State.BKGRNDIDX, SUFFIX_TYPE);
    //    baseSuffixes[target] = newSuffix;
    //}
    suffixes.add(Integer.valueOf(newSuffix));
  }

  public int numBaseContexts() {
    return baseContexts.size();
  }

  public int numContextChains() {
    return contexts.size();
  }

  /**
   * creates a new context chain by splitting the ith base context
   * created in the structure learning constructor
   */
  public void addContext(int i) {
    //Integer baseContext = (Integer) baseContexts.get(i);
    //int newContext = splitState(baseContext.intValue());
    Index index = baseContexts.get(i);
    int newContext = insertStateBetween(index.i, index.j, BACKGROUND_TYPE);
    contexts.add(Integer.valueOf(newContext));
  }

  /**
   * extends the specified context chain, where i is the ith context
   * chain created either in the structure learning constructor or
   * by the addContext method
   */
  public void lengthenContext(int i) {
    Integer context = contexts.get(i);
    insertStateAfter(context.intValue(), BACKGROUND_TYPE);
  }

  /**
   * Based on the existing transitions, makes and internally stores a
   * sensibly
   * extended version of the transition matrix with self-loops, etc.
   * Subsequent calls to {@link #getTransitions} will return this initialized
   * matrix.
   * if <tt>initType</tt>==MultiStructure.BASIC_INIT, uses @link Structure.initializeTransitions()
   * if <tt>initType</tt>==MultiStructure.LEARNING_INIT, does initialization for context learning
   */
  public void initializeTransitions(int initType) {
    if (initType == BASIC_INIT) {
      initializeTransitions();
      return;
    }
    double[][] t = new double[transitions.size()][transitions.size()];

    for (int i = 0; i < stateTypes.size(); i++) {
      int type = stateTypes.get(i).intValue();

      // start state 1 takes you to main background, prefix, or target states
      if (i == State.BKGRNDIDX) {
        t[State.STARTIDX][State.BKGRNDIDX] = 0.2;
      } else if (type > 0) {
        t[State.STARTIDX][i] = 0.05; // small possibility of going directly to target
      } else if (type == PREFIX_TYPE) {
        t[State.STARTIDX][i] = 0.05; // small possibility of going directly to prefix (for near start target)
      }

      ArrayList curTransitions = (ArrayList) transitions.get(i);
      switch (type) {
        //case START_TYPE:
        //handle all start transitions above
        case END_TYPE:
          for (int j = 0; j < curTransitions.size(); j++) {
            t[i][j] = ((Double) curTransitions.get(j)).doubleValue();
          }
          break;

        case BACKGROUND_TYPE:
          for (int j = 0; j < curTransitions.size(); j++) {
            Double weight = (Double) curTransitions.get(j);
            int curType = stateTypes.get(j).intValue();

            // transition to finish state
            if (j == State.FINISHIDX) {
              t[i][j] = 0.05;
            } else if (i == State.BKGRNDIDX) {
              // larger self loop for main background state
              if (i == j) {
                t[i][j] = 0.2;
              } else if (weight.doubleValue() != 0.0) {
                // background states likely to transition to other background states
                if (curType == BACKGROUND_TYPE) {
                  t[i][j] = 0.25;
                }
                // let main background transition into prefixes
                else if (curType == PREFIX_TYPE) {
                  t[i][j] = 0.1;
                } else {
                  t[i][j] = 0.01; // make other transitions unlikely
                }
              }
            } else {
              // smaller self loops for other context states
              if (i == j) {
                t[i][j] = 0.1;
              } else if (weight.doubleValue() != 0.0) {
                // background states likely to transition to other background states
                if (curType == BACKGROUND_TYPE) {
                  t[i][j] = 0.2;
                }
                // let context states transition into targets
                else if (curType > 0) {
                  t[i][j] = 0.25;
                } else {
                  t[i][j] = 0.01; // make other transitions unlikely
                }
              }
            }
          }
          break;

        case PREFIX_TYPE:
          for (int j = 0; j < curTransitions.size(); j++) {
            Double weight = (Double) curTransitions.get(j);
            // small self loop
            if (i == j) {
              t[i][j] = 0.1;
            }
            // else if there is a transition already specified
            else if (weight.doubleValue() != 0.0) {
              int curType = stateTypes.get(j).intValue();
              if (curType == PREFIX_TYPE) {
                t[i][j] = 0.25;
              } else if (curType > 0) {
                t[i][j] = 0.5; // if it's a target type
              } else {
                t[i][j] = 0.01; // make other transitions unlikely
              }
            }
          }
          break;

        case SUFFIX_TYPE:
          for (int j = 0; j < curTransitions.size(); j++) {
            Double weight = (Double) curTransitions.get(j);
            int curType = stateTypes.get(j).intValue();
            if (j == State.FINISHIDX) {
              t[i][j] = 0.05; // small possibility of ending early
            }
            // small self loop
            else if (j == State.BKGRNDIDX) {
              t[i][j] = 0.5;
            } else if (i == j) {
              t[i][j] = 0.1;
            } else if (weight.doubleValue() != 0.0) {
              if (curType == SUFFIX_TYPE) {
                t[i][j] = 0.25;
              } else {
                t[i][j] = 0.01; // make other transitions unlikely
              }
            }
          }
          break;
      }
      if (type > 0) { // if it is a target type
        for (int j = 0; j < curTransitions.size(); j++) {
          Double weight = (Double) curTransitions.get(j);
          if (j == State.FINISHIDX) {
            t[i][j] = 0.05; // small possibility of ending early
          } else if (j == State.BKGRNDIDX) {
            t[i][j] = 0.1; // smaller transition to main background state
          }
          // no self loops for place holders!
          else if (weight.doubleValue() != 0.0) {
            int curType = stateTypes.get(j).intValue();

            // transition to other targets
            if (curType > 0) {
              t[i][j] = 0.1;
            }
            // transition to suffixes and context states in between targets
            else if (curType == SUFFIX_TYPE || curType == BACKGROUND_TYPE) {
              t[i][j] = 0.5;
            } else {
              t[i][j] = 0.01; // make other transitions unlikely
            }
          }
        }
      }
    }

    for (int i = 0; i < t.length; i++) {
      ArrayMath.normalize(t[i]);
    }
    perturbTransitions(t);
    initializedTransitions = t;
  }

  /**
   * Tests that creation doesn't crash...
   */
  public static void main(String[] args) {
    MultiStructure ms = new MultiStructure(args);
    ms.getTransitions();
  }
}
