package edu.stanford.nlp.trees.treebank;
import edu.stanford.nlp.util.logging.Redwood;

import java.util.Date;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.international.arabic.pipeline.ATBArabicDataset;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * A data preparation pipeline for treebanks.
 * <p>
 * A simple framework for preparing various kinds of treebank data. The original goal was to prepare the
 * Penn Arabic Treebank (PATB) trees for parsing. This pipeline arose from the
 * need to prepare various data sets in a uniform manner for the execution of experiments that require
 * multiple tools. The design objectives are:
 * <ul>
 *  <li>Support multiple data input and output types
 *  <li>Allow parameterization of data sets via a plain text file
 *  <li>Support rapid, cheap lexical engineering
 *  <li>End result of processing: a folder with all data sets and a manifest of how the data was prepared
 * </ul>
 *<p>
 * These objectives are realized through three features:
 * <ul>
 *  <li>{@link ConfigParser} -- reads the plain text configuration file and creates configuration parameter objects for each data set
 *  <li>{@link Dataset} interface -- Generic interface for loading, processing, and writing datasets
 *  <li>{@link Mapper} interface -- Generic interface for applying transformations to strings (usually words and POS tags)
 * </ul>
 *<p>
 * The process for preparing arbitrary data set X is as follows:
 * <ol>
 *  <li>Add parameters to {@link ConfigParser} as necessary
 *  <li>Implement the {@link Dataset} interface for the new data set (or use one of the existing classes)
 *  <li>Implement {@link Mapper} classes as needed
 *  <li>Specify the data set parameters in a plain text file
 *  <li>Run {@link TreebankPreprocessor} using the plain text file as the argument
 * </ol>
 *
 * @author Spence Green
 *
 */
public final class TreebankPreprocessor  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(TreebankPreprocessor.class);

  private TreebankPreprocessor() {}

  private static String usage() {
    String cmdLineFormat = String.format("java %s [OPTIONS] config_file%n", TreebankPreprocessor.class.getName());
    StringBuilder sb = new StringBuilder(cmdLineFormat);

    //Add other parameters here
    sb.append(String.format("  -v         : Show verbose output%n"));
    sb.append(String.format("  -d <name>  : Make a distributable package with the specified name%n"));

    return sb.toString();
  }

  private static Dataset getDatasetClass(Properties dsParams) {
    Dataset ds = null;
    String dsType = dsParams.getProperty(ConfigParser.paramType);
    dsParams.remove(ConfigParser.paramType);

    try {
      if(dsType == null)
        ds = new ATBArabicDataset();
      else {
        Class c = ClassLoader.getSystemClassLoader().loadClass(dsType);
        ds = (Dataset) c.newInstance();
      }
    } catch (ClassNotFoundException e) {
      System.err.printf("Dataset type %s does not exist%n", dsType);
    } catch (InstantiationException e) {
      System.err.printf("Unable to instantiate dataset type %s%n", dsType);
    } catch (IllegalAccessException e) {
      System.err.printf("Unable to access dataset type %s%n", dsType);
    }

    return ds;
  }

  private static final int MIN_ARGS = 1;

  //Command line options
  private static boolean VERBOSE = false;
  private static boolean MAKE_DISTRIB = false;
  private static String distribName = null;
  private static String configFile = null;
  private static String outputPath = null;

  public static final Map<String,Integer> optionArgDefs = Generics.newHashMap();
  static {
    optionArgDefs.put("-d", 1);
    optionArgDefs.put("-v", 0);
    optionArgDefs.put("-p", 1);
  }

  private static boolean validateCommandLine(String[] args) {
    Map<String, String[]> argsMap = StringUtils.argsToMap(args,optionArgDefs);

    for(Map.Entry<String, String[]> opt : argsMap.entrySet()) {
      String key = opt.getKey();
      if (key == null) {
        // continue;
      } else switch(key) {
        case "-d":
          MAKE_DISTRIB = true;
          distribName = opt.getValue()[0];
          break;
        case "-v" :
          VERBOSE = true;
          break;
        case "-p" :
          outputPath = opt.getValue()[0];
          break;
        default :
          return false;
      }
    }

    //Regular arguments
    String[] rest = argsMap.get(null);
    if(rest == null || rest.length != MIN_ARGS) {
      return false;
    } else {
      configFile = rest[0];
    }

    return true;
  }

  /**
   * Execute with no arguments for usage.
   */
  public static void main(String[] args) {

    if(!validateCommandLine(args)) {
      log.info(usage());
      System.exit(-1);
    }

    Date startTime = new Date();
    System.out.println("##################################");
    System.out.println("# Stanford Treebank Preprocessor #");
    System.out.println("##################################");
    System.out.printf("Start time: %s%n", startTime);
    System.out.printf("Configuration: %s%n%n", configFile);


    final ConfigParser cp = new ConfigParser(configFile);
    cp.parse();

    final DistributionPackage distrib = new DistributionPackage();

    for (Properties dsParams : cp) {
      String nameOfDataset = PropertiesUtils.hasProperty(dsParams, ConfigParser.paramName) ? dsParams.getProperty(ConfigParser.paramName) : "UN-NAMED";

      if (outputPath != null) {
        dsParams.setProperty(ConfigParser.paramOutputPath, outputPath);
      }

      Dataset ds = getDatasetClass(dsParams);
      if(ds == null) {
        System.out.printf("Unable to instantiate TYPE for dataset %s. Check the javadocs%n",nameOfDataset);
        continue;
      }

      boolean shouldDistribute = dsParams.contains(ConfigParser.paramDistrib) &&
              Boolean.parseBoolean(dsParams.getProperty(ConfigParser.paramDistrib));
      dsParams.remove(ConfigParser.paramDistrib);

      boolean lacksRequiredOptions = !(ds.setOptions(dsParams));
      if(lacksRequiredOptions) {
        System.out.printf("Skipping dataset %s as it lacks required parameters. Check the javadocs%n", nameOfDataset);
        continue;
      }

      ds.build();

      if(shouldDistribute)
        distrib.addFiles(ds.getFilenames());

      if(VERBOSE)
        System.out.printf("%s%n", ds.toString());
    }

    if(MAKE_DISTRIB)
      distrib.make(distribName);

    if(VERBOSE) {
      System.out.println("-->configuration details");
      System.out.println(cp.toString());

      if(MAKE_DISTRIB) {
        System.out.println("-->distribution package details");
        System.out.println(distrib.toString());
      }
    }

    Date stopTime = new Date();
    long elapsedTime = stopTime.getTime() - startTime.getTime();
    System.out.printf("Completed processing at %s%n",stopTime);
    System.out.printf("Elapsed time: %d seconds%n", (int) (elapsedTime / 1000F));
  }

}
