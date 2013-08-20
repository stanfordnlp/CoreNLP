package edu.stanford.nlp.ie.pascal;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.sequences.SequenceGibbsSampler;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.EntityRuleAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.GoldAnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.IsURLAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NormalizedNamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ShapeAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ie.crf.FactorTable;
import edu.stanford.nlp.ie.ner.CMMClassifier;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Distribution;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Pair;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Given a trained {@link AbstractSequenceClassifier} and a document, generates sample taggings for the document from
 * distribution of labellings according to the classifier.
 * <code>CMMSampler</code> generates {@link OverloadedPascalTemplate}s which in turn provide
 * a distribution of templates (currently {@link PascalTemplate}s) that correspond to the classifier's
 * labelling of this document.
 *
 * @author Chris Cox
 */

public class CMMSampler {

  AbstractSequenceClassifier<CoreLabel> classifier;
  boolean useCRF = false; //CRF or CMM
  Pair dataAndLabels; //only used in CRF
  FactorTable[] factorTable; //only used in CRF
  SequenceGibbsSampler gibbsSampler; //only used in CRF
  List<CoreLabel> wordInfos;
  int numSamples;
  List<CoreLabel>[] samples;
  Counter<String>[] fieldValueCounter;     //each counter hashes individual field values to scores.
  Counter<PascalTemplate> templateCounter;  //hashes full templates to scores.
  private boolean sampleTemplates = true; //whether or not full templates are to be hashed

  public static ArrayList<CoreLabel> readWordInfos(String filename) throws FileNotFoundException, IOException {
    BufferedReader br = new BufferedReader(new FileReader(filename));

    ArrayList<CoreLabel> testList = new ArrayList<CoreLabel>();

    for (String line ; (line = br.readLine()) != null; ) {
      StringTokenizer tok = new StringTokenizer(line);
      CoreLabel wi = new CoreLabel();
      try {
        wi.setWord(tok.nextToken());
        wi.set(PartOfSpeechAnnotation.class, tok.nextToken());
        wi.set(ShapeAnnotation.class, tok.nextToken());
        wi.set(NamedEntityTagAnnotation.class, tok.nextToken());
        wi.set(IsURLAnnotation.class, tok.nextToken());
        wi.set(EntityRuleAnnotation.class, tok.nextToken());
        wi.set(NormalizedNamedEntityTagAnnotation.class, tok.nextToken());
        wi.set(GoldAnswerAnnotation.class, tok.nextToken());
        testList.add(wi);
      } catch (NoSuchElementException e) {
        if (wi.word() != null) {
          // System.err.println("Missing value for word " + wi.word());
          wi.set(GoldAnswerAnnotation.class, "0");
          testList.add(wi);
        }
      }
    }

    return testList;
  }

  /**
   * For testing only.
   * edu.stanford.nlp.ie.pascal.CMMSampler dataFile classifier numSamples useTemplates
   */

  public static void main(String[] args) throws Exception {

    String dataFilename = args[0];
    String classifierFilename = args[1];

    int numSamples = Integer.parseInt(args[2]);
    boolean useTemplates = Boolean.parseBoolean(args[3]);
    CRFClassifier testClass = CRFClassifier.getClassifier(classifierFilename);
    System.out.println("Done loading");
    //String line;
    ArrayList<CoreLabel> testList = readWordInfos(dataFilename);

    System.out.println("New sampler....");
    System.out.println("Generating " + numSamples + " samples.");
    System.out.flush();
    CMMSampler cs = new CMMSampler(testList, testClass, numSamples, useTemplates);
    System.out.println("Calling sampleDocuments");
    cs.sampleDocuments();
    cs.printTemplates();

  }

  /**
   * Returns a {@link Distribution} corresponding to the sampled values
   * for a particular field name.
   */
  public Distribution<String> getFieldValueDistribution(String fieldName) {
    int index = PascalTemplate.getFieldIndex(fieldName);
    System.err.println("Getting fieldValueDist");
    if (index == -1) {
      return null;
    } else {
      return Distribution.getDistribution(fieldValueCounter[index]);
    }


  }

  /**
   * The classifier should be trained before its passed to CMMSampler.
   * The <code>wordInfos</code>
   * argument should be a {@link List} of {@link CoreLabel}s.
   */
  public CMMSampler(List<CoreLabel> wordInfos, AbstractSequenceClassifier classifier, int numToSample, boolean sampleTemplates) {
    this.wordInfos = wordInfos;
    this.classifier = classifier;
    useCRF = (classifier.getClass().getName().equals("edu.stanford.nlp.ie.crf.CRFClassifier"));
    System.err.println("useCRF : " + useCRF);
    /* if we're using the CRFClassifier, we assign the class field dataAndLabels
     * to use for all sampling. CMMClassifier doesn't use this field. */
    if (useCRF) {

      CRFClassifier crf = (CRFClassifier) classifier;
      //    this.factorTable =
      // CRFLogConditionalObjectiveFunction.
      //getCalibratedCliqueTree(crf.weights, data,
      //                        crf.labelIndices, crf.classIndex.size());
    }
    this.numSamples = numToSample;
    this.samples = new List[numToSample];
    this.sampleTemplates = sampleTemplates;
    if (this.sampleTemplates) {
      //System.err.println("Sampling whole templates.");
    } else {
      System.err.println("Sampling individual field values.");
    }
    this.fieldValueCounter = new ClassicCounter[PascalTemplate.fields.length - 1];
    for (int i = 0; i < PascalTemplate.fields.length - 1; i++) {
      fieldValueCounter[i] = new ClassicCounter<String>();
    }

  }

  public CMMSampler(List<CoreLabel> wordInfos, AbstractSequenceClassifier classifier, int numToSample) {
    this(wordInfos, classifier, numToSample, false);
  }

  /**
   * Populates a list of documents, each of which contains a
   * labelling sampled from
   * the distribution.  The templates from this labelling are
   * extracted to the templateCounter.
   */

  public Counter<PascalTemplate> sampleDocuments() {
    //Deprecated.
    if (sampleTemplates) {
      this.templateCounter = new ClassicCounter<PascalTemplate>();
    }
    if (useCRF) {
      CRFClassifier crf = (CRFClassifier) classifier;
      System.err.println("Collecting samples");
      //SequenceGibbsSampler gibbsSampler = crf.getGibbsSampler(wordInfos);
      SequenceGibbsSampler gibbsSampler = null;
      List<int[]> samples = gibbsSampler.collectSamples(null, this.numSamples, 50);
      for (int[] answers : samples) {
        int i = 0;
        for (CoreLabel wi : wordInfos) {
          wi.set(AnswerAnnotation.class, classifier.classIndex.get(answers[i++]));
        }
        extractTemplate(wordInfos);
      }
      System.err.println("Done Sampling");
      return templateCounter;
    } else {
      for (int i = 0; i < this.numSamples; i++) {
        //System.err.println("Sampling document" + i);
        samples[i] = sampleOneDocument();
        if (sampleTemplates) {
          extractTemplate(samples[i]);
        } else {
          countFields(samples[i]);
        }
      }

      return templateCounter;
    }
  }

  /**
   * Returns a {@link CliqueTemplates} object populated from the
   * template counter.  Only to be called after sampleDocuments.
   */

  public CliqueTemplates getCliqueTemplates() {

    CliqueTemplates ct = new CliqueTemplates();
    Set<PascalTemplate> keys = templateCounter.keySet();
    for (PascalTemplate pt : keys) {
      double score = templateCounter.getCount(pt);
      pt.unpackToCliqueTemplates(ct, score);
    }
    ct.urls = grabURLStrings();
    return ct;
  }


  /**
   * Returns the {@link Distribution} associated with
   * this object's templateCounter.
   */
  public Distribution getDistribution() {
    return getDistribution(this.templateCounter);
  }

  public Set getCandidateFieldValues(String fieldName) {
    int index = PascalTemplate.getFieldIndex(fieldName);
    Counter c = fieldValueCounter[index];
    return c.keySet();
  }


  /**
   * Returns a {@link Distribution} represented by the values in the array.  The keys
   * in the distribution are the indices of these values in the array.
   */
  public static Distribution<Integer> getAdjustedDistributionFromDoubleArray(double[] array, CRFClassifier crf) {
    ClassicCounter<Integer> c = new ClassicCounter<Integer>();
    //LOWER BACKGROUND VALUE
    //System.err.println("Getting adjusted distribution");
    c.setCount(Integer.valueOf(0), array[0] * .8);
    //System.err.print(array[0]);
    for (int i = 1; i < array.length; i++) {
      //System.err.print(" "+array[i]);
      c.setCount(Integer.valueOf(i), array[i]);
    }
    //       System.err.print("\n");
    return (Distribution.getDistribution(c));
  }

  /**
   * Takes a HashMap of PascalTemplates to doubles and
   * returns a {@link Distribution}.
   */
  public static Distribution<PascalTemplate> getDistribution(Counter<PascalTemplate> counter) {

    ClassicCounter<PascalTemplate> c = new ClassicCounter<PascalTemplate>();

    Set<PascalTemplate> keys = counter.keySet();
    for (PascalTemplate pt : keys) {
      c.setCount(pt, counter.getCount(pt));
    }
    return Distribution.getDistribution(c);
  }

  /**
   * Returns a {@link PascalTemplate}, each field of which contains the
   * most sampled field value on a per field basis.
   */
  public PascalTemplate getBestFieldValuesTemplate() {
    PascalTemplate pt = new PascalTemplate();
    for (int i = 0; i < fieldValueCounter.length; i++) {
      pt.setValue(i, Counters.argmax(fieldValueCounter[i]));
    }
    return pt;
  }

  public void printTemplates() {

    if (sampleTemplates) {
      double best = 0.0;
      PascalTemplate bestTemp = null;
      Set<PascalTemplate> keys = templateCounter.keySet();
      for (PascalTemplate pt : keys) {
        double score = templateCounter.getCount(pt);
        if (score > best) {
          best = score;
          bestTemp = pt;
        }
      }
      System.err.println("=========================================");
      System.err.println("Sampled " + this.numSamples + " documents.");
      System.err.println("Found " + templateCounter.size() + " templates.");
      System.err.println("Best: " + bestTemp);
      System.err.println("Score: " + best);
      Distribution<PascalTemplate> d = getDistribution(templateCounter);
      System.err.println("Distribution:\n\n" + d.toString());
    }
  }

  /**
   * Prints the values for each template slot with the highest score.
   */

  public void printBestFieldValues() {
    System.out.println("Template field distributions:\n======================");
    for (int i = 0; i < fieldValueCounter.length; i++) {
      System.err.println(PascalTemplate.fields[i] + ":");
      // System.out.println(PascalTemplate.fields[i]+"\n-----------");
      System.out.println(fieldValueCounter[i]);
    }
  }

  /**
   * Returns a list of key values, which corresponds to one
   * randomly sampled labelling
   * of the classifier on the document according to the
   * confidence distributions.
   */

  private List<CoreLabel> sampleOneDocument() {
    //    System.err.print(".");
    String lastPascalTag = "0";
    String currentTag = "0";
    /* Since the internals of CRFClassifier and CMMCLassifier are quite different,
     * we use different methods for generating a sampling..... */

    // CRF SAMPLING
    //================
    if (useCRF) {
      //  CRFClassifier crf = (CRFClassifier)classifier;
      //CRFClassifier.Scorer scorer = new CRFClassifier.Scorer(this.factorTable);
      //int[] sampledTags = new int[wordInfos.size()+crf.window+1];
      //for(int i = 0; i < wordInfos.size() ; i++){
      // WordInfo currWord = (WordInfo)wordInfos.get(i);
      //   int actualPos = i + crf.window -1;
      // double[] scores = scorer.scoresOf(sampledTags, actualPos);
      // Distribution d = getAdjustedDistributionFromDoubleArray(scores,crf);
      // int sampledTag = ((Integer)d.sampleFrom()).intValue();
      // sampledTags[actualPos] = sampledTag;
      // String ans = (String)crf.classIndex.get(sampledTag);
      //System.err.println("For " + i + " Assigned label: " + crf.classIndex.get(sampledTag));
      //  currWord.setAnswer(ans);
      // }
      // return wordInfos;

      //CMM SAMPLING
      //===============
      return null;
    } else {
      List<CoreLabel> list = wordInfos;
      for (CoreLabel w : list) {
        w.set(AnswerAnnotation.class, null);
      }
      /* Iterates through each word(one on each line),
       *  getting the classifier's score
       * counter for each.  Samples a label from the distribution
       * of labellings.
       */

      int lsize = list.size();
      for (int i = 0; i < lsize; i++) {
        CoreLabel currWord = list.get(i);
        Counter<String> c = ((CMMClassifier) classifier).scoresOf(list, i);
        Distribution<String> d = Distribution.getDistributionFromLogValues(c);
        String ans = d.sampleFrom();
        currWord.set(AnswerAnnotation.class, ans);
      }
      return list;
    }
  }

  private Counter<PascalTemplate> extractTemplate(List<CoreLabel> passedList) {

    String lastTag = null;
    OverloadedPascalTemplate temp = new OverloadedPascalTemplate();

    for (CoreLabel w : passedList) {
      String tag = w.get(AnswerAnnotation.class);
      if (!tag.equals("0")) {
        String value = w.word();
        String normalized = w.get(NormalizedNamedEntityTagAnnotation.class);
        boolean isDate = w.get(NamedEntityTagAnnotation.class) != null && w.get(NamedEntityTagAnnotation.class).equalsIgnoreCase("Date");
        if (isDate) {
          value = normalized;
        }
        if (value.equals("*NewLine*")) {
          value = "";
        }
        if (lastTag == null) {
          temp.createNewValue(tag, value);
        } else if (lastTag.equals(tag)) {
          if (!isDate) {
            temp.addToCurrentValue(tag, value);
          }
        } else {
          temp.createNewValue(tag, value);
        }
      }
      lastTag = tag;
    }
    temp.unpackToSingleTemplates(templateCounter, fieldValueCounter);

    return templateCounter;
  }

  /**
   * Writes the document's non-zero fields into the fieldValueCounter.
   * XXX This doesn't handle the last item in the list.
   */
  private void countFields(List<CoreLabel> l) {
    String fieldValue = null;
    String lastTag = null;

    for (CoreLabel w : l) {
      String tag = w.get(AnswerAnnotation.class);

      if (lastTag != null && !lastTag.equals("0") && !lastTag.equals(tag)) {
        int index = PascalTemplate.getFieldIndex(lastTag);
        fieldValueCounter[index].incrementCount(fieldValue);
      }

      //Here we unpack the value to the current field.
      //NOTE we don't count NewLines here.....
      if (!tag.equals("0")) {
        String value = w.word();
        if (value.equals("*NewLine*")) {
          value = "";
        }
        if (lastTag == null) {
          fieldValue = value;
        } else if (lastTag.equals(tag)) {
          if (!value.equals("")) {
            fieldValue = fieldValue.concat(" " + value);
          }
        } else {
          fieldValue = value;
        }
      }
      lastTag = tag;
    }
  }

  /**
   * Returns a {@link List} of all URL Strings in the current document.
   */
  public ArrayList<String> grabURLStrings() {
    ArrayList<String> urlList = new ArrayList<String>();
    boolean insideURL = false;
    String currentURL = "";
    for (CoreLabel wi : wordInfos) {
      boolean currentIsURL = (wi.get(IsURLAnnotation.class)).equals("isURL");
      if (insideURL) {
        if (currentIsURL) {
          currentURL = currentURL.concat(" " + wi.word());
        } else {
          insideURL = false;
          urlList.add(currentURL);
        }
      } else {
        if (currentIsURL) {
          currentURL = wi.word();
          insideURL = true;
        }
      }
    }
    return urlList;
  }
  /**
   * The return value here is an {@link OverloadedPascalTemplate} because
   * the gold answer may have multiple fillers for one field.  Strictly speaking,
   * a {@link PascalTemplate} doesn't allow this.
   *
   * @return the overloaded template containing the gold non-background
   * labellings.
   */
  public OverloadedPascalTemplate getGoldAnswerTemplate() {

    String lastTag = null;
    OverloadedPascalTemplate temp = new OverloadedPascalTemplate();

    for (CoreLabel w : wordInfos) {
      String tag = w.get(GoldAnswerAnnotation.class);
      if (!tag.equals("0")) {
        String value = w.word();
        String normalized = w.get(NormalizedNamedEntityTagAnnotation.class);
        boolean isDate = (w.get(NamedEntityTagAnnotation.class)).equalsIgnoreCase("Date");
        if (isDate) {
          value = normalized;
        }
        if (value.equals("*NewLine*")) {
          value = "";
        }
        if (lastTag == null) {
          temp.createNewValue(tag, value);
        } else if (lastTag.equals(tag)) {
          if (!isDate) {
            temp.addToCurrentValue(tag, value);
          }
        } else {
          temp.createNewValue(tag, value);
        }
      }
      lastTag = tag;
    }
    return temp;
  }
}
