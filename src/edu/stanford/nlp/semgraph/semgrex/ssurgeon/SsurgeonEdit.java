package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.util.Collections;
import java.util.Set;

import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphName;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphs;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.ling.IndexedWord;

/**
 * An edit which an Ssurgeon pattern applies to a graph at each Semgrex match.
 * The edits available, and the syntax of their edit strings, are described
 * in {@link Ssurgeon}.
 */
public abstract class SsurgeonEdit {
   
  private SsurgeonPattern owningPattern = null;

  /** Creates an edit with no owning pattern. */
  public SsurgeonEdit() { }
  
  
  /**
   * Given a matching instance (via the SemgrexMatcher), performs an in-place
   * modification on the given SemanticGraph.
   *
   * @param sg The graph to edit
   * @param sm The matcher, positioned at the current match
   * @return whether or not there was an edit
   */
  public abstract boolean evaluate(SemanticGraph sg, SemgrexMatcher sm);

  /**
   * Given a matching instance (via the SemgrexMatcher), performs an in-place
   * modification on the graphs of a sentence.
   *<br>
   * The graphs are the ones the match was found in, or copies of them which
   * share their words and edges, so the matcher's nodes and edges can be
   * looked up in them.  This edits the basic graph with
   * {@link #evaluate(SemanticGraph, SemgrexMatcher)}; an edit which works
   * on the other graphs as well overrides it.
   *
   * @param graphs The graphs to edit
   * @param sm The matcher, positioned at the current match
   * @return whether or not there was an edit
   */
  public boolean evaluate(SemgrexGraphs graphs, SemgrexMatcher sm) {
    return evaluate(graphs.getDefault(), sm);
  }

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
   * Whether this edit, when it adds, removes, or renumbers words, does the
   * same to every graph of the sentence with the same IndexedWords.
   *<br>
   * {@link SsurgeonPattern#iterate(SemgrexGraphs)} keeps the other graphs
   * of a sentence after such an edit changes its words.  After any other
   * edit changes the words of the basic graph, the other graphs no longer
   * describe the same words, and are left out.  An edit which never
   * changes the words can leave this as it is.
   *
   * @return false unless an edit says otherwise
   */
  public boolean changesWordsInEveryGraph() {
    return false;
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

  /**
   * Returns the pattern this edit belongs to.
   *
   * @return The owning pattern, or null if none has been set
   */
  public SsurgeonPattern getOwningPattern() {
    return owningPattern;
  }

  /**
   * Sets the pattern this edit belongs to.
   *
   * @param owningPattern The owning pattern
   */
  public void setOwningPattern(SsurgeonPattern owningPattern) {
    this.owningPattern = owningPattern;
  }
  
  /**
   * Used to retrieve the named node: one the Semgrex pattern matched, or
   * one an earlier edit for the same match added, such as with addDep -name.
   *
   * @param nodeName The name of the node
   * @param sm The matcher, positioned at the current match
   * @return The named node, or null if the match has no node by that name
   */
  public IndexedWord getNamedNode(String nodeName, SemgrexMatcher sm) {
    return sm.getNode(nodeName);
  }
}
