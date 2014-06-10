package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ie.NERClassifierCombiner;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.ChineseCoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.io.IOException;


/**
 * An Annotation Pipeline for Chinese processing.<br>
 * TODO: segmentation, normalization (punctuation, number, etc), NER, POS, parsing information
 *
 * @author Pi-Chuan Chang
 */

public class ChineseAnnotationPipeline {
  public static Annotation buildAnnotation(List<String> sentences) {
    StringBuilder concatenated = new StringBuilder();
    List<CoreMap> coreMaps = new ArrayList<CoreMap>();
    for (String sentence : sentences) {
      CoreMap nextCoreMap = new ArrayCoreMap();
      nextCoreMap.set(CoreAnnotations.TextAnnotation.class, sentence);
      coreMaps.add(nextCoreMap);
      if (coreMaps.size() > 1) {
        concatenated.append("\n\n");
      }
      concatenated.append(sentence);
    }
    Annotation ann = new Annotation(concatenated.toString());
    ann.set(CoreAnnotations.SentencesAnnotation.class, coreMaps);
    return ann;
  }

  public static final String DEFAULT_NER_MODEL = "/u/nlp/data/ner/goodClassifiers/chinese.misc.distsim.crf.ser.gz";
  

  public static AnnotationPipeline basicPipeline(boolean verbose) {
    AnnotationPipeline ap = new AnnotationPipeline();
    ap.addAnnotator(new ChineseSegmenterAnnotator(verbose));
    return ap;
  }

  public static AnnotationPipeline nerPipeline(boolean verbose) {
    return nerPipeline(verbose, DEFAULT_NER_MODEL);
  }

  public static AnnotationPipeline nerPipeline(boolean verbose, String ... models) {
    try {
      AnnotationPipeline ap = new AnnotationPipeline();
      ap.addAnnotator(new ChineseSegmenterAnnotator(verbose));
      NERClassifierCombiner nerCombiner = new NERClassifierCombiner(false, false, new Properties(), models);
      ap.addAnnotator(new NERCombinerAnnotator(nerCombiner, verbose));
      return ap;
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  // java -mx4g edu.stanford.nlp.pipeline.ChineseAnnotationPipeline 
  // "今年西门子将努力参与中国的三峡工程建设。"
  public static void main(String[] args) throws IOException, ClassNotFoundException {
    boolean verbose = true;
    AnnotationPipeline ap = nerPipeline(verbose);

    List<String> args1 = Arrays.asList(args);
    Annotation a = buildAnnotation(args1);
    ap.annotate(a);
    //System.out.println(a.getAnnotation(Annotation.WORDS_KEY));
    //System.out.println(a.getAnnotation(Annotation.PARSE_KEY));
    List<CoreMap> coreMaps = a.get(CoreAnnotations.SentencesAnnotation.class);
    for (CoreMap coreMap : coreMaps) {
      System.err.println("------------------------------------");
      List<CoreLabel> al = coreMap.get(ChineseCoreAnnotations.CharactersAnnotation.class);
      System.err.println(al.size());
      for ( CoreLabel o : al ) {
        System.out.println(o.getClass().getName());
        System.out.println(o);
        System.out.println("=================================");
      }
    }
  }
}
