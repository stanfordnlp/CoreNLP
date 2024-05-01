package edu.stanford.nlp.trees.ud;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.treebank.EnglishPTBTreebankCorrector;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

import java.lang.reflect.*;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.stream.Collectors;


/**
 * Command-line utility to:
 *
 * a) convert constituency trees to basic English UD trees;
 * b) convert basic dependency trees to enhanced and enhanced++ UD graphs.
 *
 * @author Sebastian Schuster
 */
public class UniversalDependenciesConverter {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(UniversalDependenciesConverter.class);

  private static final String NER_COMBINER_NAME = "edu.stanford.nlp.ie.NERClassifierCombiner";

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
   */
  private static SemanticGraph convertBasicToEnhanced(SemanticGraph sg) {
    GrammaticalStructure gs = semanticGraphToGrammaticalStructure(sg);
    return SemanticGraphFactory.generateEnhancedDependencies(gs);
  }

  /**
   * Converts basic UD tree to enhanced++ UD graph.
   */
  private static SemanticGraph convertBasicToEnhancedPlusPlus(SemanticGraph sg) {
    GrammaticalStructure gs = semanticGraphToGrammaticalStructure(sg);
    return SemanticGraphFactory.generateEnhancedPlusPlusDependencies(gs);
  }

  private static SemanticGraph convertTreeToBasic(Tree tree) {
    addLemmata(tree);
    if (USE_NAME) {
      addNERTags(tree);
    }
    SemanticGraph sg = SemanticGraphFactory.makeFromTree(tree, SemanticGraphFactory.Mode.BASIC,
        GrammaticalStructure.Extras.NONE, null, false, true);

    addLemmata(sg);

    if (USE_NAME) {
      addNERTags(sg);
    }
    return sg;
  }


  private static class TreeToSemanticGraphIterator implements Iterator<Pair<SemanticGraph, SemanticGraph>> {

    private Iterator<Tree> treeIterator;
    private Tree currentTree; // = null;

    private TreeTransformer corrector; // = null;

    public TreeToSemanticGraphIterator(Iterator<Tree> treeIterator, TreeTransformer corrector) {
      this.treeIterator = treeIterator;
      this.corrector = corrector;
    }

    @Override
    public boolean hasNext() {
      return treeIterator.hasNext();
    }

    @Override
    public Pair<SemanticGraph, SemanticGraph> next() {
      Tree t = treeIterator.next();
      if (corrector != null) {
        t = corrector.transformTree(t);
        // The corrector uses tsurgeon, with two limitations:
        //   - adjoin nodes don't set word(), just set value()
        //   - rearranging tags doesn't update the tag() of a leaf
        List<Tree> preterminals = Trees.preTerminals(t);
        for (Tree preterminal : preterminals) {
          assert preterminal.children().length == 1;
          Tree leaf = preterminal.children()[0];
          if (!(leaf.label() instanceof CoreLabel)) {
            throw new RuntimeException("These should all be CoreLabels!");
          }
          CoreLabel leafWord = (CoreLabel) leaf.label();
          if (leafWord.word() == null && leafWord.value() != null) {
            leafWord.setWord(leafWord.value());
          }
          leafWord.setTag(preterminal.value());
        }
      }
      currentTree = t;
      return new Pair<>(convertTreeToBasic(t), null);
    }

    public Tree getCurrentTree() {
      return this.currentTree;
    }

  } // end static class TreeToSemanticGraphIterator


  private static Morphology MORPH = new Morphology();

  private static void replaceAllLemmata(SemanticGraph sg) {
    sg.vertexListSorted().forEach(w -> {
        w.setLemma(MORPH.lemma(w.word(), w.tag()));
    });
  }

  private static void addLemmata(SemanticGraph sg) {
    sg.vertexListSorted().forEach(w -> {
      if(w.lemma() == null) {
        w.setLemma(MORPH.lemma(w.word(), w.tag()));
      }
    });
  }

  private static void addLemmata(Tree tree) {
    tree.yield().forEach(l -> {
      CoreLabel w = (CoreLabel) l;
      if(w.lemma() == null) {
        w.setLemma(MORPH.lemma(w.word(), w.tag()));
      }
    });
  }

  /** variables for accessing NERClassifierCombiner via reflection **/
  private static Object NER_TAGGER; // = null;
  private static Method NER_CLASSIFY_METHOD; // = null;

  /** Try to set up the NER tagger. **/
  @SuppressWarnings("unchecked")
  private static void setupNERTagger() {
    Class NER_TAGGER_CLASS;
    try {
      NER_TAGGER_CLASS = Class.forName(NER_COMBINER_NAME);
    } catch (Exception ex) {
      log.warn(  NER_COMBINER_NAME + " not found - not applying NER tags!");
      return;
    }
    try {
      Method createMethod = NER_TAGGER_CLASS.getDeclaredMethod("createNERClassifierCombiner",
                  String.class, Properties.class);
      NER_TAGGER = createMethod.invoke(null, null, new Properties());
      NER_CLASSIFY_METHOD = NER_TAGGER_CLASS.getDeclaredMethod("classify", List.class);
    } catch (Exception ex) {
      log.warn("Error setting up " + NER_COMBINER_NAME + "! Not applying NER tags!");
    }
  }

  /** Add NER tags to a semantic graph. **/
  private static void addNERTags(SemanticGraph sg) {
    // set up tagger if necessary
    if (NER_TAGGER == null || NER_CLASSIFY_METHOD == null) {
      setupNERTagger();
    }
    if (NER_TAGGER != null && NER_CLASSIFY_METHOD != null) {
      // we have everything successfully setup and so can act.
      try {
        // classify
        List<CoreLabel> labels =
            sg.vertexListSorted().stream().map(IndexedWord::backingLabel).collect(Collectors.toList());
        NER_CLASSIFY_METHOD.invoke(NER_TAGGER, labels);
      } catch (Exception ex) {
        log.warn("Error running " + NER_COMBINER_NAME + " on SemanticGraph!  Not applying NER tags!");
      }
    }
  }

  /** Add NER tags to a tree. **/
  private static void addNERTags(Tree tree) {
    // set up tagger if necessary
    if (NER_TAGGER == null || NER_CLASSIFY_METHOD == null) {
      setupNERTagger();
    }
    if (NER_TAGGER != null && NER_CLASSIFY_METHOD != null) {
      // we have everything successfully setup and so can act.
      try {
        // classify
        List<CoreLabel> labels = tree.yield().stream().map(w -> (CoreLabel) w).collect(Collectors.toList());
        NER_CLASSIFY_METHOD.invoke(NER_TAGGER, labels);
      } catch (Exception ex) {
        log.warn("Error running " + NER_COMBINER_NAME + " on Tree!  Not applying NER tags!");
      }
    }
  }

  /**
   * Break up a provided text input to match a tree's words,
   * using the whitespace between the words to mark the AfterAnnotation.
   * We assume a blank space at the end of the sentence
   */
  private static void addSpaceAfter(SemanticGraph sg, String text, int graphIdx) {
    List<IndexedWord> tokens = sg.vertexListSorted();
    int pos = tokens.get(0).word().length();
    for (int i = 1; i < tokens.size(); ++i) {
      String word = tokens.get(i).word();
      int nextPos = text.indexOf(word, pos);
      if (nextPos < 0) {
        throw new RuntimeException("Cannot find word " + word + " in the text of sentence " + graphIdx + "\n" + text);
      }
      tokens.get(i-1).setAfter(text.substring(pos, nextPos));
      pos = nextPos + word.length();
    }
    tokens.get(tokens.size() - 1).setAfter(" ");
  }

  /**
   * Converts a constituency tree to the English basic, enhanced, or
   * enhanced++ Universal dependencies representation, or an English basic
   * Universal dependencies tree to the enhanced or enhanced++ representation.
   * <p>
   * Command-line options:<br>
   * {@code -treeFile}: File with PTB-formatted constituency trees<br>
   * {@code -conlluFile}: File with basic dependency trees in CoNLL-U format<br>
   * {@code -textFile}: A file with text to be used as a guide for SpaceAfter (optional)<br>
   * {@code -outputRepresentation}: "basic" (default), "enhanced", or "enhanced++"<br>
   * {@code -combineMWTs}: "False" (default), "True" marks things like it's as MWT
   * {@code -correctPTB}: "False" (default), "True" runs the PTB Corrector over the trees
   */
  public static void main(String[] args) {
    Properties props = StringUtils.argsToProperties(args);

    String treeFileName = props.getProperty("treeFile");
    String conlluFileName = props.getProperty("conlluFile");
    String outputRepresentation = props.getProperty("outputRepresentation", "basic");
    boolean addFeatures = PropertiesUtils.getBool(props, "addFeatures", false);
    boolean combineMWTs = PropertiesUtils.getBool(props, "combineMWTs", false);
    boolean replaceLemmata = PropertiesUtils.getBool(props, "replaceLemmata", false);
    boolean correctPTB = PropertiesUtils.getBool(props, "correctPTB", false);

    Iterator<Pair<SemanticGraph, SemanticGraph>> sgIterator; // = null;

    if (treeFileName != null) {
      NPTmpRetainingTreeNormalizer normalizer = new NPTmpRetainingTreeNormalizer(0, false, 1, false, true);
      MemoryTreebank tb = new MemoryTreebank(normalizer);
      tb.loadPath(treeFileName);
      Iterator<Tree> treeIterator = tb.iterator();
      TreeTransformer ptbCorrector = null;
      if (correctPTB) {
        ptbCorrector = new CompositeTreeTransformer(new EnglishPTBTreebankCorrector(), normalizer);
      }
      sgIterator = new TreeToSemanticGraphIterator(treeIterator, ptbCorrector);
    } else if (conlluFileName != null) {
      CoNLLUDocumentReader reader = new CoNLLUDocumentReader();
      try {
        sgIterator = reader.getIterator(IOUtils.readerFromString(conlluFileName));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    } else {
      System.err.println("No input file specified!");
      System.err.println();
      System.err.printf("Usage: java %s [-treeFile trees.tree | -conlluFile deptrees.conllu]" +
                        " [-addFeatures] [-replaceLemmata] [-correctPTB] [-textFile trees.txt] [-outputRepresentation basic|enhanced|enhanced++ (default: basic)]%n",
                        UniversalDependenciesConverter.class.getCanonicalName());
      return;
    }

    Iterator<String> textIterator = null;
    String textFileName = props.getProperty("textFile");
    if (textFileName != null) {
      textIterator = IOUtils.readLines(textFileName).iterator();
    }

    UniversalDependenciesFeatureAnnotator featureAnnotator = (addFeatures) ? new UniversalDependenciesFeatureAnnotator() : null;
    EnglishMWTCombiner mwtCombiner = (combineMWTs) ? new EnglishMWTCombiner() : null;

    CoNLLUDocumentWriter writer = new CoNLLUDocumentWriter();

    int graphIdx = 0;
    while (sgIterator.hasNext()) {
      final Pair<SemanticGraph, SemanticGraph> sgs = sgIterator.next();
      SemanticGraph sg = sgs.first();

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

        if (featureAnnotator != null) {
          featureAnnotator.addFeatures(sg, tree, false, false);
        }

        if (mwtCombiner != null) {
          sg = mwtCombiner.combineMWTs(sg);
        }
      } else {
        if (replaceLemmata) {
          replaceAllLemmata(sg);
        } else {
          addLemmata(sg);
        }
        if (USE_NAME) {
          addNERTags(sg);
        }

        if (featureAnnotator != null) {
          featureAnnotator.addFeatures(sg, null, false, false);
        }
      }

      SemanticGraph enhanced = null;
      if (outputRepresentation.equalsIgnoreCase("enhanced")) {
        enhanced = convertBasicToEnhanced(sg);
      } else if (outputRepresentation.equalsIgnoreCase("enhanced++")) {
        enhanced = convertBasicToEnhancedPlusPlus(sg);
      }
      if (textIterator != null) {
        String text = "";
        while (text.equals("")) {
          try {
            text = textIterator.next().trim();
          } catch (NoSuchElementException e) {
            throw new RuntimeException("Processed " + graphIdx + " trees, but there are more trees and text is empty", e);
          }
        }
        addSpaceAfter(sg, text, graphIdx);
      }
      System.out.println("# sent_id = " + graphIdx);
      System.out.print(writer.printSemanticGraph(sg, enhanced));
      ++graphIdx;
    }

  }

}
