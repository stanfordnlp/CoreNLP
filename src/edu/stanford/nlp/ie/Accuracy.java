package edu.stanford.nlp.ie;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.StringTokenizer;

/**
 *   Read the results file and compute accuracy, precision, recall and F1 score.
 *   
 *  @author Vijay Krishnan
 */
public class Accuracy {

  public static void printAccuracy(String inputFile) throws IOException {

    BufferedReader br = new BufferedReader(new FileReader(inputFile));
    String line;
    double correct = 0;
    double total = 0;
    double OMarkedCorrectly = 0;
    double IMarkedCorrectly = 0;
    double OMarkedWrongly = 0;
    double IMarkedWrongly = 0;
    String correctLabel, predictedLabel;

    while ((line = br.readLine()) != null) {

      StringTokenizer st = new StringTokenizer(line);
      if (st.countTokens() < 3)
        continue;

      st.nextToken();

      total++;
      correctLabel = st.nextToken();
      predictedLabel = st.nextToken();

      if (correctLabel.equalsIgnoreCase("O")) {
        if (predictedLabel.equalsIgnoreCase("O")) {
          correct++;
          OMarkedCorrectly++;
        } else {
          OMarkedWrongly++;
        }

      } else {
        if (predictedLabel.equalsIgnoreCase(correctLabel)) {
          correct++;
          IMarkedCorrectly++;
        } else {
          IMarkedWrongly++;
        }

      }
    }

    System.out.println(inputFile);
    System.out.println("Accuracy= " + correct / total);
    double recall = IMarkedCorrectly / (IMarkedCorrectly + IMarkedWrongly);
    double precision = IMarkedCorrectly / (IMarkedCorrectly + OMarkedWrongly);

    double f1 = 2 * recall * precision / (recall + precision);

    System.out.println("recall= " + recall + "    precision= " + precision
        + "    F1=" + f1);
  }

  /**
   * @throws IOException 
   */
  public static void main(String[] args) throws IOException {
    // TODO Auto-generated method stub
    //	String inputFile ="/u/kvijay/data/ner/results/CMM_majority_vote_of_12_labelings.res";
    String inputFile = args[0];
    Accuracy.printAccuracy(inputFile);
  }

}
