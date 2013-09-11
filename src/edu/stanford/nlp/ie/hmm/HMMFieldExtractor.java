package edu.stanford.nlp.ie.hmm;

import edu.stanford.nlp.ie.AbstractFieldExtractor;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * FieldExtractor wrapper for hidden Markov model information extraction.
 * You must create and train your HMM separately, this class just hooks up
 * an existing HMM to the FieldExtractor framework. You can specify that the
 * HMM returns either the extracted fields with highest probability of being
 * generated or the extracted fields found earliest in the document.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public class HMMFieldExtractor extends AbstractFieldExtractor {
  /**
   * HMM used to do the information extraction.
   *
   * @serial
   */
  private final HMM hmm;

  /**
   * HMM's targetFields except for (Background).
   *
   * @serial
   */
  private final String[] extractableFields;

  /**
   * Whether to pull out the answers occurring earliest in the document or
   * the answers with highest probability of being generated.
   *
   * @serial
   */
  private boolean useFirstAnswers;

  /**
   * Used for serialization compatibility across minor edits
   */
  private static final long serialVersionUID = -1639337761685832L;

  /**
   * Constructs a new FieldExtractor using the given HMM and unique name.
   *
   * @param hmm             underlying hidden markov model to do extraction
   * @param name            unique name for this extractor
   * @param useFirstAnswers whether to return earliest or highest-prob answers in document
   */
  public HMMFieldExtractor(HMM hmm, String name, boolean useFirstAnswers) {
    this.hmm = hmm;
    setName(name);
    setDescription("HMM FieldExtractor");
    setUseFirstAnswers(useFirstAnswers);

    // pulls out the HMM's target fields, discarding (Background) if needed.
    String[] targetFields = hmm.getTargetFields();
    if (targetFields.length > 0 && targetFields[0].equals("(Background)")) {
      extractableFields = new String[targetFields.length - 1];
      for (int i = 1; i < targetFields.length; i++) {
        extractableFields[i - 1] = targetFields[i];
      }
    } else {
      extractableFields = targetFields;
    }
  }

  /**
   * Constructs a new FieldExtractor using the best answer.
   */
  public HMMFieldExtractor(HMM hmm, String name) {
    this(hmm, name, false);
  }

  /**
   * Returns whether earliest or highest-prob answers in doc will be returned.
   */
  public boolean getUseFirstAnswers() {
    return (useFirstAnswers);
  }

  /**
   * Sets whether to return the string occurring earliest in the
   * document for each field type or the highest-probability extracted string.
   * It's empirically quite a useful heuristic to go with the first
   * instance of each field you find, because more important information is
   * often located higher up in documents.
   */
  public void setUseFirstAnswers(boolean useFirstAnswers) {
    this.useFirstAnswers = useFirstAnswers;
  }


  /**
   * Returns the set of fields this HMM knows how to extract.
   */
  public String[] getExtractableFields() {
    return (extractableFields);
  }

  /**
   * Returns a Map from the HMM's extractable fields to the extracted value
   * from the given text.
   */
  public Map extractFields(String text) {
    // performs HMM extraction
    Map answers = getUseFirstAnswers() ? hmm.firstAnswers(text) : hmm.bestAnswers(text);

    // converts Integer types to field names
    Map extractedFields = new HashMap();
    Iterator iter = answers.keySet().iterator();
    while (iter.hasNext()) {
      Integer type = (Integer) iter.next();
      String fieldName = hmm.getTargetFields()[type.intValue()];
      extractedFields.put(fieldName, answers.get(type));
    }

    // adds blank entries for non-extracted fields
    for (int i = 0; i < extractableFields.length; i++) {
      if (!extractedFields.containsKey(extractableFields[i])) {
        extractedFields.put(extractableFields[i], "");
      }
    }

    return (extractedFields);
  }

  /**
   * Command-line utility for loading a serialized HMM, wrapping it in an
   * HMMFieldExtractor and serializing the result.<p>
   * <pre>Usage: java edu.stanford.nlp.ie.hmm.HMMFieldExtractor extractorName hmmFile useFirstAnswers serializedExtractorFilename</pre><p>
   * <tt>extractorName</tt> is a unique name for this extractor,
   * <tt>hmmFile</tt>
   * is the serialized HMM to use, <tt>useFirstAnswers</tt> is "true" for
   * extracting earliest answers and "false" for extracting highest-prob
   * answers, and <tt>serializedExtractorFilename</tt> is the name of the
   * file to write the serialized HMMFieldExtractor to.
   */
  public static void main(String[] args) {
    if (args.length < 4) {
      System.err.println("Usage: java edu.stanford.nlp.ie.hmm.HMMFieldExtractor extractorName hmmFilename useFirstAnswers serializedExtractorFilename");
      System.exit(1);
    }

    try {
      String name = args[0];
      String hmmFilename = args[1];
      boolean useFirstAnswers = Boolean.parseBoolean(args[2]);
      String outFilename = args[3];

      // loads serialized hmm
      System.err.println("Loading serialized HMM from " + hmmFilename + "...");
      ObjectInputStream ois = new ObjectInputStream(new FileInputStream(hmmFilename));
      HMM hmm = (HMM) ois.readObject();
      ois.close();

      // composes and writes out extractor
      HMMFieldExtractor hmmfe = new HMMFieldExtractor(hmm, name, useFirstAnswers);
      System.err.println("Storing HMMFieldExtractor named \"" + name + "\" to " + outFilename + "...");
      hmmfe.storeExtractor(new File(outFilename));
    } catch (Exception e) {
      System.err.println("An error occurred while trying to create the HMMFieldExtractor:");
      e.printStackTrace();
    }
  }
}
