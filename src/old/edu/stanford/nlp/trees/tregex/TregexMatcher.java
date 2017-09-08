// TregexMatcher
// Copyright (c) 2004-2007 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    Support/Questions: parser-user@lists.stanford.edu
//    Licensing: parser-support@lists.stanford.edu
//    http://www-nlp.stanford.edu/software/tregex.shtml

package old.edu.stanford.nlp.trees.tregex;

import java.util.*;

import old.edu.stanford.nlp.trees.Tree;

/**
 * A TregexMatcher can be used to match a {@link TregexPattern} against a {@link edu.stanford.nlp.trees.Tree}.
 * <p>
 * Usage should to be the same as {@link java.util.regex.Matcher}.
 * <p>
 * @author Galen Andrew
 */
public abstract class TregexMatcher {

  final Tree root;
  Tree tree;
  final Map<String, Tree> namesToNodes;
  final VariableStrings variableStrings;

  // these things are used by "find"
  Iterator<Tree> findIterator;
  Tree findCurrent;


  TregexMatcher(Tree root, Tree tree, Map<String, Tree> namesToNodes, VariableStrings variableStrings) {
    this.root = root;
    this.tree = tree;
    this.namesToNodes = namesToNodes;
    this.variableStrings = variableStrings;
  }

  /**
   * Resets the matcher so that its search starts over.
   */
  public void reset() {
    findIterator = null;
    namesToNodes.clear();
  }

  /**
   * Resets the matcher to start searching on the given tree for matching subexpressions.
   *
   * @param tree The tree to start searching on
   */
  void resetChildIter(Tree tree) {
    this.tree = tree;
    resetChildIter();
  }

  /**
   * Resets the matcher to restart search for matching subexpressions
   */
  void resetChildIter() {
  }

  /**
   * Does the pattern match the tree?  It's actually closer to java.util.regex's
   * "lookingAt" in that the root of the tree has to match the root of the pattern
   * but the whole tree does not have to be "accounted for".  Like with lookingAt
   * the beginning of the string has to match the pattern, but the whole string
   * doesn't have to be "accounted for".
   *
   * @return whether the tree matches the pattern
   */
  public abstract boolean matches();

  /** Rests the matcher and tests if it matches on the tree when rooted at <code>node</code>.
   *
   *  @param node The node where the match is checked
   *  @return whether the matcher matches at node
   */
  public boolean matchesAt(Tree node) {
    resetChildIter(node);
    return matches();
  }

  /**
   * Get the last matching tree -- that is, the tree node that matches the root node of the pattern.
   * Returns null if there has not been a match.
   *
   * @return last match
   */
  public abstract Tree getMatch();


  /**
   * Find the next match of the pattern on the tree
   *
   * @return whether there is a match somewhere in the tree
   */
  public boolean find() {
    if (findIterator == null) {
      findIterator = root.iterator();
    }
    if (findCurrent != null && matches()) {
      return true;
    }
    while (findIterator.hasNext()) {
      findCurrent = findIterator.next();
      resetChildIter(findCurrent);
      if (matches()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Find the next match of the pattern on the tree such that the matching node (that is, the tree node matching the
   * root node of the pattern) differs from the previous matching node.
   * @return true iff another matching node is found.
   */
  public boolean findNextMatchingNode() {
    Tree lastMatchingNode = getMatch();
    while(find()) {
      if(getMatch() != lastMatchingNode)
        return true;
    }
    return false;
  }

  /**
   * Returns the node labeled with <code>name</code> in the pattern.
   *
   * @param name the name of the node, specified in the pattern.
   * @return node labeled by the name
   */
  public Tree getNode(String name) {
    return namesToNodes.get(name);
  }

}
