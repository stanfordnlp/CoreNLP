package edu.stanford.nlp.ling;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.util.CoreMap;

public class CoreUtilities {

  private CoreUtilities() { } // class of static methods


  /**
   * Pieces a List of CoreMaps back together using
   * word and setting a white space between each word
   * TODO: remove this (SentenceUtils.listToString does the same thing - why 2 separate classes)
   */
  public static String toSentence(List<? extends CoreMap> sentence) {
    StringBuilder text = new StringBuilder();
    for (int i = 0, sz = sentence.size(); i < sz; i++) {
      CoreMap iw = sentence.get(i);
      text.append(iw.get(CoreAnnotations.TextAnnotation.class));
      if (i < sz - 1) {
        text.append(' ');
      }
    }
    return text.toString();
  }

  public static List<CoreLabel> deepCopy(List<CoreLabel> tokens) {
    List<CoreLabel> copy = new ArrayList<>();
    for (CoreLabel ml : tokens) {
      CoreLabel ml1 = new CoreLabel(ml);  // copy the labels
      copy.add(ml1);
    }
    return copy;
  }

  public static List<CoreLabel> toCoreLabelList(String... words) {
    List<CoreLabel> tokens = new ArrayList<>(words.length);
    for (String word : words) {
      CoreLabel cl = new CoreLabel();
      cl.setWord(word);
      tokens.add(cl);
    }
    return tokens;
  }

  public static List<CoreLabel> toCoreLabelList(List<String> words) {
    List<CoreLabel> tokens = new ArrayList<>(words.size());
    for (String word : words) {
      CoreLabel cl = new CoreLabel();
      cl.setWord(word);
      tokens.add(cl);
    }
    return tokens;
  }

  public static List<CoreLabel> toCoreLabelList(String[] words, String[] tags) {
    assert tags.length == words.length;
    List<CoreLabel> tokens = new ArrayList<>(words.length);
    for (int i = 0, sz = words.length; i < sz; i++) {
      CoreLabel cl = new CoreLabel();
      cl.setWord(words[i]);
      cl.setTag(tags[i]);
      tokens.add(cl);
    }
    return tokens;
  }

  public static List<CoreLabel> toCoreLabelListWithCharacterOffsets(String[] words, String[] tags) {
    assert tags.length == words.length;
    List<CoreLabel> tokens = new ArrayList<>(words.length);
    int offset = 0;
    for (int i = 0, sz = words.length; i < sz; i++) {
      CoreLabel cl = new CoreLabel();
      cl.setWord(words[i]);
      cl.setTag(tags[i]);
      cl.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, offset);
      offset += words[i].length();
      cl.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, offset);
      offset++; // assume one space between words :-)
      tokens.add(cl);
    }
    return tokens;
  }

  public static List<CoreLabel> toCoreLabelList(String[] words,
                                                String[] tags,
                                                String[] answers) {
    assert tags.length == words.length;
    assert answers.length == words.length;
    List<CoreLabel> tokens = new ArrayList<>(words.length);
    for (int i = 0, sz = words.length; i < sz; i++) {
      CoreLabel cl = new CoreLabel();
      cl.setWord(words[i]);
      cl.setTag(tags[i]);
      cl.set(CoreAnnotations.AnswerAnnotation.class, answers[i]);
      tokens.add(cl);
    }
    return tokens;
  }

}
