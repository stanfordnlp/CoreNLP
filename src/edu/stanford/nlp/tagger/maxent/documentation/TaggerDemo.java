package edu.stanford.nlp.tagger.maxent.documentation; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.List;

import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;

public class TaggerDemo  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(TaggerDemo.class);

  private TaggerDemo() {}

  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      log.info("usage: java TaggerDemo modelFile fileToTag");
      return;
    }
    MaxentTagger tagger = new MaxentTagger(args[0]);
    List<List<HasWord>> sentences = MaxentTagger.tokenizeText(new BufferedReader(new FileReader(args[1])));
    for (List<HasWord> sentence : sentences) {
      List<TaggedWord> tSentence = tagger.tagSentence(sentence);
      System.out.println(SentenceUtils.listToString(tSentence, false));
    }
  }

}
