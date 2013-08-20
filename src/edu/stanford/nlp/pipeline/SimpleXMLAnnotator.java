package edu.stanford.nlp.pipeline;

import java.util.*;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.XmlElementAnnotation;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;

import java.util.regex.Pattern;
import java.util.regex.Matcher;


/**
 * This class assumes that the Annotation has
 * a List<CoreLabel> in the Annotation.WORDS_KEY
 * field.  It looks for matched pairs of tokens that are XML start and
 * end tags.  It will then delete these tokens, and store the element name
 * in an Xml field.
 * This is only meant to handle simple XML tagging.  It doesn't really do
 * anything sensible as soon as there is nesting.  It takes the most-nested
 * element.  It's not meant to handle tags with attributes.
 * This is probably best done straight after tokenization.
 *
 * @author Christopher Manning
 */
public class SimpleXMLAnnotator implements Annotator {

  private static final Pattern xmlStartTag = Pattern.compile("<([^>]+)>");
  private static final Pattern xmlEndTag = Pattern.compile("</([^>]+)>");
  private final boolean VERBOSE ;

  public SimpleXMLAnnotator() {
    this(true);
  }

  public SimpleXMLAnnotator(boolean verbose) {
    VERBOSE = verbose;
  }

  public void annotate(Annotation annotation) {

    if (VERBOSE) {
      System.err.print("Turning XML tags into CoreLabel attribute ... ");
    }

    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
    if (sentences != null) {
      List<CoreMap> newSentences = new ArrayList<CoreMap>();
      for (CoreMap sent : sentences) {
        List<? extends CoreLabel> words = sent.get(CoreAnnotations.TokensAnnotation.class);
        if(words == null){ throw new IllegalArgumentException("No TokensAnnotation for sentence"); }
        CoreMap newSent = new ArrayCoreMap(1);
        newSent.set(CoreAnnotations.TokensAnnotation.class, doOneSentence((words)));
        newSentences.add(newSent);
      }
      annotation.set(CoreAnnotations.SentencesAnnotation.class, newSentences);
    }
    if (VERBOSE) {
      System.err.println("output: " + sentences);
      System.err.println();
    }
  }

  private List<CoreLabel> doOneSentence(List<? extends CoreLabel> labels) {
    // note that the size of labels may well shrink as we go, so don't cache size
    for (int i = 0; i < labels.size(); i++) {
      //Iterate through each word....
      CoreLabel w = labels.get(i);
      String word = w.word();
      Matcher m = xmlStartTag.matcher(word);
      if (m.matches()) {
        String element = m.group(1);
        boolean foundClose = false;
        int j;
        for (j = i + 1; j < labels.size(); j++) {
          CoreLabel v = labels.get(j);
          String vWord = v.word();
          Matcher mv = xmlEndTag.matcher(vWord);
          if (mv.matches()) {
            String vElement = mv.group(1);
            if (element.equals(vElement)) {
              foundClose = true;
              break;
            }
          }
        }
        if ( ! foundClose) {
          if (VERBOSE) {
            System.err.println("XML tag with missing close: " + word);
          }
        } else {
          labels.remove(j);
          labels.remove(i);
          // < j - 1 because we're deleting 2 tokens and j was on last token not one after it
          for (int k = i; k < j - 1; k++) {
            CoreLabel u = labels.get(k);
            u.set(XmlElementAnnotation.class, element);
          }
        }
      }
    }
    List<CoreLabel> s = new ArrayList<CoreLabel>();
    for (CoreLabel cl : labels) {
      s.add(cl);
    }
    return s;
  }

}
