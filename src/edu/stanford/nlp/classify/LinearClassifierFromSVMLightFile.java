/**
 * Trains and evaluates a linear classifier using and input files in SVMLight format
 * This is meant to offer a quick way to evaluate a LR classifier on data originally designed for SVMLight
 * It supports both sigma tuning and feature count thresholding (i.e., removing features with a count lower than the threshold)
 * Typical command line:
 * java LinearClassifierFromSVMLightFile -train <train file in SVMLight format> \
 *    -test <test file in SVMLight format> \ 
 *    -sigma <sigma, default 1> \
 *    -threshold <feature count threshold, default 0> \
 *    -model <save model in this file> \
 *    -output <save output labels in this file> 
 *    
 * @author Mihai
 */
package edu.stanford.nlp.classify;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

public class LinearClassifierFromSVMLightFile {
  
  private static void usage() {
    System.err.println("java LinearClassifierFromSVMLightFile -train <train file in SVMLight format> \\\n" +
     "\t-test <test file in SVMLight format> \\\n" +
     "\t-sigma <sigma, default 1> \\\n" +
     "\t-threshold <feature count threshold, default 0> \\\n" +
     "\t-model <save model in this file> \\\n" +
     "\t-output <save output labels in this file>");
  }
  
  public static void save(String modelpath, LinearClassifier<String, String> classifier) throws IOException {
    // make sure the modelpath directory exists
    int lastSlash = modelpath.lastIndexOf(File.separator);
    if(lastSlash > 0){
      String path = modelpath.substring(0, lastSlash);
      File f = new File(path);
      if (! f.exists()) {
        f.mkdirs();
      }
    }
    
    FileOutputStream fos = new FileOutputStream(modelpath);
    ObjectOutputStream out = new ObjectOutputStream(fos);
    out.writeObject(classifier);    
    out.close(); 
  }
  
  private static Pair<String, Double> classOf(Datum<String, String> datum, LinearClassifier<String, String> classifier) {
    Counter<String> probs = classifier.probabilityOf(datum); 
    List<Pair<String, Double>> sortedProbs = Counters.toDescendingMagnitudeSortedListWithCounts(probs);
    return sortedProbs.get(0);
  }
  
  private static GeneralDataset<String, String> loadDataset(String trainFile, int featureCountThreshold) throws IOException {
    GeneralDataset<String, String> trainSet = new Dataset<String, String>();
    
    BufferedReader is = new BufferedReader(new FileReader(trainFile));
    int count = 0;
    for(String line; (line = is.readLine()) != null; ) {
      Datum<String, String> datum = lineToDatum(line);
      trainSet.add(datum);
      count ++;
    }
    System.err.println("Loaded " + count + " datums from " + trainFile);
    
    if(featureCountThreshold > 0) 
      trainSet.applyFeatureCountThreshold(featureCountThreshold);
    
    return trainSet;
  }
  
  private static List<Datum<String, String>> loadDatums(String testFile) throws IOException {
    List<Datum<String, String>> datums = new ArrayList<Datum<String,String>>();
    BufferedReader is = new BufferedReader(new FileReader(testFile));
    int count = 0;
    for(String line; (line = is.readLine()) != null; ) {
      Datum<String, String> datum = lineToDatum(line);
      datums.add(datum);
      count ++;
    }
    System.err.println("Loaded " + count + " datums from " + testFile);
    return datums;
  }
  
  private static Datum<String, String> lineToDatum(String line) {
    String [] bits = line.split("\\s+");
    String label = bits[0];
    Collection<String> features = new ArrayList<String>();
    for(int i = 1; i < bits.length; i ++){
      String [] nameAndWeight = bits[i].split(":");
      features.add(nameAndWeight[0]);
    }
    return new BasicDatum<String, String>(features, label);
  }
  
  public static void main(String[] args) throws Exception {
    Properties props = StringUtils.argsToProperties(args);
    System.err.println("Using properties: " + props);
    String trainFile = props.getProperty("train");
    if(trainFile == null) {
      usage();
      System.exit(1);
    }
    String testFile = props.getProperty("test");
    if(testFile == null){
      usage();
      System.exit(1);
    }
    double sigma = Double.valueOf(props.getProperty("sigma", "1.0"));
    int featureCountThreshold = Integer.valueOf(props.getProperty("threshold", "0"));
    String modelFile = props.getProperty("model");
    String outputFile = props.getProperty("output");
    
    // load the training dataset
    GeneralDataset<String, String> trainSet = loadDataset(trainFile, featureCountThreshold);
    
    // train the classifier
    LinearClassifierFactory<String, String> factory = new LinearClassifierFactory<String, String>(1e-4, false, sigma);
    LinearClassifier<String, String> classifier = factory.trainClassifier(trainSet);
    
    // save the model if model file given
    if(modelFile != null) save(modelFile, classifier);
    
    // load the testing set
    List<Datum<String, String>> datums = loadDatums(testFile);
    
    // classify the testing set
    PrintStream os = null;
    if(outputFile != null) os = new PrintStream(new FileOutputStream(outputFile));
    int correct = 0, total = 0;
    for(Datum<String, String> datum: datums) {
      Pair<String, Double> output = classOf(datum, classifier);
      
      total ++;
      if(output.first().equals(datum.label())){
        correct ++;
      }
      
      if(os != null){
        os.println(output.first() + "\t" + output.second());
      }
    }
    if(os != null) os.close();
    
    // score
    double acc = (double) correct / (double) total;
    System.err.println("Overall accuracy for sigma " + sigma + " and featureCountThreshold " + featureCountThreshold + ": " + acc);
  }
}
