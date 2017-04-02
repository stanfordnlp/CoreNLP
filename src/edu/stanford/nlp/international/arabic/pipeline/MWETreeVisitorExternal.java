package edu.stanford.nlp.international.arabic.pipeline; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeReader;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.TreeVisitor;
import edu.stanford.nlp.trees.international.arabic.ArabicTreeReaderFactory;
import edu.stanford.nlp.util.Generics;

/**
 * Converts all contiguous MWEs listed in an MWE list to flattened trees.
 * 
 * @author Spence Green
 *
 */
public class MWETreeVisitorExternal implements TreeVisitor  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(MWETreeVisitorExternal.class);

  private static final String mweFile = "/home/rayder441/sandbox/javanlp/projects/core/data/edu/stanford/nlp/pipeline/attia-mwe-list.txt.out.tok.fixed.proc.uniq";
  
  private final Set<String> mweDictionary;
  
  public MWETreeVisitorExternal() {
    mweDictionary = loadMWEs();
  }
  
  private Set<String> loadMWEs() {
    Set<String> mweSet = Generics.newHashSet();  
    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(mweFile), "UTF-8"));
      for (String line; (line = br.readLine()) != null;) {
        mweSet.add(line.trim());
      }
      br.close();
    
    } catch (UnsupportedEncodingException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return mweSet;
  }


  /**
   * Perform (possibly destructive) operations on the tree. Do a top-down DFS on the tree.
   */
  public void visitTree(Tree tree) {
    if (tree == null) return;
    String yield = SentenceUtils.listToString(tree.yield());
    if (mweDictionary.contains(yield)) {
      List<Tree> children = getPreterminalSubtrees(tree);
      String newLabel = "MW" + tree.value();
      tree.setValue(newLabel);
      tree.setChildren(children);
      // Bottom out of the recursion
      return;
      
    } else {
      for (Tree subTree : tree.children()) {
        if (subTree.isPhrasal()) {
          // Only phrasal trees can have yields > 1!!
          visitTree(subTree);
        }
      }
    }
  }
  
  private List<Tree> getPreterminalSubtrees(Tree tree) {
    List<Tree> preterminals = new ArrayList<>();
    for (Tree subTree : tree) {
      if (subTree.isPreTerminal()) {
        preterminals.add(subTree);
      }
    }
    return preterminals;
  }
  
  /**
   * For debugging.
   * 
   * @param args
   */
  public static void main(String[] args) {
    if (args.length != 1) {
      System.err.printf("Usage: java %s atb_tree_file > atb_tree_file.out%n", MWETreeVisitorExternal.class.getName());
      System.exit(-1);
    }
    
    TreeReaderFactory trf = new ArabicTreeReaderFactory();
    try {
      TreeReader tr = trf.newTreeReader(new BufferedReader(new InputStreamReader(new FileInputStream(args[0]), "UTF-8")));
      TreeVisitor visitor = new MWETreeVisitorExternal();
      
      int treeId = 0;
      for (Tree tree; (tree = tr.readTree()) != null; ++treeId) {
        if (tree.value().equals("ROOT")) {
          // Skip over the ROOT tag
          tree = tree.firstChild();
        }
        visitor.visitTree(tree);
        System.out.println(tree.toString());
      }
      tr.close();
      
      System.err.printf("Processed %d trees.%n", treeId);
    
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
