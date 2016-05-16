package edu.stanford.nlp.pipeline; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

import edu.stanford.nlp.ie.crf.CRFBiasedClassifier;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;

public class TrueCaseAnnotator implements Annotator  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(TrueCaseAnnotator.class);

  @SuppressWarnings("unchecked")
  private final CRFBiasedClassifier trueCaser;

  private Map<String,String> mixedCaseMap = Generics.newHashMap();

  private boolean VERBOSE = true;

  public static final String DEFAULT_MODEL_BIAS = "INIT_UPPER:-0.7,UPPER:-0.7,O:0";

  public TrueCaseAnnotator() {
    this(true);
  }

  public TrueCaseAnnotator(boolean verbose) {
    this(System.getProperty("truecase.model", DefaultPaths.DEFAULT_TRUECASE_MODEL),
        System.getProperty("truecase.bias", DEFAULT_MODEL_BIAS),
        System.getProperty("truecase.mixedcasefile", DefaultPaths.DEFAULT_TRUECASE_DISAMBIGUATION_LIST),
        verbose);
  }

  @SuppressWarnings("unchecked")
  public TrueCaseAnnotator(String modelLoc,
      String classBias,
      String mixedCaseFileName,
      boolean verbose){
    this.VERBOSE = verbose;

    Properties props = new Properties();
    props.setProperty("loadClassifier", modelLoc);
    props.setProperty("mixedCaseMapFile", mixedCaseFileName);
    props.setProperty("classBias", classBias);
    trueCaser = new CRFBiasedClassifier(props);

    if (modelLoc != null) {
      trueCaser.loadClassifierNoExceptions(modelLoc, props);
    } else {
      throw new RuntimeException("Model location not specified for true-case classifier!");
    }

    if(classBias != null) {
      StringTokenizer biases = new java.util.StringTokenizer(classBias,",");
      while (biases.hasMoreTokens()) {
        StringTokenizer bias = new java.util.StringTokenizer(biases.nextToken(),":");
        String cname = bias.nextToken();
        double w = Double.parseDouble(bias.nextToken());
        trueCaser.setBiasWeight(cname,w);
        if(VERBOSE) log.info("Setting bias for class "+cname+" to "+w);
      }
    }

    // Load map containing mixed-case words:
    mixedCaseMap = loadMixedCaseMap(mixedCaseFileName);
  }

  @SuppressWarnings("unchecked")
  public void annotate(Annotation annotation) {
    if (VERBOSE) {
      log.info("Adding true-case annotation...");
    }

    if (annotation.containsKey(CoreAnnotations.SentencesAnnotation.class)) {
      // classify tokens for each sentence
      for (CoreMap sentence: annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
        List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
        List<CoreLabel> output = this.trueCaser.classifySentence(tokens);
        for (int i = 0; i < tokens.size(); ++i) {

          // add the named entity tag to each token
          String neTag = output.get(i).get(CoreAnnotations.AnswerAnnotation.class);
          tokens.get(i).set(CoreAnnotations.TrueCaseAnnotation.class, neTag);
          setTrueCaseText(tokens.get(i));
        }
      }
    } else {
      throw new RuntimeException("unable to find sentences in: " + annotation);
    }
  }

  private void setTrueCaseText(CoreLabel l) {
    String trueCase = l.getString(CoreAnnotations.TrueCaseAnnotation.class);
    String text = l.word();
    String trueCaseText = text;

    switch (trueCase) {
      case "UPPER":
        trueCaseText = text.toUpperCase();
        break;
      case "LOWER":
        trueCaseText = text.toLowerCase();
        break;
      case "INIT_UPPER":
        trueCaseText = text.substring(0, 1).toUpperCase() + text.substring(1);
        break;
      case "O":
        // The model predicted mixed case, so lookup the map:
        if (mixedCaseMap.containsKey(text))
          trueCaseText = mixedCaseMap.get(text);
        break;
    }

    l.set(CoreAnnotations.TrueCaseTextAnnotation.class, trueCaseText);
  }

  public static Map<String,String> loadMixedCaseMap(String mapFile) {
    Map<String,String> map = Generics.newHashMap();
    try {
      InputStream is = IOUtils.getInputStreamFromURLOrClasspathOrFileSystem(mapFile);
      BufferedReader br = new BufferedReader(new InputStreamReader(is));
      for(String line : ObjectBank.getLineIterator(br)) {
        line = line.trim();
        String[] els = line.split("\\s+");
        if(els.length != 2)
          throw new RuntimeException("Wrong format: "+mapFile);
        map.put(els[0],els[1]);
      }
      br.close();
      is.close();
    } catch(IOException e){
      throw new RuntimeException(e);
    }
    return map;
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
        CoreAnnotations.TextAnnotation.class,
        CoreAnnotations.TokensAnnotation.class,
        CoreAnnotations.PositionAnnotation.class,
        CoreAnnotations.SentencesAnnotation.class
    )));
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
        CoreAnnotations.TrueCaseTextAnnotation.class,
        CoreAnnotations.TrueCaseAnnotation.class,
        CoreAnnotations.AnswerAnnotation.class,
        CoreAnnotations.ShapeAnnotation.class
    )));
  }
}
