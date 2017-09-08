//ExtractorFrames -- StanfordMaxEnt, A Maximum Entropy Toolkit
//Copyright (c) 2002-2008 Leland Stanford Junior University


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


package old.edu.stanford.nlp.tagger.maxent;

import old.edu.stanford.nlp.util.StringBuildMemoizer;
import old.edu.stanford.nlp.util.StringUtils;

import java.util.*;


/**
 * The static eFrames contains
 * an array of all Extractors that are used to define
 * features. This is an important class for the tagger.
 * If you want to add a new Extractor, you should add it to the
 * array eFrames.
 *
 * @author Kristina Toutanova
 * @author Michel Galley
 * @version 1.0
 */
public class ExtractorFrames {

  // all features are implicitly conjoined with the current tag
  static final Extractor cWord = new Extractor(0, false);
  static final Extractor prevWord = new Extractor(-1, false);
  private static final Extractor prevTag = new Extractor(-1, true);
  private static final Extractor prevTwoTags = new ExtractorPrevTwoTags();
  private static final Extractor nextTwoTags = new ExtractorNextTwoTags();
  private static final Extractor prevNextTag = new ExtractorPrevTagNextTag();
  private static final Extractor prevTagWord = new ExtractorPrevTagWord();  // prev tag and current word!

  /*
  static final Extractor prevTwoTag =new Extractor(-2,true);
  static final Extractor prevThreeTags = new ExtractorPrevThreeTags();
  static final Extractor nextTwoTag = new Extractor(2,true);
  static final Extractor nextThreeTags = new ExtractorNextThreeTags();
  static final Extractor prevNextTagWord = new ExtractorPrevTagNextTagWord();
  static final Extractor nextTagWord = new ExtractorNextTagWord();
  static final Extractor nextNextWordClass = new ExtractorFollowingWClass(2);
  static final Extractor cWordCapCase = new ExtractorCWordCapCase();
  */

  private static final Extractor prevWord2 = new Extractor(-2,false);
  private static final Extractor prevTwoTag = new Extractor(-2,true);
  private static final Extractor prevThreeTags = new ExtractorPrevThreeTags();
  private static final Extractor nextWord = new Extractor(1, false);
  private static final Extractor nextWord2 = new Extractor(2,false);
  private static final Extractor nextTag = new Extractor(1, true);
  private static final Extractor nextTagWord = new ExtractorNextTagWord();

  private static final Extractor cWordNextWord = new ExtractorCWordNextWord();
  private static final Extractor cWordPrevWord = new ExtractorCWordPrevWord();


  // this config was used in the best NAACL 2003 cyclic dependency tagger
  private static final Extractor[] eFrames_bidirectional = {cWord,prevWord,nextWord,prevTag,
      nextTag,prevTwoTags,nextTwoTags,prevNextTag,prevTagWord,nextTagWord,
      cWordPrevWord,cWordNextWord};

  // features for 2005 SIGHAN tagger
  private static final Extractor[] eFrames_sighan2005 = { cWord, prevWord, prevWord2, nextWord, nextWord2, prevTag, prevTwoTag, prevTwoTags };

  // features for a not-language-particular CMM tagger
  private static final Extractor[] eFrames_generic ={ cWord, prevWord, nextWord,
      prevTag, prevTwoTags, prevTagWord, cWordPrevWord };


  // features for a german-language bidirectional tagger
  private static final Extractor[] eFrames_german ={ cWord, prevWord, nextWord, nextTag,
      prevTag, prevTwoTags, prevTagWord, cWordPrevWord };

  /**
   * This class is not meant to be instantiated.
   */
  private ExtractorFrames() {
  }


  @SuppressWarnings({"fallthrough"})
  protected static Extractor[] getExtractorFrames(String arch) {
    // handle some traditional options
    // left3words is a simple trigram CMM tagger (similar to the baseline EMNLP 2000 tagger)
    arch = arch.replaceAll("left3words", "words(-1,1),order(2)");
    // like a simple trigram CMM tagger (similar to the EMNLP 2000 tagger) with 5 word context
    arch = arch.replaceAll("left5words", "words(-2,2),order(2)");

    ArrayList<Extractor> extrs = new ArrayList<Extractor>();
    List<String> args = StringUtils.valueSplit(arch, "[a-z0-9]*(?:\\([^)]*\\))?", "\\s*,\\s*");
    for (String arg : args) {
      if (arg.equals("bidirectional")) {
        extrs.addAll(Arrays.asList(eFrames_bidirectional));
      } else if (arg.equals("bidirectional5words")) {
        // like best NAACL 2003 cyclic dependency tagger but adds w_{-2}, w_{+2}
        extrs.addAll(Arrays.asList(eFrames_bidirectional));
        extrs.add(new Extractor(-2, false));
        extrs.add(new Extractor(2, false));

      } else if (arg.equals("generic")) {
        extrs.addAll(Arrays.asList(eFrames_generic));
      } else if (arg.equals("sighan2005")) {
        extrs.addAll(Arrays.asList(eFrames_sighan2005));
      } else if (arg.equalsIgnoreCase("german")) {
        extrs.addAll(Arrays.asList(eFrames_german));
      } else if (arg.startsWith("words(")) {
        // non-sequence features with just a certain number of words to the
        // left and right; e.g., words(-2,2) or words(-2,-1)
        int lWindow = Extractor.getParenthesizedNum(arg, 1);
        int rWindow = Extractor.getParenthesizedNum(arg, 2);
        for (int i = lWindow; i <= rWindow; i++) {
          extrs.add(new Extractor(i, false));
        }
      } else if (arg.startsWith("biwords(")) {
        // non-sequence features of word pairs.
        // biwords(-2,1) would give you 3 extractors for w-2w-1, w-1,w0, w0w1
        int lWindow = Extractor.getParenthesizedNum(arg, 1);
        int rWindow = Extractor.getParenthesizedNum(arg, 2);
        for (int i = lWindow; i < rWindow; i++) {
          extrs.add(new ExtractorTwoWords(i));
        }
      } else if (arg.startsWith("lowercasewords(")) {
        // non-sequence features with just a certain number of lowercase words
        // to the left and right, and always the current word
        int lWindow = Extractor.getParenthesizedNum(arg, 1);
        int rWindow = Extractor.getParenthesizedNum(arg, 2);
        for (int i = lWindow; i <= rWindow; i++) {
          extrs.add(new ExtractorWordLowerCase(i));
        }
      } else if (arg.startsWith("order(")) {
        int order = Extractor.getParenthesizedNum(arg, 1);
        if ( ! (order >= 0 && order <= 3)) {
          System.err.println("Bad order for order option: " + order + "; setting to 2");
          order = 2;
        }
        // cdm 2009: We only add successively higher tag k-grams ending at prev.  Adding lower order features at a distance appears not to help (Dec 2009)
        switch (order) {
          case 3:
            extrs.add(prevThreeTags);
            // we don't currently have a template for p3t, p2t without pt, but could
            // falls through
          case 2:
            extrs.add(prevTwoTags);
            // falls through
          case 1:
            extrs.add(new Extractor(-1, true));
            // falls through
          case 0:
        }
      } else if (arg.startsWith("vbn(")) {
        int order = Extractor.getParenthesizedNum(arg, 1);
        extrs.add(new ExtractorVerbalVBNZero(order));
      } else if (arg.equalsIgnoreCase("naacl2003unknowns") || arg.equalsIgnoreCase("lnaacl2003unknowns") || arg.equalsIgnoreCase("naacl2003conjunctions")
              || arg.startsWith("wordshapes(") || arg.startsWith("lwordshapes(") || arg.equalsIgnoreCase("motleyUnknown")
              || arg.startsWith("suffix(") || arg.startsWith("prefix(")
              || arg.startsWith("prefixsuffix") || arg.startsWith("capitalizationsuffix(")
              || arg.startsWith("distsim(") || arg.startsWith("distsimconjunction(")
              || arg.equalsIgnoreCase("lctagfeatures")
              || arg.startsWith("unicodeshapes(") || arg.startsWith("chinesedictionaryfeatures(")
              || arg.startsWith("unicodeshapeconjunction(")) {
        // okay; known unknown keyword
      } else {
        System.err.println("Unrecognized ExtractorFrames identifier (ignored): " + arg);
      }
    } // end for
    return extrs.toArray(new Extractor[extrs.size()]);
  }

} // end class ExtractorFrames


/**
 * The word in lower-cased version.
 */
class ExtractorWordLowerCase extends Extractor {

  private static final long serialVersionUID = -7847524200422095441L;

  public ExtractorWordLowerCase(int position) {
    super(position, false);
  }

  @Override
  String extract(History h, PairsHolder pH) {
    return pH.getWord(h, position).toLowerCase();
  }

  @Override public boolean isLocal() { return true; }
  @Override public boolean isDynamic() { return false; }
}

/**
 * the current word if it is capitalized, zero otherwise
 */
class ExtractorCWordCapCase extends Extractor {

  private static final long serialVersionUID = -2393096135964969744L;

  @Override
  String extract(History h, PairsHolder pH) {
    String cw = ExtractorFrames.cWord.extract(h, pH);
    String lk = cw.toLowerCase();
    if (lk.equals(cw)) {
      return zeroSt;
    }
    return cw;
  }

  @Override public boolean isLocal() { return true; }
  @Override public boolean isDynamic() { return false; }
}


/**
 * This extractor extracts two consecutive words in conjunction,
 * namely leftPosition and leftPosition+1.
 */
class ExtractorTwoWords extends Extractor {

  private static final long serialVersionUID = -1034112287022504917L;

  public ExtractorTwoWords(int leftPosition) {
    super(leftPosition, false);
  }

  @Override
  String extract(History h, PairsHolder pH) {
    return pH.getWord(h, position) + '!' + pH.getWord(h, position + 1);
  }

  @Override public boolean isLocal() { return false; }

  // isDynamic --> false, but no need to override

}


/**
 * This extractor extracts the current and the next word in conjunction.
 */
class ExtractorCWordNextWord extends Extractor {

  private static final long serialVersionUID = -1034112287022504917L;

  public ExtractorCWordNextWord() {
  }

  @Override
  String extract(History h, PairsHolder pH) {
    return pH.getWord(h, 0) + '!' + pH.getWord(h, 1);
  }

  @Override public boolean isLocal() { return false; }
  @Override public boolean isDynamic() { return false; }
}


/**
 * This extractor extracts the current and the next word and the next tag in conjunction.
 */
class ExtractorCWordNextWordTag extends Extractor {

  private static final long serialVersionUID = 277004119652781182L;

  public ExtractorCWordNextWordTag() {
  }

  @Override
  public int rightContext() {
    return 1;
  }

  @Override
  String extract(History h, PairsHolder pH) {
    return pH.getWord(h, 0) + '!' + pH.getTag(h, 1) + '!' + pH.getWord(h, 1);
  }

  @Override public boolean isLocal() { return false; }
  @Override public boolean isDynamic() { return true; }
}


/**
 * This extractor extracts the current and the previous word in conjunction.
 */
class ExtractorCWordPrevWord extends Extractor {

  private static final long serialVersionUID = -6505213465359458926L;

  public ExtractorCWordPrevWord() {
  }

  @Override
  String extract(History h, PairsHolder pH) {
    return pH.getWord(h, -1) + '!' + pH.getWord(h, 0);
  }

  @Override public boolean isLocal() { return false; }
  @Override public boolean isDynamic() { return false; }
}


/**
 * This extractor extracts the current and the previous word in conjunction and also the
 * previous tag
 */
class ExtractorCWordPrevWordTag extends Extractor {

  private static final long serialVersionUID = -3271166574128085943L;

  public ExtractorCWordPrevWordTag() {
  }

  @Override
  public int leftContext() {
    return 1;
  }

  @Override
  String extract(History h, PairsHolder pH) {
    return pH.getWord(h, -1) + '!' + pH.getTag(h, -1) + '!' + pH.getWord(h, 0);
  }

  @Override public boolean isLocal() { return false; }
  @Override public boolean isDynamic() { return true; }
}


/**
 * This extractor extracts the previous two tags.
 */
class ExtractorPrevTwoTags extends Extractor {

  //private static final int TWO_TAG_ALLOWANCE = 8;
  private static final long serialVersionUID = 5124896556547424355L;

  public ExtractorPrevTwoTags() {
  }

  @Override
  public int leftContext() {
    return 2;
  }

  @Override
  String extract(History h, PairsHolder pH) {
    return StringBuildMemoizer.toString(pH.getTag(h, -1),"!",pH.getTag(h, -2));
  }

  @Override public boolean isLocal() { return false; }
  @Override public boolean isDynamic() { return true; }
}


/**
 * This extractor extracts the previous three tags.
 */
class ExtractorPrevThreeTags extends Extractor {

  private static final long serialVersionUID = 2123985878223958420L;

  public ExtractorPrevThreeTags() {
  }

  @Override
  public int leftContext() {
    return 3;
  }


  @Override
  String extract(History h, PairsHolder pH) {
    return pH.getTag(h, -1) + '!' + pH.getTag(h, -2) + '!' + pH.getTag(h, -3);
  }

  @Override public boolean isLocal() { return false; }
  @Override public boolean isDynamic() { return true; }
}


/**
 * This extractor extracts the next two tags.
 */
class ExtractorNextTwoTags extends Extractor {

  private static final long serialVersionUID = -2623988469984672798L;

  public ExtractorNextTwoTags() {
  }

  @Override
  public int rightContext() {
    return 2;
  }

  @Override
  String extract(History h, PairsHolder pH) {
    return StringBuildMemoizer.toString(pH.getTag(h, 1),"!",pH.getTag(h, 2));
  }

  @Override public boolean isLocal() { return false; }
  @Override public boolean isDynamic() { return true; }
}


/**
 * This extractor extracts the next three tags.
 */
class ExtractorNextThreeTags extends Extractor {

  private static final long serialVersionUID = 8563584394721620568L;

  public ExtractorNextThreeTags() {
  }

  @Override
  public int rightContext() {
    return 3;
  }

  @Override
  String extract(History h, PairsHolder pH) {
    return pH.getTag(h, 1) + '!' + pH.getTag(h, 2) + '!' + pH.getTag(h, 3);
  }

  @Override public boolean isLocal() { return false; }
  @Override public boolean isDynamic() { return true; }
}


/**
 * This extractor extracts the previous tag and the current word in conjunction.
 */
class ExtractorPrevTagWord extends Extractor {

  private static final long serialVersionUID = 1283543246845193024L;

  public ExtractorPrevTagWord() {
  }

  @Override
  public int leftContext() {
    return 1;
  }

  @Override
  String extract(History h, PairsHolder pH) {
    return pH.getTag(h, -1) + '!' + pH.getWord(h, 0);
  }

  @Override public boolean isLocal() { return false; }
  @Override public boolean isDynamic() { return true; }

}


/**
 * This extractor extracts the previous tag , next tag, and the current word in conjunction.
 */
class ExtractorPrevTagNextTagWord extends Extractor {

  private static final long serialVersionUID = -4942654091455804179L;

  public ExtractorPrevTagNextTagWord() {
  }

  @Override
  public int leftContext() {
    return 1;
  }

  @Override
  public int rightContext() {
    return 1;
  }

  @Override
  String extract(History h, PairsHolder pH) {
    return pH.getTag(h, -1) + '!' + pH.getWord(h, 0) + pH.getTag(h, 1);
  }

  @Override public boolean isLocal() { return false; }
  @Override public boolean isDynamic() { return true; }
}


/**
 * This extractor extracts the previous tag , next tag in conjunction.
 */
class ExtractorPrevTagNextTag extends Extractor {

  private static final long serialVersionUID = -2807770765588266257L;

  public ExtractorPrevTagNextTag() {
  }

  @Override
  public int leftContext() {
    return 1;
  }

  @Override
  public int rightContext() {
    return 1;
  }


  @Override
  String extract(History h, PairsHolder pH) {
    return StringBuildMemoizer.toString(pH.getTag(h, -1),"!",pH.getTag(h, 1));
  }

  @Override public boolean isLocal() { return false; }
  @Override public boolean isDynamic() { return true; }
}


/**
 * This extractor extracts the next tag and the current word in conjunction.
 */
class ExtractorNextTagWord extends Extractor {

  private static final long serialVersionUID = 4037838593446895680L;

  public ExtractorNextTagWord() {
  }

  @Override
  public int rightContext() {
    return 1;
  }

  @Override
  String extract(History h, PairsHolder pH) {
    return pH.getTag(h, 1) + '!' + pH.getWord(h, 0);
  }

  @Override public boolean isLocal() { return false; }
  @Override public boolean isDynamic() { return true; }

}

