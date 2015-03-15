package edu.stanford.nlp.parser.server;

import java.io.IOException;

import junit.framework.TestCase;

import edu.stanford.nlp.net.Ports;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.trees.Tree;



public class LexicalizedParserServerITest extends TestCase {
  private static volatile LexicalizedParser parser = null;

  static final String model = LexicalizedParser.DEFAULT_PARSER_LOC;

  static final String testString = "John Bauer works at Stanford.";
  static final String resultString = "(ROOT (S (NP (NNP John) (NNP Bauer)) (VP (VBZ works) (PP (IN at) (NP (NNP Stanford)))) (. .)))";

  public void setUp() 
    throws IOException
  {
    if (parser == null) {
      synchronized(LexicalizedParserServerITest.class) {
        if (parser == null) {
          parser = LexicalizedParser.loadModel(model);
        }
      }
    }
  }

  public Thread startLPServer(int port, boolean daemon) 
    throws IOException
  {
    final LexicalizedParserServer server = 
      new LexicalizedParserServer(port, model, parser);
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


  public void testGetATree()
    throws IOException
  {
    int port = Ports.findAvailable(2000, 10000);
    System.err.println("testGetATree: starting on port " + port);
    startLPServer(port, true);

    LexicalizedParserClient client = 
      new LexicalizedParserClient("localhost", port);
    Tree tree = client.getTree(testString);
    assertEquals(resultString, tree.toString().trim());
  }


  public void testGetText()
    throws IOException
  {
    int port = Ports.findAvailable(2000, 10000);
    System.err.println("testGetText: starting on port " + port);
    startLPServer(port, true);

    LexicalizedParserClient client = 
      new LexicalizedParserClient("localhost", port);
    String tree = client.getParse(testString);
    assertEquals(resultString, tree.trim());
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
}
