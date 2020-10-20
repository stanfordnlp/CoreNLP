package edu.stanford.nlp.parser.lexparser;

import java.util.*;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.process.WordSegmenter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;


/**
 * A word-segmentation scheme using the max-match algorithm.
 *
 * @author Galen Andrew
 */
public class MaxMatchSegmenter implements WordSegmenter {

  private final Set<String> words = Generics.newHashSet();
  private static final int maxLength = 10;

  @Override
  public void initializeTraining(double numTrees) {}

  @Override
  public void train(Collection<Tree> trees) {
    for (Tree tree : trees) {
      train(tree);
    }
  }

  @Override
  public void train(Tree tree) {
    train(tree.taggedYield());
  }

  @Override
  public void train(List<TaggedWord> sentence) {
    for (TaggedWord word : sentence) {
      if (word.word().length() <= maxLength) {
        words.add(word.word());
      }
    }
  }

  @Override
  public void finishTraining() {}

  @Override
  public void loadSegmenter(String filename) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<HasWord> segment(String s) {
    List<Word> segmentedWords = new ArrayList<>();
    for (int start = 0, length = s.length(); start < length; ) {
      int end = Math.min(length, start + maxLength);
      while (end > start + 1) {
        String nextWord = s.substring(start, end);
        if (words.contains(nextWord)) {
          segmentedWords.add(new Word(nextWord));
          break;
        }
        end--;
      }
      if (end == start + 1) {
        // character does not start any word in our dictionary
        // handle non-BMP characters
        if (s.codePointAt(start) >= 0x10000) {
          segmentedWords.add(new Word(new String(s.substring(start, start + 2))));
          start += 2;
        } else {
          segmentedWords.add(new Word(new String(s.substring(start, start + 1))));
          start++;
        }
      } else {
        start = end;
      }
    }

    return new ArrayList<>(segmentedWords);
  }

  private static final long serialVersionUID = 8260792244886911724L;

}
