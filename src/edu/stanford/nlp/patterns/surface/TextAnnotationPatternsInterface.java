package edu.stanford.nlp.patterns.surface;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.patterns.*;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.TypesafeMap;


import javax.json.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Created by sonalg on 10/30/14.
 */
public class TextAnnotationPatternsInterface {

  private ServerSocket server;
  public TextAnnotationPatternsInterface(int portnum) throws IOException {
    server = new ServerSocket(portnum);
  }

  public enum Actions {
    //Commands that change the model
    NEWPHRASES ("adds new phrases, that is, phrase X is of label l"),
    REMOVEPHRASES ("removes phrases"),
    NEWANNOTATIONS ("adds new annotations, that is, when is the feedback is token x, y, z of sentence w are label l"),
    PROCESSFILE ("the first command to run to process the sentences and write back the tokenized/labeled file"),
    REMOVEANNOTATIONS ("opposite of NEWANNOTATIONS"),
    //Commands that ask for an answer
    SUGGEST ("ask for suggestions. Runs GetPatternsFromDataMultiClass"),
    MATCHEDTOKENSBYALL ("Sentence and token ids (starting at 0) matched by all the phrases"),
    MATCHEDTOKENSBYPHRASE ("Sentence and token ids (starting at 0) matched by the given phrase"),
    ALLANNOTATIONS ("If a token is labeled, it's label. returns for each sentence id, labeled_tokenid -> label. Only for tokens that are labeled."),
    ANNOTATIONSBYSENT ("For the given sentence, the labeled token ids and their corresponding labels"),
    SUMMARY ("Phrases that have been labeled by humans"),
    //Miscellaneous
    NONE ("Nothing happens"),
    CLOSE ("Close the socket");

    String whatitdoes;
    Actions(String whatitdoes){
      this.whatitdoes = whatitdoes;
    }
  };


  /**
   * A private thread to handle capitalization requests on a particular
   * socket.  The client terminates the dialogue by sending a single line
   * containing only a period.
   */
  private static class PerformActionUpdateModel extends Thread {
    private Socket socket;
    private int clientNumber;
    //GetPatternsFromDataMultiClass<SurfacePattern> model;
    Map<String, Class<? extends TypesafeMap.Key<String>>> humanLabelClasses = new HashMap<>();
    Map<String, Class<? extends TypesafeMap.Key<String>>> machineAnswerClasses = new HashMap<>();

    Properties props;

    Map<String, Set<CandidatePhrase>> seedWords;
    private String backgroundSymbol ="O";

    public PerformActionUpdateModel(Socket socket, int clientNumber) {
      this.socket = socket;
      this.clientNumber = clientNumber;
      log("New connection with client# " + clientNumber + " at " + socket);
    }

    /**
     * Services this thread's client by first sending the
     * client a welcome message then repeatedly reading strings
     * and sending back the capitalized version of the string.
     */
    public void run() {
      PrintWriter out = null;
      String msg = "";

      // Decorate the streams so we can send characters
      // and not just bytes.  Ensure output is flushed
      // after every newline.
      BufferedReader in = null;
      try {
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);
      } catch (IOException e) {
        try {
          socket.close();
        } catch (IOException e1) {
          e1.printStackTrace();
        }
        e.printStackTrace();
      }
      // Send a welcome message to the client.
      out.println("The possible actions are " + Arrays.toString(Actions.values()) + ".Enter a line with only a period to quit");

      Actions nextlineAction = Actions.NONE;
      // Get messages from the client, line by line; return them
      // capitalized
      while (true) {
        try {
          String line = in.readLine();
          if (line == null || line.equals(".")) {
            break;
          }

          String[] toks = line.split("###");
          try {
            nextlineAction = Actions.valueOf(toks[0].trim());
          } catch (IllegalArgumentException e) {
            System.out.println("read " + toks[0] + " and cannot understand");
            msg = "Did not understand " + toks[0] + ". POSSIBLE ACTIONS ARE: " + Arrays.toString(Actions.values());
          }

          String input = toks.length == 2 ? toks[1] : null;
          switch (nextlineAction) {
            case NEWPHRASES:
              msg = doNewPhrases(input);
              break;
            case REMOVEPHRASES:
              msg = doRemovePhrases(input);
              break;
            case NEWANNOTATIONS:
              msg = doNewAnnotations(input);
              break;
            case PROCESSFILE:
              msg = processFile(input);
              break;
            case REMOVEANNOTATIONS:
              msg = doRemoveAnnotations(input);
              break;
            case SUGGEST:
              msg = this.suggestPhrases();
              break;
            case MATCHEDTOKENSBYALL:
              msg = this.getMatchedTokensByAllPhrases();
              break;
            case MATCHEDTOKENSBYPHRASE:
              msg = this.getMatchedTokensByPhrase(input);
              break;
            case ALLANNOTATIONS:
              msg = this.getAllAnnotations();
              break;
            case ANNOTATIONSBYSENT:
              msg = this.getAllAnnotations(input);
              break;
            case SUMMARY:
              msg = this.currentSummary();
              break;
            case NONE:
              break;
            case CLOSE:
              msg = "bye!";
              break;
          }
          System.out.println("sending msg " + msg);
        } catch (Exception e) {
          msg = "ERROR " + e.toString().replaceAll("\n", "\t") + ". REDO.";
          nextlineAction = Actions.NONE;
          log("Error handling client# " + clientNumber);
          e.printStackTrace();
        } finally {
          out.println(msg);
        }
      }
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

    private String getAllAnnotations(String input) {
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


    private String suggestPhrases() throws IOException, ClassNotFoundException, IllegalAccessException, InterruptedException, ExecutionException, InstantiationException, NoSuchMethodException, InvocationTargetException {
      resetPatternLabelsInSents(Data.sents);
      GetPatternsFromDataMultiClass<SurfacePattern> model = new GetPatternsFromDataMultiClass<SurfacePattern>(props, Data.sents, seedWords, false, machineAnswerClasses);
      model.constVars.numIterationsForPatterns = 2;
      model.iterateExtractApply();
      return model.constVars.getLearnedWordsAsJson();
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

    private String getMatchedTokensByAllPhrases(){
      return GetPatternsFromDataMultiClass.matchedTokensByPhraseJsonString();
    }

    private String getMatchedTokensByPhrase(String input){
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
    //the format of the line input is json string of maps. required keys are "file" and "seedWordsFiles". For example: {"file":"presidents.txt","seedWordsFiles":"name;place"}
    private String processFile(String line) throws IOException, InstantiationException, InvocationTargetException, ExecutionException, SQLException, InterruptedException, IllegalAccessException, ClassNotFoundException, NoSuchMethodException {
      JsonReader jsonReader = Json.createReader(new StringReader(line));
      JsonObject objarr = jsonReader.readObject();
      jsonReader.close();
      Properties props = new Properties();

      for (Map.Entry<String, JsonValue> o : objarr.entrySet()){
        props.setProperty(o.getKey(), objarr.getString(o.getKey()));
      }

      System.out.println("file value is " + objarr.getString("file"));

      String outputfile =props.getProperty("file")+"_processed";
      if(!props.containsKey("columnOutputFile"))
        props.setProperty("columnOutputFile",outputfile);

      setProperties(props);
      this.props = props;
      seedWords = GetPatternsFromDataMultiClass.readSeedWords(props);
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

      ConstantsAndVariables<SurfacePattern> constVars = new ConstantsAndVariables<SurfacePattern>(props, seedWords.keySet(), machineAnswerClasses);
      for (String label : seedWords.keySet()) {
        GetPatternsFromDataMultiClass.runLabelSeedWords(Data.sents, humanLabelClasses.get(label), label, seedWords.get(label), constVars, true);
      }

      GetPatternsFromDataMultiClass.writeColumnOutput(outputfile, false, humanLabelClasses);

      System.out.println("written the output to " + outputfile);
      return "SUCCESS";
    }


    private String doRemovePhrases(String line){
      return ("not yet implemented");
    }

    private String doRemoveAnnotations(String line) {
      int tokensNum = changeAnnotation(line, true);
      return "SUCCESS . Labeled " + tokensNum + " tokens ";
    }

    //input is a json string, example:{“name”:[“sent1”:”1,2,4,6”,”sent2”:”11,13,15”], “birthplace”:[“sent1”:”3,5”]}
    private String doNewAnnotations(String line) {
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

    private String currentSummary(){
      return "Phrases hand labeled : "+seedWords.toString();
    }

    //line is a jsonstring of map of label to array of strings; ex: {"name":["Bush","Carter","Obama"]}
    private String doNewPhrases(String line) throws Exception {
      System.out.println("adding new phrases");
      ConstantsAndVariables<SurfacePattern> constVars = new ConstantsAndVariables<SurfacePattern>(props, humanLabelClasses.keySet(), humanLabelClasses);
      JsonReader jsonReader = Json.createReader(new StringReader(line));
      JsonObject objarr = jsonReader.readObject();
      for(Map.Entry<String, JsonValue> o: objarr.entrySet()){
        String label = o.getKey();
        Set<CandidatePhrase> seed = new HashSet<CandidatePhrase>();
        JsonArray arr = objarr.getJsonArray(o.getKey());
        for(int i = 0; i < arr.size(); i++){
          String seedw = arr.getString(i);
          System.out.println("adding " + seedw + " to seed ");
          seed.add(new CandidatePhrase(seedw));
        }
        seedWords.get(label).addAll(seed);
        constVars.addSeedWords(label, seed);
        GetPatternsFromDataMultiClass.runLabelSeedWords(Data.sents, humanLabelClasses.get(label), label, seed, constVars, false);
        //model.labelWords(label, labelclass, Data.sents, seed);
      }
      return "SUCCESS added new phrases";
    }

    /**
     * Logs a simple message.  In this case we just write the
     * message to the server applications standard output.
     */
    private void log(String message) {
      System.out.println(message);
    }


  }

  /**
   * Application method to run the server runs in an infinite loop
   * listening on port 9898.  When a connection is requested, it
   * spawns a new thread to do the servicing and immediately returns
   * to listening.  The server keeps a unique client number for each
   * client that connects just to show interesting logging
   * messages.  It is certainly not necessary to do this.
   */
  public static void main(String[] args) throws IOException {
    System.out.println("The modeling server is running.");
    int clientNumber = 0;
    ServerSocket listener = new ServerSocket(9898);
    try {
      while (true) {
        new PerformActionUpdateModel(listener.accept(), clientNumber++).start();
      }
    } finally {
      listener.close();
    }
  }

}
