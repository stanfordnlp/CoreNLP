package edu.stanford.nlp.pipeline;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;

import edu.stanford.nlp.ie.crf.CRFBiasedClassifier;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.logging.Redwood;


public class TrueCaseAnnotator implements Annotator  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(TrueCaseAnnotator.class);

  private final CRFBiasedClassifier<CoreLabel> trueCaser;

  private final Map<String,String> mixedCaseMap;

  private final boolean overwriteText;

  private final boolean verbose;

  public static final String DEFAULT_MODEL_BIAS = "INIT_UPPER:-0.7,UPPER:-0.7,O:0";
  private static final String DEFAULT_OVERWRITE_TEXT = "false";
  private static final String DEFAULT_VERBOSE = "false";


  public TrueCaseAnnotator() {
    this(true);
  }

  public TrueCaseAnnotator(boolean verbose) {
    this(System.getProperty("truecase.model", DefaultPaths.DEFAULT_TRUECASE_MODEL),
        System.getProperty("truecase.bias", DEFAULT_MODEL_BIAS),
        System.getProperty("truecase.mixedcasefile", DefaultPaths.DEFAULT_TRUECASE_DISAMBIGUATION_LIST),
        Boolean.parseBoolean(System.getProperty("truecase.overwriteText", TrueCaseAnnotator.DEFAULT_OVERWRITE_TEXT)),
        verbose);
  }

  public TrueCaseAnnotator(Properties properties) {
    this(properties.getProperty("truecase.model", DefaultPaths.DEFAULT_TRUECASE_MODEL),
            properties.getProperty("truecase.bias", TrueCaseAnnotator.DEFAULT_MODEL_BIAS),
            properties.getProperty("truecase.mixedcasefile", DefaultPaths.DEFAULT_TRUECASE_DISAMBIGUATION_LIST),
            Boolean.parseBoolean(properties.getProperty("truecase.overwriteText", TrueCaseAnnotator.DEFAULT_OVERWRITE_TEXT)),
            Boolean.parseBoolean(properties.getProperty("truecase.verbose", TrueCaseAnnotator.DEFAULT_VERBOSE)));
  }

  public TrueCaseAnnotator(String modelLoc,
                           String classBias,
                           String mixedCaseFileName,
                           boolean overwriteText,
                           boolean verbose) {
    this.overwriteText = overwriteText;
    this.verbose = verbose;

    Properties props = PropertiesUtils.asProperties(
            "loadClassifier", modelLoc,
            "mixedCaseMapFile", mixedCaseFileName,
            "classBias", classBias);
    trueCaser = new CRFBiasedClassifier<>(props);

    if (modelLoc != null) {
      trueCaser.loadClassifierNoExceptions(modelLoc, props);
    } else {
      throw new RuntimeException("Model location not specified for true-case classifier!");
    }

    if (classBias != null) {
      StringTokenizer biases = new java.util.StringTokenizer(classBias,",");
      while (biases.hasMoreTokens()) {
        StringTokenizer bias = new java.util.StringTokenizer(biases.nextToken(),":");
        String cname = bias.nextToken();
        double w = Double.parseDouble(bias.nextToken());
        trueCaser.setBiasWeight(cname,w);
        if (this.verbose) log.info("Setting bias for class " + cname + " to " + w);
      }
    }

    // Load map containing mixed-case words:
    // TODO:
    // this file has some weirdness in it
    // for example, 4 different interpretations of el-:
    //   EL-Dafla, el-Ela, El-Ein, EL-ALY
    // it is also out of date in that it doesn't have iPhone, iMac
    // and on top of all that, it doesn't support hyphenated words any
    //   more because of the new tokenization
    mixedCaseMap = loadMixedCaseMap(mixedCaseFileName);
  }

  @Override
  public void annotate(Annotation annotation) {
    if (verbose) {
      log.info("Adding true-case annotation...");
    }

    if (annotation.containsKey(CoreAnnotations.SentencesAnnotation.class)) {
      // classify tokens for each sentence
      for (CoreMap sentence: annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
        List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);

        List<CoreLabel> output = this.trueCaser.classifySentence(tokens);

        for (int i = 0, size = tokens.size(); i < size; i++) {
          // add the truecaser tag to each token
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
        trueCaseText = Character.toTitleCase(text.charAt(0)) + text.substring(1).toLowerCase();
        break;
      case "O":
        // The model predicted mixed case, so lookup the map:
        String lower = text.toLowerCase();
        if (mixedCaseMap.containsKey(lower)) {
          trueCaseText = mixedCaseMap.get(lower);
        }
        // else leave it as it was?
        break;
    }
    // System.err.println(text + " was classified as " + trueCase + " and so became " + trueCaseText);

    l.set(CoreAnnotations.TrueCaseTextAnnotation.class, trueCaseText);

    if (overwriteText) {
      l.set(CoreAnnotations.TextAnnotation.class, trueCaseText);
      l.set(CoreAnnotations.ValueAnnotation.class, trueCaseText);
    }
  }

  private static Map<String,String> loadMixedCaseMap(String mapFile) {
    Map<String,String> map = Generics.newHashMap();
    try (BufferedReader br = IOUtils.readerFromString(mapFile)) {
      for (String line : ObjectBank.getLineIterator(br)) {
        line = line.trim();
        String[] els = line.split("\\s+");
        if (els.length != 2) {
          throw new RuntimeException("Wrong format: " + mapFile);
        }
        map.put(els[0], els[1]);
      }
    } catch (IOException e) {
      throw new RuntimeIOException(e);
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
