package edu.stanford.nlp.ie.hmm;

import edu.stanford.nlp.ie.Corpus;

import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.Properties;

/**
 * A class for building a single HMM by combining multiple target HMMs and a
 * context HMM. Can either simply load and merge serialized HMMs or actually do
 * the training given target fields.  This gets called via Extractor if you
 * ask for <code>hmmType=merged</code>.
 *
 * @author Jim McFadden
 * @author Huy Nguyen (htnguyen@cs.stanford.edu)
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public class MergeTrainer {

  /**
   * This isn't a class that can be instantiated
   */
  private MergeTrainer() {
  }

  /**
   * Creates a merged HMM on the corpus constructed from the given file,
   * using the given set of target fields.
   *
   * @see #mergeTrain(edu.stanford.nlp.ie.Corpus,Properties,boolean)
   */
  public static HMM mergeTrain(String trainFile, String[] targetFields, Properties props, boolean verbose) {
    return (mergeTrain(new Corpus(trainFile, targetFields), props, verbose));
  }

  /**
   * Creates a merged HMM by training a separate HMM for each target field
   * in the given Corpus and a single context HMM, then glueing them all
   * together.
   * <p/>
   * <tt>numContextStates</tt> determines the number of prefix and suffix
   * states around each target
   * <p/>
   * If <tt>learnTargetStructure</tt> is true, the target HMMs are learned
   * using
   * the {@link TargetTrainer} code. Otherwise a fixed 2x2 structure is used.
   * If <code>contextType</code> is <code>learned</code> there is learning
   * of a context structure. Otherwise you get a <code>chain</code>
   * If <tt>verbose</tt> is true, prints a lot of stuff to stderr in the
   * process.
   */
  public static HMM mergeTrain(Corpus trainDocs, Properties props, boolean verbose) {
    if (props == null) {
      props = Extractor.getDefaultProperties();
    }

    boolean trainNow = !("conditional").equals(props.getProperty("trainType"));

    String[] corpusTargetFields = trainDocs.getTargetFields();
    String[] targetFields = new String[corpusTargetFields.length - 1];
    // get rid of "(Background)" target
    for (int i = 1; i < corpusTargetFields.length; i++) {
      targetFields[i - 1] = corpusTargetFields[i];
    }

    HMM chmm = null;
    String contextFile = props.getProperty("context.hmmFile");

    // load serialized context HMM, if specified
    if (contextFile != null) {
      if (verbose) {
        System.err.println("Loading " + contextFile + "...");
      }
      try {
        ObjectInputStream in = new ObjectInputStream(new FileInputStream(contextFile));
        chmm = (HMM) in.readObject();
      } catch (Exception e) {
        e.printStackTrace();
      }
    } else {
      // if serialized context HMM is not specified, train a context HMM
      chmm = new ContextTrainer().train(trainDocs, props, targetFields, verbose);
    }

    // trains a target HMM for each field
    HMM[] thmms = new HMM[targetFields.length];

    for (int i = 0; i < targetFields.length; i++) {
      String targetFileField = targetFields[i] + ".hmmFile";
      String targetFile = props.getProperty(targetFileField);

      // load serialized target HMM, if specified
      if (targetFile != null) {
        if (verbose) {
          System.err.println("Loading " + targetFile + "...");
        }
        try {
          ObjectInputStream in = new ObjectInputStream(new FileInputStream(targetFile));
          thmms[i] = (HMM) in.readObject();
        } catch (Exception e) {
          e.printStackTrace();
        }
      } else {
        // if serialized target HMM is not specified, train target HMM
        thmms[i] = new TargetTrainer().train(trainDocs, targetFields[i], props, verbose);
      }
    }

    // glues the context and target HMMs together
    if (verbose) {
      System.err.println("Merging the HMMs together...");
    }
    HMM finalHMM = new HMM(chmm, thmms, trainNow, verbose);
    if (!trainNow) {
      finalHMM.train(trainDocs, props, verbose);
    }
    return (finalHMM);
  }
}
