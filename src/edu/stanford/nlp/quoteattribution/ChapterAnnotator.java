package edu.stanford.nlp.quoteattribution;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.ErasureUtils;

import java.util.*;

/**
 * Created by mjfang on 12/18/16. Annotates each sentence with what chapter it is in (1-indexed).
 * Currently uses "CHAPTER" as a delimiter; may have to be extended in the future.
 */
public class ChapterAnnotator implements Annotator{

  public String CHAPTER_BREAK = "CHAPTER";

  //key to a list of sentences that begin chapters
  public static class ChapterAnnotation implements CoreAnnotation<Integer> {
    public Class<Integer> getType() {
      return ErasureUtils.uncheckedCast(List.class);
    }
  }

  public void annotate(Annotation doc)
  {
    Map<Integer, Integer> sentenceToChapter = new HashMap<>();

    List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
    int chapterNum = 0;
    int sentenceIndex = 0;
    for(CoreMap sentence : sentences)
    {
      if(sentence.get(CoreAnnotations.TextAnnotation.class).contains(CHAPTER_BREAK))
      {
        chapterNum++;
      }
      sentence.set(ChapterAnnotation.class, chapterNum);
      sentenceToChapter.put(sentenceIndex, chapterNum);
      sentenceIndex++;
    }
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return null;
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return new HashSet<>(Arrays.asList(
            CoreAnnotations.TextAnnotation.class,
            CoreAnnotations.SentencesAnnotation.class));
  }
}
