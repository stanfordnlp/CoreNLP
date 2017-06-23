package edu.stanford.nlp.paragraphs;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

import java.util.Properties;

/**
 * Created by Grace Muzny on 05/11/2016.
 */
public class RunParagraphAnnotator {

  public static String TEST1 = "Easy Peasy. Lemon squeezy.";
  public static String TEST2 = "Easy Peasy. \nLemon squeezy.\n\n Blop dop bop.";
  public static String TEST3 = "Easy Peasy. \n\nLemon squeezy. \n\n Bam! \n Not this one.";

  public static void main(String[] args) {
    runTest(TEST1, "one");
    runTest(TEST1, "two");
    runTest(TEST2, "one");
    runTest(TEST2, "two");
    runTest(TEST3, "one");
    runTest(TEST3, "two");
  }

  public static void runTest(String test, String num) {
    System.out.println("Testing: " + test + " : num newline breaks: " + num);
    Annotation ann = new Annotation(test);

    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    pipeline.annotate(ann);

    Properties propsPara = new Properties();
    propsPara.setProperty("paragraphBreak", num);
    ParagraphAnnotator para = new ParagraphAnnotator(propsPara, true);
    para.annotate(ann);

    for (CoreMap sent : ann.get(CoreAnnotations.SentencesAnnotation.class)) {
      System.out.println(sent);
      System.out.println(sent.get(CoreAnnotations.ParagraphIndexAnnotation.class));
    }
  }
}
