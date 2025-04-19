package edu.stanford.nlp.semgraph.semgrex; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.*;
import java.util.function.Predicate;

/**
 * An abstract base class for relations between graph nodes in semgrex. There
 * are two types of subclasses: static anonymous singleton instantiations for
 * relations that do not require arguments, and private subclasses for those
 * with arguments. All invocations should be made through the static factory
 * methods.
 * <br>
 * If you want to add a new relation, you just have to fill in the definition of
 * <code>satisfies()</code> and <code>searchNodeIterator()</code>. Also be
 * careful to make the appropriate adjustments to
 * <code>getRelation()</code>. Finally, if you are using the SemgrexParser, you
 * need to add the new relation symbol to the list of tokens. <br>
 *
 * @author Chloe Kiddon
 */
abstract class GraphRelation implements Serializable {
  final String symbol;
  final Predicate<String> type;
  final String rawType;

  final String name;
  final String edgeName;

  //"<" | ">" | ">>" | "<<" | "<#" | ">#" | ":" | "@">


  /**
   * Returns <code>true</code> iff this <code>GraphRelation</code> holds between
   * the given pair of nodes in the given semantic graph.
   */
  abstract boolean satisfies(IndexedWord n1, IndexedWord n2, SemanticGraph sg);

  /**
   * For a given node and its root, returns an {@link Iterator} over the nodes
   * of the semantic graph that satisfy the relation.
   */
  abstract Iterator<IndexedWord> searchNodeIterator(IndexedWord node, SemanticGraph sg);

  private GraphRelation(String symbol, String type, String name, String edgeName) {
    this.symbol = symbol;
    this.type   = getPattern(type);
    this.rawType = type;
    this.edgeName = edgeName;
    if (name != null && edgeName != null && !name.equals(edgeName)) {
      throw new SemgrexParseException("GraphRelation had both = and ~ set, but the names were different!  " + this.toString());
    }
    if (name != null) {
      this.name = name;
    } else {
      this.name = edgeName;
    }
  }

  private GraphRelation(String symbol, String type, String name) {
    this(symbol, type, name, null);
  }

  private GraphRelation(String symbol, String type) {
    this(symbol, type, null);
  }

  private GraphRelation(String symbol) {
    this(symbol, null);
  }

  @Override
  public String toString() {
    return symbol + ((rawType != null) ? rawType : "") + ((name != null) ? "=" + name : "") + ((edgeName != null) ? "~" + edgeName : "");
  }

  public Predicate<String> getPattern(String relnType)
  {
    if ((relnType == null) || (relnType.equals(""))) {
      return Filters.acceptFilter();
    } else if (relnType.matches("/.*/")) {
      return new RegexStringFilter(relnType.substring(1, relnType.length() - 1));
    } else { // raw description
      return new ArrayStringFilter(ArrayStringFilter.Mode.EXACT, relnType);
    }
  }

  public String getName() {
    if (name == null || name.equals("")) return null;
    return name;
  }

  public String getEdgeName() {
    if (edgeName == null || edgeName.equals("")) return null;
    return edgeName;
  }


  // ALIGNMENT graph relation: "@" ==============================================

  static class ALIGNMENT extends GraphRelation {

    private Alignment alignment;
    private boolean hypToText;

    ALIGNMENT() {
      super("@", "");
      hypToText = true;
    }

    void setAlignment(Alignment alignment, boolean hypToText, SearchNodeIterator itr) {
      this.alignment = alignment;
      this.hypToText = hypToText;
      //log.info("setting alignment");
      itr.advance();
    }

    @Override
    boolean satisfies(IndexedWord l1, IndexedWord l2, SemanticGraph sg) {
      if (alignment == null) return false;
      if (hypToText)
        return (alignment.getMap().get(l1)).equals(l2);
      else {
        return (alignment.getMap().get(l2)).equals(l1);
      }
    }

    @Override
    Iterator<IndexedWord> searchNodeIterator(final IndexedWord node, final SemanticGraph sg) {
      return new SearchNodeIterator() {

          boolean foundOnce = false;
          int nextNum;

          // not really initialized until alignment is set
          @Override
          public void initialize() {

          }

          @Override
          public void advance() {
            if (alignment == null) return;
            if (node.equals(IndexedWord.NO_WORD)) next = null;
            //log.info("node: " + node.word());
            if (hypToText) {
              if (!foundOnce) {
                next = alignment.getMap().get(node);
                foundOnce = true;
              //  if (next == null) log.info("no alignment"); else
               // log.info("other graph: " + next.word());
              }
              else {
                next = null;
                //log.info("next: null");
              }
            } else {

              int num = 0;
              for (Map.Entry<IndexedWord, IndexedWord> pair : alignment.getMap().entrySet()) {
                if (pair.getValue().equals(node)) {
                  if (nextNum == num) {
                    next = pair.getKey();
                    nextNum++;
                    //log.info("next: " + next.word());
                    return;
                  }
                  num++;
                }
              }
              //log.info("backwards, next: null");
              next = null;
            }
          }

        };
    }

    // Generated automatically by Eclipse
    private static final long serialVersionUID = -2936526066368043778L;
  };


  // ROOT graph relation: "Root" ================================================

  static final GraphRelation ROOT = new GraphRelation("", "") {
      @Override
      boolean satisfies(IndexedWord l1, IndexedWord l2, SemanticGraph sg) {
        return l1 == l2;
      }

      @Override
      Iterator<IndexedWord> searchNodeIterator(final IndexedWord node, final SemanticGraph sg) {
        return new SearchNodeIterator() {
            @Override
            void initialize() {
              next = node;
            }
          };
      }
      // automatically generated by Eclipse
      private static final long serialVersionUID = 4710135995247390313L;
  };

  static final GraphRelation ITERATOR = new GraphRelation(":", "") {
      @Override
      boolean satisfies(IndexedWord l1, IndexedWord l2, SemanticGraph sg) {
        return true;
      }

      Iterator<IndexedWord> searchNodeIterator(final IndexedWord node,
                                               final SemanticGraph sg) {
        return sg.vertexSet().iterator();
      }
      // automatically generated by Eclipse
      private static final long serialVersionUID = 5259713498453659251L;
    };


  // ALIGNED_ROOT graph relation: "AlignRoot" ===================================

  static final GraphRelation ALIGNED_ROOT = new GraphRelation("AlignRoot", "") {
      @Override
      boolean satisfies(IndexedWord l1, IndexedWord l2, SemanticGraph sg) {
        return l1 == l2;
      }

      @Override
      Iterator<IndexedWord> searchNodeIterator(final IndexedWord node, final SemanticGraph sg) {
        return new SearchNodeIterator() {
            @Override
            void initialize() {
              next = node;
            }
          };
      }

      // automatically generated by Eclipse
      private static final long serialVersionUID = -3088857488269777611L;
  };


  // GOVERNOR graph relation: ">" ===============================================

  static private class GOVERNOR extends GraphRelation {
    GOVERNOR(String reln, String name, String edgeName) {
      super(">", reln, name, edgeName);
    }

    @Override
    boolean satisfies(IndexedWord l1, IndexedWord l2, SemanticGraph sg) {
      List<Pair<GrammaticalRelation, IndexedWord>> deps = sg.childPairs(l1);
      for (Pair<GrammaticalRelation, IndexedWord> dep : deps) {
        if (this.type.test(dep.first().toString()) &&
            dep.second().equals(l2)) {
          return true;
        }
      }
      return false;
    }

    @Override
    Iterator<IndexedWord> searchNodeIterator(final IndexedWord node, final SemanticGraph sg) {
      return new SearchNodeIterator() {
          Iterator<SemanticGraphEdge> iterator;

          @Override
          public void advance() {
            if (node.equals(IndexedWord.NO_WORD)) {
              next = null;
              return;
            }
            if (iterator == null) {
              iterator = sg.outgoingEdgeIterator(node);
            }
            while (iterator.hasNext()) {
              SemanticGraphEdge edge = iterator.next();
              relation = edge.getRelation().toString();
              if (!type.test(relation)) {
                continue;
              }
              this.next = edge.getTarget();
              this.edge = edge;
              return;
            }
            this.next = null;
            this.edge = null;
          }
      };
    }

    // automatically generated by Eclipse
    private static final long serialVersionUID = -7003148918274183951L;
  };

  static private class GOVERNOR_RIGHT extends GraphRelation {
    GOVERNOR_RIGHT(String reln, String name, String edgeName) {
      super(">++", reln, name, edgeName);
    }


    @Override
    boolean satisfies(IndexedWord l1, IndexedWord l2, SemanticGraph sg) {
      List<Pair<GrammaticalRelation, IndexedWord>> deps = sg.childPairs(l1);
      for (Pair<GrammaticalRelation, IndexedWord> dep : deps) {
        if (this.type.test(dep.first().toString()) &&
            dep.second().equals(l2) &&
            l1.index() < l2.index()) {
          return true;
        }
      }
      return false;
    }

    @Override
    Iterator<IndexedWord> searchNodeIterator(final IndexedWord node, final SemanticGraph sg) {
      return new SearchNodeIterator() {
          Iterator<SemanticGraphEdge> iterator;

          @Override
          public void advance() {
            if (node.equals(IndexedWord.NO_WORD)) {
              next = null;
              return;
            }
            if (iterator == null) {
              iterator = sg.outgoingEdgeIterator(node);
            }
            while (iterator.hasNext()) {
              SemanticGraphEdge edge = iterator.next();
              relation = edge.getRelation().toString();
              if (!type.test(relation)) {
                continue;
              }
              if (node.index() < edge.getTarget().index()) {
                this.next = edge.getTarget();
                this.edge = edge;
                return;
              }
            }
            this.next = null;
            this.edge = null;
          }
      };
    }

    // automatically generated by button mashing
    private static final long serialVersionUID = -13455768987623L;
  }

  static private class GOVERNOR_LEFT extends GraphRelation {
    GOVERNOR_LEFT(String reln, String name, String edgeName) {
      super(">--", reln, name, edgeName);
    }


    @Override
    boolean satisfies(IndexedWord l1, IndexedWord l2, SemanticGraph sg) {
      List<Pair<GrammaticalRelation, IndexedWord>> deps = sg.childPairs(l1);
      for (Pair<GrammaticalRelation, IndexedWord> dep : deps) {
        if (this.type.test(dep.first().toString()) &&
            dep.second().equals(l2) &&
            l1.index() > l2.index()) {
          return true;
        }
      }
      return false;
    }

    @Override
    Iterator<IndexedWord> searchNodeIterator(final IndexedWord node, final SemanticGraph sg) {
      return new SearchNodeIterator() {
          Iterator<SemanticGraphEdge> iterator;

          @Override
          public void advance() {
            if (node.equals(IndexedWord.NO_WORD)) {
              next = null;
              return;
            }
            if (iterator == null) {
              iterator = sg.outgoingEdgeIterator(node);
            }
            while (iterator.hasNext()) {
              SemanticGraphEdge edge = iterator.next();
              relation = edge.getRelation().toString();
              if (!type.test(relation)) {
                continue;
              }
              if (node.index() > edge.getTarget().index()) {
                this.next = edge.getTarget();
                this.edge = edge;
                return;
              }
            }
            this.next = null;
            this.edge = null;
          }
      };
    }

    // automatically generated by button mashing
    private static final long serialVersionUID = -84679864256L;
  }

  // DEPENDENT graph relation: "<" ===============================================

  static private class DEPENDENT extends GraphRelation {
    DEPENDENT(String reln, String name, String edgeName) {
      super("<", reln, name, edgeName);
    }

    @Override
    boolean satisfies(IndexedWord l1, IndexedWord l2, SemanticGraph sg) {
      if (l1.equals(IndexedWord.NO_WORD) || l2.equals(IndexedWord.NO_WORD) )
        return false;
      List<Pair<GrammaticalRelation, IndexedWord>> govs = sg.parentPairs(l1);
      for (Pair<GrammaticalRelation, IndexedWord> gov : govs) {
        if (this.type.test(gov.first().toString()) &&
            gov.second().equals(l2)) return true;
      }
      return false;
    }

    @Override
    Iterator<IndexedWord> searchNodeIterator(final IndexedWord node, final SemanticGraph sg) {
      return new SearchNodeIterator() {
          Iterator<SemanticGraphEdge> iterator;

          @Override
          public void advance() {
            if (node.equals(IndexedWord.NO_WORD)) {
              next = null;
              return;
            }
            if (iterator == null) {
              iterator = sg.incomingEdgeIterator(node);
            }
            while (iterator.hasNext()) {
              SemanticGraphEdge edge = iterator.next();
              relation = edge.getRelation().toString();
              if (!type.test(relation)) {
                continue;
              }
              this.next = edge.getSource();
              this.edge = edge;
              return;
            }
            this.next = null;
            this.edge = null;
          }
      };
    }

    // automatically generated by Eclipse
    private static final long serialVersionUID = -5115389883698108694L;
  };


  static private class DEPENDENT_RIGHT extends GraphRelation {
    DEPENDENT_RIGHT(String reln, String name, String edgeName) {
      super("<++", reln, name, edgeName);
    }

    @Override
    boolean satisfies(IndexedWord l1, IndexedWord l2, SemanticGraph sg) {
      if (l1.equals(IndexedWord.NO_WORD) || l2.equals(IndexedWord.NO_WORD) )
        return false;
      List<Pair<GrammaticalRelation, IndexedWord>> govs = sg.parentPairs(l1);
      for (Pair<GrammaticalRelation, IndexedWord> gov : govs) {
        if (this.type.test(gov.first().toString()) &&
            gov.second().equals(l2) &&
            l1.index() < l2.index()) {
          return true;
        }
      }
      return false;
    }

    @Override
    Iterator<IndexedWord> searchNodeIterator(final IndexedWord node, final SemanticGraph sg) {
      return new SearchNodeIterator() {
          Iterator<SemanticGraphEdge> iterator;

          @Override
          public void advance() {
            if (node.equals(IndexedWord.NO_WORD)) {
              next = null;
              return;
            }
            if (iterator == null) {
              iterator = sg.incomingEdgeIterator(node);
            }
            while (iterator.hasNext()) {
              SemanticGraphEdge edge = iterator.next();
              relation = edge.getRelation().toString();
              if (!type.test(relation)) {
                continue;
              }
              if (node.index() < edge.getSource().index()) {
                this.next = edge.getSource();
                this.edge = edge;
                return;
              }
            }
            this.next = null;
            this.edge = null;
          }
      };
    }

    // automatically generated by button mashing
    private static final long serialVersionUID = -98652734561235L;
  };


  static private class DEPENDENT_LEFT extends GraphRelation {
    DEPENDENT_LEFT(String reln, String name, String edgeName) {
      super("<--", reln, name, edgeName);
    }

    @Override
    boolean satisfies(IndexedWord l1, IndexedWord l2, SemanticGraph sg) {
      if (l1.equals(IndexedWord.NO_WORD) || l2.equals(IndexedWord.NO_WORD) )
        return false;
      List<Pair<GrammaticalRelation, IndexedWord>> govs = sg.parentPairs(l1);
      for (Pair<GrammaticalRelation, IndexedWord> gov : govs) {
        if (this.type.test(gov.first().toString()) &&
            gov.second().equals(l2) &&
            l1.index() > l2.index()) {
          return true;
        }
      }
      return false;
    }


    @Override
    Iterator<IndexedWord> searchNodeIterator(final IndexedWord node, final SemanticGraph sg) {
      return new SearchNodeIterator() {
          Iterator<SemanticGraphEdge> iterator;

          @Override
          public void advance() {
            if (node.equals(IndexedWord.NO_WORD)) {
              next = null;
              return;
            }
            if (iterator == null) {
              iterator = sg.incomingEdgeIterator(node);
            }
            while (iterator.hasNext()) {
              SemanticGraphEdge edge = iterator.next();
              relation = edge.getRelation().toString();
              if (!type.test(relation)) {
                continue;
              }
              if (node.index() > edge.getSource().index()) {
                this.next = edge.getSource();
                this.edge = edge;
                return;
              }
            }
            this.next = null;
            this.edge = null;
          }
      };
    }

    // automatically generated by button mashing
    private static final long serialVersionUID = -1356537761324587L;
  };


  static private class LIMITED_GRANDPARENT extends GraphRelation {
    final int startDepth, endDepth;

    LIMITED_GRANDPARENT(String reln, String name,
                        int startDepth, int endDepth) {
      super(startDepth + "," + endDepth + ">>", reln, name);
      this.startDepth = startDepth;
      this.endDepth = endDepth;
    }

    @Override
    boolean satisfies(IndexedWord l1, IndexedWord l2, SemanticGraph sg) {
      if (l1.equals(IndexedWord.NO_WORD) || l2.equals(IndexedWord.NO_WORD) )
        return false;
      List<Set<IndexedWord>> usedNodes = new ArrayList<>();
      for (int i = 0; i <= endDepth; ++i) {
        usedNodes.add(Generics.<IndexedWord>newIdentityHashSet());
      }
      return l1 != l2 && satisfyHelper(l1, l2, sg, 0, usedNodes);
    }

    private boolean satisfyHelper(IndexedWord parent,
                                  IndexedWord l2,
                                  SemanticGraph sg,
                                  int depth,
				  List<Set<IndexedWord>> usedNodes) {
      List<Pair<GrammaticalRelation, IndexedWord>> deps = sg.childPairs(parent);
      if (depth + 1 > endDepth) {
        return false;
      }
      if (depth + 1 >= startDepth) {
        for (Pair<GrammaticalRelation, IndexedWord> dep : deps) {
          if (this.type.test(dep.first().toString()) &&
              dep.second().equals(l2)) return true;
        }
      }

      usedNodes.get(depth).add(parent);

      for (Pair<GrammaticalRelation, IndexedWord> dep : deps) {
        if ((usedNodes.size() < depth + 1 ||
             !usedNodes.get(depth + 1).contains(dep.second())) &&
            satisfyHelper(dep.second(), l2, sg, depth + 1, usedNodes))
          return true;
      }
      return false;
    }

    @Override
    Iterator<IndexedWord> searchNodeIterator(final IndexedWord node, final SemanticGraph sg) {
      return new SearchNodeIterator() {
          List<Stack<Pair<GrammaticalRelation, IndexedWord>>> searchStack;
          List<Set<IndexedWord>> seenNodes;
          Set<IndexedWord> returnedNodes;
          int currentDepth;

          @Override
          public void initialize() {
            if (node.equals(IndexedWord.NO_WORD)) {
              next = null;
              return;
            }
            searchStack = Generics.newArrayList();
            for (int i = 0; i <= endDepth; ++i) {
              searchStack.add(new Stack<>());
            }
            seenNodes = new ArrayList<>();
            for (int i = 0; i <= endDepth; ++i) {
              seenNodes.add(Generics.<IndexedWord>newIdentityHashSet());
            }
            returnedNodes = Generics.newIdentityHashSet();
            currentDepth = 1;
            List<Pair<GrammaticalRelation, IndexedWord>> children = sg.childPairs(node);
            for (int i = children.size() - 1; i >= 0; i--) {
              searchStack.get(1).push(children.get(i));
            }
            if (!searchStack.get(1).isEmpty()) {
              advance();
            }
          }

          @Override
          void advance() {
            if (node.equals(IndexedWord.NO_WORD)) {
              next = null;
              return;
            }
            Pair<GrammaticalRelation, IndexedWord> nextPair;
            while (currentDepth <= endDepth) {
              Stack<Pair<GrammaticalRelation, IndexedWord>> thisStack = searchStack.get(currentDepth);
              Set<IndexedWord> thisSeen = seenNodes.get(currentDepth);
              Stack<Pair<GrammaticalRelation, IndexedWord>> nextStack;
              Set<IndexedWord> nextSeen;
              if (currentDepth < endDepth) {
                nextStack = searchStack.get(currentDepth + 1);
                nextSeen = seenNodes.get(currentDepth + 1);
              } else {
                nextStack = null;
                nextSeen = null;
              }

              while (!thisStack.isEmpty()) {
                nextPair = thisStack.pop();
                if (thisSeen.contains(nextPair.second())) {
                  continue;
                }

                thisSeen.add(nextPair.second());
                List<Pair<GrammaticalRelation, IndexedWord>> children =
                  sg.childPairs(nextPair.second());
                for (int i = children.size() - 1; i >= 0; i--) {
                  if (nextSeen != null &&
                      !nextSeen.contains(children.get(i).second()))
                    nextStack.push(children.get(i));
                }
                if (currentDepth >= startDepth &&
                    type.test(nextPair.first().toString()) &&
                    !returnedNodes.contains(nextPair.second())) {
                  next = nextPair.second();
                  relation = nextPair.first().toString();
                  returnedNodes.add(nextPair.second());
                  return;
                }
              }
              // didn't see anything at this depth, move to the next depth
              ++currentDepth;
            }
            // oh well, fell through with no results
            next = null;
          }
        };
    }

    // automatically generated by Eclipse
    private static final long serialVersionUID = 1L;
  };


  /**
   * Factored out the common code from GRANDKID and GRANDPARENT
   * <br>
   * In general, the only differences are which ways to go on edges,
   * so that is gotten through abstract methods
   */
  static private abstract class GRANDSOMETHING extends GraphRelation {
    GRANDSOMETHING(String symbol, String reln, String name) {
      super(symbol, reln, name);
    }

    abstract List<Pair<GrammaticalRelation, IndexedWord>> getNeighborPairs(SemanticGraph sg, IndexedWord node);

    abstract Iterator<SemanticGraphEdge> neighborIterator(SemanticGraph sg, IndexedWord search);

    abstract IndexedWord followEdge(SemanticGraphEdge edge);

    @Override
    boolean satisfies(IndexedWord l1, IndexedWord l2, SemanticGraph sg) {
      return l1 != l2 && satisfyHelper(l1, l2, sg, Generics.<IndexedWord>newIdentityHashSet());
    }

    private boolean satisfyHelper(IndexedWord node, IndexedWord l2, SemanticGraph sg,
                                  Set<IndexedWord> usedNodes) {
      List<Pair<GrammaticalRelation, IndexedWord>> govs = getNeighborPairs(sg, node);
      for (Pair<GrammaticalRelation, IndexedWord> gov : govs) {
        if (this.type.test(gov.first().toString()) &&
            gov.second().equals(l2)) return true;
      }

      usedNodes.add(node);

      for (Pair<GrammaticalRelation, IndexedWord> gov : govs) {
        if (!usedNodes.contains(gov.second()) && satisfyHelper(gov.second(), l2, sg, usedNodes))
          return true;
      }
      return false;
    }

    @Override
    Iterator<IndexedWord> searchNodeIterator(final IndexedWord node, final SemanticGraph sg) {
      return new SearchNodeIterator() {
          Stack<IndexedWord> searchStack;
          Set<IndexedWord> searchedNodes;
          Set<IndexedWord> matchedNodes;

          Iterator<SemanticGraphEdge> neighborIterator;

          @Override
          public void initialize() {
            if (node.equals(IndexedWord.NO_WORD)) {
              next = null;
              return;
            }
            neighborIterator = null;
            searchedNodes = Generics.newIdentityHashSet();
            matchedNodes = Generics.newIdentityHashSet();
            searchStack = Generics.newStack();
            searchStack.push(node);
            advance();
          }

          @Override
          void advance() {
            if (node.equals(IndexedWord.NO_WORD)) {
              next = null;
              return;
            }

            while (!searchStack.isEmpty()) {
              if (neighborIterator == null || !neighborIterator.hasNext()) {
                IndexedWord search = searchStack.pop();
                neighborIterator = neighborIterator(sg, search);
              }

              while (neighborIterator.hasNext()) {
                SemanticGraphEdge edge = neighborIterator.next();
                IndexedWord otherEnd = followEdge(edge);
                if (!searchedNodes.contains(otherEnd)) {
                  searchStack.push(otherEnd);
                  searchedNodes.add(otherEnd);
                }
                if (type.test(edge.getRelation().toString()) && !matchedNodes.contains(otherEnd)) {
                  matchedNodes.add(otherEnd);
                  next = otherEnd;
                  relation = edge.getRelation().toString();
                  return;
                }
              }
            }
            // oh well, fell through with no results
            next = null;
          }
        };
    }

    // automatically generated by Eclipse
    private static final long serialVersionUID = 1L;
  };

  // GRANDPARENT graph relation: ">>" ===========================================

  static private class GRANDPARENT extends GRANDSOMETHING {
    GRANDPARENT(String reln, String name) {
      super(">>", reln, name);
    }

    @Override
    List<Pair<GrammaticalRelation, IndexedWord>> getNeighborPairs(SemanticGraph sg, IndexedWord node) {
      return sg.childPairs(node);
    }

    @Override
    Iterator<SemanticGraphEdge> neighborIterator(SemanticGraph sg, IndexedWord search) {
      return sg.outgoingEdgeIterator(search);
    }

    @Override
    IndexedWord followEdge(SemanticGraphEdge edge) {
      return edge.getTarget();
    }

    // automatically generated by Eclipse
    private static final long serialVersionUID = 1L;
  }

  // GRANDKID graph relation: "<<" ==============================================

  static private class GRANDKID extends GRANDSOMETHING {
    GRANDKID(String reln, String name) {
      super("<<", reln, name);
    }

    @Override
    List<Pair<GrammaticalRelation, IndexedWord>> getNeighborPairs(SemanticGraph sg, IndexedWord node) {
      return sg.parentPairs(node);
    }

    @Override
    Iterator<SemanticGraphEdge> neighborIterator(SemanticGraph sg, IndexedWord search) {
      return sg.incomingEdgeIterator(search);
    }

    @Override
    IndexedWord followEdge(SemanticGraphEdge edge) {
      return edge.getSource();
    }

    // automatically generated by copying some other serialVersionUID
    private static final long serialVersionUID = 1L;
  }


  static private class LIMITED_GRANDKID extends GraphRelation {
    final int startDepth, endDepth;

    LIMITED_GRANDKID(String reln, String name,
                        int startDepth, int endDepth) {
      super(startDepth + "," + endDepth + "<<", reln, name);
      this.startDepth = startDepth;
      this.endDepth = endDepth;
    }

    @Override
    boolean satisfies(IndexedWord l1, IndexedWord l2, SemanticGraph sg) {
      if (l1.equals(IndexedWord.NO_WORD) || l2.equals(IndexedWord.NO_WORD) )
        return false;
      List<Set<IndexedWord>> usedNodes = new ArrayList<>();
      for (int i = 0; i <= endDepth; ++i) {
        usedNodes.add(Generics.<IndexedWord>newIdentityHashSet());
      }
      return l1 != l2 && satisfyHelper(l1, l2, sg, 0, usedNodes);
    }

    private boolean satisfyHelper(IndexedWord child,
                                  IndexedWord l2,
                                  SemanticGraph sg,
                                  int depth,
				  List<Set<IndexedWord>> usedNodes) {
      List<Pair<GrammaticalRelation, IndexedWord>> deps = sg.parentPairs(child);
      if (depth + 1 > endDepth) {
        return false;
      }
      if (depth + 1 >= startDepth) {
        for (Pair<GrammaticalRelation, IndexedWord> dep : deps) {
          if (this.type.test(dep.first().toString()) &&
              dep.second().equals(l2)) return true;
        }
      }

      usedNodes.get(depth).add(child);

      for (Pair<GrammaticalRelation, IndexedWord> dep : deps) {
        if ((usedNodes.size() < depth + 1 ||
             !usedNodes.get(depth + 1).contains(dep.second())) &&
            satisfyHelper(dep.second(), l2, sg, depth + 1, usedNodes))
          return true;
      }
      return false;
    }

    @Override
    Iterator<IndexedWord> searchNodeIterator(final IndexedWord node, final SemanticGraph sg) {
      return new SearchNodeIterator() {
          List<Stack<Pair<GrammaticalRelation, IndexedWord>>> searchStack;
          List<Set<IndexedWord>> seenNodes;
          Set<IndexedWord> returnedNodes;
          int currentDepth;

          @Override
          public void initialize() {
            if (node.equals(IndexedWord.NO_WORD)) {
              next = null;
              return;
            }
            searchStack = Generics.newArrayList();
            for (int i = 0; i <= endDepth; ++i) {
              searchStack.add(new Stack<>());
            }
            seenNodes = new ArrayList<>();
            for (int i = 0; i <= endDepth; ++i) {
              seenNodes.add(Generics.<IndexedWord>newIdentityHashSet());
            }
            returnedNodes = Generics.newIdentityHashSet();
            currentDepth = 1;
            List<Pair<GrammaticalRelation, IndexedWord>> parents = sg.parentPairs(node);
            for (int i = parents.size() - 1; i >= 0; i--) {
              searchStack.get(1).push(parents.get(i));
            }
            if (!searchStack.get(1).isEmpty()) {
              advance();
            }
          }

          @Override
          void advance() {
            if (node.equals(IndexedWord.NO_WORD)) {
              next = null;
              return;
            }
            Pair<GrammaticalRelation, IndexedWord> nextPair;
            while (currentDepth <= endDepth) {
              Stack<Pair<GrammaticalRelation, IndexedWord>> thisStack = searchStack.get(currentDepth);
              Set<IndexedWord> thisSeen = seenNodes.get(currentDepth);
              Stack<Pair<GrammaticalRelation, IndexedWord>> nextStack;
              Set<IndexedWord> nextSeen;
              if (currentDepth < endDepth) {
                nextStack = searchStack.get(currentDepth + 1);
                nextSeen = seenNodes.get(currentDepth + 1);
              } else {
                nextStack = null;
                nextSeen = null;
              }

              while (!thisStack.isEmpty()) {
                nextPair = thisStack.pop();
                if (thisSeen.contains(nextPair.second())) {
                  continue;
                }

                thisSeen.add(nextPair.second());
                List<Pair<GrammaticalRelation, IndexedWord>> parents =
                  sg.parentPairs(nextPair.second());
                for (int i = parents.size() - 1; i >= 0; i--) {
                  if (nextSeen != null &&
                      !nextSeen.contains(parents.get(i).second()))
                    nextStack.push(parents.get(i));
                }
                if (currentDepth >= startDepth &&
                    type.test(nextPair.first().toString()) &&
                    !returnedNodes.contains(nextPair.second())) {
                  returnedNodes.add(nextPair.second());
                  next = nextPair.second();
                  relation = nextPair.first().toString();
                  return;
                }
              }
              // didn't see anything at this depth, move to the next depth
              ++currentDepth;
            }
            // oh well, fell through with no results
            next = null;
          }
        };
    }

    // automatically generated by Eclipse
    private static final long serialVersionUID = 1L;
  };

  /**
   * Iterates over nodes which are either gov or dep of the current node.
   *<br>
   * In other words, this can take the place of a disjunction over &lt; and &gt;
   */
  static private class CONNECTED extends GraphRelation {
    CONNECTED(String reln, String name, String edgeName) {
      super("<>", reln, name);
    }

    @Override
    boolean satisfies(IndexedWord l1, IndexedWord l2, SemanticGraph sg) {
      if (l1.equals(IndexedWord.NO_WORD) || l2.equals(IndexedWord.NO_WORD) )
        return false;
      List<Pair<GrammaticalRelation, IndexedWord>> govs = sg.parentPairs(l1);
      for (Pair<GrammaticalRelation, IndexedWord> gov : govs) {
        if (this.type.test(gov.first().toString()) &&
            gov.second().equals(l2)) return true;
      }
      List<Pair<GrammaticalRelation, IndexedWord>> deps = sg.childPairs(l1);
      for (Pair<GrammaticalRelation, IndexedWord> dep : deps) {
        if (this.type.test(dep.first().toString()) &&
            dep.second().equals(l2)) {
          return true;
        }
      }
      return false;
    }


    @Override
    Iterator<IndexedWord> searchNodeIterator(final IndexedWord node, final SemanticGraph sg) {
      return new SearchNodeIterator() {
          Iterator<SemanticGraphEdge> it1;
          Iterator<SemanticGraphEdge> it2;

          @Override
          public void advance() {
            if (node.equals(IndexedWord.NO_WORD)) {
              next = null;
              return;
            }
            if (it1 == null) {
              it1 = sg.outgoingEdgeIterator(node);
            }
            while (it1.hasNext()) {
              SemanticGraphEdge edge = it1.next();
              relation = edge.getRelation().toString();
              if (!type.test(relation)) {
                continue;
              }
              this.next = edge.getTarget();
              this.edge = edge;
              return;
            }
            if (it2 == null) {
              it2 = sg.incomingEdgeIterator(node);
            }
            while (it2.hasNext()) {
              SemanticGraphEdge edge = it2.next();
              relation = edge.getRelation().toString();
              if (!type.test(relation)) {
                continue;
              }
              this.next = edge.getSource();
              this.edge = edge;
              return;
            }
            this.next = null;
            this.edge = null;
          }
      };
    }

    // automatically generated by button mashing
    private static final long serialVersionUID = -413981378678L;
  }

  static private class EQUALS extends GraphRelation {
    EQUALS(String reln, String name) {
      super("==", reln, name);
    }

    @Override
    boolean satisfies(IndexedWord l1, IndexedWord l2, SemanticGraph sg) {
      if (l1 == l2) {
        return true;
      }
      return false;
    }

    @Override
    Iterator<IndexedWord> searchNodeIterator(final IndexedWord node, final SemanticGraph sg) {
      return new SearchNodeIterator() {
        boolean alreadyIterated;

        @Override
        public void advance() {
          if (node.equals(IndexedWord.NO_WORD)) {
            next = null;
            return;
          }
          if (alreadyIterated) {
            next = null;
            return;
          }
          alreadyIterated = true;
          next = node;
          return;
        }
      };
    }
  }

  static private abstract class SIBLING_RELATION extends GraphRelation {

    private static final long serialVersionUID = 1L;

    SIBLING_RELATION(String symbol, String reln, String name) {
      super(symbol, reln, name);
    }

    abstract boolean satisfiesOrder(IndexedWord l1, IndexedWord l2);

    @Override
    boolean satisfies(IndexedWord l1, IndexedWord l2, SemanticGraph sg) {
      IndexedWord parent = sg.getCommonAncestor(l1, l2);
      Set<IndexedWord> l1Parents = sg.getParents(l1);
      if (parent != null
          && l1Parents.contains(parent)
          && satisfiesOrder(l1, l2)) {
        return true;
      }
      return false;
    }

    @Override
    Iterator<IndexedWord> searchNodeIterator(final IndexedWord node, final SemanticGraph sg) {
      return new SearchNodeIterator() {
        Iterator<IndexedWord> iterator;

        @Override
        public void advance() {
          if (node.equals(IndexedWord.NO_WORD)) {
            next = null;
            return;
          }

          if (iterator == null) {
            Set<IndexedWord> parents = sg.getParents(node);
            Set<IndexedWord> neighbors = Generics.newIdentityHashSet();
            for (IndexedWord parent : parents) {
              neighbors.addAll(sg.getChildren(parent));
            }
            iterator = neighbors.iterator();
          }

          while (iterator.hasNext()) {
            IndexedWord word = iterator.next();
            if ( ! satisfiesOrder(node, word)) {
              continue;
            }
            this.next = word;
            return;
          }
          this.next = null;
        }
      };
    }

  }


  static private class RIGHT_IMMEDIATE_SIBLING extends SIBLING_RELATION {

    RIGHT_IMMEDIATE_SIBLING(String reln, String name) {
      super("$+", reln, name);
    }

    private static final long serialVersionUID = 1L;

    boolean satisfiesOrder(IndexedWord l1, IndexedWord l2) {
      return (l1.index() == (l2.index() - 1));
    }
  }

  static private class LEFT_IMMEDIATE_SIBLING extends SIBLING_RELATION {

    LEFT_IMMEDIATE_SIBLING(String reln, String name) {
      super("$-", reln, name);
    }

    private static final long serialVersionUID = 1L;

    boolean satisfiesOrder(IndexedWord l1, IndexedWord l2) {
      return (l1.index() == (l2.index() + 1));
    }
  }

  static private class RIGHT_SIBLING extends SIBLING_RELATION {

    RIGHT_SIBLING(String reln, String name) {
      super("$++", reln, name);
    }

    private static final long serialVersionUID = 1L;

    boolean satisfiesOrder(IndexedWord l1, IndexedWord l2) {
      return (l1.index() < l2.index());
    }
  }

  static private class LEFT_SIBLING extends SIBLING_RELATION {

    LEFT_SIBLING(String reln, String name) {
      super("$--", reln, name);
    }

    private static final long serialVersionUID = 1L;

    boolean satisfiesOrder(IndexedWord l1, IndexedWord l2) {
      return (l1.index() > l2.index());
    }
  }

  static private class ADJACENT_RIGHT extends GraphRelation {

    private static final long serialVersionUID = 1L;

    ADJACENT_RIGHT(String reln, String name) {
      super(".", reln, name);
    }


    @Override
    boolean satisfies(IndexedWord l1, IndexedWord l2, SemanticGraph sg) {
      if (l1.index() == (l2.index() - 1)) {
        return true;
      }
      return false;
    }

    @Override
    Iterator<IndexedWord> searchNodeIterator(final IndexedWord node, final SemanticGraph sg) {
      return new SearchNodeIterator() {
        Iterator<IndexedWord> iterator;

        @Override
        public void advance() {
          if (node.equals(IndexedWord.NO_WORD)) {
            next = null;
            return;
          }

          if (iterator == null) {
            iterator = sg.vertexSet().iterator();
          }

          while (iterator.hasNext()) {
            IndexedWord word = iterator.next();
            // note that index might not be unique if there are copy nodes
            if (node.index() != (word.index() - 1)) {
              continue;
            }
            this.next = word;
            return;
          }
          this.next = null;
        }
      };
    }

  }


  static private class ADJACENT_LEFT extends GraphRelation {

    private static final long serialVersionUID = 1L;

    ADJACENT_LEFT(String reln, String name) {
      super("-", reln, name);
    }


    @Override
    boolean satisfies(IndexedWord l1, IndexedWord l2, SemanticGraph sg) {
      if (l1.index() == (l2.index() + 1)) {
        return true;
      }
      return false;
    }

    @Override
    Iterator<IndexedWord> searchNodeIterator(final IndexedWord node, final SemanticGraph sg) {
      return new SearchNodeIterator() {
        Iterator<IndexedWord> iterator;

        @Override
        public void advance() {
          if (node.equals(IndexedWord.NO_WORD)) {
            next = null;
            return;
          }

          if (iterator == null) {
            iterator = sg.vertexSet().iterator();
          }

          while (iterator.hasNext()) {
            IndexedWord word = iterator.next();
            // note that index might not be unique if there are copy nodes
            if (node.index() != (word.index() + 1)) {
              continue;
            }
            this.next = word;
            return;
          }
          this.next = null;
        }
      };
    }

  }


  static private class RIGHT extends GraphRelation {

    private static final long serialVersionUID = 1L;

    RIGHT(String reln, String name) {
      super("..", reln, name);
    }


    @Override
    boolean satisfies(IndexedWord l1, IndexedWord l2, SemanticGraph sg) {
      if (l1.index() < l2.index()) {
        return true;
      }
      return false;
    }

    @Override
    Iterator<IndexedWord> searchNodeIterator(final IndexedWord node, final SemanticGraph sg) {
      return new SearchNodeIterator() {
        Iterator<IndexedWord> iterator;

        @Override
        public void advance() {
          if (node.equals(IndexedWord.NO_WORD)) {
            next = null;
            return;
          }

          if (iterator == null) {
            iterator = sg.vertexSet().iterator();
          }

          while (iterator.hasNext()) {
            IndexedWord word = iterator.next();
            if (node.index() >= word.index()) {
              continue;
            }
            this.next = word;
            return;
          }
          this.next = null;
        }
      };
    }

  }


  static private class LEFT extends GraphRelation {

    private static final long serialVersionUID = 1L;

    LEFT(String reln, String name) {
      super("--", reln, name);
    }


    @Override
    boolean satisfies(IndexedWord l1, IndexedWord l2, SemanticGraph sg) {
      if (l1.index() > l2.index()) {
        return true;
      }
      return false;
    }

    @Override
    Iterator<IndexedWord> searchNodeIterator(final IndexedWord node, final SemanticGraph sg) {
      return new SearchNodeIterator() {
        Iterator<IndexedWord> iterator;

        @Override
        public void advance() {
          if (node.equals(IndexedWord.NO_WORD)) {
            next = null;
            return;
          }

          if (iterator == null) {
            iterator = sg.vertexSet().iterator();
          }

          while (iterator.hasNext()) {
            IndexedWord word = iterator.next();
            if (node.index() <= word.index()) {
              continue;
            }
            this.next = word;
            return;
          }
          this.next = null;
        }
      };
    }

  }


  // ============================================================================

  public static boolean isKnownRelation(String reln) {
    return (reln.equals(">") || reln.equals("<") ||
            reln.equals(">>") || reln.equals("<<") ||
            reln.equals("<>") ||
            reln.equals("@") || reln.equals("==") ||
            reln.equals("$+") || reln.equals("$++") ||
            reln.equals("$-") || reln.equals("$--") ||
            reln.equals(".") || reln.equals("..") ||
            reln.equals("-") || reln.equals("--") ||
            reln.equals(">++") || reln.equals(">--") ||
            reln.equals("<++") || reln.equals("<--"));
  }

  public static GraphRelation getRelation(String reln,
                                          String type,
                                          String name,
                                          String edgeName) throws ParseException {
    if (reln == null && type == null)
      return null;
    if (!isKnownRelation(reln)) {
      throw new ParseException("Unknown relation " + reln);
    }
    switch (reln) {
      case ">":
        return new GOVERNOR(type, name, edgeName);
      case ">++":
        return new GOVERNOR_RIGHT(type, name, edgeName);
      case ">--":
        return new GOVERNOR_LEFT(type, name, edgeName);
      case "<":
        return new DEPENDENT(type, name, edgeName);
      case "<++":
        return new DEPENDENT_RIGHT(type, name, edgeName);
      case "<--":
        return new DEPENDENT_LEFT(type, name, edgeName);
      case "<>":
        return new CONNECTED(type, name, edgeName);
    }
    if (edgeName != null) {
      throw new ParseException("Relation " + reln + " does not allow for named edges");
    }
    switch (reln) {
      case ">>":
        return new GRANDPARENT(type, name);
      case "<<":
        return new GRANDKID(type, name);
      case "==":
        return new EQUALS(type, name);
      case "$+":
        return new RIGHT_IMMEDIATE_SIBLING(type, name);
      case "$-":
        return new LEFT_IMMEDIATE_SIBLING(type, name);
      case "$++":
        return new RIGHT_SIBLING(type, name);
      case "$--":
        return new LEFT_SIBLING(type, name);
      case ".":
        return new ADJACENT_RIGHT(type, name);
      case "..":
        return new RIGHT(type, name);
      case "-":
        return new ADJACENT_LEFT(type, name);
      case "--":
        return new LEFT(type, name);
      case "@":
        return new ALIGNMENT();
      default:
        //error
        throw new ParseException("Relation " + reln +
            " not handled by getRelation");
    }
  }

  public static GraphRelation getRelation(String reln,
                                          String type,
                                          int num,
                                          String name,
                                          String edgeName) throws ParseException {
    if (edgeName != null) {
      throw new ParseException("Relation " + reln + " does not allow for named edges");
    }
    if (reln == null && type == null)
      return null;
    if (reln.equals(">>"))
      return new LIMITED_GRANDPARENT(type, name, num, num);
    else if (reln.equals("<<"))
      return new LIMITED_GRANDKID(type, name, num, num);
    else if (isKnownRelation(reln))
      throw new ParseException("Relation " + reln +
                               " does not use numeric arguments");
    else //error
      throw new ParseException("Unrecognized compound relation " + reln + " "
                               + type);
  }

  public static GraphRelation getRelation(String reln,
                                          String type,
                                          int num, int num2,
                                          String name,
                                          String edgeName) throws ParseException {
    if (edgeName != null) {
      throw new ParseException("Relation " + reln + " does not allow for named edges");
    }
    if (reln == null && type == null)
      return null;
    if (reln.equals(">>"))
      return new LIMITED_GRANDPARENT(type, name, num, num2);
    else if (reln.equals("<<"))
      return new LIMITED_GRANDKID(type, name, num, num2);
    else if (isKnownRelation(reln))
      throw new ParseException("Relation " + reln +
                               " does not use numeric arguments");
    else //error
      throw new ParseException("Unrecognized compound relation " + reln + " "
                               + type);
  }

  @Override
  public int hashCode() {
    return symbol.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof GraphRelation)) {
      return false;
    }

    final GraphRelation relation = (GraphRelation) o;

    if (!symbol.equals(relation.symbol) ||
        !type.equals(relation.type)) {
      return false;
    }

    return true;
  }

  /**
   * This abstract Iterator implements a NULL iterator, but by subclassing and
   * overriding advance and/or initialize, it is an efficient implementation.
   */
  static abstract class SearchNodeIterator implements Iterator<IndexedWord> {
    public SearchNodeIterator() {
      initialize();
    }

    /**
     * This is the next node to be returned by the iterator, or null if there
     * are no more items.
     */
    IndexedWord next = null;

    /**
     * Current relation string for next;
     */
    String relation = null;

    /**
     * If the relation is a type which a single edge, such as dependent or governor,
     * this variable can store that edge.  Other relations such as grandparent
     * do not store an edge.
     */
    SemanticGraphEdge edge = null;
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

    public boolean hasNext() {
      return next != null;
    }

    public IndexedWord next() {
      if (next == null) {
        return null;
      }
      IndexedWord ret = next;
      advance();
      return ret;
    }

    String getReln() {return relation;}

    /**
     * Return the edge cached by the match - might be null if it was not an appropriate relation type,
     * even if there was a labeled relation
     */
    SemanticGraphEdge getEdge() {return edge;}

    public void remove() {
      throw new UnsupportedOperationException("SearchNodeIterator does not support remove().");
    }
  }

  // Automatically generated by Eclipse
  private static final long serialVersionUID = -9128973950911993056L;
}
