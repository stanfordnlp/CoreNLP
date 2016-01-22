package edu.stanford.nlp.semgraph.semgrex.demo;

import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.parser.lexparser.EnglishTreebankParserParams;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.Tree;

/**
 * A small demo that shows how to convert a tree to a SemanticGraph
 * and then run a SemgrexPattern on it
 *
 * @author John Bauer
 */
public class SemgrexDemo {
  public static void main(String[] args) {
    String treeString = "(ROOT  (S (NP (PRP$ My) (NN dog)) (ADVP (RB also)) (VP (VBZ likes) (S (VP (VBG eating) (NP (NN sausage))))) (. .)))";
    // Typically the tree is constructed by parsing or reading a
    // treebank.  This is just for example purposes
    Tree tree = Tree.valueOf(treeString);

    // This creates English uncollapsed dependencies as a
    // SemanticGraph.  If you are creating many SemanticGraphs, you
    // should use a GrammaticalStructureFactory and use it to generate
    // the intermediate GrammaticalStructure instead
    SemanticGraph graph = SemanticGraphFactory.generateUncollapsedDependencies(tree);

    // Alternatively, this could have been the Chinese params or any
    // other language supported.  As of 2014, only English and Chinese
    TreebankLangParserParams params = new EnglishTreebankParserParams();
    GrammaticalStructureFactory gsf = params.treebankLanguagePack().grammaticalStructureFactory(params.treebankLanguagePack().punctuationWordRejectFilter(), params.typedDependencyHeadFinder());

    GrammaticalStructure gs = gsf.newGrammaticalStructure(tree);

    System.err.println(graph);

    SemgrexPattern semgrex = SemgrexPattern.compile("{}=A <<nsubj {}=B");
    SemgrexMatcher matcher = semgrex.matcher(graph);
    // This will produce two results on the given tree: "likes" is an
    // ancestor of both "dog" and "my" via the nsubj relation
    while (matcher.find()) {
      System.err.println(matcher.getNode("A") + " <<nsubj " + matcher.getNode("B"));
    }
  }
}
