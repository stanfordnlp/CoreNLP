package edu.stanford.nlp.ie.hmm;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.text.NumberFormat;

/**
 * Class to model a single state in an HMM.
 *
 * @author Jim McFadden
 */
public class State implements Serializable {

  /**
   * Creates new state, initializes transitions
   * and type to background.
   */
  public State(int numStates) {
    this(0, null, numStates);
  }

  /**
   * Creates new state, initializes transitions and type.
   */
  public State(int type, EmitMap emit, int numStates) {
    this(type, emit, new double[numStates]);
  }


  /**
   * Creates new state, initializes transitions and type.
   */
  public State(int type, EmitMap emit, double[] transition) {
    if (type < -2 || type > 1000) {
      System.err.println("State: warning -- suspicious type.");
    }
    this.type = type;
    if (type < 0 && emit != null) {
      System.err.println("State: warning non-null emit will be ignored.");
    } else {
      this.emit = emit;
    }
    if (transition == null) {
      System.err.println("State: warning null transition vector.");
    } else {
      this.transition = transition;
    }
  }


  /**
   * Creates a new state that is a copy of an old one.
   * This makes a deep copy of the transition probs, but just keeps a
   * pointer to the emissions.  This is useful when one is doing FSM-like
   * combination operations on HMMs.
   *
   * @param state The state to be cloned
   */
  public State(State state) {
    type = state.type;
    emit = state.emit;
    transition = new double[state.transition.length];
    for (int i = 0; i < state.transition.length; i++) {
      transition[i] = state.transition[i];
    }
  }


  /**
   * Returns the transitions and common emissions for this State, suitable for printing.
   *
   * @param index  the index in the state array of this state (since it doesn't know it).
   * @param target the name of the target field for this state, or null if it's not a target state
   */
  public String toVerboseString(int index, String target) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw, true);

    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMaximumFractionDigits(6);
    nf.setMinimumFractionDigits(6);

    pw.print("****** State " + index + " ****** ");

    if (type == State.FINISHTYPE) {
      pw.print("(FINISHSTATE) ******");
    } else if (type == State.STARTTYPE) {
      pw.print("(STARTSTATE) ******");
    } else if (target != null) {
      pw.print(target + " ******");
    }

    if (emit == null) {
      pw.println(" No emissions ******");
    } else {
      String cl = emit.getClass().getName();
      cl = cl.substring(cl.lastIndexOf('.') + 1);
      pw.println(" Type: " + cl + " ******");
      pw.println();
      emit.printEmissions(pw, true);
    }
    pw.println();

    pw.println("Transitions");
    pw.println("-----------");

    for (int j = 0; j < transition.length; j++) {
      if (transition[j] != 0.0) {
        pw.println("--> " + j + ": " + nf.format(transition[j]));
      }
    }
    pw.println();
    return (sw.toString());
  }


  /**
   * Print the sum of the transitions from a state.  Should normally be
   * almost 1.0.
   */
  public double transitionSum() {
    double total = 0.0;
    for (int j = 0; j < transition.length; j++) {
      total += transition[j];
    }
    return total;
  }


  @Override
  public String toString() {
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMaximumFractionDigits(4);
    double total = 0.0;
    double printTotal = 0.0;
    StringBuffer sb = new StringBuffer("State{type=");
    sb.append(type);
    for (int j = 0; j < transition.length; j++) {
      total += transition[j];
      if (transition[j] > 0.2) {
        printTotal += transition[j];
        sb.append(", --> ").append(j).append(": ");
        sb.append(nf.format(transition[j]));
      }
    }
    if (total > 1.001 || total < 0.999) {
      sb.append("... [unnormalized: sum " + nf.format(total) + "]}");
    } else if (printTotal < 0.999) {
      sb.append("...}");
    } else {
      sb.append("}");
    }
    return sb.toString();
  }


  /**
   * An integer type for a state, giving equivalence classes of states.
   * Types run from  -2 to (num_types - 1).  -2 is end state (no emissions).
   * -1 is start state (no emissions).  In general, types < 0 have no
   * emissions and special transitions.  In the IE world, type 0 is the
   * background state, types >= 1 are target types, and types after the end
   * of target types are other special non-target types.
   * This is a classification of states not a numbering.  It is used for the
   * restricted EM used in the HMM module.  If this feature is not desired,
   * one can simply give all tokens and states the same type, and then
   * regular EM will be done (give them a type between 0 and 1000
   * inclusive to avoid warning messages!).
   */
  public int type;

  public EmitMap emit;          // emission probabilities for this state

  public double[] transition;   // transition probabilities for this state

  public static final int STARTTYPE = -1;

  public static final int FINISHTYPE = -2;

  /**
   * This variable indicates a state numbering in a state array, not
   * a state type (unlike the TYPE variables above).
   */
  public static final int STARTIDX = 1;   // the start you only start in

  public static final int FINISHIDX = 0;  // you must finish here: PRG

  public static final int BKGRNDIDX = 2; // this is IE specific: background

  public static final long serialVersionUID = 2L;

}
