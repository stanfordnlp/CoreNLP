package edu.stanford.nlp.tagger.util; 

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.tagger.io.TaggedFileReader;
import edu.stanford.nlp.tagger.io.TaggedFileRecord;
import edu.stanford.nlp.tagger.maxent.TaggerConfig;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;


/**
 * Implements Chris's heuristic for when a closed tag class can be
 * treated as a closed tag.  You count how many different words in the
 * class you see in the first X% of the training data, then make sure
 * you don't see any new words in the rest of the training or test data.
 * <br>
 * This handles tagged training/test data in any format handled by the
 *  tagger (@see edu.stanford.nlp.tagger.maxent.MaxentTagger).  Files
 *  are specified as a comma-separated list via the flag
 *  -TRAIN_FILE_PROPERTY or -TEST_FILE_PROPERTY.  Closed tags are
 *  specified as a space separated list using the flag
 *  -CLOSED_TAGS_PROPERTY.
 * <br>
 * CountClosedTags then reads each training file to count how many
 * lines are in it.  First, it reads the first
 * -TRAINING_RATIO_PROPERTY fraction of the lines and keeps track of
 * which words show up for each closed tag.  Next, it reads the rest
 * of the training file and keeps track of which words show up in the
 * rest of the data that didn't show up in the rest of the training
 * data.  Finally, it reads all of the test files, once again tracking
 * the words that didn't show up in the training data.
 * <br>
 * CountClosedTags then outputs the number of unique words that showed
 * up in the TRAINING_RATIO_PROPERTY training data and the total
 * number of unique words for each tag.  If the -PRINT_WORDS_PROPERTY
 * flag is set to true, it also prints out the sets of observed words.
 * <br>
 * @author John Bauer
 */
public class CountClosedTags  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(CountClosedTags.class);

  /**
   * Which tags to look for
   */
  private Set<String> closedTags;

  /**
   * Words seen in the first trainingRatio fraction of the trainFiles
   */
  private Map<String, Set<String>> trainingWords = Generics.newHashMap();
  /**
   * Words seen in either trainFiles or testFiles
   */
  private Map<String, Set<String>> allWords = Generics.newHashMap();

  private static final double DEFAULT_TRAINING_RATIO = 2.0 / 3.0;
  /**
   * How much of each training file to count for trainingWords
   */
  private final double trainingRatio;
  /**
   * Whether or not the final output should print the words
   */
  private final boolean printWords;

  /*
   * Tag separator...
   */
  // private static final String tagSeparator = "_";

  // intended to be a standalone program, not a class
  private CountClosedTags(Properties props) {
    String tagList = props.getProperty(CLOSED_TAGS_PROPERTY);
    if (tagList != null) {
      closedTags = new TreeSet<>();
      String[] pieces = tagList.split("\\s+");
      Collections.addAll(closedTags, pieces);
    } else {
      closedTags = null;
    }

    if (props.containsKey(TRAINING_RATIO_PROPERTY)) {
      trainingRatio =
        Double.valueOf(props.getProperty(TRAINING_RATIO_PROPERTY));
    } else {
      trainingRatio = DEFAULT_TRAINING_RATIO;
    }

    printWords = Boolean.valueOf(props.getProperty(PRINT_WORDS_PROPERTY,
                                                   "false"));
  }

  /**
   * Count how many sentences there are in filename.
   */
  private static int countSentences(TaggedFileRecord file) {
    int count = 0;
    for (List<TaggedWord> ignored : file.reader())
      ++count;
    return count;
  }

  /**
   * Given a line, split it into tagged words and add each word to
   * the given tagWordMap.
   */
  private void addTaggedWords(List<TaggedWord> line,
                              Map<String, Set<String>> tagWordMap) {
    for (TaggedWord taggedWord : line) {
      String word = taggedWord.word();
      String tag = taggedWord.tag();
      if (closedTags == null || closedTags.contains(tag)) {
        if (!tagWordMap.containsKey(tag)) {
          tagWordMap.put(tag, new TreeSet<>());
        }
        tagWordMap.get(tag).add(word);
      }
    }
  }

  /**
   * Count trainingRatio of the sentences for both trainingWords and
   * allWords, and count the rest for just allWords
   */
  private void countTrainingTags(TaggedFileRecord file) {
    int sentences = countSentences(file);
    int trainSentences = (int) (sentences * trainingRatio);
    TaggedFileReader reader = file.reader();
    List<TaggedWord> line;
    for (int i = 0; i < trainSentences && reader.hasNext(); ++i) {
      line = reader.next();
      addTaggedWords(line, trainingWords);
      addTaggedWords(line, allWords);
    }
    while (reader.hasNext()) {
      line = reader.next();
      addTaggedWords(line, allWords);
    }
  }

  /**
   * Count all the words in the given file for just allWords
   */
  private void countTestTags(TaggedFileRecord file) {
    for (List<TaggedWord> line : file.reader()) {
      addTaggedWords(line, allWords);
    }
  }

  /**
   * Print out the results found
   */
  private void report() {
    List<String> successfulTags = new ArrayList<>();
    Set<String> tags = new TreeSet<>();
    tags.addAll(allWords.keySet());
    tags.addAll(trainingWords.keySet());
    if (closedTags != null)
      tags.addAll(closedTags);
    for (String tag : tags) {
      int numTraining = (trainingWords.containsKey(tag) ?
                         trainingWords.get(tag).size() : 0);
      int numTotal = (allWords.containsKey(tag) ?
                      allWords.get(tag).size() : 0);
      if (numTraining == numTotal && numTraining > 0)
        successfulTags.add(tag);
      System.out.println(tag + ' ' + numTraining + ' ' + numTotal);
      if (printWords) {
        Set<String> trainingSet = trainingWords.get(tag);
        if (trainingSet == null)
          trainingSet = Collections.emptySet();
        Set<String> allSet = allWords.get(tag);
        for (String word : trainingSet) {
          System.out.print(' ' + word);
        }
        if (trainingSet.size() < allSet.size()) {
          System.out.println();
          System.out.print(" *");
          for (String word : allWords.get(tag)) {
            if (!trainingSet.contains(word)) {
              System.out.print(' ' + word);
            }
          }
        }
        System.out.println();
      }
    }
    System.out.println(successfulTags);
  }

  private static final String TEST_FILE_PROPERTY = "testFile";
  private static final String TRAIN_FILE_PROPERTY = "trainFile";
  private static final String CLOSED_TAGS_PROPERTY = "closedTags";
  private static final String TRAINING_RATIO_PROPERTY = "trainingRatio";
  private static final String PRINT_WORDS_PROPERTY = "printWords";

  private static final Set<String> knownArgs =
    Generics.newHashSet(Arrays.asList(TEST_FILE_PROPERTY,
                                      TRAIN_FILE_PROPERTY,
                                      CLOSED_TAGS_PROPERTY,
                                      TRAINING_RATIO_PROPERTY,
                                      PRINT_WORDS_PROPERTY,
                                      TaggerConfig.ENCODING_PROPERTY,
                                      TaggerConfig.TAG_SEPARATOR_PROPERTY));

  private static void help(String error) {
    if (error != null && ! error.isEmpty()) {
      log.info(error);
    }
    System.exit(2);
  }

  private static void checkArgs(Properties props) {
    if (!props.containsKey(TRAIN_FILE_PROPERTY)) {
      help("No " + TRAIN_FILE_PROPERTY + " specified");
    }
    for (String arg : props.stringPropertyNames()) {
      if (!knownArgs.contains(arg))
        help("Unknown arg " + arg);
    }
  }

  public static void main(String[] args) throws Exception {
    System.setOut(new PrintStream(System.out, true, "UTF-8"));
    System.setErr(new PrintStream(System.err, true, "UTF-8"));

    Properties config = StringUtils.argsToProperties(args);
    checkArgs(config);

    CountClosedTags cct = new CountClosedTags(config);
    String trainFiles = config.getProperty(TRAIN_FILE_PROPERTY);
    String testFiles = config.getProperty(TEST_FILE_PROPERTY);
    List<TaggedFileRecord> files =
      TaggedFileRecord.createRecords(config, trainFiles);
    for (TaggedFileRecord file : files) {
      cct.countTrainingTags(file);
    }
    if (testFiles != null) {
      files = TaggedFileRecord.createRecords(config, testFiles);
      for (TaggedFileRecord file : files) {
        cct.countTestTags(file);
      }
    }
    cct.report();
  }

}
