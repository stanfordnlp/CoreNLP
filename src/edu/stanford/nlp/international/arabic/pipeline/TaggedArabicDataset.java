package edu.stanford.nlp.international.arabic.pipeline; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

import edu.stanford.nlp.trees.treebank.ConfigParser;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.international.arabic.ATBTreeUtils;

/**
 * Converts ATB gold parse trees to a format appropriate for training a POS tagger (especially
 * the Stanford POS tagger!).
 *
 * @author Spence Green
 *
 */
public class TaggedArabicDataset extends ATBArabicDataset  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(TaggedArabicDataset.class);

  private String wordTagDelim = "_";

  @Override
  public void build() {
    //Set specific options for this dataset
    if(options.containsKey(ConfigParser.paramTagDelim)) {
      wordTagDelim = options.getProperty(ConfigParser.paramTagDelim);
    }

    for(File path : pathsToData) {
      int prevSize = treebank.size();
      if(splitFilter == null) {
        treebank.loadPath(path,treeFileExtension,false);
      } else {
        treebank.loadPath(path,splitFilter);
      }
      toStringBuilder.append(String.format(" Loaded %d trees from %s\n", treebank.size() - prevSize, path.getPath()));
      prevSize = treebank.size();
    }

    PrintWriter outfile = null;
    PrintWriter flatFile = null;
    try {
      outfile = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFileName),"UTF-8")));
      flatFile = (makeFlatFile) ? new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(flatFileName),"UTF-8"))) : null;

      ArabicTreeTaggedNormalizer tv = new ArabicTreeTaggedNormalizer(outfile,flatFile);

      treebank.apply(tv);

      outputFileList.add(outFileName);

      if(makeFlatFile) {
        outputFileList.add(flatFileName);
      }

    } catch (UnsupportedEncodingException e) {
      System.err.printf("%s: Filesystem does not support UTF-8 output%n", this.getClass().getName());
      e.printStackTrace();
    } catch (FileNotFoundException e) {
      System.err.printf("%s: Could not open %s for writing%n", this.getClass().getName(), outFileName);
    } finally {
      if(outfile != null) {
        outfile.close();
      }
      if(flatFile != null) {
        flatFile.close();
      }
    }
  }

  protected class ArabicTreeTaggedNormalizer extends ArabicRawTreeNormalizer {

    public ArabicTreeTaggedNormalizer(PrintWriter outFile, PrintWriter flatFile) {
      super(outFile,flatFile);
    }

    public void visitTree(Tree t) {
      if(t == null || t.value().equals("X")) return;

      t = t.prune(nullFilter, new LabeledScoredTreeFactory());

      for(Tree node : t) {
        if(node.isPreTerminal()) {
          processPreterminal(node);
        }
      }

      outfile.println(ATBTreeUtils.taggedStringFromTree(t, removeEscapeTokens, wordTagDelim));

      if(flatFile != null) {
        flatFile.println(ATBTreeUtils.flattenTree(t));
      }
    }
  }
}
