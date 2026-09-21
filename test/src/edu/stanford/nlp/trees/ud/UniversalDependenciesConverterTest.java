package edu.stanford.nlp.trees.ud;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.Properties;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import edu.stanford.nlp.io.IOUtils;

/**
 * What UniversalDependenciesConverter writes for a small English treebank.
 *<br>
 * These record the behavior rather than argue for it: the expected values
 * are what the converter produced when the test was written.  That is what
 * makes them useful when something underneath changes, since anything which
 * moves shows up here as a diff.
 *<br>
 * Only the CoNLL-U input path is covered.  The tree input path needs a
 * treebank on disk and does not touch the reader.
 *
 * @author John Bauer
 */
public class UniversalDependenciesConverterTest {

  /**
   * A relative clause, a coordination sharing a preposition, and a
   * multiword token with a misc of its own
   */
  private static final String FIXTURE = String.join("\n",
      "# sent_id = relcl",
      "# text = the man who I saw left",
      "1\tthe\tthe\tDET\tDT\tDefinite=Def\t2\tdet\t_\t_",
      "2\tman\tman\tNOUN\tNN\tNumber=Sing\t6\tnsubj\t_\t_",
      "3\twho\twho\tPRON\tWP\tPronType=Rel\t5\tobj\t_\t_",
      "4\tI\tI\tPRON\tPRP\tNumber=Sing\t5\tnsubj\t_\t_",
      "5\tsaw\tsee\tVERB\tVBD\tTense=Past\t2\tacl:relcl\t_\t_",
      "6\tleft\tleave\tVERB\tVBD\tTense=Past\t0\troot\t_\t_",
      "",
      "# sent_id = coord",
      "# text = He works in Paris and London.",
      "1\tHe\the\tPRON\tPRP\tNumber=Sing\t2\tnsubj\t_\t_",
      "2\tworks\twork\tVERB\tVBZ\tNumber=Sing\t0\troot\t_\t_",
      "3\tin\tin\tADP\tIN\t_\t4\tcase\t_\t_",
      "4\tParis\tParis\tPROPN\tNNP\tNumber=Sing\t2\tobl\t_\t_",
      "5\tand\tand\tCCONJ\tCC\t_\t6\tcc\t_\t_",
      "6\tLondon\tLondon\tPROPN\tNNP\tNumber=Sing\t4\tconj\t_\tSpaceAfter=No",
      "7\t.\t.\tPUNCT\t.\t_\t2\tpunct\t_\t_",
      "",
      "# sent_id = mwt",
      "# text = it's fine",
      "1-2\tit's\t_\t_\t_\t_\t_\t_\t_\tGloss=it+is",
      "1\tit\tit\tPRON\tPRP\tNumber=Sing\t3\tnsubj\t_\t_",
      "2\t's\tbe\tAUX\tVBZ\tNumber=Sing\t3\tcop\t_\t_",
      "3\tfine\tfine\tADJ\tJJ\t_\t0\troot\t_\tSpaceAfter=No",
      "",
      "");

  /** Run the converter over the fixture, with any extra properties as name, value pairs */
  static String convert(String... properties) throws Exception {
    File file = IOUtils.writeStringToTempFile(FIXTURE, "convertertest", "UTF-8");
    file.deleteOnExit();
    try {
      Properties props = new Properties();
      props.setProperty("conlluFile", file.getPath());
      for (int i = 0; i < properties.length; i += 2) {
        props.setProperty(properties[i], properties[i + 1]);
      }
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (PrintStream out = new PrintStream(bytes, true, "UTF-8")) {
        UniversalDependenciesConverter.convert(props, out);
      }
      return bytes.toString("UTF-8");
    } finally {
      file.delete();
    }
  }

  /** The lines of a written treebank, ending with the blank line after the last sentence */
  static String written(String... lines) {
    StringBuilder text = new StringBuilder();
    for (String line : lines) {
      text.append(line).append(System.lineSeparator());
    }
    text.append(System.lineSeparator());
    return text.toString();
  }

  /**
   * The basic conversion
   *<br>
   * Every DEPS column is empty here, which
   * testBasicHasNoEnhancedDependencies says on its own.
   */
  @Test
  public void testBasic() throws Exception {
    assertEquals(written(
        "# sent_id = 0",
        "# sent_id = relcl",
        "# text = the man who I saw left",
        "1\tthe\tthe\tDET\tDT\tDefinite=Def\t2\tdet\t_\t_",
        "2\tman\tman\tNOUN\tNN\tNumber=Sing\t6\tnsubj\t_\t_",
        "3\twho\twho\tPRON\tWP\tPronType=Rel\t5\tobj\t_\t_",
        "4\tI\tI\tPRON\tPRP\tNumber=Sing\t5\tnsubj\t_\t_",
        "5\tsaw\tsee\tVERB\tVBD\tTense=Past\t2\tacl:relcl\t_\t_",
        "6\tleft\tleave\tVERB\tVBD\tTense=Past\t0\troot\t_\t_",
        "",
        "# sent_id = 1",
        "# sent_id = coord",
        "# text = He works in Paris and London.",
        "1\tHe\the\tPRON\tPRP\tNumber=Sing\t2\tnsubj\t_\t_",
        "2\tworks\twork\tVERB\tVBZ\tNumber=Sing\t0\troot\t_\t_",
        "3\tin\tin\tADP\tIN\t_\t4\tcase\t_\t_",
        "4\tParis\tParis\tPROPN\tNNP\tNumber=Sing\t2\tobl\t_\t_",
        "5\tand\tand\tCCONJ\tCC\t_\t6\tcc\t_\t_",
        "6\tLondon\tLondon\tPROPN\tNNP\tNumber=Sing\t4\tconj\t_\tSpaceAfter=No",
        "7\t.\t.\tPUNCT\t.\t_\t2\tpunct\t_\t_",
        "",
        "# sent_id = 2",
        "# sent_id = mwt",
        "# text = it's fine",
        "1-2\tit's\t_\t_\t_\t_\t_\t_\t_\tGloss=it+is",
        "1\tit\tit\tPRON\tPRP\tNumber=Sing\t3\tnsubj\t_\t_",
        "2\t's\tbe\tAUX\tVBZ\tNumber=Sing\t3\tcop\t_\t_",
        "3\tfine\tfine\tADJ\tJJ\t_\t0\troot\t_\tSpaceAfter=No"),
        convert());
  }

  @Test
  public void testEnhanced() throws Exception {
    assertEquals(written(
        "# sent_id = 0",
        "# sent_id = relcl",
        "# text = the man who I saw left",
        "1\tthe\tthe\tDET\tDT\tDefinite=Def\t2\tdet\t2:det\t_",
        "2\tman\tman\tNOUN\tNN\tNumber=Sing\t6\tnsubj\t5:obj|6:nsubj\t_",
        "3\twho\twho\tPRON\tWP\tPronType=Rel\t5\tobj\t2:ref\t_",
        "4\tI\tI\tPRON\tPRP\tNumber=Sing\t5\tnsubj\t5:nsubj\t_",
        "5\tsaw\tsee\tVERB\tVBD\tTense=Past\t2\tacl:relcl\t2:acl:relcl\t_",
        "6\tleft\tleave\tVERB\tVBD\tTense=Past\t0\troot\t0:root\t_",
        "",
        "# sent_id = 1",
        "# sent_id = coord",
        "# text = He works in Paris and London.",
        "1\tHe\the\tPRON\tPRP\tNumber=Sing\t2\tnsubj\t2:nsubj\t_",
        "2\tworks\twork\tVERB\tVBZ\tNumber=Sing\t0\troot\t0:root\t_",
        "3\tin\tin\tADP\tIN\t_\t4\tcase\t4:case\t_",
        "4\tParis\tParis\tPROPN\tNNP\tNumber=Sing\t2\tobl\t2:obl:in\t_",
        "5\tand\tand\tCCONJ\tCC\t_\t6\tcc\t6:cc\t_",
        "6\tLondon\tLondon\tPROPN\tNNP\tNumber=Sing\t4\tconj\t2:obl:in|4:conj:and\tSpaceAfter=No",
        "7\t.\t.\tPUNCT\t.\t_\t2\tpunct\t2:punct\t_",
        "",
        "# sent_id = 2",
        "# sent_id = mwt",
        "# text = it's fine",
        "1-2\tit's\t_\t_\t_\t_\t_\t_\t_\tGloss=it+is",
        "1\tit\tit\tPRON\tPRP\tNumber=Sing\t3\tnsubj\t3:nsubj\t_",
        "2\t's\tbe\tAUX\tVBZ\tNumber=Sing\t3\tcop\t3:cop\t_",
        "3\tfine\tfine\tADJ\tJJ\t_\t0\troot\t0:root\tSpaceAfter=No"),
        convert("outputRepresentation", "enhanced"));
  }

  @Test
  public void testEnhancedPlusPlus() throws Exception {
    // nothing in this fixture is treated differently by enhanced++, so the
    // two come out the same.  a fixture which told them apart would be
    // worth having
    assertEquals(convert("outputRepresentation", "enhanced"),
                 convert("outputRepresentation", "enhanced++"));
  }

  @Test
  public void testReplaceLemmata() throws Exception {
    // the lemmata in the fixture are already the ones Morphology produces,
    // so replacing them changes nothing here
    assertEquals(convert("outputRepresentation", "enhanced"),
                 convert("outputRepresentation", "enhanced", "replaceLemmata", "true"));
  }

  @Test
  public void testBasicHasNoEnhancedDependencies() throws Exception {
    // a basic treebank says nothing in its DEPS column: the enhanced graph
    // is what fills that in, and a basic conversion has none
    for (String line : convert().split("\\R")) {
      if (line.startsWith("#") || line.isEmpty()) {
        continue;
      }
      String[] fields = line.split("\t");
      assertEquals(line, "_", fields[8]);
    }
  }

  @Test
  public void testMultiWordTokenMiscIsKept() throws Exception {
    // the misc of an MWT range line comes through the reader and out the
    // writer.  SpaceAfter=No lives there in a great many treebanks
    for (String written : new String[] {convert(), convert("outputRepresentation", "enhanced")}) {
      assertTrue(written, written.contains("1-2\tit's\t_\t_\t_\t_\t_\t_\t_\tGloss=it+is"));
    }
  }

  /**
   * Run the converter with a text file as well, one line of text per sentence
   */
  static String convertWithText(String text, String... properties) throws Exception {
    File textFile = IOUtils.writeStringToTempFile(text, "convertertext", "UTF-8");
    textFile.deleteOnExit();
    try {
      String[] withText = new String[properties.length + 2];
      System.arraycopy(properties, 0, withText, 0, properties.length);
      withText[properties.length] = "textFile";
      withText[properties.length + 1] = textFile.getPath();
      return convert(withText);
    } finally {
      textFile.delete();
    }
  }

  /** The misc of the word with this form, in the first sentence it turns up in */
  private static String miscOf(String written, String word) {
    for (String line : written.split("\\R")) {
      String[] fields = line.split("\t");
      if (fields.length == 10 && fields[1].equals(word)) {
        return fields[9];
      }
    }
    return null;
  }

  @Test
  public void testTextFileSetsTheSpacing() throws Exception {
    // the text says where the spaces are, and the spacing comes back out in
    // the misc column.  the fixture's own SpaceAfter is replaced by what
    // the text says, not added to it
    String text = String.join("\n",
        "the man who I saw left",
        "He works in Paris and London.",
        "it's fine",
        "");
    String written = convertWithText(text);
    assertEquals("_", miscOf(written, "Paris"));
    assertEquals("SpaceAfter=No", miscOf(written, "London"));
    assertEquals("_", miscOf(written, "."));
  }

  @Test
  public void testTextFileWithWiderSpacing() throws Exception {
    // two spaces between two words is not the same as one, and says so
    String text = String.join("\n",
        "the man who I saw left",
        "He works in  Paris and London.",
        "it's fine",
        "");
    String written = convertWithText(text);
    assertEquals("SpacesAfter=\\s\\s", miscOf(written, "in"));
  }

  @Test
  public void testTextFileWhichDoesNotMatch() throws Exception {
    // the second line of text is not the second sentence, so its words
    // cannot be found there, and the converter stops with an error rather
    // than taking the spacing of the sentence from the wrong text
    String text = String.join("\n",
        "the man who I saw left",
        "something else entirely",
        "it's fine",
        "");
    try {
      convertWithText(text);
      fail("Expected a text which does not match the sentence to be reported");
    } catch (RuntimeException e) {
      assertTrue(e.getMessage(), e.getMessage().contains("Cannot find word"));
    }
  }

  @Test
  public void testTheConverterWritesItsOwnSentenceIds() throws Exception {
    // the converter numbers the sentences itself, and the writer prints the
    // comments the file came with, so a CoNLL-U input gives two sent_id
    // lines for each sentence
    String written = convert();
    assertTrue(written, written.contains("# sent_id = 0"));
    assertTrue(written, written.contains("# sent_id = relcl"));
  }
}
