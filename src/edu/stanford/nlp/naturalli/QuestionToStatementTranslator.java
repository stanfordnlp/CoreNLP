package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Translate a question to a statement. For example, "where was Obama born?" to "Obama was born in ?".
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("unchecked")
public class QuestionToStatementTranslator {

  public static class UnknownTokenMarker implements CoreAnnotation<Boolean> {
    @Override
    public Class<Boolean> getType() { return Boolean.class; }
  }

  /** The missing word marker, when the object of the sentence is not type constrained. */
  private final CoreLabel WORD_MISSING = new CoreLabel(){{
    setWord("thing");
    setValue("thing");
    setLemma("thing");
    setTag("NN");
    setNER("O");
    setIndex(-1);
    setBeginPosition(-1);
    setEndPosition(-1);
    set(UnknownTokenMarker.class, true);
  }};


  /**
   * The pattern for "what is ..." sentences.
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#processWhatIs(edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher)
   */
  private final TokenSequencePattern triggerWhatIs = TokenSequencePattern.compile(
      "[{lemma:what; tag:/W.*/}] " +
          "(?$answer_type [tag:/N.*/]+)? " +
          "(?$be [{lemma:be}] )" +
          "(?: /the/ (?$answer_type [word:/name/]) [tag:/[PW].*/])? " +
          "(?$statement_body []+?) " +
          "(?$suffix_specifier [tag:/DT/] [tag:/[NJ].*/]+ )? " +
          "(?$suffix [tag:/R.*/]? [tag:/[VJ].*/]? [tag:/[PRI].*/]? )? " +
          "(?$punct [word:/[?\\.!]/])");

  /**
   * Process sentences matching the "what is ..." pattern.
   *
   * @param matcher The matcher that matched the pattern.
   *
   * @return The converted statement.
   *
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#triggerWhatIs
   */
  private List<CoreLabel> processWhatIs(TokenSequenceMatcher matcher) {
    // Grab the body of the sentence
    List<CoreLabel> body = (List<CoreLabel>) matcher.groupNodes("$statement_body");
    body.addAll((List<CoreLabel>) matcher.groupNodes("$be"));

    // Grab the suffix (e.g., known for, doing now, etc.)
    List<CoreLabel> suffix = (List<CoreLabel>) matcher.groupNodes("$suffix");
    if (suffix != null) {
      // Add the suffix specifier
      List<CoreLabel> suffixSpecifier = (List<CoreLabel>) matcher.groupNodes("$suffix_specifier");
      if (suffixSpecifier != null) {
        body.addAll(suffixSpecifier);
      }
      // Add the suffix
      if (suffix.size() > 1 && suffix.get(0).lemma().equals("do")) {
        // Gah! "what is martin cooper doing now?" -> "martin cooper is now doing"
        body.addAll(suffix.subList(1, suffix.size()));
        body.add(suffix.get(0));
      } else {
        body.addAll(suffix);
      }
    }

    // Grab the object
    List<CoreLabel> objType = (List<CoreLabel>) matcher.groupNodes("$answer_type");
    if (objType == null || objType.isEmpty() ||
        (objType.size() == 1 && objType.get(0).word().equals("name"))) {
      body.add(WORD_MISSING);
    } else {
      for (CoreLabel obj : objType) {
        obj.set(UnknownTokenMarker.class, true);
      }
      body.addAll(objType);
    }

    // Return
    return body;
  }

  /**
   * The pattern for "what is there ..." sentences.
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#processWhatIs(edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher)
   */
  private final TokenSequencePattern triggerWhatIsThere = TokenSequencePattern.compile(
      "[{lemma:what; tag:/W.*/}] " +
          "(?$answer_type [tag:/N.*/]+)? " +
          "(?$be [{lemma:be}] )" +
          "(?$there [{lemma:there; tag:RB}] ) " +
          "(?$adjmod [{tag:/[JN].*/}] )? " +
          "(?$to_verb [{tag:TO}] [{tag:/V.*/}] )? " +
          "(?$statement_body [{tag:IN}] []+?) " +
          "(?$punct [word:/[?\\.!]/])");

  /**
   * Process sentences matching the "what is ..." pattern.
   *
   * @param matcher The matcher that matched the pattern.
   *
   * @return The converted statement.
   *
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#triggerWhatIs
   */
  private List<CoreLabel> processWhatIsThere(TokenSequenceMatcher matcher) {
    List<CoreLabel> optSpan;

    // Grab the prefix of the sentence
    List<CoreLabel> sentence = (List<CoreLabel>) matcher.groupNodes("$there");
    sentence.addAll((List<CoreLabel>) matcher.groupNodes("$be"));

    // Grab the unknown term
    if ((optSpan = (List<CoreLabel>) matcher.groupNodes("$adjmod")) != null) {
      sentence.addAll(optSpan);
    }
    sentence.add(WORD_MISSING);

    // Add body
    if ((optSpan = (List<CoreLabel>) matcher.groupNodes("$to_verb")) != null) {
      sentence.addAll(optSpan);
    }
    sentence.addAll((Collection<CoreLabel>) matcher.groupNodes("$statement_body"));

    // Return
    return sentence;
  }

  /**
   * Convert a question to a statement, if possible.
   * <ul>
   *   <li>The question must have words, lemmas, and part of speech tags.</li>
   *   <li>The question must have valid punctuation.</li>
   * </ul>
   *
   * @param question The question to convert to a statement.
   * @return A list of statement translations of the question. This is usually a singleton list.
   */
  public List<List<CoreLabel>> toStatement(List<CoreLabel> question) {
    TokenSequenceMatcher matcher;
    if ((matcher = triggerWhatIsThere.matcher(question)).matches()) {
      return Collections.singletonList(processWhatIsThere(matcher));
    } else if ((matcher = triggerWhatIs.matcher(question)).matches()) {
      return Collections.singletonList(processWhatIs(matcher));
    } else {
      return Collections.emptyList();
    }
  }

}
