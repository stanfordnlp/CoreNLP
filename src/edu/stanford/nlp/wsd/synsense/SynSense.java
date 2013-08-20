package edu.stanford.nlp.wsd.synsense;

import edu.stanford.nlp.classify.ClassifiedDatum;
import edu.stanford.nlp.classify.ClassifierTester;
import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Timing;

import java.io.*;
import java.util.*;

/**
 * @author Galen Andrew (galand@cs.stanford.edu)
 */
public class SynSense {

  // datasets
  static List<String> words;
  protected static List[] senseTrainData = null;
  protected static List[] senseTestData = null;
  //  protected static List[] senseHeldoutData = null;
  protected static List[] subcatTrainData = null;
  protected static List[] subcatTestData = null;
  //  protected static List[] subcatHeldoutData = null;
  
  protected static int numFolds = 10;
  protected static boolean verbose = false;

  // for tabulating results
  static int totalSenseCorrect = 0;
  static int totalSense = 0;
  static int totalSubcatCorrect = 0;
  static int totalSubcat = 0;

  static Map<String, String> senseMap; // a Map from senses to their supersenses (which may be themselves)
  static ClassicCounter<String> subsenseCounter;
  // 0 is fine, 1 is mixed, 2 is coarse
  static int senseGranularity = 0;

  static PrintStream out = null;
  static Set<InstanceMarking> basicWrongAndJointRight;
  static Set<InstanceMarking> basicRightAndJointWrong;

  private static final double TRAIN_FRACTION = 0.75;
  //  private static final double HELDOUT_FRACTION = 0.1;

  private SynSense() {}

  protected static void printUsageAndExit() {
    System.out.println("usage: java edu.stanford.nlp.wsd.synsense.SynSense -model [basic|joint|both] " + "[-kfold] [-all]  [-word word] [-infile file] [-course]");
    System.exit(1);
  }

  public static void main(String[] args) throws Exception {
    Map argMap = StringUtils.parseCommandLineArguments(args);
    String modelString = (String) argMap.get("-model");
    if (argMap.containsKey("-coarse")) {
      senseGranularity = Integer.parseInt((String) argMap.get("-coarse"));
      readSenseMap("sensemap");
    }

    readInData(argMap);
    shuffleDatasets();
    if (argMap.containsKey("-output")) {
      out = new PrintStream(new FileOutputStream((String) argMap.get("-output")));
      out.print("word,");
      out.print("BASkfSense,BASkfSubcat,BAStSense,BAStSubcat,");
      out.print("JOINTkfSense,JOINTkfSubcat,JOINTtSense,JOINTtSubcat,");
      out.println("numSenseTrain,numSubcatTrain,numSenseTest,numSubcatTest");
      trainAndTestAllWords();
    } else {
      if (modelString.equalsIgnoreCase("basic")) {
        // do basic
        System.out.println("training basic model");
        trainAndTestAllWords(argMap.containsKey("-kfold"), new JointModel());
      } else if (modelString.equalsIgnoreCase("joint")) {
        // do joint
        System.out.println("training joint model");
        trainAndTestAllWords(argMap.containsKey("-kfold"), new JointModel());
      } else if (modelString.equalsIgnoreCase("both")) {
        // do both
        // first initialize sets
        basicWrongAndJointRight = new HashSet<InstanceMarking>();
        basicRightAndJointWrong = new HashSet<InstanceMarking>();

        System.out.println("training basic model");
        trainAndTestAllWords(argMap.containsKey("-kfold"), new BasicModel());
        System.out.println("training joint model");
        trainAndTestAllWords(argMap.containsKey("-kfold"), new JointModel());

        // print out sets
        System.out.println("jointRightButBasicWrong:");
        for (Iterator<InstanceMarking> iter = basicWrongAndJointRight.iterator(); iter.hasNext();) {
          InstanceMarking instanceMarking = iter.next();
          System.out.println(instanceMarking);
        }
        System.out.println("basicRightButJointWrong:");
        for (Iterator<InstanceMarking> iter = basicRightAndJointWrong.iterator(); iter.hasNext();) {
          InstanceMarking instanceMarking = iter.next();
          System.out.println(instanceMarking);
        }
      } else {
        printUsageAndExit();
      }
    }
    if (out != null) {
      out.close();
    }
  }

  protected static void randomTrainSetSplitTrainAndTestAllWords(Model model) {
    totalSenseCorrect = 0;
    totalSense = 0;
    totalSubcatCorrect = 0;
    totalSubcat = 0;
    for (int i = 0; i < words.size(); i++) {
      randomTrainSetSplitTrainAndTestOneWord(model, i);
    }
  }

  protected static void trainAndTestAllWords(boolean kFold, Model model) {
    totalSenseCorrect = 0;
    totalSense = 0;
    totalSubcatCorrect = 0;
    totalSubcat = 0;
    for (int i = 0; i < words.size(); i++) {
      if (kFold) {
        kFoldTrainAndTestOneWord(model, i);
      } else {
        trainAndTestOneWord(model, i);
      }
    }
  }

  protected static void trainAndTestAllWords() {
    for (int i = 0; i < words.size(); i++) {
      String word = words.get(i);
      out.print(word + ",");
      int[] result;
      System.out.println("kFold basic for " + word);
      result = kFoldTrainAndTestOneWord(new BasicModel(), i);
      out.print(result[0] + "," + result[1] + ",");
      System.out.println("testset basic for " + word);
      result = trainAndTestOneWord(new BasicModel(), i);
      out.print(result[0] + "," + result[1] + ",");
      System.out.println("kfold joint for " + word);
      result = kFoldTrainAndTestOneWord(new JointModel(), i);
      out.print(result[0] + "," + result[1] + ",");
      System.out.println("testset joint for " + word);
      result = trainAndTestOneWord(new JointModel(), i);
      out.print(result[0] + "," + result[1] + ",");
      out.println(senseTrainData[i].size() + "," + subcatTrainData[i].size() + "," + senseTestData[i].size() + "," + subcatTestData[i].size());
    }
  }

  protected static int[] trainAndTestOneWord(Model model, int i) {
    String word = words.get(i);
    List<Instance> trainInstances = new ArrayList<Instance>();
    trainInstances.addAll(subcatTrainData[i]);
    trainInstances.addAll(senseTrainData[i]);
    List<Instance> senseTestInstances = senseTestData[i];
    List<Instance> subcatTestInstances = subcatTestData[i];
    //    System.out.println("Training model for word " + word);
    model.train(trainInstances);

    //    System.out.println("Testing sense instances for word " + word);
    List<InstanceMarking> senseGuesses = model.test(senseTestInstances);
    //TODO! write sense evaluation scheme using Senseval scorer
    int numSenseCorrect = evaluateSense(senseGuesses, model, false);
    int numSense = senseTestInstances.size();

    //    System.out.println("Testing subcat instances for word " + word);
    List<InstanceMarking> subcatGuesses = model.test(subcatTestInstances);
    int numSubcatCorrect = evaluateSubcat(subcatGuesses, false);
    int numSubcat = subcatTestInstances.size();

    //    System.out.println("Totals for word " + word);
    double senseAcc = ((double) numSenseCorrect / (double) numSense);
    System.out.println("Sense: " + numSenseCorrect + " correct out of " + numSense + " = " + senseAcc);
    double subcatAcc = ((double) numSubcatCorrect / (double) numSubcat);
    System.out.println("Subcat: " + numSubcatCorrect + " correct out of " + numSubcat + " = " + subcatAcc);

    totalSenseCorrect += numSenseCorrect;
    totalSense += numSense;
    totalSenseCorrect += numSenseCorrect;
    totalSense += numSense;

    return new int[]{numSenseCorrect, numSubcatCorrect};
  }

  protected static void shuffleDatasets() {
    for (int i = 0; i < words.size(); i++) {
      Random random = new Random(0L);
      //      if (senseTrainData[i].size() > 0)
      Collections.shuffle(senseTrainData[i], random);
      //     if (subcatTrainData[i].size() > 0)
      Collections.shuffle(subcatTrainData[i], random);
    }
  }

  protected static int[] randomTrainSetSplitTrainAndTestOneWord(Model model, int i) {
    String word = words.get(i);
    if (verbose) {
      System.out.println("Testing on word " + word);
    }
    int numPerWordSenseCorrect = 0;
    int numPerWordSense = 0;
    int numPerWordSubcatCorrect = 0;
    int numPerWordSubcat = 0;
    List<Instance> trainInstances = new ArrayList<Instance>();
    List<Instance> senseTestInstances = new ArrayList<Instance>();
    List<Instance> subcatTestInstances = new ArrayList<Instance>();
    shuffleDatasets();
    splitData(i, trainInstances, senseTestInstances, subcatTestInstances);
    if (verbose) {
      System.out.println("Training model.");
    }
    model.train(trainInstances);
    List<InstanceMarking> senseGuesses = model.test(senseTestInstances);
    int numSenseCorrect = evaluateSense(senseGuesses, model, false);
    int numSense = senseTestInstances.size();
    numPerWordSenseCorrect += numSenseCorrect;
    numPerWordSense += numSense;
    if (verbose) {
      System.out.println("Sense: " + numSenseCorrect + " correct out of " + numSense + " = " + ((double) numSenseCorrect / (double) numSense));
    }
    List<InstanceMarking> subcatGuesses = model.test(subcatTestInstances);
    int numSubcatCorrect = evaluateSubcat(subcatGuesses, false);
    int numSubcat = subcatTestInstances.size();
    numPerWordSubcatCorrect += numSubcatCorrect;
    numPerWordSubcat += numSubcat;
    if (verbose) {
      System.out.println("Subcat: " + numSubcatCorrect + " correct out of " + numSubcat + " = " + ((double) numSubcatCorrect / (double) numSubcat));
    }
    totalSenseCorrect += numPerWordSenseCorrect;
    totalSense += numPerWordSense;
    totalSubcatCorrect += numPerWordSubcatCorrect;
    totalSubcat += numPerWordSubcat;
    //    double senseAccuracy = (double) numPerWordSenseCorrect / (double) numPerWordSense;
    //    double subcatAccuracy = (double) numPerWordSubcatCorrect / (double) numPerWordSubcat;
    return new int[]{numPerWordSenseCorrect, numPerWordSubcatCorrect};
  }


  // returns double[2] with senseAccuracy and subcatAccuracy
  protected static int[] kFoldTrainAndTestOneWord(Model model, int i) {
    String word = words.get(i);
    if (verbose) {
      System.out.println("Testing on word " + word);
    }
    int numPerWordSenseCorrect = 0;
    int numPerWordSense = 0;
    int numPerWordSubcatCorrect = 0;
    int numPerWordSubcat = 0;
    for (int fold = 0; fold < numFolds; fold++) { // TODO make sure we do up to numFolds
      if (verbose) {
        System.out.println("Fold " + fold);
      }
      List<Instance> trainInstances = new ArrayList<Instance>();
      List<Instance> senseTestInstances = new ArrayList<Instance>();
      List<Instance> subcatTestInstances = new ArrayList<Instance>();
      splitData(i, fold, trainInstances, senseTestInstances, subcatTestInstances);
      if (verbose) {
        System.out.println("Training model.");
      }
      model.train(trainInstances);
      List<InstanceMarking> senseGuesses = model.test(senseTestInstances);
      int numSenseCorrect = evaluateSense(senseGuesses, model, false);
      int numSense = senseTestInstances.size();
      numPerWordSenseCorrect += numSenseCorrect;
      numPerWordSense += numSense;
      if (verbose) {
        System.out.println("Sense: " + numSenseCorrect + " correct out of " + numSense + " = " + ((double) numSenseCorrect / (double) numSense));
      }
      List<InstanceMarking> subcatGuesses = model.test(subcatTestInstances);
      int numSubcatCorrect = evaluateSubcat(subcatGuesses, false);
      int numSubcat = subcatTestInstances.size();
      numPerWordSubcatCorrect += numSubcatCorrect;
      numPerWordSubcat += numSubcat;
      if (verbose) {
        System.out.println("Subcat: " + numSubcatCorrect + " correct out of " + numSubcat + " = " + ((double) numSubcatCorrect / (double) numSubcat));
      }
    }
    //    if (verbose) {
    //      System.out.println("Totals for all folds for word " + word);
    System.out.println("Sense: " + numPerWordSenseCorrect + " correct out of " + numPerWordSense + " = " + ((double) numPerWordSenseCorrect / (double) numPerWordSense));
    System.out.println("Subcat: " + numPerWordSubcatCorrect + " correct out of " + numPerWordSubcat + " = " + ((double) numPerWordSubcatCorrect / (double) numPerWordSubcat));
    //    }
    totalSenseCorrect += numPerWordSenseCorrect;
    totalSense += numPerWordSense;
    totalSubcatCorrect += numPerWordSubcatCorrect;
    totalSubcat += numPerWordSubcat;
    return new int[]{numPerWordSenseCorrect, numPerWordSubcatCorrect};
  }

  protected static void splitData(int i, List<Instance> trainInstances, List<Instance> senseTestInstances, List<Instance> subcatTestInstances) {
    int split = (int) (senseTrainData[i].size() * TRAIN_FRACTION);
    trainInstances.addAll(senseTrainData[i].subList(0, split));
    senseTestInstances.addAll(senseTrainData[i].subList(split + 1, senseTrainData[i].size()));
    split = (int) (subcatTrainData[i].size() * TRAIN_FRACTION);
    trainInstances.addAll(subcatTrainData[i].subList(0, split));
    subcatTestInstances.addAll(subcatTrainData[i].subList(split + 1, subcatTrainData[i].size()));
  }

  protected static void splitData(int i, int fold, List<Instance> trainInstances, List<Instance> senseTestInstances, List<Instance> subcatTestInstances) {
    // split sense data
    int start = (int) ((double) senseTrainData[i].size() * (double) fold / (double) numFolds);
    int end = (int) ((double) senseTrainData[i].size() * (double) (fold + 1) / (double) numFolds);
    trainInstances.addAll(senseTrainData[i].subList(0, start));
    trainInstances.addAll(senseTrainData[i].subList(end, senseTrainData[i].size()));
    senseTestInstances.addAll(senseTrainData[i].subList(start, end));
    // split subcat data
    start = (int) ((double) subcatTrainData[i].size() * (double) fold / (double) numFolds);
    end = (int) ((double) subcatTrainData[i].size() * (double) (fold + 1) / (double) numFolds);
    trainInstances.addAll(subcatTrainData[i].subList(0, start));
    trainInstances.addAll(subcatTrainData[i].subList(end, subcatTrainData[i].size()));
    subcatTestInstances.addAll(subcatTrainData[i].subList(start, end));
  }

  protected static void readInData(Map argMap) {
    System.out.println("Reading in data.");

    if (argMap.containsKey("-infile")) {
      readAllWordsFromOneSerializedFile((String) argMap.get("-infile"));
      return;
    }

    words = new ArrayList<String>();
    if (argMap.containsKey("-word")) {
      // get parsed instances from file
      words.add(argMap.get("-word").toString());
    } else {
      // get all ser files from the current directory, that's how we'll get the word list
      File currentDir = new File(".");
      String[] filenames = currentDir.list();
      System.out.println("loading data for words: ");
      for (int i = 0; i < filenames.length; i++) {
        String filename = filenames[i];
        if (filename.endsWith(".ser")) {
          String word = filename.substring(0, filename.length() - 4);
          words.add(word);
          System.out.print(word + " ");
        }
      }
      System.out.println();
    }

    senseTrainData = new ArrayList[words.size()];
    senseTestData = new ArrayList[words.size()];
    subcatTrainData = new ArrayList[words.size()];
    subcatTestData = new ArrayList[words.size()];
    for (int i = 0; i < words.size(); i++) {
      String word = words.get(i);
      readInstancesOfOneWordFromSerializedFile(word, i);
    }
  }

  protected static void printInstances(List instances) {
    for (Iterator iter = instances.iterator(); iter.hasNext();) {
      Instance instance = (Instance) iter.next();
      System.out.println(instance);
    }
  }

  protected static void readAllWordsFromOneSerializedFile(String infile) {
    Timing.startTime();
    System.out.println("Reading data instances from serialized file " + infile + "...");
    FileInputStream fis = null;
    try {
      fis = new FileInputStream(infile);
      ObjectInputStream ois = new ObjectInputStream(fis);
      words = (List<String>) ois.readObject();
      senseTrainData = (List[]) ois.readObject();
      senseTestData = (List[]) ois.readObject();
      subcatTrainData = (List[]) ois.readObject();
      ois.close();
      Timing.tick("done.");
      System.out.println("got words: " + words);
    } catch (FileNotFoundException e) {
      e.printStackTrace();  //To change body of catch statement use Options | File Templates.
    } catch (IOException e) {
      e.printStackTrace();  //To change body of catch statement use Options | File Templates.
    } catch (ClassNotFoundException e) {
      e.printStackTrace();  //To change body of catch statement use Options | File Templates.
    }
  }

  protected static void readInstancesOfOneWordFromSerializedFile(String word, int i) {
    Timing.startTime();
    System.out.println("Reading data instances from serialized file " + word + ".ser...");
    FileInputStream fis = null;
    try {
      fis = new FileInputStream(word + ".ser");
      ObjectInputStream ois = new ObjectInputStream(fis);
      senseTrainData[i] = (List) ois.readObject();
      senseTestData[i] = (List) ois.readObject();
      subcatTrainData[i] = (List) ois.readObject();
      subcatTestData[i] = (List) ois.readObject();
      ois.close();
      System.out.println("Found " + senseTrainData[i].size() + " senseTrain.");
      System.out.println("Found " + senseTestData[i].size() + " senseTest.");
      System.out.println("Found " + subcatTrainData[i].size() + " subcatTrain.");
      System.out.println("Found " + subcatTestData[i].size() + " subcatTest.");
      Timing.tick("done.");
    } catch (FileNotFoundException e) {
      e.printStackTrace();  //To change body of catch statement use Options | File Templates.
    } catch (IOException e) {
      e.printStackTrace();  //To change body of catch statement use Options | File Templates.
    } catch (ClassNotFoundException e) {
      e.printStackTrace();  //To change body of catch statement use Options | File Templates.
    }


    // remove subcat instances that didn't parse

    int illegal = 0;
    int unparsed = 0;
    for (int j = 0; j < subcatTrainData[i].size(); j++) {
      if (((Instance) subcatTrainData[i].get(j)).logSequenceGivenSubcat.isEmpty()) {
        unparsed++;
      }
      if (((Instance) subcatTrainData[i].get(j)).subcat == Subcategory.ILLEGAL) {
        ((Instance) subcatTrainData[i].get(j)).subcat = Subcategory.OTHER;
        illegal++;
      }
    }

    System.out.println("illegal " + illegal + " unparsed: " + unparsed);

    illegal = 0;
    unparsed = 0;
    for (int j = 0; j < subcatTestData[i].size(); j++) {
      if (((Instance) subcatTestData[i].get(j)).logSequenceGivenSubcat.isEmpty()) {
        unparsed++;
      }
      if (((Instance) subcatTestData[i].get(j)).subcat == Subcategory.ILLEGAL) {
        ((Instance) subcatTestData[i].get(j)).subcat = Subcategory.OTHER;
        illegal++;
      }
    }
    System.out.println("illegal " + illegal + " unparsed: " + unparsed);

    // replace senses with coarse grained ones
    if (senseGranularity == 2) {
      System.out.println("Loading coarse-grained senses.");
      for (int j = 0; j < senseTrainData[i].size(); j++) {
        Instance ins = (Instance) senseTrainData[i].get(j);
        for (int k = 0; k < ins.sense.length; k++) {
          if (senseMap.containsKey(ins.sense[k])) {
            ins.sense[k] = senseMap.get(ins.sense[k]);
          } else {
            senseMap.put(ins.sense[k], ins.sense[k]);
          }
        }
      }
      for (int j = 0; j < senseTestData[i].size(); j++) {
        Instance ins = (Instance) senseTestData[i].get(j);
        for (int k = 0; k < ins.sense.length; k++) {
          if (senseMap.containsKey(ins.sense[k])) {
            ins.sense[k] = senseMap.get(ins.sense[k]);
          } else {
            senseMap.put(ins.sense[k], ins.sense[k]);
          }
        }
      }

    }
  }

  public static int evaluateSense(List<InstanceMarking> guesses, Model model, boolean verbose) {
    //    List markedSenseData = new ArrayList();
    int numCorrect = 0;
    for (Iterator<InstanceMarking> iter = guesses.iterator(); iter.hasNext();) {
      InstanceMarking m = iter.next();
      Instance ins = m.getInstance();
      // mightn't it be DISTRIBUTION?
      if (ins.sense[0] != Instance.UNASSIGNED) {
        String guess = m.getSense();
        if (senseGranularity == 2) {
          guess = senseMap.get(guess);
        }
        boolean correct = false;
        for (int i = 0; i < ins.sense.length; i++) {
          String answer = ins.sense[i];
          if (senseGranularity == 2) {
            answer = senseMap.get(answer);
          }
          if (guess.equals(answer)) {
            correct = true;
            numCorrect++;
            break;
          }
        }
        if (basicRightAndJointWrong != null && basicWrongAndJointRight != null) {
          if (model instanceof JointModel) {
            if (correct) {
              // correct joint
              basicRightAndJointWrong.remove(m);
            } else {
              // incorrect joint
              basicWrongAndJointRight.remove(m);
            }
          } else {
            if (correct) {
              // correct basic
              basicRightAndJointWrong.add(m);
            } else {
              // incorrect basic
              basicWrongAndJointRight.add(m);
            }
          }
        }

        if (verbose) {
          System.out.println("correct sense(s): " + ins.sense + ":" + guess + (correct ? "  CORRECT" : "  INCORRECT"));
          String just = m.getSenseJustification();
          if (just != null) {
            System.out.println(just);
          }
        }
        //        markedSenseData.add(new ClassifiedDatum(new BasicDatum(), new Integer(guess), ins.sense));
      } else {
        throw new RuntimeException("Error: unmarked instance.");
      }
    }
    // this doesn't work because of multiple correct answers
    //    if (verbose) {
    //      if (!markedSenseData.isEmpty()) {
    //        System.out.println("Sense Confusion Matrix");
    //        ClassifierTester.printConfusionMatrix((ClassifiedDatum[]) markedSenseData.toArray(new ClassifiedDatum[0]), new PrintWriter(System.out, true), 8);
    //      }
    //    }
    return numCorrect;
  }

  public static int evaluateSubcat(List<InstanceMarking> guesses, boolean verbose) {
    List<ClassifiedDatum> markedSubcatData = new ArrayList<ClassifiedDatum>();
    int numCorrect = 0;
    for (Iterator<InstanceMarking> iter = guesses.iterator(); iter.hasNext();) {
      InstanceMarking m = iter.next();
      Instance ins = m.getInstance();
      if (ins.subcat != Subcategory.UNASSIGNED) {
        Subcategory guess = m.getSubcat();
        if (verbose) {
          System.out.println("subcat: " + ins.subcat + ":" + guess + (ins.subcat == guess ? "  CORRECT" : "  INCORRECT"));
          String just = m.getSubcatJustification();
          if (just != null) {
            System.out.println(just);
          }
        }
        if (ins.subcat == guess) {
          numCorrect++;
        }
        markedSubcatData.add(new ClassifiedDatum(new BasicDatum(), guess, ins.subcat));
      } else {
        throw new RuntimeException("Error: unmarked instance.");
      }
    }
    if (verbose) {
      if (!markedSubcatData.isEmpty()) {
        System.out.println("Subcat Confusion Matrix");
        ClassifierTester.printConfusionMatrix(markedSubcatData.toArray(new ClassifiedDatum[0]), new PrintWriter(System.out, true), 8);
      }
    }
    return numCorrect;
  }

  public static void readSenseMap(String filename) {
    senseMap = new HashMap<String, String>();
    subsenseCounter = new ClassicCounter<String>();
    BufferedReader senseMapFile;
    try {
      senseMapFile = new BufferedReader(new FileReader(filename));
      String line;
      while ((line = senseMapFile.readLine()) != null) {
        StringTokenizer strtok = new StringTokenizer(line);
        String subsense = strtok.nextToken();
        if (strtok.hasMoreTokens()) {
          int numSubsenses = (Integer.parseInt(strtok.nextToken()));
          String mainSense = strtok.nextToken();
          subsenseCounter.setCount(mainSense, numSubsenses);
          senseMap.put(subsense, mainSense);
          senseMap.put(mainSense, mainSense);
        } else {
          subsenseCounter.setCount(subsense, 1.0);
          senseMap.put(subsense, subsense);
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}

class InstanceMarking {
  protected String sense;
  protected Subcategory subcat;
  protected String senseJustification;
  protected String subcatJustification;
  protected Instance instance;

  public InstanceMarking(Instance instance, String sense, Subcategory subcat, String senseJustification, String subcatJustification) {
    this.instance = instance;
    this.sense = sense;
    this.subcat = subcat;
    this.senseJustification = senseJustification;
    this.subcatJustification = subcatJustification;
  }

  public String getSense() {
    return sense;
  }

  public Subcategory getSubcat() {
    return subcat;
  }

  public String getSenseJustification() {
    return senseJustification;
  }

  public String getSubcatJustification() {
    return subcatJustification;
  }

  public Instance getInstance() {
    return instance;
  }

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof InstanceMarking)) {
      return false;
    }

    final InstanceMarking instanceMarking = (InstanceMarking) o;

    if (instance != null ? !instance.equals(instanceMarking.instance) : instanceMarking.instance != null) {
      return false;
    }

    return true;
  }

  public int hashCode() {
    return (instance != null ? instance.hashCode() : 0);
  }

  public String toString() {
    return instance + "senseGuess: + " + sense + "\nsubcatGuess: " + subcat + "\n";
  }
}

