package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.util.Pair;

import java.util.*;

import static edu.stanford.nlp.naturalli.NaturalLogicRelation.*;

/**
 * A collection of quantifiers. This is the exhaustive list of quantifiers our system knows about.
 *
 * @author Gabor Angeli
 */
public enum Operator {

  // "All" quantifiers
  ALL("all",                                         FORWARD_ENTAILMENT, "anti-additive", "multiplicative"),
  EVERY("every",                                     FORWARD_ENTAILMENT, "anti-additive", "multiplicative"),
  ANY("any",                                         FORWARD_ENTAILMENT, "anti-additive", "multiplicative"),
  EACH("each",                                       FORWARD_ENTAILMENT, "anti-additive", "multiplicative"),
  THE_LOT_OF("the lot of",                           FORWARD_ENTAILMENT, "anti-additive", "multiplicative"),
  ALL_OF("all of",                                   FORWARD_ENTAILMENT, "anti-additive", "multiplicative"),
  EACH_OF("each of",                                 FORWARD_ENTAILMENT, "anti-additive", "multiplicative"),
  FOR_ALL("for all",                                 FORWARD_ENTAILMENT, "anti-additive", "multiplicative"),
  FOR_EVERY("for every",                             FORWARD_ENTAILMENT, "anti-additive", "multiplicative"),
  FOR_EACH("for each",                               FORWARD_ENTAILMENT, "anti-additive", "multiplicative"),
  EVERYONE("everyone",                               FORWARD_ENTAILMENT, "anti-additive", "multiplicative"),
  NUM("--num--",                                     FORWARD_ENTAILMENT, "anti-additive", "multiplicative"),  // TODO check me
  NUM_NUM("--num-- --num--",                         FORWARD_ENTAILMENT, "anti-additive", "multiplicative"),  // TODO check me
  NUM_NUM_NUM("--num-- --num-- --num--",             FORWARD_ENTAILMENT, "anti-additive", "multiplicative"),  // TODO check me
  NUM_NUM_NUM_NUM("--num-- --num-- --num-- --num--", FORWARD_ENTAILMENT, "anti-additive", "multiplicative"),  // TODO check me
  FEW("few",                                         FORWARD_ENTAILMENT, "anti-additive", "multiplicative"),  // TODO check me
  IMPLICIT_NAMED_ENTITY("__implicit_named_entity__", FORWARD_ENTAILMENT, "additive",      "multiplicative"),  // TODO check me

  // "No" quantifiers
  NO("no",               INDEPENDENCE, "anti-additive", "anti-additive"),
  NEITHER("neither",     INDEPENDENCE, "anti-additive", "anti-additive"),
  NO_ONE("no one",       INDEPENDENCE, "anti-additive", "anti-additive"),
  NOBODY("nobody",       INDEPENDENCE, "anti-additive", "anti-additive"),
  NOT("not",             INDEPENDENCE, "anti-additive", "anti-additive"),
  BUT("but",             INDEPENDENCE, "anti-additive", "anti-additive"),
  EXCEPT("except",       INDEPENDENCE, "anti-additive", "anti-additive"),
  UNARY_NO("no",         INDEPENDENCE, "anti-additive"),
  UNARY_NOT("not",       INDEPENDENCE, "anti-additive"),
  UNARY_NO_ONE("no one", INDEPENDENCE, "anti-additive"),
  UNARY_NT("n't",        INDEPENDENCE, "anti-additive"),
  UNARY_BUT("but",       INDEPENDENCE, "anti-additive"),
  UNARY_EXCEPT("except", INDEPENDENCE, "anti-additive"),

  // A general quantifier for all "doubt"-like words
  GENERAL_NEG_POLARITY("neg_polarity_trigger",   INDEPENDENCE, "anti-additive"),

  // "Some" quantifiers
  SOME("some",                     FORWARD_ENTAILMENT, "additive", "additive"),
  SEVERAL("several",               FORWARD_ENTAILMENT, "additive", "additive"),
  EITHER("either",                 FORWARD_ENTAILMENT, "additive", "additive"),
  A("a",                           FORWARD_ENTAILMENT, "additive-multiplicative", "additive-multiplicative"),
  THE("the",                       FORWARD_ENTAILMENT, "additive-multiplicative", "additive-multiplicative"),
  LESS_THAN("less than --num--",   FORWARD_ENTAILMENT, "additive", "additive"),
  SOME_OF("some of",               FORWARD_ENTAILMENT, "additive", "additive"),
  ONE_OF("one of",                 FORWARD_ENTAILMENT, "additive", "additive"),
  AT_LEAST("at least --num--",     FORWARD_ENTAILMENT, "additive", "additive"),
  A_FEW("a few",                   FORWARD_ENTAILMENT, "additive", "additive"),
  AT_LEAST_A_FEW("at least a few", FORWARD_ENTAILMENT, "additive", "additive"),
  THERE_BE("there be",             FORWARD_ENTAILMENT, "additive", "additive"),
  THERE_BE_A_FEW("there be a few", FORWARD_ENTAILMENT, "additive", "additive"),
  THERE_EXIST("there exist",       FORWARD_ENTAILMENT, "additive", "additive"),
  NUM_OF("--num-- of",             FORWARD_ENTAILMENT, "additive", "additive"),

  // "Not All" quantifiers
  NOT_ALL("not all",     INDEPENDENCE, "additive", "anti-multiplicative"),
  NOT_EVERY("not every", INDEPENDENCE, "additive", "anti-multiplicative"),

  // "Most" quantifiers
  // TODO(gabor) check these
  MOST("most",                  FORWARD_ENTAILMENT, "nonmonotone", "multiplicative"),
  MORE("more",                  FORWARD_ENTAILMENT, "nonmonotone", "multiplicative"),
  MANY("many",                  FORWARD_ENTAILMENT, "nonmonotone", "multiplicative"),
  ENOUGH("enough",              FORWARD_ENTAILMENT, "nonmonotone", "multiplicative"),
  MORE_THAN("more than __num_", FORWARD_ENTAILMENT, "nonmonotone", "multiplicative"),
  LOTS_OF("lots of",            FORWARD_ENTAILMENT, "nonmonotone", "multiplicative"),
  PLENTY_OF("plenty of",        FORWARD_ENTAILMENT, "nonmonotone", "multiplicative"),
  HEAPS_OF("heap of",           FORWARD_ENTAILMENT, "nonmonotone", "multiplicative"),
  A_LOAD_OF("a load of",        FORWARD_ENTAILMENT, "nonmonotone", "multiplicative"),
  LOADS_OF("load of",           FORWARD_ENTAILMENT, "nonmonotone", "multiplicative"),
  TONS_OF("ton of",             FORWARD_ENTAILMENT, "nonmonotone", "multiplicative"),
  BOTH("both",                  FORWARD_ENTAILMENT, "nonmonotone", "multiplicative"),
  JUST_NUM("just --num--",      FORWARD_ENTAILMENT, "nonmonotone", "multiplicative"),
  ONLY_NUM("only --num--",      FORWARD_ENTAILMENT, "nonmonotone", "multiplicative"),

  // Strange cases
  AT_MOST_NUM("at most --num--", FORWARD_ENTAILMENT, "anti-additive", "anti-additive"),
  ;

  public static final Set<String> GLOSSES = Collections.unmodifiableSet(new HashSet<String>() {{
    for (Operator q : Operator.values()) {
      add(q.surfaceForm);
    }
  }});

  /**
   * An ordered list of the known operators, by token length (descending). This ensures that we're matching the
   * widest scoped operator.
   */
  public static final List<Operator> valuesByLengthDesc = Collections.unmodifiableList(new ArrayList<Operator>(){{
    for (Operator op : values()) {
      add(op);
    }
    Collections.sort(this, (a, b) -> b.surfaceForm.split(" ").length - a.surfaceForm.split(" ").length);
  }});

  public final String surfaceForm;
  public final Monotonicity subjMono;
  public final MonotonicityType subjType;
  public final Monotonicity objMono;
  public final MonotonicityType objType;
  public final NaturalLogicRelation deleteRelation;

  Operator(String surfaceForm, NaturalLogicRelation deleteRelation, String subjMono, String objMono) {
    this.surfaceForm = surfaceForm;
    this.deleteRelation = deleteRelation;
    Pair<Monotonicity, MonotonicityType> subj = monoFromString(subjMono);
    this.subjMono = subj.first;
    this.subjType = subj.second;
    Pair<Monotonicity, MonotonicityType> obj = monoFromString(objMono);
    this.objMono = obj.first;
    this.objType = obj.second;
  }

  Operator(String surfaceForm, NaturalLogicRelation deleteRelation, String subjMono) {
    this.surfaceForm = surfaceForm;
    this.deleteRelation = deleteRelation;
    Pair<Monotonicity, MonotonicityType> subj = monoFromString(subjMono);
    this.subjMono = subj.first;
    this.subjType = subj.second;
    this.objMono = Monotonicity.INVALID;
    this.objType = MonotonicityType.NONE;
  }

  public boolean isUnary() {
    return objMono == Monotonicity.INVALID;
  }

  public static Pair<Monotonicity, MonotonicityType> monoFromString(String mono) {
    switch (mono) {
      case "nonmonotone": return Pair.makePair(Monotonicity.NONMONOTONE, MonotonicityType.NONE);
      case "additive": return Pair.makePair(Monotonicity.MONOTONE, MonotonicityType.ADDITIVE);
      case "multiplicative": return Pair.makePair(Monotonicity.MONOTONE, MonotonicityType.MULTIPLICATIVE);
      case "additive-multiplicative": return Pair.makePair(Monotonicity.MONOTONE, MonotonicityType.BOTH);
      case "anti-additive": return Pair.makePair(Monotonicity.ANTITONE, MonotonicityType.ADDITIVE);
      case "anti-multiplicative": return Pair.makePair(Monotonicity.ANTITONE, MonotonicityType.MULTIPLICATIVE);
      case "anti-additive-multiplicative": return Pair.makePair(Monotonicity.ANTITONE, MonotonicityType.BOTH);
      default: throw new IllegalArgumentException("Unknown monotonicity: " + mono);
    }
  }

  public static String monotonicitySignature(Monotonicity mono, MonotonicityType type) {
    switch (mono) {
      case MONOTONE:
        switch (type) {
          case NONE: return "nonmonotone";
          case ADDITIVE: return "additive";
          case MULTIPLICATIVE: return "multiplicative";
          case BOTH: return "additive-multiplicative";
        }
      case ANTITONE:
        switch (type) {
          case NONE: return "nonmonotone";
          case ADDITIVE: return "anti-additive";
          case MULTIPLICATIVE: return "anti-multiplicative";
          case BOTH: return "anti-additive-multiplicative";
        }
      case NONMONOTONE: return "nonmonotone";
    }
    throw new IllegalStateException("Unhandled case: " + mono + " and " + type);
  }

  @SuppressWarnings("UnusedDeclaration")
  public static final Set<String> quantifierGlosses = Collections.unmodifiableSet(new HashSet<String>() {{
    for (Operator operator : values()) {
      add(operator.surfaceForm);
    }
  }});

  public static Optional<Operator> fromString(String word) {
    String wordToLowerCase = word.toLowerCase().replaceAll("[0-9]", "--num-- ").trim();
    for (Operator candidate : Operator.values()) {
      if (candidate.surfaceForm.equals(wordToLowerCase)) {
        return Optional.of(candidate);
      }
    }
    return Optional.empty();
  }
}
