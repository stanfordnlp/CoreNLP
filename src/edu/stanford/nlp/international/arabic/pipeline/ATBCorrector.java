package edu.stanford.nlp.international.arabic.pipeline;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeReader;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.international.arabic.ArabicTreeReaderFactory;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;
import edu.stanford.nlp.util.Pair;

/**
 * Makes ATB trees consistent with ArabicTreebankLanguagePack. Specifically, it removes
 * sentence-initial punctuation, and constraints sentence-final punctuation to be one of
 * [.!?].
 * <p>
 * Also cleans up some of the headlines, and other weirdly tokenized sentences.
 * 
 * @author Spence Green
 *
 */
public class ATBCorrector implements TreeTransformer {

  private static final boolean DEBUG = false;
  
  private final List<Pair<TregexPattern,TsurgeonPattern>> ops;
  
  public ATBCorrector() {
    ops = loadOps();
  }
  
  private List<Pair<TregexPattern, TsurgeonPattern>> loadOps() {
    List<Pair<TregexPattern,TsurgeonPattern>> ops = new ArrayList<Pair<TregexPattern,TsurgeonPattern>>();
    
    String line = null;
    try {
      BufferedReader br = new BufferedReader(new StringReader(editStr));
      List<TsurgeonPattern> tsp = new ArrayList<TsurgeonPattern>();
      while ((line = br.readLine()) != null) {
        if (DEBUG) System.err.print("Pattern is " + line);
        TregexPattern matchPattern = TregexPattern.compile(line);
        if (DEBUG) System.err.println(" [" + matchPattern + "]");
        tsp.clear();
        while (continuing(line = br.readLine())) {
          TsurgeonPattern p = Tsurgeon.parseOperation(line);
          if (DEBUG) System.err.println("Operation is " + line + " [" + p + "]");
          tsp.add(p);
        }
        if ( ! tsp.isEmpty()) {
          TsurgeonPattern tp = Tsurgeon.collectOperations(tsp);
          ops.add(new Pair<TregexPattern,TsurgeonPattern>(matchPattern, tp));
        }
      } // while not at end of file
    } catch (IOException ioe) {
      ioe.printStackTrace();
    }
    
    return ops;
  }
  
  private static boolean continuing(String str) {
    return str != null && ! str.matches("\\s*");
  }


  public Tree transformTree(Tree t) {
    return Tsurgeon.processPatternsOnTree(ops, t);
  }
  
  /**
   * The Tsurgeon patterns
   */
  private static final String editStr = 
    
    //Delete sentence-initial punctuation
    ("@PUNC=punc <: __ >>, (/^S/ > @ROOT) \n"
        + "prune punc\n"
        + "\n") +

    //Delete sentence-initial punctuation (again)
    ("@PUNC=punc <: __ >>, (/^S/ > @ROOT) \n"
        + "prune punc\n"
        + "\n") +

    //Delete sentence final punctuation that is preceded by punctuation (first time)
    ("@PUNC=punc >>- (/^S/ > @ROOT) <: __ $, @PUNC \n"
        + "prune punc\n"
        + "\n") +
   
    //Delete sentence final punctuation that is preceded by punctuation (second time)
    ("@PUNC=punc >>- (/^S/ > @ROOT) <: __ $, @PUNC \n"
        + "prune punc\n"
        + "\n") +
    
    //Convert remaining sentence-final punctuation to . if it is not [.!?]
    ("@PUNC=pos >>- (/^S/ > @ROOT) <: /[^\\.\\?!]/=term !$, @PUNC \n"
        + "relabel pos PUNC\n"
        + "relabel term /./\n" 
        + "\n") +
    
    //Delete medial, sentence-final punctuation
//    ("@PUNC=punc <: /[!\\.\\?]+/ $. __\n"
//        + "prune punc\n"
//        + "\n") +
        
    //Now move the sentence-final mark under the top-level node
    ("@PUNC=punc <: /^[\\.!\\?]+$/ >>- (/^S/ > @ROOT <- __=sfpos) !> (/^S/ > @ROOT)\n"
        + "move punc $- sfpos\n" 
        + "\n");
    
    //For those trees that lack a sentence-final punc, add one.
//    ("/^[^\\.!\\?]$/ >>- (__ > @ROOT <- __=loc) <: __\n"
//        + "insert (PUNC .) $- loc\n"
//        + "\n");
  
  /**
   * @param args
   */
  public static void main(String[] args) {
    if(args.length != 1) {
      System.err.println("Usage: java " + ATBCorrector.class.getName() + " filename\n");
      System.exit(-1);
    }
    
    TreeTransformer tt = new ATBCorrector();

    File f = new File(args[0]);
    try {

      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
      TreeReaderFactory trf = new ArabicTreeReaderFactory.ArabicRawTreeReaderFactory();
      TreeReader tr = trf.newTreeReader(br);

      int nTrees = 0;
      for(Tree t; (t = tr.readTree()) != null;nTrees++) {
        Tree fixedT = tt.transformTree(t);
        System.out.println(fixedT.toString());
      }

      tr.close();

      System.err.printf("Wrote %d trees%n",nTrees);

    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();

    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
