package edu.stanford.nlp.classify;

import edu.stanford.nlp.ling.DataCollection;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.stats.PRStatsManager;
import edu.stanford.nlp.stats.PrecisionRecallStats;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.Filter;
import edu.stanford.nlp.util.Filters;
import edu.stanford.nlp.util.StringUtils;

import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.*;

// for confusion matrix

/**
 * Set of utility methods for evaluating Classifier accuracy.
 * <p/>
 * The first step is to test a Classifier on a DataCollection (list of Datums):
 * <pre>ClassifiedDatum[] results = {@link #testClassifier ClassifierTester.testClassifier(classifier, testData)};</pre>
 * Then there are a variety of methods to get subsets of these results and their counts, which can be chained:
 * <ul>
 * <li>{@link #accuracy accuracy(results)} - fraction of results with correctly predicted label
 * <li>{@link #correctResults correctResults(results)} - subset of results with correctly predicted label
 * <li>{@link #resultsWithLabel resultsWithLabel(results, label)} - subset of results with given correct label
 * <li>{@link #perLabelAccuracy perLabelAccuracy(results)} - separate accuracies for each correct label
 * <li>{@link #precisionRecallStats precisionRecallStats(results, label)} - Prec/Recall/F1 for given label
 * <li>and many more...
 * </ul>
 * Some further examples:
 * <p/>
 * Accuracy on "apple" class:
 * <pre>ClassifierTester.accuracy(ClassifierTester.resultsWithLabel(results, "apple"));</pre>
 * <p/>
 * Number of results predicted to be "apple" that are actually labeled "banana":
 * <pre>ClassifierTester.numPredicted(ClassifierTester.resultsWithLabel(results, "banana"), "apple");</pre>
 * <p/>
 * Mis-classified Datums with a specific feature (as determined by a {@link Filter}):
 * <pre>Filters.filter(ClassifierTester.incorrectResults(results), filter);</pre>
 * <p/>
 * All results that <i>weren't</i> predicted to be "apple":
 * <pre>
 * Set predictedLabels = ClassifierTester.predictedLabels(results);
 * predictedLabels.remove("apple"); // retain all non-apple labels
 * ClassifierTester.resultsWithPredictedLabel(results, allPredictedLabels);
 * </pre>
 * <p/>
 * Overall (micro) and per-class (macro) averages:
 * <pre>
 * double microAvgAcc = ClassifierTester.accuracy(results);
 * double macroAvgAcc = ClassifierTester.perLabelAccuracy(results).averageCount();</pre>
 * <p/>
 * Average F1 acoss all labels:
 * <pre>precisionRecallStats(results).getAverageFMeasure();</pre>
 * <p/>
 * Accuracy on Datums with a specified feature (e.g. Documents with some keyword):
 * <pre>
 * // original filter
 * Filter filter = Filters.collectionAcceptFilter(Collections.singleton("keyword"));
 * // filter for use on ClassifiedDatums that gets run on their Datums
 * Filter datumFilter = Filters.transformedFilter(filter, ClassifiedDatum.datumExtractor());
 * double acc = ClassifierTester.accuracy(Filters.filter(results, datumFilter));</pre>
 * <p/>
 * Print confusion matrix (with accuracy) to stdout (with 8 chars per cell)
 * <pre>ClassifierTester.printConfusionMatrix(results, new PrintWriter(System.out, true), 8);</pre>
 * <p/>
 * <p>TODO:</p>
 * <ul>
 * <li>Support for scoresOf output?
 * <li>Automatic support for multiple train/test folds?
 * </ul>
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 *
 * @param <L> The type of the labels in the Classifier
 * @param <F> The type of the features in the Classifier
 */
public class ClassifierTester<L, F> {
  /**
   * Private constructor to prevent direct instantiation.
   */
  private ClassifierTester() {
  }

  /**
   * Tests the given classifier on each of the given test Datums.
   *
   * @return a ClassifiedDatum for each test Datum with labels predicted from
   *         the given Classifier
   */
  public static <L, F> ClassifiedDatum<L, F>[] testClassifier(Classifier<L, F> classifier, DataCollection<L, F> testData) {
    ClassifiedDatum<L, F>[] results = ErasureUtils.mkTArray(ClassifiedDatum.class,testData.size());
    for (int i = 0; i < testData.size(); i++) {
      Datum<L, F> d = testData.getDatum(i);
      results[i] = new ClassifiedDatum<L, F>(d, classifier.classOf(d));
    }

    return (results);
  }

  /**
   * Returns the Set of predicted labels in the given results.
   */
  public static <L, F> Set<L> predictedLabels(ClassifiedDatum<L, F>[] results) {
    Set<L> labels = new HashSet<L>();
    for (int i = 0; i < results.length; i++) {
      labels.add(results[i].getPredictedLabel());
    }
    return (labels);
  }

  /**
   * Returns the subset of results with the given predicted label.
   */
  public static <L, F> ClassifiedDatum<L, F>[] resultsWithPredictedLabel(ClassifiedDatum<L, F>[] results, L predictedLabel) {
    return Filters.filter(results, new PredictedLabelFilter<L, F>(predictedLabel));
  }

  /**
   * Returns the subset of results with any of the given predicted labels.
   */
  public static <L, F> ClassifiedDatum<L, F>[] resultsWithPredictedLabel(ClassifiedDatum<L, F>[] results, Set<L> predictedLabels) {
    return Filters.filter(results, new PredictedLabelFilter<L, F>(predictedLabels));
  }

  /**
   * Returns the number of results with the given predicted label.
   */
  public static <L, F> int numPredicted(ClassifiedDatum<L, F>[] results, L predictedLabel) {
    return (resultsWithPredictedLabel(results, predictedLabel).length);
  }

  /**
   * Returns the Set of correct labels in the given results.
   */
  public static <L, F> Set<L> labels(ClassifiedDatum<L, F>[] results) {
    Set<L> labels = new HashSet<L>();
    for (int i = 0; i < results.length; i++) {
      labels.add(results[i].label());
    }
    return (labels);
  }

  /**
   * Returns the subset of results with the given correct label.
   */
  public static <L, F> ClassifiedDatum<L, F>[] resultsWithLabel(ClassifiedDatum<L, F>[] results, L correctLabel) {
    return Filters.filter(results, new CorrectLabelFilter<L, F>(correctLabel));
  }

  /**
   * Returns the subset of results with the any of the given correct labels.
   */
  public static <L, F> ClassifiedDatum<L, F>[] resultsWithLabel(ClassifiedDatum<L, F>[] results, Set<L> correctLabels) {
    return Filters.filter(results, new CorrectLabelFilter<L, F>(correctLabels));
  }

  /**
   * Returns the number of results with the given correct label.
   */
  public static <L, F> int numLabeled(ClassifiedDatum<L, F>[] results, L correctLabel) {
    return (resultsWithLabel(results, correctLabel).length);
  }

  /**
   * Returns the subset of results where the predicted label matches the correct label.
   */
  public static <L, F> ClassifiedDatum<L, F>[] correctResults(ClassifiedDatum<L, F>[] results) {
    return Filters.filter(results, new CorrectFilter<L, F>());
  }

  /**
   * Returns the number of results where the predicted label matches the correct label.
   */
  public static <L, F> int numCorrect(ClassifiedDatum<L, F>[] results) {
    return (correctResults(results).length);
  }

  /**
   * Returns the fraction of results where the predicted label matches the correct label.
   */
  public static <L, F> double accuracy(ClassifiedDatum<L, F>[] results) {
    return (1.0 * numCorrect(results) / results.length);
  }

  /**
   * Returns the subset of results where the predicted label doesn't match the correct label.
   */
  public static <L, F> ClassifiedDatum<L, F>[] incorrectResults(ClassifiedDatum<L, F>[] results) {
    return Filters.filter(results, Filters.notFilter(new CorrectFilter<L, F>()));
  }

  /**
   * Returns the number of results where the predicted label doesn't match the correct label.
   */
  public static <L, F> int numIncorrect(ClassifiedDatum<L, F>[] results) {
    return (incorrectResults(results).length);
  }

  /**
   * Returns separate accuracies for each correct label.
   */
  public static <L, F> ClassicCounter<L> perLabelAccuracy(ClassifiedDatum<L, F>[] results) {
    ClassicCounter<L> accuracies = new ClassicCounter<L>();
    for (Iterator<L> iter = labels(results).iterator(); iter.hasNext();) {
      L label = iter.next();
      accuracies.setCount(label, accuracy(resultsWithLabel(results, label)));
    }
    return (accuracies);
  }

  /**
   * Returns separate accuracies for each predicted label.
   */
  public static<L, F> ClassicCounter<L> perPredictedLabelAccuracy(ClassifiedDatum<L, F>[] results) {
    ClassicCounter<L> accuracies = new ClassicCounter<L>();
    for (L label: predictedLabels(results)) {
      accuracies.setCount(label, accuracy(resultsWithPredictedLabel(results, label)));
    }
    return (accuracies);
  }

  /**
   * Returns Precision/Recall/F1 stats for the results with respect to the
   * given label. A true positive means both the predicted and correct
   * label match the given label. A false positive means only the predicted
   * label matches, and a false negative means that only the correct label
   * matches. Datums in which neither label matches are disregarded as is
   * standard.
   */
  public static <L, F> PrecisionRecallStats precisionRecallStats(ClassifiedDatum<L, F>[] results, L label) {
    PrecisionRecallStats stats = new PrecisionRecallStats();
    for (int i = 0; i < results.length; i++) {
      if (results[i].label().equals(label)) {
        if (results[i].isCorrect()) {
          stats.incrementTP(); // hit
        } else {
          stats.incrementFN(); // miss
        }
      } else if (results[i].getPredictedLabel().equals(label)) {
        stats.incrementFP(); // false alarm
      }
    }
    return (stats);
  }

  /**
   * Returns PrecisionRecall/F1 stats for every label in the results.
   * Call <tt>getAggregateStats(label)</tt> to get PrecisionRecallStats for a
   * specific label or <tt>getAveragePrecision/Recall/FMeasure</tt> to get
   * average stats across all labels.
   */
  public static <L, F> PRStatsManager<L> precisionRecallStats(ClassifiedDatum<L, F>[] results) {
    PRStatsManager<L> allStats = new PRStatsManager<L>();
    for (L label: labels(results)) {
      allStats.addStats(label, precisionRecallStats(results, label));
    }
    return (allStats);
  }

  /**
   * Prints a confusion matrix summarizing the given results to the
   * given writer. Note that <tt>System.out</tt> is not a
   * PrintWriter but <tt>new PrintWriter(System.out, true)</tt> is
   * (with auto-flushing enabled).  Accuracy is printed at the
   * top. Rows are correct label, cols are predicted label.  Format
   * taken from eval.pl script used with CS224N assignments. Labels
   * are ordered by "natural order" (alphabetic, numeric, etc) for
   * consistent presentation across several folds/permutations of
   * data.  Therefore all labels must be <i>mutually comparable</i>.
   * <p/>
   * <p/>
   * To improve legibility, the row labels are printed with a wider
   * row label cell width, calculated internally, if the cell Width is very
   * narrow.
   *
   * @param cellWidth specifies the max number of characters for each
   *                  cell in the matrix -- labels will be trimmed and numbers will be
   *                  padded. Using a cellWidth of 8 is sufficient for most cases.
   *                  cellWidth must be at least 2 -- one column is for spaces between
   *                  columns.
   */
  public static <L extends Comparable<? super L>, F> void printConfusionMatrix(ClassifiedDatum<L, F>[] results, PrintWriter out, int cellWidth) {
    if (cellWidth < 2) {
      throw new IllegalArgumentException("cellWidth too narrow");
    }
    DecimalFormat df = new DecimalFormat("#.000"); // for percent accuracy
    out.println("Score " + numCorrect(results) + " right out of " + results.length + " (" + df.format(accuracy(results) * 100) + "%).");
    out.println();
    out.println("Confusions: (rows correct answer, columns guesses)");
    out.println();

    // aggregates predicted and correct labels and sorts them for consistent presentation
    Set<L> allLabels = labels(results);
    allLabels.addAll(predictedLabels(results));
    List<L> labels = new ArrayList<L>(allLabels);
    Collections.sort(labels);

    // Calculate rowLabelCellWidth
    int rowLabelCellWidth = 0;
    for (L label: labels) {
      int thisLabelLeng = label.toString().length();
      if (thisLabelLeng > rowLabelCellWidth) {
        rowLabelCellWidth = thisLabelLeng;
      }
    }
    int max = Math.min(cellWidth * 2, 7); // print 6 letters minimum
    if (rowLabelCellWidth < cellWidth) {
      rowLabelCellWidth = cellWidth;
    } else if (rowLabelCellWidth > max) {
      rowLabelCellWidth = max;
    }

    for (int i = 0; i < rowLabelCellWidth; i++) {
      out.print(' '); // skip row labels
    }
    for (Iterator<L> colIter = labels.iterator(); colIter.hasNext();) {
      out.print(StringUtils.padLeft(StringUtils.trim(colIter.next(), cellWidth - 1), cellWidth - 1));
      if (colIter.hasNext()) {
        out.print(" ");
      }
    }
    out.println();
    for (L correctLabel: labels) {
      out.print(StringUtils.pad(StringUtils.trim(correctLabel, rowLabelCellWidth - 1), rowLabelCellWidth));
      ClassifiedDatum<L, F>[] curResults = resultsWithLabel(results, correctLabel);
      for (Iterator<L> colIter = labels.iterator(); colIter.hasNext();) {
        // pull num predicted each as label for this correct label
        L predictedLabel = colIter.next();
        int count = numPredicted(curResults, predictedLabel);
        String temp = Integer.toString(count);
        if (temp.length() <= cellWidth - 1) {
          out.print(StringUtils.padLeft(count, cellWidth - 1));
        } else {
          StringBuffer tempSB = new StringBuffer();
          int k = 1;
          for (int p = 1; p < cellWidth; p++) {
            k *= 10;
          }
          while (tempSB.length() < cellWidth - 1 && count > k) {
            tempSB.append("#");
            k *= 2;
          }
          out.print(StringUtils.padLeft(tempSB, cellWidth - 1));
        }
        if (colIter.hasNext()) {
          out.print(" ");
        }
      }
      out.println();
    }
  }

  /**
   * Filter for restricting the correct label of a Datum.
   * If no (or null) labels are passed in, this filter always returns true.
   */
  private static class CorrectLabelFilter<L, F> implements Filter<ClassifiedDatum<L, F>> {
    /**
     * 
     */
    private static final long serialVersionUID = -1867609384235536632L;
    private final Set<L> labels = new HashSet<L>(); // restricted labels
    public boolean enabled; // whether to restrict at all

    /**
     * Uses the given label unless it's null.
     */
    public CorrectLabelFilter(L label) {
      this(label == null ? new HashSet<L>() : Collections.singleton(label));
    }

    /**
     * Uses all labels unless labels is null or empty.
     */
    public CorrectLabelFilter(Set<L> labels) {
      if (labels != null) {
        this.labels.addAll(labels);
      }
      enabled = this.labels.size() > 0;
    }

    /**
     * Returns true if the correct label of the given ClassifiedDatum is
     * among the set of correct labels for this filter, or true if this
     * filter isn't enabled.
     */
    public boolean accept(ClassifiedDatum<L, F> o) {
      L label = o.label();
      return (!enabled || labels.contains(label));
    }
  }

  /**
   * Filter for restricting the predicted label of a Datum.
   * If no (or null) labels are passed in, this filter always returns true.
   */
  private static class PredictedLabelFilter<L, F> implements Filter<ClassifiedDatum<L, F>> {
    /**
     * 
     */
    private static final long serialVersionUID = 384064123047452362L;
    private final Set<L> labels = new HashSet<L>(); // restricted labels
    public boolean enabled; // whether to restrict at all

    /**
     * Uses the given label unless it's null.
     */
    public PredictedLabelFilter(L label) {
      this(label == null ? new HashSet<L>() : Collections.singleton(label));
    }

    /**
     * Uses all labels unless labels is null or empty.
     */
    public PredictedLabelFilter(Set<L> labels) {
      if (labels != null) {
        this.labels.addAll(labels);
      }
      enabled = this.labels.size() > 0;
    }

    /**
     * Returns true if the predicted label of the given ClassifiedDatum is
     * among the set of correct labels for this filter, or true if this
     * filter isn't enabled.
     */
    public boolean accept(ClassifiedDatum<L, F> o) {
      L label = o.getPredictedLabel();
      return (!enabled || labels.contains(label));
    }
  }

  /**
   * Accepts correctly classified datums.
   */
  private static class CorrectFilter<L, F> implements Filter<ClassifiedDatum<L, F>> {
    /**
     * 
     */
    private static final long serialVersionUID = -50012196011795735L;

    public boolean accept(ClassifiedDatum<L, F> o) {
      return (o.isCorrect());
    }
  }
}
