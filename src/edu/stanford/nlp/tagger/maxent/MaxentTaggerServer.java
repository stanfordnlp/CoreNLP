package edu.stanford.nlp.tagger.maxent; 
import edu.stanford.nlp.util.logging.Redwood;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.*;

import edu.stanford.nlp.io.EncodingPrintWriter;
import edu.stanford.nlp.util.StringUtils;


/** A POS tagger server for the Stanford POS Tagger.
 *  Runs on a socket and waits for text to tag and returns the
 *  tagged text.
 *
 * @author Christopher Manning
 */
public class MaxentTaggerServer  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(MaxentTaggerServer.class);

  //// Variables

  /**
   * Debugging toggle.
   */
  private boolean DEBUG = false;

  private final String charset;

  /**
   * The listener socket of this server.
   */
  private final ServerSocket listener;

  /**
   * The classifier that does the actual tagging.
   */
  private final MaxentTagger.TaggerWrapper tagger;


  //// Constructors

  /**
   * Creates a new tagger server on the specified port.
   *
   * @param port the port this NERServer listens on.
   * @param tagger The classifier which will do the tagging
   * @param charset The character set for encoding Strings over the socket stream, e.g., "utf-8"
   * @throws java.io.IOException If there is a problem creating a ServerSocket
   */
  public MaxentTaggerServer(int port, MaxentTagger.TaggerWrapper tagger, String charset) 
    throws IOException 
  {
    this.tagger = tagger;
    listener = new ServerSocket(port);
    this.charset = charset;
  }

  //// Public Methods

  /**
   * Runs this tagger server.
   */
  @SuppressWarnings({"InfiniteLoopStatement", "ConstantConditions", "null"})
  public void run() {
    if (DEBUG) {
      log.info("Starting server loop");
    }
    Socket client = null;
    while (true) {
      try {
        client = listener.accept();
        if (DEBUG) {
          log.info("Accepted request from ");
          log.info(client.getInetAddress().getHostName());
        }
        new Session(client);
      } catch (Exception e1) {
        log.info("MaxentTaggerServer: couldn't accept");
        e1.printStackTrace(System.err);
        try {
          client.close();
        } catch (Exception e2) {
          log.info("MaxentTaggerServer: couldn't close client");
          e2.printStackTrace(System.err);
        }
      }
    }
  }


  //// Inner Classes

  /**
   * A single user session, accepting one request, processing it, and
   * sending back the results.
   */
  private class Session extends Thread {

  //// Instance Fields

    /**
     * The socket to the client.
     */
    private final Socket client;

    /**
     * The input stream from the client.
     */
    private final BufferedReader in;

    /**
     * The output stream to the client.
     */
    private PrintWriter out;


    //// Constructors

    private Session(Socket socket) throws IOException {
      client = socket;
      in = new BufferedReader(new InputStreamReader(client.getInputStream(), charset));
      out = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), charset));
      start();
    }


    //// Public Methods

    /**
     * Runs this session by reading a string, tagging it, and writing
     * back the result.  The input should be a single line (no embedded
     * newlines), which represents a whole sentence or document.
     */
    @Override
    public void run() {
      if (DEBUG) {log.info("Created new session");}

      try {
        String input = in.readLine();
        if (DEBUG) {
          EncodingPrintWriter.err.println("Receiving: \"" + input + '\"', charset);
        }
        if (! (input == null)) {
          String output = tagger.apply(input);
          if (DEBUG) {
            EncodingPrintWriter.err.println("Sending: \"" + output + '\"', charset);
          }
          out.print(output);
          out.flush();
        }
        close();
      } catch (IOException e) {
        log.info("MaxentTaggerServer:Session: couldn't read input or error running POS tagger");
        e.printStackTrace(System.err);
      } catch (NullPointerException npe) {
        log.info("MaxentTaggerServer:Session: connection closed by peer");
        npe.printStackTrace(System.err);
      }
    }

    /**
     * Terminates this session gracefully.
     */
    private void close() {
      try {
        in.close();
        out.close();
        client.close();
      } catch (Exception e) {
        log.info("MaxentTaggerServer:Session: can't close session");
        e.printStackTrace();
      }
    }

  } // end class Session


  /** This example sends material to the tagger server one line at a time.
   *  Each line should be at least a whole sentence, but can be a whole
   *  document.
   */
  private static class TaggerClient {

    private TaggerClient() {}

    private static void communicateWithMaxentTaggerServer(String host, int port, String charset) throws IOException {

      if (host == null) {
        host = "localhost";
      }

      BufferedReader stdIn = new BufferedReader(
              new InputStreamReader(System.in, charset));
      log.info("Input some text and press RETURN to POS tag it, or just RETURN to finish.");

      for (String userInput; (userInput = stdIn.readLine()) != null && ! userInput.matches("\\n?"); ) {
        try {
          Socket socket = new Socket(host, port);
          PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), charset), true);
          BufferedReader in = new BufferedReader(new InputStreamReader(
                  socket.getInputStream(), charset));
          PrintWriter stdOut = new PrintWriter(new OutputStreamWriter(System.out, charset), true);
          // send material to NER to socket
          out.println(userInput);
          // Print the results of NER

          stdOut.println(in.readLine());
          while (in.ready()) {
            stdOut.println(in.readLine());
          }
          in.close();
          socket.close();
        } catch (UnknownHostException e) {
          log.info("Cannot find host: ");
          log.info(host);
          return;
        } catch (IOException e) {
          log.info("I/O error in the connection to: ");
          log.info(host);
          return;
        }
      }
      stdIn.close();
    }
  } // end static class NERClient


  private static final String USAGE = "Usage: MaxentTaggerServer [-model file|-client] -port portNumber [other MaxentTagger options]";

  /**
   * Starts this server on the specified port.  The classifier used can be
   * either a default one stored in the jar file from which this code is
   * invoked or you can specify it as a filename or as another classifier
   * resource name, which must correspond to the name of a resource in the
   * /classifiers/ directory of the jar file.
   * <p>
   * Usage: <code>java edu.stanford.nlp.tagger.maxent.MaxentTaggerServer [-model file|-client] -port portNumber [other MaxentTagger options]</code>
   *
   * @param args Command-line arguments (described above)
   * @throws Exception If file or Java class problems with serialized classifier
   */
  @SuppressWarnings({"StringEqualsEmptyString"})
  public static void main (String[] args) throws Exception {
    if (args.length == 0) {
      log.info(USAGE);
      return;
    }
    // Use both Properties and TaggerConfig.  It's okay.
    Properties props = StringUtils.argsToProperties(args);
    String client = props.getProperty("client");

    String portStr = props.getProperty("port");
    if (portStr == null || portStr.equals("")) {
      log.info(USAGE);
      return;
    }
    int port = 0;
    try {
      port = Integer.parseInt(portStr);
    } catch (NumberFormatException e) {
      log.info("Non-numerical port");
      log.info(USAGE);
      System.exit(1);
    }

    if (client != null && ! client.equals("")) {
      // run a test client for illustration/testing
      String host = props.getProperty("host");
      String encoding = props.getProperty("encoding");
      if (encoding == null || "".equals(encoding)) {
        encoding = "utf-8";
      }
      TaggerClient.communicateWithMaxentTaggerServer(host, port, encoding);
    } else {
      TaggerConfig config = new TaggerConfig(args);
      MaxentTagger tagger = new MaxentTagger(config.getModel(), config); // initializes tagger
      MaxentTagger.TaggerWrapper wrapper = new MaxentTagger.TaggerWrapper(tagger);
      new MaxentTaggerServer(port, wrapper, config.getEncoding()).run();
    }
  }

}
