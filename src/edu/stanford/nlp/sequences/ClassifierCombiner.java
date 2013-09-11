package edu.stanford.nlp.sequences;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;

import java.util.*;
import java.io.IOException;

/**
 * Merges the outputs of two classifiers according to a Main/Auxiliary 
 * distinction.
 * Auxiliary classifiers only contribute classifications of labels that
 * Main classifiers don't label.
 * <p/>
 * <b>Usage</b>
 * <p>For running a trained model with two serialized classifiers: <p>
 * <code>
 * java -server -mx1000m edu.stanford.nlp.ie.crf.CRFClassifier -loadClassifier
 * mainClassifier.3class.gz -loadAuxClassifier auxClassifier.7class.gz
 * -textFile samplesentences.txt
 * </code><p>
 *
 * @author Jenny Finkel
 * @author Chris Cox   
 */
public class ClassifierCombiner {

  private static final boolean DEBUG = false;
  private static final boolean DEBUG_VERBOSE = false;
//  private Properties props;
  private SequenceClassifier mainClassifier;
  private SequenceClassifier auxClassifier;
  
//   private static final String DEFAULT_CLASSIFIER_PATH="/u/nlp/data/ner/dist/ner-en-3class.crf.gz";
//   private static final String DEFAULT_AUX_CLASSIFIER_PATH="/u/nlp/data/ner/dist/muc.7class.crf.gz";

//  private static final String DEFAULT_CLASSIFIER_PATH="/u/jrfinkel/scr/combo_tests/conll.ser.gz";
//  private static final String DEFAULT_AUX_CLASSIFIER_PATH="/u/jrfinkel/scr/combo_tests/muc.ser.gz";

  private static final String DEFAULT_AUX_CLASSIFIER_PATH="/u/nlp/data/ner/goodClassifiers/english.muc.7class.distsim.crf.ser.gz";
  private static final String DEFAULT_CLASSIFIER_PATH="/u/nlp/data/ner/goodClassifiers/english.conll.4class.distsim.crf.ser.gz";

  
  /**
   * @param p Properties File that specifies <code>loadClassifier</code>
   * and <code>loadAuxClassifier</code>
   */
  public ClassifierCombiner(Properties p) throws IOException, ClassNotFoundException {
    //props = p;
    
    String loadPath = p.getProperty("loadClassifier");
    if (loadPath == null) {
      loadPath = DEFAULT_CLASSIFIER_PATH;
    } 
    String loadPath2 = p.getProperty("loadAuxClassifier");
    if (loadPath2 == null) {
      loadPath2 = DEFAULT_AUX_CLASSIFIER_PATH;
    }    

    auxClassifier = SequenceClassifier.getClassifier(new String[]{"-loadClassifier", loadPath2});

    //auxClassifier.flags.setProperties(p);

    if (DEBUG) {
      System.err.println("Successfully loaded auxiliary classifier.");
    }

    mainClassifier = SequenceClassifier.getClassifier(new String[]{"-loadClassifier", loadPath});

    //mainClassifier.flags.setProperties(p);
    
    if (DEBUG) {
      System.err.println("Successfully loaded main classifier");
    }
  }

  private List<CoreLabel> mergeDocuments(List<CoreLabel> mainDocument, List<CoreLabel> auxDocument) {
    Set mainLabels = new HashSet(mainClassifier.getModelFactory().metaInfo().getLabels());
    Set auxLabels = new HashSet(auxClassifier.getModelFactory().metaInfo().getLabels());
    String background = (String)mainClassifier.getModelFactory().metaInfo().backgroundLabel();
    auxLabels.removeAll(mainLabels);
    if (DEBUG) {
      System.err.println("mergeDocuments: Using main classifier for " + mainLabels);
      System.err.println("mergeDocuments: Using aux classifier for " + auxLabels);
    }
    if (DEBUG_VERBOSE) {
      int sz1 = mainDocument.size();
      int sz2 = auxDocument.size();
      if (sz1 != sz2) {
        System.err.println("ClassifierCombiner: size mismatch!");
      }
      System.err.println("----------");
      for (int i = 0 ; i < sz1; i++) {
        CoreLabel f1 = mainDocument.get(i);
        CoreLabel f2 = auxDocument.get(i);
        if ( ! f1.word().equals(f2.word())) {
          System.err.println("NER: word mismatch " + f1.word() + " != " +
                             f2.word());
        }
        System.err.println(f1.word() + "\t" + f1.ner() + "\t" + f2.ner());
      }
      System.err.println("----------");
    }
    boolean insideMainTag=false;
    boolean insideAuxTag = false;
    boolean auxTagValid = true;
    String prevAnswer = background;
    Collection <CoreLabel> constituents = new ArrayList<CoreLabel>();

    Iterator<CoreLabel> auxIterator = auxDocument.listIterator();

    for (CoreLabel wMain : mainDocument) {
      CoreLabel wAux = auxIterator.next();
      String auxAnswer = wAux.ner();
      String mainAnswer = wMain.ner();
      insideMainTag = !mainAnswer.equals(background);

      /*if the auxiliary classifier gave it one of the labels unique to
        auxClassifier, we set the mainLabel to that.*/
      //if (auxLabels.contains(auxAnswer)) {
      if (auxLabels.contains(auxAnswer)) {
        if (!prevAnswer.equals(auxAnswer) && !prevAnswer.equals(background)) {
          if (auxTagValid){
            for (CoreLabel wi : constituents) {
              wi.setNER(prevAnswer);
            }
          }
          constituents = new ArrayList<CoreLabel>();
        }
        insideAuxTag = true;
        if (insideMainTag) { auxTagValid = false; }
        prevAnswer=auxAnswer;
        constituents.add(wMain);
      } else {
        if(insideAuxTag) {
          if (auxTagValid){
            for (CoreLabel wi : constituents) {
              wi.setNER(prevAnswer);
            }
          }
          constituents = new ArrayList<CoreLabel>();
        }
        insideAuxTag=false;
        auxTagValid = true;
        prevAnswer = background;
      }
    }
    return mainDocument;
  }

  public List<CoreLabel> testSentence(List<? extends HasWord> s) {
    List<CoreLabel> toLabelAux = new ArrayList<CoreLabel>();
    List<CoreLabel> toLabelMain = new ArrayList<CoreLabel>();
    for (HasWord ml : s) {
      CoreLabel ml1 = new CoreLabel();
      ml1.setWord(ml.word());
      toLabelAux.add(ml1);
      CoreLabel ml2 = new CoreLabel();
      ml2.setWord(ml.word());
      toLabelMain.add(ml2);
    }
    mainClassifier.testSentence(toLabelMain, NamedEntityTagAnnotation.class);
    auxClassifier.testSentence(toLabelAux, NamedEntityTagAnnotation.class);

//     System.err.println("=====================================================");
    
//     for (CoreLabel fl : toLabelAux) {
//       System.err.print(fl.word()+"["+fl.ner()+"] ");
//     }
//     System.err.println();

//     for (CoreLabel fl : toLabelMain) {
//       System.err.print(fl.word()+"["+fl.ner()+"] ");
//     }
//     System.err.println();

    
    List<CoreLabel>nerOutput = mergeDocuments(toLabelMain,toLabelAux);
    //QuantifiableEntityNormalizer.addNormalizedQuantitiesToEntities(nerOutput);

    for (CoreLabel fl : nerOutput) {
      System.err.print(fl.word()+"["+fl.ner()+"] ");
    }
    System.err.println();

    return nerOutput;
  }

}
