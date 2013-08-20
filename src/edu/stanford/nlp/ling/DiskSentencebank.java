package edu.stanford.nlp.ling;

import edu.stanford.nlp.io.NumberRangeFileFilter;
import edu.stanford.nlp.util.FilePathProcessor;
import edu.stanford.nlp.util.FileProcessor;
import edu.stanford.nlp.util.Timing;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A <code>DiskSentencebank</code> object stores merely the information to
 * get at a corpus of sentences that is stored on disk.  Access is usually
 * via an Iterator, or applying a SentenceVisitor to an iteration over the
 * sentences.
 *
 * @author Christopher Manning
 */
public final class DiskSentencebank<T extends HasWord> extends Sentencebank<ArrayList<T>, T> {

  private static final boolean PRINT_FILENAMES = false;

  private ArrayList<File> filePaths = new ArrayList<File>();
  private ArrayList<FileFilter> fileFilters = new ArrayList<FileFilter>();

  /**
   * Maintains as a class variable the <code>File</code> from which
   * trees are currently being read.
   */
  private File currentFile = null;


  /**
   * Create a new DiskSentencebank.
   */
  public DiskSentencebank() {
    this(new SimpleSentenceReaderFactory<T>());
  }


  /**
   * Create a new DiskSentencebank.
   *
   * @param srf the factory class to be called to create a new
   *            <code>SentenceReader</code>
   */
  public DiskSentencebank(SentenceReaderFactory<T> srf) {
    super(srf);
  }


  /**
   * Create a new Sentencebank.
   *
   * @param initialCapacity The initial size of the underlying Collection.
   *                        For a <code>DiskSentencebank</code>, this parameter is ignored.
   */
  public DiskSentencebank(int initialCapacity) {
    this(initialCapacity, new SimpleSentenceReaderFactory<T>());
  }


  /**
   * Create a new Sentencebank.
   *
   * @param initialCapacity The initial size of the underlying Collection,
   *                        For a <code>DiskSentencebank</code>, this parameter is ignored.
   * @param srf             the factory class to be called to create a new
   *                        <code>SentenceReader</code>
   */
  public DiskSentencebank(int initialCapacity, SentenceReaderFactory<T> srf) {
    this(srf);
  }


  /**
   * Empty a <code>Sentencebank</code>.
   */
  @Override
  public void clear() {
    filePaths.clear();
    fileFilters.clear();
  }


  /**
   * Load trees from given directory.  This version just records
   * the paths to be processed, and actually processes them at apply time.
   *
   * @param path file or directory to load from
   * @param filt a FilenameFilter of files to load
   */
  @Override
  public void loadPath(File path, FileFilter filt) {
    filePaths.add(path);
    fileFilters.add(filt);
  }


  /**
   * Applies the SentenceVisitor to to all trees in the Sentencebank.
   *
   * @param sp A class that can process trees.
   */
  @Override
  public void apply(final SentenceVisitor<T> sp) {
    DiskSentencebankFileProcessor dsfp = new DiskSentencebankFileProcessor(sp);
    int numPaths = filePaths.size();
    for (int i = 0; i < numPaths; i++) {
      FilePathProcessor.processPath(filePaths.get(i), fileFilters.get(i), dsfp);
    }
  }


  private final class DiskSentencebankFileProcessor implements FileProcessor {

    /**
     * The <code>SentenceVisitor</code> to apply to trees in the file
     */
    final SentenceVisitor<T> sp;

    DiskSentencebankFileProcessor(SentenceVisitor<T> sp) {
      this.sp = sp;
    }


    /**
     * Load a collection of parse trees from the file of given name.
     * Each tree may optionally be encased in parens to allow for Penn
     * Sentencebank style trees.
     *
     * @param file <code>File</code> to load trees from
     */
    public void processFile(File file) {
      SentenceReader<T> sr = null;
      ArrayList<T> s;

      // maybe print file name to stdout to get some feedback
      if (PRINT_FILENAMES) {
        System.err.println(file);
      }
      // save the current file so you can get at it
      currentFile = file;
      try {
        // could throw an IO exception if can't open for reading
        sr = sentenceReaderFactory().newSentenceReader(new BufferedReader(new FileReader(file)));
        while ((s = sr.readSentence()) != null) {
          sp.visitSentence(s);
        }
      } catch (IOException e) {
        System.err.println("loadSentence IO Exception: " + e + " in " + getCurrentFile());
      } finally {
        currentFile = null;
        try {
          if (sr != null) {
            sr.close();  // important: closes file even if error!
          }
        } catch (IOException e) {
        }
      }
    } // end processFile()

  } // end private class DiskSentencebankFileProcessor


  /**
   * Return the <code>File</code> from which trees are currently being
   * read by <code>apply()</code>, and pased to a
   * <code>SentencePprocessor</code>.  This is useful if one wants to map
   * the original file and
   * directory structure over to a set of modified trees.
   *
   * @return the file that trees are currently being read from, or
   *         <code>null</code> if no file is currently open
   */
  public File getCurrentFile() {
    return currentFile;
  }


  private class DiskSentencebankIterator implements Iterator<ArrayList<T>> {

    private int fileUpto = -1; // before starting on index array 0
    private int sentenceUpto; // = 0
    private List<String> files;
    private MemorySentencebank<T> currentFileSentences;

    DiskSentencebankIterator() {
      files = new ArrayList<String>();
      // get the list of files in the Sentencebank via a new
      // FilePathProcessor()
      FileProcessor dtifp = new FileProcessor() {
        public void processFile(File file) {
          files.add(file.toString());
        }
      };
      int numPaths = filePaths.size();
      for (int i = 0; i < numPaths; i++) {
        FilePathProcessor.processPath(filePaths.get(i), fileFilters.get(i), dtifp);
      }
      currentFileSentences = new MemorySentencebank<T>(sentenceReaderFactory());
      // we're now all setup to read a new file on first call
      // to hasNext()
    }

    /**
     * Returns true if the iteration has more elements.
     */
    public boolean hasNext() {
      while (fileUpto < files.size()) {
        if (sentenceUpto < currentFileSentences.size()) {
          return true;
        } else {
          // load the next file
          currentFileSentences.clear();
          fileUpto++;
          sentenceUpto = 0;
          if (fileUpto < files.size()) {
            currentFileSentences.loadPath(files.get(fileUpto));
          }
        }
      }
      // there's nothing left;
      return false;
    }

    /**
     * Returns the next element in the interation.
     */
    public ArrayList<T> next() {
      return currentFileSentences.get(sentenceUpto++);
    }

    /**
     * Not supported
     */
    public void remove() {
      throw new UnsupportedOperationException();
    }

  }


  /**
   * Return an Iterator over Sentences in the Sentencebank.  This is
   * implemented by building per-file MemorySentencebanks for the files
   * in the DiskSentencebank.  As such, it isn't as efficient as using
   * <code>apply()</code>.
   */
  @Override
  public Iterator<ArrayList<T>> iterator() {
    return new DiskSentencebankIterator();
  }


  /**
   * Loads SentenceBank from first argument and prints it out.
   * Flags allow printing just words (-w) or also tags, and whether to do
   * Treebank-style normalization of brackets. <br>
   * Usage: <code>java edu.stanford.nlp.ling.DiskSentencebank
   * [-n|-w] sentencebankPath [low high]</code>
   *
   * @param args Array of command-line arguments
   */
  public static void main(String[] args) {
    Timing.startTime();
    boolean treebankNormalize = false;
    boolean justWordsFlag = false;
    final boolean justWords;
    int j;
    for (j = 0; j < args.length && args[j].startsWith("-"); j++) {
      if (args[j].equals("-n")) {
        treebankNormalize = true;
      } else if (args[j].equals("-w")) {
        justWordsFlag = true;
      } else {
        System.err.println("Unknown option: " + args[j]);
      }
    }
    justWords = justWordsFlag;  // ruse to get around finality restriction
    if (j >= args.length) {
      System.err.println("Usage: java edu.stanford.nlp.trees." + "DiskSentencebank [-n|-w] sentencebankPath" + " [low high]");
      System.exit(0);
    }

    final SentenceNormalizer<HasWord> sn;
    if (treebankNormalize) {
      // use special normalizer that recodes brackets
      sn = new PennSentenceMrgNormalizer<HasWord>();
    } else {
      sn = new PennSentenceNormalizer<HasWord>();
    }
    SentenceReaderFactory<HasWord> srf = new SentenceReaderFactory<HasWord>() {
      public SentenceReader<HasWord> newSentenceReader(Reader in) {
        return new SentenceReader<HasWord>(in, new TaggedWordFactory(), sn, new PennTagbankStreamTokenizer(in));
      }
    };
    Sentencebank<ArrayList<HasWord>, HasWord> sentencebank = new DiskSentencebank<HasWord>(srf);

    if (j + 2 < args.length) {
      int start = Integer.parseInt(args[j + 1]);
      int end = Integer.parseInt(args[j + 2]);
      sentencebank.loadPath(new File(args[j]), new NumberRangeFileFilter(start, end, true));
    } else {
      sentencebank.loadPath(args[j]);
    }
    sentencebank.apply(new SentenceVisitor<HasWord>() {
      public void visitSentence(final ArrayList<HasWord> t) {
        if (justWords) {
          System.out.println(Sentence.listToString(t, true));
        } else {
          System.out.println(Sentence.listToString(t, false));
        }
      }
    });
    System.err.println();
    Timing.endTime("traversing corpus, printing sentences 1-by-1");
    /*
System.out.println();
Timing.startTime();
System.out.println(sentencebank);
Timing.endTime("traversing corpus, appending all files");

System.out.println();
Timing.startTime();
Iterator iter = sentencebank.iterator();
while (iter.hasNext()) {
  Sentence s = (Sentence) iter.next();
  System.out.println(s);
}
System.out.println();
Timing.endTime("traversing corpus, with iterator");

System.out.println();
Timing.startTime();
System.out.println("This Sentencebank contains " +
     sentencebank.size() + " sentences.");
Timing.endTime("size of corpus");

System.out.println();
Sentencebank sentencebank2 = new DiskSentencebank(new
         SentenceReaderFactory() {
  public SentenceReader newSentenceReader(Reader in) {
      return new SentenceReader(in, new WordFactory(),
      new PennSentenceNormalizer(true, '/'),
      new PennTagbankStreamTokenizer(in));
  }
});
sentencebank2.loadPath(args[0]);
sentencebank2.apply(new SentenceVisitor() {
  public void visitSentence(final Sentence t) {
System.out.println(t);
  }
});
System.out.println();
    */
  }

}
