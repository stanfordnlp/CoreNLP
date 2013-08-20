package edu.stanford.nlp.util;

import java.io.*;
import java.util.*;


/** An implementation of a generalized suffix tree using the Ukkonen algorithm
 *  as described in D. Gusfield, <i>Algorithms on Strings Trees, and Sequences:
 *  Computer Science and Computational Biology</i>, Cambridge University Press,
 *  1997.
 *
 *  Parameterized by type &lt;E&gt;, each suffix tree is a tree where each
 *  element is a list of Es.
 *
 *  The algorithm requires that each new string be terminated by a unique termination
 *  symbol.  So the constructor requires you to specify an element of type E
 *  that will not appear anywhere else in the lists of symbols that are added
 *  to the tree.  The public static final fields RESERVED_CHAR and RESERVED_STRING
 *  can be used for this purpose if a String or a Character is an E.
 *
 *  TODO: This should be extended to count copies of sequence in tree.
 *
 *  @author Jeff Michels
 *  @param <E> Each element of the suffix tree is a list of E
 */
public class SuffixTree<E> {

  public static final Character RESERVED_CHAR = '\u0015'; // ASCII NAK ctrl char
  public static final String RESERVED_STRING = "\u0015";
  protected static final int LEAF_END = -1;

  protected ArrayList<E> sequences;
  protected E termination_token;
  protected SuffixNode<E> root;
  protected int count;

  /** Possible ways to finish a walk down the tree: <br>
   *  END_LEAF: walk ended at a leaf <br>
   *  EXTEND_INTERNAL: walk ended at an internal node but we're still looking for
   *  at least one symbol, so a new child of that node must be added
   *  (if we are building the tree) <br>
   *  SPLIT_EDGE: same as EXTEND_INTERNAL except that not all of the symbols at
   *  the internal node were matched, so that node must be split into 3 <br>
   *  END_EDGE: walk ends in the middle of an edge with no more symbols
   *  to be matched <br>
   *  END_INTERNAL: walk ends at the end of an internal node with no more
   *  symbols to be matched
   */
  protected enum End {END_LEAF, EXTEND_INTERNAL, SPLIT_EDGE, END_EDGE, END_INTERNAL}

  public static class PathEnd<EE> {
    SuffixNode<EE> node;
    End end;
    public PathEnd(SuffixNode<EE> n, End e) {
      node = n;
      end = e;
    }
  } // end static class PathEnd

  /** Create an empty suffix tree.
   *
   *  @param term The reserved termination token which is outside the alphabet
   *      of normal tokens
   */
  public SuffixTree(E term) {
    termination_token = term;
    root = new SuffixNode<E>();
    count = 0;
    sequences = new ArrayList<E>();
  }

  /** Add a sequence to the suffix tree.
   *
   * @param sequence The sequence
   * @param terminate If true, and the last element of the sequence is not
   *     the termination token, then a termination token is added at the end
   *     of the sequence
   */
  public void addSequence(List<E> sequence, boolean terminate) {
    SuffixNode<E> oldNode = null, currentNode;
    boolean canJump = false;

    //Puts i at the end of the previous sequences
    int i = sequences.size();
    int j = i;

    sequences.addAll(sequence);
    if (terminate && !sequences.get(sequences.size()-1).equals(termination_token)) {
      sequences.add(termination_token);
    }

    currentNode = root;
    //phase i
    for (; i<sequences.size();i++) {
      //System.out.println("phase " + i);

      count++;
      //extension j;
      for ( ; j<=i; j++) {
        //System.out.println("extension " + j);

        //find first node at or above current node that is root or has a suffixLink
        while ((currentNode != root) && (currentNode.suffixLink == null) && canJump) {
          currentNode = currentNode.parent;
        }

        PathEnd<E> path_end;
        if (currentNode == root) {
          path_end = walkTo(root, sequences, j, i+1);
        } else {
          if (canJump) {
            currentNode = currentNode.suffixLink;
          }
          path_end = walkTo(currentNode, sequences, j + getPathLength(currentNode), i+1);
        }
        currentNode = path_end.node;
        SuffixNode<E> newNode = null;

        switch (path_end.end) {
          case END_LEAF: //1
            addPositionToLeaf(j, currentNode);
          case END_EDGE: //4
            // fall through
          case END_INTERNAL: //5
            currentNode = currentNode.parent;
            break;
          case EXTEND_INTERNAL: //2
            doExtendInternal(currentNode,i,j);
            break;
          case SPLIT_EDGE: //3
            newNode=doSplitEdge(currentNode,i,j);
            currentNode=newNode;
            break;
        }

        if (oldNode != null) {
          if (currentNode.isTerminal()) {
            currentNode = currentNode.parent;
          }
          oldNode.suffixLink=currentNode;
        }
        oldNode=newNode;
        // newNode=null;

        if (path_end.end == End.END_LEAF || path_end.end == End.END_EDGE || path_end.end == End.END_INTERNAL) {
          oldNode=null;
          canJump=false;
          break;
        } else {
          canJump=true;
        }
      } //end extension j
    } //end phase i
    setLeafEnds();
  }

  //indicies inclusive start, exclusive end
  protected static <E> boolean arraySubListsEqual(ArrayList<E> a1, int s1, int e1, ArrayList<E> a2, int s2, int e2){
    if ((e2 - s2) != (e1 - s1)) return false;
    for (int i=0; i<e2-s2; i++) {
      if (!a1.get(s1+i).equals(a2.get(s2+i))) {
        return false;
      }
    }
    return true;
  }

  protected static <E> String arraySubListToString(ArrayList<E> a, int start, int end) {
    StringBuilder s = new StringBuilder();
    for (int i=start; i<end; i++) {
      s.append(a.get(i).toString()).append(" ");
    }
    return s.toString();
  }

  public static class Label<EE> {

    ArrayList<EE> source;
    int start;
    int end;

    public Label(ArrayList<EE> source, int s, int e) {
      this.source = source;
      start = s;
      end = e;
    }

    public int length() {
      return end - start;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean equals(Object o) {
      if (o instanceof SuffixTree.Label) {
        return arraySubListsEqual(source, start, end, ((Label<EE>)o).source,
                ((Label)o).start, ((Label)o).end);
      }
      if (o instanceof ArrayList) {
        ArrayList<EE> item = (ArrayList<EE>) o;
        return arraySubListsEqual(source, start, end, item, 0, item.size());
      }
      return false;
    }

    @Override
    public int hashCode() {
      int i = 17;
      for (int j = start; j < end; j++) {
        i ^= source.get(j).hashCode();
      }
      return i;
    }

    public boolean equals(ArrayList<EE> a, int s, int e) {
      return arraySubListsEqual(source, start, end, a, s, e);
    }

    public boolean beginsWith(ArrayList<EE> a, int s, int e) {
      return arraySubListsEqual(source, start, start + e - s, a, s, e);
    }

    @Override
    public String toString() {
      return arraySubListToString(source, start, end);
    }

  } // end static class Label


  /** Find the place in the tree where a specific sequence ends.
   *
   * @param starting the root of the subtree we're startingform.
   * @param lookingFor the sequence of tokens that we're looking for
   * @param from the starting index (inclusive) of the target string in lookingFor
   * @param to the ending index (exclusive) of the target string in lookingFor.
   * @return a <code>PathEnd</code> that the walk stopped at.
   */
  public PathEnd<E> walkTo(SuffixNode<E> starting, ArrayList<E> lookingFor, int from, int to) {
    SuffixNode<E> currentNode;
    SuffixNode<E> arrivedAt;
    End end = null;

    currentNode = starting;
    arrivedAt = starting;
    while (from < to){
      arrivedAt = currentNode.children.get(lookingFor.get(from));
      if (arrivedAt == null){ //no matching child
        arrivedAt = currentNode;
        end = End.EXTEND_INTERNAL;
        break;
      }

      Label<E> edgeLabel = getEdgeLabel(arrivedAt);
      if (edgeLabel.length() >= to-from){ //label at least as long as what we're looking for
        if (edgeLabel.equals(lookingFor, from, to)) {
          // ends at this node exactly
          if (arrivedAt.isTerminal()) {
            end = End.END_LEAF;
          } else {
            end = End.END_INTERNAL;
          }
        } else if (edgeLabel.beginsWith(lookingFor, from, to)) {
          end = End.END_EDGE;
        } else {
          end = End.SPLIT_EDGE;
        }
        break;
      } else if (edgeLabel.equals(lookingFor, from, from+edgeLabel.length())) { //label too short but matches what we're looking for
        from += edgeLabel.length();
        currentNode = arrivedAt;
      } else { //label doesn't match what we're looking for
        end = End.SPLIT_EDGE;
        break;
      }
    }
    return new PathEnd<E>(arrivedAt, end);
  }


  protected int getEdgeLength(SuffixNode<E> child){
    int parentLength, childLength;
    SuffixNode<E> parent;
    if (child == root) {
      return 0;
    }

    parent = child.parent;
    parentLength = getPathLength(parent);
    childLength = getPathLength(child);

    return childLength - parentLength;
  }

  protected Label<E> getEdgeLabel(SuffixNode<E> child) {
    return new Label<E>(sequences, child.labelStart +
            (getPathLength(child) - getEdgeLength(child)),
            getPathEnd(child));
  }

  protected int getPathLength(SuffixNode<E> node){
    return getPathEnd(node) - node.labelStart;
  }

  protected int getPathEnd(SuffixNode<E> node){
    return (node.labelEnd == LEAF_END) ? count : node.labelEnd;
  }

  //move to setLeafEnds
  protected ArrayList<SuffixNode<E>> getAllNodes(SuffixNode<E> root, ArrayList<SuffixNode<E>> list, boolean leavesOnly) {
    if (list==null)
      list = new ArrayList<SuffixNode<E>>();
    if (!leavesOnly || root.isTerminal())
      list.add(root);
    if (!root.isTerminal()){
      for (SuffixNode<E> eSuffixNode : root.children.values()) {
        list = getAllNodes(eSuffixNode, list, leavesOnly);
      }
    }
    return list;
  }

  public void printEdges() {
    printEdges(root, "");
  }
  public void printEdges(SuffixNode<E> start, String pre) {
    if (start == root) {
      System.out.println(pre + "root");
    } else {
      System.out.println(pre + getEdgeLabel(start));
    }
    if (!start.isTerminal()) {
      String newPre = pre + "  ";
      for (SuffixNode<E> sn : start.children.values()) {
        printEdges(sn, newPre);
      }
    }
  }

  public SuffixNode<E> getRoot() {
    return root;
  }


  private static <EEE> void addPositionToLeaf(int pos, SuffixNode<EEE> leaf) {
    if (leaf.additionalLabels == null) {
      leaf.additionalLabels = new int[]{pos};
    } else {
      int[] moreLabels = new int[leaf.additionalLabels.length + 1];
      System.arraycopy(leaf.additionalLabels, 0, moreLabels, 0, leaf.additionalLabels.length);
      moreLabels[moreLabels.length-1] = pos;
      leaf.additionalLabels = moreLabels;
    }
  }

  private void doExtendInternal(SuffixNode<E> parent, int splittingPos, int suffixStart) {
    SuffixNode<E> leaf = new SuffixNode<E>(parent, suffixStart);
    parent.children.put(sequences.get(splittingPos), leaf);
  }

  private SuffixNode<E> doSplitEdge(SuffixNode<E> child, int splittingPos, int suffixStart) {
    SuffixNode<E> parent = child.parent;
    SuffixNode<E> middle = new SuffixNode<E>(parent, suffixStart, splittingPos);
    E x = sequences.get(child.labelStart + getPathLength(child) - getEdgeLength(child));
    E y = sequences.get(child.labelStart + getPathLength(child) -
            getEdgeLength(child) + getEdgeLength(middle));

    parent.children.remove(x);
    parent.children.put(x,middle);

    middle.children.put(y,child);
    child.parent=middle;
    doExtendInternal(middle,splittingPos,suffixStart);
    return middle;
  }

  private void setLeafEnds(){
    ArrayList<SuffixNode<E>> leaves = getAllNodes(root, null, true);
    for (SuffixNode<E> leaf : leaves) {
      if (leaf.labelEnd == LEAF_END) {
        leaf.labelEnd = count;
      }
    }
  }

  public static class SuffixNode<EE> {
    static final int A_LEAF=-1;
    SuffixNode<EE> parent;
    SuffixNode<EE> suffixLink;
    int labelStart, labelEnd;
    HashMap<EE, SuffixNode<EE>> children;
    int[] additionalLabels;


    //new root node
    public SuffixNode() {
      parent=null;
      suffixLink=null;
      labelStart=0;
      labelEnd=0;
      children=new HashMap<EE, SuffixNode<EE>>();
      additionalLabels=null;
    }

    /** new leaf node
     * @param parent the parent node
     * @param start the starting index
     */
    public SuffixNode(SuffixNode<EE> parent, int start) {
      this();
      this.parent = parent;
      labelStart = start;
      labelEnd = A_LEAF;
      children = null;
    }

    /** new internal node
     * @param parent The node parent
     * @param start starting index of the path label
     * @param end ending index of the path label
     */
    public SuffixNode(SuffixNode<EE> parent, int start, int end){
      this();
      this.parent = parent;
      this.labelStart = start;
      this.labelEnd = end;
    }

    public boolean isTerminal() {
      return children == null;
    }
    public boolean hasChild(EE x) {
      return getChild(x) != null;
    }
    public SuffixNode<EE> getChild(EE x) {
      return (children == null) ? null : children.get(x);
    }
    public SuffixNode<EE> getParent() {
      return parent;
    }

  } // end class SuffixNode


  /** Checks whether a substring exists in the suffix tree.
   *
   *  @param str The list of tokens
   *  @return Whether the list exists in the tree.
   */
  public boolean subStringExists(ArrayList<E> str) {
    PathEnd<E> pe = walkTo(root, str, 0, str.size());
    switch (pe.end) {
      case END_LEAF:
      case END_EDGE:
      case END_INTERNAL:
        return true;
    }
    return false;
  }


  /** Reads in one or more files of String tokens.
   *  Each line of a file is a path.
   *  Each whitespace separated thing on one line is an element of a path.
   *  It puts them into a Suffix tree, and then prints out the tree.
   *
   *  @param args One or more files with lines of spaces separated Strings
   *  @throws IOException If IO problems
   */
  public static void main(String[] args) throws IOException {
    SuffixTree<String> ust = new SuffixTree<String>("$");
    for (String arg : args) {
      BufferedReader in = new BufferedReader(new FileReader(arg));
      while (in.ready()) {
        String line = in.readLine();
        ArrayList<String> l = new ArrayList<String>(Arrays.asList(line.split("\\s+")));
        ust.addSequence(l, true);
      }
    }
    ust.printEdges();
  }

} // end class SuffixTree
