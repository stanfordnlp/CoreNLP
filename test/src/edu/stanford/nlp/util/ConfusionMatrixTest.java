package edu.stanford.nlp.util;

import junit.framework.TestCase;

public class ConfusionMatrixTest extends TestCase {

  boolean echo;

  public ConfusionMatrixTest() {
    this(false);
  }

  public ConfusionMatrixTest(boolean echo) {
    this.echo = echo;
  }

  public void test1() {
    String expected = "      Guess/Gold      C1      C2      C3    Marg. (Guess)\n" +
      "              C1       2       0       0       2\n" +
      "              C2       1       0       0       1\n" +
      "              C3       0       0       1       1\n" +
      "    Marg. (Gold)       3       0       1\n\n" +
      "              C1 = a        prec=1, recall=0.66667, spec=1, f1=0.8\n" +
      "              C2 = b        prec=0, recall=n/a, spec=0.75, f1=n/a\n" +
      "              C3 = c        prec=1, recall=1, spec=1, f1=1\n";

    ConfusionMatrix<String> conf = new ConfusionMatrix<String>();
    conf.add("a","a");
    conf.add("a","a");
    conf.add("b","a");
    conf.add("c","c");
    String result = conf.printTable();
    if (echo) {
      System.err.println(result);
    } else {
      assertEquals(expected, result);
    }
  }
	
  public void test2() {
    String expected = "      Guess/Gold      C1      C2    Marg. (Guess)\n" +
      "              C1      10       5      15\n" +
      "              C2       2       3       5\n" +
      "    Marg. (Gold)      12       8\n\n" +
      "              C1 = 1        prec=0.66667, recall=0.83333, spec=0.375, f1=0.74074\n" +
      "              C2 = 2        prec=0.6, recall=0.375, spec=0.83333, f1=0.46154\n";

    ConfusionMatrix<Integer> conf = new ConfusionMatrix<Integer>();
    conf.add(1,1, 10);
    conf.add(1,2, 5);
    conf.add(2,1,2);
    conf.add(2,2,3);
    String result = conf.printTable();
    if (echo) {
      System.err.println(result);
    } else {
      assertEquals(expected, result);
    }
  }
  
  public static void main(String[] args) {
    ConfusionMatrixTest tester = new ConfusionMatrixTest(true);
    System.out.println("Test 1");
    tester.test1();
    System.out.println("\nTest 2");
    tester.test2();
  }

}

