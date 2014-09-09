// TsurgeonPattern
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

package edu.stanford.nlp.trees.tregex.tsurgeon;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;


/**
 * An abstract class for patterns to manipulate {@link Tree}s when
 * successfully matched on with a {@link TregexMatcher}.
 *
 * @author Roger Levy
 */
public abstract class TsurgeonPattern {

  static final TsurgeonPattern[] EMPTY_TSURGEON_PATTERN_ARRAY = new TsurgeonPattern[0];

  TsurgeonPatternRoot root;
  final String label;
  final TsurgeonPattern[] children;

  /**
   * In some cases, the order of the children has special meaning.
   * For example, in the case of ReplaceNode, the first child will
   * evaluate to the node to be replaced, and the other(s) will
   * evaluate to the replacement.
   */
  TsurgeonPattern(String label, TsurgeonPattern[] children) {
    this.label = label;
    this.children = children;
  }

  protected void setRoot(TsurgeonPatternRoot root) {
    this.root = root;
    for (TsurgeonPattern child : children) {
      child.setRoot(root);
    }
  }

  @Override
  public String toString() {
    StringBuilder resultSB = new StringBuilder();
    resultSB.append(label);
    if (children.length > 0) {
      resultSB.append('(');
      for (int i = 0; i < children.length; i++) {
        resultSB.append(children[i]);
        if (i < children.length - 1) {
          resultSB.append(", ");
        }
      }
      resultSB.append(')');
    }
    return resultSB.toString();
  }

  /**
   * Evaluates the surgery pattern against a {@link Tree} and a {@link TregexMatcher}
   * that has been successfully matched against the tree.
   *
   * @param t The {@link Tree} that has been matched upon; typically this tree will be destructively modified.
   * @param m The successfully matched {@link TregexMatcher}.
   * @return Some node in the tree; depends on implementation and use of the specific subclass.
   */
  public abstract Tree evaluate(Tree t, TregexMatcher m);

}
