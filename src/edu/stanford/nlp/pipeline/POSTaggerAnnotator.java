package edu.stanford.nlp.pipeline;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.Timing;

/**
 * Wrapper for the maxent part of speech tagger.
 *
 * @author Anna Rafferty
 *
 */
public class POSTaggerAnnotator implements Annotator {

  private final MaxentTagger pos;

  private int maxSentenceLength;

  public POSTaggerAnnotator() {
    this(true);
  }

  public POSTaggerAnnotator(boolean verbose) {
    this(System.getProperty("pos.model", MaxentTagger.DEFAULT_NLP_GROUP_MODEL_PATH), verbose);
  }

  public POSTaggerAnnotator(String posLoc, boolean verbose) {
    this(posLoc, verbose, Integer.MAX_VALUE);
  }

  public POSTaggerAnnotator(String posLoc, boolean verbose, int maxSentenceLength) {
    this(loadModel(posLoc, verbose), maxSentenceLength);
  }

  public POSTaggerAnnotator(MaxentTagger model) {
    this(model, Integer.MAX_VALUE);
  }

  public POSTaggerAnnotator(MaxentTagger model, int maxSentenceLength) {
    this.pos = model;
    this.maxSentenceLength = maxSentenceLength;
  }

  public POSTaggerAnnotator(String annotatorName, Properties props) {
    String posLoc = props.getProperty(annotatorName + ".model");
    if (posLoc == null) {
      throw new IllegalArgumentException("No model specified for " +
                                         "POS tagger annotator " +
                                         annotatorName);
    }
    boolean verbose =
      PropertiesUtils.getBool(props, annotatorName + ".verbose", true);
    this.pos = loadModel(posLoc, verbose);
    this.maxSentenceLength =
      PropertiesUtils.getInt(props, annotatorName + ".maxlen",
                             Integer.MAX_VALUE);
  }

  public void setMaxSentenceLength(int maxLen) {
    this.maxSentenceLength = maxLen;
  }

  private static MaxentTagger loadModel(String loc, boolean verbose) {
    Timing timer = null;
    if (verbose) {
      timer = new Timing();
      timer.doing("Loading POS Model [" + loc + ']');
    }
    MaxentTagger tagger;
    try {
      tagger = new MaxentTagger(loc);
    } catch (IOException e) {
      RuntimeException runtimeException = new RuntimeException(e);
      throw runtimeException;
    } catch (ClassNotFoundException e) {
      RuntimeException runtimeException = new RuntimeException(e);
      throw runtimeException;
    }
    if (verbose) {
      timer.done();
    }
    return tagger;
  }

  @Override
  public void annotate(Annotation annotation) {
    // turn the annotation into a sentence
    if (annotation.has(CoreAnnotations.SentencesAnnotation.class)) {
      for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
        List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
        List<TaggedWord> tagged = pos.apply(tokens);

        for (int i = 0; i < tokens.size(); ++i) {
          tokens.get(i).set(PartOfSpeechAnnotation.class, tagged.get(i).tag());
        }
      }
    } else {
      throw new RuntimeException("unable to find words/tokens in: " + annotation);
    }
  }

  /**
   * Takes in a list of words and POS tags them. Tagging is done in place - the
   * returned CoreLabels are the same ones you passed in, with tags added.
   *
   * @param text
   *          List of tokens to tag
   * @return Tokens with tags
   */
  public List<? extends CoreLabel> processText(List<? extends CoreLabel> text) {
    // cdm 2009: copying isn't necessary; the POS tagger's apply()
    // method does not change the parameter passed in. But I think you
    // can't have it correctly generic without copying. Sigh.

    // if the text size is more than the max length allowed
    if (text.size() > maxSentenceLength) {
      return processTextLargerThanMaxLen(text);
    }

    // todo: Is the list copy in the next line required??
    List<TaggedWord> tagged = pos.apply(new ArrayList<CoreLabel>(text));
    // copy in the tags
    Iterator<TaggedWord> taggedIter = tagged.iterator();
    for (CoreLabel word : text) {
      TaggedWord cur = taggedIter.next();
      word.setTag(cur.tag());
    }
    return text;
  }

  /**
   * if the text length is more than specified than the text is divided into
   * (length/MaxLen) sentences and tagged individually
   *
   * @param text
   */
  private List<? extends CoreLabel> processTextLargerThanMaxLen(List<? extends CoreLabel> text) {

    int startIndx = 0;
    int endIndx = (startIndx + maxSentenceLength < text.size() ? startIndx + maxSentenceLength : text.size());
    while (true) {
      System.out.println(startIndx + "\t" + endIndx);
      List<? extends CoreLabel> textToTag = text.subList(startIndx, endIndx);
      List<TaggedWord> tagged = pos.apply(textToTag);

      Iterator<TaggedWord> taggedIter = tagged.iterator();
      for (CoreLabel word : textToTag) {
        TaggedWord cur = taggedIter.next();
        word.setTag(cur.tag());
      }

      if (startIndx + maxSentenceLength >= text.size())
        break;

      startIndx += maxSentenceLength;
      endIndx = (startIndx + maxSentenceLength < text.size() ? startIndx + maxSentenceLength : text.size());

    }
    return text;
  }

}
