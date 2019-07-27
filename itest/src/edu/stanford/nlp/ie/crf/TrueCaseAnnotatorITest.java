package edu.stanford.nlp.classify.machinereadability;

import java.io.File;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TrueCaseTextAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

/**
 * Verifies the output of the true caser annotator
 */
public class CheckTrueCaser {
  private CheckTrueCaser() {
  }
  
  public static void main(String[] args) throws Exception {
    Properties props = new Properties();
    props.setProperty("pos.model", "/u/nlp/data/pos-tagger/wsj3t0-18-left3words/left3words-wsj-0-18.tagger");
    props.setProperty("parse.maxlen", "50");
    props.setProperty("parse.model", "/u/nlp/data/lexparser/englishPCFG.ser.gz");
    props.setProperty("ner.model", "/u/nlp/data/ner/goodClassifiers/english.all.3class.distsim.crf.ser.gz");
    props.setProperty("annotators", "tokenize,lemma,ssplit,pos,parse,ner,truecase,dcoref");
    props.setProperty("ssplit.boundariesToDiscard","<p>,<P>,</p>,</P>");
    StanfordCoreNLP nlp = new StanfordCoreNLP(props);
    
    processPath(args[0], nlp);
  }
  
  private static void processPath(String path, StanfordCoreNLP nlp) {
    File f = new File(path);
    if(f.isFile()){
      processFile(path, nlp);
    } else {
      // run on all .xml files in this directory
      File [] files = new File(path).listFiles();
      for(File file: files){
        if(file.getAbsolutePath().endsWith(".xml")){
          processFile(file.getAbsolutePath(), nlp);
        }
      }
    }
  }
  
  private static final Pattern START_TEXT = Pattern.compile("<text>", Pattern.CASE_INSENSITIVE);
  private static final Pattern END_TEXT = Pattern.compile("</text>", Pattern.CASE_INSENSITIVE);
  
  private static void processFile(String arg, StanfordCoreNLP nlp) {
    System.out.print("### ");
    System.out.println(arg);
    String doc = IOUtils.slurpFileNoExceptions(arg);
    Matcher sm = START_TEXT.matcher(doc);
    sm.find();
    Matcher em = END_TEXT.matcher(doc);
    em.find();
    int start = sm.end();
    assert(start > 0);
    int end = em.start();
    assert(end > 0);
    String text = doc.substring(start, end);
    
    Annotation anno = nlp.process(text);
    int count = 0;
    for(CoreMap sent: anno.get(SentencesAnnotation.class)){
      List<? extends CoreLabel> words = sent.get(TokensAnnotation.class);
      for(int i = 0; i < words.size(); i ++){
        String w = words.get(i).word();
        String tcw = words.get(i).get(TrueCaseTextAnnotation.class);
        if(! w.equals(tcw)){
          System.out.print("\"" + w + "\" true cased to \"" + tcw + "\" in context:");
          for(int j = Math.max(0, i - 2); j < Math.min(words.size(), i + 2); j ++){
            System.out.print(" " + words.get(j).word());
          }
          System.out.println();
          count ++;
        }
      }
    }
    System.out.println("True case change count: " + count);
  }
}
