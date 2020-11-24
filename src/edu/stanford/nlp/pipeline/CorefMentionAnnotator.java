package edu.stanford.nlp.pipeline;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.HashMap;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.CorefProperties;
import edu.stanford.nlp.coref.CorefUtils;
import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.coref.md.CorefMentionFinder;
import edu.stanford.nlp.coref.md.DependencyCorefMentionFinder;
import edu.stanford.nlp.coref.md.HybridCorefMentionFinder;
import edu.stanford.nlp.coref.md.RuleBasedCorefMentionFinder;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * This class adds mention information to an Annotation.
 *
 * After annotation each sentence will have a {@code List<Mention>} representing the Mentions in the sentence.
 *
 * The {@code List<Mention>} containing the Mentions will be put under the annotation
 * {@link edu.stanford.nlp.coref.CorefCoreAnnotations.CorefMentionsAnnotation}.
 *
 * @author heeyoung
 * @author Jason Bolton
 */

public class CorefMentionAnnotator extends TextAnnotationCreator implements Annotator {

  /**
   * A logger for this class
   */
  private static final Redwood.RedwoodChannels log = Redwood.channels(CorefMentionAnnotator.class);

  private HeadFinder headFinder;
  private CorefMentionFinder md;
  private String mdName;
  private Dictionaries dictionaries;
  private Properties corefProperties;

  private final Set<Class<? extends CoreAnnotation>> mentionAnnotatorRequirements = new HashSet<>();

  public CorefMentionAnnotator(Properties props) {
    try {
      corefProperties = props;
      //System.out.println("corefProperties: "+corefProperties);
      dictionaries = new Dictionaries(props);
      //System.out.println("got dictionaries");
      headFinder = CorefProperties.getHeadFinder(props);
      //System.out.println("got head finder");
      md = getMentionFinder(props, headFinder);
      log.info("Using mention detector type: " + mdName);
      mentionAnnotatorRequirements.addAll(Arrays.asList(CoreAnnotations.TokensAnnotation.class,
                                                        CoreAnnotations.SentencesAnnotation.class,
                                                        CoreAnnotations.PartOfSpeechAnnotation.class,
                                                        CoreAnnotations.NamedEntityTagAnnotation.class,
                                                        CoreAnnotations.EntityTypeAnnotation.class,
                                                        CoreAnnotations.IndexAnnotation.class,
                                                        CoreAnnotations.TextAnnotation.class,
                                                        CoreAnnotations.ValueAnnotation.class,
                                                        SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class,
                                                        SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class));
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  /**
   * Return true if the coref mention synchs with the entity mention
   * For instance the em "Joe Smith" synchs with "Joe Smith", "Joe Smith's" and "President Joe Smith's"
   * It does not synch with "Joe Smith's car" or "President Joe"
   *
   * @param cm the coref mention
   * @param em the entity mention
   * @return true if the coref mention and entity mention synch
   */

  public static boolean synchCorefMentionEntityMention(Annotation ann, Mention cm, CoreMap em) {
    int currCMTokenIndex = 0;
    int tokenOverlapCount = 0;
    // get cm tokens
    CoreMap cmSentence = ann.get(CoreAnnotations.SentencesAnnotation.class).get(cm.sentNum);
    List<CoreLabel>
        cmTokens = cmSentence.get(CoreAnnotations.TokensAnnotation.class).subList(cm.startIndex, cm.endIndex);
    // if trying to synch with a PERSON entity mention, ignore leading TITLE tokens
    if (em.get(CoreAnnotations.EntityTypeAnnotation.class).equals("PERSON")) {
      while (currCMTokenIndex < cmTokens.size() &&
          cmTokens.get(currCMTokenIndex).get(CoreAnnotations.FineGrainedNamedEntityTagAnnotation.class) != null &&
          cmTokens.get(currCMTokenIndex).get(CoreAnnotations.FineGrainedNamedEntityTagAnnotation.class).equals("TITLE")) {
        currCMTokenIndex++;
      }
    }
    // get em tokens
    int currEMTokenIndex = 0;
    List<CoreLabel> emTokens = em.get(CoreAnnotations.TokensAnnotation.class);
    // search for token mismatch
    while (currEMTokenIndex < emTokens.size() && currCMTokenIndex < cmTokens.size()) {
      // if a token mismatch is found, return false
      if (!(emTokens.get(currEMTokenIndex) == cmTokens.get(currCMTokenIndex))) {
        return false;
      }
      currCMTokenIndex++;
      currEMTokenIndex++;
      tokenOverlapCount += 1;
    }
    // finally allow for a trailing "'s"
    if (currCMTokenIndex < cmTokens.size() && cmTokens.get(currCMTokenIndex).word().equals("'s")) {
      currCMTokenIndex++;
    }
    // check that both em and cm tokens have been exhausted, check for token overlap, or return false
    if (currCMTokenIndex < cmTokens.size() || currEMTokenIndex < emTokens.size() || tokenOverlapCount == 0) {
      return false;
    } else {
      return true;
    }
  }

  @Override
  public void annotate(Annotation annotation) {
    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
    // TO DO: be careful, this could introduce a really hard to find bug
    // this is necessary for Chinese coreference
    // removeNested needs to be set to "false" for newswire text or big performance drop
    String docID = annotation.get(CoreAnnotations.DocIDAnnotation.class);
    if (docID == null) {
      docID = "";
    }
    if (docID.contains("nw") && (CorefProperties.conll(corefProperties)
        || corefProperties.getProperty("coref.input.type", "raw").equals("conll")) &&
            CorefProperties.getLanguage(corefProperties) == Locale.CHINESE &&
            PropertiesUtils.getBool(corefProperties,"coref.specialCaseNewswire")) {
      corefProperties.setProperty("removeNestedMentions", "false");
    } else {
      corefProperties.setProperty("removeNestedMentions", "true");
    }
    List<List<Mention>> mentions = md.findMentions(annotation, dictionaries, corefProperties);
    if (CorefProperties.removeXmlMentions(corefProperties)) {
      mentions = CorefUtils.filterXmlTagsFromMentions(mentions);
    }
    // build list of coref mentions in this document
    annotation.set(CorefCoreAnnotations.CorefMentionsAnnotation.class , new ArrayList<Mention>());
    // initialize indexes
    int mentionIndex = 0;
    int currIndex = 0;
    // initialize each token with an empty set of corresponding coref mention id's
    for (CoreLabel token : annotation.get(CoreAnnotations.TokensAnnotation.class)) {
      token.set(CorefCoreAnnotations.CorefMentionIndexesAnnotation.class, new ArraySet<Integer>());
    }
    for (CoreMap sentence : sentences) {
      List<Mention> mentionsForThisSentence = mentions.get(currIndex);
      sentence.set(CorefCoreAnnotations.CorefMentionsAnnotation.class, mentionsForThisSentence);
      annotation.get(CorefCoreAnnotations.CorefMentionsAnnotation.class).addAll(mentionsForThisSentence);
      // set sentNum correctly for each coref mention
      for (Mention corefMention : mentionsForThisSentence) {
        corefMention.sentNum = currIndex;
      }
      // increment to next list of mentions
      currIndex++;
      // assign latest mentionID, annotate tokens with coref mention info
      for (Mention m : mentionsForThisSentence) {
        m.mentionID = mentionIndex;
        // go through all the tokens corresponding to this coref mention
        // annotate them with the index into the document wide coref mention list
        for (int corefMentionTokenIndex = m.startIndex ; corefMentionTokenIndex < m.endIndex ;
            corefMentionTokenIndex++) {
          CoreLabel currToken =
              sentence.get(CoreAnnotations.TokensAnnotation.class).get(corefMentionTokenIndex);
          currToken.get(CorefCoreAnnotations.CorefMentionIndexesAnnotation.class).add(mentionIndex);
        }
        mentionIndex++;
      }
    }

    // synch coref mentions to entity mentions
    HashMap<Integer,Integer> corefMentionToEntityMentionMapping = new HashMap<Integer,Integer>();
    HashMap<Integer,Integer> entityMentionToCorefMentionMapping = new HashMap<Integer,Integer>();
    for (CoreLabel token : annotation.get(CoreAnnotations.TokensAnnotation.class)) {
      if (token.get(CoreAnnotations.EntityMentionIndexAnnotation.class) != null) {
        int tokenEntityMentionIndex = token.get(CoreAnnotations.EntityMentionIndexAnnotation.class);
        CoreMap tokenEntityMention =
            annotation.get(CoreAnnotations.MentionsAnnotation.class).get(tokenEntityMentionIndex);
        for (Integer candidateCorefMentionIndex : token.get(CorefCoreAnnotations.CorefMentionIndexesAnnotation.class)) {
          Mention candidateTokenCorefMention =
              annotation.get(CorefCoreAnnotations.CorefMentionsAnnotation.class).get(candidateCorefMentionIndex);
          if (synchCorefMentionEntityMention(annotation, candidateTokenCorefMention, tokenEntityMention)) {
            entityMentionToCorefMentionMapping.put(tokenEntityMentionIndex, candidateCorefMentionIndex);
            corefMentionToEntityMentionMapping.put(candidateCorefMentionIndex, tokenEntityMentionIndex);
          }
        }
      }
    }

    // store mappings between entity mentions and coref mentions in annotation
    annotation.set(CoreAnnotations.CorefMentionToEntityMentionMappingAnnotation.class,
        corefMentionToEntityMentionMapping);
    annotation.set(CoreAnnotations.EntityMentionToCorefMentionMappingAnnotation.class,
        entityMentionToCorefMentionMapping);
  }

  private CorefMentionFinder getMentionFinder(Properties props, HeadFinder headFinder)
          throws ClassNotFoundException, IOException {

    switch (CorefProperties.mdType(props)) {
      case DEPENDENCY:
        mdName = "dependency";
        return new DependencyCorefMentionFinder(props);

      case HYBRID:
        mdName = "hybrid";
        mentionAnnotatorRequirements.add(TreeCoreAnnotations.TreeAnnotation.class);
        mentionAnnotatorRequirements.add(CoreAnnotations.BeginIndexAnnotation.class);
        mentionAnnotatorRequirements.add(CoreAnnotations.EndIndexAnnotation.class);
        return new HybridCorefMentionFinder(headFinder, props);

      case RULE:
      default:
        mentionAnnotatorRequirements.add(TreeCoreAnnotations.TreeAnnotation.class);
        mentionAnnotatorRequirements.add(CoreAnnotations.BeginIndexAnnotation.class);
        mentionAnnotatorRequirements.add(CoreAnnotations.EndIndexAnnotation.class);
        mdName = "rule";
        return new RuleBasedCorefMentionFinder(headFinder, props);
    }
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return mentionAnnotatorRequirements;
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
        CorefCoreAnnotations.CorefMentionsAnnotation.class,
        CoreAnnotations.ParagraphAnnotation.class,
        CoreAnnotations.SpeakerAnnotation.class,
        CoreAnnotations.UtteranceAnnotation.class
    )));
  }

}
