package edu.stanford.nlp.stats;

import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.classify.GeneralDataset;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Triple;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Extension of MultiClassPrecisionRecallStats that also computes accuracy
 * @author Angel Chang
 */
public class MultiClassPrecisionRecallExtendedStats<L> extends MultiClassPrecisionRecallStats<L> {
  protected IntCounter<L> correctGuesses;
  protected IntCounter<L> foundCorrect;
  protected IntCounter<L> foundGuessed;
  protected int tokensCount = 0;
  protected int tokensCorrect = 0;
  protected int noLabel = 0;

  protected Function<String,L> stringConverter;

  public <F> MultiClassPrecisionRecallExtendedStats(Classifier<L,F> classifier, GeneralDataset<L,F> data, L negLabel)
  {
    super(classifier, data, negLabel);
  }

  public MultiClassPrecisionRecallExtendedStats(L negLabel)
  {
    super(negLabel);
  }

  public MultiClassPrecisionRecallExtendedStats(Index<L> dataLabelIndex, L negLabel)
  {
    this(negLabel);
    setLabelIndex(dataLabelIndex);
  }

  public void setLabelIndex(Index<L> dataLabelIndex) {
    labelIndex = dataLabelIndex;
    negIndex = labelIndex.indexOf(negLabel);
  }

  public <F> double score(Classifier<L,F> classifier, GeneralDataset<L,F> data) {
    labelIndex = new HashIndex<L>();
    labelIndex.addAll(classifier.labels());
    labelIndex.addAll(data.labelIndex.objectsList());
    clearCounts();
    int[] labelsArr = data.getLabelsArray();
    for (int i = 0; i < data.size(); i++) {
      Datum<L, F> d = data.getRVFDatum(i);
      L guess = classifier.classOf(d);
      addGuess(guess, labelIndex.get(labelsArr[i]));
    }
    finalizeCounts();

    return getFMeasure();
  }

  /**
   * Returns the score (F1) for the given list of guesses
   * @param guesses - Guesses by classifier
   * @param trueLabels - Gold labels to compare guesses against
   * @param dataLabelIndex - Index of labels
   * @return F1 score
   */
  public double score(List<L> guesses, List<L> trueLabels, Index<L> dataLabelIndex) {
    setLabelIndex(dataLabelIndex);
    return score(guesses, trueLabels);
  }

  /**
   * Returns the score (F1) for the given list of guesses
   * @param guesses - Guesses by classifier
   * @param trueLabels - Gold labels to compare guesses against
   * @return F1 score
   */
  public double score(List<L> guesses, List<L> trueLabels) {
    clearCounts();
    addGuesses(guesses, trueLabels);
    finalizeCounts();
    return getFMeasure();
  }

  public double score()
  {
    finalizeCounts();
    return getFMeasure();    
  }

  public void clearCounts()
  {
    if (foundCorrect != null) {
      foundCorrect.clear();
    } else {
      foundCorrect = new IntCounter<L>();
    }
    if (foundGuessed != null) {
      foundGuessed.clear();
    } else {
      foundGuessed = new IntCounter<L>();
    }
    if (correctGuesses != null) {
      correctGuesses.clear();
    } else {
      correctGuesses = new IntCounter<L>();
    }
    if (tpCount != null) {
      Arrays.fill(tpCount, 0);
    }
    if (fnCount != null) {
      Arrays.fill(fnCount, 0);
    }
    if (fpCount != null) {
      Arrays.fill(fpCount, 0);
    }
    tokensCount = 0;
    tokensCorrect = 0;
  }

  protected void finalizeCounts()
  {
    negIndex = labelIndex.indexOf(negLabel);
    int numClasses = labelIndex.size();
    if (tpCount == null || tpCount.length != numClasses) {
      tpCount = new int[numClasses];
    }
    if (fpCount == null || fpCount.length != numClasses) {
      fpCount = new int[numClasses];
    }
    if (fnCount == null || fnCount.length != numClasses) {
      fnCount = new int[numClasses];
    }
    for (int i = 0; i < numClasses; i++) {
      L label = labelIndex.get(i);
      tpCount[i] = correctGuesses.getIntCount(label);
      fnCount[i] = foundCorrect.getIntCount(label) - tpCount[i];
      fpCount[i] = foundGuessed.getIntCount(label) - tpCount[i];
    }
  }

  protected void markBoundary()
  {
  }

  protected void addGuess(L guess, L label)
  {
    addGuess(guess, label, true);
  }
  
  protected void addGuess(L guess, L label, boolean addUnknownLabels)
  {
    if (label == null) {
        noLabel++;
        return;
    }
    if (addUnknownLabels) {
      if (labelIndex == null) {
        labelIndex = new HashIndex<L>();
      }
      labelIndex.add(guess);
      labelIndex.add(label);
    }
    if (guess.equals(label)) {
      correctGuesses.incrementCount(label);
      tokensCorrect++;
    }

    if (!guess.equals(negLabel)) {
      foundGuessed.incrementCount(guess);
    }

    if (!label.equals(negLabel)) {
      foundCorrect.incrementCount(label);
    }
    tokensCount++;
  }

  public void addGuesses(List<L> guesses, List<L> trueLabels)
  {
    for (int i=0; i < guesses.size(); ++i)
    {
      L guess = guesses.get(i);
      L label = trueLabels.get(i);
      addGuess(guess, label);
    }
  }

  /**
   * Return overall number of correct answers
   */
  public int getCorrect()
  {
    return correctGuesses.totalIntCount();
  }

  public int getCorrect(L label)
  {
    return correctGuesses.getIntCount(label);
  }

  public int getRetrieved(L label)
  {
    return foundGuessed.getIntCount(label);
  }

  public int getRetrieved()
  {
    return foundGuessed.totalIntCount();
  }

  public int getRelevant(L label)
  {
    return foundCorrect.getIntCount(label);
  }

  public int getRelevant()
  {
    return foundCorrect.totalIntCount();
  }

  /**
   * Return overall per token accuracy
   */
  public Triple<Double, Integer, Integer> getAccuracyInfo()
  {
    int totalCorrect = tokensCorrect;
    int totalWrong = tokensCount - tokensCorrect;
    return new Triple<Double, Integer, Integer>((((double) totalCorrect) / tokensCount),
            totalCorrect, totalWrong);
  }

  public double getAccuracy() {
    return getAccuracyInfo().first();
  }

  /**
   * Returns a String summarizing overall accuracy that will print nicely.
   */
  public String getAccuracyDescription(int numDigits) {
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMaximumFractionDigits(numDigits);
    Triple<Double, Integer, Integer> accu = getAccuracyInfo();
    return nf.format(accu.first()) + "  (" + accu.second() + "/" + (accu.second() + accu.third()) + ")";
  }

  public double score(String filename, String delimiter) throws IOException {
    return score(filename, delimiter, null);
  }

  public double score(String filename, String delimiter, String boundary) throws IOException {
    return score(IOUtils.getBufferedFileReader(filename), delimiter, boundary);
  }

  public double score(BufferedReader br, String delimiter) throws IOException
  {
    return score(br, delimiter, null);
  }
  
  public double score(BufferedReader br, String delimiter, String boundary) throws IOException
  {
    int TOKEN_INDEX = 0;
    int ANSWER_INDEX = 1;
    int GUESS_INDEX = 2;
    String line;
    Pattern delimPattern = Pattern.compile(delimiter);
    clearCounts();
    while ((line = br.readLine()) != null) {
      line = line.trim();
      if (line.length() > 0) {
        String[] fields = delimPattern.split(line);
        if (boundary != null && boundary.equals(fields[TOKEN_INDEX])) {
          markBoundary();
        } else {
          L answer = stringConverter.apply(fields[ANSWER_INDEX]);
          L guess = stringConverter.apply(fields[GUESS_INDEX]);
          addGuess(guess, answer);
        }
      } else {
        markBoundary();
      }
    }
    finalizeCounts();
    return getFMeasure();
  }

  public List<L> getLabels() {
    return labelIndex.objectsList();
  }

  public String getConllEvalString()
  {
    return getConllEvalString(true);
  }

  public String getConllEvalString(boolean ignoreNegLabel)
  {
    List<L> labels = getLabels();
    if (labels.size() > 1 && labels.get(0) instanceof Comparable) {
      List<Comparable> sortedLabels = (List<Comparable>) labels;
      Collections.sort(sortedLabels);
    }
    return getConllEvalString(labels, ignoreNegLabel);
  }

  private String getConllEvalString(List<L> orderedLabels, boolean ignoreNegLabel)
  {
    StringBuilder sb = new StringBuilder();
    int correctPhrases = getCorrect() - getCorrect(negLabel);
    Triple<Double,Integer,Integer> accuracyInfo = getAccuracyInfo();
    int totalCount = accuracyInfo.second() + accuracyInfo.third();
    sb.append("processed " + totalCount + " tokens with " + getRelevant() + " phrases; ");
    sb.append("found: " + getRetrieved() + " phrases; correct: " + correctPhrases + "\n");

    Formatter formatter = new Formatter(sb, Locale.US);
    formatter.format("accuracy: %6.2f%%; ", accuracyInfo.first() * 100);
    formatter.format("precision: %6.2f%%; ", getPrecision() * 100);
    formatter.format("recall: %6.2f%%; ", getRecall() * 100);
    formatter.format("FB1: %6.2f\n", getFMeasure() * 100);
    for (L label: orderedLabels) {
      if (ignoreNegLabel && label.equals(negLabel)) { continue; }
      formatter.format("%17s: ", label);
      formatter.format("precision: %6.2f%%; ", getPrecision(label) * 100);
      formatter.format("recall: %6.2f%%; ", getRecall(label) * 100);
      formatter.format("FB1: %6.2f  %d\n", getFMeasure(label) * 100, getRetrieved(label));
    }
    return sb.toString();
  }

  public static class StringStringConverter implements Function<String,String>
  {
    public String apply(String str) { return str; }
  }

  public static class MultiClassStringLabelStats extends MultiClassPrecisionRecallExtendedStats<String>
  {
    public <F> MultiClassStringLabelStats(Classifier<String,F> classifier, GeneralDataset<String,F> data, String negLabel)
    {
      super(classifier, data, negLabel);
      stringConverter = new StringStringConverter();
    }

    public MultiClassStringLabelStats(String negLabel)
    {
      super(negLabel);
      stringConverter = new StringStringConverter();
    }

    public MultiClassStringLabelStats(Index<String> dataLabelIndex, String negLabel)
    {
      this(negLabel);
      setLabelIndex(dataLabelIndex);
    }
  }

}
