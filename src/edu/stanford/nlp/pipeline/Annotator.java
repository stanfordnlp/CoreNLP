package edu.stanford.nlp.pipeline;

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
   * The Requirement is a general way of describing the pre and post
   * conditions of an Annotator running.  Typical use is to have
   * constants for the different requirement types, such as the
   * TOKENIZE_REQUIREMENT below, and to reuse those constants instead
   * of creating new objects.  It is also possible to subclass
   * Requirement if an Annotator has a more general output.  For
   * example, one could imagine a TsurgeonAnnotator which has a wide
   * range of possible effects; this would probably subclass
   * Requirement to indicate which particular surgery it provided.
   * <br>
   * We do nothing to override the equals or hashCode methods.  This
   * means that two Requirements are equal iff they are the same
   * object.  We do not want to use {@code name} to decide
   * equality because a subclass that uses more information, such as
   * the particular kind of tsurgeon used in a hypothetical
   * TsurgeonAnnotator, cannot use a stricter equals() than the
   * superclass.  It is hard to get stricter than ==.
   */
  class Requirement {
    public final String name;
    public Requirement(String name) {
      this.name = name;
    }
    @Override
    public String toString() {
      return name;
    }
  }

  /**
   * Returns a set of requirements for which tasks this annotator can
   * provide.  For example, the POS annotator will return "pos".
   */
  Set<Requirement> requirementsSatisfied();

  /**
   * Returns the set of tasks which this annotator requires in order
   * to perform.  For example, the POS annotator will return
   * "tokenize", "ssplit".
   */
  Set<Requirement> requires();

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
  String STANFORD_COREF = "hcoref";
  String STANFORD_RELATION = "relation";
  String STANFORD_SENTIMENT = "sentiment";
  String STANFORD_COLUMN_DATA_CLASSIFIER = "cdc";
  String STANFORD_DEPENDENCIES = "depparse";
  String STANFORD_NATLOG = "natlog";
  String STANFORD_OPENIE = "openie";
  String STANFORD_QUOTE = "quote";
  String STANFORD_UD_FEATURES = "udfeats";

  Requirement TOKENIZE_REQUIREMENT = new Requirement(STANFORD_TOKENIZE);
  Requirement CLEAN_XML_REQUIREMENT = new Requirement(STANFORD_CLEAN_XML);
  Requirement SSPLIT_REQUIREMENT = new Requirement(STANFORD_SSPLIT);
  Requirement POS_REQUIREMENT = new Requirement(STANFORD_POS);
  Requirement LEMMA_REQUIREMENT = new Requirement(STANFORD_LEMMA);
  Requirement NER_REQUIREMENT = new Requirement(STANFORD_NER);
  Requirement GENDER_REQUIREMENT = new Requirement(STANFORD_GENDER);
  Requirement TRUECASE_REQUIREMENT = new Requirement(STANFORD_TRUECASE);
  Requirement PARSE_REQUIREMENT = new Requirement(STANFORD_PARSE);
  Requirement DEPENDENCY_REQUIREMENT = new Requirement(STANFORD_DEPENDENCIES);
  Requirement DETERMINISTIC_COREF_REQUIREMENT = new Requirement(STANFORD_DETERMINISTIC_COREF);
  Requirement COREF_REQUIREMENT = new Requirement(STANFORD_COREF);
  Requirement RELATION_EXTRACTOR_REQUIREMENT = new Requirement(STANFORD_RELATION);
  Requirement NATLOG_REQUIREMENT = new Requirement(STANFORD_NATLOG);
  Requirement OPENIE_REQUIREMENT = new Requirement(STANFORD_OPENIE);
  Requirement QUOTE_REQUIREMENT = new Requirement(STANFORD_QUOTE);
  Requirement UD_FEATURES_REQUIREMENT = new Requirement(STANFORD_UD_FEATURES);

  /**
   * A map from annotator name to a set of requirements for that annotator.
   * This is useful to have here for the purpose of static analysis on an
   * annotators list.
   */
  @SuppressWarnings("unchecked")
  Map<String, Set<Requirement>> REQUIREMENTS = Collections.unmodifiableMap(new HashMap<String, Set<Requirement>>() {
    {
      put(STANFORD_TOKENIZE, Collections.EMPTY_SET);
      put(STANFORD_CLEAN_XML, Collections.unmodifiableSet(new HashSet<Requirement>() {{
        add(TOKENIZE_REQUIREMENT);  // A requirement for STANFORD_CLEAN_XML
      }}));
      put(STANFORD_SSPLIT, Collections.unmodifiableSet(new HashSet<Requirement>() {{
        add(TOKENIZE_REQUIREMENT);
      }}));
      put(STANFORD_POS, Collections.unmodifiableSet(new HashSet<Requirement>() {{
        add(TOKENIZE_REQUIREMENT);
        add(SSPLIT_REQUIREMENT);
      }}));
      put(STANFORD_LEMMA, Collections.unmodifiableSet(new HashSet<Requirement>() {{
        add(TOKENIZE_REQUIREMENT);
        add(SSPLIT_REQUIREMENT);
        add(POS_REQUIREMENT);
      }}));
      put(STANFORD_NER, Collections.unmodifiableSet(new HashSet<Requirement>() {{
        add(TOKENIZE_REQUIREMENT);
        add(SSPLIT_REQUIREMENT);
        add(POS_REQUIREMENT);
        add(LEMMA_REQUIREMENT);
      }}));
      put(STANFORD_GENDER, Collections.unmodifiableSet(new HashSet<Requirement>() {{
        add(TOKENIZE_REQUIREMENT);
        add(SSPLIT_REQUIREMENT);
        add(POS_REQUIREMENT);
      }}));
      put(STANFORD_TRUECASE, Collections.unmodifiableSet(new HashSet<Requirement>() {{
        add(TOKENIZE_REQUIREMENT);
        add(SSPLIT_REQUIREMENT);
        add(POS_REQUIREMENT);
        add(LEMMA_REQUIREMENT);
      }}));
      put(STANFORD_PARSE, Collections.unmodifiableSet(new HashSet<Requirement>() {{
        add(TOKENIZE_REQUIREMENT);
        add(SSPLIT_REQUIREMENT);
      }}));
      put(STANFORD_DEPENDENCIES, Collections.unmodifiableSet(new HashSet<Requirement>() {{
        add(TOKENIZE_REQUIREMENT);
        add(SSPLIT_REQUIREMENT);
        add(POS_REQUIREMENT);
      }}));
      put(STANFORD_DETERMINISTIC_COREF, Collections.unmodifiableSet(new HashSet<Requirement>() {{
        add(TOKENIZE_REQUIREMENT);
        add(SSPLIT_REQUIREMENT);
        add(POS_REQUIREMENT);
        add(LEMMA_REQUIREMENT);
        add(NER_REQUIREMENT);
        add(PARSE_REQUIREMENT);
      }}));
      put(STANFORD_COREF, Collections.unmodifiableSet(new HashSet<Requirement>() {{
        add(TOKENIZE_REQUIREMENT);
        add(SSPLIT_REQUIREMENT);
        add(POS_REQUIREMENT);
        add(LEMMA_REQUIREMENT);
        add(NER_REQUIREMENT);
        add(DEPENDENCY_REQUIREMENT);
      }}));
      put(STANFORD_RELATION, Collections.unmodifiableSet(new HashSet<Requirement>() {{
        add(TOKENIZE_REQUIREMENT);
        add(SSPLIT_REQUIREMENT);
        add(POS_REQUIREMENT);
        add(LEMMA_REQUIREMENT);
        add(NER_REQUIREMENT);
        add(DEPENDENCY_REQUIREMENT);
      }}));
      put(STANFORD_NATLOG, Collections.unmodifiableSet(new HashSet<Requirement>() {{
        add(TOKENIZE_REQUIREMENT);
        add(SSPLIT_REQUIREMENT);
        add(POS_REQUIREMENT);
        add(LEMMA_REQUIREMENT);
        add(DEPENDENCY_REQUIREMENT);  // TODO(gabor) can also use 'parse' annotator, technically
      }}));
      put(STANFORD_OPENIE, Collections.unmodifiableSet(new HashSet<Requirement>() {{
        add(TOKENIZE_REQUIREMENT);
        add(SSPLIT_REQUIREMENT);
        add(POS_REQUIREMENT);
        add(DEPENDENCY_REQUIREMENT);  // TODO(gabor) can also use 'parse' annotator, technically
        add(NATLOG_REQUIREMENT);
      }}));
      put(STANFORD_QUOTE, Collections.unmodifiableSet(new HashSet<Requirement>() {{
        // No requirements
      }}));
      put(STANFORD_UD_FEATURES, Collections.unmodifiableSet(new HashSet<Requirement>(){{
        add(TOKENIZE_REQUIREMENT);
        add(SSPLIT_REQUIREMENT);
        add(POS_REQUIREMENT);
        add(DEPENDENCY_REQUIREMENT);
      }}));
  }});

  /**
   * These are annotators which StanfordCoreNLP does not know how to
   * create by itself, meaning you would need to use the custom
   * annotator mechanism to create them.  Note that some of them are
   * already included in other parts of the system, such as sutime,
   * which is already included in ner.
   */
  Requirement GUTIME_REQUIREMENT = new Requirement("gutime");
  Requirement SUTIME_REQUIREMENT = new Requirement("sutime");
  Requirement HEIDELTIME_REQUIREMENT = new Requirement("heideltime");
  Requirement STEM_REQUIREMENT = new Requirement("stem");
  Requirement NUMBER_REQUIREMENT = new Requirement("number");
  Requirement TIME_WORDS_REQUIREMENT = new Requirement("timewords");
  Requirement QUANTIFIABLE_ENTITY_NORMALIZATION_REQUIREMENT = new Requirement("quantifiable_entity_normalization");
  Requirement COLUMN_DATA_CLASSIFIER = new Requirement("column_data_classifer");

  /**
   * The Stanford Parser can produce this if it is specifically requested.
   */
  Requirement BINARIZED_TREES_REQUIREMENT = new Requirement("binarized_trees");

  /**
   * These are typical combinations of annotators which may be used as
   * requirements by other annotators.
   */
  Set<Requirement> TOKENIZE_AND_SSPLIT = Collections.unmodifiableSet(new ArraySet<>(TOKENIZE_REQUIREMENT, SSPLIT_REQUIREMENT));
  Set<Requirement> TOKENIZE_SSPLIT_POS = Collections.unmodifiableSet(new ArraySet<>(TOKENIZE_REQUIREMENT, SSPLIT_REQUIREMENT, POS_REQUIREMENT));
  Set<Requirement> TOKENIZE_SSPLIT_NER = Collections.unmodifiableSet(new ArraySet<>(TOKENIZE_REQUIREMENT, SSPLIT_REQUIREMENT, NER_REQUIREMENT));
  Set<Requirement> TOKENIZE_SSPLIT_PARSE = Collections.unmodifiableSet(new ArraySet<>(TOKENIZE_REQUIREMENT, SSPLIT_REQUIREMENT, PARSE_REQUIREMENT));
  Set<Requirement> TOKENIZE_SSPLIT_PARSE_NER = Collections.unmodifiableSet(new ArraySet<>(TOKENIZE_REQUIREMENT, SSPLIT_REQUIREMENT, PARSE_REQUIREMENT, NER_REQUIREMENT));
  Set<Requirement> TOKENIZE_SSPLIT_POS_LEMMA = Collections.unmodifiableSet(new ArraySet<>(TOKENIZE_REQUIREMENT, SSPLIT_REQUIREMENT, POS_REQUIREMENT, LEMMA_REQUIREMENT));
  Set<Requirement> TOKENIZE_SSPLIT_POS_DEPPARSE = Collections.unmodifiableSet(new ArraySet<>(TOKENIZE_REQUIREMENT, SSPLIT_REQUIREMENT, POS_REQUIREMENT, DEPENDENCY_REQUIREMENT));
  Set<Requirement> PARSE_AND_TAG = Collections.unmodifiableSet(new ArraySet<>(POS_REQUIREMENT, PARSE_REQUIREMENT));
  Set<Requirement> PARSE_TAG_BINARIZED_TREES = Collections.unmodifiableSet(new ArraySet<>(POS_REQUIREMENT, PARSE_REQUIREMENT, BINARIZED_TREES_REQUIREMENT));
  Set<Requirement> PARSE_TAG_DEPPARSE_BINARIZED_TREES = Collections.unmodifiableSet(new ArraySet<>(POS_REQUIREMENT, PARSE_REQUIREMENT, DEPENDENCY_REQUIREMENT, BINARIZED_TREES_REQUIREMENT));
  Set<Requirement> PARSE_TAG_DEPPARSE = Collections.unmodifiableSet(new ArraySet<>(POS_REQUIREMENT, PARSE_REQUIREMENT, DEPENDENCY_REQUIREMENT));

}
