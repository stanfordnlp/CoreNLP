package edu.stanford.nlp.parser.lexparser; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.io.EncodingPrintWriter;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.WordTag;
import edu.stanford.nlp.ling.Tag;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;

/**
 * Stores, trains, and scores with an unknown word model.  A couple
 * of filters deterministically force rewrites for certain proper
 * nouns, dates, and cardinal and ordinal numbers; when none of these
 * filters are met, either the distribution of terminals with the same
 * first character is used, or Good-Turing smoothing is used. Although
 * this is developed for Chinese, the training and storage methods
 * could be used cross-linguistically.
 *
 * @author Roger Levy
 */
public class ChineseUnknownWordModel extends BaseUnknownWordModel  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ChineseUnknownWordModel.class);

  private static final String encoding = "GB18030"; // used only for debugging

  private final boolean useUnicodeType;


  /* These strings are stored in ascii-type Unicode encoding.  To
   * edit them, either use the Unicode codes or use native2ascii or a
   * similar program to convert the file into a Chinese encoding, then
   * convert back. */
  private static final String numberMatch = ".*[0-9\uff10-\uff19\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d\u5341\u767e\u5343\u4e07\u4ebf\u96F6\u3007\u25cb\u25ef].*";
  private static final String dateMatch = numberMatch + "[\u5e74\u6708\u65e5\u53f7]";
  private static final String ordinalMatch = "\u7b2c.*";
  // uses midDot characters as one clue of being proper name
  private static final String properNameMatch = ".*[\u00b7\u0387\u2022\u2024\u2027\u2219\u22C5\u30FB].*";

  private final Set<String> seenFirst;


  public ChineseUnknownWordModel(Options op, Lexicon lex,
                                 Index<String> wordIndex,
                                 Index<String> tagIndex,
                                 ClassicCounter<IntTaggedWord> unSeenCounter,
                                 Map<Label,ClassicCounter<String>> tagHash,
                                 Map<String,Float> unknownGT,
                                 boolean useGT,
                                 Set<String> seenFirst) {
    super(op, lex, wordIndex, tagIndex,
          unSeenCounter, tagHash, unknownGT, null);
    this.useFirst = !useGT;
    this.useGT = useGT;
    this.useUnicodeType = op.lexOptions.useUnicodeType;
    this.seenFirst = seenFirst;
  }

  /**
   * This constructor creates an UWM with empty data structures.  Only
   * use if loading in the data separately, such as by reading in text
   * lines containing the data.
   * TODO: would need to set useGT correctly if you saved a model with
   * useGT and then wanted to recover it from text.
   */
  public ChineseUnknownWordModel(Options op, Lexicon lex,
                                 Index<String> wordIndex,
                                 Index<String> tagIndex) {
    this(op, lex, wordIndex, tagIndex,
            new ClassicCounter<>(),
         Generics.<Label,ClassicCounter<String>>newHashMap(),
         Generics.<String,Float>newHashMap(),
         false, Generics.<String>newHashSet());
  }

  @Override
  public float score(IntTaggedWord itw, String word) {
    // Label tagL = itw.tagLabel();
    // String tag = tagL.value();
    String tag = itw.tagString(tagIndex);
    Label tagL = new Tag(tag);

    float logProb;

    if (VERBOSE) EncodingPrintWriter.out.println("Scoring unknown word |" + word + "| with tag " + tag, encoding);

    if (word.matches(dateMatch)) {
      //EncodingPrintWriter.out.println("Date match for " + word,encoding);
      if (tag.equals("NT")) {
        logProb = 0.0f;
      } else {
        logProb = Float.NEGATIVE_INFINITY;
      }
    } else if (word.matches(numberMatch)) {
      //EncodingPrintWriter.out.println("Number match for " + word,encoding);
      if (tag.equals("CD") && (!word.matches(ordinalMatch))) {
        logProb = 0.0f;
      } else if (tag.equals("OD") && word.matches(ordinalMatch)) {
        logProb = 0.0f;
      } else {
        logProb = Float.NEGATIVE_INFINITY;
      }
    } else if (word.matches(properNameMatch)) {
      //EncodingPrintWriter.out.println("Proper name match for " + word,encoding);
      if (tag.equals("NR")) {
        logProb = 0.0f;
      } else {
        logProb = Float.NEGATIVE_INFINITY;
      }
    /* -------------
      // this didn't seem to work -- too categorical
      int type = Character.getType(word.charAt(0));
      // the below may not normalize probs over options, but is probably okay
      if (type == Character.START_PUNCTUATION) {
        if (tag.equals("PU-LPAREN") || tag.equals("PU-PAREN") ||
            tag.equals("PU-LQUOTE") || tag.equals("PU-QUOTE") ||
            tag.equals("PU")) {
          // if (VERBOSE) log.info("ChineseUWM: unknown L Punc");
          logProb = 0.0f;
        } else {
          logProb = Float.NEGATIVE_INFINITY;
        }
      } else if (type == Character.END_PUNCTUATION) {
        if (tag.equals("PU-RPAREN") || tag.equals("PU-PAREN") ||
            tag.equals("PU-RQUOTE") || tag.equals("PU-QUOTE") ||
            tag.equals("PU")) {
          // if (VERBOSE) log.info("ChineseUWM: unknown R Punc");
          logProb = 0.0f;
        } else {
          logProb = Float.NEGATIVE_INFINITY;
        }
      } else {
        if (tag.equals("PU-OTHER") || tag.equals("PU-ENDSENT") ||
            tag.equals("PU")) {
          // if (VERBOSE) log.info("ChineseUWM: unknown O Punc");
          logProb = 0.0f;
        } else {
          logProb = Float.NEGATIVE_INFINITY;
        }
      }
    ------------- */
    } else {
      first:
        if (useFirst) {
          String first = word.substring(0, 1);
          if (useUnicodeType) {
            char ch = word.charAt(0);
            int type = Character.getType(ch);
            if (type != Character.OTHER_LETTER) {
              // standard Chinese characters are of type "OTHER_LETTER"!!
              first = Integer.toString(type);
            }
          }
          if ( ! seenFirst.contains(first)) {
            if (useGT) {
              logProb = scoreGT(tag);
              break first;
            } else {
              first = unknown;
            }
          }

          /* get the Counter of terminal rewrites for the relevant tag */
          ClassicCounter<String> wordProbs = tagHash.get(tagL);

          /* if the proposed tag has never been seen before, issue a
             warning and return probability 0. */
          if (wordProbs == null) {
            if (VERBOSE) log.info("Warning: proposed tag is unseen in training data!");
            logProb = Float.NEGATIVE_INFINITY;
          } else if (wordProbs.containsKey(first)) {
            logProb = (float) wordProbs.getCount(first);
          } else {
            logProb = (float) wordProbs.getCount(unknown);
          }
        } else if (useGT) {
          logProb = scoreGT(tag);
        } else {
          if (VERBOSE) log.info("Warning: no unknown word model in place!\nGiving the combination " + word + " " + tag + " zero probability.");
          logProb = Float.NEGATIVE_INFINITY; // should never get this!
        }
    }

    if (VERBOSE) EncodingPrintWriter.out.println("Unknown word estimate for " + word + " as " + tag + ": " + logProb, encoding);
    return logProb;
  }



  public static void main(String[] args) {
    System.out.println("Testing unknown matching");
    String s = "\u5218\u00b7\u9769\u547d";
    if (s.matches(properNameMatch)) {
      System.out.println("hooray names!");
    } else {
      System.out.println("Uh-oh names!");
    }
    String s1 = "\uff13\uff10\uff10\uff10";
    if (s1.matches(numberMatch)) {
      System.out.println("hooray numbers!");
    } else {
      System.out.println("Uh-oh numbers!");
    }
    String s11 = "\u767e\u5206\u4e4b\u56db\u5341\u4e09\u70b9\u4e8c";
    if (s11.matches(numberMatch)) {
      System.out.println("hooray numbers!");
    } else {
      System.out.println("Uh-oh numbers!");
    }
    String s12 = "\u767e\u5206\u4e4b\u4e09\u5341\u516b\u70b9\u516d";
    if (s12.matches(numberMatch)) {
      System.out.println("hooray numbers!");
    } else {
      System.out.println("Uh-oh numbers!");
    }
    String s2 = "\u4e09\u6708";
    if (s2.matches(dateMatch)) {
      System.out.println("hooray dates!");
    } else {
      System.out.println("Uh-oh dates!");
    }

    System.out.println("Testing tagged word");
    ClassicCounter<TaggedWord> c = new ClassicCounter<>();
    TaggedWord tw1 = new TaggedWord("w", "t");
    c.incrementCount(tw1);
    TaggedWord tw2 = new TaggedWord("w", "t2");
    System.out.println(c.containsKey(tw2));
    System.out.println(tw1.equals(tw2));

    WordTag wt1 = toWordTag(tw1);
    WordTag wt2 = toWordTag(tw2);
    WordTag wt3 = new WordTag("w", "t2");
    System.out.println(wt1.equals(wt2));
    System.out.println(wt2.equals(wt3));
  }

  private static WordTag toWordTag(TaggedWord tw) {
    return new WordTag(tw.word(), tw.tag());
  }

  private static final long serialVersionUID = 221L;


  @Override
  public String getSignature(String word, int loc) {
    throw new UnsupportedOperationException();
  }

}

