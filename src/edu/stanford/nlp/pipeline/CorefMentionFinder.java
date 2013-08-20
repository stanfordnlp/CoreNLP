package edu.stanford.nlp.pipeline;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.ModCollinsHeadFinder;
import edu.stanford.nlp.trees.Tree;

/**
 * Locates phrases (Trees) that are valid mentions for coreference resolution
 */
public class CorefMentionFinder {
  public static class CorefMentionFinderException extends RuntimeException {
    private static final long serialVersionUID = -4043332236723982286L;
    public CorefMentionFinderException(String msg) { super(msg); }
  }
  
  private static final String NP = "NP";
  private static final String PRP = "PRP";
  private static final String [] INVALID_NES = { "DATE", "NUMBER", "PERCENT" };
  private static final Pattern [] INVALID_PATTERNS = {
    Pattern.compile("percent|%", Pattern.CASE_INSENSITIVE),
    Pattern.compile("\\d+")
  };
  
  /** Maps from tree leaves to positions in the words list */
  HashMap<Tree, Integer> leavesToPositions;
  
  /** Surface representation of this sentence as a list of words */
  List<? extends CoreLabel> words;
  
  /** The full syntactic tree for this sentence */
  Tree top;
  
  /** Locates the head words in Trees */
  HeadFinder headFinder;
  
  /** Keeps track of seen head words; needed to avoid duplicated mentions */
  HashSet<Integer> seenHeads;
  
  public CorefMentionFinder(Tree tree, List<? extends CoreLabel> words) {
    this.words = words;
    this.top = tree;
    if(tree == null || words == null)
      throw new CorefMentionFinderException("CorefMentionFinder cannot be created with null parameters!");
    mapLeavesToPositions(tree, words);
    this.headFinder = new ModCollinsHeadFinder();
    this.seenHeads = new HashSet<Integer>();
  }
  
  /**
   * Maps tree leaves to positions in the words list
   */
  private void mapLeavesToPositions(Tree tree, List<? extends CoreLabel> words) {
    List<Tree> leaves = tree.getLeaves();
    if(leaves.size() != words.size()) 
      throw new CorefMentionFinderException("Number of tree leaves not equal with number of words for tree " + tree);
    
    leavesToPositions = new HashMap<Tree, Integer>();
    for(int pos = 0; pos < leaves.size(); pos ++){
      Tree leaf = leaves.get(pos);
      leavesToPositions.put(leaf, pos);
    }
  }
  
  private boolean invalidNamedEntity(Tree headTree, int headPos) {
    String ne = words.get(headPos).ner();
    for(int i = 0; i < INVALID_NES.length; i ++)
      if(INVALID_NES[i].equalsIgnoreCase(ne)) return true;
    
    String word = words.get(headPos).word();
    for(int i = 0; i < INVALID_PATTERNS.length; i ++){
      Matcher m = INVALID_PATTERNS[i].matcher(word);
      if(m.matches()) return true;
    } 
    
    return false;
  }
  
  /**
   * Returns true if this subtree is a valid coreference mention
   * Note: tree must be an actual child of the tree passed to the c'tor!
   * Note: this keeps track of heads it has seen before so traversal order is important!
   *       for example, if traversal is top-downn, it will consider as a valid mention the top-most NP in a recurive NP structure,
   *       e.g., it will only pick "mother of the girl" and skip "mother".
   *       If traversal of the tree is bottom-up, the situation is reversed.
   */
  public boolean valid(Tree tree) {
    // a valid mention must be an NP or a PRP
    if(! tree.value().startsWith(PRP) && ! tree.value().startsWith(NP)) return false;

    // info about the head word of this tree
    Tree head = tree.headTerminal(headFinder);
    // this should never happen, but let's be safe
    if(head == null) return false;
    Integer headPos = leavesToPositions.get(head);
    // this should never happen, but let's be safe
    if(headPos == null) return false;

    // have we seen this head before? if yes then skip 
    if(seenHeads.contains(headPos)) return false;
    
    // the POS tag of the head must be NN*
    String pos = words.get(headPos).tag();
    if(! pos.startsWith("NN") && ! pos.startsWith("PRP")) return false;
    
    // skip certain entities that should not be mentions
    if(invalidNamedEntity(head, headPos)) return false;
      
    // this looks like a valid candidate, it passed all filters
    seenHeads.add(headPos);
    return true;
  }
}
