package edu.stanford.nlp.util;

import junit.framework.TestCase;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;

public class StringUtilsTest extends TestCase {

  public void testTr() {
    assertEquals(StringUtils.tr("chris", "irs", "mop"), "chomp");
  }

  public void testGetBaseName() {
    assertEquals(StringUtils.getBaseName("/u/wcmac/foo.txt"), "foo.txt");
    assertEquals(StringUtils.getBaseName("/u/wcmac/foo.txt", ""), "foo.txt");
    assertEquals(StringUtils.getBaseName("/u/wcmac/foo.txt", ".txt"), "foo");
    assertEquals(StringUtils.getBaseName("/u/wcmac/foo.txt", ".pdf"), "foo.txt");
  }

  public void testArgsToProperties() {
    Properties p1 = new Properties();
    p1.setProperty("fred", "-2");
    p1.setProperty("", "joe");
    Properties p2 = new Properties();
    p2.setProperty("fred", "true");
    p2.setProperty("2", "joe");
    Map<String,Integer> argNums = new HashMap<>();
    argNums.put("fred", 1);
    assertEquals(StringUtils.argsToProperties(new String[]{"-fred", "-2", "joe"}), p2);
    assertEquals(StringUtils.argsToProperties(new String[]{"-fred", "-2", "joe"}, argNums), p1);
  }

  public void testValueSplit() {
    List<String> vals1 = StringUtils.valueSplit("arg(a,b),foo(d,e,f)", "[a-z]*(?:\\([^)]*\\))?", "\\s*,\\s*");
    List<String> ans1 = Arrays.asList("arg(a,b)", "foo(d,e,f)");
    assertEquals("Split failed", ans1, vals1);
    vals1 = StringUtils.valueSplit("arg(a,b) , foo(d,e,f) , ", "[a-z]*(?:\\([^)]*\\))?", "\\s*,\\s*");
    assertEquals("Split failed", ans1, vals1);
    vals1 = StringUtils.valueSplit(",arg(a,b),foo(d,e,f)", "[a-z]*(?:\\([^)]*\\))?", "\\s*,\\s*");
    List<String> ans2 = Arrays.asList("", "arg(a,b)", "foo(d,e,f)");
    assertEquals("Split failed", ans2, vals1);
    List<String> vals3 = StringUtils.valueSplit("\"quoted,comma\",\"with \\\"\\\" quote\" , \"stuff\",or not,quoted,",
             "\"(?:[^\"\\\\]+|\\\\\")*\"|[^,\"]+", "\\s*,\\s*");
    List<String> ans3 = Arrays.asList("\"quoted,comma\"", "\"with \\\"\\\" quote\"", "\"stuff\"", "or not", "quoted");
    assertEquals("Split failed", ans3, vals3);
  }

  public void testLongestCommonSubstring(){
    assertEquals(12,StringUtils.longestCommonSubstring("Jo3seph Smarr!", "Joseph R Smarr"));
    assertEquals(12,StringUtils.longestCommonSubstring("Joseph R Smarr","Jo3seph Smarr!"));
  }

  public void testEditDistance() {
    // test insert
    assertEquals(4, StringUtils.editDistance("Hi!","Hi you!"));
    assertEquals(5, StringUtils.editDistance("Hi!","Hi you!?"));
    assertEquals(1, StringUtils.editDistance("sdf", "asdf"));
    assertEquals(1, StringUtils.editDistance("asd", "asdf"));
    // test delete
    assertEquals(4, StringUtils.editDistance("Hi you!","Hi!"));
    assertEquals(5, StringUtils.editDistance("Hi you!?", "Hi!"));
    assertEquals(1, StringUtils.editDistance("asdf", "asd"));
    assertEquals(1, StringUtils.editDistance("asdf", "sdf"));
    // test modification
    assertEquals(3, StringUtils.editDistance("Hi you!","Hi Sir!"));
    assertEquals(5, StringUtils.editDistance("Hi you!","Hi Sir!!!"));
    // test transposition
    assertEquals(2, StringUtils.editDistance("hello", "hlelo"));
    assertEquals(2, StringUtils.editDistance("asdf", "adsf"));
    assertEquals(2, StringUtils.editDistance("asdf", "sadf"));
    assertEquals(2, StringUtils.editDistance("asdf", "asfd"));
    // test empty
    assertEquals(0, StringUtils.editDistance("", ""));
    assertEquals(3, StringUtils.editDistance("", "bar"));
    assertEquals(3, StringUtils.editDistance("foo", ""));
  }

  public void testSplitOnChar() {
    assertEquals(3, StringUtils.splitOnChar("hello\tthere\tworld", '\t').length);
    assertEquals(2, StringUtils.splitOnChar("hello\tworld", '\t').length);
    assertEquals(1, StringUtils.splitOnChar("hello", '\t').length);

    assertEquals("hello", StringUtils.splitOnChar("hello\tthere\tworld", '\t')[0]);
    assertEquals("there", StringUtils.splitOnChar("hello\tthere\tworld", '\t')[1]);
    assertEquals("world", StringUtils.splitOnChar("hello\tthere\tworld", '\t')[2]);

    assertEquals(1, StringUtils.splitOnChar("hello\tthere\tworld\n", ' ').length);
    assertEquals("hello\tthere\tworld\n", StringUtils.splitOnChar("hello\tthere\tworld\n", ' ')[0]);

    assertEquals(5, StringUtils.splitOnChar("a\tb\tc\td\te", '\t').length);
    assertEquals(5, StringUtils.splitOnChar("\t\t\t\t", '\t').length);
    assertEquals("", StringUtils.splitOnChar("\t\t\t\t", '\t')[0]);
    assertEquals("", StringUtils.splitOnChar("\t\t\t\t", '\t')[1]);
    assertEquals("", StringUtils.splitOnChar("\t\t\t\t", '\t')[4]);
  }

  /*
  public void testSplitOnCharSpeed() {
    String line = "1;2;3;4;5;678;901;234567;1";
    int runs = 1000000;

    for (int gcIter = 0; gcIter < 10; ++gcIter) {
      long start = System.currentTimeMillis();
      for (int i = 0; i < runs; ++i) {
        StringUtils.split(line, ";");
      }
      System.err.println("Old: " + Redwood.formatTimeDifference(System.currentTimeMillis() - start) + " for " + runs + " splits");

      start = System.currentTimeMillis();
      for (int i = 0; i < runs; ++i) {
        StringUtils.splitOnChar(line, ';');
      }
      System.err.println("New: " + Redwood.formatTimeDifference(System.currentTimeMillis() - start) + " for " + runs + " splits");
      System.err.println();
    }
  }
  */

  public void testStringIsNullOrEmpty() {
    assertTrue(StringUtils.isNullOrEmpty(null));
    assertTrue(StringUtils.isNullOrEmpty(""));
    assertFalse(StringUtils.isNullOrEmpty(" "));
    assertFalse(StringUtils.isNullOrEmpty("foo"));
  }

  public void testNormalize() {
    assertEquals("can't", StringUtils.normalize("can't"));
    assertEquals("Beyonce", StringUtils.normalize("Beyoncé"));
    assertEquals("krouzek", StringUtils.normalize("kroužek"));
    assertEquals("office", StringUtils.normalize("o\uFB03ce"));
    assertEquals("DZ", StringUtils.normalize("Ǆ"));
    assertEquals("1⁄4", StringUtils.normalize("¼"));
    assertEquals("한국어", StringUtils.normalize("한국어"));
    assertEquals("조선말", StringUtils.normalize("조선말"));
    assertEquals("が", StringUtils.normalize("が"));
    assertEquals("か", StringUtils.normalize("か"));
  }

  private static final char[] escapeInputs = {
          '\\', '\\', '\\', '\\', '\\',
          '\\', '\\', '\\', '\\', '\\',
          '"', '"', '"',
  };

  private static final String[] csvInputs = {
          "", ",", "foo", "foo,bar", "foo,    bar",
          ",foo,bar,", "foo,\"bar\"", "\"foo,foo2\"", "1997, \"Ford\" ,E350", "foo,\"\",bar",
          "1999,Chevy,\"Venture \"\"Extended Edition, Large\"\"\",,5000.00", "\"\"\",foo,\"", "\"\"\"\",foo",
  };

  private static final String[][] csvOutputs = {
          {},
          {""},
          {"foo"},
          {"foo", "bar"},
          {"foo", "    bar"},

          {"", "foo", "bar"},
          {"foo", "bar"},
          {"foo,foo2"},
          {"1997"," Ford ","E350"},
          {"foo", "", "bar"},

          {"1999", "Chevy", "Venture \"Extended Edition, Large\"","", "5000.00"},
          {"\",foo,"},
          {"\"", "foo"},
  };

  public void testCSV() {
    assertEquals("Bung test", csvInputs.length, csvOutputs.length);
    for (int i = 0; i < csvInputs.length; i++) {
      String[] answer = StringUtils.splitOnCharWithQuoting(csvInputs[i], ',', '"', escapeInputs[i]);
      assertTrue("Bad CSV line handling of ex " + i +": " + Arrays.toString(csvOutputs[i]) +
              " vs. " + Arrays.toString(answer),
              Arrays.equals(csvOutputs[i], answer));
    }
  }

  public void testGetCharacterNgrams() {
    testCharacterNgram("abc", 0, 0);
    testCharacterNgram("abc", 1, 1, "a", "b", "c");
    testCharacterNgram("abc", 2, 2, "ab", "bc");
    testCharacterNgram("abc", 1, 2, "a", "b", "c", "ab", "bc");
    testCharacterNgram("abc", 1, 3, "a", "b", "c", "ab", "bc", "abc");
    testCharacterNgram("abc", 1, 4, "a", "b", "c", "ab", "bc", "abc");
  }

  private void testCharacterNgram(String string, int min, int max, String... expected) {
    System.out.println(makeSet(expected));
    System.out.println(StringUtils.getCharacterNgrams(string, min, max));
    assertEquals(makeSet(expected),
                 new HashSet<>(StringUtils.getCharacterNgrams(string, min, max)));
  }

  @SafeVarargs
  private final <T> Set<T> makeSet(T... elems) {
    return new HashSet<>(Arrays.asList(elems));
  }


  public void testExpandEnvironmentVariables() {
    Map<String, String> env = new HashMap<String, String>() {{
      put("A", "[outA]");
      put("A_B", "[outA_B]");
      put("a_B", "[outa_B]");
      put("a_B45", "[outa_B45]");
      put("_A", "[out_A]");
      put("3A", "[out_3A]");
    }};
    assertEquals("xxx [outA] xxx", StringUtils.expandEnvironmentVariables("xxx $A xxx", env));
    assertEquals("xxx[outA] xxx", StringUtils.expandEnvironmentVariables("xxx$A xxx", env));
    assertEquals("xxx[outA]xxx", StringUtils.expandEnvironmentVariables("xxx${A}xxx", env));
    assertEquals("xxx [outA_B] xxx", StringUtils.expandEnvironmentVariables("xxx $A_B xxx", env));
    assertEquals("xxx [outa_B] xxx", StringUtils.expandEnvironmentVariables("xxx $a_B xxx", env));
    assertEquals("xxx [outa_B45] xxx", StringUtils.expandEnvironmentVariables("xxx $a_B45 xxx", env));
    assertEquals("xxx [out_A] xxx", StringUtils.expandEnvironmentVariables("xxx $_A xxx", env));
    assertEquals("xxx $3A xxx", StringUtils.expandEnvironmentVariables("xxx $3A xxx", env));
    assertEquals("xxx  xxx", StringUtils.expandEnvironmentVariables("xxx $UNDEFINED xxx", env));
  }

  public void testDecodeArray() throws IOException {
    String tempFile1 = Files.createTempFile("test", "tmp").toString();
    String tempFile2 = Files.createTempFile("test", "tmp").toString();
    String[] decodedArray = StringUtils.decodeArray("'"+tempFile1 + "','" + tempFile2+"'");

    assertEquals(2, decodedArray.length);
    assertEquals(tempFile1, decodedArray[0]);
    assertEquals(tempFile2, decodedArray[1]);

    String[] test10 = { "\"C:\\Users\\BELLCH~1\\AppData\\Local\\Temp\\bill-ie5804201486895318826regex_rules.txt\"",
                        "[\"C:\\Users\\BELLCH~1\\AppData\\Local\\Temp\\bill-ie5804201486895318826regex_rules.txt\"]" };
    String[] ans10 = { "C:\\Users\\BELLCH~1\\AppData\\Local\\Temp\\bill-ie5804201486895318826regex_rules.txt" };
    String[] test11 = { "C:\\Users\\BELLCH~1\\AppData\\Local\\Temp\\bill-ie5804201486895318826regex_rules.txt",
                        "[C:\\Users\\BELLCH~1\\AppData\\Local\\Temp\\bill-ie5804201486895318826regex_rules.txt]" };
    String[] ans11 = { "C:UsersBELLCH~1AppDataLocalTempbill-ie5804201486895318826regex_rules.txt" };

    for (String s : test10) {
      assertEquals(Arrays.asList(ans10), Arrays.asList(StringUtils.decodeArray(s)));
    }
    for (String s : test11) {
      assertEquals(Arrays.asList(ans11), Arrays.asList(StringUtils.decodeArray(s)));
    }
  }

  public void testRegexGroups() {
    List<String> ans = Arrays.asList("42", "123", "1965");
    assertEquals(ans, StringUtils.regexGroups(Pattern.compile("(\\d+)\\D*(\\d+)\\D*(\\d+)"), "abc-x42!123   -1965."));
  }

}
