package edu.stanford.nlp.process;


import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.BasicDocument;
import edu.stanford.nlp.ling.Document;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.Word;


/**
 * Transforms a Document of Words into a document all or partly of
 * TaggedWords by breaking words on a tag divider character.
 *
 * @author Teg Grenager (grenager@stanford.edu)
 * @author Christopher Manning
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 *
 * @param <L> The type of the labels
 * @param <F> The type of the features
 */
public class WordToTaggedWordProcessor<IN extends HasWord, L, F> extends AbstractListProcessor<IN, HasWord, L, F> {

  /**
   * The char that we will split on.
   */
  protected char splitChar;

  /**
   * Returns a new Document where each Word with a tag has been converted
   * to a TaggedWord.  Things in the input which don't implement HasWord
   * will be deleted in the output.  Things which do will be scanned for
   * being word + splitChar + tag.  If they are, they are split up and
   * inserted as TaggedWords, otherwise they are added to the document
   * with their current type.  More precisely, they will be split on the
   * last instance of splitChar with index above 0.  This will give the
   * correct split, providing tags don't include the splitChar, regardless
   * of escaping, and will not allow an empty or null word - you can think
   * of the first character as always being escaped.
   *
   * @param words The input Document (should be of HasWords)
   * @return A new Document, perhaps with some of the things TaggedWords
   */
  public List<HasWord> process(List<? extends IN> words) {
    List<HasWord> result = new ArrayList<>();
    for (HasWord w : words) {
      result.add(splitTag(w));
    }
    return result;
  }

  /**
   * Splits the Word w on the character splitChar.
   */
  private HasWord splitTag(HasWord w) {
    if (splitChar == 0) {
      return w;
    }
    String s = w.word();
    int split = s.lastIndexOf(splitChar);
    if (split <= 0) {    // == 0 isn't allowed - no empty words!
      return w;
    }
    String word = s.substring(0, split);
    String tag = s.substring(split + 1, s.length());
    return new TaggedWord(word, tag);
  }


  /**
   * Create a <code>WordToTaggedWordProcessor</code> using the default
   * forward slash character to split on.
   */
  public WordToTaggedWordProcessor() {
    this('/');
  }

  /**
   * Flexibly set the tag splitting chars.  A splitChar of 0 is
   * interpreted to mean never split off a tag.
   *
   * @param splitChar The character at which to split
   */
  public WordToTaggedWordProcessor(char splitChar) {
    this.splitChar = splitChar;
  }

  /**
   * This will print out some text, recognizing tags.  It can be used to
   * test tag breaking.  <br>  Usage: <code>
   * java edu.stanford.nlp.process.WordToTaggedWordProcessor fileOrUrl
   * </code>
   *
   * @param args Command line argument: a file or URL
   */
  public static void main(String[] args) {
    if (args.length != 1) {
      System.out.println("usage: java edu.stanford.nlp.process.WordToTaggedWordProcessor fileOrUrl");
      System.exit(0);
    }
    String filename = args[0];
    try {
      Document<HasWord, Word, Word> d;
      if (filename.startsWith("http://")) {
        Document<HasWord, Word, Word> dpre = new BasicDocument<HasWord>().init(new URL(filename));
        DocumentProcessor<Word, Word, HasWord, Word> notags = new StripTagsProcessor<>();
        d = notags.processDocument(dpre);
      } else {
        d = new BasicDocument<HasWord>().init(new File(filename));
      }
      DocumentProcessor<Word, HasWord, HasWord, Word> proc = new WordToTaggedWordProcessor<>();
      Document<HasWord, Word, HasWord> sentd = proc.processDocument(d);
      // System.out.println(sentd);
      int i = 0;
      for (HasWord w : sentd) {
        System.out.println(i + ": " + w);
        i++;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
