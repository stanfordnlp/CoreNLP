package edu.stanford.nlp.sequences; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.PrintWriter;
import java.io.Reader;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.objectbank.LineIterator;
import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;

// Note: This DocumentReaderAndWriter needs to be in core because it is
// used in the truecasing Annotator (loaded by reflection).

/** A DocumentReaderAndWriter for truecasing documents.
 *  Adapted from Jenny's TrueCasingDocumentReaderAndWriter.java.
 *
 *  @author Pi-Chuan Chang
 */
public class TrueCasingForNISTDocumentReaderAndWriter implements DocumentReaderAndWriter<CoreLabel>  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(TrueCasingForNISTDocumentReaderAndWriter.class);

  public static final String THREE_CLASSES_PROPERTY = "3class";
  public static final boolean THREE_CLASSES = Boolean.parseBoolean(System.getProperty(THREE_CLASSES_PROPERTY, "false"));

  private static final long serialVersionUID = -3000389291781534479L;
  private IteratorFromReaderFactory<List<CoreLabel>> factory;
  private Boolean verboseForTrueCasing = false;
  private static final Pattern alphabet = Pattern.compile("[A-Za-z]+");

  /**
   * for test only
   **/
  public static void main(String[] args) throws IOException{
    Reader reader = new BufferedReader(new FileReader(args[0]));
    TrueCasingForNISTDocumentReaderAndWriter raw = new TrueCasingForNISTDocumentReaderAndWriter();
    raw.init(null);
    for (Iterator<List<CoreLabel>> it = raw.getIterator(reader); it.hasNext(); ) {
      List<CoreLabel> l = it.next();
      for (CoreLabel cl : l) {
        System.out.println(cl);
      }
      System.out.println("========================================");
    }
  }

  @Override
  public void init(SeqClassifierFlags flags) {
    verboseForTrueCasing = flags.verboseForTrueCasing;
    factory = LineIterator.getFactory(new LineToTrueCasesParser()); // todo
  }

  public static Set knownWords = null;

  public static boolean known(String s) {
    return knownWords.contains(s.toLowerCase());
  }

  @Override
  public Iterator<List<CoreLabel>> getIterator(Reader r) {
    return factory.getIterator(r);
  }

  @Override
  public void printAnswers(List<CoreLabel> doc, PrintWriter out) {
    List<String> sentence = new ArrayList<>();
    int wrong = 0;

    for (CoreLabel wi : doc) {
      StringBuilder sb = new StringBuilder();
      if (! wi.get(CoreAnnotations.AnswerAnnotation.class).equals(wi.get(CoreAnnotations.GoldAnswerAnnotation.class))) {
        wrong++;
      }
      if (!THREE_CLASSES && wi.get(CoreAnnotations.AnswerAnnotation.class).equals("UPPER")) {
        sb.append(wi.word().toUpperCase());
      } else if (wi.get(CoreAnnotations.AnswerAnnotation.class).equals("LOWER")) {
        sb.append(wi.word().toLowerCase());
      } else if (wi.get(CoreAnnotations.AnswerAnnotation.class).equals("INIT_UPPER")) {
        sb.append(wi.word().substring(0,1).toUpperCase())
          .append(wi.word().substring(1));
      } else if (wi.get(CoreAnnotations.AnswerAnnotation.class).equals("O")) {
        // in this case, if it contains a-z at all, then append "MIX" at the end
        sb.append(wi.word());
        Matcher alphaMatcher = alphabet.matcher(wi.word());
        if (alphaMatcher.matches()) {
          sb.append("/MIX");
        }
      }

      if (verboseForTrueCasing) {
        sb.append("/GOLD-")
          .append(wi.get(CoreAnnotations.GoldAnswerAnnotation.class))
          .append("/GUESS-")
          .append(wi.get(CoreAnnotations.AnswerAnnotation.class));
      }
      sentence.add(sb.toString());
    }
    out.print(StringUtils.join(sentence, " "));
    System.err.printf("> wrong = %d ; total = %d%n", wrong, doc.size());
    out.println();
  }

  public static class LineToTrueCasesParser implements Function<String,List<CoreLabel>> {
    private static final Pattern allLower = Pattern.compile("[^A-Z]*?[a-z]+[^A-Z]*?");
    private static final Pattern allUpper = Pattern.compile("[^a-z]*?[A-Z]+[^a-z]*?");
    private static final Pattern startUpper = Pattern.compile("[A-Z].*");
    // TODO: add classes for iPod, O'Bryant, l'Aviron, E-Group ?

    @Override
    public List<CoreLabel> apply(String line) {
      int pos = 0;

      //line = line.replaceAll(" +"," ");
      //log.info("pichuan: processing line = "+line);

      String[] toks = line.split(" ");
      List<CoreLabel> doc = new ArrayList<>(toks.length);
      for (String word : toks) {
        CoreLabel wi = new CoreLabel();
        Matcher lowerMatcher = allLower.matcher(word);

        if (lowerMatcher.matches()) {
          wi.set(CoreAnnotations.AnswerAnnotation.class, "LOWER");
          wi.set(CoreAnnotations.GoldAnswerAnnotation.class, "LOWER");
        } else {
          Matcher upperMatcher = allUpper.matcher(word);
          if (!THREE_CLASSES && upperMatcher.matches()) {
            wi.set(CoreAnnotations.AnswerAnnotation.class, "UPPER");
            wi.set(CoreAnnotations.GoldAnswerAnnotation.class, "UPPER");
          } else {
            Matcher startUpperMatcher = startUpper.matcher(word);

            boolean isINIT_UPPER; // = false;
            if (word.length() > 1) {
              String w2 = word.substring(1);
              String lcw2 = w2.toLowerCase();
              isINIT_UPPER = w2.equals(lcw2);
            } else {
              isINIT_UPPER = false;
            }

            if (startUpperMatcher.matches() && isINIT_UPPER) {
              wi.set(CoreAnnotations.AnswerAnnotation.class, "INIT_UPPER");
              wi.set(CoreAnnotations.GoldAnswerAnnotation.class, "INIT_UPPER");
            } else {
              wi.set(CoreAnnotations.AnswerAnnotation.class, "O");
              wi.set(CoreAnnotations.GoldAnswerAnnotation.class, "O");
            }
          }
        }

        wi.setWord(word.toLowerCase());
        wi.set(CoreAnnotations.PositionAnnotation.class, String.valueOf(pos).intern());
        doc.add(wi);
        pos++;
      }
      return doc;
    }
  }

}
