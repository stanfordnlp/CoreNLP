package edu.stanford.nlp.ie.crf;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.Triple;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

public class SelfTrainingCRFClassifier extends CRFClassifier<CoreLabel> {

  private SelfTrainingCRFClassifier() {
    super(new Properties());
  }

  public static SelfTrainingCRFClassifier getClassifierNoExceptions(File file) {
    SelfTrainingCRFClassifier crf = new SelfTrainingCRFClassifier();
    crf.loadClassifierNoExceptions(file);
    return crf;
  }

  /**
   * Prints out the correctness of labeling and confidence of
   * each word in the following format.
   *
   * correct=(0|1) p(O) p(PERSON) p(LOCATION) p(ORGANIZATION)
   *
   * @param document A {@link List} of {@link CoreLabel}s.
   */
  @Override
  public void printProbsDocument(List<CoreLabel> document) {
    //List docs = makeObjectBank(filename, flags.testMap);
    Triple<int[][][],int[], double[][][]> p = documentToDataAndLabels(document);
    int[][][] data = p.first();

    //List document = test(filename);

    CliquePotentialFunction cliquePotentialFunc = new LinearCliquePotentialFunction(weights);
    CRFCliqueTree cliqueTree = CRFCliqueTree.getCalibratedCliqueTree(data, labelIndices,
                                                                     classIndex.size(), classIndex,
								     flags.backgroundSymbol, cliquePotentialFunc, null);

    for (int i = 0; i <cliqueTree.length(); i++) {
      CoreLabel wi = document.get(i);
      //correct = wi.answer().equals(wi.get(CoreAnnotations.GoldAnswerAnnotation.class)) ? 1 : 0;
      //System.out.print(correct + "\t" + wi.word() + "\t" + wi.answer() + "\t" + wi.get(CoreAnnotations.GoldAnswerAnnotation.class) + "\t <-->");

      String highestLabel = "";
      double highestProb  = 0.0;
      String line = "";
      for (Iterator<String> iter = classIndex.iterator(); iter.hasNext();) {
        String label = iter.next();
        int index = classIndex.indexOf(label);
        double prob = cliqueTree.prob(i, index);
        if (highestProb < prob){
          highestProb = prob;
          highestLabel = label.toString();
        }

        line += label.toString() + "=" + prob;
        if (iter.hasNext()) {
          line += "\t";
        } else {
          line += "\n";
        }
      }

      // errorType has four values
      // 0 means in correct
      // 1 means entity labeled as other entity
      // 2 means entity labled as other
      // 3 means other labled as entity
      int errorType  = highestLabel.equals(wi.get(CoreAnnotations.GoldAnswerAnnotation.class)) ? 0 : 1;                   //check if error
      errorType  = highestLabel.equals("O") && errorType    != 0 ? 2 : errorType;  //check if entity labled other
      errorType  = wi.get(CoreAnnotations.GoldAnswerAnnotation.class).equals("O") && errorType != 0 ? 3 : errorType;  //check if other labled as entity
      //System.out.print(correct + "\t" + wi.word() + "\t" + highestLabel + "\t" + wi.get(CoreAnnotations.GoldAnswerAnnotation.class) + "\t <-->" + line);
      //if (!wi.get(CoreAnnotations.GoldAnswerAnnotation.class).equals("O")){
      System.out.println(highestProb + "\t" + errorType + "\t" + (wi.get(CoreAnnotations.GoldAnswerAnnotation.class).equals("O") ? 1 : 0) );
      //}
    }
  }


  /**
   * Takes a {@link List} of {@link CoreLabel}s and prints the likelihood
   * of each possible label at each point.
   *
   * @param document A {@link List} of {@link CoreLabel}s.
   */
  @Override
  public void printFirstOrderProbsDocument(List<CoreLabel> document) {

    Triple<int[][][],int[],double[][][]> p = documentToDataAndLabels(document);
    int[][][] data = p.first();

    CliquePotentialFunction cliquePotentialFunc = new LinearCliquePotentialFunction(weights);
    CRFCliqueTree cliqueTree = CRFCliqueTree.getCalibratedCliqueTree(data, labelIndices,
                                                                     classIndex.size(), classIndex,
								     flags.backgroundSymbol, cliquePotentialFunc, null);

    int gramSize = 3;


    //build up our ngram
    LinkedList<String> nGram = new LinkedList<String>();
    nGram.offer("junk");
    for (int i = 0; i < gramSize -1; i++) {
      CoreLabel wi = document.get(i);
      nGram.offer(wi.get(CoreAnnotations.GoldAnswerAnnotation.class));
    }

    // compute probablities
    int[] labels = new int[gramSize];
    for (int docIndex = gramSize-1; docIndex < cliqueTree.length(); docIndex++) {
      //update ngram
      CoreLabel wi = document.get(docIndex);
      nGram.offer(wi.get(CoreAnnotations.GoldAnswerAnnotation.class));
      nGram.remove();

      //set labels to zero
      for (int i = 0; i < labels.length; i++) labels[i] = 0;

      double highestProb   = 0.0;
      int[]  highestLabels = new int[gramSize];
      for (int i = 0; i < labels.length; i++) labels[i] = 0;

      //compute permutations
    OUTER: while (true) {
        //do stuff
        double prob = cliqueTree.prob(docIndex, labels);
        if (prob > highestProb) {
          highestProb = prob;
          for (int i = 0; i < labels.length; i++) highestLabels[i] = labels[i];
        }

        for (int i = 0; i < labels.length; i++){
          labels[i]++;
          if (labels[i] < classIndex.size()) break;
          if (i == labels.length -1)         break OUTER;
          labels[i] = 0;
        }
      }

      String gold    = "";
      String highest = "";
      String others  = "";
      for (int i = 0; i < gramSize; i++) {
        others  += "O";
        gold    += nGram.get(i);
        highest += classIndex.get(highestLabels[i]).toString();
      }
      highest = highest.replaceAll("\\s", "2");
      gold = gold.replaceAll("\\s", "2");
      //if (!gold.equals(others)) System.err.println(gold + "    " + highest);

      // errorType
      //  0 mean no error
      //  1 means error
      int errorType = gold.equals(highest) ? 0 : 1;
      System.out.println(highestProb + "\t" + errorType + "\t" + (gold.equals(others) ? 1 : 0) );
    }

  }

  public static void main(String[] args){
    if (args.length != 2){
      System.err.println("Useage: java TestCRF <classifierFile> <testFile>");
      System.exit(1);
    }

    SelfTrainingCRFClassifier crf = SelfTrainingCRFClassifier.getClassifierNoExceptions(new File(args[0]));

    //crf.printProbs(args[1]);
    //System.out.println("\n~~~\n");

    crf.printFirstOrderProbs(args[1], crf.makeReaderAndWriter());
    //System.out.println("\n~~~\n");

    //System.out.println(crf.test(args[1]));
    //PlainTextDocumentIterator.printAnswers(crf.test(args[1]));
  }
}
