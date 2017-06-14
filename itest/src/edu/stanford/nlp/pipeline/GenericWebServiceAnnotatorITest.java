package edu.stanford.nlp.pipeline;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.logging.Redwood;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Properties;

import static java.net.HttpURLConnection.HTTP_OK;

/**
 * Test for web service annotator
 */
public class GenericWebServiceAnnotatorITest {
  // TODO: Abstract into a "AnnotatorBackend" class or something.
  public static class TestServer implements Runnable {
    protected static class PingHandler implements HttpHandler {
      @Override
      public void handle(HttpExchange httpExchange) throws IOException {
        Redwood.log("Received ping.");
        // Return a simple text message that says pong.
        httpExchange.getResponseHeaders().set("Content-type", "text/plain");
        String response = "pong\n";
        httpExchange.sendResponseHeaders(HTTP_OK, response.getBytes().length);
        httpExchange.getResponseBody().write(response.getBytes());
        httpExchange.close();
      }
    }

    protected static class AnnotateHandler implements HttpHandler {
      ProtobufAnnotationSerializer serializer;

      public AnnotateHandler() {
        serializer = new ProtobufAnnotationSerializer();
      }

      @Override
      public void handle(HttpExchange httpExchange) throws IOException {
        Redwood.log("Received annotation request.");
        Annotation ann;
        try(InputStream inputStream = httpExchange.getRequestBody()) {
          Pair<Annotation, InputStream> pair = serializer.read(inputStream);
          ann = pair.first;
        } catch (ClassNotFoundException e) {
          throw new RuntimeException(e);
        }
        Redwood.log("Read annotation.");

        ann.set(CoreAnnotations.DocIDAnnotation.class, "test");
        List<CoreMap> sentences = ann.get(CoreAnnotations.SentencesAnnotation.class);
        for (int i = 0; i < sentences.size(); i++) {
          sentences.get(i).set(CoreAnnotations.SentenceIndexAnnotation.class, i+42);
        }

        Redwood.log("Sending annotation.");

        byte[] response;
        try {
          ByteArrayOutputStream os = new ByteArrayOutputStream();
          serializer.toProto(ann).writeDelimitedTo(os);
          os.close();
          response = os.toByteArray();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }

        // Return a simple text message that says pong.
        httpExchange.getResponseHeaders().add("Content-Type", "application/x-protobuf");
        httpExchange.sendResponseHeaders(HTTP_OK, response.length);
        httpExchange.getResponseBody().write(response);
        httpExchange.close();
        Redwood.log("Sent response.");
      }
    }

    @Override
    public void run() {
      try {
        HttpServer server = HttpServer.create(new InetSocketAddress(8432), 0);
        server.createContext("/ping/", new PingHandler());
        server.createContext("/annotate/", new AnnotateHandler());
        server.start();

        Redwood.log("Server started.");
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  Thread serverThread;
  Thread coreNLPServerThread;

  @Before
  public void setUp() throws InterruptedException, IOException {
    serverThread = new Thread(new TestServer(), "annotatorServer");
    serverThread.start();

    coreNLPServerThread = new Thread(new StanfordCoreNLPServer(), "corenlpServer");
    coreNLPServerThread.start();
  }

  @After
  public void shutDown() throws InterruptedException {
    serverThread.interrupt();
    serverThread.join();
    coreNLPServerThread.interrupt();
    coreNLPServerThread.join();
  }


  @Test
  public void testServiceWithCoreNLP() {
    Properties props = new Properties();
    props.setProperty("generic.endpoint", "http://localhost:8432");
    props.setProperty("generic.requires", "TokensAnnotation, SentencesAnnotation, PartOfSpeechAnnotation");
    props.setProperty("generic.provides", "DocIDAnnotation, SentenceIndexAnnotation");

    props.setProperty("annotators", "tokenize, ssplit, pos, test");
    props.setProperty("customAnnotatorClass.test", "edu.stanford.nlp.pipeline.GenericWebServiceAnnotator");

    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    Annotation ann = new Annotation("This is a test sentence. I hope the generic annotator works.");
    pipeline.annotate(ann);

    assert ann.get(CoreAnnotations.DocIDAnnotation.class).equals("test");

    List<CoreMap> sentences = ann.get(CoreAnnotations.SentencesAnnotation.class);
    for (int i = 0; i < sentences.size(); i++) {
      assert sentences.get(i).get(CoreAnnotations.SentenceIndexAnnotation.class).equals(i+42);
    }
  }

  @Test
  public void testServiceWithCoreNLPServer() throws InterruptedException, MalformedURLException {
    Properties props = new Properties();
    props.setProperty("generic.endpoint", "http://localhost:8432");
    props.setProperty("generic.requires", "TokensAnnotation, SentencesAnnotation, PartOfSpeechAnnotation");
    props.setProperty("generic.provides", "DocIDAnnotation, SentenceIndexAnnotation");

    props.setProperty("annotators", "tokenize, ssplit, pos, test");
    props.setProperty("customAnnotatorClass.test", "edu.stanford.nlp.pipeline.GenericWebServiceAnnotator");

    StanfordCoreNLPClient pipeline = new StanfordCoreNLPClient(props, "http://localhost", 9000);
    assert pipeline.checkStatus(new URL("http://localhost:9000"));

    Annotation ann = new Annotation("This is a test sentence. I hope the generic annotator works.");
    pipeline.annotate(ann);

    assert ann.get(CoreAnnotations.DocIDAnnotation.class).equals("test");

    List<CoreMap> sentences = ann.get(CoreAnnotations.SentencesAnnotation.class);
    for (int i = 0; i < sentences.size(); i++) {
      assert sentences.get(i).get(CoreAnnotations.SentenceIndexAnnotation.class).equals(i+42);
    }

    pipeline.shutdown();
  }

}
