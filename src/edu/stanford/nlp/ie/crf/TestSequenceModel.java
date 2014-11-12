package edu.stanford.nlp.ie.crf;

import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.sequences.SequenceModel;
import edu.stanford.nlp.util.CoreMap;

/**
 * For sequence model inference at test-time.
 * 
 * @author Spence Green
 *
 */
public class TestSequenceModel implements SequenceModel {

  private final int window;
  private final int numClasses;
  private final CRFCliqueTree cliqueTree;
  private final int[] backgroundTag;

  private final int[] allTags;
  private int[][] allowedTagsAtPosition;
  
  public TestSequenceModel(CRFCliqueTree cliqueTree) {
    this(cliqueTree, null, null);
  }

  public TestSequenceModel(CRFCliqueTree<String> cliqueTree,
      LabelDictionary labelDictionary, List<? extends CoreMap> document) {
    // this.factorTables = factorTables;
    this.cliqueTree = cliqueTree;
    // this.window = factorTables[0].windowSize();
    this.window = cliqueTree.window();
    // this.numClasses = factorTables[0].numClasses();
    this.numClasses = cliqueTree.getNumClasses();

    this.backgroundTag = new int[] { cliqueTree.backgroundIndex() };
    allTags = new int[numClasses];
    for (int i = 0; i < allTags.length; i++) {
      allTags[i] = i;
    }
    if (labelDictionary != null) {
      // Constrained
      allowedTagsAtPosition = new int[document.size()][];
      for (int i = 0; i < allowedTagsAtPosition.length; ++i) {
        CoreMap token  = document.get(i);
        String observation = token.get(CoreAnnotations.TextAnnotation.class);
        allowedTagsAtPosition[i] = labelDictionary.isConstrained(observation) ?
            labelDictionary.getConstrainedSet(observation) : allTags;
      }
    }
  }

  @Override
  public int length() {
    return cliqueTree.length();
  }

  @Override
  public int leftWindow() {
    return window - 1;
  }

  @Override
  public int rightWindow() {
    return 0;
  }

  @Override
  public int[] getPossibleValues(int pos) {
    if (pos < leftWindow()) {
      return backgroundTag;
    }
    int realPos = pos - window + 1;
    return allowedTagsAtPosition == null ? allTags :
      allowedTagsAtPosition[realPos];
  }

  /**
   * Return the score of the proposed tags for position given.
   * @param tags is an array indicating the assignment of labels to score.
   * @param pos is the position to return a score for.
   */
  @Override
  public double scoreOf(int[] tags, int pos) {
    int[] previous = new int[window - 1];
    int realPos = pos - window + 1;
    for (int i = 0; i < window - 1; i++) {
      previous[i] = tags[realPos + i];
    }
    return cliqueTree.condLogProbGivenPrevious(realPos, tags[pos], previous);
  }

  @Override
  public double[] scoresOf(int[] tags, int pos) {
    int[] allowedTags = getPossibleValues(pos);
    int realPos = pos - window + 1;
    int[] previous = new int[window - 1];
    for (int i = 0; i < window - 1; i++) {
      previous[i] = tags[realPos + i];
    }
    double[] scores = new double[allowedTags.length];
    for (int i = 0; i < allowedTags.length; i++) {
      scores[i] = cliqueTree.condLogProbGivenPrevious(realPos, allowedTags[i], previous);
    }
    return scores;
  }

  @Override
  public double scoreOf(int[] sequence) {
    throw new UnsupportedOperationException();
  }

}
