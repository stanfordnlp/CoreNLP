package edu.stanford.nlp.scenegraph;

import java.io.IOException;
import java.util.Map;

import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

public class Sandbox {


  private static void printMostDiscriminativeSyntacticPatterns(String pathToModel) throws ClassNotFoundException, IOException {
    LinearClassifier<String, String> linearClassifier = IOUtils.readObjectFromFile(pathToModel);

    Map<String, Counter<String>> weights = linearClassifier.weightsAsMapOfCounters();
    Counter<String> positivePatterns = new ClassicCounter<String>();
    Counter<String> negativePatterns = new ClassicCounter<String>();

    for (String label : weights.keySet()) {
      boolean neg = label.equals(BoWSceneGraphParser.NONE_RELATION);

      for (String feat : weights.get(label).keySet()) {

        if ( ! feat.startsWith("sPath")) {
          continue;
        }

        //if (weights.get(label).getCount(feat) > 0) {
        if (neg) {
          negativePatterns.incrementCount(feat, weights.get(label).getCount(feat));
        } else {
          positivePatterns.incrementCount(feat, weights.get(label).getCount(feat));
        }
      }

    }

    System.err.println("POSITIVE PATTERNS:");

    System.err.println(Counters.toSortedString(positivePatterns, Integer.MAX_VALUE, "%1$s %2$f", "\n"));

    System.err.println("NEGATIVE PATTERNS:");
    System.err.println(Counters.toSortedString(negativePatterns, Integer.MAX_VALUE, "%1$s %2$f", "\n"));




  }


  public static void main(String args[]) throws ClassNotFoundException, IOException {
    printMostDiscriminativeSyntacticPatterns(args[0]);
  }

}
