package edu.stanford.nlp.util;

import org.junit.Assert;
import org.junit.Test;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Word;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.regex.Pattern;
import java.util.stream.BaseStream;
import java.util.stream.Collector;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

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
  public void testGetNgramsString() {
    String sentence = "a b c d e f";
    Collection<String> uniGrams = StringUtils.getNgramsString(sentence, 1, 1);
    Collection<String> biGrams = StringUtils.getNgramsString(sentence, 2, 2);
    // Same number of unigrams as chars
    assertEquals(sentence.split(" ").length, uniGrams.size());
    // Same number of bigrams as chars - 1
    assertEquals(sentence.split(" ").length - 1, biGrams.size());
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
  public void testRegexGroupsNotFound() {
    List<String> ans = Arrays.asList("42", "123", "1965");
    Assert.assertNotEquals(ans, StringUtils.regexGroups(Pattern.compile("(\\d+)\\D*(\\d+)\\D*(\\d+)"), "abc"));
    Assert.assertEquals(null, StringUtils.regexGroups(Pattern.compile("(\\d+)\\D*(\\d+)\\D*(\\d+)"), null));
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
  
  @Test
  public void testTitleCase() {
    String titleCased = "Every Word In This Sentence Is Captialized";
    String notTitleCased = "Every Word In This Sentence Is Captialized, or is it";
    String empty = "";
    assertTrue("Should accept sentence with uppercase words", StringUtils.isTitleCase(titleCased));
    assertFalse("Should not accept sentence with lowercase words", StringUtils.isTitleCase(notTitleCased));
    assertFalse("Should not accept sentence empty sentences", StringUtils.isTitleCase(empty));
  }
  
  @Test
  public void testObjectToColumnString () throws IllegalAccessException, NoSuchFieldException, NoSuchMethodException, InvocationTargetException {
    class MockObject extends Object {
      public String name = "Alice";
      public int age = 100;
    }
    MockObject mockobj = new MockObject();
    String[] fieldNames = {"name", "age"};
    String columnString = StringUtils.objectToColumnString(mockobj, ",", fieldNames);
    assertEquals("Alice,100", columnString);
  }

  @Test
  public void testStripNonAlphaNumerics() {
    assertEquals("ACB45DEFGHIJKLs3MNOpqrstus32vw", StringUtils.stripNonAlphaNumerics("ACB45D_EFGHIJ-KLs\"3MNOp@qrstus32vw"));
  }

  @Test(expected = NoSuchFieldException.class)
  public void testObjectToColumnStringThrowing () throws IllegalAccessException, NoSuchFieldException, NoSuchMethodException, InvocationTargetException {
    Object stub = new Object();
    String[] fieldNames = {"name", "age"};
    StringUtils.objectToColumnString(stub, ",", fieldNames);
    fail("Stringifying a non-existent column should throw an exception.");
  }
  
  @Test
  public void testLongestContinuousSubstring() {
    assertEquals(2, StringUtils.longestCommonContiguousSubstring("ABC", "BABA"));
    assertEquals(0, StringUtils.longestCommonContiguousSubstring("", "BABA"));
    assertEquals(0, StringUtils.longestCommonContiguousSubstring("ABC", ""));
  }
  
  @Test
  public void testPennPOSToWordnetPos() {
    assertEquals(null, StringUtils.pennPOSToWordnetPOS("Nothing"));
    assertEquals("noun", StringUtils.pennPOSToWordnetPOS("NNS"));
    assertEquals("verb", StringUtils.pennPOSToWordnetPOS("VB"));
    assertEquals("adjective", StringUtils.pennPOSToWordnetPOS("JJS"));
    assertEquals("adverb", StringUtils.pennPOSToWordnetPOS("WRB"));
  }
  
  @Test
  public void testGetShortClassName() {
    class MockObject extends Object {
    }
    assertEquals("StringUtilsTest$2MockObject", StringUtils.getShortClassName(new MockObject()));
    assertEquals("null", StringUtils.getShortClassName(null));
  }

  @Test
  public void testObjectToColumnStringIllegalAccess () throws IllegalAccessException, NoSuchFieldException, NoSuchMethodException, InvocationTargetException {
    class MockObject extends Object {
      public int age = 100;
      private String name = "Alice";
      public String getName () { return this.name; };
    }
    Object stub = new MockObject();
    String[] fieldNames = {"name", "age"};
    String cols = StringUtils.objectToColumnString(stub, ",", fieldNames);
    assertEquals("Alice,100", cols);
  }
  
  @Test
  public void testCapitalize () {
    assertEquals("Hello!", StringUtils.capitalize("hello!"));
    assertEquals("Hello!", StringUtils.capitalize("Hello!"));
  }
  
  @Test
  public void testIsCapitalized() {
    assertFalse(StringUtils.isCapitalized("starts with lowercase"));
    assertTrue(StringUtils.isCapitalized("Starts with uppercase"));
  }
  
  @Test
  public void testToTitleCase() {
    String toCapitalize = "first chapter: introduction";
    assertEquals("First Chapter: Introduction", StringUtils.toTitleCase(toCapitalize));
  }
  
  @Test
  public void testFind() {
    String vowelsRegex = "[aeiou]";
    String wordWithVowels = "Hello";
    String wordNoVowels = "Myth";
    
    boolean foundVowel = StringUtils.find(wordWithVowels, vowelsRegex);
    assertTrue("Find the vowel regex in word with vowel", foundVowel);
    foundVowel = StringUtils.find(wordNoVowels, vowelsRegex);
    assertFalse("Don't find the vowel regex in word without vowel", foundVowel);
  }
  
  @Test
  public void testLookingAt() {
    String regex = "[aeiouAEIOU]";
    String word = "Allô";
    boolean found = StringUtils.lookingAt(word, regex);
    assertTrue("Pattern should be found at the beginning of the string", found);
  }
  
  @Test
  public void testContainsIgnoreCase() {
    List<String> wordCollection = Arrays.asList("Ignore", "case", "TEST");
    assertTrue(StringUtils.containsIgnoreCase(wordCollection, "ignorE"));
    assertTrue(StringUtils.containsIgnoreCase(wordCollection, "CASE"));
    assertTrue(StringUtils.containsIgnoreCase(wordCollection, "Test"));
    assertFalse(StringUtils.containsIgnoreCase(wordCollection, "New"));
  }
  
  @Test
  public void testMapStringToArray() {
    String toMap = "x=1,y=0;";
    String[] mapped = StringUtils.mapStringToArray(toMap);
    
    assertTrue("Two elements should be present", mapped.length == 2);
    assertTrue("Element 'y' should be first", mapped[0].equalsIgnoreCase("y"));
  }

  @Test
  public void testMapStringToMap() {
    String toMap = "x=10,y=100;";
    Map<String, String> mapped = StringUtils.mapStringToMap(toMap);
    
    assertTrue("Two elements should be present", mapped.keySet().toArray().length == 2);
    assertTrue("Element with key 'y' should hold value 100", mapped.get("y").equals("100"));
  }
  
  @Test
  public void testRegexesToPatters () {
    List<String> patternsToCompile = Arrays.asList("[aeiou]", "[AEIOU]");
    Pattern firstPattern = Pattern.compile("[aeiou]");

    List<Pattern> patterns = StringUtils.regexesToPatterns(patternsToCompile);
    assertTrue(patterns.size() == 2);
    boolean patternsMatch = firstPattern.pattern().equals(patterns.get(0).pattern());
    assertTrue("Compiled pattern should match", patternsMatch);
  }
  
  @Test
  public void testMatches() {
    String consonantRegex = "[a-z&&[^aeiou]]";
    assertTrue("Should match consonants", StringUtils.matches("x", consonantRegex));
    assertFalse("Should not match vowels", StringUtils.matches("o", consonantRegex));
  }

  @Test
  public void testStringToSet() {
    String delimiter = ";";
    String delimitedString = "hello;world";
    Set<String> result = StringUtils.stringToSet(delimitedString, delimiter);
    assertEquals(2, result.toArray().length);
  }

  @Test
  public void testJoinWords() {
    Word firstWord = new Word("test");
    Word otherWord = new Word("joining");
    List<Word> wordsToJoin = Arrays.asList(firstWord, otherWord);
    String joined = StringUtils.joinWords(wordsToJoin, "-");
    assertEquals("test-joining", joined);
  }

  @Test
  public void testJoinGenericWithFunctionalArg() {
    List<String> stringsToJoin = Arrays.asList("One", "Two", "Three", "Four");
    Function<String, String> toS = x -> x.toLowerCase();
    String delimiter = "..";
    String joined = StringUtils.join(stringsToJoin, delimiter, toS, 1, 3);
    
    assertEquals("two..three", joined);
  }
  
  @Test
  public void testJoinWordsWithBounds() {
    Word firstWord = new Word("test");
    Word otherWord = new Word("joining");
    List<Word> wordsToJoin = Arrays.asList(firstWord, otherWord);
    String delimiter = "-";
    String joined = StringUtils.joinWords(wordsToJoin, delimiter, 0, 2);
    assertEquals("test-joining", joined);
  }
  
  @Test
  public void testJoinOriginalWhiteSpace() {
    CoreLabel label1 = new CoreLabel();
    label1.setWord("going");
    CoreLabel label2 = new CoreLabel();
    label2.setWord("home");
    List<CoreLabel> labels = Arrays.asList(label1, label2);
    String joined = StringUtils.joinWithOriginalWhiteSpace(labels);
    assertEquals("goinghome", joined);
  }
  
  @Test
  public void testJoinIterable() {
    List<Integer> stringsToJoin = Arrays.asList(1, 2, 3, 4);
    String delimiter = "-";
    String joined = StringUtils.join(stringsToJoin, delimiter);
    assertEquals("1-2-3-4", joined);
  }
  @Test
  public void joinObjects() {
    Integer[] objectsToJoin = {1, 2, 3};
    String joined = StringUtils.join(objectsToJoin);
    assertEquals("1 2 3", joined);
  }

  @Test
  public void joinObjectsWithGlue() {
    Integer[] objectsToJoin = {1, 2, 3};
    String glue = "-";
    String joined = StringUtils.join(objectsToJoin, glue);
    assertEquals("1-2-3", joined);
  }

  @Test
  public void joinObjectsWithRange() {
    Integer[] objectsToJoin = {100, 200, 300, 400, 500};
    String glue = "-";
    String joined = StringUtils.join(objectsToJoin, 2, 4, glue);
    assertEquals("300-400", joined);
  }

  @Test
  public void joinString() {
    String[] items = {"hello", "there", "how", "are", "you"};
    String glue = " ";
    String joined = StringUtils.join(items, glue);
    assertEquals("hello there how are you", joined);
  }

  @Test
  public void joinWithImplicitWhiteSpace() {
    List<String> items = Arrays.asList("hello", "there", "how", "are", "you");
    String joined = StringUtils.join(items);
    assertEquals("hello there how are you", joined);
  }
  
  @Test
  public void testSplitFieldsFast() {
    String whiteSpaceTokens = "x1 y1 | x2 y2 | x3 y3";
    List<List<String>> split = StringUtils.splitFieldsFast(whiteSpaceTokens, "|");
    assertEquals(3, split.size());
    assertEquals("y3", split.get(2).get(1));
  }

  @Test
  public void testSplitOnCharWithDelimiter() {
    String[] outPutToFill = {"", "", ""};
    StringUtils.splitOnChar(outPutToFill, "hello,1,200", ',');
    assertEquals(3, outPutToFill.length);
    assertEquals("hello", outPutToFill[0]);
    assertEquals("1", outPutToFill[1]);
    assertEquals("200", outPutToFill[2]);
  }
  @Test
  public void testPadBelowMax() {
    String fourCharLeftPadded = "    =";
    String padded = StringUtils.pad(fourCharLeftPadded, 9);
    assertEquals("    =    ", padded);
  }

  @Test
  public void testPadMaxReached() {
    String fourCharLeftPadded = "    =";
    String padded = StringUtils.pad(fourCharLeftPadded, 5);
    assertEquals("    =", padded);
  }

  @Test
  public void testPadEmpty() {
    String empty = null;
    String padded = StringUtils.pad(empty, 0);
    assertEquals("null", padded);
  }

  @Test
  public void testPadObject() {
    ArrayList toPad = new ArrayList();
    String padded = StringUtils.pad(toPad, 3);
    assertEquals("[] ", padded);
  }

  @Test
  public void testPadOrTrim() {
    String toPadOrTrim = " = ";
    String padded = StringUtils.padOrTrim(toPadOrTrim, 4);
    String trimmed = StringUtils.padOrTrim(toPadOrTrim, 2);
    String unchanged = StringUtils.padOrTrim(toPadOrTrim, 3);
    String nullResult = StringUtils.padOrTrim(null, 0);

    assertEquals(" =  ", padded);
    assertEquals(" =", trimmed);
    assertEquals(toPadOrTrim, unchanged);
  }

  @Test
  public void testPadLeftOrTrim() {
    String toPadOrTrim = " = ";
    String padded = StringUtils.padLeftOrTrim(toPadOrTrim, 4);
    String trimmed = StringUtils.padLeftOrTrim(toPadOrTrim, 2);
    String unchanged = StringUtils.padLeftOrTrim(toPadOrTrim, 3);
    String nullResult = StringUtils.padLeftOrTrim(null, 0);

    assertEquals("  = ", padded);
    assertEquals("= ", trimmed);
    assertEquals(toPadOrTrim, unchanged);
  }
  
  @Test
  public void testPadOrTrimObject() {
    Object objToTrim = new ArrayList();
    assertEquals("[]  ", StringUtils.padOrTrim(objToTrim, 4));
  }

  @Test
  public void testPadLeft() {
    String toPad = "<br>";
    assertEquals("  <br>", StringUtils.padLeft(toPad, 6));
    assertEquals("null", StringUtils.padLeft(null, 4));
  }

  @Test
  public void testPadObjectLeft() {
    Object toPad = new ArrayList();
    assertEquals("  []", StringUtils.padLeft(toPad, 4));
  }

  @Test
  public void testPadIntLeft() {
    int i = 123;
    assertEquals(" 123", StringUtils.padLeft(i, 4));
  }

  @Test
  public void testPadDoubleLeft() {
    double i = 123d;
    assertEquals(" 123.0", StringUtils.padLeft(i, 6));
  }
  
  @Test
  public void testTrimString() {
    String toTrim = "hello there!!!";
    assertEquals("hello there", StringUtils.trim(toTrim, 11));
    assertEquals(toTrim, StringUtils.trim(toTrim, toTrim.length() + 1));
  }

  @Test
  public void testTrimObj() {
    Object objToTrim = new ArrayList();
    assertEquals("[", StringUtils.trim(objToTrim, 1));
    assertEquals("[]", StringUtils.trim(objToTrim, 3));
  }

  @Test
  public void testTrimStringWithEllipsis() {
    String toTrim = "this might be to long?";
    assertEquals(toTrim, StringUtils.trimWithEllipsis(toTrim, toTrim.length() + 1));
    assertEquals("this might be to l...", StringUtils.trimWithEllipsis(toTrim, 21));
  }

  @Test
  public void testTrimObjWithEllipsis() {
    class MockObj extends Object {
      public String toString() {
        return "Hello there";
      }
    }
    Object objToTrim = new MockObj();
    assertEquals("Hel...", StringUtils.trimWithEllipsis(objToTrim, 6));
  }

  @Test
  public void testRepeatString() {
    String toRepeat = "hello,";
    assertEquals("hello,hello,hello,", StringUtils.repeat(toRepeat, 3));
    assertEquals("", StringUtils.repeat(toRepeat, 0));
  }
  @Test
  public void testRepeatChar() {
    char toRepeat = 'o';
    assertEquals("ooo", StringUtils.repeat(toRepeat, 3));
    assertEquals("", StringUtils.repeat(toRepeat, 0));
  }
  @Test
  public void testFileNameClean() {
    String toCleanWithWhiteSpace = "my cool song";
    String toCleanWithOtherChars = "word@word";
    String nothingToClean = "word_WORD_123";
    assertEquals("my_cool_song", StringUtils.fileNameClean(toCleanWithWhiteSpace));
    assertEquals("wordx64xword", StringUtils.fileNameClean(toCleanWithOtherChars));
    assertEquals(nothingToClean, StringUtils.fileNameClean(nothingToClean));
  }
  
  @Test
  public void testNthIndex() {
    String repeatingString = "abcabcabc";
    assertEquals(6, StringUtils.nthIndex(repeatingString, 'a', 2));
    assertEquals(-1, StringUtils.nthIndex(repeatingString, 'a', 3));
  }
  
  @Test
  public void testTruncate() {
    assertEquals("007", StringUtils.truncate(7, 0, 2));
  }
  
  @Test
  public void parseCLIArguments() {
    String[] args = {"-file", "file.jpg", "-precision", "2.0", "file.xy"};
    Map<String, Object> parse = StringUtils.parseCommandLineArguments(args, true);
    assertEquals(2, parse.size());
    parse = StringUtils.parseCommandLineArguments(args, false);
    assertEquals(2, parse.size());
  }
  
  @Test
  public void argsToMap() {
    String[] args = {"-x", "file.jpg", "-d", "100", "200"};
    Map<String, Integer> flagsToNumArgs = new HashMap();
    flagsToNumArgs.put("-x", new Integer(1));
    flagsToNumArgs.put("-d", new Integer(2));
    Map<String, String[]> result = StringUtils.argsToMap(args, flagsToNumArgs);
    
    assertEquals(1, result.get("-x").length);
    assertEquals("file.jpg", result.get("-x")[0]);
    assertEquals(2, result.get("-d").length);
    assertEquals("100", result.get("-d")[0]);
    assertEquals("200", result.get("-d")[1]);

  }

  @Test
  public void argsToMapUnconfigured() {
    String[] args = {"-x", "file.jpg", "-d", "100", "file"};
    Map<String, String[]> result = StringUtils.argsToMap(args);
    assertEquals(3, result.size());
  }
  
  @Test
  public void testStringToProperties() {
    String propsWithWhiteSpace = "name=Alice, age=100";
    String propsWithoutWhiteSpaceAndBool = "name =Alice,age=100,verbose";
    Properties props = StringUtils.stringToProperties(propsWithWhiteSpace);
    assertEquals(2, props.size());
    props = StringUtils.stringToProperties(propsWithoutWhiteSpaceAndBool);
    assertEquals(3, props.size());
  }

  @Test
  public void testArgsToPropertiesWithResolve() {
    String[] args = {"-x", "file.jpg", "--d", "100"};
    Properties props = StringUtils.argsToPropertiesWithResolve(args);
    assertEquals(2, props.size());
  }

  @Test
  public void testGetNGrams() {
    List<String> tokens = Arrays.asList("going", "to", "bed");
    Collection<String> result = StringUtils.getNgrams(tokens, 3, 10);
    assertEquals(1, result.size());
  }
  
  @Test
  public void testGetNGramsFromTokens() {
    CoreLabel label1 = new CoreLabel();
    label1.setWord("going");
    CoreLabel label2 = new CoreLabel();
    label2.setWord("go");
    List<CoreLabel> labels = Arrays.asList(label1, label2);
    Collection<String> result = StringUtils.getNgramsFromTokens(labels, 1, 2);
    assertEquals(3, result.size());
  }
  
  @Test
  public void testToString() {
    CoreLabel label1 = new CoreLabel();
    label1.setWord("going");
    CoreLabel label2 = new CoreLabel();
    label2.setWord("go");
    List<CoreLabel> labels = Arrays.asList(label1, label2);
    assertEquals("going go", StringUtils.toString(labels));
  }

  @Test
  public void testGetLevenShteinDistance() {
    String s1 = "thedeepwater";
    String s2 = "thedeepbluewater";
    int distance = StringUtils.levenshteinDistance(s1, s2);
    // TODO: Compare with other impl
    assertEquals(4, distance);
  }
  
  @Test
  public void testLevenShteinDistanceGeneric() {
    String[] s1 = {"t", "h", "e", "d", "e", "e", "p", "w", "a", "t", "e", "r"};
    String[] s2 = {"a", "d", "e", "e", "p", "w", "a", "t", "e", "r"};
    int distance = StringUtils.levenshteinDistance(s1, s2);
    assertEquals(3, distance);
  }

  @Test
  public void testIsPunct() {
    String dot = ".";
    String comma = ",";
    String exclamationMark = "!";
    String questionMark = "?";
    String word = "testing";
    assertTrue("Accept dot", StringUtils.isPunct(dot));
    assertTrue("Accept comma", StringUtils.isPunct(comma));
    assertTrue("Accept exclamation marks", StringUtils.isPunct(exclamationMark));
    assertTrue("Accept question marks", StringUtils.isPunct(questionMark));
    assertFalse("Accept punct", StringUtils.isPunct(word));
  }
  
  @Test
  public void testIsAllUpperCase() {
    String upperCase = "ABCDEFGHIJKL";
    String notUpperCase = "ABCDEFGHIjkl";
    assertTrue(StringUtils.isAllUpperCase(upperCase));
    assertFalse(StringUtils.isAllUpperCase(notUpperCase));
  }
  
  @Test
  public void testSearchAndReplace() {
    String from = "Hello";
    String to = "Allo";
    String text = "Hello there!";
    assertEquals("Allo there!", StringUtils.searchAndReplace(text, from, to));
  }
  
  @Test
  public void testMakeHTMLTable() {
    String[][] tableElements = {
        {"x", "70", "Alice"},
        {"a", "60", "Bob"}
    };
    String[] rows = {"person 1", "person 2"};
    String[] cols = {"dimons 1", "dimension 2", "dimension 3"};

    String htmlTable = StringUtils.makeHTMLTable(tableElements, rows, cols);
    boolean hasTwoRows = htmlTable.split("<td class=\"label\">person").length == 3;
    boolean hasTwoCols = htmlTable.split("<td class=\"label\">dimension").length == 4;
    assertTrue(hasTwoRows);;
  }
  
  @Test
  public void testToCSVString() {
    String[] fields = {"Peter", "12", "100"};
    String csv = StringUtils.toCSVString(fields);
    assertEquals("\"Peter\",\"12\",\"100\"", csv);
  }
  
  @Test
  public void testMakeAsciiTableCell() {
    class MockObject extends Object {
      private String content = "content";
      public String toString() { return this.content; }
      public MockObject(String content) { this.content = content; }
    }
    Object[][] cellContents = {
        { new MockObject("r1c1"), new MockObject("r1c2") },
        { new MockObject("r2c1"), new MockObject("r2c2") },
        { new MockObject("r3c1"), new MockObject("r3c2") },
    };
    Object[] rowLabels = {
        new MockObject("row1"),
        new MockObject("row2"),
        new MockObject("row3")
    };
    Object[] colLabels = { new MockObject("cell1"), new MockObject("cell2") };
    String asciiTable = StringUtils.makeTextTable(cellContents, rowLabels, colLabels, 4, 8, true);
    List<String> rows = Arrays.asList(asciiTable.split("\n"));
    boolean rowsHaveThreeCols = rows.stream().allMatch(r -> r.split("\t").length == 3);
    System.out.println(asciiTable);
    assertEquals(4, rows.size());
    assertTrue(rowsHaveThreeCols);

    String asciiTableNoHeader = StringUtils.makeTextTable(cellContents, rowLabels, null, 4, 8, true);
    assertEquals(3, asciiTableNoHeader.split("\n").length);
  }
  
  @Test
  public void testToAscii() {
    String s = "xÅÆÇÈËÌÏÐÑÒÖ×ØÙÜÝàåæçèëìïñøùüýÿ‘’“„ȓ—¢¥…";
    String expected = "xAAECEEFFDNOOxOUUYaaaeceeiinouuyy''\"\"--$$.";
    assertEquals(expected, StringUtils.toAscii(s));
  }
  
  @Test
  public void testChomp() {
    String toChomp = "Is this a oneliner?\r\n";
    String chomped = StringUtils.chomp(toChomp);
    assertTrue("Should not contain trailing newline and tab", chomped.indexOf('\n') == -1);
    toChomp = "Is this a oneliner?\n";
    chomped = StringUtils.chomp(toChomp);
    assertTrue("Should not contain trailing newline", chomped.indexOf('\n') == -1);
    toChomp = "Nothing to do";
    assertEquals(toChomp, StringUtils.chomp(toChomp));
    assertEquals(null, StringUtils.chomp(null));
  }

  @Test
  public void testChompObject() {
    class MockObject extends Object {
      public String toString() {
        return "chomp this!\r\n";
      }
    }
    Object mock = new MockObject();
    assertEquals("chomp this!", StringUtils.chomp(mock));
  }
  
  @Test
  public void testGetNotNullString() {
    String notNull = "not null";
    assertEquals(notNull, StringUtils.getNotNullString(notNull));
    assertEquals("", StringUtils.getNotNullString(null));
  }
  
  @Test
  public void testIsAlphaNumeric() {
    String alphaNumeric = "abcdeEFGh123456789";
    String notAlphaNumeric = "abcdeEFGh123456789!";
    assertTrue(StringUtils.isAlphanumeric(alphaNumeric));
    assertFalse(StringUtils.isAlphanumeric(notAlphaNumeric));
  }

  @Test
  public void testIsAlpha() {
    String alpha = "abcdefghijKLMNOPQRSTUVWXYZ";
    String notAlpha = "abcdefghijKLMNOPQRSTUVWXYZ1";
    assertTrue(StringUtils.isAlpha(alpha));
    assertFalse(StringUtils.isAlpha(notAlpha));
  }

  @Test
  public void testIsNumeric() {
    String numeric = "1234567890";
    String notNumeric  = "123457890abcde";
    assertTrue(StringUtils.isNumeric(numeric));
    assertFalse(StringUtils.isNumeric(notNumeric));
  }

  @Test
  public void testIsAcronym() {
    String acronym = "UZH";
    String notAcronym = "Hi";
    assertTrue("Accept actual acronym", StringUtils.isAcronym(acronym));
    assertFalse("Reject other words", StringUtils.isAcronym(notAcronym));
  }

  @Test
  public void testUnescapeHtml() {
    // Predefined XML entities
    String ampersand = "&amp;";
    String quotationMark = "&quot;";
    String lowerThan = "&lt;";
    String greaterThan = "&gt;";
    assertEquals("&", StringUtils.unescapeHtml3(ampersand));
    assertEquals("\"", StringUtils.unescapeHtml3(quotationMark));
    assertEquals("<", StringUtils.unescapeHtml3(lowerThan));
    assertEquals(">", StringUtils.unescapeHtml3(greaterThan));

    // Character entities
    String euro = "&#8364;";
    String tradeMark = "&#174;";
    assertEquals("€", StringUtils.unescapeHtml3(euro));
    assertEquals("®", StringUtils.unescapeHtml3(tradeMark));
  }
}
