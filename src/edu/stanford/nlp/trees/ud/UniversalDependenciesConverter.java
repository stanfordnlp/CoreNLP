package edu.stanford.nlp.trees.ud;

import edu.stanford.nlp.ie.NERClassifierCombiner;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.StringUtils;

import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 *
 * Command-line utility to:
 *
 * a) convert constituency trees to basic English UD trees
 * b) convert basic dependency trees to enhanced and enhanced++ UD graphs
 *
 *
 * @author Sebastian Schuster
 */
public class UniversalDependenciesConverter {


  private static final boolean USE_NAME = System.getProperty("UDUseNameRelation") != null;

  private UniversalDependenciesConverter() {} // static main


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
    addLemmata(tree);
    addNERTags(tree);
    SemanticGraph sg = SemanticGraphFactory.makeFromTree(tree, SemanticGraphFactory.Mode.BASIC,
        GrammaticalStructure.Extras.NONE, null, false, true);

    addLemmata(sg);

    if (USE_NAME) {
      addNERTags(sg);
    }
    return sg;
  }


  private static class TreeToSemanticGraphIterator implements Iterator<SemanticGraph> {

    private Iterator<Tree> treeIterator;
    private Tree currentTree; // = null;

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


  private static Morphology MORPH = new Morphology();

  private static void addLemmata(SemanticGraph sg) {
    sg.vertexListSorted().forEach(w -> {
      if(w.lemma() == null) {
        w.setLemma(MORPH.lemma(w.word(), w.tag()));
      }
    });
  }

  private static void addLemmata(Tree tree) {
    tree.yield().forEach(l-> {
      CoreLabel w = (CoreLabel) l;
      if(w.lemma() == null) {
        w.setLemma(MORPH.lemma(w.word(), w.tag()));
      }
    });
  }



  private static NERClassifierCombiner NER_TAGGER = null;


  private static void addNERTags(SemanticGraph sg) {
    if (NER_TAGGER == null) {
      NER_TAGGER = NERClassifierCombiner.createNERClassifierCombiner(null, new Properties());
    }

    List<CoreLabel> labels = sg.vertexListSorted().stream().map(IndexedWord::backingLabel).collect(Collectors.toList());
    NER_TAGGER.classify(labels);
  }

  private static void addNERTags(Tree tree) {
    if (NER_TAGGER == null) {
      NER_TAGGER = NERClassifierCombiner.createNERClassifierCombiner(null, new Properties());
    }

    List<CoreLabel> labels = tree.yield().stream().map(w -> (CoreLabel) w).collect(Collectors.toList());
    NER_TAGGER.classify(labels);
  }

  /**
   *
   * Converts a constituency tree to the English basic, enhanced, or
   * enhanced++ Universal dependencies representation, or an English basic
   * Universal dependencies tree to the enhanced or enhanced++ representation.
   *
   * Command-line options:<br>
   * {@code -treeFile}: File with PTB-formatted constituency trees<br>
   * {@code -conlluFile}: File with basic dependency trees in CoNLL-U format<br>
   * {@code -outputRepresentation}: "basic" (default), "enhanced", or "enhanced++"
   *
   */
  public static void main(String[] args) {
    Properties props = StringUtils.argsToProperties(args);

    String treeFileName = props.getProperty("treeFile");
    String conlluFileName = props.getProperty("conlluFile");
    String outputRepresentation = props.getProperty("outputRepresentation", "basic");

    Iterator<SemanticGraph> sgIterator; // = null;

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
      } else {
        addLemmata(sg);
        if (USE_NAME) {
          addNERTags(sg);
        }
      }

      if (outputRepresentation.equalsIgnoreCase("enhanced")) {
        sg = convertBasicToEnhanced(sg);
      } else if (outputRepresentation.equalsIgnoreCase("enhanced++")) {
        sg = convertBasicToEnhancedPlusPlus(sg);
      }
      System.out.print(writer.printSemanticGraph(sg));
    }

  }

}
