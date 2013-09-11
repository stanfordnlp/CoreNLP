package edu.stanford.nlp.wsd.synsense;

import edu.stanford.nlp.ling.StringLabel;
import edu.stanford.nlp.ling.WordFactory;
import edu.stanford.nlp.trees.*;

import java.io.Reader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/* This class provides a way to revise tagging of a corpus to reflect
* all possible ways that future verb phrases might be subcategorized.
*
 * @author Teg Grenager (grenager@cs.stanford.edu)
*/
public class SubcatMarker implements TreeTransformer {

  private static int numMarked = 0;

  public Tree transformTree(Tree tree) {

    //    System.out.println("original tree:");
    //    tree.pennPrint();

    if (tree.children() == null || tree.children().length == 0 || tree.children()[0] == null) {
      return tree;
    }

    TgrepMatcher.addParents(tree);

    Map<Tree, Subcategory> markedVerbs = new HashMap<Tree, Subcategory>();
    markVerbs(tree, markedVerbs);
    Subcategory subcat;
    //    System.out.println("verbs marked:");
    //    tree.pennPrint();
    // go through all the verbs that were subcategorized
    for (Iterator<Tree> treeI = markedVerbs.keySet().iterator(); treeI.hasNext();) {
      Tree tempTree = treeI.next();
      subcat = markedVerbs.get(tempTree);
      // and percolate up the labels for this set of subcategorizedVerbs
      while (tempTree != tree) {
        tempTree = tempTree.parent(tree);
        tempTree.setLabel(new StringLabel(tempTree.label().toString() + "_" + subcat.index()));
      }
    }

    //    System.out.println("marked tree:");
    //    tree.pennPrint();

    return tree;
  }

  private void markVerbs(Tree tree, Map<Tree, Subcategory> markedVerbs) {
    Subcategory subcat = Subcategory.getSubcategory(tree);
    Tree[] kids = tree.children();
    if (subcat != Subcategory.ILLEGAL) {
      numMarked++;
      for (int i = 0; i < kids.length; i++) {
        if (kids[i].label().value().matches("VB.*")) {
          kids[i].setLabel(new StringLabel(kids[i].label().toString() + "_" + subcat.index()));
          markedVerbs.put(kids[i], subcat);
        }
      }
    }
    for (int i = 0; i < kids.length; i++) {
      markVerbs(kids[i], markedVerbs);
    }
  }

  public static void unmarkTree(Tree tree) {
    String label = tree.label().toString();
    if (label == null) {
      unmarkTree(tree.children()[0]);
    } else {
      int underScore = label.indexOf('_');
      if (underScore >= 0) {
        tree.setLabel(new StringLabel(label.substring(0, underScore)));
        if (!tree.isPreTerminal()) {
          Tree[] kids = tree.children();
          for (int i = 0; i < kids.length; i++) {
            unmarkTree(kids[i]);
          }
        }
      }
    }
  }

  // for testing
  public static void main(String[] argv) {
    if (argv.length < 1) {
      System.err.println("Error: must specify filename.");
      System.exit(1);
    }
    int number = 0;
    if (argv.length == 2) {
      number = Integer.parseInt(argv[1]);
    }

    if (argv.length > 2) {
      System.err.println("Error: Too many arguments.");
      System.exit(1);
    }

    String filename = argv[0];
    // cdm Jun 2004: I updated this to be functionally equivalent when I
    // updated NPTmpRetainingTreeNormalizer, but I'm not sure that you're 
    // better using the option here than TEMPORAL_ACL03PCFG
    DiskTreebank treebank = new DiskTreebank(new TreeReaderFactory() {
      public TreeReader newTreeReader(Reader in) {
        return new PennTreeReader(in, new LabeledScoredTreeFactory(new WordFactory()), new NPTmpRetainingTreeNormalizer(NPTmpRetainingTreeNormalizer.TEMPORAL_ANY_TMP_PERCOLATED, false, 0, false));
      }
    });
    // new DiskTreebank(new WordLabeledScoredTreeReaderFactory());
    treebank.loadPath(filename);
    Iterator<Tree> iter = treebank.iterator();
    SubcatMarker m = new SubcatMarker();
    int count = 0;
    while (iter.hasNext()) {
      Tree tree = iter.next();
      if (number > 0 && count++ < number) {
        continue;
      }
      /*
      tree.pennPrint();
      List sentList = tree.yield(new Vector());
      for (int i=0; i<sentList.size(); i++) {
      System.out.print(((StringLabel)sentList.get(i)).toString() + ' ');
      }
      */
      System.out.println();
      m.transformTree(tree);
      tree.pennPrint();
      if (number > 0) {
        break;
      }
      //m.unmarkTree(tree);
      //tree.pennPrint();
    }

    System.out.println("Total marked:" + numMarked);
    System.out.println("Total passive upgrades: " + Subcategory.numPassUpgrades);
    System.out.println("Part counter:");
    System.out.println(Subcategory.partCounter.toString());
  }
}
