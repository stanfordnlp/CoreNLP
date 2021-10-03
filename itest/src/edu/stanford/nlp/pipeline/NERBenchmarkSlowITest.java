package edu.stanford.nlp.pipeline;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.TestPaths;

public class NERBenchmarkSlowITest {

  String NER_BENCHMARK_WORKING_DIR = String.format("%s/stanford-corenlp-testing/ner-benchmark-working-dir", TestPaths.testHome());

  private static final Pattern FB1_Pattern = Pattern.compile("FB1:  (\\d+\\.\\d+)");

  /** handle Spanish NER **/
  private static final Map<String, String> spanishToEnglishTag = new HashMap<>();

  static {
    spanishToEnglishTag.put("PERS", "PERSON");
    spanishToEnglishTag.put("ORG", "ORGANIZATION");
    spanishToEnglishTag.put("LUG", "LOCATION");
    spanishToEnglishTag.put("OTROS", "MISC");
  }

  /** convert Spanish tag content of older models **/
  public String spanishToEnglishTag(String spanishTag) {
    if (spanishToEnglishTag.containsKey(spanishTag))
      return spanishToEnglishTag.get(spanishTag);
    else
      return spanishTag;
  }

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
        currDoc = "";
        currNERTagList = new ArrayList<>();
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
            + "\t" + spanishToEnglishTag(currCoNLLDoc.second().get(tokenNum)) + "\t" +
            currAnnotationTokens.get(tokenNum).ner();
        perlScriptInput += (perlScriptLine + "\n");
      }
      perlScriptInput += "\n";
    }
    // remove last newline
    perlScriptInput = perlScriptInput.substring(0, perlScriptInput.length()-1);
    IOUtils.writeStringToFile(perlScriptInput, filePath, "UTF-8");
  }

  public String runEvalScript(String inputCoNLLFile) throws IOException{
    String result = null;
    String evalCmd = NER_BENCHMARK_WORKING_DIR+"/eval_conll_cmd.sh "+inputCoNLLFile;
    Process p = Runtime.getRuntime().exec(evalCmd);
    BufferedReader in =
        new BufferedReader(new InputStreamReader(p.getInputStream()));
    String inputLine;
    while ((inputLine = in.readLine()) != null) {
      System.out.println(inputLine);
      result += inputLine + "\n";
    }
    in.close();
    return result;
  }

  public double parseResults(String conllEvalScriptResults) {
    String[] resultLines = conllEvalScriptResults.split("\n");
    double foundF1Score = 0.0;
    for (String resultLine : resultLines) {
      Matcher m = FB1_Pattern.matcher(resultLine);
      // Should parse the F1 after "FB1:"
      if (m.find()) {
        String f1 = m.group(1);
        foundF1Score = Double.parseDouble(f1);
        break;
      }
    }
    return foundF1Score;
  }

  @Test
  public void testChineseNEROnOntoNotesDev() throws IOException {
    String conllTestPath =
        String.format("%s/stanford-corenlp-testing/ner-benchmark-working-dir/ontonotes5-chinese-ner-7class.dev", TestPaths.testHome());
    Properties props = StringUtils.argsToProperties("-props", "StanfordCoreNLP-chinese.properties");
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
    props.setProperty("tokenize.whitespace", "true");
    props.setProperty("ner.applyFineGrained", "false");
    props.setProperty("ner.useSUTime", "false");
    props.setProperty("ner.applyNumericClassifiers", "false");
    StanfordCoreNLP chinesePipeline = new StanfordCoreNLP(props);
    runNERTest("Chinese OntoNotes Dev 7 Class ", chinesePipeline, NER_BENCHMARK_WORKING_DIR, conllTestPath,
        78.98);
  }

  @Test
  public void testChineseNEROnOntoNotesTest() throws IOException {
    String conllTestPath =
        String.format("%s/stanford-corenlp-testing/ner-benchmark-working-dir/ontonotes5-chinese-ner-7class.test", TestPaths.testHome());
    Properties props = StringUtils.argsToProperties("-props", "StanfordCoreNLP-chinese.properties");
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
    props.setProperty("tokenize.whitespace", "true");
    props.setProperty("ner.applyFineGrained", "false");
    props.setProperty("ner.useSUTime", "false");
    props.setProperty("ner.applyNumericClassifiers", "false");
    StanfordCoreNLP chinesePipeline = new StanfordCoreNLP(props);
    runNERTest("Chinese OntoNotes Test 7 Class ", chinesePipeline, NER_BENCHMARK_WORKING_DIR, conllTestPath,
        80.00);
  }

  @Test
  public void testEnglishNEROnCoNLLDev() throws IOException {
    String conllTestPath = String.format("%s/ner/english/conll.4class.testa", TestPaths.testHome());
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
    props.setProperty("tokenize.whitespace", "true");
    props.setProperty("ner.model", "edu/stanford/nlp/models/ner/english.conll.4class.distsim.crf.ser.gz");
    props.setProperty("ner.applyFineGrained", "false");
    props.setProperty("ner.useSUTime", "false");
    props.setProperty("ner.applyNumericClassifiers", "false");
    StanfordCoreNLP englishPipeline = new StanfordCoreNLP(props);
    runNERTest("CoNLL 2003 English Dev", englishPipeline, NER_BENCHMARK_WORKING_DIR, conllTestPath,
        94.01);
  }

  @Test
  public void testEnglishNEROnCoNLLTest() throws IOException {
    String conllTestPath = String.format("%s/ner/english/conll.4class.testb", TestPaths.testHome());
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
    props.setProperty("tokenize.whitespace", "true");
    props.setProperty("ner.model", "edu/stanford/nlp/models/ner/english.conll.4class.distsim.crf.ser.gz");
    props.setProperty("ner.applyFineGrained", "false");
    props.setProperty("ner.useSUTime", "false");
    props.setProperty("ner.applyNumericClassifiers", "false");
    StanfordCoreNLP englishPipeline = new StanfordCoreNLP(props);
    runNERTest("CoNLL 2003 English Test", englishPipeline, NER_BENCHMARK_WORKING_DIR, conllTestPath,
        90.19);
  }

  @Test
  public void testEnglishNEROnOntoNotesDev() throws IOException {
    String conllTestPath = String.format("%s/ner/english/ontonotes.3class.dev", TestPaths.testHome());
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
    props.setProperty("tokenize.whitespace", "true");
    props.setProperty("ner.model", "edu/stanford/nlp/models/ner/english.all.3class.distsim.crf.ser.gz");
    props.setProperty("ner.applyFineGrained", "false");
    props.setProperty("ner.useSUTime", "false");
    props.setProperty("ner.applyNumericClassifiers", "false");
    StanfordCoreNLP englishPipeline = new StanfordCoreNLP(props);
    runNERTest("OntoNotes English Dev 3 Class", englishPipeline, NER_BENCHMARK_WORKING_DIR, conllTestPath,
        89.93);
  }

  @Test
  public void testEnglishNEROnOntoNotesTest() throws IOException {
    String conllTestPath = String.format("%s/ner/english/ontonotes.3class.test", TestPaths.testHome());
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
    props.setProperty("tokenize.whitespace", "true");
    props.setProperty("ner.model", "edu/stanford/nlp/models/ner/english.all.3class.distsim.crf.ser.gz");
    props.setProperty("ner.applyFineGrained", "false");
    props.setProperty("ner.useSUTime", "false");
    props.setProperty("ner.applyNumericClassifiers", "false");
    StanfordCoreNLP englishPipeline = new StanfordCoreNLP(props);
    runNERTest("OntoNotes English Test 3 Class", englishPipeline, NER_BENCHMARK_WORKING_DIR, conllTestPath,
        90.77);
  }

  public void runNERTest(String testName, StanfordCoreNLP pipeline, String workingDir, String goldFilePath,
                         double f1Threshold) throws IOException {
    // load gold data
    List<Pair<String, List<String>>> conllDocs = loadCoNLLDocs(goldFilePath);
    List<Annotation> conllAnnotations = createPipelineAnnotations(conllDocs, pipeline);
    // annotate and prepare perl eval script input data
    writePerlScriptInputToPath(conllAnnotations, conllDocs, workingDir+"/conllEvalInput.txt");
    System.err.println("---");
    System.err.println("running perl eval script for "+testName);
    // get results
    String conllEvalScriptResults = runEvalScript(workingDir+"/conllEvalInput.txt");
    double modelScore = parseResults(conllEvalScriptResults);
    assertTrue(String.format(testName+" failed: should have found F1 of at least %.2f but found F1 of %.2f",
        f1Threshold, modelScore), (modelScore >= f1Threshold));
    System.err.println("Current F1 score for "+testName+" is: "+modelScore);
  }

}
