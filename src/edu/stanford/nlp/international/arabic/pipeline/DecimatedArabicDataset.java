package edu.stanford.nlp.international.arabic.pipeline; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.trees.treebank.ConfigParser;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.international.arabic.ATBTreeUtils;
import edu.stanford.nlp.util.Generics;

/**
 * Decimates a set of ATB parse trees. For every 10 parse trees, eight are added to the training set, and one
 * is added to each of the dev and test sets.
 *
 * @author Spence Green
 *
 */
public class DecimatedArabicDataset extends ATBArabicDataset  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(DecimatedArabicDataset.class);

  private boolean taggedOutput = false;
  private String wordTagDelim = "_";

  @Override
  public void build() {
    //Set specific options for this dataset
    if(options.containsKey(ConfigParser.paramSplit)) {
      System.err.printf("%s: Ignoring split parameter for this dataset type\n", this.getClass().getName());
    } else if(options.containsKey(ConfigParser.paramTagDelim)) {
      wordTagDelim = options.getProperty(ConfigParser.paramTagDelim);
      taggedOutput = true;
    }

    for(File path : pathsToData) {
      int prevSize = treebank.size();
      treebank.loadPath(path,treeFileExtension,false);

      toStringBuilder.append(String.format(" Loaded %d trees from %s\n", treebank.size() - prevSize, path.getPath()));
      prevSize = treebank.size();
    }

    ArabicTreeDecimatedNormalizer tv = new ArabicTreeDecimatedNormalizer(outFileName,makeFlatFile,taggedOutput);

    treebank.apply(tv);

    outputFileList.addAll(tv.getFilenames());

    tv.closeOutputFiles();
  }

  public class ArabicTreeDecimatedNormalizer extends ArabicRawTreeNormalizer {

    private int treesVisited = 0;
    private final String trainExtension = ".train";
    private final String testExtension = ".test";
    private final String devExtension = ".dev";
    private final String flatExtension = ".flat";

    private boolean makeFlatFile = false;

    private boolean taggedOutput = false;

    private Map<String,String> outFilenames;
    private Map<String,PrintWriter> outFiles;

    public ArabicTreeDecimatedNormalizer(String filePrefix, boolean makeFlat, boolean makeTagged) {
    	super(null,null);

      makeFlatFile = makeFlat;
      taggedOutput = makeTagged;

      //Setup the decimation output files
      outFilenames = Generics.newHashMap();
      outFilenames.put(trainExtension, filePrefix + trainExtension);
      outFilenames.put(testExtension, filePrefix + testExtension);
      outFilenames.put(devExtension, filePrefix + devExtension);

      if(makeFlatFile) {
        outFilenames.put(trainExtension + flatExtension,filePrefix + trainExtension + flatExtension);
        outFilenames.put(testExtension + flatExtension,filePrefix + testExtension + flatExtension);
        outFilenames.put(devExtension + flatExtension,filePrefix + devExtension + flatExtension);
      }

      setupOutputFiles();
    }

    private void setupOutputFiles() {
      PrintWriter outfile = null;
      String curOutFileName = "";
      try {

        outFiles = Generics.newHashMap();

        for(String keyForFile : outFilenames.keySet()) {

          curOutFileName = outFilenames.get(keyForFile);

          if(!makeFlatFile && curOutFileName.contains(flatExtension)) continue;

          outfile = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(curOutFileName),"UTF-8")));
          outFiles.put(keyForFile, outfile);
        }

      } catch (UnsupportedEncodingException e) {
        System.err.printf("%s: Filesystem does not support UTF-8 output\n", this.getClass().getName());
        e.printStackTrace();
      } catch (FileNotFoundException e) {
        System.err.printf("%s: Could not open %s for writing\n", this.getClass().getName(), curOutFileName);
      }
    }

    public void closeOutputFiles() {
      for(String keyForFile : outFiles.keySet())
        outFiles.get(keyForFile).close();
    }

    public void visitTree(Tree t) {
      if(t == null || t.value().equals("X")) return;

      t = t.prune(nullFilter, new LabeledScoredTreeFactory());

      //Do *not* strip traces here. The ArabicTreeReader will do that if needed
      for(Tree node : t)
        if(node.isPreTerminal())
          processPreterminal(node);

      treesVisited++;

      String flatString = (makeFlatFile) ? ATBTreeUtils.flattenTree(t) : null;

      //Do the decimation
      if(treesVisited % 9 == 0) {
        write(t, outFiles.get(devExtension));
        if(makeFlatFile) outFiles.get(devExtension + flatExtension).println(flatString);
      } else if(treesVisited % 10 == 0) {
        write(t, outFiles.get(testExtension));
        if(makeFlatFile) outFiles.get(testExtension + flatExtension).println(flatString);
      } else {
        write(t, outFiles.get(trainExtension));
        if(makeFlatFile) outFiles.get(trainExtension + flatExtension).println(flatString);
      }
    }

    private void write(Tree t, PrintWriter pw) {
      if(taggedOutput)
        pw.println(ATBTreeUtils.taggedStringFromTree(t, removeEscapeTokens, wordTagDelim));
      else
        t.pennPrint(pw);
    }

    public List<String> getFilenames() {
      List<String> filenames = new ArrayList<>();
      for(String keyForFile : outFilenames.keySet())
        filenames.add(outFilenames.get(keyForFile));

      return filenames;
    }
  }


}
