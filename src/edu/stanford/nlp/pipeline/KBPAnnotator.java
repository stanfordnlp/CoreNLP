package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.coref.data.WordLists;
import edu.stanford.nlp.ie.*;
import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.simple.Document;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import edu.stanford.nlp.coref.CorefCoreAnnotations;

import edu.stanford.nlp.coref.data.CorefChain;

/**
 * An annotator which takes as input sentences, and produces KBP relation annotations.
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("FieldCanBeLocal")
public class KBPAnnotator implements Annotator {

  private static final String NOT_PROVIDED = "none";

  private final Properties kbpProperties;

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(KBPAnnotator.class);

  //@ArgumentParser.Option(name="kbp.language", gloss="language for kbp")
  //private String language = "english";

  @ArgumentParser.Option(name="kbp.model", gloss="The path to the model, set to \"none\" for no model")
  private String model = DefaultPaths.DEFAULT_KBP_CLASSIFIER;

  @ArgumentParser.Option(name="kbp.semgrex", gloss="Semgrex patterns directory, set to \"none\" to not use semgrex")
  private String semgrexdir = DefaultPaths.DEFAULT_KBP_SEMGREX_DIR;

  @ArgumentParser.Option(name="kbp.tokensregex", gloss="Tokensregex patterns directory, set to \"none\" to not use tokensregex")
  private String tokensregexdir = DefaultPaths.DEFAULT_KBP_TOKENSREGEX_DIR;

  @ArgumentParser.Option(name="kbp.verbose", gloss="Print out KBP logging info")
  private boolean VERBOSE = false;

  private final LanguageInfo.HumanLanguage kbpLanguage;
  /**
   * The extractor implementation.
   */
  public final KBPRelationExtractor extractor;

  /**
   * A serializer to convert to the Simple CoreNLP representation.
   */
  private final ProtobufAnnotationSerializer serializer = new ProtobufAnnotationSerializer(false);

  /**
   * A basic rule-based system for Spanish coreference
   */
  private KBPBasicSpanishCorefSystem spanishCorefSystem;

  /*
   * A TokensRegexNER annotator for the special KBP NER types (case-sensitive).
   */
  //private final TokensRegexNERAnnotator casedNER;

  /*
   * A TokensRegexNER annotator for the special KBP NER types (case insensitive).
   */
  //private final TokensRegexNERAnnotator caselessNER;

  /** maximum length sentence to run on **/
  private final int maxLength;

  /** pattern matchers for processing coref mentions **/
  TokenSequencePattern titlePersonPattern =
      TokenSequencePattern.compile("[pos:JJ & ner:O]? [ner: TITLE]+ ([ner: PERSON]+)");

  /** Map for converting KBP relation names to latest names. **/
  private final HashMap<String,String> relationNameConversionMap;

  /**
   * Create a new KBP annotator from the given properties.
   *
   * @param props The properties to use when creating this extractor.
   */
  public KBPAnnotator(String name, Properties props) {
    // Parse standard properties
    ArgumentParser.fillOptions(this, name, props);
    //Locale kbpLanguage =
            //(language.toLowerCase().equals("zh") || language.toLowerCase().equals("chinese")) ?
                    //Locale.CHINESE : Locale.ENGLISH ;
    kbpProperties = props;
    try {
      ArrayList<KBPRelationExtractor> extractors = new ArrayList<>();
      // add tokensregex rules
      if (!tokensregexdir.equals(NOT_PROVIDED))
        extractors.add(new KBPTokensregexExtractor(tokensregexdir, VERBOSE));
      // add semgrex rules
      if (!semgrexdir.equals(NOT_PROVIDED))
        extractors.add(new KBPSemgrexExtractor(semgrexdir,VERBOSE));
      // attempt to add statistical model
      if (!model.equals(NOT_PROVIDED)) {
        log.info("Loading KBP classifier from: " + model);
        Object object = IOUtils.readObjectFromURLOrClasspathOrFileSystem(model);
        KBPRelationExtractor statisticalExtractor;
        if (object instanceof LinearClassifier) {
          //noinspection unchecked
          statisticalExtractor = new KBPStatisticalExtractor((Classifier<String, String>) object);
        } else if (object instanceof KBPStatisticalExtractor) {
          statisticalExtractor = (KBPStatisticalExtractor) object;
        } else {
          throw new ClassCastException(object.getClass() + " cannot be cast into a " + KBPStatisticalExtractor.class);
        }
        extractors.add(statisticalExtractor);
      }
      // build extractor
      this.extractor = new KBPEnsembleExtractor(extractors.toArray(new KBPRelationExtractor[0]));
      // set maximum length of sentence to operate on
      maxLength = Integer.parseInt(props.getProperty("kbp.maxlen", "-1"));
    } catch (IOException | ClassNotFoundException e) {
      throw new RuntimeIOException(e);
    }

    // set up map for converting between older and new KBP relation names
    relationNameConversionMap = new HashMap<>();
    relationNameConversionMap.put("org:dissolved", "org:date_dissolved");
    relationNameConversionMap.put("org:founded", "org:date_founded");
    relationNameConversionMap.put("org:number_of_employees/members", "org:number_of_employees_members");
    relationNameConversionMap.put("org:political/religious_affiliation", "org:political_religious_affiliation");
    relationNameConversionMap.put("org:top_members/employees", "org:top_members_employees");
    relationNameConversionMap.put("per:member_of", "per:employee_or_member_of");
    relationNameConversionMap.put("per:employee_of", "per:employee_or_member_of");
    relationNameConversionMap.put("per:stateorprovinces_of_residence", "per:statesorprovinces_of_residence");

    // set up KBP language
    kbpLanguage = LanguageInfo.getLanguageFromString(props.getProperty("kbp.language", "en"));

    // build the Spanish coref system if necessary
    if (LanguageInfo.HumanLanguage.SPANISH.equals(kbpLanguage))
      spanishCorefSystem = new KBPBasicSpanishCorefSystem();
  }


  /** @see KBPAnnotator#KBPAnnotator(String, Properties) */
  @SuppressWarnings("unused")
  public KBPAnnotator(Properties properties) {
    this(STANFORD_KBP, properties);

  }

  /**
   * Augment the coreferent mention map with acronym matches.
   */
  private static void acronymMatch(List<CoreMap> mentions, Map<CoreMap, Set<CoreMap>> mentionsMap) {
    int ticks = 0;

    // Get all the candidate antecedents
    Map<List<String>, CoreMap> textToMention = new HashMap<>();
    for (CoreMap mention : mentions) {
      String nerTag = mention.get(CoreAnnotations.NamedEntityTagAnnotation.class);
      if (nerTag != null && (nerTag.equals(KBPRelationExtractor.NERTag.ORGANIZATION.name)
          || nerTag.equals(KBPRelationExtractor.NERTag.LOCATION.name))) {
        List<String> tokens = mention.get(CoreAnnotations.TokensAnnotation.class).stream().map(CoreLabel::word).collect(Collectors.toList());
        if (tokens.size() > 1) {
          textToMention.put(tokens, mention);
        }
      }
    }

    // Look for candidate acronyms
    for (CoreMap acronym : mentions) {
      String nerTag = acronym.get(CoreAnnotations.NamedEntityTagAnnotation.class);
      if (nerTag != null && (nerTag.equals(KBPRelationExtractor.NERTag.ORGANIZATION.name)
          || nerTag.equals(KBPRelationExtractor.NERTag.LOCATION.name))) {
        String text = acronym.get(CoreAnnotations.TextAnnotation.class);
        if (!text.contains(" ")) {
          // Candidate acronym
          Set<CoreMap> acronymCluster = mentionsMap.get(acronym);
          if (acronymCluster == null) {
            acronymCluster = new LinkedHashSet<>();
            acronymCluster.add(acronym);
          }
          // Try to match it to an antecedent
          for (Map.Entry<List<String>, CoreMap> entry : textToMention.entrySet()) {
            // Time out if we take too long in this loop.
            ticks += 1;
            if (ticks > 1000) {
              return;
            }
            // Check if the pair is an acronym
            if (AcronymMatcher.isAcronym(text, entry.getKey())) {
              // Case: found a coreferent pair
              CoreMap coreferent = entry.getValue();
              Set<CoreMap> coreferentCluster = mentionsMap.get(coreferent);
              if (coreferentCluster == null) {
                coreferentCluster = new LinkedHashSet<>();
                coreferentCluster.add(coreferent);
              }
              // Create a new coreference cluster
              Set<CoreMap> newCluster = new LinkedHashSet<>();
              newCluster.addAll(acronymCluster);
              newCluster.addAll(coreferentCluster);
              // Set the new cluster
              for (CoreMap key : newCluster) {
                mentionsMap.put(key, newCluster);
              }
            }
          }
        }
      }
    }
  }

  /**
   * Helper method to find best kbp mention in a coref chain
   * This is defined as longest kbp mention or null if
   * the coref chain does not contain a kbp mention
   *
   * @param ann the annotation
   * @param corefChain CorefChain containing potential KBP mentions to search through
   * @param kbpMentions HashMap mapping character offsets to KBP mentions
   * @return a list of kbp mentions (or null) for each coref mention in this coref chain, and the index of "best"
   *         kbp mention, which in this case is the longest kbp mention
   *
   */

  public Pair<List<CoreMap>, CoreMap> corefChainToKBPMentions(CorefChain corefChain, Annotation ann,
                                             HashMap<Pair<Integer,Integer>, CoreMap> kbpMentions) {
    // map coref mentions into kbp mentions (possibly null if no corresponding kbp mention)
    List<CoreMap> annSentences = ann.get(CoreAnnotations.SentencesAnnotation.class);
    // create a list of kbp mentions in this coref chain, possibly all null
    //System.err.println("---");
    //System.err.println("KBP mentions for coref chain");
    List<CoreMap> kbpMentionsForCorefChain = corefChain.getMentionsInTextualOrder().stream().map((cm) -> {
      CoreMap cmSentence = annSentences.get(cm.sentNum - 1);
      List<CoreLabel> cmSentenceTokens = cmSentence.get(CoreAnnotations.TokensAnnotation.class);
      int cmCharBegin = cmSentenceTokens.get(cm.startIndex - 1).get(
          CoreAnnotations.CharacterOffsetBeginAnnotation.class);
      int cmCharEnd = cmSentenceTokens.get(cm.endIndex - 2).get(
          CoreAnnotations.CharacterOffsetEndAnnotation.class);
      CoreMap kbpMentionFound = kbpMentions.get(new Pair<>(cmCharBegin, cmCharEnd));
      // if a best KBP mention can't be found, handle special cases
      if (kbpMentionFound == null) {
        List<CoreLabel> corefMentionTokens =
            cmSentence.get(CoreAnnotations.TokensAnnotation.class).subList(cm.startIndex-1, cm.endIndex-1);
        // look for a PERSON kbp mention in TITLE+ (PERSON+)
        TokenSequenceMatcher titlePersonMatcher = titlePersonPattern.matcher(corefMentionTokens);
        if (titlePersonMatcher.find()) {
          List<CoreMap> overallMatch = titlePersonMatcher.groupNodes(0);
          List<CoreMap> personWithinMatch = titlePersonMatcher.groupNodes(1);
          if (overallMatch.size() == corefMentionTokens.size()) {
            int personBeginOffset = ((CoreLabel) personWithinMatch.get(0)).beginPosition();
            int personEndOffset = ((CoreLabel) personWithinMatch.get(personWithinMatch.size()-1)).endPosition();
            Pair<Integer,Integer> personOffsets = new Pair<>(personBeginOffset, personEndOffset);
            kbpMentionFound = kbpMentions.get(personOffsets);
          }
        }
      }
      //if (kbpMentionFound != null)
        //System.err.println(kbpMentionFound.get(CoreAnnotations.TextAnnotation.class));
      return kbpMentionFound;
    }).collect(Collectors.toList());
    // map kbp mentions to the lengths of their text
    List<Integer> kbpMentionLengths = kbpMentionsForCorefChain.stream().map(
        km -> (Integer.valueOf(km == null ? 0 : km.get(CoreAnnotations.TextAnnotation.class).length()))).collect(
        Collectors.toList());
    int bestIndex = kbpMentionLengths.indexOf(kbpMentionLengths.stream().reduce(0, (a, b) -> Math.max(a, b)));
    // return the first occurrence of the kbp mention with max length (possibly null)
    return new Pair<>(kbpMentionsForCorefChain, kbpMentionsForCorefChain.get(bestIndex));
  }

  /**
   * Convert between older naming convention and current for relation names.
   *
   * @param relationName the original relation name.
   * @return the converted relation name
   *
   */
  private String convertRelationNameToLatest(String relationName) {
    return relationNameConversionMap.getOrDefault(relationName, relationName);
  }

  /**
   * Returns whether the given token counts as a valid pronominal mention for KBP.
   * This method (at present) works for either Chinese or English.
   *
   * @param word The token to classify.
   * @return true if this token is a pronoun that KBP should recognize.
   */
  private static boolean kbpIsPronominalMention(CoreLabel word) {
    return WordLists.isKbpPronominalMention(word.word());
  }

  /**
   * Annotate this document for KBP relations.
   * @param annotation The document to annotate.
   */
  @Override
  public void annotate(Annotation annotation) {
    // get a list of sentences for this annotation
    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);

    // Create simple document
    Document doc = new Document(kbpProperties, serializer.toProto(annotation));

    // Get the mentions in the document
    List<CoreMap> mentions = new ArrayList<>();
    for (CoreMap sentence : sentences) {
      mentions.addAll(sentence.get(CoreAnnotations.MentionsAnnotation.class));
    }

    // Compute coreferent clusters
    // (map an index to a KBP mention)
    Map<Pair<Integer, Integer>, CoreMap> mentionByStartIndex = new HashMap<>();
    for (CoreMap mention : mentions) {
      for (CoreLabel token : mention.get(CoreAnnotations.TokensAnnotation.class)) {
        mentionByStartIndex.put(Pair.makePair(token.sentIndex(), token.index()), mention);
      }
    }

    // (collect coreferent KBP mentions)
    Map<CoreMap, Set<CoreMap>> mentionsMap = new HashMap<>();  // map from canonical mention -> other mentions
    if (annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class) != null) {
      for (Map.Entry<Integer, CorefChain> chain : annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class).entrySet()) {
        CoreMap firstMention = null;
        for (CorefChain.CorefMention mention : chain.getValue().getMentionsInTextualOrder()) {
          CoreMap kbpMention = null;
          for (int i = mention.startIndex; i < mention.endIndex; ++i) {
            if (mentionByStartIndex.containsKey(Pair.makePair(mention.sentNum - 1, i))) {
              kbpMention = mentionByStartIndex.get(Pair.makePair(mention.sentNum - 1, i));
              break;
            }
          }
          if (firstMention == null) {
            firstMention = kbpMention;
          }
          if (kbpMention != null) {
            if (!mentionsMap.containsKey(firstMention)) {
              mentionsMap.put(firstMention, new LinkedHashSet<>());
            }
            mentionsMap.get(firstMention).add(kbpMention);
          }
        }
      }
    }
    // (coreference acronyms)
    acronymMatch(mentions, mentionsMap);
    // (ensure valid NER tag for canonical mention)
    for (CoreMap key : new HashSet<>(mentionsMap.keySet())) {
      if (key.get(CoreAnnotations.NamedEntityTagAnnotation.class) == null) {
        CoreMap newKey = null;
        for (CoreMap candidate : mentionsMap.get(key)) {
          if (candidate.get(CoreAnnotations.NamedEntityTagAnnotation.class) != null) {
            newKey = candidate;
            break;
          }
        }
        if (newKey != null) {
          mentionsMap.put(newKey, mentionsMap.remove(key));
        } else {
          mentionsMap.remove(key);  // case: no mention in this chain has an NER tag.
        }
      }
    }

    // Propagate Entity Link
    for (Map.Entry<CoreMap, Set<CoreMap>> entry : mentionsMap.entrySet()) {
      String entityLink = entry.getKey().get(CoreAnnotations.WikipediaEntityAnnotation.class);
      if (entityLink != null) {
        for (CoreMap mention : entry.getValue()) {
          for (CoreLabel token : mention.get(CoreAnnotations.TokensAnnotation.class)) {
            token.set(CoreAnnotations.WikipediaEntityAnnotation.class, entityLink);
          }
        }
      }
    }

    // create a mapping of char offset pairs to KBPMention
    HashMap<Pair<Integer, Integer>, CoreMap> charOffsetToKBPMention = new HashMap<>();
    for (CoreMap mention : mentions) {
      int nerMentionCharBegin = mention.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
      int nerMentionCharEnd = mention.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
      charOffsetToKBPMention.put(new Pair<>(nerMentionCharBegin, nerMentionCharEnd), mention);
    }

    // Create a canonical mention map
    Map<CoreMap, CoreMap> mentionToCanonicalMention;
    if (kbpLanguage.equals(LanguageInfo.HumanLanguage.SPANISH)) {
      mentionToCanonicalMention = spanishCorefSystem.canonicalMentionMapFromEntityMentions(mentions);
      if (VERBOSE) {
        log.info("---");
        log.info("basic spanish coref results");
        for (CoreMap originalMention : mentionToCanonicalMention.keySet()) {
          if (!originalMention.equals(mentionToCanonicalMention.get(originalMention))) {
            log.info("mapped: "+originalMention+" to: "+
                mentionToCanonicalMention.get(originalMention));
          }
        }
      }
    } else {
      mentionToCanonicalMention = new HashMap<>();
    }
    // check if there is coref info
    Set<Map.Entry<Integer, CorefChain>> corefChains;
    if (annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class) != null &&
        !kbpLanguage.equals(LanguageInfo.HumanLanguage.SPANISH))
      corefChains = annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class).entrySet();
    else
      corefChains = new HashSet<>();
    for (Map.Entry<Integer, CorefChain> indexCorefChainPair : corefChains) {
      CorefChain corefChain = indexCorefChainPair.getValue();
      Pair<List<CoreMap>, CoreMap> corefChainKBPMentionsAndBestIndex = corefChainToKBPMentions(corefChain, annotation,
          charOffsetToKBPMention);
      List<CoreMap> corefChainKBPMentions = corefChainKBPMentionsAndBestIndex.first();
      CoreMap bestKBPMentionForChain = corefChainKBPMentionsAndBestIndex.second();
      if (bestKBPMentionForChain != null) {
        for (CoreMap kbpMention : corefChainKBPMentions) {
          if (kbpMention != null) {
            //System.err.println("---");
            // ad hoc filters ; assume acceptable unless a filter blocks it
            boolean acceptableLink = true;
            // block people matches without a token overlap, exempting pronominal to non-pronominal
            // good: Ashton --> Catherine Ashton
            // good: she --> Catherine Ashton
            // bad: Morsi --> Catherine Ashton
            String kbpMentionNERTag = kbpMention.get(CoreAnnotations.NamedEntityTagAnnotation.class);
            String bestKBPMentionForChainNERTag =
                bestKBPMentionForChain.get(CoreAnnotations.NamedEntityTagAnnotation.class);
            if (kbpMentionNERTag != null && bestKBPMentionForChainNERTag != null &&
                kbpMentionNERTag.equals("PERSON") && bestKBPMentionForChainNERTag.equals("PERSON")
                && !kbpIsPronominalMention(kbpMention.get(CoreAnnotations.TokensAnnotation.class).get(0))
                && !kbpIsPronominalMention(bestKBPMentionForChain.get(CoreAnnotations.TokensAnnotation.class).get(0))) {
              //System.err.println("testing PERSON to PERSON coref link");
              boolean tokenMatchFound = false;
              for (CoreLabel kbpToken : kbpMention.get(CoreAnnotations.TokensAnnotation.class)) {
                for (CoreLabel bestKBPToken : bestKBPMentionForChain.get(CoreAnnotations.TokensAnnotation.class)) {
                  if (kbpToken.word().toLowerCase().equals(bestKBPToken.word().toLowerCase())) {
                    tokenMatchFound = true;
                    break;
                  }
                }
                if (tokenMatchFound)
                  break;
              }
              if (!tokenMatchFound)
                acceptableLink = false;
            }
            // check the coref link passed the filters
            if (acceptableLink)
              mentionToCanonicalMention.put(kbpMention, bestKBPMentionForChain);
            //System.err.println("kbp mention: " + kbpMention.get(CoreAnnotations.TextAnnotation.class));
            //System.err.println("coref mention: " + bestKBPMentionForChain.get(CoreAnnotations.TextAnnotation.class));
          }
        }
      }
    }

    // (add missing mentions)
    mentions.stream().filter(mention -> mentionToCanonicalMention.get(mention) == null)
        .forEach(mention -> mentionToCanonicalMention.put(mention, mention));

    // handle acronym coreference
    HashMap<String,List<CoreMap>> acronymClusters = new HashMap<>();
    HashMap<String,List<CoreMap>> acronymInstances = new HashMap<>();
    for (CoreMap acronymMention : mentionToCanonicalMention.keySet()) {
      String acronymNERTag = acronymMention.get(CoreAnnotations.NamedEntityTagAnnotation.class);
      if ((acronymMention == mentionToCanonicalMention.get(acronymMention)) && acronymNERTag != null &&
          (acronymNERTag.equals(KBPRelationExtractor.NERTag.ORGANIZATION.name) ||
              acronymNERTag.equals(KBPRelationExtractor.NERTag.LOCATION.name))) {
        String acronymText = acronymMention.get(CoreAnnotations.TextAnnotation.class);
        // List<CoreMap> coreferentMentions = new ArrayList<>();
        // define acronyms as not containing spaces (e.g. ACLU)
        if (!acronymText.contains(" ")) {
          int numCoreferentsChecked = 0;
          for (CoreMap coreferentMention : mentions) {
            // only check first 1000
            if (numCoreferentsChecked > 1000)
              break;
            // don't check a mention against itself
            if (acronymMention == coreferentMention)
              continue;
            // don't check other mentions without " "
            String coreferentText = coreferentMention.get(CoreAnnotations.TextAnnotation.class);
            if (!coreferentText.contains(" "))
              continue;
            numCoreferentsChecked++;
            List<String> coreferentTokenStrings = coreferentMention.get(
                CoreAnnotations.TokensAnnotation.class).stream().map(CoreLabel::word).collect(
                Collectors.toList());
            // when an acronym match is found:
            // store every mention (that isn't ACLU) that matches with ACLU in acronymClusters
            // store every instance of "ACLU" in acronymInstances
            // afterwards find the best mention in acronymClusters, and match it to every mention in acronymInstances
            if (AcronymMatcher.isAcronym(acronymText, coreferentTokenStrings)) {
              if (!acronymClusters.containsKey(acronymText))
                acronymClusters.put(acronymText, new ArrayList<>());
              if (!acronymInstances.containsKey(acronymText))
                acronymInstances.put(acronymText, new ArrayList<>());
              acronymClusters.get(acronymText).add(coreferentMention);
              acronymInstances.get(acronymText).add(acronymMention);
            }
          }
        }
      }
    }
    // process each acronym (e.g. ACLU)
    for (String acronymText : acronymInstances.keySet()) {
      // find longest ORG or null
      CoreMap bestORG = null;
      for (CoreMap coreferentMention : acronymClusters.get(acronymText)) {
        if (!coreferentMention.get(CoreAnnotations.NamedEntityTagAnnotation.class).equals(
            KBPRelationExtractor.NERTag.ORGANIZATION.name))
          continue;
        if (bestORG == null)
          bestORG = coreferentMention;
        else if (coreferentMention.get(CoreAnnotations.TextAnnotation.class).length() >
            bestORG.get(CoreAnnotations.TextAnnotation.class).length())
          bestORG = coreferentMention;
      }
      // find longest LOC or null
      CoreMap bestLOC = null;
      for (CoreMap coreferentMention : acronymClusters.get(acronymText)) {
        if (!coreferentMention.get(CoreAnnotations.NamedEntityTagAnnotation.class).equals(
            KBPRelationExtractor.NERTag.LOCATION.name))
          continue;
        if (bestLOC == null)
          bestLOC = coreferentMention;
        else if (coreferentMention.get(CoreAnnotations.TextAnnotation.class).length() >
            bestLOC.get(CoreAnnotations.TextAnnotation.class).length())
          bestLOC = coreferentMention;
      }
      // link ACLU to "American Civil Liberties Union" ; make sure NER types match
      for (CoreMap acronymMention : acronymInstances.get(acronymText)) {
        String mentionType = acronymMention.get(CoreAnnotations.NamedEntityTagAnnotation.class);
        if (mentionType.equals(KBPRelationExtractor.NERTag.ORGANIZATION.name) && bestORG != null)
          mentionToCanonicalMention.put(acronymMention, bestORG);
        if (mentionType.equals(KBPRelationExtractor.NERTag.LOCATION.name) && bestLOC != null)
          mentionToCanonicalMention.put(acronymMention, bestLOC);
      }
    }

    // Cluster mentions by sentence
    @SuppressWarnings("unchecked") List<CoreMap>[] mentionsBySentence = new List[annotation.get(CoreAnnotations.SentencesAnnotation.class).size()];
    for (int i = 0; i < mentionsBySentence.length; ++i) {
      mentionsBySentence[i] = new ArrayList<>();
    }
    for (CoreMap mention : mentionToCanonicalMention.keySet()) {
      mentionsBySentence[mention.get(CoreAnnotations.SentenceIndexAnnotation.class)].add(mention);
    }

    // Classify
    for (int sentenceI = 0; sentenceI < mentionsBySentence.length; ++sentenceI) {
      HashMap<String, RelationTriple> relationStringsToTriples = new HashMap<>();
      List<RelationTriple> finalTriplesList = new ArrayList<>();  // the annotations
      List<CoreMap> candidates = mentionsBySentence[sentenceI];
      // determine sentence length
      int sentenceLength =
              annotation.get(CoreAnnotations.SentencesAnnotation.class)
                      .get(sentenceI).get(CoreAnnotations.TokensAnnotation.class).size();
      // check if sentence is too long, if it's too long don't run kbp
      if (maxLength != -1 && sentenceLength > maxLength) {
        // set the triples annotation to an empty list of RelationTriples
        annotation.get(
                CoreAnnotations.SentencesAnnotation.class).get(sentenceI).set(
                CoreAnnotations.KBPTriplesAnnotation.class, finalTriplesList);
        // continue to next sentence
        continue;
      }
      // sentence isn't too long, so continue processing this sentence
      for (int subjI = 0; subjI < candidates.size(); ++subjI) {
        CoreMap subj = candidates.get(subjI);
        int subjBegin = subj.get(CoreAnnotations.TokensAnnotation.class).get(0).index() - 1;
        int subjEnd = subj.get(CoreAnnotations.TokensAnnotation.class).get(subj.get(CoreAnnotations.TokensAnnotation.class).size() - 1).index();
        Optional<KBPRelationExtractor.NERTag> subjNER = KBPRelationExtractor.NERTag.fromString(subj.get(CoreAnnotations.NamedEntityTagAnnotation.class));
        if (subjNER.isPresent()) {
          for (int objI = 0; objI < candidates.size(); ++objI) {
            if (subjI == objI) {
              continue;
            }
            if (Thread.interrupted()) {
              throw new RuntimeInterruptedException();
            }
            CoreMap obj = candidates.get(objI);
            int objBegin = obj.get(CoreAnnotations.TokensAnnotation.class).get(0).index() - 1;
            int objEnd = obj.get(CoreAnnotations.TokensAnnotation.class).get(obj.get(CoreAnnotations.TokensAnnotation.class).size() - 1).index();
            Optional<KBPRelationExtractor.NERTag> objNER = KBPRelationExtractor.NERTag.fromString(obj.get(CoreAnnotations.NamedEntityTagAnnotation.class));

            if (objNER.isPresent() &&
                KBPRelationExtractor.RelationType.plausiblyHasRelation(subjNER.get(), objNER.get())) {  // type check
              KBPRelationExtractor.KBPInput input = new KBPRelationExtractor.KBPInput(
                  new Span(subjBegin, subjEnd),
                  new Span(objBegin, objEnd),
                  subjNER.get(),
                  objNER.get(),
                  doc.sentence(sentenceI)
              );

              //  -- BEGIN Classify
              Pair<String, Double> prediction = extractor.classify(input);
              //  -- END Classify

              // Handle the classifier output
              if (!KBPStatisticalExtractor.NO_RELATION.equals(prediction.first)) {
                RelationTriple triple = new RelationTriple.WithLink(
                    subj.get(CoreAnnotations.TokensAnnotation.class),
                    mentionToCanonicalMention.get(subj).get(CoreAnnotations.TokensAnnotation.class),
                    Collections.singletonList(
                        new CoreLabel(new Word(convertRelationNameToLatest(prediction.first)))),
                    obj.get(CoreAnnotations.TokensAnnotation.class),
                    mentionToCanonicalMention.get(obj).get(CoreAnnotations.TokensAnnotation.class),
                    prediction.second,
                    sentences.get(sentenceI).get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class),
                    subj.get(CoreAnnotations.WikipediaEntityAnnotation.class),
                    obj.get(CoreAnnotations.WikipediaEntityAnnotation.class)
                    );
                String tripleString =
                    triple.subjectGloss()+"\t"+triple.relationGloss()+"\t"+triple.objectGloss();
                // ad hoc checks for problems
                boolean acceptableTriple = true;
                if (triple.objectGloss().equals(triple.subjectGloss()) &&
                    triple.relationGloss().endsWith("alternate_names"))
                  acceptableTriple = false;
                // only add this triple if it has the highest confidence ; this process generates duplicates with
                // different confidence scores, so we want to filter out the lower confidence versions
                if (acceptableTriple && !relationStringsToTriples.containsKey(tripleString))
                  relationStringsToTriples.put(tripleString, triple);
                else if (acceptableTriple && triple.confidence > relationStringsToTriples.get(tripleString).confidence)
                  relationStringsToTriples.put(tripleString, triple);
              }
            }
          }
        }
      }
      finalTriplesList = new ArrayList<>(relationStringsToTriples.values());
      // Set triples
      annotation.get(CoreAnnotations.SentencesAnnotation.class).get(sentenceI).set(
          CoreAnnotations.KBPTriplesAnnotation.class, finalTriplesList);
    }
  }

  /** {@inheritDoc} */
  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    Set<Class<? extends CoreAnnotation>> requirements = new HashSet<>(Arrays.asList(
        CoreAnnotations.KBPTriplesAnnotation.class
    ));
    return Collections.unmodifiableSet(requirements);
  }

  /** {@inheritDoc} */
  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    Set<Class<? extends CoreAnnotation>> requirements = new HashSet<>(Arrays.asList(
        CoreAnnotations.TextAnnotation.class,
        CoreAnnotations.TokensAnnotation.class,
        CoreAnnotations.IndexAnnotation.class,
        CoreAnnotations.SentencesAnnotation.class,
        CoreAnnotations.SentenceIndexAnnotation.class,
        CoreAnnotations.PartOfSpeechAnnotation.class,
        CoreAnnotations.LemmaAnnotation.class,
        SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class,
        SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class,
        SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class,
        CoreAnnotations.MentionsAnnotation.class,
        CoreAnnotations.OriginalTextAnnotation.class
    ));
    return Collections.unmodifiableSet(requirements);
  }

  /**
   * A debugging method to try relation extraction from the console.
   * @throws IOException If any IO problem
   */
  public static void main(String[] args) throws IOException {
    Properties props = StringUtils.argsToProperties(args);
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,regexner,parse,mention,coref,kbp");
    props.setProperty("regexner.mapping", "ignorecase=true,validpospattern=(NN|JJ|ADD).*,edu/stanford/nlp/models/kbp/regexner_caseless.tab;edu/stanford/nlp/models/kbp/regexner_cased.tab");

    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    IOUtils.console("sentence> ", line -> {
      Annotation ann = new Annotation(line);
      pipeline.annotate(ann);
      for (CoreMap sentence : ann.get(CoreAnnotations.SentencesAnnotation.class)) {
        sentence.get(CoreAnnotations.KBPTriplesAnnotation.class).forEach(System.err::println);
      }
    });
  }

}
