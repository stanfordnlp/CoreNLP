
import edu.stanford.nlp.ie.crf.*;
import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.util.StringUtils;

import java.util.List;
import java.io.IOException;

/** This is a demo of calling CRFClassifier programmatically.
 *  <p>
 *  Usage: <code> java -cp "stanford-ner.jar:." NERDemo [serializedClassifier [fileName]]</code>
 *  <p>
 *  If arguments aren't specified, they default to
 * ner-eng-ie.crf-3-all2006.ser.gz and some hardcoded sample text.
 *  <p>
 *  To use CRFClassifier from the command line:
 *  java edu.stanford.nlp.ie.crf.CRFClassifier -loadClassifier
 *      [classifier] -textFile [file]
 *  Or if the file is already tokenized and one word per line, perhaps in
 *  a tab-separated value format with extra columns for part-of-speech tag,
 *  etc., use the version below (note the 's' instead of the 'x'): 
 *  java edu.stanford.nlp.ie.crf.CRFClassifier -loadClassifier
 *      [classifier] -testFile [file]
 *
 *  @author Jenny Finkel
 *  @author Christopher Manning
 */

public class NERDemo {

    public static void main(String[] args) throws IOException {

      String serializedClassifier = "classifiers/ner-eng-ie.crf-3-all2008.ser.gz";      

      if (args.length > 0) {
        serializedClassifier = args[0];
      }
      
      AbstractSequenceClassifier classifier = CRFClassifier.getClassifierNoExceptions(serializedClassifier);

      if (args.length > 1) {
        String fileContents = StringUtils.slurpFile(args[1]);
        List<List<CoreLabel>> out = classifier.testSentences(fileContents);
        for (List<CoreLabel> sentence : out) {
          for (CoreLabel word : sentence) {
	      System.out.print(word.word() + '/' + word.get(AnswerAnnotation.class) + ' ');
          }
          System.out.println();
        }

      } else {
        String s1 = "Good afternoon Rajat Raina, how are you today?";
        String s2 = "I go to school at Stanford University, which is located in California.";
        System.out.println(classifier.testString(s1));
        System.out.println(classifier.testStringInlineXML(s2));
      }
    }
  
}
