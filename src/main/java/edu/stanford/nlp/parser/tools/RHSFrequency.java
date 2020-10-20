package edu.stanford.nlp.parser.tools;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.parser.lexparser.EnglishTreebankParserParams;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.trees.DiskTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;

/**
 * Create frequency distribution for RHS of grammar rules.
 * 
 * @author Spence Green
 *
 */
public class RHSFrequency {

  private static final int minArgs = 2;
  private static final StringBuilder usage = new StringBuilder();
  static {
    usage.append(String.format("Usage: java %s [OPTS] lhs tree_file \n\n",RHSFrequency.class.getName()));
    usage.append("Options:\n");
    usage.append("  -l lang    : Select language settings from " + Language.langList + "\n");
    usage.append("  -e enc     : Encoding.\n");
  }

  public static void main(String[] args) {
    if(args.length < minArgs) {
      System.out.println(usage.toString());
      System.exit(-1);
    }

    TreebankLangParserParams tlpp = new EnglishTreebankParserParams();
    DiskTreebank tb = null;
    String encoding = "UTF-8";
    TregexPattern rootMatch = null;
    
    for(int i = 0; i < args.length; i++) {
      if(args[i].startsWith("-")) {
        switch (args[i]) {
          case "-l":
            Language lang = Language.valueOf(args[++i].trim());
            tlpp = lang.params;

            break;
          case "-e":
            encoding = args[++i];

            break;
          default:
            System.out.println(usage.toString());
            System.exit(-1);
        }

      } else {
        rootMatch = TregexPattern.compile("@" + args[i++]);

        if(tb == null) {
          if(tlpp == null) {
            System.out.println(usage.toString());
            System.exit(-1);
          } else {
            tlpp.setInputEncoding(encoding);
            tlpp.setOutputEncoding(encoding);
            tb = tlpp.diskTreebank();
          }
        }
        tb.loadPath(args[i++]);
      }
    }

    Counter<String> rhsCounter = new ClassicCounter<>();
    for(Tree t : tb) {
      TregexMatcher m = rootMatch.matcher(t);
      while(m.findNextMatchingNode()) {
        Tree match = m.getMatch();
        StringBuilder sb = new StringBuilder();
        for(Tree kid : match.children())
          sb.append(kid.value()).append(" ");
        rhsCounter.incrementCount(sb.toString().trim());
      }
    }

    List<String> biggestKeys = new ArrayList<>(rhsCounter.keySet());
    Collections.sort(biggestKeys, Counters.toComparatorDescending(rhsCounter));

    PrintWriter pw = tlpp.pw();
    for(String rhs : biggestKeys)
      pw.printf("%s\t%d%n", rhs,(int) rhsCounter.getCount(rhs));
    pw.close();
  }
  
}
