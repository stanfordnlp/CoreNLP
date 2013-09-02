package edu.stanford.nlp.process;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;
import edu.stanford.nlp.ling.Word;

/**
 * Tests lowercaseification
 * 
 * @author dramage
 */
public class LowercaseProcessorTest extends TestCase {
  
  List<Word> words = Arrays.asList(new Word[] {
      new Word("HI"), new Word("thErE"), new Word("woop")
  });
  
  List<Word> lower = Arrays.asList(new Word[] {
      new Word("hi"), new Word("there"), new Word("woop")
  });
  
  public void testLowecaseProcessor() {
    assertEquals(lower, new LowercaseProcessor<Object,Object>().process(words));
  }

}
