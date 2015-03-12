package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.util.Pair;

import java.util.ArrayList;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * A test for the {@link edu.stanford.nlp.naturalli.Polarity} class.
 *
 * This is primarily just spot-checking the projection table, and then some of the utility functions.
 *
 * @author Gabor Angeli
 */
public class PolarityTest {

  private static final Polarity none = new Polarity(new ArrayList<Pair<Monotonicity, MonotonicityType>>() {{
  }});

  private static final Polarity additive = new Polarity(new ArrayList<Pair<Monotonicity, MonotonicityType>>() {{
    add( Pair.makePair(Monotonicity.MONOTONE, MonotonicityType.ADDITIVE));
  }});

  private static final Polarity multiplicative = new Polarity(new ArrayList<Pair<Monotonicity, MonotonicityType>>() {{
    add( Pair.makePair(Monotonicity.MONOTONE, MonotonicityType.MULTIPLICATIVE));
  }});

  private static final Polarity antimultiplicative = new Polarity(new ArrayList<Pair<Monotonicity, MonotonicityType>>() {{
    add( Pair.makePair(Monotonicity.MONOTONE, MonotonicityType.ADDITIVE));
    add( Pair.makePair(Monotonicity.ANTITONE, MonotonicityType.MULTIPLICATIVE));
  }});

  private static final Polarity additiveAntiMultiplicative = new Polarity(new ArrayList<Pair<Monotonicity, MonotonicityType>>() {{
    add( Pair.makePair(Monotonicity.MONOTONE, MonotonicityType.ADDITIVE));
    add( Pair.makePair(Monotonicity.ANTITONE, MonotonicityType.MULTIPLICATIVE));
  }});

  private static final Polarity multiplicativeAntiMultiplicative = new Polarity(new ArrayList<Pair<Monotonicity, MonotonicityType>>() {{
    add( Pair.makePair(Monotonicity.MONOTONE, MonotonicityType.MULTIPLICATIVE));
    add( Pair.makePair(Monotonicity.ANTITONE, MonotonicityType.MULTIPLICATIVE));
  }});

  @Test
  public void noneProject() {
    assertEquals(NaturalLogicRelation.EQUIVALENCE, none.projectLexicalRelation(NaturalLogicRelation.EQUIVALENCE));
    assertEquals(NaturalLogicRelation.FORWARD_ENTAILMENT, none.projectLexicalRelation(NaturalLogicRelation.FORWARD_ENTAILMENT));
    assertEquals(NaturalLogicRelation.REVERSE_ENTAILMENT, none.projectLexicalRelation(NaturalLogicRelation.REVERSE_ENTAILMENT));
    assertEquals(NaturalLogicRelation.NEGATION, none.projectLexicalRelation(NaturalLogicRelation.NEGATION));
    assertEquals(NaturalLogicRelation.ALTERNATION, none.projectLexicalRelation(NaturalLogicRelation.ALTERNATION));
    assertEquals(NaturalLogicRelation.COVER, none.projectLexicalRelation(NaturalLogicRelation.COVER));
    assertEquals(NaturalLogicRelation.INDEPENDENCE, none.projectLexicalRelation(NaturalLogicRelation.INDEPENDENCE));
  }

  @Test
  public void additive_antimultiplicativeProject() {
    assertEquals(NaturalLogicRelation.EQUIVALENCE, additiveAntiMultiplicative.projectLexicalRelation(NaturalLogicRelation.EQUIVALENCE));
    assertEquals(NaturalLogicRelation.REVERSE_ENTAILMENT, additiveAntiMultiplicative.projectLexicalRelation(NaturalLogicRelation.FORWARD_ENTAILMENT));
    assertEquals(NaturalLogicRelation.FORWARD_ENTAILMENT, additiveAntiMultiplicative.projectLexicalRelation(NaturalLogicRelation.REVERSE_ENTAILMENT));
    assertEquals(NaturalLogicRelation.COVER, additiveAntiMultiplicative.projectLexicalRelation(NaturalLogicRelation.NEGATION));
    assertEquals(NaturalLogicRelation.COVER, additiveAntiMultiplicative.projectLexicalRelation(NaturalLogicRelation.ALTERNATION));
    assertEquals(NaturalLogicRelation.INDEPENDENCE, additiveAntiMultiplicative.projectLexicalRelation(NaturalLogicRelation.COVER));
    assertEquals(NaturalLogicRelation.INDEPENDENCE, additiveAntiMultiplicative.projectLexicalRelation(NaturalLogicRelation.INDEPENDENCE));
  }

  @Test
  public void multiplicative_antimultiplicativeProject() {
    assertEquals(NaturalLogicRelation.EQUIVALENCE, multiplicativeAntiMultiplicative.projectLexicalRelation(NaturalLogicRelation.EQUIVALENCE));
    assertEquals(NaturalLogicRelation.REVERSE_ENTAILMENT, multiplicativeAntiMultiplicative.projectLexicalRelation(NaturalLogicRelation.FORWARD_ENTAILMENT));
    assertEquals(NaturalLogicRelation.FORWARD_ENTAILMENT, multiplicativeAntiMultiplicative.projectLexicalRelation(NaturalLogicRelation.REVERSE_ENTAILMENT));
    assertEquals(NaturalLogicRelation.INDEPENDENCE, multiplicativeAntiMultiplicative.projectLexicalRelation(NaturalLogicRelation.NEGATION));
    assertEquals(NaturalLogicRelation.INDEPENDENCE, multiplicativeAntiMultiplicative.projectLexicalRelation(NaturalLogicRelation.ALTERNATION));
    assertEquals(NaturalLogicRelation.INDEPENDENCE, multiplicativeAntiMultiplicative.projectLexicalRelation(NaturalLogicRelation.COVER));
    assertEquals(NaturalLogicRelation.INDEPENDENCE, multiplicativeAntiMultiplicative.projectLexicalRelation(NaturalLogicRelation.INDEPENDENCE));
  }

  @Test
  public void additiveProject() {
    assertEquals(NaturalLogicRelation.EQUIVALENCE, additive.projectLexicalRelation(NaturalLogicRelation.EQUIVALENCE));
    assertEquals(NaturalLogicRelation.FORWARD_ENTAILMENT, additive.projectLexicalRelation(NaturalLogicRelation.FORWARD_ENTAILMENT));
    assertEquals(NaturalLogicRelation.REVERSE_ENTAILMENT, additive.projectLexicalRelation(NaturalLogicRelation.REVERSE_ENTAILMENT));
    assertEquals(NaturalLogicRelation.COVER, additive.projectLexicalRelation(NaturalLogicRelation.NEGATION));
    assertEquals(NaturalLogicRelation.INDEPENDENCE, additive.projectLexicalRelation(NaturalLogicRelation.ALTERNATION));
    assertEquals(NaturalLogicRelation.COVER, additive.projectLexicalRelation(NaturalLogicRelation.COVER));
    assertEquals(NaturalLogicRelation.INDEPENDENCE, additive.projectLexicalRelation(NaturalLogicRelation.INDEPENDENCE));
  }

  @Test
  public void antimultiplicativeProject() {
    assertEquals(NaturalLogicRelation.EQUIVALENCE, antimultiplicative.projectLexicalRelation(NaturalLogicRelation.EQUIVALENCE));
    assertEquals(NaturalLogicRelation.REVERSE_ENTAILMENT, antimultiplicative.projectLexicalRelation(NaturalLogicRelation.FORWARD_ENTAILMENT));
    assertEquals(NaturalLogicRelation.FORWARD_ENTAILMENT, antimultiplicative.projectLexicalRelation(NaturalLogicRelation.REVERSE_ENTAILMENT));
    assertEquals(NaturalLogicRelation.COVER, antimultiplicative.projectLexicalRelation(NaturalLogicRelation.NEGATION));
    assertEquals(NaturalLogicRelation.COVER, antimultiplicative.projectLexicalRelation(NaturalLogicRelation.ALTERNATION));
    assertEquals(NaturalLogicRelation.INDEPENDENCE, antimultiplicative.projectLexicalRelation(NaturalLogicRelation.COVER));
    assertEquals(NaturalLogicRelation.INDEPENDENCE, antimultiplicative.projectLexicalRelation(NaturalLogicRelation.INDEPENDENCE));
  }

  @Test
  public void multiplicativeTruth() {
    assertEquals(true, multiplicative.maintainsEntailment(NaturalLogicRelation.EQUIVALENCE));
    assertEquals(true, multiplicative.maintainsEntailment(NaturalLogicRelation.FORWARD_ENTAILMENT));
    assertEquals(false, multiplicative.maintainsEntailment(NaturalLogicRelation.REVERSE_ENTAILMENT));
    assertEquals(false, multiplicative.maintainsEntailment(NaturalLogicRelation.NEGATION));
    assertEquals(false, multiplicative.maintainsEntailment(NaturalLogicRelation.ALTERNATION));
    assertEquals(false, multiplicative.maintainsEntailment(NaturalLogicRelation.COVER));
    assertEquals(false, multiplicative.maintainsEntailment(NaturalLogicRelation.INDEPENDENCE));

    assertEquals(false, multiplicative.introducesNegation(NaturalLogicRelation.EQUIVALENCE));
    assertEquals(false, multiplicative.introducesNegation(NaturalLogicRelation.FORWARD_ENTAILMENT));
    assertEquals(false, multiplicative.introducesNegation(NaturalLogicRelation.REVERSE_ENTAILMENT));
    assertEquals(true, multiplicative.introducesNegation(NaturalLogicRelation.NEGATION));
    assertEquals(true, multiplicative.introducesNegation(NaturalLogicRelation.ALTERNATION));
    assertEquals(false, multiplicative.introducesNegation(NaturalLogicRelation.COVER));
    assertEquals(false, multiplicative.introducesNegation(NaturalLogicRelation.INDEPENDENCE));
  }

  @Test
  public void upwardDownward() {
    assertEquals(true, multiplicative.isUpwards());
    assertEquals(true, additive.isUpwards());
    assertEquals(false, antimultiplicative.isUpwards());
    assertEquals(false, multiplicativeAntiMultiplicative.isUpwards());
    assertEquals(false, additiveAntiMultiplicative.isUpwards());

    assertEquals(false, multiplicative.isDownwards());
    assertEquals(false, additive.isDownwards());
    assertEquals(true, antimultiplicative.isDownwards());
    assertEquals(true, multiplicativeAntiMultiplicative.isDownwards());
    assertEquals(true, additiveAntiMultiplicative.isDownwards());
  }
}
