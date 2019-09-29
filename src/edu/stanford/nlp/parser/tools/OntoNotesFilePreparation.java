package edu.stanford.nlp.parser.tools;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.objectbank.ObjectBank;

import java.io.PrintWriter;

/** Divide up OntoNotes v5 tree files in the standard way that has been used for CoNLL-2012, etc.
 *
 *  @author Christopher Manning
 */
public class OntoNotesFilePreparation {

  private static int rotator;

  private OntoNotesFilePreparation() {} // static main

  @SuppressWarnings("StatementWithEmptyBody")
  public static void main(String[] args) throws Exception {
    ObjectBank<String> ob = ObjectBank.getLineIterator(args[0]);
    PrintWriter train = IOUtils.getPrintWriter(args[0] + ".train");
    PrintWriter dev = IOUtils.getPrintWriter(args[0] + ".dev");
    PrintWriter test = IOUtils.getPrintWriter(args[0] + ".test");

    for (String line : ob) {
      if (line.endsWith(".")) {
      }
      if (line.contains("/wsj/")) {
        if (line.contains("/22/")) {
          dev.print(' ');
          dev.print(line);
        } else if (line.contains("/23/")) {
          test.print(' ');
          test.print(line);
        } else if (line.contains("/00/") || line.contains("/01/") || line.contains("/24/")) {
          // future use!
        } else {
          train.print(' ');
          train.print(line);
        }
      } else {
        // divide the files round robin 80-10-10
        if (rotator == 9) {
          test.print(' ');
          test.print(line);
        } else if (rotator == 8) {
          dev.print(' ');
          dev.print(line);
        } else {
          train.print(' ');
          train.print(line);
        }
        rotator = (rotator + 1) % 10;
      }
    }
    train.println();
    train.close();
    dev.println();
    dev.close();
    test.println();
    test.close();
  }

}
