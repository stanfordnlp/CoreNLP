package edu.stanford.nlp.pipeline;

import java.util.*;
import java.text.*;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.NERClassifierCombiner;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.MutableInteger;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Timing;


/**
 * This is a pipeline that takes in a string and returns a list of CoreLabels.
 * The String is tokenized via a tokenizer (such as PTBTokenizerAnnotator), and
 * then other sequence model style annotation can be used to add things like
 * lemmas and POS tags.
 *
 * @author Anna Rafferty
 * @author Christopher Manning
 */
public class StringToAnnotatedWordsPipeline implements Annotator {

  /** Whether to record timing information on each annotator in the pipeline */
  private static final boolean TIME = true;

  private List<Annotator> annotators = new ArrayList<Annotator>();
  private List<MutableInteger> accumulatedTime;


  /**
   * Construct a processing pipeline.  Currently just adds PTBTokenizer,
   * a part-of-speech tagger, and lemmas.
   */
  public StringToAnnotatedWordsPipeline() {
    this(StringUtils.stringToProperties("tokenize,pos,lemma"));
  }


  @SuppressWarnings("unchecked")
  public StringToAnnotatedWordsPipeline(Properties props) {
    try {
      if (PropertiesUtils.hasProperty(props, "tokenize")) {
        annotators.add(new PTBTokenizerAnnotator(false));

        // we always want exactly one sentence, so after tokenizing we
        // use a single sentence splitter
        WordsToSentencesAnnotator wts = new WordsToSentencesAnnotator(false);
        wts.setOneSentence(true);
        annotators.add(wts);
      }
      if (PropertiesUtils.hasProperty(props, "tackbp")) {
        annotators.add(new SimpleXMLAnnotator(false));
      }
      if (PropertiesUtils.hasProperty(props, "pos")) {
        try {
          annotators.add(new POSTaggerAnnotator(true));
        } catch(Exception e) {
          System.err.println("Error loading POSTaggerAnnotator.");
          e.printStackTrace();
        }
      }
      if (PropertiesUtils.hasProperty(props, "lemma")) {
        annotators.add(new MorphaAnnotator(false));
      }
      if (PropertiesUtils.hasProperty(props, "ner")) {
        AbstractSequenceClassifier firstCombiner = new NERClassifierCombiner(
          "/u/nlp/data/ner/goodClassifiers/english.all.3class.distsim.crf.gz",
          "/u/nlp/data/ner/goodClassifiers/english.muc.7class.distsim.crf.gz");
        AbstractSequenceClassifier aux2 = CRFClassifier.getClassifier("/u/nlp/data/ner/goodClassifiers/english.conll.4class.distsim.crf.ser.gz");
        NERClassifierCombiner nerCombiner = 
          new NERClassifierCombiner(firstCombiner, aux2);
        annotators.add(new NERCombinerAnnotator(nerCombiner, false));
        annotators.add(new NumberAnnotator(false));
        annotators.add(new TimeWordAnnotator(false));
        annotators.add(new QuantifiableEntityNormalizingAnnotator(false, false));
      }

      if (TIME) {
        accumulatedTime = new ArrayList<MutableInteger>(annotators.size());
        for (int i = 0, sz = annotators.size(); i < sz; i++) {
          accumulatedTime.add(new MutableInteger());
        }
      }
    } catch (Exception e) {
      System.err.println("Error setting up StringToAnnotatedWordsPipeline");
      e.printStackTrace();
    }
  }


  /**
   * Entry point to the pipeline.  Converts the txt into
   * Annotation format, annotates, and returns the resulting labels.
   */
  public List<CoreLabel> processText(String txt) {
    Annotation annotation = new Annotation(txt);
    annotate(annotation);
    // safe to get(0) because we never split to sentences - if one wanted
    // to do sentence stuff, return type would have to change
    List<CoreMap> sentences = annotation.get(SentencesAnnotation.class);
    return sentences.get(0).get(TokensAnnotation.class);
  }


  public void annotate(Annotation annotation) {
    Iterator<MutableInteger> it;
    Timing t;
    if (TIME) {
      it = accumulatedTime.iterator();
      t = new Timing();
    }
    for (Annotator annotator : annotators) {
      if (TIME) {
        t.start();
      }
      annotator.annotate(annotation);
      if (TIME) {
        int elapsed = (int) t.stop();
        MutableInteger m = it.next();
        m.incValue(elapsed);
      }
    }
  }


  public String timingInformation(int numWords) {
    StringBuilder sb = new StringBuilder();
    if (TIME) {
      NumberFormat nf = new DecimalFormat("0.00");
      sb.append("Annotation pipeline timing information:\n");
      Iterator<MutableInteger> it = accumulatedTime.iterator();
      long total = 0;
      for (Annotator annotator : annotators) {
        MutableInteger m = it.next();
        sb.append(StringUtils.getShortClassName(annotator)).append(": ");
        sb.append(Timing.toSecondsString(m.longValue())).append(" sec.\n");
        total += m.longValue();
      }
      sb.append("TOTAL: ").append(Timing.toSecondsString(total)).append(" sec. for ")
        .append(numWords).append(" tokens at ").append(nf.format(numWords / (((double) total)/1000))).append( " tokens/sec.");
    }
    return sb.toString();
  }


  /**
   * Mainly just for testing, but this runs the pipeline you specify on the
   * text you specify and sends results to stdout
   *
   * @param args Two arguments: The text and the pipeline as a comma-separated
   *     list of things to run
   */
  public static void main(String[] args) {
    if (args.length != 2) {
      System.err.println("usage: StringToAnnotatedWordsPipeline file annotatorsCSV");
      return;
    }
    Timing tim = new Timing();
    int numWords = 0;
    String file = args[0];
    Properties props = StringUtils.stringToProperties(args[1]);
    StringToAnnotatedWordsPipeline staw = new StringToAnnotatedWordsPipeline(props);
    long setupTime = tim.report();
    for (String line : ObjectBank.getLineIterator(file)) {
      List<CoreLabel> labels = staw.processText(line);
      for (CoreLabel lab : labels) {
        // System.out.println(lab.get(TextAnnotation.class) + "(" + lab.get(LemmaAnnotation.class) + ")/" + lab.get(TagAnnotation.class));
        System.out.println(lab.toShorterString("Word","Current","Tag","Lemma","NER","NormalizedNER", "XmlElement"));
        numWords++;
      }
    }
    if (TIME) {
      System.err.println(staw.timingInformation(numWords));
      System.err.println("Pipeline setup: " +
                         Timing.toSecondsString(setupTime) + " sec.");
      System.err.println("Total time for StringToAnnotatedWordsPipeline: " +
                         tim.toSecondsString() + " sec.");
    }
  }

}
