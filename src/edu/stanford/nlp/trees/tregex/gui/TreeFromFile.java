package edu.stanford.nlp.trees.tregex.gui; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JTextField;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasIndex;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.trees.Constituent;
import edu.stanford.nlp.trees.Tree;


/**
 * Simple utility class for storing a tree as well as the sentence the tree represents and
 * a label with the filename of the file that the tree was stored in.
 *
 * @author Anna Rafferty
 */
public class TreeFromFile  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(TreeFromFile.class);

  private final String treeString;
  private String filename;
  private String sentence = "";
  private int sentId = -1;
  private JTextField label; // = null;

  //TDiff stuff
  private Set<Constituent> diffSet;
  private Tree markedTree;

  /**
   * Stores a tree as a string, along with its yield.  If the root label has
   * a sentence index and document id, the displayed sentence is prefixed with them.
   *
   * @param t The tree to store
   */
  public TreeFromFile(Tree t) {
    this.treeString = t.toString();
    sentence = SentenceUtils.listToString(t.yield());
    if(t.label() instanceof HasIndex) {
      sentId = ((CoreLabel)t.label()).sentIndex();
      filename = ((CoreLabel)t.label()).docID();

      if(sentId != -1 && filename != null && !filename.equals(""))
      	sentence = String.format("%s-%d   %s", filename,sentId,sentence);
    }
  }

  /**
   * Stores a tree and the name of the file it came from.
   *
   * @param t The tree to store
   * @param filename The name of the file, which replaces any document id from the tree
   */
  public TreeFromFile(Tree t, String filename) {
    this(t);
    this.filename = filename;
  }

  /**
   * Returns the name of the file the tree came from.
   *
   * @return The filename, which may be null
   */
  public String getFilename() {
    return filename;
  }

  /**
   * Sets the name of the file the tree came from.
   *
   * @param filename The filename
   */
  public void setFilename(String filename) {
    this.filename = filename;
  }

  /**
   * Returns the sentence index from the tree's root label.
   *
   * @return The sentence index, or -1 if there was none
   */
  public int getSentenceId() { return sentId; }

  /**
   * Rebuilds the tree from its stored string, using the current tree reader
   * factory, so each call returns a new Tree.
   *
   * @return The tree, or null if the string could not be read
   */
  public Tree getTree() {
    try {
      // return Tree.valueOf(treeString, new LabeledScoredTreeReaderFactory(new TreeNormalizer()));
      return Tree.valueOf(treeString, FileTreeModel.getTRF());
    } catch(Exception e) {
      System.err.printf("%s: Could not recover tree from internal string:\n%s\n",this.getClass().getName(),treeString);
    }
    return null;
  }

  /**
   * Returns a text field showing the sentence, creating it on first use.
   *
   * @return The label
   */
  public JTextField getLabel() {
    if(label == null) {
      label = new JTextField(this.toString());
      label.setBorder(BorderFactory.createEmptyBorder());
    }
    return label;
  }

  @Override
  public String toString() {
    if (sentence.length() == 0)
      sentence = "* deleted *";
    return sentence;
  }

  /**
   * Sets the constituents found by a tree diff.
   *
   * @param lessConstituents The constituents; kept, not copied
   */
  public void setDiffConstituents(Set<Constituent> lessConstituents) { diffSet = lessConstituents; }

  /**
   * Returns the constituents found by a tree diff.
   *
   * @return The constituents, or null if none were set
   */
  public Set<Constituent> getDiffConstituents() { return diffSet; }

  /**
   * Sets the tree as decorated by a tree diff.
   *
   * @param decoratedTree The decorated tree
   */
  public void setDiffDecoratedTree(Tree decoratedTree) { markedTree = decoratedTree; }

  /**
   * Returns the tree as decorated by a tree diff.
   *
   * @return The decorated tree, or null if none was set
   */
  public Tree getDiffDecoratedTree() { return markedTree; }

}
