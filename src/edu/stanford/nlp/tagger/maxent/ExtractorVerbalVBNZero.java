package edu.stanford.nlp.tagger.maxent; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.regex.Pattern;


/**
 * Look for verbs selecting a VBN verb.
 * This is now a zeroeth order observed data only feature.
 * But reminiscent of what was done in Toutanova and Manning 2000.
 * It doesn't seem to help tagging performance any more.
 *
 * @author Christopher Manning
 */
public class ExtractorVerbalVBNZero extends DictionaryExtractor  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ExtractorVerbalVBNZero.class);

  private static final String vbnTag = "VBN";
  private static final String vbdTag = "VBD";
  private static final String jjTag = "JJ";
  private static final String edSuff = "ed";
  private static final String enSuff = "en";
  private static final String oneSt = "1";
  private static final String naWord = "NA";

  private final int bound;
  private static final Pattern stopper = Pattern.compile("(?i:and|or|but|,|;|-|--)");
  private static final Pattern vbnWord = Pattern.compile("(?i:have|has|having|had|is|am|are|was|were|be|being|been|'ve|'s|s|'d|'re|'m|gotten|got|gets|get|getting)"); // cf. list in EnglishPTBTreebankCorrector


  public ExtractorVerbalVBNZero(int bound) {
    this.bound = bound;
  }


  @Override
  public boolean precondition(String tag) {
    log.info("VBN: Testing precondition on " + tag + ": " + (tag.equals(vbnTag) || tag.equals(vbdTag) || tag.equals(jjTag)));
    return tag.equals(vbnTag) || tag.equals(vbdTag) || tag.equals(jjTag);
  }


  @Override
  String extract(History h, PairsHolder pH) {
    String cword = pH.getWord(h, 0);
    int allCount = dict.sum(cword);
    int vBNCount = dict.getCount(cword, vbnTag);
    int vBDCount = dict.getCount(cword, vbdTag);

    // Conditions for deciding inapplicable
    if ((allCount == 0) && (!(cword.endsWith(edSuff) || cword.endsWith(enSuff)))) {
      return zeroSt;
    }
    if ((allCount > 0) && (vBNCount + vBDCount <= allCount / 100)) {
      return zeroSt;
    }

    String lastverb = naWord;
    //String lastvtag = zeroSt; // mg: written but never read

    for (int index = -1; index >= -bound; index--) {
      String word2 = pH.getWord(h, index);
      if ("NA".equals(word2)) {
        break;
      }
      if (stopper.matcher(word2).matches()) {
        break;
      }
      if (vbnWord.matcher(word2).matches()) {
        lastverb = word2;
        break;
      }
      index--;
    }

    if ( ! lastverb.equals(naWord)) {
      log.info("VBN: For " + cword + ", found preceding VBN cue " + lastverb);
      return oneSt;
    }

    return zeroSt;
  }

  @Override
  public String toString() {
    return "ExtractorVerbalVBNZero(bound=" + bound + ')';
  }



  private static final long serialVersionUID = -5881204185400060636L;

}
