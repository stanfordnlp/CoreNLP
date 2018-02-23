package edu.stanford.nlp.international.arabic.parsesegment; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.zip.GZIPInputStream;

import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 *
 * @author Spence Green
 *
 */
public final class JointParser  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(JointParser.class);

  private JointParser() {}

  private final static int MIN_ARGS = 1;
  private static String usage() {
    String cmdLineUsage = String.format("Usage: java %s [OPTS] trainFile < lattice_file > trees%n", JointParser.class.getName());
    StringBuilder classUsage = new StringBuilder(cmdLineUsage);
    String nl = System.getProperty("line.separator");
    classUsage.append(" -v        : Verbose output").append(nl);
    classUsage.append(" -t file   : Test on input trees").append(nl);
    classUsage.append(" -l num    : Max (gold) sentence length to evaluate (in interstices)").append(nl);
    classUsage.append(" -o        : Input is a serialized list of lattices").append(nl);
    return classUsage.toString();
  }
  private static Map<String, Integer> optionArgDefs() {
    Map<String, Integer> optionArgDefs = Generics.newHashMap();
    optionArgDefs.put("v", 0);
    optionArgDefs.put("t", 1);
    optionArgDefs.put("l", 1);
    optionArgDefs.put("o", 0);
    return optionArgDefs;
  }

  /**
   *
   * @param args
   */
  public static void main(String[] args) {
    if(args.length < MIN_ARGS) {
      log.info(usage());
      System.exit(-1);
    }
    Properties options = StringUtils.argsToProperties(args, optionArgDefs());
    boolean VERBOSE = PropertiesUtils.getBool(options, "v", false);
    File testTreebank = options.containsKey("t") ? new File(options.getProperty("t")) : null;
    int maxGoldSentLen = PropertiesUtils.getInt(options, "l", Integer.MAX_VALUE);
    boolean SER_INPUT = PropertiesUtils.getBool(options, "o", false);

    String[] parsedArgs = options.getProperty("","").split("\\s+");
    if (parsedArgs.length != MIN_ARGS) {
      log.info(usage());
      System.exit(-1);
    }
    File trainTreebank = new File(parsedArgs[0]);

    Date startTime = new Date();
    log.info("###################################");
    log.info("### Joint Segmentation / Parser ###");
    log.info("###################################");
    System.err.printf("Start time: %s\n", startTime);

    JointParsingModel parsingModel = new JointParsingModel();
    parsingModel.setVerbose(VERBOSE);
    parsingModel.setMaxEvalSentLen(maxGoldSentLen);
    parsingModel.setSerInput(SER_INPUT);

    //WSGDEBUG -- Some stuff for eclipse debugging
    InputStream inputStream = null;
    try {
      if(System.getProperty("eclipse") == null) {
        inputStream = (SER_INPUT) ? new ObjectInputStream(new GZIPInputStream(System.in)) : System.in;
      } else {
        FileInputStream fileStream = new FileInputStream(new File("debug.2.xml"));
        inputStream = (SER_INPUT) ? new ObjectInputStream(new GZIPInputStream(fileStream)) : fileStream;
      }
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(-1);
    } finally {
      if (inputStream != null) {
        try {
          inputStream.close();
        } catch (IOException ignored) {}
      }
    }

    if(!trainTreebank.exists())
      log.info("Training treebank does not exist!\n  " + trainTreebank.getPath());
    else if(testTreebank != null && !testTreebank.exists())
      log.info("Test treebank does not exist!\n  " + testTreebank.getPath());
    else if(parsingModel.run(trainTreebank, testTreebank, inputStream))
      log.info("Successful shutdown!");
    else
      log.error("Parsing model failure.");


    Date stopTime = new Date();
    long elapsedTime = stopTime.getTime() - startTime.getTime();
    log.info();
    log.info();
    System.err.printf("Completed processing at %s\n",stopTime);
    System.err.printf("Elapsed time: %d seconds\n", (int) (elapsedTime / 1000F));
  }
}
