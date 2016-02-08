package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.ArraySet;

import java.util.*;

/**
 * This is an interface for adding annotations to a partially annotated
 * Annotation.  In some ways, it is just a glorified function, except
 * that it explicitly operates in-place on Annotation objects.  Annotators
 * should be given to an AnnotationPipeline in order to make
 * annotation pipelines (the whole motivation of this package), and
 * therefore implementers of this interface should be designed to play
 * well with other Annotators and in their javadocs they should
 * explicitly state what annotations they are assuming already exist
 * in the annotation (like parse, POS tag, etc), what keys they are
 * expecting them under (see, for instance, the ones in CoreAnnotations),
 * and what annotations they will add (or modify) and the keys
 * for them as well.  If you would like to look at the code for a
 * relatively simple Annotator, I recommend NERAnnotator.  For a lot
 * of code you could just add the implements directly, but I recommend
 * wrapping instead because I believe that it will help to keep the
 * pipeline code more manageable.
 * <br>
 * An Annotator can also provide a description of what it produces and
 * a description of what it requires to have been produced by using
 * the Requirement objects.  Predefined Requirement objects are
 * provided for most of the core annotators, such as tokenize, ssplit,
 * etc.  The StanfordCoreNLP version of the AnnotationPipeline can
 * enforce requirements, throwing an exception if an annotator does
 * not have all of its prerequisite met.  An Annotator which does not
 * participate in this system can simply return Collections.emptySet()
 * for both requires() and requirementsSatisfied().
 *
 * @author Jenny Finkel
 */
public interface Annotator {

  /**
   * Given an Annotation, perform a task on this Annotation.
   */
  void annotate(Annotation annotation);


  /**
   * Returns a set of requirements for which tasks this annotator can
   * provide.  For example, the POS annotator will return "pos".
   */
  Set<Class<? extends CoreAnnotation>> requirementsSatisfied();

  /**
   * Returns the set of tasks which this annotator requires in order
   * to perform.  For example, the POS annotator will return
   * "tokenize", "ssplit".
   */
  Set<Class<? extends CoreAnnotation>> requires();

  /**
   * These are annotators which StanfordCoreNLP knows how to create.
   * Add new annotators and/or annotators from other groups here!
   */
  String STANFORD_TOKENIZE = "tokenize";
  String STANFORD_CLEAN_XML = "cleanxml";
  String STANFORD_SSPLIT = "ssplit";
  String STANFORD_POS = "pos";
  String STANFORD_LEMMA = "lemma";
  String STANFORD_NER = "ner";
  String STANFORD_REGEXNER = "regexner";
  String STANFORD_ENTITY_MENTIONS = "entitymentions";
  String STANFORD_GENDER = "gender";
  String STANFORD_TRUECASE = "truecase";
  String STANFORD_PARSE = "parse";
  String STANFORD_DETERMINISTIC_COREF = "dcoref";
  String STANFORD_COREF = "coref";
  String STANFORD_MENTION = "mention";  // TODO(jebolton) Merge with entitymention
  String STANFORD_RELATION = "relation";
  String STANFORD_SENTIMENT = "sentiment";
  String STANFORD_COLUMN_DATA_CLASSIFIER = "cdc";
  String STANFORD_DEPENDENCIES = "depparse";
  String STANFORD_NATLOG = "natlog";
  String STANFORD_OPENIE = "openie";
  String STANFORD_QUOTE = "quote";
  String STANFORD_UD_FEATURES = "udfeats";


  /**
   * A mapping from an annotator to a its default transitive dependencies.
   * Note that this is not guaranteed to be accurate, as properties set in the annotator
   * can change the annotator's dependencies; but, it's a reasonable guess if you're using
   * things out-of-the-box.
   */
  @SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
  Map<String, Set<String>> DEFAULT_REQUIREMENTS = new HashMap<String, Set<String>>(){{
    put(STANFORD_TOKENIZE,                 new HashSet<>(Arrays.asList()));
    put(STANFORD_CLEAN_XML,                new HashSet<>(Arrays.asList(STANFORD_TOKENIZE)));
    put(STANFORD_SSPLIT,                   new HashSet<>(Arrays.asList(STANFORD_TOKENIZE)));
    put(STANFORD_POS,                      new HashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_SSPLIT)));
    put(STANFORD_LEMMA,                    new HashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_SSPLIT, STANFORD_POS)));
    put(STANFORD_LEMMA,                    new HashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_SSPLIT, STANFORD_POS)));
    put(STANFORD_NER,                      new HashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_SSPLIT, STANFORD_POS, STANFORD_LEMMA)));
    put(STANFORD_REGEXNER,                 new HashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_SSPLIT)));
    put(STANFORD_ENTITY_MENTIONS,          new HashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_SSPLIT, STANFORD_POS, STANFORD_LEMMA, STANFORD_NER)));
    put(STANFORD_GENDER,                   new HashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_SSPLIT)));
    put(STANFORD_TRUECASE,                 new HashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_SSPLIT, STANFORD_POS, STANFORD_LEMMA)));
    put(STANFORD_PARSE,                    new HashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_SSPLIT)));
    put(STANFORD_DETERMINISTIC_COREF,      new HashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_SSPLIT, STANFORD_POS, STANFORD_LEMMA, STANFORD_NER, STANFORD_MENTION, STANFORD_PARSE)));
    put(STANFORD_COREF,                    new HashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_SSPLIT, STANFORD_POS, STANFORD_LEMMA, STANFORD_NER, STANFORD_MENTION)));
    put(STANFORD_MENTION,                  new HashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_SSPLIT, STANFORD_POS, STANFORD_LEMMA, STANFORD_NER, STANFORD_DEPENDENCIES)));
    put(STANFORD_RELATION,                 new HashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_SSPLIT, STANFORD_POS, STANFORD_LEMMA, STANFORD_NER, STANFORD_PARSE, STANFORD_DEPENDENCIES)));
    put(STANFORD_SENTIMENT,                new HashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_SSPLIT, STANFORD_POS, STANFORD_PARSE)));
    put(STANFORD_COLUMN_DATA_CLASSIFIER,   new HashSet<>(Arrays.asList()));
    put(STANFORD_DEPENDENCIES,             new HashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_SSPLIT, STANFORD_POS)));
    put(STANFORD_NATLOG,                   new HashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_SSPLIT, STANFORD_POS, STANFORD_LEMMA, STANFORD_DEPENDENCIES)));
    put(STANFORD_OPENIE,                   new HashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_SSPLIT, STANFORD_POS, STANFORD_LEMMA, STANFORD_DEPENDENCIES, STANFORD_NATLOG, STANFORD_MENTION, STANFORD_COREF)));
    put(STANFORD_QUOTE,                    new HashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_SSPLIT)));
    put(STANFORD_UD_FEATURES,              new HashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_SSPLIT, STANFORD_POS, STANFORD_DEPENDENCIES)));
  }};


  /*
   * These are typical combinations of annotators which may be used as
   * requirements by other annotators.
   */
  Set<Class<? extends CoreAnnotation>> TOKENIZE_AND_SSPLIT = Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
      CoreAnnotations.TokensAnnotation.class,
      CoreAnnotations.SentencesAnnotation.class
  )));
  Set<Class<? extends CoreAnnotation>> TOKENIZE_SSPLIT_POS = Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
      CoreAnnotations.TokensAnnotation.class,
      CoreAnnotations.SentencesAnnotation.class,
      CoreAnnotations.PartOfSpeechAnnotation.class
  )));
  Set<Class<? extends CoreAnnotation>> TOKENIZE_SSPLIT_NER = Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
      CoreAnnotations.TokensAnnotation.class,
      CoreAnnotations.SentencesAnnotation.class,
      CoreAnnotations.NamedEntityTagAnnotation.class
  )));
  Set<Class<? extends CoreAnnotation>> TOKENIZE_SSPLIT_PARSE = Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
      CoreAnnotations.TokensAnnotation.class,
      CoreAnnotations.SentencesAnnotation.class,
      TreeCoreAnnotations.TreeAnnotation.class
  )));
  Set<Class<? extends CoreAnnotation>> TOKENIZE_SSPLIT_PARSE_NER = Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
      CoreAnnotations.TokensAnnotation.class,
      CoreAnnotations.SentencesAnnotation.class,
      TreeCoreAnnotations.TreeAnnotation.class,
      CoreAnnotations.NamedEntityTagAnnotation.class
  )));
  Set<Class<? extends CoreAnnotation>> TOKENIZE_SSPLIT_POS_LEMMA = Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
      CoreAnnotations.TokensAnnotation.class,
      CoreAnnotations.SentencesAnnotation.class,
      CoreAnnotations.PartOfSpeechAnnotation.class,
      CoreAnnotations.LemmaAnnotation.class
  )));
  Set<Class<? extends CoreAnnotation>> TOKENIZE_SSPLIT_POS_DEPPARSE = Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
      CoreAnnotations.TokensAnnotation.class,
      CoreAnnotations.SentencesAnnotation.class,
      CoreAnnotations.PartOfSpeechAnnotation.class,
      SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class,
      SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class,
      SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class
  )));



  /*
   * These are typically requirements satisfied by an annotator
   */
  Set<Class<? extends CoreAnnotation>> PARSE_AND_TAG = Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
      CoreAnnotations.PartOfSpeechAnnotation.class,
      TreeCoreAnnotations.TreeAnnotation.class
  )));
  Set<Class<? extends CoreAnnotation>> PARSE_TAG_BINARIZED_TREES = Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
      CoreAnnotations.PartOfSpeechAnnotation.class,
      TreeCoreAnnotations.TreeAnnotation.class,
      TreeCoreAnnotations.BinarizedTreeAnnotation.class
  )));
  Set<Class<? extends CoreAnnotation>> PARSE_TAG_DEPPARSE_BINARIZED_TREES = Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
      CoreAnnotations.PartOfSpeechAnnotation.class,
      TreeCoreAnnotations.TreeAnnotation.class,
      TreeCoreAnnotations.BinarizedTreeAnnotation.class,
      SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class,
      SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class,
      SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class
  )));
  Set<Class<? extends CoreAnnotation>> PARSE_TAG_DEPPARSE = Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
      CoreAnnotations.PartOfSpeechAnnotation.class,
      TreeCoreAnnotations.TreeAnnotation.class,
      SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class,
      SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class,
      SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class
  )));

}
