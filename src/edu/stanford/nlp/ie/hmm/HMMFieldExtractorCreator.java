package edu.stanford.nlp.ie.hmm;

import edu.stanford.nlp.ie.AbstractFieldExtractorCreator;
import edu.stanford.nlp.ie.Corpus;
import edu.stanford.nlp.ie.IllegalPropertyException;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;

/**
 * FieldExtractorCreator for HMMs. Similar to Extractor, the HMM is either
 * trained or loaded from a set of properties. Unlike Extractor, there is no
 * notion of training and testing or folds etc, just training an HMM with
 * all the data and target fields you provide.
 * <p/>
 * The <code>main</code> method for creating an <code>HMMFieldExtractor</code>
 * lives in {@link HMMFieldExtractor}, not here.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public class HMMFieldExtractorCreator extends AbstractFieldExtractorCreator {
  /**
   * Constructs a new HMMFieldExtractorCreator using the default properties
   * from {@link Extractor#getDefaultProperties} and the name
   * "Hidden Markov Model".
   */
  public HMMFieldExtractorCreator() {
    setName("Hidden Markov Model");
    setPropertyDefaults(Extractor.getDefaultProperties());

    // required properties
    setPropertyRequired("dataFile");
    setPropertyRequired("targetFields");
    setPropertyRequired("singleMatchStrategy");

    // property classes (so gui can edit them appropriately)
    setPropertyClass("dataFile", PC_FILE);
    setPropertyClass("singleMatchStrategy", Arrays.asList(new String[]{"best_match", "first_match"}));
    setPropertyClass("uniformTransSmoothing", PC_DOUBLE);
    setPropertyClass("trainType", Arrays.asList(new String[]{"joint", "conditional"}));
    setPropertyClass("numCandidates", PC_INTEGER);
    setPropertyClass("targetType", Arrays.asList(new String[]{"fixed", "chain", "ergodic", "learned", "learned2", "lchain"}));
    setPropertyClass("nts", PC_INTEGER);
    setPropertyClass("nbs", PC_INTEGER);
    setPropertyClass("contextType", Arrays.asList(new String[]{"fixed", "flexible", "learned", "ergodic"}));
    setPropertyClass("ncs", PC_INTEGER);
    setPropertyClass("clInitThreshold", PC_INTEGER);
    setPropertyClass("sigmaSquared", PC_DOUBLE);
    setPropertyClass("initEmissions", PC_BOOLEAN);
    setPropertyClass("unseenMode", Arrays.asList(new String[]{"nonexistent", "unk_low_counts", "hold_out_mass"}));
    setPropertyClass("unkModel", Arrays.asList(new String[]{"single_unk", "featural_decomp"}));
    setPropertyClass("unseenProbSource", Arrays.asList(new String[]{"singletons", "held_out"}));
    setPropertyClass("featureSource", Arrays.asList(new String[]{"singletons", "held_out"}));
    setPropertyClass("hmmFile", PC_FILE);
  }

  /**
   * Returns the set of standard properties needed to train an HMM. This is
   * a subset of the properties described in {@link Extractor}.
   */
  @Override
  public Set propertyNames() {
    return (asSet(new String[]{

      "dataFile", "targetFields", "singleMatchStrategy",

      "hmmType", "uniformTransSmoothing", "trainType", "numCandidates", "targetType", "nts", "nbs", "contextType", "ncs", "clInitThreshold", "sigmaSquared", "initEmissions",

      "unseenMode", "unkModel", "unseenProbSource", "featureSource", "unkFeature", "shrinkage",

      "hmmFile"}));
  }

  /**
   * Creates and trains an HMM using the properties and given name.
   * If <tt>hmmType</tt> is null and <tt>hmmFile</tt> is not null, loads
   * a serialized HMM instead. Throws IllegalStateException if certain
   * properties are wrong (hmmType, dataFilename, etc). Most of this code
   * was taken with minor edits from Extractor.
   */
  public edu.stanford.nlp.ie.FieldExtractor createFieldExtractor(String name) throws IllegalPropertyException {
    // to make recoding from extractor minimally painful
    Properties props = new Properties(propertyDefaults);
    props.putAll(properties);
    boolean verbose = false;

    // resulting HMM (either best trained candidate or serialized file)
    HMM hmm = null;

    // data file for training/testing
    String dataFilename = props.getProperty("dataFile");
    if (dataFilename == null || !new File(dataFilename).canRead()) {
      throw(new IllegalPropertyException(this, "must specify valid data file", "dataFile", dataFilename));
    }

    // space-separated list of target fields
    String allTargetFields = props.getProperty("targetFields");
    if (allTargetFields == null || allTargetFields.length() == 0) {
      throw(new IllegalPropertyException(this, "must specify at least one target field", "targetFields", allTargetFields));
    }
    String[] targetFields = allTargetFields.split(" ");

    String hmmType = props.getProperty("hmmType");
    if (hmmType != null) {
      boolean useMerged = hmmType.equals("merged"); // whether to
      // train a merged HMM for all fields
      boolean useSMerged = hmmType.equals("s-merged"); // whether to
      // train a separate merged HMM for each field

      // gets predefined structure for non-merged hmm types
      Structure structure = null; // structure to train this hmm with
      if (!useMerged && !useSMerged) {
        try {
          structure = Extractor.getStructure(hmmType, props);
        } // HAVE TO EXPLICITLY REFERECNE EXTRACTOR HERE
        catch (IllegalArgumentException e) {
          throw(new IllegalPropertyException(this, "invalid hmmType: " + hmmType, "hmmType", hmmType));
        }
      }

      // DIFFERENT FROM EXTRACTOR - ALWAYS USES ALL FIELDS AND DOCS
      Corpus trainDocs = new Corpus(dataFilename, targetFields);
      // TODO: add held out splitting

      double bestScore = Double.NEGATIVE_INFINITY; // best score of any candidate hmm so far
      int numCandidates = Integer.parseInt(props.getProperty("numCandidates"));
      boolean trainConditionally = props.getProperty("trainType").equals("conditional");
      for (int k = 0; k < numCandidates; k++) {
        if (verbose && numCandidates > 1) {
          System.err.println("Training candidate HMM " + (k + 1) + " of " + numCandidates);
        }
        HMM candidateHMM;
        if (useMerged || useSMerged) {
          // merged hmm for all fields
          candidateHMM = MergeTrainer.mergeTrain(trainDocs, props, verbose);
        } else {
          // hmm for current field
          candidateHMM = new HMM(structure, HMM.REGULAR_HMM);
          candidateHMM.train(trainDocs, props, verbose);
        }
        double candidateScore = trainConditionally ? candidateHMM.logConditionalLikelihood(trainDocs) : candidateHMM.logLikelihood(trainDocs);
        if (verbose && numCandidates > 1) {
          System.err.println("Score for candidate HMM " + (k + 1) + " of " + numCandidates + ": " + candidateScore);
        }
        if (k == 0 || candidateScore > bestScore) {
          hmm = candidateHMM;
          bestScore = candidateScore;
          if (verbose && numCandidates > 1) {
            System.err.println("Candidate " + (k + 1) + " is best HMM so far");
          }
        }
      }
      if (hmm == null) {
        throw(new IllegalStateException("No candidate HMM was selected!"));
      }
    } else if (props.getProperty("hmmFile") != null) {
      File hmmFile = new File(props.getProperty("hmmFile")); // serialized hmm to load
      if (!hmmFile.canRead()) {
        throw(new IllegalPropertyException(this, "cannot read hmmFile: " + hmmFile, "hmmFile", hmmFile.toString()));
      }

      try {
        // reads in the HMM and tests it against the corpus
        System.err.println("Loading HMM from " + hmmFile + "...");
        hmm = (HMM) new ObjectInputStream(new FileInputStream(hmmFile)).readObject();
        if (verbose) {
          hmm.printProbs(); // print out hmm if -v was specified
        }

      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    // whether to use highest-prob or earliest answers
    boolean useFirstAnswers = "first_match".equals(props.getProperty("singleMatchStrategy"));

    return (new HMMFieldExtractor(hmm, name, useFirstAnswers));
  }

}
