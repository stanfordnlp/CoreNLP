package edu.stanford.nlp.ling;

import java.io.*;
import java.util.ArrayList;


import edu.stanford.nlp.util.ErasureUtils;

/**
 * A <code>SentenceReader</code> adds functionality to a <code>Reader</code>
 * by reading in <code>Sentence</code>s, or some descendant class. Like
 * other standard classes, this class does not provide buffering, so for
 * high performance, a typical invocation of <code>SentenceReader</code>
 * would be something like:<p><code>
 * SentenceReader sr = new SentenceReader(new BufferedReader(new
 * FileReader(file)), myWordFactory);
 * </code><p>
 * It is designed for sentences to be delimited in one of two ways: either
 * by a recognizable end-of-sentence token or by an end-of-line.  If the
 * latter, the StreamTokenizer should return end-of-lines, and the
 * SentenceNormalizer should say to use them.
 *
 * @author Christopher Manning
 */
public class SentenceReader<T extends HasWord> {

  private static final boolean DEBUG = false;

  private Reader in;
  private StreamTokenizer st;
  private SentenceNormalizer<T> sn;
  private LabelFactory lf;
  private boolean eolIsSentenceEnd;


  /**
   * Construct class to read sentences from a <code>Reader</code>.
   *
   * @param in the <code>Reader</code>
   */
  public SentenceReader(Reader in) {
    this(in, new WordFactory(), new SentenceNormalizer<T>(), new PennTagbankStreamTokenizer(in));
  }


  /**
   * Construct class to read sentences from a <code>Reader</code>.
   *
   * @param in The Reader
   * @param lf The LabelFactory that creates some kind of Label
   */
  public SentenceReader(Reader in, LabelFactory lf) {
    this(in, lf, new SentenceNormalizer<T>(), new PennTagbankStreamTokenizer(in));
  }


  /**
   * Construct class to read sentences from a <code>Reader</code>.
   *
   * @param in Input stream
   * @param lf The LabelFactory that creates some kind of Label
   * @param sn the method of normalizing sentences
   */
  public SentenceReader(Reader in, LabelFactory lf, SentenceNormalizer<T> sn) {
    this(in, lf, sn, new PennTagbankStreamTokenizer(in));
  }


  /**
   * Construct class to read sentences from a <code>Reader</code>.
   * All of the arguments must
   * be provided.  They cannot be <code>null</code>.
   *
   * @param in input <code>Reader</code> from which sentences are read
   * @param lf The LabelFactory that creates some kind of Label, such as a
   *           WordFactory or a TaggedWordFactory
   * @param sn The method of normalizing sentences.
   * @param st StreamTokenizer that divides up input from Reader
   */
  public SentenceReader(Reader in, LabelFactory lf, SentenceNormalizer<T> sn, StreamTokenizer st) {
    this.in = in;
    this.lf = lf;
    this.sn = sn;
    this.st = st;
    this.eolIsSentenceEnd = sn.eolIsSentenceEnd();
  }


  /**
   * Reads a single sentence.
   *
   * @return The sentence read in.  This may be a zero length sentence
   *         (e.g., a blank line in a file where line ends indicate
   *         sentence ends).
   *         It returns <code>null</code> at (and only at) end of file.
   * @throws java.io.IOException If format is invalid
   */
  public ArrayList<T> readSentence() throws IOException {
    ArrayList<T> sent = new ArrayList<T>();
    String prev = null;
    boolean continuing = true;
    boolean isEOF = false;

    if (DEBUG) {
      System.err.println("In readSentence");
    }
    while (continuing) {
      int code = st.nextToken();
      if (DEBUG) {
        System.err.println("Read token" + code + " " + st.sval);
      }

      if (code == StreamTokenizer.TT_WORD) {
        String current = st.sval;
        String name = sn.normalizeString(current);
        sent.add(ErasureUtils.<T>uncheckedCast(lf.newLabelFromString(name)));
        if (!eolIsSentenceEnd) {
          st.nextToken(); // codeP1
          if (sn.endSentenceToken(current, prev, st.sval)) {
            continuing = false;
          }
          st.pushBack();
        }
        prev = current;
      } else if (code == StreamTokenizer.TT_EOF) {
        if (sent.size() > 0) {
          System.err.println("Warning: Sentence ended by EOF.");
        } else {
          isEOF = true;
        }
        continuing = false;
      } else if (code == StreamTokenizer.TT_EOL) {
        if (eolIsSentenceEnd) {
          continuing = false;
        }
        // else ignore it
      } else {
        throw(new IOException("expecting word or eof, found: " + code + "/" + st.sval));
      }
    }
    if (DEBUG) {
      System.err.println("Sentence normalized to " + sn.normalizeSentence(sent, lf));
    }
    if (isEOF) {
      return null;
    }
    return sn.normalizeSentence(sent, lf);
  }


  /**
   * Close the Reader behind this <code>SentenceReader</code>.
   *
   * @throws java.io.IOException If can't close file
   */
  public void close() throws IOException {
    in.close();
  }


  /**
   * Returns a String representing the type of the
   * <code>SentenceReader</code>
   * object. This includes information on the objects it contains.
   *
   * @return Representation of the <code>SentenceReader</code>
   */
  @Override
  public String toString() {
    return getClass().getName() + "[" + in.toString() + "," + st.toString() + "," + sn.toString() + "," + lf.toString() + "]";
  }


  /**
   * Loads sentences from first argument and prints them.
   * These files are assumed to be in the format of Penn Treebank
   * POS tagged sentences.
   *
   * @param args Array of command-line arguments (just filePath)
   */
  public static void main(String[] args) {
    if (args.length < 1) {
      System.err.println("Usage: java edu.stanford.nlp.ling.SentenceReader file");
      System.exit(0);
    }
    try {
      Reader r = new BufferedReader(new FileReader(args[0]));
      SentenceReader<HasWord> sr = new SentenceReader<HasWord>(r, new TaggedWordFactory(), new PennSentenceNormalizer<HasWord>(), new PennTagbankStreamTokenizer(r));
      ArrayList<HasWord> s = sr.readSentence();
      while (s != null) {
        System.out.println(Sentence.listToString(s, false));
        System.out.println();
        s = sr.readSentence();
      }
    } catch (IOException ioe) {
      ioe.printStackTrace();
    }
  }

}
