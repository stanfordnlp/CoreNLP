package edu.stanford.nlp.international.arabic.pipeline; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.Properties;

import edu.stanford.nlp.international.arabic.Buckwalter;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.treebank.AbstractDataset;
import edu.stanford.nlp.trees.DiskTreebank;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TreeVisitor;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.BobChrisTreeNormalizer.AOverAFilter;
import edu.stanford.nlp.trees.international.arabic.*;
import java.util.function.Predicate;

/**
 * Converts raw ATB trees into a format appropriate for treebank parsing.
 *
 * @author Spence Green
 *
 */
public class ATBArabicDataset extends AbstractDataset  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ATBArabicDataset.class);

  public ATBArabicDataset() {
    super();

    //Read the raw file as UTF-8 irrespective of output encoding
    treebank = new DiskTreebank(new ArabicTreeReaderFactory.ArabicRawTreeReaderFactory(true), "UTF-8");
  }

  public void build() {
    for(File path : pathsToData) {
      if(splitFilter == null) {
        treebank.loadPath(path,treeFileExtension,false);
      } else {
        treebank.loadPath(path,splitFilter);
      }
    }

    PrintWriter outfile = null;
    PrintWriter flatFile = null;
    try {
      outfile = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFileName),"UTF-8")));
      flatFile = (makeFlatFile) ? new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(flatFileName),"UTF-8"))) : null;

      treebank.apply(new ArabicRawTreeNormalizer(outfile,flatFile));

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


  public boolean setOptions(Properties opts) {
    boolean ret = super.setOptions(opts);

    if(lexMapper == null) {
      lexMapper = new DefaultLexicalMapper();
      lexMapper.setup(null, lexMapOptions.split(","));
    }

    if(pathsToMappings.size() != 0) {
      if(posMapper == null) {
      	posMapper = new LDCPosMapper(addDeterminer);
      }
      String[] mapOpts = posMapOptions.split(",");
      for(File path : pathsToMappings)
        posMapper.setup(path,mapOpts);
    }

    return ret;
  }


  /**
   * A {@link edu.stanford.nlp.trees.TreeVisitor} for raw ATB trees. This class performs
   * minimal pre-processing (for example, it does not prune traces). It also provides
   * a facility via <code>enableIBMArabicEscaping</code> for sub-classes to process
   * IBM Arabic parse trees.
   *
   */
  protected class ArabicRawTreeNormalizer implements TreeVisitor {
    protected final Buckwalter encodingMap;
    protected final PrintWriter outfile;
    protected final PrintWriter flatFile;
    protected final Predicate<Tree> nullFilter;
    protected final Predicate<Tree> aOverAFilter;
    protected final TreeFactory tf;
    protected final TreebankLanguagePack tlp;

    public ArabicRawTreeNormalizer(PrintWriter outFile, PrintWriter flatFile) {
      encodingMap = (encoding == Encoding.UTF8) ? new Buckwalter() : new Buckwalter(true);

      this.outfile = outFile;
      this.flatFile = flatFile;

      nullFilter = new ArabicTreeNormalizer.ArabicEmptyFilter();
      aOverAFilter = new AOverAFilter();

      tf = new LabeledScoredTreeFactory();
      tlp = new ArabicTreebankLanguagePack();
    }

    protected void processPreterminal(Tree node) {
      String rawTag = node.value();
      String posTag = (posMapper == null) ? rawTag : posMapper.map(rawTag,node.firstChild().value());
      String rawWord = node.firstChild().value();

      //Hack for LDC2008E22 idiosyncrasy in which (NOUN.VN F) is a pre-terminal/word
      //This is a bare fathatan that bears no semantic content. Replacing it with the
      //conjunction ف / f .
      if(rawWord.equals("F")) {
        posTag = posTag.equals("NOUN.VN") ? "CONJ" : "CC";
        rawWord = "f";
      }

      // Hack for annotation error in ATB
      if (rawWord.startsWith("MERGE_with_previous_token:")) {
        rawWord = rawWord.replace("MERGE_with_previous_token:", "");
      }

      // Hack for annotation error in ATB
      if (rawWord.contains("e")) {
        rawWord = rawWord.replace("e", "");
      }

      String finalWord = lexMapper.map(rawTag, rawWord);
      if(lexMapper.canChangeEncoding(rawTag, finalWord))
        finalWord = encodingMap.apply(finalWord);

      node.setValue(posTag);
      if(morphDelim == null) {
        node.firstChild().setValue(finalWord);
        if (node.firstChild().label() instanceof CoreLabel) ((CoreLabel) node.firstChild().label()).setWord(finalWord);
      } else {
        node.firstChild().setValue(finalWord + morphDelim + rawTag);
      }
    }

    //Modifies the tree in-place...should be run after
    //mapping to reduced tag set
    public Tree arabicAoverAFilter(Tree t) {
    	if(t == null || t.isLeaf() || t.isPreTerminal())
    		return t;

    	//Specific nodes to filter out
    	if(t.numChildren() == 1) {
    		final Tree fc = t.firstChild();

    		//A over A nodes i.e. from BobChrisTreeNormalizer
    		if(t.label() != null && fc.label() != null && t.value().equals(fc.value())) {
    			t.setChildren(fc.children());
    		}
    	}

    	for(Tree kid : t.getChildrenAsList())
    		arabicAoverAFilter(kid);

    	return t;
    }


    public void visitTree(Tree t) {
      // Filter out XBar trees
      if(t == null || t.value().equals("X")) return;
      if(t.yield().size() > maxLen) return;

      // Strip out traces and pronoun deletion markers,
      t = t.prune(nullFilter, tf);
      t = arabicAoverAFilter(t);

      // Visit nodes with a custom visitor
      if(customTreeVisitor != null)
        customTreeVisitor.visitTree(t);

      // Process each node in the tree
      for(Tree node : t) {
        if(node.isPreTerminal()) {
          processPreterminal(node);
        }
        if(removeDashTags && !node.isLeaf())
          node.setValue(tlp.basicCategory(node.value()));
      }

      // Add a ROOT node if necessary
      if (addRoot && t.value() != null && !t.value().equals("ROOT")) {
        t = tf.newTreeNode("ROOT", Collections.singletonList(t));
      }

      // Output the trees to file
      outfile.println(t.toString());
      if(flatFile != null) {
        String flatString = (removeEscapeTokens) ?
            ATBTreeUtils.unEscape(ATBTreeUtils.flattenTree(t)) : ATBTreeUtils.flattenTree(t);
        flatFile.println(flatString);
      }
    }
  }
}
