package edu.stanford.nlp.trees.ud;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.Filters;
import edu.stanford.nlp.util.StringUtils;

import java.util.Iterator;
import java.util.List;
import java.util.Properties;

/**
 *
 * Command-line utility to
 *
 * a) convert constituency trees to basic English UD trees
 * b) convert basic dependency trees to enhanced and enhanced++ UD graphs
 *
 *
 * @author Sebastian Schuster
 */
public class UniversalDependenciesConverter {


  private static GrammaticalStructure semanticGraphToGrammaticalStructure(SemanticGraph sg) {

    /* sg.typedDependency() generates an ArrayList */
    List<TypedDependency> deps = (List<TypedDependency>) sg.typedDependencies();

    IndexedWord root = deps.get(0).gov();
    TreeGraphNode rootNode = new TreeGraphNode(root);
    GrammaticalStructure gs = new UniversalEnglishGrammaticalStructure(deps, rootNode);

    return gs;
  }


  /**
   * Converts basic UD tree to enhanced UD graph.
   *
   */
  private static SemanticGraph convertBasicToEnhanced(SemanticGraph sg) {
    GrammaticalStructure gs = semanticGraphToGrammaticalStructure(sg);
    return SemanticGraphFactory.generateEnhancedDependencies(gs);
  }

  /**
   * Converts basic UD tree to enhanced++ UD graph.
   *
   */
  private static SemanticGraph convertBasicToEnhancedPlusPlus(SemanticGraph sg) {
    GrammaticalStructure gs = semanticGraphToGrammaticalStructure(sg);
    return SemanticGraphFactory.generateEnhancedPlusPlusDependencies(gs);
  }

  private static SemanticGraph convertTreeToBasic(Tree tree) {
    SemanticGraph sg = SemanticGraphFactory.makeFromTree(tree, SemanticGraphFactory.Mode.BASIC,
        GrammaticalStructure.Extras.NONE, false, null, false, true);
    return sg;
  }


  private static class TreeToSemanticGraphIterator implements Iterator<SemanticGraph> {

    private Iterator<Tree> treeIterator;
    private Tree currentTree = null;

    public TreeToSemanticGraphIterator(Iterator<Tree> treeIterator) {
      this.treeIterator = treeIterator;
    }

    @Override
    public boolean hasNext() {
      return treeIterator.hasNext();
    }

    @Override
    public SemanticGraph next() {
      Tree t = treeIterator.next();
      currentTree = t;
      return convertTreeToBasic(t);
    }

    public Tree getCurrentTree() {
      return this.currentTree;
    }

  }


  /**
   *
   * Converts a constituency tree to the English basic, enhanced, or
   * enhanced++ Universal dependencies representation, or an English basic
   * Universal dependencies tree to the enhanced or enhanced++ representation.
   * <p>
   * Command-line options:<br>
   * {@code -treeFile}: File with PTB-formatted constituency trees<br>
   * {@code -conlluFile}: File with basic dependency trees in CoNLL-U format<br>
   * {@code -outputRepresentation}: "basic" (default), "enhanced", or "enhanced++"
   *
   */
  public static void main(String args[]) {
    Properties props = StringUtils.argsToProperties(args);

    String treeFileName = props.getProperty("treeFile");
    String conlluFileName = props.getProperty("conlluFile");
    String outputRepresentation = props.getProperty("outputRepresentation", "basic");


    Iterator<SemanticGraph> sgIterator = null;

    if (treeFileName != null) {
      MemoryTreebank tb = new MemoryTreebank(new NPTmpRetainingTreeNormalizer(0, false, 1, false));
      tb.loadPath(treeFileName);
      Iterator<Tree> treeIterator = tb.iterator();
      sgIterator = new TreeToSemanticGraphIterator(treeIterator);
    } else if (conlluFileName != null) {
      CoNLLUDocumentReader reader = new CoNLLUDocumentReader();
      try {
        sgIterator = reader.getIterator(IOUtils.readerFromString(conlluFileName));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    } else {
      System.err.println("No input file specified!");
      System.err.println("");
      System.err.printf("Usage: java %s [-treeFile trees.tree | -conlluFile deptrees.conllu]"
          + " [-outputRepresentation basic|enhanced|enhanced++ (default: basic)]%n",
          UniversalDependenciesConverter.class.getCanonicalName());
      return;
    }

    CoNLLUDocumentWriter writer = new CoNLLUDocumentWriter();

    while (sgIterator.hasNext()) {
      SemanticGraph sg = sgIterator.next();

      if (treeFileName != null) {
        //add UPOS tags
        Tree tree = ((TreeToSemanticGraphIterator) sgIterator).getCurrentTree();
        Tree uposTree = UniversalPOSMapper.mapTree(tree);
        List<Label> uposLabels = uposTree.preTerminalYield();
        for (IndexedWord token: sg.vertexListSorted()) {
          int idx = token.index() - 1;
          String uposTag = uposLabels.get(idx).value();
          token.set(CoreAnnotations.CoarseTagAnnotation.class, uposTag);
        }
      }

     if (outputRepresentation.equalsIgnoreCase("enhanced")) {
        sg = convertBasicToEnhanced(sg);
      } else if (outputRepresentation.equalsIgnoreCase("enhanced++")) {
        sg = convertBasicToEnhancedPlusPlus(sg);
      }
      System.out.println(writer.printSemanticGraph(sg));

    }

  }

}
