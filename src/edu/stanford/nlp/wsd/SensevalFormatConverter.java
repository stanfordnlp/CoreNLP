package edu.stanford.nlp.wsd;

import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;


/**
 * Program for converting and padding Senseval-1 and Senseval-2
 * format files to CS224N format files.
 * <p/>
 * Reads multiple instances and outputs them to files.  For each lexical item there is a .train,
 * .test and .ans file.  The .train file has one instance per line, marked for sense with small
 * integers, the .test file has one instance per line, unmarked, and the .ans file has answer(s) of each
 * .test instance on corresponding lines (there may be multiple answers).  Senses are small integers if
 * wordnet sense is provided in the input data, they may also be 'P' or 'U'.
 * <p/>
 * <p/>
 * Usage: <command> [options] <key file> <input file> [...]
 * Options:
 * -ttsplit p : specifies the fraction (0<=p<=1) of instances to put in .train
 * If absent, Senseval's test/train split will be used.
 * -noNonIntSenses : causes the sense markings 'P' and 'U' to be supressed
 * -allSenses : makes train and test files contain multiple senses if listed
 * (default is to output all senses in .test but only first sense in .train)
 * -stringSenses : causes WordNet synset strings to be used instead of small-integer senses
 * -outputPrefix o : prepends o to output filenames to specify output directory
 * -quiet : suppresses warnings
 * -combineTestAndAns : puts answers in .test files instead of separate .ans file
 * -instanceIDs : outputs referenceIDs for task and instance as the first and second words on each output line
 *
 * @author Galen Andrew (pupochik@cs.stanford.edu)
 * @author Sepandar Kamvar (sdkamvar@stanford.edu)
 */

public class SensevalFormatConverter {

  private SensevalFormatConverter() {} // only static methods

  private static HashMap<String,Index<String>> numMap;
  private static HashMap<String,ArrayList<String>> keyMap;
  private static ArrayList<SensevalInstance> allInstances;
  private static boolean quiet;
  private static boolean allSenses;
  private static boolean stringSenses;
  private static boolean instanceIDs;

  private static void parseError() {
    System.err.println("Usage: <command> [options] <key file> <input file> [...]");
    System.err.println("Options:");
    System.err.println("  -ttsplit p : specifies the fraction (0<=p<=1) of instances to put in .train");
    System.err.println("     If absent, Senseval's test/train split will be used.");
    System.err.println("  -noNonIntSenses : causes the sense markings 'P' and 'U' to be supressed");
    System.err.println("  -allSenses : makes train and test files contain multiple senses if listed");
    System.err.println("    (default is to output all senses in .test but only first sense in .train)");
    System.err.println("  -stringSenses : causes WordNet synset strings to be used instead of small-integer senses");
    System.err.println("  -outputPrefix o : prepends o to output filenames to specify output directory");
    System.err.println("  -quiet : suppresses warnings");
    System.err.println("  -combineTestAndAns : puts answers in .test files instead of separate .ans file");
    System.err.println("  -instanceIDs : outputs referenceIDs for task and instance as the first and second words on each output line");
    System.exit(1);
  }

  private static void parseKeyFile(String keyFilename, boolean noNonIntSenses) {
    BufferedReader key = null;

    try {
      key = new BufferedReader(new FileReader(keyFilename));
    } catch (FileNotFoundException e) {
      System.err.println("Cannot find key file " + keyFilename);
      parseError();
    }
    try {
      System.out.println("Processing key file \"" + keyFilename + "\"");
      for (String line; (line = key.readLine()) != null; ) {
        StringTokenizer tokens = new StringTokenizer(line);
        ArrayList<String> senseIDs = new ArrayList<String>();
        String instanceID = null;
        String lexElt = null;
        while (tokens.hasMoreTokens()) {
          String token = tokens.nextToken();
          if (lexElt == null) {
            lexElt = token;
          } else if (instanceID == null) {
            instanceID = token;
          }

          // found instanceID-- everything else is a senseID
          else if (!noNonIntSenses || token.length() > 1) {
            senseIDs.add(new String(token));
          }
        }
        if (instanceID == null) {
          if (!quiet) {
            System.err.println("Could not find instanceID in key file " + keyFilename + ":");
            System.err.println(line);
          }
        } else if (senseIDs.isEmpty()) {
          if (!quiet) {
            System.err.println("Could not find valid senseID in key file " + keyFilename + ":");
            System.err.println(line);
            System.err.println("Instance will be skipped if test instance.");
          }
        } else {
          Index<String> senseNum;
          if (numMap.containsKey(lexElt)) {
            senseNum = numMap.get(lexElt);
          } else {
            numMap.put(lexElt, senseNum = new HashIndex<String>());
          }
          for (int i = 0; i < senseIDs.size(); i++) {
            // don't number strange cases of sense = 'P' or 'U'
            if (senseIDs.get(i).length() > 1) {
              senseNum.indexOf(senseIDs.get(i), true);
            }
          }
          keyMap.put(instanceID, senseIDs);
        }
      }
    } catch (Exception e) {
      System.err.println("IOexception reading key file " + keyFilename);
      System.exit(1);
    }
  }

  private static void parseInputFiles(ArrayList<String> inFilenames, boolean noNonIntSenses) {
    SensevalHandler handler = new SensevalHandler(allInstances, numMap, noNonIntSenses);


    XMLReader parser = null;
    try {
      String parserName = "org.apache.xerces.parsers.SAXParser";
      parser = (XMLReader) Class.forName(parserName).newInstance();
    } catch (Exception e) {
      System.err.println("Can't find the class: " + e);
      System.err.println("You must have xerces package on your classpath.");
      System.exit(1);
    }

    try {
      parser.setContentHandler(handler);
      parser.setErrorHandler(handler);
      for (int i = 0; i < inFilenames.size(); i++) {
        String inFilename = inFilenames.get(i);
        try {
          Reader reader = new BufferedReader(new FileReader(inFilename));
          InputSource inputSource = new InputSource(reader);
          System.out.println("Processing input file \"" + inFilename + "\".");
          parser.parse(inputSource);

        } catch (FileNotFoundException e) {
          System.err.println("Cannot find input file \"" + inFilename + "\"");
          parseError();
        }
      }

    } catch (Exception e) {
      System.err.println("parse problem: " + e);
      System.exit(1);
    }
  }

  private static void writeOutput(double ttsplit, String outputPrefix, boolean combineTestAndAns) {
    HashMap<String,BufferedWriter> outFiles = new HashMap<String,BufferedWriter>();
    System.out.println("Writing output files.");

    for (SensevalInstance instance : allInstances) {

      boolean noSenseID = instance.getSenseIDs().isEmpty();
      boolean isTestIns;
      if (ttsplit >= 0) {
        isTestIns = (Math.random() > ttsplit);
      } else {
        isTestIns = noSenseID;
      }

      ArrayList<String> senseIDs;
      if (noSenseID) {
        senseIDs = keyMap.get(instance.getInstId());
        if (senseIDs == null) {
          if (!quiet) {
            System.err.println("Instance " + instance.getInstId() + " has no senseID in key file.  Skipping.");
          }
          continue;
        }
      } else {
        senseIDs = instance.getSenseIDs();
      }

      BufferedWriter insFile = null;
      BufferedWriter ansFile = null;
      String insExt = new String(isTestIns ? ".test" : ".train");
      String insFilename = new String(outputPrefix + instance.getLexElt() + insExt);
      String ansFilename = new String(outputPrefix + instance.getLexElt() + ".ans");

      try {
        if (outFiles.containsKey(insFilename)) {
          insFile = outFiles.get(insFilename);
        } else {
          insFile = new BufferedWriter(new FileWriter(insFilename));
          outFiles.put(insFilename, insFile);
        }

        if (isTestIns && !combineTestAndAns) {
          if (outFiles.containsKey(ansFilename)) {
            ansFile = outFiles.get(ansFilename);
          } else {
            ansFile = new BufferedWriter(new FileWriter(ansFilename));
            outFiles.put(ansFilename, ansFile);
          }
        }

        int targetWordPos = instance.getTargetWordPos();

        if (instanceIDs) {
          insFile.write(instance.getNakedLexElt() + " " + instance.getInstId() + " ");
        }

        for (int i = 0; i < 5 - targetWordPos; i++) {
          insFile.write("xtrainx ");
        }

        Index<String> senseNum = numMap.get(instance.getNakedLexElt());

        ArrayList<String> context = instance.getContext();
        for (int i = 0; i < context.size(); i++) {
          String word = new String(context.get(i));
          if (i == targetWordPos) {

            // here we append the senseIDs to the word or the .ans file
            // according to whether this is a test instance.
            for (String thisSenseID : senseIDs) {
              String ourSenseID;

              if (stringSenses) {
                ourSenseID = "(" + thisSenseID + ")";
              } else if (thisSenseID.length() == 1)
              // for "P" or "U" tag
              {
                ourSenseID = thisSenseID;
              } else // in small-integer format
              {
                ourSenseID = Integer.toString(senseNum.indexOf(thisSenseID, true));
              }

              if (!isTestIns || combineTestAndAns) {
                word += "_" + ourSenseID;
                if (!allSenses) {
                  break;  // default is to only append first sense to train files
                }
              } else {
                ansFile.write(ourSenseID + " ");
              }
            }

            if (isTestIns && !combineTestAndAns) {
              ansFile.newLine();
            }
          }

          word += ' ';
          insFile.write(word);
        }

        for (int i = 5; i >= context.size() - targetWordPos; i--) {
          insFile.write("xtrainx ");
        }

        insFile.newLine();

      } catch (IOException e) {
        System.err.println(e.getMessage());
        System.exit(1);
      }
    }

    // close all files
    for (String filename : outFiles.keySet()) {
      BufferedWriter outFile = outFiles.get(filename);
      try {
        outFile.close();
      } catch (IOException e) {
        System.err.println("Error closing output file \"" + filename + "\". Ignoring.");
      }
    }
  }

  public static void main(String[] args) {
    if (args.length < 2) {
      parseError();
    }

    int nextArg = 0;

    double ttsplit = -1;
    quiet = false;
    boolean noNonIntSenses = false;
    String outputPrefix = "";
    boolean combineTestAndAns = false;

    while (args[nextArg].charAt(0) == '-') {
      if (args[nextArg].equals("-ttsplit")) {
        nextArg++;
        ttsplit = Double.parseDouble(args[nextArg]);
        if (args.length < 4) {
          parseError();
        }
      } else if (args[nextArg].equals("-quiet")) {
        quiet = true;
      } else if (args[nextArg].equals("-noNonIntSenses")) {
        noNonIntSenses = true;
      } else if (args[nextArg].equals("-outputPrefix")) {
        nextArg++;
        outputPrefix = args[nextArg];
      } else if (args[nextArg].equals("-combineTestAndAns")) {
        combineTestAndAns = true;
      } else if (args[nextArg].equals("-stringSenses")) {
        stringSenses = true;
      } else if (args[nextArg].equals("-allSenses")) {
        allSenses = true;
      } else if (args[nextArg].equals("-instanceIDs")) {
        instanceIDs = true;
      }
      nextArg++;
      if (nextArg >= args.length) {
        parseError();
      }
    }

    String keyFilename = args[nextArg++];

    numMap = new HashMap<String,Index<String>>();
    keyMap = new HashMap<String,ArrayList<String>>();

    parseKeyFile(keyFilename, noNonIntSenses);

    if (nextArg >= args.length) {
      parseError();
    }
    ArrayList<String> inFilenames = new ArrayList<String>();
    while (nextArg < args.length) {
      inFilenames.add(args[nextArg++]);
    }

    allInstances = new ArrayList<SensevalInstance>();

    parseInputFiles(inFilenames, noNonIntSenses);

    writeOutput(ttsplit, outputPrefix, combineTestAndAns);

    System.out.println("Finished processing.");
  }

}
