package edu.stanford.nlp.util;

import java.util.Locale;

import junit.framework.TestCase;

/**
 * Tests that the output of the ConfusionMatrix is in the expected format.
 *
 * @author Eric Yeh yeh1@cs.stanford.edu
 */
public class ConfusionMatrixTest extends TestCase {

  boolean echo;

  public ConfusionMatrixTest() {
    this(false);
  }

  public ConfusionMatrixTest(boolean echo) {
    this.echo = echo;
  }

  public void testBasic() {
    String expected = "      Guess/Gold      C1      C2      C3    Marg. (Guess)\n" +
                      "              C1       2       0       0       2\n" +
                      "              C2       1       0       0       1\n" +
                      "              C3       0       0       1       1\n" +
                      "    Marg. (Gold)       3       0       1\n\n" +
                      "              C1 = a        prec=1, recall=0.66667, spec=1, f1=0.8\n" +
                      "              C2 = b        prec=0, recall=n/a, spec=0.75, f1=n/a\n" +
                      "              C3 = c        prec=1, recall=1, spec=1, f1=1\n";

    ConfusionMatrix<String> conf = new ConfusionMatrix<String>(Locale.US);
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

  public void testRealLabels() {
    String expected = "      Guess/Gold       a       b       c    Marg. (Guess)\n" +
                      "               a       2       0       0       2\n" +
                      "               b       1       0       0       1\n" +
                      "               c       0       0       1       1\n" +
                      "    Marg. (Gold)       3       0       1\n\n" +
                      "               a        prec=1, recall=0.66667, spec=1, f1=0.8\n" +
                      "               b        prec=0, recall=n/a, spec=0.75, f1=n/a\n" +
                      "               c        prec=1, recall=1, spec=1, f1=1\n";

    ConfusionMatrix<String> conf = new ConfusionMatrix<String>(Locale.US);
    conf.setUseRealLabels(true);
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
	
  public void testBulkAdd() {
    String expected = "      Guess/Gold      C1      C2    Marg. (Guess)\n" +
                      "              C1      10       5      15\n" +
                      "              C2       2       3       5\n" +
                      "    Marg. (Gold)      12       8\n\n" +
                      "              C1 = 1        prec=0.66667, recall=0.83333, spec=0.375, f1=0.74074\n" +
                      "              C2 = 2        prec=0.6, recall=0.375, spec=0.83333, f1=0.46154\n";

    ConfusionMatrix<Integer> conf = new ConfusionMatrix<Integer>(Locale.US);
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
  
  private static class BackwardsInteger implements Comparable<BackwardsInteger> {
    private final int value;

    public BackwardsInteger(int value) {
      this.value = value;
    }

    public int compareTo(BackwardsInteger other) {
      return other.value - this.value; // backwards
    }

    @Override
    public int hashCode() {
      return value;
    }

    public boolean equals(Object o) {
      if (o == null || (!(o instanceof BackwardsInteger))) {
        return false;
      }
      return (((BackwardsInteger) o).value == value);
    }

    @Override
    public String toString() {
      return Integer.toString(value);
    }
  }

  public void testValueSort() {
    String expected = "      Guess/Gold       2       1    Marg. (Guess)\n" +
                      "               2       3       2       5\n" +
                      "               1       5      10      15\n" +
                      "    Marg. (Gold)       8      12\n\n" +
                      "               2        prec=0.6, recall=0.375, spec=0.83333, f1=0.46154\n" + 
                      "               1        prec=0.66667, recall=0.83333, spec=0.375, f1=0.74074\n";

    BackwardsInteger one = new BackwardsInteger(1);
    BackwardsInteger two = new BackwardsInteger(2);

    ConfusionMatrix<BackwardsInteger> conf = new ConfusionMatrix<BackwardsInteger>(Locale.US);
    conf.setUseRealLabels(true);
    conf.add(one, one, 10);
    conf.add(one, two, 5);
    conf.add(two, one, 2);
    conf.add(two, two, 3);
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
    tester.testBasic();
    System.out.println("\nTest 2");
    tester.testRealLabels();
    System.out.println("\nTest 3");
    tester.testBulkAdd();
    System.out.println("\nTest 4");
    tester.testValueSort();
  }

}

