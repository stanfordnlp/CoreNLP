package edu.stanford.nlp.international.spanish.pipeline;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.StringUtils;

import java.io.File;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * We generate a USD-style dependency treebank from the original AnCora
 * dependency treebank using the HamleDT tool. This tool converts to
 * USD by first translating AnCora to Prague format, then from Prague
 * format to USD. As you might guess, the translation is lossy.
 *
 * This class synthesizes the HamleDT output (in CoNLL X format) with
 * the original AnCora data, re-inserting data from the AnCora treebank
 * that was lost in translation.
 *
 * @see <a href="http://ufal.mff.cuni.cz/hamledt">HamleDT homepage</a>
 * @author Jon Gauthier
 */
public class HamleDTCorrector {

  private String hamledtPath;
  private String ancoraPath;

  public HamleDTCorrector(String hamledtPath, String ancoraPath) {
    this.hamledtPath = hamledtPath;
    this.ancoraPath = ancoraPath;
  }

  public List<String> correct() {
    Iterator<List<CoreLabel>> hamledtSentences = new CoNLLXReader(new File(hamledtPath));
    Iterator<List<CoreLabel>> ancoraSentences = new CoNLL2009Reader(new File(ancoraPath));

    List<String> ret = new ArrayList<String>();

    List<CoreLabel> hamledtSentence = hamledtSentences.next(),
      ancoraSentence = ancoraSentences.next();
    for (; hamledtSentences.hasNext() && ancoraSentences.hasNext();
      hamledtSentence = hamledtSentences.next(), ancoraSentence = ancoraSentences.next()) {

      if (hamledtSentence == null || ancoraSentence == null)
        throw new RuntimeException("Parse exception");

      if (hamledtSentence.size() != ancoraSentence.size()) {
        throw new RuntimeException(String.format(
          "Treebank mismatch: HamleDT sentence aligned with AnCora sentence of different length%n" +
            "\tHamleDT sentence: " + StringUtils.joinWords(hamledtSentence, " ") + "%n" +
            "\tAnCora sentence: " + StringUtils.joinWords(ancoraSentence, " ")));
      }

      List<CoreLabel> corrected = correctSentence(hamledtSentence, ancoraSentence);
      ret.addAll(toCoNLLXString(corrected));
      ret.add("");
    }

    return ret;
  }

  /**
   * Correct the given HamleDT-output sentence with the given AnCora
   * sentence as extra context.
   *
   * @return A corrected form of {@code hamledtSentence}.
   */
  private List<CoreLabel> correctSentence(List<CoreLabel> hamledtSentence,
                                          List<CoreLabel> ancoraSentence) {
    List<CoreLabel> ret = new ArrayList<CoreLabel>(hamledtSentence.size());

    // First perform individual word corrections
    Iterator<CoreLabel> iHam = hamledtSentence.iterator(), iAnc = ancoraSentence.iterator();
    CoreLabel hamWord = iHam.next(), ancWord = iAnc.next();
    for (; iHam.hasNext() && iAnc.hasNext(); hamWord = iHam.next(), ancWord = iAnc.next()) {
      ret.add(correctWord(hamWord, ancWord, hamledtSentence, ancoraSentence));
    }

    return ret;
  }

  /**
   * Correct a HamleDT-output word in isolation with the given AnCora
   * word as context. May modify {@code hamledtWord} in place.
   *
   * @param hamledtWord A particular word drawn from {@code hamledtSentence}
   * @param ancoraWord Corresponding word drawn from {@code ancoraSentence}
   * @param hamledtSentence Originating sentence in HamleDT corpus
   * @param ancoraSentence Corresponding originating sentenc in AnCora corpus
   */
  private CoreLabel correctWord(CoreLabel hamledtWord, CoreLabel ancoraWord,
                                List<CoreLabel> hamledtSentence, List<CoreLabel> ancoraSentence) {
    if (!hamledtWord.word().equals(ancoraWord.word()))
      throw new RuntimeException(String.format(
        "Treebank line mismatch: HamleDT '%s' does not match AnCora line's '%s'",
        hamledtWord.word(), ancoraWord.word()));

    String depRel = correctDepRel(hamledtWord, ancoraWord);
    hamledtWord.set(CoreAnnotations.CoNLLDepTypeAnnotation.class, depRel);

    // Correct conjunctions which are given as roots of sentence
    int hParent = hamledtWord.get(CoreAnnotations.CoNLLDepParentIndexAnnotation.class);
    int aParent = ancoraWord.get(CoreAnnotations.CoNLLDepParentIndexAnnotation.class);
    if (hParent == 0 && aParent != 0) {
      // If aParent is a copula, find its head in the HamleDT parse -- this is the item of which
      // the conjunction should be a dependent
      CoreLabel aParentLabel = hamledtSentence.get(aParent - 1);
      int newParent = (aParentLabel.get(CoreAnnotations.CoNLLDepTypeAnnotation.class).equals("cop"))
        ? aParentLabel.get(CoreAnnotations.CoNLLDepParentIndexAnnotation.class)
        : aParent;

      hamledtWord.set(CoreAnnotations.CoNLLDepParentIndexAnnotation.class, newParent);
      hamledtWord.set(CoreAnnotations.CoNLLDepTypeAnnotation.class, "cc");
    }

    return hamledtWord;
  }

  /**
   * Fix the HamleDT-produced dependency relation label given the
   * AnCora line from which it was sourced.
   *
   * @return Corrected dependency relation
   */
  private String correctDepRel(CoreLabel hamledtWord, CoreLabel ancoraWord) {
    // Original dependency relation label
    String aDepRel = ancoraWord.get(CoreAnnotations.CoNLLDepTypeAnnotation.class);

    // HamleDT dependency relation label
    String depRel = hamledtWord.get(CoreAnnotations.CoNLLDepTypeAnnotation.class);

    // Vocative
    if (aDepRel.equals("voc"))
      // TODO: Handle nmods nearby that HamleDT marks as "appos" -- these should probably be nmod
      // e.g. "Señor caballero" should yield `nmod(caballero, Señor)`
      return "vocative";

    // Distinguish dative and accusative objects
    if (depRel.equals("obj")) {
      if (isDative(ancoraWord))
        // "dative" annotation retained in Stanford output as "case=dat"
        return "nmod";
      else
        return "dobj";
    }

    return depRel;
  }

  /**
   * Return true if the given label read from an AnCora-CoNLL corpus
   * is in the dative case.
   */
  private boolean isDative(CoreLabel word) {
    return word.get(CoreAnnotations.ValueAnnotation.class).contains("case=dative");
  }

  private List<String> toCoNLLXString(List<CoreLabel> sentence) {
    List<String> ret = new ArrayList<String>(sentence.size());

    for (CoreLabel l : sentence) {
      ret.add(String.format(
        "%d\t%s\t%s\t%s\t%s\t%s\t%d\t%s\t_\t_",
        l.index(), l.word(), l.lemma(), l.tag(), l.get(CoreAnnotations.CoarseTagAnnotation.class),
        l.get(CoreAnnotations.ValueAnnotation.class),
        l.get(CoreAnnotations.CoNLLDepParentIndexAnnotation.class),
        l.get(CoreAnnotations.CoNLLDepTypeAnnotation.class)
      ));
    }

    return ret;
  }

  private static final String usage = String.format(
    "Usage: java %s <hamledt_file> <ancora_file>%n%n" +
      "\t(where <hamledt_file> was produced directly from <ancora_file>,%n" +
      "\tso that the lines match up exactly)", HamleDTCorrector.class.getName());

  public static void main(String[] args) {
    if (args.length < 2) {
      System.err.println(usage);
      System.exit(1);
    }

    HamleDTCorrector corrector = new HamleDTCorrector(args[0], args[1]);
    List<String> corrected = corrector.correct();

    for (String line : corrected)
      System.out.println(line);
  }

  /**
   * Reads AnCora sentences from a CoNLL-style dependency treebank.
   */
  private abstract static class CoNLLReader implements Iterator<List<CoreLabel>> {

    private final Iterator<String> fileIterator;

    public CoNLLReader(File file) {
      fileIterator = IOUtils.readLines(file).iterator();
    }

    @Override
    public boolean hasNext() {
      return fileIterator.hasNext();
    }

    @Override
    public List<CoreLabel> next() {
      if (!hasNext())
        throw new NoSuchElementException("No more sentences");

      List<CoreLabel> sentence = new ArrayList<CoreLabel>();
      String line;

      while (fileIterator.hasNext() && !(line = fileIterator.next()).equals("")) {
        try {
          sentence.add(makeCoreLabel(line));
        } catch (ParseException e) {
          System.err.println(e.getMessage());
          return null;
        }
      }

      return sentence;
    }

    protected abstract CoreLabel makeCoreLabel(String line) throws ParseException;

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  private static class CoNLLXReader extends CoNLLReader {

    public CoNLLXReader(File file) {
      super(file);
    }

    protected CoreLabel makeCoreLabel(String line) throws ParseException {
      CoreLabel ret = new CoreLabel();

      String[] fields = line.split("\t");
      if (fields.length != 10)
        throw new ParseException("Line does not have 10 fields as CoNLL-X format specifies:'" +
                                   line + "'");

      ret.setIndex(Integer.parseInt(fields[0]));
      ret.setWord(fields[1]);
      ret.setLemma(fields[2]);
      ret.setTag(fields[3]);
      ret.set(CoreAnnotations.CoarseTagAnnotation.class, fields[4]);

      // Stick arbitrary features in value annotation
      ret.set(CoreAnnotations.ValueAnnotation.class, fields[5]);

      ret.set(CoreAnnotations.CoNLLDepParentIndexAnnotation.class, Integer.parseInt(fields[6]));
      ret.set(CoreAnnotations.CoNLLDepTypeAnnotation.class, fields[7]);

      // Ignore projective head / projective head relation fields --
      // these are null for AnCora

      return ret;
    }

  }

  private static class CoNLL2009Reader extends CoNLLReader {

    public CoNLL2009Reader(File file) {
      super(file);
    }

    protected CoreLabel makeCoreLabel(String line) throws ParseException {
      CoreLabel ret = new CoreLabel();

      String[] fields = line.split("\t");
      if (fields.length != 18)
        throw new ParseException("Line does not have 18 fields as CoNLL-09 format specifies:'" +
                                   line + "'");

      ret.setIndex(Integer.parseInt(fields[0]));
      ret.setWord(fields[1]);
      ret.setLemma(fields[2]);
      // Skip predicted lemma field (3)
      ret.setTag(fields[4]);
      // Skip predicted POS field (5)

      // Stick arbitrary features in value annotation
      ret.set(CoreAnnotations.ValueAnnotation.class, fields[6]);
      // Skip predicted features field (7)

      ret.set(CoreAnnotations.CoNLLDepParentIndexAnnotation.class, Integer.parseInt(fields[8]));
      // Skip predicted parent index field (9)
      ret.set(CoreAnnotations.CoNLLDepTypeAnnotation.class, fields[10]);
      // Skip predicted relation type field (11)

      // Ignore all other argument fields (12-18)

      return ret;
    }

  }

  static class ParseException extends Exception {

    public ParseException(String msg) {
      super(msg);
    }

  }

}
