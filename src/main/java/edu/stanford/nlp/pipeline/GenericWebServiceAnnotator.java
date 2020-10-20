package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.util.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.*;

/**
 * A simple web service annotator that can be defined through properties:
 *
 * annotatorEndpoint: a URL endpoint for annotator the service
 * annotatorRequires: Requirements for the annotator
 * annotatorProvides: Annotations provided by the annotator
 * annotatorStartCommand: command line and arguments to start the service.
 * annotatorStopCommand: command line and arguments to stop the service.
 *
 * The annotator is expected to provide the following interface:
 * - ENDPOINT/ping/ : Checks if the service is still alive.
 * - ENDPOINT/annotate/ : Runs all the annotator.
 *
 * @author <a href="mailto:chaganty@cs.stanford.edu">Arun Chaganty</a>
 */
public class GenericWebServiceAnnotator extends WebServiceAnnotator {
  @ArgumentParser.Option(name="generic.endpoint")
  public String annotatorEndpoint = "https://localhost:8000/";

  @ArgumentParser.Option(name="generic.requires")
  public Set<Class<? extends CoreAnnotation>> annotatorRequires;

  @ArgumentParser.Option(name="generic.provides")
  public Set<Class<? extends CoreAnnotation>> annotatorProvides;

  @ArgumentParser.Option(name="generic.start")
  public Optional<String[]> startCommand;

  @ArgumentParser.Option(name="generic.stop")
  public Optional<String[]> stopCommand;

  protected ProtobufAnnotationSerializer serializer;

  private static Set<Class<? extends CoreAnnotation>> parseClasses(String classList) {
    Set<Class<? extends CoreAnnotation>> ret = new HashSet<>();
    for (String s : classList.split(",")) {
      s = s.trim();
      if (s.length() == 0) continue;
      // If s is not fully specified ASSUME edu.stanford.nlp.ling.CoreAnnotations.{s}
      if (!s.contains(".")) {
        s = "edu.stanford.nlp.ling.CoreAnnotations$" + s;
      }
      try {
        ret.add((Class<? extends CoreAnnotation>) Class.forName(s));
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }

    return ret;
  }

  public GenericWebServiceAnnotator(Properties props) {
    // annotator endpoint
    annotatorEndpoint = props.getProperty("generic.endpoint");
    annotatorRequires = parseClasses(props.getProperty("generic.requires", ""));
    annotatorProvides = parseClasses(props.getProperty("generic.provides", ""));
    startCommand = Optional.ofNullable(props.getProperty("generic.start")).map(CommandLineTokenizer::tokenize);
    stopCommand = Optional.ofNullable(props.getProperty("generic.stop")).map(CommandLineTokenizer::tokenize);

    serializer = new ProtobufAnnotationSerializer();
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return annotatorProvides;
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return annotatorRequires;
  }

  @Override
  protected Optional<String[]> startCommand() {
    return startCommand;
  }

  @Override
  protected Optional<String[]> stopCommand() {
    return stopCommand;
  }

  @Override
  protected boolean ready(boolean initialTest) {
    return this.ping(annotatorEndpoint + "/ping/");
  }

  private static <V> void copyValue(CoreMap source, CoreMap target, Class k) {
    Class<? extends TypesafeMap.Key<V>> k_ = (Class<? extends TypesafeMap.Key<V>>) k;
    target.set(k_, source.get(k_));
  }

  private static void copy(final Annotation source, final Annotation target) {
    source.keySet().forEach(k -> {
      copyValue(source, target, k);
    });
  }

  @Override
  protected void annotateImpl(Annotation ann) throws ShouldRetryException, PermanentlyFailedException {
    Annotation ann_; // New annotaiton
    try { // Executes the connection from conn
      HttpURLConnection conn;
      conn = (HttpURLConnection) new URL(annotatorEndpoint + "/annotate/").openConnection();
      conn.setRequestMethod("POST");
      conn.setDoOutput(true);
      conn.setRequestProperty("Content-Type", "application/octet-stream; charset=UTF-8");

      try(OutputStream outputStream = conn.getOutputStream()) {
        serializer.toProto(ann).writeDelimitedTo(outputStream);
        outputStream.flush();
      }
      conn.connect();

      try(InputStream inputStream = conn.getInputStream()) {
        Pair<Annotation, InputStream> pair = serializer.read(inputStream);
        ann_ = pair.first;
      } catch (ClassNotFoundException | IOException e) {
        throw new PermanentlyFailedException(e);
      }

    } catch (MalformedURLException e) {
      throw new PermanentlyFailedException(e);
    }
    catch (IOException e) {
      throw new ShouldRetryException();
    }

    // Copy over annotation.
    copy(ann_, ann);
  }

}
