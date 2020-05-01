package edu.stanford.nlp.util;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;

public class StringUtilsTest {

  @Test
  public void testTr() {
    Assert.assertEquals(StringUtils.tr("chris", "irs", "mop"), "chomp");
  }

  @Test
  public void testGetBaseNameNoSuffix() {
    Assert.assertEquals(StringUtils.getBaseName("/u/wcmac/foo.txt"), "foo.txt");
  }

  @Test
  public void testGetBaseNameEmptySuffix() {
    Assert.assertEquals(StringUtils.getBaseName("/u/wcmac/foo.txt", ""), "foo.txt");
  }

  @Test
  public void testGetBaseNameDotTxtSuffix() {
    Assert.assertEquals(StringUtils.getBaseName("/u/wcmac/foo.txt", ".txt"), "foo");
  }

  @Test
  public void testGetBaseNamePdfSuffix() {
    Assert.assertEquals(StringUtils.getBaseName("/u/wcmac/foo.txt", ".pdf"), "foo.txt");
  }

  @Test
  public void testArgsToProperties() {
    Properties p1 = new Properties();
    p1.setProperty("fred", "-2");
    p1.setProperty("", "joe");
    Properties p2 = new Properties();
    p2.setProperty("fred", "true");
    p2.setProperty("2", "joe");
    Map<String, Integer> argNums = new HashMap<>();
    argNums.put("fred", 1);
    Assert.assertEquals(p2, StringUtils.argsToProperties("-fred", "-2", "joe"));
    Assert.assertEquals(StringUtils.argsToProperties(new String[]{"-fred", "-2", "joe"}, argNums), p1);
  }

  @Test
  public void testValueSplit() {
    List<String> vals1 = StringUtils.valueSplit("arg(a,b),foo(d,e,f)", "[a-z]*(?:\\([^)]*\\))?", "\\s*,\\s*");
    List<String> ans1 = Arrays.asList("arg(a,b)", "foo(d,e,f)");
    Assert.assertEquals("Split failed", ans1, vals1);
    vals1 = StringUtils.valueSplit("arg(a,b) , foo(d,e,f) , ", "[a-z]*(?:\\([^)]*\\))?", "\\s*,\\s*");
    Assert.assertEquals("Split failed", ans1, vals1);
    vals1 = StringUtils.valueSplit(",arg(a,b),foo(d,e,f)", "[a-z]*(?:\\([^)]*\\))?", "\\s*,\\s*");
    List<String> ans2 = Arrays.asList("", "arg(a,b)", "foo(d,e,f)");
    Assert.assertEquals("Split failed", ans2, vals1);
    List<String> vals3 = StringUtils.valueSplit("\"quoted,comma\",\"with \\\"\\\" quote\" , \"stuff\",or not,quoted,",
                                                "\"(?:[^\"\\\\]+|\\\\\")*\"|[^,\"]+", "\\s*,\\s*");
    List<String> ans3 = Arrays.asList("\"quoted,comma\"", "\"with \\\"\\\" quote\"", "\"stuff\"", "or not", "quoted");
    Assert.assertEquals("Split failed", ans3, vals3);
  }

  @Test
  public void testLongestCommonSubstring() {
    Assert.assertEquals(12, StringUtils.longestCommonSubstring("Jo3seph Smarr!", "Joseph R Smarr"));
    Assert.assertEquals(12, StringUtils.longestCommonSubstring("Joseph R Smarr", "Jo3seph Smarr!"));
  }

  @Test
  public void testEditDistance() {
    // test insert
    Assert.assertEquals(4, StringUtils.editDistance("Hi!", "Hi you!"));
    Assert.assertEquals(5, StringUtils.editDistance("Hi!", "Hi you!?"));
    Assert.assertEquals(1, StringUtils.editDistance("sdf", "asdf"));
    Assert.assertEquals(1, StringUtils.editDistance("asd", "asdf"));
    // test delete
    Assert.assertEquals(4, StringUtils.editDistance("Hi you!", "Hi!"));
    Assert.assertEquals(5, StringUtils.editDistance("Hi you!?", "Hi!"));
    Assert.assertEquals(1, StringUtils.editDistance("asdf", "asd"));
    Assert.assertEquals(1, StringUtils.editDistance("asdf", "sdf"));
    // test modification
    Assert.assertEquals(3, StringUtils.editDistance("Hi you!", "Hi Sir!"));
    Assert.assertEquals(5, StringUtils.editDistance("Hi you!", "Hi Sir!!!"));
    // test transposition
    Assert.assertEquals(2, StringUtils.editDistance("hello", "hlelo"));
    Assert.assertEquals(2, StringUtils.editDistance("asdf", "adsf"));
    Assert.assertEquals(2, StringUtils.editDistance("asdf", "sadf"));
    Assert.assertEquals(2, StringUtils.editDistance("asdf", "asfd"));
    // test empty
    Assert.assertEquals(0, StringUtils.editDistance("", ""));
    Assert.assertEquals(3, StringUtils.editDistance("", "bar"));
    Assert.assertEquals(3, StringUtils.editDistance("foo", ""));
  }

  @Test
  public void testSplitOnChar() {
    Assert.assertEquals(3, StringUtils.splitOnChar("hello\tthere\tworld", '\t').length);
    Assert.assertEquals(2, StringUtils.splitOnChar("hello\tworld", '\t').length);
    Assert.assertEquals(1, StringUtils.splitOnChar("hello", '\t').length);

    Assert.assertEquals("hello", StringUtils.splitOnChar("hello\tthere\tworld", '\t')[0]);
    Assert.assertEquals("there", StringUtils.splitOnChar("hello\tthere\tworld", '\t')[1]);
    Assert.assertEquals("world", StringUtils.splitOnChar("hello\tthere\tworld", '\t')[2]);

    Assert.assertEquals(1, StringUtils.splitOnChar("hello\tthere\tworld\n", ' ').length);
    Assert.assertEquals("hello\tthere\tworld\n", StringUtils.splitOnChar("hello\tthere\tworld\n", ' ')[0]);

    Assert.assertEquals(5, StringUtils.splitOnChar("a\tb\tc\td\te", '\t').length);
    Assert.assertEquals(5, StringUtils.splitOnChar("\t\t\t\t", '\t').length);
    Assert.assertEquals("", StringUtils.splitOnChar("\t\t\t\t", '\t')[0]);
    Assert.assertEquals("", StringUtils.splitOnChar("\t\t\t\t", '\t')[1]);
    Assert.assertEquals("", StringUtils.splitOnChar("\t\t\t\t", '\t')[4]);
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

  @Test
  public void testStringIsNullOrEmpty() {
    Assert.assertTrue(StringUtils.isNullOrEmpty(null));
    Assert.assertTrue(StringUtils.isNullOrEmpty(""));
    Assert.assertFalse(StringUtils.isNullOrEmpty(" "));
    Assert.assertFalse(StringUtils.isNullOrEmpty("foo"));
  }

  @Test
  public void testNormalize() {
    Assert.assertEquals("can't", StringUtils.normalize("can't"));
    Assert.assertEquals("Beyonce", StringUtils.normalize("Beyoncé"));
    Assert.assertEquals("krouzek", StringUtils.normalize("kroužek"));
    Assert.assertEquals("office", StringUtils.normalize("o\uFB03ce"));
    Assert.assertEquals("DZ", StringUtils.normalize("Ǆ"));
    Assert.assertEquals("1⁄4", StringUtils.normalize("¼"));
    Assert.assertEquals("한국어", StringUtils.normalize("한국어"));
    Assert.assertEquals("조선말", StringUtils.normalize("조선말"));
    Assert.assertEquals("が", StringUtils.normalize("が"));
    Assert.assertEquals("か", StringUtils.normalize("か"));
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
          {"1997", " Ford ", "E350"},
          {"foo", "", "bar"},

          {"1999", "Chevy", "Venture \"Extended Edition, Large\"", "", "5000.00"},
          {"\",foo,"},
          {"\"", "foo"},
  };

  @Test
  public void testCSV() {
    Assert.assertEquals("Bung test", csvInputs.length, csvOutputs.length);
    for (int i = 0; i < csvInputs.length; i++) {
      String[] answer = StringUtils.splitOnCharWithQuoting(csvInputs[i], ',', '"', escapeInputs[i]);
      Assert.assertArrayEquals("Bad CSV line handling of ex " + i + ": " + Arrays.toString(csvOutputs[i]) +
              " vs. " + Arrays.toString(answer), csvOutputs[i], answer);
    }
  }

  @Test
  public void testGetCharacterNgrams() {
    testCharacterNgram("abc", 0, 0);
    testCharacterNgram("abc", 1, 1, "a", "b", "c");
    testCharacterNgram("def", 2, 2, "de", "ef");
    testCharacterNgram("abc", 1, 2, "a", "b", "c", "ab", "bc");
    testCharacterNgram("abc", 1, 3, "a", "b", "c", "ab", "bc", "abc");
    testCharacterNgram("abc", 1, 4, "a", "b", "c", "ab", "bc", "abc");
  }

  private static void testCharacterNgram(String string, int min, int max, String... expected) {
    System.out.println(makeSet(expected));
    System.out.println(StringUtils.getCharacterNgrams(string, min, max));
    Assert.assertEquals(makeSet(expected),
            new HashSet<>(StringUtils.getCharacterNgrams(string, min, max)));
  }

  @SafeVarargs
  private static <T> Set<T> makeSet(T... elems) {
    return new HashSet<>(Arrays.asList(elems));
  }


  @Test
  public void testExpandEnvironmentVariables() {
    Map<String, String> env = new HashMap<>();
    env.put("A", "[outA]");
    env.put("A_B", "[outA_B]");
    env.put("a_B", "[outa_B]");
    env.put("a_B45", "[outa_B45]");
    env.put("_A", "[out_A]");
    env.put("3A", "[out_3A]");

    Assert.assertEquals("xxx [outA] xxx", StringUtils.expandEnvironmentVariables("xxx $A xxx", env));
    Assert.assertEquals("xxx[outA] xxx", StringUtils.expandEnvironmentVariables("xxx$A xxx", env));
    Assert.assertEquals("xxx[outA]xxx", StringUtils.expandEnvironmentVariables("xxx${A}xxx", env));
    Assert.assertEquals("xxx [outA_B] xxx", StringUtils.expandEnvironmentVariables("xxx $A_B xxx", env));
    Assert.assertEquals("xxx [outa_B] xxx", StringUtils.expandEnvironmentVariables("xxx $a_B xxx", env));
    Assert.assertEquals("xxx [outa_B45] xxx", StringUtils.expandEnvironmentVariables("xxx $a_B45 xxx", env));
    Assert.assertEquals("xxx [out_A] xxx", StringUtils.expandEnvironmentVariables("xxx $_A xxx", env));
    Assert.assertEquals("xxx $3A xxx", StringUtils.expandEnvironmentVariables("xxx $3A xxx", env));
    Assert.assertEquals("xxx  xxx", StringUtils.expandEnvironmentVariables("xxx $UNDEFINED xxx", env));
  }

  @Test
  public void testDecodeArray() throws IOException {
    String tempFile1 = Files.createTempFile("test", "tmp").toString();
    String tempFile2 = Files.createTempFile("test", "tmp").toString();
    String[] decodedArray = StringUtils.decodeArray('\'' + tempFile1 + "','" + tempFile2 + '\'');

    Assert.assertEquals(2, decodedArray.length);
    Assert.assertEquals(tempFile1, decodedArray[0]);
    Assert.assertEquals(tempFile2, decodedArray[1]);

    String[] test10 = {"\"C:\\Users\\BELLCH~1\\AppData\\Local\\Temp\\bill-ie5804201486895318826regex_rules.txt\"",
                       "[\"C:\\Users\\BELLCH~1\\AppData\\Local\\Temp\\bill-ie5804201486895318826regex_rules.txt\"]"};
    String[] ans10 = {"C:\\Users\\BELLCH~1\\AppData\\Local\\Temp\\bill-ie5804201486895318826regex_rules.txt"};
    String[] test11 = {"C:\\Users\\BELLCH~1\\AppData\\Local\\Temp\\bill-ie5804201486895318826regex_rules.txt",
                       "[C:\\Users\\BELLCH~1\\AppData\\Local\\Temp\\bill-ie5804201486895318826regex_rules.txt]"};
    String[] ans11 = {"C:UsersBELLCH~1AppDataLocalTempbill-ie5804201486895318826regex_rules.txt"};

    for (String s : test10) {
      Assert.assertEquals(Arrays.asList(ans10), Arrays.asList(StringUtils.decodeArray(s)));
    }
    for (String s : test11) {
      Assert.assertEquals(Arrays.asList(ans11), Arrays.asList(StringUtils.decodeArray(s)));
    }
  }

  @Test
  public void testRegexGroups() {
    List<String> ans = Arrays.asList("42", "123", "1965");
    Assert.assertEquals(ans, StringUtils.regexGroups(Pattern.compile("(\\d+)\\D*(\\d+)\\D*(\\d+)"), "abc-x42!123   -1965."));
  }

  @Test
  public void testEscapeJsonString() {
    Assert.assertEquals("\\u0001\\b\\r\\u001D\\u001Fz", StringUtils.escapeJsonString("\u0001\b\r\u001d\u001fz"));
    Assert.assertEquals("food", StringUtils.escapeJsonString("food"));
    Assert.assertEquals("\\\\\\\"here\\u0000goes\\b\\u000B", StringUtils.escapeJsonString("\\\"here\u0000goes\b\u000B"));
  }

  private static final Pattern p = Pattern.compile("foo[dl]");
  private static final Pattern HYPHENS_DASHES = Pattern.compile("[-\u2010-\u2015]");

  @Test
  public void testIndexOfRegex() {
    Assert.assertEquals(10, StringUtils.indexOfRegex(p, "Fred is a fool for food"));
    Assert.assertEquals(2, StringUtils.indexOfRegex(HYPHENS_DASHES, "18-11"));
    Assert.assertEquals(5, StringUtils.indexOfRegex(HYPHENS_DASHES, "Asian-American"));
  }

  @Test
  public void testSplit() {
    Assert.assertEquals(Arrays.asList("1", "2"), StringUtils.split("1 2"));
    Assert.assertEquals(Arrays.asList("1"), StringUtils.split("1"));
    Assert.assertEquals(Arrays.asList("1", "2", "3"), StringUtils.split("1 2 3"));
    Assert.assertEquals(Arrays.asList("1", "2", "3"), StringUtils.split("1     2     3"));
    // java split semantics cut off the trailing entities for split(..., 0)
    Assert.assertEquals(Arrays.asList("", "1", "2", "3"), StringUtils.split("   1     2     3   "));
  }

  @Test
  public void testSplitRegex() {
    Assert.assertEquals(Arrays.asList("a", "dfa"), StringUtils.split("asdfa", "s"));
    // java split semantics cut off the trailing entities for split(..., 0)
    Assert.assertEquals(Arrays.asList("", "sdf"), StringUtils.split("asdfa", "a"));
  }

  @Test
  public void testSplitKeepDelimiter() {
    Assert.assertEquals(Arrays.asList("a", "s", "dfa"), StringUtils.splitKeepDelimiter("asdfa", "s"));
    // java split semantics cut off the trailing entities for split(..., 0)
    Assert.assertEquals(Arrays.asList("asdf", "\n", "sdf"), StringUtils.splitKeepDelimiter("asdf\nsdf", "\\R"));
    Assert.assertEquals(Arrays.asList("asdf", "\n", "\n", "sdf"), StringUtils.splitKeepDelimiter("asdf\n\nsdf", "\\R"));
    Assert.assertEquals(Arrays.asList("\n", "asdf", "\n", "sdf"), StringUtils.splitKeepDelimiter("\nasdf\nsdf", "\\R"));
  }

  @Test
  public void testSplitLinesKeepNewlines() {
    Assert.assertEquals(Arrays.asList("asdf", "\n", "sdf"), StringUtils.splitLinesKeepNewlines("asdf\nsdf"));
    Assert.assertEquals(Arrays.asList("asdf", "\n", "sdf", "\n"), StringUtils.splitLinesKeepNewlines("asdf\nsdf\n"));
    Assert.assertEquals(Arrays.asList("asdf", "\r\n", "sdf", "\n"), StringUtils.splitLinesKeepNewlines("asdf\r\nsdf\n"));
    Assert.assertEquals(Arrays.asList("asdf", "\r\n", "sdf", "\r\n", "\r\n"), StringUtils.splitLinesKeepNewlines("asdf\r\nsdf\r\n\r\n"));
  }
}
