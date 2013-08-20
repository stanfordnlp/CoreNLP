package edu.stanford.nlp.ie.ner;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Add gazette matching information as an extra column to an NER
 * multicolumn input file.
 * Usage: <p><code>
 * java edu.stanford.nlp.ie.ner.GazetteerMatcher fileToBeMarked gazetteFile+ > outfile
 * </code><p>The file to be marked should be in the format: <p><code>
 * id <br>
 * word info <br>
 * word info <br>
 * ... <br>
 * BLANKLINE
 * </code><p>
 * This is the same format as all of our input stuff.
 * <p/>
 * The list of gazettes should be in the same format as
 * in <code>/u/nlp/data/biocreative/Gaz/chris-lotta-gene-names</code>.
 * <p/>
 * Words which are not in the gazette are labeled 'XX'
 *
 * @author Jenny Finkel
 */

public class FuzzyGazetteMatcher {

  static class Sentence extends ArrayList<Word> {
    /**
     * 
     */
    private static final long serialVersionUID = 7216991748455264595L;
    String id;

    public String subsentence(int i) {
      StringBuffer sb = new StringBuffer();
      for (; i < size(); i++) {
        sb.append(get(i).word + " ");
      }
      return sb.toString().trim();
    }

    @Override
    public String toString() {
      StringBuffer s = new StringBuffer(id + "\n");
      Iterator<Word> i = iterator();
      while (i.hasNext()) {
        s.append(i.next());
        if (i.hasNext()) {
          s.append("\n");
        }
      }
      s.append("\n");
      return s.toString();
    }

  }

  static class Word {
    String word;
    String info;
    String match = "XX";

    @Override
    public String toString() {
      return word + " " + info.trim() + " " + match;
    }
  }

  private BioRegexpDictionary brd;


  public FuzzyGazetteMatcher(String inFile, String[] gf) throws Exception {

    brd = new BioRegexpDictionary(false);
    buildDictionary(gf);
    findMatches(inFile);
  }

  public void findMatches(String inFile) throws Exception {
    BufferedReader in = new BufferedReader(new FileReader(inFile));
    Sentence sent;

    while ((sent = getNextSentence(in)) != null) {
      for (int i = 0; i < sent.size(); i++) {
        String subsentence = sent.subsentence(i);
        //System.err.println(subsentence);
        if (brd.lookingAt(subsentence)) {
          //System.err.println("true");
          int numWords = subsentence.substring(0, brd.end()).split(" ").length;
          String tag = sent.get(i).info;
          if (numWords > 1 || tag.startsWith("NN") || tag.startsWith("CD")) {
            for (int j = i; j < i + numWords; j++) {
              sent.get(j).match = "NEWGENE"; //(String)brd.firstData();
            }
          }
        }
      }
      System.out.println(sent);
    }
  }

  public Sentence getNextSentence(BufferedReader in) throws Exception {

    String line;
    int index;
    Sentence sentence = null;
    Word word;
    while ((line = in.readLine()) != null) {
      if (line.trim().equals("")) {
        break;
      }
      if (line.startsWith("@@")) {
        sentence = new Sentence();
        sentence.id = line;
        continue;
      }

      word = new Word();
      index = line.indexOf(" ");
      word.word = line.substring(0, index).trim();
      word.info = line.substring(index + 1).trim();
      sentence.add(word);
    }
    if (line == null) {
      return null;
    }
    return sentence;
  }

  public void buildDictionary(String[] files) throws Exception {

    String line;
    for (int i = 0; i < files.length; i++) {
      BufferedReader in = new BufferedReader(new FileReader(files[i]));
      while ((line = in.readLine()) != null) {
        //index = line.indexOf(" ");
        //entity = line.substring(0,index);
        //line = line.substring(index+1);
        //System.err.println(line);
        brd.add(line, null); // entity);
      }
    }
  }

  /**
   * The main program.  Usage: <p><code>
   * java edu.stanford.nlp.ie.ner.GazetteerMatcher fileToBeMarked
   * nameOfOutputFile gazetteFile+
   * </code>
   */
  public static void main(String[] args) throws Exception {
    String[] gm = new String[args.length - 1];
    for (int i = 0; i < gm.length; i++) {
      gm[i] = args[i + 1];
    }
    new FuzzyGazetteMatcher(args[0], gm);
  }

}
