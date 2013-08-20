package edu.stanford.nlp.sequences;

/**
 * Service implementation wrapping a given SequenceClassifier.
 * 
 * @author dramage
 */
public class SequenceClassifierService { // implements ServiceFactory {

  /** The classifier to use in each ServiceSession */
  private SequenceClassifier classifier;
  
//  public SequenceClassifierService(String[] args) {
//    this.classifier = SequenceClassifier.getClassifier(args);
//  }
//
//  public ServiceSession getSession() {
//    return new ServiceSession() {
//      public String process(String request) {
//        // classifier.test(doc);
//        // TODO Auto-generated method stub
//        return null;
//      }
//    };
//  }
}
