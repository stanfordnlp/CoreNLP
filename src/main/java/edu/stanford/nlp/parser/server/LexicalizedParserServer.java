package edu.stanford.nlp.parser.server; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collection;
import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.parser.common.ParserGrammar;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.TreeBinarizer;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.Filters;

/**
 * Serves requests to the given parser model on the given port.
 * See processRequest for a description of the query formats that are
 * handled.
 */
public class LexicalizedParserServer  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(LexicalizedParserServer.class);
  final int port;

  final ServerSocket serverSocket;

  final ParserGrammar parser;
  final TreeBinarizer binarizer;

  //static final Charset utf8Charset = Charset.forName("utf-8");

  boolean stillRunning = true;

  public LexicalizedParserServer(int port, String parserModel) 
    throws IOException
  {
    this(port, loadModel(parserModel, null));
  }

  public LexicalizedParserServer(int port, String parserModel, String taggerModel) 
    throws IOException
  {
    this(port, loadModel(parserModel, taggerModel));
  }

  public LexicalizedParserServer(int port, ParserGrammar parser)
    throws IOException
  {
    this.port = port;
    this.serverSocket = new ServerSocket(port);
    this.parser = parser;
    this.binarizer = TreeBinarizer.simpleTreeBinarizer(parser.getTLPParams().headFinder(), parser.treebankLanguagePack());
  }


  private static ParserGrammar loadModel(String parserModel, String taggerModel) {
    ParserGrammar model;
    if (taggerModel == null) {
      model = ParserGrammar.loadModel(parserModel);
    } else {
      model = ParserGrammar.loadModel(parserModel, "-preTag", "-taggerSerializedFile", taggerModel);
      // preload tagger so the first query doesn't take forever
      model.loadTagger();
    }
    model.setOptionFlags(model.defaultCoreNLPFlags());
    return model;
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
        log.info("Got a connection");
        processRequest(clientSocket);
        log.info("Goodbye!");
        log.info();
      } catch (IOException e) {
        // accidental multiple closes don't seem to have any bad effect
        clientSocket.close();
        log.info(e);
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

    log.info(line);
    if (line == null)
      return;
    line = line.trim();
    String[] pieces = line.split(" ", 2);
    String[] commandPieces = pieces[0].split(":", 2);
    String command = commandPieces[0];
    String commandArgs = "";
    if (commandPieces.length > 1) {
      commandArgs = commandPieces[1];
    }
    String arg = null;
    if (pieces.length > 1) {
      arg = pieces[1];
    }
    log.info("Got the command " + command);
    if (arg != null) {
      log.info(" ... with argument " + arg);
    }
    switch (command) {
    case "quit":
      handleQuit();
      break;
    case "parse":
      handleParse(arg, clientSocket.getOutputStream(), commandArgs.equals("binarized"));
      break;
    case "dependencies":
      handleDependencies(arg, clientSocket.getOutputStream(), commandArgs);
      break;
    case "tree":
      handleTree(arg, clientSocket.getOutputStream());
      break;
    case "tokenize":
      handleTokenize(arg, clientSocket.getOutputStream());
      break;
    case "lemma":
      handleLemma(arg, clientSocket.getOutputStream());
      break;
    }

    log.info("Handled request");

    clientSocket.close();
  }

  /**
   * Tells the server to exit.
   */
  public void handleQuit() {
    stillRunning = false;
  }

  public void handleTokenize(String arg, OutputStream outStream) 
    throws IOException
  {
    if (arg == null) {
      return;
    }
    List<? extends HasWord> tokens = parser.tokenize(arg);
    OutputStreamWriter osw = new OutputStreamWriter(outStream, "utf-8");
    for (int i = 0; i < tokens.size(); ++i) {
      HasWord word = tokens.get(i);
      if (i > 0) {
        osw.write(" ");
      }
      osw.write(word.toString());
    }
    osw.write("\n");
    osw.flush();
  }

  public void handleLemma(String arg, OutputStream outStream) 
    throws IOException
  {
    if (arg == null) {
      return;
    }
    List<CoreLabel> tokens = parser.lemmatize(arg);
    OutputStreamWriter osw = new OutputStreamWriter(outStream, "utf-8");
    for (int i = 0; i < tokens.size(); ++i) {
      CoreLabel word = tokens.get(i);
      if (i > 0) {
        osw.write(" ");
      }
      osw.write(word.lemma());
    }
    osw.write("\n");
    osw.flush();
  }

  // TODO: when this method throws an exception (for whatever reason)
  // a waiting client might hang.  There should be some graceful
  // handling of that.
  public void handleDependencies(String arg, OutputStream outStream, String commandArgs) 
    throws IOException
  {
    Tree tree = parse(arg, false);
    if (tree == null) {
      return;
    }
    // TODO: this might throw an exception if the parser doesn't support dependencies.  Handle that cleaner?
    GrammaticalStructure gs = parser.getTLPParams().getGrammaticalStructure(tree, parser.treebankLanguagePack().punctuationWordRejectFilter(), parser.getTLPParams().typedDependencyHeadFinder());
    Collection<TypedDependency> deps = null;
    switch (commandArgs.toUpperCase()) {
    case "COLLAPSED_TREE":
      deps = gs.typedDependenciesCollapsedTree();
      break;
    default:
      throw new UnsupportedOperationException("Dependencies type not implemented: " + commandArgs);
    }
    OutputStreamWriter osw = new OutputStreamWriter(outStream, "utf-8");
    for (TypedDependency dep : deps) {
      osw.write(dep.toString());
      osw.write("\n");
    }
    osw.flush();
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
    log.info(tree);
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
    log.info(tree);
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

  private static void help() {
    log.info("-help:   display this message");
    log.info("-model:  load this parser (default englishPCFG.ser.gz)");
    log.info("-tagger: pretag with this tagger model");
    log.info("-port:   run on this port (default 4466)");
  }

  static final int DEFAULT_PORT = 4466;

  public static void main(String[] args) 
    throws IOException
  {
    System.setOut(new PrintStream(System.out, true, "utf-8"));
    System.setErr(new PrintStream(System.err, true, "utf-8"));

    int port = DEFAULT_PORT;
    String model = LexicalizedParser.DEFAULT_PARSER_LOC;
    String tagger = null;

    // TODO: rewrite this a bit to allow for passing flags to the parser
    for (int i = 0; i < args.length; i += 2) {
      if (i + 1 >= args.length) {
        log.info("Unspecified argument " + args[i]);
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
        port = Integer.parseInt(args[i + 1]);
      } else if (arg.equalsIgnoreCase("tagger")) {
        tagger = args[i + 1];
      } else if (arg.equalsIgnoreCase("help")) {
        help();
        System.exit(0);
      }
    }
    
    LexicalizedParserServer server = new LexicalizedParserServer(port, model, tagger);
    log.info("Server ready!");
    server.listen();
  }
  
}
