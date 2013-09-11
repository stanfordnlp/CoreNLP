package edu.stanford.nlp.parser.eval;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

import no.uib.cipr.matrix.EVD;
import no.uib.cipr.matrix.Matrix;
import no.uib.cipr.matrix.NotConvergedException;

import edu.stanford.nlp.international.Languages;
import edu.stanford.nlp.international.Languages.Language;
import edu.stanford.nlp.math.mtj.ArrayMatrix;
import edu.stanford.nlp.parser.eval.SimplePCFG.RuleRHS;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.trees.TreebankLanguagePack;

/**
 * 
 * @author Spence Green
 *
 */
public class PCFGBranchingRate {

  private final SimplePCFG pcfg;
  private final Map<String,Integer> symLUT;

  public PCFGBranchingRate(Treebank tb, TreebankLanguagePack tlp, boolean basicCat, boolean noTerminals) {
    pcfg = new SimplePCFG(tlp,basicCat,noTerminals);
    pcfg.extract(tb);
    pcfg.normalize();

    //Make the symbol table
    Set<String> symbols = new HashSet<String>(pcfg.VnCtr.keySet());
    symbols.addAll(pcfg.VtCtr.keySet());
    symLUT = new HashMap<String,Integer>();
    int i = 0;
    for(String sym : symbols)
      symLUT.put(sym, i++);
  }

  public int getNumSymbols() { return symLUT.keySet().size(); }

  public double computeRho() {
    double[][] mExp = new double[symLUT.keySet().size()][symLUT.keySet().size()];

    for(String A : pcfg.ruleCtr.firstKeySet()) {
      if( ! symLUT.containsKey(A))
        throw new RuntimeException("LUT does not contain |||" + A + "|||");
      final int aIdx = symLUT.get(A);
      final Set<RuleRHS> rhsSet = pcfg.ruleCtr.getCounter(A).keySet();
      for(RuleRHS rhs : rhsSet) {
        final double p_r = pcfg.ruleCtr.getCount(A, rhs);
        Counter<String> n = new ClassicCounter<String>();
        for(String sRhs : rhs.getStates())
          n.incrementCount(sRhs);
        for(String B : n.keySet()) {
          int bIdx = symLUT.get(B);
          double n_B = n.getCount(B);
          double p_A_B = p_r * n_B;
          mExp[aIdx][bIdx] = p_A_B;
        }
      }
    }

    //Convert to mtj and do an eigen decomposition
    //  A = VDV^-1
    Matrix expMatrix = new ArrayMatrix(mExp);
    double rho = 0.0;
    try {
      EVD evd = EVD.factorize(expMatrix);
      double[] eigenvalues = evd.getRealEigenvalues();
      rho = absMax(eigenvalues);

    } catch (NotConvergedException e) {
      System.err.println(this.getClass().getName() + ": EVD did not converge");
    }

    return rho;
  }

  private double absMax(double[] mat) {
    double max = -1.0;
    for(int i = 0; i < mat.length; i++)
      if(Math.abs(mat[i]) > max)
        max = mat[i];
    return max;
  }


  private static final int minArgs = 2;
  private static final StringBuilder usage = new StringBuilder();
  static {
    usage.append(String.format("Usage: java %s language treebank_paths\n\n",PCFGBranchingRate.class.getName()));
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

    PCFGBranchingRate pbr = new PCFGBranchingRate(tb,tlpp.treebankLanguagePack(),basicCategory,skipTerminals);
    System.out.println("|V u T| = " + pbr.getNumSymbols());
    NumberFormat nf = new DecimalFormat("0.0000");
    System.out.println("rho = " + nf.format(pbr.computeRho()));
  }
}
