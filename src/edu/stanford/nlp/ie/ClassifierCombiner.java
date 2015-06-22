package edu.stanford.nlp.ie;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ie.ner.CMMClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.pipeline.DefaultPaths;
import edu.stanford.nlp.sequences.DocumentReaderAndWriter;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;

import java.io.FileNotFoundException;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.util.*;

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
 * ms 2009: removed all NER functionality (see NERClassifierCombiner), changed code so it accepts an arbitrary number of base classifiers, removed dead code.
 *
 * @author Chris Cox
 * @author Mihai Surdeanu
 */
public class ClassifierCombiner<IN extends CoreMap & HasWord> extends AbstractSequenceClassifier<IN> {

  private static final boolean DEBUG = false;
  private List<AbstractSequenceClassifier<IN>> baseClassifiers;

  private static final String DEFAULT_AUX_CLASSIFIER_PATH="edu/stanford/nlp/models/ner/english.muc.7class.distsim.crf.ser.gz";
  private static final String DEFAULT_CLASSIFIER_PATH="edu/stanford/nlp/models/ner/english.all.3class.distsim.crf.ser.gz";

  /**
   * NORMAL means that if one classifier uses PERSON, later classifiers can't also add PERSON, for example. <br>
   * HIGH_RECALL allows later models to set PERSON as long as it doesn't clobber existing annotations.
   */
  static enum CombinationMode {
    NORMAL, HIGH_RECALL
  }

  static final CombinationMode DEFAULT_COMBINATION_MODE = CombinationMode.NORMAL;
  static final String COMBINATION_MODE_PROPERTY = "ner.combinationMode";
  final CombinationMode combinationMode;

  /**
   * @param p Properties File that specifies <code>loadClassifier</code>
   * and <code>loadAuxClassifier</code> properties or, alternatively, <code>loadClassifier[1-10]</code> properties.
   * @throws FileNotFoundException If classifier files not found
   */
  public ClassifierCombiner(Properties p) throws FileNotFoundException {
    super(p);
    this.combinationMode = extractCombinationModeSafe(p);
    String loadPath1, loadPath2;
    List<String> paths = new ArrayList<String>();

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
      loadClassifiers(paths);
    }

    //
    // second accepted setup (backward compatible): two classifier given in loadClassifier and loadAuxClassifier
    //
    else if((loadPath1 = p.getProperty("loadClassifier")) != null && (loadPath2 = p.getProperty("loadAuxClassifier")) != null){
      paths.add(loadPath1);
      paths.add(loadPath2);
      loadClassifiers(paths);
    }

    //
    // fall back strategy: use the two default paths on NLP machines
    //
    else {
      paths.add(DefaultPaths.DEFAULT_NER_THREECLASS_MODEL);
      paths.add(DefaultPaths.DEFAULT_NER_MUC_MODEL);
      loadClassifiers(paths);
    }
  }

  /** Loads a series of base classifiers from the paths specified.
   *
   * @param loadPaths Paths to the base classifiers
   * @throws FileNotFoundException If classifier files not found
   */
  public ClassifierCombiner(CombinationMode combinationMode, String... loadPaths) throws FileNotFoundException {
    super(new Properties());
    this.combinationMode = combinationMode;
    List<String> paths = new ArrayList<String>(Arrays.asList(loadPaths));
    loadClassifiers(paths);
  }

  /** Loads a series of base classifiers from the paths specified.
   *
   * @param loadPaths Paths to the base classifiers
   * @throws FileNotFoundException If classifier files not found
   */
  public ClassifierCombiner(String... loadPaths) throws FileNotFoundException {
    super(new Properties());
    this.combinationMode = DEFAULT_COMBINATION_MODE;
    List<String> paths = new ArrayList<String>(Arrays.asList(loadPaths));
    loadClassifiers(paths);
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
  }

  /**
   * Either finds COMBINATION_MODE_PROPERTY or returns a default value
   */
  public static CombinationMode extractCombinationMode(Properties p) {
    String mode = p.getProperty(COMBINATION_MODE_PROPERTY);
    if (mode == null) {
      return DEFAULT_COMBINATION_MODE;
    } else {
      return CombinationMode.valueOf(mode.toUpperCase());
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
      System.err.print("Illegal value of " + COMBINATION_MODE_PROPERTY + ": " + p.getProperty(COMBINATION_MODE_PROPERTY));
      System.err.print("  Legal values:");
      for (CombinationMode mode : CombinationMode.values()) {
        System.err.print("  " + mode);
      }
      System.err.println();
      return CombinationMode.NORMAL;
    }
  }

  private void loadClassifiers(List<String> paths) throws FileNotFoundException {
    baseClassifiers = new ArrayList<AbstractSequenceClassifier<IN>>();
    for(String path: paths){
      AbstractSequenceClassifier<IN> cls = loadClassifierFromPath(path);
      baseClassifiers.add(cls);
      if(DEBUG){
        System.err.printf("Successfully loaded classifier #%d from %s.\n", baseClassifiers.size(), path);
      }
    }
    if (baseClassifiers.size() > 0) {
      flags.backgroundSymbol = baseClassifiers.get(0).flags.backgroundSymbol;
    }
  }


  public static <INN extends CoreMap & HasWord> AbstractSequenceClassifier<INN> loadClassifierFromPath(String path)
      throws FileNotFoundException {
    //try loading as a CRFClassifier
    try {
       return ErasureUtils.uncheckedCast(CRFClassifier.getClassifier(path));
    } catch (Exception e) {
      e.printStackTrace();
    }
    //try loading as a CMMClassifier
    try {
      return ErasureUtils.uncheckedCast(CMMClassifier.getClassifier(path));
    } catch (Exception e) {
      //fail
      //System.err.println("Couldn't load classifier from path :"+path);
      FileNotFoundException fnfe = new FileNotFoundException();
      fnfe.initCause(e);
      throw fnfe;
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
    List<Set<String>> baseLabels = new ArrayList<Set<String>>();
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
        System.err.println("mergeDocuments: Using classifier #" + i + " for " + baseLabels.get(i));
      System.err.println("mergeDocuments: Background symbol is " + background);

      System.err.println("Base model outputs:");
      for( int i = 0; i < baseDocuments.size(); i ++){
        System.err.printf("Output of model #%d:", i);
        for (IN l : baseDocuments.get(i)) {
          System.err.print(' ');
          System.err.print(l.get(CoreAnnotations.AnswerAnnotation.class));
        }
        System.err.println();
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
      System.err.print("Output of combined model:");
      for (IN l: mainDocument) {
        System.err.print(' ');
        System.err.print(l.get(CoreAnnotations.AnswerAnnotation.class));
      }
      System.err.println();
      System.err.println();
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
    Collection<INN> constituents = new ArrayList<INN>();

    Iterator<INN> auxIterator = auxDocument.listIterator();

    for (INN wMain : mainDocument) {
      String mainAnswer = wMain.get(CoreAnnotations.AnswerAnnotation.class);
      INN wAux = auxIterator.next();
      String auxAnswer = wAux.get(CoreAnnotations.AnswerAnnotation.class);
      boolean insideMainTag = !mainAnswer.equals(background);

      /* if the auxiliary classifier gave it one of the labels unique to
         auxClassifier, we might set the mainLabel to that. */
      if (auxLabels.contains(auxAnswer)) {
        if ( ! prevAnswer.equals(auxAnswer) && ! prevAnswer.equals(background)) {
          if (auxTagValid){
            for (INN wi : constituents) {
              wi.set(CoreAnnotations.AnswerAnnotation.class, prevAnswer);
            }
          }
          auxTagValid = true;
          constituents = new ArrayList<INN>();
        }
        insideAuxTag = true;
        if (insideMainTag) { auxTagValid = false; }
        prevAnswer = auxAnswer;
        constituents.add(wMain);
      } else {
        if (insideAuxTag) {
          if (auxTagValid){
            for (INN wi : constituents) {
              wi.set(CoreAnnotations.AnswerAnnotation.class, prevAnswer);
            }
          }
          constituents = new ArrayList<INN>();
        }
        insideAuxTag=false;
        auxTagValid = true;
        prevAnswer = background;
      }
    }
    // deal with a sequence final auxLabel
    if (auxTagValid){
      for (INN wi : constituents) {
        wi.set(CoreAnnotations.AnswerAnnotation.class, prevAnswer);
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
    List<List<IN>> baseOutputs = new ArrayList<List<IN>>();

    // the first base model works in place, modifying the original tokens
    List<IN> output = baseClassifiers.get(0).classifySentence(tokens);
    // classify(List<IN>) is supposed to work in place, so add AnswerAnnotation to tokens!
    for (int i = 0, sz = output.size(); i < sz; i++) {
      tokens.get(i).set(CoreAnnotations.AnswerAnnotation.class, output.get(i).get(CoreAnnotations.AnswerAnnotation.class));
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
    List<IN> finalAnswer = mergeDocuments(baseOutputs);

    return finalAnswer;
  }


  @SuppressWarnings("unchecked")
  @Override
  public void train(Collection<List<IN>> docs,
                    DocumentReaderAndWriter<IN> readerAndWriter) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void printProbsDocument(List<IN> document) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void serializeClassifier(String serializePath) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void loadClassifier(ObjectInputStream in, Properties props) throws IOException, ClassCastException, ClassNotFoundException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<IN> classifyWithGlobalInformation(List<IN> tokenSeq, CoreMap doc, CoreMap sent) {
    return classify(tokenSeq);
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

    System.err.println(ec.classifyToString("Marketing : Sony Hopes to Win Much Bigger Market For Wide Range of Small-Video Products --- By Andrew B. Cohen Staff Reporter of The Wall Street Journal"));
  }

}
