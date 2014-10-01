package edu.stanford.nlp.parser.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;

import edu.stanford.nlp.parser.common.ParserGrammar;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.TreeBinarizer;
import edu.stanford.nlp.trees.Tree;

/**
 * Serves requests to the given parser model on the given port.
 * See processRequest for a description of the query formats that are
 * handled.
 */
public class LexicalizedParserServer {
  final int port;
  final String model;

  final ServerSocket serverSocket;

  final ParserGrammar parser;
  final TreeBinarizer binarizer;

  //static final Charset utf8Charset = Charset.forName("utf-8");

  boolean stillRunning = true;

  public LexicalizedParserServer(int port, String model) 
    throws IOException
  {
    this(port, model, ParserGrammar.loadModel(model));
  }

  public LexicalizedParserServer(int port, String model, ParserGrammar parser)
    throws IOException
  {
    this.port = port;
    this.serverSocket = new ServerSocket(port);
    this.model = model;
    this.parser = parser;
    this.binarizer = TreeBinarizer.simpleTreeBinarizer(parser.getTLPParams().headFinder(), parser.treebankLanguagePack());
  }


  /**
   * Runs in a loop, getting requests from new clients until a client
   * tells us to exit.
   */
  public void listen() 
    throws IOException
  {
    while (stillRunning) {
      Socket clientSocket = null;
      try {
        clientSocket = serverSocket.accept();
        System.err.println("Got a connection");
        processRequest(clientSocket);
        System.err.println("Goodbye!");
        System.err.println();
      } catch (IOException e) {
        // accidental multiple closes don't seem to have any bad effect
        clientSocket.close();
        System.err.println(e);
        continue;
      }
    }
    serverSocket.close();
  }



  // TODO: handle multiple requests in one connection?  why not?
  /**
   * Possible commands are of the form: <br>
   * quit <br>
   * parse query: returns a String of the parsed query <br>
   * tree query: returns a serialized Tree of the parsed query <br>
   */
  public void processRequest(Socket clientSocket) 
    throws IOException
  {
    BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "utf-8"));
    String line = reader.readLine();

    System.err.println(line);
    if (line == null)
      return;
    line = line.trim();
    String[] pieces = line.split(" ", 2);
    String command = pieces[0];
    String arg = null;
    if (pieces.length > 1) {
      arg = pieces[1];
    }
    System.err.println("Got the command " + command);
    if (arg != null) {
      System.err.println(" ... with argument " + arg);
    }
    switch (command) {
    case "quit":
      handleQuit();
      break;
    case "parse":
      handleParse(arg, clientSocket.getOutputStream(), false);
      break;
    case "parse:binarized": 
      // TODO: if commands get more complex, can do more intelligent
      // parsing of commands
      handleParse(arg, clientSocket.getOutputStream(), true);
      break;
    case "tree":
      handleTree(arg, clientSocket.getOutputStream());
      break;
    }

    System.err.println("Handled request");

    clientSocket.close();
  }

  /**
   * Tells the server to exit.
   */
  public void handleQuit() {
    stillRunning = false;
  }

  /**
   * Returns the result of applying the parser to arg as a serialized tree.
   */
  public void handleTree(String arg, OutputStream outStream) 
    throws IOException
  {
    Tree tree = parse(arg, false);
    if (tree == null) {
      return;
    }
    System.err.println(tree);
    if (tree != null) {
      ObjectOutputStream oos = new ObjectOutputStream(outStream);
      oos.writeObject(tree);
      oos.flush();
    }    
  }

  /**
   * Returns the result of applying the parser to arg as a string.
   */
  public void handleParse(String arg, OutputStream outStream, boolean binarized) 
    throws IOException
  {
    Tree tree = parse(arg, binarized);
    if (tree == null) {
      return;
    }
    System.err.println(tree);
    if (tree != null) {
      OutputStreamWriter osw = new OutputStreamWriter(outStream, "utf-8");
      osw.write(tree.toString());
      osw.write("\n");
      osw.flush();
    }
  }

  private Tree parse(String arg, boolean binarized) {
    if (arg == null) {
      return null;
    }
    Tree tree = parser.parse(arg);
    if (binarized) {
      tree = binarizer.transformTree(tree);
    }
    return tree;
  }

  static final int DEFAULT_PORT = 4466;

  public static void main(String[] args) 
    throws IOException
  {
    System.setOut(new PrintStream(System.out, true, "utf-8"));
    System.setErr(new PrintStream(System.err, true, "utf-8"));

    int port = DEFAULT_PORT;
    String model = LexicalizedParser.DEFAULT_PARSER_LOC;

    for (int i = 0; i < args.length; i += 2) {
      if (i + 1 >= args.length) {
        System.err.println("Unspecified argument " + args[i]);
        System.exit(2);
      }
      String arg = args[i];
      if (arg.startsWith("--")) {
        arg = arg.substring(2);
      } else if (arg.startsWith("-")) {
        arg = arg.substring(1);
      }
      if (arg.equalsIgnoreCase("model")) {
        model = args[i + 1];
      } else if (arg.equalsIgnoreCase("port")) {
        port = Integer.valueOf(args[i + 1]);
      }
    }
    
    LexicalizedParserServer server = new LexicalizedParserServer(port, model);
    System.err.println("Server ready!");
    server.listen();
  }
  
}
