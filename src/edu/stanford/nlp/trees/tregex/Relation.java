package edu.stanford.nlp.trees.tregex; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.Serializable;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Trees;
import java.util.function.Function;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.IdentityHashSet;
import edu.stanford.nlp.util.Interner;
import edu.stanford.nlp.util.Pair;


/**
 * An abstract base class for relations between tree nodes in tregex. There are
 * two types of subclasses: static anonymous singleton instantiations for
 * relations that do not require arguments, and private subclasses for those
 * with arguments. All invocations should be made through the static factory
 * methods, which insure that there is only a single instance of each relation.
 * Thus == can be used instead of .equals.
 * <br>
 * If you want to add a new
 * relation, you just have to fill in the definition of searchNodeIterator.
 * Also be careful to make the appropriate adjustments to
 * getRelation and SIMPLE_RELATIONS. Finally, if you are using the TregexParser,
 * you need to add the new relation symbol to the list of tokens.
 *
 * @author Galen Andrew
 * @author Roger Levy
 * @author Christopher Manning
 */
abstract class Relation implements Serializable {


  /**
   *
   */
  private static final long serialVersionUID = -1564793674551362909L;

  private final String symbol;

  /**
   * For a given node, returns an {@link Iterator} over the nodes
   * of the tree containing the node that satisfy the relation.
   *
   * @param t A node in a Tree
   * @param matcher The matcher that nodes have to satisfy
   * @return An Iterator over the nodes
   *     of the root tree that satisfy the relation.
   */
  abstract Iterator<Tree> searchNodeIterator(final Tree t,
                                             final TregexMatcher matcher);

  private static final Pattern parentOfLastChild = Pattern.compile("(<-|<`)");

  private static final Pattern lastChildOfParent = Pattern.compile("(>-|>`)");

  /**
   * Static factory method for all relations with no arguments. Includes:
   * DOMINATES, DOMINATED_BY, PARENT_OF, CHILD_OF, PRECEDES,
   * IMMEDIATELY_PRECEDES, HAS_LEFTMOST_DESCENDANT, HAS_RIGHTMOST_DESCENDANT,
   * LEFTMOST_DESCENDANT_OF, RIGHTMOST_DESCENDANT_OF, SISTER_OF, LEFT_SISTER_OF,
   * RIGHT_SISTER_OF, IMMEDIATE_LEFT_SISTER_OF, IMMEDIATE_RIGHT_SISTER_OF,
   * HEADS, HEADED_BY, IMMEDIATELY_HEADS, IMMEDIATELY_HEADED_BY, ONLY_CHILD_OF,
   * HAS_ONLY_CHILD, EQUALS, ANCESTOR_OF_LEAF
   *
   * @param s The String representation of the relation
   * @return The singleton static relation of the specified type
   * @throws ParseException If bad relation s
   */
  static Relation getRelation(String s,
                              Function<String, String> basicCatFunction,
                              HeadFinder headFinder)
    throws ParseException
  {
    if (SIMPLE_RELATIONS_MAP.containsKey(s))
      return SIMPLE_RELATIONS_MAP.get(s);

    // these are shorthands for relations with arguments
    if (s.equals("<,")) {
      return getRelation("<", "1", basicCatFunction, headFinder);
    } else if (parentOfLastChild.matcher(s).matches()) {
      return getRelation("<", "-1", basicCatFunction, headFinder);
    } else if (s.equals(">,")) {
      return getRelation(">", "1", basicCatFunction, headFinder);
    } else if (lastChildOfParent.matcher(s).matches()) {
      return getRelation(">", "-1", basicCatFunction, headFinder);
    }

    // finally try relations with headFinders
    Relation r;
    switch (s) {
      case ">>#":
        r = new Heads(headFinder);
        break;
      case "<<#":
        r = new HeadedBy(headFinder);
        break;
      case ">#":
        r = new ImmediatelyHeads(headFinder);
        break;
      case "<#":
        r = new ImmediatelyHeadedBy(headFinder);
        break;
      default:
        throw new ParseException("Unrecognized simple relation " + s);
    }

    return Interner.globalIntern(r);
  }

  /**
   * Static factory method for relations requiring an argument, including
   * HAS_ITH_CHILD, ITH_CHILD_OF, UNBROKEN_CATEGORY_DOMINATES,
   * UNBROKEN_CATEGORY_DOMINATED_BY, ANCESTOR_OF_ITH_LEAF.
   *
   * @param s The String representation of the relation
   * @param arg The argument to the relation, as a string; could be a node
   *          description or an integer
   * @return The singleton static relation of the specified type with the
   *         specified argument. Uses Interner to insure singleton-ity
   * @throws ParseException If bad relation s
   */
  static Relation getRelation(String s, String arg,
                              Function<String,String> basicCatFunction,
                              HeadFinder headFinder)
    throws ParseException
  {
    if (arg == null) {
      return getRelation(s, basicCatFunction, headFinder);
    }
    Relation r;
    switch (s) {
      case "<":
        r = new HasIthChild(Integer.parseInt(arg));
        break;
      case ">":
        r = new IthChildOf(Integer.parseInt(arg));
        break;
      case "<+":
        r = new UnbrokenCategoryDominates(arg, basicCatFunction);
        break;
      case ">+":
        r = new UnbrokenCategoryIsDominatedBy(arg, basicCatFunction);
        break;
      case ".+":
        r = new UnbrokenCategoryPrecedes(arg, basicCatFunction);
        break;
      case ",+":
        r = new UnbrokenCategoryFollows(arg, basicCatFunction);
        break;
      case "<<<":
        r = new AncestorOfIthLeaf(Integer.parseInt(arg));
        break;
      default:
        throw new ParseException("Unrecognized compound relation " + s + ' '
            + arg);
    }
    return Interner.globalIntern(r);
  }

  /**
   * Produce a TregexPattern which represents the given MULTI_RELATION
   * and its children
   */
  static TregexPattern constructMultiRelation(String s, List<DescriptionPattern> children,
                                              Function<String, String> basicCatFunction,
                                              HeadFinder headFinder) throws ParseException {
    if (s.equals("<...")) {
      List<TregexPattern> newChildren = Generics.newArrayList();
      for (int i = 0; i < children.size(); ++i) {
        Relation rel = getRelation("<", Integer.toString(i + 1), basicCatFunction, headFinder); 
        DescriptionPattern oldChild = children.get(i);
        TregexPattern newChild = new DescriptionPattern(rel, oldChild);
        newChildren.add(newChild);
      }
      Relation rel = getRelation("<", Integer.toString(children.size() + 1), basicCatFunction, headFinder);
      TregexPattern noExtraChildren = new DescriptionPattern(rel, false, "__", null, false, basicCatFunction, Collections.<Pair<Integer,String>>emptyList(), false, null);
      noExtraChildren.negate();
      newChildren.add(noExtraChildren);
      return new CoordinationPattern(newChildren, true);
    } else {
      throw new ParseException("Unknown multi relation " + s);
    }
  }

  private Relation(String symbol) {
    this.symbol = symbol;
  }

  @Override
  public String toString() {
    return symbol;
  }

  /**
   * This abstract Iterator implements a NULL iterator, but by subclassing and
   * overriding advance and/or initialize, it is an efficient implementation.
   */
  abstract static class SearchNodeIterator implements Iterator<Tree> {
    public SearchNodeIterator() {
      initialize();
    }

    /**
     * This is the next tree to be returned by the iterator, or null if there
     * are no more items.
     */
    Tree next; // = null;

    /**
     * This method must insure that next points to first item, or null if there
     * are no items.
     */
    void initialize() {
      advance();
    }

    /**
     * This method must insure that next points to next item, or null if there
     * are no more items.
     */
    void advance() {
      next = null;
    }

    @Override
    public boolean hasNext() {
      return next != null;
    }

    @Override
    public Tree next() {
      if (next == null) {
        throw new NoSuchElementException();
      }
      Tree ret = next;
      advance();
      return ret;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException(
          "SearchNodeIterator does not support remove().");
    }
  }

  static final Relation ROOT = new Relation("Root") {  // used in TregexParser

    private static final long serialVersionUID = -8311913236233762612L;

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        @Override
        void initialize() {
          next = t;
        }
      };
    }
  };

  private static final Relation EQUALS = new Relation("==") {

    private static final long serialVersionUID = 164629344977943816L;

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return Collections.singletonList(t).iterator();
    }

  };

  /* this is a "dummy" relation that allows you to segment patterns. */
  private static final Relation PATTERN_SPLITTER = new Relation(":") {

    private static final long serialVersionUID = 3409941930361386114L;

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return matcher.getRoot().iterator();
    }
  };

  private static final Relation DOMINATES = new Relation("<<") {

    private static final long serialVersionUID = -2580199434621268260L;

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        Stack<Tree> searchStack;

        @Override
        public void initialize() {
          searchStack = new Stack<>();
          for (int i = t.numChildren() - 1; i >= 0; i--) {
            searchStack.push(t.getChild(i));
          }
          if (!searchStack.isEmpty()) {
            advance();
          }
        }

        @Override
        void advance() {
          if (searchStack.isEmpty()) {
            next = null;
          } else {
            next = searchStack.pop();
            for (int i = next.numChildren() - 1; i >= 0; i--) {
              searchStack.push(next.getChild(i));
            }
          }
        }
      };
    }
  };

  private static final Relation DOMINATED_BY = new Relation(">>") {

    private static final long serialVersionUID = 6140614010121387690L;

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        @Override
        void initialize() {
          next = matcher.getParent(t);
        }

        @Override
        public void advance() {
          next = matcher.getParent(next);
        }
      };
    }
  };

  private static final Relation PARENT_OF = new Relation("<") {

    private static final long serialVersionUID = 9140193735607580808L;

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        // subtle bug warning here: if we use 
        //   int nextNum=0;
        // instead, we get the first daughter twice because the
        // assignment occurs after advance() has already been called
        // once by the constructor of SearchNodeIterator.
        int nextNum;

        @Override
        public void advance() {
          if (nextNum < t.numChildren()) {
            next = t.getChild(nextNum);
            nextNum++;
          } else {
            next = null;
          }
        }
      };
    }
  };

  private static final Relation CHILD_OF = new Relation(">") {

    private static final long serialVersionUID = 8919710375433372537L;

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        @Override
        void initialize() {
          next = matcher.getParent(t);
        }
      };
    }
  };

  private static final Relation PRECEDES = new Relation("..") {

    private static final long serialVersionUID = -9065012389549976867L;

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        Stack<Tree> searchStack;

        @Override
        public void initialize() {
          searchStack = new Stack<>();
          Tree current = t;
          Tree parent = matcher.getParent(t);
          while (parent != null) {
            for (int i = parent.numChildren() - 1; parent.getChild(i) != current; i--) {
              searchStack.push(parent.getChild(i));
            }
            current = parent;
            parent = matcher.getParent(parent);
          }
          advance();
        }

        @Override
        void advance() {
          if (searchStack.isEmpty()) {
            next = null;
          } else {
            next = searchStack.pop();
            for (int i = next.numChildren() - 1; i >= 0; i--) {
              searchStack.push(next.getChild(i));
            }
          }
        }
      };
    }
  };

  private static final Relation IMMEDIATELY_PRECEDES = new Relation(".") {

    private static final long serialVersionUID = 3390147676937292768L;

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        @Override
        void initialize() {
          Tree current;
          Tree parent = t;
          do {
            current = parent;
            parent = matcher.getParent(parent);
            if (parent == null) {
              next = null;
              return;
            }
          } while (parent.lastChild() == current);

          for (int i = 1, n = parent.numChildren(); i < n; i++) {
            if (parent.getChild(i - 1) == current) {
              next = parent.getChild(i);
              return;
            }
          }
        }

        @Override
        public void advance() {
          if (next.isLeaf()) {
            next = null;
          } else {
            next = next.firstChild();
          }
        }
      };
    }
  };

  private static final Relation FOLLOWS = new Relation(",,") {

    private static final long serialVersionUID = -5948063114149496983L;

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        Stack<Tree> searchStack;

        @Override
        public void initialize() {
          searchStack = new Stack<>();
          Tree current = t;
          Tree parent = matcher.getParent(t);
          while (parent != null) {
            for (int i = 0; parent.getChild(i) != current; i++) {
              searchStack.push(parent.getChild(i));
            }
            current = parent;
            parent = matcher.getParent(parent);
          }
          advance();
        }

        @Override
        void advance() {
          if (searchStack.isEmpty()) {
            next = null;
          } else {
            next = searchStack.pop();
            for (int i = next.numChildren() - 1; i >= 0; i--) {
              searchStack.push(next.getChild(i));
            }
          }
        }
      };
    }
  };

  private static final Relation IMMEDIATELY_FOLLOWS = new Relation(",") {

    private static final long serialVersionUID = -2895075562891296830L;

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        @Override
        void initialize() {
          Tree current;
          Tree parent = t;
          do {
            current = parent;
            parent = matcher.getParent(parent);
            if (parent == null) {
              next = null;
              return;
            }
          } while (parent.firstChild() == current);

          for (int i = 0, n = parent.numChildren() - 1; i < n; i++) {
            if (parent.getChild(i + 1) == current) {
              next = parent.getChild(i);
              return;
            }
          }
        }

        @Override
        public void advance() {
          if (next.isLeaf()) {
            next = null;
          } else {
            next = next.lastChild();
          }
        }
      };
    }
  };

  private static final Relation HAS_LEFTMOST_DESCENDANT = new Relation("<<,") {

    private static final long serialVersionUID = -7352081789429366726L;

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        @Override
        void initialize() {
          next = t;
          advance();
        }

        @Override
        public void advance() {
          if (next.isLeaf()) {
            next = null;
          } else {
            next = next.firstChild();
          }
        }
      };
    }
  };

  private static final Relation HAS_RIGHTMOST_DESCENDANT = new Relation("<<-") {

    private static final long serialVersionUID = -1405509785337859888L;

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        @Override
        void initialize() {
          next = t;
          advance();
        }

        @Override
        public void advance() {
          if (next.isLeaf()) {
            next = null;
          } else {
            next = next.lastChild();
          }
        }
      };
    }
  };

  private static final Relation LEFTMOST_DESCENDANT_OF = new Relation(">>,") {

    private static final long serialVersionUID = 3103412865783190437L;

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        @Override
        void initialize() {
          next = t;
          advance();
        }

        @Override
        public void advance() {
          Tree last = next;
          next = matcher.getParent(next);
          if (next != null && next.firstChild() != last) {
            next = null;
          }
        }
      };
    }
  };

  private static final Relation RIGHTMOST_DESCENDANT_OF = new Relation(">>-") {

    private static final long serialVersionUID = -2000255467314675477L;

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        @Override
        void initialize() {
          next = t;
          advance();
        }

        @Override
        public void advance() {
          Tree last = next;
          next = matcher.getParent(next);
          if (next != null && next.lastChild() != last) {
            next = null;
          }
        }
      };
    }
  };

  private static final Relation SISTER_OF = new Relation("$") {

    private static final long serialVersionUID = -3776688096782419004L;

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        Tree parent;

        int nextNum;

        @Override
        void initialize() {
          parent = matcher.getParent(t);
          if (parent != null) {
            nextNum = 0;
            advance();
          }
        }

        @Override
        public void advance() {
          if (nextNum < parent.numChildren()) {
            next = parent.getChild(nextNum++);
            if (next == t) {
              advance();
            }
          } else {
            next = null;
          }
        }
      };
    }
  };

  private static final Relation LEFT_SISTER_OF = new Relation("$++") {

    private static final long serialVersionUID = -4516161080140406862L;

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        Tree parent;

        int nextNum;

        @Override
        void initialize() {
          parent = matcher.getParent(t);
          if (parent != null) {
            nextNum = parent.numChildren() - 1;
            advance();
          }
        }

        @Override
        public void advance() {
          next = parent.getChild(nextNum--);
          if (next == t) {
            next = null;
          }
        }
      };
    }
  };

  private static final Relation RIGHT_SISTER_OF = new Relation("$--") {

    private static final long serialVersionUID = -5880626025192328694L;

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        Tree parent;

        int nextNum;

        @Override
        void initialize() {
          parent = matcher.getParent(t);
          if (parent != null) {
            nextNum = 0;
            advance();
          }
        }

        @Override
        public void advance() {
          next = parent.getChild(nextNum++);
          if (next == t) {
            next = null;
          }
        }
      };
    }
  };

  private static final Relation IMMEDIATE_LEFT_SISTER_OF = new Relation("$+") {

    private static final long serialVersionUID = 7745237994722126917L;

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        @Override
        void initialize() {
          if (t != matcher.getRoot()) {
            Tree parent = matcher.getParent(t);
            int i = 0;
            while (parent.getChild(i) != t) {
              i++;
            }
            if (i + 1 < parent.numChildren()) {
              next = parent.getChild(i + 1);
            }
          }
        }
      };
    }
  };

  private static final Relation IMMEDIATE_RIGHT_SISTER_OF = new Relation("$-") {

    private static final long serialVersionUID = -6555264189937531019L;

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        @Override
        void initialize() {
          if (t != matcher.getRoot()) {
            Tree parent = matcher.getParent(t);
            int i = 0;
            while (parent.getChild(i) != t) {
              i++;
            }
            if (i > 0) {
              next = parent.getChild(i - 1);
            }
          }
        }
      };
    }
  };

  private static final Relation ONLY_CHILD_OF = new Relation(">:") {

    private static final long serialVersionUID = 1719812660770087879L;

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        @Override
        void initialize() {
          if (t != matcher.getRoot()) {
            next = matcher.getParent(t);
            if (next.numChildren() != 1) {
              next = null;
            }
          }
        }
      };
    }
  };

  private static final Relation HAS_ONLY_CHILD = new Relation("<:") {

    private static final long serialVersionUID = -8776487500849294279L;

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        @Override
        void initialize() {
          if (!t.isLeaf() && t.numChildren() == 1) {
            next = t.firstChild();
          }
        }
      };
    }
  };

  private static final Relation UNARY_PATH_ANCESTOR_OF = new Relation("<<:") {

    private static final long serialVersionUID = -742912038636163403L;

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        Stack<Tree> searchStack;

        @Override
        public void initialize() {
          searchStack = new Stack<>();
          if (!t.isLeaf() && t.children().length == 1)
            searchStack.push(t.getChild(0));
          if (!searchStack.isEmpty()) {
            advance();
          }
        }

        @Override
        void advance() {
          if (searchStack.isEmpty()) {
            next = null;
          } else {
            next = searchStack.pop();
            if (!next.isLeaf() && next.children().length == 1)
              searchStack.push(next.getChild(0));
          }
        }
      };
    }
  };

  private static final Relation UNARY_PATH_DESCENDANT_OF = new Relation(">>:") {

    private static final long serialVersionUID = 4364021807752979404L;

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        Stack<Tree> searchStack;

        @Override
        public void initialize() {
          searchStack = new Stack<>();
          Tree parent = matcher.getParent(t);
          if (parent != null && !parent.isLeaf() &&
              parent.children().length == 1)
            searchStack.push(parent);
          if (!searchStack.isEmpty()) {
            advance();
          }
        }

        @Override
        void advance() {
          if (searchStack.isEmpty()) {
            next = null;
          } else {
            next = searchStack.pop();
            Tree parent = matcher.getParent(next);
            if (parent != null && !parent.isLeaf() &&
                parent.children().length == 1)
              searchStack.push(parent);
          }
        }
      };
    }
  };

  private static final Relation PARENT_EQUALS = new Relation("<=") {
    private static final long serialVersionUID = 98745298745198245L;

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        int nextNum;
        boolean usedParent;

        @Override
        public void advance() {
          if (!usedParent) {
            next = t;
            usedParent = true;
          } else {
            if (nextNum < t.numChildren()) {
              next = t.getChild(nextNum);
              nextNum++;
            } else {
              next = null;
            }
          }
        }
      };
    }
  };

  private static final Relation ANCESTOR_OF_LEAF = new Relation("<<<") {

    private static final long serialVersionUID = -78542691313554L;

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        Stack<Tree> searchStack;

        @Override
        public void initialize() {
          searchStack = new Stack<>();
          for (int i = t.numChildren() - 1; i >= 0; i--) {
            searchStack.push(t.getChild(i));
          }
          if (!searchStack.isEmpty()) {
            advance();
          }
        }

        @Override
        void advance() {
          // using getLeaves() would be simpler code,
          // but this was easy enough
          next = null;
          while (next == null && !searchStack.isEmpty()) {
            next = searchStack.pop();
            for (int i = next.numChildren() - 1; i >= 0; i--) {
              searchStack.push(next.getChild(i));
            }
            // we've added the next layer of children, but we're not
            // at a leaf, so keep going if there are nodes left to search
            if (!next.isLeaf()) {
              next = null;
            }
          }
        }
      };
    }
  };

  /**
   * Looks for the ith leaf of the current node
   */
  private static class AncestorOfIthLeaf extends Relation {

    private static final long serialVersionUID = -6495191354526L;

    private final int leafNum;

    AncestorOfIthLeaf(int i) {
      super("<<<" + String.valueOf(i));
      if (i == 0) {
        throw new IllegalArgumentException("Error -- no such thing as zeroth leaf!");
      }
      leafNum = i;
    }

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        @Override
        void initialize() {
          // this is a little lazy in terms of coding
          // would be a bit faster to actually recurse through the tree
          // this is unlikely to ever be a performance limitation, though
          List<Tree> leaves = t.getLeaves();
          if (leaves.size() >= Math.abs(leafNum)) {
            final int index;
            if (leafNum > 0) {
              index = leafNum - 1;
            } else {
              index = leafNum + leaves.size();
            }
            next = leaves.get(index);
          }
        }
      };
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof AncestorOfIthLeaf)) {
        return false;
      }

      final AncestorOfIthLeaf other = (AncestorOfIthLeaf) o;
      if (leafNum != other.leafNum) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      return leafNum + 20;
    }
  };

  private static final Relation[] SIMPLE_RELATIONS = {
      DOMINATES, DOMINATED_BY, PARENT_OF, CHILD_OF, PRECEDES,
      IMMEDIATELY_PRECEDES, FOLLOWS, IMMEDIATELY_FOLLOWS,
          HAS_LEFTMOST_DESCENDANT, HAS_RIGHTMOST_DESCENDANT,
          LEFTMOST_DESCENDANT_OF, RIGHTMOST_DESCENDANT_OF, SISTER_OF,
      LEFT_SISTER_OF, RIGHT_SISTER_OF, IMMEDIATE_LEFT_SISTER_OF,
      IMMEDIATE_RIGHT_SISTER_OF, ONLY_CHILD_OF, HAS_ONLY_CHILD, EQUALS,
      PATTERN_SPLITTER,UNARY_PATH_ANCESTOR_OF, UNARY_PATH_DESCENDANT_OF,
      PARENT_EQUALS, ANCESTOR_OF_LEAF };

  private static final Map<String, Relation> SIMPLE_RELATIONS_MAP = Generics.newHashMap();

  static {
    for (Relation r : SIMPLE_RELATIONS) {
      SIMPLE_RELATIONS_MAP.put(r.symbol, r);
    }
    SIMPLE_RELATIONS_MAP.put("<<`", HAS_RIGHTMOST_DESCENDANT);
    SIMPLE_RELATIONS_MAP.put("<<,", HAS_LEFTMOST_DESCENDANT);
    SIMPLE_RELATIONS_MAP.put(">>`", RIGHTMOST_DESCENDANT_OF);
    SIMPLE_RELATIONS_MAP.put(">>,", LEFTMOST_DESCENDANT_OF);
    SIMPLE_RELATIONS_MAP.put("$..", LEFT_SISTER_OF);
    SIMPLE_RELATIONS_MAP.put("$,,", RIGHT_SISTER_OF);
    SIMPLE_RELATIONS_MAP.put("$.", IMMEDIATE_LEFT_SISTER_OF);
    SIMPLE_RELATIONS_MAP.put("$,", IMMEDIATE_RIGHT_SISTER_OF);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Relation)) {
      return false;
    }

    final Relation relation = (Relation) o;

    return symbol.equals(relation.symbol);
  }

  @Override
  public int hashCode() {
    return symbol.hashCode();
  }


  private static class Heads extends Relation {

    private static final long serialVersionUID = 4681433462932265831L;

    final HeadFinder hf;

    Heads(HeadFinder hf) {
      super(">>#");
      this.hf = hf;
    }

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        @Override
        void initialize() {
          next = t;
          advance();
        }

        @Override
        public void advance() {
          HeadFinder headFinder = matcher.getHeadFinder();
          if (headFinder == null) headFinder = hf;

          Tree last = next;
          next = matcher.getParent(next);
          if (next != null && headFinder.determineHead(next) != last) {
            next = null;
          }
        }
      };
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Heads)) {
        return false;
      }
      if (!super.equals(o)) {
        return false;
      }

      final Heads heads = (Heads) o;

      if (hf != null ? !hf.equals(heads.hf) : heads.hf != null) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 29 * result + (hf != null ? hf.hashCode() : 0);
      return result;
    }
  }


  private static class HeadedBy extends Relation {

    private static final long serialVersionUID = 2825997185749055693L;

    private final Heads heads;

    HeadedBy(HeadFinder hf) {
      super("<<#");
      this.heads = Interner.globalIntern(new Heads(hf));
    }

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        @Override
        void initialize() {
          next = t;
          advance();
        }

        @Override
        public void advance() {
          if (next.isLeaf()) {
            next = null;
          } else {
            if (matcher.getHeadFinder() != null) {
              next = matcher.getHeadFinder().determineHead(next);
            } else {
              next = heads.hf.determineHead(next);
            }
          }
        }
      };
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof HeadedBy)) {
        return false;
      }
      if (!super.equals(o)) {
        return false;
      }

      final HeadedBy headedBy = (HeadedBy) o;

      if (heads != null ? !heads.equals(headedBy.heads)
          : headedBy.heads != null) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 29 * result + (heads != null ? heads.hashCode() : 0);
      return result;
    }
  }


  private static class ImmediatelyHeads extends Relation {


    private static final long serialVersionUID = 2085410152913894987L;

    private final HeadFinder hf;

    ImmediatelyHeads(HeadFinder hf) {
      super(">#");
      this.hf = hf;
    }

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        @Override
        void initialize() {
          if (t != matcher.getRoot()) {
            next = matcher.getParent(t);
            HeadFinder headFinder = matcher.getHeadFinder() == null ? hf : matcher.getHeadFinder();
            if (headFinder.determineHead(next) != t) {
              next = null;
            }
          }
        }
      };
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ImmediatelyHeads)) {
        return false;
      }
      if (!super.equals(o)) {
        return false;
      }

      final ImmediatelyHeads immediatelyHeads = (ImmediatelyHeads) o;

      if (hf != null ? !hf.equals(immediatelyHeads.hf) : immediatelyHeads.hf != null) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 29 * result + (hf != null ? hf.hashCode() : 0);
      return result;
    }
  }


  private static class ImmediatelyHeadedBy extends Relation {

    private static final long serialVersionUID = 5910075663419780905L;

    private final ImmediatelyHeads immediatelyHeads;

    ImmediatelyHeadedBy(HeadFinder hf) {
      super("<#");
      this.immediatelyHeads = Interner
          .globalIntern(new ImmediatelyHeads(hf));
    }

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        @Override
        void initialize() {
          if (!t.isLeaf()) {
            if (matcher.getHeadFinder() != null) {
              next = matcher.getHeadFinder().determineHead(t);
            } else {
              next = immediatelyHeads.hf.determineHead(t);
            }
          }
        }
      };
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ImmediatelyHeadedBy)) {
        return false;
      }
      if (!super.equals(o)) {
        return false;
      }

      final ImmediatelyHeadedBy immediatelyHeadedBy = (ImmediatelyHeadedBy) o;

      if (immediatelyHeads != null ? !immediatelyHeads
          .equals(immediatelyHeadedBy.immediatelyHeads)
          : immediatelyHeadedBy.immediatelyHeads != null) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 29 * result
          + (immediatelyHeads != null ? immediatelyHeads.hashCode() : 0);
      return result;
    }
  }


  private static class IthChildOf extends Relation {

    private static final long serialVersionUID = -1463126827537879633L;

    private final int childNum;

    IthChildOf(int i) {
      super('>' + String.valueOf(i));
      if (i == 0) {
        throw new IllegalArgumentException(
            "Error -- no such thing as zeroth child!");
      } else {
        childNum = i;
      }
    }

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        @Override
        void initialize() {
          if (t != matcher.getRoot()) {
            next = matcher.getParent(t);
            if (childNum > 0
                && (next.numChildren() < childNum || next
                    .getChild(childNum - 1) != t)
                || childNum < 0
                && (next.numChildren() < -childNum || next.getChild(next
                    .numChildren()
                    + childNum) != t)) {
              next = null;
            }
          }
        }
      };
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof IthChildOf)) {
        return false;
      }

      final IthChildOf ithChildOf = (IthChildOf) o;

      if (childNum != ithChildOf.childNum) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      return childNum;
    }

  }


  private static class HasIthChild extends Relation {

    private static final long serialVersionUID = 3546853729291582806L;

    private final IthChildOf ithChildOf;

    HasIthChild(int i) {
      super('<' + String.valueOf(i));
      ithChildOf = Interner.globalIntern(new IthChildOf(i));
    }

    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        @Override
        void initialize() {
          int childNum = ithChildOf.childNum;
          if (t.numChildren() >= Math.abs(childNum)) {
            if (childNum > 0) {
              next = t.getChild(childNum - 1);
            } else {
              next = t.getChild(t.numChildren() + childNum);
            }
          }
        }
      };
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof HasIthChild)) {
        return false;
      }
      if (!super.equals(o)) {
        return false;
      }

      final HasIthChild hasIthChild = (HasIthChild) o;

      if (ithChildOf != null ? !ithChildOf.equals(hasIthChild.ithChildOf)
          : hasIthChild.ithChildOf != null) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 29 * result + (ithChildOf != null ? ithChildOf.hashCode() : 0);
      return result;
    }
  }




  private static class UnbrokenCategoryDominates extends Relation {

    private static final long serialVersionUID = -4174923168221859262L;

    private final Pattern pattern;
    private final boolean negatedPattern;
    private final boolean basicCat;
    private Function<String, String> basicCatFunction;


    /**
     *
     * @param arg This may have a ! and then maybe a @ and then either an
     *            identifier or regex
     */
    UnbrokenCategoryDominates(String arg,
                              Function<String, String> basicCatFunction) {
      super("<+(" + arg + ')');
      if (arg.startsWith("!")) {
        negatedPattern = true;
        arg = arg.substring(1);
      } else {
        negatedPattern = false;
      }
      if (arg.startsWith("@")) {
        basicCat = true;
        this.basicCatFunction = basicCatFunction;
        arg = arg.substring(1);
      } else {
        basicCat = false;
      }
      if (arg.matches("/.*/")) {
        pattern = Pattern.compile(arg.substring(1, arg.length() - 1));
      } else if (arg.matches("__")) {
        pattern = Pattern.compile("^.*$");
      } else {
        pattern = Pattern.compile("^(?:" + arg + ")$");
      }
    }

    private boolean pathMatchesNode(Tree node) {
      String lab = node.value();
      // added this code to not crash if null node, even though there probably should be null nodes in the tree
      if (lab == null) {
        // Say that a null label matches no positive pattern, but any negated patern
        return negatedPattern;
      } else {
        if (basicCat) {
          lab = basicCatFunction.apply(lab);
        }
        Matcher m = pattern.matcher(lab);
        return m.find() != negatedPattern;
      }
    }

    /** {@inheritDoc} */
    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        Stack<Tree> searchStack;

        @Override
        public void initialize() {
          searchStack = new Stack<>();
          for (int i = t.numChildren() - 1; i >= 0; i--) {
            searchStack.push(t.getChild(i));
          }
          if (!searchStack.isEmpty()) {
            advance();
          }
        }

        @Override
        void advance() {
          if (searchStack.isEmpty()) {
            next = null;
          } else {
            next = searchStack.pop();
            if (pathMatchesNode(next)) {
              for (int i = next.numChildren() - 1; i >= 0; i--) {
                searchStack.push(next.getChild(i));
              }
            }
          }
        }
      };
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof UnbrokenCategoryDominates)) {
        return false;
      }

      final UnbrokenCategoryDominates unbrokenCategoryDominates = (UnbrokenCategoryDominates) o;

      if (negatedPattern != unbrokenCategoryDominates.negatedPattern) {
        return false;
      }
      if (!pattern.equals(unbrokenCategoryDominates.pattern)) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      int result;
      result = pattern.hashCode();
      result = 29 * result + (negatedPattern ? 1 : 0);
      return result;
    }

  } // end class UnbrokenCategoryDominates


  private static class UnbrokenCategoryIsDominatedBy extends Relation {

    private static final long serialVersionUID = 2867922828235355129L;

    private final UnbrokenCategoryDominates unbrokenCategoryDominates;

    UnbrokenCategoryIsDominatedBy(String arg,
                                  Function<String, String> basicCatFunction) {
      super(">+(" + arg + ')');
      unbrokenCategoryDominates = Interner
        .globalIntern((new UnbrokenCategoryDominates(arg, basicCatFunction)));
    }

    /** {@inheritDoc} */
    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        @Override
        void initialize() {
          next = matcher.getParent(t);
        }

        @Override
        public void advance() {
          if (unbrokenCategoryDominates.pathMatchesNode(next)) {
            next = matcher.getParent(next);
          } else {
            next = null;
          }
        }
      };
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof UnbrokenCategoryIsDominatedBy)) {
        return false;
      }
      if (!super.equals(o)) {
        return false;
      }

      final UnbrokenCategoryIsDominatedBy unbrokenCategoryIsDominatedBy = (UnbrokenCategoryIsDominatedBy) o;

      return unbrokenCategoryDominates.equals(unbrokenCategoryIsDominatedBy.unbrokenCategoryDominates);
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 29 * result + unbrokenCategoryDominates.hashCode();
      return result;
    }
  }


  /**
   * Note that this only works properly for context-free trees.
   * Also, the use of initialize and advance is not very efficient just yet.  Finally, each node in the tree
   * is added only once, even if there is more than one unbroken-category precedence path to it.
   *
   */
  private static class UnbrokenCategoryPrecedes extends Relation {

    private static final long serialVersionUID = 6866888667804306111L;

    private final Pattern pattern;
    private final boolean negatedPattern;
    private final boolean basicCat;
    private Function<String, String> basicCatFunction;

    /**
     * @param arg The pattern to match, perhaps preceded by ! and/or @
     */
    UnbrokenCategoryPrecedes(String arg,
                             Function<String, String> basicCatFunction) {
      super(".+(" + arg + ')');
      if (arg.startsWith("!")) {
        negatedPattern = true;
        arg = arg.substring(1);
      } else {
        negatedPattern = false;
      }
      if (arg.startsWith("@")) {
        basicCat = true;
        this.basicCatFunction = basicCatFunction; // todo -- this was missing a this. which must be testable in a unit test!!! Make one
        arg = arg.substring(1);
      } else {
        basicCat = false;
      }
      if (arg.matches("/.*/")) {
        pattern = Pattern.compile(arg.substring(1, arg.length() - 1));
      } else if (arg.matches("__")) {
        pattern = Pattern.compile("^.*$");
      } else {
        pattern = Pattern.compile("^(?:" + arg + ")$");
      }
    }

    private boolean pathMatchesNode(Tree node) {
      String lab = node.value();
      // added this code to not crash if null node, even though there probably should be null nodes in the tree
      if (lab == null) {
        // Say that a null label matches no positive pattern, but any negated pattern
        return negatedPattern;
      } else {
        if (basicCat) {
          lab = basicCatFunction.apply(lab);
        }
        Matcher m = pattern.matcher(lab);
        return m.find() != negatedPattern;
      }
    }

    /** {@inheritDoc} */
    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        private IdentityHashSet<Tree> nodesToSearch;
        private Stack<Tree> searchStack;

        @Override
        public void initialize() {
          nodesToSearch = new IdentityHashSet<>();
          searchStack = new Stack<>();
          initializeHelper(searchStack, t, matcher.getRoot());
          advance();
        }

        private void initializeHelper(Stack<Tree> stack, Tree node, Tree root) {
          if (node==root) {
            return;
          }
          Tree parent = matcher.getParent(node);
          int i = parent.objectIndexOf(node);
          while (i == parent.children().length-1 && parent != root) {
            node = parent;
            parent = matcher.getParent(parent);
            i = parent.objectIndexOf(node);
          }
          Tree followingNode;
          if (i+1 < parent.children().length) {
            followingNode = parent.children()[i+1];
          } else {
            followingNode = null;
          }
          while (followingNode != null) {
            //log.info("adding to stack node " + followingNode.toString());
            if (! nodesToSearch.contains(followingNode)) {
              stack.add(followingNode);
              nodesToSearch.add(followingNode);
            }
            if (pathMatchesNode(followingNode)) {
              initializeHelper(stack, followingNode, root);
            }
            if (! followingNode.isLeaf()) {
              followingNode = followingNode.children()[0];
            } else {
              followingNode = null;
            }
          }
        }

        @Override
        void advance() {
          if (searchStack.isEmpty()) {
            next = null;
          } else {
            next = searchStack.pop();
          }
        }
      };
    }
  }


  /**
   * Note that this only works properly for context-free trees.
   * Also, the use of initialize and advance is not very efficient just yet.  Finally, each node in the tree
   * is added only once, even if there is more than one unbroken-category precedence path to it.
   */
  private static class UnbrokenCategoryFollows extends Relation {

    private static final long serialVersionUID = -7890430001297866437L;

    private final Pattern pattern;
    private final boolean negatedPattern;
    private final boolean basicCat;
    private Function<String, String> basicCatFunction;

    /**
     * @param arg The pattern to match, perhaps preceded by ! and/or @
     */
    UnbrokenCategoryFollows(String arg,
                            Function<String, String> basicCatFunction) {
      super(",+(" + arg + ')');
      if (arg.startsWith("!")) {
        negatedPattern = true;
        arg = arg.substring(1);
      } else {
        negatedPattern = false;
      }
      if (arg.startsWith("@")) {
        basicCat = true;
        this.basicCatFunction = basicCatFunction;
        arg = arg.substring(1);
      } else {
        basicCat = false;
      }
      if (arg.matches("/.*/")) {
        pattern = Pattern.compile(arg.substring(1, arg.length() - 1));
      } else if (arg.matches("__")) {
        pattern = Pattern.compile("^.*$");
      } else {
        pattern = Pattern.compile("^(?:" + arg + ")$");
      }
    }

    private boolean pathMatchesNode(Tree node) {
      String lab = node.value();
      // added this code to not crash if null node, even though there probably should be null nodes in the tree
      if (lab == null) {
        // Say that a null label matches no positive pattern, but any negated pattern
        return negatedPattern;
      } else {
        if (basicCat) {
          lab = basicCatFunction.apply(lab);
        }
        Matcher m = pattern.matcher(lab);
        return m.find() != negatedPattern;
      }
    }

    /** {@inheritDoc} */
    @Override
    Iterator<Tree> searchNodeIterator(final Tree t,
                                      final TregexMatcher matcher) {
      return new SearchNodeIterator() {
        IdentityHashSet<Tree> nodesToSearch;
        Stack<Tree> searchStack;

        @Override
        public void initialize() {
          nodesToSearch = new IdentityHashSet<>();
          searchStack = new Stack<>();
          initializeHelper(searchStack, t, matcher.getRoot());
          advance();
        }

        private void initializeHelper(Stack<Tree> stack, Tree node, Tree root) {
          if (node==root) {
            return;
          }
          Tree parent = matcher.getParent(node);
          int i = parent.objectIndexOf(node);
          while (i == 0 && parent != root) {
            node = parent;
            parent = matcher.getParent(parent);
            i = parent.objectIndexOf(node);
          }
          Tree precedingNode;
          if (i > 0) {
            precedingNode = parent.children()[i-1];
          } else {
            precedingNode = null;
          }
          while (precedingNode != null) {
            //log.info("adding to stack node " + precedingNode.toString());
            if ( ! nodesToSearch.contains(precedingNode)) {
              stack.add(precedingNode);
              nodesToSearch.add(precedingNode);
            }
            if (pathMatchesNode(precedingNode)) {
              initializeHelper(stack, precedingNode, root);
            }
            if (! precedingNode.isLeaf()) {
              precedingNode = precedingNode.children()[0];
            } else {
              precedingNode = null;
            }
          }
        }

        @Override
        void advance() {
          if (searchStack.isEmpty()) {
            next = null;
          } else {
            next = searchStack.pop();
          }
        }
      };
    }
  }

}

