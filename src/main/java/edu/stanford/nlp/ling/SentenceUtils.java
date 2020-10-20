package edu.stanford.nlp.ling;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import edu.stanford.nlp.util.CoreMap;

/**
 * SentenceUtils holds a couple utility methods for lists that are sentences.
 * Those include a method that nicely prints a list of words and methods that
 * construct lists of words from lists of strings.
 *
 * @author Dan Klein
 * @author Christopher Manning (generified)
 * @author John Bauer
 * @version 2010
 */
public class SentenceUtils {

  private SentenceUtils() {} // static methods

  /**
   * Create an ArrayList as a list of {@code TaggedWord} from two
   * lists of {@code String}, one for the words, and the second for
   * the tags.
   *
   * @param lex  a list whose items are of type {@code String} and
   *             are the words
   * @param tags a list whose items are of type {@code String} and
   *             are the tags
   * @return The Sentence
   */
  public static ArrayList<TaggedWord> toTaggedList(List<String> lex, List<String> tags) {
    ArrayList<TaggedWord> sent = new ArrayList<>();
    int ls = lex.size();
    int ts = tags.size();
    if (ls != ts) {
      throw new IllegalArgumentException("Sentence.toSentence: lengths differ");
    }
    for (int i = 0; i < ls; i++) {
      sent.add(new TaggedWord(lex.get(i), tags.get(i)));
    }
    return sent;
  }

  /**
   * Create an ArrayList as a list of {@code Word} from a
   * list of {@code String}.
   *
   * @param lex  a list whose items are of type {@code String} and
   *             are the words
   * @return The Sentence
   */
  //TODO wsg2010: This should be deprecated in favor of the method below with new labels
  public static ArrayList<Word> toUntaggedList(List<String> lex) {
    ArrayList<Word> sent = new ArrayList<>();
    for (String str : lex) {
      sent.add(new Word(str));
    }
    return sent;
  }

  /**
   * Create a Sentence as a list of {@code Word} objects from
   * an array of String objects.
   *
   * @param words  The words to make it from
   * @return The Sentence
   */
  //TODO wsg2010: This should be deprecated in favor of the method below with new labels
  public static ArrayList<Word> toUntaggedList(String... words) {
    ArrayList<Word> sent = new ArrayList<>();
    for (String str : words) {
      sent.add(new Word(str));
    }
    return sent;
  }

  public static List<HasWord> toWordList(String... words) {
    List<HasWord> sent = new ArrayList<>();
    for (String word : words) {
      CoreLabel cl = new CoreLabel();
      cl.setValue(word);
      cl.setWord(word);
      sent.add(cl);
    }
    return sent;
  }

  /**
   * Create a sentence as a List of {@code CoreLabel} objects from
   * an array (or varargs) of String objects.
   *
   * @param words The words to make it from
   * @return The Sentence
   */
  public static List<CoreLabel> toCoreLabelList(String... words) {
    List<CoreLabel> sent = new ArrayList<>(words.length);
    for (String word : words) {
      CoreLabel cl = new CoreLabel();
      cl.setValue(word);
      cl.setWord(word);
      sent.add(cl);
    }
    return sent;
  }

  /**
   * Create a sentence as a List of {@code CoreLabel} objects from
   * a List of other label objects.
   *
   * @param words The words to make it from
   * @return The Sentence
   */
  public static List<CoreLabel> toCoreLabelList(List<? extends HasWord> words) {
    List<CoreLabel> sent = new ArrayList<>(words.size());
    for (HasWord word : words) {
      CoreLabel cl = new CoreLabel();
      if (word instanceof Label) {
        cl.setValue(((Label) word).value());
      }
      cl.setWord(word.word());
      if (word instanceof HasTag) {
        cl.setTag(((HasTag) word).tag());
      }
      if (word instanceof HasLemma) {
        cl.setLemma(((HasLemma) word).lemma());
      }
      sent.add(cl);
    }
    return sent;
  }

  /**
   * Returns the sentence as a string with a space between words.
   * It prints out the {@code value()} of each item -
   * this will give the expected answer for a short form representation
   * of the "sentence" over a range of cases.  It is equivalent to
   * calling {@code toString(true)}.
   *
   * TODO: Sentence used to be a subclass of ArrayList, with this
   * method as the toString.  Therefore, there may be instances of
   * ArrayList being printed that expect this method to be used.
   *
   * @param list The tokenized sentence to print out
   * @return The tokenized sentence as a String
   */
  public static <T> String listToString(List<T> list) {
    return listToString(list, true);
  }


  /**
   * Returns the sentence as a string with a space between words.
   * Designed to work robustly, even if the elements stored in the
   * 'Sentence' are not of type Label.
   *
   * This one uses the default separators for any word type that uses
   * separators, such as TaggedWord.
   *
   * @param list The tokenized sentence to print out
   * @param justValue If {@code true} and the elements are of type
   *                  {@code Label}, return just the
   *                  {@code value()} of the {@code Label} of each word;
   *                  otherwise,
   *                  call the {@code toString()} method on each item.
   * @return The sentence in String form
   */
  public static <T> String listToString(List<T> list, final boolean justValue) {
    return listToString(list, justValue, null);
  }

  /**
   * As already described, but if separator is not null, then objects
   * such as TaggedWord
   *
   * @param separator The string used to separate Word and Tag
   *                  in TaggedWord, etc
   */
  public static <T> String listToString(List<T> list, final boolean justValue,
                                        final String separator) {
    StringBuilder s = new StringBuilder();
    for (Iterator<T> wordIterator = list.iterator(); wordIterator.hasNext();) {
      T o = wordIterator.next();
      s.append(wordToString(o, justValue, separator));
      if (wordIterator.hasNext()) {
        s.append(' ');
      }
    }
    return s.toString();
  }

  /**
   * Pretty print CoreMap classes using the same semantics as the toShorterString method.
   */
  public static <T extends CoreMap> String listToString(List<T> list, String... keys) {
    StringBuilder s = new StringBuilder();
    for (Iterator<T> wordIterator = list.iterator(); wordIterator.hasNext();) {
      T o = wordIterator.next();
      s.append(o.toShorterString(keys));
      if (wordIterator.hasNext()) {
        s.append(' ');
      }
    }
    return s.toString();
  }


  /**
   * Returns the sentence as a string, based on the original text and spacing
   * prior to tokenization.
   * This method assumes that this extra information has been encoded in CoreLabel
   * objects for each token of the sentence, which do have the original spacing
   * preserved (done with "invertible=true" for PTBTokenizer).
   * However, the method has loose typing for easier inter-operation
   * with old code that still works with a {@code List<HasWord>}.
   *
   * @param list The sentence (List of tokens) to print out
   * @return The original sentence String, which may contain newlines or other artifacts of spacing
   */
  public static <T extends HasWord> String listToOriginalTextString(List<T> list) {
    return listToOriginalTextString(list, true);
  }

  /**
   * Returns the sentence as a string, based on the original text and spacing
   * prior to tokenization.
   * This method assumes that this extra information has been encoded in CoreLabel
   * objects for each token of the sentence, which do have the original spacing
   * preserved (done with "invertible=true" for PTBTokenizer). If that information
   * is not there, you will see null outputs, and if you do not pass in a List
   * of CoreLabel objects, then the code will Exception.
   * The method has loose typing for easier inter-operation
   * with old code that still works with a {@code List<HasWord>}.
   *
   * @param list The sentence (List of tokens) to print out
   * @param printBeforeBeforeStart Whether to print the BeforeAnnotation before the first token
   *                               of the sentence. (In general, the BeforeAnnotation is the same
   *                               as the AfterAnnotation of the preceding token. So, usually this
   *                               is correct to do only for the first sentence of a text.)
   * @return The original sentence String, which may contain newlines or other artifacts of spacing
   */
  public static <T extends HasWord> String listToOriginalTextString(List<T> list, boolean printBeforeBeforeStart) {
    if (list == null) {
      return null;
    }
    StringBuilder s = new StringBuilder();
    for (HasWord word : list) {
      CoreLabel cl = (CoreLabel) word;
      // check if this is an MWT
      // skip all but the first MWT token in that case
      if (cl.containsKey(CoreAnnotations.IsMultiWordTokenAnnotation.class) && cl.isMWT() && !cl.isMWTFirst()) {
        continue;
      }
      if (printBeforeBeforeStart) {
        // Only print Before for first token, since otherwise same as After of previous token
        // BUG: if you print a sequence of sentences, you double up between sentence spacing.
        if (cl.get(CoreAnnotations.BeforeAnnotation.class) != null) {
          s.append(cl.get(CoreAnnotations.BeforeAnnotation.class));
        }
        printBeforeBeforeStart = false;
      }
      if (cl.containsKey(CoreAnnotations.IsMultiWordTokenAnnotation.class) && cl.isMWT()) {
        s.append(cl.get(CoreAnnotations.MWTTokenTextAnnotation.class));
      } else {
        s.append(cl.get(CoreAnnotations.OriginalTextAnnotation.class));
      }
      if (cl.get(CoreAnnotations.AfterAnnotation.class) != null) {
        s.append(cl.get(CoreAnnotations.AfterAnnotation.class));
      } else {
        s.append(' ');
      }
    }
    return s.toString();
  }

  public static <T> String wordToString(T o, final boolean justValue) {
    return wordToString(o, justValue, null);
  }

  public static <T> String wordToString(T o, final boolean justValue,
                                        final String separator) {
    if (justValue && o instanceof Label) {
      if (o instanceof CoreLabel) {
        CoreLabel l = (CoreLabel) o;
        String w = l.value();
        if (w == null)
          w = l.word();
        return w;
      } else {
        return (((Label) o).value());
      }
    } else if (o instanceof CoreLabel) {
      CoreLabel l = ((CoreLabel) o);
      String w = l.value();
      if (w == null)
        w = l.word();
      if (l.tag() != null) {
        if (separator == null) {
          return w + CoreLabel.TAG_SEPARATOR + l.tag();
        } else {
          return w + separator + l.tag();
        }
      }
      return w;
      // an interface that covered these next four cases would be
      // nice, but we're moving away from these data types anyway
    } else if (separator != null && o instanceof TaggedWord) {
      return ((TaggedWord) o).toString(separator);
    } else if (separator != null && o instanceof LabeledWord) {
      return ((LabeledWord) o).toString(separator);
    } else if (separator != null && o instanceof WordLemmaTag) {
      return ((WordLemmaTag) o).toString(separator);
    } else if (separator != null && o instanceof WordTag) {
      return ((WordTag) o).toString(separator);
    } else {
      return (o.toString());
    }
  }

  /**
   * Returns the substring of the sentence from start (inclusive)
   * to end (exclusive).
   *
   * @param start Leftmost index of the substring
   * @param end Rightmost index of the ngram
   * @return The ngram as a String. Currently returns null if one of the indices is out of bounds.
   *         But maybe it should exception instead.
   */
  public static <T> String extractNgram(List<T> list, int start, int end) {
    if (start < 0 || end > list.size() || start >= end) return null;
    final StringBuilder sb = new StringBuilder();
    for (int i = start; i < end; i++) {
      T o = list.get(i);
      if (sb.length() != 0) sb.append(' ');
      sb.append((o instanceof HasWord) ? ((HasWord) o).word() : o.toString());
    }
    return sb.toString();
  }

}
