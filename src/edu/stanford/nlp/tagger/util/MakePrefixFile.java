package edu.stanford.nlp.tagger.util; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.tagger.maxent.TaggerConfig;
import edu.stanford.nlp.tagger.io.TaggedFileRecord;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

import java.util.List;
import java.util.Properties;
import java.util.Random;

/**
 * Takes a tagger data file of any format readable by the tagger and
 * outputs a new file containing tagged sentences which are prefixes
 * of the original data.  The prefixes are of random length.  If the
 * -fullSentence parameter is true, the original sentence is output
 * after each prefix.
 * <br>
 * Input is taken from the tagger file described in "input".  Output
 * goes to stdout.
 *
 * @author John Bauer
 */
public class MakePrefixFile  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(MakePrefixFile.class);

  private MakePrefixFile() { } // static main only

  public static void main(String[] args) {
    Properties config = StringUtils.argsToProperties(args);
    log.info(config);

    boolean fullSentence = PropertiesUtils.getBool(config, "fullSentence", false);

    Random random = new Random();
    String tagSeparator = config.getProperty("tagSeparator", TaggerConfig.TAG_SEPARATOR);

    TaggedFileRecord record = TaggedFileRecord.createRecord(config, config.getProperty("input"));
    for (List<TaggedWord> sentence : record.reader()) {
      int len = random.nextInt(sentence.size()) + 1;
      System.out.println(SentenceUtils.listToString(sentence.subList(0, len), false, tagSeparator));
      if (fullSentence) {
        System.out.println(SentenceUtils.listToString(sentence, false, tagSeparator));
      }
    }
  }

  
}
