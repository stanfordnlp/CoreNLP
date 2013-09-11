package edu.stanford.nlp.trees;

import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.util.FilePathProcessor;
import edu.stanford.nlp.util.FileProcessor;
import edu.stanford.nlp.ling.IndexedWord;

import java.io.File;
import java.io.FileFilter;
import java.io.Reader;
import java.util.List;
import java.util.ArrayList;

/**
 * Finds a tree based on filename and sentence index.
 * @author grenager
 */
public class TreeLookup implements FileProcessor {

  int CACHE_MIN = 2;
  int CACHE_MAX = 4;

  File treebankDir;
  TreeReaderFactory trf;
  List<String> keys;
  List<MemoryTreebank> values;

  /**
   * Puts a MemoryTreebank corresponding to this file in the cache for later use.
   */
  public void processFile(File file) {
    MemoryTreebank tb = new MemoryTreebank(trf);
    tb.loadPath(file);
    addToCache(file.getName(), tb);
  }

  private void addToCache(String name, MemoryTreebank tb) {
    keys.add(name);
    values.add(tb);
    int size = keys.size();
    if (size>=CACHE_MAX) {
      keys = new ArrayList<String>(keys.subList(size-CACHE_MIN+1, size));
      values = new ArrayList<MemoryTreebank>(values.subList(size-CACHE_MIN+1, size));
    }
//    System.err.println("added file: " + name);
  }

  /**
   */
  public Tree getTree(String filename, int sentenceIndex) {
    MemoryTreebank tb = getFromCache(filename);
    if (tb==null) {
      loadFile(filename);
      tb = getFromCache(filename);
    }
    return tb.get(sentenceIndex);
  }

  private MemoryTreebank getFromCache(String filename) {
    int index = keys.indexOf(filename);
    if (index<0) return null;
    return values.get(index);
  }

  public void loadFile(final String filename) {
    FileFilter filter = new FileFilter() {
      public boolean accept(File file) {
//        System.err.println("Checking file: " + file.getName());
        return file.getName().equals(filename) || file.isDirectory();
      }
    };
    FilePathProcessor.processPath(treebankDir, filter, this); // sets the treebank
  }

  public TreeLookup(String treebankPath, TreeReaderFactory trf) {
    treebankDir = new File(treebankPath);
    keys = new ArrayList<String>();
    values = new ArrayList<MemoryTreebank>();
    this.trf = trf;
  }

  /**
   * Usage TreeLookup treebankPath filename index
   * index starts at 0 for each file.
   */
  public static void main(String[] args) {
    String path = args[0];
    final String filename = args[1];
    int sentenceIndex = Integer.parseInt(args[2]);
    System.err.println("Finding sentence " + sentenceIndex + " of file " + filename);
    TreeReaderFactory trf = new TreeReaderFactory() {
      public TreeReader newTreeReader(Reader in) {
        return new PennTreeReader(in,
                                  new LabeledScoredTreeFactory(IndexedWord.factory())
//                                  , new NPTmpRetainingTreeNormalizer(NPTmpRetainingTreeNormalizer.TEMPORAL_ALL_NP_PP_ADVP, false, false, false)
                                 );
      }
    };
    TreeLookup tl = new TreeLookup(path, trf);
    Tree t = tl.getTree(filename, sentenceIndex);
    IndexedWord.setPrintFormat(IndexedWord.VALUE_FORMAT);
    t.pennPrint();
    System.out.println();
    SemanticGraph g = SemanticGraphFactory.allTypedDependencies(t, true);
    System.out.println(g);
  }

}
