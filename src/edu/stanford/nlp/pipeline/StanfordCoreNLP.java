//
// StanfordCoreNLP -- a suite of NLP tools.
// Copyright (c) 2009-2011 The Board of Trustees of
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
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//

package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ie.NERClassifierCombiner;
import edu.stanford.nlp.ie.regexp.NumberSequenceClassifier;
import edu.stanford.nlp.ie.regexp.RegexNERSequenceClassifier;
import edu.stanford.nlp.io.FileSequentialCollection;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.trees.TreePrint;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;
import edu.stanford.nlp.util.logging.StanfordRedwoodConfiguration;

import java.io.*;
import java.util.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static edu.stanford.nlp.util.logging.Redwood.Util.*;

/**
 * This is a pipeline that takes in a string and returns various analyzed
 * linguistic forms.
 * The String is tokenized via a tokenizer (such as PTBTokenizerAnnotator), and
 * then other sequence model style annotation can be used to add things like
 * lemmas, POS tags, and named entities.  These are returned as a list of CoreLabels.
 * Other analysis components build and store parse trees, dependency graphs, etc.
 * <p>
 * This class is designed to apply multiple Annotators
 * to an Annotation.  The idea is that you first
 * build up the pipeline by adding Annotators, and then
 * you take the objects you wish to annotate and pass
 * them in and get in return a fully annotated object.
 * Please see the package level javadoc for sample usage
 * and a more complete description.
 * <p>
 * The main entry point for the API is StanfordCoreNLP.process()
 * <p>
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

public class StanfordCoreNLP extends AnnotationPipeline {

  enum OutputFormat { TEXT, XML, SERIALIZED }

  // other constants
  public static final String CUSTOM_ANNOTATOR_PREFIX = "customAnnotatorClass.";
  private static final String PROPS_SUFFIX = ".properties";
  public static final String NEWLINE_SPLITTER_PROPERTY = "ssplit.eolonly";

  public static final String DEFAULT_OUTPUT_FORMAT = isXMLOutputPresent() ? "xml" : "text";

  /** Formats the constituent parse trees for display */
  private TreePrint constituentTreePrinter;
  /** Formats the dependency parse trees for human-readable display */
  private TreePrint dependencyTreePrinter;

  /** Stores the overall number of words processed */
  private int numWords;

  /** Maintains the shared pool of annotators */
  private static AnnotatorPool pool = null;

  private Properties properties;


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
    construct(props, enforceRequirements);
  }

  /**
   * Constructs a pipeline with the properties read from this file, which must be found in the classpath
   * @param propsFileNamePrefix
   */
  public StanfordCoreNLP(String propsFileNamePrefix) {
    this(propsFileNamePrefix, true);
  }

  public StanfordCoreNLP(String propsFileNamePrefix, boolean enforceRequirements) {
    Properties props = loadProperties(propsFileNamePrefix);
    if (props == null) {
      throw new RuntimeIOException("ERROR: cannot find properties file \"" + propsFileNamePrefix + "\" in the classpath!");
    }
    construct(props, enforceRequirements);
  }

  //
  // property-specific methods
  //

  private static String getRequiredProperty(Properties props, String name) {
    String val = props.getProperty(name);
    if (val == null) {
      System.err.println("Missing property \"" + name + "\"!");
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
    for (String name: validNames) {
      Properties props = loadProperties(name);
      if (props != null) return props;
    }
    throw new RuntimeException("ERROR: Could not find properties file in the classpath!");
  }

  private static Properties loadProperties(String name) {
    return loadProperties(name, Thread.currentThread().getContextClassLoader());
  }

  private static Properties loadProperties(String name, ClassLoader loader){
    if(name.endsWith (PROPS_SUFFIX)) name = name.substring(0, name.length () - PROPS_SUFFIX.length ());
    name = name.replace('.', '/');
    name += PROPS_SUFFIX;
    Properties result = null;

    // Returns null on lookup failures
    System.err.println("Searching for resource: " + name);
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

    return result;
  }

  /** Fetches the Properties object used to construct this Annotator */
  public Properties getProperties() { return properties; }

  public TreePrint getConstituentTreePrinter() { return constituentTreePrinter; }

  public TreePrint getDependencyTreePrinter() { return dependencyTreePrinter; }

  public double getBeamPrintingOption() {
    return PropertiesUtils.getDouble(properties, "printable.relation.beam", 0.0);
  }

  public String getEncoding() {
    return properties.getProperty("encoding", "UTF-8");
  }

  public static boolean isXMLOutputPresent() {
    try {
      Class clazz = Class.forName("edu.stanford.nlp.pipeline.XMLOutputter");
    } catch (ClassNotFoundException ex) {
      return false;
    } catch (NoClassDefFoundError ex) {
      return false;
    }
    return true;
  }

  //
  // AnnotatorPool construction support
  //

  private void construct(Properties props, boolean enforceRequirements) {
    this.numWords = 0;
    this.constituentTreePrinter = new TreePrint("penn");
    this.dependencyTreePrinter = new TreePrint("typedDependenciesCollapsed");
    
    if (props == null) {
      // if undefined, find the properties file in the classpath
      props = loadPropertiesFromClasspath();
    } else if (props.getProperty("annotators") == null) {
      // this happens when some command line options are specified (e.g just "-filelist") but no properties file is.
      // we use the options that are given and let them override the default properties from the class path properties.
      Properties fromClassPath = loadPropertiesFromClasspath();
      fromClassPath.putAll(props);
      props = fromClassPath;
    }
    this.properties = props;
    AnnotatorPool pool = getDefaultAnnotatorPool(props);

    // now construct the annotators from the given properties in the given order
    List<String> annoNames = Arrays.asList(getRequiredProperty(props, "annotators").split("[, \t]+"));
    Set<String> alreadyAddedAnnoNames = Generics.newHashSet();
    Set<Requirement> requirementsSatisfied = Generics.newHashSet();
    for (String name : annoNames) {
      name = name.trim();
      if (name.isEmpty()) { continue; }
      System.err.println("Adding annotator " + name);

      Annotator an = pool.get(name);
      this.addAnnotator(an);

      if (enforceRequirements) {
        Set<Requirement> allRequirements = an.requires();
        for (Requirement requirement : allRequirements) {
          if (!requirementsSatisfied.contains(requirement)) {
            String fmt = "annotator \"%s\" requires annotator \"%s\"";
            throw new IllegalArgumentException(String.format(fmt, name, requirement));
          }
        }
        requirementsSatisfied.addAll(an.requirementsSatisfied());
      }

      // the NFL domain requires several post-processing rules after
      // tokenization.  add these transparently if the NFL annotator
      // is required
      if (name.equals(STANFORD_TOKENIZE) &&
          annoNames.contains(STANFORD_NFL) &&
          !annoNames.contains(STANFORD_NFL_TOKENIZE)) {
        Annotator pp = pool.get(STANFORD_NFL_TOKENIZE);
        this.addAnnotator(pp);
      }

      alreadyAddedAnnoNames.add(name);
    }

    // Sanity check
    if (! alreadyAddedAnnoNames.contains(STANFORD_SSPLIT)) {
      System.setProperty(NEWLINE_SPLITTER_PROPERTY, "false");
    }
  }

  /**
   * Call this if you are no longer using StanfordCoreNLP and want to
   * release the memory associated with the annotators.
   */
  public static synchronized void clearAnnotatorPool() {
    pool = null;
  }

  private static synchronized AnnotatorPool getDefaultAnnotatorPool(final Properties inputProps) {
    // if the pool already exists reuse!
    if(pool == null) {
      // first time we get here
      pool = new AnnotatorPool();
    }

    //
    // tokenizer: breaks text into a sequence of tokens
    // this is required for all following annotators!
    //
    pool.register(STANFORD_TOKENIZE, new AnnotatorFactory(inputProps) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        if (Boolean.valueOf(properties.getProperty("tokenize.whitespace",
                          "false"))) {
          return new WhitespaceTokenizerAnnotator(properties);
        } else {
          String options = properties.getProperty("tokenize.options",
                  PTBTokenizerAnnotator.DEFAULT_OPTIONS);
          boolean keepNewline =
                  Boolean.valueOf(properties.getProperty(NEWLINE_SPLITTER_PROPERTY,
                          "false"));
          // If the user specifies "tokenizeNLs=false" in tokenize.options, then this default will
          // be overridden.
          if (keepNewline) {
            options = "tokenizeNLs," + options;
          }
          return new PTBTokenizerAnnotator(false, options);
        }
      }

      @Override
      public String signature() {
        // keep track of all relevant properties for this annotator here!
        StringBuilder os = new StringBuilder();
        os.append("tokenize.whitespace:" +
                properties.getProperty("tokenize.whitespace", "false"));
        if (Boolean.valueOf(properties.getProperty("tokenize.whitespace",
                "false"))) {
          os.append(WhitespaceTokenizerAnnotator.EOL_PROPERTY + ":" +
                  properties.getProperty(WhitespaceTokenizerAnnotator.EOL_PROPERTY,
                          "false"));
          os.append(StanfordCoreNLP.NEWLINE_SPLITTER_PROPERTY + ":" +
                  properties.getProperty(StanfordCoreNLP.NEWLINE_SPLITTER_PROPERTY,
                          "false"));
          return os.toString();
        } else {
          os.append(NEWLINE_SPLITTER_PROPERTY + ":" +
                  Boolean.valueOf(properties.getProperty(NEWLINE_SPLITTER_PROPERTY,
                          "false")));
        }
        return os.toString();
      }
    });

    pool.register(STANFORD_CLEAN_XML, new AnnotatorFactory(inputProps) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        String xmlTags =
          properties.getProperty("clean.xmltags",
                            CleanXmlAnnotator.DEFAULT_XML_TAGS);
        String sentenceEndingTags =
          properties.getProperty("clean.sentenceendingtags",
                            CleanXmlAnnotator.DEFAULT_SENTENCE_ENDERS);
        String allowFlawedString = properties.getProperty("clean.allowflawedxml");
        boolean allowFlawed = CleanXmlAnnotator.DEFAULT_ALLOW_FLAWS;
        if (allowFlawedString != null)
          allowFlawed = Boolean.valueOf(allowFlawedString);
        String dateTags =
          properties.getProperty("clean.datetags",
                            CleanXmlAnnotator.DEFAULT_DATE_TAGS);
        return new CleanXmlAnnotator(xmlTags,
            sentenceEndingTags,
            dateTags,
            allowFlawed);
      }

      @Override
      public String signature() {
        // keep track of all relevant properties for this annotator here!
        return "clean.xmltags:" +
                properties.getProperty("clean.xmltags",
                CleanXmlAnnotator.DEFAULT_XML_TAGS) +
                "clean.sentenceendingtags:" +
                properties.getProperty("clean.sentenceendingtags",
                CleanXmlAnnotator.DEFAULT_SENTENCE_ENDERS) +
                "clean.allowflawedxml:" +
                properties.getProperty("clean.allowflawedxml", "") +
                "clean.datetags:" +
                properties.getProperty("clean.datetags",
                CleanXmlAnnotator.DEFAULT_DATE_TAGS);
      }
    });

    //
    // sentence splitter: splits the above sequence of tokens into
    // sentences.  This is required when processing entire documents or
    // text consisting of multiple sentences
    //
    pool.register(STANFORD_SSPLIT, new AnnotatorFactory(inputProps) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        boolean nlSplitting = Boolean.valueOf(properties.getProperty(NEWLINE_SPLITTER_PROPERTY, "false"));
        if (nlSplitting) {
          boolean whitespaceTokenization = Boolean.valueOf(properties.getProperty("tokenize.whitespace", "false"));
          WordsToSentencesAnnotator wts;
          if (whitespaceTokenization) {
            if (System.getProperty("line.separator").equals("\n")) {
              wts = WordsToSentencesAnnotator.newlineSplitter(false, "\n");
            } else {
              // throw "\n" in just in case files use that instead of
              // the system separator
              wts = WordsToSentencesAnnotator.newlineSplitter(false, System.getProperty("line.separator"), "\n");
            }
          } else {
            wts = WordsToSentencesAnnotator.newlineSplitter(false, PTBTokenizer.getNewlineToken());
          }

          wts.setCountLineNumbers(true);

          return wts;
        } else {
          WordsToSentencesAnnotator wts;
          String boundaryTokenRegex = properties.getProperty("ssplit.boundaryTokenRegex");
          if (boundaryTokenRegex != null) {
            wts = new WordsToSentencesAnnotator(false, boundaryTokenRegex);
          } else {
            wts = new WordsToSentencesAnnotator();
          }

          // regular boundaries
          String bounds = properties.getProperty("ssplit.boundariesToDiscard");
          if (bounds != null){
            String [] toks = bounds.split(",");
            // for(int i = 0; i < toks.length; i ++)
            //   System.err.println("BOUNDARY: " + toks[i]);
            wts.setSentenceBoundaryToDiscard(Generics.newHashSet (Arrays.asList(toks)));
          }

          // HTML boundaries
          bounds = properties.getProperty("ssplit.htmlBoundariesToDiscard");
          if (bounds != null){
            String [] toks = bounds.split(",");
            wts.addHtmlSentenceBoundaryToDiscard(Generics.newHashSet (Arrays.asList(toks)));
          }

          // Treat as one sentence
          String isOneSentence = properties.getProperty("ssplit.isOneSentence");
          if (isOneSentence != null){
            wts.setOneSentence(Boolean.parseBoolean(isOneSentence));
          }

          return wts;
        }
      }

      @Override
      public String signature() {
        // keep track of all relevant properties for this annotator here!
        StringBuilder os = new StringBuilder();
        os.append(NEWLINE_SPLITTER_PROPERTY + ":" +
                properties.getProperty(NEWLINE_SPLITTER_PROPERTY, "false"));
        if(Boolean.valueOf(properties.getProperty(NEWLINE_SPLITTER_PROPERTY,
                "false"))) {
          os.append("tokenize.whitespace:" +
                  properties.getProperty("tokenize.whitespace", "false"));
        } else {
          os.append("ssplit.boundariesToDiscard:" +
                  properties.getProperty("ssplit.boundariesToDiscard", ""));
          os.append("ssplit.htmlBoundariesToDiscard:" +
                  properties.getProperty("ssplit.htmlBoundariesToDiscard", ""));
          os.append("ssplit.isOneSentence:" +
                  properties.getProperty("ssplit.isOneSentence", ""));
        }
        return os.toString();
      }
    });

    //
    // POS tagger
    //
    pool.register(STANFORD_POS, new AnnotatorFactory(inputProps) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        try {
          return new POSTaggerAnnotator("pos", properties);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public String signature() {
        // keep track of all relevant properties for this annotator here!
        return ("pos.maxlen:" + properties.getProperty("pos.maxlen", "") +
                "pos.model:" + properties.getProperty("pos.model", DefaultPaths.DEFAULT_POS_MODEL) +
                "pos.nthreads:" + properties.getProperty("pos.nthreads", properties.getProperty("nthreads", "")));
      }
    });

    //
    // Lemmatizer
    //
    pool.register(STANFORD_LEMMA, new AnnotatorFactory(inputProps) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        return new MorphaAnnotator(false);
      }

      @Override
      public String signature() {
        // keep track of all relevant properties for this annotator here!
        // nothing for this one
        return "";
      }
    });

    //
    // NER
    //
    pool.register(STANFORD_NER, new AnnotatorFactory(inputProps) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        List<String> models = new ArrayList<String>();
        String modelNames = properties.getProperty("ner.model");
        if (modelNames == null) {
          modelNames = DefaultPaths.DEFAULT_NER_THREECLASS_MODEL + "," + DefaultPaths.DEFAULT_NER_MUC_MODEL + "," + DefaultPaths.DEFAULT_NER_CONLL_MODEL;
        }
        if (modelNames.length() > 0) {
          models.addAll(Arrays.asList(modelNames.split(",")));
        }
        if (models.isEmpty()) {
          // Allow for no real NER model - can just use numeric classifiers or SUTime
          // Will have to explicitly unset ner.model.3class, ner.model.7class, ner.model.MISCclass
          // So unlikely that people got here by accident
          System.err.println("WARNING: no NER models specified");
        }
        NERClassifierCombiner nerCombiner;
        try {
          boolean applyNumericClassifiers =
            PropertiesUtils.getBool(properties,
                NERClassifierCombiner.APPLY_NUMERIC_CLASSIFIERS_PROPERTY,
                NERClassifierCombiner.APPLY_NUMERIC_CLASSIFIERS_DEFAULT);
          boolean useSUTime =
            PropertiesUtils.getBool(properties,
                NumberSequenceClassifier.USE_SUTIME_PROPERTY,
                NumberSequenceClassifier.USE_SUTIME_DEFAULT);
          nerCombiner = new NERClassifierCombiner(applyNumericClassifiers,
                useSUTime, properties,
                models.toArray(new String[models.size()]));
        } catch (FileNotFoundException e) {
          throw new RuntimeIOException(e);
        }
        return new NERCombinerAnnotator(nerCombiner, false);
      }

      @Override
      public String signature() {
        // keep track of all relevant properties for this annotator here!
        return "ner.model:" +
                properties.getProperty("ner.model", "") +
                "ner.model.3class:" +
                properties.getProperty("ner.model.3class",
                        DefaultPaths.DEFAULT_NER_THREECLASS_MODEL) +
                "ner.model.7class:" +
                properties.getProperty("ner.model.7class",
                        DefaultPaths.DEFAULT_NER_MUC_MODEL) +
                "ner.model.MISCclass:" +
                properties.getProperty("ner.model.MISCclass",
                        DefaultPaths.DEFAULT_NER_CONLL_MODEL) +
                NERClassifierCombiner.APPLY_NUMERIC_CLASSIFIERS_PROPERTY + ":" +
                properties.getProperty(NERClassifierCombiner.APPLY_NUMERIC_CLASSIFIERS_PROPERTY,
                        Boolean.toString(NERClassifierCombiner.APPLY_NUMERIC_CLASSIFIERS_DEFAULT)) +
                NumberSequenceClassifier.USE_SUTIME_PROPERTY + ":" +
                properties.getProperty(NumberSequenceClassifier.USE_SUTIME_PROPERTY,
                        Boolean.toString(NumberSequenceClassifier.USE_SUTIME_DEFAULT));
      }
    });

    //
    // Regex NER
    //
    pool.register(STANFORD_REGEXNER, new AnnotatorFactory(inputProps) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        String mapping = properties.getProperty("regexner.mapping", DefaultPaths.DEFAULT_REGEXNER_RULES);
        String ignoreCase = properties.getProperty("regexner.ignorecase", "false");
        String validPosPattern = properties.getProperty("regexner.validpospattern", RegexNERSequenceClassifier.DEFAULT_VALID_POS);
        return new RegexNERAnnotator(mapping, Boolean.valueOf(ignoreCase), validPosPattern);
      }

      @Override
      public String signature() {
        // keep track of all relevant properties for this annotator here!
        return "regexner.mapping:" +
                properties.getProperty("regexner.mapping",
                        DefaultPaths.DEFAULT_REGEXNER_RULES) +
                "regexner.ignorecase:" +
                properties.getProperty("regexner.ignorecase",
                        "false") +
                "regexner.validpospattern:" +
                properties.getProperty("regexner.validpospattern",
                        RegexNERSequenceClassifier.DEFAULT_VALID_POS);
      }
    });

    //
    // Gender Annotator
    //
    pool.register(STANFORD_GENDER, new AnnotatorFactory(inputProps) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        return new GenderAnnotator(false, properties.getProperty("gender.firstnames", DefaultPaths.DEFAULT_GENDER_FIRST_NAMES));
      }

      @Override
      public String signature() {
        // keep track of all relevant properties for this annotator here!
        return "gender.firstnames:" +
                properties.getProperty("gender.firstnames",
                        DefaultPaths.DEFAULT_GENDER_FIRST_NAMES);
      }
    });


    //
    // True caser
    //
    pool.register(STANFORD_TRUECASE, new AnnotatorFactory(inputProps) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        String model = properties.getProperty("truecase.model", DefaultPaths.DEFAULT_TRUECASE_MODEL);
        String bias = properties.getProperty("truecase.bias", TrueCaseAnnotator.DEFAULT_MODEL_BIAS);
        String mixed = properties.getProperty("truecase.mixedcasefile", DefaultPaths.DEFAULT_TRUECASE_DISAMBIGUATION_LIST);
        return new TrueCaseAnnotator(model, bias, mixed, false);
      }

      @Override
      public String signature() {
        // keep track of all relevant properties for this annotator here!
        return "truecase.model:" +
                properties.getProperty("truecase.model",
                        DefaultPaths.DEFAULT_TRUECASE_MODEL) +
                "truecase.bias:" +
                properties.getProperty("truecase.bias",
                        TrueCaseAnnotator.DEFAULT_MODEL_BIAS) +
                "truecase.mixedcasefile:" +
                properties.getProperty("truecase.mixedcasefile",
                        DefaultPaths.DEFAULT_TRUECASE_DISAMBIGUATION_LIST);
      }
    });

    //
    // Post-processing tokenization rules for the NFL domain
    //
    pool.register(STANFORD_NFL_TOKENIZE, new AnnotatorFactory(inputProps) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        final String className =
          "edu.stanford.nlp.pipeline.NFLTokenizerAnnotator";
        return ReflectionLoading.loadByReflection(className);
      }

      @Override
      public String signature() {
        // keep track of all relevant properties for this annotator here!
        // no used props for this one
        return "";
      }
    });

    //
    // Entity and relation extraction for the NFL domain
    //
    pool.register(STANFORD_NFL, new AnnotatorFactory(inputProps) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        // these paths now extracted inside c'tor
        // String gazetteer = properties.getProperty("nfl.gazetteer", DefaultPaths.DEFAULT_NFL_GAZETTEER);
        // String entityModel = properties.getProperty("nfl.entity.model", DefaultPaths.DEFAULT_NFL_ENTITY_MODEL);
        // String relationModel = properties.getProperty("nfl.relation.model", DefaultPaths.DEFAULT_NFL_RELATION_MODEL);
        final String className = "edu.stanford.nlp.pipeline.NFLAnnotator";
        return ReflectionLoading.loadByReflection(className, properties);
      }

      @Override
      public String signature() {
        // keep track of all relevant properties for this annotator here!
        return "nfl.verbose:" +
                properties.getProperty("nfl.verbose",
                        "false") +
                "nfl.relations.use.max.recall:" +
                properties.getProperty("nfl.relations.use.max.recall",
                        "false") +
                "nfl.relations.use.model.merging:" +
                properties.getProperty("nfl.relations.use.model.merging",
                        "false") +
                "nfl.relations.use.basic.inference:" +
                properties.getProperty("nfl.relations.use.basic.inference",
                        "true") +
                "nfl.gazetteer:" +
                properties.getProperty("nfl.gazetteer",
                        DefaultPaths.DEFAULT_NFL_GAZETTEER) +
                "nfl.entity.model:" +
                properties.getProperty("nfl.entity.model",
                        DefaultPaths.DEFAULT_NFL_ENTITY_MODEL) +
                "nfl.relation.model:" +
                properties.getProperty("nfl.relation.model",
                        DefaultPaths.DEFAULT_NFL_RELATION_MODEL);
      }
    });

    //
    // Parser
    //
    pool.register(STANFORD_PARSE, new AnnotatorFactory(inputProps) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        String parserType = properties.getProperty("parse.type", "stanford");
        String maxLenStr = properties.getProperty("parse.maxlen");

        if (parserType.equalsIgnoreCase("stanford")) {
          ParserAnnotator anno = new ParserAnnotator("parse", properties);
          return anno;
        } else if (parserType.equalsIgnoreCase("charniak")) {
          String model = properties.getProperty("parse.model");
          String parserExecutable = properties.getProperty("parse.executable");
          if (model == null || parserExecutable == null) {
            throw new RuntimeException("Both parse.model and parse.executable properties must be specified if parse.type=charniak");
          }
          int maxLen = 399;
          if (maxLenStr != null) {
            maxLen = Integer.parseInt(maxLenStr);
          }

          CharniakParserAnnotator anno = new CharniakParserAnnotator(model, parserExecutable, false, maxLen);

          return anno;
        } else {
          throw new RuntimeException("Unknown parser type: " + parserType + " (currently supported: stanford and charniak)");
        }
      }

      @Override
      public String signature() {
        // keep track of all relevant properties for this annotator here!
        String type = properties.getProperty("parse.type", "stanford");
        if(type.equalsIgnoreCase("stanford")){
          return ParserAnnotator.signature("parser", properties);
        } else if(type.equalsIgnoreCase("charniak")) {
          return "parse.model:" +
                  properties.getProperty("parse.model", "") +
                  "parse.executable:" +
                  properties.getProperty("parse.executable", "") +
                  "parse.maxlen:" +
                  properties.getProperty("parse.maxlen", "");
        } else {
          throw new RuntimeException("Unknown parser type: " + type +
                  " (currently supported: stanford and charniak)");
        }
      }
    });

    //
    // Coreference resolution
    //
    pool.register(STANFORD_DETERMINISTIC_COREF, new AnnotatorFactory(inputProps) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        return new DeterministicCorefAnnotator(properties);
      }

      @Override
      public String signature() {
        // keep track of all relevant properties for this annotator here!
        return DeterministicCorefAnnotator.signature(properties);
      }
    });

    // add annotators loaded via reflection from classnames specified
    // in the properties
    for (Object propertyKey : inputProps.keySet()) {
      if (!(propertyKey instanceof String))
        continue; // should this be an Exception?
      String property = (String) propertyKey;
      if (property.startsWith(CUSTOM_ANNOTATOR_PREFIX)) {
        final String customName =
          property.substring(CUSTOM_ANNOTATOR_PREFIX.length());
        final String customClassName = inputProps.getProperty(property);
        System.err.println("Registering annotator " + customName +
            " with class " + customClassName);
        pool.register(customName, new AnnotatorFactory(inputProps) {
          private static final long serialVersionUID = 1L;
          private final String name = customName;
          private final String className = customClassName;
          @Override
          public Annotator create() {
            return ReflectionLoading.loadByReflection(className, name,
                                                      properties);
          }
          @Override
          public String signature() {
            // keep track of all relevant properties for this annotator here!
            // since we don't know what props they need, let's copy all
            // TODO: can we do better here? maybe signature() should be a method in the Annotator?
            StringBuilder os = new StringBuilder();
            for(Object key: properties.keySet()) {
              String skey = (String) key;
              os.append(skey + ":" + properties.getProperty(skey));
            }
            return os.toString();
          }
        });
      }
    }


    //
    // add more annotators here!
    //
    return pool;
  }

  public static synchronized Annotator getExistingAnnotator(String name) {
    if(pool == null){
      System.err.println("ERROR: attempted to fetch annotator \"" + name + "\" before the annotator pool was created!");
      return null;
    }
    try {
      Annotator a =  pool.get(name);
      return a;
    } catch(IllegalArgumentException e) {
      System.err.println("ERROR: attempted to fetch annotator \"" + name + "\" but the annotator pool does not store any such type!");
      return null;
    }
  }

  @Override
  public void annotate(Annotation annotation) {
    super.annotate(annotation);
    List<CoreLabel> words = annotation.get(CoreAnnotations.TokensAnnotation.class);
    if (words != null) {
      numWords += words.size();
    }
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

  //
  // output and formatting methods (including XML-specific methods)
  //

  /**
   * Displays the output of all annotators in a format easily readable by people.
   * @param annotation Contains the output of all annotators
   * @param os The output stream
   */
  public void prettyPrint(Annotation annotation, OutputStream os) {
    TextOutputter.prettyPrint(annotation, os, this);
  }

  /**
   * Displays the output of all annotators in a format easily readable by people.
   * @param annotation Contains the output of all annotators
   * @param os The output stream
   */
  public void prettyPrint(Annotation annotation, PrintWriter os) {
    TextOutputter.prettyPrint(annotation, os, this);
  }

  /**
   * Wrapper around xmlPrint(Annotation, OutputStream).
   * Added for backward compatibility.
   * @param annotation
   * @param w The Writer to send the output to
   * @throws IOException
   */
  public void xmlPrint(Annotation annotation, Writer w) throws IOException {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    xmlPrint(annotation, os); // this builds it as the encoding specified in the properties
    w.write(new String(os.toByteArray(), getEncoding()));
    w.flush();
  }

  /**
   * Displays the output of all annotators in XML format.
   * @param annotation Contains the output of all annotators
   * @param os The output stream
   * @throws IOException
   */
  public void xmlPrint(Annotation annotation, OutputStream os) throws IOException {
    try {
      Class clazz = Class.forName("edu.stanford.nlp.pipeline.XMLOutputter");
      Method method = clazz.getMethod("xmlPrint", Annotation.class, OutputStream.class, StanfordCoreNLP.class);
      method.invoke(null, annotation, os, this);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  //
  // runtime, shell-specific, and help menu methods
  //

  /**
   * Prints the list of properties required to run the pipeline
   * @param os PrintStream to print usage to
   * @param helpTopic a topic to print help about (or null for general options)
   */
  private static void printHelp(PrintStream os, String helpTopic) {
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
    // TODO some annotators (ssplit, regexner, gender, some parser
    // options, dcoref?) are not documented
    os.println("The following properties can be defined:");
    os.println("(if -props or -annotators is not passed in, default properties will be loaded via the classpath)");
    os.println("\t\"props\" - path to file with configuration properties");
    os.println("\t\"annotators\" - comma separated list of annotators");
    os.println("\tThe following annotators are supported: cleanxml, tokenize, ssplit, pos, lemma, ner, truecase, parse, coref, dcoref, nfl");

    os.println("\n\tIf annotator \"tokenize\" is defined:");
    os.println("\t\"tokenize.options\" - PTBTokenizer options (see edu.stanford.nlp.process.PTBTokenizer for details)");
    os.println("\t\"tokenize.whitespace\" - If true, just use whitespace tokenization");

    os.println("\n\tIf annotator \"cleanxml\" is defined:");
    os.println("\t\"clean.xmltags\" - regex of tags to extract text from");
    os.println("\t\"clean.sentenceendingtags\" - regex of tags which mark sentence endings");
    os.println("\t\"clean.allowflawedxml\" - if set to false, don't complain about XML errors");

    os.println("\n\tIf annotator \"pos\" is defined:");
    os.println("\t\"pos.maxlen\" - maximum length of sentence to POS tag");
    os.println("\t\"pos.model\" - path towards the POS tagger model");

    os.println("\n\tIf annotator \"ner\" is defined:");
    os.println("\t\"ner.model.3class\" - path towards the three-class NER model");
    os.println("\t\"ner.model.7class\" - path towards the seven-class NER model");
    os.println("\t\"ner.model.MISCclass\" - path towards the NER model with a MISC class");

    os.println("\n\tIf annotator \"truecase\" is defined:");
    os.println("\t\"truecase.model\" - path towards the true-casing model; default: " + DefaultPaths.DEFAULT_TRUECASE_MODEL);
    os.println("\t\"truecase.bias\" - class bias of the true case model; default: " + TrueCaseAnnotator.DEFAULT_MODEL_BIAS);
    os.println("\t\"truecase.mixedcasefile\" - path towards the mixed case file; default: " + DefaultPaths.DEFAULT_TRUECASE_DISAMBIGUATION_LIST);

    os.println("\n\tIf annotator \"nfl\" is defined:");
    os.println("\t\"nfl.gazetteer\" - path towards the gazetteer for the NFL domain");
    os.println("\t\"nfl.relation.model\" - path towards the NFL relation extraction model");

    os.println("\n\tIf annotator \"parse\" is defined:");
    os.println("\t\"parse.model\" - path towards the PCFG parser model");

    /* XXX: unstable, do not use for now
    os.println("\n\tIf annotator \"srl\" is defined:");
    os.println("\t\"srl.verb.args\" - path to the file listing verbs and their core arguments (\"verbs.core_args\")");
    os.println("\t\"srl.model.id\" - path prefix for the role identification model (adds \".model.gz\" and \".fe\" to this prefix)");
    os.println("\t\"srl.model.cls\" - path prefix for the role classification model (adds \".model.gz\" and \".fe\" to this prefix)");
    os.println("\t\"srl.model.jic\" - path to the directory containing the joint model's \"model.gz\", \"fe\" and \"je\" files");
    os.println("\t                  (if not specified, the joint model will not be used)");
    */

    os.println("\nCommand line properties:");
    os.println("\t\"file\" - run the pipeline on the content of this file, or on the content of the files in this directory");
    os.println("\t         XML output is generated for every input file \"file\" as file.xml");
    os.println("\t\"extension\" - if -file used with a directory, process only the files with this extension");
    os.println("\t\"filelist\" - run the pipeline on the list of files given in this file");
    os.println("\t             output is generated for every input file as file.outputExtension");
    os.println("\t\"outputDirectory\" - where to put output (defaults to the current directory)");
    os.println("\t\"outputExtension\" - extension to use for the output file (defaults to \".xml\" for XML, \".ser.gz\" for serialized).  Don't forget the dot!");
    os.println("\t\"outputFormat\" - \"xml\" to output XML (default), \"serialized\" to output serialized Java objects, \"text\" to output text");
    os.println("\t\"replaceExtension\" - flag to chop off the last extension before adding outputExtension to file");
    os.println("\t\"noClobber\" - don't automatically override (clobber) output files that already exist");
		os.println("\t\"threads\" - multithread on this number of threads");
    os.println("\nIf none of the above are present, run the pipeline in an interactive shell (default properties will be loaded from the classpath).");
    os.println("The shell accepts input from stdin and displays the output at stdout.");

    os.println("\nRun with -help [topic] for more help on a specific topic.");
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
   * Runs an interactive shell where input text is processed with the given pipeline.
   *
   * @param pipeline The pipeline to be used
   * @throws IOException If IO problem with stdin
   */
  private static void shell(StanfordCoreNLP pipeline) throws IOException {
    String encoding = pipeline.getEncoding();
    BufferedReader r = new BufferedReader(IOUtils.encodedInputStreamReader(System.in, encoding));
    System.err.println("Entering interactive shell. Type q RETURN or EOF to quit.");
    while (true) {
      System.err.print("NLP> ");
      String line = r.readLine();
      if (line == null || line.equalsIgnoreCase("q")) {
        break;
      }
      if (line.length() > 0) {
        Annotation anno = pipeline.process(line);
        pipeline.prettyPrint(anno, System.out);
      }
    }
  }

  private static Collection<File> readFileList(String fileName) throws IOException {
    return ObjectBank.getLineIterator(fileName, new ObjectBank.PathToFileFunction());
  }



  public void processFiles(final Collection<File> files, int numThreads) throws IOException {
    List<Runnable> toRun = new LinkedList<Runnable>();
    //for each file...
    for (final File file : files) {
      //register a task...
      toRun.add(new Runnable(){
        //who's run() method is...
        @Override
        public void run(){
          //catching exceptions...
          try {
            //--Get Output File Info
            //(filename)
            String outputFilename = new File(properties.getProperty("outputDirectory", "."), file.getName()).getPath();
            if (properties.getProperty("replaceExtension") != null) {
              int lastDot = outputFilename.lastIndexOf('.');
              // for paths like "./zzz", lastDot will be 0
              if (lastDot > 0) {
                outputFilename = outputFilename.substring(0, lastDot);
              }
            }
            //(file info)
            OutputFormat outputFormat = OutputFormat.valueOf(properties.getProperty("outputFormat", DEFAULT_OUTPUT_FORMAT).toUpperCase());
            String defaultExtension;
            switch (outputFormat) {
            case XML: defaultExtension = ".xml"; break;
            case TEXT: defaultExtension = ".out"; break;
            case SERIALIZED: defaultExtension = ".ser.gz"; break;
            default: throw new IllegalArgumentException("Unknown output format " + outputFormat);
            }
            String extension = properties.getProperty("outputExtension", defaultExtension);
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
              err("Skipping " + file.getName() + ": output file " + outputFilename + " has the same filename as the input file -- assuming you don't actually want to do this.");
              return;
            }
            if (properties.getProperty("noClobber") != null && new File(outputFilename).exists()) {
              err("Skipping " + file.getName() + ": output file " + outputFilename + " as it already exists.  Don't use the noClobber option to override this.");
              return;
            }

            //--Process File
            Annotation annotation = null;
            if (file.getAbsolutePath().endsWith(".ser.gz")) {
              // maybe they want to continue processing a partially processed annotation
              try {
                annotation = IOUtils.readObjectFromFile(file);
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
              String encoding = getEncoding();
              String text = IOUtils.slurpFile(file, encoding);
              annotation = new Annotation(text);
            }

            annotate(annotation);

            forceTrack("Processing file " + file.getAbsolutePath() + " ... writing to " + outputFilename);

            //--Output File
            switch (outputFormat) {
            case XML: {
              OutputStream fos = new BufferedOutputStream(new FileOutputStream(outputFilename));
              xmlPrint(annotation, fos);
              fos.close();
              break;
            }
            case TEXT: {
              OutputStream fos = new BufferedOutputStream(new FileOutputStream(outputFilename));
              prettyPrint(annotation, fos);
              fos.close();
              break;
            }
            case SERIALIZED: {
              IOUtils.writeObjectToFile(annotation, outputFilename);
              break;
            }
            default:
              throw new IllegalArgumentException("Unknown output format " + outputFormat);
            }
            endTrack("Processing file " + file.getAbsolutePath() + " ... writing to " + outputFilename);
          } catch (IOException e) {
            throw new RuntimeIOException(e);
          }
        }
      });
    }

    //--Run Jobs
    if(numThreads == 1){
      for(Runnable r : toRun){ r.run(); }
    } else {
      Redwood.Util.threadAndRun("StanfordCoreNLP <" + numThreads + " threads>", toRun, numThreads);
    }
  }

  public void processFiles(final Collection<File> files) throws IOException {
    processFiles(files, 1);
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
   * @throws ClassNotFoundException If class loading problem
   */
  public static void main(String[] args) throws IOException, ClassNotFoundException {
    Timing tim = new Timing();
    StanfordRedwoodConfiguration.minimalSetup();

    //
    // process the arguments
    //
    // extract all the properties from the command line
    // if cmd line is empty, set the properties to null. The processor will search for the properties file in the classpath
    Properties props = null;
    if (args.length > 0) {
      props = StringUtils.argsToProperties(args);
      boolean hasH = props.containsKey("h");
      boolean hasHelp = props.containsKey("help");
      if (hasH || hasHelp) {
        String helpValue = hasH ? props.getProperty("h") : props.getProperty("help");
        printHelp(System.err, helpValue);
        return;
      }
    }
    // multithreading thread count
    String numThreadsString = (props == null) ? null : props.getProperty("threads");
    int numThreads = 1;
    try{
      if (numThreadsString != null) {
        numThreads = Integer.parseInt(numThreadsString);
      }
    } catch(NumberFormatException e) {
      err("-threads [number]: was not given a valid number: " + numThreadsString);
    }

    //
    // construct the pipeline
    //
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    props = pipeline.getProperties();
    long setupTime = tim.report();

    // blank line after all the loading statements to make output more readable
    log("");

    //
    // Process one file or a directory of files
    //
    if(props.containsKey("file")){
      String fileName = props.getProperty("file");
      Collection<File> files = new FileSequentialCollection(new File(fileName), props.getProperty("extension"), true);
      pipeline.processFiles(files, numThreads);
    }

    //
    // Process a list of files
    //
    else if(props.containsKey("filelist")){
      String fileName = props.getProperty("filelist");
      Collection<File> files = readFileList(fileName);
      pipeline.processFiles(files, numThreads);
    }

    //
    // Run the interactive shell
    //
    else {
      shell(pipeline);
    }

    if (TIME) {
      log();
      log(pipeline.timingInformation());
      log("Pipeline setup: " +
          Timing.toSecondsString(setupTime) + " sec.");
      log("Total time for StanfordCoreNLP pipeline: " +
          tim.toSecondsString() + " sec.");
    }
  }

}
