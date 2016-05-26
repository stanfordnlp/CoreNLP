package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.hcoref.CorefCoreAnnotations;
import edu.stanford.nlp.hcoref.data.CorefChain;
import edu.stanford.nlp.ie.KBPRelationExtractor;
import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.simple.Document;
import edu.stanford.nlp.util.AcronymMatcher;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * An annotator which takes as input sentences, and produces KBP relation annotations.
 *
 * @author Gabor Angeli
 */
public class KBPAnnotator implements Annotator {

  /**
   * The number of threads to run on.
   */
  public final int threads;

  /**
   * The extractor implementation.
   */
  public final KBPRelationExtractor extractor;

  /**
   * A serializer to convert to the Simple CoreNLP representation.
   */
  private final ProtobufAnnotationSerializer serializer = new ProtobufAnnotationSerializer(false);


  /**
   * A TokensRegexNER annotator for the special KBP NER types (case-sensitive).
   */
  private final TokensRegexNERAnnotator casedNER;
  /**
   * A TokensRegexNER annotator for the special KBP NER types (case insensitive).
   */
  private final TokensRegexNERAnnotator caselessNER;


  /**
   * Create a new KBP annotator from the given properties.
   *
   * @param props The properties to use when creating this extractor.
   */
  public KBPAnnotator(Properties props) {
    // Parse standard properties
    this.threads = Integer.parseInt(props.getProperty("threads", "1"));

    // Load the extractor
    try {
      Object object = IOUtils.readObjectFromURLOrClasspathOrFileSystem(
          props.getProperty("kbp.model", DefaultPaths.DEFAULT_KBP_CLASSIFIER));
      if (object instanceof LinearClassifier) {
        //noinspection unchecked
        this.extractor = new KBPRelationExtractor((Classifier<String, String>) object);
      } else if (object instanceof KBPRelationExtractor) {
        this.extractor = (KBPRelationExtractor) object;
      } else {
        throw new ClassCastException(object.getClass() + " cannot be cast into a " + KBPRelationExtractor.class);
      }
    } catch (IOException | ClassNotFoundException e) {
      throw new RuntimeIOException(e);
    }

    // Load TokensRegexNER
    this.casedNER = new TokensRegexNERAnnotator(
        props.getProperty("kbp.regexner.cased", DefaultPaths.DEFAULT_KBP_REGEXNER_CASED),
        true);
    this.caselessNER = new TokensRegexNERAnnotator(
        props.getProperty("kbp.regexner.caseless", DefaultPaths.DEFAULT_KBP_REGEXNER_CASELESS),
        false,
        "^(NN|JJ).*");
  }


  /**
   * Returns whether the given token counts as a valid pronominal mention for KBP.
   * @param word The token to classify.
   * @return True if this token is a pronoun that KBP should recognize.
   */
  private static boolean kbpIsPronominalMention(CoreLabel word) {
    String str = word.word().toLowerCase();
    return str.equals("he") || str.equals("him") || str.equals("his")
        || str.equals("she") || str.equals("her") || str.equals("hers");
  }


  /**
   * Annotate all the pronominal mentions in the document.
   * @param ann The document.
   * @return The list of pronominal mentions in the document.
   */
  private static List<CoreMap> annotatePronominalMentions(Annotation ann) {
    List<CoreMap> pronouns = new ArrayList<>();
    List<CoreMap> sentences = ann.get(CoreAnnotations.SentencesAnnotation.class);
    for (int sentenceIndex = 0; sentenceIndex < sentences.size(); sentenceIndex++) {
      CoreMap sentence = sentences.get(sentenceIndex);
      Integer annoTokenBegin = sentence.get(CoreAnnotations.TokenBeginAnnotation.class);
      if (annoTokenBegin == null) {
        annoTokenBegin = 0;
      }

      List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      for (int tokenIndex = 0; tokenIndex < tokens.size(); tokenIndex++) {
        CoreLabel token = tokens.get(tokenIndex);
        if (kbpIsPronominalMention(token)) {
          CoreMap pronoun = ChunkAnnotationUtils.getAnnotatedChunk(tokens, tokenIndex, tokenIndex + 1,
              annoTokenBegin, null, CoreAnnotations.TextAnnotation.class, null);
          pronoun.set(CoreAnnotations.SentenceIndexAnnotation.class, sentenceIndex);
          sentence.get(CoreAnnotations.MentionsAnnotation.class).add(pronoun);
          pronouns.add(pronoun);
        }
      }
    }

    return pronouns;
  }


  /**
   * Augment the coreferent mention map with acronym matches.
   */
  protected void acronymMatch(List<CoreMap> mentions, Map<CoreMap, Set<CoreMap>> mentionsMap) {
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
   * Annotate this document for KBP relations.
   * @param annotation The document to annotate.
   */
  public void annotate(Annotation annotation) {
    // Annotate with NER
    casedNER.annotate(annotation);
    caselessNER.annotate(annotation);

    // Create simple document
    Document doc = new Document(serializer.toProto(annotation));

    // Get the mentions in the document
    List<CoreMap> mentions = new ArrayList<>();
    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
    for (CoreMap sentence : sentences) {
      mentions.addAll(sentence.get(CoreAnnotations.MentionsAnnotation.class));
    }
    List<CoreMap> pronounMentions = annotatePronominalMentions(annotation);
    mentions.addAll(pronounMentions);

    // Compute coreferent clusters
    // (map an index to a KBP mention)
    Map<Pair<Integer, Integer>, CoreMap> mentionByStartIndex = new HashMap<>();
    for (CoreMap mention : mentions) {
      for (CoreLabel token : mention.get(CoreAnnotations.TokensAnnotation.class)) {
        mentionByStartIndex.put(Pair.makePair(token.sentIndex(), token.index()), mention);
      }
    }
    // (collect coreferent KBP mentions)
    Map<CoreMap, Set<CoreMap>> mentionsMap = new HashMap<>();
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

    // Create a canonical mention map
    Map<CoreMap, CoreMap> mentionToCanonicalMention = new HashMap<>();
    for (Map.Entry<CoreMap, Set<CoreMap>> entry : mentionsMap.entrySet()) {
      for (CoreMap mention : entry.getValue()) {
        // (set the NER tag to be axiomatically that of the canonical mention)
        mention.set(CoreAnnotations.NamedEntityTagAnnotation.class, entry.getKey().get(CoreAnnotations.NamedEntityTagAnnotation.class));
        // (add the mention (note: this must come after we set the NER!)
        mentionToCanonicalMention.put(mention, entry.getKey());
      }
    }
    // (add missing mentions)
    mentions.stream().filter(mention -> mentionToCanonicalMention.get(mention) == null)
        .forEach(mention -> mentionToCanonicalMention.put(mention, mention));

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
      List<RelationTriple> triples = new ArrayList<>();  // the annotations
      List<CoreMap> candidates = mentionsBySentence[sentenceI];
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
            CoreMap obj = candidates.get(objI);
            int objBegin = obj.get(CoreAnnotations.TokensAnnotation.class).get(0).index() - 1;
            int objEnd = obj.get(CoreAnnotations.TokensAnnotation.class).get(obj.get(CoreAnnotations.TokensAnnotation.class).size() - 1).index();
            Optional<KBPRelationExtractor.NERTag> objNER = KBPRelationExtractor.NERTag.fromString(obj.get(CoreAnnotations.NamedEntityTagAnnotation.class));

            if (objNER.isPresent() &&
                KBPRelationExtractor.RelationType.plausiblyHasRelation(subjNER.get(), objNER.get())) {  // type check
              KBPRelationExtractor.FeaturizerInput input = new KBPRelationExtractor.FeaturizerInput(
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
              if (!KBPRelationExtractor.NO_RELATION.equals(prediction.first)) {
                RelationTriple triple = new RelationTriple(
                    subj.get(CoreAnnotations.TokensAnnotation.class),
                    mentionToCanonicalMention.get(subj).get(CoreAnnotations.TokensAnnotation.class),
                    Collections.singletonList(new CoreLabel(new Word(prediction.first))),
                    obj.get(CoreAnnotations.TokensAnnotation.class),
                    mentionToCanonicalMention.get(obj).get(CoreAnnotations.TokensAnnotation.class),
                    prediction.second);
                triples.add(triple);
              }
            }
          }
        }
      }

      // Set triples
      annotation.get(CoreAnnotations.SentencesAnnotation.class).get(sentenceI).set(CoreAnnotations.KBPTriplesAnnotation.class, triples);
    }
  }

  /** {@inheritDoc} */
  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.singleton(CoreAnnotations.KBPTriplesAnnotation.class);
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
        CoreAnnotations.OriginalTextAnnotation.class,
        CoreAnnotations.MentionsAnnotation.class
    ));
    return Collections.unmodifiableSet(requirements);
  }
}
