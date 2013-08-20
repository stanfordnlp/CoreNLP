package edu.stanford.nlp.ie.hmm;

import java.util.ArrayList;

/**
 * Class to model the structure of a target HMM.  This structure contains only
 * target states along with a start and end state
 *
 * @author Huy Nguyen (htnguyen@cs.stanford.edu)
 */

public class TargetStructure extends Structure {
  /**
   * 
   */
  private static final long serialVersionUID = -8657718167337155015L;

  /**
   * Constructs a new target structure using {@link #TargetStructure(int)
   * TargetStructure(1)}
   */
  public TargetStructure() {
    this(1);
  }

  /**
   * Constructs a target structure using {@link #TargetStructure(int,
          * boolean) TargetStructure(numStates,false)}
   */
  public TargetStructure(int numStates) {
    this(numStates, false);
  }

  public TargetStructure(TargetStructure struc) {
    if (struc.targets != null) {
      targets = new ArrayList(struc.targets);
    }
    if (struc.transitions != null) {
      transitions = copyList(struc.transitions);
    }
    stateTypes = new ArrayList(struc.stateTypes);
  }

  /**
   * Constructs a target structure
   * <tt>numStates</tt> is the number of target states in the target
   * structure
   * if <tt>ergodic</tt> is true, the target structure will be fully
   * connected.  Otherwise, it will be a chain.
   */
  public TargetStructure(int numStates, boolean ergodic) {
    super();
    if (numStates <= 0) {
      System.err.println("Number of target states has to be greater or equal to zero");
      throw(new IllegalArgumentException());
    }
    if (ergodic) {
      int[] stateTypes = new int[numStates];
      for (int i = 0; i < numStates; i++) {
        stateTypes[i] = TARGET_TYPE;
      }
      giveErgodic(stateTypes);
    } else {
      giveEmpty();
      int targetState = -1;
      for (int i = 0; i < numStates; i++) {
        if (targetState == -1) {
          targetState = insertStateAfter(State.STARTIDX, TARGET_TYPE);
          // for legacy structure learning operation
          // HN TODO: fix the target structure learning
          targets.add(Integer.valueOf(targetState));
        } else {
          targetState = insertStateAfter(targetState, TARGET_TYPE);
        }
      }
    }
  }

  @Override
  public Structure copy() {
    return new TargetStructure(this);
  }

  /**
   * Adds a new target chain to the target structure
   */
  @Override
  public void addTarget(int length) {
    int newTarget = insertStateBetween(State.STARTIDX, State.FINISHIDX, TARGET_TYPE);
    // add the new chain to the target chain list
    targets.add(Integer.valueOf(newTarget));
    // lengthen the chain
    for (int i = 1; i < length; i++) {
      insertStateAfter(newTarget, TARGET_TYPE);
    }
  }

}
