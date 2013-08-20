package edu.stanford.nlp.ie.ner;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.process.PTBTokenizer;

/**
 * Simple utility class for converting english gigaword formatted files into
 * format for aclark's distsim cluster creator. Probably would work for any
 * text files.
 *
 * @author Jenny Finkel
 * @author Anna Rafferty (ported to repository, various cleanup)
 *
 */
public class ConvertEnglishGigaword {

  private static final Pattern sgmlP = Pattern.compile("<.*?>");


  private ConvertEnglishGigaword() {
  }


  private static void processSingleFile(String filename) {
    try {
    InputStream is = new FileInputStream(filename);
    if (filename.endsWith(".gz")) {
      is = new GZIPInputStream(is);
    }
    BufferedReader in = new BufferedReader(new InputStreamReader(new BufferedInputStream(is)));
    for (String line; (line = in.readLine()) != null; ) {
      Matcher m = sgmlP.matcher(line);
      line = m.replaceAll(" ");
      PTBTokenizer<Word> ptb = PTBTokenizer.newPTBTokenizer(new StringReader(line));
      List<Word> words = ptb.tokenize();
      for (Word w : words) {
        System.out.println(w.toString().toUpperCase());
      }
    }
    in.close();
    } catch(Exception e) {
      System.err.println("Error for file " + filename);
      e.printStackTrace();
    }
  }


  /**
   * Take a list of directories or files with data and convert to
   * distsim format.  Result is printed to System.out ; redirect to get
   * a data file for making the distsim clusters.
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    for (String directoryName : args) {
      File directory = new File(directoryName);
      if(directory.isDirectory()) {
        for(String filename : directory.list()) {
          processSingleFile(directoryName + File.separator + filename);
        }
      } else {
        processSingleFile(directoryName);
      }
    }

  }

}
