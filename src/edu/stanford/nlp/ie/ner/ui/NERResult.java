package edu.stanford.nlp.ie.ner.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Class that packages a document, and the results of NER on that document.
 *
 * @author Huy Nguyen (<a href="mailto:htnguyen@cs.stanford.edu">htnguyen@cs.stanford.edu</a>)
 */
public class NERResult {

  private String id;
  private String text;
  private int tp, fp, fn;
  private ArrayList<NERMatch> matches;
  private boolean hasBeenViewed;

  public NERResult(String id) {
    this.id = id;
    text = "";
    matches = new ArrayList<NERMatch>();
    hasBeenViewed = false;
  }

  public void setText(String text) {
    this.text = text;
  }

  /**
   * Marks this result as having been viewed in the visualizer.
   */
  public void markAsViewed() {
    hasBeenViewed = true;
  }

  /**
   * Returns whether this result has been viewed in the visualizer.
   *
   * @return Whether this result has been viewed in the visualizer.
   */
  public boolean hasBeenViewed() {
    return (hasBeenViewed);
  }

  public String getID() {
    return (id);
  }

  public String getText() {
    return (text);
  }

  public int getTP() {
    return (tp);
  }

  public int getFP() {
    return (fp);
  }

  public int getFN() {
    return (fn);
  }

  public void addMatch(NERMatch match) {
    switch (match.getType()) {
      case NERMatch.TP:
        tp++;
        break;
      case NERMatch.FP:
        fp++;
        break;
      case NERMatch.FN:
        fn++;
        break;
    }
    matches.add(match);
  }

  /**
   * Returns the List of {@link NERMatch NERMatches} for this result.
   *
   * @return The List of {@link NERMatch NERMatches} for this result.
   */
  public List<NERMatch> getMatches() {
    return (matches);
  }
}
