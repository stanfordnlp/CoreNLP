package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

import java.io.IOException;
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
    setBefore(" ");
    setAfter(" ");
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
    setBefore(" ");
    setAfter(" ");
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
    setBefore(" ");
    setAfter(" ");
    set(UnknownTokenMarker.class, true);
  }};

  /** The missing word marker typed as a time. */
  private final CoreLabel WORD_ADJECTIVE = new CoreLabel(){{
    setWord("adjective");
    setValue("adjective");
    setLemma("adjective");
    setTag("JJ");
    setNER("O");
    setIndex(-1);
    setBeginPosition(-1);
    setEndPosition(-1);
    setBefore(" ");
    setAfter(" ");
    set(UnknownTokenMarker.class, true);
  }};

  /** The missing word marker typed as a time. */
  private final CoreLabel WORD_WAY = new CoreLabel(){{
    setWord("way");
    setValue("way");
    setLemma("way");
    setTag("RB");
    setNER("O");
    setIndex(-1);
    setBeginPosition(-1);
    setEndPosition(-1);
    setBefore(" ");
    setAfter(" ");
    set(UnknownTokenMarker.class, true);
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
    setBefore(" ");
    setAfter(" ");
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
    setBefore(" ");
    setAfter(" ");
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
    setBefore(" ");
    setAfter(" ");
  }};

  /** The word "to" as a CoreLabel */
  private final CoreLabel WORD_TO = new CoreLabel(){{
    setWord("to");
    setValue("to");
    setLemma("to");
    setTag("TO");
    setNER("O");
    setIndex(-1);
    setBeginPosition(-1);
    setEndPosition(-1);
    setBefore(" ");
    setAfter(" ");
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
    LinkedList<CoreLabel> body = new LinkedList<>((List<CoreLabel>) matcher.groupNodes("$statement_body"));

    // Add the "be" token
    // [Gabor]: This is basically the most principled code I've ever written.
    // [Gabor]: If the "be" gets misplaced, God help us all.
    // [Gabor]: Mostly you. I'm graduated and gone, so you'll need most of the help.
    List<CoreLabel> be = (List<CoreLabel>) matcher.groupNodes("$be");
    List<CoreLabel> suffix = (List<CoreLabel>) matcher.groupNodes("$suffix");
    boolean addedBe = false;
    boolean addedSuffix = false;
    if (body.size() > 1 && !"PRP".equals(body.get(0).tag())) {
      for (int i = 2; i < body.size(); ++i) {
        CoreLabel tokI = body.get(i);
        if (tokI.tag() != null &&
            ((tokI.tag().startsWith("V") && !tokI.tag().equals("VBD") && !"be".equals(body.get(i - 1).lemma())) ||
                (tokI.tag().startsWith("J") && suffix != null) ||
                (tokI.tag().startsWith("D") && suffix != null) ||
                (tokI.tag().startsWith("R") && suffix != null))) {
          body.add(i, be.get(0));
          i += 1;
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
    }

    // Tweak to handle dropped prepositions
    List<CoreLabel> prepNum = (List<CoreLabel>) matcher.groupNodes("$prep_num");
    if (prepNum != null) {
      body.add(prepNum.get(0));
      body.add(WORD_IN);
      body.add(prepNum.get(1));
    }
    // Add the "be" and suffix
    if (!addedSuffix && suffix != null) {
      body.addAll(suffix);
    }
    if (!addedBe) {
      if (body.size() > 1 && "PRP".equals(body.get(0).tag())) {
        body.add(1, be.get(0));
      } else {
        body.addAll(be);
      }
    }

    // Drop a final 'do'
    if (body.size() > 1 && "do".equals(body.get(body.size() - 1).word())) {
      body = new LinkedList<>(body.subList(0, body.size() - 1));
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
        if (body.size() >= 2 &&
            body.get(body.size() - 2).tag() != null &&
            body.get(body.size() - 2).tag().startsWith("N") &&
            !body.get(body.size() - 1).tag().equals("IN")) {
          // This is a bit of a giant hack. But:
          // 1. Add 'thing is' to the beginning of the sentence
          // 2. remove the 'be' we added to the end of the sentence above
          if (!addedBe) {
            Collections.reverse(be);
            be.forEach(body::addFirst);
          }
          body.addFirst(WORD_MISSING);
          Iterator<CoreLabel> beIter = be.iterator();
          if (beIter.hasNext() && body.getLast() == beIter.next()) {
            body.removeLast();
          }
        } else {
          body.addLast(WORD_MISSING);
        }
      }
    } else {
      // (case: typed)
      for (CoreLabel obj : objType) {
        obj.set(UnknownTokenMarker.class, true);
      }
      body.addAll(objType);
    }

    // Swap determiner + be -> be determiner
    for (int k = 1; k < body.size(); ++k) {
      if ("DT".equals(body.get(k - 1).tag()) && "be".equals(body.get(k).lemma())) {
        Collections.swap(body, k - 1, k);
      }
    }
    // Swap IN + be -> be IN
    if (body.stream().noneMatch(x -> x.tag() != null && x.tag().startsWith("V") && (be.isEmpty() || x != be.get(0)))) {
      for (int k = 1; k < body.size(); ++k) {
        if ("IN".equals(body.get(k - 1).tag()) && "be".equals(body.get(k).lemma())) {
          Collections.swap(body, k - 1, k);
        }
      }
    }

    // Return
    return body;
  }

  /**
   * The pattern for "what NN will (I|NN) ..." sentences.
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#processWhNNIs(edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher)
   */
  private final TokenSequencePattern triggerWhNNWill = TokenSequencePattern.compile(
      "[{lemma:/what|which/; tag:/W.*/}] " +
          "(?$answer_type [!{lemma:be} & !{pos:\"PRP$\"} & !{pos:MD}]+) " +
          "(?$will [{pos:MD}]) " +
          "(?$subj [{pos:/NN.?.?/} | {pos:PRP}]+) " +
          "(?$statement_body [!{pos:IN}]+) " +
          "(?$pp_prefix [{pos:IN}]*) " +
          "(?$pp [{pos:IN}]) " +
          "(?$pp_body []*) " +
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
  private List<CoreLabel> processWhNNWill(TokenSequenceMatcher matcher) {
    List<CoreLabel> sentence = (List<CoreLabel>) matcher.groupNodes("$subj");
    sentence.addAll((Collection<CoreLabel>) matcher.groupNodes("$will"));
    sentence.addAll((Collection<CoreLabel>) matcher.groupNodes("$statement_body"));
    Collection<CoreLabel> answerType = (Collection<CoreLabel>) matcher.groupNodes("$answer_type");
    for (CoreLabel lbl : answerType) {
      lbl.set(UnknownTokenMarker.class, true);
    }
    sentence.addAll((Collection<CoreLabel>) matcher.groupNodes("$pp_prefix"));
    sentence.addAll(answerType);
    sentence.addAll((Collection<CoreLabel>) matcher.groupNodes("$pp"));
    sentence.addAll((Collection<CoreLabel>) matcher.groupNodes("$pp_body"));
    return sentence;
  }

  /**
   * The pattern for "what/which NN is ..." sentences.
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#processWhNNIs(edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher)
   */
  private final TokenSequencePattern triggerWhNNIs = TokenSequencePattern.compile(
      "[{lemma:/what|which/; tag:/W.*/}] " +
          "(?$answer_type [!{lemma:be} & !{pos:\"PRP$\"} | {word:i}]+) " +
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
   * The pattern for "what/which NN (have|does|is) ..." sentences.
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#processWhNNHaveIs(edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher)
   */
  private final TokenSequencePattern triggerWhNNHave = TokenSequencePattern.compile(
      "[{lemma:/what|which/; tag:/W.*/}] " +
          "(?$answer_type [!{tag:/V.*/}]+) " +
          "(?$have [{lemma:have} | {lemma:do} | {lemma:be}] ) " +
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
  private List<CoreLabel> processWhNNHaveIs(TokenSequenceMatcher matcher) {
    // Add prefix
    List<CoreLabel> sentence = new ArrayList<>((Collection<CoreLabel>) matcher.groupNodes("$pre_verb"));

    // Add have/be
    List<CoreLabel> have = (List<CoreLabel>) matcher.groupNodes("$have");
    if (have != null && have.size() > 0 && have.get(0).lemma() != null &&
        (have.get(0).lemma().equalsIgnoreCase("have") || have.get(0).lemma().equalsIgnoreCase("be"))) {
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
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#processWhereDo(edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher, List)
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
   * @param question The original question we asked
   *
   * @return The converted statement.
   *
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#triggerWhereDo
   */
  private List<CoreLabel> processWhereDo(TokenSequenceMatcher matcher, List<CoreLabel> question) {
    // Get the "at" preposition and the "location" missing marker to use
    List<CoreLabel> specloc = (List<CoreLabel>) matcher.groupNodes("$loc");
    CoreLabel wordAt = WORD_AT;
    CoreLabel missing = WORD_MISSING_LOCATION;
    if (specloc != null && fromNotAtDict.contains(specloc.get(0).word())) {
      wordAt = WORD_FROM;
      missing = WORD_MISSING;
    }
    String questionLemmas = " " + StringUtils.join(question.stream().map(CoreLabel::lemma), " ") + " ";
    if (questionLemmas.contains(" go ") && !questionLemmas.contains(" go to ")) {
      wordAt = WORD_TO;
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
//    if (specloc != null && at != null) {
//      sentence.add(WORD_COMMA);
//      sentence.addAll(specloc);
//    }

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
          "(?$subject [{tag:/NN.?.?/}]+ ((in|at|of) [{tag:/NN.?.?/}]+)* )? " +
          "(?$statement_body []*?)? " +
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
    List<CoreLabel> sentence = new ArrayList<>();

    // The subject of the sentence
    List<CoreLabel> subject = (List<CoreLabel>) matcher.groupNodes("$subject");
    if (subject != null) {
      sentence.addAll(subject);
    }

    // Add the "is" part
    List<CoreLabel> be = (List<CoreLabel>) matcher.groupNodes("$be");
    sentence.addAll(be);

    // The extra body of the sentence
    List<CoreLabel> body = (List<CoreLabel>) matcher.groupNodes("$statement_body");
    if (body != null) {
      sentence.addAll(body);
    }

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
   * The pattern for "what do..."  sentences.
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
   * The pattern for "when do..."  sentences.
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#processHow(edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher)
   */
  private final TokenSequencePattern triggerHow = TokenSequencePattern.compile(
      "([{lemma:/[Hh]ow/; tag:/W.*/}] | /[Ww]hat/ [{lemma:be}] /ways?/ (?$prp0 [{tag:/PRP.?/} | {word:i}]) ) " +
          "((?$do [ {lemma:/do/} | {lemma:can}]) | (?$jj [ {pos:JJ} ]{0,3}) (?$be [ {lemma:be} ])) " +
          "(?$prp1 [{tag:/PRP.?/} | {word:i}])? " +
          "(?$statement_body []+?) " +
          "(?$punct [word:/[?\\.!]/])" );

  /**
   * Process sentences matching 'how...'
   *
   * @param matcher The matcher that matched the pattern.
   *
   * @return The converted statement.
   *
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#triggerHow
   */
  private List<CoreLabel> processHow(TokenSequenceMatcher matcher) {
    List<CoreLabel> sentence = new ArrayList<>();

    // Resolve prepositions
    List<CoreLabel> prp = (List<CoreLabel>) matcher.groupNodes("$prp0");
    if (prp == null || prp.isEmpty()) {
      prp = (List<CoreLabel>) matcher.groupNodes("$prp1");
    }
    if (prp != null && !prp.isEmpty()) {
      sentence.addAll(prp);
      List<CoreLabel> doOrCan = (List<CoreLabel>) matcher.groupNodes("$do");
      if (doOrCan != null && doOrCan.size() == 1 && "can".equalsIgnoreCase(doOrCan.get(0).lemma())) {
        sentence.addAll(doOrCan);
      }
    }

    // Add the meat
    sentence.addAll((List<CoreLabel>) matcher.groupNodes("$statement_body"));

    // Add an optional 'be'
    List<CoreLabel> wordBe = (List<CoreLabel>) matcher.groupNodes("$be");
    if (wordBe != null) {
      sentence.addAll(wordBe);
      sentence.add(WORD_ADJECTIVE);
    } else {
      sentence.add(WORD_WAY);
    }

    // Return
    return sentence;
  }


  /**
   * The pattern for "how much...do..."  sentences.
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#processHowMuchDo(edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher)
   */
  private final TokenSequencePattern triggerHowMuchDo = TokenSequencePattern.compile(
      "[{lemma:/[Hh]ow/; tag:/W.*/}] " +
          "(much | many) [{pos:NN}]{0,10} " +
          "((?$do [ {lemma:/do/} | {lemma:can}]) | (?$jj [ {pos:JJ} ]) (?$be [ {lemma:be} ])) " +
          "(?$prefix [!{lemma:to}]{1,25}) " +
          "(?$connective [{lemma:to}])? " +
          "(?$suffix [!{lemma:to}]{1,25}) " +
          "(?$punct [word:/[?\\.!]/])" );

  /**
   * Process sentences matching 'how much do...'
   *
   * @param matcher The matcher that matched the pattern.
   *
   * @return The converted statement.
   *
   * @see edu.stanford.nlp.naturalli.QuestionToStatementTranslator#triggerHowMuchDo
   */
  private List<CoreLabel> processHowMuchDo(TokenSequenceMatcher matcher) {
    List<CoreLabel> sentence = new ArrayList<>((List<CoreLabel>) matcher.groupNodes("$prefix"));
    List<CoreLabel> connective = (List<CoreLabel>) matcher.groupNodes("$connective");
    if (connective != null && !connective.isEmpty()) {
      sentence.add(WORD_MISSING);
      sentence.addAll(connective);
      sentence.addAll((List<CoreLabel>) matcher.groupNodes("$suffix"));
    } else {
      sentence.addAll((List<CoreLabel>) matcher.groupNodes("$suffix"));
      sentence.add(WORD_WAY);
    }
    return sentence;
  }


  /**
   * Post-process a statement, e.g., replacing 'I' with 'you', capitalizing the
   * first letter (and not capitalizing the other letters), etc.
   *
   * @param question The original question that we converted to a statement.
   * @param statement The statement to post-process.
   *
   * @return The post-processed utterance.
   */
  private static List<List<CoreLabel>> postProcess(List<CoreLabel> question, List<CoreLabel> statement) {
    // 1. Replace 'i' with 'you', etc.
    for (CoreLabel token : statement) {
      String originalText = token.originalText();
      if (originalText == null || "".equals(originalText)) {
        originalText = token.word();
      }
      switch (originalText.toLowerCase()) {
        case "i":
          token.set(CoreAnnotations.StatementTextAnnotation.class, "you");
          break;
        case "you":
          token.set(CoreAnnotations.StatementTextAnnotation.class, "i");
          break;
        case "my":
          token.set(CoreAnnotations.StatementTextAnnotation.class, "your");
          break;
        case "your":
          token.set(CoreAnnotations.StatementTextAnnotation.class, "my");
          break;
        default:
          token.set(CoreAnnotations.StatementTextAnnotation.class, originalText);
          break;
      }
    }

    // 2. Properly upper-case the sentence
    for (int i = 0; i < statement.size(); ++i) {
      CoreLabel token = statement.get(i);
      String originalText = token.get(CoreAnnotations.StatementTextAnnotation.class);
      String uppercase = originalText.length() == 0
              ? originalText
              : Character.toUpperCase(originalText.charAt(0)) + originalText.substring(1);
      if (i == 0) {
        token.set(CoreAnnotations.StatementTextAnnotation.class, uppercase);
      } else if (Optional.ofNullable(token.tag()).map(x -> x.startsWith("NNP")).orElse(false)) {
        token.set(CoreAnnotations.StatementTextAnnotation.class, uppercase);
      } else if ("i".equals(originalText.toLowerCase())) {
          token.set(CoreAnnotations.StatementTextAnnotation.class, uppercase);
      } else {
          token.set(CoreAnnotations.StatementTextAnnotation.class, originalText.toLowerCase());
      }
    }

    // 3. Fix the tense of the question
    // 3.1. Get tense + participality(sp?)
    boolean past = false;
    boolean participle = false;
    TENSE_LOOP: for (CoreLabel token : question) {
      switch (Optional.ofNullable(token.lemma()).orElse(token.word()).toLowerCase()) {
        case "do":
          switch (token.tag()) {
            case "VBG":
              participle = true;
            case "VB":
              past = false;
              break TENSE_LOOP;
            case "VBN":
              participle = true;
            case "VBD":
              past = true;
              break TENSE_LOOP;
          }
          break;
      }
    }
    // 3.2. Get plurality
    boolean plural = false;
    PLURALITY_LOOP: for (CoreLabel token : statement) {
      switch (Optional.ofNullable(token.tag()).orElse("")) {
        case "NN":
        case "NNP":
          plural = false;
          break PLURALITY_LOOP;
        case "NNS":
        case "NNPS":
          plural = true;
          break PLURALITY_LOOP;
      }
    }
    // 3.3. Get person
    int person = 3;  // 1st, 2nd, or 3rd
    PERSON_LOOP: for (CoreLabel token : statement) {
      if (Optional.ofNullable(token.tag()).map(x -> x.startsWith("N")).orElse(false)) {
        break;
      }
      switch (token.get(CoreAnnotations.StatementTextAnnotation.class).toLowerCase()) {
        case "us":
          plural = true;
          person = 1;
          break PERSON_LOOP;
        case "i":
        case "me":
        case "mine":
        case "my":
          plural = false;
          person = 1;
          break PERSON_LOOP;
        case "you":
          plural = false;
          person = 2;
          break PERSON_LOOP;
        case "they":
        case "them":
          plural = true;
          person = 2;
          break PERSON_LOOP;
        case "he":
        case "she":
        case "him":
        case "her":
        case "it":
          plural = false;
          person = 3;
          break PERSON_LOOP;
      }
    }
    // 3.4. Conjugate the verb
    VerbTense tense = VerbTense.of(past, plural, participle, person);
    boolean foundVerb = false;
    for (CoreLabel token : statement) {
      if (Optional.ofNullable(token.tag()).map(x -> x.startsWith("V") && !x.equals("VBG") && !"be".equals(token.word())).orElse(false)) {
        foundVerb = true;
        token.set(CoreAnnotations.StatementTextAnnotation.class,
            tense.conjugateEnglish(token.get(CoreAnnotations.StatementTextAnnotation.class), false));
      }
    }
    if (!foundVerb) {
      for (CoreLabel token : statement) {
        if (Optional.ofNullable(token.tag()).map("NN"::equals).orElse(false)) {
          token.set(CoreAnnotations.StatementTextAnnotation.class,
              tense.conjugateEnglish(token.get(CoreAnnotations.StatementTextAnnotation.class), false));
        }
      }
    }

    // Return
    return Collections.singletonList(statement);
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
      return postProcess(question, processWhatIsThere(matcher));
    } else if ((matcher = triggerWhNNWill.matcher(question)).matches()) {  // must come before triggerWhNNIs
      return postProcess(question, processWhNNWill(matcher));
    } else if ((matcher = triggerWhNNIs.matcher(question)).matches()) {  // must come before triggerWhatIs
      return postProcess(question, processWhNNIs(matcher));
    } else if ((matcher = triggerWhNNHave.matcher(question)).matches()) {  // must come before triggerWhatHave
      return postProcess(question, processWhNNHaveIs(matcher));
    } else if ((matcher = triggerWhNNHaveNN.matcher(question)).matches()) {  // must come before triggerWhatHave
      return postProcess(question, processWhNNHaveNN(matcher));
    } else if ((matcher = triggerHow.matcher(question)).matches()) {  // must come before triggerWhatIs
      return postProcess(question, processHow(matcher));
    } else if ((matcher = triggerHowMuchDo.matcher(question)).matches()) {
      return postProcess(question, processHowMuchDo(matcher));
    } else if ((matcher = triggerWhatIs.matcher(question)).matches()) {
      return postProcess(question, processWhatIs(matcher));
    } else if ((matcher = triggerWhatHave.matcher(question)).matches()) {
      return postProcess(question, processWhatHave(matcher));
    } else if ((matcher = triggerWhereDo.matcher(question)).matches()) {
      return postProcess(question, processWhereDo(matcher, question));
    } else if ((matcher = triggerWhereIs.matcher(question)).matches()) {
      return postProcess(question, processWhereIs(matcher));
    } else if ((matcher = triggerWhoIs.matcher(question)).matches()) {
      return postProcess(question, processWhoIs(matcher));
    } else if ((matcher = triggerWhoDid.matcher(question)).matches()) {
      return postProcess(question, processWhoDid(matcher));
    } else if ((matcher = triggerWhatDo.matcher(question)).matches()) {
      return postProcess(question, processWhatDo(matcher));
    } else if ((matcher = triggerWhenDo.matcher(question)).matches()) {
      return postProcess(question, processWhenDo(matcher));
    } else {
      return Collections.emptyList();
    }
  }


  public static void main(String[] args) throws IOException {
    StanfordCoreNLP pipeline = new StanfordCoreNLP(PropertiesUtils.asProperties("annotators", "tokenize,ssplit,pos,lemma"));
    QuestionToStatementTranslator translator = new QuestionToStatementTranslator();

    if (args.length > 0) {
      int upto = 0;
      for (String question : ObjectBank.getLineIterator(args[0])) {
        System.out.println(upto);
        System.out.println(question);
        Annotation ann = new Annotation(question);
        pipeline.annotate(ann);
        List<CoreLabel> tokens = ann.get(CoreAnnotations.TokensAnnotation.class);
        List<List<CoreLabel>> statements = translator.toStatement(tokens);
        for (List<CoreLabel> statement : statements) {
          // System.out.println("  -> " + StringUtils.join(statement.stream().map(CoreLabel::word), " "));
          // System.out.println("  -> " + StringUtils.join(statement.stream().map(cl -> cl.get(UnknownTokenMarker.class)), " "));
          System.out.println(StringUtils.join(statement.stream().map(cl -> {
            if (cl.get(UnknownTokenMarker.class) != null && cl.get(UnknownTokenMarker.class)) {
              return '[' + (cl.get(CoreAnnotations.StatementTextAnnotation.class) == null ? cl.word(): cl.get(CoreAnnotations.StatementTextAnnotation.class)) + ']';
            } else {
              if (cl.get(CoreAnnotations.StatementTextAnnotation.class) != null) {
                return cl.get(CoreAnnotations.StatementTextAnnotation.class);
              } else {
                return cl.word();
              }
            }
          }), " ") + ".");
        }
        System.out.println("----------");
        upto++;
      }
    } else {
      IOUtils.console("question> ", question -> {
        System.out.println(question);
        Annotation ann = new Annotation(question);
        pipeline.annotate(ann);
        List<CoreLabel> tokens = ann.get(CoreAnnotations.TokensAnnotation.class);
        List<List<CoreLabel>> statements = translator.toStatement(tokens);
        for (List<CoreLabel> statement : statements) {
          System.out.println("  -> " + StringUtils.join(statement.stream().map(CoreLabel::word), " "));
        }
      });
    }
  }

}
