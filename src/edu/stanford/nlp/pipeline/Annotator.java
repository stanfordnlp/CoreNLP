package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.CoreAnnotation;

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
 *
 * An Annotator should also provide a description of what it produces and
 * a description of what it requires to have been produced by using Sets
 * of requirements.
 * The StanfordCoreNLP version of the AnnotationPipeline can
 * enforce requirements, throwing an exception if an annotator does
 * not have all of its prerequisites met.  An Annotator which does not
 * participate in this system can simply return Collections.emptySet()
 * for both requires() and requirementsSatisfied().
 *
 * <h2>Properties</h2>
 *
 * We extensively use Properties objects to configure each Annotator.
 * In particular, CoreNLP has most of its properties in an informal
 * namespace with properties names like "parse.maxlen" to specify that
 * a property only applies to a parser annotator. There can also be
 * global properties; they should not have any periods in their names.
 * Each Annotator knows its own name; we assume these don't collide badly,
 * though possibly two parsers could share the "parse.*" namespace.
 * An Annotator should have a constructor that simply takes a Properties
 * object. At this point, the Annotator should expect to be getting
 * properties in namespaces. The classes that annotators call (like
 * a concrete parser, tagger, or whatever) mainly expect properties
 * not in namespaces. In general the annotator should subset the
 * passed in properties to keep only global properties and ones in
 * its own namespace, and then strip the namespace prefix from the
 * latter properties.
 *
 * @author Jenny Finkel
 */
public interface Annotator {

  /**
   * Given an Annotation, perform a task on this Annotation.
   */
  void annotate(Annotation annotation);

  /**
   * A block of code called when this annotator unmounts from the
   * {@link AnnotatorPool}.
   * By default, nothing is done.
   */
  default void unmount() { }


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

  default Collection<String> exactRequirements() {
    return null;
  }

  /**
   * These are annotators which StanfordCoreNLP knows how to create.
   * Add new annotators and/or annotators from other groups here!
   */
  String STANFORD_TOKENIZE = "tokenize";
  String STANFORD_CDC_TOKENIZE = "cdc_tokenize";
  String STANFORD_CLEAN_XML = "cleanxml";
  String STANFORD_SSPLIT = "ssplit";
  String STANFORD_MWT = "mwt";
  String STANFORD_DOCDATE = "docdate";
  String STANFORD_POS = "pos";
  String STANFORD_LEMMA = "lemma";
  String STANFORD_NER = "ner";
  String STANFORD_REGEXNER = "regexner";
  String STANFORD_TOKENSREGEX = "tokensregex";
  String STANFORD_ENTITY_MENTIONS = "entitymentions";
  String STANFORD_GENDER = "gender";
  String STANFORD_TRUECASE = "truecase";
  String STANFORD_PARSE = "parse";
  String STANFORD_DETERMINISTIC_COREF = "dcoref";
  String STANFORD_COREF = "coref";
  String STANFORD_COREF_MENTION = "coref.mention";  // TODO(jebolton) Merge with entitymention
  String STANFORD_RELATION = "relation";
  String STANFORD_SENTIMENT = "sentiment";
  String STANFORD_COLUMN_DATA_CLASSIFIER = "cdc";
  String STANFORD_DEPENDENCIES = "depparse";
  String STANFORD_NATLOG = "natlog";
  String STANFORD_OPENIE = "openie";
  String STANFORD_QUOTE = "quote";
  String STANFORD_QUOTE_ATTRIBUTION = "quote.attribution";
  String STANFORD_UD_FEATURES = "udfeats";
  String STANFORD_LINK = "entitylink";
  String STANFORD_KBP = "kbp";


  /**
   * A mapping from an annotator to a its default transitive dependencies.
   * Note that this is not guaranteed to be accurate, as properties set in the annotator
   * can change the annotator's dependencies; but, it's a reasonable guess if you're using
   * things out-of-the-box.
   */
  @SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
  Map<String, Set<String>> DEFAULT_REQUIREMENTS = new HashMap<String, Set<String>>(){{
    put(STANFORD_TOKENIZE,                 new LinkedHashSet<>(Arrays.asList()));
    put(STANFORD_CDC_TOKENIZE,             new LinkedHashSet<>(Arrays.asList()));
    put(STANFORD_CLEAN_XML,                new LinkedHashSet<>(Arrays.asList(STANFORD_TOKENIZE)));
    put(STANFORD_SSPLIT,                   new LinkedHashSet<>(Arrays.asList(STANFORD_TOKENIZE)));
    put(STANFORD_MWT,                      new LinkedHashSet<>(Arrays.asList(STANFORD_TOKENIZE)));
    put(STANFORD_DOCDATE,                  new LinkedHashSet<>(Arrays.asList()));
    put(STANFORD_POS,                      new LinkedHashSet<>(Arrays.asList(STANFORD_TOKENIZE)));
    put(STANFORD_LEMMA,                    new LinkedHashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_POS)));
    put(STANFORD_NER,                      new LinkedHashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_POS, STANFORD_LEMMA)));
    put(STANFORD_TOKENSREGEX,              new LinkedHashSet<>(Arrays.asList(STANFORD_TOKENIZE)));
    put(STANFORD_REGEXNER,                 new LinkedHashSet<>(Arrays.asList(STANFORD_TOKENIZE)));
    put(STANFORD_ENTITY_MENTIONS,          new LinkedHashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_POS, STANFORD_LEMMA, STANFORD_NER)));
    put(STANFORD_GENDER,                   new LinkedHashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_POS, STANFORD_LEMMA, STANFORD_NER)));
    put(STANFORD_TRUECASE,                 new LinkedHashSet<>(Arrays.asList(STANFORD_TOKENIZE)));
    put(STANFORD_PARSE,                    new LinkedHashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_POS)));
    put(STANFORD_DETERMINISTIC_COREF,      new LinkedHashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_POS, STANFORD_LEMMA, STANFORD_NER, STANFORD_PARSE)));
    put(STANFORD_COREF,                    new LinkedHashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_POS, STANFORD_LEMMA, STANFORD_NER, STANFORD_DEPENDENCIES)));
    put(STANFORD_COREF_MENTION,            new LinkedHashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_POS, STANFORD_LEMMA, STANFORD_NER, STANFORD_DEPENDENCIES)));
    put(STANFORD_RELATION,                 new LinkedHashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_POS, STANFORD_LEMMA, STANFORD_NER, STANFORD_PARSE, STANFORD_DEPENDENCIES)));
    put(STANFORD_SENTIMENT,                new LinkedHashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_POS, STANFORD_PARSE)));
    put(STANFORD_COLUMN_DATA_CLASSIFIER,   new LinkedHashSet<>(Arrays.asList()));
    put(STANFORD_DEPENDENCIES,             new LinkedHashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_POS)));
    put(STANFORD_NATLOG,                   new LinkedHashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_POS, STANFORD_LEMMA, STANFORD_DEPENDENCIES)));
    put(STANFORD_OPENIE,                   new LinkedHashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_POS, STANFORD_LEMMA, STANFORD_DEPENDENCIES, STANFORD_NATLOG)));
    put(STANFORD_QUOTE,                    new LinkedHashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_POS, STANFORD_LEMMA, STANFORD_NER, STANFORD_COREF)));
    put(STANFORD_QUOTE_ATTRIBUTION,        new LinkedHashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_POS, STANFORD_LEMMA, STANFORD_NER, STANFORD_COREF_MENTION, STANFORD_DEPENDENCIES, STANFORD_QUOTE)));
    put(STANFORD_UD_FEATURES,              new LinkedHashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_POS, STANFORD_DEPENDENCIES)));
    put(STANFORD_LINK,                     new LinkedHashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_POS, STANFORD_DEPENDENCIES, STANFORD_LEMMA, STANFORD_NER, STANFORD_ENTITY_MENTIONS)));
    // TODO: there are language specific dependencies which we may
    // want to encode somehow.  For example, English KBP needs coref
    // to function.  Spanish KBP doesn't need coref, and in fact,
    // Spanish coref doesn't even exist.
    put(STANFORD_KBP,                      new LinkedHashSet<>(Arrays.asList(STANFORD_TOKENIZE, STANFORD_POS, STANFORD_DEPENDENCIES, STANFORD_LEMMA, STANFORD_NER)));
  }};

}
