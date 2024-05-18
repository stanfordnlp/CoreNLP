package edu.stanford.nlp.trees;

import edu.stanford.nlp.util.StringUtils;

import java.util.regex.Pattern;

/** This class contains some English String or Tregex regular expression
 *  patterns. They originated in other classes like
 *  EnglishGrammaticalRelations, but were collected here so that they
 *  could be used without having to load large classes (which we might want
 *  to have parallel versions of).
 *  Some are just stored here as String objects, since they are often used as
 *  sub-patterns inside larger patterns.
 *
 *  @author Christopher Manning
 */
public class EnglishPatterns {

  public static final String[] copularVerbs = {
          "be", "being", "been", "am", "are", "r", "is", "ai", "was", "were", "'m", "’m", "m",
          "'re", "’re", "'s", "’s", "s", "`s", "art", "ar", "wase"};

  public static final String[] beGetVerbs = {
          "be", "being", "been", "am", "are", "r", "is", "ai", "was", "were", "'m", "’m", "m",
          "'re", "’re", "'s", "’s", "s", "`s", "art", "ar", "wase",
          "get", "getting", "gets", "got", "gotten" };

  /* A few times the apostrophe is missing on "'s", so we have "s" */
  /* Tricky auxiliaries: "a", "na" is from "(gon|wan)na", "ve" from "Weve", etc.  "of" as non-standard for "have" */
  /* "as" is "has" with missing first letter. "to" is rendered "the" once in EWT. */
  public static final String[] auxiliaries = {
          "will", "wo", "shall", "sha", "may", "might", "should", "would", "can", "could", "ca", "must", "'ll", "’ll", "ll", "-ll", "cold",
          "has", "have", "had", "having", "'ve", "’ve", "ve", "v", "of", "hav", "hvae", "as",
          "get", "gets", "getting", "got", "gotten", "do", "does", "did", "'d", "’d", "d", "du",
          "to", "2", "na", "a", "ot", "ta", "the", "too" };

  public static final String timeWordRegex =
    "/^(?i:Mondays?|Tuesdays?|Wednesdays?|Thursdays?|Fridays?|Saturdays?|Sundays?|years?|months?|weeks?|days?|mornings?|evenings?|nights?|January|Jan\\.|February|Feb\\.|March|Mar\\.|April|Apr\\.|May|June|July|August|Aug\\.|September|Sept\\.|October|Oct\\.|November|Nov\\.|December|Dec\\.|today|yesterday|tomorrow|spring|summer|fall|autumn|winter)$/";

  public static final String timeWordLotRegex =
      "/^(?i:Mondays?|Tuesdays?|Wednesdays?|Thursdays?|Fridays?|Saturdays?|Sundays?|years?|months?|weeks?|days?|mornings?|evenings?|nights?|January|Jan\\.|February|Feb\\.|March|Mar\\.|April|Apr\\.|May|June|July|August|Aug\\.|September|Sept\\.|October|Oct\\.|November|Nov\\.|December|Dec\\.|today|yesterday|tomorrow|spring|summer|fall|autumn|winter|lot)$/";

  public static final String copularWordRegex =
    "/^(?i:" + StringUtils.join(copularVerbs, "|") + ")$/";

  public static final String clausalComplementRegex =
    "/^(?i:seem|seems|seemed|seeming|resemble|resembles|resembled|resembling|become|becomes|became|becoming|remain|remains|remained|remaining)$/";

  // r is for texting r = are
  public static final String passiveAuxWordRegex =
      "/^(?i:" +StringUtils.join(beGetVerbs, "|") + ")$/";

  public static final String beAuxiliaryRegex =
        "/^(?i:am|is|are|r|be|being|'s|’s|'re|’re|'m|’m|was|were|been|s|ai|m|art|ar|wase)$/";

  public static final String haveRegex =
          "/^(?i:have|had|has|having|'ve|’ve|ve|v|'d|’d|d|hvae|hav|as)$/";

  // private static final String stopKeepRegex = "/^(?i:stop|stops|stopped|stopping|keep|keeps|kept|keeping)$/";

  public static final String selfRegex =
    "/^(?i:myself|yourself|himself|herself|itself|ourselves|yourselves|themselves)$/";

  public static final String xcompVerbRegex =
      "/^(?i:(?:allow|ask|demand|expect|help|order|prompt)(?:s|ed|ing)?|(?:advis|convinc|declar|defin|desir|encourag|forc|implor|nam|persuad|pressur|requir|urg)(?:e|es|ed|ing)|" +
              "beg|begs|begged|begging|compel|compels|compelled|compelling|lobby|lobbies|lobbied|lobbying|permit|permits|permitted|permitting|tell|tells|told|telling)$/";

  // A list of verbs with an xcomp as an argument
  // which don't require a NP before the xcomp.
  public static final String xcompNoObjVerbRegex =
      "/^(?i:advis|afford|allow|am$|appear|are$|ask|attempt|avoid|be$|bec[oa]m|beg[ia]n|believ|call|caus[ei]|ceas[ei]|choos[ei]|chose|claim|consider|continu|convinc|decid|decline|end|enjoy|expect|feel|felt|find|forb[ia]d|forc[ei]|forg[eo]t|found|going|gon|g[eo]t|happen|hat[ei]|ha[vds]|help|hesitat|hop[ei]|intend|instruct|invit|['’i]s$|keep|kept|learn|leav[ei]|left|let|lik[ei]|look|lov[ei]|made|mak[ei]|manag|nam[ei]|need|offer|order|plan|pretend|proceed|promis|prov[ei]|rate|recommend|refus|regret|remember|requir|sa[iy]|seem|sound|start|stop|suggest|suppos|tell|tend|threaten|told|tr[yi]|turn|used|wan|was$|willing|wish)/";

  /** A list of verbs where the answer to a question involving that
   *  verb would be a ccomp.  For example, "I know when the train is
   * arriving."  What does the person know?
   */
  public static final String ccompVerbRegex =
    "/^(?i:ask|asks|asked|asking|know|knows|knew|knowing|specify|specifies|specified|specifying|tell|tells|told|telling|understand|understands|understood|understanding|wonder|wonders|wondered|wondering)$/";

  // A subset of ccompVerbRegex where you could expect an object and
  // still have a ccomp.  For example, "They told me when ..." can
  // still have a ccomp.  "They know my order when ..." would not
  // expect a ccomp between "know" and the head of "when ..."
  public static final String ccompObjVerbRegex =
    "/^(?i:tell|tells|told|telling)$/";

  /** A list of verbs which are verbs of speaking that easily take an S (as a complement or topicalized)
   *  which is a direct speech ccomp. For example: "He concedes: ``This is a difficult market.''"
   * <br>
   * TODO: maybe sign, as in ASL?  sing ... wish?
   */
  public static final String sayVerbRegex =
    "/^(?i:say|says|said|saying|(?:add|bellow|bleat|blubber|bluster|boast|boom|bray|call|chant|chirp|claim|complain|coo|counsel|croak|crow|drawl|explain|gasp|inform|interject|pray|proclaim|protest|purr|recall|remark|report|respond|scream|shout|shriek|sigh|sulk|whisper|whoop|yammer|yap|yell|yelp)(?:s|ed|ing)?|(?:advis|announc|acknowledg|cackl|chortl|chuckl|conced|conclud|decid|declar|dron|grip|grous|inton|not|observ|pledg|propos|stat|whin|whing)(?:e|es|ed|ing)|(?:bitch|confess|kibitz|kibbitz|screech)(?:es|ed|ing)?|(?:agree)(?:s|d|ing)?|(?:cr|repl)(?:y|ied|ies|ying)|admit|admits|admitted|admitting|hold|holds|holding|held|write|writes|writing|wrote|tell|tells|telling|told|quipped|quip|quips|quipping|signal|signals|signaled|signalled|signaling|signallingthink|thinks|thinking|thought)$/";


  // TODO: is there some better pattern to look for? We do not have tag information at this point
  public static final String RELATIVIZING_WORD_REGEX = "(?i:that|what|which|who|whom|whose)";

  public static final Pattern RELATIVIZING_WORD_PATTERN = Pattern.compile(RELATIVIZING_WORD_REGEX);

  // Lemmata of verbs with the argument structure NP V S-INF.
  // Extracted from VerbNet 3.2.
  public static final String NP_V_S_INF_VERBS_REGEX = "(?i:acquiesce|submit|bow|defer|accede|succumb|yield|capitulate|despise|disdain|dislike|regret|like|love|enjoy|fear|hate|pledge|proceed|begin|start|commence|recommence|resume|undertake|ally|collaborate|collude|conspire|discriminate|legislate|partner|protest|rebel|retaliate|scheme|sin|befriend|continue|broadcast|cable|e-mail|fax|modem|netmail|phone|radio|relay|satellite|semaphore|sign|signal|telecast|telegraph|telephone|telex|wire|wireless|ache|crave|fall|hanker|hope|hunger|itch|long|lust|pine|pray|thirst|wish|yearn|dangle|hanker|lust|thirst|yearn|babble|bark|bawl|bellow|bleat|blubber|boom|bray|burble|bluster|cackle|call|carol|chant|chatter|chirp|chortle|chuckle|cluck|coo|croak|croon|crow|cry|drawl|drone|gabble|gasp|gibber|groan|growl|grumble|grunt|hiss|holler|hoot|howl|jabber|keen|lilt|lisp|mewl|moan|mumble|murmur|mutter|nasal|natter|pant|prattle|purr|quaver|rage|rant|rasp|roar|rumble|scream|screech|shout|shriek|sibilate|simper|sigh|sing|smatter|smile|snap|snarl|snivel|snuffle|splutter|squall|squawk|squeak|squeal|stammer|stemmer|stutter|thunder|tisk|trill|trumpet|twang|twitter|vociferate|wail|warble|wheeze|whimper|whine|whisper|whistle|witter|whoop|yammer|yap|yell|yelp|yodel|blare|gurgle|hum|neglect|fail|forego|forgo|flub|overleap|manage|omit|seem|appear|prove|manage|fail|flub|try|attempt|intend|enjoy|expect|wish|hope|intend|mean|plan|propose|think|aim|dream|imagine|yen)";

  // match "not", "n't", "nt" (for informal writing), or "never" as _complete_ string
  public static final String NOT_PAT_WORD = "^(?i:n[o'’]?t|never)$";

  public static final String NOT_PAT = "/" + NOT_PAT_WORD + "/";

  // ect seems to be a common misspelling for etc in the PTB
  public static final String ETC_PAT = "(FW < /^(?i:(etc|ect))$/)";
  public static final String ETC_PAT_target = "(FW=target < /^(?i:(etc|ect))$/)";

  public static final String FW_ETC_PAT = "(ADVP|NP <1 (FW < /^(?i:(etc|ect))$/))";
  public static final String FW_ETC_PAT_target = "(ADVP|NP=target <1 (FW < /^(?i:(etc|ect))$/))";

  // The smiley expressions allow for () or LRB/RRB
  public static final String WESTERN_SMILEY = "/^(?:[<>]?[:;=8][\\-o\\*'’]?(?:-RRB-|-LRB-|[()DPdpO\\/\\\\\\:}{@\\|\\[\\]])|(?:-RRB-|-LRB-|[()DPdpO\\/\\\\\\:}{@\\|\\[\\]])[\\-o\\*'’]?[:;=8][<>]?)$/";

  public static final String ASIAN_SMILEY = "/(?!^--$)^(?:-LRB-|[(])?[\\-\\^x=~<>'’][_.]?[\\-\\^x=~<>'’](?:-RRB-|[)])?$/";

  private EnglishPatterns() {} // static constants

}
