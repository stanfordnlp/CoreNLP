package edu.stanford.nlp.trees;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import edu.stanford.nlp.util.ArgumentParser;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Given a list of trees, splits the trees into three separate files.
 * <p>
 * The program uses a random seed to divide the trees.  If the input
 * dataset is later extended, the same seed can be used and trees
 * which did not change position in the data set will be put in the
 * same division.
 * <p>
 * Example command line:
 * {@code java edu.stanford.nlp.trees.SplitTrainingSet -input foo.mrg -output bar.mrg -seed 1000 }
 */
public class SplitTrainingSet {

  private static final Redwood.RedwoodChannels logger = Redwood.channels(SplitTrainingSet.class);

  @ArgumentParser.Option(name="input", gloss="The file to use as input.", required=true)
  private static String INPUT; // = null;

  @ArgumentParser.Option(name="output", gloss="Where to send the splits.", required=true)
  private static String OUTPUT; // = null;

  @ArgumentParser.Option(name="split_names", gloss="Divisions to use for the output")
  private static String[] SPLIT_NAMES = { "train", "dev", "test" };

  @ArgumentParser.Option(name="split_weights", gloss="Portions to use for the divisions")
  private static Double[] SPLIT_WEIGHTS = { 0.7, 0.15, 0.15 };

  @ArgumentParser.Option(name="seed", gloss="Random seed to use")
  private static long SEED; // = 0L;

  private SplitTrainingSet() { } // only static methods


  private static int weightedIndex(List<Double> weights, Random random) {
    double offset = random.nextDouble();
    int index = 0;
    for (Double weight : weights) {
      offset = offset - weight;
      if (offset < 0.0) {
        return index;
      }
      index = index + 1;
    }
    return weights.size() - 1;
  }

  @SuppressWarnings("unused")
  public static void main(String[] args) throws IOException {
    // Parse the arguments
    Properties props = StringUtils.argsToProperties(args);
    ArgumentParser.fillOptions(new Class[]{ArgumentParser.class, SplitTrainingSet.class}, props);

    if (SPLIT_NAMES.length != SPLIT_WEIGHTS.length) {
      throw new IllegalArgumentException("Name and weight arrays must be of the same length");
    }

    double totalWeight = 0.0;
    for (Double weight : SPLIT_WEIGHTS) {
      totalWeight += weight;
      if (weight < 0.0) {
        throw new IllegalArgumentException("Split weights cannot be negative");
      }
    }

    if (totalWeight <= 0.0) {
      throw new IllegalArgumentException("Split weights must total to a positive weight");
    }

    List<Double> splitWeights = new ArrayList<>();
    for (Double weight : SPLIT_WEIGHTS) {
      splitWeights.add(weight / totalWeight);
    }
    logger.info("Splitting into " + splitWeights.size() + " lists with weights " + splitWeights);


    if (SEED == 0L) {
      SEED = System.nanoTime();
      logger.info("Random seed not set by options, using " + SEED);
    }
    Random random = new Random(SEED);

    List<List<Tree>> splits = new ArrayList<>();
    for (Double d : splitWeights) {
      splits.add(new ArrayList<>());
    }

    Treebank treebank = new MemoryTreebank(PennTreeReader::new);
    treebank.loadPath(INPUT);

    logger.info("Splitting " + treebank.size() + " trees");
    for (Tree tree : treebank) {
      int index = weightedIndex(splitWeights, random);
      splits.get(index).add(tree);
    }

    for (int i = 0; i < splits.size(); ++i) {
      String filename = OUTPUT + '.' + SPLIT_NAMES[i];
      List<Tree> split = splits.get(i);
      logger.info("Writing " + split.size() + " trees to " + filename);
      FileWriter fout = new FileWriter(filename);
      BufferedWriter bout = new BufferedWriter(fout);
      for (Tree tree : split) {
        bout.write(tree.toString());
        bout.newLine();
      }
      bout.close();
      fout.close();
    }
  }

}
