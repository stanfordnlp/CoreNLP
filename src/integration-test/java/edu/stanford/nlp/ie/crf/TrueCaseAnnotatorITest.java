package edu.stanford.nlp.ie.crf;

import java.io.File;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TrueCaseTextAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.DefaultPaths;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

/**
 * Verifies the output of the true caser annotator
 */
public class TrueCaseAnnotatorITest {

  @Test
  public void testTrueCaser() {
    // Results used to be higher, but the new UD2.0 style tokenization throws off names like Al-___
    Assert.assertEquals("Different number of truecasings done", 25,
                        runTest("edu/stanford/nlp/dcoref/STILLALONEWOLF_20050102.1100.eng.LDC2005E83.sgm"));
  }

  public static void main(String[] args) {
    if (args.length > 0) {
      runTest(args[0]);
    }
  }

  private static int runTest(String filename) {
    Properties props = new Properties();
    props.setProperty("pos.model", DefaultPaths.DEFAULT_POS_MODEL);
    props.setProperty("parse.maxlen", "50");
    props.setProperty("parse.model", DefaultPaths.DEFAULT_PARSER_MODEL);
    props.setProperty("ner.model", DefaultPaths.DEFAULT_NER_THREECLASS_MODEL);
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,parse,ner,truecase");
    props.setProperty("ssplit.boundariesToDiscard","<p>,<P>,</p>,</P>");
    StanfordCoreNLP nlp = new StanfordCoreNLP(props);
    
    // processPath(filename, nlp);
    return processFile(filename, nlp);
  }
  
  /*
  private static void processPath(String path, StanfordCoreNLP nlp) {
    File f = new File(path);
    if (f.isFile()) {
      processFile(path, nlp);
    } else {
      // run on all .xml files in this directory
      File [] files = new File(path).listFiles();
      if (files.length > 0) {
        for (File file: files){
          if (file.getAbsolutePath().endsWith(".xml")) {
            processFile(file.getAbsolutePath(), nlp);
          }
        }
      }
    }
  }
  */
  
  private static final Pattern START_TEXT = Pattern.compile("<text>", Pattern.CASE_INSENSITIVE);
  private static final Pattern END_TEXT = Pattern.compile("</text>", Pattern.CASE_INSENSITIVE);
  
  private static int processFile(String arg, StanfordCoreNLP nlp) {
    System.err.print("### ");
    System.err.println(arg);
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
    for (CoreMap sent: anno.get(SentencesAnnotation.class)){
      List<? extends CoreLabel> words = sent.get(TokensAnnotation.class);
      for (int i = 0; i < words.size(); i++) {
        String w = words.get(i).word();
        String tcw = words.get(i).get(TrueCaseTextAnnotation.class);
        if (! w.equals(tcw)){
          System.err.print('"' + w + "\" true cased to \"" + tcw + "\" in context:");
          for (int j = Math.max(0, i - 2); j < Math.min(words.size(), i + 2); j ++){
            System.err.print(" " + words.get(j).word());
          }
          System.err.println();
          count ++;
        }
      }
    }
    System.err.println("True case change count: " + count);
    return count;
  }

}
