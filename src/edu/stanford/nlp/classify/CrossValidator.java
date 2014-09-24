package edu.stanford.nlp.classify;

import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.stanford.nlp.util.Function;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * This class is meant to simplify performing cross validation of
 * classifiers for hyper-parameters.  It has the ability to save
 * state for each fold (for instance, the weights for a MaxEnt
 * classifier, and the alphas for an SVM).
 *
 * @author Aria Haghighi
 * @author Jenny Finkel
 * @author Sarah Spikes (Templatization)
 */
public class CrossValidator<L, F> {
  private final GeneralDataset<L, F> originalTrainData;
  private final int kFold;
  private final SavedState[] savedStates;

  public CrossValidator(GeneralDataset<L, F> trainData) {
    this(trainData, 10);
  }

  public CrossValidator(GeneralDataset<L, F> trainData, int kFold) {
    originalTrainData = trainData;
    this.kFold = kFold;
    savedStates = new SavedState[kFold];
    for (int i = 0; i < savedStates.length; i++) {
      savedStates[i] = new SavedState();
    }
  }

  /**
   * Returns an Iterator over train/test/saved states.
   *
   * @return An Iterator over train/test/saved states
   */
  private Iterator<Triple<GeneralDataset<L, F>,GeneralDataset<L, F>,SavedState>> iterator() { return new CrossValidationIterator(); }

  /**
   * This computes the average over all folds of the function we're trying to optimize.
   * The input triple contains, in order, the train set, the test set, and the saved state.
   * You don't have to use the saved state if you don't want to.
   */
  public double computeAverage (Function<Triple<GeneralDataset<L, F>,GeneralDataset<L, F>,SavedState>,Double> function) {
    double sum = 0;
    Iterator<Triple<GeneralDataset<L, F>,GeneralDataset<L, F>,SavedState>> foldIt = iterator();
    while (foldIt.hasNext()) {
      sum += function.apply(foldIt.next());
    }
    return sum / kFold;
  }


  class CrossValidationIterator implements Iterator<Triple<GeneralDataset<L, F>,GeneralDataset<L, F>,SavedState>> {

    private int iter = 0;

    @Override
    public boolean hasNext() { return iter < kFold; }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("CrossValidationIterator doesn't support remove()");
    }

    @Override
    public Triple<GeneralDataset<L, F>,GeneralDataset<L, F>,SavedState> next() {
      if (iter == kFold) throw new NoSuchElementException("CrossValidatorIterator exhausted.");
      int start = originalTrainData.size() * iter / kFold;
      int end = originalTrainData.size() * (iter + 1) / kFold;
      //System.err.println("##train data size: " +  originalTrainData.size() + " start " + start + " end " + end);
      Pair<GeneralDataset<L, F>, GeneralDataset<L, F>> split = originalTrainData.split(start, end);

      return new Triple<GeneralDataset<L, F>,GeneralDataset<L, F>,SavedState>(split.first(),split.second(),savedStates[iter++]);
    }

  } // end class CrossValidationIterator


  public static class SavedState {
    public Object state;
  }

  public static void main(String[] args) {
    Dataset<String, String> d = Dataset.readSVMLightFormat(args[0]);
    Iterator<Triple<GeneralDataset<String, String>,GeneralDataset<String, String>,SavedState>> it = (new CrossValidator<String, String>(d)).iterator();
    while (it.hasNext()) {
      it.next();
    }
  }

}
