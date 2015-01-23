package edu.stanford.nlp.patterns.surface;

import edu.stanford.nlp.patterns.Data;
import edu.stanford.nlp.patterns.GetPatternsFromDataMultiClass;
import edu.stanford.nlp.patterns.PatternFactory;

import javax.json.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static edu.stanford.nlp.patterns.PatternFactory.PatternType;
import static edu.stanford.nlp.patterns.PatternFactory.PatternType.*;

/**
 * Created by sonalg on 10/30/14.
 */
public class TextAnnotationPatternsInterface {

  private ServerSocket server;
  public TextAnnotationPatternsInterface(int portnum) throws IOException {
    server = new ServerSocket(portnum);
  }

  public enum Actions {NEWPHRASES, REMOVEPHRASES, NEWANNOTATIONS, NONE, CLOSE, SUMMARY, PROCESSFILE, SUGGEST};


  /**
   * A private thread to handle capitalization requests on a particular
   * socket.  The client terminates the dialogue by sending a single line
   * containing only a period.
   */
  private static class PerformActionUpdateModel extends Thread {
    private Socket socket;
    private int clientNumber;
    GetPatternsFromDataMultiClass<SurfacePattern> model;


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
      }catch (IOException e) {
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
            String input = in.readLine();
          if (input == null || input.equals(".")) {
            break;
          }

          ;

          if(nextlineAction.equals(Actions.NEWPHRASES)){
            msg = "Added new phrases";
            doNewPhrases(input);
            nextlineAction = Actions.NONE;
          } else if(nextlineAction.equals(Actions.NEWANNOTATIONS)){
            doNewAnnotations(input);
            msg = "Added new annotations";
            nextlineAction = Actions.NONE;
          } else if(nextlineAction.equals(Actions.REMOVEPHRASES)){
            msg = "Removed phrases";
            doRemovePhrases(input);
            nextlineAction = Actions.NONE;
          } else if(nextlineAction.equals(Actions.PROCESSFILE)){
            msg = processFile(input);
            nextlineAction = Actions.NONE;
          }else{
            try{
              nextlineAction = Actions.valueOf(input.trim());
            }catch(IllegalArgumentException e){
              System.out.println("read " + input + " and cannot understand");
              msg = "Did not understand " + input + ". POSSIBLE ACTIONS ARE: " + Arrays.toString(Actions.values());
            }
            if(nextlineAction.equals(Actions.NEWPHRASES))
              msg = "Please write the new phrases to add in the next line ";
            else if(nextlineAction.equals(Actions.NEWANNOTATIONS))
              msg = "Please write the new annotations to add in the next line ";
            else if(nextlineAction.equals(Actions.REMOVEPHRASES))
              msg = "Please write the  phrases to remove in the next line ";
            else if(nextlineAction.equals(Actions.CLOSE))
              msg = "bye!";
            else if(nextlineAction.equals(Actions.PROCESSFILE)){
              msg = "please write the filename to process";
            } else if (nextlineAction.equals(Actions.SUMMARY)){
              msg = this.currentSummary();
              nextlineAction = Actions.NONE;
            } else if (nextlineAction.equals(Actions.SUGGEST)){
              msg = this.suggestPhrases();
              nextlineAction = Actions.NONE;
            }
          }
          System.out.println("sending msg " + msg);
          } catch (Exception e) {
            msg = "ERROR " + e.toString().replaceAll("\n","\t") +". REDO.";
            nextlineAction = Actions.NONE;
            log("Error handling client# " + clientNumber);
            e.printStackTrace();
          } finally {
            out.println(msg);
          }
        }
      }


    private String suggestPhrases() throws IOException, ClassNotFoundException {
      model.constVars.numIterationsForPatterns = 2;
      model.iterateExtractApply();
      return model.constVars.getLearnedWords().toString();
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

      if(!props.containsKey("fileFormat"))
        props.setProperty("fileFormat","txt");

      if(!props.containsKey("learn"))
        props.setProperty("learn","false");
      if(!props.containsKey("patternType"))
        props.setProperty("patternType","SURFACE");
      if(!props.containsKey("columnOutputFile"))
        props.setProperty("columnOutputFile",outputfile);

      props.setProperty("preserveSentenceSequence", "true");
      if(!props.containsKey("debug"))
        props.setProperty("debug","4");
      if(!props.containsKey("thresholdWordExtract"))
        props.setProperty("thresholdWordExtract","0.00000000000000001");
      if(!props.containsKey("thresholdNumPatternsApplied"))
        props.setProperty("thresholdNumPatternsApplied", "1");

      model = GetPatternsFromDataMultiClass.<SurfacePattern>run(props);
      System.out.println("written the output to " + outputfile);
      return "SUCCESS";
    }


    private void doRemovePhrases(String line) {

      System.out.println("removing phrases");
    }

    private void doNewAnnotations(String line) {
      System.out.println("Adding new annotations");
    }

    private String currentSummary(){
      return "HAND:"+model.constVars.getSeedLabelDictionary().toString()+"\t\tLEARNED:"+model.constVars.getLearnedWords();
    }

    //line is a jsonstring of map of label to array of strings; ex: {"name":["Bush","Carter","Obama"]}
    private void doNewPhrases(String line) throws Exception {
      System.out.println("adding new phrases");
      JsonReader jsonReader = Json.createReader(new StringReader(line));
      JsonObject objarr = jsonReader.readObject();
      for(Map.Entry<String, JsonValue> o: objarr.entrySet()){
        String label = o.getKey();
        Set<String> seed = new HashSet<String>();
        JsonArray arr = objarr.getJsonArray(o.getKey());
        for(int i = 0; i < arr.size(); i++){
          String seedw = arr.getString(i);
          System.out.println("adding " + seedw + " to seed ");
          seed.add(seedw);
        }
        model.constVars.addSeedWords(label, seed);
        model.labelWords(label, Data.sents, seed);
      }
      System.out.println("added new phrases");
    }

    /**
     * Logs a simple message.  In this case we just write the
     * message to the server applications standard output.
     */
    private void log(String message) {
      System.out.println(message);
    }
  }

  /*
  public void serve()
  {
    try
    {
      while (true)
      {
        Socket client = server.accept();
        client.setTcpNoDelay(true);
        BufferedReader r = new BufferedReader(new InputStreamReader(client.getInputStream()));
        PrintWriter w = new PrintWriter(client.getOutputStream(), true);
        w.println("POSSIBLE ACTIONS ARE: " + Arrays.toString(Actions.values()));
        w.flush();
        String line;
        Actions nextlineAction = Actions.NONE;
        do
        {
          line = r.readLine();
          System.out.println("read " + line);
          String msg = "";
          if ( line != null){
            if(nextlineAction.equals(Actions.NEWPHRASES)){
              msg = "Added new phrases";
              doNewPhrases(line);
              nextlineAction = Actions.NONE;
            } else if(nextlineAction.equals(Actions.NEWANNOTATIONS)){
              doNewAnnotations(line);
              msg = "Added new annotations";
              nextlineAction = Actions.NONE;
            } else if(nextlineAction.equals(Actions.REMOVEPHRASES)){
              msg = "Removed phrases";
              doRemovePhrases(line);
              nextlineAction = Actions.NONE;
            } else{
               try{
                nextlineAction = Actions.valueOf(line.trim());
               }catch(IllegalArgumentException e){
                 System.out.println("read " + line + " and cannot understand");
                msg = "Did not understand " + line + ". POSSIBLE ACTIONS ARE: " + Arrays.toString(Actions.values());
               }
              if(nextlineAction.equals(Actions.NEWPHRASES))
                msg = "Please write the new phrases to add in the next line ";
              else if(nextlineAction.equals(Actions.NEWANNOTATIONS))
                msg = "Please write the new annotations to add in the next line ";
              else if(nextlineAction.equals(Actions.REMOVEPHRASES))
                msg = "Please write the  phrases to remove in the next line ";
              else if(nextlineAction.equals(Actions.CLOSE))
                msg = "bye!";
            }
            System.out.println("sending msg " + msg);

            w.println(msg);
            w.flush();
          }
        }
        while (!nextlineAction.equals(Actions.CLOSE));
        client.close();
      }
    }
    catch (Exception err)
    {
    throw new RuntimeException(err);
    }
  }*/

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
