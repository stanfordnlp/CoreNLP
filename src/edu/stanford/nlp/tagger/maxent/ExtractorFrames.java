//ExtractorFrames -- StanfordMaxEnt, A Maximum Entropy Toolkit
//Copyright (c) 2002-2011 Leland Stanford Junior University


//This program is free software; you can redistribute it and/or
//modify it under the terms of the GNU General Public License
//as published by the Free Software Foundation; either version 2
//of the License, or (at your option) any later version.

//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.

//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

//For more information, bug reports, fixes, contact:
//Christopher Manning
//Dept of Computer Science, Gates 1A
//Stanford CA 94305-9010
//USA
//    Support/Questions: java-nlp-user@lists.stanford.edu
//    Licensing: java-nlp-support@lists.stanford.edu
//http://www-nlp.stanford.edu/software/tagger.shtml


package edu.stanford.nlp.tagger.maxent;

import edu.stanford.nlp.process.WordShapeClassifier;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

import java.util.*;


/**
 * This class contains the basic feature extractors used for all words and
 * tag sequences (and interaction terms) for the MaxentTagger, but not the
 * feature extractors explicitly targeting generalization for rare or unknown
 * words.
 *
 * The following options are supported:
 * <table>
 * <caption>Supported feature options for the maxent tagger</caption>
 * <tr><td>Name</td><td>Args</td><td>Effect</td></tr>
 * <tr><td>words</td><td>begin, end</td>
 *     <td>Individual features for words begin ... end.
 *     If just one argument words(-2) is given, then end is taken as 0. If
 *     begin is not less than or equal to end, no features are made.</td></tr>
 * <tr><td>tags</td><td>begin, end</td>
 *     <td>Individual features for tags begin ... end.
 *     If just one argument words(-2) is given, then end is taken as 0. </td></tr>
 * <tr><td>biword</td><td>w1, w2</td>
 *     <td>One feature for the pair of words w1, w2</td></tr>
 * <tr><td>biwords</td><td>begin, end</td>
 *     <td>One feature for each sequential pair of words
 *         from begin to end</td></tr>
 * <tr><td>twoTags</td><td>t1, t2</td>
 *     <td>One feature for the pair of tags t1, t2</td></tr>
 * <tr><td>lowercasewords</td><td>begin, end</td>
 *     <td>One feature for each word begin ... end, lowercased</td></tr>
 * <tr><td>order</td><td>left, right</td>
 *     <td>A feature for tags left through 0 and a feature for
 *         tags 0 through right (not including 0).  Lower order left and right features are also added.
 *         This gets very expensive for higher order terms.</td></tr>
 * <tr><td>wordTag</td><td>w, t</td>
 *     <td>A feature combining word w and tag t.</td></tr>
 * <tr><td>wordTwoTags</td><td>w, t1, t2</td>
 *     <td>A feature combining word w and tags t1, t2.</td></tr>
 * <tr><td>threeTags</td><td>t1, t2, t3</td>
 *     <td>A feature combining tags t1, t2, t3.</td></tr>
 * <tr><td>vbn</td><td>length</td>
 *     <td>A feature that looks at the left length words for something that
 *         appears to be a VBN (in English) without looking at the actual tags.
 *         It is zeroeth order, as it does not look at the tag predictions.
 *         It also is never used, since it doesn't seem to help.</td></tr>
 * <tr><td>allwordshapes</td><td>left, right</td>
 *     <td>Word shape features, eg transform Foo5 into Xxx#
 *         (not exactly like that, but that general idea).
 *         Creates individual features for each word left ... right.
 *         Compare with the feature "wordshapes" in ExtractorFramesRare,
 *         which is only applied to rare words. Fairly English-specific.
 *         Slightly increases accuracy.</td></tr>
 * <tr><td>allunicodeshapes</td><td>left, right</td>
 *     <td>Same thing, but works for unicode characters more generally.</td></tr>
 * <tr><td>allwordshapeconjunction</td><td>left, right</td>
 *     <td>Instead of individual token word shape features, combines several
 *         word shapes into one feature.</td></tr>
 * <tr><td>allunicodeshapeconjunction</td><td>left, right</td>
 *     <td>Instead of individual token word shape features, combines several
 *         word shapes into one feature.</td></tr>
 * <tr><td>spanishauxiliaries</td><td></td>
 *     <td>Add feature detectors for Spanish auxiliaries.</td></tr>
 * <tr><td>extractor</td><td>classname</td>
 *     <td>Add a feature extractor by classname, which will be applied to all words.</td></tr>
 * </table>
 *
 * See {@link ExtractorFramesRare} for more options.
 * <br>
 * There are also macro features:
 * <br>
 * left3words = words(-1,1),order(2) <br>
 * left5words = words(-2,2),order(2) <br>
 * generic = words(-1,1),order(2),biwords(-1,0),wordTag(0,-1) <br>
 * bidirectional5words =
 *   words(-2,2),order(-2,2),twoTags(-1,1),
 *   wordTag(0,-1),wordTag(0,1),biwords(-1,1) <br>
 * bidirectional =
 *   words(-1,1),order(-2,2),twoTags(-1,1),
 *   wordTag(0,-1),wordTag(0,1),biwords(-1,1) <br>
 * german = some random stuff <br>
 * sighan2005 = some other random stuff <br>
 * The left3words architectures are faster, but slightly less
 * accurate, than the bidirectional architectures.
 * 'naacl2003unknowns' was our traditional set of unknown word
 * features, but you can now specify features more flexibility via the
 * various other supported keywords defined in {@link ExtractorFramesRare}.
 *
 * Note: All features are implicitly conjoined with the current tag.
 *
 * @author Kristina Toutanova
 * @author Michel Galley
 * @version 1.0
 */
public class ExtractorFrames  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(ExtractorFrames.class);

  // Used in various testing for properties of current word elsewhere
  static final Extractor cWord = new Extractor(0, false);

  /**
   * This class is not meant to be instantiated.
   */
  private ExtractorFrames() { }


  @SuppressWarnings("StatementWithEmptyBody")
  protected static Extractor[] getExtractorFrames(String arch) {
    // handle some traditional macro options
    // left3words: a simple trigram CMM tagger (similar to the baseline EMNLP 2000 tagger)
    // left5words: a simple trigram CMM tagger, like left3words, with 5 word context
    // generic: our standard multilingual CMM baseline

    arch = arch.replaceAll("left3words", "words(-1,1),order(2)");
    arch = arch.replaceAll("left5words", "words(-2,2),order(2)");
    arch = arch.replaceAll("generic", "words(-1,1),order(2),biwords(-1,0),wordTag(0,-1)");
    arch = arch.replaceAll("bidirectional5words", "words(-2,2),order(-2,2),twoTags(-1,1),wordTag(0,-1),wordTag(0,1),biwords(-1,1)");
    arch = arch.replaceAll("bidirectional", "words(-1,1),order(-2,2),twoTags(-1,1),wordTag(0,-1),wordTag(0,1),biwords(-1,1)");
    // There was an historical bidirectional "german" option which was: "words(-1,1),order(-2,1),wordTag(0,-1),biword(-1,-0)"
    // There was an historical left "sighan2005" option which was: "words(-2,2),order(2),tags(-2, -2)"

    ArrayList<Extractor> extrs = new ArrayList<>();
    List<String> args = StringUtils.valueSplit(arch, "[a-zA-Z0-9]*(?:\\([^)]*\\))?", "\\s*,\\s*");
    for (String arg : args) {
      if (arg.startsWith("words(")) {
        // non-sequence features with just a certain number of words to the
        // left and right; e.g., words(-2,2) or words(-2,-1)
        int lWindow = Extractor.getParenthesizedNum(arg, 1);
        int rWindow = Extractor.getParenthesizedNum(arg, 2);
        for (int i = lWindow; i <= rWindow; i++) {
          extrs.add(new Extractor(i, false));
        }
      } else if (arg.startsWith("tags(")) {
        // non-sequence features with just a certain number of words to the
        // left and right; e.g., tags(-2,2) or tags(-2,-1)
        int lWindow = Extractor.getParenthesizedNum(arg, 1);
        int rWindow = Extractor.getParenthesizedNum(arg, 2);
        for (int i = lWindow; i <= rWindow; i++) {
          // refuse to add a tag extractor at position 0 -- that's what we're predicting
          if (i != 0) {
            extrs.add(new Extractor(i, true));
          }
        }
      } else if (arg.startsWith("biwords(")) {
        // non-sequence features of word pairs.
        // biwords(-2,1) would give you 3 extractors for w-2w-1, w-1,w0, w0w1
        int lWindow = Extractor.getParenthesizedNum(arg, 1);
        int rWindow = Extractor.getParenthesizedNum(arg, 2);
        for (int i = lWindow; i < rWindow; i++) {
          extrs.add(new ExtractorTwoWords(i));
        }
      } else if (arg.startsWith("biword(")) {
        // non-sequence feature of a word pair.
        // biwords(-2,1) would give you 1 extractor for w-2, w+1
        int left = Extractor.getParenthesizedNum(arg, 1);
        int right = Extractor.getParenthesizedNum(arg, 2);
        extrs.add(new ExtractorTwoWords(left, right));
      } else if (arg.startsWith("twoTags(")) {
        // non-sequence feature of a tag pair.
        // twoTags(-2,1) would give you 1 extractor for t-2, t+1
        int left = Extractor.getParenthesizedNum(arg, 1);
        int right = Extractor.getParenthesizedNum(arg, 2);
        extrs.add(new ExtractorTwoTags(left, right));
      } else if (arg.startsWith("lowercasewords(")) {
        // non-sequence features with just a certain number of lowercase words
        // to the left and right
        int lWindow = Extractor.getParenthesizedNum(arg, 1);
        int rWindow = Extractor.getParenthesizedNum(arg, 2);
        for (int i = lWindow; i <= rWindow; i++) {
          extrs.add(new ExtractorWordLowerCase(i));
        }
      } else if (arg.startsWith("order(")) {
        // anything like order(2), order(-4), order(0,3), or
        // order(-2,1) are okay.
        int leftOrder = Extractor.getParenthesizedNum(arg, 1);
        int rightOrder = Extractor.getParenthesizedNum(arg, 2);
        if (leftOrder > 0) { leftOrder = -leftOrder; }
	if (rightOrder < 0) { throw new IllegalArgumentException("Right order must be non-negative, not " + rightOrder); }
        // cdm 2009: We only add successively higher order tag k-grams
        // ending adjacent to t0.  Adding lower order features at a distance
        // appears not to help (Dec 2009). But they can now be added with tags().

        for (int idx = leftOrder ; idx <= rightOrder; idx++) {
          if (idx == -1 || idx == 1) {
            extrs.add(new Extractor(idx, true));
          } else if (idx != 0) {
            extrs.add(new ExtractorContinuousTagConjunction(idx));
          }
          // do nothing if idx = 0. You can't use the  tag to infer itself!
        }
      } else if (arg.startsWith("wordTag(")) {
        // sequence feature of a word and a tag: wordTag(-1,1)
        int posW = Extractor.getParenthesizedNum(arg, 1);
        int posT = Extractor.getParenthesizedNum(arg, 2);
        extrs.add(new ExtractorWordTag(posW, posT));
      } else if (arg.startsWith("wordTwoTags(")) {
        int word = Extractor.getParenthesizedNum(arg, 1);
        int tag1 = Extractor.getParenthesizedNum(arg, 2);
        int tag2 = Extractor.getParenthesizedNum(arg, 3);
        extrs.add(new ExtractorWordTwoTags(word,tag1,tag2));
      } else if (arg.startsWith("threeTags(")) {
        int pos1 = Extractor.getParenthesizedNum(arg, 1);
        int pos2 = Extractor.getParenthesizedNum(arg, 2);
        int pos3 = Extractor.getParenthesizedNum(arg, 3);
        extrs.add(new ExtractorThreeTags(pos1,pos2,pos3));
      } else if (arg.startsWith("vbn(")) {
        int order = Extractor.getParenthesizedNum(arg, 1);
        extrs.add(new ExtractorVerbalVBNZero(order));
      } else if (arg.startsWith("allwordshapes(")) {
        int lWindow = Extractor.getParenthesizedNum(arg, 1);
        int rWindow = Extractor.getParenthesizedNum(arg, 2);
        String wsc = Extractor.getParenthesizedArg(arg, 3);
        if (wsc == null) {
          wsc = "chris2";
        }
        for (int i = lWindow; i <= rWindow; i++) {
          extrs.add(new ExtractorWordShapeClassifier(i, wsc));
        }
      } else if (arg.startsWith("allwordshapeconjunction(")) {
        int lWindow = Extractor.getParenthesizedNum(arg, 1);
        int rWindow = Extractor.getParenthesizedNum(arg, 2);
        String wsc = Extractor.getParenthesizedArg(arg, 3);
        if (wsc == null) {
          wsc = "chris2";
        }
        extrs.add(new ExtractorWordShapeConjunction(lWindow, rWindow, wsc));
      } else if (arg.startsWith("allunicodeshapes(")) {
        int lWindow = Extractor.getParenthesizedNum(arg, 1);
        int rWindow = Extractor.getParenthesizedNum(arg, 2);
        for (int i = lWindow; i <= rWindow; i++) {
          extrs.add(new ExtractorWordShapeClassifier(i, "chris4"));
        }
      } else if (arg.startsWith("allunicodeshapeconjunction(")) {
        int lWindow = Extractor.getParenthesizedNum(arg, 1);
        int rWindow = Extractor.getParenthesizedNum(arg, 2);
        extrs.add(new ExtractorWordShapeConjunction(lWindow, rWindow, "chris4"));
      } else if (arg.equalsIgnoreCase("spanishauxiliaries")) {
        extrs.add(new ExtractorSpanishAuxiliaryTag());
        extrs.add(new ExtractorSpanishSemiauxiliaryTag());
      } else if (arg.startsWith("extractor(")) {
        String className = Extractor.getParenthesizedArg(arg, 1);
        try {
          Extractor e = (Extractor) Class.forName(className).getDeclaredConstructor().newInstance();
          extrs.add(e);
        } catch (Exception e) {
          throw new RuntimeException("Couldn't create POS tagger extractor class " + className, e);
        }
      } else if (arg.equalsIgnoreCase("naacl2003unknowns") ||
                 arg.equalsIgnoreCase("lnaacl2003unknowns") ||
                 arg.equalsIgnoreCase("caselessnaacl2003unknowns") ||
                 arg.equalsIgnoreCase("naacl2003conjunctions") ||
                 arg.equalsIgnoreCase("frenchunknowns") ||
                 arg.equalsIgnoreCase("spanishunknowns") ||
                 arg.startsWith("wordshapes(") ||
                 arg.startsWith("wordshapeconjunction(") ||
                 arg.equalsIgnoreCase("motleyUnknown") ||
                 arg.startsWith("suffix(") ||
                 arg.startsWith("prefix(") ||
                 arg.startsWith("prefixsuffix") ||
                 arg.startsWith("capitalizationsuffix(") ||
                 arg.startsWith("distsim(") ||
                 arg.startsWith("distsimconjunction(") ||
                 arg.equalsIgnoreCase("lctagfeatures") ||
                 arg.startsWith("rareExtractor(") ||
                 arg.startsWith("unicodeshapes(") ||
                 arg.startsWith("chinesedictionaryfeatures(") ||
                 arg.startsWith("unicodeshapeconjunction(")) {
        // okay; known unknown keyword
      } else {
        log.info("Unrecognized ExtractorFrames identifier (ignored): " + arg);
      }
    } // end for
    return extrs.toArray(Extractor.EMPTY_EXTRACTOR_ARRAY);
  }


  /**
   * This extractor extracts a word and tag in conjunction.
   */
  static class ExtractorWordTag extends Extractor {

    private static final long serialVersionUID = 3L;

    private final int wordPosition;
    public ExtractorWordTag(int posW, int posT) {
      super(posT, true);
      wordPosition = posW;
    }

    @Override
    String extract(History h, PairsHolder pH) {
      return pH.getTag(h, position) + '!' + pH.getWord(h, wordPosition);
    }

    @Override
    public String toString() {
      return (getClass().getName() + "(w" + wordPosition +
              ",t" + position + ')');
    }
  }


  /**
   * The word in lower-cased version.
   * Always uses Locale.ENGLISH.
   */
  static class ExtractorWordLowerCase extends Extractor {

    private static final long serialVersionUID = -7847524200422095441L;

    public ExtractorWordLowerCase(int position) {
      super(position, false);
    }

    @Override
      String extract(History h, PairsHolder pH) {
      return pH.getWord(h, position).toLowerCase(Locale.ENGLISH);
    }

  }

  /**
   * The current word if it is capitalized, zero otherwise.
   * Always uses Locale.ENGLISH.
   */
  static class ExtractorCWordCapCase extends Extractor {

    private static final long serialVersionUID = -2393096135964969744L;

    @Override
      String extract(History h, PairsHolder pH) {
      String cw = pH.getWord(h, 0);
      String lk = cw.toLowerCase(Locale.ENGLISH);
      if (lk.equals(cw)) {
        return zeroSt;
      }
      return cw;
    }

    @Override public boolean isLocal() { return true; }
    @Override public boolean isDynamic() { return false; }
  }


  /**
   * This extractor extracts two words in conjunction.
   * The one argument constructor gives you leftPosition and
   * leftPosition+1, but with the two argument constructor,
   * they can be any pair of word positions.
   */
  static class ExtractorTwoWords extends Extractor {

    private static final long serialVersionUID = -1034112287022504917L;

    private final int leftPosition;
    private final int rightPosition;

    public ExtractorTwoWords(int leftPosition) {
      this(leftPosition, leftPosition+1);
    }

    public ExtractorTwoWords(int position1, int position2) {
      super(0, false);
      if (position1 > position2) {
        leftPosition = position1;
        rightPosition = position2;
      } else {
        leftPosition = position2;
        rightPosition = position1;
      }
    }

    @Override
      String extract(History h, PairsHolder pH) {
      // I ran a bunch of timing tests that seem to indicate it is
      // cheaper to simply add string + char + string than use a
      // StringBuilder or go through the StringBuildMemoizer -horatio
      return pH.getWord(h, leftPosition) + '!' + pH.getWord(h, rightPosition);
    }

    @Override public boolean isLocal() { return false; }

    // isDynamic --> false, but no need to override


    @Override
    public String toString() {
      return (getClass().getName() + "(w" + leftPosition +
              ",w" + rightPosition + ')');
    }
  }



  /**
   * This extractor extracts two tags in conjunction.
   * The one argument constructor gives you leftPosition and
   * leftPosition+1, but with the two argument constructor,
   * they can be any pair of tag positions.
   */
  static class ExtractorTwoTags extends Extractor {

    private static final long serialVersionUID = -7342144764725605134L;

    private final int leftPosition;
    private final int rightPosition;
    private final int leftContext, rightContext;

    public ExtractorTwoTags(int position1, int position2) {
      leftPosition = Math.min(position1, position2);
      rightPosition = Math.max(position1, position2);

      leftContext = -Math.min(leftPosition, 0);
      rightContext = Math.max(rightPosition, 0);
    }

    @Override
    public int rightContext() {
      return rightContext;
    }

    @Override
    public int leftContext() {
      return leftContext;
    }

    @Override
    String extract(History h, PairsHolder pH) {
      // I ran a bunch of timing tests that seem to indicate it is
      // cheaper to simply add string + char + string than use a
      // StringBuilder or go through the StringBuildMemoizer -horatio
      return pH.getTag(h, leftPosition) + '!' + pH.getTag(h, rightPosition);
    }

    @Override public boolean isLocal() { return false; }
    @Override public boolean isDynamic() { return true; }

    @Override
    public String toString() {
      return (getClass().getName() + "(t" + leftPosition +
              ",t" + rightPosition + ')');
    }
  }


  /**
   * This extractor extracts two words and a tag in conjunction.
   */
  static class ExtractorTwoWordsTag extends Extractor {

    private static final long serialVersionUID = 277004119652781188L;

    private final int leftWord, rightWord, tag;
    private final int rightContext, leftContext;

    public ExtractorTwoWordsTag(int leftWord, int rightWord, int tag) {
      this.leftWord = Math.min(leftWord, rightWord);
      this.rightWord = Math.max(leftWord, rightWord);
      this.tag = tag;

      this.rightContext = Math.max(tag, 0);
      this.leftContext = -Math.min(tag, 0);
    }

    @Override
    public int rightContext() {
      return rightContext;
    }

    @Override
    public int leftContext() {
      return leftContext;
    }

    @Override
      String extract(History h, PairsHolder pH) {
      return (pH.getWord(h, leftWord) + '!' + pH.getTag(h, tag) + '!' +
              pH.getWord(h, rightWord));
    }

    @Override public boolean isLocal() { return false; }
    @Override public boolean isDynamic() { return true; }

    @Override
    public String toString() {
      return (getClass().getName() + "(w" + leftWord +
              ",t" + tag + ",w" + rightWord + ')');
    }
  }



  /**
   * This extractor extracts several contiguous tags only on one side of position 0.
   * E.g., use constructor argument -3 for an order 3 predictor on the left.
   * isLocal=false, isDynamic=true (through super call)
   */
  static class ExtractorContinuousTagConjunction extends Extractor {

    private static final long serialVersionUID = 3;

    public ExtractorContinuousTagConjunction(int maxPosition) {
      super(maxPosition, true);
    }

    @Override
    String extract(History h, PairsHolder pH) {
      StringBuilder sb = new StringBuilder();
      if (position < 0) {
        for (int idx = position; idx < 0; idx++) {
          if (idx != position) {
            sb.append('!');
          }
          sb.append(pH.getTag(h, idx));
        }
      } else {
        for (int idx = position; idx > 0; idx--) {
          if (idx != position) {
            sb.append('!');
          }
          sb.append(pH.getTag(h, idx));
        }
      }
      return sb.toString();
    }

    @Override
    public String toString() {
      return "ExtractorContinuousTagConj(" + (position < 0 ? position + " ... -1": "1 ... " + position) + ')';
    }

  }


  /**
   * This extractor extracts three tags.
   */
  static class ExtractorThreeTags extends Extractor {

    private static final long serialVersionUID = 8563584394721620568L;

    private int position1;
    private int position2;
    private int position3;

    public ExtractorThreeTags(int position1, int position2, int position3) {
      // bubblesort them!
      int x;
      if (position1 > position2) {
        x = position2;
        position2 = position1;
        position1 = x;
      }
      if (position2 > position3) {
        x = position3;
        position3 = position2;
        position2 = x;
      }
      if (position1 > position2) {
        x = position2;
        position2 = position1;
        position1 = x;
      }
      this.position1 = position1;
      this.position2 = position2;
      this.position3 = position3;
    }

    @Override
      public int rightContext() {
      if (position3 > 0) {
        return position3;
      } else {
        return 0;
      }
    }

    @Override
      public int leftContext() {
      if (position1 < 0) {
        return -position1;
      } else {
        return 0;
      }
    }

    @Override
      String extract(History h, PairsHolder pH) {
      return pH.getTag(h, position1) + '!' + pH.getTag(h, position2) + '!' + pH.getTag(h, position3);
    }

    @Override public boolean isLocal() { return false; }
    @Override public boolean isDynamic() { return true; }

    @Override
    public String toString() {
      return (getClass().getName() + "(t" + position1 +
              ",t" + position2 + ",t" + position3 + ')');
    }
  }


  /**
   * This extractor extracts two tags and the a word in conjunction.
   */
  static class ExtractorWordTwoTags extends Extractor {

    private static final long serialVersionUID = -4942654091455804176L;

    // We sort so that position1 <= position2 and then rely on that.
    private int position1;
    private int position2;
    private int word;

    public ExtractorWordTwoTags(int word, int position1, int position2) {
      if (position1 < position2) {
        this.position1 = position1;
        this.position2 = position1;
      } else {
        this.position1 = position2;
        this.position2 = position1;
      }
      this.word = word;
    }

    @Override
      public int leftContext() {
      if (position1 < 0) {
        return  -position1;
      } else {
        return 0;
      }
    }

    @Override
      public int rightContext() {
      if (position2 > 0) {
        return position2;
      } else {
        return 0;
      }
    }

    @Override
      String extract(History h, PairsHolder pH) {
      return pH.getTag(h, position1) + '!' + pH.getWord(h, word) + '!' + pH.getTag(h, position2);
    }

    @Override public boolean isLocal() { return false; }
    @Override public boolean isDynamic() { return true; }

    @Override
    public String toString() {
      return (getClass().getName() + "(t" + position1 +
              ",t" + position2 + ",w" + word + ')');
    }
  }


} // end class ExtractorFrames


class ExtractorWordShapeClassifier extends Extractor {

  private final int wordShaper;
  private final String name;

  // This cache speeds things up a little bit.  I used
  // -Xrunhprof:cpu=samples,interval=1 when using the "distsim" tagger
  // on the training set to measure roughly how much time was spent in
  // this method.  I concluded that with the cache, 1.24% of the time
  // is spent here, and without the cache, 1.26% of the time is spent
  // here.  This is a very small savings, which would be even smaller
  // if we make the cache thread safe.  It turns out that, as written,
  // the cache is not thread safe for various reasons.  In particular,
  // it assumes only one wordshape classifier is ever used, which
  // might not be true even with just one tagger, and has an even
  // higher chance of not being true if there are multiple taggers.
  // Furthermore, access to the cache should really be synchronized
  // regardless.  The easiest solution is to comment out the cache and
  // note that if you want to bring it back, make it a map from wsc to
  // cache rather than just a single cache.  -- horatio
  //private static final Map<String, String> shapes =
  //  Generics.newHashMap();
  // --- should be:
  //private static final Map<String, Map<String, String>> ...

  ExtractorWordShapeClassifier(int position, String wsc) {
    super(position, false);
    wordShaper = WordShapeClassifier.lookupShaper(wsc);
    name = "ExtractorWordShapeClassifier(" + position+ ',' + wsc + ')';
  }

  @Override
  String extract(History h, PairsHolder pH) {
    String s = super.extract(h, pH);
    String shape = WordShapeClassifier.wordShape(s, wordShaper);
    return shape;
  }

  private static final long serialVersionUID = 101L;

  @Override
  public String toString() {
    return name;
  }

  @Override public boolean isLocal() { return position == 0; }
  @Override public boolean isDynamic() { return false; }
}


/**
 * This extractor extracts a conjunction of word shapes.
 */
class ExtractorWordShapeConjunction extends Extractor {

  private static final long serialVersionUID = -49L;

  private final int wordShaper;
  private final int left;
  private final int right;
  private final String name;

  ExtractorWordShapeConjunction(int left, int right, String wsc) {
    super();
    this.left = left;
    this.right = right;
    wordShaper = WordShapeClassifier.lookupShaper(wsc);
    name = "ExtractorWordShapeConjunction(" + left + ',' + right + ',' + wsc + ')';
  }

  @Override
  String extract(History h, PairsHolder pH) {
    StringBuilder sb = new StringBuilder();
    for (int j = left; j <= right; j++) {
      String s = pH.getWord(h, j);
      sb.append(WordShapeClassifier.wordShape(s, wordShaper));
      if (j < right) {
        sb.append('|');
      }
    }
    return sb.toString();
  }

  @Override
  public String toString() {
    return name;
  }

  @Override public boolean isLocal() { return false; }
  @Override public boolean isDynamic() { return false; }

}


/**
 * Extracts a boolean indicating whether the given word is preceded by
 * an auxiliary verb.
 */
class ExtractorSpanishAuxiliaryTag extends Extractor {

  private static final long serialVersionUID = -3352770856914897103L;

  public ExtractorSpanishAuxiliaryTag() {
    super(-1, true);
  }

  @Override
  String extract(History h, PairsHolder pH) {
    String tag = super.extract(h, pH);
    return tag.startsWith("va") ? "1" : "0";
  }

  @Override
  public String toString() {
    return "ExtractorSpanishAuxiliaryTag";
  }

}


/**
 * Extracts a boolean indicating whether the given word is preceded by
 * a semi-auxiliary verb.
 */
class ExtractorSpanishSemiauxiliaryTag extends Extractor {

  private static final long serialVersionUID = -164942945521643734L;

  public ExtractorSpanishSemiauxiliaryTag() {
    super(-1, true);
  }

  @Override
  String extract(History h, PairsHolder pH) {
    String tag = super.extract(h, pH);
    return tag.startsWith("vs") ? "1" : "0";
  }

  @Override
  public String toString() {
    return "ExtractorSpanishSemiauxiliaryTag";
  }

}
