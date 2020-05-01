package edu.stanford.nlp.objectbank;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DelimitRegExIteratorTest {

  private static final String[] testCases = {
          "@@123\nthis\nis\na\nsentence\n\n@@124\nThis\nis\nanother\n.\n\n@125\nThis\nis\nthe\nlast\n",
          "@@123\nthis\nis\na\nsentence\n\n@@124\nThis\nis\nanother\n.\n\n@125\nThis\nis\nthe\nlast\n",
  };

  private static final String[] delimiterCases = {
          "\n\n",
          "a|e"
  };

  private static final List[] answerCases = {
          Arrays.asList("@@123\nthis\nis\na\nsentence", "@@124\nThis\nis\nanother\n.", "@125\nThis\nis\nthe\nlast\n"),
          Arrays.asList("@@123\nthis\nis\n", "\ns", "nt", "nc", "\n\n@@124\nThis\nis\n", "noth",
                  "r\n.\n\n@125\nThis\nis\nth", "\nl", "st\n"),
  };

  @Test
  public void testAnswer() {
    for (int i = 0; i < testCases.length; i++) {
      String s = testCases[i];
      DelimitRegExIterator<String> di = DelimitRegExIterator.defaultDelimitRegExIterator(
              new StringReader(s), delimiterCases[i]
      );

      List<String> answer = new ArrayList<>();

      while (di.hasNext()) {
        answer.add(di.next());
      }

      assertEquals(answerCases[i], answer);
    }
  }

  @Test
  public void testDelimiterLength() {
    assertEquals(testCases.length, delimiterCases.length);
  }

  @Test
  public void testAnswerLength() {
    assertEquals(testCases.length, answerCases.length);
  }
}
