// CRFClassifier -- a probabilistic (CRF) sequence model, mainly used for NER.
// Copyright (c) 2002-2016 The Board of Trustees of
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
// along with this program; if not, write to the Free Software Foundation,
// Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    Support/Questions: java-nlp-user@lists.stanford.edu
//    Licensing: java-nlp-support@lists.stanford.edu

package edu.stanford.nlp.ie.crf;

import edu.stanford.nlp.ie.*;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.optimization.*;
import edu.stanford.nlp.optimization.Function;
import edu.stanford.nlp.sequences.*;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

/**
 * Class for sequence classification using a Conditional Random Field model.
 * The code has functionality for different document formats, but when
 * using the standard {@link edu.stanford.nlp.sequences.ColumnDocumentReaderAndWriter} for training
 * or testing models, input files are expected to
 * be one token per line with the columns indicating things like the word,
 * POS, chunk, and answer class.  The default for
 * {@code ColumnDocumentReaderAndWriter} training data is 3 column input,
 * with the columns containing a word, its POS, and its gold class, but
 * this can be specified via the {@code map} property.
 * <p>
 * When run on a file with {@code -textFile} or {@code -textFiles},
 * the file is assumed to be plain English text (or perhaps simple HTML/XML),
 * and a reasonable attempt is made at English tokenization by
 * {@link PlainTextDocumentReaderAndWriter}.  The class used to read
 * the text can be changed with -plainTextDocumentReaderAndWriter.
 * Extra options can be supplied to the tokenizer using the
 * -tokenizerOptions flag.
 * <p>
 * To read from stdin, use the flag -readStdin.  The same
 * reader/writer will be used as for -textFile.
 * <p>
 * <b>Typical command-line usage</b>
 * <p>
 * For running a trained model with a provided serialized classifier on a
 * text file:
 * <p>
 * {@code java -mx500m edu.stanford.nlp.ie.crf.CRFClassifier -loadClassifier
 * conll.ner.gz -textFile sampleSentences.txt }
 * <p>
 * When specifying all parameters in a properties file (train, test, or
 * runtime):
 * <p>
 * {@code java -mx1g edu.stanford.nlp.ie.crf.CRFClassifier -prop propFile }
 * <p>
 * To train and test a simple NER model from the command line:
 * <p>
 * {@code java -mx1g edu.stanford.nlp.ie.crf.CRFClassifier -trainFile trainFile -testFile testFile -macro > output }
 * <p>
 * To train with multiple files:
 * <p>
 * {@code java -mx1g edu.stanford.nlp.ie.crf.CRFClassifier -trainFileList file1,file2,... -testFile testFile -macro > output }
 * <p>
 * To test on multiple files, use the -testFiles option and a comma
 * separated list.
 * <p>
 * Features are defined by a {@link edu.stanford.nlp.sequences.FeatureFactory}.
 * {@link NERFeatureFactory} is used by default, and you should look
 * there for feature templates and properties or flags that will cause
 * certain features to be used when training an NER classifier. There
 * are also various feature factories for Chinese word segmentation
 * such as {@link edu.stanford.nlp.wordseg.ChineseSegmenterFeatureFactory}.
 * Features are specified either
 * by a Properties file (which is the recommended method) or by flags on the
 * command line. The flags are read into a {@link SeqClassifierFlags} object,
 * which the user need not be concerned with, unless wishing to add new
 * features.
 * <p>
 * CRFClassifier may also be used programmatically. When creating
 * a new instance, you <i>must</i> specify a Properties object. You may then
 * call train methods to train a classifier, or load a classifier. The other way
 * to get a CRFClassifier is to deserialize one via the static
 * {@link CRFClassifier#getClassifier(String)} methods, which return a
 * deserialized classifier. You may then tag (classify the items of) documents
 * using either the assorted {@code classify()} methods here or the additional
 * ones in {@link AbstractSequenceClassifier}.
 * Probabilities assigned by the CRF can be interrogated using either the
 * {@code printProbsDocument()} or {@code getCliqueTrees()} methods.
 *
 * @author Jenny Finkel
 * @author Sonal Gupta (made the class generic)
 * @author Mengqiu Wang (LOP implementation and non-linear CRF implementation)
 */
public class CRFClassifier<IN extends CoreMap> extends AbstractSequenceClassifier<IN>  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(CRFClassifier.class);

  // TODO(mengqiu) need to move the embedding lookup and capitalization features into a FeatureFactory

  List<Index<CRFLabel>> labelIndices;
  Index<String> tagIndex;
  private Pair<double[][], double[][]> entityMatrices;

  CliquePotentialFunction cliquePotentialFunction;
  HasCliquePotentialFunction cliquePotentialFunctionHelper;

  /** Parameter weights of the classifier.  weights[featureIndex][labelIndex] */
  float[][] weights;

  /** index the features of CRF */
  Index<String> featureIndex;
  /** caches the featureIndex */
  int[] map;
  Random random = new Random(2147483647L);
  Index<Integer> nodeFeatureIndicesMap;
  Index<Integer> edgeFeatureIndicesMap;

  private Map<String, double[]> embeddings; // = null;

  /**
   * Name of default serialized classifier resource to look for in a jar file.
   */
  public static final String DEFAULT_CLASSIFIER = "edu/stanford/nlp/models/ner/english.all.3class.distsim.crf.ser.gz";
  private static final boolean VERBOSE = false;

  /**
   * Fields for grouping features
   */
  private Pattern suffixPatt = Pattern.compile(".+?((?:-[A-Z]+)+)\\|.*C");
  private Index<String> templateGroupIndex;
  private Map<Integer, Integer> featureIndexToTemplateIndex;

  // Label dictionary for fast decoding
  private LabelDictionary labelDictionary;

  // List selftraindatums = new ArrayList();

  protected CRFClassifier() {
    super(new SeqClassifierFlags());
  }

  public CRFClassifier(Properties props) {
    super(props);
  }

  public CRFClassifier(SeqClassifierFlags flags) {
    super(flags);
  }

  /**
   * Makes a copy of the crf classifier
   */
  public CRFClassifier(CRFClassifier<IN> crf) {
    super(crf.flags);
    this.windowSize = crf.windowSize;
    this.featureFactories = crf.featureFactories;
    this.pad = crf.pad;
    if (crf.knownLCWords == null) {
      this.knownLCWords = new MaxSizeConcurrentHashSet<>(crf.flags.maxAdditionalKnownLCWords);
    } else {
      this.knownLCWords = new MaxSizeConcurrentHashSet<>(crf.knownLCWords);
      this.knownLCWords.setMaxSize(this.knownLCWords.size() + crf.flags.maxAdditionalKnownLCWords);
    }
    this.featureIndex = (crf.featureIndex != null) ? new HashIndex<>(crf.featureIndex.objectsList()) : null;
    this.classIndex = (crf.classIndex != null) ? new HashIndex<>(crf.classIndex.objectsList()) : null;
    if (crf.labelIndices != null) {
      this.labelIndices = new ArrayList<>(crf.labelIndices.size());
      for (int i = 0; i < crf.labelIndices.size(); i++) {
        this.labelIndices.add((crf.labelIndices.get(i) != null) ? new HashIndex<>(crf.labelIndices.get(i).objectsList()) : null);
      }
    } else {
      this.labelIndices = null;
    }
    this.cliquePotentialFunction = crf.cliquePotentialFunction;
  }

  /**
   * Returns the total number of weights associated with this classifier.
   *
   * @return number of weights
   */
  public int getNumWeights() {
    if (weights == null) return 0;
    int numWeights = 0;
    for (float[] wts : weights) {
      numWeights += wts.length;
    }
    return numWeights;
  }

  /**
   * Get index of featureType for feature indexed by i. (featureType index is
   * used to index labelIndices to get labels.)
   *
   * @param i Feature index
   * @return index of featureType
   */
  private int getFeatureTypeIndex(int i) {
    return getFeatureTypeIndex(featureIndex.get(i));
  }

  /**
   * Get index of featureType for feature based on the feature string
   * (featureType index used to index labelIndices to get labels)
   *
   * @param feature Feature string
   * @return index of featureType
   */
  private static int getFeatureTypeIndex(String feature) {
    if (feature.endsWith("|C")) {
      return 0;
    } else if (feature.endsWith("|CpC")) {
      return 1;
    } else if (feature.endsWith("|Cp2C")) {
      return 2;
    } else if (feature.endsWith("|Cp3C")) {
      return 3;
    } else if (feature.endsWith("|Cp4C")) {
      return 4;
    } else if (feature.endsWith("|Cp5C")) {
      return 5;
    } else {
      throw new RuntimeException("Unknown feature type " + feature);
    }
  }

  /**
   * Scales the weights of this CRFClassifier by the specified weight.
   *
   * @param scale The scale to multiply by
   */
  public void scaleWeights(double scale) {
    for (int i = 0; i < weights.length; i++) {
      for (int j = 0; j < weights[i].length; j++) {
        weights[i][j] *= scale;
      }
    }
  }

  /**
   * Combines weights from another crf (scaled by weight) into this CRF's
   * weights (assumes that this CRF's indices have already been updated to
   * include features/labels from the other crf)
   *
   * @param crf Other CRF whose weights to combine into this CRF
   * @param weight Amount to scale the other CRF's weights by
   */
  private void combineWeights(CRFClassifier<IN> crf, double weight) {
    int numFeatures = featureIndex.size();
    int oldNumFeatures = weights.length;

    // Create a map of other crf labels to this crf labels
    Map<CRFLabel, CRFLabel> crfLabelMap = Generics.newHashMap();
    for (int i = 0; i < crf.labelIndices.size(); i++) {
      for (int j = 0; j < crf.labelIndices.get(i).size(); j++) {
        CRFLabel labels = crf.labelIndices.get(i).get(j);
        int[] newLabelIndices = new int[i + 1];
        for (int ci = 0; ci <= i; ci++) {
          String classLabel = crf.classIndex.get(labels.getLabel()[ci]);
          newLabelIndices[ci] = this.classIndex.indexOf(classLabel);
        }
        CRFLabel newLabels = new CRFLabel(newLabelIndices);
        crfLabelMap.put(labels, newLabels);
        int k = this.labelIndices.get(i).indexOf(newLabels); // IMPORTANT: the indexing is needed, even when not printed out!
        // log.info("LabelIndices " + i + " " + labels + ": " + j +
        // " mapped to " + k);
      }
    }

    // Create map of featureIndex to featureTypeIndex
    map = new int[numFeatures];
    for (int i = 0; i < numFeatures; i++) {
      map[i] = getFeatureTypeIndex(i);
    }

    // Create new weights
    float[][] newWeights = new float[numFeatures][];
    for (int i = 0; i < numFeatures; i++) {
      int length = labelIndices.get(map[i]).size();
      newWeights[i] = new float[length];
      if (i < oldNumFeatures) {
        assert (length >= weights[i].length);
        System.arraycopy(weights[i], 0, newWeights[i], 0, weights[i].length);
      }
    }
    weights = newWeights;

    // Get original weight indices from other crf and weight them in
    // depending on the type of the feature, different number of weights is
    // associated with it
    for (int i = 0; i < crf.weights.length; i++) {
      String feature = crf.featureIndex.get(i);
      int newIndex = featureIndex.indexOf(feature);
      // Check weights are okay dimension
      if (weights[newIndex].length < crf.weights[i].length) {
        throw new RuntimeException("Incompatible CRFClassifier: weight length mismatch for feature " + newIndex + ": "
            + featureIndex.get(newIndex) + " (also feature " + i + ": " + crf.featureIndex.get(i) + ") " + ", len1="
            + weights[newIndex].length + ", len2=" + crf.weights[i].length);
      }
      int featureTypeIndex = map[newIndex];
      for (int j = 0; j < crf.weights[i].length; j++) {
        CRFLabel labels = crf.labelIndices.get(featureTypeIndex).get(j);
        CRFLabel newLabels = crfLabelMap.get(labels);
        int k = this.labelIndices.get(featureTypeIndex).indexOf(newLabels);
        weights[newIndex][k] += crf.weights[i][j] * weight;
      }
    }
  }

  /**
   * Combines weighted crf with this crf.
   *
   * @param crf Other CRF whose weights to combine into this CRF
   * @param weight Amount to scale the other CRF's weights by
   */
  public void combine(CRFClassifier<IN> crf, double weight) {
    Timing timer = new Timing();

    // Check the CRFClassifiers are compatible
    if (!this.pad.equals(crf.pad)) {
      throw new RuntimeException("Incompatible CRFClassifier: pad does not match");
    }
    if (this.windowSize != crf.windowSize) {
      throw new RuntimeException("Incompatible CRFClassifier: windowSize does not match");
    }
    if (this.labelIndices.size() != crf.labelIndices.size()) {
      // Should match since this should be same as the windowSize
      throw new RuntimeException("Incompatible CRFClassifier: labelIndices length does not match");
    }
    this.classIndex.addAll(crf.classIndex.objectsList());

    // Combine weights of the other classifier with this classifier,
    // weighing the other classifier's weights by weight
    // First merge the feature indices
    int oldNumFeatures1 = this.featureIndex.size();
    int oldNumFeatures2 = crf.featureIndex.size();
    int oldNumWeights1 = this.getNumWeights();
    int oldNumWeights2 = crf.getNumWeights();
    this.featureIndex.addAll(crf.featureIndex.objectsList());
    this.knownLCWords.addAll(crf.knownLCWords);
    assert (weights.length == oldNumFeatures1);

    // Combine weights of this classifier with other classifier
    for (int i = 0; i < labelIndices.size(); i++) {
      this.labelIndices.get(i).addAll(crf.labelIndices.get(i).objectsList());
    }
    log.info("Combining weights: will automatically match labelIndices");
    combineWeights(crf, weight);

    int numFeatures = featureIndex.size();
    int numWeights = getNumWeights();
    long elapsedMs = timer.stop();
    log.info("numFeatures: orig1=" + oldNumFeatures1 + ", orig2=" + oldNumFeatures2 + ", combined="
        + numFeatures);
    log.info("numWeights: orig1=" + oldNumWeights1 + ", orig2=" + oldNumWeights2 + ", combined=" + numWeights);
    log.info("Time to combine CRFClassifier: " + Timing.toSecondsString(elapsedMs) + " seconds");
  }

  public void dropFeaturesBelowThreshold(double threshold) {
    Index<String> newFeatureIndex = new HashIndex<>();
    for (int i = 0; i < weights.length; i++) {
      double smallest = weights[i][0];
      double biggest = weights[i][0];
      for (int j = 1; j < weights[i].length; j++) {
        if (weights[i][j] > biggest) {
          biggest = weights[i][j];
        }
        if (weights[i][j] < smallest) {
          smallest = weights[i][j];
        }
        if (biggest - smallest > threshold) {
          newFeatureIndex.add(featureIndex.get(i));
          break;
        }
      }
    }

    int[] newMap = new int[newFeatureIndex.size()];
    for (int i = 0; i < newMap.length; i++) {
      int index = featureIndex.indexOf(newFeatureIndex.get(i));
      newMap[i] = map[index];
    }
    map = newMap;
    featureIndex = newFeatureIndex;
  }

  /**
   * Convert a document List into arrays storing the data features and labels.
   * This is used at test time.
   *
   * @param document Testing documents
   * @return A Triple, where the first element is an int[][][] representing the
   *         data, the second element is an int[] representing the labels, and
   *         the third element is a double[][][] representing the feature values (optionally null)
   */
  public Triple<int[][][], int[], double[][][]> documentToDataAndLabels(List<IN> document) {
    int docSize = document.size();
    // first index is position in the document also the index of the
    // clique/factor table
    // second index is the number of elements in the clique/window these
    // features are for (starting with last element)
    // third index is position of the feature in the array that holds them.
    // An element in data[j][k][m] is the feature index of the mth feature occurring in
    // position k of the jth clique
    int[][][] data = new int[docSize][windowSize][];
    double[][][] featureVals = new double[docSize][windowSize][];
    // index is the position in the document.
    // element in labels[j] is the index of the correct label (if it exists) at
    // position j of document
    int[] labels = new int[docSize];

    if (flags.useReverse) {
      Collections.reverse(document);
    }

    // log.info("docSize:"+docSize);
    for (int j = 0; j < docSize; j++) {
      int[][] data_j = data[j];
      double[][] featureVals_j = featureVals[j];
      CRFDatum<Collection<String>, CRFLabel> d = makeDatum(document, j, featureFactories);

      List<Collection<String>> features = d.asFeatures();
      List<double[]> featureValList = d.asFeatureVals();
      for (int k = 0, fSize = features.size(); k < fSize; k++) {
        Collection<String> cliqueFeatures = features.get(k);
        int[] data_jk = data_j[k] = new int[cliqueFeatures.size()];
        if(featureValList != null && k < featureValList.size()) { // CRFBiasedClassifier.makeDatum causes null
          featureVals_j[k] = featureValList.get(k);
        }
        int m = 0;
        for (String feature : cliqueFeatures) {
          int index = featureIndex.indexOf(feature);
          if (index >= 0) {
            data_jk[m] = index;
            m++;
          } else {
            // this is where we end up when we do feature threshold cutoffs
          }
        }

        if (m < data_j[k].length) {
          data_j[k] = Arrays.copyOf(data_j[k], m);
          if (featureVals_j[k] != null) {
            featureVals_j[k] = Arrays.copyOf(featureVals_j[k], m);
          }
        }
      }

      IN wi = document.get(j);
      labels[j] = classIndex.indexOf(wi.get(CoreAnnotations.AnswerAnnotation.class));
    }

    if (flags.useReverse) {
      Collections.reverse(document);
    }

    return new Triple<>(data, labels, featureVals);
  }

  public void printLabelInformation(String testFile, DocumentReaderAndWriter<IN> readerAndWriter) throws Exception {
    ObjectBank<List<IN>> documents = makeObjectBankFromFile(testFile, readerAndWriter);
    for (List<IN> document : documents) {
      printLabelValue(document);
    }
  }

  public void printLabelValue(List<IN> document) {
    if (flags.useReverse) {
      Collections.reverse(document);
    }

    NumberFormat nf = new DecimalFormat();

    List<String> classes = new ArrayList<>();
    for (int i = 0; i < classIndex.size(); i++) {
      classes.add(classIndex.get(i));
    }
    String[] columnHeaders = classes.toArray(new String[classes.size()]);

    // log.info("docSize:"+docSize);
    for (int j = 0; j < document.size(); j++) {

      System.out.println("--== " + document.get(j).get(CoreAnnotations.TextAnnotation.class) + " ==--");

      List<String[]> lines = new ArrayList<>();
      List<String> rowHeaders = new ArrayList<>();
      List<String> line = new ArrayList<>();

      for (int p = 0; p < labelIndices.size(); p++) {
        if (j + p >= document.size()) {
          continue;
        }
        CRFDatum<Collection<String>, CRFLabel> d = makeDatum(document, j + p, featureFactories);

        List<Collection<String>> features = d.asFeatures();
        for (int k = p, fSize = features.size(); k < fSize; k++) {
          Collection<String> cliqueFeatures = features.get(k);
          for (String feature : cliqueFeatures) {
            int index = featureIndex.indexOf(feature);
            if (index >= 0) {
              // line.add(feature+"["+(-p)+"]");
              rowHeaders.add(feature + '[' + (-p) + ']');
              double[] values = new double[labelIndices.get(0).size()];
              for (CRFLabel label : labelIndices.get(k)) {
                int[] l = label.getLabel();
                double v = weights[index][labelIndices.get(k).indexOf(label)];
                values[l[l.length - 1 - p]] += v;
              }
              for (double value : values) {
                line.add(nf.format(value));
              }
              lines.add(line.toArray(new String[line.size()]));
              line = new ArrayList<>();
            }
          }
        }
        // lines.add(Collections.<String>emptyList());
        System.out.println(StringUtils.makeTextTable(lines.toArray(new String[lines.size()][0]), rowHeaders
                .toArray(new String[rowHeaders.size()]), columnHeaders, 0, 1, true));
        System.out.println();
      }
      // log.info(edu.stanford.nlp.util.StringUtils.join(lines,"\n"));
    }

    if (flags.useReverse) {
      Collections.reverse(document);
    }
  }

  /**
   * Convert an ObjectBank to arrays of data features and labels.
   * This version is used at training time.
   *
   * @return A Triple, where the first element is an int[][][][] representing the
   *         data, the second element is an int[][] representing the labels, and
   *         the third element is a double[][][][] representing the feature values
   *         which could be optionally left as null.
   */
  public Triple<int[][][][], int[][], double[][][][]> documentsToDataAndLabels(Collection<List<IN>> documents) {

    // first index is the number of the document
    // second index is position in the document also the index of the
    // clique/factor table
    // third index is the number of elements in the clique/window these features
    // are for (starting with last element)
    // fourth index is position of the feature in the array that holds them
    // element in data[i][j][k][m] is the index of the mth feature occurring in
    // position k of the jth clique of the ith document
    // int[][][][] data = new int[documentsSize][][][];
    int numDocs = documents.size();
    List<int[][][]> data = new ArrayList<>(numDocs);
    List<double[][][]> featureVal = flags.useEmbedding ? new ArrayList<>(numDocs) : null;

    // first index is the number of the document
    // second index is the position in the document
    // element in labels[i][j] is the index of the correct label (if it exists)
    // at position j in document i
    // int[][] labels = new int[documentsSize][];
    List<int[]> labels = new ArrayList<>(numDocs);

    int numDatums = 0;

    for (List<IN> doc : documents) {
      Triple<int[][][], int[], double[][][]> docTriple = documentToDataAndLabels(doc);
      data.add(docTriple.first());
      labels.add(docTriple.second());
      if (flags.useEmbedding)
        featureVal.add(docTriple.third());
      numDatums += doc.size();
    }

    if (labels.size() != numDocs || data.size() != numDocs) {
      throw new AssertionError("Inexplicable miscalculation in the size of some arrays");
    }

    log.info("numClasses: " + classIndex.size() + ' ' + classIndex);
    log.info("numDocuments: " + data.size());
    log.info("numDatums: " + numDatums);
    log.info("numFeatures: " + featureIndex.size());
    printFeatures();

    double[][][][] featureValArr = null;
    if (flags.useEmbedding)
      featureValArr = featureVal.toArray(new double[data.size()][][][]);


    return new Triple<>(
            data.toArray(new int[data.size()][][][]),
            labels.toArray(new int[labels.size()][]),
            featureValArr);
  }

  /**
   * Convert an ObjectBank to corresponding collection of data features and
   * labels. This version is used at test time.
   *
   * @return A List of pairs, one for each document, where the first element is
   *         an int[][][] representing the data and the second element is an
   *         int[] representing the labels.
   */
  public List<Triple<int[][][], int[], double[][][]>> documentsToDataAndLabelsList(Collection<List<IN>> documents) {
    int numDatums = 0;

    List<Triple<int[][][], int[], double[][][]>> docList = new ArrayList<>(documents.size());
    for (List<IN> doc : documents) {
      Triple<int[][][], int[], double[][][]> docTriple = documentToDataAndLabels(doc);
      docList.add(docTriple);
      numDatums += doc.size();
    }

    log.info("numClasses: " + classIndex.size() + ' ' + classIndex);
    log.info("numDocuments: " + docList.size());
    log.info("numDatums: " + numDatums);
    log.info("numFeatures: " + featureIndex.size());
    return docList;
  }

  protected void printFeatures() {
    if (flags.printFeatures == null) {
      return;
    }
    try {
      String enc = flags.inputEncoding;
      if (flags.inputEncoding == null) {
        log.info("flags.inputEncoding doesn't exist, using UTF-8 as default");
        enc = "UTF-8";
      }

      PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream("features-" + flags.printFeatures
          + ".txt"), enc), true);
      for (String feat : featureIndex) {
        pw.println(feat);
      }
      pw.close();
    } catch (IOException ioe) {
      ioe.printStackTrace();
    }
  }

  /**
   * This routine builds the {@code labelIndices} which give the
   * empirically legal label sequences (of length (order) at most
   * {@code windowSize}) and the {@code classIndex}, which indexes
   * known answer classes.
   *
   * @param ob The training data: Read from an ObjectBank, each item in it is a
   *          {@code List<CoreLabel>}.
   */
  protected void makeAnswerArraysAndTagIndex(Collection<List<IN>> ob) {
    // TODO: slow?
    boolean useFeatureCountThresh = flags.featureCountThresh > 1;

    Set<String>[] featureIndices = new HashSet[windowSize];
    Map<String, Integer>[] featureCountIndices = null;
    for (int i = 0; i < windowSize; i++) {
      featureIndices[i] = Generics.newHashSet();
    }
    if (useFeatureCountThresh) {
      featureCountIndices = new HashMap[windowSize];
      for (int i = 0; i < windowSize; i++) {
        featureCountIndices[i] = Generics.newHashMap();
      }
    }

    labelIndices = new ArrayList<>(windowSize);
    for (int i = 0; i < windowSize; i++) {
      labelIndices.add(new HashIndex<>());
    }

    Index<CRFLabel> labelIndex = labelIndices.get(windowSize - 1);

    if (classIndex == null)
      classIndex = new HashIndex<>();
    // classIndex.add("O");
    classIndex.add(flags.backgroundSymbol);

    Set<String>[] seenBackgroundFeatures = new HashSet[2];
    seenBackgroundFeatures[0] = Generics.newHashSet();
    seenBackgroundFeatures[1] = Generics.newHashSet();

    int wordCount = 0;

    if (flags.labelDictionaryCutoff > 0) {
      this.labelDictionary = new LabelDictionary();
    }

    for (List<IN> doc : ob) {
      if (flags.useReverse) {
        Collections.reverse(doc);
      }

      // create the full set of labels in classIndex
      // note: update to use addAll later
      for (IN token : doc) {
        wordCount++;
        String ans = token.get(CoreAnnotations.AnswerAnnotation.class);
        if (ans == null || ans.isEmpty()) {
          throw new IllegalArgumentException("Word " + wordCount + " (\"" + token.get(CoreAnnotations.TextAnnotation.class) + "\") has a blank answer");
        }
        classIndex.add(ans);
        if (labelDictionary != null) {
          String observation = token.get(CoreAnnotations.TextAnnotation.class);
          labelDictionary.increment(observation, ans);
        }
      }

      for (int j = 0, docSize = doc.size(); j < docSize; j++) {
        CRFDatum<Collection<String>, CRFLabel> d = makeDatum(doc, j, featureFactories);
        labelIndex.add(d.label());

        List<Collection<String>> features = d.asFeatures();
        for (int k = 0, fSize = features.size(); k < fSize; k++) {
          Collection<String> cliqueFeatures = features.get(k);
          if (k < 2 && flags.removeBackgroundSingletonFeatures) {
            String ans = doc.get(j).get(CoreAnnotations.AnswerAnnotation.class);
            boolean background = ans.equals(flags.backgroundSymbol);
            if (k == 1 && j > 0 && background) {
              ans = doc.get(j - 1).get(CoreAnnotations.AnswerAnnotation.class);
              background = ans.equals(flags.backgroundSymbol);
            }
            if (background) {
              for (String f : cliqueFeatures) {
                if (useFeatureCountThresh) {
                  if (!featureCountIndices[k].containsKey(f)) {
                    if (seenBackgroundFeatures[k].contains(f)) {
                      seenBackgroundFeatures[k].remove(f);
                      featureCountIndices[k].put(f, 1);
                    } else {
                      seenBackgroundFeatures[k].add(f);
                    }
                  }
                } else {
                  if (!featureIndices[k].contains(f)) {
                    if (seenBackgroundFeatures[k].contains(f)) {
                      seenBackgroundFeatures[k].remove(f);
                      featureIndices[k].add(f);
                    } else {
                      seenBackgroundFeatures[k].add(f);
                    }
                  }
                }
              }
            } else {
              seenBackgroundFeatures[k].removeAll(cliqueFeatures);
              if (useFeatureCountThresh) {
                Map<String, Integer> fCountIndex = featureCountIndices[k];
                for (String f: cliqueFeatures) {
                  if (fCountIndex.containsKey(f))
                    fCountIndex.put(f, fCountIndex.get(f)+1);
                  else
                    fCountIndex.put(f, 1);
                }
              } else {
                featureIndices[k].addAll(cliqueFeatures);
              }
            }
          } else {
            if (useFeatureCountThresh) {
              Map<String, Integer> fCountIndex = featureCountIndices[k];
              for (String f: cliqueFeatures) {
                if (fCountIndex.containsKey(f))
                  fCountIndex.put(f, fCountIndex.get(f)+1);
                else
                  fCountIndex.put(f, 1);
              }
            } else {
              featureIndices[k].addAll(cliqueFeatures);
            }
          }
        }
      }

      if (flags.useReverse) {
        Collections.reverse(doc);
      }
    }

    if (useFeatureCountThresh) {
      int numFeatures = 0;
      for (int i = 0; i < windowSize; i++) {
        numFeatures += featureCountIndices[i].size();
      }
      log.info("Before feature count thresholding, numFeatures = " + numFeatures);
      for (int i = 0; i < windowSize; i++) {
        for (Iterator<Map.Entry<String, Integer>> it = featureCountIndices[i].entrySet().iterator(); it.hasNext(); ) {
          Map.Entry<String, Integer> entry = it.next();
          if(entry.getValue() < flags.featureCountThresh) {
            it.remove();
          }
        }
        featureIndices[i].addAll(featureCountIndices[i].keySet());
        featureCountIndices[i] = null;
      }
    }

    int numFeatures = 0;
    for (int i = 0; i < windowSize; i++) {
      numFeatures += featureIndices[i].size();
    }
    log.info("numFeatures = " + numFeatures);

    featureIndex = new HashIndex<>();
    map = new int[numFeatures];

    if (flags.groupByFeatureTemplate) {
      templateGroupIndex = new HashIndex<>();
      featureIndexToTemplateIndex = new HashMap<>();
    }

    for (int i = 0; i < windowSize; i++) {
      Index<Integer> featureIndexMap = new HashIndex<>();

      featureIndex.addAll(featureIndices[i]);
      for (String str : featureIndices[i]) {
        int index = featureIndex.indexOf(str);
        map[index] = i;
        featureIndexMap.add(index);

        // grouping features by template
        if (flags.groupByFeatureTemplate) {
          Matcher m = suffixPatt.matcher(str);
          String groupSuffix = (m.matches() ? m.group(1) : "NoTemplate") + "-c:" + i;
          int groupIndex = templateGroupIndex.addToIndex(groupSuffix);
          featureIndexToTemplateIndex.put(index, groupIndex);
        }
      }
      // todo [cdm 2014]: Talk to Mengqiu about this; it seems like it only supports first order CRF
      if (i == 0) {
        nodeFeatureIndicesMap = featureIndexMap;
        // log.info("setting nodeFeatureIndicesMap, size="+nodeFeatureIndicesMap.size());
      } else {
        edgeFeatureIndicesMap = featureIndexMap;
        // log.info("setting edgeFeatureIndicesMap, size="+edgeFeatureIndicesMap.size());
      }
    }

    if (flags.numOfFeatureSlices > 0) {
      log.info("Taking " + flags.numOfFeatureSlices + " out of " + flags.totalFeatureSlice + " slices of node features for training");
      pruneNodeFeatureIndices(flags.totalFeatureSlice, flags.numOfFeatureSlices);
    }

    if (flags.useObservedSequencesOnly) {
      for (int i = 0, liSize = labelIndex.size(); i < liSize; i++) {
        CRFLabel label = labelIndex.get(i);
        for (int j = windowSize - 2; j >= 0; j--) {
          label = label.getOneSmallerLabel();
          labelIndices.get(j).add(label);
        }
      }
    } else {
      for (int i = 0; i < labelIndices.size(); i++) {
        labelIndices.set(i, allLabels(i + 1, classIndex));
      }
    }

    if (VERBOSE) {
      for (int i = 0, fiSize = featureIndex.size(); i < fiSize; i++) {
        System.out.println(i + ": " + featureIndex.get(i));
      }
    }
    if (labelDictionary != null) {
      labelDictionary.lock(flags.labelDictionaryCutoff, classIndex);
    }
  }

  protected static Index<CRFLabel> allLabels(int window, Index<String> classIndex) {
    int[] label = new int[window];
    int numClasses = classIndex.size();
    Index<CRFLabel> labelIndex = new HashIndex<>();
    OUTER: while (true) {
      CRFLabel l = new CRFLabel(label);
      labelIndex.add(l);
      label = Arrays.copyOf(label, window);
      for (int j = 0; j < label.length; j++) {
        label[j]++;
        if (label[j] < numClasses) break;
        label[j] = 0;
        if (j == label.length - 1) break OUTER;
      }
    }
    return labelIndex;
  }

  /**
   * Makes a CRFDatum by producing features and a label from input data at a
   * specific position, using the provided factory.
   *
   * @param info The input data. Particular feature factories might look for arbitrary keys in the IN items.
   * @param loc The position to build a datum at
   * @param featureFactories The FeatureFactories to use to extract features
   * @return The constructed CRFDatum
   */
  public CRFDatum<Collection<String>, CRFLabel> makeDatum(List<IN> info, int loc,
                                                    List<FeatureFactory<IN>> featureFactories) {
    // pad.set(CoreAnnotations.AnswerAnnotation.class, flags.backgroundSymbol); // cdm: isn't this unnecessary, as this is how it's initialized in AbstractSequenceClassifier.reinit?
    PaddedList<IN> pInfo = new PaddedList<>(info, pad);

    ArrayList<Collection<String>> features = new ArrayList<>(windowSize);
    List<double[]> featureVals = flags.useEmbedding ? new ArrayList<>(1) : null;

    for (int i = 0; i < windowSize; i++) {
      List<String> featuresC = new ArrayList<>();
      if (flags.useEmbedding && i == 0) { // only activated for node features
        featureVals.add(makeDatumUsingEmbedding(info, loc, featureFactories, pInfo, featuresC));
      } else {
        FeatureFactory.eachClique(i, 0, c -> {
          for (FeatureFactory<IN> featureFactory : featureFactories) {
            featuresC.addAll(featureFactory.getCliqueFeatures(pInfo, loc, c)); //todo useless copy because of typing reasons
          }
        });
      }
      features.add(featuresC);
    }

    int[] labels = new int[windowSize];

    for (int i = 0; i < windowSize; i++) {
      String answer = pInfo.get(loc + i - windowSize + 1).get(CoreAnnotations.AnswerAnnotation.class);
      labels[i] = classIndex.indexOf(answer);
    }

    printFeatureLists(pInfo.get(loc), features);

    CRFDatum<Collection<String>, CRFLabel> d = new CRFDatum<>(features, new CRFLabel(labels), featureVals);
    // log.info(d);
    return d;
  }

  private double[] makeDatumUsingEmbedding(List<IN> info, int loc, List<FeatureFactory<IN>> featureFactories, PaddedList<IN> pInfo, Collection<String> featuresC) {
    double[] featureValArr;
    List<double[]> embeddingList = new ArrayList<>();
    int concatEmbeddingLen = 0;
    String currentWord = null;
    for (int currLoc = loc-2; currLoc <= loc+2; currLoc++) {
      double[] embedding; // Initialized in cases below // = null;
      if (currLoc >=0 && currLoc < info.size()) {
        currentWord = info.get(loc).get(CoreAnnotations.TextAnnotation.class);
        String word = currentWord.toLowerCase();
        word = word.replaceAll("(-)?\\d+(\\.\\d*)?", "0");
        embedding = embeddings.get(word);
        if (embedding == null)
          embedding = embeddings.get("UNKNOWN");
      } else {
        embedding = embeddings.get("PADDING");
      }

      for (int e = 0; e < embedding.length; e++) {
        featuresC.add("EMBEDDING-(" + (currLoc-loc) + ")-" + e);
      }

      if (flags.addCapitalFeatures) {
        int numOfCapitalFeatures = 4;
        int currLen = embedding.length;
        embedding = Arrays.copyOf(embedding, currLen + numOfCapitalFeatures);
        for (int e = 0; e < numOfCapitalFeatures; e++)
          featuresC.add("CAPITAL-(" + (currLoc-loc) + ")-" + e);

        if (currLoc >=0 && currLoc < info.size()) { // skip PADDING
          // check if word is all caps
          if (currentWord.toUpperCase().equals(currentWord))
            embedding[currLen] = 1;
          else {
            currLen += 1;
            // check if word is all lower
            if (currentWord.toLowerCase().equals(currentWord))
              embedding[currLen] = 1;
            else {
              currLen += 1;
              // check first letter cap
              if (Character.isUpperCase(currentWord.charAt(0)))
                embedding[currLen] = 1;
              else {
                currLen += 1;
                // check if at least one non-initial letter is cap
                String remainder = currentWord.substring(1);
                if (!remainder.toLowerCase().equals(remainder))
                  embedding[currLen] = 1;
              }
            }
          }
        }
      }

      embeddingList.add(embedding);
      concatEmbeddingLen += embedding.length;
    }
    double[] concatEmbedding = new double[concatEmbeddingLen];
    int currPos = 0;
    for (double[] em: embeddingList) {
      System.arraycopy(em, 0, concatEmbedding, currPos, em.length);
      currPos += em.length;
    }

    if (flags.prependEmbedding) {
      FeatureFactory.eachClique(0, 0, c -> {
        for (FeatureFactory<IN> featureFactory : featureFactories) {
          featuresC.addAll(featureFactory.getCliqueFeatures(pInfo, loc, c)); //todo useless copy because of typing reasons
        }
      });
      featureValArr = Arrays.copyOf(concatEmbedding, featuresC.size());
      Arrays.fill(featureValArr, concatEmbedding.length, featureValArr.length, 1.0);
    } else {
      featureValArr = concatEmbedding;
    }

    if (flags.addBiasToEmbedding) {
      featuresC.add("BIAS-FEATURE");
      featureValArr = Arrays.copyOf(featureValArr, featureValArr.length + 1);
      featureValArr[featureValArr.length - 1] = 1;
    }
    return featureValArr;
  }

  @Override
  public void dumpFeatures(Collection<List<IN>> docs) {
    if (flags.exportFeatures != null) {
      Timing timer = new Timing();
      CRFFeatureExporter<IN> featureExporter = new CRFFeatureExporter<>(this);
      featureExporter.printFeatures(flags.exportFeatures, docs);
      long elapsedMs = timer.stop();
      log.info("Time to export features: " + Timing.toSecondsString(elapsedMs) + " seconds");
    }
  }

  @Override
  public List<IN> classify(List<IN> document) {
    if (flags.doGibbs) {
      try {
        return classifyGibbs(document);
      } catch (Exception e) {
        throw new RuntimeException("Error running testGibbs inference!", e);
      }
    } else if (flags.crfType.equalsIgnoreCase("maxent")) {
      return classifyMaxEnt(document);
    } else {
      throw new RuntimeException("Unsupported inference type: " + flags.crfType);
    }
  }

  private List<IN> classify(List<IN> document, Triple<int[][][], int[], double[][][]> documentDataAndLabels) {
    if (flags.doGibbs) {
      try {
        return classifyGibbs(document, documentDataAndLabels);
      } catch (Exception e) {
        throw new RuntimeException("Error running testGibbs inference!", e);
      }
    } else if (flags.crfType.equalsIgnoreCase("maxent")) {
      return classifyMaxEnt(document, documentDataAndLabels);
    } else {
      throw new RuntimeException("Unsupported inference type: " + flags.crfType);
    }
  }

  /**
   * This method is supposed to be used by CRFClassifierEvaluator only, should not have global visibility.
   * The generic {@code classifyAndWriteAnswers} omits the second argument {@code documentDataAndLabels}.
   */
  void classifyAndWriteAnswers(Collection<List<IN>> documents,
                                      List<Triple<int[][][], int[], double[][][]>> documentDataAndLabels,
                                      PrintWriter printWriter,
                                      DocumentReaderAndWriter<IN> readerAndWriter) throws IOException {
    Timing timer = new Timing();

    Counter<String> entityTP = new ClassicCounter<>();
    Counter<String> entityFP = new ClassicCounter<>();
    Counter<String> entityFN = new ClassicCounter<>();
    boolean resultsCounted = true;

    int numWords = 0;
    int numDocs = 0;
    for (List<IN> doc : documents) {
      classify(doc, documentDataAndLabels.get(numDocs));
      numWords += doc.size();
      writeAnswers(doc, printWriter, readerAndWriter);
      resultsCounted = resultsCounted && countResults(doc, entityTP, entityFP, entityFN);
      numDocs++;
    }
    long millis = timer.stop();
    double wordspersec = numWords / (((double) millis) / 1000);
    NumberFormat nf = new DecimalFormat("0.00"); // easier way!
    if (!flags.suppressTestDebug)
      log.info(StringUtils.getShortClassName(this) + " tagged " + numWords + " words in " + numDocs
        + " documents at " + nf.format(wordspersec) + " words per second.");
    if (resultsCounted && ! flags.suppressTestDebug) {
      printResults(entityTP, entityFP, entityFN);
    }
  }

  @Override
  public SequenceModel getSequenceModel(List<IN> doc) {
    Triple<int[][][], int[], double[][][]> p = documentToDataAndLabels(doc);
    return getSequenceModel(p, doc);
  }

  private SequenceModel getSequenceModel(Triple<int[][][], int[], double[][][]> documentDataAndLabels, List<IN> document) {
    return labelDictionary == null ? new TestSequenceModel(getCliqueTree(documentDataAndLabels)) :
      new TestSequenceModel(getCliqueTree(documentDataAndLabels), labelDictionary, document);
  }

  protected CliquePotentialFunction getCliquePotentialFunctionForTest() {
    if (cliquePotentialFunction == null) {
      cliquePotentialFunction = new LinearCliquePotentialFunction(weights);
    }
    return cliquePotentialFunction;
  }

  public void updateWeightsForTest(double[] x) {
    cliquePotentialFunction = cliquePotentialFunctionHelper.getCliquePotentialFunction(x);
  }

  /**
   * Do standard sequence inference, using either Viterbi or Beam inference
   * depending on the value of {@code flags.inferenceType}.
   *
   * @param document Document to classify. Classification happens in place.
   *          This document is modified.
   * @return The classified document
   */
  public List<IN> classifyMaxEnt(List<IN> document) {
    if (document.isEmpty()) {
      return document;
    }

    SequenceModel model = getSequenceModel(document);
    return classifyMaxEnt(document, model);
  }

  private List<IN> classifyMaxEnt(List<IN> document, Triple<int[][][], int[], double[][][]> documentDataAndLabels) {
    if (document.isEmpty()) {
      return document;
    }
    SequenceModel model = getSequenceModel(documentDataAndLabels, document);
    return classifyMaxEnt(document, model);
  }

  private List<IN> classifyMaxEnt(List<IN> document, SequenceModel model) {
    if (document.isEmpty()) {
      return document;
    }

    if (flags.inferenceType == null) {
      flags.inferenceType = "Viterbi";
    }

    BestSequenceFinder tagInference;
    if (flags.inferenceType.equalsIgnoreCase("Viterbi")) {
      tagInference = new ExactBestSequenceFinder();
    } else if (flags.inferenceType.equalsIgnoreCase("Beam")) {
      tagInference = new BeamBestSequenceFinder(flags.beamSize);
    } else {
      throw new RuntimeException("Unknown inference type: " + flags.inferenceType + ". Your options are Viterbi|Beam.");
    }

    int[] bestSequence = tagInference.bestSequence(model);

    if (flags.useReverse) {
      Collections.reverse(document);
    }
    for (int j = 0, docSize = document.size(); j < docSize; j++) {
      IN wi = document.get(j);
      String guess = classIndex.get(bestSequence[j + windowSize - 1]);
      wi.set(CoreAnnotations.AnswerAnnotation.class, guess);
      int index = classIndex.indexOf(guess);
      double guessProb = ((TestSequenceModel) model).labelProb(j, index);
      wi.set(CoreAnnotations.AnswerProbAnnotation.class, guessProb);
    }
    if (flags.useReverse) {
      Collections.reverse(document);
    }
    return document;
  }

  public List<IN> classifyGibbs(List<IN> document) throws ClassNotFoundException, SecurityException,
      NoSuchMethodException, IllegalArgumentException, InstantiationException, IllegalAccessException,
      InvocationTargetException {
    Triple<int[][][], int[], double[][][]> p = documentToDataAndLabels(document);
    return classifyGibbs(document, p);
  }

  public List<IN> classifyGibbs(List<IN> document, Triple<int[][][], int[], double[][][]> documentDataAndLabels)
      throws ClassNotFoundException, SecurityException, NoSuchMethodException, IllegalArgumentException,
      InstantiationException, IllegalAccessException, InvocationTargetException {
    // log.info("Testing using Gibbs sampling.");
    List<IN> newDocument = document; // reversed if necessary
    if (flags.useReverse) {
      Collections.reverse(document);
      newDocument = new ArrayList<>(document);
      Collections.reverse(document);
    }

    CRFCliqueTree<? extends CharSequence> cliqueTree = getCliqueTree(documentDataAndLabels);

    PriorModelFactory<IN> pmf = (PriorModelFactory<IN>) Class.forName(flags.priorModelFactory).newInstance();
    ListeningSequenceModel prior = pmf.getInstance(flags.backgroundSymbol, classIndex, tagIndex, newDocument, entityMatrices, flags);

    if ( ! flags.useUniformPrior) {
      throw new RuntimeException("no prior specified");
    }

    SequenceModel model = new FactoredSequenceModel(cliqueTree, prior);
    SequenceListener listener = new FactoredSequenceListener(cliqueTree, prior);

    SequenceGibbsSampler sampler = new SequenceGibbsSampler(0, 0, listener);
    int[] sequence = new int[cliqueTree.length()];

    if (flags.initViterbi) {
      TestSequenceModel testSequenceModel = new TestSequenceModel(cliqueTree);
      ExactBestSequenceFinder tagInference = new ExactBestSequenceFinder();
      int[] bestSequence = tagInference.bestSequence(testSequenceModel);
      System.arraycopy(bestSequence, windowSize - 1, sequence, 0, sequence.length);
    } else {
      int[] initialSequence = SequenceGibbsSampler.getRandomSequence(model);
      System.arraycopy(initialSequence, 0, sequence, 0, sequence.length);
    }

    sampler.verbose = 0;

    if (flags.annealingType.equalsIgnoreCase("linear")) {
      sequence = sampler.findBestUsingAnnealing(model, CoolingSchedule.getLinearSchedule(1.0, flags.numSamples),
          sequence);
    } else if (flags.annealingType.equalsIgnoreCase("exp") || flags.annealingType.equalsIgnoreCase("exponential")) {
      sequence = sampler.findBestUsingAnnealing(model, CoolingSchedule.getExponentialSchedule(1.0, flags.annealingRate,
          flags.numSamples), sequence);
    } else {
      throw new RuntimeException("No annealing type specified");
    }

    if (flags.useReverse) {
      Collections.reverse(document);
    }

    for (int j = 0, dsize = newDocument.size(); j < dsize; j++) {
      IN wi = document.get(j);
      if (wi == null) throw new RuntimeException("");
      if (classIndex == null) throw new RuntimeException("");
      wi.set(CoreAnnotations.AnswerAnnotation.class, classIndex.get(sequence[j]));
    }

    if (flags.useReverse) {
      Collections.reverse(document);
    }

    return document;
  }

  /**
   * Takes a {@link List} of something that extends {@link CoreMap} and prints
   * the likelihood of each possible label at each point.
   *
   * @param document A {@link List} of something that extends CoreMap.
   * @return If verboseMode is set, a Triple of Counters recording classification decisions, else null.
   */
  @Override
  public Triple<Counter<Integer>, Counter<Integer>, TwoDimensionalCounter<Integer,String>> printProbsDocument(List<IN> document) {
    // TODO: Probably this would really be better with 11 bins, with edge ones from 0-0.5 and 0.95-1.0, a bit like 11-point ave precision
    final int numBins = 10;
    boolean verbose = flags.verboseMode;

    Triple<int[][][], int[], double[][][]> p = documentToDataAndLabels(document);
    CRFCliqueTree<String> cliqueTree = getCliqueTree(p);

    Counter<Integer> calibration = new ClassicCounter<>();
    Counter<Integer> correctByBin = new ClassicCounter<>();
    TwoDimensionalCounter<Integer,String> calibratedTokens = new TwoDimensionalCounter<>();

    // for (int i = 0; i < factorTables.length; i++) {
    for (int i = 0; i < cliqueTree.length(); i++) {
      IN wi = document.get(i);
      String token = wi.get(CoreAnnotations.TextAnnotation.class);
      String goldAnswer = wi.get(CoreAnnotations.GoldAnswerAnnotation.class);
      System.out.print(token);
      System.out.print('\t');
      System.out.print(goldAnswer);
      double maxProb = Double.NEGATIVE_INFINITY;
      String bestClass = "";
      for (String label : classIndex) {
        int index = classIndex.indexOf(label);
        // double prob = Math.pow(Math.E, factorTables[i].logProbEnd(index));
        double prob = cliqueTree.prob(i, index);
        if (prob > maxProb) {
          bestClass = label;
        }
        System.out.print('\t');
        System.out.print(label);
        System.out.print('=');
        System.out.print(prob);
        if (verbose ) {
          int binnedProb = (int) (prob * numBins);
          if (binnedProb > (numBins - 1)) {
            binnedProb = numBins - 1;
          }
          calibration.incrementCount(binnedProb);
          if (label.equals(goldAnswer)) {
            if (bestClass.equals(goldAnswer)) {
              correctByBin.incrementCount(binnedProb);
            }
            if ( ! label.equals(flags.backgroundSymbol)) {
              calibratedTokens.incrementCount(binnedProb, token);
            }
          }
        }
      }
      System.out.println();
    }
    if (verbose) {
      return new Triple<>(calibration, correctByBin, calibratedTokens);
    } else {
      return null;
    }
  }

  public List<Counter<String>> zeroOrderProbabilities(List<IN> document) {
    List<Counter<String>> ret = new ArrayList<>();
    Triple<int[][][], int[], double[][][]> p = documentToDataAndLabels(document);
    CRFCliqueTree<String> cliqueTree = getCliqueTree(p);
    for (int i = 0; i < cliqueTree.length(); i++) {
      Counter<String> ctr = new ClassicCounter<>();
      for (String label : classIndex) {
        int index = classIndex.indexOf(label);
        double prob = cliqueTree.prob(i, index);
        ctr.setCount(label, prob);
      }
      ret.add(ctr);
    }
    return ret;
  }

  /**
   * Takes the file, reads it in, and prints out the likelihood of each possible
   * label at each point. This gives a simple way to examine the probability
   * distributions of the CRF. See {@code getCliqueTrees()} for more.
   *
   * @param filename The path to the specified file
   */
  public void printFirstOrderProbs(String filename, DocumentReaderAndWriter<IN> readerAndWriter) {
    // only for the OCR data does this matter
    // flags.ocrTrain = false;

    ObjectBank<List<IN>> docs = makeObjectBankFromFile(filename, readerAndWriter);
    printFirstOrderProbsDocuments(docs);
  }

  /**
   * Takes a {@link List} of documents and prints the likelihood of each
   * possible label at each point.
   *
   * @param documents A {@link List} of {@link List} of INs.
   */
  public void printFirstOrderProbsDocuments(ObjectBank<List<IN>> documents) {
    for (List<IN> doc : documents) {
      printFirstOrderProbsDocument(doc);
      System.out.println();
    }
  }

  /**
   * Takes the file, reads it in, and prints out the factor table at each position.
   *
   * @param filename The path to the specified file
   */
  public void printFactorTable(String filename, DocumentReaderAndWriter<IN> readerAndWriter) {
    // only for the OCR data does this matter
    // flags.ocrTrain = false;

    ObjectBank<List<IN>> docs = makeObjectBankFromFile(filename, readerAndWriter);
    printFactorTableDocuments(docs);
  }

  /**
   * Takes a {@link List} of documents and prints the factor table
   * at each point.
   *
   * @param documents A {@link List} of {@link List} of INs.
   */
  public void printFactorTableDocuments(ObjectBank<List<IN>> documents) {
    for (List<IN> doc : documents) {
      printFactorTableDocument(doc);
      System.out.println();
    }
  }

  /**
   * Want to make arbitrary probability queries? Then this is the method for
   * you. Given the filename, it reads it in and breaks it into documents, and
   * then makes a CRFCliqueTree for each document. you can then ask the clique
   * tree for marginals and conditional probabilities of almost anything you want.
   */
  public List<CRFCliqueTree<String>> getCliqueTrees(String filename, DocumentReaderAndWriter<IN> readerAndWriter) {
    // only for the OCR data does this matter
    // flags.ocrTrain = false;

    List<CRFCliqueTree<String>> cts = new ArrayList<>();
    ObjectBank<List<IN>> docs = makeObjectBankFromFile(filename, readerAndWriter);
    for (List<IN> doc : docs) {
      cts.add(getCliqueTree(doc));
    }

    return cts;
  }

  public CRFCliqueTree<String> getCliqueTree(Triple<int[][][], int[], double[][][]> p) {
    int[][][] data = p.first();
    double[][][] featureVal = p.third();

    return CRFCliqueTree.getCalibratedCliqueTree(data, labelIndices, classIndex.size(), classIndex,
        flags.backgroundSymbol, getCliquePotentialFunctionForTest(), featureVal);
  }

  // This method should stay public
  @SuppressWarnings("WeakerAccess")
  public CRFCliqueTree<String> getCliqueTree(List<IN> document) {
    Triple<int[][][], int[], double[][][]> p = documentToDataAndLabels(document);
    return getCliqueTree(p);
  }

  // This method should stay public
  /**
   * Takes a {@link List} of something that extends {@link CoreMap} and prints
   * the factor table at each point.
   *
   * @param document A {@link List} of something that extends {@link CoreMap}.
   */
  @SuppressWarnings("WeakerAccess")
  public void printFactorTableDocument(List<IN> document) {

    CRFCliqueTree<String> cliqueTree = getCliqueTree(document);
    FactorTable[] factorTables = cliqueTree.getFactorTables();

    StringBuilder sb = new StringBuilder();
    for (int i=0; i < factorTables.length; i++) {
      IN wi = document.get(i);
      sb.append(wi.get(CoreAnnotations.TextAnnotation.class));
      sb.append('\t');
      FactorTable table = factorTables[i];
      for (int j = 0; j < table.size(); j++) {
        int[] arr = table.toArray(j);
        sb.append(classIndex.get(arr[0]));
        sb.append(':');
        sb.append(classIndex.get(arr[1]));
        sb.append(':');
        sb.append(cliqueTree.logProb(i, arr));
        sb.append(' ');
      }
      sb.append('\n');
    }
    System.out.print(sb);
  }

  /**
   * Takes a {@link List} of something that extends {@link CoreMap} and prints
   * the likelihood of each possible label at each point.
   *
   * @param document A {@link List} of something that extends {@link CoreMap}.
   */
  public void printFirstOrderProbsDocument(List<IN> document) {

    CRFCliqueTree<String> cliqueTree = getCliqueTree(document);

    // for (int i = 0; i < factorTables.length; i++) {
    for (int i = 0; i < cliqueTree.length(); i++) {
      IN wi = document.get(i);
      System.out.print(wi.get(CoreAnnotations.TextAnnotation.class) + '\t');
      for (Iterator<String> iter = classIndex.iterator(); iter.hasNext();) {
        String label = iter.next();
        int index = classIndex.indexOf(label);
        if (i == 0) {
          // double prob = Math.pow(Math.E, factorTables[i].logProbEnd(index));
          double prob = cliqueTree.prob(i, index);
          System.out.print(label + '=' + prob);
          if (iter.hasNext()) {
            System.out.print("\t");
          } else {
            System.out.print("\n");
          }
        } else {
          for (Iterator<String> iter1 = classIndex.iterator(); iter1.hasNext();) {
            String label1 = iter1.next();
            int index1 = classIndex.indexOf(label1);
            // double prob = Math.pow(Math.E, factorTables[i].logProbEnd(new
            // int[]{index1, index}));
            double prob = cliqueTree.prob(i, new int[] { index1, index });
            System.out.print(label1 + '_' + label + '=' + prob);
            if (iter.hasNext() || iter1.hasNext()) {
              System.out.print("\t");
            } else {
              System.out.print("\n");
            }
          }
        }
      }
    }
  }

  /**
   * Load auxiliary data to be used in constructing features and labels
   * Intended to be overridden by subclasses
   */
  protected Collection<List<IN>> loadAuxiliaryData(Collection<List<IN>> docs, DocumentReaderAndWriter<IN> readerAndWriter) {
    return docs;
  }

  /** {@inheritDoc} */
  @Override
  public void train(Collection<List<IN>> objectBankWrapper, DocumentReaderAndWriter<IN> readerAndWriter) {
    Timing timer = new Timing();

    Collection<List<IN>> docs = new ArrayList<>();
    for (List<IN> doc : objectBankWrapper) {
      docs.add(doc);
    }

    if (flags.numOfSlices > 0) {
      log.info("Taking " + flags.numOfSlices + " out of " + flags.totalDataSlice + " slices of data for training");
      List<List<IN>> docsToShuffle = new ArrayList<>();
      for (List<IN> doc : docs) {
        docsToShuffle.add(doc);
      }
      Collections.shuffle(docsToShuffle, random);
      int cutOff = (int)(docsToShuffle.size() / (flags.totalDataSlice + 0.0) * flags.numOfSlices);
      docs = docsToShuffle.subList(0, cutOff);
    }

    Collection<List<IN>> totalDocs = loadAuxiliaryData(docs, readerAndWriter);

    makeAnswerArraysAndTagIndex(totalDocs);

    long elapsedMs = timer.stop();
    log.info("Time to convert docs to feature indices: " + Timing.toSecondsString(elapsedMs) + " seconds");
    log.info("Current memory used: " + MemoryMonitor.getUsedMemoryString());

    if (flags.serializeClassIndexTo != null) {
      timer.start();
      serializeClassIndex(flags.serializeClassIndexTo);
      elapsedMs = timer.stop();
      log.info("Time to export class index : " + Timing.toSecondsString(elapsedMs) + " seconds");
    }

    if (flags.exportFeatures != null) {
      dumpFeatures(docs);
    }

    for (int i = 0; i <= flags.numTimesPruneFeatures; i++) {
      timer.start();
      Triple<int[][][][], int[][], double[][][][]> dataAndLabelsAndFeatureVals = documentsToDataAndLabels(docs);
      elapsedMs = timer.stop();
      log.info("Time to convert docs to data/labels: " + Timing.toSecondsString(elapsedMs) + " seconds");
      log.info("Current memory used: " + MemoryMonitor.getUsedMemoryString());

      Evaluator[] evaluators = null;
      if (flags.evaluateIters > 0 || flags.terminateOnEvalImprovement) {
        List<Evaluator> evaluatorList = new ArrayList<>();
        if (flags.useMemoryEvaluator)
          evaluatorList.add(new MemoryEvaluator());
        if (flags.evaluateTrain) {
          CRFClassifierEvaluator<IN> crfEvaluator = new CRFClassifierEvaluator<>("Train set", this);
          int[][][][] data = dataAndLabelsAndFeatureVals.first();
          int[][] labels = dataAndLabelsAndFeatureVals.second();
          double[][][][] featureVal = dataAndLabelsAndFeatureVals.third();
          List<Triple<int[][][], int[], double[][][]>> trainDataAndLabels = new ArrayList<>(data.length);
          for (int j = 0; j < data.length; j++) {
            Triple<int[][][], int[], double[][][]> p = new Triple<>(data[j], labels[j], featureVal[j]);
            trainDataAndLabels.add(p);
          }
          crfEvaluator.setTestData(docs, trainDataAndLabels);
          if (flags.evalCmd.length() > 0)
            crfEvaluator.setEvalCmd(flags.evalCmd);
          evaluatorList.add(crfEvaluator);
        }
        if (flags.testFile != null) {
          CRFClassifierEvaluator<IN> crfEvaluator = new CRFClassifierEvaluator<>("Test set (" + flags.testFile + ")",
                  this);
          ObjectBank<List<IN>> testObjBank = makeObjectBankFromFile(flags.testFile, readerAndWriter);
          List<List<IN>> testDocs = new ArrayList<>(testObjBank);
          List<Triple<int[][][], int[], double[][][]>> testDataAndLabels = documentsToDataAndLabelsList(testDocs);
          crfEvaluator.setTestData(testDocs, testDataAndLabels);
          if ( ! flags.evalCmd.isEmpty()) {
            crfEvaluator.setEvalCmd(flags.evalCmd);
          }
          evaluatorList.add(crfEvaluator);
        }
        if (flags.testFiles != null) {
          String[] testFiles = flags.testFiles.split(",");
          for (String testFile : testFiles) {
            CRFClassifierEvaluator<IN> crfEvaluator = new CRFClassifierEvaluator<>("Test set (" + testFile + ')', this);
            ObjectBank<List<IN>> testObjBank = makeObjectBankFromFile(testFile, readerAndWriter);
            List<Triple<int[][][], int[], double[][][]>> testDataAndLabels = documentsToDataAndLabelsList(testObjBank);
            crfEvaluator.setTestData(testObjBank, testDataAndLabels);
            if ( ! flags.evalCmd.isEmpty()) {
              crfEvaluator.setEvalCmd(flags.evalCmd);
            }
            evaluatorList.add(crfEvaluator);
          }
        }
        evaluators = new Evaluator[evaluatorList.size()];
        evaluatorList.toArray(evaluators);
      }

      if (flags.numTimesPruneFeatures == i) {
        docs = null; // hopefully saves memory
      }
      // save feature index to disk and read in later
      File featIndexFile = null;

      // CRFLogConditionalObjectiveFunction.featureIndex = featureIndex;
      // int numFeatures = featureIndex.size();
      if (flags.saveFeatureIndexToDisk) {
        try {
          log.info("Writing feature index to temporary file.");
          featIndexFile = IOUtils.writeObjectToTempFile(featureIndex, "featIndex" + i + ".tmp");
          // featureIndex = null;
        } catch (IOException e) {
          throw new RuntimeException("Could not open temporary feature index file for writing.");
        }
      }

      // first index is the number of the document
      // second index is position in the document also the index of the
      // clique/factor table
      // third index is the number of elements in the clique/window these
      // features are for (starting with last element)
      // fourth index is position of the feature in the array that holds them
      // element in data[i][j][k][m] is the index of the mth feature occurring
      // in position k of the jth clique of the ith document
      int[][][][] data = dataAndLabelsAndFeatureVals.first();
      // first index is the number of the document
      // second index is the position in the document
      // element in labels[i][j] is the index of the correct label (if it
      // exists) at position j in document i
      int[][] labels = dataAndLabelsAndFeatureVals.second();
      double[][][][] featureVals = dataAndLabelsAndFeatureVals.third();

      if (flags.loadProcessedData != null) {
        List<List<CRFDatum<Collection<String>, String>>> processedData = loadProcessedData(flags.loadProcessedData);
        if (processedData != null) {
          // enlarge the data and labels array
          int[][][][] allData = new int[data.length + processedData.size()][][][];
          double[][][][] allFeatureVals = new double[featureVals.length + processedData.size()][][][];
          int[][] allLabels = new int[labels.length + processedData.size()][];
          System.arraycopy(data, 0, allData, 0, data.length);
          System.arraycopy(labels, 0, allLabels, 0, labels.length);
          System.arraycopy(featureVals, 0, allFeatureVals, 0, featureVals.length);
          // add to the data and labels array
          addProcessedData(processedData, allData, allLabels, allFeatureVals, data.length);
          data = allData;
          labels = allLabels;
          featureVals = allFeatureVals;
        }
      }

      double[] oneDimWeights = trainWeights(data, labels, evaluators, i, featureVals);
      if (oneDimWeights != null) {
        this.weights = to2D(oneDimWeights, labelIndices, map);
      }

      // if (flags.useFloat) {
      //   oneDimWeights = trainWeightsUsingFloatCRF(data, labels, evaluators, i, featureVals);
      // } else if (flags.numLopExpert > 1) {
      //   oneDimWeights = trainWeightsUsingLopCRF(data, labels, evaluators, i, featureVals);
      // } else {
      //   oneDimWeights = trainWeightsUsingDoubleCRF(data, labels, evaluators, i, featureVals);
      // }

      // save feature index to disk and read in later
      if (flags.saveFeatureIndexToDisk) {
        try {
          log.info("Reading temporary feature index file.");
          featureIndex = IOUtils.readObjectFromFile(featIndexFile);
        } catch (Exception e) {
          throw new RuntimeException("Could not open temporary feature index file for reading.");
        }
      }

      if (i != flags.numTimesPruneFeatures) {
        dropFeaturesBelowThreshold(flags.featureDiffThresh);
        log.info("Removing features with weight below " + flags.featureDiffThresh + " and retraining...");
      }
    }
  }

  public static float[][] to2D(double[] weights, List<Index<CRFLabel>> labelIndices, int[] map) {
    float[][] newWeights = new float[map.length][];
    int index = 0;
    for (int i = 0; i < map.length; i++) {
      newWeights[i] = new float[labelIndices.get(map[i]).size()];
      final int arrLength = labelIndices.get(map[i]).size();
      for (int j = 0; j < arrLength; j++) {
        newWeights[i][j] = (float) weights[index++];
      }
    }
    return newWeights;
  }


  protected void pruneNodeFeatureIndices(int totalNumOfFeatureSlices, int numOfFeatureSlices) {
    int numOfNodeFeatures = nodeFeatureIndicesMap.size();
    int beginIndex = 0;
    int endIndex = Math.min( (int)(numOfNodeFeatures / (totalNumOfFeatureSlices+0.0) * numOfFeatureSlices), numOfNodeFeatures);
    List<Integer> nodeFeatureOriginalIndices = nodeFeatureIndicesMap.objectsList();
    List<Integer> edgeFeatureOriginalIndices = edgeFeatureIndicesMap.objectsList();

    Index<Integer> newNodeFeatureIndex = new HashIndex<>();
    Index<Integer> newEdgeFeatureIndex = new HashIndex<>();
    Index<String> newFeatureIndex = new HashIndex<>();

    for (int i = beginIndex; i < endIndex; i++) {
      int oldIndex = nodeFeatureOriginalIndices.get(i);
      String f = featureIndex.get(oldIndex);
      int index = newFeatureIndex.addToIndex(f);
      newNodeFeatureIndex.add(index);
    }
    for (Integer edgeFIndex: edgeFeatureOriginalIndices) {
      String f = featureIndex.get(edgeFIndex);
      int index = newFeatureIndex.addToIndex(f);
      newEdgeFeatureIndex.add(index);
    }

    nodeFeatureIndicesMap = newNodeFeatureIndex;
    edgeFeatureIndicesMap = newEdgeFeatureIndex;

    int[] newMap = new int[newFeatureIndex.size()];
    for (int i = 0; i < newMap.length; i++) {
      int index = featureIndex.indexOf(newFeatureIndex.get(i));
      newMap[i] = map[index];
    }
    map = newMap;

    featureIndex = newFeatureIndex;
  }

  protected CRFLogConditionalObjectiveFunction getObjectiveFunction(int[][][][] data, int[][] labels) {
    return new CRFLogConditionalObjectiveFunction(data, labels, windowSize, classIndex,
      labelIndices, map, flags.priorType, flags.backgroundSymbol, flags.sigma, null, flags.multiThreadGrad);
  }

  protected double[] trainWeights(int[][][][] data, int[][] labels, Evaluator[] evaluators, int pruneFeatureItr, double[][][][] featureVals) {

    CRFLogConditionalObjectiveFunction func = getObjectiveFunction(data, labels);
    cliquePotentialFunctionHelper = func;

    // create feature grouping
    // todo [cdm 2016]: Use a CollectionValuedMap
    Map<String, Set<Integer>> featureSets = null;
    if (flags.groupByOutputClass) {
      featureSets = new HashMap<>();
      if (flags.groupByFeatureTemplate) {
        int pIndex = 0;
        for (int fIndex = 0; fIndex < map.length; fIndex++) {
          int cliqueType = map[fIndex];
          int numCliqueTypeOutputClass = labelIndices.get(map[fIndex]).size();
          for (int cliqueOutClass = 0; cliqueOutClass < numCliqueTypeOutputClass; cliqueOutClass++) {
            String name = "c:"+cliqueType+"-o:"+cliqueOutClass+"-g:"+featureIndexToTemplateIndex.get(fIndex);
            if (featureSets.containsKey(name)) {
              featureSets.get(name).add(pIndex);
            } else {
              Set<Integer> newSet = new HashSet<>();
              newSet.add(pIndex);
              featureSets.put(name, newSet);
            }
            pIndex++;
          }
        }
      } else {
        int pIndex = 0;
        for (int cliqueType : map) {
          int numCliqueTypeOutputClass = labelIndices.get(cliqueType).size();
          for (int cliqueOutClass = 0; cliqueOutClass < numCliqueTypeOutputClass; cliqueOutClass++) {
            String name = "c:" + cliqueType + "-o:" + cliqueOutClass;
            if (featureSets.containsKey(name)) {
              featureSets.get(name).add(pIndex);
            } else {
              Set<Integer> newSet = new HashSet<>();
              newSet.add(pIndex);
              featureSets.put(name, newSet);
            }
            pIndex++;
          }
        }
      }
    } else if (flags.groupByFeatureTemplate) {
      featureSets = new HashMap<>();
      int pIndex = 0;
      for (int fIndex = 0; fIndex < map.length; fIndex++) {
        int cliqueType = map[fIndex];
        int numCliqueTypeOutputClass = labelIndices.get(map[fIndex]).size();
        for (int cliqueOutClass = 0; cliqueOutClass < numCliqueTypeOutputClass; cliqueOutClass++) {
          String name = "c:"+cliqueType+"-g:"+featureIndexToTemplateIndex.get(fIndex);
          if (featureSets.containsKey(name)) {
            featureSets.get(name).add(pIndex);
          } else {
            Set<Integer> newSet = new HashSet<>();
            newSet.add(pIndex);
            featureSets.put(name, newSet);
          }
          pIndex++;
        }
      }
    }
    if (featureSets != null) {
      int[][] fg = new int[featureSets.size()][];
      log.info("After feature grouping, total of "+fg.length+" groups");
      int count = 0;
      for (Set<Integer> aSet: featureSets.values()) {
        fg[count] = new int[aSet.size()];
        int i = 0;
        for (Integer val : aSet)
          fg[count][i++] = val;
        count++;
      }
      func.setFeatureGrouping(fg);
    }

    Minimizer<DiffFunction> minimizer = getMinimizer(pruneFeatureItr, evaluators);

    double[] initialWeights;
    if (flags.initialWeights == null) {
      initialWeights = func.initial();
    } else {
      try {
        log.info("Reading initial weights from file " + flags.initialWeights);
        DataInputStream dis = IOUtils.getDataInputStream(flags.initialWeights);
        initialWeights = ConvertByteArray.readDoubleArr(dis);
      } catch (IOException e) {
        throw new RuntimeException("Could not read from double initial weight file " + flags.initialWeights);
      }
    }
    log.info("numWeights: " + initialWeights.length);

    if (flags.testObjFunction) {
      StochasticDiffFunctionTester tester = new StochasticDiffFunctionTester(func);
      if (tester.testSumOfBatches(initialWeights, 1e-4)) {
        log.info("Successfully tested stochastic objective function.");
      } else {
        throw new IllegalStateException("Testing of stochastic objective function failed.");
      }

    }
    //check gradient
    if (flags.checkGradient) {
      if (func.gradientCheck()) {
        log.info("gradient check passed");
      } else {
        throw new RuntimeException("gradient check failed");
      }
    }
    return minimizer.minimize(func, flags.tolerance, initialWeights);
  }

  public Minimizer<DiffFunction> getMinimizer() {
    return getMinimizer(0, null);
  }

  public Minimizer<DiffFunction> getMinimizer(int featurePruneIteration, Evaluator[] evaluators) {
    Minimizer<DiffFunction> minimizer = null;
    QNMinimizer qnMinimizer = null;

    if (flags.useQN || flags.useSGDtoQN) {
      // share code for creation of QNMinimizer
      int qnMem;
      if (featurePruneIteration == 0) {
        qnMem = flags.QNsize;
      } else {
        qnMem = flags.QNsize2;
      }

      if (flags.interimOutputFreq != 0) {
        Function monitor = new ResultStoringMonitor(flags.interimOutputFreq, flags.serializeTo);
        qnMinimizer = new QNMinimizer(monitor, qnMem, flags.useRobustQN);
      } else {
        qnMinimizer = new QNMinimizer(qnMem, flags.useRobustQN);
      }

      qnMinimizer.terminateOnMaxItr(flags.maxQNItr);
      qnMinimizer.terminateOnEvalImprovement(flags.terminateOnEvalImprovement);
      qnMinimizer.setTerminateOnEvalImprovementNumOfEpoch(flags.terminateOnEvalImprovementNumOfEpoch);
      qnMinimizer.suppressTestPrompt(flags.suppressTestDebug);
      if (flags.useOWLQN) {
        qnMinimizer.useOWLQN(flags.useOWLQN, flags.priorLambda);
      }
    }

    if (flags.useQN) {
      minimizer = qnMinimizer;
    } else if (flags.useInPlaceSGD) {
      SGDMinimizer<DiffFunction> sgdMinimizer =
              new SGDMinimizer<>(flags.sigma, flags.SGDPasses, flags.tuneSampleSize, flags.stochasticBatchSize);
      if (flags.useSGDtoQN) {
        minimizer = new HybridMinimizer(sgdMinimizer, qnMinimizer, flags.SGDPasses);
      } else {
        minimizer = sgdMinimizer;
      }
    } else if (flags.useAdaGradFOBOS) {
      double lambda = 0.5 / (flags.sigma * flags.sigma);
      minimizer = new SGDWithAdaGradAndFOBOS<>(
              flags.initRate, lambda, flags.SGDPasses, flags.stochasticBatchSize,
              flags.priorType, flags.priorAlpha, flags.useAdaDelta, flags.useAdaDiff, flags.adaGradEps, flags.adaDeltaRho);
      ((SGDWithAdaGradAndFOBOS<?>) minimizer).terminateOnEvalImprovement(flags.terminateOnEvalImprovement);
      ((SGDWithAdaGradAndFOBOS<?>) minimizer).terminateOnAvgImprovement(flags.terminateOnAvgImprovement, flags.tolerance);
      ((SGDWithAdaGradAndFOBOS<?>) minimizer).setTerminateOnEvalImprovementNumOfEpoch(flags.terminateOnEvalImprovementNumOfEpoch);
      ((SGDWithAdaGradAndFOBOS<?>) minimizer).suppressTestPrompt(flags.suppressTestDebug);
    } else if (flags.useSGDtoQN) {
      minimizer = new SGDToQNMinimizer(flags.initialGain, flags.stochasticBatchSize,
                                       flags.SGDPasses, flags.QNPasses, flags.SGD2QNhessSamples,
                                       flags.QNsize, flags.outputIterationsToFile);
    } else if (flags.useSMD) {
      minimizer = new SMDMinimizer<>(flags.initialGain, flags.stochasticBatchSize, flags.stochasticMethod,
              flags.SGDPasses);
    } else if (flags.useSGD) {
      minimizer = new InefficientSGDMinimizer<>(flags.initialGain, flags.stochasticBatchSize);
    } else if (flags.useScaledSGD) {
      minimizer = new ScaledSGDMinimizer(flags.initialGain, flags.stochasticBatchSize, flags.SGDPasses,
          flags.scaledSGDMethod);
    } else if (flags.l1reg > 0.0) {
      minimizer = ReflectionLoading.loadByReflection("edu.stanford.nlp.optimization.OWLQNMinimizer", flags.l1reg);
    } else {
      throw new RuntimeException("No minimizer assigned!");
    }

    if (minimizer instanceof HasEvaluators) {
      if (minimizer instanceof QNMinimizer) {
        ((QNMinimizer) minimizer).setEvaluators(flags.evaluateIters, flags.startEvaluateIters, evaluators);
      } else
        ((HasEvaluators) minimizer).setEvaluators(flags.evaluateIters, evaluators);
    }

    return minimizer;
  }

  /**
   * Creates a new CRFDatum from the preprocessed allData format, given the
   * document number, position number, and a List of Object labels.
   *
   * @return A new CRFDatum
   */
  protected List<CRFDatum<? extends Collection<String>, ? extends CharSequence>> extractDatumSequence(int[][][] allData, int beginPosition, int endPosition,
      List<IN> labeledWordInfos) {
    List<CRFDatum<? extends Collection<String>, ? extends CharSequence>> result = new ArrayList<>();
    int beginContext = beginPosition - windowSize + 1;
    if (beginContext < 0) {
      beginContext = 0;
    }
    // for the beginning context, add some dummy datums with no features!
    // TODO: is there any better way to do this?
    for (int position = beginContext; position < beginPosition; position++) {
      List<Collection<String>> cliqueFeatures = new ArrayList<>();
      List<double[]> featureVals = new ArrayList<>();
      for (int i = 0; i < windowSize; i++) {
        // create a feature list
        cliqueFeatures.add(Collections.emptyList());
        featureVals.add(null);
      }
      CRFDatum<Collection<String>, String> datum = new CRFDatum<>(cliqueFeatures,
              labeledWordInfos.get(position).get(CoreAnnotations.AnswerAnnotation.class), featureVals);
      result.add(datum);
    }
    // now add the real datums
    for (int position = beginPosition; position <= endPosition; position++) {
      List<Collection<String>> cliqueFeatures = new ArrayList<>();
      List<double[]> featureVals = new ArrayList<>();
      for (int i = 0; i < windowSize; i++) {
        // create a feature list
        Collection<String> features = new ArrayList<>();
        for (int j = 0; j < allData[position][i].length; j++) {
          features.add(featureIndex.get(allData[position][i][j]));
        }
        cliqueFeatures.add(features);
        featureVals.add(null);
      }
      CRFDatum<Collection<String>,String> datum = new CRFDatum<>(cliqueFeatures,
              labeledWordInfos.get(position).get(CoreAnnotations.AnswerAnnotation.class), featureVals);
      result.add(datum);
    }
    return result;
  }

  /**
   * Adds the List of Lists of CRFDatums to the data and labels arrays, treating
   * each datum as if it were its own document. Adds context labels in addition
   * to the target label for each datum, meaning that for a particular document,
   * the number of labels will be windowSize-1 greater than the number of
   * datums.
   *
   * @param processedData A List of Lists of CRFDatums
   */
  protected void addProcessedData(List<List<CRFDatum<Collection<String>, String>>> processedData, int[][][][] data,
      int[][] labels, double[][][][] featureVals, int offset) {
    for (int i = 0, pdSize = processedData.size(); i < pdSize; i++) {
      int dataIndex = i + offset;
      List<CRFDatum<Collection<String>, String>> document = processedData.get(i);
      int dsize = document.size();
      labels[dataIndex] = new int[dsize];
      data[dataIndex] = new int[dsize][][];
      if (featureVals != null)
        featureVals[dataIndex] = new double[dsize][][];
      for (int j = 0; j < dsize; j++) {
        CRFDatum<Collection<String>, String> crfDatum = document.get(j);
        // add label, they are offset by extra context
        labels[dataIndex][j] = classIndex.indexOf(crfDatum.label());
        // add featureVals
        List<double[]> featureValList = featureVals != null ? crfDatum.asFeatureVals() : null;
        // add features
        List<Collection<String>> cliques = crfDatum.asFeatures();
        int csize = cliques.size();
        data[dataIndex][j] = new int[csize][];
        if (featureVals != null)
          featureVals[dataIndex][j] = new double[csize][];
        for (int k = 0; k < csize; k++) {
          Collection<String> features = cliques.get(k);

          data[dataIndex][j][k] = new int[features.size()];
          if (featureVals != null && k < featureValList.size())
            featureVals[dataIndex][j][k] = featureValList.get(k);

          int m = 0;
          try {
            for (String feature : features) {
              // log.info("feature " + feature);
              // if (featureIndex.indexOf(feature)) ;
              if (featureIndex == null) {
                System.out.println("Feature is NULL!");
              }
              data[dataIndex][j][k][m] = featureIndex.indexOf(feature);
              m++;
            }
          } catch (Exception e) {
            log.error("Add processed data failed.", e);
            log.info(String.format("[index=%d, j=%d, k=%d, m=%d]%n", dataIndex, j, k, m));
            log.info("data.length                    " + data.length);
            log.info("data[dataIndex].length         " + data[dataIndex].length);
            log.info("data[dataIndex][j].length      " + data[dataIndex][j].length);
            log.info("data[dataIndex][j][k].length   " + data[dataIndex][j].length);
            log.info("data[dataIndex][j][k][m]       " + data[dataIndex][j][k][m]);
            return;
          }
        }
      }
    }
  }

  protected static void saveProcessedData(List<?> datums, String filename) {
    log.info("Saving processed data of size " + datums.size() + " to serialized file...");
    ObjectOutputStream oos = null;
    try {
      oos = new ObjectOutputStream(new FileOutputStream(filename));
      oos.writeObject(datums);
    } catch (IOException e) {
      // do nothing
    } finally {
      IOUtils.closeIgnoringExceptions(oos);
    }
    log.info("done.");
  }

  protected static List<List<CRFDatum<Collection<String>, String>>> loadProcessedData(String filename) {
    List<List<CRFDatum<Collection<String>, String>>> result;
    try {
      result = IOUtils.readObjectFromURLOrClasspathOrFileSystem(filename);
    } catch (Exception e) {
      log.warn(e);
      result = Collections.emptyList();
    }
    log.info("Loading processed data from serialized file ... done. Got " + result.size() + " datums.");
    return result;
  }

  protected void loadTextClassifier(BufferedReader br) throws Exception {
    String line = br.readLine();
    // first line should be this format:
    // labelIndices.size()=\t%d
    String[] toks = line.split("\\t");
    if (!toks[0].equals("labelIndices.length=")) {
      throw new RuntimeException("format error");
    }
    int size = Integer.parseInt(toks[1]);
    labelIndices = new ArrayList<>(size);
    for (int labelIndicesIdx = 0; labelIndicesIdx < size; labelIndicesIdx++) {
      line = br.readLine();
      // first line should be this format:
      // labelIndices.length=\t%d
      // labelIndices[0].size()=\t%d
      toks = line.split("\\t");
      if (!(toks[0].startsWith("labelIndices[") && toks[0].endsWith("].size()="))) {
        throw new RuntimeException("format error");
      }
      int labelIndexSize = Integer.parseInt(toks[1]);
      labelIndices.add(new HashIndex<>());
      int count = 0;
      while (count < labelIndexSize) {
        line = br.readLine();
        toks = line.split("\\t");
        int idx = Integer.parseInt(toks[0]);
        if (count != idx) {
          throw new RuntimeException("format error");
        }

        String[] crflabelstr = toks[1].split(" ");
        int[] crflabel = new int[crflabelstr.length];
        for (int i = 0; i < crflabelstr.length; i++) {
          crflabel[i] = Integer.parseInt(crflabelstr[i]);
        }
        CRFLabel crfL = new CRFLabel(crflabel);

        labelIndices.get(labelIndicesIdx).add(crfL);
        count++;
      }
    }

    for (Index<CRFLabel> index : labelIndices) {
      for (int j = 0; j < index.size(); j++) {
        int[] label = index.get(j).getLabel();
        List<Integer> list = new ArrayList<>();
        for (int l : label) {
          list.add(l);
        }
      }
    }

    line = br.readLine();
    toks = line.split("\\t");
    if (!toks[0].equals("classIndex.size()=")) {
      throw new RuntimeException("format error");
    }
    int classIndexSize = Integer.parseInt(toks[1]);
    classIndex = new HashIndex<>();
    int count = 0;
    while (count < classIndexSize) {
      line = br.readLine();
      toks = line.split("\\t");
      int idx = Integer.parseInt(toks[0]);
      if (count != idx) {
        throw new RuntimeException("format error");
      }
      classIndex.add(toks[1]);
      count++;
    }

    line = br.readLine();
    toks = line.split("\\t");
    if (!toks[0].equals("featureIndex.size()=")) {
      throw new RuntimeException("format error");
    }
    int featureIndexSize = Integer.parseInt(toks[1]);
    featureIndex = new HashIndex<>();
    count = 0;
    while (count < featureIndexSize) {
      line = br.readLine();
      toks = line.split("\\t");
      int idx = Integer.parseInt(toks[0]);
      if (count != idx) {
        throw new RuntimeException("format error");
      }
      featureIndex.add(toks[1]);
      count++;
    }

    line = br.readLine();
    if (!line.equals("<flags>")) {
      throw new RuntimeException("format error");
    }
    Properties p = new Properties();
    line = br.readLine();

    while (!line.equals("</flags>")) {
      // log.info("DEBUG: flags line: "+line);
      String[] keyValue = line.split("=");
      // System.err.printf("DEBUG: p.setProperty(%s,%s)%n", keyValue[0],
      // keyValue[1]);
      p.setProperty(keyValue[0], keyValue[1]);
      line = br.readLine();
    }

    // log.info("DEBUG: out from flags");
    flags = new SeqClassifierFlags(p);

    if (flags.useEmbedding) {
      line = br.readLine();
      toks = line.split("\\t");
      if (!toks[0].equals("embeddings.size()=")) {
        throw new RuntimeException("format error in embeddings");
      }
      int embeddingSize = Integer.parseInt(toks[1]);
      embeddings = Generics.newHashMap(embeddingSize);
      count = 0;
      while (count < embeddingSize) {
        line = br.readLine().trim();
        toks = line.split("\\t");
        String word = toks[0];
        double[] arr = ArrayUtils.toDoubleArray(toks[1].split(" "));
        embeddings.put(word, arr);
        count++;
      }
    }

    // <featureFactory>
    // edu.stanford.nlp.wordseg.Gale2007ChineseSegmenterFeatureFactory
    // </featureFactory>
    line = br.readLine();

    String[] featureFactoryName = line.split(" ");
    if (featureFactoryName.length < 2 || !featureFactoryName[0].equals("<featureFactory>") || !featureFactoryName[featureFactoryName.length - 1].equals("</featureFactory>")) {
      throw new RuntimeException("format error unexpected featureFactory line: " + line);
    }
    featureFactories = Generics.newArrayList();
    for (int ff = 1; ff < featureFactoryName.length - 1; ++ff) {
      FeatureFactory<IN> featureFactory = (FeatureFactory<IN>) Class.forName(featureFactoryName[1]).newInstance();
      featureFactory.init(flags);
      featureFactories.add(featureFactory);
    }

    reinit();

    // <windowSize> 2 </windowSize>
    line = br.readLine();

    String[] windowSizeName = line.split(" ");
    if (!windowSizeName[0].equals("<windowSize>") || !windowSizeName[2].equals("</windowSize>")) {
      throw new RuntimeException("format error");
    }
    windowSize = Integer.parseInt(windowSizeName[1]);

    // weights.length= 2655170
    line = br.readLine();

    toks = line.split("\\t");
    if (!toks[0].equals("weights.length=")) {
      throw new RuntimeException("format error");
    }
    int weightsLength = Integer.parseInt(toks[1]);
    weights = new float[weightsLength][];
    count = 0;
    while (count < weightsLength) {
      line = br.readLine();

      toks = line.split("\\t");
      int weights2Length = Integer.parseInt(toks[0]);
      weights[count] = new float[weights2Length];
      String[] weightsValue = toks[1].split(" ");
      if (weights2Length != weightsValue.length) {
        throw new RuntimeException("weights format error");
      }

      for (int i2 = 0; i2 < weights2Length; i2++) {
        // TODO: check that this doesn't barf... why would it?
        weights[count][i2] = Float.parseFloat(weightsValue[i2]);
      }
      count++;
    }
    System.err.printf("DEBUG: float[%d][] weights loaded%n", weightsLength);
    line = br.readLine();

    if (line != null) {
      throw new RuntimeException("weights format error");
    }
  }

  public void loadTextClassifier(String text, Properties props) throws ClassCastException, IOException,
      ClassNotFoundException, InstantiationException, IllegalAccessException {
    // log.info("DEBUG: in loadTextClassifier");
    log.info("Loading Text Classifier from " + text);
    try (BufferedReader br = IOUtils.readerFromString(text)) {
      loadTextClassifier(br);
    } catch (Exception ex) {
      log.info("Exception in loading text classifier from " + text, ex);
    }
  }

  protected void serializeTextClassifier(PrintWriter pw) throws Exception {
    pw.printf("labelIndices.length=\t%d%n", labelIndices.size());
    for (int i = 0; i < labelIndices.size(); i++) {
      pw.printf("labelIndices[%d].size()=\t%d%n", i, labelIndices.get(i).size());
      for (int j = 0; j < labelIndices.get(i).size(); j++) {
        int[] label = labelIndices.get(i).get(j).getLabel();
        List<Integer> list = new ArrayList<>();
        for (int l : label) {
          list.add(l);
        }
        pw.printf("%d\t%s%n", j, StringUtils.join(list, " "));
      }
    }

    pw.printf("classIndex.size()=\t%d%n", classIndex.size());
    for (int i = 0; i < classIndex.size(); i++) {
      pw.printf("%d\t%s%n", i, classIndex.get(i));
    }
    // pw.printf("</classIndex>%n");

    pw.printf("featureIndex.size()=\t%d%n", featureIndex.size());
    for (int i = 0; i < featureIndex.size(); i++) {
      pw.printf("%d\t%s%n", i, featureIndex.get(i));
    }
    // pw.printf("</featureIndex>%n");

    pw.println("<flags>");
    pw.print(flags);
    pw.println("</flags>");

    if (flags.useEmbedding) {
      pw.printf("embeddings.size()=\t%d%n", embeddings.size());
      for (String word: embeddings.keySet()) {
        double[] arr = embeddings.get(word);
        Double[] arrUnboxed = new Double[arr.length];
        for(int i = 0; i < arr.length; i++)
          arrUnboxed[i] = arr[i];
        pw.printf("%s\t%s%n", word, StringUtils.join(arrUnboxed, " "));
      }
    }

    pw.printf("<featureFactory>");
    for (FeatureFactory<IN> featureFactory : featureFactories) {
      pw.printf(" %s ", featureFactory.getClass().getName());
    }
    pw.printf("</featureFactory>%n");

    pw.printf("<windowSize> %d </windowSize>%n", windowSize);

    pw.printf("weights.length=\t%d%n", weights.length);
    for (float[] ws : weights) {
      ArrayList<Float> list = new ArrayList<>();
      for (float w : ws) {
        list.add(w);
      }
      pw.printf("%d\t%s%n", ws.length, StringUtils.join(list, " "));
    }
  }

  /**
   * Serialize the model to a human readable format. It's not yet complete. It
   * should now work for Chinese segmenter though. TODO: check things in
   * serializeClassifier and add other necessary serialization back.
   *
   * @param serializePath File to write text format of classifier to.
   */
  public void serializeTextClassifier(String serializePath) {
    try {
      PrintWriter pw = new PrintWriter(new GZIPOutputStream(new FileOutputStream(serializePath)));
      serializeTextClassifier(pw);

      pw.close();
      log.info("Serializing Text classifier to " + serializePath + "... done.");
    } catch (Exception e) {
      log.info("Serializing Text classifier to " + serializePath + "... FAILED.", e);
    }
  }

  public void serializeClassIndex(String serializePath) {

    ObjectOutputStream oos = null;
    try {
      oos = IOUtils.writeStreamFromString(serializePath);
      oos.writeObject(classIndex);
      log.info("Serializing class index to " + serializePath + "... done.");
    } catch (Exception e) {
      log.info("Serializing class index to " + serializePath + "... FAILED.", e);
    } finally {
      IOUtils.closeIgnoringExceptions(oos);
    }
  }

  public static Index<String> loadClassIndexFromFile(String serializePath) {
    ObjectInputStream ois = null;
    Index<String> c = null;
    try {
      ois = IOUtils.readStreamFromString(serializePath);
      c = (Index<String>) ois.readObject();
      log.info("Reading class index from " + serializePath + "... done.");
    } catch (Exception e) {
      log.info("Reading class index from " + serializePath + "... FAILED.", e);
    } finally {
      IOUtils.closeIgnoringExceptions(ois);
    }

    return c;
  }

  public void serializeWeights(String serializePath) {
    ObjectOutputStream oos = null;
    try {
      oos = IOUtils.writeStreamFromString(serializePath);
      oos.writeObject(weights);
      log.info("Serializing weights to " + serializePath + "... done.");
    } catch (Exception e) {
      log.info("Serializing weights to " + serializePath + "... FAILED.", e);
    } finally {
      IOUtils.closeIgnoringExceptions(oos);
    }
  }

  public static double[][] loadWeightsFromFile(String serializePath) {

    ObjectInputStream ois = null;
    double[][] w = null;
    try {
      ois = IOUtils.readStreamFromString(serializePath);
      w = (double[][]) ois.readObject();
      log.info("Reading weights from " + serializePath + "... done.");
    } catch (Exception e) {
      log.info("Reading weights from " + serializePath + "... FAILED.", e);
    } finally {
      IOUtils.closeIgnoringExceptions(ois);
    }

    return w;
  }

  public void serializeFeatureIndex(String serializePath) {
    ObjectOutputStream oos = null;
    try {
      oos = IOUtils.writeStreamFromString(serializePath);
      oos.writeObject(featureIndex);
      log.info("Serializing FeatureIndex to " + serializePath + "... done.");
    } catch (Exception e) {
      log.info("Failed");
      log.info("Serializing FeatureIndex to " + serializePath + "... FAILED.", e);
    } finally {
      IOUtils.closeIgnoringExceptions(oos);
    }
  }

  public void serializeFeatureIndexToText(String serializePath) {
    PrintWriter fout = null;
    try {
      fout = IOUtils.getPrintWriter(serializePath);
      for (String feature : featureIndex) {
        fout.print(feature + "\n");
      }
      log.info("Serializing FeatureIndex to " + serializePath + "... done.");
    } catch (IOException e) {
      log.info("Failed");
      log.info("Serializing FeatureIndex to " + serializePath + "... FAILED.", e);
    } finally {
      fout.close();
    }
  }

  public static Index<String> loadFeatureIndexFromFile(String serializePath) {
    ObjectInputStream ois = null;
    Index<String> f = null;
    try {
      ois = IOUtils.readStreamFromString(serializePath);
      f = (Index<String>) ois.readObject();
      log.info("Reading FeatureIndex from " + serializePath + "... done.");
    } catch (Exception e) {
      log.info("Reading FeatureIndex from " + serializePath + "... FAILED.", e);
    } finally {
      IOUtils.closeIgnoringExceptions(ois);
    }

    return f;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void serializeClassifier(String serializePath) {
    ObjectOutputStream oos = null;
    try {
      oos = IOUtils.writeStreamFromString(serializePath);
      serializeClassifier(oos);
      log.info("Serializing classifier to " + serializePath + "... done.");

    } catch (Exception e) {
      throw new RuntimeIOException("Serializing classifier to " + serializePath + "... FAILED", e);
    } finally {
      IOUtils.closeIgnoringExceptions(oos);
    }
  }

  /**
   * Serialize the classifier to the given ObjectOutputStream.
   * <br>
   * (Since the classifier is a processor, we don't want to serialize the
   * whole classifier but just the data that represents a classifier model.)
   */
  @Override
  public void serializeClassifier(ObjectOutputStream oos) {
    try {
      oos.writeObject(labelIndices);
      oos.writeObject(classIndex);
      oos.writeObject(featureIndex);
      oos.writeObject(flags);
      if (flags.useEmbedding) {
        oos.writeObject(embeddings);
      }
      // For some reason, writing out the array of FeatureFactory
      // objects doesn't seem to work.  The resulting classifier
      // doesn't have the lexicon (distsim object) correctly saved.  So now custom write the list
      oos.writeObject(featureFactories.size());
      for (FeatureFactory<IN> ff : featureFactories) {
        oos.writeObject(ff);
      }
      oos.writeInt(windowSize);
      oos.writeObject(weights);
      // oos.writeObject(WordShapeClassifier.getKnownLowerCaseWords());

      oos.writeObject(knownLCWords);
      if (labelDictionary != null) {
        oos.writeObject(labelDictionary);
      }
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  /**
   * Loads a classifier from the specified InputStream. This version works
   * quietly (unless VERBOSE is true). If props is non-null then any properties
   * it specifies override those in the serialized file. However, only some
   * properties are sensible to change (you shouldn't change how features are
   * defined).
   * <p>
   * <i>Note:</i> This method does not close the ObjectInputStream. (But earlier
   * versions of the code used to, so beware....)
   */
  @Override
  @SuppressWarnings( { "unchecked" })
  // can't have right types in deserialization
  public void loadClassifier(ObjectInputStream ois, Properties props) throws ClassCastException, IOException,
      ClassNotFoundException {
    Object o = ois.readObject();
    // TODO: when we next break serialization, get rid of this fork and only read the List<Index> (i.e., keep first case)
    if (o instanceof List) {
      labelIndices = (List<Index<CRFLabel>>) o;
    } else {
      Index<CRFLabel>[] indexArray = (Index<CRFLabel>[]) o;
      labelIndices = new ArrayList<>(indexArray.length);
      Collections.addAll(labelIndices, indexArray);
    }
    classIndex = (Index<String>) ois.readObject();
    featureIndex = (Index<String>) ois.readObject();
    flags = (SeqClassifierFlags) ois.readObject();
    if (flags.useEmbedding) {
      embeddings = (Map<String, double[]>) ois.readObject();
    }
    Object featureFactory = ois.readObject();
    if (featureFactory instanceof List) {
      featureFactories = ErasureUtils.uncheckedCast(featureFactories);
//      int i = 0;
//      for (FeatureFactory ff : featureFactories) { // XXXX
//        System.err.println("List FF #" + i + ": " + ((NERFeatureFactory) ff).describeDistsimLexicon()); // XXXX
//        i++;
//      }
    } else if (featureFactory instanceof FeatureFactory) {
      featureFactories = Generics.newArrayList();
      featureFactories.add((FeatureFactory<IN>) featureFactory);
//      System.err.println(((NERFeatureFactory) featureFactory).describeDistsimLexicon()); // XXXX
    } else if (featureFactory instanceof Integer) {
      // this is the current format (2014) since writing list didn't work (see note in serializeClassifier).
      int size = (Integer) featureFactory;
      featureFactories = Generics.newArrayList(size);
      for (int i = 0; i < size; ++i) {
        featureFactory = ois.readObject();
        if (!(featureFactory instanceof FeatureFactory)) {
          throw new RuntimeIOException("Should have FeatureFactory but got " + featureFactory.getClass());
        }
//        System.err.println("FF #" + i + ": " + ((NERFeatureFactory) featureFactory).describeDistsimLexicon()); // XXXX
        featureFactories.add((FeatureFactory<IN>) featureFactory);
      }
    }

    // log.info("properties passed into CRF's loadClassifier are:" + props);
    if (props != null) {
      flags.setProperties(props, false);
    }

    windowSize = ois.readInt();
    Object tempWeights = ois.readObject();
    if (tempWeights instanceof double[][]) {
      // TODO: if slow, maybe use some temp variables for the arrays
      double[][] dWeights = (double[][]) tempWeights;
      weights = new float[dWeights.length][];
      for (int i = 0; i < dWeights.length; ++i) {
        weights[i] = new float[dWeights[i].length];
        for (int j = 0; j < dWeights[i].length; ++j) {
          weights[i][j] = (float) dWeights[i][j];
        }
      }
    } else {
      weights = (float[][]) tempWeights;
    }

    // WordShapeClassifier.setKnownLowerCaseWords((Set) ois.readObject());
    Set<String> lcWords = (Set<String>) ois.readObject();
    if (lcWords instanceof MaxSizeConcurrentHashSet) {
      knownLCWords = (MaxSizeConcurrentHashSet<String>) lcWords;
    } else {
      knownLCWords = new MaxSizeConcurrentHashSet<>(lcWords);
    }

    reinit();

    if (flags.labelDictionaryCutoff > 0) {
      labelDictionary = (LabelDictionary) ois.readObject();
    }

    if (VERBOSE) {
      log.info("windowSize=" + windowSize);
      log.info("flags=\n" + flags);
    }
  }

  /**
   * This is used to load the default supplied classifier stored within the jar
   * file. THIS FUNCTION WILL ONLY WORK IF THE CODE WAS LOADED FROM A JAR FILE
   * WHICH HAS A SERIALIZED CLASSIFIER STORED INSIDE IT.
   */
  public void loadDefaultClassifier() {
    loadClassifierNoExceptions(DEFAULT_CLASSIFIER);
  }

  public void loadTagIndex() {
    if (tagIndex == null) {
      tagIndex = new HashIndex<>();
      for (String tag: classIndex.objectsList()) {
        String[] parts = tag.split("-");
        // if (parts.length > 1)
        tagIndex.add(parts[parts.length-1]);
      }
      tagIndex.add(flags.backgroundSymbol);
    }
    if (flags.useNERPriorBIO) {
      if (entityMatrices == null)
        entityMatrices = readEntityMatrices(flags.entityMatrix, tagIndex);
    }
  }

  private static double[][] parseMatrix(String[] lines, Index<String> tagIndex, int matrixSize, boolean smooth) {
    return parseMatrix(lines, tagIndex, matrixSize, smooth, true);
  }

  /**
   * @return a matrix where each entry m[i][j] is logP(j|i)
   * in other words, each row vector is normalized log conditional likelihood
   */
   static double[][] parseMatrix(String[] lines, Index<String> tagIndex, int matrixSize, boolean smooth, boolean useLogProb) {
    double[][] matrix = new double[matrixSize][matrixSize];
    for (int i = 0; i < matrix.length; i++) {
      matrix[i] = new double[matrixSize];
    }
    for (String line: lines) {
      String[] parts = line.split("\t");
      for (String part: parts) {
        String[] subparts = part.split(" ");
        String[] subsubparts = subparts[0].split(":");
        double counts = Double.parseDouble(subparts[1]);
        if (counts == 0.0 && smooth) // smoothing
          counts = 1.0;
        int tagIndex1 = tagIndex.indexOf(subsubparts[0]);
        int tagIndex2 = tagIndex.indexOf(subsubparts[1]);
        matrix[tagIndex1][tagIndex2] = counts;
      }
    }
    for (int i = 0; i < matrix.length; i++) {
      double sum = ArrayMath.sum(matrix[i]);
      for (int j = 0; j < matrix[i].length; j++) {
        // log conditional probability
        if (useLogProb)
          matrix[i][j] = Math.log(matrix[i][j] / sum);
        else
          matrix[i][j] = matrix[i][j] / sum;
      }
    }
    return matrix;
  }

  static Pair<double[][], double[][]> readEntityMatrices(String fileName, Index<String> tagIndex) {
    int numTags = tagIndex.size();
    int matrixSize = numTags-1;

    String[] matrixLines = new String[matrixSize];
    String[] subMatrixLines = new String[matrixSize];
    try (BufferedReader br = IOUtils.readerFromString(fileName)) {
      int lineCount = 0;
      for (String line; (line = br.readLine()) != null; ) {
        line = line.trim();
        if (lineCount < matrixSize)
          matrixLines[lineCount] = line;
        else
          subMatrixLines[lineCount-matrixSize] = line;
        lineCount++;
      }
    } catch (Exception ex) {
      throw new RuntimeIOException(ex);
    }

    double[][] matrix = parseMatrix(matrixLines, tagIndex, matrixSize, true);
    double[][] subMatrix = parseMatrix(subMatrixLines, tagIndex, matrixSize, true);

    // In Jenny's paper, use the square root of non-log prob for matrix, but not for subMatrix
    for (int i = 0; i < matrix.length; i++) {
      for (int j = 0; j < matrix[i].length; j++)
        matrix[i][j] = matrix[i][j] / 2;
    }

    log.info("Matrix: ");
    log.info(ArrayUtils.toString(matrix));
    log.info("SubMatrix: ");
    log.info(ArrayUtils.toString(subMatrix));

    return new Pair<>(matrix, subMatrix);
  }

  public void writeWeights(PrintStream p) {
    for (String feature : featureIndex) {
      int index = featureIndex.indexOf(feature);
      // line.add(feature+"["+(-p)+"]");
      // rowHeaders.add(feature + '[' + (-p) + ']');
      float[] v = weights[index];
      Index<CRFLabel> l = this.labelIndices.get(0);
      p.println(feature + "\t\t");
      for (CRFLabel label : l) {
        p.print(label.toString(classIndex) + ':' + v[l.indexOf(label)] + '\t');
      }
      p.println();

    }
  }

  public Map<String, Counter<String>> topWeights() {
    Map<String, Counter<String>> w = new HashMap<>();
    for (String feature : featureIndex) {
      int index = featureIndex.indexOf(feature);
      // line.add(feature+"["+(-p)+"]");
      // rowHeaders.add(feature + '[' + (-p) + ']');
      float[] v = weights[index];
      Index<CRFLabel> l = this.labelIndices.get(0);
      for (CRFLabel label : l) {
        if(!w.containsKey(label.toString(classIndex)))
          w.put(label.toString(classIndex), new ClassicCounter<>());
        w.get(label.toString(classIndex)).setCount(feature, v[l.indexOf(label)]);
      }
    }
    return w;
  }

  /** Read real-valued vector embeddings for (lowercased) word tokens.
   *  A lexicon is contained in the file flags.embeddingWords.
   *  The word vectors are then in the same order in the file flags.embeddingVectors.
   *
   *  @throws IOException If embedding vectors canot be loaded
   */
  private void readEmbeddingsData() throws IOException {
    System.err.printf("Reading embedding files %s and %s.%n", flags.embeddingWords, flags.embeddingVectors);
    List<String> wordList = new ArrayList<>();
    try (BufferedReader br = IOUtils.readerFromString(flags.embeddingWords)) {

      for (String line; (line = br.readLine()) != null; ) {
        wordList.add(line.trim());
      }
      log.info("Found a dictionary of size " + wordList.size());
    }

    embeddings = Generics.newHashMap();
    try (BufferedReader br = IOUtils.readerFromString(flags.embeddingVectors)) {
      int count = 0;
      int vectorSize = -1;
      boolean warned = false;
      for (String line; (line = br.readLine()) != null; ) {
        double[] vector = ArrayUtils.toDoubleArray(line.trim().split(" "));
        if (vectorSize < 0) {
          vectorSize = vector.length;
        } else {
          if (vectorSize != vector.length && !warned) {
            log.info("Inconsistent vector lengths: " + vectorSize + " vs. " + vector.length);
            warned = true;
          }
        }
        embeddings.put(wordList.get(count++), vector);
      }
      log.info("Found " + count + " matching embeddings of dimension " + vectorSize);
    }
  }

  @Override
  public List<IN> classifyWithGlobalInformation(List<IN> tokenSeq, final CoreMap doc, final CoreMap sent) {
    return classify(tokenSeq);
  }



  /**
   * This is used to load the default supplied classifier stored within the jar
   * file. THIS FUNCTION WILL ONLY WORK IF THE CODE WAS LOADED FROM A JAR FILE
   * WHICH HAS A SERIALIZED CLASSIFIER STORED INSIDE IT.
   */
  public void loadDefaultClassifier(Properties props) {
    loadClassifierNoExceptions(DEFAULT_CLASSIFIER, props);
  }

  /**
   * Used to get the default supplied classifier inside the jar file. THIS
   * FUNCTION WILL ONLY WORK IF THE CODE WAS LOADED FROM A JAR FILE WHICH HAS A
   * SERIALIZED CLASSIFIER STORED INSIDE IT.
   *
   * @return The default CRFClassifier in the jar file (if there is one)
   */
  public static <INN extends CoreMap> CRFClassifier<INN> getDefaultClassifier() {
    CRFClassifier<INN> crf = new CRFClassifier<>();
    crf.loadDefaultClassifier();
    return crf;
  }

  /**
   * Used to get the default supplied classifier inside the jar file. THIS
   * FUNCTION WILL ONLY WORK IF THE CODE WAS LOADED FROM A JAR FILE WHICH HAS A
   * SERIALIZED CLASSIFIER STORED INSIDE IT.
   *
   * @return The default CRFClassifier in the jar file (if there is one)
   */
  public static <INN extends CoreMap> CRFClassifier<INN> getDefaultClassifier(Properties props) {
    CRFClassifier<INN> crf = new CRFClassifier<>();
    crf.loadDefaultClassifier(props);
    return crf;
  }
  
  /**
   * Loads a CRF classifier from a filepath, and returns it.
   *
   * @param file File to load classifier from
   * @return The CRF classifier
   *
   * @throws IOException If there are problems accessing the input stream
   * @throws ClassCastException If there are problems interpreting the serialized data
   * @throws ClassNotFoundException If there are problems interpreting the serialized data
   */
  public static <INN extends CoreMap> CRFClassifier<INN> getClassifier(File file) throws IOException, ClassCastException,
      ClassNotFoundException {
    CRFClassifier<INN> crf = new CRFClassifier<>();
    crf.loadClassifier(file);
    return crf;
  }

  /**
   * Loads a CRF classifier from an InputStream, and returns it. This method
   * does not buffer the InputStream, so you should have buffered it before
   * calling this method.
   *
   * @param in InputStream to load classifier from
   * @return The CRF classifier
   *
   * @throws IOException If there are problems accessing the input stream
   * @throws ClassCastException If there are problems interpreting the serialized data
   * @throws ClassNotFoundException If there are problems interpreting the serialized data
   */
  public static <INN extends CoreMap> CRFClassifier<INN> getClassifier(InputStream in) throws IOException, ClassCastException,
      ClassNotFoundException {
    CRFClassifier<INN> crf = new CRFClassifier<>();
    crf.loadClassifier(in);
    return crf;
  }

  // new method for getting a CRFClassifier from an ObjectInputStream
  public static <INN extends CoreMap> CRFClassifier<INN> getClassifier(ObjectInputStream ois) throws IOException,
          ClassCastException,
          ClassNotFoundException {
    CRFClassifier<INN> crf = new CRFClassifier<>();
    crf.loadClassifier(ois,null);
    return crf;
  }

  public static <INN extends CoreMap> CRFClassifier<INN> getClassifierNoExceptions(String loadPath) {
    CRFClassifier<INN> crf = new CRFClassifier<>();
    crf.loadClassifierNoExceptions(loadPath);
    return crf;
  }

  public static CRFClassifier<CoreLabel> getClassifier(String loadPath) throws IOException, ClassCastException,
      ClassNotFoundException {
    CRFClassifier<CoreLabel> crf = new CRFClassifier<>();
    crf.loadClassifier(loadPath);
    return crf;
  }

  public static <INN extends CoreMap> CRFClassifier<INN> getClassifier(String loadPath, Properties props) throws IOException, ClassCastException,
      ClassNotFoundException {
    CRFClassifier<INN> crf = new CRFClassifier<>();
    crf.loadClassifier(loadPath, props);
    return crf;
  }

  public static <INN extends CoreMap> CRFClassifier<INN> getClassifier(ObjectInputStream ois, Properties props) throws IOException, ClassCastException,
      ClassNotFoundException {
    CRFClassifier<INN> crf = new CRFClassifier<>();
    crf.loadClassifier(ois, props);
    return crf;
  }

  private static CRFClassifier<CoreLabel> chooseCRFClassifier(SeqClassifierFlags flags) {
    CRFClassifier<CoreLabel> crf; // initialized in if/else
    if (flags.useFloat) {
      crf = new CRFClassifierFloat<>(flags);
    } else if (flags.nonLinearCRF) {
      crf = new CRFClassifierNonlinear<>(flags);
    } else if (flags.numLopExpert > 1) {
      crf = new CRFClassifierWithLOP<>(flags);
    } else if (flags.priorType.equals("DROPOUT")) {
      crf = new CRFClassifierWithDropout<>(flags);
    } else if (flags.useNoisyLabel) {
      crf = new CRFClassifierNoisyLabel<>(flags);
    } else {
      crf = new CRFClassifier<>(flags);
    }
    return crf;
  }

  public String toString() {
    String name = flags.loadClassifier;
    if (name == null) {
      name = flags.serializeTo;
    }
    if (name == null) {
      name = flags.trainFile;
    }
    if (name == null) {
      name = super.toString();
    }
    return name + classIndex.toString();
  }

  /** The main method. See the class documentation. */
  public static void main(String[] args) throws Exception {
    StringUtils.logInvocationString(log, args);

    Properties props = StringUtils.argsToProperties(args, SeqClassifierFlags.flagsToNumArgs());
    SeqClassifierFlags flags = new SeqClassifierFlags(props);
    CRFClassifier<CoreLabel> crf = chooseCRFClassifier(flags);
    String testFile = flags.testFile;
    String testFiles = flags.testFiles;
    String textFile = flags.textFile;
    String textFiles = flags.textFiles;
    String loadPath = flags.loadClassifier;
    String loadTextPath = flags.loadTextClassifier;
    String serializeTo = flags.serializeTo;
    String serializeToText = flags.serializeToText;

    if (crf.flags.useEmbedding && crf.flags.embeddingWords != null && crf.flags.embeddingVectors != null) {
      crf.readEmbeddingsData();
    }

    if (crf.flags.loadClassIndexFrom != null) {
      crf.classIndex = loadClassIndexFromFile(crf.flags.loadClassIndexFrom);
    }

    if (loadPath != null) {
      crf.loadClassifierNoExceptions(loadPath, props);
    } else if (loadTextPath != null) {
      log.info("Warning: this is now only tested for Chinese Segmenter");
      log.info("(Sun Dec 23 00:59:39 2007) (pichuan)");
      try {
        crf.loadTextClassifier(loadTextPath, props);
        // log.info("DEBUG: out from crf.loadTextClassifier");
      } catch (Exception e) {
        throw new RuntimeException("error loading " + loadTextPath, e);
      }
    } else if (crf.flags.loadJarClassifier != null) {
      // legacy option support
      crf.loadClassifierNoExceptions(crf.flags.loadJarClassifier, props);
    } else if (crf.flags.trainFile != null || crf.flags.trainFileList != null) {
      Timing timing = new Timing();
      // temporarily unlimited size of knownLCWords
      int knownLCWordsLimit = crf.knownLCWords.getMaxSize();
      crf.knownLCWords.setMaxSize(-1);
      crf.train();
      crf.knownLCWords.setMaxSize(knownLCWordsLimit);
      timing.done(log, "CRFClassifier training");
    } else {
      crf.loadDefaultClassifier();
    }

    crf.loadTagIndex();

    if (serializeTo != null) {
      crf.serializeClassifier(serializeTo);
    }

    if (crf.flags.serializeWeightsTo != null) {
      crf.serializeWeights(crf.flags.serializeWeightsTo);
    }

    if (crf.flags.serializeFeatureIndexTo != null) {
      crf.serializeFeatureIndex(crf.flags.serializeFeatureIndexTo);
    }

    if (crf.flags.serializeFeatureIndexToText != null) {
      crf.serializeFeatureIndexToText(crf.flags.serializeFeatureIndexToText);
    }

    if (serializeToText != null) {
      crf.serializeTextClassifier(serializeToText);
    }

    if (testFile != null) {
      // todo: Change testFile to call testFiles with a singleton list
      DocumentReaderAndWriter<CoreLabel> readerAndWriter = crf.defaultReaderAndWriter();
      if (crf.flags.searchGraphPrefix != null) {
        crf.classifyAndWriteViterbiSearchGraph(testFile, crf.flags.searchGraphPrefix, readerAndWriter);
      } else if (crf.flags.printFirstOrderProbs) {
        crf.printFirstOrderProbs(testFile, readerAndWriter);
      } else if (crf.flags.printFactorTable) {
        crf.printFactorTable(testFile, readerAndWriter);
      } else if (crf.flags.printProbs) {
        crf.printProbs(testFile, readerAndWriter);
      } else if (crf.flags.useKBest) {
        int k = crf.flags.kBest;
        crf.classifyAndWriteAnswersKBest(testFile, k, readerAndWriter);
      } else if (crf.flags.printLabelValue) {
        crf.printLabelInformation(testFile, readerAndWriter);
      } else {
        crf.classifyAndWriteAnswers(testFile, readerAndWriter, true);
      }
    }

    if (testFiles != null) {
      List<File> files = Arrays.stream(testFiles.split(",")).map(File::new).collect(Collectors.toList());
      if (crf.flags.printProbs) {
        crf.printProbs(files, crf.defaultReaderAndWriter());
      } else {
        crf.classifyFilesAndWriteAnswers(files, crf.defaultReaderAndWriter(), true);
      }
    }

    if (textFile != null) {
      crf.classifyAndWriteAnswers(textFile, crf.plainTextReaderAndWriter(), false);
    }

    if (textFiles != null) {
      List<File> files = Arrays.stream(textFiles.split(",")).map(File::new).collect(Collectors.toList());
      crf.classifyFilesAndWriteAnswers(files);
    }

    if (crf.flags.readStdin) {
      crf.classifyStdin();
    }
  } // end main

} // end class CRFClassifier
