package edu.stanford.nlp.sequences;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.objectbank.ObjectBank;
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
      Collection<String> docs = new ArrayList<>();
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
    List<CoreLabel> list = new ArrayList<>();
    String[] lines = doc.split("\n");
    for (String line : lines) {
      if ( ! flags.deleteBlankLines || ! white.matcher(line).matches()) {
        list.add(makeCoreLabel(line));
      }
    }
    IOBUtils.entitySubclassify(list, CoreAnnotations.AnswerAnnotation.class,
            flags.backgroundSymbol, flags.entitySubclassification, flags.intern);
    return list;
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
      wi.set(CoreAnnotations.AnswerAnnotation.class, flags.backgroundSymbol);
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
    // The copy to GoldAnswerAnnotation is done before the recoding is done, and so it preserves the original coding.
    // This is important if the original coding is true, but the recoding is defective (like IOB2 to IO), since
    // it will allow correct evaluation later.
    wi.set(CoreAnnotations.GoldAnswerAnnotation.class, wi.get(CoreAnnotations.AnswerAnnotation.class));
    return wi;
  }

  /** Return the coding scheme to IOB1 coding, regardless of what was used
   *  internally (unless retainEntitySubclassification is set).
   *  This is useful for scoring against CoNLL test output.
   *
   *  @param tokens List of tokens in some NER encoding
   */
  private void deEndify(List<CoreLabel> tokens) {
    if (flags.retainEntitySubclassification) {
      return;
    }
    IOBUtils.entitySubclassify(tokens, CoreAnnotations.AnswerAnnotation.class,
            flags.backgroundSymbol, "iob1", flags.intern);
  }


  /** Write a standard CoNLL format output file.
   *
   *  @param doc The document: A List of CoreLabel
   *  @param out Where to send the answers to
   */
  @Override
  @SuppressWarnings({"StringEquality", "StringContatenationInLoop"})
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
        String gold = fl.getString(CoreAnnotations.GoldAnswerAnnotation.class);
        String guess = fl.get(CoreAnnotations.AnswerAnnotation.class);
        // System.err.println(word + "\t" + gold + "\t" + guess));
        String pos = fl.getString(CoreAnnotations.PartOfSpeechAnnotation.class);
        String chunk = fl.getString(CoreAnnotations.ChunkAnnotation.class);
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
        if ( ! ansBase.equals("O")) {
          if (ansBase.equals(lastAnsBase)) {
            if (ansPrefix.equals("B")) {
              numEntities++;
            }
          } else {
            numEntities++;
          }
        }
      }
    }
    System.out.println("File " + args[0] + " has " + numDocs + " documents, " +
            numTokens + " (non-blank line) tokens and " +
            numEntities + " entities.");
  } // end main

} // end class CoNLLDocumentReaderAndWriter
