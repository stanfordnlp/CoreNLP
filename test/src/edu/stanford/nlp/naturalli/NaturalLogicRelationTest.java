package edu.stanford.nlp.naturalli;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * A test for {@link edu.stanford.nlp.naturalli.NaturalLogicRelation}.
 *
 * @author Gabor Angeli
 */
public class NaturalLogicRelationTest {

  @Test
  public void fixedIndex() {
    for (NaturalLogicRelation rel : NaturalLogicRelation.values()) {
      assertEquals(rel, NaturalLogicRelation.byFixedIndex(rel.fixedIndex));
    }
  }

  @Test
  public void spotTestJoinTable() {
    assertEquals(NaturalLogicRelation.COVER, NaturalLogicRelation.NEGATION.join(NaturalLogicRelation.FORWARD_ENTAILMENT));
    assertEquals(NaturalLogicRelation.FORWARD_ENTAILMENT, NaturalLogicRelation.ALTERNATION.join(NaturalLogicRelation.NEGATION));
    assertEquals(NaturalLogicRelation.REVERSE_ENTAILMENT, NaturalLogicRelation.COVER.join(NaturalLogicRelation.ALTERNATION));
    assertEquals(NaturalLogicRelation.EQUIVALENCE, NaturalLogicRelation.NEGATION.join(NaturalLogicRelation.NEGATION));
    for (NaturalLogicRelation rel : NaturalLogicRelation.values()) {
      assertEquals(rel, NaturalLogicRelation.EQUIVALENCE.join(rel));
      assertEquals(NaturalLogicRelation.INDEPENDENCE, NaturalLogicRelation.INDEPENDENCE.join(rel));
      assertEquals(NaturalLogicRelation.INDEPENDENCE, rel.join(NaturalLogicRelation.INDEPENDENCE));
    }
  }

  @Test
  public void entailmentState() {
    assertTrue(NaturalLogicRelation.EQUIVALENCE.isEntailed);
    assertTrue(NaturalLogicRelation.FORWARD_ENTAILMENT.isEntailed);
    assertTrue(NaturalLogicRelation.NEGATION.isNegated);
    assertTrue(NaturalLogicRelation.ALTERNATION.isNegated);

    assertFalse(NaturalLogicRelation.EQUIVALENCE.isNegated);
    assertFalse(NaturalLogicRelation.FORWARD_ENTAILMENT.isNegated);
    assertFalse(NaturalLogicRelation.NEGATION.isEntailed);
    assertFalse(NaturalLogicRelation.ALTERNATION.isEntailed);

    assertFalse(NaturalLogicRelation.COVER.isEntailed);
    assertFalse(NaturalLogicRelation.COVER.isNegated);
    assertFalse(NaturalLogicRelation.INDEPENDENCE.isEntailed);
    assertFalse(NaturalLogicRelation.INDEPENDENCE.isNegated);
  }

}
