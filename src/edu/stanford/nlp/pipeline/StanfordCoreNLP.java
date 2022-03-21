//
// StanfordCoreNLP -- a suite of NLP tools.
// Copyright (c) 2009-2017 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 2A
//    Stanford CA 94305-9020
//    USA
//

package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.FileSequentialCollection;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;
import edu.stanford.nlp.util.logging.StanfordRedwoodConfiguration;
// import static edu.stanford.nlp.util.logging.Redwood.Util.*;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.regex.Pattern;


/**
 * This is a pipeline that takes in a string and returns various analyzed
 * linguistic forms.
 * The String is tokenized via a tokenizer (using a TokenizerAnnotator), and
 * then other sequence model style annotation can be used to add things like
 * lemmas, POS tags, and named entities.  These are returned as a list of CoreLabels.
 * Other analysis components build and store parse trees, dependency graphs, etc.
 *
 * This class is designed to apply multiple Annotators
 * to an Annotation.  The idea is that you first
 * build up the pipeline by adding Annotators, and then
 * you take the objects you wish to annotate and pass
 * them in and get in return a fully annotated object.
 * At the command-line level you can, e.g., tokenize text with StanfordCoreNLP with a command like:
 * <br><pre>
 * java edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit -file document.txt
 * </pre><br>
 * Please see the package level javadoc for sample usage
 * and a more complete description.
 *
 * The main entry point for the API is StanfordCoreNLP.process() .
 *
 * <i>Implementation note:</i> There are other annotation pipelines, but they
 * don't extend this one. Look for classes that implement Annotator and which
 * have "Pipeline" in their name.
 *
 * @author Jenny Finkel
 * @author Anna Rafferty
 * @author Christopher Manning
 * @author Mihai Surdeanu
 * @author Steven Bethard
 */

public class StanfordCoreNLP extends AnnotationPipeline  {

  public enum OutputFormat { TEXT, TAGGED, XML, JSON, CONLL, CONLLU, INLINEXML, SERIALIZED, CUSTOM }

  private static String getDefaultExtension(OutputFormat outputFormat) {
    switch (outputFormat) {
      case XML: return ".xml";
      case JSON: return ".json";
      case CONLL: return ".conll";
      case CONLLU: return ".conllu";
      case TEXT: return ".out";
      case TAGGED: return ".tag";
      case INLINEXML: return ".inxml";
      case SERIALIZED: return ".ser.gz";
      case CUSTOM: return ".out";
      default: throw new IllegalArgumentException("Unknown output format " + outputFormat);
    }
  }


  /**
   * An annotator name and its associated signature.
   * Used in {@link #GLOBAL_ANNOTATOR_CACHE}.
   */
  public static class AnnotatorSignature {
    public final String name;
    public final String signature;

    public AnnotatorSignature(String name, String signature) {
      this.name = name;
      this.signature = signature;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      AnnotatorSignature that = (AnnotatorSignature) o;
      return Objects.equals(name, that.name) &&
          Objects.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, signature);
    }

    @Override
    public String toString() {
      return "AnnotatorSignature{name='" + name +
              "', signature='" + signature + "'}";
    }

  } // end static class AnnotatorSignature


  /**
   * A global cache of annotators, so we don't have to re-create one if there's enough memory floating around.
   */
  public static final Map<AnnotatorSignature, Lazy<Annotator>> GLOBAL_ANNOTATOR_CACHE = new ConcurrentHashMap<>();



  // other constants
  public static final String CUSTOM_ANNOTATOR_PREFIX = "customAnnotatorClass.";
  private static final String PROPS_SUFFIX = ".properties";
  public static final String NEWLINE_SPLITTER_PROPERTY = "ssplit.eolonly";
  public static final String NEWLINE_IS_SENTENCE_BREAK_PROPERTY = "ssplit.newlineIsSentenceBreak";
  public static final String DEFAULT_NEWLINE_IS_SENTENCE_BREAK = "never";

  public static final String DEFAULT_OUTPUT_FORMAT = "text";

  /** A logger for this class */
  private static final Redwood.RedwoodChannels logger = Redwood.channels(StanfordCoreNLP.class);

  /** Stores the overall number of words processed. */
  private int numWords;

  /** Stores the time (in milliseconds) required to construct the pipeline, for later statistics reporting. */
  private final long pipelineSetupTime;

  /** Properties for this pipeline. Always non-null. */
  private final Properties properties;

  private final Semaphore availableProcessors;

  /** The annotator pool we should be using to get annotators. */
  public final AnnotatorPool pool;


  /**
   * Constructs a pipeline using as properties the properties file found in the classpath
   */
  public StanfordCoreNLP() {
    this((Properties) null);
  }

  /**
   * Construct a basic pipeline. The Properties will be used to determine
   * which annotators to create, and a default AnnotatorPool will be used
   * to create the annotators.
   *
   */
  public StanfordCoreNLP(Properties props)  {
    this(props, (props == null || PropertiesUtils.getBool(props, "enforceRequirements", true)));
  }

  public StanfordCoreNLP(Properties props, boolean enforceRequirements)  {
    this(props, enforceRequirements, null);
  }

  /**
   * Constructs a pipeline with the properties read from this file, which must be found in the classpath.
   *
   * @param propsFileNamePrefix Filename/resource name of properties file without extension
   */
  public StanfordCoreNLP(String propsFileNamePrefix) {
    this(propsFileNamePrefix, true);
  }

  public StanfordCoreNLP(String propsFileNamePrefix, boolean enforceRequirements) {
    this(loadPropertiesOrException(propsFileNamePrefix), enforceRequirements);
  }

  /**
   * Construct a CoreNLP with a custom Annotator Pool.
   */
  public StanfordCoreNLP(Properties props, boolean enforceRequirements, AnnotatorPool annotatorPool)  {
    Timing tim = new Timing();
    this.numWords = 0;

    if (props == null) {
      // if undefined, find the properties file in the classpath; this method returns non-null (it exceptions if null)
      props = loadPropertiesFromClasspath();
    } else if (props.getProperty("annotators") == null) {
      // this happens when some command line options are specified (e.g just "-filelist") but no properties file is.
      // we use the options that are given and let them override the default properties from the class path properties.
      Properties fromClassPath = loadPropertiesFromClasspath();
      fromClassPath.putAll(props);
      props = fromClassPath;
    }
    // handle new fileList by making sure filelist is also set [cdm2018: do in constructor so everyone gets the love]
    if (props.containsKey("fileList")) {
      props.setProperty("filelist", props.getProperty("fileList"));
    }
    this.properties = props;  // from now on we use this.properties

    // alter annotator list if preTokenized option is set
    // preTokenized means just split input text on white space and one sentence per line
    if (PropertiesUtils.getBool(this.properties, ("preTokenized"))) {
      this.properties.setProperty("tokenize.whitespace", "true");
      this.properties.setProperty("ssplit.eolonly", "true");
      String oldAnnotators = this.properties.getProperty("annotators").replaceAll("\\s+", "");
      String newAnnotators = oldAnnotators;
      if (oldAnnotators != null && oldAnnotators.startsWith("cdc_tokenize")) {
        newAnnotators = "tokenize,ssplit" + oldAnnotators.substring(12,oldAnnotators.length());
        logger.info("preTokenized option set: Changing annotators cdc_tokenize to tokenize,ssplit");
      } else if (oldAnnotators != null && oldAnnotators.startsWith("tokenize,ssplit,mwt")) {
        newAnnotators = "tokenize,ssplit" + oldAnnotators.substring(19,oldAnnotators.length());
        logger.info("preTokenized option set: Changing annotators tokenize,ssplit,mwt to tokenize,ssplit");
      } else if (oldAnnotators != null && oldAnnotators.startsWith("tokenize,ssplit")) {
        logger.info("preTokenized option set: Annotators list starts with tokenize,ssplit, no change needed.");
      } else if (oldAnnotators != null && !oldAnnotators.contains("tokenize") && !oldAnnotators.contains("mwt")
                 && !oldAnnotators.contains("ssplit") && !oldAnnotators.contains("cdc_tokenize")) {
        logger.info("preTokenized option set: Adding tokenize,ssplit to beginning.");
        newAnnotators = "tokenize,ssplit," + oldAnnotators;
      } else {
        logger.warn("preTokenized option set: Non-standard annotators list, preTokenized may not work in this case."); 
      }
      this.properties.setProperty("annotators", newAnnotators);
    }

    normalizeAnnotators(this.properties);

    // cdm [2017]: constructAnnotatorPool (PropertiesUtils.getSignature) requires non-null Properties, so after properties setup
    this.pool = annotatorPool != null ? annotatorPool : constructAnnotatorPool(props, getAnnotatorImplementations());

    // Set threading
    if (this.properties.containsKey("threads")) {
      ArgumentParser.threads = PropertiesUtils.getInt(this.properties, "threads");
      this.availableProcessors = new Semaphore(ArgumentParser.threads);
    } else {
      this.availableProcessors = new Semaphore(1);
    }

    // now construct the annotators from the given properties in the given order
    String[] annoNames = getRequiredProperty(this.properties, "annotators").split("[, \t]+");
    Set<String> alreadyAddedAnnoNames = Generics.newHashSet();
    Set<Class<? extends CoreAnnotation>> requirementsSatisfied = Generics.newHashSet();
    for (String name : annoNames) {
      name = name.trim();
      if (name.isEmpty()) { continue; }
      logger.info("Adding annotator " + name);

      Annotator an = pool.get(name);
      this.addAnnotator(an);

      if (enforceRequirements) {
        Set<Class<? extends CoreAnnotation>> allRequirements = an.requires();
        for (Class<? extends CoreAnnotation> requirement : allRequirements) {
          if (!requirementsSatisfied.contains(requirement)) {
            String fmt = "annotator \"%s\" requires annotation \"%s\". The usual requirements for this annotator are: %s";
            Collection<String> defaultRequirements = an.exactRequirements();
            if (defaultRequirements == null) {
              defaultRequirements = Annotator.DEFAULT_REQUIREMENTS.getOrDefault(name, Collections.singleton("unknown"));
            }
            throw new IllegalArgumentException(String.format(fmt, name, requirement.getSimpleName(), StringUtils.join(defaultRequirements, ",")));
          }
        }
        requirementsSatisfied.addAll(an.requirementsSatisfied());
      }

      alreadyAddedAnnoNames.add(name);
    }

    // Sanity check
    if (! alreadyAddedAnnoNames.contains(STANFORD_SSPLIT)) {
      System.setProperty(NEWLINE_SPLITTER_PROPERTY, "false");
    }
    this.pipelineSetupTime = tim.report();
  }

  /**
   * update the annotators, hopefully in a backwards compatible manner
   */
  static void normalizeAnnotators(Properties properties) {
    // if cleanxml is requested and tokenize is here,
    // make it part of tokenize rather than its own annotator
    unifyTokenizeProperty(properties, STANFORD_CLEAN_XML, STANFORD_TOKENIZE + "." + STANFORD_CLEAN_XML);
    // ssplit is always part of tokenize now
    unifyTokenizeProperty(properties, STANFORD_SSPLIT, null);
    // cdc_tokenize is also absorbed into tokenize
    replaceAnnotator(properties, STANFORD_CDC_TOKENIZE, STANFORD_TOKENIZE);
  }

  /**
   * The cdc_tokenize annotator is now part of tokenize
   */
  static void replaceAnnotator(Properties properties, String oldAnnotator, String newAnnotator) {
    String annotators = properties.getProperty("annotators", "");
    String replaced = annotators.replace(oldAnnotator, newAnnotator);
    if (!replaced.equals(annotators)) {
      logger.debug("|" + oldAnnotator + "| is now part of |" + newAnnotator + "|.  Annotators updated to |" + replaced + "|");
      properties.setProperty("annotators", replaced);
    }
  }

  /**
   * The cleanxml annotator can now be invoked as part of the tokenize annotator.
   *<br>
   * To ensure backwards compatibility with previous usage of the pipeline,
   * we allow annotators to be specified tokenize,cleanxml.
   * In such a case, we remove the cleanxml from the annotators and set
   * the tokenize.cleanxml option instead
   */
  static void unifyTokenizeProperty(Properties properties, String property, String option) {
    String annotators = properties.getProperty("annotators", "");
    int tokenize = annotators.indexOf(STANFORD_TOKENIZE);
    int unwanted = annotators.indexOf(property);

    if (unwanted >= 0 && tokenize >= 0) {
      if (option != null) {
        properties.setProperty(option, "true");
      }
      int comma = annotators.indexOf(",", unwanted);
      if (comma >= 0) {
        annotators = annotators.substring(0, unwanted) + annotators.substring(comma+1);
      } else {
        comma = annotators.lastIndexOf(",");
        if (comma < 0) {
          throw new IllegalArgumentException("Unable to process annotators " + annotators);
        }
        annotators = annotators.substring(0, comma);
      }
      if (option != null) {
        logger.debug(property + " can now be triggered as an option to tokenize rather than a separate annotator via " + option + "=true");
      } else {
        logger.debug(property + " is now included as part of the tokenize annotator by default");
      }
      logger.debug("Updating annotators from " + properties.getProperty("annotators") + " to " + annotators);
      properties.setProperty("annotators", annotators);
    }
  }

  //
  // @Override-able methods to change pipeline behavior
  //

  /**
   * Get the implementation of each relevant annotator in the pipeline.
   * The primary use of this method is to be overwritten by subclasses of StanfordCoreNLP
   * to call different annotators that obey the exact same contract as the default
   * annotator.
   * <p>
   * The canonical use case for this is as an implementation of the Curator server,
   * where the annotators make server calls rather than calling each annotator locally.
   *
   * @return A class which specifies the actual implementation of each of the annotators called
   *         when creating the annotator pool. The canonical annotators are defaulted to in
   *         {@link edu.stanford.nlp.pipeline.AnnotatorImplementations}.
   */
  protected AnnotatorImplementations getAnnotatorImplementations() {
    return new AnnotatorImplementations();
  }

  //
  // property-specific methods
  //

  private static String getRequiredProperty(Properties props, String name) {
    String val = props.getProperty(name);
    if (val == null) {
      logger.error("Missing property \"" + name + "\"!");
      printRequiredProperties(System.err);
      throw new RuntimeException("Missing property: \"" + name + '\"');
    }
    return val;
  }

  /**
   * Finds the properties file in the classpath and loads the properties from there.
   *
   * @return The found properties object (must be not-null)
   * @throws RuntimeException If no properties file can be found on the classpath
   */
  private static Properties loadPropertiesFromClasspath() {
    List<String> validNames = Arrays.asList("StanfordCoreNLP", "edu.stanford.nlp.pipeline.StanfordCoreNLP");
    for (String name : validNames) {
      Properties props = loadProperties(name);
      if (props != null) return props;
    }
    throw new RuntimeException("ERROR: Could not find properties file in the classpath!");
  }

  private static Properties loadPropertiesOrException(String propsFileNamePrefix) {
    Properties props = loadProperties(propsFileNamePrefix);
    if (props == null) {
      throw new RuntimeIOException("ERROR: cannot find properties file \"" + propsFileNamePrefix + "\" in the classpath!");
    }
    return props;
  }

  private static Properties loadProperties(String name) {
    return loadProperties(name, Thread.currentThread().getContextClassLoader());
  }

  private static Properties loadProperties(String name, ClassLoader loader) {
    // check if name represents a Stanford CoreNLP supported language
    if (LanguageInfo.isStanfordCoreNLPSupportedLang(name))
      name = LanguageInfo.getLanguagePropertiesFile(name);
    if(name.endsWith (PROPS_SUFFIX)) name = name.substring(0, name.length () - PROPS_SUFFIX.length ());
    name = name.replace('.', '/');
    name += PROPS_SUFFIX;
    Properties result = null;

    // Returns null on lookup failures
    InputStream in = loader.getResourceAsStream (name);
    try {
      if (in != null) {
        InputStreamReader reader = new InputStreamReader(in, "utf-8");
        result = new Properties ();
        result.load(reader); // Can throw IOException
      }
    } catch (IOException e) {
      result = null;
    } finally {
      IOUtils.closeIgnoringExceptions(in);
    }
    if (result != null) {
      logger.info("Searching for resource: " + name + " ... found.");
    } else {
      logger.info("Searching for resource: " + name + " ... not found.");
    }

    return result;
  }

  /** Fetches the Properties object used to construct this Annotator. */
  public Properties getProperties() { return properties; }

  public String getEncoding() {
    return properties.getProperty("encoding", "UTF-8");
  }

  /**
   * Take a collection of requested annotators, and produce a list of annotators such that all of the
   * prerequisites for each of the annotators in the input is met.
   * For example, if the user requests lemma, ensure that pos is also run because lemma depends on
   * pos. As a side effect, this function orders the annotators in the proper order.
   * Note that this is not guaranteed to return a valid set of annotators,
   * as properties passed to the annotators can change their requirements.
   *
   * @param annotators The annotators the user has requested.
   * @return A sanitized annotators string with all prerequisites met.
   */
  public static String ensurePrerequisiteAnnotators(String[] annotators, Properties props) {
    int posIndex = ArrayUtils.indexOf(annotators, Annotator.STANFORD_POS);
    int parseIndex = ArrayUtils.indexOf(annotators, Annotator.STANFORD_PARSE);
    boolean useParseForPos = ((parseIndex >= 0) && (posIndex < 0)); // Already doing the parsing, use the parsing for the pos tag

    // Get an unordered set of annotators
    Set<String> unorderedAnnotators = new LinkedHashSet<>();  // linked to preserve order
    Collections.addAll(unorderedAnnotators, annotators);
    for (String annotator : annotators) {
      // Add the annotator
      if (!getNamedAnnotators().containsKey(annotator.toLowerCase())) {
        throw new IllegalArgumentException("Unknown annotator: " + annotator);
      }

      // Add its transitive dependencies
      unorderedAnnotators.add(annotator.toLowerCase());
      if (!Annotator.DEFAULT_REQUIREMENTS.containsKey(annotator.toLowerCase())) {
        throw new IllegalArgumentException("Cannot infer requirements for annotator: " + annotator);
      }
      Queue<String> fringe = new LinkedList<>(Annotator.DEFAULT_REQUIREMENTS.get(annotator.toLowerCase()));
      int ticks = 0;
      while (!fringe.isEmpty()) {
        ticks += 1;
        if (ticks == 1000000) {
          throw new IllegalStateException("[INTERNAL ERROR] Annotators have a circular dependency.");
        }
        String prereq = fringe.poll();
        unorderedAnnotators.add(prereq);
        fringe.addAll(Annotator.DEFAULT_REQUIREMENTS.get(prereq.toLowerCase()));
      }
    }

    if (useParseForPos) {
      unorderedAnnotators.remove(Annotator.STANFORD_POS);
    }

    // Order the annotators
    List<String> orderedAnnotators = new ArrayList<>();
    while (!unorderedAnnotators.isEmpty()) {
      boolean somethingAdded = false;  // to make sure the dependencies are satisfiable
      // Loop over candidate annotators to add
      Iterator<String> iter = unorderedAnnotators.iterator();
      while (iter.hasNext()) {
        String candidate = iter.next();
        // Are the requirements satisfied?
        boolean canAdd = true;
        for (String prereq : Annotator.DEFAULT_REQUIREMENTS.get(candidate.toLowerCase())) {
          // Weird hack to replace POS with POS tags from PARSE if we are already doing parse (and just parse)
          if (useParseForPos && Annotator.STANFORD_POS.equals(prereq)) {
            prereq = Annotator.STANFORD_PARSE;
          }
          if (!prereq.equals(candidate) && !orderedAnnotators.contains(prereq)) {
            canAdd = false;
            break;
          }
        }
        // If so, add the annotator
        if (canAdd) {
          orderedAnnotators.add(candidate);
          iter.remove();
          somethingAdded = true;
        }
      }
      // Make sure we're making progress every iteration, to prevent an infinite loop
      if (!somethingAdded) {
        throw new IllegalArgumentException("Unsatisfiable annotator list: " + StringUtils.join(annotators, ","));
      }
    }

    // Remove depparse + parse -- these are redundant
    if (orderedAnnotators.contains(STANFORD_PARSE) && !ArrayUtils.contains(annotators, STANFORD_DEPENDENCIES)) {
      orderedAnnotators.remove(STANFORD_DEPENDENCIES);
    }

    // Tweak the properties, if necessary
    // (set the mention annotator to use dependency trees, if appropriate)
    if ((orderedAnnotators.contains(Annotator.STANFORD_COREF_MENTION) || orderedAnnotators.contains(Annotator.STANFORD_COREF))
        && !orderedAnnotators.contains(Annotator.STANFORD_PARSE) &&
        !props.containsKey("coref.md.type")) {
      props.setProperty("coref.md.type", "dep");
    }
    // (ensure regexner is after ner)
    if (orderedAnnotators.contains(Annotator.STANFORD_NER) && orderedAnnotators.contains(STANFORD_REGEXNER)) {
      orderedAnnotators.remove(STANFORD_REGEXNER);
      int nerIndex = orderedAnnotators.indexOf(Annotator.STANFORD_NER);
      orderedAnnotators.add(nerIndex + 1, STANFORD_REGEXNER);
    }
    // (ensure coref is before openie)
    if (orderedAnnotators.contains(Annotator.STANFORD_COREF) && orderedAnnotators.contains(STANFORD_OPENIE)) {
      int maxIndex = Math.max(
          orderedAnnotators.indexOf(STANFORD_OPENIE),
          orderedAnnotators.indexOf(STANFORD_COREF)
          );
      if (Objects.equals(orderedAnnotators.get(maxIndex), STANFORD_OPENIE)) {
        orderedAnnotators.add(maxIndex, STANFORD_COREF);
        orderedAnnotators.remove(STANFORD_COREF);
      } else {
        orderedAnnotators.add(maxIndex + 1, STANFORD_OPENIE);
        orderedAnnotators.remove(STANFORD_OPENIE);
      }
    }

    // Return
    return StringUtils.join(orderedAnnotators, ",");
  }


  /**
   * Check if we can construct an XML outputter.
   *
   * @return Whether we can construct an XML outputter.
   */
  private static boolean isXMLOutputPresent() {
    try {
      Class.forName("edu.stanford.nlp.pipeline.XMLOutputter");
    } catch (ClassNotFoundException | NoClassDefFoundError ex) {
      return false;
    }
    return true;
  }

  //
  // AnnotatorPool construction support
  //

  /**
   * Call this if you are no longer using StanfordCoreNLP and want to
   * release the memory associated with the annotators.
   */
  public static synchronized void clearAnnotatorPool() {
    logger.warn("Clearing CoreNLP annotation pool; this should be unnecessary in production");
    GLOBAL_ANNOTATOR_CACHE.clear();
  }


  /**
   * This function defines the list of named annotators in CoreNLP, along with how to construct
   * them.
   *
   * @return A map from annotator name, to the function which constructs that annotator.
   */
  private static Map<String, BiFunction<Properties, AnnotatorImplementations, Annotator>> getNamedAnnotators() {
    Map<String, BiFunction<Properties, AnnotatorImplementations, Annotator>> pool = new HashMap<>();
    pool.put(STANFORD_TOKENIZE, (props, impl) -> impl.tokenizer(props));
    pool.put(STANFORD_CDC_TOKENIZE, (props, impl) -> impl.cdcTokenizer(props));
    pool.put(STANFORD_CLEAN_XML, (props, impl) -> impl.cleanXML(props));
    pool.put(STANFORD_SSPLIT, (props, impl) -> impl.wordToSentences(props));
    pool.put(STANFORD_MWT, (props, impl) -> impl.multiWordToken(props));
    pool.put(STANFORD_DOCDATE, (props, impl) -> impl.docDate(props));
    pool.put(STANFORD_POS, (props, impl) -> impl.posTagger(props));
    pool.put(STANFORD_LEMMA, (props, impl) -> impl.morpha(props, false));
    pool.put(STANFORD_NER, (props, impl) -> impl.ner(props));
    pool.put(STANFORD_TOKENSREGEX, (props, impl) -> impl.tokensregex(props, STANFORD_TOKENSREGEX));
    pool.put(STANFORD_REGEXNER, (props, impl) -> impl.tokensRegexNER(props, STANFORD_REGEXNER));
    pool.put(STANFORD_ENTITY_MENTIONS, (props, impl) -> impl.entityMentions(props, STANFORD_ENTITY_MENTIONS));
    pool.put(STANFORD_GENDER, (props, impl) -> impl.gender(props, STANFORD_GENDER));
    pool.put(STANFORD_TRUECASE, (props, impl) -> impl.trueCase(props));
    pool.put(STANFORD_PARSE, (props, impl) -> impl.parse(props));
    pool.put(STANFORD_COREF_MENTION, (props, impl) -> impl.corefMention(props));
    pool.put(STANFORD_DETERMINISTIC_COREF, (props, impl) -> impl.dcoref(props));
    pool.put(STANFORD_COREF, (props, impl) -> impl.coref(props));
    pool.put(STANFORD_RELATION, (props, impl) -> impl.relations(props));
    pool.put(STANFORD_SENTIMENT, (props, impl) -> impl.sentiment(props, STANFORD_SENTIMENT));
    pool.put(STANFORD_COLUMN_DATA_CLASSIFIER, (props, impl) -> impl.columnData(props));
    pool.put(STANFORD_DEPENDENCIES, (props, impl) -> impl.dependencies(props));
    pool.put(STANFORD_NATLOG, (props, impl) -> impl.natlog(props));
    pool.put(STANFORD_OPENIE, (props, impl) -> impl.openie(props));
    pool.put(STANFORD_QUOTE, (props, impl) -> impl.quote(props));
    pool.put(STANFORD_QUOTE_ATTRIBUTION, (props, impl) -> impl.quoteattribution(props));
    pool.put(STANFORD_UD_FEATURES, (props, impl) -> impl.udfeats(props));
    pool.put(STANFORD_LINK, (props, impl) -> impl.link(props));
    pool.put(STANFORD_KBP, (props, impl) -> impl.kbp(props));
    return pool;
  }


  /**
   * Construct the default annotator pool, and save it as the static annotator pool
   * for CoreNLP.
   *
   * @see StanfordCoreNLP#constructAnnotatorPool(Properties, AnnotatorImplementations)
   */
  public static synchronized AnnotatorPool getDefaultAnnotatorPool(final Properties inputProps, final AnnotatorImplementations annotatorImplementation) {
    // if the pool already exists reuse!
    AnnotatorPool pool = AnnotatorPool.SINGLETON;
    for (Map.Entry<String, BiFunction<Properties, AnnotatorImplementations, Annotator>> entry : getNamedAnnotators().entrySet()) {
      AnnotatorSignature key = new AnnotatorSignature(entry.getKey(), PropertiesUtils.getSignature(entry.getKey(), inputProps));
      pool.register(entry.getKey(), inputProps, GLOBAL_ANNOTATOR_CACHE.computeIfAbsent(key, (sig) -> Lazy.cache(() -> entry.getValue().apply(inputProps, annotatorImplementation))));
    }
    registerCustomAnnotators(pool, annotatorImplementation, inputProps);
    return pool;
  }


  /**
   * Register any custom annotators defined in the input properties, and add them to the pool.
   *
   * @param pool The annotator pool to add the new custom annotators to.
   * @param annotatorImplementation The implementation thunk to use to create any new annotators.
   * @param inputProps The properties to read new annotator definitions from.
   */
  private static void registerCustomAnnotators(AnnotatorPool pool, AnnotatorImplementations annotatorImplementation, Properties inputProps) {
    // add annotators loaded via reflection from class names specified
    // in the properties
    for (String property : inputProps.stringPropertyNames()) {
      if (property.startsWith(CUSTOM_ANNOTATOR_PREFIX)) {
        final String customName =
            property.substring(CUSTOM_ANNOTATOR_PREFIX.length());
        final String customClassName = inputProps.getProperty(property);
        logger.info("Registering annotator " + customName + " with class " + customClassName);
        AnnotatorSignature key = new AnnotatorSignature(customName, PropertiesUtils.getSignature(customName, inputProps));
        pool.register(customName, inputProps, GLOBAL_ANNOTATOR_CACHE.computeIfAbsent(key, (sig) -> Lazy.cache(() -> annotatorImplementation.custom(inputProps, property))));
      }
    }
  }



  /**
   * Construct the default annotator pool from the passed in properties, and overwriting annotators which have changed
   * since the last call.
   *
   * @param inputProps Properties to determine behavior of annotators
   * @param annotatorImplementation Source of annotator implementations
   * @return A populated AnnotatorPool
   */
  private static AnnotatorPool constructAnnotatorPool(final Properties inputProps, final AnnotatorImplementations annotatorImplementation) {
    AnnotatorPool pool = new AnnotatorPool();
    for (Map.Entry<String, BiFunction<Properties, AnnotatorImplementations, Annotator>> entry : getNamedAnnotators().entrySet()) {
      AnnotatorSignature key = new AnnotatorSignature(entry.getKey(), PropertiesUtils.getSignature(entry.getKey(), inputProps));
      pool.register(entry.getKey(), inputProps, GLOBAL_ANNOTATOR_CACHE.computeIfAbsent(key, (sig) -> Lazy.cache(() -> entry.getValue().apply(inputProps, annotatorImplementation))));
    }
    registerCustomAnnotators(pool, annotatorImplementation, inputProps);
    return pool;
  }

  public static synchronized Annotator getExistingAnnotator(String name) {
    Optional<Annotator> annotator = GLOBAL_ANNOTATOR_CACHE.entrySet().stream()
        .filter(entry -> name.equals(entry.getKey().name))
        .map(entry -> Optional.ofNullable(entry.getValue().getIfDefined()))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst();
    if (annotator.isPresent()) {
      return annotator.get();
    } else {
      logger.error("Attempted to fetch annotator \"" + name +
          "\" but the annotator pool does not store any such type!");
      return null;
    }
  }

  /** Annotate the CoreDocument wrapper. **/
  public void annotate(CoreDocument document) {
    // annotate the underlying Annotation
    this.annotate(document.annotationDocument);
    // wrap the sentences and entity mentions post annotation
    document.wrapAnnotations();
  }

  /** {@inheritDoc} */
  @Override
  public void annotate(Annotation annotation) {
    super.annotate(annotation);
    List<CoreLabel> words = annotation.get(CoreAnnotations.TokensAnnotation.class);
    if (words != null) {
      numWords += words.size();
    }
  }


  public void annotate(final Annotation annotation, final Consumer<Annotation> callback){
    if (PropertiesUtils.getInt(properties, "threads", 1) == 1) {
      annotate(annotation);
      callback.accept(annotation);
    } else {
      try {
        availableProcessors.acquire();
      } catch (InterruptedException e) {
        throw new RuntimeInterruptedException(e);
      }
      new Thread(() -> {
        try {
          annotate(annotation);
        } catch (Throwable t) {
          annotation.set(CoreAnnotations.ExceptionAnnotation.class, t);
        }
        callback.accept(annotation);
        availableProcessors.release();
      }).start();
    }
  }



  /**
   * Determines whether the parser annotator should default to
   * producing binary trees.  Currently there is only one condition
   * under which this is true: the sentiment annotator is used.
   */
  public static boolean usesBinaryTrees(Properties props) {
    Set<String> annoNames = Generics.newHashSet(Arrays.asList(props.getProperty("annotators","").split("[, \t]+")));
    return annoNames.contains(STANFORD_SENTIMENT);
  }

  /**
   * Runs the entire pipeline on the content of the given text passed in.
   * @param text The text to process
   * @return An Annotation object containing the output of all annotators
   */
  public Annotation process(String text) {
    Annotation annotation = new Annotation(text);
    annotate(annotation);
    return annotation;
  }

  /**
   * Runs the entire pipeline on the content of the given text passed in.
   * @param text The text to process
   * @return An Annotation object containing the output of all annotators
   */
  public CoreDocument processToCoreDocument(String text) {
    return new CoreDocument(process(text));
  }

  //
  // output and formatting methods (including XML-specific methods)
  //

  /**
   * Displays the output of all annotators in a format easily readable by people.
   *
   * @param annotation Contains the output of all annotators
   * @param os The output stream
   */
  public void prettyPrint(Annotation annotation, OutputStream os) {
    TextOutputter.prettyPrint(annotation, os, this);
  }

  /**
   * Displays the output of all annotators in a format easily readable by people.
   *
   * @param annotation Contains the output of all annotators
   * @param os The output stream
   */
  public void prettyPrint(Annotation annotation, PrintWriter os) {
    TextOutputter.prettyPrint(annotation, os, this);
  }

  /**
   * Wrapper around xmlPrint(Annotation, OutputStream).
   * Added for backward compatibility.
   *
   * @param annotation The Annotation to print
   * @param w The Writer to send the output to
   * @throws IOException If any IO problem
   */
  public void xmlPrint(Annotation annotation, Writer w) throws IOException {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    xmlPrint(annotation, os); // this builds it as the encoding specified in the properties
    w.write(new String(os.toByteArray(), getEncoding()));
    w.flush();
  }

  /**
   * Displays the output of all annotators in XML format.
   *
   * @param annotation Contains the output of all annotators
   * @param os The output stream
   * @throws IOException If any IO problem
   */
  public void xmlPrint(Annotation annotation, OutputStream os) throws IOException {
    try {
      Class clazz = Class.forName("edu.stanford.nlp.pipeline.XMLOutputter");
      Method method = clazz.getMethod("xmlPrint", Annotation.class, OutputStream.class, StanfordCoreNLP.class);
      method.invoke(null, annotation, os, this);
    } catch (NoSuchMethodException | IllegalAccessException | ClassNotFoundException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Displays the output of all annotators in JSON format.
   *
   * @param annotation Contains the output of all annotators
   * @param w The Writer to send the output to
   * @throws IOException If any IO problem
   */
  public void jsonPrint(Annotation annotation, Writer w) throws IOException {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    JSONOutputter.jsonPrint(annotation, os, this);
    w.write(new String(os.toByteArray(), getEncoding()));
    w.flush();
  }

  /**
   * Displays the output of many annotators in CoNLL format.
   * (Only used by CoreNLPServelet.)
   *
   * @param annotation Contains the output of all annotators
   * @param w The Writer to send the output to
   * @throws IOException If any IO problem
   */
  public void conllPrint(Annotation annotation, Writer w) throws IOException {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    CoNLLOutputter.conllPrint(annotation, os, this);
    w.write(new String(os.toByteArray(), getEncoding()));
    w.flush();
  }

  //
  // runtime, shell-specific, and help menu methods
  //

  /**
   * Prints the list of properties required to run the pipeline
   * @param os PrintStream to print usage to
   * @param helpTopic a topic to print help about (or null for general options)
   */
  protected static void printHelp(PrintStream os, String helpTopic) {
    if (helpTopic.toLowerCase().startsWith("pars")) {
      os.println("StanfordCoreNLP currently supports the following parsers:");
      os.println("\tstanford - Stanford lexicalized parser (default)");
      os.println("\tcharniak - Charniak and Johnson reranking parser (sold separately)");
      os.println();
      os.println("General options: (all parsers)");
      os.println("\tparse.type - selects the parser to use");
      os.println("\tparse.model - path to model file for parser");
      os.println("\tparse.maxlen - maximum sentence length");
      os.println();
      os.println("Stanford Parser-specific options:");
      os.println("(In general, you shouldn't need to set this flags)");
      os.println("\tparse.flags - extra flags to the parser (default: -retainTmpSubcategories)");
      os.println("\tparse.debug - set to true to make the parser slightly more verbose");
      os.println();
      os.println("Charniak and Johnson parser-specific options:");
      os.println("\tparse.executable - path to the parseIt binary or parse.sh script");
    } else {
      // argsToProperties will set the value of a -h or -help to "true" if no arguments are given
      if ( ! helpTopic.equalsIgnoreCase("true")) {
        os.println("Unknown help topic: " + helpTopic);
        os.println("See -help for a list of all help topics.");
      } else {
        printRequiredProperties(os);
      }
    }
  }

  /**
   * Prints the list of properties required to run the pipeline
   * @param os PrintStream to print usage to
   */
  private static void printRequiredProperties(PrintStream os) {
    // TODO some annotators (ssplit, regexner, gender, some parser options, dcoref?) are not documented
    os.println("The following properties can be defined:");
    os.println("(if -props or -annotators is not passed in, default properties will be loaded via the classpath)");
    os.println("\t\"props\" - path to file with configuration properties");
    os.println("\t\"annotators\" - comma separated list of annotators");
    os.println("\tThe following annotators are supported: cleanxml, tokenize, quote, ssplit, pos, lemma, ner, truecase, parse, hcoref, relation");

    os.println();
    os.println("\tIf annotator \"tokenize\" is defined:");
    os.println("\t\"tokenize.options\" - PTBTokenizer options (see edu.stanford.nlp.process.PTBTokenizer for details)");
    os.println("\t\"tokenize.whitespace\" - If true, just use whitespace tokenization");
    os.println("\t\"tokenize.codepoint\" - If true, add codepoint offsets for counting non-BMP characters");

    os.println();
    os.println("\tIf annotator \"cleanxml\" is defined:");
    os.println("\t\"clean.xmltags\" - regex of tags to extract text from");
    os.println("\t\"clean.sentenceendingtags\" - regex of tags which mark sentence endings");
    os.println("\t\"clean.allowflawedxml\" - if set to true, don't complain about XML errors");

    os.println();
    os.println("\tIf annotator \"pos\" is defined:");
    os.println("\t\"pos.maxlen\" - maximum length of sentence to POS tag");
    os.println("\t\"pos.model\" - path towards the POS tagger model");

    os.println();
    os.println("\tIf annotator \"ner\" is defined:");
    os.println("\t\"ner.model\" - paths for the ner models.  By default, the English 3 class, 7 class, and 4 class models are used.");
    os.println("\t\"ner.useSUTime\" - Whether or not to use sutime (English specific)");
    os.println("\t\"ner.applyNumericClassifiers\" - whether or not to use any numeric classifiers (English specific)");
    os.println("\t\"ner.applyFineGrained\" - whether or not to apply fine grained regex NER annotation (English specific)");
    os.println("\t\"ner.additional.tokensregex.rules\" - additional tokensregex rules to use for NER recognition");
    os.println("\t\"ner.additional.regexner.mapping\" - additional regex rules to use for NER recognition");

    os.println();
    os.println("\tIf annotator \"truecase\" is defined:");
    os.println("\t\"truecase.model\" - path towards the true-casing model; default: " + DefaultPaths.DEFAULT_TRUECASE_MODEL);
    os.println("\t\"truecase.bias\" - class bias of the true case model; default: " + TrueCaseAnnotator.DEFAULT_MODEL_BIAS);
    os.println("\t\"truecase.mixedcasefile\" - path towards the mixed case file; default: " + DefaultPaths.DEFAULT_TRUECASE_DISAMBIGUATION_LIST);

    os.println();
    os.println("\tIf annotator \"relation\" is defined:");
    os.println("\t\"sup.relation.verbose\" - whether verbose or not");
    os.println("\t\"sup.relation.model\" - path towards the relation extraction model");

    os.println();
    os.println("\tIf annotator \"parse\" is defined:");
    os.println("\t\"parse.model\" - path towards the PCFG parser model");

    /* XXX: unstable, do not use for now
    os.println();
    os.println("\tIf annotator \"srl\" is defined:");
    os.println("\t\"srl.verb.args\" - path to the file listing verbs and their core arguments (\"verbs.core_args\")");
    os.println("\t\"srl.model.id\" - path prefix for the role identification model (adds \".model.gz\" and \".fe\" to this prefix)");
    os.println("\t\"srl.model.cls\" - path prefix for the role classification model (adds \".model.gz\" and \".fe\" to this prefix)");
    os.println("\t\"srl.model.jic\" - path to the directory containing the joint model's \"model.gz\", \"fe\" and \"je\" files");
    os.println("\t                  (if not specified, the joint model will not be used)");
    */

    os.println();
    os.println("Command line properties:");
    os.println("\t\"file\" - run the pipeline on the content of this file, or on the content of the files in this directory");
    os.println("\t         XML output is generated for every input file \"file\" as file.xml");
    os.println("\t\"extension\" - if -file used with a directory, process only the files with this extension");
    os.println("\t\"fileList\" - run the pipeline on the list of files given in this file");
    os.println("\t             output is generated for every input file as file.outputExtension");
    os.println("\t\"outputDirectory\" - where to put output (defaults to the current directory)");
    os.println("\t\"outputExtension\" - extension to use for the output file (defaults to \".xml\" for XML, \".ser.gz\" for serialized).  Don't forget the dot!");
    os.println("\t\"outputFormat\" - \"text\"  (default), \"tagged\", \"json\", \"conll\", \"conllu\", \"serialized\", \"xml\" or \"custom\"");
    os.println("\t\"customOutputter\" - specify a class to a custom outputter instead of a pre-defined output format");
    os.println("\t\"serializer\" - Class of annotation serializer to use when outputFormat is \"serialized\".  By default, uses ProtobufAnnotationSerializer.");
    os.println("\t\"replaceExtension\" - flag to chop off the last extension before adding outputExtension to file");
    os.println("\t\"noClobber\" - don't automatically override (clobber) output files that already exist");
    os.println("\t\"isOneDocument\" - (for piped input only) treat the text till eof as one document rather than one document per line");
    os.println("\t\"threads\" - multithread on this number of threads");
    os.println();
    os.println("If none of the above are present, run the pipeline in an interactive shell (default properties will be loaded from the classpath).");
    os.println("The shell accepts input from stdin and displays the output at stdout.");

    os.println();
    os.println("Run with -help [topic] for more help on a specific topic.");
    os.println("Current topics include: parser");

    os.println();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String timingInformation() {
    StringBuilder sb = new StringBuilder(super.timingInformation());
    if (TIME && numWords >= 0) {
      long total = this.getTotalTime();
      sb.append(" for ").append(this.numWords).append(" tokens at ");
      sb.append(String.format("%.1f", numWords / (((double) total)/1000)));
      sb.append( " tokens/sec.");
    }
    return sb.toString();
  }


  /**
   * Runs as either a filter or as an interactive shell where input text is processed with the given pipeline.
   * The default case is to treat each line as a document. This can be altered with the property.
   *
   * @throws IOException If IO problem with stdin
   */
  private void shell() throws IOException {
    AnnotationOutputter.Options options = AnnotationOutputter.getOptions(properties);
    String encoding = getEncoding();
    BufferedReader r = new BufferedReader(IOUtils.encodedInputStreamReader(System.in, encoding));
    boolean isTty = System.console() != null;
    boolean oneDocument = Boolean.parseBoolean(properties.getProperty("isOneDocument"));
    if (isTty) {
      System.err.println("Entering interactive shell. Type q RETURN or EOF to quit.");
    }

    while (true) {
      if (isTty) { System.err.print("NLP> "); }
      String line;
      if (oneDocument) {
        line = IOUtils.slurpReader(r);
      } else {
        line = r.readLine();
      }
      if (line == null || isTty && line.equalsIgnoreCase("q")) {
        break;
      }
      if ( ! line.isEmpty()) {
        Annotation anno = process(line);
        outputAnnotation(System.out, anno, properties, options);
      }
      if (oneDocument) {
        break;
      }
    }
  }


  protected static Collection<File> readFileList(String fileName) {
    return ObjectBank.getLineIterator(fileName, new ObjectBank.PathToFileFunction());
  }

  private static AnnotationSerializer loadSerializer(String serializerClass, String name, Properties properties) {
    AnnotationSerializer serializer; // initialized below
    try {
      // Try loading with properties
      serializer = ReflectionLoading.loadByReflection(serializerClass, name, properties);
    } catch (ReflectionLoading.ReflectionLoadingException ex) {
      // Try loading with just default constructor
      serializer = ReflectionLoading.loadByReflection(serializerClass);
    }
    return serializer;
  }


  /**
   * Create an outputter to be passed into {@link StanfordCoreNLP#processFiles(String, Collection, int, Properties, BiConsumer, BiConsumer, OutputFormat, boolean)}.
   *
   * @param properties The properties file to use.
   *
   * @return A consumer that can be passed into the processFiles method.
   */
  public static BiConsumer<Annotation, OutputStream> createOutputter(Properties properties, AnnotationOutputter.Options options) {
    return (Annotation annotation, OutputStream fos) -> {
      try {
        outputAnnotation(fos, annotation, properties, options);
      } catch (IOException e) {
        throw new RuntimeIOException(e);
      }
    };
  }


  private static void outputAnnotation(OutputStream fos,
                                       Annotation annotation,
                                       Properties properties,
                                       AnnotationOutputter.Options outputOptions) throws IOException {
    final OutputFormat outputFormat =
            OutputFormat.valueOf(properties.getProperty("outputFormat", DEFAULT_OUTPUT_FORMAT).toUpperCase(Locale.ROOT));

    switch (outputFormat) {
      case XML:
        AnnotationOutputter outputter = MetaClass.create("edu.stanford.nlp.pipeline.XMLOutputter").createInstance();
        outputter.print(annotation, fos, outputOptions);
        break;
      case JSON:
        new JSONOutputter().print(annotation, fos, outputOptions);
        break;
      case CONLL:
        new CoNLLOutputter().print(annotation, fos, outputOptions);
        break;
      case TEXT:
        new TextOutputter().print(annotation, fos, outputOptions);
        break;
      case TAGGED:
        new TaggedTextOutputter().print(annotation, fos, outputOptions);
        break;
      case SERIALIZED:
        final String serializerClass = properties.getProperty("serializer", ProtobufAnnotationSerializer.class.getName());
        final String outputSerializerClass = properties.getProperty("outputSerializer", serializerClass);
        final String outputSerializerName = (serializerClass.equals(outputSerializerClass))? "serializer":"outputSerializer";

        if (outputSerializerClass != null) {
          AnnotationSerializer outputSerializer = loadSerializer(outputSerializerClass, outputSerializerName, properties);
          outputSerializer.write(annotation, fos);
        }
        break;
      case CONLLU:
        new CoNLLUOutputter(properties).print(annotation, fos, outputOptions);
        break;
      case INLINEXML:
        new InlineXMLOutputter().print(annotation, fos, outputOptions);
        break;
      case CUSTOM:
        AnnotationOutputter customOutputter = ReflectionLoading.loadByReflection(properties.getProperty("customOutputter"));
        customOutputter.print(annotation, fos, outputOptions);
        break;
      default:
        throw new IllegalArgumentException("Unknown output format " + outputFormat);
    }
  }

  /**
   * Helper method for printing out timing info after an annotation run
   *
   * @param pipeline the StanfordCoreNLP pipeline to log timing info for
   * @param tim the Timing object to log timing info
   */
  private static void logTimingInfo(StanfordCoreNLP pipeline, Timing tim) {
    logger.info(""); // puts blank line in logging output
    logger.info(pipeline.timingInformation());
    logger.info("Pipeline setup: " +
        Timing.toSecondsString(pipeline.pipelineSetupTime) + " sec.");
    logger.info("Total time for StanfordCoreNLP pipeline: " +
        Timing.toSecondsString(pipeline.pipelineSetupTime + tim.report()) + " sec.");
  }


  /**
   * Process a collection of files.
   *
   * @param base The base input directory to process from.
   * @param files The files to process.
   * @param numThreads The number of threads to annotate on.
   * @param clearPool Whether or not to clear pool when process is done
   *
   * @throws IOException
   */
  public void processFiles(String base, final Collection<File> files, int numThreads, boolean clearPool, Optional<Timing> tim) throws IOException {
    AnnotationOutputter.Options options = AnnotationOutputter.getOptions(properties);
    StanfordCoreNLP.OutputFormat outputFormat = StanfordCoreNLP.OutputFormat.valueOf(properties.getProperty("outputFormat", DEFAULT_OUTPUT_FORMAT).toUpperCase(Locale.ROOT));
    processFiles(base, files, numThreads, properties, this::annotate, createOutputter(properties, options), outputFormat, clearPool, Optional.of(this), tim);
  }

  protected static void processFiles(String base, final Collection<File> files, int numThreads,
                                     Properties properties, BiConsumer<Annotation, Consumer<Annotation>> annotate,
                                     BiConsumer<Annotation, OutputStream> print,
                                     OutputFormat outputFormat, boolean clearPool) throws IOException {
    processFiles(base, files, numThreads, properties, annotate, print, outputFormat, clearPool, Optional.empty(), Optional.empty());
  }

  /**
   * A common method for processing a set of files, used in both {@link StanfordCoreNLP} as well as
   * {@link StanfordCoreNLPClient}.
   *
   * @param base The base input directory to process from.
   * @param files The files to process.
   * @param numThreads The number of threads to annotate on.
   * @param properties The properties file to use during annotation.
   *                   This should match the properties file used in the implementation of the annotate function.
   * @param annotate The function used to annotate a document.
   * @param print The function used to print a document.
   * @param outputFormat The format used for printing out documents
   * @param clearPool Whether or not to clear the pool when done
   * @param pipeline the pipeline annotating the objects
   * @param tim the Timing object for this annotation run
   *
   * @throws IOException If any IO problem
   */
  protected static void processFiles(String base, final Collection<File> files, int numThreads,
                                     Properties properties, BiConsumer<Annotation, Consumer<Annotation>> annotate,
                                     BiConsumer<Annotation, OutputStream> print,
                                     OutputFormat outputFormat, boolean clearPool,
                                     Optional<StanfordCoreNLP> pipeline, Optional<Timing> tim) throws IOException {
    // List<Runnable> toRun = new LinkedList<>();

    // Process properties here
    final String baseOutputDir = properties.getProperty("outputDirectory", ".");
    final String baseInputDir = properties.getProperty("inputDirectory", base);

    // Set of files to exclude
    final String excludeFilesParam = properties.getProperty("excludeFiles");
    final Set<String> excludeFiles = new HashSet<>();
    if (excludeFilesParam != null) {
      Iterable<String> lines = IOUtils.readLines(excludeFilesParam);
      for (String line:lines) {
        String name = line.trim();
        if (!name.isEmpty()) excludeFiles.add(name);
      }
    }

    //(file info)
    final String serializerClass = properties.getProperty("serializer", GenericAnnotationSerializer.class.getName());
    final String inputSerializerClass = properties.getProperty("inputSerializer", serializerClass);
    final String inputSerializerName = (serializerClass.equals(inputSerializerClass))? "serializer":"inputSerializer";

    final String extension = properties.getProperty("outputExtension", getDefaultExtension(outputFormat));
    final boolean replaceExtension = Boolean.parseBoolean(properties.getProperty("replaceExtension", "false"));
    final boolean continueOnAnnotateError = Boolean.parseBoolean(properties.getProperty("continueOnAnnotateError", "false"));

    final boolean noClobber = Boolean.parseBoolean(properties.getProperty("noClobber", "false"));
    // final boolean randomize = Boolean.parseBoolean(properties.getProperty("randomize", "false"));

    final MutableInteger totalProcessed = new MutableInteger(0);
    final MutableInteger totalSkipped = new MutableInteger(0);
    final MutableInteger totalErrorAnnotating = new MutableInteger(0);

    //for each file...
    for (final File file : files) {
      // Determine if there is anything to be done....
      if (excludeFiles.contains(file.getName())) {
        logger.err("Skipping excluded file " + file.getName());
        totalSkipped.incValue(1);
        continue;
      }

      //--Get Output File Info
      //(filename)
      String outputDir = baseOutputDir;
      if (baseInputDir != null) {
        // Get input file name relative to base
        String relDir = file.getParent().replaceFirst(Pattern.quote(baseInputDir), "");
        outputDir = outputDir + File.separator + relDir;
      }
      // Make sure output directory exists
      new File(outputDir).mkdirs();
      String outputFilename = new File(outputDir, file.getName()).getPath();
      if (replaceExtension) {
        int lastDot = outputFilename.lastIndexOf('.');
        // for paths like "./zzz", lastDot will be 0
        if (lastDot > 0) {
          outputFilename = outputFilename.substring(0, lastDot);
        }
      }
      // ensure we don't make filenames with doubled extensions like .xml.xml
      if (!outputFilename.endsWith(extension)) {
        outputFilename += extension;
      }
      // normalize filename for the upcoming comparison
      outputFilename = new File(outputFilename).getCanonicalPath();

      //--Conditions For Skipping The File
      // TODO this could fail if there are softlinks, etc. -- need some sort of sameFile tester
      //      Java 7 will have a Files.isSymbolicLink(file) method
      if (outputFilename.equals(file.getCanonicalPath())) {
        logger.err("Skipping " + file.getName() + ": output file " + outputFilename + " has the same filename as the input file -- assuming you don't actually want to do this.");
        totalSkipped.incValue(1);
        continue;
      }
      if (noClobber && new File(outputFilename).exists()) {
        logger.err("Skipping " + file.getName() + ": output file " + outputFilename + " as it already exists.  Don't use the noClobber option to override this.");
        totalSkipped.incValue(1);
        continue;
      }

      final String finalOutputFilename = outputFilename;

      //register a task...
      //catching exceptions...
      try {
        // Check whether this file should be skipped again
        if (noClobber && new File(finalOutputFilename).exists()) {
          logger.err("Skipping " + file.getName() + ": output file " + finalOutputFilename + " as it already exists.  Don't use the noClobber option to override this.");
          synchronized (totalSkipped) {
            totalSkipped.incValue(1);
          }
          return;
        }

        logger.info("Processing file " + file.getAbsolutePath() + " ... writing to " + finalOutputFilename);

        //--Process File
        Annotation annotation = null;
        if (file.getAbsolutePath().endsWith(".ser.gz")) {
          // maybe they want to continue processing a partially processed annotation
          try {
            // Create serializers
            if (inputSerializerClass != null) {
              AnnotationSerializer inputSerializer = loadSerializer(inputSerializerClass, inputSerializerName, properties);
              InputStream is = new BufferedInputStream(new FileInputStream(file));
              Pair<Annotation, InputStream> pair = inputSerializer.read(is);
              pair.second.close();
              annotation = pair.first;
              IOUtils.closeIgnoringExceptions(is);
            } else {
              annotation = IOUtils.readObjectFromFile(file);
            }
          } catch (IOException e) {
            // guess that's not what they wanted
            // We hide IOExceptions because ones such as file not
            // found will be thrown again in a moment.  Note that
            // we are intentionally letting class cast exceptions
            // and class not found exceptions go through.
          } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
          }
        }

        //(read file)
        if (annotation == null) {
          String encoding = properties.getProperty("encoding", "UTF-8");
          String text = IOUtils.slurpFile(file.getAbsoluteFile(), encoding);
          annotation = new Annotation(text);
          annotation.set(CoreAnnotations.DocIDAnnotation.class, file.getName());
        }

        Timing timing = new Timing();
        annotate.accept(annotation, finishedAnnotation -> {
          timing.done(logger, "Annotating file " + file.getAbsoluteFile());
          Throwable ex = finishedAnnotation.get(CoreAnnotations.ExceptionAnnotation.class);
          if (ex == null) {
            try {
              //--Output File
              OutputStream fos = new BufferedOutputStream(new FileOutputStream(finalOutputFilename));
              print.accept(finishedAnnotation, fos);
              fos.close();
            } catch(IOException e) {
              throw new RuntimeIOException(e);
            }

            synchronized (totalProcessed) {
              totalProcessed.incValue(1);
              if (totalProcessed.intValue() % 1000 == 0) {
                logger.info("Processed " + totalProcessed + " documents");
              }
              // check we've processed or errored on every file, handle tasks to run after last document
              if ((totalProcessed.intValue() + totalErrorAnnotating.intValue()) == files.size()) {
                // clear pool if necessary
                if (clearPool)
                  GLOBAL_ANNOTATOR_CACHE.clear();
                // print out timing info
                if (TIME && pipeline.isPresent() && tim.isPresent())
                  logTimingInfo(pipeline.get(), tim.get());
              }
            }
          } else if (continueOnAnnotateError) {
            // Error annotating but still wanna continue
            // (maybe in the middle of long job and maybe next one will be okay)
            logger.err("Error annotating " + file.getAbsoluteFile() + ": " + ex);
            synchronized (totalErrorAnnotating) {
              totalErrorAnnotating.incValue(1);
              // check we've processed or errored on every file, handle tasks to run after last document
              if ((totalProcessed.intValue() + totalErrorAnnotating.intValue()) == files.size()) {
                // clear pool if necessary
                if (clearPool)
                  GLOBAL_ANNOTATOR_CACHE.clear();
                // print out timing info
                if (TIME && pipeline.isPresent() && tim.isPresent())
                  logTimingInfo(pipeline.get(), tim.get());
              }
            }

          } else {
            // if stopping due to error, make sure to clear the pool
            if (clearPool) {
              GLOBAL_ANNOTATOR_CACHE.clear();
            }
            throw new RuntimeException("Error annotating " + file.getAbsoluteFile(), ex);
          }
        });

      } catch (IOException e) {
        throw new RuntimeIOException(e);
      }

    }
  }

  public void processFiles(final Collection<File> files, int numThreads, boolean clearPool, Optional<Timing> tim) throws IOException {
    processFiles(null, files, numThreads, clearPool, tim);
  }

  public void processFiles(final Collection<File> files, boolean clearPool, Optional<Timing> tim) throws IOException {
    processFiles(files, 1, clearPool, tim);
  }

  public void run() throws IOException {
    run(false);
  }

  public void run(boolean clearPool) throws IOException {
    Timing tim = new Timing();
    StanfordRedwoodConfiguration.minimalSetup();

    // multithreading thread count
    String numThreadsString = this.properties.getProperty("threads");
    int numThreads = 1;
    try {
      if (numThreadsString != null) {
        numThreads = Integer.parseInt(numThreadsString);
      }
    } catch (NumberFormatException e) {
      logger.err("-threads [number]: was not given a valid number: " + numThreadsString);
    }

    // blank line after all the loading statements to make output more readable
    logger.info("");

    //
    // Process one file or a directory of files
    //
    if (properties.containsKey("file") || properties.containsKey("textFile")) {
      String fileName = properties.getProperty("file");
      if (fileName == null) {
        fileName = properties.getProperty("textFile");
      }
      Collection<File> files = new FileSequentialCollection(new File(fileName), properties.getProperty("extension"), true);
      this.processFiles(null, files, numThreads, clearPool, Optional.of(tim));
    }

    //
    // Process a list of files
    //
    else if (properties.containsKey("filelist")) {
      String fileName = properties.getProperty("filelist");
      Collection<File> inputFiles = readFileList(fileName);
      Collection<File> files = new ArrayList<>(inputFiles.size());
      for (File file : inputFiles) {
        if (file.isDirectory()) {
          files.addAll(new FileSequentialCollection(new File(fileName), properties.getProperty("extension"), true));
        } else {
          files.add(file);
        }
      }
      this.processFiles(null, files, numThreads, clearPool, Optional.of(tim));
    }

    //
    // Run as a filter or the interactive shell depending on whether atached to console
    //
    else {
      this.shell();
    }

    // clear the pool if not running in multi-thread mode
    if (clearPool && numThreads == 1) {
      pool.clear();
    }
  }

  /**
   * This can be used just for testing or for command-line text processing.
   * This runs the pipeline you specify on the
   * text in the file that you specify and sends some results to stdout.
   * The current code in this main method assumes that each line of the file
   * is to be processed separately as a single sentence.
   * <p>
   * Example usage:<br>
   * java -mx6g edu.stanford.nlp.pipeline.StanfordCoreNLP properties
   *
   * @param args List of required properties
   * @throws java.io.IOException If IO problem
   */
  public static void main(String[] args) throws IOException {
    //
    // process the arguments
    //
    // Extract all the properties from the command line.
    // As well as command-line properties, the processor will search for the properties file in the classpath
    Properties props = new Properties();
    if (args.length > 0) {
      props = StringUtils.argsToProperties(args);
      String helpValue = props.getProperty("h", props.getProperty("help"));
      if (helpValue != null) {
        printHelp(System.err, helpValue);
        return;
      }
    }
    // Run the pipeline
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    if (PropertiesUtils.getBool(props, "memoryUsage", false)) {
      System.gc();
      System.gc();
      logger.info("Finished loading pipeline.  Current memory usage: " +
                  SystemUtils.getMemoryInUse() + "mb");
    }
    pipeline.run(true);
  }

}
