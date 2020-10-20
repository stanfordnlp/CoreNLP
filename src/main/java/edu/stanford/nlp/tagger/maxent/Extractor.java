/*
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Stanford University<p>
 */

package edu.stanford.nlp.tagger.maxent; 

import java.io.Serializable;

import edu.stanford.nlp.util.StringUtils;


/**
 * This class serves as the base class for classes which extract relevant
 * information from a history to give it to the features. Every feature has
 * an associated extractor or maybe more.  GlobalHolder keeps all the
 * extractors; two histories are considered equal if all extractors return
 * equal values for them.  The main functionality of the Extractors is
 * provided by the method extract which takes a History as an argument.
 * The Extractor looks at the history and takes out something important for
 * the features - e.g. specific words and tags at specific positions or
 * some function of the History. The histories are effectively vectors
 * of values, with each dimension being the output of some extractor.
 * <p>
 * New extractors are created in either ExtractorFrames or
 * ExtractorFramesRare; those are the places you want to consider
 * adding your new extractor.  For a new Extractor, typically the things
 * that you have to define are:
 * <ul>
 * <li>leftContext() and/or rightContext() if the extractor uses the tag
 * sequence to the left or right (so that dynamic programming will be done
 * correctly.
 * <li>isLocal() Return true iff the function is only of the current word
 * (for efficiency)
 * <li>isDynamic() Return true if a function of any tags (for efficiency)
 * <li>extract(History, PairsHolder) The actual function that returns the
 * value for the feature.
 * </ul>
 * <p>
 * Note that some extractors can be reused across multiple taggers,
 * but many cannot.  Any extractor that uses information from the
 * tagger such as its dictionary, for example, cannot.  For the
 * moment, some of the extractors in ExtractorFrames and
 * ExtractorFramesRare are static; those are all reusable at the
 * moment, but if you change them in any way to make them not
 * reusable, make sure to change the way they are constructed as well.
 *
 * @author Kristina Toutanova
 * @author Christoper Manning
 * @version 1.0
 */
public class Extractor implements Serializable  {


  // /** A logger for this class */
  // private static final Redwood.RedwoodChannels log = Redwood.channels(Extractor.class);

  private static final long serialVersionUID = -4694133872973560083L;

  static final String zeroSt = "0";

  final int position;
  private final boolean isTag;

  public Extractor() {
    this(Integer.MAX_VALUE, false);
  }

  public static final Extractor[] EMPTY_EXTRACTOR_ARRAY = new Extractor[0];

  /**
   * This constructor creates an extractor which extracts either the tag or
   * the word from position position in the history.
   *
   * @param position The position of the thing to be extracted. This is
   *                 relative to the current word. For example, position 0
   *                 will be the current word, -1 will be
   *                 the word before +1 will be the word after, etc.
   * @param isTag    If true this means that the POS tag is extracted from
   *                 position, otherwise the word is extracted.
   */
  protected Extractor(int position, boolean isTag) {
    this.position = position;
    this.isTag = isTag;
  }

  /**
   * Subclasses should override this method and keep only the data
   * they want about the tagger.  Note that such data should also be
   * declared "transient" if it is already available in the tagger.
   * This is because, when we save the tagger to disk, we do so by
   * writing out objects, and there is no need to write the same
   * object more than once.  setGlobalHolder will be called both after
   * construction when building a new tag and when loading existing
   * taggers from disk, so the same data will available then as well.
   */
  protected void setGlobalHolder(MaxentTagger tagger) {}


  /** This evaluates any precondition for a feature being applicable based
   *  on a certain tag. It returns true if the feature is applicable.
   *  By default an Extractor is applicable everywhere, but some
   *  subclasses limit application.
   *
   *  @param tag The possible tag that the feature will be generated for
   *  @return Whether the feature extractor is applicable (true) or not (false)
   */
  @SuppressWarnings({"UnusedDeclaration"})
  public boolean precondition(String tag) {
    return true;
  }


  /**
   * @return the number of positions to the left the extractor looks at (only tags, because words are fixed.)
   */
  public int leftContext() {
    if (isTag) {
      if (position < 0) {
        return -position;
      }
    }

    return 0;
  }


  /**
   * @return the number of positions to the right the extractor looks at (only tags, because words are fixed.)
   */
  public int rightContext() {
    if (isTag) {
      if (position > 0) {
        return position;
      }
    }

    return 0;
  }

  // CDM May 2007: This feature is currently never used. Maybe we should
  // change things so it is, and each feature template has a threshold, but
  // need to then work out what a TaggerFeature is and whether we should still
  // be using one of those to index with.
  // At present real threshold check happens in TaggerExperiments with
  // the populated(int, int) method.
  //  public boolean isPopulated(TaggerFeature f) {
  //    return (f.indexedValues.length > GlobalHolder.minFeatureThresh);
  //  }

  /** Subclasses should only override the two argument version
   *  of this method.
   *
   *  @param h The history to extract from
   *  @return The feature value
   */
  final String extract(History h) {
    return extract(h, h.pairs);
  }

  /**
   * @return Returns true if extractor is a function of POS tags; if it returns false,
   * features are pre-computed.
   */
  public boolean isDynamic() {
    return isTag;
  }

  /**
   * @return Returns true if extractor is not a function of POS tags, and only
   * depends on current word.
   */
  public boolean isLocal() {
    return !isTag && position == 0;
  }

  String extract(History h, PairsHolder pH) {
    return isTag ? pH.getTag(h, position) : pH.getWord(h, position);
  }

  @SuppressWarnings({"MethodMayBeStatic"})
  String extractLV(History h, PairsHolder pH) {
    // should extract last verbal word and also the current word
    int start = h.start;
    String lastverb = "NA";
    int current = h.current;
    int index = current - 1;
    while (index >= start) {
      String tag = pH.getTag(index);
      if (tag.startsWith("VB")) {
        lastverb = pH.getWord(index);
        break;
      }
      if (tag.startsWith(",")) {
        break;
      }
      index--;
    }
    return lastverb;
  }

  String extractLV(History h, PairsHolder pH, int bound) {
    // should extract last verbal word and also the current word
    int start = h.start;
    String lastverb = "NA";
    int current = h.current;
    int index = current - 1;
    while ((index >= start) && (index >= current - bound)) {
      String tag = pH.getTag(index);
      if (tag.startsWith("VB")) {
        lastverb = pH.getWord(index);
        break;
      }
      if (tag.startsWith(",")) {
        break;
      }
      index--;
    }
    return lastverb;
  }


  // By default the bound is ignored, but a few subclasses make use of it.
  @SuppressWarnings({"UnusedDeclaration"})
  String extract(History h, PairsHolder pH, int bound) {
    return extract(h, pH);
  }


  @Override
  public String toString() {
    String cl = getClass().getName();
    int ind = cl.lastIndexOf('.');
    // MAX_VALUE is the default value and means we aren't using these two arguments
    String args = (position == Integer.MAX_VALUE) ? "": (position + "," + (isTag ? "tag" : "word"));
    return cl.substring(ind + 1) + '(' + args + ')';
  }


  /** This is used for argument parsing in arch variable.
   *  It can extract from a comma separated values argument list.
   *  Values can be quoted with double quotes (with a second double quote as double quote escape char)
   *  like in a regular CSV file. It assumes the input format is "name(arg,arg,arg)".
   *
   *  @param str arch variable component input
   *  @param num Number of argument. Numbers are 1-indexed (i.e., start from 1 not 0)
   *  @return The parenthesized String, or null if none.
   */
  static String getParenthesizedArg(String str, int num) {
    int left = str.indexOf('(');
    int right = str.lastIndexOf(')');
    if (left < 0 || right <= left) {
      throw new IllegalArgumentException("getParenthesizedArg: Bad format String: " + str);
    }
    String argStr = str.substring(left + 1, right);
    String[] args = StringUtils.splitOnCharWithQuoting(argStr, ',', '"', '"');
    // log.info("getParenthesizedArg split " + str + " into " + args.length + " pieces; returning number " + num);
    // for (int i = 0; i < args.length; i++) {
    //   log.info("  " + args[i]);
    // }
    num--;
    if (args.length <= num || num < 0) {
      return null;
    }
    return args[num];
  }

  /** This is used for argument parsing in arch variable.
   *  It can extract a comma separated argument.
   *  Assumes the input format is "name(arg,arg,arg)", with possible
   *  spaces around the parentheses and comma(s).
   *
   *  @param str arch variable component input
   *  @param num Number of argument
   *  @return The int value of the arg or 0 if missing or empty
   */
  @SuppressWarnings("ConstantConditions")
  static int getParenthesizedNum(String str, int num) {
    String arg = getParenthesizedArg(str, num);
    int ans = 0;
    try {
      ans = Integer.parseInt(arg);
    } catch (NumberFormatException | ArrayIndexOutOfBoundsException nfe) {
      // just leave ans as 0
    }
    return ans;
  }

}
