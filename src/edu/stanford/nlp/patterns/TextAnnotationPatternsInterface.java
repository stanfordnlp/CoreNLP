package edu.stanford.nlp.patterns;

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
    TextAnnotationPatterns annotate;



    public PerformActionUpdateModel(Socket socket, int clientNumber) throws IOException {
      this.socket = socket;
      this.clientNumber = clientNumber;
      this.annotate =  new TextAnnotationPatterns();
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
              msg = annotate.doNewPhrases(input);
              break;
            case REMOVEPHRASES:
              msg = annotate.doRemovePhrases(input);
              break;
            case NEWANNOTATIONS:
              msg = annotate.doNewAnnotations(input);
              break;
            case PROCESSFILE:
              annotate.setUpProperties(input, true, true, null);
              msg = annotate.processText(true);
              break;
            case REMOVEANNOTATIONS:
              msg = annotate.doRemoveAnnotations(input);
              break;
            case SUGGEST:
              msg = annotate.suggestPhrases();
              break;
            case MATCHEDTOKENSBYALL:
              msg = annotate.getMatchedTokensByAllPhrases();
              break;
            case MATCHEDTOKENSBYPHRASE:
              msg = annotate.getMatchedTokensByPhrase(input);
              break;
            case ALLANNOTATIONS:
              msg = annotate.getAllAnnotations();
              break;
            case ANNOTATIONSBYSENT:
              msg = annotate.getAllAnnotations(input);
              break;
            case SUMMARY:
              msg = annotate.currentSummary();
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


    /**
     * Logs a simple message.  In this case we just write the
     * message to the server applications standard output.
     */
    private static void log(String message) {
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
