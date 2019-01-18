package edu.stanford.nlp.ling;

import edu.stanford.nlp.process.Morphology;

/**
 * A WordLemmaTag corresponds to a pair of a tagged (e.g., for part of speech)
 * word and its lemma. WordLemmaTag is implemented with String-valued word,
 * lemma and tag.
 * It implements the Label interface; the {@code value()} method for that
 * interface corresponds to the word of the WordLemmaTag.
 * <p/>
 * The equality relation for WordLemmaTag is defined as identity of
 * word, lemma and tag.
 *
 * @author Marie-Catherine de Marneffe
 */
public class WordLemmaTag implements Label, Comparable<WordLemmaTag>, HasWord, HasTag {

  private String word;
  private String lemma;
  private String tag;
  private static final String DIVIDER = "/";

  public WordLemmaTag(String word) {
    this.word = word;
    this.lemma = null;
    setTag(null);
  }

  public WordLemmaTag(Label word) {
    this(word.value());
  }

  public WordLemmaTag() {
  }

  /**
   * Create a new {@code WordLemmaTag}.
   *
   * @param word This word is set as the word of this Label
   * @param tag  The {@code value()} of this Label is set as the
   *             tag of this Label
   */
  public WordLemmaTag(String word, String tag) {
    WordTag wT = new WordTag(word, tag);
    this.word = word;
    this.lemma = Morphology.stemStatic(wT).word();
    setTag(tag);
  }

  /**
   * Create a new {@code WordLemmaTag}.
   *
   * @param word  This word is passed to the supertype constructor
   * @param lemma The lemma is set as the lemma of this Label
   * @param tag   The {@code value()} of this Label is set as the
   *              tag of this Label
   */
  public WordLemmaTag(String word, String lemma, String tag) {
    this(word);
    this.lemma = lemma;
    setTag(tag);
  }

  /**
   * Create a new {@code WordLemmaTag} from a Label.  The value of
   * the Label corresponds to the word of the WordLemmaTag.
   *
   * @param word This word is passed to the supertype constructor
   * @param tag  The {@code value()} of this Label is set as the
   *             tag of this Label
   */
  public WordLemmaTag(Label word, Label tag) {
    this(word);
    WordTag wT = new WordTag(word, tag);
    this.lemma = Morphology.stemStatic(wT).word();
    setTag(tag.value());
  }


  /**
   * Return a String representation of just the "main" value of this Label.
   *
   * @return the "value" of the Label
   */
  @Override
  public String value() {
    return word;
  }

  @Override
  public String word() {
    return value();
  }

  /**
   * Set the value for the Label.
   *
   * @param value the value for the Label
   */
  @Override
  public void setValue(String value) {
    word = value;
  }

  @Override
  public void setWord(String word) {
    setValue(word);
  }

  public void setLemma(String lemma) {
    this.lemma = lemma;
  }

  /**
   * Set the tag for the Label.
   *
   * @param tag the value for the Label
   */
  @Override
  public final void setTag(String tag) {
    this.tag = tag;
  }

  @Override
  public String tag() {
    return tag;
  }

  public String lemma() {
    return lemma;
  }

  /**
   * Return a String representation of the Label.  For a multipart Label,
   * this will return all parts.
   *
   * @return a text representation of the full label contents: word/lemma/tag
   */
  @Override
  public String toString() {
    return toString(DIVIDER);
  }

  public String toString(String divider) {
    return word() + divider + lemma + divider + tag;
  }


  /**
   * The String is divided according to the divider character (usually, "/").
   * We assume that we can always just divide on the rightmost divider character,
   * rather than trying to parse up escape sequences.  If the divider character isn't found
   * in the word, then the whole string becomes the word, and lemma and tag
   * are {@code null}.
   * We assume that if only one divider character is found, word and tag are present in
   * the String, and lemma will be computed.
   *
   * @param labelStr The word that will go into the {@code WordLemmaTag}
   */
  @Override
  public void setFromString(String labelStr) {
    setFromString(labelStr, DIVIDER);
  }

  public void setFromString(String labelStr, String divider) {
    int first = labelStr.indexOf(divider);
    int second = labelStr.lastIndexOf(divider);
    if (first == second) {
      setWord(labelStr.substring(0, first));
      setTag(labelStr.substring(first + 1));
      setLemma(Morphology.lemmaStatic(labelStr.substring(0, first), labelStr.substring(first + 1)));
    } else if (first >= 0) {
      setWord(labelStr.substring(0, first));
      setLemma(labelStr.substring(first + 1, second));
      setTag(labelStr.substring(second + 1));
    } else {
      setWord(labelStr);
      setLemma(null);
      setTag(null);
    }
  }

  /**
   * Equality is satisfied only if the compared object is a WordLemmaTag
   * and has String-equal word, lemma and tag fields.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof WordLemmaTag)) return false;

    final WordLemmaTag other = (WordLemmaTag) o;
    return word().equals(other.word()) && lemma().equals(other.lemma()) &&
           tag().equals(other.tag());
  }


  @Override
  public int hashCode() {
    int result;
    result = (word != null ? word.hashCode() : 3);
    result = 29 * result + (tag != null ? tag.hashCode() : 0);
    result = 29 * result + (lemma != null ? lemma.hashCode() : 0);
    return result;
  }


  /**
   * Orders first by word, then by lemma, then by tag.
   *
   * @param wordLemmaTag object to compare to
   * @return result (positive if {@code this} is greater than
   *         {@code obj}, 0 if equal, negative otherwise)
   */
  @Override
  public int compareTo(WordLemmaTag wordLemmaTag) {
    int first = word().compareTo(wordLemmaTag.word());
    if (first != 0)
      return first;
    int second = lemma().compareTo(wordLemmaTag.lemma());
    if (second != 0)
      return second;
    else
      return tag().compareTo(wordLemmaTag.tag());
  }


  /**
   * Return a factory for this kind of label
   * (i.e., {@code TaggedWord}).
   * The factory returned is always the same one (a singleton).
   *
   * @return The label factory
   */
  @Override
  public LabelFactory labelFactory() {
    return new WordLemmaTagFactory();
  }


  /*for debugging only*/
  public static void main(String[] args) {
    WordLemmaTag wLT = new WordLemmaTag();
    wLT.setFromString("hunter/NN");

    System.out.println(wLT.word());
    System.out.println(wLT.lemma());
    System.out.println(wLT.tag());

    WordLemmaTag wLT2 = new WordLemmaTag();
    wLT2.setFromString("bought/buy/V");
    System.out.println(wLT2.word());
    System.out.println(wLT2.lemma());
    System.out.println(wLT2.tag());

    WordLemmaTag wLT3 = new WordLemmaTag();
    wLT2.setFromString("life");
    System.out.println(wLT3.word());
    System.out.println(wLT3.lemma());
    System.out.println(wLT3.tag());

  }

  private static final long serialVersionUID = -5993410244163988138L;

}
