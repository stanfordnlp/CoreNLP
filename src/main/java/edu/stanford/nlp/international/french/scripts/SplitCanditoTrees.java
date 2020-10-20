package edu.stanford.nlp.international.french.scripts; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import edu.stanford.nlp.international.french.FrenchMorphoFeatureSpecification;
import edu.stanford.nlp.international.french.pipeline.FTBCorrector;
import edu.stanford.nlp.international.french.pipeline.MWEPreprocessor;
import edu.stanford.nlp.international.morph.MorphoFeatureSpecification;
import edu.stanford.nlp.international.morph.MorphoFeatures;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.international.french.FrenchXMLTreeReader;
import edu.stanford.nlp.trees.international.french.FrenchXMLTreeReaderFactory;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.util.Generics;

/**
 * Performs the pre-processing of raw (XML) FTB trees for the EMNLP2011 and CL2011 experiments.
 *
 * @author John Bauer
 * @author Spence Green
 *
 */
public final class SplitCanditoTrees  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(SplitCanditoTrees.class);

  /**
   * true -- mwetoolkit experiments, factored lexicon experiments
   * false -- basic parsing experiments
   */
  private static final boolean LEMMAS_AS_LEAVES = false;

  /**
   * true -- factored lexicon experiments
   * false -- mwetoolkit experiments, basic parsing experiments
   */
  private static final boolean ADD_MORPHO_TO_LEAVES = false;

  /**
   * true -- Use the CC tagset
   * false -- Use the default tagset
   */
  private static final boolean CC_TAGSET = true;

  /**
   * Output Morfette training files instead of PTB-style trees
   */
  private static final boolean MORFETTE_OUTPUT = false;

  /**
   * return -LRB- instead of (, -RRB- instead of )
   */
  private static final boolean ESCAPE_PARENS = true;
  
  
  // Statistics
  private static int nTokens = 0;
  private static int nMorphAnalyses = 0;
  
  private static final Integer[] fSizes = {1235,1235,9881,10000000};
  private static final String[] fNames = {"candito.test", "candito.dev",
                                  "candito.train",
                                  "candito.train.extended"};

  private SplitCanditoTrees() {} // static main method only

  static List<String> readIds(String filename)
    throws IOException
  {
    List<String> ids = new ArrayList<>();
    BufferedReader fin =
      new BufferedReader(new InputStreamReader
                         (new FileInputStream(filename), "ISO8859_1"));
    String line;
    while ((line = fin.readLine()) != null) {
      String[] pieces = line.split("\t");
      ids.add(pieces[0].trim());
    }
    return ids;
  }

  static Map<String, Tree> readTrees(String[] filenames)
    throws IOException
  {
    // TODO: perhaps we can just pass in CC_TAGSET and get rid of replacePOSTags
    // need to test that
    final TreeReaderFactory trf = new FrenchXMLTreeReaderFactory(false); 
    Map<String, Tree> treeMap = Generics.newHashMap();
    for (String filename : filenames) {
      File file = new File(filename);
      String canonicalFilename =
        file.getName().substring(0, file.getName().lastIndexOf('.'));

      FrenchXMLTreeReader tr = (FrenchXMLTreeReader)
        trf.newTreeReader(new BufferedReader
                          (new InputStreamReader
                           (new FileInputStream(file),"ISO8859_1")));

      Tree t = null;
      int numTrees;
      for (numTrees = 0; (t = tr.readTree()) != null; numTrees++) {
        String id = canonicalFilename + "-" + ((CoreLabel) t.label()).get(CoreAnnotations.SentenceIDAnnotation.class);
        treeMap.put(id, t);
      }

      tr.close();
      System.err.printf("%s: %d trees%n", file.getName(), numTrees);
    }
    return treeMap;
  }

  static void preprocessMWEs(Map<String, Tree> treeMap) {

    TwoDimensionalCounter<String,String> labelTerm =
            new TwoDimensionalCounter<>();
    TwoDimensionalCounter<String,String> termLabel =
            new TwoDimensionalCounter<>();
    TwoDimensionalCounter<String,String> labelPreterm =
            new TwoDimensionalCounter<>();
    TwoDimensionalCounter<String,String> pretermLabel =
            new TwoDimensionalCounter<>();

    TwoDimensionalCounter<String,String> unigramTagger =
            new TwoDimensionalCounter<>();

    for (Tree t : treeMap.values()) {
      MWEPreprocessor.countMWEStatistics(t, unigramTagger,
                                         labelPreterm, pretermLabel,
                                         labelTerm, termLabel);
    }

    for (Tree t : treeMap.values()) {
      MWEPreprocessor.traverseAndFix(t, pretermLabel, unigramTagger);
    }
  }

  public static void mungeLeaves(Tree tree, boolean lemmasAsLeaves, boolean addMorphoToLeaves) {
    List<Label> labels = tree.yield();
    
    for (Label label : labels) {
      ++nTokens;
      if (!(label instanceof CoreLabel)) {
        throw new IllegalArgumentException("Only works with CoreLabels trees");
      }

      CoreLabel coreLabel = (CoreLabel) label;
      String lemma = coreLabel.lemma();
      //PTB escaping since we're going to put this in the leaf
      if (lemma == null) {
        // No lemma, so just add the surface form
        lemma = coreLabel.word();
      } else if (ESCAPE_PARENS && lemma.equals("(")) {
        lemma = "-LRB-";
      } else if (ESCAPE_PARENS && lemma.equals(")")) {
        lemma = "-RRB-";
      }

      if (lemmasAsLeaves) {
        String escapedLemma = lemma;
        coreLabel.setWord(escapedLemma);
        coreLabel.setValue(escapedLemma);
        coreLabel.setLemma(lemma);
      }

      if (addMorphoToLeaves) {
        String morphStr = coreLabel.originalText();
        if(morphStr == null || morphStr.equals("")) {
          morphStr = MorphoFeatureSpecification.NO_ANALYSIS;
        } else {
          ++nMorphAnalyses;
        }
        // Normalize punctuation analyses
        if (morphStr.startsWith("PONCT")) {
          morphStr = "PUNC";
        }
        
        String newLeaf = String.format("%s%s%s%s%s", coreLabel.value(),
                                                   MorphoFeatureSpecification.MORPHO_MARK,
                                                   lemma,
                                                   MorphoFeatureSpecification.LEMMA_MARK,
                                                   morphStr);
        coreLabel.setValue(newLeaf);
        coreLabel.setWord(newLeaf);
      }
    }
  }

  private static void replacePOSTags(Tree tree) {
    List<Label> yield = tree.yield();
    List<Label> preYield = tree.preTerminalYield();

    assert yield.size() == preYield.size();

    MorphoFeatureSpecification spec = new FrenchMorphoFeatureSpecification();
    for(int i = 0; i < yield.size(); i++) {
      // Morphological Analysis
      String morphStr = ((CoreLabel) yield.get(i)).originalText();
      if (morphStr == null || morphStr.equals("")) {
        morphStr = preYield.get(i).value();
        // POS subcategory
        String subCat = ((CoreLabel) yield.get(i)).category();
        if (subCat != null && subCat != "") {
          morphStr += "-" + subCat + "--";
        } else {
          morphStr += "---";
        }
      }
      MorphoFeatures feats = spec.strToFeatures(morphStr);
      if(feats.getAltTag() != null && !feats.getAltTag().equals("")) {
        CoreLabel cl = (CoreLabel) preYield.get(i);
        cl.setValue(feats.getAltTag());
        cl.setTag(feats.getAltTag());
      }
    }
  }

  /**
   * Right now this outputs trees in PTB format.  It outputs one tree
   * at a time until we have output enough trees to fill the given
   * file, then moves on to the next file.  Trees are output in the
   * order given in the <code>ids</code> list.
   * <br>
   * Trees have their words replaced with the words' lemmas, if those
   * lemmas exist.
   */
  public static void outputSplits(List<String> ids,
                                  Map<String, Tree> treeMap)
    throws IOException
  {
    Queue<Integer> fSizeQueue = new LinkedList<>(Arrays.asList(fSizes));
    Queue<String> fNameQueue = new LinkedList<>(Arrays.asList(fNames));

    TregexPattern pBadTree = TregexPattern.compile("@SENT <: @PUNC");
    TregexPattern pBadTree2 = TregexPattern.compile("@SENT <1 @PUNC <2 @PUNC !<3 __");
    
    final TreeTransformer tt = new FTBCorrector();

    int size = fSizeQueue.remove();
    String filename = fNameQueue.remove();

    log.info("Outputing " + filename);

    PrintWriter writer =
      new PrintWriter(new BufferedWriter
                      (new OutputStreamWriter
                       (new FileOutputStream(filename), "UTF-8")));

    int outputCount = 0;
    for (String id : ids) {
      if (!treeMap.containsKey(id)) {
        log.info("Missing id: " + id);
        continue;
      }

      Tree tree = treeMap.get(id);
      TregexMatcher m = pBadTree.matcher(tree);
      TregexMatcher m2 = pBadTree2.matcher(tree);
      if(m.find() || m2.find()) {
        log.info("Discarding tree: " + tree.toString());
        continue;
      }
      
      // Punctuation normalization, etc.
      Tree backupCopy = tree.deepCopy();
      tree = tt.transformTree(tree);
      if (tree.firstChild().children().length == 0) {
        // Some trees have only punctuation. Tregex will mangle these. Don't throw those away.
        log.info("Saving tree: " + tree.toString());
        log.info("Backup: " + backupCopy.toString());
        tree = backupCopy;
      }
      
      if(LEMMAS_AS_LEAVES || ADD_MORPHO_TO_LEAVES) {
        mungeLeaves(tree,LEMMAS_AS_LEAVES,ADD_MORPHO_TO_LEAVES);
      }

      if(CC_TAGSET) {
        replacePOSTags(tree);
      }

      if (MORFETTE_OUTPUT) {
        writer.println(treeToMorfette(tree));
      } else {
        writer.println(tree.toString());
      }

      ++outputCount;
      if (outputCount == size) {
        outputCount = 0;
        size = fSizeQueue.remove();
        filename = fNameQueue.remove();
        log.info("Outputing " + filename);
        writer.close();
        writer =
          new PrintWriter(new BufferedWriter
                          (new OutputStreamWriter
                           (new FileOutputStream(filename), "UTF-8")));
      }
    }
    writer.close();
  }

  /**
   * Converts a tree to the Morfette training format.
   */
  private static String treeToMorfette(Tree tree) {
    StringBuilder sb = new StringBuilder();
    List<Label> yield = tree.yield();
    List<Label> tagYield = tree.preTerminalYield();
    assert yield.size() == tagYield.size();
    int listLen = yield.size();
    for (int i = 0; i < listLen; ++i) {
      CoreLabel token = (CoreLabel) yield.get(i);
      CoreLabel tag = (CoreLabel) tagYield.get(i);
      String morphStr = token.originalText();
      if (morphStr == null || morphStr.equals("")) {
        morphStr = tag.value();
      }
      String lemma = token.lemma();
      if (lemma == null || lemma.equals("")) {
        lemma = token.value();
      }
      sb.append(String.format("%s %s %s%n", token.value(), lemma, morphStr));
    }
    return sb.toString();
  }

  /**
   * Sample command line:
   * <br>
   * java edu.stanford.nlp.international.french.scripts.SplitCanditoTrees
   * projects/core/src/edu/stanford/nlp/international/french/pipeline/splits/ftb-uc-2010.id_mrg
   * ../data/french/corpus-fonctions/*.xml
   */
  public static void main(String[] args) throws IOException {
    if (args.length < 2) {
      System.err.printf("Usage: java %s id_file [xml files]%n", SplitCanditoTrees.class.getName());
      System.exit(-1);
    }
    // first arg is expected to be the file of IDs
    // all subsequent args are .xml files with the trees in them
    List<String> ids = readIds(args[0]);

    log.info("Read " + ids.size() + " ids");

    String[] newArgs = new String[args.length - 1];
    for (int i = 1; i < args.length; ++i)
      newArgs[i - 1] = args[i];

    Map<String, Tree> treeMap = readTrees(newArgs);
    log.info("Read " + treeMap.size() + " trees");

    preprocessMWEs(treeMap);

    outputSplits(ids, treeMap);
    
    if (nTokens != 0) {
      log.info("CORPUS STATISTICS");
      System.err.printf("#tokens:\t%d%n", nTokens);
      System.err.printf("#with morph:\t%d%n", nMorphAnalyses);
    }
  }
}
