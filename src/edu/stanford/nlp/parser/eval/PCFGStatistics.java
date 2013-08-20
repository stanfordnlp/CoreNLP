package edu.stanford.nlp.parser.eval;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import edu.stanford.nlp.international.Languages;
import edu.stanford.nlp.international.Languages.Language;
import edu.stanford.nlp.parser.eval.SimplePCFG.RuleRHS;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.trees.TreebankLanguagePack;

/**
 * A package for determining statistical properties of (arbitrary) PCFGs.
 * <p>
 * Implements the entropy measure for (sub-critical) PCFGs suggested by Chi (1999).
 * No binarization is necessary here.
 * <p>
 * The transformations of Johnson (1998) are applied to the raw trees (e.g., a ROOT node,
 * second-order Markovization, etc.).
 * <p>
 * This package does not tally lexical insertion rules.
 * 
 * @author Spence Green
 *
 */
public class PCFGStatistics {

  private final SimplePCFG pcfg;

  public PCFGStatistics(TreebankLanguagePack tlp, boolean basicCat, boolean noTerminals) {
    pcfg = new SimplePCFG(tlp, basicCat, noTerminals);
  }
  
  public void extract(Treebank tb) {
    pcfg.extract(tb);
  }
  
  public double derivationalEntropy() {
    //H(p) per Chi (1999)
    double F_A = 0.0;
    for(String nTerm : pcfg.VnCtr.keySet()) {
      F_A += pcfg.VnCtr.getCount(nTerm) * (Math.log(pcfg.VnCtr.getCount(nTerm)) / Math.log(2));
    }
    
    double F_A_a = 0.0;
    for(String lhsState : pcfg.ruleCtr.firstKeySet()) {
      for(RuleRHS rhs : pcfg.ruleCtr.getCounter(lhsState).keySet()) {
        double ruleCnt = pcfg.ruleCtr.getCount(lhsState, rhs);
        F_A_a += ruleCnt * (Math.log(ruleCnt) / Math.log(2));
      }
    }
    double H_p = F_A - F_A_a;
    
    return H_p;
  }

  public void display(PrintStream ps) {    
    final NumberFormat nf = new DecimalFormat("0.00");
    ps.println("PCFG statistics");
    ps.println("|Vn|      : " + pcfg.VnCtr.keySet().size());
    ps.println("|Vt|      : " + pcfg.VtCtr.keySet().size());
    ps.println("|R|       : " + pcfg.ruleTypes.size());
    ps.println("H(p)      : " + nf.format(derivationalEntropy()));
  }


  private static final int minArgs = 2;
  private static final StringBuilder usage = new StringBuilder();
  static {
    usage.append(String.format("Usage: java %s language treebank_paths\n\n",PCFGStatistics.class.getName()));
    usage.append("Options:\n");
    usage.append("  -b       : Basic category only.\n");
    usage.append("  -e enc   : Input encoding.\n");
    usage.append("  -n       : Don't include terminals in the entropy calculation.\n");
  }

  /**
   * @param args
   */
  public static void main(String[] args) {

    if(args.length < minArgs) {
      System.out.println(usage.toString());
      System.exit(-1);
    }

    boolean basicCategory = false;
    boolean skipTerminals = false;
    String encoding = "UTF-8";

    int i;
    for(i = 0; i < args.length; i++) {
      if(args[i].startsWith("-")) {
        if(args[i].equals("-b"))
          basicCategory = true;
        else if(args[i].equals("-e"))
          encoding = args[++i].trim();
        else if(args[i].equals("-n"))
          skipTerminals = true;

      } else {
        break;
      }
    }
    final Language lang = Language.valueOf(args[i++]);
    final TreebankLangParserParams tlpp = Languages.getLanguageParams(lang);
    
    tlpp.setInputEncoding(encoding);

    Treebank tb = tlpp.diskTreebank();
    for(; i < args.length; i++)
      tb.loadPath(args[i]);

    PCFGStatistics sre = new PCFGStatistics(tlpp.treebankLanguagePack(), basicCategory, skipTerminals);
    sre.extract(tb);
    sre.display(System.out);
  }
}
