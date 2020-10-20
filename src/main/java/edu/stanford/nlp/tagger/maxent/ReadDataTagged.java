/*
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Trustees of Leland Stanford Junior University<p>
 */
package edu.stanford.nlp.tagger.maxent; 

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.WordTag;
import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.tagger.common.Tagger;
import edu.stanford.nlp.tagger.io.TaggedFileReader;
import edu.stanford.nlp.tagger.io.TaggedFileRecord;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.logging.Redwood;


/**
 * Reads tagged data from a file and creates a dictionary.
 * The tagged data has to be whitespace-separated items, with the word and
 * tag split off by a delimiter character, which is found as the last instance
 * of the delimiter character in the item.
 *
 * @author Kristina Toutanova
 * @version 1.0
 */
public class ReadDataTagged  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(ReadDataTagged.class);

  private final ArrayList<DataWordTag> v = new ArrayList<>();
  private int numElements; // = 0;
  private int totalSentences; // = 0;
  private int totalWords; // = 0;
  private final PairsHolder pairs;
  private final MaxentTagger maxentTagger;

  //TODO: make a class DataHolder that holds the dict, tags, pairs, etc, for tagger and pass it around

  protected ReadDataTagged(TaggerConfig config, MaxentTagger maxentTagger,
                           PairsHolder pairs) {
    this.maxentTagger = maxentTagger;
    this.pairs = pairs;
    List<TaggedFileRecord> fileRecords = TaggedFileRecord.createRecords(config, config.getFile());
    Map<String, IntCounter<String>> wordTagCounts = Generics.newHashMap();
    for (TaggedFileRecord record : fileRecords) {
      loadFile(record.reader(), wordTagCounts);
    }
    // By counting the words and then filling the Dictionary, we can
    // make it so there are no calls that mutate the Dictionary or its
    // TagCount objects later
    maxentTagger.dict.fillWordTagCounts(wordTagCounts);
  }


  /** Frees the memory that is stored in this object by dropping the word-tag data.
   */
  void release() {
    v.clear();
  }


  DataWordTag get(int index) {
    return v.get(index);
  }

  private void loadFile(TaggedFileReader reader, Map<String, IntCounter<String>> wordTagCounts) {
    log.info("Loading tagged words from " + reader.filename());

    ArrayList<String> words = new ArrayList<>();
    ArrayList<String> tags = new ArrayList<>();
    int numSentences = 0;
    int numWords = 0;
    int maxLen = Integer.MIN_VALUE;
    int minLen = Integer.MAX_VALUE;

    for (List<TaggedWord> sentence : reader) {
      if (maxentTagger.wordFunction != null) {
        List<TaggedWord> newSentence =
                new ArrayList<>(sentence.size());
        for (TaggedWord word : sentence) {
          TaggedWord newWord =
            new TaggedWord(maxentTagger.wordFunction.apply(word.word()),
                           word.tag());
          newSentence.add(newWord);
        }
        sentence = newSentence;
      }
      for (TaggedWord tw : sentence) {
        if (tw != null) {
          words.add(tw.word());
          tags.add(tw.tag());
          if ( ! maxentTagger.tagTokens.containsKey(tw.tag())) {
            maxentTagger.tagTokens.put(tw.tag(), Generics.newHashSet());
          }
          maxentTagger.tagTokens.get(tw.tag()).add(tw.word());
        }
      }
      if (sentence.size() > maxLen) { maxLen = sentence.size(); }
      if (sentence.size() < minLen) { minLen = sentence.size(); }
      words.add(Tagger.EOS_WORD);
      tags.add(Tagger.EOS_TAG);
      numElements = numElements + sentence.size() + 1;
      // iterate over the words in the sentence
      for (int i = 0; i < sentence.size() + 1; i++) {
        History h = new History(totalWords + totalSentences,
                                totalWords + totalSentences + sentence.size(),
                                totalWords + totalSentences + i,
                                pairs, maxentTagger.extractors);
        String tag = tags.get(i);
        String word = words.get(i);
        pairs.add(new WordTag(word,tag));
        int y = maxentTagger.addTag(tag);
        DataWordTag dat = new DataWordTag(h, y, tag);
        v.add(dat);

        IntCounter<String> tagCounts = wordTagCounts.get(word);
        if (tagCounts == null) {
          tagCounts = new IntCounter<>();
          wordTagCounts.put(word, tagCounts);
        }
        tagCounts.incrementCount(tag, 1);
      }
      totalSentences++;
      totalWords += sentence.size();
      numSentences++;
      numWords += sentence.size();
      words.clear();
      tags.clear();
      // if ((numSentences % 100000) == 0) log.info("Read " + numSentences + " sentences, min " + minLen + " words, max " + maxLen + " words ... [still reading]");
    }

    log.info("Read " + numWords + " words and " + numSentences + " sentences (min " + minLen +
            " words, max " + maxLen + " words).");
  }


  /** Returns the number of tokens in the data read, which is the number of words
   *  plus one end sentence token per sentence.
   *  @return The number of tokens in the data
   */
  public int getSize() {
    return numElements;
  }

}
