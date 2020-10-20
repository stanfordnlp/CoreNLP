package edu.stanford.nlp.international.french.pipeline; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeReader;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.international.french.FrenchTreeReaderFactory;
import edu.stanford.nlp.trees.tregex.TregexParseException;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;
import edu.stanford.nlp.util.Pair;

/**
 * Makes FTB trees consistent with FrenchTreebankLanguagePack. Specifically, it removes
 * sentence-initial punctuation, and constraints sentence-final punctuation to be one of
 * [.!?].
 * <p>
 * Also discards two trees of the form (SENT .), which appear in the Candito training
 * set.
 * 
 * @author Spence Green
 *
 */
public class FTBCorrector implements TreeTransformer  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(FTBCorrector.class);

  private static final boolean DEBUG = false;
  
  private final List<Pair<TregexPattern,TsurgeonPattern>> ops;
  
  public FTBCorrector() {
    ops = loadOps();
  }
  
  private List<Pair<TregexPattern, TsurgeonPattern>> loadOps() {
    List<Pair<TregexPattern,TsurgeonPattern>> ops = new ArrayList<>();
    
    String line = null;
    try {
      BufferedReader br = new BufferedReader(new StringReader(editStr));
      List<TsurgeonPattern> tsp = new ArrayList<>();
      while ((line = br.readLine()) != null) {
        if (DEBUG) log.info("Pattern is " + line);
        TregexPattern matchPattern = TregexPattern.compile(line);
        if (DEBUG) log.info(" [" + matchPattern + "]");
        tsp.clear();
        while (continuing(line = br.readLine())) {
          TsurgeonPattern p = Tsurgeon.parseOperation(line);
          if (DEBUG) log.info("Operation is " + line + " [" + p + "]");
          tsp.add(p);
        }
        if ( ! tsp.isEmpty()) {
          TsurgeonPattern tp = Tsurgeon.collectOperations(tsp);
          ops.add(new Pair<>(matchPattern, tp));
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
    ("@PUNC=punc <: __ >, @SENT\n"
        + "delete punc\n"
        + "\n") +
    
    //Delete sentence final punctuation that is preceded by punctuation (first time)
    ("@PUNC=punc <: __ >>- @SENT $, @PUNC\n"
        + "delete punc\n"
        + "\n") +
   
    //Delete sentence final punctuation that is preceded by punctuation (second time)
    ("@PUNC=punc <: __ >>- @SENT $, @PUNC\n"
        + "delete punc\n"
        + "\n") +
    
    //Convert remaining sentence-final punctuation to either . if it is not [.!?]
    ("@PUNC <: /^[^!\\.\\?]$/=term >>- @SENT !$, @PUNC\n"
        + "relabel term /./\n" 
        + "\n") +
    
    //Delete medial, sentence-final punctuation
    ("@PUNC=punc <: (/^[!\\.\\?]$/ . __)\n"
        + "delete punc\n"
        + "\n") +
        
    //Now move the sentence-final mark under SENT
    ("@PUNC=punc <: /^[\\.!\\?]$/ >>- (@SENT <- __=sfpos) !> @SENT\n"
        + "move punc $- sfpos\n" 
        + "\n") +
    
    //For those trees that lack a sentence-final punc, add one.
    ("!@PUNC <: /^[^\\.!\\?]$/ >>- (@SENT <- __=loc)\n"
        + "insert (PUNC .) $- loc\n"
        + "\n") +
    
    //Finally, delete these punctuation marks, which I can't seem to kill otherwise...
    ("@PUNC <: /^[\\.!\\?]+$/=punc . (@PUNC <: /[\\.!\\?]/)\n"
        + "prune punc\n"
        + "\n") +
    
    //A bad MWADV tree in the training set
    ("@NP=bad > @MWADV\n"
        + "excise bad bad\n"
        + "\n") +

    // Not sure why this got a label of X.  Similar trees suggest it
    // should be A instead
    ("X=bad < demi\n"
        + "relabel bad A\n"
        + "\n") +

    // This also seems to be mislabeled
    ("PC=pc < D'|depuis|après\n"
        + "relabel pc P\n"
        + "\n");
    
  /**
   * @param args
   */
  public static void main(String[] args) {
    if(args.length != 1) {
      log.info("Usage: java " + FTBCorrector.class.getName() + " filename\n");
      System.exit(-1);
    }
    
    TreeTransformer tt = new FTBCorrector();
    
    File f = new File(args[0]);
    try {
      //These bad trees in the Candito training set should be thrown out:
      //  (ROOT (SENT (" ") (. .)))
      //  (ROOT (SENT (. .)))
      TregexPattern pBadTree = TregexPattern.compile("@SENT <: @PUNC");
      TregexPattern pBadTree2 = TregexPattern.compile("@SENT <1 @PUNC <2 @PUNC !<3 __");
      
      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
      TreeReaderFactory trf = new FrenchTreeReaderFactory();
      TreeReader tr = trf.newTreeReader(br);
   
      int nTrees = 0;
      for(Tree t; (t = tr.readTree()) != null;nTrees++) {
        TregexMatcher m = pBadTree.matcher(t);
        TregexMatcher m2 = pBadTree2.matcher(t);
        if(m.find() || m2.find()) {
          log.info("Discarding tree: " + t.toString());
        } else {
          Tree fixedT = tt.transformTree(t);
          System.out.println(fixedT.toString());
        }
      }
      
      tr.close();
      
      System.err.printf("Wrote %d trees%n",nTrees);
      
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();

    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (TregexParseException e) {
      e.printStackTrace();
    }
  }
}
