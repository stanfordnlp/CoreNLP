package edu.stanford.nlp.ie.pnp;

import edu.stanford.nlp.classify.ClassifiedDatum;
import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.classify.ClassifierFactory;
import edu.stanford.nlp.classify.ClassifierTester;
import edu.stanford.nlp.classify.GeneralDataset;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.BasicDataCollection;
import edu.stanford.nlp.ling.DataCollection;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.util.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.util.*;

/**
 * Trainer for PnpClassifier that conforms to the Classify API. A single
 * instance of this class can be used to train multiple PnpClassifier's, since
 * all it stores internally is the properties.
 *
 * @author Joseph Smarr
 */
public class PnpClassifierFactory implements ClassifierFactory { // Serializable
  /**
   *
   */
  private static final long serialVersionUID = 2736394118252253618L;
  private final Properties properties; // PnpClassifier properties

  /**
   * Constructs a new PnpClassifierFactory using the given PnpClassifier properties.
   * Note that the <tt>numCategory</tt> property will be ignored since it's set
   * based on the training corpus in {@link #trainClassifier}.
   */
  public PnpClassifierFactory(Properties properties) {
    this.properties = properties;
    //this.properties.setProperty("DEBUG","true"); // TAKE ME OUT
  }

  /**
   * Constructs a new PnpClassifierFactory using the default PnpClassifier properties.
   */
  public PnpClassifierFactory() {
    this(PnpClassifier.getDefaultProperties());
  }

  /**
   * Trains a PnpClassifier on the given list of example PNP Datums.
   * Datums should be made from {@link PnpClassifier#makeDatum}.
   * The number of categories to use is determined by counting the number of
   * unique labels in the example Datums.
   */
  public Classifier trainClassifier(List examples) {
    if (!(examples instanceof BasicDataCollection)) {
      examples = new BasicDataCollection(examples);
    }
    BasicDataCollection bdc = (BasicDataCollection) examples;

    properties.setProperty("numCategories", Integer.toString(bdc.labelIndex().size()));
    PnpClassifier pnpc = new PnpClassifier(properties);
    for (Iterator iter = examples.iterator(); iter.hasNext();) {
      pnpc.addCounts((Datum) iter.next());
    }
    pnpc.tuneParameters();
    return (pnpc);
  }

  /**
   * For internal debugging purposes only.
   * <pre>Usage:java [ -p propertiesfile ] PnpClassifierFactory pnpfile+</pre>
   */
  public static void main(String[] args) throws Exception {
    // params that ideally should be properties or some such
    int numFolds = 1;
    double trainFraction = 0.75;
    Random rand = new Random(0);
    PrintWriter out = new PrintWriter(System.err, true);
    //args=new String[]{"c:\\tmp-pnp.txt"}; // TAKE ME OUT
    if (args.length == 0) {
      System.err.println("Usage: java PnpClassifierFactory [ -p propertiesfile ] pnpfile+");
      System.err.println("Each pnpfile should have one pnp per line, category = filename");
      System.exit(0);
    }

    Properties props = null; // custom pnp properties
    // reads in each file, one pnp per line (category = filename)
    BasicDataCollection data = new BasicDataCollection();
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-p")) {
        // reads in the properties file
        props = new Properties(PnpClassifier.getDefaultProperties());
        out.println("Reading pnp properties from " + args[i + 1]);
        props.load(new FileInputStream(args[i + 1]));
        i++;
        continue;
      }

      File file = new File(args[i]);
      String category = getName(file);
      out.print("Reading category " + category + "...");
      String[] examples = IOUtils.slurpFile(file).split("\\s*[\r\n]+\\s*");
      for (int j = 0; j < examples.length; j++) {
        data.add(PnpClassifier.makeDatum(examples[j], category));
      }
      out.println("read " + examples.length + " examples");
    }
    out.println();
    if (props == null) {
      props = PnpClassifier.getDefaultProperties();
    }

    ClassifierFactory pnpcf = new PnpClassifierFactory(props);

    for (int i = 1; i <= numFolds; i++) {
      out.println("Fold " + i + " of " + numFolds);
      Collections.shuffle(data, rand); // since splitRandom is broken
      DataCollection[] splits = data.split(trainFraction);
      out.println("Training on " + splits[0].size() + " docs...");
      Classifier pnpc = pnpcf.trainClassifier(splits[0]);

      out.println("Testing on " + splits[1].size() + " docs...");
      ClassifiedDatum[] results = ClassifierTester.testClassifier(pnpc, splits[1]);
      ClassifierTester.printConfusionMatrix(results, out, 8);
      out.println(ClassifierTester.perLabelAccuracy(results));
      out.println(StringUtils.join(ClassifierTester.incorrectResults(results), "\n"));
      out.println();
    }

    /**
     // very basic test
     BasicDataCollection bdc=new BasicDataCollection();
     bdc.add(PnpClassifier.makeDatum("ABC","word"));
     bdc.add(PnpClassifier.makeDatum("BCD","word"));
     bdc.add(PnpClassifier.makeDatum("123","number"));
     bdc.add(PnpClassifier.makeDatum("234","number"));

     bdc.add(PnpClassifier.makeDatum("ACD","word"));
     bdc.add(PnpClassifier.makeDatum("134","number"));
     bdc.add(PnpClassifier.makeDatum("AB3","word"));
     bdc.add(PnpClassifier.makeDatum("12C","room"));

     DataCollection[] splits=bdc.split(0.5);
     PrintWriter out=new PrintWriter(System.err,true);
     Classifier pnpc=new PnpClassifierFactory().trainClassifier(splits[0]);
     ClassifiedDatum[] results=ClassifierTester.testClassifier(pnpc,splits[1]);
     ClassifierTester.printConfusionMatrix(results,out,8);
     out.println(ClassifierTester.perLabelAccuracy(results));
     out.println(ClassifierTester.perPredictedLabelAccuracy(results));
     out.println(ClassifierTester.accuracy(ClassifierTester.resultsWithLabel(results,"word")));
     out.println(ClassifierTester.precisionRecallStats(results,"number"));
     */
  }

  /**
   * Retains only the single-word test pnps not seen in training.
   */
  private static void retainSingleUnks(DataCollection test, DataCollection train) {
    System.err.println("Size of test before pruning: " + test.size());
    for (Iterator iter = test.iterator(); iter.hasNext();) {
      Datum d = (Datum) iter.next();
      String pnp = (String) ((List) d.asFeatures()).get(0);
      if (train.contains(d) || pnp.indexOf(' ') != -1) {
        iter.remove();
      }
    }
    System.err.println("Size of test after pruning: " + test.size());
  }

  /**
   * Returns name of file with extension stripped. /tmp/lkj.sh --> lkj
   */
  private static String getName(File file) {
    String name = file.getName();
    int extensionStart = name.lastIndexOf(".");
    if (extensionStart != -1) {
      name = name.substring(0, extensionStart);
    }
    return (name);
  }

  public Classifier trainClassifier(GeneralDataset dataset) {
    throw new UnsupportedOperationException();
  }
}
