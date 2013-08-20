package edu.stanford.nlp.pipeline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.WordToSentenceProcessor;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;


/**
 * This class assumes that there is either a
 * <code>List&lt;? extends CoreLabel&gt;</code> under the
 * <code>TokensAnnotation</code> field, and runs it
 * through {@link edu.stanford.nlp.process.WordToSentenceProcessor} and
 * puts the new <code>List&lt;List&lt;? extends CoreLabel&gt;&gt;</code>
 * (it is now definitely a
 * <code>List&lt;List&lt;? extends CoreLabel&gt;&gt;</code>) back under
 * the Annotation.WORDS_KEY field.
 *
 * @author Jenny Finkel
 */
public class WordsToSentencesAnnotator implements Annotator {

  private final WordToSentenceProcessor<CoreLabel> wts;

  private final boolean VERBOSE;

  public WordsToSentencesAnnotator() {
    this(false);
  }

  public WordsToSentencesAnnotator(boolean verbose) {
    this(new WordToSentenceProcessor<CoreLabel>(), verbose);
  }

  public WordsToSentencesAnnotator(boolean verbose, String boundaryTokenRegex) {
    this(new WordToSentenceProcessor<CoreLabel>(boundaryTokenRegex), verbose);
  }

  private WordsToSentencesAnnotator(WordToSentenceProcessor<CoreLabel> wts, boolean verbose) {
    VERBOSE = verbose;
    this.wts = wts;
  }

  public static WordsToSentencesAnnotator newlineSplitter(boolean verbose, String ... nlToken) {
    WordToSentenceProcessor<CoreLabel> wts;
    if (nlToken.length == 0) {
      wts = new WordToSentenceProcessor<CoreLabel>("",
                                                   Collections.<String>emptySet(),
                                                   Collections.<String>emptySet());
    } else if (nlToken.length == 1) {
      wts = new WordToSentenceProcessor<CoreLabel>("",
                                                   Collections.<String>emptySet(),
                                                   Collections.singleton(nlToken[0]));
    } else {
      Set<String> nlTokens = Generics.newHashSet(Arrays.asList(nlToken));
      wts = new WordToSentenceProcessor<CoreLabel>("",
                                                   Collections.<String>emptySet(),
                                                   nlTokens);
    }
    return new WordsToSentencesAnnotator(wts, verbose);
  }


  public void setSentenceBoundaryToDiscard(Set<String> boundaries) {
    wts.setSentenceBoundaryToDiscard(boundaries);
  }

  public void addHtmlSentenceBoundaryToDiscard(Set<String> boundaries) {
    wts.addHtmlSentenceBoundaryToDiscard(boundaries);
  }

  public void setOneSentence(boolean isOneSentence) {
    wts.setOneSentence(isOneSentence);
  }

  @Override
  public void annotate(Annotation annotation) {
    if (VERBOSE) {
      System.err.print("Sentence splitting ...");
    }
    if (annotation.has(CoreAnnotations.TokensAnnotation.class)) {

      // get text and tokens from the document
      String text = annotation.get(CoreAnnotations.TextAnnotation.class);
      List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
      // System.err.println("Tokens are: " + tokens);

      // assemble the sentence annotations
      int tokenOffset = 0;
      List<CoreMap> sentences = new ArrayList<CoreMap>();
      for (List<CoreLabel> sentenceTokens: this.wts.process(tokens)) {
        if (sentenceTokens.isEmpty()) {
          throw new RuntimeException("unexpected empty sentence: " + sentenceTokens);
        }

        // get the sentence text from the first and last character offsets
        int begin = sentenceTokens.get(0).get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
        int last = sentenceTokens.size() - 1;
        int end = sentenceTokens.get(last).get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
        String sentenceText = text.substring(begin, end);

        // create a sentence annotation with text and token offsets
        Annotation sentence = new Annotation(sentenceText);
        sentence.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, begin);
        sentence.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, end);
        sentence.set(CoreAnnotations.TokensAnnotation.class, sentenceTokens);
        sentence.set(CoreAnnotations.TokenBeginAnnotation.class, tokenOffset);
        tokenOffset += sentenceTokens.size();
        sentence.set(CoreAnnotations.TokenEndAnnotation.class, tokenOffset);
        sentence.set(CoreAnnotations.SentenceIndexAnnotation.class, sentences.size());

        // add the sentence to the list
        sentences.add(sentence);
      }
      // the condition below is possible if sentenceBoundaryToDiscard is initialized!
      /*
      if (tokenOffset != tokens.size()) {
        throw new RuntimeException(String.format(
            "expected %d tokens, found %d", tokens.size(), tokenOffset));
      }
      */

      // add the sentences annotations to the document
      annotation.set(CoreAnnotations.SentencesAnnotation.class, sentences);

    } else {
      throw new RuntimeException("unable to find words/tokens in: " + annotation);
    }
  }


  @Override
  public Set<Requirement> requires() {
    return Collections.singleton(TOKENIZE_REQUIREMENT);
  }

  @Override
  public Set<Requirement> requirementsSatisfied() {
    return Collections.singleton(SSPLIT_REQUIREMENT);
  }
}
