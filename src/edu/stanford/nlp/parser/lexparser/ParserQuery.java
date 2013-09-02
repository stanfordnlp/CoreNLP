package edu.stanford.nlp.parser.lexparser;

import java.io.PrintWriter;
import java.util.List;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.parser.KBestViterbiParser;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.ScoredObject;

public interface ParserQuery {
  Tree parseWithFallback(List<? extends HasWord> sentence, PrintWriter pwErr, PrintWriter pwo);

  Tree getBestPCFGParse();

  Tree getBestDependencyParse(boolean debinarize);

  Tree getBestFactoredParse();

  List<ScoredObject<Tree>> getBestPCFGParses();

  void restoreOriginalWords(Tree tree);

  boolean hasFactoredParse();

  List<ScoredObject<Tree>> getKBestPCFGParses(int kbestPCFG);

  List<ScoredObject<Tree>> getKGoodFactoredParses(int kbest);

  KBestViterbiParser getPCFGParser();

  KBestViterbiParser getFactoredParser();

  KBestViterbiParser getDependencyParser();
}
