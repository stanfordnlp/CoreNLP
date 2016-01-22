package edu.stanford.nlp.process;


import edu.stanford.nlp.util.Function;


import edu.stanford.nlp.ling.BasicDocument;
import edu.stanford.nlp.ling.Document;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Word;

import java.io.File;
import java.net.URL;
import java.util.*;


/**
 * Produces a new Document of Words in which special characters of the PTB
 * have been properly escaped.
 *
 * @author Teg Grenager (grenager@stanford.edu)
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 *
 * @param <L> The type of the labels
 * @param <F> The type of the features
 */
public class PTBEscapingProcessor<IN extends HasWord, L, F> extends AbstractListProcessor<IN, HasWord, L, F>
  implements Function<List<IN>, List<HasWord>> {

  private static final char[] SUBST_CHARS = {'(', ')', '[', ']', '{', '}'};
  private static final String[] REPLACE_SUBSTS = {"-LRB-", "-RRB-", "-LSB-", "-RSB-", "-LCB-", "-RCB-"};

  protected char[] substChars = SUBST_CHARS;
  protected String[] replaceSubsts = REPLACE_SUBSTS;
  protected char[] escapeChars = {'/', '*'};
  protected String[] replaceEscapes = {"\\/", "\\*"};

  protected boolean fixQuotes = true;

  public PTBEscapingProcessor() {
  }

  public PTBEscapingProcessor(char[] escapeChars, String[] replaceEscapes, char[] substChars, String[] replaceSubsts, boolean fixQuotes) {
    this.escapeChars = escapeChars;
    this.replaceEscapes = replaceEscapes;
    this.substChars = substChars;
    this.replaceSubsts = replaceSubsts;
    this.fixQuotes = fixQuotes;
  }


  /*
  public Document processDocument(Document input) {
    Document result = input.blankDocument();
    result.addAll(process((List)input));
    return result;
  }
  */


  /** Escape a List of HasWords.  Implements the
   *  Function&lt;List&lt;HasWord&gt;, List&lt;HasWord&gt;&gt; interface.
   */
  public List<HasWord> apply(List<IN> hasWordsList) {
    return process(hasWordsList);
  }

  public static String unprocess(String s) {
    for (int i = 0; i < REPLACE_SUBSTS.length; i++) {
      s = s.replaceAll(REPLACE_SUBSTS[i], String.valueOf(SUBST_CHARS[i]));
    }
    // at present doesn't deal with * / stuff ... never did
    return s;
  }

  /**
   * @param input must be a List of objects of type HasWord
   */
  public List<HasWord> process(List<? extends IN> input) {
    List<HasWord> output = new ArrayList<HasWord>();
    for (IN h : input) {
      String s = h.word();
      h.setWord(escapeString(s));
      output.add(h);
    }
    if (fixQuotes) {
      return fixQuotes(output);
    }
    return output;
  }


  private static List<HasWord> fixQuotes(List<HasWord> input) {
    int inputSize = input.size();
    LinkedList<HasWord> result = new LinkedList<HasWord>();
    if (inputSize == 0) {
      return result;
    }
    boolean begin;
    // see if there is a quote at the end
    if (input.get(inputSize - 1).word().equals("\"")) {
      // alternate from the end
      begin = false;
      for (int i = inputSize - 1; i >= 0; i--) {
        HasWord hw = input.get(i);
        String tok = hw.word();
        if (tok.equals("\"")) {
          if (begin) {
            hw.setWord("``");
            begin = false;
          } else {
            hw.setWord("\'\'");
            begin = true;
          }
        } // otherwise leave it alone
        result.addFirst(hw);
      } // end loop
    } else {
      // alternate from the beginning
      begin = true;
      for (int i = 0; i < inputSize; i++) {
        HasWord hw = input.get(i);
        String tok = hw.word();
        if (tok.equals("\"")) {
          if (begin) {
            hw.setWord("``");
            begin = false;
          } else {
            hw.setWord("\'\'");
            begin = true;
          }
        } // otherwise leave it alone
        result.addLast(hw);
      } // end loop
    }
    return result;
  }


  public String escapeString(String s) {
    StringBuilder buff = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char curChar = s.charAt(i);
      // run through all the chars we need to replace
      boolean found = false;
      for (int k = 0; k < substChars.length; k++) {
        if (curChar == substChars[k]) {
          buff.append(replaceSubsts[k]);
          found = true;
          break;
        }
      }
      if (found) {
        continue;
      }
      // don't do it if escape is already there usually
      if (curChar == '\\') {
        // add this and the next one unless bracket
        buff.append(curChar);
        if (maybeAppendOneMore(i + 1, s, buff)) {
          i++;
        }
        found = true;
      }
      if (found) {
        continue;
      }
      // run through all the chars we need to escape
      for (int k = 0; k < escapeChars.length; k++) {
        if (curChar == escapeChars[k]) {
          buff.append(replaceEscapes[k]);
          found = true;
          break;
        }
      }
      if (found) {
        continue;
      }

      // append the old char no matter what
      buff.append(curChar);
    }
    return buff.toString();
  }

  private boolean maybeAppendOneMore(int pos, String s, StringBuilder buff) {
    if (pos >= s.length()) {
      return false;
    }
    char candidate = s.charAt(pos);
    boolean found = false;
    for (char ch : substChars) {
      if (candidate == ch) {
        found = true;
        break;
      }
    }
    if (found) {
      return false;
    }
    buff.append(candidate);
    return true;
  }

  /**
   * This will do the escaping on an input file. Input file should already be tokenized,
   * with tokens separated by whitespace. <br>
   * Usage: java edu.stanford.nlp.process.PTBEscapingProcessor fileOrUrl
   *
   * @param args Command line argument: a file or URL
   */
  public static void main(String[] args) {
    if (args.length != 1) {
      System.out.println("usage: java edu.stanford.nlp.process.PTBEscapingProcessor fileOrUrl");
      return;
    }
    String filename = args[0];
    try {
      Document<String, Word, Word> d; // initialized below
      if (filename.startsWith("http://")) {
        Document<String, Word, Word> dpre = new BasicDocument<String>(WhitespaceTokenizer.factory()).init(new URL(filename));
        DocumentProcessor<Word, Word, String, Word> notags = new StripTagsProcessor<String, Word>();
        d = notags.processDocument(dpre);
      } else {
        d = new BasicDocument<String>(WhitespaceTokenizer.factory()).init(new File(filename));
      }
      DocumentProcessor<Word, HasWord, String, Word> proc = new PTBEscapingProcessor<Word, String, Word>();
      Document<String, Word, HasWord> newD = proc.processDocument(d);
      for (HasWord word : newD) {
        System.out.println(word);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

  }

}
