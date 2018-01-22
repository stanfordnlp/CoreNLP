package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.util.logging.StanfordRedwoodConfiguration;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.*;

/**
 * A slow itest that just runs the pipeline over a whole bunch of
 * documents, making sure it doesn't crash on any of them.  Not sure
 * what to do with the output, if anything.
 *
 * @author John Bauer
 * @author Gabor Angeli (parallelism test)
 */
public class StanfordCoreNLPSlowITest extends TestCase {

  static List<File> getFileList() {
    List<File> files = new ArrayList<File>();
    File pathFile = new File("/u/nlp/ACE2005/" +
                             "ACE2005_Multilingual_LDC2006T06/data/English");
    for (File subFile : pathFile.listFiles()) {
      if (!subFile.isDirectory()) {
        continue;
      }
      for (File subSubFile : subFile.listFiles()) {
        if (!subSubFile.isDirectory() ||
            !subSubFile.getName().equals("timex2norm")) {
          continue;
        }
        for (File sgmlFile : subSubFile.listFiles()) {
          if (sgmlFile.isDirectory() ||
              !sgmlFile.getName().endsWith(".sgm")) {
            continue;
          }
          files.add(sgmlFile);
        }
      }
    }
    return files;
  }

  @Override
  public void setUp(){
    StanfordRedwoodConfiguration.minimalSetup();
  }

  private static StanfordCoreNLP buildPipeline() throws IOException {
    List<File> files = getFileList();
    File dir = File.createTempFile("StanfordCoreNLPSlowITest", "");
    dir.delete();
    dir.mkdir();
    dir.deleteOnExit();
    System.out.println("Temp path: " + dir.getPath());

    Properties props = new Properties();
    props.setProperty("outputDirectory", dir.getPath());
    props.setProperty("annotators", "tokenize,cleanxml,ssplit,pos,lemma,ner,parse,depparse," +
        "coref,natlog,openie,kbp,entitylink,sentiment,quote");
    props.setProperty("coref.algorithm", "neural");
    props.setProperty("serializer", "AnnotationSerializer");

    return new StanfordCoreNLP(props);
  }

  public void testNoCrashes() throws IOException {
    StanfordCoreNLP pipeline = buildPipeline();
    for (File file : getFileList()) {
      try {
        pipeline.processFiles(Collections.singletonList(file), false, Optional.empty());
      } catch (Exception e) {
        // process files one at a time and rethrow exceptions so that
        // we know which file caused the problem
        throw new RuntimeException("Failed to process file " + file, e);
      }
    }
  }

  public void testParallelism() throws IOException {
    StanfordCoreNLP pipeline = buildPipeline();
    pipeline.processFiles(getFileList(), Runtime.getRuntime().availableProcessors(), false, Optional.empty());
  }

}
