package edu.stanford.nlp.trees.tregex.tsurgeon;

import java.util.Map;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;

/**
 * An object factored out to keep the state of a {@code Tsurgeon}
 * operation separate from the {@code TsurgeonPattern} objects.
 * This makes it easier to reset state between invocations and makes
 * it easier to use in a threadsafe manner.
 * <br>
 * TODO: it would be nice to go through all the patterns and make sure
 * they update {@code newNodeNames} or look for appropriate nodes
 * in {@code newNodeNames} when possible.
 * <br>
 * It would also be nicer if the call to {@code matcher()} took
 * the tree &amp; tregex instead of {@code evaluate()}, but that
 * is a little more complicated because of the way the
 * {@code TsurgeonMatcher} is used in {@code Tsurgeon}.
 * Basically, you would need to move that code from
 * {@code Tsurgeon} to {@code TsurgeonMatcher}.
 *
 * @author John Bauer
 */
public abstract class TsurgeonMatcher {

  Map<String,Tree> newNodeNames;
  CoindexationGenerator coindexer;

  TsurgeonMatcher[] childMatcher;

  // TODO: ideally we should have the tree and the tregex matcher be
  // part of this as well.  That would involve putting some of the
  // functionality in Tsurgeon.java in this object
  public TsurgeonMatcher(TsurgeonPattern pattern, Map<String, Tree> newNodeNames, CoindexationGenerator coindexer) {
    this.newNodeNames = newNodeNames;
    this.coindexer = coindexer;

    this.childMatcher = new TsurgeonMatcher[pattern.children.length];
    for (int i = 0; i < pattern.children.length; ++i) {
      this.childMatcher[i] = pattern.children[i].matcher(newNodeNames, coindexer);
    }
  }


  /**
   * Evaluates the surgery pattern against a {@link Tree} and a {@link TregexMatcher}
   * that has been successfully matched against the tree.
   *
   * @param tree The {@link Tree} that has been matched upon; typically this tree will be destructively modified.
   * @param tregex The successfully matched {@link TregexMatcher}.
   * @return Some node in the tree; depends on implementation and use of the specific subclass.
   */
  public abstract Tree evaluate(Tree tree, TregexMatcher tregex);

}
