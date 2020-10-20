package edu.stanford.nlp.international.french.scripts; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.List;

import edu.stanford.nlp.international.morph.MorphoFeatureSpecification;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeReader;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.international.french.FrenchTreeReaderFactory;
import edu.stanford.nlp.util.Pair;

/**
 * Writes out an FTB tree file in s-notation to Morfette format.
 * 
 * @author Spence Green
 *
 */
public class TreeToMorfette  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(TreeToMorfette.class);

  /**
   * @param args
   */
  public static void main(String[] args) {
    if (args.length != 1) {
      System.err.printf("Usage: java %s tree_file%n", TreeToMorfette.class.getName());
      System.exit(-1);
    }

    String treeFile = args[0];
    
    TreeReaderFactory trf = new FrenchTreeReaderFactory();
    try {
      TreeReader tr = trf.newTreeReader(new BufferedReader(new InputStreamReader(new FileInputStream(treeFile), "UTF-8")));
  
      for (Tree tree1; (tree1 = tr.readTree()) != null;) {
        List<Label> pretermYield = tree1.preTerminalYield();
        List<Label> yield = tree1.yield();
        int yieldLen = yield.size();
        for (int i = 0; i < yieldLen; ++i) {
          CoreLabel rawToken = (CoreLabel) yield.get(i);
          String word = rawToken.value();
          String morphStr = rawToken.originalText();
          Pair<String,String> lemmaMorph = MorphoFeatureSpecification.splitMorphString(word, morphStr);
          String lemma = lemmaMorph.first();
          String morph = lemmaMorph.second();
          if (morph == null || morph.equals("") || morph.equals("XXX")) {
            morph = ((CoreLabel) pretermYield.get(i)).value();
          }
          System.out.printf("%s %s %s%n", word, lemma, morph);
        }
        System.out.println();
      }
    
      tr.close();
      
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
