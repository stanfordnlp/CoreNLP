package edu.stanford.nlp.pipeline;

import java.util.Collections;
import java.util.Set;

import edu.stanford.nlp.util.ArraySet;

/**
 * This is an interface for adding annotations to a fully annotated
 * Annotation.  In some ways, it is just a glorified Function, except
 * that it explicitly operates on Annotation objects.  Annotators
 * should be given to an AnnotationPipeline in order to make
 * annotation pipelines (the whole motivation of this package), and
 * therefore implementers of this interface should be designed to play
 * well with other Annotators and in their javadocs they should
 * explicitly state what annotations they are assuming already exist
 * in the annotation (like parse, POS tag, etc), what field they are
 * expecting them under (Annotation.WORDS_KEY, Annotation.PARSE_KEY,
 * etc) and what annotations they will add (or modify) and the keys
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
  public void annotate(Annotation annotation) ;

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
   * object.  We do not want to use <code>name</code> to decide
   * equality because a subclass that uses more information, such as
   * the particular kind of tsurgeon used in a hypothetical
   * TsurgeonAnnotator, cannot use a stricter equals() than the
   * superclass.  It is hard to get stricter than ==.
   */
  public class Requirement {
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
  public Set<Requirement> requirementsSatisfied();

  /**
   * Returns the set of tasks which this annotator requires in order
   * to perform.  For example, the POS annotator will return
   * "tokenize", "ssplit".
   */
  public Set<Requirement> requires();

  /**
   * These are annotators which StanfordCoreNLP knows how to create.
   * Add new annotators and/or annotators from other groups here!
   */
  public static final String STANFORD_TOKENIZE = "tokenize";
  public static final String STANFORD_CLEAN_XML = "cleanxml";
  public static final String STANFORD_SSPLIT = "ssplit";
  public static final String STANFORD_POS = "pos";
  public static final String STANFORD_LEMMA = "lemma";
  public static final String STANFORD_NER = "ner";
  public static final String STANFORD_REGEXNER = "regexner";
  public static final String STANFORD_GENDER = "gender";
  public static final String STANFORD_TRUECASE = "truecase";
  public static final String STANFORD_PARSE = "parse";
  public static final String STANFORD_DETERMINISTIC_COREF = "dcoref";
  public static final String STANFORD_RELATION = "relation";
  public static final String STANFORD_SENTIMENT = "sentiment";


  public static final Requirement TOKENIZE_REQUIREMENT = new Requirement(STANFORD_TOKENIZE);
  public static final Requirement CLEAN_XML_REQUIREMENT = new Requirement(STANFORD_CLEAN_XML);
  public static final Requirement SSPLIT_REQUIREMENT = new Requirement(STANFORD_SSPLIT);
  public static final Requirement POS_REQUIREMENT = new Requirement(STANFORD_POS);
  public static final Requirement LEMMA_REQUIREMENT = new Requirement(STANFORD_LEMMA);
  public static final Requirement NER_REQUIREMENT = new Requirement(STANFORD_NER);
  public static final Requirement GENDER_REQUIREMENT = new Requirement(STANFORD_GENDER);
  public static final Requirement TRUECASE_REQUIREMENT = new Requirement(STANFORD_TRUECASE);
  public static final Requirement PARSE_REQUIREMENT = new Requirement(STANFORD_PARSE);
  public static final Requirement DETERMINISTIC_COREF_REQUIREMENT = new Requirement(STANFORD_DETERMINISTIC_COREF);
  public static final Requirement RELATION_EXTRACTOR_REQUIREMENT = new Requirement(STANFORD_RELATION);

  /**
   * These are annotators which StanfordCoreNLP does not know how to
   * create by itself, meaning you would need to use the custom
   * annotator mechanism to create them.  Note that some of them are
   * already included in other parts of the system, such as sutime,
   * which is already included in ner.
   */
  public static final Requirement GUTIME_REQUIREMENT = new Requirement("gutime");
  public static final Requirement SUTIME_REQUIREMENT = new Requirement("sutime");
  public static final Requirement HEIDELTIME_REQUIREMENT = new Requirement("heideltime");
  public static final Requirement STEM_REQUIREMENT = new Requirement("stem");
  public static final Requirement NUMBER_REQUIREMENT = new Requirement("number");
  public static final Requirement TIME_WORDS_REQUIREMENT = new Requirement("timewords");
  public static final Requirement QUANTIFIABLE_ENTITY_NORMALIZATION_REQUIREMENT = new Requirement("quantifiable_entity_normalization");

  /**
   * The Stanford Parser can produce this if it is specifically requested
   */
  public static final Requirement BINARIZED_TREES_REQUIREMENT = new Requirement("binarized_trees");

  /**
   * These are typical combinations of annotators which may be used as
   * requirements by other annotators.
   */
  public static final Set<Requirement> TOKENIZE_AND_SSPLIT = Collections.unmodifiableSet(new ArraySet<Requirement>(TOKENIZE_REQUIREMENT, SSPLIT_REQUIREMENT));
  public static final Set<Requirement> TOKENIZE_SSPLIT_POS = Collections.unmodifiableSet(new ArraySet<Requirement>(TOKENIZE_REQUIREMENT, SSPLIT_REQUIREMENT, POS_REQUIREMENT));
  public static final Set<Requirement> TOKENIZE_SSPLIT_NER = Collections.unmodifiableSet(new ArraySet<Requirement>(TOKENIZE_REQUIREMENT, SSPLIT_REQUIREMENT, NER_REQUIREMENT));
  public static final Set<Requirement> TOKENIZE_SSPLIT_PARSE = Collections.unmodifiableSet(new ArraySet<Requirement>(TOKENIZE_REQUIREMENT, SSPLIT_REQUIREMENT, PARSE_REQUIREMENT));
  public static final Set<Requirement> TOKENIZE_SSPLIT_PARSE_NER = Collections.unmodifiableSet(new ArraySet<Requirement>(TOKENIZE_REQUIREMENT, SSPLIT_REQUIREMENT, PARSE_REQUIREMENT, NER_REQUIREMENT));
  public static final Set<Requirement> TOKENIZE_SSPLIT_POS_LEMMA = Collections.unmodifiableSet(new ArraySet<Requirement>(TOKENIZE_REQUIREMENT, SSPLIT_REQUIREMENT, POS_REQUIREMENT, LEMMA_REQUIREMENT));
  public static final Set<Requirement> PARSE_AND_TAG = Collections.unmodifiableSet(new ArraySet<Requirement>(POS_REQUIREMENT, PARSE_REQUIREMENT));
  public static final Set<Requirement> PARSE_TAG_BINARIZED_TREES = Collections.unmodifiableSet(new ArraySet<Requirement>(POS_REQUIREMENT, PARSE_REQUIREMENT, BINARIZED_TREES_REQUIREMENT));
}
