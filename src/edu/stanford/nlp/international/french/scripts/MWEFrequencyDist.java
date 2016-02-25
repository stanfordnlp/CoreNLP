package edu.stanford.nlp.international.french.scripts; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeReader;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.international.french.FrenchTreeReaderFactory;
import edu.stanford.nlp.trees.tregex.TregexParseException;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.util.Generics;

/**
 * Prints a frequency distribution of MWEs in French.
 * 
 * @author Spence Green
 *
 */
public final class MWEFrequencyDist  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(MWEFrequencyDist.class);

  private MWEFrequencyDist() {};
  
  public static void main(String[] args) {
    if(args.length != 1) {
      System.err.printf("Usage: java %s file%n", MWEFrequencyDist.class.getName());
      System.exit(-1);
    }
    
    final File treeFile = new File(args[0]);
    TwoDimensionalCounter<String,String> mweLabelToString = new TwoDimensionalCounter<>();
    Set<String> uniquePOSSequences = Generics.newHashSet();
    
    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(treeFile), "UTF-8"));
      TreeReaderFactory trf = new FrenchTreeReaderFactory();
      TreeReader tr = trf.newTreeReader(br);

      final TregexPattern pMWE = TregexPattern.compile("/^MW/");
      for(Tree t; (t = tr.readTree()) != null;) {
        //Count MWE statistics
        TregexMatcher m = pMWE.matcher(t);
        while(m.findNextMatchingNode()) {
          Tree match = m.getMatch();
          String label = match.value();
          List<CoreLabel> yield = match.taggedLabeledYield();
          StringBuilder termYield = new StringBuilder();
          StringBuilder posYield = new StringBuilder();
          for(CoreLabel cl : yield) {
            termYield.append(cl.word()).append(" ");
            posYield.append(cl.tag()).append(" ");
          }
          mweLabelToString.incrementCount(label, termYield.toString().trim());
          uniquePOSSequences.add(posYield.toString().trim());
        }
      }
      tr.close(); //Closes the underlying reader
      
      System.out.printf("Type\t#Type\t#Single\t%%Single\t%%Total%n");
      
      double nMWEs = mweLabelToString.totalCount();
      int nAllSingletons = 0;
      int nTokens = 0;
      for(String mweLabel : mweLabelToString.firstKeySet()) {
        int nSingletons = 0;
        double totalCount = mweLabelToString.totalCount(mweLabel);
        Counter<String> mc = mweLabelToString.getCounter(mweLabel);
        for(String term : mc.keySet()) {
          if(mc.getCount(term) == 1.0)
            nSingletons++;
          nTokens += term.split("\\s+").length * (int) mc.getCount(term);
        }
        nAllSingletons += nSingletons;
        System.out.printf("%s\t%d\t%d\t%.2f\t%.2f%n",mweLabel,(int) totalCount,nSingletons, 100.0 * nSingletons / totalCount, 100.0 * totalCount / nMWEs);
      }
      System.out.printf("TOTAL:\t%d\t%d\t%.2f%n", (int) nMWEs,nAllSingletons, 100.0 * nAllSingletons / nMWEs);
      System.out.println("#tokens = " + nTokens);
      System.out.println("#unique MWE POS sequences = " + uniquePOSSequences.size());

    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();

    } catch (FileNotFoundException e) {
      e.printStackTrace();

    } catch (TregexParseException e) {
      e.printStackTrace();

    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
