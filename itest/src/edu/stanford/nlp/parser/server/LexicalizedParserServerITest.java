package edu.stanford.nlp.parser.server;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import edu.stanford.nlp.util.Lazy;
import junit.framework.TestCase;

import edu.stanford.nlp.net.Ports;
import edu.stanford.nlp.parser.common.ParserGrammar;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.TestPaths;


// TODO: tests should fail if a query causes the server to crash.  Right now it just hangs.
// Alternatively, the server should catch exceptions and do something productive with them
public class LexicalizedParserServerITest {
  private static LexicalizedParser lexparser = null;
  private static Lazy<ShiftReduceParser> srparser = null;

  static final String lexmodel = LexicalizedParser.DEFAULT_PARSER_LOC;

  static final String srmodel = "edu/stanford/nlp/models/srparser/englishSR.ser.gz";
  static final String tagger = "edu/stanford/nlp/models/pos-tagger/english-left3words-distsim.tagger";

  static final String testString = "John Bauer works at Stanford.";
  static final String resultString = "(ROOT (S (NP (NNP John) (NNP Bauer)) (VP (VBZ works) (PP (IN at) (NP (NNP Stanford)))) (. .)))";
  static final String binarizedResultString = "(ROOT (S (@S (NP (NNP John) (NNP Bauer)) (VP (VBZ works) (PP (IN at) (NP (NNP Stanford))))) (. .)))";
  static final String collapsedTreeStanfordDependenciesString =
    ("nn(Bauer-2, John-1)\n" +
     "nsubj(works-3, Bauer-2)\n" +
     "root(ROOT-0, works-3)\n" +
     "prep_at(works-3, Stanford-5)");
  static final String collapsedTreeUniversalDependenciesString =
    ("compound(Bauer-2, John-1)\n" +
     "nsubj(works-3, Bauer-2)\n" +
     "root(ROOT-0, works-3)\n" +
     "case(Stanford-5, at-4)\n" +
     "obl:at(works-3, Stanford-5)");
  static final String tokenizedString = "John Bauer works at Stanford .";

  static final String lemmaTestString = "A man was walking in the rain.";
  static final String lemmaExpectedString = "a man be walk in the rain .";

  @Before
  public void setUp() 
    throws IOException
  {
    if (lexparser == null) {
      synchronized(LexicalizedParserServerITest.class) {
        if (lexparser == null) {
          lexparser = LexicalizedParser.loadModel(lexmodel);
        }
        if (srparser == null) {
          srparser = Lazy.of( () -> ShiftReduceParser.loadModel(srmodel, "-preTag", "-taggerSerializedFile", tagger));
        }
      }
    }
  }

  public Thread startLPServer(int port, boolean daemon)
    throws IOException
  {
    return startLPServer(port, daemon, lexparser);
  }

  public Thread startLPServer(int port, boolean daemon, boolean useStanfordDependencies)
    throws IOException
  {
    lexparser.getTLPParams().setGenerateOriginalDependencies(useStanfordDependencies);
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

  @Test
  public void testStartServer() 
    throws IOException
  {
    int port = Ports.findAvailable(2000, 10000);
    System.err.println("testStartServer: starting on port " + port);
    startLPServer(port, true);
  }


  @Test
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

  @Test
  public void testGetTokenizedTest()
    throws IOException
  {
    int port = Ports.findAvailable(2000, 10000);
    System.err.println("testGetTokenizedText: starting on port " + port);
    startLPServer(port, true);

    LexicalizedParserClient client = 
      new LexicalizedParserClient("localhost", port);
    String tokenized = client.getTokenizedText(testString);
    assertEquals(tokenizedString, tokenized);
  }

  @Test
  public void testGetLemmas()
    throws IOException
  {
    int port = Ports.findAvailable(2000, 10000);
    System.err.println("testGetLemmas: starting on port " + port);
    startLPServer(port, true);

    LexicalizedParserClient client = 
      new LexicalizedParserClient("localhost", port);
    String tokenized = client.getLemmas(lemmaTestString);
    assertEquals(lemmaExpectedString, tokenized);
  }    

  @Test
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

  @Test
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

  @Test
  public void testGetCollapsedTreeStanfordDependencies()
    throws IOException
  {
    int port = Ports.findAvailable(2000, 10000);
    System.err.println("testGetCollapsedTreeStanfordDependencies: starting on port " + port);
    startLPServer(port, true, true);

    LexicalizedParserClient client =
      new LexicalizedParserClient("localhost", port);
    String result = client.getDependencies(testString, "collapsed_tree");
    assertEquals(collapsedTreeStanfordDependenciesString, result.trim());
  }

  @Test
  public void testGetCollapsedTreeUniversalDependencies()
    throws IOException
  {
    int port = Ports.findAvailable(2000, 10000);
    System.err.println("testGetCollapsedTreeUniversalDependencies: starting on port " + port);
    startLPServer(port, true, false);

    LexicalizedParserClient client =
      new LexicalizedParserClient("localhost", port);
    String result = client.getDependencies(testString, "collapsed_tree");
    assertEquals(collapsedTreeUniversalDependenciesString, result.trim());
  }

  @Test
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

  @Test
  public void testGetShiftReduceText()
    throws IOException
  {
    int port = Ports.findAvailable(2000, 10000);
    System.err.println("testGetShiftReduceText: starting on port " + port);
    startLPServer(port, true, srparser.get());

    LexicalizedParserClient client = 
      new LexicalizedParserClient("localhost", port);
    String tree = client.getParse(testString, false);
    assertEquals(resultString, tree.trim());
  }

}
