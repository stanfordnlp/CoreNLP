package edu.stanford.nlp.ie;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/* Does the training and testing for the two-stage CRF 
 * approach to NER
 * 
 *  @author Vijay Krishnan
 */

public class TwoStageNER {
  private String classPath = null;
  private String options = null;

  protected void setClassPath(String classPath) {
    this.classPath = classPath;
  }

  protected void setOptions(String options) {
    this.options = options;
  }

  private String getJavaCmd()
  {
    String classPathStr = (classPath != null)? " -cp " + classPath:"";
    return "java " + ((options != null)? options:"") + classPathStr;
  }

  public void preparePropFiles(String trainFile, String basicPropFile,
      String directory) throws IOException, InterruptedException {

    for (int i = 0; i < 10; i++) {
      String propFile = directory + "/cv" + i + ".prop";
      BufferedWriter bw = new BufferedWriter(new FileWriter(propFile));

      bw.write("trainFile = " + trainFile + "_Train" + i + "\n");
      bw.write("testFile = " + trainFile + "_Test" + i + "\n");
      bw.write("serializeTo = " + directory + "/cv" + i + ".gz" + "\n");
      bw.close();
      CommandLineCall.execute("cat " + basicPropFile + " >> " + propFile);
    }
  }
  
  public void prepareRound1PropFile(String trainFile, String basicPropFile,
      String directory) throws IOException, InterruptedException {

      String propFile = directory + "/round1.train.prop";
      BufferedWriter bw = new BufferedWriter(new FileWriter(propFile));

      bw.write("trainFile = " + trainFile + "\n");
      bw.write("serializeTo = " + directory + "/round1_serialized.gz" + "\n");
      bw.close();
      CommandLineCall.execute("cat " + basicPropFile + " >> " + propFile);

  }

  public void getMajorityFiles(String resultsFile) throws IOException,
      InterruptedException {
    String cmd = "paste ";
    for (int i = 1; i <= 6; i++) {
      String majFile = resultsFile + "_maj" + i;
      CommandLineCall
          .execute(getJavaCmd() + " edu.stanford.nlp.ie.NamedEntityConfusionMatrices "
              + resultsFile + " " + i + " > " + majFile);
      cmd += majFile + " ";
    }
    cmd += " > " + resultsFile + "_majorities";
    CommandLineCall.execute(cmd);
  }
  
  public void prepareRound2TrainPropFile(String round2TrainFile,
      String basicPropFile, String directory,int colsInDataFile) throws IOException,
      InterruptedException {

    String round2PropFile = directory + "/round2.train.prop";
 
    BufferedWriter bw = new BufferedWriter(new FileWriter(round2PropFile));

    bw.write("trainFile = " + round2TrainFile + "\n");
    bw.write("serializeTo = " + directory + "/round2_serialized.gz" + "\n");
    bw.write("twoStage=true" + "\n");
    // Now run through lines and append stuff to the "map" line
    BufferedReader br = new BufferedReader(new FileReader(basicPropFile));
    String line;
    
    while ((line = br.readLine()) != null){
      if (line.startsWith("map")){
        bw.write(line);
        for (int i=1;i<=6;i++)
          bw.write(",bin" + i + "=" + (colsInDataFile -1 + i));
        bw.write("\n");
      }
      else{
        bw.write(line + "\n");
      }
    }
    bw.close();
  }

  public void train(String trainFile, String basicPropFile,
                    String directory, int colsInDataFile,
                    boolean skipRound1)
      throws IOException, InterruptedException {
    /*
     * 1. prepare the 10 train + test files
     * 2. prepare 10 prop files
     * 3. call CRFClassifer 10 times
     * 4. cat the results
     * 5. prepare new trainFile with the 6 majorities as extra cols
     * 6. Train on this to get the serialized CRF for round 2.
     * 7. Train normally on train data to get serialized CRF for round 1.
     * 
     * Finally writes the serialized file to directory/round2_serialized.gz
     * and the round1 serialized file to:
     * directory/round2_serialized.gz
     * 
     */

    String allresultsFile = directory + "/cv_all_train_data.res";
    if (!skipRound1) {
      PrepareDataforKFoldCrossValidation.prepareData(trainFile, 10);

      preparePropFiles(trainFile, basicPropFile, directory);
      String cmd = "cat ";
      for (int i = 0; i < 10; i++) {
        String propFile = directory + "/cv" + i + ".prop";
        String resFile = directory + "/cv" + i + ".res";
        CommandLineCall.execute(
                getJavaCmd() + " edu.stanford.nlp.ie.crf.CRFClassifier -prop "
                        + propFile + " > " + resFile);

        cmd += resFile + " ";
      }

      cmd += " > " + allresultsFile;
      CommandLineCall.execute(cmd);
    }

    // get the majority files
    getMajorityFiles(allresultsFile);
    String round2TrainFile = directory + "/round2.train"; 
    CommandLineCall.execute("paste " + trainFile + " " + allresultsFile + "_majorities" +
        " > " + round2TrainFile);

    // Now train on the new train data file and serialize
    String round2PropFile = directory + "/round2.train.prop";
    prepareRound2TrainPropFile(round2TrainFile,
        basicPropFile, directory, colsInDataFile);
  
    CommandLineCall.execute(
        getJavaCmd() + " edu.stanford.nlp.ie.crf.CRFClassifier -prop "
        + round2PropFile);
    
    String round1PropFile = directory + "/round1.train.prop";
    prepareRound1PropFile(trainFile,basicPropFile, directory);
    CommandLineCall.execute(
        getJavaCmd() + " edu.stanford.nlp.ie.crf.CRFClassifier -prop "
        + round1PropFile);
  }

  public void prepareRound1TestPropFile(String testFile, String basicPropFile,
      String directory,String round1SerializedFile) throws IOException, InterruptedException {

      String propFile = directory + "/round1.test.prop";
      BufferedWriter bw = new BufferedWriter(new FileWriter(propFile));

      bw.write("testFile = " + testFile + "\n");
      bw.write("loadClassifier = " + round1SerializedFile + "\n");
      bw.close();
      CommandLineCall.execute("cat " + basicPropFile + " >> " + propFile);
  }
  
  public void prepareRound2TestPropFile(String round2TestFile, String basicPropFile,
      String directory,int colsInDataFile, String round2SerializedFile)
  throws IOException, InterruptedException {
    String round2PropFile = directory + "/round2.test.prop";
 
    BufferedWriter bw = new BufferedWriter(new FileWriter(round2PropFile));

    bw.write("testFile = " + round2TestFile + "\n");
    bw.write("loadClassifier = " + round2SerializedFile + "\n");
    bw.write("twoStage=true" + "\n");
    // Now run through lines and append stuff to the "map" line
    BufferedReader br = new BufferedReader(new FileReader(basicPropFile));
    String line;
    
    while ((line = br.readLine()) != null){
      if (line.startsWith("map")){
        bw.write(line);
        for (int i=1;i<=6;i++)
          bw.write(",bin" + i + "=" + (colsInDataFile -1 + i));
        bw.write("\n");
      }
      else{
        bw.write(line + "\n");
      }
    }
    bw.close();
  }
  
  public void test(String round1SerializedFile, String round2SerializedFile,
      String testFile, String basicPropFile, String directory,int colsInDataFile,
      String finalResultsFile) throws IOException, InterruptedException {

    /* 1. Prepare prop file for round1 test
     * 2. Test using round1 serialized CRF. 
     * 3. Get Majorities
     * 4. Append to original data file
     * 5. Prop file for round2 test.
     * 6. Test with 2nd serialized CRF for final results.
     * 
     * Check if 1000m is fine or do we want to pass it as arg?
     */

    prepareRound1TestPropFile(testFile,basicPropFile,directory,round1SerializedFile);
    String propFile = directory + "/round1.test.prop";
    String allresultsFile = directory + "/round1.test.res";
      CommandLineCall.execute(
          getJavaCmd() + " edu.stanford.nlp.ie.crf.CRFClassifier -prop "
              + propFile + " > " + allresultsFile);
    
    // get the majority files
    getMajorityFiles(allresultsFile);
    String round2TestFile = directory + "/round2.test"; 
    CommandLineCall.execute("paste " + testFile + " " + allresultsFile + "_majorities" +
        " > " + round2TestFile);

    // Now train on the new train data file and serialize
    String round2PropFile = directory + "/round2.test.prop";
    prepareRound2TestPropFile(round2TestFile,basicPropFile,directory,
        colsInDataFile,round2SerializedFile);
        
    CommandLineCall.execute(
        getJavaCmd() + " edu.stanford.nlp.ie.crf.CRFClassifier -prop "
        + round2PropFile + " > " + finalResultsFile);
  }

  public static void usage()
  {
    System.err.println("java edu.stanford.nlp.ie.TwoStageNER [train|test] ...");
    System.err.println(" train <trainFile> <basicPropFile> <tmpDirectory> <colsInDataFile>");
    System.err.println(" test <round1SerializedFile> <round2SerializedFile> <testFile> "
            + " <basicPropFile> <tmpDirectory> <colsInDataFile> <finalResultsFile>");
    System.err.println("   round1SerializedFile - Serialized classifier from first round of training");
    System.err.println("   round2SerializedFile - Serialized classifier from second round of training");
    System.err.println("   colsInDataFile - Number of columns in data file");
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    // TODO Auto-generated method stub
    TwoStageNER ner = new TwoStageNER();
    ner.setClassPath(System.getProperties().getProperty("java.class.path"));
    ner.setOptions("-mx16g");

    if (args.length > 0 && args[0].equalsIgnoreCase("train")){
      if (args.length != 5 && args.length != 6) {
        System.err.println("Wrong no. of arguments");
        usage();
      }
      else{
        String trainFile =args[1];
        String basicPropFile = args[2];
        String directory = args[3];
        int colsInDataFile = Integer.parseInt(args[4]);
        boolean skipRound1 = false;
        if (args.length == 6) {
          skipRound1 = Boolean.parseBoolean(args[5]);
        }
        
        ner.train(trainFile,basicPropFile,directory,colsInDataFile, skipRound1);
      }
    } else if (args.length > 0 && args[0].equalsIgnoreCase("test")){
      if (args.length != 8) {
        System.err.println("Wrong no. of arguments");
        usage();
      }
      else{
        String round1SerializedFile = args[1];
        String round2SerializedFile = args[2];
        String testFile = args[3];
        String basicPropFile = args[4];
        String directory = args[5];
        int colsInDataFile = Integer.parseInt(args[6]);
        String finalResultsFile = args[7];
        
        ner.test(round1SerializedFile, round2SerializedFile, testFile, 
            basicPropFile, directory, colsInDataFile, finalResultsFile);
      }
    } else {
      usage();
    }
  }
}
