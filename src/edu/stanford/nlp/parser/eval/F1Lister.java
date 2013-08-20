package edu.stanford.nlp.parser.eval;

import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.parser.lexparser.EnglishTreebankParserParams;
import edu.stanford.nlp.stats.EquivalenceClassEval;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.TreeReader;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.StringUtils;

import java.io.Reader;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** List F1 for each parse in a collection of trees.
 * <p>
 * You can use this in conjunction with the script scoreSig.R as follows:
 * <p>
 * <tt>
 * java edu.stanford.nlp.parser.eval.F1Lister -metric metric goldfile testfile1 &gt; outfile1
 * java edu.stanford.nlp.parser.eval.F1Lister -metric metric goldfile testfile2 &gt; outfile2
 * R CMD BATCH -f1=outfile1 -f2=outfile2 scoreSig.R finalOutfile
 * </tt>
 * <p>
 * And <tt>finalOutfile</tt> will contain a significance level by a Wilcoxon
 * signed rank test.  Your installation of
 * R must have the exactRankTests package installed to do this.
 *
 * @author Roger Levy
 */
class F1Lister {

  private F1Lister() {} // static class

  private static final String METRIC_OPTION = "-metric";
  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      System.err.println("Usage: java CandidateParseManager goldFile candidateFile");
      System.exit(0);
    }
    Map<String,Integer> flagMap = new HashMap<String,Integer>();
    flagMap.put(CandidateParseManager.LANG_OPTION, Integer.valueOf(1));
    flagMap.put(METRIC_OPTION, Integer.valueOf(1));
    Map<String,String[]> argsMap = StringUtils.argsToMap(args, flagMap);
    args = argsMap.get(null);

    TreebankLangParserParams tlpp = null;
    if (argsMap.keySet().contains(CandidateParseManager.LANG_OPTION)) {
      tlpp = (TreebankLangParserParams) Class.forName((argsMap.get(CandidateParseManager.LANG_OPTION))[0]).newInstance();
      System.err.println("Using treebank language parameters "+ tlpp.getClass().getName());
    } else {
      tlpp = new EnglishTreebankParserParams();
    }

    TreeObjectifier tObj = null;
    if(argsMap.keySet().contains(METRIC_OPTION)) {
      if(argsMap.get(METRIC_OPTION)[0].equals("parseval"))
        tObj = CandidateParseManager.parsevalObjectifier(tlpp.collinizerEvalb(), Pattern.compile(""));
      if(argsMap.get(METRIC_OPTION)[0].equals("untypedDep"))
        tObj = CandidateParseManager.untypedDependencyObjectifier(tlpp.collinizerEvalb(),tlpp.headFinder());
     if(argsMap.get(METRIC_OPTION)[0].equals("typedDep"))
        tObj = CandidateParseManager.typedDependencyObjectifier(tlpp.collinizerEvalb(),tlpp.headFinder());
    }

    Reader rTrue = new BufferedReader(new FileReader(args[0]));
    Reader rCand = new BufferedReader(new FileReader(args[1]));
    TreeReaderFactory trf = tlpp.treeReaderFactory();
    TreeReader trTrue = trf.newTreeReader(rTrue);
    TreeReader trCand = trf.newTreeReader(rCand);

    for (Tree gold ; ((gold = trTrue.readTree()) != null); ) {
      Tree guess = trCand.readTree();
      CandidateParseManager manager = new CandidateParseManager();
      Set<Tree> c = new HashSet<Tree>();
      c.add(guess);
      manager.add(new CandidateParses(c,gold));
      System.out.println(manager.performance(tObj,EquivalenceClassEval.DEFAULT_CHECKER, new CandidateParseManager.BestTallierFactory())[4].getCount(null));
    }

  }

}
