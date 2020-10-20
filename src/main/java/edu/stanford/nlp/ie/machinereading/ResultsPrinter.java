package edu.stanford.nlp.ie.machinereading;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.CoreMap;

/**
 * Class for comparing the output of information extraction to a gold standard, and printing the results.
 * Subclasses may customize the formatting and content of the printout.
 *
 * @author mrsmith
 *
 */
public abstract class ResultsPrinter {

  /**
   * Given a set of sentences with annotations from an information extractor class, and the same sentences
   * with gold-standard annotations, print results on how the information extraction performed.
   */
  public String printResults(CoreMap goldStandard, CoreMap extractorOutput) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw, true);
    List<CoreMap> mutableGold = new ArrayList<>();
    mutableGold.addAll(goldStandard.get(CoreAnnotations.SentencesAnnotation.class));
    List<CoreMap> mutableOutput = new ArrayList<>();
    mutableOutput.addAll(extractorOutput.get(CoreAnnotations.SentencesAnnotation.class));
    printResults(pw, mutableGold, mutableOutput);
    return sw.getBuffer().toString();
  }

  public String printResults(List<String> goldStandard, List<String> extractorOutput) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw, true);
    printResultsUsingLabels(pw, goldStandard, extractorOutput);
    return sw.getBuffer().toString();
  }

  public abstract void printResults(PrintWriter pw, List<CoreMap> goldStandard, List<CoreMap> extractorOutput);

  public abstract void printResultsUsingLabels(PrintWriter pw,
                                               List<String> goldStandard,
                                               List<String> extractorOutput);

  /**
   * If the same set of sentences is contained in two lists, order the lists so that their sentences are in the same order (and return true).
   * Return false if the lists don't contain the same set of sentences.
   */
  public static void align(List<CoreMap> list1, List<CoreMap> list2) {
    boolean alignable = true;
    if (list1.size() != list2.size())
      alignable = false;

    class CompareSentences implements Comparator<CoreMap> {
      @Override
      public int compare(CoreMap sent1, CoreMap sent2) {
        String d1 = sent1.get(CoreAnnotations.DocIDAnnotation.class);
        String d2 = sent2.get(CoreAnnotations.DocIDAnnotation.class);
        if (d1 != null && d2 != null && !d1.equals(d2))
          return d1.compareTo(d2);

        String t1 = sent1.get(CoreAnnotations.TextAnnotation.class);
        String t2 = sent2.get(CoreAnnotations.TextAnnotation.class);
        return t1.compareTo(t2);
      }
    }
    Collections.sort(list1,new CompareSentences());
    Collections.sort(list2,new CompareSentences());

    for (int i = 0; i < list1.size(); i++) {
      if (!list1.get(i).get(CoreAnnotations.TextAnnotation.class).equals(list2.get(i).get(CoreAnnotations.TextAnnotation.class)))
        alignable = false;
    }

    if (!alignable) {
      throw new RuntimeException("ResultsPrinter.align: gold standard sentences don't match extractor output sentences!");
    }
  }

}
