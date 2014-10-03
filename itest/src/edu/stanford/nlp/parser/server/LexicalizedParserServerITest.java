package edu.stanford.nlp.parser.server;

import java.io.IOException;

import junit.framework.TestCase;

import edu.stanford.nlp.net.Ports;
import edu.stanford.nlp.parser.common.ParserGrammar;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser;
import edu.stanford.nlp.trees.Tree;


// TODO: tests should fail if a query causes the server to crash.  Right now it just hangs.
// Alternatively, the server should catch exceptions and do something productive with them
public class LexicalizedParserServerITest extends TestCase {
  private static LexicalizedParser lexparser = null;
  private static ShiftReduceParser srparser = null;

  static final String lexmodel = LexicalizedParser.DEFAULT_PARSER_LOC;

  static final String srmodel = "/u/nlp/data/srparser/englishSR.ser.gz";
  static final String tagger = "/u/nlp/data/pos-tagger/distrib/wsj-0-18-left3words-distsim.tagger";

  static final String testString = "John Bauer works at Stanford.";
  static final String resultString = "(ROOT (S (NP (NNP John) (NNP Bauer)) (VP (VBZ works) (PP (IN at) (NP (NNP Stanford)))) (. .)))";
  static final String binarizedResultString = "(ROOT (S (NP (NNP John) (NNP Bauer)) (@S (VP (VBZ works) (PP (IN at) (NP (NNP Stanford)))) (. .))))";
  static final String collapsedTreeString = ("nn(Bauer-2, John-1)\n" + 
                                             "nsubj(works-3, Bauer-2)\n" + 
                                             "root(ROOT-0, works-3)\n" +
                                             "prep_at(works-3, Stanford-5)\n" +
                                             "punct(works-3, .-6)");

  public void setUp() 
    throws IOException
  {
    if (lexparser == null) {
      synchronized(LexicalizedParserServerITest.class) {
        if (lexparser == null) {
          lexparser = LexicalizedParser.loadModel(lexmodel);
        }
        if (srparser == null) {
          srparser = ShiftReduceParser.loadModel(srmodel, "-preTag", "-taggerSerializedFile", tagger);
        }
      }
    }
  }

  public Thread startLPServer(int port, boolean daemon) 
    throws IOException
  {
    return startLPServer(port, daemon, lexparser);
  }

  public Thread startLPServer(int port, boolean daemon, ParserGrammar parser) 
    throws IOException
  {
    final LexicalizedParserServer server = 
      new LexicalizedParserServer(port, parser);
    Thread thread = new Thread() {
        public void run() {
          try {
            server.listen();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      };
    thread.setDaemon(daemon);
    thread.start();
    return thread;
  }



  public void testStartServer() 
    throws IOException
  {
    int port = Ports.findAvailable(2000, 10000);
    System.err.println("testStartServer: starting on port " + port);
    startLPServer(port, true);
  }


  public void testGetTree()
    throws IOException
  {
    int port = Ports.findAvailable(2000, 10000);
    System.err.println("testGetTree: starting on port " + port);
    startLPServer(port, true);

    LexicalizedParserClient client = 
      new LexicalizedParserClient("localhost", port);
    Tree tree = client.getTree(testString);
    assertEquals(resultString, tree.toString().trim());
  }


  public void testGetTextTree()
    throws IOException
  {
    int port = Ports.findAvailable(2000, 10000);
    System.err.println("testGetTextTree: starting on port " + port);
    startLPServer(port, true);

    LexicalizedParserClient client = 
      new LexicalizedParserClient("localhost", port);
    String tree = client.getParse(testString, false);
    assertEquals(resultString, tree.trim());
  }

  public void testGetBinarizedTextTree()
    throws IOException
  {
    int port = Ports.findAvailable(2000, 10000);
    System.err.println("testGetBinarizedTextTree: starting on port " + port);
    startLPServer(port, true);

    LexicalizedParserClient client = 
      new LexicalizedParserClient("localhost", port);
    String tree = client.getParse(testString, true);
    assertEquals(binarizedResultString, tree.trim());
  }

  public void testGetCollapsedTreeDependencies()
    throws IOException
  {
    int port = Ports.findAvailable(2000, 10000);
    System.err.println("testGetCollapsedTreeDependencies: starting on port " + port);
    startLPServer(port, true);

    LexicalizedParserClient client = 
      new LexicalizedParserClient("localhost", port);
    String result = client.getDependencies(testString, "collapsed_tree");
    assertEquals(collapsedTreeString, result.trim());
  }

  public void testQuit()
    throws IOException
  {
    int port = Ports.findAvailable(2000, 10000);
    System.err.println("testQuit: starting on port " + port);
    Thread serverThread = startLPServer(port, false);

    LexicalizedParserClient client = 
      new LexicalizedParserClient("localhost", port);
    client.sendQuit();
    try {
      serverThread.join(5000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    assertEquals(Thread.State.TERMINATED, serverThread.getState());
  }

  public void testGetShiftReduceText()
    throws IOException
  {
    int port = Ports.findAvailable(2000, 10000);
    System.err.println("testGetShiftReduceText: starting on port " + port);
    startLPServer(port, true, srparser);

    LexicalizedParserClient client = 
      new LexicalizedParserClient("localhost", port);
    String tree = client.getParse(testString, false);
    assertEquals(resultString, tree.trim());
  }

  
}
