package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.*;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.util.*;

import java.util.*;
import java.util.stream.*;


/**
 * An Annotator for splitting tokens into words based on a dictionary, rules, or a statistical model.
 */

public class MWTAnnotator implements Annotator {

  /**
   * Mapping from an original token to a list of words
   * <p>
   * Dictionary entries should be of form: token\tword1,word2
   * <p>
   * That is a tsv, with column 1 = token, column 2 = csv of words
   */

  private HashMap<String, List<String>> multiWordTokenMapping = new HashMap<>();
  private boolean useDictionary = false;

  /**
   * Preserve casing when generating multi word tokens (e.g. Des -> de les OR De les)
   * or not ?
   */
  private boolean preserveCasing;

  /**
   * A sub annotator for part-of-speech tagging to help with MWT decisions
   * At the moment the French MWT annotator uses a custom part-of-speech
   * tagger to make decisions on splitting the word "des"
   * <p>
   * The system makes split-decisions, and then refers to a corresponding
   * dictionary to decide the split
   * <p>
   * Dictionary entries should be of the form: token-tag\tword1,word2
   * <p>
   * That is a tsv, with column 1 = token-tag, column 2 = csv of words
   * <p>
   * Example entry: des-ADP_DET
   * <p>
   * The model is specified with mwt.pos.model = /path/to/model
   * The statistical dictionary is specified with mwt.statisticalMappingFile = /path/to/dictionary
   * <p>
   * In this example, the part-of-speech tagger will tag examples of the word "des"
   * with the tag "ADP_DET" instead of "DET".  Those examples will appear in the dictionary file,
   * and be split to "de" and "les".
   */

  private Annotator statisticalMWTAnnotator;
  private HashMap<String, List<String>> statisticalMultiWordTokenMapping = new HashMap<>();
  private boolean useStatisticalModel = false;


  public MWTAnnotator(String name, Properties props) {
    String prefix = (name != null && !name.equals("")) ? name + ".mwt." : "mwt.";
    // load the MWT dictionary entries if applicable
    if (!props.getProperty(prefix + "mappingFile", "").equals("")) {
      loadMultiWordTokenMappings(multiWordTokenMapping, props.getProperty(prefix + "mappingFile"));
      useDictionary = true;
    }
    // if a part-of-speech tagging model was provided, use statistical MWT as well
    if (!props.getProperty(prefix + "pos.model", "").equals("")) {
      useStatisticalModel = true;
      statisticalMWTAnnotator = new POSTaggerAnnotator("mwt.pos", props);
      // load dictionary entries for the statistical MWT
      loadMultiWordTokenMappings(statisticalMultiWordTokenMapping,
          props.getProperty(prefix + "statisticalMappingFile"));
    }
    // check whether or not to preserve casing, default is true
    preserveCasing = PropertiesUtils.getBool(props, prefix + "preserveCasing", true);
  }

  public void loadMultiWordTokenMappings(HashMap<String, List<String>> dictionary, String mapFilePath) {
    // read in entries from mapping file
    List<String> mapEntries = IOUtils.linesFromFile(mapFilePath);
    // load entries into the HashMap
    // map should be all lower case
    for (String mapEntry : mapEntries) {
      String originalWord = mapEntry.split("\t")[0].toLowerCase();
      List<String> mwtWords =
          Arrays.asList(
              mapEntry.split("\t")[1].split(",")).stream().map(
              w -> w.toLowerCase()).collect(Collectors.toList());
      dictionary.put(originalWord, mwtWords);
    }
  }

  /**
   * The annotation process runs in two steps.
   * <p>
   * 1.) Split all tokens that are in the multiWordTokenMapping dictionary
   * 2.) Run the part-of-speech model, split all words according to statisticalMappingFile
   */

  public void annotate(Annotation annotation) {
    List<CoreLabel> finalDocumentTokens = new ArrayList<CoreLabel>();
    // if using statistical model, run the mwt part-of-speech tagger
    if (useStatisticalModel) {
      statisticalMWTAnnotator.annotate(annotation);
    }
    // keep track of sentence number
    int sentNum = 0;
    for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
      // set token begin for sentence
      sentence.set(CoreAnnotations.TokenBeginAnnotation.class, finalDocumentTokens.size());
      List<CoreLabel> newSentenceTokens = new ArrayList<>();
      int sentenceIndex = 1;
      for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
        // list of potential multi word tokens
        List<String> tokenWords = new ArrayList<>();

        // check if statistical model detected a split
        if (useStatisticalModel) {
          String mwtTagKey = String.format("%s-%s", token.word().toLowerCase(), token.tag()).toLowerCase();
          if (statisticalMultiWordTokenMapping.containsKey(mwtTagKey))
            tokenWords = statisticalMultiWordTokenMapping.get(mwtTagKey).stream().collect(Collectors.toList());
        }
        // Note: it is not recommended to have the deterministic dictionary and the statistical model
        // both handle the same tokens, but if for some reason this is done, the deterministic dictionary
        // will take precedence...that is, if "des" is an entry for both the statistical model and the
        // dictionary, the split dictated by the dictionary will be used regardless of what the model says
        // in other words...deterministic

        // check if deterministic dictionary says to split
        if (useDictionary && multiWordTokenMapping.containsKey(token.word().toLowerCase())) {
          tokenWords =
              multiWordTokenMapping.get(token.word().toLowerCase()).stream().collect(Collectors.toList());
        }

        // process the words
        if (tokenWords.size() > 1) {
          // this is an MWT token
          // check if case needs to be corrected
          if (preserveCasing) {
            if (StringUtils.isAllUpperCase(token.word())) {
              // DES
              tokenWords = tokenWords.stream().map(t -> t.toUpperCase()).collect(Collectors.toList());
            } else if (StringUtils.isTitleCase(token.word())) {
              // Des
              tokenWords.set(0, StringUtils.toTitleCase(tokenWords.get(0)));
            }
          }
          boolean isFirst = true;
          for (String word : tokenWords) {
            CoreLabel newToken = new CoreLabel();
            newToken.setWord(word);
            newToken.setValue(word);
            newToken.setOriginalText(word);
            newToken.setIsNewline(false);
            if (token.keySet().contains(CoreAnnotations.ParentAnnotation.class)) {
              newToken.set(CoreAnnotations.ParentAnnotation.class,
                  token.get(CoreAnnotations.ParentAnnotation.class));
            }
            newToken.set(CoreAnnotations.TokenBeginAnnotation.class, finalDocumentTokens.size());
            newToken.set(CoreAnnotations.TokenEndAnnotation.class, finalDocumentTokens.size() + 1);
            // the char offsets, before, and after should match the original token
            newToken.setBeginPosition(token.beginPosition());
            newToken.setEndPosition(token.endPosition());
            newToken.setBefore(token.before());
            newToken.setAfter(token.after());
            newToken.set(CoreAnnotations.MWTTokenTextAnnotation.class, token.word());
            // set that this is a multi-word-token
            newToken.setIsMWT(true);
            // set that this is the first word derived from a multi-word-token
            // e.g. when "des" is split into "de" and "les", "de" would be true
            if (isFirst) {
              newToken.setIsMWTFirst(true);
              isFirst = false;
            } else {
              newToken.setIsMWTFirst(false);
            }
            newToken.setIndex(sentenceIndex);
            newToken.setSentIndex(sentNum);
            // add finalized token
            newSentenceTokens.add(newToken);
            finalDocumentTokens.add(newToken);
            sentenceIndex++;
          }
        } else {
          CoreLabel newToken = new CoreLabel(token);
          newToken.set(CoreAnnotations.TokenBeginAnnotation.class, finalDocumentTokens.size());
          newToken.set(CoreAnnotations.TokenEndAnnotation.class, finalDocumentTokens.size() + 1);
          newToken.setIndex(sentenceIndex);
          newToken.setIsMWT(false);
          newToken.setIsMWTFirst(false);
          // add finalized token
          newSentenceTokens.add(newToken);
          finalDocumentTokens.add(newToken);
          sentenceIndex++;
        }
      }
      // set end token index for sentence
      sentence.set(CoreAnnotations.TokenEndAnnotation.class, finalDocumentTokens.size());
      sentence.set(CoreAnnotations.TokensAnnotation.class, newSentenceTokens);
      sentNum++;
    }
    // set final tokens list for document
    annotation.set(CoreAnnotations.TokensAnnotation.class, finalDocumentTokens);
    // remove mwt part-of-speech tags
    if (useStatisticalModel) {
      for (CoreLabel token : annotation.get(CoreAnnotations.TokensAnnotation.class)) {
        token.remove(CoreAnnotations.PartOfSpeechAnnotation.class);
      }
    }
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
        CoreAnnotations.TokensAnnotation.class,
        CoreAnnotations.CharacterOffsetBeginAnnotation.class,
        CoreAnnotations.CharacterOffsetEndAnnotation.class,
        CoreAnnotations.SentencesAnnotation.class
    )));
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
        CoreAnnotations.MWTTokenTextAnnotation.class,
        CoreAnnotations.IsMultiWordTokenAnnotation.class
    )));
  }
}
