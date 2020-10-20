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
    assertEquals(NaturalLogicRelation.EQUIVALENT, NaturalLogicRelation.NEGATION.join(NaturalLogicRelation.NEGATION));
    for (NaturalLogicRelation rel : NaturalLogicRelation.values()) {
      assertEquals(rel, NaturalLogicRelation.EQUIVALENT.join(rel));
      assertEquals(NaturalLogicRelation.INDEPENDENCE, NaturalLogicRelation.INDEPENDENCE.join(rel));
      assertEquals(NaturalLogicRelation.INDEPENDENCE, rel.join(NaturalLogicRelation.INDEPENDENCE));
    }
  }

  @Test
  public void entailmentState() {
    assertTrue(NaturalLogicRelation.EQUIVALENT.maintainsTruth);
    assertTrue(NaturalLogicRelation.FORWARD_ENTAILMENT.maintainsTruth);
    assertTrue(NaturalLogicRelation.NEGATION.negatesTruth);
    assertTrue(NaturalLogicRelation.ALTERNATION.negatesTruth);

    assertFalse(NaturalLogicRelation.EQUIVALENT.negatesTruth);
    assertFalse(NaturalLogicRelation.FORWARD_ENTAILMENT.negatesTruth);
    assertFalse(NaturalLogicRelation.NEGATION.maintainsTruth);
    assertFalse(NaturalLogicRelation.ALTERNATION.maintainsTruth);

    assertFalse(NaturalLogicRelation.COVER.maintainsTruth);
    assertFalse(NaturalLogicRelation.COVER.negatesTruth);
    assertFalse(NaturalLogicRelation.INDEPENDENCE.maintainsTruth);
    assertFalse(NaturalLogicRelation.INDEPENDENCE.negatesTruth);
  }

  @Test
  public void someInsertionRelations() {
//    assertEquals(NaturalLogicRelation.INDEPENDENCE, NaturalLogicRelation.forDependencyInsertion("nsubj"));
    assertEquals(NaturalLogicRelation.FORWARD_ENTAILMENT, NaturalLogicRelation.forDependencyInsertion("quantmod"));
    assertEquals(NaturalLogicRelation.REVERSE_ENTAILMENT, NaturalLogicRelation.forDependencyInsertion("amod"));
  }

  @Test
  public void conjOrPeculiarities() {
    assertEquals(NaturalLogicRelation.FORWARD_ENTAILMENT, NaturalLogicRelation.forDependencyInsertion("conj:or"));
    assertEquals(NaturalLogicRelation.FORWARD_ENTAILMENT, NaturalLogicRelation.forDependencyInsertion("conj:or", true));
    assertEquals(NaturalLogicRelation.REVERSE_ENTAILMENT, NaturalLogicRelation.forDependencyInsertion("conj:or", false));
  }

  @Test
  public void someDeletionRelations() {
//    assertEquals(NaturalLogicRelation.INDEPENDENCE, NaturalLogicRelation.forDependencyDeletion("nsubj"));
    assertEquals(NaturalLogicRelation.REVERSE_ENTAILMENT, NaturalLogicRelation.forDependencyDeletion("quantmod"));
    assertEquals(NaturalLogicRelation.FORWARD_ENTAILMENT, NaturalLogicRelation.forDependencyDeletion("amod"));
  }

}
