package edu.stanford.nlp.ie.machinereading.domains.ace;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;

import edu.stanford.nlp.ie.machinereading.structure.AnnotationUtils;
import edu.stanford.nlp.ling.CoreAnnotations.DocIDAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;

/**
 * Converts the entities in the ACE corpus to the CoNLL column format
 */
public class AceToCoNLL {
  public static void main(String[] args) throws Exception {
    Properties props = StringUtils.argsToProperties(args);
    StanfordCoreNLP processor = new StanfordCoreNLP(props, false);
    AceReader r = new AceReader(processor, true);
    r.setLoggerLevel(Level.INFO);

    String inputDir = props.getProperty("input");
    assert(inputDir != null);
    String outFile = props.getProperty("output");
    assert(outFile != null);
    
    Annotation corpus = r.parse(inputDir);
    
    toCoNLL(outFile + ".full", corpus, true, true, true);
    toCoNLL(outFile + ".noprp", corpus, true, true, false);
    toCoNLL(outFile + ".noprp.nonn", corpus, true, false, false);
  }
  
  static void toCoNLL(String fileName, 
      Annotation corpus,
      boolean includeProper,
      boolean includeCommon,
      boolean includePron) throws Exception {
    boolean useBIO = false;
    if(includeCommon || includePron) useBIO = true;
    Set<String> mentionTypes = new HashSet<String>();
    if(includeProper) mentionTypes.addAll(Arrays.asList("NAM", "NAMPRE"));
    if(includeCommon) mentionTypes.addAll(Arrays.asList("NOM", "BAR", "HLS", "NOMPRE"));
    if(includePron) mentionTypes.addAll(Arrays.asList("PRO", "PTV", "WHQ"));
    
    PrintStream os = new PrintStream(new FileOutputStream(fileName));
    String crtDocId = "none";
    List<CoreMap> sentences = corpus.get(CoreAnnotations.SentencesAnnotation.class);
    for(CoreMap sentence: sentences){
      String docId = sentence.get(DocIDAnnotation.class);
      assert(docId != null);
      if(! docId.equals(crtDocId)){
        os.println("-DOCSTART- -X- O\n");
        crtDocId = docId;
      }
      sentenceToCoNLL(os, sentence, useBIO, mentionTypes);
      os.println();
    }
    os.close();
    System.err.println("CoNLL format saved in " + fileName);
  }
  
  static void sentenceToCoNLL(PrintStream os, CoreMap sentence, boolean useBIO, Set<String> mentionTypes) {
    List<CoreLabel> tokens = AnnotationUtils.sentenceEntityMentionsToCoreLabels(
        sentence, true, null, mentionTypes, false, useBIO);
    for(CoreLabel t: tokens) {
      os.printf("%s %s %s\n", t.word(), t.tag(), t.get(AnswerAnnotation.class));
    }
  }
}
