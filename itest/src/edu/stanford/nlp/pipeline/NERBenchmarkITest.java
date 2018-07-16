package edu.stanford.nlp.pipeline;

import junit.framework.TestCase;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.*;

public class NERBenchmarkITest extends TestCase {

  String NER_BENCHMARK_WORKING_DIR = "/u/scr/nlp/data/stanford-corenlp-testing/ner-benchmark-working-dir";

  public List<Pair<String, List<String>>> loadCoNLLDocs(String filePath) {
    List<Pair<String, List<String>>> returnList = new ArrayList<Pair<String, List<String>>>();
    String currDoc = "";
    List<String> currNERTagList = new ArrayList<String>();
    List<String> conllLines = IOUtils.linesFromFile(filePath);
    conllLines.add("");
    for (String conllLine : conllLines) {
      if (conllLine.equals("")) {
        // remove the extra " "
        if (currDoc.length() > 0) {
          currDoc = currDoc.substring(0, currDoc.length() - 1);
          Pair<String, List<String>> docPair = new Pair<>(currDoc, currNERTagList);
          returnList.add(docPair);
        }
      } else {
        currDoc += (conllLine.split("\t")[0] + " ");
        currNERTagList.add(conllLine.split("\t")[1]);
      }
    }
    return returnList;
  }

  public List<Annotation> createPipelineAnnotations(List<Pair<String, List<String>>> conllDocs,
                                                    StanfordCoreNLP pipeline) {
    List<Annotation> returnList = new ArrayList<Annotation>();

    for (Pair<String, List<String>> conllDoc : conllDocs) {
      Annotation conllDocAnnotation = new Annotation(conllDoc.first());
      pipeline.annotate(conllDocAnnotation);
      returnList.add(conllDocAnnotation);
    }

    return returnList;
  }

  public void writePerlScriptInputToPath(List<Annotation> annotations,
                                         List<Pair<String, List<String>>> conllDocs,
                                         String filePath) throws IOException {
    String perlScriptInput = "";
    for (int docNum = 0 ; docNum < annotations.size() ; docNum++) {
      Annotation currAnnotation = annotations.get(docNum);
      Pair<String, List<String>> currCoNLLDoc = conllDocs.get(docNum);
      List<CoreLabel> currAnnotationTokens = currAnnotation.get(CoreAnnotations.TokensAnnotation.class);
      for (int tokenNum = 0 ;
           tokenNum < currAnnotationTokens.size() ; tokenNum++) {
        String perlScriptLine = currAnnotationTokens.get(tokenNum).word()
            + "\t" + currCoNLLDoc.second().get(tokenNum) + "\t" + currAnnotationTokens.get(tokenNum).ner();
        perlScriptInput += (perlScriptLine + "\n");
      }
      perlScriptInput += "\n";
    }
    // remove last newline
    perlScriptInput = perlScriptInput.substring(0, perlScriptInput.length()-1);
    IOUtils.writeStringToFile(perlScriptInput, filePath, "UTF-8");
  }

  public void testEnglishNEROnCoNLLTest() throws IOException {
    String conllTestPath = "/u/scr/nlp/data/stanford-corenlp-testing/ner-benchmark-working-dir/conll.4class.testa";
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
    props.setProperty("tokenize.whitespace", "true");
    props.setProperty("ner.applyFineGrained", "false");
    props.setProperty("ner.useSUTime", "false");
    props.setProperty("ner.applyNumericClassifiers", "false");
    StanfordCoreNLP englishPipeline = new StanfordCoreNLP(props);
    runNERTest(englishPipeline, NER_BENCHMARK_WORKING_DIR, conllTestPath);
  }

  public void runNERTest(StanfordCoreNLP pipeline, String workingDir, String goldFilePath) throws IOException {
    List<Pair<String, List<String>>> conllDocs = loadCoNLLDocs(goldFilePath);
    List<Annotation> conllAnnotations = createPipelineAnnotations(conllDocs, pipeline);
    writePerlScriptInputToPath(conllAnnotations, conllDocs, workingDir+"/conllEvalInput.txt");
  }




}
