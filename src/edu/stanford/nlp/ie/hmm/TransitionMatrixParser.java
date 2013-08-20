package edu.stanford.nlp.ie.hmm;

import java.io.*;

/**
 * Parses a printed out transition matrix into a State array.
 * State 0 is finish, State 1 is start, and rest are background.
 * Can't parse emissions, only transition matrix.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public class TransitionMatrixParser {
  /**
   * Parses the transition matrix in the given file.
   *
   * @see #parseTransitionMatrix(Reader)
   */
  public static State[] parseTransitionMatrix(String inputFilename) throws FileNotFoundException, IOException {
    return (parseTransitionMatrix(new FileReader(inputFilename)));
  }

  /**
   * Parses the transition matrix in the given file.
   *
   * @see #parseTransitionMatrix(Reader)
   */
  public static State[] parseTransitionMatrix(File inputFile) throws FileNotFoundException, IOException {
    return (parseTransitionMatrix(new FileReader(inputFile)));
  }

  /**
   * Reads in and parses a transition matrix.
   * The format is from {@link HMM#printTransitions} and only the transition
   * matrix itself must be in the input.
   */
  public static State[] parseTransitionMatrix(Reader input) throws IOException {
    // ensures the reader is buffered
    BufferedReader br;
    if (!(input instanceof BufferedReader)) {
      input = new BufferedReader(input);
    }
    br = (BufferedReader) input;

    State[] states = null;
    int numStates = 0;
    int currentRow = 0;
    String line;
    int curTargetType = 0; // each asterisk is the next highest target num
    while ((line = br.readLine()) != null) {
      line = line.trim(); // removes surrounding whitespace
      if (line.length() == 0) {
        continue; // skips blank lines
      }

      if (states == null) {
        // first line -> just count numStates
        String[] stateLabels = line.split(" +");
        numStates = stateLabels.length;
        states = new State[numStates];
      } else {
        // main line -> skip first token (label) and parse rest
        String[] tokens = line.split(" +");
        int type;
        if (currentRow == 0) {
          type = State.FINISHTYPE;
        } else if (currentRow == 1) {
          type = State.STARTTYPE;
        } else if (tokens[0].length() > 0 && tokens[0].charAt(0) == '*') {
          type = (++curTargetType);
        } else {
          type = 0;
        }
        states[currentRow] = new State(type, null, numStates);

        for (int i = 1; i < tokens.length; i++) {
          double value;
          if ("-".equals(tokens[i])) {
            value = 0.0;
          } else if ("=".equals(tokens[i])) {
            value = 0.001; // arbitrary
          } else {
            try {
              value = Double.parseDouble(tokens[i]);
            } catch (NumberFormatException e) {
              e.printStackTrace();
              value = 0.0;
            }
          }
          states[currentRow].transition[i - 1] = value;
        }
        if (currentRow++ == numStates) {
          break;
        }
      }
    }
    return (states);
  }

  /**
   * For internal debugging purposes only.
   * <p><tt>Usage: java TransitionMatrixParser transitionfile</tt></p>
   */
  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      System.err.println("Usage: java TransitionMatrixParser transitionfile");
      System.exit(1);
    }
    parseTransitionMatrix(args[0]);
  }
}

