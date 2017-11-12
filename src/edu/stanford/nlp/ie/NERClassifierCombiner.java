package edu.stanford.nlp.ie;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.*;
import java.util.stream.Collectors;

import edu.stanford.nlp.ie.regexp.ChineseNumberSequenceClassifier;
import edu.stanford.nlp.ie.regexp.NumberSequenceClassifier;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.DefaultPaths;
import edu.stanford.nlp.sequences.DocumentReaderAndWriter;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Subclass of ClassifierCombiner that behaves like a NER, by copying
 * the AnswerAnnotation labels to NERAnnotation. Also, it can run additional
 * classifiers (NumberSequenceClassifier, QuantifiableEntityNormalizer, SUTime)
 * to recognize numeric and date/time entities, depending on flag settings.
 *
 * @author Mihai Surdeanu
 */
public class NERClassifierCombiner extends ClassifierCombiner<CoreLabel>  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(NERClassifierCombiner.class);

  private final boolean applyNumericClassifiers;
  public static final boolean APPLY_NUMERIC_CLASSIFIERS_DEFAULT = true;
  public static final String APPLY_NUMERIC_CLASSIFIERS_PROPERTY = "ner.applyNumericClassifiers";
  private static final String APPLY_NUMERIC_CLASSIFIERS_PROPERTY_BASE = "applyNumericClassifiers";
  public static final String APPLY_GAZETTE_PROPERTY = "ner.regex";
  public static final boolean APPLY_GAZETTE_DEFAULT = false;

  private final Language nerLanguage;
  public static final Language NER_LANGUAGE_DEFAULT = Language.ENGLISH;
  public static final String NER_LANGUAGE_PROPERTY = "ner.language";
  public static final String NER_LANGUAGE_PROPERTY_BASE = "language";

  public static final String USE_PRESET_NER_PROPERTY = "ner.usePresetNERTags";

  private final boolean useSUTime;

  public enum Language {
    ENGLISH("English"),
    CHINESE("Chinese");

    public String languageName;

    Language(String name) {
      this.languageName = name;
    }

    public static Language fromString(String name, Language defaultValue) {
      if(name != null) {
        for(Language l : Language.values()) {
          if(name.equalsIgnoreCase(l.languageName)) {
            return l;
          }
        }
      }
      return defaultValue;
    }
  }

  // todo [cdm 2015]: Could avoid constructing this if applyNumericClassifiers is false
  private final AbstractSequenceClassifier<CoreLabel> nsc;

  /**
   * A mapping from single words to the NER tag that they should be.
   */
  private final Map<String, String> gazetteMapping;

  public NERClassifierCombiner(Properties props)
    throws IOException
  {
    super(props);
    applyNumericClassifiers = PropertiesUtils.getBool(props, APPLY_NUMERIC_CLASSIFIERS_PROPERTY, APPLY_NUMERIC_CLASSIFIERS_DEFAULT);
    nerLanguage = Language.fromString(PropertiesUtils.getString(props, NER_LANGUAGE_PROPERTY, null), NER_LANGUAGE_DEFAULT);
    useSUTime = PropertiesUtils.getBool(props, NumberSequenceClassifier.USE_SUTIME_PROPERTY, NumberSequenceClassifier.USE_SUTIME_DEFAULT);
    nsc = new NumberSequenceClassifier(new Properties(), useSUTime, props);
    if (PropertiesUtils.getBool(props, NERClassifierCombiner.APPLY_GAZETTE_PROPERTY, NERClassifierCombiner.APPLY_GAZETTE_DEFAULT) ) {
      this.gazetteMapping = readRegexnerGazette(DefaultPaths.DEFAULT_NER_GAZETTE_MAPPING);
    } else {
      this.gazetteMapping = Collections.emptyMap();
    }
  }

  public NERClassifierCombiner(String... loadPaths)
    throws IOException
  {
    this(APPLY_NUMERIC_CLASSIFIERS_DEFAULT, NERClassifierCombiner.APPLY_GAZETTE_DEFAULT, NumberSequenceClassifier.USE_SUTIME_DEFAULT, loadPaths);
  }

  public NERClassifierCombiner(boolean applyNumericClassifiers,
                               boolean augmentRegexNER,
                               boolean useSUTime,
                               String... loadPaths)
    throws IOException
  {
    super(loadPaths);
    this.applyNumericClassifiers = applyNumericClassifiers;
    this.nerLanguage = NER_LANGUAGE_DEFAULT;
    this.useSUTime = useSUTime;
    this.nsc = new NumberSequenceClassifier(useSUTime);
    if (augmentRegexNER) {
      this.gazetteMapping = readRegexnerGazette(DefaultPaths.DEFAULT_NER_GAZETTE_MAPPING);
    } else {
      this.gazetteMapping = Collections.emptyMap();
    }
  }

  public NERClassifierCombiner(boolean applyNumericClassifiers,
                               Language nerLanguage,
                               boolean useSUTime,
                               boolean augmentRegexNER,
                               Properties nscProps,
                               String... loadPaths)
    throws IOException
  {
    // NOTE: nscProps may contains sutime props which will not be recognized by the SeqClassifierFlags
    super(nscProps, ClassifierCombiner.extractCombinationModeSafe(nscProps), loadPaths);
    this.applyNumericClassifiers = applyNumericClassifiers;
    this.nerLanguage = nerLanguage;
    this.useSUTime = useSUTime;
    // check for which language to use for number sequence classifier
    if (nerLanguage == Language.CHINESE) {
      this.nsc = new ChineseNumberSequenceClassifier(new Properties(), useSUTime, nscProps);
    } else {
      this.nsc = new NumberSequenceClassifier(new Properties(), useSUTime, nscProps);
    }
    if (augmentRegexNER) {
      this.gazetteMapping = readRegexnerGazette(DefaultPaths.DEFAULT_NER_GAZETTE_MAPPING);
    } else {
      this.gazetteMapping = Collections.emptyMap();
    }
  }

  @SafeVarargs
  public NERClassifierCombiner(AbstractSequenceClassifier<CoreLabel>... classifiers)
    throws IOException
  {
    this(APPLY_NUMERIC_CLASSIFIERS_DEFAULT, NumberSequenceClassifier.USE_SUTIME_DEFAULT, NERClassifierCombiner.APPLY_GAZETTE_DEFAULT, classifiers);
  }

  @SafeVarargs
  public NERClassifierCombiner(boolean applyNumericClassifiers,
                               boolean useSUTime,
                               boolean augmentRegexNER,
                               AbstractSequenceClassifier<CoreLabel>... classifiers)
    throws IOException
  {
    super(classifiers);
    this.applyNumericClassifiers = applyNumericClassifiers;
    this.nerLanguage = NER_LANGUAGE_DEFAULT;
    this.useSUTime = useSUTime;
    this.nsc = new NumberSequenceClassifier(useSUTime);
    if (augmentRegexNER) {
      this.gazetteMapping = readRegexnerGazette(DefaultPaths.DEFAULT_NER_GAZETTE_MAPPING);
    } else {
      this.gazetteMapping = Collections.emptyMap();
    }
  }

  // constructor which builds an NERClassifierCombiner from an ObjectInputStream
  public NERClassifierCombiner(ObjectInputStream ois, Properties props) throws IOException, ClassCastException, ClassNotFoundException {
    super(ois,props);
    // read the useSUTime from disk
    Boolean diskUseSUTime = ois.readBoolean();
    if (props.getProperty("ner.useSUTime") != null) {
      this.useSUTime = Boolean.parseBoolean(props.getProperty("ner.useSUTime"));
    } else {
      this.useSUTime = diskUseSUTime;
    }
    // read the applyNumericClassifiers from disk
    Boolean diskApplyNumericClassifiers = ois.readBoolean();
    if (props.getProperty("ner.applyNumericClassifiers") != null) {
      this.applyNumericClassifiers = Boolean.parseBoolean(props.getProperty("ner.applyNumericClassifiers"));
    } else {
      this.applyNumericClassifiers = diskApplyNumericClassifiers;
    }
    this.nerLanguage = NER_LANGUAGE_DEFAULT;
    // build the nsc, note that initProps should be set by ClassifierCombiner
    this.nsc = new NumberSequenceClassifier(new Properties(), useSUTime, props);
    if (PropertiesUtils.getBool(props, NERClassifierCombiner.APPLY_GAZETTE_PROPERTY, NERClassifierCombiner.APPLY_GAZETTE_DEFAULT) ) {
      this.gazetteMapping = readRegexnerGazette(DefaultPaths.DEFAULT_NER_GAZETTE_MAPPING);
    } else {
      this.gazetteMapping = Collections.emptyMap();
    }
  }

  public static final Set<String> DEFAULT_PASS_DOWN_PROPERTIES =
          CollectionUtils.asSet("encoding", "inputEncoding", "outputEncoding", "maxAdditionalKnownLCWords","map",
                  "ner.combinationMode", "ner.usePresetNERTags");

  /** This factory method is used to create the NERClassifierCombiner used in NERCombinerAnnotator
   *  (and, thence, in StanfordCoreNLP).
   *
   *  @param name A "x.y" format property name prefix (the "x" part). This is commonly null,
   *              and then "ner" is used.  If it is the empty string, then no property prefix is used.
   *  @param properties Various properties, including a list in "ner.model".
   *                    The used ones start with name + "." or are in passDownProperties
   *  @return An NERClassifierCombiner with the given properties
   */
  public static NERClassifierCombiner createNERClassifierCombiner(String name, Properties properties) {
    return createNERClassifierCombiner(name, DEFAULT_PASS_DOWN_PROPERTIES, properties);
  }

  /** This factory method is used to create the NERClassifierCombiner used in NERCombinerAnnotator
   *  (and, thence, in StanfordCoreNLP).
   *
   *  @param name A "x.y" format property name prefix (the "x" part). This is commonly null,
   *              and then "ner" is used.  If it is the empty string, then no property prefix is used.
   *  @param passDownProperties Property names for which the property should be passed down
   *              to the NERClassifierCombiner. The default is not to pass down, but pass down is
   *              useful for things like charset encoding.
   *  @param properties Various properties, including a list in "ner.model".
   *                    The used ones start with name + "." or are in passDownProperties
   *  @return An NERClassifierCombiner with the given properties
   */
  public static NERClassifierCombiner createNERClassifierCombiner(String name,
                                                                  Set<String> passDownProperties,
                                                                  Properties properties) {
    String prefix = (name == null) ? "ner." : name.isEmpty() ? "" : name + '.';
    String modelNames = properties.getProperty(prefix + "model");
    if (modelNames == null) {
      modelNames = DefaultPaths.DEFAULT_NER_THREECLASS_MODEL + ',' + DefaultPaths.DEFAULT_NER_MUC_MODEL + ',' +
              DefaultPaths.DEFAULT_NER_CONLL_MODEL;
    }
    // but modelNames can still be empty string is set explicitly to be empty!
    String[] models;
    if ( ! modelNames.isEmpty()) {
      models  = modelNames.split(",");
    } else {
      // Allow for no real NER model - can just use numeric classifiers or SUTime
      log.info("WARNING: no NER models specified");
      models = StringUtils.EMPTY_STRING_ARRAY;
    }
    NERClassifierCombiner nerCombiner;
    try {
      boolean applyNumericClassifiers =
              PropertiesUtils.getBool(properties,
                      prefix + APPLY_NUMERIC_CLASSIFIERS_PROPERTY_BASE,
                      APPLY_NUMERIC_CLASSIFIERS_DEFAULT);
      boolean useSUTime =
              PropertiesUtils.getBool(properties,
                      prefix + NumberSequenceClassifier.USE_SUTIME_PROPERTY_BASE,
                      NumberSequenceClassifier.USE_SUTIME_DEFAULT);
      boolean applyRegexner =
          PropertiesUtils.getBool(properties,
              NERClassifierCombiner.APPLY_GAZETTE_PROPERTY,
              NERClassifierCombiner.APPLY_GAZETTE_DEFAULT);
      Properties combinerProperties;
      if (passDownProperties != null) {
        combinerProperties = PropertiesUtils.extractSelectedProperties(properties, passDownProperties);
        if (useSUTime) {
          // Make sure SUTime parameters are included
          Properties sutimeProps = PropertiesUtils.extractPrefixedProperties(properties, NumberSequenceClassifier.SUTIME_PROPERTY + ".", true);
          PropertiesUtils.overWriteProperties(combinerProperties, sutimeProps);
        }
      } else {
        // if passDownProperties is null, just pass everything through
        combinerProperties = properties;
      }
      //Properties combinerProperties = PropertiesUtils.extractSelectedProperties(properties, passDownProperties);
      Language nerLanguage = Language.fromString(properties.getProperty(prefix+"language"),Language.ENGLISH);
      nerCombiner = new NERClassifierCombiner(applyNumericClassifiers, nerLanguage,
              useSUTime, applyRegexner, combinerProperties, models);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }

    return nerCombiner;
  }

  public boolean appliesNumericClassifiers() {
    return applyNumericClassifiers;
  }

  public boolean usesSUTime() {
    // if applyNumericClassifiers is false, SUTime isn't run regardless of setting of useSUTime
    return useSUTime && applyNumericClassifiers;
  }

  private static <INN extends CoreMap> void copyAnswerFieldsToNERField(List<INN> l) {
    l.forEach(
        m -> {
          m.set(
              CoreAnnotations.NamedEntityTagAnnotation.class,
              m.get(CoreAnnotations.AnswerAnnotation.class));
        });
    }

  @Override
  public List<CoreLabel> classify(List<CoreLabel> tokens) {
    return classifyWithGlobalInformation(tokens, null, null);
  }

  @Override
  public List<CoreLabel> classifyWithGlobalInformation(
      List<CoreLabel> tokens, final CoreMap document, final CoreMap sentence) {
    List<CoreLabel> output = super.classify(tokens);
    if (applyNumericClassifiers) {
      try {
        // recognizes additional MONEY, TIME, DATE, and NUMBER using a set of deterministic rules
        // note: some DATE and TIME entities are recognized by our statistical NER based on MUC
        // note: this includes SUTime
        // note: requires TextAnnotation, PartOfSpeechTagAnnotation, and AnswerAnnotation
        // note: this sets AnswerAnnotation!
        recognizeNumberSequences(output, document, sentence);
      } catch (RuntimeInterruptedException e) {
        throw e;
      } catch (Exception e) {
        log.info("Ignored an exception in NumberSequenceClassifier: (result is that some numbers were not classified)");
        log.info("Tokens: " + StringUtils.joinWords(tokens, " "));
        e.printStackTrace(System.err);
      }

      // AnswerAnnotation -> NERAnnotation
      copyAnswerFieldsToNERField(output);

      try {
        // normalizes numeric entities such as MONEY, TIME, DATE, or PERCENT
        // note: this uses and sets NamedEntityTagAnnotation!
        if(nerLanguage == Language.CHINESE) {
          // For chinese there is no support for SUTime by default
          // We need to hand in document and sentence for Chinese to handle DocDate; however, since English normalization
          // is handled by SUTime, and the information is passed in recognizeNumberSequences(), English only need output.
          ChineseQuantifiableEntityNormalizer.addNormalizedQuantitiesToEntities(output, document, sentence);
        } else {
          QuantifiableEntityNormalizer.addNormalizedQuantitiesToEntities(output, false, useSUTime);
        }
      } catch (Exception e) {
        log.info("Ignored an exception in QuantifiableEntityNormalizer: (result is that entities were not normalized)");
        log.info("Tokens: " + StringUtils.joinWords(tokens, " "));
        e.printStackTrace(System.err);
      } catch(AssertionError e) {
        log.info("Ignored an assertion in QuantifiableEntityNormalizer: (result is that entities were not normalized)");
        log.info("Tokens: " + StringUtils.joinWords(tokens, " "));
        e.printStackTrace(System.err);
      }
    } else {
      // AnswerAnnotation -> NERAnnotation
      copyAnswerFieldsToNERField(output);
    }

    // Apply RegexNER annotations
    // cdm 2016: Used to say and do "// skip first token" but I couldn't understand why, so I
    // removed that.
    tokens
        .stream()
        .filter(
            token ->
                (token.tag() == null || token.tag().charAt(0) == 'N') && "O".equals(token.ner())
                    || "MISC".equals(token.ner()))
        .forEach(
            token -> {
              String target = gazetteMapping.get(token.originalText());
              if (target != null) {
                token.setNER(target);
              }
            });

    // Return
    return output;
  }

  private void recognizeNumberSequences(
      List<CoreLabel> words, final CoreMap document, final CoreMap sentence) {
    // we need to copy here because NumberSequenceClassifier overwrites the AnswerAnnotation
    List<CoreLabel> newWords = NumberSequenceClassifier.copyTokens(words, sentence);

    nsc.classifyWithGlobalInformation(newWords, document, sentence);

    // copy AnswerAnnotation back. Do not overwrite!
    // also, copy all the additional annotations generated by SUTime and NumberNormalizer
    for (int i = 0, sz = words.size(); i < sz; i++){
      CoreLabel origWord = words.get(i);
      CoreLabel newWord = newWords.get(i);

      // log.info(newWord.word() + " => " + newWord.get(CoreAnnotations.AnswerAnnotation.class) + " " + origWord.ner());

      String before = origWord.get(CoreAnnotations.AnswerAnnotation.class);
      String newGuess = newWord.get(CoreAnnotations.AnswerAnnotation.class);
      if ((before == null || before.equals(nsc.flags.backgroundSymbol) || before.equals("MISC")) && !newGuess.equals(nsc.flags.backgroundSymbol)) {
        origWord.set(CoreAnnotations.AnswerAnnotation.class, newGuess);
      }

      // transfer other annotations generated by SUTime or NumberNormalizer
      NumberSequenceClassifier.transferAnnotations(newWord, origWord);
    }
  }

  public void finalizeAnnotation(Annotation annotation) {
    nsc.finalizeClassification(annotation);
  }

  // write an NERClassifierCombiner to an ObjectOutputStream

  public void serializeClassifier(ObjectOutputStream oos) {
    try {
      // first write the ClassifierCombiner part to disk
      super.serializeClassifier(oos);
      // write whether to use SUTime
      oos.writeBoolean(useSUTime);
      // write whether to use NumericClassifiers
      oos.writeBoolean(applyNumericClassifiers);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  /** Static method for getting an NERClassifierCombiner from a string path. */
  public static NERClassifierCombiner getClassifier(String loadPath, Properties props) throws IOException,
          ClassNotFoundException, ClassCastException {
    ObjectInputStream ois = IOUtils.readStreamFromString(loadPath);
    NERClassifierCombiner returnNCC = getClassifier(ois, props);
    IOUtils.closeIgnoringExceptions(ois);
    return returnNCC;
  }

  // static method for getting an NERClassifierCombiner from an ObjectInputStream
  public static NERClassifierCombiner getClassifier(ObjectInputStream ois, Properties props) throws IOException,
          ClassNotFoundException, ClassCastException {
    return new NERClassifierCombiner(ois, props);
  }

  /** Method for displaying info about an NERClassifierCombiner. */
  public static void showNCCInfo(NERClassifierCombiner ncc) {
    log.info("");
    log.info("info for this NERClassifierCombiner: ");
    ClassifierCombiner.showCCInfo(ncc);
    log.info("useSUTime: "+ncc.useSUTime);
    log.info("applyNumericClassifier: "+ncc.applyNumericClassifiers);
    log.info("");
  }


  /**
   * Read a gazette mapping in TokensRegex format from the given path
   * The format is: 'case_sensitive_word \t target_ner_class' (additional info is ignored).
   *
   * @param mappingFile The mapping file to read from, as a path either on the filesystem or in your classpath.
   *
   * @return The mapping from word to NER tag.
   */
  private static Map<String, String> readRegexnerGazette(String mappingFile) {
    Map<String, String> mapping = new HashMap<>();
    try {
      for (String line : IOUtils.slurpReader(IOUtils.readerFromString(mappingFile.trim())).split("\n")) {
        String[] fields = line.split("\t");
        String key = fields[0];
        String target = fields[1];
        mapping.put(key, target);
      }
    } catch (IOException e) {
      log.warn("Could not read Regex mapping: " + mappingFile);
    }
    return Collections.unmodifiableMap(mapping);
  }



  /** The main method. */
  public static void main(String[] args) throws Exception {
    StringUtils.logInvocationString(log, args);
    Properties props = StringUtils.argsToProperties(args);
    SeqClassifierFlags flags = new SeqClassifierFlags(props, false); // false for print probs as printed in next code block

    String loadPath = props.getProperty("loadClassifier");
    NERClassifierCombiner ncc;
    if (loadPath != null) {
      // note that when loading a serialized classifier, the philosophy is override
      // any settings in props with those given in the commandline
      // so if you dumped it with useSUTime = false, and you say -useSUTime at
      // the commandline, the commandline takes precedence
      ncc = getClassifier(loadPath,props);
    } else {
      // pass null for passDownProperties to let all props go through
      ncc = createNERClassifierCombiner("ner", null, props);
    }

    // write the NERClassifierCombiner to the given path on disk
    String serializeTo = props.getProperty("serializeTo");
    if (serializeTo != null) {
      ncc.serializeClassifier(serializeTo);
    }

    String textFile = props.getProperty("textFile");
    if (textFile != null) {
      ncc.classifyAndWriteAnswers(textFile);
    }

    // run on multiple textFiles , based off CRFClassifier code
    String textFiles = props.getProperty("textFiles");
    if (textFiles != null) {
      List<File> files = new ArrayList<>();
      for (String filename : textFiles.split(",")) {
        files.add(new File(filename));
      }
      ncc.classifyFilesAndWriteAnswers(files);
    }

    // options for run the NERClassifierCombiner on a testFile or testFiles
    String testFile = props.getProperty("testFile");
    String testFiles = props.getProperty("testFiles");
    String crfToExamine = props.getProperty("crfToExamine");
    DocumentReaderAndWriter<CoreLabel> readerAndWriter = ncc.defaultReaderAndWriter();
    if (testFile != null || testFiles != null) {
      // check if there is not a crf specific request
      if (crfToExamine == null) {
        // in this case there is no crfToExamine
        if (testFile != null) {
          ncc.classifyAndWriteAnswers(testFile, readerAndWriter, true);
        } else {
          List<File> files = Arrays.stream(testFiles.split(",")).map(File::new).collect(Collectors.toList());
          ncc.classifyFilesAndWriteAnswers(files, ncc.defaultReaderAndWriter(), true);
        }
      } else {
        ClassifierCombiner.examineCRF(ncc, crfToExamine, flags, testFile, testFiles, readerAndWriter);
      }
    }

    // option for showing info about the NERClassifierCombiner
    String showNCCInfo = props.getProperty("showNCCInfo");
    if (showNCCInfo != null) {
      showNCCInfo(ncc);
    }

    // option for reading in from stdin
    if (flags.readStdin) {
      ncc.classifyStdin();
    }
  }

}

