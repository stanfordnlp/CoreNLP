package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;

import java.util.*;

/**
 * <p>
 * Translate a question to a statement. For example, "where was Obama born?" to "Obama was born in ?".
 * </p>
 *
 * <p>
 * This class was developed for, and therefore likely performs best on (read: "over-fits gloriously to")
 * the webquestions dataset (http://www-nlp.stanford.edu/software/sempre/).
 * The rules were created based off of the webquestions
 * training set, and tested against the sentences in the QuestionToStatementTranslatorTest.
 * If something fails, please add it to the test when you fix it!
 * If you change something here, please validate it wit the test!
 * </p>
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

  /** The missing word marker typed as a location. */
  private final CoreLabel WORD_MISSING_LOCATION = new CoreLabel(){{
    setWord("location");
    setValue("location");
    setLemma("location");
    setTag("NNP");
    setNER("O");
    setIndex(-1);
    setBeginPosition(-1);
    setEndPosition(-1);
    set(UnknownTokenMarker.class, true);
  }};

  /** The missing word marker typed as a person. */
  private final CoreLabel WORD_MISSING_PERSON = new CoreLabel(){{
    setWord("person");
    setValue("person");
    setLemma("person");
    setTag("NNP");
    setNER("O");
    setIndex(-1);
    setBeginPosition(-1);
    setEndPosition(-1);
    set(UnknownTokenMarker.class, true);
  }};

  /** The missing word marker typed as a time. */
  private final CoreLabel WORD_MISSING_TIME = new CoreLabel(){{
    setWord("time");
    setValue("time");
    setLemma("time");
    setTag("NN");
    setNER("O");
    setIndex(-1);
    setBeginPosition(-1);
    setEndPosition(-1);
    set(UnknownTokenMarker.class, true);
  }};

  /** The word "," as a CoreLabel */
  private final CoreLabel WORD_COMMA = new CoreLabel(){{
    setWord(",");
    setValue(",");
    setLemma(",");
    setTag(",");
    setNER("O");
    setIndex(-1);
    setBeginPosition(-1);
    setEndPosition(-1);
  }};

  /** The word "from" as a CoreLabel */
  private final CoreLabel WORD_FROM = new CoreLabel(){{
    setWord("from");
    setValue("from");
    setLemma("from");
    setTag("IN");
    setNER("O");
    setIndex(-1);
    setBeginPosition(-1);
    setEndPosition(-1);
  }};

  /** The word "at" as a CoreLabel */
  private final CoreLabel WORD_AT = new CoreLabel(){{
    setWord("at");
    setValue("at");
    setLemma("at");
    setTag("IN");
    setNER("O");
    setIndex(-1);
    setBeginPosition(-1);
    setEndPosition(-1);
  }};

  /** The word "in" as a CoreLabel */
  private final CoreLabel WORD_IN = new CoreLabel(){{
    setWord("in");
    setValue("in");
    setLemma("in");
    setTag("IN");
    setNER("O");
    setIndex(-1);
    setBeginPosition(-1);
    setEndPosition(-1);
  }};

  private final Set<String> fromNotAtDict = Collections.unmodifiableSet(new HashSet<String>() {{
    add("funding"); add("oil");
  }});


  /**
   * The pattern for "what is ..." sentences.
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#processWhatIs(edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher)
   */
  private final TokenSequencePattern triggerWhatIs = TokenSequencePattern.compile(
      "[{lemma:/what|which/; tag:/W.*/}] " +
          "(?$answer_type [tag:/N.*/]+)? " +
          "(?$be [{lemma:be}] )" +
          "(?: /the/ (?$answer_type [word:/name/]) [tag:/[PW].*/])? " +
          "(?$statement_body []+?) " +
          "(?$prep_num [!{tag:IN}] [tag:CD] )? " +
          "(?$suffix [tag:/[RI].*/] )? " +
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

    // Add the "be" token
    // [Gabor]: This is black magic -- if the "be" got misplaced, God help us all.
    // [Gabor]: Mostly you. You'll need most of the help.
    List<CoreLabel> be = (List<CoreLabel>) matcher.groupNodes("$be");
    List<CoreLabel> suffix = (List<CoreLabel>) matcher.groupNodes("$suffix");
    boolean addedBe = false;
    boolean addedSuffix = false;
    for (int i = 1; i < body.size(); ++i) {
      CoreLabel tokI = body.get(i);
      if (tokI.tag() != null &&
          (tokI.tag().startsWith("V") ||
              (tokI.tag().startsWith("J") && suffix != null) ||
              (tokI.tag().startsWith("D") && suffix != null) ||
              (tokI.tag().startsWith("R") && suffix != null) )) {
        body.add(i, be.get(0)); i += 1;
        if (suffix != null) {
          while (i < body.size() && body.get(i).tag() != null &&
              (body.get(i).tag().startsWith("J") || body.get(i).tag().startsWith("V") || body.get(i).tag().startsWith("R") ||
               body.get(i).tag().startsWith("N") || body.get(i).tag().startsWith("D")) &&
              !body.get(i).tag().equals("VBG")) {
            i += 1;
          }
          body.add(i, suffix.get(0));
          addedSuffix = true;
        }
        addedBe = true;
        break;
      }
    }
    // Tweak to handle dropped prepositions
    List<CoreLabel> prepNum = (List<CoreLabel>) matcher.groupNodes("$prep_num");
    if (prepNum != null) {
      body.add(prepNum.get(0));
      body.add(WORD_IN);
      body.add(prepNum.get(1));
    }
    // Add the "be" and suffix
    if (!addedBe) {
      body.addAll(be);
    }
    if (!addedSuffix && suffix != null) {
      body.addAll(suffix);
    }


    // Grab the object
    List<CoreLabel> objType = (List<CoreLabel>) matcher.groupNodes("$answer_type");
    // (try to insert the object earlier)
    int i = body.size() - 1;
    while (i >= 1 && body.get(i).tag() != null &&
        (body.get(i).tag().startsWith("N") || body.get(i).tag().startsWith("J"))) {
      i -= 1;
    }
    // (actually insert the object)
    if (objType == null || objType.isEmpty() ||
        (objType.size() == 1 && objType.get(0).word().equals("name"))) {
      // (case: untyped)
      if (i < body.size() - 1 && body.get(i).tag() != null && body.get(i).tag().startsWith("IN")) {
        body.add(i, WORD_MISSING);
      } else {
        body.add(WORD_MISSING);
      }
    } else {
      // (case: typed)
      for (CoreLabel obj : objType) {
        obj.set(UnknownTokenMarker.class, true);
      }
      body.addAll(objType);
    }

    // Return
    return body;
  }

  /**
   * The pattern for "what/which NN is ..." sentences.
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#processWhNNIs(edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher)
   */
  private final TokenSequencePattern triggerWhNNIs = TokenSequencePattern.compile(
      "[{lemma:/what|which/; tag:/W.*/}] " +
          "(?$answer_type [!{lemma:be}]+) " +
          "(?$be [{lemma:be}] [{tag:/[VRIJ].*/}] ) " +
          "(?$statement_body []+?) " +
          "(?$punct [word:/[?\\.!]/])");

  /**
   * Process sentences matching the "what NN is ..." pattern.
   *
   * @param matcher The matcher that matched the pattern.
   *
   * @return The converted statement.
   *
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#triggerWhNNIs
   */
  private List<CoreLabel> processWhNNIs(TokenSequenceMatcher matcher) {
    List<CoreLabel> sentence = (List<CoreLabel>) matcher.groupNodes("$answer_type");
    for (CoreLabel lbl : sentence) {
      lbl.set(UnknownTokenMarker.class, true);
    }
    sentence.addAll((Collection<CoreLabel>) matcher.groupNodes("$be"));
    sentence.addAll((Collection<CoreLabel>) matcher.groupNodes("$statement_body"));
    return sentence;
  }

  /**
   * The pattern for "what/which NN have ..." sentences.
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#processWhNNHave(edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher)
   */
  private final TokenSequencePattern triggerWhNNHave = TokenSequencePattern.compile(
      "[{lemma:/what|which/; tag:/W.*/}] " +
          "(?$answer_type [!{tag:/V.*/}]+) " +
          "(?$have [{lemma:have} | {lemma:do}] ) " +
          "(?$pre_verb [!{tag:/V.*/}]+ ) " +
          "(?$verb [{tag:/V.*/}] [{tag:IN}]? ) " +
          "(?$post_verb []+ )? " +
          "(?$punct [word:/[?\\.!]/])");

  /**
   * Process sentences matching the "what NN has ..." pattern.
   *
   * @param matcher The matcher that matched the pattern.
   *
   * @return The converted statement.
   *
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#triggerWhNNHave
   */
  private List<CoreLabel> processWhNNHave(TokenSequenceMatcher matcher) {
    List<CoreLabel> sentence = new ArrayList<>();
    // Add prefix
    sentence.addAll((Collection<CoreLabel>) matcher.groupNodes("$pre_verb"));

    // Add have/do
    List<CoreLabel> have = (List<CoreLabel>) matcher.groupNodes("$have");
    if (have != null && have.size() > 0 && have.get(0).lemma() != null && have.get(0).lemma().equals("have")) {
      sentence.addAll((Collection<CoreLabel>) matcher.groupNodes("$have"));
    }

    // Compute answer type
    List<CoreLabel> answer = (List<CoreLabel>) matcher.groupNodes("$answer_type");
    if (answer != null) {
      for (CoreLabel lbl : answer) {
        lbl.set(UnknownTokenMarker.class, true);
      }
    }

    // Add verb + Answer
    List<CoreLabel> verb = (List<CoreLabel>) matcher.groupNodes("$verb");
    List<CoreLabel> post = (List<CoreLabel>) matcher.groupNodes("$post_verb");
    if (verb.size() < 2 || post == null || post.size() == 0 || post.get(0).tag() == null || post.get(0).tag().equals("CD")) {
      sentence.addAll(verb);
      if (answer == null) {
        sentence.add(WORD_MISSING);
      } else {
        sentence.addAll(answer);
      }
    } else {
      sentence.add(verb.get(0));
      if (answer == null) {
        sentence.add(WORD_MISSING);
      } else {
        sentence.addAll(answer);
      }
      sentence.addAll(verb.subList(1, verb.size()));
    }

    // Add postfix
    if (post != null) {
      if (post.size() == 1 && post.get(0).tag() != null && post.get(0).tag().equals("CD")) {
        sentence.add(WORD_IN);
      }
      sentence.addAll(post);
    }

    // Return
    return sentence;
  }

  /**
   * The pattern for "what/which NN have NN ..." sentences.
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#processWhNNHaveNN(edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher)
   */
  private final TokenSequencePattern triggerWhNNHaveNN = TokenSequencePattern.compile(
      "[{lemma:/what|which/; tag:/W.*/}] " +
          "(?$answer_type [tag:/N.*/]+) " +
          "(?$have [{lemma:have}] ) " +
          "(?$statement_body [!{tag:/V.*/}]+?) " +
          "(?$punct [word:/[?\\.!]/])");

  /**
   * Process sentences matching the "what NN have NN ..." pattern.
   *
   * @param matcher The matcher that matched the pattern.
   *
   * @return The converted statement.
   *
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#triggerWhNNHaveNN
   */
  private List<CoreLabel> processWhNNHaveNN(TokenSequenceMatcher matcher) {
    List<CoreLabel> sentence = (List<CoreLabel>) matcher.groupNodes("$answer_type");
    for (CoreLabel lbl : sentence) {
      lbl.set(UnknownTokenMarker.class, true);
    }
    sentence.addAll((Collection<CoreLabel>) matcher.groupNodes("$have"));
    sentence.addAll((Collection<CoreLabel>) matcher.groupNodes("$statement_body"));
    return sentence;
  }

  /**
   * The pattern for "what is there ..." sentences.
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#processWhatIsThere(edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher)
   */
  private final TokenSequencePattern triggerWhatIsThere = TokenSequencePattern.compile(
      "[{lemma:/what|which/; tag:/W.*/}] " +
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
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#triggerWhatIsThere
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
   * The pattern for "where do..."  sentences.
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#processWhereDo(edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher)
   */
  private final TokenSequencePattern triggerWhereDo = TokenSequencePattern.compile(
      "[{lemma:where; tag:/W.*/}] " +
          "(?$do [ {lemma:/do/} ]) " +
          "(?$statement_body []+?) " +
          "(?$at [tag:/[IT].*/] )? " +
          "(?$loc [tag:/N.*/] )*? " +
          "(?$punct [word:/[?\\.!]/])" );

  /**
   *
   * Process sentences matching the "where do ..." pattern.
   *
   * @param matcher The matcher that matched the pattern.
   *
   * @return The converted statement.
   *
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#triggerWhereDo
   */
  private List<CoreLabel> processWhereDo(TokenSequenceMatcher matcher) {
    // Get the "at" preposition and the "location" missing marker to use
    List<CoreLabel> specloc = (List<CoreLabel>) matcher.groupNodes("$loc");
    CoreLabel wordAt = WORD_AT;
    CoreLabel missing = WORD_MISSING_LOCATION;
    if (specloc != null && fromNotAtDict.contains(specloc.get(0).word())) {
      wordAt = WORD_FROM;
      missing = WORD_MISSING;
    }

    // Grab the prefix of the sentence
    List<CoreLabel> sentence = (List<CoreLabel>) matcher.groupNodes("$statement_body");
    // (check if we should be looking for a location)
    for (CoreLabel lbl : sentence) {
      if ("name".equals(lbl.word())) {
        missing = WORD_MISSING;
      }
    }

    // Add the "at" part
    List<CoreLabel> at = (List<CoreLabel>) matcher.groupNodes("$at");
    if (at != null && at.size() > 0) {
      sentence.addAll(at);
    } else {
      if (specloc != null) {
        sentence.addAll(specloc);
      }
      sentence.add(wordAt);
    }

    // Add the location
    sentence.add(missing);

    // Add an optional specifier location
    if (specloc != null && at != null) {
      sentence.add(WORD_COMMA);
      sentence.addAll(specloc);
    }

    // Return
    return sentence;
  }

  /**
   * The pattern for "where is..."  sentences.
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#processWhereIs(edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher)
   */
  private final TokenSequencePattern triggerWhereIs = TokenSequencePattern.compile(
      "[{lemma:where; tag:/W.*/}] " +
          "(?$be [ {lemma:/be/} ]) " +
          "(?$initial_verb [tag:/[VJ].*/] )? " +
          "(?$statement_body []+?) " +
          "(?$ignored [lemma:locate] [tag:IN] [word:a]? [word:map]? )? " +
          "(?$final_verb [tag:/[VJ].*/] )? " +
          "(?$at [tag:IN] )? " +
          "(?$punct [word:/[?\\.!]/])" );

  /**
   *
   * Process sentences matching the "where is ..." pattern.
   *
   * @param matcher The matcher that matched the pattern.
   *
   * @return The converted statement.
   *
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#triggerWhereIs
   */
  private List<CoreLabel> processWhereIs(TokenSequenceMatcher matcher) {
    // Grab the prefix of the sentence
    List<CoreLabel> sentence = (List<CoreLabel>) matcher.groupNodes("$statement_body");

    // Add the "is" part
    List<CoreLabel> be = (List<CoreLabel>) matcher.groupNodes("$be");
    sentence.addAll(be);

    // Add the optional final verb
    List<CoreLabel> verb = (List<CoreLabel>) matcher.groupNodes("$final_verb");
    if (verb != null) {
      sentence.addAll(verb);
    }
    // Add the optional initial verb (from disfluent questions!)
    verb = (List<CoreLabel>) matcher.groupNodes("$initial_verb");
    if (verb != null) {
      sentence.addAll(verb);
    }

    // Add the "at" part
    List<CoreLabel> at = (List<CoreLabel>) matcher.groupNodes("$at");
    if (at != null && at.size() > 0) {
      sentence.addAll(at);
    } else {
      sentence.add(WORD_AT);
    }

    // Add the location
    sentence.add(WORD_MISSING_LOCATION);

    // Return
    return sentence;
  }

  /**
   * The pattern for "who is..."  sentences.
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#processWhoIs(edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher)
   */
  private final TokenSequencePattern triggerWhoIs = TokenSequencePattern.compile(
      "[{lemma:who; tag:/W.*/}] " +
          "(?$be [ {lemma:/be/} ] ) " +
          "(?$prep [ {tag:/IN|V.*/} ] )? " +
          "(?$statement_body []+?) " +
          "(?$final_verb [tag:/V.*/] [tag:/[IRT].*/] )? " +
          "(?$final_verb [tag:VBG] )? " +
          "(?$now [tag:RB] )? " +
          "(?$prep_num [!{tag:IN}] [tag:CD] )? " +
          "(?$punct [word:/[?\\.!]/])" );

  /**
   *
   * Process sentences matching the "who is ..." pattern.
   *
   * @param matcher The matcher that matched the pattern.
   *
   * @return The converted statement.
   *
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#triggerWhoIs
   */
  private List<CoreLabel> processWhoIs(TokenSequenceMatcher matcher) {
    List<CoreLabel> sentence = new ArrayList<>();
    List<CoreLabel> prep = (List<CoreLabel>) matcher.groupNodes("$prep");
    boolean addedBe = false;

    if (prep != null && !prep.isEmpty()) {
      // Add the person
      sentence.add(WORD_MISSING_PERSON);

      // Add the "is" part
      List<CoreLabel> be = (List<CoreLabel>) matcher.groupNodes("$be");
      sentence.addAll(be);
      addedBe = true;

      // Add the preposition
      sentence.addAll(prep);

      // Grab the prefix of the sentence
      sentence.addAll((List<CoreLabel>) matcher.groupNodes("$statement_body"));

    } else {

      // Grab the prefix of the sentence
      sentence.addAll((List<CoreLabel>) matcher.groupNodes("$statement_body"));

      // Tweak to handle dropped prepositions
      List<CoreLabel> prepNum = (List<CoreLabel>) matcher.groupNodes("$prep_num");
      if (prepNum != null) {
        sentence.add(prepNum.get(0));
        sentence.add(WORD_IN);
        sentence.add(prepNum.get(1));
      }

      // Add the "is" part
      List<CoreLabel> be = (List<CoreLabel>) matcher.groupNodes("$be");
      if (sentence.size() > 1 &&
          !sentence.get(sentence.size() - 1).word().equals("be")) {
        sentence.addAll(be);
        addedBe = true;
      }

      // Add the final verb part
      List<CoreLabel> verb = (List<CoreLabel>) matcher.groupNodes("$final_verb");
      if (verb != null) {
        if (verb.size() > 1 && verb.get(verb.size() - 1).word().equals("too")) {  // Fix common typo
          verb.get(verb.size() - 1).setWord("to");
          verb.get(verb.size() - 1).setValue("to");
          verb.get(verb.size() - 1).setLemma("to");
          verb.get(verb.size() - 1).setTag("IN");
        }
        sentence.addAll(verb);
      }

      // Add the person
      sentence.add(WORD_MISSING_PERSON);
    }

    // Add a final modifier (e.g., "now")
    List<CoreLabel> now = (List<CoreLabel>) matcher.groupNodes("$now");
    if (now != null) {
      sentence.addAll(now);
    }

    // Insert "was" before first verb, if applicable
    if (!addedBe) {
      for (int i = 0; i < sentence.size(); ++i) {
        if (sentence.get(i).tag() != null && sentence.get(i).tag().startsWith("V")) {
          sentence.add(i, (CoreLabel) matcher.groupNodes("$be").get(0));
          break;
        }
      }
    }

    // Return
    return sentence;
  }

  /**
   * The pattern for "who did..."  sentences.
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#processWhoDid(edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher)
   */
  private final TokenSequencePattern triggerWhoDid = TokenSequencePattern.compile(
      "[{lemma:who; tag:/W.*/}] " +
          "(?$do [ {lemma:/do/} ] ) " +
          "(?$statement_body []+?) " +
          "(?$now [tag:RB] )? " +
          "(?$punct [word:/[?\\.!]/])" );

  /**
   *
   * Process sentences matching the "who did ..." pattern.
   *
   * @param matcher The matcher that matched the pattern.
   *
   * @return The converted statement.
   *
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#triggerWhoDid
   */
  private List<CoreLabel> processWhoDid(TokenSequenceMatcher matcher) {
    // Get the body
    List<CoreLabel> sentence = (List<CoreLabel>) matcher.groupNodes("$statement_body");

    // Check if there is no main verb other than "do"
    // If it doesn't, then the sentence should be "person do ...."
    boolean hasVerb = false;
    for (CoreLabel w : sentence) {
      if (w.tag() != null && w.tag().startsWith("V")) {
        hasVerb = true;
      }
    }
    if (!hasVerb) {
      sentence.add(0, WORD_MISSING_PERSON);
      sentence.add(1, (CoreLabel) matcher.groupNodes("$do").get(0));
      return sentence;
    }

    // Add the missing word
    // (in front of the PPs)
    boolean addedPerson = false;
    if (sentence.size() > 0 && sentence.get(sentence.size() - 1).tag() != null && !sentence.get(sentence.size() - 1).tag().startsWith("I")) {
      for (int i = 0; i < sentence.size() - 1; ++i) {
        if (sentence.get(i).tag() != null &&
            (sentence.get(i).tag().equals("IN") || sentence.get(i).word().equals("last") || sentence.get(i).word().equals("next") || sentence.get(i).word().equals("this"))) {
          sentence.add(i, WORD_MISSING_PERSON);
          addedPerson = true;
          break;
        }
      }
    }
    // (at the end of the sentence)
    if (!addedPerson) {
      sentence.add(WORD_MISSING_PERSON);
    }

    // Add "now" / "first" / etc.
    List<CoreLabel> now = (List<CoreLabel>) matcher.groupNodes("$now");
    if (now != null) {
      sentence.addAll(now);
    }

    // Return
    return sentence;
  }

  /**
   * The pattern for "where is..."  sentences.
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#processWhatDo(edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher)
   */
  private final TokenSequencePattern triggerWhatDo = TokenSequencePattern.compile(
      "[{lemma:/what|which/; tag:/W.*/}] " +
          "(?$do [ {lemma:/do/} ]) " +
          "(?$pre_do [ !{lemma:do} & !{tag:IN} ]+) " +
          "(?$mid_do [ {lemma:do} ] )? " +
          "(?$in [ {tag:IN} ] )? " +
          "(?$post_do []+ )? " +
          "(?$punct [word:/[?\\.!]/])" );

  /**
   *
   * Process sentences matching the "what do ..." pattern.
   *
   * @param matcher The matcher that matched the pattern.
   *
   * @return The converted statement.
   *
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#triggerWhatDo
   */
  private List<CoreLabel> processWhatDo(TokenSequenceMatcher matcher) {
    // Grab the prefix of the sentence
    List<CoreLabel> sentence = (List<CoreLabel>) matcher.groupNodes("$pre_do");

    // Add the optional middle do
    List<CoreLabel> midDo = (List<CoreLabel>) matcher.groupNodes("$mid_do");
    if (midDo != null) {
      sentence.addAll((List<CoreLabel>) matcher.groupNodes("$do"));
    }

    // Add the thing (not end of sentence)
    if (matcher.groupNodes("$post_do") != null) {
      sentence.add(WORD_MISSING);
    }

    // Add IN token
    List<CoreLabel> midIN = (List<CoreLabel>) matcher.groupNodes("$in");
    if (midIN != null) {
      sentence.addAll(midIN);
    }

    // Add the thing (end of sentence)
    if (matcher.groupNodes("$post_do") == null) {
      if (sentence.size() > 1 && "off".equals(sentence.get(sentence.size() - 1).word())) { // Fix common typo
        sentence.get(sentence.size() - 1).setWord("of");
        sentence.get(sentence.size() - 1).setValue("of");
        sentence.get(sentence.size() - 1).setLemma("of");
        sentence.get(sentence.size() - 1).setTag("IN");
      }
      sentence.add(WORD_MISSING);
    }

    // Add post do
    List<CoreLabel> postDo = (List<CoreLabel>) matcher.groupNodes("$post_do");
    if (postDo != null) {
      sentence.addAll(postDo);
    }

    // Tweak to handle dropped prepositions
    if (sentence.size() > 2 &&
        !"IN".equals(sentence.get(sentence.size() - 2).tag()) &&
        "CD".equals(sentence.get(sentence.size() - 1).tag())) {
      sentence.add(sentence.size() - 1, WORD_IN);
    }

    // Return
    return sentence;
  }

  /**
   * The pattern for "when do..."  sentences.
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#processWhenDo(edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher)
   */
  private final TokenSequencePattern triggerWhenDo = TokenSequencePattern.compile(
      "[{lemma:when; tag:/W.*/}] " +
          "(?$do [ {lemma:/do/} ]) " +
          "(?$statement_body []+?) " +
          "(?$in [tag:/[IT].*/] )? " +
          "(?$punct [word:/[?\\.!]/])" );

  /**
   *
   * Process sentences matching the "when do ..." pattern.
   *
   * @param matcher The matcher that matched the pattern.
   *
   * @return The converted statement.
   *
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#triggerWhenDo
   */
  private List<CoreLabel> processWhenDo(TokenSequenceMatcher matcher) {
    // Grab the prefix of the sentence
    List<CoreLabel> sentence = (List<CoreLabel>) matcher.groupNodes("$statement_body");

    // Add the "at" part
    List<CoreLabel> in = (List<CoreLabel>) matcher.groupNodes("$in");
    if (in != null && in.size() > 0) {
      sentence.addAll(in);
    } else {
      sentence.add(WORD_IN);
    }

    // Add the location
    sentence.add(WORD_MISSING_TIME);

    // Return
    return sentence;
  }

  /**
   * The pattern for "what have..."  sentences.
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#processWhereIs(edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher)
   */
  private final TokenSequencePattern triggerWhatHave = TokenSequencePattern.compile(
      "[{lemma:what; tag:/W.*/}] " +
          "(?$have [ {lemma:/have/} ]) " +
          "(?$pre_verb [!{tag:/V.*/}]+ )? " +
          "(?$verb [tag:/V.*/] [tag:IN]? ) " +
          "(?$post_verb []+ )? " +
          "(?$punct [word:/[?\\.!]/])" );

  /**
   *
   * Process sentences matching the "when do ..." pattern.
   *
   * @param matcher The matcher that matched the pattern.
   *
   * @return The converted statement.
   *
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#triggerWhenDo
   */
  private List<CoreLabel> processWhatHave(TokenSequenceMatcher matcher) {
    List<CoreLabel> sentence = new ArrayList<>();

    // Grab the prefix of the sentence
    List<CoreLabel> preVerb = (List<CoreLabel>) matcher.groupNodes("$pre_verb");
    if (preVerb != null) {
      sentence.addAll(preVerb);
    }

    // Add "thing have verb" or "have verb thing"
    if (sentence.size() == 0) {
      sentence.add(WORD_MISSING);
      sentence.addAll( (List<CoreLabel>) matcher.groupNodes("$have") );
      sentence.addAll( (List<CoreLabel>) matcher.groupNodes("$verb") );
    } else {
      sentence.addAll( (List<CoreLabel>) matcher.groupNodes("$have") );
      sentence.addAll( (List<CoreLabel>) matcher.groupNodes("$verb") );
      sentence.add(WORD_MISSING);
    }

    List<CoreLabel> postVerb = (List<CoreLabel>) matcher.groupNodes("$post_verb");
    if (postVerb != null) {
      sentence.addAll(postVerb);
    }

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
    if ((matcher = triggerWhatIsThere.matcher(question)).matches()) {  // must come before triggerWhatIs
      return Collections.singletonList(processWhatIsThere(matcher));
    } else if ((matcher = triggerWhNNIs.matcher(question)).matches()) {  // must come before triggerWhatIs
      return Collections.singletonList(processWhNNIs(matcher));
    } else if ((matcher = triggerWhNNHave.matcher(question)).matches()) {  // must come before triggerWhatHave
      return Collections.singletonList(processWhNNHave(matcher));
    } else if ((matcher = triggerWhNNHaveNN.matcher(question)).matches()) {  // must come before triggerWhatHave
      return Collections.singletonList(processWhNNHaveNN(matcher));
    } else if ((matcher = triggerWhatIs.matcher(question)).matches()) {
      return Collections.singletonList(processWhatIs(matcher));
    } else if ((matcher = triggerWhatHave.matcher(question)).matches()) {
      return Collections.singletonList(processWhatHave(matcher));
    } else if ((matcher = triggerWhereDo.matcher(question)).matches()) {
      return Collections.singletonList(processWhereDo(matcher));
    } else if ((matcher = triggerWhereIs.matcher(question)).matches()) {
      return Collections.singletonList(processWhereIs(matcher));
    } else if ((matcher = triggerWhoIs.matcher(question)).matches()) {
      return Collections.singletonList(processWhoIs(matcher));
    } else if ((matcher = triggerWhoDid.matcher(question)).matches()) {
      return Collections.singletonList(processWhoDid(matcher));
    } else if ((matcher = triggerWhatDo.matcher(question)).matches()) {
      return Collections.singletonList(processWhatDo(matcher));
    } else if ((matcher = triggerWhenDo.matcher(question)).matches()) {
      return Collections.singletonList(processWhenDo(matcher));
    } else {
      return Collections.emptyList();
    }
  }

}
