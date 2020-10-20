package edu.stanford.nlp.trees.tregex.gui; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.trees.Constituent;
import edu.stanford.nlp.trees.ConstituentFactory;
import edu.stanford.nlp.trees.LabeledConstituent;
import edu.stanford.nlp.trees.LabeledScoredTreeReaderFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeReader;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.IntPair;

/**
 * Extracts the differences between the sets of constituents indicated by
 * a pair of parse trees. This class requires trees with CoreLabels.
 *
 * @author Spence Green
 *
 */
public class Tdiff  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(Tdiff.class);

  private static final ConstituentFactory cf = LabeledConstituent.factory();


  private Tdiff() {
  }


  /**
   * Marks bracketings in t2 not in t1 using the DoAnnotation field.
   * Returns a list of brackets in t1 not in t2.
   *
   * @param t1
   * @param t2
   * @return A list of brackets in t1 not in t2;
   */
  public static Set<Constituent> markDiff(Tree t1, Tree t2) {
//    if (t1 == null || t2 == null || ! t1.value().equals(t2.value())) {
//      System.err.printf("t1 value is %s; t2 value is %s; t1 is %s t2 is %s", t1.value(), t2.value(), t1, t2);
//    }
    Set<Constituent> t1Labels = (t1 == null) ? Generics.<Constituent>newHashSet() : t1.constituents(cf);
    if(t2 != null) {
      t2.setSpans();
      for(Tree subTree : t2) {
        if(subTree.isPhrasal()) {
          IntPair span = subTree.getSpan();
          Constituent c = cf.newConstituent(span.getSource(), span.getTarget(), subTree.label(), 0.0);
          if(t1Labels.contains(c)) {
            t1Labels.remove(c);
            ((CoreLabel) subTree.label()).set(CoreAnnotations.DoAnnotation.class, false);
          } else {
            ((CoreLabel) subTree.label()).set(CoreAnnotations.DoAnnotation.class, true);
          }
        }
      }
    }

    return t1Labels;
  }


  /**
   * @param args
   */
  public static void main(String[] args) {
    if(args.length != 2) {
      System.out.println("Usage: java Tdiff tree1 tree2");
      return;
    }

    File tree1Path = new File(args[0]);
    File tree2Path = new File(args[1]);

    try {
      TreeReaderFactory trf = new LabeledScoredTreeReaderFactory();
      TreeReader tR1 = trf.newTreeReader(new BufferedReader(new FileReader(tree1Path)));
      TreeReader tR2 = trf.newTreeReader(new BufferedReader(new FileReader(tree2Path)));

      Tree t1 = tR1.readTree();
      Tree t2 = tR2.readTree();

      Set<Constituent> t1Diff = markDiff(t1,t2);
      System.out.println(t2.pennString());
      System.out.println();
      for(Constituent c : t1Diff)
        System.out.println(c);


    } catch (FileNotFoundException e) {
      log.info("File not found!");
    } catch (IOException e) {
      log.info("Unable to read file!");
    }
  }

}
