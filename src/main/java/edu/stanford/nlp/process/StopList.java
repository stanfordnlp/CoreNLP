package edu.stanford.nlp.process;


import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.util.Generics;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Set;

/**
 * Simple stoplist class.
 *
 * @author Sepandar Kamvar
 */

public class StopList {
  private Set<Word> wordSet;

  /*
	 *     Constructs a stoplist with very few stopwords.
  */

  public StopList() {
    wordSet = Generics.newHashSet();
    addGenericWords();
  }

  /**
   * Constructs a new stoplist from the contents of a file. It is
   * assumed that the file contains stopwords, one on a line.
   * The stopwords need not be in any order.
   */

  public StopList(File list) {
    wordSet = Generics.newHashSet();

    try {
      BufferedReader reader = new BufferedReader(new FileReader(list));

      while (reader.ready()) {
        wordSet.add(new Word(reader.readLine()));
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
      //e.printStackTrace(System.err);
      //addGenericWords();
    }
  }

  /**
   * Adds some extremely common words to the stoplist.
   */
  private void addGenericWords() {
    String[] genericWords = {"a", "an", "the", "and", "or", "but", "nor"};
    for (int i = 1; i < 7; i++) {
      wordSet.add(new Word(genericWords[i]));
    }
  }

  /**
   * Returns true if the word is in the stoplist.
   */
  public boolean contains(Word word) {
    return wordSet.contains(word);
  }

  /**
   * Returns true if the word is in the stoplist.
   */
  public boolean contains(String word) {
    return wordSet.contains(new Word(word));
  }


}
