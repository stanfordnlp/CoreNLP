package edu.stanford.nlp.ie.crf;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Exports CRF features for use with other programs.
 * Usage: CRFFeatureExporter -prop crfClassifierPropFile -trainFile inputFile -exportFeatures outputFile
 * - Output file is automatically gzipped/b2zipped if ending in gz
 * - Currently exports features in a format that can be read by a modified crfsgd
 *   (crfsgd assumes features are gzipped)
 * TODO: Support other formats (like crfsuite)
 *
 * @author Angel Chang
 */
public class CRFFeatureExporter<IN extends CoreMap>  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(CRFFeatureExporter.class);
  private char delimiter = '\t';
  private static final String eol = System.lineSeparator();
  private CRFClassifier<IN> classifier;

  public CRFFeatureExporter(CRFClassifier<IN> classifier)
  {
    this.classifier = classifier;
  }

  /**
   * Prefix features with U- (for unigram) features
   * or B- (for bigram) features
   * @param feat String representing the feature
   * @return new prefixed feature string
   */
  private static String ubPrefixFeatureString(String feat)
  {
    if (feat.endsWith("|C")) {
      return "U-" + feat;
    } else if (feat.endsWith("|CpC")) {
      return "B-" + feat;
    } else {
      return feat;
    }
  }

  /**
   * Constructs a big string representing the input list of CoreLabel,
   *  with one line per token using the following format
   * word label feat1 feat2 ...
   *  (where each space is actually a tab).
   * Assumes that CoreLabel has both TextAnnotation and AnswerAnnotation.
   * @param document List of CoreLabel
   *        (does not have to represent a "document", just a sequence of text,
   *         like a sentence or a paragraph)
   * @return String representation of features
   */
  private String getFeatureString(List<IN> document) {
     int docSize = document.size();
     if (classifier.flags.useReverse) {
      Collections.reverse(document);
    }

    StringBuilder sb = new StringBuilder();
    for (int j = 0; j < docSize; j++) {
      IN token = document.get(j);
      sb.append(token.get(CoreAnnotations.TextAnnotation.class));
      sb.append(delimiter);
      sb.append(token.get(CoreAnnotations.AnswerAnnotation.class));

      CRFDatum<Collection<String>,CRFLabel> d = classifier.makeDatum(document, j, classifier.featureFactories);

      List<Collection<String>> features = d.asFeatures();
      for (Collection<String> cliqueFeatures : features) {
        List<String> sortedFeatures = new ArrayList<>(cliqueFeatures);
        Collections.sort(sortedFeatures);
        for (String feat : sortedFeatures) {
          feat = ubPrefixFeatureString(feat);
          sb.append(delimiter);
          sb.append(feat);
        }
      }
      sb.append(eol);
    }
    if (classifier.flags.useReverse) {
      Collections.reverse(document);
    }
    return sb.toString();
  }

  /**
   * Output features that have already been converted into features
   *  (using documentToDataAndLabels) in format suitable for CRFSuite.
   * Format is with one line per token using the following format
   * label feat1 feat2 ...
   *  (where each space is actually a tab)
   * Each document is separated by an empty line.
   *
   * @param exportFile file to export the features to
   * @param docsData array of document features
   * @param labels correct labels indexed by document, and position within document
   */
  public void printFeatures(String exportFile, int[][][][] docsData, int[][] labels)  {
    try {
      PrintWriter pw = IOUtils.getPrintWriter(exportFile);
      for (int i = 0; i < docsData.length; i++) {
        for (int j = 0; j < docsData[i].length; j++) {
          StringBuilder sb = new StringBuilder();
          int label = labels[i][j];
          sb.append(classifier.classIndex.get(label));
          for (int k = 0; k < docsData[i][j].length; k++) {
            for (int m = 0; m < docsData[i][j][k].length; m++) {
              String feat = classifier.featureIndex.get(docsData[i][j][k][m]);
              feat = ubPrefixFeatureString(feat);
              sb.append(delimiter);
              sb.append(feat);
            }
          }
          pw.println(sb.toString());
        }
        pw.println();
      }
      pw.close();
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  /**
   * Output features from a collection of documents to a file
   * Format is with one line per token using the following format
   * word label feat1 feat2 ...
   *  (where each space is actually a tab)
   * Each document is separated by an empty line
   * This format is suitable for modified crfsgd.
   *
   * @param exportFile file to export the features to
   * @param documents input collection of documents
   */
  public void printFeatures(String exportFile, Collection<List<IN>> documents) {
    try {
      PrintWriter pw = IOUtils.getPrintWriter(exportFile);
      for (List<IN> doc:documents) {
        String str = getFeatureString(doc);
        pw.println(str);
      }
      pw.close();
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public static void main(String[] args) throws Exception {
    StringUtils.logInvocationString(log, args);
    Properties props = StringUtils.argsToProperties(args);
    CRFClassifier<CoreLabel> crf = new CRFClassifier<>(props);
    String inputFile = crf.flags.trainFile;
    if (inputFile == null) {
      log.info("Please provide input file using -trainFile");
      System.exit(-1);
    }
    String outputFile = crf.flags.exportFeatures;
    if (outputFile == null) {
      log.info("Please provide output file using -exportFeatures");
      System.exit(-1);
    }
    CRFFeatureExporter<CoreLabel> featureExporter = new CRFFeatureExporter<>(crf);
    Collection<List<CoreLabel>> docs =
      crf.makeObjectBankFromFile(inputFile, crf.makeReaderAndWriter());
    crf.makeAnswerArraysAndTagIndex(docs);
    featureExporter.printFeatures(outputFile, docs);
  }

}
