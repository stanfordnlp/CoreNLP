package edu.stanford.nlp.international.arabic.pipeline; 

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeVisitor;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Converts {@code VP < PP-CLR} construction to {@code MWV < MWP}.
 * 
 * @author Spence Green
 *
 */
public class MWETreeVisitor implements TreeVisitor  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(MWETreeVisitor.class);

 private static final boolean DEBUG = false;
  
  private final List<Pair<TregexPattern,TsurgeonPattern>> ops;
  
  public MWETreeVisitor() {
    ops = loadOps();
  }
  
  private static List<Pair<TregexPattern, TsurgeonPattern>> loadOps() {
    List<Pair<TregexPattern,TsurgeonPattern>> ops = new ArrayList<>();

    try {
      BufferedReader br = new BufferedReader(new StringReader(editStr));
      List<TsurgeonPattern> tsp = new ArrayList<>();
      for (String line; (line = br.readLine()) != null; ) {
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
      log.warn(ioe);
    }
    
    return ops;
  }
  
  private static boolean continuing(String str) {
    return str != null && ! str.matches("\\s*");
  }

  @Override
  public void visitTree(Tree t) {
    Tsurgeon.processPatternsOnTree(ops, t);
  }

  
  /**
   * The Tsurgeon patterns
   */
  private static final String editStr = 
    
    //Mark MWEs
    ("@VP=vp < /PP-CLR/=pp\n"
        + "relabel vp MWV\n"
        + "relabel pp MWP\n"
        + "\n");

}
