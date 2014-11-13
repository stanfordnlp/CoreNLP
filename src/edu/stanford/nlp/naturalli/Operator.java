package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.util.Pair;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A collection of quantifiers. This is the exhaustive list of quantifiers our system knows about.
 *
 * @author Gabor Angeli
 */
public enum Operator {
  // "All" quantifiers
  ALL("all", "anti-additive", "multiplicative"),
  EVERY("every", "anti-additive", "multiplicative"),
  ANY("any", "anti-additive", "multiplicative"),
  EACH("each", "anti-additive", "multiplicative"),
  THE_LOT_OF("the lot of", "anti-additive", "multiplicative"),
  ALL_OF("all of", "anti-additive", "multiplicative"),
  FOR_ALL("for all", "anti-additive", "multiplicative"),
  FOR_EVERY("for every", "anti-additive", "multiplicative"),
  FOR_EACH("for each", "anti-additive", "multiplicative"),
  NUM("__num__", "anti-additive", "multiplicative"),  // TODO check me
  FEW("few", "anti-additive", "multiplicative"),        // TODO check me
  IMPLICIT_NAMED_ENTITY("__implicit_named_entity__", "anti-additive", "multiplicative"),

  // "No" quantifiers
  NO("no", "anti-additive", "anti-additive"),
  UNARY_NO("no", "anti-additive"),
  UNARY_NOT("not", "anti-additive"),
  UNARY_NO_ONE("no one", "anti-additive"),
  UNARY_NT("n't", "anti-additive"),

  // "Some" quantifiers
  SOME("some", "additive", "additive"),
  SEVERAL("several", "additive", "additive"),
  EITHER("either", "additive", "additive"),
  A("a", "additive", "additive"),
  THE("the", "additive", "additive"),
  LESS_THAN("less than __num__", "additive", "additive"),
  SOME_OF("some of", "additive", "additive"),
  ONE_OF("one of", "additive", "additive"),
  AT_LEAST("at least __num__", "additive", "additive"),
  A_FEW("a few", "additive", "additive"),
  AT_LEAST_A_FEW("at least a few", "additive", "additive"),
  THERE_BE("there be", "additive", "additive"),
  THERE_BE_A_FEW("there be a few", "additive", "additive"),
  THERE_EXIST("there exist", "additive", "additive"),
  NUM_OF_THE("__num__ of the", "additive", "additive"),

  // "Not All" quantifiers
  NOT_ALL("not all", "additive", "anti-multiplicative"),
  NOT_EVERY("not every", "additive", "anti-multiplicative"),

  // "Most" quantifiers
  // TODO(gabor) check these
  MOST("most", "nonmonotone", "multiplicative"),
  MANY("many", "nonmonotone", "multiplicative"),
  ENOUGH("enough", "nonmonotone", "multiplicative"),
  MORE_THAN("more than __num_", "nonmonotone", "multiplicative"),
  A_LOT_OF("a lot of", "nonmonotone", "multiplicative"),
  LOTS_OF("lots of", "nonmonotone", "multiplicative"),
  PLENTY_OF("plenty of", "nonmonotone", "multiplicative"),
  HEAPS_OF("heap of", "nonmonotone", "multiplicative"),
  A_LOAD_OF("a load of", "nonmonotone", "multiplicative"),
  LOADS_OF("load of", "nonmonotone", "multiplicative"),
  TONS_OF("ton of", "nonmonotone", "multiplicative"),
  BOTH("both", "nonmonotone", "multiplicative"),
  JUST_NUM("just __num__", "nonmonotone", "multiplicative"),
  ONLY_NUM("only __num__", "nonmonotone", "multiplicative"),

  // Strange cases
  AT_MOST_NUM("at most __num__", "anti-additive", "anti-additive"),
  ;

  public static final Set<String> GLOSSES = Collections.unmodifiableSet(new HashSet<String>() {{
    for (Operator q : Operator.values()) {
      add(q.surfaceForm);
    }
  }});

  public final String surfaceForm;
  public final Monotonicity subjMono;
  public final MonotonicityType subjType;
  public final Monotonicity objMono;
  public final MonotonicityType objType;

  Operator(String surfaceForm, String subjMono, String objMono) {
    this.surfaceForm = surfaceForm;
    Pair<Monotonicity, MonotonicityType> subj = monoFromString(subjMono);
    this.subjMono = subj.first;
    this.subjType = subj.second;
    Pair<Monotonicity, MonotonicityType> obj = monoFromString(objMono);
    this.objMono = obj.first;
    this.objType = obj.second;
  }

  Operator(String surfaceForm, String subjMono) {
    this.surfaceForm = surfaceForm;
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
}
