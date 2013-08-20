package edu.stanford.nlp.trees;

import static java.lang.System.err;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.WordTag;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.util.ArrayUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

/**
 * Finds WordNet collocations in parse trees.  It can restructure
 * collocations as single words, where the original words are joined by
 * underscores.  You can test performance by using the "collocations" option
 * to the TreePrint class.
 *
 * @author Chris Cox
 * @author Eric Yeh
 */

public class CollocationFinder {

  private static boolean DEBUG = false;
  private final Tree qTree;
  private final HeadFinder hf;
  private final List<Collocation> collocationCollector;
  private final WordNetConnection wnConnect;

  /**
   * Construct a new {@code CollocationFinder} over the {@code Tree} t.
   * The default {@link HeadFinder} is a {@link CollinsHeadFinder}.
   * @param t parse tree
   * @param w wordnet connection
   */
  public CollocationFinder(Tree t, WordNetConnection w) {
    this(t, w, new CollinsHeadFinder());
  }

  /**
   * Construct a new {@code CollocationFinder} over the {@code Tree} t.
   * @param t parse tree
   * @param w wordnet connection
   * @param hf {@link HeadFinder} to use
   */
  public CollocationFinder(Tree t, WordNetConnection w, HeadFinder hf) {
    this(t, w, hf, false);
  }

  /**
   * Construct a new {@code CollocationFinder} over the {@code Tree} t.
   * @param t parse tree
   * @param w wordnet connection
   * @param hf {@link HeadFinder} to use
   * @param threadSafe whether to include synchronization, etc.
   */
  public CollocationFinder(Tree t, WordNetConnection w, HeadFinder hf, boolean threadSafe) {
    CoordinationTransformer transformer = new CoordinationTransformer();
    this.wnConnect = w;
    this.qTree = transformer.transformTree(t);
    this.collocationCollector = Generics.newArrayList();
    this.hf = hf;
    this.getCollocationsList(threadSafe);
    if (DEBUG) {
      System.err.println("Collected collocations: " + collocationCollector);
    }
  }

  /**
   * Returns the "collocations included" parse tree.
   *
   * @return the mangled tree which applies collocations found in this object.
   */
  public Tree getMangledTree() {
    return getMangledTree(qTree);
  }

  private Tree getMangledTree(Tree t) {
    Collocation matchingColl = null;
    for (Tree child : t.children()) {
      child = getMangledTree(child);
    }
    //boolean additionalCollocationsExist = false;
    for (Collocation c : collocationCollector) {
      // if there are multiple collocations with the same parent node,
      // this will take the longer one
      if (t.equals(c.parentNode)) {
        if (matchingColl == null ||
            (c.span.first() <= matchingColl.span.first() &&
                c.span.second() >= matchingColl.span.second())) {
          matchingColl = c;
          if (DEBUG) {
            err.println("Found matching collocation for tree:");
            t.pennPrint();
            err.print("  head label: " + c.headLabel);
            err.println("; collocation string: " + c.collocationString);
            err.println("  Constituents: "+ c.indicesOfConstituentChildren);
          }
        }
      }
    }

    if (matchingColl == null) {
      return t;
    } else {
      if (DEBUG) {
        err.println("Collapsing " + matchingColl);
      }
      Tree[] allChildren = t.children();
      // get the earliest child in the collocation and store it as first child.
      // delete the rest.
      StringBuffer mutatedString = new StringBuffer(160);
      for (int i : matchingColl.indicesOfConstituentChildren) {
        String strToAppend = mergeLeavesIntoCollocatedString(allChildren[i]);
        mutatedString.append(strToAppend);
        mutatedString.append("_");
      }
      mutatedString = mutatedString.deleteCharAt(mutatedString.length() - 1);

      // Starting with the latest constituent, delete all the "pruned" children
      if (DEBUG) { err.println("allChildren is: " + Arrays.toString(allChildren)); }
      for (int index = matchingColl.indicesOfConstituentChildren.size() - 1; index > 0; index--) {
        int thisConstituent = matchingColl.indicesOfConstituentChildren.get(index);
        allChildren = (Tree[]) ArrayUtils.removeAt(allChildren, thisConstituent);
        if (DEBUG) { err.println(" deleted " + thisConstituent + "; allChildren is: " + Arrays.toString(allChildren)); }
      }
      //name for the leaf string of our new collocation
      String newNodeString = mutatedString.toString();

      int firstChildIndex = matchingColl.indicesOfConstituentChildren.get(0);
      //now we mutate the earliest constituent
      Tree newCollocationChild = allChildren[firstChildIndex];
      if (DEBUG) err.println("Manipulating: " + newCollocationChild);
      newCollocationChild.setValue(matchingColl.headLabel.value());
      Tree newCollocationLeaf = newCollocationChild.treeFactory().newLeaf(newNodeString);
      newCollocationChild.setChildren(Collections.singletonList(newCollocationLeaf));
      if (DEBUG) err.println("  changed to: " + newCollocationChild);

      allChildren[firstChildIndex] = newCollocationChild;
      t.setChildren(allChildren);

      if (DEBUG) {
        err.println("Restructured tree is:");
        t.pennPrint();
        err.println();
      }
      return t;
    }
  }

/**
 * Traverses the parse tree to find WordNet collocations.
 */
  private void getCollocationsList(boolean threadSafe) {
    getCollocationsList(qTree, threadSafe);
  }

  /**
   * Prints the collocations found in this <code>Tree</code> as strings.
   * Each is followed by its boundary constituent indices in the original tree.
   * <br>Example: <code> throw_up (2,3) </code>
   *   <br>       <code> came_up_with (7,9) </code>
   */
  public void PrintCollocationStrings(PrintWriter pw){
    //ArrayList<String> strs = new ArrayList<String>();
    for(Collocation c: collocationCollector){
      String cs = c.collocationString;
      pw.println(cs+" ("+(c.span.first()+1)+","+(c.span.second()+1)+")");
    }
  }

  /**
   * This method does the work of traversing the tree and writing collocations
   * to the CollocationCollector (an internal data structure).
   *
   * @param t Tree to get collocations from.
   */
  private void getCollocationsList(Tree t, boolean threadSafe) {
    int leftMostLeaf = Trees.leftEdge(t,qTree);
    if (t.isPreTerminal()) return;
    List<Tree> children = t.getChildrenAsList();
    if (children.isEmpty()) return;
    //TODO: fix determineHead
    // - in phrases like "World Trade Organization 's" the head of the parent NP is "POS".
    // - this is problematic for the collocationFinder which assigns this head
    // as the POS for the collocation "World_Trade_Organization"!
    Label headLabel= hf.determineHead(t).label();
    StringBuffer testString = null;
    Integer leftSistersBuffer=0;//measures the length of sisters in words when reading
    for (int i = 0; i < children.size();i++){
      ArrayList<Integer> childConstituents = new ArrayList<Integer>();
      childConstituents.add(i);
      Tree subtree = children.get(i);
      Integer currWindowLength=0; //measures the length in words of the current collocation.
      getCollocationsList(subtree, threadSafe); //recursive call to get colls in subtrees.
      testString = new StringBuffer(160);
      testString.append(treeAsStemmedCollocation(subtree, threadSafe));
      testString.append("_");
      Integer thisSubtreeLength = subtree.yield().size();
      currWindowLength+=thisSubtreeLength;
      StringBuffer testStringNonStemmed = new StringBuffer(160);
      testStringNonStemmed.append(treeAsNonStemmedCollocation(subtree));
      testStringNonStemmed.append("_");

      //for each subtree i, we iteratively append word yields of succeeding sister
      //subtrees j and check their wordnet entries.  if they exist we write them to
      //the global collocationCollector pair by the indices of the leftmost and
      //rightmost words in the collocation.

      for (int j = i+1; j < children.size(); j++) {
        Tree sisterNode = children.get(j);
        childConstituents.add(j);
        testString.append(treeAsStemmedCollocation(sisterNode, threadSafe));
        testStringNonStemmed.append(treeAsNonStemmedCollocation(sisterNode));
        currWindowLength+=sisterNode.yield().size();
        if (DEBUG) {
       //   err.println("Testing string w/ reported indices:" + testString.toString()
         //             + " (" +(leftMostLeaf+leftSistersBuffer)+","+(leftMostLeaf+leftSistersBuffer+currWindowLength-1)+")");
        }
        //ignore collocations beginning with "the" or "a"
        if (StringUtils.lookingAt(testString.toString(), "(?:[Tt]he|THE|[Aa][Nn]?)[ _]")) {
          if (false) {
            err.println("CollocationFinder: Not collapsing the/a word: " +
                testString);
          }
        } else if (wordNetContains(testString.toString())) {
          Pair <Integer, Integer> c = new Pair<Integer,Integer>(leftMostLeaf+leftSistersBuffer,leftMostLeaf+leftSistersBuffer+currWindowLength-1);

          Collocation col = new Collocation(c,t,(ArrayList<Integer>)childConstituents.clone(),testString.toString(),headLabel);
          collocationCollector.add(col);
          if (DEBUG) {
            err.println("Found collocation in wordnet: "+ testString.toString());
            err.println("  Span of collocation is: " + c +
                "; childConstituents is: " + c);
          }
        }
        testString.append("_");
        if (StringUtils.lookingAt(testStringNonStemmed.toString(), "(?:[Tt]he|THE|[Aa][Nn]?)[ _]")) {
          if (false) {
            err.println("CollocationFinder: Not collapsing the/a word: " +
                testStringNonStemmed);
          }
        } else if (wordNetContains(testStringNonStemmed.toString())) {
          Pair <Integer, Integer> c = new Pair<Integer,Integer>(leftMostLeaf+leftSistersBuffer,leftMostLeaf+leftSistersBuffer+currWindowLength-1);

          Collocation col = new Collocation(c,t,(ArrayList<Integer>)childConstituents.clone(),testStringNonStemmed.toString(),headLabel);
          collocationCollector.add(col);
          if (DEBUG) {
            err.println("Found collocation in wordnet: "+ testStringNonStemmed.toString());
            err.println("  Span of collocation is: " + c +
                "; childConstituents is: " + c);
          }
        }
        testStringNonStemmed.append("_");
      }
      leftSistersBuffer+=thisSubtreeLength;
    }
  }

  private static String treeAsStemmedCollocation(Tree t, boolean threadSafe) {
    List<WordTag> list= getStemmedWordTagsFromTree(t, threadSafe);
    // err.println(list.size());
    StringBuffer s = new StringBuffer(160);
    WordTag firstWord = list.remove(0);
    s.append(firstWord.word());
    for(WordTag wt : list) {
      s.append("_");
      s.append(wt.word());
    }
    //err.println("Expressing this as:"+s.toString());
    return s.toString();
  }

  private static String treeAsNonStemmedCollocation(Tree t) {
    List<WordTag> list= getNonStemmedWordTagsFromTree(t);

    StringBuffer s = new StringBuffer(160);
    WordTag firstWord = list.remove(0);
    s.append(firstWord.word());
    for(WordTag wt : list) {
      s.append("_");
      s.append(wt.word());
    }
    return s.toString();
  }

  private static String mergeLeavesIntoCollocatedString(Tree t) {
    StringBuilder sb = new StringBuilder(160);
    ArrayList<TaggedWord> sent = t.taggedYield();
    for (TaggedWord aSent : sent) {
      sb.append(aSent.word()).append("_");
    }
    return sb.substring(0,sb.length() -1);
  }

  private static String mergeLeavesIntoCollocatedString(Tree[] trees) {
    StringBuilder sb = new StringBuilder(160);
    for (Tree t: trees) {
      ArrayList<TaggedWord> sent = t.taggedYield();
      for (TaggedWord aSent : sent) {
        sb.append(aSent.word()).append("_");
      }
    }
    return sb.substring(0,sb.length() -1);
  }

  /**
   *
   * @param t a tree
   * @return the WordTags corresponding to the leaves of the tree,
   * stemmed according to their POS tags in the tree.
   */
  private static List<WordTag> getStemmedWordTagsFromTree(Tree t, boolean threadSafe) {
    List<WordTag> stemmedWordTags = Generics.newArrayList();
    ArrayList<TaggedWord> s = t.taggedYield();
    for (TaggedWord w : s) {
      WordTag wt = threadSafe ? Morphology.stemStaticSynchronized(w.word(), w.tag())
              : Morphology.stemStatic(w.word(), w.tag());
      stemmedWordTags.add(wt);
    }
    return stemmedWordTags;
  }

  private static List<WordTag> getNonStemmedWordTagsFromTree(Tree t) {
    List<WordTag> wordTags = Generics.newArrayList();
    ArrayList<TaggedWord> s = t.taggedYield();
    for (TaggedWord w : s) {
      WordTag wt = new WordTag(w.word(), w.tag());
      wordTags.add(wt);
    }
    return wordTags;
  }

  // Convert arg from StringBuffer to String - EY 02/02/07
  /**
   * Checks to see if WordNet contains the given word in its lexicon.
   * @param s Token
   * @return If the given token is in WordNet.
   */
  private boolean wordNetContains(String s) {
    return wnConnect.wordNetContains(s);
  }


  /**
   * Holds information for one collocation.
   */
  private static class Collocation {

    Pair<Integer,Integer> span;
    Tree parentNode;
    Label headLabel;
    List<Integer> indicesOfConstituentChildren;
    String collocationString;

    private Collocation(Pair<Integer,Integer> span,
                        Tree parentNode,
                        ArrayList<Integer> indicesOfConstituentChildren,
                        String collocationString,
                        Label headLabel) {
      this.span=span;
      this.parentNode = parentNode;
      this.collocationString=collocationString;
      this.indicesOfConstituentChildren=indicesOfConstituentChildren;
      this.headLabel=headLabel;
    }

    @Override
    public String toString() {
      return collocationString + indicesOfConstituentChildren + "/" +
             headLabel;
    }

  } // end static class Collocation

} // end class CollocationFinder
