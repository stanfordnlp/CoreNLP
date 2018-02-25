package edu.stanford.nlp.patterns;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.ArgumentParser;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.TypesafeMap;
import edu.stanford.nlp.patterns.surface.*;
import edu.stanford.nlp.util.logging.Redwood;

import javax.json.*;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Created by sonalg on 3/10/15.
 */
public class TextAnnotationPatterns {

  private Map<String, Class<? extends TypesafeMap.Key<String>>> humanLabelClasses = new HashMap<>();
  private Map<String, Class<? extends TypesafeMap.Key<String>>> machineAnswerClasses = new HashMap<>();
  Properties props;
  private String outputFile;

  Counter<String> matchedSeedWords;

  private Map<String, Set<CandidatePhrase>> seedWords = new HashMap<>();
  private String backgroundSymbol ="O";

  //Properties testProps = new Properties();
  /** A logger for this class */
  private static final Redwood.RedwoodChannels logger = Redwood.channels(TextAnnotationPatterns.class);

  public TextAnnotationPatterns() throws IOException {
//    if(testPropertiesFile!= null && new File(testPropertiesFile).exists()){
//      logger.info("Loading test properties from " + testPropertiesFile);
//      testProps.load(new FileReader(testPropertiesFile));
//    }
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
    GetPatternsFromDataMultiClass<SurfacePattern> model = new GetPatternsFromDataMultiClass<>(props, Data.sents, seedWords, false, humanLabelClasses);
    //model.constVars.numIterationsForPatterns = 2;
    model.iterateExtractApply();
    return model.constVars.getLearnedWordsAsJson();
  }

  public String suggestPhrasesTest(Properties testProps, String modelPropertiesFile, String stopWordsFile) throws IllegalAccessException, InterruptedException, ExecutionException, IOException, InstantiationException, NoSuchMethodException, InvocationTargetException, ClassNotFoundException, SQLException {
    logger.info("Suggesting phrases in test");
    logger.info("test properties are " + testProps);

    Properties runProps = StringUtils.argsToPropertiesWithResolve(new String[]{"-props",modelPropertiesFile});

    String[] removeProperties = new String[]{"allPatternsDir","storePatsForEachToken","invertedIndexClass","savePatternsWordsDir","batchProcessSents","outDir","saveInvertedIndex","removeOverLappingLabels","numThreads"};

    for(String s: removeProperties)
      if(runProps.containsKey(s))
        runProps.remove(s);

    runProps.setProperty("stopWordsPatternFiles",stopWordsFile);
    runProps.setProperty("englishWordsFiles", stopWordsFile);
    runProps.setProperty("commonWordsPatternFiles", stopWordsFile);

    runProps.putAll(props);
    runProps.putAll(testProps);

    props.putAll(runProps);

    processText(false);


    GetPatternsFromDataMultiClass<SurfacePattern> model = new GetPatternsFromDataMultiClass<>(runProps, Data.sents, seedWords, true, humanLabelClasses);
    ArgumentParser.fillOptions(model, runProps);

    GetPatternsFromDataMultiClass.loadFromSavedPatternsWordsDir(model , runProps);

    Map<String, Integer> alreadyLearnedIters = new HashMap<>();
    for(String label: model.constVars.getLabels())
      alreadyLearnedIters.put(label, model.constVars.getLearnedWordsEachIter().get(label).lastEntry().getKey());


    if (model.constVars.learn) {
//      Map<String, E> p0 = new HashMap<String, SurfacePattern>();
//      Map<String, Counter<CandidatePhrase>> p0Set = new HashMap<String, Counter<CandidatePhrase>>();
//      Map<String, Set<E>> ignorePatterns = new HashMap<String, Set<E>>();
      model.iterateExtractApply(null, null, null);
    }


    Map<String, Counter<CandidatePhrase>> allExtractions = new HashMap<>();

    //Only for one label right now!
    String label = model.constVars.getLabels().iterator().next();
    allExtractions.put(label, new ClassicCounter<>());

    for(Map.Entry<String, DataInstance> sent: Data.sents.entrySet()){
      StringBuffer str = new StringBuffer();
      for(CoreLabel l : sent.getValue().getTokens()){
        if(l.get(PatternsAnnotations.MatchedPatterns.class) != null && !l.get(PatternsAnnotations.MatchedPatterns.class).isEmpty()){
          str.append(" " + l.word());
        }else{
          allExtractions.get(label).incrementCount(CandidatePhrase.createOrGet(str.toString().trim()));
          str.setLength(0);
        }
      }
    }
    allExtractions.putAll(model.matchedSeedWords);

    return  model.constVars.getSetWordsAsJson(allExtractions);
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
      props.setProperty("debug","3");
    if(!props.containsKey("thresholdWordExtract"))
      props.setProperty("thresholdWordExtract","0.00000000000000001");
    if(!props.containsKey("thresholdNumPatternsApplied"))
      props.setProperty("thresholdNumPatternsApplied", "1");
    if(!props.containsKey("writeMatchedTokensIdsForEachPhrase"))
      props.setProperty("writeMatchedTokensIdsForEachPhrase","true");

  }

  void setUpProperties(String line, boolean readFile, boolean writeOutputToFile, String additionalSeedWordsFiles) throws IOException, ClassNotFoundException {
    JsonReader jsonReader = Json.createReader(new StringReader(line));
    JsonObject objarr = jsonReader.readObject();
    jsonReader.close();
    Properties props = new Properties();


    for (String o : objarr.keySet()){
      if(o.equals("seedWords")){
        JsonObject obj = objarr.getJsonObject(o);
        for (String st : obj.keySet()){
          seedWords.put(st, new HashSet<>());
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

    if(additionalSeedWordsFiles != null && !additionalSeedWordsFiles.isEmpty()) {
      Map<String, Set<CandidatePhrase>> additionalSeedWords = GetPatternsFromDataMultiClass.readSeedWords(additionalSeedWordsFiles);
      logger.info("additional seed words are " + additionalSeedWords);
      for (String label : seedWords.keySet()) {
        if(additionalSeedWords.containsKey(label))
          seedWords.get(label).addAll(additionalSeedWords.get(label));
      }
    }

    outputFile = null;
    if(readFile) {
      System.out.println("input value is " + objarr.getString("input"));
      outputFile = props.getProperty("input") + "_processed";
      props.setProperty("file",objarr.getString("input"));
      if (writeOutputToFile && !props.containsKey("columnOutputFile"))
        props.setProperty("columnOutputFile", outputFile);
    } else{
      String systemdir = System.getProperty("java.io.tmpdir");
      File tempFile= File.createTempFile("sents", ".tmp", new File(systemdir));
      tempFile.deleteOnExit();
      IOUtils.writeStringToFile(props.getProperty("input"),tempFile.getPath(), "utf8");
      props.setProperty("file", tempFile.getAbsolutePath());
    }



    setProperties(props);
    this.props = props;

    int i = 1;
    for (String label : seedWords.keySet()) {
      String ansclstr = "edu.stanford.nlp.patterns.PatternsAnnotations$PatternLabel" + i;
      Class<? extends TypesafeMap.Key<String>> mcCl = (Class<? extends TypesafeMap.Key<String>>) Class.forName(ansclstr);
      machineAnswerClasses.put(label, mcCl);
      String humanansclstr = "edu.stanford.nlp.patterns.PatternsAnnotations$PatternHumanLabel" + i;
      humanLabelClasses.put(label, (Class<? extends TypesafeMap.Key<String>>) Class.forName(humanansclstr));
      i++;
    }


  }


  //the format of the line input is json string of maps. required keys are "input" and "seedWords". "input" can be a string or file (in which case readFile should be true.)
  // For example: {"input":"presidents.txt","seedWords":{"name":["Obama"],"place":["Chicago"]}}
  public String processText(boolean writeOutputToFile) throws IOException, InstantiationException, InvocationTargetException, ExecutionException, SQLException, InterruptedException, IllegalAccessException, ClassNotFoundException, NoSuchMethodException {
    logger.info("Starting to process text");

    logger.info("all seed words are " + seedWords);
    Pair<Map<String, DataInstance>, Map<String, DataInstance>> sentsPair = GetPatternsFromDataMultiClass.processSents(props, seedWords.keySet());

    Data.sents = sentsPair.first();

    ConstantsAndVariables constVars = new ConstantsAndVariables(props, seedWords.keySet(), machineAnswerClasses);
    for (String label : seedWords.keySet()) {
      GetPatternsFromDataMultiClass.runLabelSeedWords(Data.sents, humanLabelClasses.get(label), label, seedWords.get(label), constVars, true);
    }

    if(writeOutputToFile){
      GetPatternsFromDataMultiClass.writeColumnOutput(outputFile, false, humanLabelClasses);
      System.out.println("written the output to " + outputFile);
    }
    logger.info("Finished processing text");
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
          Data.sents.get(sentid).getTokens().get(Integer.parseInt(tokenid.toString())).set(humanLabelClasses.get(label), remove ? backgroundSymbol: label);
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
      Set<CandidatePhrase> seed = new HashSet<>();
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
