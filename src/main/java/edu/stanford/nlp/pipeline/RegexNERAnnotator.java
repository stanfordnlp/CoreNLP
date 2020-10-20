package edu.stanford.nlp.pipeline; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.*;

import edu.stanford.nlp.ie.regexp.RegexNERSequenceClassifier;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;


/**
 * This class adds NER information to an annotation using the RegexNERSequenceClassifier.
 * It assumes that the Annotation has already been split into sentences, then tokenized
 * into Lists of CoreLabels. Adds NER information to each CoreLabel as a NamedEntityTagAnnotation.
 *
 * @author jtibs
 */
public class RegexNERAnnotator implements Annotator  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(RegexNERAnnotator.class);

  private final RegexNERSequenceClassifier classifier;
  private final boolean verbose;

  public static PropertiesUtils.Property[] SUPPORTED_PROPERTIES = new PropertiesUtils.Property[]{
          new PropertiesUtils.Property("mapping", DefaultPaths.DEFAULT_REGEXNER_RULES, "Mapping file to use."),
          new PropertiesUtils.Property("ignorecase", "true", "Whether to ignore case or not when matching patterns."),
          new PropertiesUtils.Property("validpospattern", "", "Regular expression pattern for matching POS tags."),
          new PropertiesUtils.Property("verbose", "false", ""),
  };

  public RegexNERAnnotator(String name, Properties properties) {
    String mapping = properties.getProperty(name + ".mapping", DefaultPaths.DEFAULT_REGEXNER_RULES);
    boolean ignoreCase = Boolean.parseBoolean(properties.getProperty(name + ".ignorecase", "true"));
    String validPosPattern = properties.getProperty(name + ".validpospattern", RegexNERSequenceClassifier.DEFAULT_VALID_POS);
    boolean overwriteMyLabels = true;
    boolean verbose = Boolean.parseBoolean(properties.getProperty(name + ".verbose", "false"));

    classifier = new RegexNERSequenceClassifier(mapping, ignoreCase, overwriteMyLabels, validPosPattern);
    this.verbose = verbose;
  }

  public RegexNERAnnotator(String mapping) {
    this(mapping, false);
  }

  public RegexNERAnnotator(String mapping, boolean ignoreCase) {
    this(mapping, ignoreCase, RegexNERSequenceClassifier.DEFAULT_VALID_POS);
  }

  public RegexNERAnnotator(String mapping, boolean ignoreCase, String validPosPattern) {
    this(mapping, ignoreCase, true, validPosPattern, false);
  }

  public RegexNERAnnotator(String mapping, boolean ignoreCase, boolean overwriteMyLabels, String validPosPattern, boolean verbose) {
    classifier = new RegexNERSequenceClassifier(mapping, ignoreCase, overwriteMyLabels, validPosPattern);
    this.verbose = verbose;
  }

  @Override
  public void annotate(Annotation annotation) {
    if (verbose) {
      log.info("Adding RegexNER annotations ... ");
    }

    if (! annotation.containsKey(CoreAnnotations.SentencesAnnotation.class))
      throw new RuntimeException("Unable to find sentences in " + annotation);

    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
    for (CoreMap sentence : sentences) {
      List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      classifier.classify(tokens);

      for (CoreLabel token : tokens) {
        if (token.get(CoreAnnotations.NamedEntityTagAnnotation.class) == null)
          token.set(CoreAnnotations.NamedEntityTagAnnotation.class, classifier.flags.backgroundSymbol);
      }

      for (int start = 0; start < tokens.size(); start++) {
        CoreLabel token = tokens.get(start);
        String answerType = token.get(CoreAnnotations.AnswerAnnotation.class);
        if (answerType == null) continue;
        String NERType = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);

        int answerEnd = findEndOfAnswerAnnotation(tokens, start);
        int NERStart = findStartOfNERAnnotation(tokens, start);
        int NEREnd = findEndOfNERAnnotation(tokens, start);

        // check that the spans are the same, specially handling the case of
        // tokens with background named entity tags ("other")
        if ((NERStart == start || NERType.equals(classifier.flags.backgroundSymbol)) &&
            (answerEnd == NEREnd || (NERType.equals(classifier.flags.backgroundSymbol) && NEREnd >= answerEnd))) {

          // annotate each token in the span
          for (int i = start; i < answerEnd; i ++)
            tokens.get(i).set(CoreAnnotations.NamedEntityTagAnnotation.class, answerType);
        }
        start = answerEnd - 1;
      }
    }

    if (verbose)
      log.info("done.");
  }

  private static int findEndOfAnswerAnnotation(List<CoreLabel> tokens, int start) {
    String type = tokens.get(start).get(CoreAnnotations.AnswerAnnotation.class);
    while (start < tokens.size() && type.equals(tokens.get(start).get(CoreAnnotations.AnswerAnnotation.class)))
      start++;
    return start;
  }

  private static int findStartOfNERAnnotation(List<CoreLabel> tokens, int start) {
    String type = tokens.get(start).get(CoreAnnotations.NamedEntityTagAnnotation.class);
    while (start >= 0 && type.equals(tokens.get(start).get(CoreAnnotations.NamedEntityTagAnnotation.class)))
      start--;
    return start + 1;
  }

  private static int findEndOfNERAnnotation(List<CoreLabel> tokens, int start) {
    String type = tokens.get(start).get(CoreAnnotations.NamedEntityTagAnnotation.class);
    while (start < tokens.size() && type.equals(tokens.get(start).get(CoreAnnotations.NamedEntityTagAnnotation.class)))
      start++;
    return start;
  }


  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
        CoreAnnotations.TextAnnotation.class,
        CoreAnnotations.TokensAnnotation.class,
        CoreAnnotations.CharacterOffsetBeginAnnotation.class,
        CoreAnnotations.CharacterOffsetEndAnnotation.class,
        CoreAnnotations.SentencesAnnotation.class,
        CoreAnnotations.PartOfSpeechAnnotation.class
    )));
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    // TODO: we might want to allow for different RegexNER annotators
    // to satisfy different requirements
    return Collections.emptySet();
  }
}
