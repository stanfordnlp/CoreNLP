package edu.stanford.nlp.util;

import junit.framework.TestCase;

public class TreeShapedStackTest extends TestCase {

	public void testBasicOperations() {

		TreeShapedStack<String> tss = new TreeShapedStack<String>();
		assertEquals(tss.size, 0);

		TreeShapedStack<String> tss1 = tss.push("1");
		assertEquals(tss1.size, 1);
		assertEquals(tss1.peek(), "1");

		TreeShapedStack<String> tss2 = tss1.push("2");
		assertEquals(tss2.size, 2);
		assertEquals(tss2.peek(), "2");
		assertEquals(tss2.previous.peek(), "1");

		TreeShapedStack<String> tss3 = tss2.push("3");
		assertEquals(tss3.size, 3);
		assertEquals(tss3.peek(), "3");
		assertEquals(tss3.previous.peek(), "2");

		tss3 = tss3.pop();
		assertEquals(tss3.peek(), "2");
		assertEquals(tss3.previous.peek(), "1");

		assertEquals(tss3.peek(), "2");

		TreeShapedStack<String> tss4 = tss3.push("4");
		assertEquals(tss4.peek(), "4");
		assertEquals(tss4.peek(), "4");
		assertEquals(tss4.previous.peek(), "2");

		tss4 = tss4.pop();
		assertEquals(tss4.peek(), "2");
		tss4 = tss4.pop();
		assertEquals(tss4.peek(), "1");
		tss4 = tss4.pop();
		assertEquals(tss4.size, 0);
	}
}
