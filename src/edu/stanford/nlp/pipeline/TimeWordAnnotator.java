package edu.stanford.nlp.pipeline;

import java.util.*;
import java.util.regex.*;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.NormalizedNamedEntityTagAnnotation;
import edu.stanford.nlp.util.CoreMap;



public class TimeWordAnnotator implements Annotator {

  private boolean VERBOSE = true;
  private List<String> timeWords;

  public static final Pattern armyTimeMorning = Pattern.compile("0([0-9])([0-9]){2}");

  public TimeWordAnnotator(boolean verb) {
    VERBOSE = verb;
    setupTimeWords();
  }

  public TimeWordAnnotator() {
    this(true);
  }

  private void setupTimeWords() {
    timeWords = new ArrayList<String>();
    timeWords.add("morning");
    timeWords.add("evening");
    timeWords.add("night");
    timeWords.add("noon");
    timeWords.add("midnight");
    timeWords.add("teatime");
    timeWords.add("lunchtime");
    timeWords.add("dinnertime");
    timeWords.add("suppertime");
    timeWords.add("afternoon");
    timeWords.add("midday");
    timeWords.add("dusk");
    timeWords.add("dawn");
    timeWords.add("sunup");
    timeWords.add("sundown");
    timeWords.add("daybreak");
    timeWords.add("day");
  }

  public void annotate(Annotation annotation) {

    if (VERBOSE) {
      System.err.print("Adding extra \"time word\" NER...");
    }

    if (annotation.containsKey(CoreAnnotations.SentencesAnnotation.class)) {
      // classify tokens for each sentence 
      for (CoreMap sentence: annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
        List<CoreLabel> words = sentence.get(CoreAnnotations.TokensAnnotation.class);
        for(CoreLabel word: words){
          String text = word.word();
          String ner = word.ner();
          if (timeWords.contains(text.toLowerCase()) && (ner == null || ner.equals("TIME")))
            word.setNER("TIME");
          else {
            Matcher m = armyTimeMorning.matcher(text);
            if (m.matches()) {
              word.setNER("TIME");
              word.set(NormalizedNamedEntityTagAnnotation.class, m.group(1) + ":" + m.group(2) + "am");
            }
          }
        }
      }
    } else {
      throw new RuntimeException("unable to find sentences in: " + annotation);
    }
  }
}
