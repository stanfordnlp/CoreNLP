package edu.stanford.nlp.ie.pnp;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.ie.pnp.DataGenerator.Example;

/**
 * Command-line PNP test to measure human accuracy.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @see #main
 */
public class HumanClassifier extends Object {

  /**
   * Constructs a new HumanClassifier to test human accuracy at distinguishing
   * betwene the two given categories.
   */
  public HumanClassifier(String cat1Name, String cat2Name, String testFilename, String answerFilename) {
    try {
      System.out.println("Test your classifying powers!");
      List<Example> examples = new ArrayList<Example>();
      BufferedReader tests = new BufferedReader(new FileReader(testFilename));
      BufferedReader answers = new BufferedReader(new FileReader(answerFilename));
      String test;
      String answer;
      while ((test = tests.readLine()) != null && (answer = answers.readLine()) != null) {
        examples.add(new DataGenerator.Example(Integer.parseInt(answer), test));
      }
      Collections.shuffle(examples);

      List<String>[] errors = new ArrayList[3];
      for (int i = 0; i < errors.length; i++) {
        errors[i] = new ArrayList<String>();
      }
      BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
      int correct = 0;
      int total;
      for (total = 1; total <= examples.size(); total++) {
        DataGenerator.Example cur = examples.get(total - 1);

        System.out.println("Example " + total + "> " + cur.text);
        System.out.print("Category (1=" + cat1Name + ", 2=" + cat2Name + ", or \"quit\"): ");
        String response = in.readLine();
        if (response.equals("quit")) {
          System.out.println("You human classifiers have no patience! :)");
          total--;
          break;
        }

        try {
          int category = Integer.parseInt(response);
          if (category == cur.category) {
            System.out.println("Correct!");
            correct++;
          } else {
            System.out.println("Nope, sorry...");
            errors[cur.category].add(cur.text);
          }
        } catch (NumberFormatException e) {
          System.out.println("Invalid response.");
          errors[0].add(cur.text);
        }

        System.out.println("Your current score: " + correct + "/" + total + " (" + (100.0 * correct / total) + "%).");
        System.out.println();
      }

      if (total == examples.size() + 1) {
        System.out.println("That's all the examples I have. We're done.");
      }

      System.out.println("Your final score: " + correct + "/" + total + " (" + (100.0 * correct / total) + "%).");
      System.out.println("Errors by category:");
      for (int i = 1; i <= 2; i++) {
        System.out.println(i + ": " + errors[i].size());
      }
      if (errors[0].size() > 0) {
        System.out.println("You also gave " + errors[0].size() + " invalid responses.");
      }
      System.out.print("Would you like to see your specific errors (y|n)? ");
      if (in.readLine().toLowerCase().charAt(0) == 'y') {
        if (errors[1].size() > 0) {
          System.out.println("You thought the following " + cat1Name + " examples were " + cat2Name + " examples:");
          for (int i = 0; i < errors[1].size(); i++) {
            System.out.println(errors[1].get(i));
          }
          System.out.println();
        }
        if (errors[2].size() > 0) {
          System.out.println("You thought the following " + cat2Name + " examples were " + cat1Name + " examples:");
          for (int i = 0; i < errors[2].size(); i++) {
            System.out.println(errors[2].get(i));
          }
          System.out.println();
        }
        if (errors[0].size() > 0) {
          System.out.println("You gave invalid responses for the following examples:");
          for (int i = 0; i < errors[0].size(); i++) {
            System.out.println(errors[0].get(i));
          }
          System.out.println();
        }
      }
      System.out.println("Goodbye!");
      System.exit(0);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Runs the commans-line human tester.
   * <p><tt>Usage: java HumanClassifier cat1-name cat2-name testFilename answerFilename</tt></p>.
   * Only works for binary classification between two categories.
   */
  public static void main(String args[]) {
    if (args.length < 4) {
      System.err.println("Usage: java HumanClassifier cat1-name cat2-name testFilename answerFilename");
      System.exit(-1);
    }
    new HumanClassifier(args[0], args[1], args[2], args[3]);
  }
}
