package edu.stanford.nlp.parser.tools; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.trees.DiskTreebank;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * Reads in a set of treebank files and either adds (default) or removes a top bracket.
 * 
 * @author Spence Green
 *
 */
public class ManipulateTopBracket  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ManipulateTopBracket.class);

  private static final int minArgs = 1;
  private static String usage() {
    StringBuilder usage = new StringBuilder();
    String nl = System.getProperty("line.separator");
    usage.append(String.format("Usage: java %s [OPTS] file(s) > bracketed_trees%n%n",ManipulateTopBracket.class.getName()));
    usage.append("Options:").append(nl);
    usage.append("  -v         : Verbose mode.").append(nl);
    usage.append("  -r         : Remove top bracket.").append(nl);
    usage.append("  -l lang    : Select language settings from " + Language.langList).append(nl);
    usage.append("  -e enc     : Encoding.").append(nl);
    return usage.toString();
  }
  private static Map<String,Integer> argDefs() {
    Map<String,Integer> argDefs = Generics.newHashMap();
    argDefs.put("e", 1);
    argDefs.put("v", 0);
    argDefs.put("l", 1);
    argDefs.put("r", 0);
    return argDefs;
  }

  public static void main(String[] args) {
    if(args.length < minArgs) {
      System.out.println(usage());
      System.exit(-1);
    }

    Properties options = StringUtils.argsToProperties(args, argDefs());
    Language language = PropertiesUtils.get(options, "l", Language.English, Language.class);
    TreebankLangParserParams tlpp = language.params;
    DiskTreebank tb = null;
    String encoding = options.getProperty("l", "UTF-8");
    boolean removeBracket = PropertiesUtils.getBool(options, "b", false);
    
    tlpp.setInputEncoding(encoding);
    tlpp.setOutputEncoding(encoding);
    tb = tlpp.diskTreebank();

    String[] files = options.getProperty("", "").split("\\s+");
    if (files.length != 0) {
      for (String filename : files) {
        tb.loadPath(filename);
      }
    } else {
      log.info(usage());
      System.exit(-1);
    }

    PrintWriter pwo = tlpp.pw();
    String startSymbol = tlpp.treebankLanguagePack().startSymbol();
    TreeFactory tf = new LabeledScoredTreeFactory();
    int nTrees = 0;
    for(Tree t : tb) {
      if(removeBracket) {
        if(t.value().equals(startSymbol)) {
          t = t.firstChild();
        }
        
      } else if( ! t.value().equals(startSymbol)) { //Add a bracket if it isn't already there
        t = tf.newTreeNode(startSymbol, Collections.singletonList(t));
      }
      pwo.println(t.toString());
      nTrees++;
    }      
    pwo.close();
    System.err.printf("Processed %d trees.%n", nTrees);
  }
}
