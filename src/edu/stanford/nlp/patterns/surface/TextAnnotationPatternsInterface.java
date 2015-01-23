package edu.stanford.nlp.patterns.surface;

import edu.stanford.nlp.patterns.GetPatternsFromDataMultiClass;
import edu.stanford.nlp.patterns.PatternFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Properties;
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

  public enum Actions {NEWPHRASES, REMOVEPHRASES, NEWANNOTATIONS, NONE, CLOSE, PROCESSFILE};


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
      try {

        // Decorate the streams so we can send characters
        // and not just bytes.  Ensure output is flushed
        // after every newline.
        BufferedReader in = new BufferedReader(
          new InputStreamReader(socket.getInputStream()));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

        // Send a welcome message to the client.
        out.println("Hello, you are client #" + clientNumber + ".");
        out.println("Enter a line with only a period to quit\n");

        Actions nextlineAction = Actions.NONE;
        // Get messages from the client, line by line; return them
        // capitalized
        while (true) {
          String input = in.readLine();
          if (input == null || input.equals(".")) {
            break;
          }

          String msg = "";

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
            processFile(input);
            msg = "DONEPROCESS";
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
            }
          }
          System.out.println("sending msg " + msg);

          out.println(msg);

          //out.println(input.toUpperCase());
        }
      } catch (IOException e) {
        log("Error handling client# " + clientNumber + ": " + e);
      } catch (IllegalAccessException e) {
        log("Error handling client# " + clientNumber + ": " + e);
      } catch (InterruptedException e) {
        log("Error handling client# " + clientNumber + ": " + e);
      } catch (ExecutionException e) {
        log("Error handling client# " + clientNumber + ": " + e);
      } catch (InstantiationException e) {
        log("Error handling client# " + clientNumber + ": " + e);
      } catch (SQLException e) {
        log("Error handling client# " + clientNumber + ": " + e);
      } catch (NoSuchMethodException e) {
        log("Error handling client# " + clientNumber + ": " + e);
      } catch (InvocationTargetException e) {
        log("Error handling client# " + clientNumber + ": " + e);
      } catch (ClassNotFoundException e) {
        log("Error handling client# " + clientNumber + ": " + e);
      } finally {
        try {
          socket.close();
        } catch (IOException e) {
          log("Couldn't close a socket, what's going on?");
        }
        log("Connection with client# " + clientNumber + " closed");
      }
    }

    private void processFile(String file) throws IOException, InstantiationException, InvocationTargetException, ExecutionException, SQLException, InterruptedException, IllegalAccessException, ClassNotFoundException, NoSuchMethodException {
      String outputfile =file+"_processed";
      Properties props = new Properties();
      props.setProperty("file",file);
      props.setProperty("fileFormat","txt");
      props.setProperty("learn","false");
      props.setProperty("labelUsingSeedSets","false");
      props.setProperty("patternType","SURFACE");
      props.setProperty("columnOutputFile",outputfile);
      props.setProperty("preserveSentenceSequence", "true");
      model = GetPatternsFromDataMultiClass.<SurfacePattern>run(props);
      System.out.println("written the output to " + outputfile);
    }


    static private void doRemovePhrases(String line) {
      model.labelWords();
      System.out.println("removing phrases");
    }

    static private void doNewAnnotations(String line) {
      System.out.println("Adding new annotations");
    }

    static private void doNewPhrases(String line) {
      System.out.println("adding new phrases");
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
  public static void main(String[] args) throws Exception {
    System.out.println("The capitalization server is running.");
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
