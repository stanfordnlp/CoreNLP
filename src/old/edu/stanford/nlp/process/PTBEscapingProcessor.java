package old.edu.stanford.nlp.process;


import old.edu.stanford.nlp.util.Function;


import old.edu.stanford.nlp.ling.BasicDocument;
import old.edu.stanford.nlp.ling.Document;
import old.edu.stanford.nlp.ling.HasWord;
import old.edu.stanford.nlp.ling.Word;

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

  protected Map<String,String> stringSubs;
  protected char[] oldChars;

  // these should all appear as tokens themselves
  protected static final String[] oldStrings = {"(", ")", "[", "]", "{", "}", "/", "*"};
  protected static final String[] newStrings = {"-LRB-", "-RRB-", "-LSB-", "-RSB-", "-LCB-", "-RCB-", "\\/", "\\*"};

  // these are chars that might appear inside tokens
  protected static final char[] defaultOldChars = {'*', '/'};

  protected boolean fixQuotes = true;

  public PTBEscapingProcessor() {
    this(makeStringMap(), defaultOldChars, true);
  }

  public PTBEscapingProcessor(Map<String,String> stringSubs, char[] oldChars, boolean fixQuotes) {
    this.stringSubs = stringSubs;
    this.oldChars = oldChars;
    this.fixQuotes = fixQuotes;
  }

  protected static Map<String,String> makeStringMap() {
    Map<String,String> map = new HashMap<String,String>();
    for (int i = 0; i < oldStrings.length; i++) {
      map.put(oldStrings[i], newStrings[i]);
    }
    return map;
  }

  /*
  public Document processDocument(Document input) {
    Document result = input.blankDocument();
    result.addAll(process((List)input));
    return result;
  }
  */


  /** Unescape a List of HasWords.  Implements the
   *  Function&lt;List&lt;HasWord&gt;, List&lt;HasWord&gt;&gt; interface.
   */
  public List<HasWord> apply(List<IN> hasWordsList) {
    return process(hasWordsList);
  }

  public static String unprocess(String s) {
    for (int i = 0; i < newStrings.length; i++) {
      if (newStrings[i].equals(s)) { return oldStrings[i]; }
    }
    return s;
  }

  /**
   * @param input must be a List of objects of type HasWord
   */
  public List<HasWord> process(List<? extends IN> input) {
    List<HasWord> output = new ArrayList<HasWord>();
    for (IN h : input) {
      String s = h.word();
      String newS = stringSubs.get(s);
      if (newS != null) {
        h.setWord(newS);
      } else {
        h.setWord(escapeString(s));
      }
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

  private String escapeString(String s) {
    StringBuilder buff = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char curChar = s.charAt(i);
      if (curChar == '\\') {
        // add this and the next one
        buff.append(curChar);
        i++;
        if (i < s.length()) {
          curChar = s.charAt(i);
          buff.append(curChar);
        }
      } else {
        // run through all the chars we need to escape
        for (char oldChar : oldChars) {
          if (curChar == oldChar) {
            buff.append('\\');
            break;
          }
        }
        // append the old char no matter what
        buff.append(curChar);
      }
    }
    return buff.toString();
  }

  /**
   * This will do the escaping on an input file. Input file must already be tokenized,
   * with tokens separated by whitespace. <br>
   * Usage: java edu.stanford.nlp.process.PTBEscapingProcessor fileOrUrl
   *
   * @param args Command line argument: a file or URL
   */
  public static void main(String[] args) {
    if (args.length != 1) {
      System.out.println("usage: java edu.stanford.nlp.process.PTBEscapingProcessor fileOrUrl");
      System.exit(0);
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
