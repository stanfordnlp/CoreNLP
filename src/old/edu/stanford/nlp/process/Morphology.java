package old.edu.stanford.nlp.process;


import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import old.edu.stanford.nlp.io.IOUtils;
import old.edu.stanford.nlp.ling.Word;
import old.edu.stanford.nlp.ling.WordLemmaTag;
import old.edu.stanford.nlp.ling.WordTag;
import old.edu.stanford.nlp.util.Function;


/**
 * Morphology computes the base form of English words, by removing just
 * inflections (not derivational morphology).  That is, it only does noun
 * plurals, pronoun case, and verb endings, and not things like comparative adjectives
 * or derived nominals.  It is based on a finite-state
 * transducer implemented by John Carroll et al., written in flex and publicly
 * available.
 * See: http://www.informatics.susx.ac.uk/research/nlp/carroll/morph.html .
 * There are several ways of invoking Morphology. One is by calling the static
 * methods
 * WordTag stemStatic(String word, String tag) or
 * WordTag stemStatic(WordTag wordTag).
 * If we have created a Morphology object already we can use the methods
 * WordTag stem(String word, string tag) or WordTag stem(WordTag wordTag).
 * <p/>
 * Another way of using Morphology is to run it on an input file by running
 * <code>java Morphology filename</code>.  In this case, POS tags must be
 * separated from words by an underscore ("_").
 *
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 * @author Christopher Manning
 */
public class Morphology implements Function {

  // todo: The main method of this class no longer works. If the tag separator isn't _, it errors, if it is, it doesn't correctly use the POS to do lemmatization

  private static final boolean DEBUG = false;

  private Morpha lexer;
  private static Morpha staticLexer;

  public Morphology() {
    lexer = new Morpha(System.in);
  }

  /**
   * Process morphologically words from a Reader.
   *
   * @param in The Reader to read from
   */
  public Morphology(Reader in) {
    lexer = new Morpha(in);
  }

  public Morphology(String filename) {
    try {
      lexer = new Morpha(new FileReader(filename));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public Word next() throws IOException {
    String nx = lexer.next();
    if (nx == null) {
      return null;
    } else {
      return new Word(nx);
    }
  }

  static boolean isProper(String posTag) {
    return posTag.equals("NNP") || posTag.equals("NNPS") || posTag.equals("NP");
  }

  public Word stem(Word w) {
    try {
      lexer.yyreset(new StringReader(w.value()));
      lexer.yybegin(Morpha.any);
      String wordRes = lexer.next();
      return new Word(wordRes);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return w;
  }

  public String stem(String word) {
    try {
      lexer.yyreset(new StringReader(word));
      lexer.yybegin(Morpha.any);
      String wordRes = lexer.next();
      return wordRes;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return word;
  }

  public WordTag stem(WordTag wT) {
    return stem(wT.word(), wT.tag());
  }

  public WordTag stem(String word, String tag) {
    return stem(word, tag, lexer, lexer.option(1));
  }

  /** Lemmatize the word, being sensitive to the tag, using the
   *  passed in lexer.
   *
   *  @param lowercase If this is true, words other than proper nouns will
   *      be changed to all lowercase.
   */
  // XXX why does this return a WordTag, and not just a String?
  public static WordTag stem(String word, String tag, Morpha lexer,
                             boolean lowercase) {
    boolean wordHasForbiddenChar = word.indexOf('_') >= 0 ||
      word.indexOf(' ') >= 0;
    String quotedWord = word;
    if (wordHasForbiddenChar) {
      try {
        // choose something unlikely. Devangari!
        quotedWord = quotedWord.replaceAll("_", "\u0960");
        quotedWord = quotedWord.replaceAll(" ", "\u0961");
      } catch (Exception e) {
        System.err.println("stem: Didn't work");
      }
    }
    String wordtag = quotedWord + "_" + tag;
    if (DEBUG) System.err.println("Trying to normalize |" + wordtag + "|");
    try {
      lexer.setOption(1, lowercase);
      lexer.yyreset(new StringReader(wordtag));
      lexer.yybegin(Morpha.scan);
      String wordRes = lexer.next();
      lexer.next(); // go past tag
      if (wordHasForbiddenChar) {
        try {
          if (DEBUG) System.err.println("Restoring forbidden chars");
          wordRes = wordRes.replaceAll("\u0960", "_");
          wordRes = wordRes.replaceAll("\u0961", " ");
        } catch (Exception e) {
          System.err.println("stem: Didn't work");
        }
      }
      return new WordTag(wordRes, tag);
    } catch (Throwable e) {
      System.err.println("Morphology.stem() had error on word " + word + "/" +
                         tag);
      if (DEBUG) e.printStackTrace();
      return new WordTag(word, tag);
    }
  }

  private static synchronized void initStaticLexer() {
    if (staticLexer == null) {
      staticLexer = new Morpha(System.in);
    }
  }

  /** Return a new WordTag which has the lemma as the value of word().
   *  The default is to lowercase non-proper-nouns, unless options have
   *  been set.
   */
  public static WordTag stemStatic(String word, String tag) {
    initStaticLexer();
    return stem(word, tag, staticLexer, staticLexer.option(1));
  }

  public static WordTag stemStatic(String word, String tag,
                                   boolean lowercase) {
    initStaticLexer();
    return stem(word, tag, staticLexer, lowercase);
  }

  public synchronized static WordTag stemStaticSynchronized(String word,
                                                            String tag) {
    return stemStatic(word, tag);
  }

  public synchronized static WordTag stemStaticSynchronized(String word,
                                                            String tag,
                                                            boolean lowercase) {
    return stemStatic(word, tag, lowercase);
  }

  /** Return a new WordTag which has the lemma as the value of word().
   *  The default is to lowercase non-proper-nouns, unless options have
   *  been set.
   */
  public static WordTag stemStatic(WordTag wT) {
    return stemStatic(wT.word(), wT.tag());
  }


  public Object apply(Object in) {
    if (in instanceof WordTag) {
      return stem((WordTag) in);
    }
    if (in instanceof Word) {
      return stem((Word) in);
    }
    return in;
  }

  /**
   * Lemmatize returning a <code>WordLemmaTag </code>.
   */
  public WordLemmaTag lemmatize(WordTag wT) {
    String tag = wT.tag();
    String word = wT.word();
    String lemma = stem(wT).word();
    return new WordLemmaTag(word, lemma, tag);
  }

  public static WordLemmaTag lemmatizeStatic(WordTag wT) {
    String tag = wT.tag();
    String word = wT.word();
    String lemma = stemStatic(wT).word();
    return new WordLemmaTag(word, lemma, tag);
  }


  /** Run the morphological analyzer.  Options are:
   *  <ul>
   *  <li>-rebuildVerbTable verbTableFile Convert a verb table from a text file
   *  (e.g., /u/nlp/data/morph/verbstem.list) to Java code contained in Morpha.flex .
   *  <li>-stem args ...  Stem each of the following arguments, which should either be
   *  in the form of just word or word/tag.
   *  <li> args ...  Each argument is a file and the contents of it are stemmed as
   *  space-separated tokens.    <i>Note:</i> If the tokens are tagged
   *  words, they must be in the format of whitespace separated word_tag pairs.
   */
  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err.println("java Morphology [-rebuildVerbTable file|-stem word+|file+]");
    } else if (args.length == 2 && args[0].equals("-rebuildVerbTable")) {
      String verbs = IOUtils.slurpFile(args[1]);
      String[] words = verbs.split("\\s+");
      System.out.print(" private static String[] verbStems = new String[] { ");
      for (int i = 0; i < words.length; i++) {
        System.out.print("\"" + words[i] + "\"");
        if (i != words.length - 1) {
          System.out.print(", ");
          if (i % 5 == 0) {
            System.out.println();
            System.out.print("    ");
          }
        }
      }
      System.out.println(" };");
    } else if (args[0].equals("-stem")) {
      for (int i = 1; i < args.length; i++) {
        System.out.println(args[i] + " --> " + stemStatic(WordTag.valueOf(args[i])));
      }
    } else {
      for (String arg :  args) {
        Morphology morph = new Morphology(arg);
        for (Word next; (next = morph.next()) != null; ) {
          System.out.print(next);
          System.out.print(" ");
        }
      }
    }
  }

}
