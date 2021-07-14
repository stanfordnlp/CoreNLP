package edu.stanford.nlp.ie;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ie.ner.CMMClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.pipeline.DefaultPaths;
import edu.stanford.nlp.sequences.DocumentReaderAndWriter;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.logging.Redwood;


/**
 * Merges the outputs of two or more AbstractSequenceClassifiers according to
 * a simple precedence scheme: any given base classifier contributes only
 * classifications of labels that do not exist in the base classifiers specified
 * before, and that do not have any token overlap with labels assigned by
 * higher priority classifiers.
 * <p>
 * This is a pure AbstractSequenceClassifier, i.e., it sets the AnswerAnnotation label.
 * If you work with NER classifiers, you should use NERClassifierCombiner. This class
 * inherits from ClassifierCombiner, and takes care that all AnswerAnnotations are also
 * copied to NERAnnotation.
 * <p>
 * You can specify up to 10 base classifiers using the -loadClassifier1 to -loadClassifier10
 * properties. We also maintain the older usage when only two base classifiers were accepted,
 * specified using -loadClassifier and -loadAuxClassifier.
 * <p>
 * ms 2009: removed all NER functionality (see NERClassifierCombiner), changed code so it
 * accepts an arbitrary number of base classifiers, removed dead code.
 *
 * @author Chris Cox
 * @author Mihai Surdeanu
 */
public class ClassifierCombiner<IN extends CoreMap & HasWord> extends AbstractSequenceClassifier<IN>  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(ClassifierCombiner.class);

  private static final boolean DEBUG = System.getProperty("ClassifierCombiner", null) != null;

  private List<AbstractSequenceClassifier<IN>> baseClassifiers;

  /**
   * NORMAL means that if one classifier uses PERSON, later classifiers can't also add PERSON, for example. <br>
   * HIGH_RECALL allows later models do set PERSON as long as it doesn't clobber existing annotations.
   */
  enum CombinationMode {
    NORMAL, HIGH_RECALL
  }

  private static final CombinationMode DEFAULT_COMBINATION_MODE = CombinationMode.NORMAL;
  private static final String COMBINATION_MODE_PROPERTY = "ner.combinationMode";
  private final CombinationMode combinationMode;

  // keep track of properties used to initialize
  private final Properties initProps;
  // keep track of paths used to load CRFs
  private List<String> initLoadPaths = new ArrayList<>();

  /**
   * @param p Properties File that specifies {@code loadClassifier}
   * and {@code loadAuxClassifier} properties or, alternatively, {@code loadClassifier[1-10]} properties.
   * @throws FileNotFoundException If classifier files not found
   */
  public ClassifierCombiner(Properties p) throws IOException {
    super(p);
    this.combinationMode = extractCombinationModeSafe(p);
    String loadPath1, loadPath2;
    List<String> paths = new ArrayList<>();

    //
    // preferred configuration: specify up to 10 base classifiers using loadClassifier1 to loadClassifier10 properties
    //
    if((loadPath1 = p.getProperty("loadClassifier1")) != null && (loadPath2 = p.getProperty("loadClassifier2")) != null) {
      paths.add(loadPath1);
      paths.add(loadPath2);
      for(int i = 3; i <= 10; i ++){
        String path;
        if ((path = p.getProperty("loadClassifier" + i)) != null) {
          paths.add(path);
        }
      }
      loadClassifiers(p, paths);
    }

    //
    // second accepted setup (backward compatible): two classifier given in loadClassifier and loadAuxClassifier
    //
    else if((loadPath1 = p.getProperty("loadClassifier")) != null && (loadPath2 = p.getProperty("loadAuxClassifier")) != null){
      paths.add(loadPath1);
      paths.add(loadPath2);
      loadClassifiers(p, paths);
    }

    //
    // fall back strategy: use the two default paths on NLP machines
    //
    else {
      paths.add(DefaultPaths.DEFAULT_NER_THREECLASS_MODEL);
      paths.add(DefaultPaths.DEFAULT_NER_MUC_MODEL);
      loadClassifiers(p, paths);
    }
    this.initLoadPaths = new ArrayList<>(paths);
    this.initProps = p;
  }

  /** Loads a series of base classifiers from the paths specified using the
   *  Properties specified.
   *
   *  @param props Properties for the classifier to use (encodings, output format, etc.)
   *  @param combinationMode How to handle multiple classifiers specifying the same entity type
   *  @param loadPaths Paths to the base classifiers
   *  @throws IOException If IO errors in loading classifier files
   */
  public ClassifierCombiner(Properties props, CombinationMode combinationMode, String... loadPaths) throws IOException {
    super(props);
    this.combinationMode = combinationMode;
    List<String> paths = new ArrayList<>(Arrays.asList(loadPaths));
    loadClassifiers(props, paths);
    this.initLoadPaths = new ArrayList<>(paths);
    this.initProps = props;
  }

  /** Loads a series of base classifiers from the paths specified using the
   *  Properties specified.
   *
   *  @param combinationMode How to handle multiple classifiers specifying the same entity type
   *  @param loadPaths Paths to the base classifiers
   *  @throws IOException If IO errors in loading classifier files
   */
  public ClassifierCombiner(CombinationMode combinationMode, String... loadPaths) throws IOException {
    this(new Properties(), combinationMode, loadPaths);
  }

  /** Loads a series of base classifiers from the paths specified.
   *
   * @param loadPaths Paths to the base classifiers
   * @throws FileNotFoundException If classifier files not found
   */
  public ClassifierCombiner(String... loadPaths) throws IOException {
    this(DEFAULT_COMBINATION_MODE, loadPaths);
  }


  /** Combines a series of base classifiers.
   *
   * @param classifiers The base classifiers
   */
  @SafeVarargs
  public ClassifierCombiner(AbstractSequenceClassifier<IN>... classifiers) {
    super(new Properties());
    this.combinationMode = DEFAULT_COMBINATION_MODE;
    baseClassifiers = new ArrayList<>(Arrays.asList(classifiers));
    flags.backgroundSymbol = baseClassifiers.get(0).flags.backgroundSymbol;
    this.initProps = new Properties();
  }

  // constructor for building a ClassifierCombiner from an ObjectInputStream
  public ClassifierCombiner(ObjectInputStream ois, Properties props) throws IOException, ClassNotFoundException, ClassCastException {
    // read the initial Properties out of the ObjectInputStream so you can properly start the AbstractSequenceClassifier
    // note now we load in props from command line and overwrite any that are given for command line
    super(PropertiesUtils.overWriteProperties((Properties) ois.readObject(),props));
    // read another copy of initProps that I have helpfully included
    // TODO: probably set initProps in AbstractSequenceClassifier to avoid this writing twice thing, its hacky
    this.initProps = PropertiesUtils.overWriteProperties((Properties) ois.readObject(),props);
    // read the initLoadPaths
    this.initLoadPaths = (ArrayList<String>) ois.readObject();
    // read the combinationMode from the serialized version
    String cm = (String) ois.readObject();
    // see if there is a commandline override for the combinationMode, else set newCM to the serialized version
    CombinationMode newCM;
    if (props.getProperty("ner.combinationMode") != null) {
      // there is a possible commandline override, have to see if its valid
      try {
        // see if the commandline has a proper value
        newCM = CombinationMode.valueOf(props.getProperty("ner.combinationMode"));
      } catch (IllegalArgumentException e) {
        // the commandline override did not have a proper value, so just use the serialized version
        newCM = CombinationMode.valueOf(cm);
      }
    } else {
      // there was no commandline override given, so just use the serialized version
      newCM = CombinationMode.valueOf(cm);
    }
    this.combinationMode = newCM;
    // read in the base classifiers
    int numClassifiers = ois.readInt();
    // set up the list of base classifiers
    this.baseClassifiers = new ArrayList<>();
    int i = 0;
    while (i < numClassifiers) {
      try {
        log.info("loading CRF...");
        CRFClassifier<IN> newCRF = ErasureUtils.uncheckedCast(CRFClassifier.getClassifier(ois, props));
        baseClassifiers.add(newCRF);
        i++;
      } catch (Exception e) {
        try {
          log.info("loading CMM...");
          CMMClassifier newCMM = ErasureUtils.uncheckedCast(CMMClassifier.getClassifier(ois, props));
          baseClassifiers.add(newCMM);
          i++;
        } catch (Exception ex) {
          throw new IOException("Couldn't load classifier!", ex);
        }
      }
    }
  }

  /**
   * Either finds COMBINATION_MODE_PROPERTY or returns a default value.
   */
  public static CombinationMode extractCombinationMode(Properties p) {
    String mode = p.getProperty(COMBINATION_MODE_PROPERTY);
    if (mode == null) {
      return DEFAULT_COMBINATION_MODE;
    } else {
      return CombinationMode.valueOf(mode.toUpperCase(Locale.ROOT));
    }
  }

  /**
   * Either finds COMBINATION_MODE_PROPERTY or returns a default
   * value.  If the value is not a legal value, a warning is printed.
   */
  public static CombinationMode extractCombinationModeSafe(Properties p) {
    try {
      return extractCombinationMode(p);
    } catch (IllegalArgumentException e) {
      log.info("Illegal value of " + COMBINATION_MODE_PROPERTY + ": " + p.getProperty(COMBINATION_MODE_PROPERTY));
      log.info("  Legal values:");
      for (CombinationMode mode : CombinationMode.values()) {
        log.info("  " + mode);
      }
      log.info();
      return CombinationMode.NORMAL;
    }
  }

  private void loadClassifiers(Properties props, List<String> paths) throws IOException {
    baseClassifiers = new ArrayList<>();
    if (PropertiesUtils.getBool(props, "ner.usePresetNERTags", false)) {
      AbstractSequenceClassifier<IN> presetASC = new PresetSequenceClassifier<>(props);
      baseClassifiers.add(presetASC);
    }
    for (String path: paths){
      AbstractSequenceClassifier<IN> cls = loadClassifierFromPath(props, path);
      baseClassifiers.add(cls);
      if (DEBUG) {
        System.err.printf("Successfully loaded classifier #%d from %s.%n", baseClassifiers.size(), path);
      }
    }
    if (baseClassifiers.size() > 0) {
      flags.backgroundSymbol = baseClassifiers.get(0).flags.backgroundSymbol;
    }
  }


  public static <INN extends CoreMap & HasWord> AbstractSequenceClassifier<INN> loadClassifierFromPath(Properties props, String path)
      throws IOException {
    //try loading as a CRFClassifier
    try {
      return ErasureUtils.uncheckedCast(CRFClassifier.getClassifier(path, props));
    } catch (Exception e) {
      e.printStackTrace();
    }
    //try loading as a CMMClassifier
    try {
      return ErasureUtils.uncheckedCast(CMMClassifier.getClassifier(path));
    } catch (Exception e) {
      //fail
      //log.info("Couldn't load classifier from path :"+path);
      throw new IOException("Couldn't load classifier from " + path, e);
    }
  }

  @Override
  public Set<String> labels() {
    Set<String> labs = Generics.newHashSet();
    for(AbstractSequenceClassifier<? extends CoreMap> cls: baseClassifiers)
      labs.addAll(cls.labels());
    return labs;
  }


  /**
   * Reads the Answer annotations in the given labellings (produced by the base models)
   *   and combines them using a priority ordering, i.e., for a given baseDocument all
   *   labellings seen before in the baseDocuments list have higher priority.
   *   Writes the answer to AnswerAnnotation in the labeling at position 0
   *   (considered to be the main document).
   *
   *  @param baseDocuments Results of all base AbstractSequenceClassifier models
   *  @return A List of IN with the combined annotations.  (This is an
   *     updating of baseDocuments.get(0), not a new List.)
   */
  private List<IN> mergeDocuments(List<List<IN>> baseDocuments){
    // we should only get here if there is something to merge
    assert(! baseClassifiers.isEmpty() && ! baseDocuments.isEmpty());
    // all base outputs MUST have the same length (we generated them internally!)
    for(int i = 1; i < baseDocuments.size(); i ++)
      assert(baseDocuments.get(0).size() == baseDocuments.get(i).size());

    String background = baseClassifiers.get(0).flags.backgroundSymbol;

    // baseLabels.get(i) points to the labels assigned by baseClassifiers.get(i)
    List<Set<String>> baseLabels = new ArrayList<>();
    Set<String> seenLabels = Generics.newHashSet();
    for (AbstractSequenceClassifier<? extends CoreMap> baseClassifier : baseClassifiers) {
      Set<String> labs = baseClassifier.labels();
      if (combinationMode != CombinationMode.HIGH_RECALL) {
        labs.removeAll(seenLabels);
      } else {
        labs.remove(baseClassifier.flags.backgroundSymbol);
        labs.remove(background);
      }
      seenLabels.addAll(labs);
      baseLabels.add(labs);
    }

    if (DEBUG) {
      for(int i = 0; i < baseLabels.size(); i ++)
        log.info("mergeDocuments: Using classifier #" + i + " for " + baseLabels.get(i));
      log.info("mergeDocuments: Background symbol is " + background);

      log.info("Base model outputs:");
      for( int i = 0; i < baseDocuments.size(); i ++){
        System.err.printf("Output of model #%d:", i);
        for (IN l : baseDocuments.get(i)) {
          log.info(' ');
          log.info(l.get(CoreAnnotations.AnswerAnnotation.class));
        }
        log.info();
      }
    }

    // incrementally merge each additional model with the main model (i.e., baseDocuments.get(0))
    // this keeps adding labels from the additional models to mainDocument
    // hence, when all is done, mainDocument contains the labels of all base models
    List<IN> mainDocument = baseDocuments.get(0);
    for (int i = 1; i < baseDocuments.size(); i ++) {
      mergeTwoDocuments(mainDocument, baseDocuments.get(i), baseLabels.get(i), background);
    }

    if (DEBUG) {
      log.info("Output of combined model:");
      for (IN l: mainDocument) {
        log.info(' ');
        log.info(l.get(CoreAnnotations.AnswerAnnotation.class));
      }
      log.info();
      log.info();
    }

    return mainDocument;
  }


  /** This merges in labels from the auxDocument into the mainDocument when
   *  tokens have one of the labels in auxLabels, and the subsequence
   *  labeled with this auxLabel does not conflict with any non-background
   *  labelling in the mainDocument.
   */
  static <INN extends CoreMap & HasWord> void mergeTwoDocuments(List<INN> mainDocument, List<INN> auxDocument, Set<String> auxLabels, String background) {
    boolean insideAuxTag = false;
    boolean auxTagValid = true;
    String prevAnswer = background;
    Double prevAnswerProb = null;
    Collection<INN> constituents = new ArrayList<>();

    Iterator<INN> auxIterator = auxDocument.listIterator();

    for (INN wMain : mainDocument) {
      String mainAnswer = wMain.get(CoreAnnotations.AnswerAnnotation.class);
      INN wAux = auxIterator.next();
      String auxAnswer = wAux.get(CoreAnnotations.AnswerAnnotation.class);
      Double auxAnswerProb = wAux.get(CoreAnnotations.AnswerProbAnnotation.class);
      boolean insideMainTag = !mainAnswer.equals(background);

      /* if the auxiliary classifier gave it one of the labels unique to
         auxClassifier, we might set the mainLabel to that. */
      if (auxLabels.contains(auxAnswer)) {
        if ( ! prevAnswer.equals(auxAnswer) && ! prevAnswer.equals(background)) {
          if (auxTagValid){
            for (INN wi : constituents) {
              wi.set(CoreAnnotations.AnswerAnnotation.class, prevAnswer);
              if (prevAnswerProb != null)
                wi.set(CoreAnnotations.AnswerProbAnnotation.class, prevAnswerProb);
            }
          }
          auxTagValid = true;
          constituents = new ArrayList<>();
        }
        insideAuxTag = true;
        if (insideMainTag) { auxTagValid = false; }
        prevAnswer = auxAnswer;
        prevAnswerProb = auxAnswerProb;
        constituents.add(wMain);
      } else {
        if (insideAuxTag) {
          if (auxTagValid){
            for (INN wi : constituents) {
              wi.set(CoreAnnotations.AnswerAnnotation.class, prevAnswer);
              if (prevAnswerProb != null)
                wi.set(CoreAnnotations.AnswerProbAnnotation.class, prevAnswerProb);
            }
          }
          constituents = new ArrayList<>();
        }
        insideAuxTag=false;
        auxTagValid = true;
        prevAnswer = background;
        prevAnswerProb = null;
      }
    }
    // deal with a sequence final auxLabel
    if (auxTagValid){
      for (INN wi : constituents) {
        wi.set(CoreAnnotations.AnswerAnnotation.class, prevAnswer);
        if (prevAnswerProb != null)
          wi.set(CoreAnnotations.AnswerProbAnnotation.class, prevAnswerProb);
      }
    }
  }

  /**
   * Generates the AnswerAnnotation labels of the combined model for the given
   * tokens, storing them in place in the tokens.
   *
   * @param tokens A List of IN
   * @return The passed in parameters, which will have the AnswerAnnotation field added/overwritten
   */
  @Override
  public List<IN> classify(List<IN> tokens) {
    if (baseClassifiers.isEmpty()) {
      return tokens;
    }
    List<List<IN>> baseOutputs = new ArrayList<>();

    // the first base model works in place, modifying the original tokens
    List<IN> output = baseClassifiers.get(0).classifySentence(tokens);
    // classify(List<IN>) is supposed to work in place, so add AnswerAnnotation to tokens!
    for (int i = 0, sz = output.size(); i < sz; i++) {
      tokens.get(i).set(CoreAnnotations.AnswerAnnotation.class, output.get(i).get(CoreAnnotations.AnswerAnnotation.class));
      tokens.get(i).set(CoreAnnotations.AnswerProbAnnotation.class, output.get(i).get(CoreAnnotations.AnswerProbAnnotation.class));
    }
    baseOutputs.add(tokens);

    for (int i = 1, sz = baseClassifiers.size(); i < sz; i ++) {
      //List<CoreLabel> copy = deepCopy(tokens);
      // no need for deep copy: classifySentence creates a copy of the input anyway
      // List<CoreLabel> copy = tokens;
      output = baseClassifiers.get(i).classifySentence(tokens);
      baseOutputs.add(output);
    }
    assert(baseOutputs.size() == baseClassifiers.size());

    return mergeDocuments(baseOutputs);
  }


  @Override
  public void train(Collection<List<IN>> docs,
                    DocumentReaderAndWriter<IN> readerAndWriter) {
    throw new UnsupportedOperationException();
  }

  // write a ClassifierCombiner to disk, this is based on CRFClassifier code
  @Override
  public void serializeClassifier(String serializePath) {
    log.info("Serializing classifier to " + serializePath + "...");

    ObjectOutputStream oos = null;
    try {
      oos = IOUtils.writeStreamFromString(serializePath);
      serializeClassifier(oos);
      log.info("done.");

    } catch (Exception e) {
      throw new RuntimeIOException("Failed to save classifier", e);
    } finally {
      IOUtils.closeIgnoringExceptions(oos);
    }
  }

  // method for writing a ClassifierCombiner to an ObjectOutputStream
  @Override
  public void serializeClassifier(ObjectOutputStream oos) {
    try {
      // record the properties used to initialize
      oos.writeObject(initProps);
      // this is a bit of a hack, but have to write this twice so you can get it again
      // after you initialize AbstractSequenceClassifier
      // basically when this is read from the ObjectInputStream, I read it once to call
      // super(props) and then I read it again so I can set this.initProps
      // TODO: probably should have AbstractSequenceClassifier store initProps to get rid of this double writing
      oos.writeObject(initProps);
      // record the initial loadPaths
      oos.writeObject(initLoadPaths);
      // record the combinationMode
      String combinationModeString = combinationMode.name();
      oos.writeObject(combinationModeString);
      // get the number of classifiers to write to disk
      int numClassifiers = baseClassifiers.size();
      oos.writeInt(numClassifiers);
      // go through baseClassifiers and write each one to disk with CRFClassifier's serialize method
      log.info("");
      for (AbstractSequenceClassifier<IN> asc : baseClassifiers) {
        //CRFClassifier crfc = (CRFClassifier) asc;
        //log.info("Serializing a base classifier...");
        asc.serializeClassifier(oos);
      }
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  @Override
  public void loadClassifier(ObjectInputStream in, Properties props) throws IOException, ClassCastException, ClassNotFoundException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<IN> classifyWithGlobalInformation(List<IN> tokenSeq, CoreMap doc, CoreMap sent) {
    return classify(tokenSeq);
  }

  // static method for getting a ClassifierCombiner from a string path
  public static ClassifierCombiner getClassifier(String loadPath, Properties props) throws IOException,
          ClassNotFoundException, ClassCastException {
    ObjectInputStream ois = IOUtils.readStreamFromString(loadPath);
    ClassifierCombiner returnCC = getClassifier(ois, props);
    IOUtils.closeIgnoringExceptions(ois);
    return returnCC;
  }

  // static method for getting a ClassifierCombiner from ObjectInputStream
  public static ClassifierCombiner getClassifier(ObjectInputStream ois, Properties props) throws IOException,
          ClassCastException, ClassNotFoundException {
    return new ClassifierCombiner(ois, props);
  }

  // Run a particular CRF of this ClassifierCombiner on a testFile.
  // User can say -crfToExamine 0 to get 1st element or -crfToExamine /edu/stanford/models/muc7.crf.ser.gz .
  // This does not currently support drill down on CMMs.
  public static void examineCRF(ClassifierCombiner cc, String crfNameOrIndex, SeqClassifierFlags flags,
                                String testFile, String testFiles,
                                DocumentReaderAndWriter<CoreLabel> readerAndWriter) throws Exception {
    CRFClassifier<CoreLabel> crf;
    // potential index into baseClassifiers
    int ci;
    // set ci with the following rules
    // 1. first see if ci is an index into baseClassifiers
    // 2. if its not an integer or wrong size, see if its a file name of a loadPath
    try {
      ci = Integer.parseInt(crfNameOrIndex);
      if (ci < 0 || ci >= cc.baseClassifiers.size()) {
        // ci is not an int corresponding to an element in baseClassifiers, see if name of a crf loadPath
        ci = cc.initLoadPaths.indexOf(crfNameOrIndex);
      }
    } catch (NumberFormatException e) {
      // cannot interpret crfNameOrIndex as an integer, see if name of a crf loadPath
      ci = cc.initLoadPaths.indexOf(crfNameOrIndex);
    }
    // if ci corresponds to an index in baseClassifiers, get the crf at that index, otherwise set crf to null
    if (ci >= 0 && ci < cc.baseClassifiers.size()) {
      // TODO: this will break if baseClassifiers contains something that is not a CRF
      crf = (CRFClassifier<CoreLabel>) cc.baseClassifiers.get(ci);
    } else {
      crf = null;
    }
    // if you can get a specific crf, generate the appropriate report, if null do nothing
    if (crf != null) {
      // if there is a crf and testFile was set , do the crf stuff for a single testFile
      if (testFile != null) {
        if (flags.searchGraphPrefix != null) {
          crf.classifyAndWriteViterbiSearchGraph(testFile, flags.searchGraphPrefix, crf.makeReaderAndWriter());
        } else if (flags.printFirstOrderProbs) {
          crf.printFirstOrderProbs(testFile, readerAndWriter);
        } else if (flags.printFactorTable) {
          crf.printFactorTable(testFile, readerAndWriter);
        } else if (flags.printProbs) {
          crf.printProbs(testFile, readerAndWriter);
        } else if (flags.useKBest) {
          // TO DO: handle if user doesn't provide kBest
          int k = flags.kBest;
          crf.classifyAndWriteAnswersKBest(testFile, k, readerAndWriter);
        } else if (flags.printLabelValue) {
          crf.printLabelInformation(testFile, readerAndWriter);
        } else {
          // no crf test flag provided
          log.info("Warning: no crf test flag was provided, running classify and write answers");
          crf.classifyAndWriteAnswers(testFile,readerAndWriter,true);
        }
      } else if (testFiles != null) {
        // if there is a crf and testFiles was set , do the crf stuff for testFiles
        // if testFile was set as well, testFile overrides
        List<File> files = Arrays.stream(testFiles.split(",")).map(File::new).collect(Collectors.toList());
        if (flags.printProbs) {
          // there is a crf and printProbs
          crf.printProbs(files, crf.defaultReaderAndWriter());
        } else {
          log.info("Warning: no crf test flag was provided, running classify files and write answers");
          crf.classifyFilesAndWriteAnswers(files, crf.defaultReaderAndWriter(), true);
        }
      }
    }
  }

  // show some info about a ClassifierCombiner
  public static void showCCInfo(ClassifierCombiner cc) {
    log.info("");
    log.info("classifiers used:");
    log.info("");
    if (cc.initLoadPaths.size() == cc.baseClassifiers.size()) {
      for (int i = 0 ; i < cc.initLoadPaths.size() ; i++) {
        log.info("baseClassifiers index "+i+" : "+cc.initLoadPaths.get(i));
      }
    } else {
      for (int i = 0 ; i < cc.initLoadPaths.size() ; i++) {
        log.info("baseClassifiers index "+i);
      }
    }
    log.info("");
    log.info("combinationMode: "+cc.combinationMode);
    log.info("");
  }

  /**
   * Some basic testing of the ClassifierCombiner.
   *
   * @param args Command-line arguments as properties: -loadClassifier1 serializedFile -loadClassifier2 serializedFile
   * @throws Exception If IO or serialization error loading classifiers
   */
  public static void main(String[] args) throws Exception {
    Properties props = StringUtils.argsToProperties(args);
    ClassifierCombiner ec = new ClassifierCombiner(props);

    log.info(ec.classifyToString("Marketing : Sony Hopes to Win Much Bigger Market For Wide Range of Small-Video Products --- By Andrew B. Cohen Staff Reporter of The Wall Street Journal"));
  }

}
