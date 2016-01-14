package edu.stanford.nlp.wordseg;

import java.util.*;
import java.io.*;

import edu.stanford.nlp.io.EncodingPrintWriter;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.trees.international.pennchinese.ChineseUtils;
import edu.stanford.nlp.util.Generics;

/**
 * Check if a bigram exists in bakeoff corpora.
 * The dictionaries that this class reads have to be in UTF-8.
 *
 * @author Huihsin Tseng
 * @author Pichuan Chang
 */


public class CorpusDictionary {

  private Set<String> oneWord; // = null;

  /** Load a dictionary of words.
   *
   * @param filename A file of words, one per line. It must be in UTF-8.
   */
  public CorpusDictionary(String filename) {
    this(filename, false);
  }

  public CorpusDictionary(String filename, boolean normalize) {
    if (oneWord == null) {
      oneWord = readDict(filename, normalize);
    }
  }

  public Set<String> getTable() {
    return oneWord;
  }


  private static Set<String> readDict(String filename, boolean normalize)  {
    Set<String> word = Generics.newHashSet();

    System.err.println("Loading " + (normalize ? "normalized" : "unnormalized") + " dictionary from " + filename);

    try {
      InputStream is = IOUtils.getInputStreamFromURLOrClasspathOrFileSystem(filename);
      BufferedReader wordDetectorReader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
      int i = 0;
      for (String wordDetectorLine; (wordDetectorLine = wordDetectorReader.readLine()) != null; ) {
        i++;
        //String[] fields = wordDetectorLine.split("	");
        //System.err.println("DEBUG: "+filename+" "+wordDetectorLine);
        int origLeng = wordDetectorLine.length();
        wordDetectorLine = wordDetectorLine.trim();
        int newLeng = wordDetectorLine.length();
        if (newLeng != origLeng) {
          EncodingPrintWriter.err.println("Line " + i + " of " + filename + " has leading/trailing whitespace: |" + wordDetectorLine + "|", "UTF-8");
        }
        if (newLeng == 0) {
          EncodingPrintWriter.err.println("Line " + i + " of " + filename + " is empty", "UTF-8");
        } else {
          if (normalize) {
            wordDetectorLine = ChineseUtils.normalize(wordDetectorLine,
                                                      ChineseUtils.ASCII,
                                                      ChineseUtils.ASCII,
                                                      ChineseUtils.NORMALIZE);
          }
          word.add(wordDetectorLine);
        }
      }
      is.close();
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
    return word;
  }

  public boolean contains(String word) {
    return getTable().contains(word);
  }

  public String getW(String a1) {
    if (contains(a1))
      return "1";
    return "0";
  }

} // end class CorpusDictionary
