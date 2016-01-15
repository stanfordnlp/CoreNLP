package edu.stanford.nlp.parser.tools;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.parser.common.ParserGrammar;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.StringUtils;

/**
 * Given a list of sentences, converts the sentences to trees and then
 * relabels them using a list of new labels.
 * <br>
 * This tool processes the text using a given parser model, one
 * sentence per line.
 * <br>
 * The labels file is expected to be a tab separated file.  If there
 * are multiple labels on a line, only the last one is used.
 * <br>
 * There are a few options for how to handle missing labels: 
 * FAIL, DEFAULT, KEEP_ORIGINAL
 * <br>
 * Example command line:
 * <br>
 * java edu.stanford.nlp.parser.tools.ParseAndSetLabels -output foo.txt -sentences "C:\Users\JohnBauer\Documents\alphasense\dataset\sentences10.txt" -labels "C:\Users\JohnBauer\Documents\alphasense\dataset\phrases10.tsv" -parser edu/stanford/nlp/models/srparser/englishSR.ser.gz -tagger edu/stanford/nlp/models/pos-tagger/english-left3words/english-left3words-distsim.tagger -remapLabels 0=1,1=2,2=2,3=0,4=0
 */

public class ParseAndSetLabels {
  private static Logger logger = LoggerFactory.getLogger(ParseAndSetLabels.class);

  public enum MissingLabels {
    FAIL, DEFAULT, KEEP_ORIGINAL
  }

  public static void setLabels(Tree tree, Map<String, String> labelMap,
                               MissingLabels missing, String defaultLabel,
                               Set<String> unknowns) {
    if (tree.isLeaf()) {
      return;
    }
    String text = Sentence.listToString(tree.yield());
    String label = labelMap.get(text);
    if (label != null) {
      tree.label().setValue(label);
    } else {
      switch (missing) {
      case FAIL:
        throw new RuntimeException("No label for '" + text + "'");
      case DEFAULT:
        tree.label().setValue(defaultLabel);
        unknowns.add(text);
        break;
      case KEEP_ORIGINAL:
        // do nothing
        break;
      default:
        throw new IllegalArgumentException("Unknown MissingLabels mode " + missing);
      }
    }
    for (Tree child : tree.children()) {
      setLabels(child, labelMap, missing, defaultLabel, unknowns);
    }
  }

  public static Set<String> setLabels(List<Tree> trees, Map<String, String> labelMap,
                                      MissingLabels missing, String defaultLabel) {
    logger.info("Setting labels");

    Set<String> unknowns = new HashSet<>();

    for (Tree tree : trees) {
      setLabels(tree, labelMap, missing, defaultLabel, unknowns);
    }

    return unknowns;
  }

  public static void writeTrees(List<Tree> trees, String outputFile) {
    logger.info("Writing new trees to " + outputFile);

    try {
      BufferedWriter out = new BufferedWriter(new FileWriter(outputFile));
      for (Tree tree : trees) {
        out.write(tree.toString());
        out.write("\n");
      }
      out.close();
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  public static Map<String, String> readLabelMap(String labelsFile, String separator, String remapLabels) {
    logger.info("Reading labels from " + labelsFile);

    Map<String, String> remap = Collections.emptyMap();
    if (remapLabels != null) {
      remap = StringUtils.mapStringToMap(remapLabels);
      logger.info("Remapping labels using " + remap);
    }

    Map<String, String> labelMap = new HashMap<>();
    for (String phrase : IOUtils.readLines(labelsFile)) {
      String[] pieces = phrase.split(separator);
      String label = pieces[pieces.length - 1];
      if (remap.containsKey(label)) {
        label = remap.get(label);
      }
      labelMap.put(pieces[0], label);
    }
    return labelMap;
  }

  public static List<String> readSentences(String sentencesFile) {
    logger.info("Reading sentences from " + sentencesFile);

    List<String> sentences = new ArrayList<>();
    for (String sentence : IOUtils.readLines(sentencesFile)) {
      sentences.add(sentence);
    }
    return sentences;
  }

  public static ParserGrammar loadParser(String parserFile, String taggerFile) {
    if (taggerFile != null) {
      return ParserGrammar.loadModel(parserFile, "-preTag", "-taggerSerializedFile", taggerFile);
    } else {
      return ParserGrammar.loadModel(parserFile);
    }
  }

  public static List<Tree> parseSentences(List<String> sentences, ParserGrammar parser) {
    logger.info("Parsing sentences");

    List<Tree> trees = new ArrayList<>();
    for (String sentence : sentences) {
      trees.add(parser.parse(sentence));
      if (trees.size() % 1000 == 0) {
        logger.info("  Parsed " + trees.size() + " trees");
      }
    }
    return trees;
  }

  public static void main(String[] args) {
    // TODO: rather than always rolling our own arg parser, we should
    // find a library which does it for us nicely
    String outputFile = null;
    String sentencesFile = null;
    String labelsFile = null;
    String parserFile = LexicalizedParser.DEFAULT_PARSER_LOC;
    String taggerFile = null;
    MissingLabels missing = MissingLabels.DEFAULT;
    String defaultLabel = "-1";
    String separator = "\\t+";
    String saveUnknownsFile = null;
    String remapLabels = null;
    int argIndex = 0;
    while (argIndex < args.length) {
      if (args[argIndex].equalsIgnoreCase("-output")) {
        outputFile = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-sentences")) {
        sentencesFile = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-labels")) {
        labelsFile = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-parser")) {
        parserFile = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-tagger")) {
        taggerFile = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-missing")) {
        missing = MissingLabels.valueOf(args[argIndex + 1]);
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-separator")) {
        separator = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-default")) {
        defaultLabel = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-saveUnknowns")) {
        saveUnknownsFile = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-remapLabels")) {
        remapLabels = args[argIndex + 1];
        argIndex += 2;
      } else {
        throw new IllegalArgumentException("Unknown argument " + args[argIndex]);
      }
    }

    if (outputFile == null) {
      throw new IllegalArgumentException("-output is required");
    }
    if (sentencesFile == null) {
      throw new IllegalArgumentException("-sentences is required");
    }
    if (labelsFile == null) {
      throw new IllegalArgumentException("-labels is required");
    }

    ParserGrammar parser = loadParser(parserFile, taggerFile);

    Map<String, String> labelMap = readLabelMap(labelsFile, separator, remapLabels);

    List<String> sentences = readSentences(sentencesFile);

    List<Tree> trees = parseSentences(sentences, parser);

    Set<String> unknowns = setLabels(trees, labelMap, missing, defaultLabel);

    writeTrees(trees, outputFile);
  }
}
