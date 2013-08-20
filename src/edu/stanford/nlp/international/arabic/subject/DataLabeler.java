package edu.stanford.nlp.international.arabic.subject;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * 
 * @author Spence Green
 *
 */
public final class DataLabeler {

  private DataLabeler() {}

  private final static int MIN_ARGS = 3;
  private static String usage() {
    String cmdLineUsage = String.format("Usage: java %s [options] input_file mada_file output_prefix%n%n", DataLabeler.class.getName());
    StringBuilder classUsage = new StringBuilder(cmdLineUsage);
    String nl = System.getProperty("line.separator");
    classUsage.append(" -v               : Verbose mode (print matched trees)").append(nl);
    classUsage.append(" -g               : Use gold pre-terminal analyses (raw trees)").append(nl);
    classUsage.append(" -f <model_file>  : Flat test mode (specify Stanford POS tagger model)").append(nl);
    classUsage.append(" -t <model_file>  : Tree test mode (specify Stanford POS tagger model)").append(nl);
    classUsage.append(" -c               : Collapse NN* and VB* into single tags").append(nl);
    classUsage.append(" -p <tag_map>     : Specify location of LDC tag maps in this file (one per line)").append(nl);
    classUsage.append(" -l num           : Specify a maximum length for labeled subjects").append(nl);
    return classUsage.toString();
  }

  private static Map<String, Integer> optionArgDefs() {
    Map<String,Integer> optionArgDefs = new HashMap<String,Integer>();
    optionArgDefs.put("v", 0);
    optionArgDefs.put("g", 0);
    optionArgDefs.put("f", 1);
    optionArgDefs.put("t", 1);
    optionArgDefs.put("c", 0);
    optionArgDefs.put("p", 1);
    optionArgDefs.put("l", 1);
    return optionArgDefs;
  }

  public static void main(String[] args) {
    if(args.length < MIN_ARGS) {
      System.err.println(usage());
      System.exit(-1);
    }
    Properties options = StringUtils.argsToProperties(args, optionArgDefs());

    boolean VERBOSE = PropertiesUtils.getBool(options, "v", false);
    boolean COLLAPSE_TAGS = PropertiesUtils.getBool(options, "c", false);
    boolean USE_GOLD = PropertiesUtils.getBool(options, "g", false);
    boolean TEST_TREE = options.containsKey("t");
    String posTaggerModelFile = options.getProperty("t", null);
    boolean TEST_FLAT = options.contains("f");
    posTaggerModelFile = options.getProperty("f", posTaggerModelFile);
    int SUBJ_LEN_LIMIT = PropertiesUtils.getInt(options, "l", Integer.MAX_VALUE);
    String mappingFile = options.getProperty("p", null);
    
    String[] parsedArgs = options.getProperty("","").split("\\s+");
    if (parsedArgs.length != MIN_ARGS) {
      System.err.println(usage());
      System.exit(-1);
    }
    String inputFile = parsedArgs[0];
    String madaFile = parsedArgs[1];
    String outputPrefix = parsedArgs[2];
    
    Date startTime = new Date();
    System.out.println("############################################");
    System.out.println("### Arabic Subject Detector Data Labeler ###");
    System.out.println("############################################");
    System.out.printf("Start time: %s\n", startTime);

    LabelerController controller = new LabelerController(inputFile,outputPrefix,madaFile);
    controller.setVerbose(VERBOSE);
    controller.setOptions(COLLAPSE_TAGS,TEST_FLAT,TEST_TREE,USE_GOLD,SUBJ_LEN_LIMIT,mappingFile,posTaggerModelFile);
    controller.run();

    Date stopTime = new Date();
    long elapsedTime = stopTime.getTime() - startTime.getTime();
    System.out.println();
    System.out.println();
    System.out.printf("Completed processing at %s\n",stopTime);
    System.out.printf("Elapsed time: %d seconds\n", (int) (elapsedTime / 1000F));
  }
}
