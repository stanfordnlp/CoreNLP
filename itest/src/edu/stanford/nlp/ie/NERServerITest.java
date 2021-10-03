package edu.stanford.nlp.ie;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.net.Ports;
import edu.stanford.nlp.util.TestPaths;

/**
 * Tests several operations on the NERServer.
 * <br>
 * First, it tests that you can start a server without something
 * horrible happening.
 * <br>
 * Then it tests that you can send it a simple query and get the
 * correct result.  This result could change, breaking the test, but
 * it's a pretty simple query that should work.
 * <br>
 * Then it tests that if the server goes wrong somehow, the client
 * doesn't blow up or hang.
 * <br>
 * Finally, it tests that two clients accessing the server in multiple
 * threads get the same results, ie testing the server is thread safe.
 * <br>
 * @author John Bauer
 */
public class NERServerITest {
  private static CRFClassifier crf = null;

  private static final String englishCRFPath = "edu/stanford/nlp/models/ner/english.all.3class.distsim.crf.ser.gz";
  private static final String englishTestFile =
    String.format("%s/ner/column_data/conll.testa", TestPaths.testHome());
  private static String loadedQueryFile = null;

  private static final String CHARSET = "UTF-8";

  private static final String QUERY = "John Bauer was born in New Jersey";
  private static final String EXPECTED_ANSWER =
    "John/PERSON Bauer/PERSON was/O born/O in/O New/LOCATION Jersey/LOCATION";

  public Thread startNERServer(int port,
                               AbstractSequenceClassifier classifier,
                               String charset, boolean daemon)
    throws IOException
  {
    final NERServer server = new NERServer(port, classifier, charset);
    Thread thread = new Thread() {
        public void run() {
          server.run();
        }
      };
    thread.setDaemon(daemon);
    thread.start();
    return thread;
  }

  @Before
  public void setUp()
    throws IOException
  {
    if (crf == null) {
      synchronized(NERServerITest.class) {
        if (crf == null) {
          Properties props = new Properties();
          props.setProperty("outputFormat", "slashTags");
          crf = new CRFClassifier(props);
          crf.loadClassifierNoExceptions(englishCRFPath, props);
        }
      }
    }

    if (loadedQueryFile == null) {
      synchronized(NERServerITest.class) {
        if (loadedQueryFile == null) {
          BufferedReader br = IOUtils.readerFromString(englishTestFile);
          String line;
          StringBuilder query = new StringBuilder();
          StringBuilder allQueries = new StringBuilder();
          while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0) {
              if (query.length() > 0) {
                allQueries.append(query.toString());
                allQueries.append("\n");
                query = new StringBuilder();
              }
              continue;
            }
            String queryWord = line.split("\\s+")[0];
            if (query.length() > 0) {
              query.append(" ");
            }
            query.append(queryWord);
          }
          loadedQueryFile = allQueries.toString();
        }
      }
    }

  }

  @Test
  public void testStartServer()
    throws IOException
  {
    int port = Ports.findAvailable(2000, 10000);
    System.err.println("testStartServer: starting on port " + port);
    startNERServer(port, crf, CHARSET, true);
  }

  @Test
  public void testQueryServer()
    throws IOException
  {
    int port = Ports.findAvailable(2000, 10000);
    System.err.println("testQueryServer: starting on port " + port);
    startNERServer(port, crf, CHARSET, true);
    StringReader sin = new StringReader(QUERY);
    BufferedReader bin = new BufferedReader(sin);
    StringWriter sout = new StringWriter();
    BufferedWriter bout = new BufferedWriter(sout);
    NERServer.NERClient.communicateWithNERServer("localhost", port, CHARSET,
                                                 bin, bout, false);
    bout.flush();
    assertEquals(EXPECTED_ANSWER, sout.toString().trim());
  }

  /**
   * This test would hang forever for some various kinds of bugs in
   * the server/client read/write code
   */
  @Test
  public void testServerDoesntHang()
    throws IOException
  {
    int port = Ports.findAvailable(2000, 10000);
    System.err.println("testServerDoesntHang: starting on port " + port);
    startNERServer(port, null, CHARSET, true);

    StringReader sin = new StringReader(QUERY);
    BufferedReader bin = new BufferedReader(sin);
    StringWriter sout = new StringWriter();
    BufferedWriter bout = new BufferedWriter(sout);
    NERServer.NERClient.communicateWithNERServer("localhost", port, CHARSET,
                                                 bin, bout, false);
    bout.flush();
    assertEquals("", sout.toString().trim());
  }

  @Test
  public void testThreadedServer()
    throws IOException, InterruptedException
  {
    int port = Ports.findAvailable(2000, 10000);
    System.err.println("testThreadedServer: starting on port " + port);
    startNERServer(port, crf, CHARSET, true);

    StringReader sin = new StringReader(loadedQueryFile);
    BufferedReader bin = new BufferedReader(sin);
    StringWriter sout = new StringWriter();
    BufferedWriter bout = new BufferedWriter(sout);
    NERServer.NERClient.communicateWithNERServer("localhost", port, CHARSET,
                                                 bin, bout, false);
    bout.flush();
    String results = sout.toString();
    // this should happen regardless of the outputs
    System.out.println("Got first results, length " + results.length());
    assertTrue(results.length() >= loadedQueryFile.length());

    // rerun the results in case the "transductive learning" of the
    // NER causes the results to be different the second time through
    sin = new StringReader(loadedQueryFile);
    bin = new BufferedReader(sin);
    sout = new StringWriter();
    bout = new BufferedWriter(sout);
    NERServer.NERClient.communicateWithNERServer("localhost", port, CHARSET,
                                                 bin, bout, false);
    bout.flush();
    results = sout.toString();
    // this should happen regardless of the outputs
    System.out.println("Reran results, length " + results.length());
    assertTrue(results.length() >= loadedQueryFile.length());

    NERClientThread t1 = new NERClientThread("localhost", port, CHARSET,
                                             loadedQueryFile);
    NERClientThread t2 = new NERClientThread("localhost", port, CHARSET,
                                             loadedQueryFile);
    t1.start();
    t2.start();
    t1.join();
    t2.join();
    assertEquals(results, t1.results());
    System.out.println("Results from simul client 1 matched");
    assertEquals(results, t2.results());
    System.out.println("Results from simul client 2 matched");
  }

  private class NERClientThread extends Thread {
    final String host;
    final int port;
    final String charset;
    final String queryText;

    String results;

    public NERClientThread(String host, int port, String charset,
                           String queryText) {
      this.host = host;
      this.port = port;
      this.charset = charset;
      this.queryText = queryText;
    }

    public String results() { return results; }

    public void run() {
      try {
        StringReader sin = new StringReader(queryText);
        BufferedReader bin = new BufferedReader(sin);
        StringWriter sout = new StringWriter();
        BufferedWriter bout = new BufferedWriter(sout);
        NERServer.NERClient.communicateWithNERServer(host, port, charset,
                                                     bin, bout, false);
        bout.flush();
        results = sout.toString();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
