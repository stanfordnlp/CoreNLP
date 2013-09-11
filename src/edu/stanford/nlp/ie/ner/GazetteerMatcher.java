package edu.stanford.nlp.ie.ner;

import edu.stanford.nlp.util.Pair;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

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

public class GazetteerMatcher {

  private static class Sentence extends ArrayList {
    private String id;

    @Override
    public String toString() {
      //StringBuffer s = new StringBuffer(id+"\n");
      StringBuilder s = new StringBuilder();
      Iterator i = iterator();
      while (i.hasNext()) {
        s.append(i.next());
        if (i.hasNext()) {
          s.append("\n");
        }
      }
      s.append("\n");
      return s.toString();
    }

    private static final long serialVersionUID = -7716669158437729758L;

  } // end static class Sentence


  private static class Word {
    private String word;
    private String info;
    private String match = "XX";

    @Override
    public String toString() {
      return word + " " + info.trim() + " " + match;
    }
  }

  public GazetteerMatcher(String inFile, String[] gf) throws Exception {
    List sentences = makeSentences(new BufferedReader(new FileReader(inFile)));
    Map gazettes = makeGazettes(gf);
    System.err.println(sentences.size() + " sentences.");
    System.err.println(gazettes.size() + " gazette entries.");
    findMatches(sentences, gazettes);
  }

  public List makeSentences(BufferedReader in) throws Exception {
    String line;
    List sentences = new ArrayList(7500);
    Sentence sentence = new Sentence();
    Word word;
    while ((line = in.readLine()) != null) {
      if (line.trim().equals("")) {
        if (sentence != null) {
          sentences.add(sentence);
        }
        sentence = new Sentence();
        continue;
      }

      word = new Word();
      //index = line.indexOf(" ");
      //word.word = line.substring(0, index).trim();
      //word.info = line.substring(index+1).trim();
      String[] w = line.split("\\s");
      word.word = w[0].trim();
      word.info = line.substring(word.word.length() + 1).trim();
      sentence.add(word);
    }
    if (sentence != null) {
      sentences.add(sentence);
    }
    return sentences;
  }

  public Map makeGazettes(String[] files) throws Exception {
    Map gazettes = new HashMap();
    String entity, line;
    int index;
    List l;
    String key;
    for (int i = 0; i < files.length; i++) {
      BufferedReader in = new BufferedReader(new FileReader(files[i]));
      while ((line = in.readLine()) != null) {
        index = line.indexOf(" ");
        entity = line.substring(0, index);
        line = line.substring(index + 1);
        index = line.indexOf(" ");
        if (index < 0) {
          key = line;
        } else {
          key = line.substring(0, index);
        }
        l = (List) gazettes.get(key.toLowerCase());
        if (l == null) {
          l = new ArrayList();
        }
        l.add(new Pair<String,String>(entity, line.toLowerCase()));
        gazettes.put(key.toLowerCase(), l);
      }
    }
    return gazettes;
  }

  public void findMatches(List sentences, Map gazettes) {
    for (int i = 0; i < sentences.size(); i++) {
      Sentence s = (Sentence) sentences.get(i);
      for (int j = 0; j < s.size(); j++) {
        List l = (List) gazettes.get(((Word) (s.get(j))).word.toLowerCase());
        if (l != null) {
          findMatch(s, j, l);
        }
      }
      System.out.println(s);
    }
  }

  public void findMatch(Sentence s, int pos, List gazettes) {
    for (int i = 0; i < gazettes.size(); i++) {
      String[] g = ((String) ((Pair) gazettes.get(i)).second()).split(" ");
      for (int j = 0; j <= g.length; j++) {
        if (j == g.length) {
          mark(s, pos, j, (String) ((Pair) gazettes.get(i)).first());
          continue;
        }
        if ((pos + j) >= s.size()) {
          break;
        }
        if (!g[j].equalsIgnoreCase(((Word) s.get(pos + j)).word)) {
          break;
        }
      }
    }
  }

  public static void mark(Sentence s, int start, int length, String entity) {
    for (int i = start; i < start + length; i++) {
      ((Word) s.get(i)).match = entity;
      //System.out.println(((Word)s.get(i)).word);
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
    new GazetteerMatcher(args[0], gm);
  }

}
