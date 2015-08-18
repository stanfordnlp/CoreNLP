package edu.stanford.nlp.patterns;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.patterns.*;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.TypesafeMap;
import edu.stanford.nlp.patterns.surface.*;

import javax.json.*;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

/**
 * Created by sonalg on 3/10/15.
 */
public class TextAnnotationPatterns {


  Map<String, Class<? extends TypesafeMap.Key<String>>> humanLabelClasses = new HashMap<String, Class<? extends TypesafeMap.Key<String>>>();
  Map<String, Class<? extends TypesafeMap.Key<String>>> machineAnswerClasses = new HashMap<String, Class<? extends TypesafeMap.Key<String>>>();
  Properties props;

  Map<String, Set<CandidatePhrase>> seedWords = new HashMap<String, Set<CandidatePhrase>>();
  private String backgroundSymbol ="O";

  Properties testProps = new Properties();
  Logger logger = Logger.getAnonymousLogger();

  public TextAnnotationPatterns() throws IOException {
    testProps.load(new FileReader("test.properties"));
  }

  public String getAllAnnotations() {
    JsonObjectBuilder obj = Json.createObjectBuilder();
    for(Map.Entry<String, DataInstance> sent: Data.sents.entrySet()){
      boolean sentHasLabel = false;
      JsonObjectBuilder objsent = Json.createObjectBuilder();
      int tokenid = 0;
      for(CoreLabel l : sent.getValue().getTokens()){
        boolean haslabel = false;
        JsonArrayBuilder labelArr = Json.createArrayBuilder();
        for(Map.Entry<String, Class<? extends TypesafeMap.Key<String>>> en: this.humanLabelClasses.entrySet()){
          if(!l.get(en.getValue()).equals(backgroundSymbol)){
            haslabel = true;
            sentHasLabel = true;
            labelArr.add(en.getKey());
          }
        }
        if(haslabel)
          objsent.add(String.valueOf(tokenid), labelArr);
        tokenid++;
      }
      if(sentHasLabel)
        obj.add(sent.getKey(), objsent);
    }
    return obj.build().toString();
  }

  public String getAllAnnotations(String input) {
    JsonObjectBuilder objsent = Json.createObjectBuilder();
    int tokenid = 0;
    for(CoreLabel l : Data.sents.get(input).getTokens()){
      boolean haslabel = false;
      JsonArrayBuilder labelArr = Json.createArrayBuilder();
      for(Map.Entry<String, Class<? extends TypesafeMap.Key<String>>> en: this.humanLabelClasses.entrySet()){
        if(!l.get(en.getValue()).equals(backgroundSymbol)){
          haslabel = true;
          labelArr.add(en.getKey());
        }
      }
      if(haslabel)
        objsent.add(String.valueOf(tokenid), labelArr);
      tokenid++;
    }
    return objsent.build().toString();
  }


  public String suggestPhrases() throws IOException, ClassNotFoundException, IllegalAccessException, InterruptedException, ExecutionException, InstantiationException, NoSuchMethodException, InvocationTargetException {
    resetPatternLabelsInSents(Data.sents);
    GetPatternsFromDataMultiClass<SurfacePattern> model = new GetPatternsFromDataMultiClass<SurfacePattern>(props, Data.sents, seedWords, false, machineAnswerClasses);
    //model.constVars.numIterationsForPatterns = 2;
    model.iterateExtractApply();
    return model.constVars.getLearnedWordsAsJson();
  }

  public String suggestPhrasesTest() throws IllegalAccessException, InterruptedException, ExecutionException, IOException, InstantiationException, NoSuchMethodException, InvocationTargetException, ClassNotFoundException {
    Properties runProps = new Properties(props);
    runProps.putAll(testProps);
    GetPatternsFromDataMultiClass<SurfacePattern> model = new GetPatternsFromDataMultiClass<SurfacePattern>(runProps, Data.sents, seedWords, false, machineAnswerClasses);
    model.iterateExtractApply();
    return model.constVars.getLearnedWordsAsJsonLastIteration();
  }

  //label the sents with the labels provided by humans
  private void resetPatternLabelsInSents(Map<String, DataInstance> sents) {
    for(Map.Entry<String, DataInstance> sent: sents.entrySet()){
      for(CoreLabel l : sent.getValue().getTokens()){
        for(Map.Entry<String, Class<? extends TypesafeMap.Key<String>>> cl: humanLabelClasses.entrySet()){
          l.set(machineAnswerClasses.get(cl.getKey()), l.get(cl.getValue()));
        }
      }
    }
  }

  public String getMatchedTokensByAllPhrases(){
    return GetPatternsFromDataMultiClass.matchedTokensByPhraseJsonString();
  }

  public String getMatchedTokensByPhrase(String input){
    return GetPatternsFromDataMultiClass.matchedTokensByPhraseJsonString(input);
  }

  private void setProperties(Properties props){
    if(!props.containsKey("fileFormat"))
      props.setProperty("fileFormat","txt");

    if(!props.containsKey("learn"))
      props.setProperty("learn","false");
    if(!props.containsKey("patternType"))
      props.setProperty("patternType","SURFACE");


    props.setProperty("preserveSentenceSequence", "true");
    if(!props.containsKey("debug"))
      props.setProperty("debug","4");
    if(!props.containsKey("thresholdWordExtract"))
      props.setProperty("thresholdWordExtract","0.00000000000000001");
    if(!props.containsKey("thresholdNumPatternsApplied"))
      props.setProperty("thresholdNumPatternsApplied", "1");
    if(!props.containsKey("writeMatchedTokensIdsForEachPhrase"))
      props.setProperty("writeMatchedTokensIdsForEachPhrase","true");

  }


  //the format of the line input is json string of maps. required keys are "input" and "seedWords". "input" can be a string or file (in which case readFile should be true.)
  // For example: {"input":"presidents.txt","seedWords":{"name":["Obama"],"place":["Chicago"]}}
  public String processText(String line, boolean readFile, boolean writeOutputToFile) throws IOException, InstantiationException, InvocationTargetException, ExecutionException, SQLException, InterruptedException, IllegalAccessException, ClassNotFoundException, NoSuchMethodException {

    JsonReader jsonReader = Json.createReader(new StringReader(line));
    JsonObject objarr = jsonReader.readObject();
    jsonReader.close();
    Properties props = new Properties();

    for (String o : objarr.keySet()){
      if(o.equals("seedWords")){
        JsonObject obj = objarr.getJsonObject(o);
        for (String st : obj.keySet()){
          seedWords.put(st, new HashSet<CandidatePhrase>());
          JsonArray arr  = obj.getJsonArray(st);
          for(int i = 0; i < arr.size(); i++){
            String val = arr.getString(i);
            seedWords.get(st).add(CandidatePhrase.createOrGet(val));
            System.out.println("adding " + val + " for label " + st);
          }
        }
      }else
        props.setProperty(o, objarr.getString(o));
    }

    System.out.println("seedwords are " + seedWords);
    String outputfile = null;

    if(readFile) {
      System.out.println("input value is " + objarr.getString("input"));
      outputfile = props.getProperty("input") + "_processed";
      props.setProperty("file",objarr.getString("input"));
      if (writeOutputToFile && !props.containsKey("columnOutputFile"))
        props.setProperty("columnOutputFile", outputfile);
    } else{
      String systemdir = System.getProperty("java.io.tmpdir");
      File tempFile= File.createTempFile("sents", ".tmp", new File(systemdir));
      tempFile.deleteOnExit();
      IOUtils.writeStringToFile(props.getProperty("input"),tempFile.getPath(), "utf8");
      props.setProperty("file", tempFile.getAbsolutePath());
    }

    setProperties(props);
    this.props = props;
    Pair<Map<String, DataInstance>, Map<String, DataInstance>> sentsPair = GetPatternsFromDataMultiClass.processSents(props, seedWords.keySet());

    Data.sents = sentsPair.first();

    int i = 1;
    for (String label : seedWords.keySet()) {
      String ansclstr = "edu.stanford.nlp.patterns.PatternsAnnotations$PatternLabel" + i;
      Class<? extends TypesafeMap.Key<String>> mcCl = (Class<? extends TypesafeMap.Key<String>>) Class.forName(ansclstr);
      machineAnswerClasses.put(label, mcCl);
      String humanansclstr = "edu.stanford.nlp.patterns.PatternsAnnotations$PatternHumanLabel" + i;
      humanLabelClasses.put(label, (Class<? extends TypesafeMap.Key<String>>) Class.forName(humanansclstr));
      i++;
    }

    ConstantsAndVariables constVars = new ConstantsAndVariables(props, seedWords.keySet(), machineAnswerClasses);
    for (String label : seedWords.keySet()) {
      GetPatternsFromDataMultiClass.runLabelSeedWords(Data.sents, humanLabelClasses.get(label), label, seedWords.get(label), constVars, true);
    }

    if(writeOutputToFile){
      GetPatternsFromDataMultiClass.writeColumnOutput(outputfile, false, humanLabelClasses);
      System.out.println("written the output to " + outputfile);
    }
    return "SUCCESS";
  }


  public String doRemovePhrases(String line){
    return ("not yet implemented");
  }

  public String doRemoveAnnotations(String line) {
    int tokensNum = changeAnnotation(line, true);
    return "SUCCESS . Labeled " + tokensNum + " tokens ";
  }

  //input is a json string, example:{“name”:[“sent1”:”1,2,4,6”,”sent2”:”11,13,15”], “birthplace”:[“sent1”:”3,5”]}
  public String doNewAnnotations(String line) {
    int tokensNum = changeAnnotation(line, false);
    return "SUCCESS . Labeled " + tokensNum + " tokens ";
  }

  private int changeAnnotation(String line, boolean remove){
    int tokensNum = 0;
    JsonReader jsonReader = Json.createReader(new StringReader(line));
    JsonObject objarr = jsonReader.readObject();
    for(String label: objarr.keySet()) {
      JsonObject obj4label = objarr.getJsonObject(label);
      for(String sentid: obj4label.keySet()){
        JsonArray tokenArry = obj4label.getJsonArray(sentid);
        for(JsonValue tokenid: tokenArry){
          tokensNum ++;
          Data.sents.get(sentid).getTokens().get(Integer.valueOf(tokenid.toString())).set(humanLabelClasses.get(label), remove ? backgroundSymbol: label);
        }
      }
    }
    return tokensNum;
  }

  public String currentSummary(){
    return "Phrases hand labeled : "+seedWords.toString();
  }

  //line is a jsonstring of map of label to array of strings; ex: {"name":["Bush","Carter","Obama"]}
  public String doNewPhrases(String line) throws Exception {
    System.out.println("adding new phrases");
    ConstantsAndVariables constVars = new ConstantsAndVariables(props, humanLabelClasses.keySet(), humanLabelClasses);
    JsonReader jsonReader = Json.createReader(new StringReader(line));
    JsonObject objarr = jsonReader.readObject();
    for(Map.Entry<String, JsonValue> o: objarr.entrySet()){
      String label = o.getKey();
      Set<CandidatePhrase> seed = new HashSet<CandidatePhrase>();
      JsonArray arr = objarr.getJsonArray(o.getKey());
      for(int i = 0; i < arr.size(); i++){
        String seedw = arr.getString(i);
        System.out.println("adding " + seedw + " to seed ");
        seed.add(CandidatePhrase.createOrGet(seedw));
      }
      seedWords.get(label).addAll(seed);
      constVars.addSeedWords(label, seed);
      GetPatternsFromDataMultiClass.runLabelSeedWords(Data.sents, humanLabelClasses.get(label), label, seed, constVars, false);
      //model.labelWords(label, labelclass, Data.sents, seed);
    }
    return "SUCCESS added new phrases";
  }

}
