package edu.stanford.nlp.sequences;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.util.PaddedList;
import edu.stanford.nlp.util.AbstractIterator;

import java.util.*;
import java.util.regex.*;
import java.io.*;

/**
 * DocumentReader for the original CoNLL 03 format.  In this format, there is
 * one word per line, with extra attributes of a word (POS tag, chunk, etc.) in
 * other space or tab separated columns, where leading and trailing whitespace
 * on the line are ignored.  Sentences are supposedly
 * separated by a blank line (one with no non-whitespace characters), but
 * where blank lines occur is in practice often fairly random. In particular,
 * sometimes entities span blank lines.  Nevertheless, in this class, like in
 * our original CoNLL system, these blank lines are preserved as a special
 * BOUNDARY token and detected and exploited by some features. The text is
 * divided into documents at each '-DOCSTART-' token, which is seen as a
 * special token, which is also preserved.  The reader can read data in any
 * of the IOB/IOE/etc. formats and output tokens in any other, based on the
 * entitySubclassification flag.
 * <p>
 * This reader is specifically for replicating CoNLL systems. For normal use,
 * you should use the saner ColumnDocumentReaderAndWriter.
 *
 * @author Jenny Finkel
 * @author Huy Nguyen
 * @author Christopher Manning
 */
public class CoNLLDocumentReaderAndWriter implements DocumentReaderAndWriter<CoreLabel> {

  private static final long serialVersionUID = 6281374154299530460L;

  public static final String BOUNDARY = "*BOUNDARY*";
  public static final String OTHER = "O";
  /** Historically, this reader used to treat the whole input as one document, but now it doesn't */
  private static final boolean TREAT_FILE_AS_ONE_DOCUMENT = false;
  private static final Pattern docPattern = Pattern.compile("^\\s*-DOCSTART-\\s");
  private static final Pattern white = Pattern.compile("^\\s*$");


  private SeqClassifierFlags flags; // = null;


  @Override
  public void init(SeqClassifierFlags flags) {
    this.flags = flags;
  }

  @Override
  public String toString() {
    return "CoNLLDocumentReaderAndWriter[entitySubclassification: " +
        flags.entitySubclassification + ", intern: " + flags.intern + ']';
  }


  @Override
  public Iterator<List<CoreLabel>> getIterator(Reader r) {
    return new CoNLLIterator(r);
  }

  private class CoNLLIterator extends AbstractIterator<List<CoreLabel>> {

    public CoNLLIterator (Reader r) {
      stringIter = splitIntoDocs(r);
    }

    @Override
    public boolean hasNext() { return stringIter.hasNext(); }

    @Override
    public List<CoreLabel> next() { return processDocument(stringIter.next()); }

    private Iterator<String> stringIter; // = null;

  } // end class CoNLLIterator


  private static Iterator<String> splitIntoDocs(Reader r) {
    if (TREAT_FILE_AS_ONE_DOCUMENT) {
      return Collections.singleton(IOUtils.slurpReader(r)).iterator();
    } else {
      Collection<String> docs = new ArrayList<String>();
      ObjectBank<String> ob = ObjectBank.getLineIterator(r);
      StringBuilder current = new StringBuilder();
      for (String line : ob) {
        if (docPattern.matcher(line).lookingAt()) {
          // Start new doc, store old one if non-empty
          if (current.length() > 0) {
            docs.add(current.toString());
            current = new StringBuilder();
          }
        }
        current.append(line);
        current.append('\n');
      }
      if (current.length() > 0) {
        docs.add(current.toString());
      }
      return docs.iterator();
    }
  }


  private List<CoreLabel> processDocument(String doc) {
    List<CoreLabel> lis = new ArrayList<CoreLabel>();
    String[] lines = doc.split("\n");
    for (String line : lines) {
      if ( ! flags.deleteBlankLines || ! white.matcher(line).matches()) {
        lis.add(makeCoreLabel(line));
      }
    }
    entitySubclassify(lis, flags.entitySubclassification);
    return lis;
  }

  /**
   * This can be used on the CoNLL data to map from a representation where
   * normally entities were marked I-PERS, but the beginning of non-first
   * items of an entity sequences were marked B-PERS (IOB1 representation).
   * It changes this representation to other representations:
   * a 4 way representation of all entities, like S-PERS, B-PERS,
   * I-PERS, E-PERS for single word, beginning, internal, and end of entity
   * (SBIEO); always marking the first word of an entity (IOB2);
   * the reverse IOE1 and IOE2 and IO.
   * This code is very specific to the particular CoNLL way of labeling
   * classes.  It will work on any of these styles of input. However, note
   * that IO is a lossy mapping, which necessarily loses information if
   * two entities of the same class are adjacent.
   * If the labels are not of the form "X-Y+" then they will be
   * left unaltered, regardless of the value of style.
   *
   * @param tokens List of read in tokens with AnswerAnnotation
   * @param style Output style; one of iob[12], ioe[12], io, sbieo
   */
  private void entitySubclassify(List<CoreLabel> tokens,
                                 String style) {
    int how;
    if ("iob1".equalsIgnoreCase(style)) {
      how = 0;
    } else if ("iob2".equalsIgnoreCase(style)) {
      how = 1;
    } else if ("ioe1".equalsIgnoreCase(style)) {
      how = 2;
    } else if ("ioe2".equalsIgnoreCase(style)) {
      how = 3;
    } else if ("io".equalsIgnoreCase(style)) {
      how = 4;
    } else if ("sbieo".equalsIgnoreCase(style)) {
      how = 5;
    } else {
      System.err.println("entitySubclassify: unknown style: " + style);
      how = 4;
    }
    tokens = new PaddedList<CoreLabel>(tokens, new CoreLabel());
    int k = tokens.size();
    String[] newAnswers = new String[k];
    for (int i = 0; i < k; i++) {
      final CoreLabel c = tokens.get(i);
      final CoreLabel p = tokens.get(i - 1);
      final CoreLabel n = tokens.get(i + 1);
      final String cAns = c.get(CoreAnnotations.AnswerAnnotation.class);
      if (cAns.length() > 1 && cAns.charAt(1) == '-') {
        String pAns = p.get(CoreAnnotations.AnswerAnnotation.class);
        if (pAns == null) { pAns = OTHER; }
        String nAns = n.get(CoreAnnotations.AnswerAnnotation.class);
        if (nAns == null) { nAns = OTHER; }
        final String base = cAns.substring(2, cAns.length());
        String pBase = (pAns.length() > 2 ? pAns.substring(2, pAns.length()) : pAns);
        String nBase = (nAns.length() > 2 ? nAns.substring(2, nAns.length()) : nAns);
        char prefix = cAns.charAt(0);
        char pPrefix = (pAns.length() > 0) ? pAns.charAt(0) : ' ';
        char nPrefix = (nAns.length() > 0) ? nAns.charAt(0) : ' ';
        boolean isStartAdjacentSame = base.equals(pBase) &&
          (prefix == 'B' || prefix == 'S' || pPrefix == 'E' || pPrefix == 'S');
        boolean isEndAdjacentSame = base.equals(nBase) &&
          (prefix == 'E' || prefix == 'S' || nPrefix == 'B' || pPrefix == 'S');
        boolean isFirst = (!base.equals(pBase)) || cAns.charAt(0) == 'B';
        boolean isLast = (!base.equals(nBase)) || nAns.charAt(0) == 'B';
        switch (how) {
        case 0:
          if (isStartAdjacentSame) {
            newAnswers[i] = intern("B-" + base);
          } else {
            newAnswers[i] = intern("I-" + base);
          }
          break;
        case 1:
          if (isFirst) {
            newAnswers[i] = intern("B-" + base);
          } else {
            newAnswers[i] = intern("I-" + base);
          }
          break;
        case 2:
          if (isEndAdjacentSame) {
            newAnswers[i] = intern("E-" + base);
          } else {
            newAnswers[i] = intern("I-" + base);
          }
          break;
        case 3:
          if (isLast) {
            newAnswers[i] = intern("E-" + base);
          } else {
            newAnswers[i] = intern("I-" + base);
          }
          break;
        case 4:
          newAnswers[i] = intern("I-" + base);
          break;
        case 5:
          if (isFirst && isLast) {
            newAnswers[i] = intern("S-" + base);
          } else if ((!isFirst) && isLast) {
            newAnswers[i] = intern("E-" + base);
          } else if (isFirst && (!isLast)) {
            newAnswers[i] = intern("B-" + base);
          } else {
            newAnswers[i] = intern("I-" + base);
          }
        }
      } else {
        newAnswers[i] = cAns;
      }
    }
    for (int i = 0; i < k; i++) {
      CoreLabel c = tokens.get(i);
      c.set(CoreAnnotations.AnswerAnnotation.class, newAnswers[i]);
    }
  }


  /** This deals with the CoNLL files for different languages which have
   *  between 2 and 5 columns on non-blank lines.
   *
   *  @param line A line of CoNLL input
   *  @return The constructed token
   */
  private CoreLabel makeCoreLabel(String line) {
    CoreLabel wi = new CoreLabel();
    // wi.line = line;
    String[] bits = line.split("\\s+");
    switch (bits.length) {
    case 0:
    case 1:
      wi.setWord(BOUNDARY);
      wi.set(CoreAnnotations.AnswerAnnotation.class, OTHER);
      break;
    case 2:
      wi.setWord(bits[0]);
      wi.set(CoreAnnotations.AnswerAnnotation.class, bits[1]);
      break;
    case 3:
      wi.setWord(bits[0]);
      wi.setTag(bits[1]);
      wi.set(CoreAnnotations.AnswerAnnotation.class, bits[2]);
      break;
    case 4:
      wi.setWord(bits[0]);
      wi.setTag(bits[1]);
      wi.set(CoreAnnotations.ChunkAnnotation.class, bits[2]);
      wi.set(CoreAnnotations.AnswerAnnotation.class, bits[3]);
      break;
    case 5:
      if (flags.useLemmaAsWord) {
        wi.setWord(bits[1]);
      } else {
        wi.setWord(bits[0]);
        }
      wi.set(CoreAnnotations.LemmaAnnotation.class, bits[1]);
      wi.setTag(bits[2]);
      wi.set(CoreAnnotations.ChunkAnnotation.class, bits[3]);
      wi.set(CoreAnnotations.AnswerAnnotation.class, bits[4]);
      break;
    default:
      throw new RuntimeIOException("Unexpected input (many fields): " + line);
    }
    wi.set(CoreAnnotations.OriginalAnswerAnnotation.class, wi.get(CoreAnnotations.AnswerAnnotation.class));
    return wi;
  }

  private String intern(String s) {
    if (flags.intern) {
      return s.intern();
    } else {
      return s;
    }
  }

  /** Return the coding scheme to IOB1 coding, regardless of what was used
   *  internally. This is useful for scoring against CoNLL test output.
   *
   *  @param tokens List of tokens in some NER encoding
   */
  private void deEndify(List<CoreLabel> tokens) {
    if (flags.retainEntitySubclassification) {
      return;
    }
    tokens = new PaddedList<CoreLabel>(tokens, new CoreLabel());
    int k = tokens.size();
    String[] newAnswers = new String[k];
    for (int i = 0; i < k; i++) {
      CoreLabel c = tokens.get(i);
      CoreLabel p = tokens.get(i - 1);
      if (c.get(CoreAnnotations.AnswerAnnotation.class).length() > 1 && c.get(CoreAnnotations.AnswerAnnotation.class).charAt(1) == '-') {
        String base = c.get(CoreAnnotations.AnswerAnnotation.class).substring(2);
        String pBase = (p.get(CoreAnnotations.AnswerAnnotation.class).length() <= 2 ? p.get(CoreAnnotations.AnswerAnnotation.class) : p.get(CoreAnnotations.AnswerAnnotation.class).substring(2));
        boolean isSecond = (base.equals(pBase));
        boolean isStart = (c.get(CoreAnnotations.AnswerAnnotation.class).charAt(0) == 'B' || c.get(CoreAnnotations.AnswerAnnotation.class).charAt(0) == 'S');
        if (isSecond && isStart) {
          newAnswers[i] = intern("B-" + base);
        } else {
          newAnswers[i] = intern("I-" + base);
        }
      } else {
        newAnswers[i] = c.get(CoreAnnotations.AnswerAnnotation.class);
      }
    }
    for (int i = 0; i < k; i++) {
      CoreLabel c = tokens.get(i);
      c.set(CoreAnnotations.AnswerAnnotation.class, newAnswers[i]);
    }
  }


  /** Write a standard CoNLL format output file.
   *
   *  @param doc The document: A List of CoreLabel
   *  @param out Where to send the answers to
   */
  @Override
  @SuppressWarnings({"StringEquality"})
  public void printAnswers(List<CoreLabel> doc, PrintWriter out) {
    // boolean tagsMerged = flags.mergeTags;
    // boolean useHead = flags.splitOnHead;

    if ( ! "iob1".equalsIgnoreCase(flags.entitySubclassification)) {
      deEndify(doc);
    }

    for (CoreLabel fl : doc) {
      String word = fl.word();
      if (word == BOUNDARY) { // Using == is okay, because it is set to constant
        out.println();
      } else {
        String gold = fl.get(CoreAnnotations.OriginalAnswerAnnotation.class);
        if(gold == null) gold = "";
        String guess = fl.get(CoreAnnotations.AnswerAnnotation.class);
        // System.err.println(fl.word() + "\t" + fl.get(CoreAnnotations.AnswerAnnotation.class) + "\t" + fl.get(CoreAnnotations.AnswerAnnotation.class));
        String pos = fl.tag();
        String chunk = (fl.get(CoreAnnotations.ChunkAnnotation.class) == null ? "" : fl.get(CoreAnnotations.ChunkAnnotation.class));
        out.println(fl.word() + '\t' + pos + '\t' + chunk + '\t' +
                    gold + '\t' + guess);
      }
    }
  }

  /** Count some stats on what occurs in a file.
   */
  public static void main(String[] args) throws IOException, ClassNotFoundException {
    CoNLLDocumentReaderAndWriter f = new CoNLLDocumentReaderAndWriter();
    f.init(new SeqClassifierFlags());
    int numDocs = 0;
    int numTokens = 0;
    int numEntities = 0;
    String lastAnsBase = "";
    for (Iterator<List<CoreLabel>> it = f.getIterator(new FileReader(args[0])); it.hasNext(); ) {
      List<CoreLabel> doc = it.next();
      numDocs++;
      for (CoreLabel fl : doc) {
        // System.out.println("FL " + (++i) + " was " + fl);
        if (fl.word().equals(BOUNDARY)) {
          continue;
        }
        String ans = fl.get(CoreAnnotations.AnswerAnnotation.class);
        String ansBase;
        String ansPrefix;
        String[] bits = ans.split("-");
        if (bits.length == 1) {
          ansBase = bits[0];
          ansPrefix = "";
        } else {
          ansBase = bits[1];
          ansPrefix = bits[0];
        }
        numTokens++;
        if (ansBase.equals("O")) {
           // don't need to do anything
        } else if (ansBase.equals(lastAnsBase)) {
          if (ansPrefix.equals("B")) {
            numEntities++;
          }
        } else {
          numEntities++;
        }
      }
    }
    System.out.println("File " + args[0] + " has " + numDocs + " documents, " +
            numTokens + " (non-blank line) tokens and " +
            numEntities + " entities.");
  } // end main

} // end class CoNLLDocumentReaderAndWriter
