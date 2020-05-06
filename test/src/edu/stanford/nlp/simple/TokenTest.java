package edu.stanford.nlp.simple;

import org.junit.Test;
import static org.junit.Assert.*;

public class TokenTest {

  private String string = "the quick brown fox jumped over the lazy dog";

  @Test
  public void testNotNull() {
    Sentence sentence = new Sentence(string);
    Token token = new Token(sentence, 0);

    assertNotNull(token);
  }

  @Test
  public void testWord() {
    Sentence sentence = new Sentence(string);
    Token token = new Token(sentence, 0);

    assertEquals("the", token.word());
  }

  @Test
  public void testPreviousWord() {
    Sentence sentence = new Sentence(string);
    Token token = new Token(sentence, 1);

    assertEquals("the", token.previous().word());
  }

  @Test
  public void testNextWord() {
    Sentence sentence = new Sentence(string);
    Token token = new Token(sentence, 0);

    assertEquals("quick", token.next().word());
  }

  @Test
  public void testOriginalSentence() {
    Sentence sentence = new Sentence(string);
    Token token = new Token(sentence, 0);

    assertEquals("the", token.originalText());
  }

  @Test
  public void testCharacterOffsetBegin() {
    Sentence sentence = new Sentence(string);
    Token token = new Token(sentence, 0);
    Token last =  new Token(new Sentence("last"), 0);

    assertEquals(-1, token.previous().characterOffsetBegin());
    assertEquals(-1, last.next().characterOffsetBegin());
    assertEquals(0, token.characterOffsetBegin());
    assertEquals(4, token.next().characterOffsetBegin());
    assertEquals(10, token.next().next().characterOffsetBegin());
    assertEquals(16, token.next().next().next().characterOffsetBegin());

  }

  @Test
  public void testCharacterOffsetEnd() {
    Sentence sentence = new Sentence(string);
    Token token = new Token(sentence, 0);
    Token last =  new Token(new Sentence("last"), 0);

    assertEquals(-1, token.previous().characterOffsetEnd());
    assertEquals(-1, last.next().characterOffsetEnd());
    assertEquals(3, token.characterOffsetEnd());
    assertEquals(9, token.next().characterOffsetEnd());
    assertEquals(15, token.next().next().characterOffsetEnd());
    assertEquals(19, token.next().next().next().characterOffsetEnd());
  }

  @Test
  public void testWhiteSpace() {
    Sentence sentence = new Sentence(string);
    Token token = new Token(sentence, 0);

    assertEquals("", token.before());
    assertEquals(" ", token.after());

  }

  @Test
  public void testIndices() {
    Sentence sentence = new Sentence(string);
    Token token = new Token(sentence, 0);

    assertEquals(0, token.index());
    assertEquals(1, token.next().index());
  }

  @Test
  public void testBeginPosition() {
    Sentence sentence = new Sentence(string);
    Token token = new Token(sentence, 3);

    assertEquals(16, token.beginPosition());
  }

  @Test
  public void testEndPosition() {
    Sentence sentence = new Sentence(string);
    Token token = new Token(sentence, 6);

    assertEquals(35, token.endPosition());
  }
}
