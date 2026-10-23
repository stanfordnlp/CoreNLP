package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.util.Collections;
import java.util.Set;

import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphName;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphs;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatch;

/**
 * An edit which an Ssurgeon pattern applies to a graph at each Semgrex match.
 * The edits available, and the syntax of their edit strings, are described
 * in {@link Ssurgeon}.
 */
public abstract class SsurgeonEdit {

  /** Creates an edit. */
  public SsurgeonEdit() { }
  
  
  /**
   * Given a match, performs an in-place modification on the graphs of a sentence.
   *<br>
   * The match has its own copies of the named nodes, edges, and variables
   * of the semgrex match.  An edit can bind names and set variables in it
   * for the edits after it, without changing the matcher's search.
   *<br>
   * The graphs are the ones the match was found in, or copies of them which
   * share their words and edges, so the match's nodes and edges can be
   * looked up in them.  A sentence with only a basic graph is edited by
   * passing {@code new SemgrexGraphs(graph)}.
   *<br>
   * An edit which adds, removes, or renumbers words does the same to every
   * graph of the sentence with the same IndexedWords, so the graphs keep
   * describing the same words.
   *
   * @param graphs The graphs to edit
   * @param sm The match, with its named nodes, edges, and variables
   * @return whether or not there was an edit
   */
  public abstract boolean evaluate(SemgrexGraphs graphs, SemgrexMatch sm);

  /**
   * The graphs this edit chooses to edit, such as with {@code -graph enhanced}.
   *<br>
   * The sentence must have each of them for the edit to be applied.  An
   * edit which works on the graph its match came from, or on the words
   * themselves, chooses none.
   *
   * @return the graphs, which is none unless an edit says otherwise
   */
  public Set<SemgrexGraphName> requiredGraphs() {
    return Collections.emptySet();
  }

  /**
   * Returns this edit in the Ssurgeon edit syntax.
   *
   * @return A parseable string representing the edit
   */
  public abstract String toEditString(); // This should be a parseable String representing the edit
  @Override
  public String toString() { return toEditString(); }
  
  /**
   * Compares two edits by their string representations.  This overloads,
   * rather than overrides, {@link Object#equals(Object)}.
   *
   * @param tgt The edit to compare to
   * @return Whether the two edits have the same string representation
   */
  public boolean equals(SsurgeonEdit tgt) {
    return this.toString().equals(tgt.toString());
  }
}
