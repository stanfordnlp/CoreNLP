package edu.stanford.nlp.wsd.synsense;

import edu.stanford.nlp.ling.WordFactory;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.Timing;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Galen Andrew (galand@cs.stanford.edu)
 */
public class InstancePreparer {

  // datasets
  protected static List<String> words;
  private static List<Instance>[] senseTrainData = null;
  private static List<Instance>[] senseTestData = null;
  private static List<Instance>[] subcatTrainData = null;
  private static List<Instance>[] subcatTestData = null;

  private static Options op = new Options();

  private static void printUsageAndExit() {
    System.out.println("usage: java edu.stanford.nlp.wsd.synsense.InstancePreparer [word] | [-convert file]");
    System.out.println("If no args, all words in dir will be processed");
    System.out.println("If word is specified, will look for files in current directory for word.");
    System.out.println("If -convert option is used, file is read as single instance file and output as multiple");
    System.exit(1);
  }

  public static void main(String[] args) {
    if (args.length == 2 && args[0].equals("-convert")) {
      readAllWordsFromOneSerializedFile(args[1]);
      for (int i = 0; i < words.size(); i++) {
        String word = words.get(i);
        writeInstancesOfOneWordToSerializedFile(word, i);
      }
      return;
    }

    words = new ArrayList<String>();
    if (args.length == 1) {
      words.add(args[0]);
    } else if (args.length == 0) {
      File currentDir = new File(".");
      String[] filenames = currentDir.list();
      System.out.println("loading data for words: ");
      for (int i = 0; i < filenames.length; i++) {
        String filename = filenames[i];
        if (filename.endsWith(".parser")) {
          String word = filename.substring(0, filename.length() - 7);
          words.add(word);
          System.out.print(word + " ");
        }
      }
      System.out.println();
    } else {
      printUsageAndExit();
    }

    senseTrainData = new List[words.size()];
    senseTestData = new List[words.size()];
    subcatTrainData = new List[words.size()];
    subcatTestData = new List[words.size()];
    for (int i = 0; i < words.size(); i++) {
      String word = words.get(i);
      loadDataForOneWord(word, i);
      writeInstancesOfOneWordToSerializedFile(word, i);
    }
  }

  private static void loadDataForOneWord(String word, int i) {
    System.out.println("Loading data for word " + word);
    SubcatProbabilityMetric subcatParser = new SubcatProbabilityMetric(word + ".parser", op);
    System.out.println("Parsing data instances.");
    try {
      senseTrainData[i] = new ArrayList<Instance>();
      readSenseMarkedInstances(word + ".v.train", senseTrainData[i], subcatParser);
      senseTestData[i] = new ArrayList<Instance>();
      readSenseMarkedInstances(word + ".v.test", senseTestData[i], subcatParser);
      subcatTrainData[i] = new ArrayList<Instance>();
      readSubcatMarkedInstances(word + ".subcat.train", subcatTrainData[i], subcatParser);
      subcatTestData[i] = new ArrayList<Instance>();
      readSubcatMarkedInstances(word + ".subcat.test", subcatTestData[i], subcatParser);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void writeInstancesOfOneWordToSerializedFile(String word, int i) {
    // get parsed instances from file
    System.out.println("Writing instances for word " + word);
    FileOutputStream fos = null;
    try {
      fos = new FileOutputStream(word + ".ser");
      ObjectOutputStream oos = new ObjectOutputStream(fos);
      System.out.println("Writing sense train instances");
      oos.writeObject(senseTrainData[i]);
      System.out.println("Writing sense test instances");
      oos.writeObject(senseTestData[i]);
      System.out.println("Writing subcat train instances");
      oos.writeObject(subcatTrainData[i]);
      System.out.println("Writing subcat test instances");
      oos.writeObject(subcatTestData[i]);
      oos.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static void readSenseMarkedInstances(String filename, List<Instance> list, SubcatProbabilityMetric parser) throws Exception {
    System.out.println("Reading and processing sense instances from " + filename);
    String line;
    BufferedReader senseInstanceFile = new BufferedReader(new FileReader(filename));
    int numTotal = 0;
    while ((line = senseInstanceFile.readLine()) != null) {
      Instance instance = new Instance(line, parser); // TODO pass parser
      list.add(instance);
      numTotal++;
      if (numTotal % 10 == 0) {
        System.out.println("processed: " + numTotal);
      }
      //      if (numTotal == 2) break; // TODO remove
    }
    System.out.println("Got " + numTotal + " sense marked data instances");
  }

  private static void readSubcatMarkedInstances(String filename, List<Instance> list, SubcatProbabilityMetric parser) {
    System.out.println("Reading and processing subcat instances from " + filename);
    // cdm Jun 2004: I updated this to be functionally equivalent when I
    // updated NPTmpRetainingTreeNormalizer, but I'm not sure that you're 
    // better using the option here than TEMPORAL_ACL03PCFG
    DiskTreebank subcatTreebank = new DiskTreebank(new TreeReaderFactory() {
      public TreeReader newTreeReader(Reader in) {
        return new PennTreeReader(in, new LabeledScoredTreeFactory(new WordFactory()), new NPTmpRetainingTreeNormalizer(NPTmpRetainingTreeNormalizer.TEMPORAL_ANY_TMP_PERCOLATED, false, 0, false));
      }
    });
    // new DiskTreebank(new WordLabeledScoredTreeReaderFactory());
    subcatTreebank.loadPath(filename);

    Iterator it = subcatTreebank.iterator();
    int numTotal = 0;
    while (it.hasNext()) {
      Tree tree = (Tree) it.next();
      try {
        Instance instance = new Instance(tree, parser); // TODO pass parser
        list.add(instance);
        numTotal++;
      } catch (Exception e) {
        throw new RuntimeException(e); // shouldn't throw exceptions
      }
      if (numTotal % 10 == 0) {
        System.out.println("processed: " + numTotal);
      }
      //      if (numTotal == 2) break; // TODO remove
    }
    System.out.println("Got " + numTotal + " subcat marked data instances");
  }

  protected static void readAllWordsFromOneSerializedFile(String infile) {
    Timing.startTime();
    System.out.print("Reading data instances from serialized file " + infile + "...");
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
    subcatTestData = new List[words.size()];
    for (int i = 0; i < subcatTestData.length; i++) {
      subcatTestData[i] = new ArrayList<Instance>();
    }
  }
}
