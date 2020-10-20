package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.Lazy;
import edu.stanford.nlp.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A mapping from verbs to their different tenses.
 * This is English-only, for now.
 *
 * @author <a href="mailto:angeli@cs.stanford.edu">Gabor Angeli</a>
 */
@SuppressWarnings("unused")
public enum VerbTense {

  INFINITIVE(0),
  SINGULAR_PRESENT_FIRST_PERSON(1),
  SINGULAR_PRESENT_SECOND_PERSON(2),
  SINGULAR_PRESENT_THIRD_PERSON(3),
  PRESENT_PLURAL(4),
  PRESENT_PARTICIPLE(5),
  SINGULAR_PAST_FIRST_PERSON(6),
  SINGULAR_PAST_SECOND_PERSON(7),
  SINGULAR_PAST_THIRD_PERSON(8),
  PAST_PLURAL(9),
  PAST(10),
  PAST_PARTICIPLE(11),
  ;


  /**
   * The data for common verb conjugations.
   */
  private static final Lazy<Map<String, String[]>> ENGLISH_TENSES = Lazy.of(() -> {
    Map<String, String[]> tenseMap = new HashMap<>();
    try (BufferedReader reader = IOUtils.readerFromString("edu/stanford/nlp/models/naturalli/conjugations_english.tab")) {
      String line;
      while ( (line = reader.readLine()) != null) {
        String[] fields = StringUtils.splitOnChar(line, '\t');
        assert fields.length == 24;
        tenseMap.put(fields[0], fields);
      }
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
    return Collections.unmodifiableMap(tenseMap);
  });


  /** The column of the file for the tense. */
  private final int column;


  VerbTense(int column) {
    this.column = column;
  }


  /**
   * Get the correct verb tense for the verb tense's features.
   *
   * @param past If true, this is a past-tense verb.
   * @param plural If true, this is a plural verb.
   * @param participle If true, this is a participle verb.
   * @param person 1st, 2nd, or 3rd person: corresponds to 1,2, or 3 for this argument.
   *
   * @return The verb tense corresponding to this information
   */
  @SuppressWarnings("Duplicates")
  public static VerbTense of(boolean past, boolean plural, boolean participle, int person) {
    if (past) {
      if (plural) {
        return PAST_PLURAL;
      }
      if (participle) {
        return PAST_PARTICIPLE;
      }
      switch (person) {
        case 1:
          return SINGULAR_PAST_FIRST_PERSON;
        case 2:
          return SINGULAR_PAST_SECOND_PERSON;
        case 3:
        default:
          return SINGULAR_PAST_THIRD_PERSON;
      }
    } else {
      if (plural) {
        return PRESENT_PLURAL;
      }
      if (participle) {
        return PRESENT_PARTICIPLE;
      }
      switch (person) {
        case 1:
          return SINGULAR_PRESENT_FIRST_PERSON;
        case 2:
          return SINGULAR_PRESENT_SECOND_PERSON;
        case 3:
        default:
          return SINGULAR_PRESENT_THIRD_PERSON;

      }
    }
  }


  /**
   * Apply this tense to the given verb.
   *
   * @param lemma The verb to conjugate.
   * @param negated If true, this verb is negated.
   *
   * @return The conjugated verb.
   */
  public String conjugateEnglish(String lemma, boolean negated) {
    String[] data = ENGLISH_TENSES.get().get(lemma);
    if (data != null) {
      String conjugated = data[negated ? column + 12 : column];
      if (!"".equals(conjugated)) {
        // case: we found a match
        return conjugated;
      } else if (negated) {
        // case: try the unnegated form
        conjugated = data[column];
        if (!"".equals(conjugated)) {
          return conjugated;
        }
      }
      // case: tense not explicit in map
      if (column >= 0 && column < 6) {
        conjugated = data[INFINITIVE.column];
      } else {
        conjugated = data[PAST.column];
      }
      if (!"".equals(conjugated)) {
        return conjugated;
      } else {
        return lemma;
      }
    } else {
      // case: word not in dictionary
      return lemma;
    }
  }


  /**
   * @see #conjugateEnglish(String, boolean)
   */
  public String conjugateEnglish(String lemma) {
    return conjugateEnglish(lemma, false);
  }


  /**
   * @see #conjugateEnglish(String, boolean)
   */
  public String conjugateEnglish(CoreLabel token, boolean negated) {
    return conjugateEnglish(Optional.ofNullable(token.lemma()).orElse(token.word()), negated);
  }


  /**
   * @see #conjugateEnglish(String, boolean)
   */
  public String conjugateEnglish(CoreLabel token) {
    return conjugateEnglish(Optional.ofNullable(token.lemma()).orElse(token.word()), false);

  }
}
