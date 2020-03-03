package edu.stanford.nlp.international.arabic.pipeline; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.util.regex.*;

import edu.stanford.nlp.trees.Tree;

public class LabeledATBDataset extends ATBArabicDataset  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(LabeledATBDataset.class);

  @Override
  public void build() {
    for(File path : pathsToData) {
      int prevSize = treebank.size();
      if(splitFilter == null)
        treebank.loadPath(path,treeFileExtension,false);
      else
        treebank.loadPath(path,splitFilter);

      toStringBuilder.append(String.format(" Loaded %d trees from %s\n", treebank.size() - prevSize, path.getPath()));
    }

    PrintWriter outfile = null;
    PrintWriter flatFile = null;
    try {
      outfile = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFileName),"UTF-8")));
      flatFile = (makeFlatFile) ? new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(flatFileName),"UTF-8"))) : null;

      ArabicRawTreeNormalizer tv = new LabelingTreeNormalizer(outfile,flatFile);

      treebank.apply(tv);

      outputFileList.add(outFileName);

      if(makeFlatFile) {
        outputFileList.add(flatFileName);
        toStringBuilder.append(" Made flat files\n");
      }

    } catch (UnsupportedEncodingException e) {
      System.err.printf("%s: Filesystem does not support UTF-8 output\n", this.getClass().getName());
      e.printStackTrace();
    } catch (FileNotFoundException e) {
      System.err.printf("%s: Could not open %s for writing\n", this.getClass().getName(), outFileName);
    } finally {
      if(outfile != null)
        outfile.close();
      if(flatFile != null)
        flatFile.close();
    }
  }
  
  protected class LabelingTreeNormalizer extends ArabicRawTreeNormalizer {
    
    private final Pattern leftClitic;
    private final Pattern rightClitic;
    
    public LabelingTreeNormalizer(PrintWriter outFile, PrintWriter flatFile) {
      super(outFile, flatFile);
    
      leftClitic = Pattern.compile("^-");
      rightClitic = Pattern.compile("-$");
    }

    @Override
    protected void processPreterminal(Tree node) {
      String rawTag = node.value();
      if(rawTag.equals("-NONE-"))
        return;
      
      String rawWord = node.firstChild().value().trim();
      
      Matcher left = leftClitic.matcher(rawWord);
      boolean hasLeft = left.find();
      Matcher right = rightClitic.matcher(rawWord);
      boolean hasRight = right.find();
      
      if(rawTag.equals("PUNC") || !(hasRight || hasLeft)) {
        node.firstChild().setValue("XSEG");
      
      } else if(hasRight && hasLeft){
        node.firstChild().setValue("SEGC");
      } else if(hasRight) {
        node.firstChild().setValue("SEGL");
      } else if(hasLeft) {
        node.firstChild().setValue("SEGR");
      } else {
        throw new RuntimeException("Messy token: " + rawWord);
      }
    }
  }
}
