package edu.stanford.nlp.ie.machinereading;

import java.text.DecimalFormat;
import java.util.*;
import java.io.*;

import edu.stanford.nlp.ie.machinereading.structure.AnnotationUtils;
import edu.stanford.nlp.ie.machinereading.structure.RelationMention;
import edu.stanford.nlp.ie.machinereading.structure.RelationMentionFactory;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

public class RelationExtractorResultsPrinter extends ResultsPrinter {
  
  protected boolean createUnrelatedRelations;
  
  protected final RelationMentionFactory relationMentionFactory;
  
  public RelationExtractorResultsPrinter(RelationMentionFactory factory) {
    this(factory, true);
  }
  
  public RelationExtractorResultsPrinter() {
    this(new RelationMentionFactory(), true);
  }
  
  public RelationExtractorResultsPrinter(boolean createUnrelatedRelations) {
    this(new RelationMentionFactory(), createUnrelatedRelations);
  }
  
  public RelationExtractorResultsPrinter(RelationMentionFactory factory, boolean createUnrelatedRelations) {
    this.createUnrelatedRelations = createUnrelatedRelations;
    this.relationMentionFactory = factory;
  }
  
  private static final int MAX_LABEL_LENGTH = 31;

  @Override
  public void printResults(PrintWriter pw, 
      List<CoreMap> goldStandard,
      List<CoreMap> extractorOutput) {
    ResultsPrinter.align(goldStandard, extractorOutput);
    
    // the mention factory cannot be null here
    assert relationMentionFactory != null: "ERROR: RelationExtractorResultsPrinter.relationMentionFactory cannot be null in printResults!";
    
    // Count predicted-actual relation type pairs
    Counter<Pair<String, String>> results = new ClassicCounter<>();
    ClassicCounter<String> labelCount = new ClassicCounter<>();
    
    // TODO: assumes binary relations
    for (int goldSentenceIndex = 0; goldSentenceIndex < goldStandard.size(); goldSentenceIndex++) {
      for (RelationMention goldRelation : AnnotationUtils.getAllRelations(relationMentionFactory, goldStandard.get(goldSentenceIndex), createUnrelatedRelations)) {
        CoreMap extractorSentence = extractorOutput.get(goldSentenceIndex);
        List<RelationMention> extractorRelations = AnnotationUtils.getRelations(relationMentionFactory, extractorSentence, goldRelation.getArg(0), goldRelation.getArg(1));
        labelCount.incrementCount(goldRelation.getType());
        for (RelationMention extractorRelation : extractorRelations) {
          results.incrementCount(new Pair<>(extractorRelation.getType(), goldRelation.getType()));
        }
      }
    }

    printResultsInternal(pw, results, labelCount);
  }
  
  private void printResultsInternal(PrintWriter pw, Counter<Pair<String, String>> results, ClassicCounter<String> labelCount) {
    ClassicCounter<String> correct = new ClassicCounter<>();
    ClassicCounter<String> predictionCount = new ClassicCounter<>();
    boolean countGoldLabels = false;
    if (labelCount == null) {
      labelCount = new ClassicCounter<>();
      countGoldLabels = true;
    }

    for (Pair<String, String> predictedActual : results.keySet()) {
      String predicted = predictedActual.first;
      String actual = predictedActual.second;
      if (predicted.equals(actual)) {
        correct.incrementCount(actual, results.getCount(predictedActual));
      }
      predictionCount.incrementCount(predicted, results.getCount(predictedActual));
      if (countGoldLabels) {
        labelCount.incrementCount(actual, results.getCount(predictedActual));	
      }
    }

    DecimalFormat formatter = new DecimalFormat();
    formatter.setMaximumFractionDigits(1);
    formatter.setMinimumFractionDigits(1);

    double totalCount = 0;
    double totalCorrect = 0;
    double totalPredicted = 0;
    pw.println("Label\tCorrect\tPredict\tActual\tPrecn\tRecall\tF");
    List<String> labels = new ArrayList<>(labelCount.keySet());
    Collections.sort(labels);
    for (String label : labels) {
      double numcorrect = correct.getCount(label);
      double predicted = predictionCount.getCount(label);
      double trueCount = labelCount.getCount(label);
      double precision = (predicted > 0) ? (numcorrect / predicted) : 0;
      double recall = numcorrect / trueCount;
      double f = (precision + recall > 0) ? 2 * precision * recall / (precision + recall) : 0.0;
      pw.println(StringUtils.padOrTrim(label, MAX_LABEL_LENGTH) + "\t" + numcorrect + "\t" + predicted + "\t" + trueCount + "\t"
          + formatter.format(precision * 100) + "\t" + formatter.format(100 * recall) + "\t"
          + formatter.format(100 * f));
      if (!RelationMention.isUnrelatedLabel(label)) {
        totalCount += trueCount;
        totalCorrect += numcorrect;
        totalPredicted += predicted;
      }
    }
    double precision = (totalPredicted > 0) ? (totalCorrect / totalPredicted) : 0;
    double recall = totalCorrect / totalCount;
    double f = (totalPredicted > 0 && totalCorrect > 0) ? 2 * precision * recall / (precision + recall) : 0.0;
    pw.println("Total\t" + totalCorrect + "\t" + totalPredicted + "\t" + totalCount + "\t"
        + formatter.format(100 * precision) + "\t" + formatter.format(100 * recall) + "\t" + formatter.format(100 * f));
  }

  public void printResultsUsingLabels(PrintWriter pw, 
      List<String> goldStandard,
      List<String> extractorOutput) {
    
    // Count predicted-actual relation type pairs
    Counter<Pair<String, String>> results = new ClassicCounter<>();
    assert(goldStandard.size() == extractorOutput.size());
    for(int i = 0; i < goldStandard.size(); i ++) 
      results.incrementCount(new Pair<>(extractorOutput.get(i), goldStandard.get(i)));
    
    printResultsInternal(pw, results, null);
  }
}
