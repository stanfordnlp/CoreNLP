package edu.stanford.nlp.international.french.pipeline; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.trees.treebank.AbstractDataset;
import edu.stanford.nlp.trees.treebank.ConfigParser;
import edu.stanford.nlp.trees.treebank.DefaultMapper;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.international.arabic.ATBTreeUtils;
import edu.stanford.nlp.trees.international.french.FrenchTreebankLanguagePack;
import edu.stanford.nlp.trees.international.french.FrenchXMLTreeReaderFactory;
import edu.stanford.nlp.trees.tregex.TregexParseException;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.util.DataFilePaths;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;

/**
 * Produces the pre-processed version of the FTB used in the experiments of
 * Green et al. (2011).
 *
 * @author Spence Green
 *
 */
public class FTBDataset extends AbstractDataset  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(FTBDataset.class);

  private boolean CC_TAGSET = false;

  private Set<String> splitSet;

  public FTBDataset() {
    super();

    //Need to use a MemoryTreebank so that we can compute gross corpus
    //stats for MWE pre-processing
    // The treebank may be reset if setOptions changes CC_TAGSET
    treebank = new MemoryTreebank(new FrenchXMLTreeReaderFactory(CC_TAGSET), FrenchTreebankLanguagePack.FTB_ENCODING);
    treeFileExtension = "xml";
  }

  /**
   * Return the ID of this tree according to the Candito split files.
   */
  private String getCanditoTreeID(Tree t) {
    String canditoName = null;
    if (t.label() instanceof CoreLabel) {
      String fileName = ((CoreLabel) t.label()).docID();
      fileName = fileName.substring(0, fileName.lastIndexOf('.'));
      String ftbID = ((CoreLabel) t.label()).get(CoreAnnotations.SentenceIDAnnotation.class);
      if (fileName != null && ftbID != null) {
        canditoName = fileName + "-" + ftbID;
      } else {
        throw new NullPointerException("fileName " + fileName + ", ftbID " + ftbID);
      }
    } else {
      throw new IllegalArgumentException("Trees constructed without CoreLabels! Can't extract metadata!");
    }
    return canditoName;
  }

  @Override
  public void build() {
    for(File path : pathsToData) {
      treebank.loadPath(path,treeFileExtension,false);
    }

    PrintWriter outfile = null;
    PrintWriter flatFile = null;
    try {
      outfile = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFileName),"UTF-8")));
      flatFile = (makeFlatFile) ? new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(flatFileName),"UTF-8"))) : null;

      outputFileList.add(outFileName);

      if(makeFlatFile) {
        outputFileList.add(flatFileName);
        toStringBuilder.append(" Made flat files\n");
      }

      preprocessMWEs();

      List<TregexPattern> badTrees = new ArrayList<>();
      //These trees appear in the Candito training set
      //They are mangled by the TreeCorrector, so discard them ahead of time.
      badTrees.add(TregexPattern.compile("@SENT <: @PUNC"));
      badTrees.add(TregexPattern.compile("@SENT <1 @PUNC <2 @PUNC !<3 __"));

      //wsg2011: This filters out tree #552 in the Candito test set. We saved this tree for the
      //EMNLP2011 paper, but since it consists entirely of punctuation, it won't be evaluated anyway.
      //Since we aren't doing the split in this data set, just remove the tree.
      badTrees.add(TregexPattern.compile("@SENT <1 @PUNC <2 @PUNC <3 @PUNC <4 @PUNC !<5 __"));

      for(Tree t : treebank) {
        //Filter out bad trees
        boolean skipTree = false;
        for(TregexPattern p : badTrees) {
          skipTree = p.matcher(t).find();
          if(skipTree) break;
        }
        if(skipTree) {
          log.info("Discarding tree: " + t.toString());
          continue;
        }

        // Filter out trees that aren't in this part of the split
        if (splitSet != null) {
          String canditoTreeID = getCanditoTreeID(t);
          if ( ! splitSet.contains(canditoTreeID)) {
            continue;
          }
        }

        if(customTreeVisitor != null)
          customTreeVisitor.visitTree(t);

        // outfile.printf("%s\t%s%n",treeName,t.toString());
        outfile.println(t.toString());

        if(makeFlatFile) {
          String flatString = (removeEscapeTokens) ?
              ATBTreeUtils.unEscape(ATBTreeUtils.flattenTree(t)) : ATBTreeUtils.flattenTree(t);
              flatFile.println(flatString);
        }
      }

    } catch (UnsupportedEncodingException e) {
      System.err.printf("%s: Filesystem does not support UTF-8 output%n", this.getClass().getName());
      e.printStackTrace();

    } catch (FileNotFoundException e) {
      System.err.printf("%s: Could not open %s for writing%n", this.getClass().getName(), outFileName);

    } catch (TregexParseException e) {
      System.err.printf("%s: Could not compile Tregex expressions%n", this.getClass().getName());
      e.printStackTrace();

    } finally {
      if(outfile != null)
        outfile.close();
      if(flatFile != null)
        flatFile.close();
    }
  }

  /**
   * Corrects MWE annotations that lack internal POS labels.
   */
  private void preprocessMWEs() {

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

    for (Tree t : treebank) {
      MWEPreprocessor.countMWEStatistics(t, unigramTagger,
          labelPreterm, pretermLabel,
          labelTerm, termLabel);
    }

    for (Tree t : treebank) {
      MWEPreprocessor.traverseAndFix(t, pretermLabel, unigramTagger);
    }
  }


  @Override
  public boolean setOptions(Properties opts) {
    boolean ret = super.setOptions(opts);

    if (opts.containsKey(ConfigParser.paramSplit)) {
      String splitFileName = opts.getProperty(ConfigParser.paramSplit);
      splitSet = makeSplitSet(splitFileName);
    }

    CC_TAGSET = PropertiesUtils.getBool(opts, ConfigParser.paramCCTagset, false);
    treebank = new MemoryTreebank(new FrenchXMLTreeReaderFactory(CC_TAGSET), FrenchTreebankLanguagePack.FTB_ENCODING);

    if(lexMapper == null) {
      lexMapper = new DefaultMapper();
      lexMapper.setup(null, lexMapOptions.split(","));
    }

    if(pathsToMappings.size() != 0) {
      if(posMapper == null)
        posMapper = new DefaultMapper();
      for(File path : pathsToMappings)
        posMapper.setup(path);
    }

    return ret;
  }

  private Set<String> makeSplitSet(String splitFileName) {
    splitFileName = DataFilePaths.convert(splitFileName);
    Set<String> splitSet = Generics.newHashSet();
    LineNumberReader reader = null;
    try {
      reader = new LineNumberReader(new FileReader(splitFileName));
      for (String line; (line = reader.readLine()) != null;) {
        splitSet.add(line.trim());
      }
      reader.close();

    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      System.err.printf("%s: Error reading %s (line %d)%n", this.getClass().getName(), splitFileName, reader.getLineNumber());
      e.printStackTrace();
    }
    return splitSet;
  }
}
