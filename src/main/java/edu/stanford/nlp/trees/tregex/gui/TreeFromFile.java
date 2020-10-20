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

  public TreeFromFile(Tree t, String filename) {
    this(t);
    this.filename = filename;
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  public int getSentenceId() { return sentId; }

  public Tree getTree() {
    try {
      // return Tree.valueOf(treeString, new LabeledScoredTreeReaderFactory(new TreeNormalizer()));
      return Tree.valueOf(treeString, FileTreeModel.getTRF());
    } catch(Exception e) {
      System.err.printf("%s: Could not recover tree from internal string:\n%s\n",this.getClass().getName(),treeString);
    }
    return null;
  }

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

  public void setDiffConstituents(Set<Constituent> lessConstituents) { diffSet = lessConstituents; }

  public Set<Constituent> getDiffConstituents() { return diffSet; }

  public void setDiffDecoratedTree(Tree decoratedTree) { markedTree = decoratedTree; }

  public Tree getDiffDecoratedTree() { return markedTree; }

}
