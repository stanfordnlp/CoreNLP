package edu.stanford.nlp.time;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Checks that the SUTime rule files in the source tree match the copies shipped in the
 * models jar.
 *
 * <p>The pipeline loads its grammar from {@code edu/stanford/nlp/models/sutime/} on the
 * classpath, which comes from the models jar. The copies under
 * {@code src/edu/stanford/nlp/time/rules/} are the editable master, and nothing in the
 * build connects the two. Editing a rule in the source tree therefore has no effect on
 * anything until the models jar is rebuilt, and the two can sit out of step
 * indefinitely without any test noticing.
 *
 * <p>That is worth one test rather than a second copy of every behavioural test. Tests
 * that exercise rule behaviour point at the source tree, because that is the copy being
 * edited and a red build there means a real mistake. This test is the only one that
 * cares about the jar, and when it fails the action is always the same: rebuild the
 * models jar. Duplicating the behavioural tests against the jar would instead leave
 * every new rule with a test that fails from the day it is written until release, which
 * teaches people to ignore it.
 *
 * <p>This is an ITest because it needs the models jar on the classpath.
 */
public class SUTimeRuleSyncITest {

  /** The editable master copies. */
  private static final String SOURCE_DIR = "src/edu/stanford/nlp/time/rules";

  /** Where the pipeline actually loads them from. */
  private static final String CLASSPATH_DIR = "/edu/stanford/nlp/models/sutime";

  @Test
  public void testSourceRulesMatchModelsJar() throws IOException {
    File sourceDir = new File(SOURCE_DIR);
    assertTrue("cannot find " + sourceDir.getAbsolutePath()
                    + " -- this test expects to run from the repository root",
            sourceDir.isDirectory());

    File[] sourceFiles = sourceDir.listFiles((d, name) -> name.endsWith(".sutime.txt"));
    assertNotNull(sourceFiles);
    assertTrue("no rule files found in " + SOURCE_DIR, sourceFiles.length > 0);
    Arrays.sort(sourceFiles);

    List<String> problems = new ArrayList<>();
    for (File sourceFile : sourceFiles) {
      String resource = CLASSPATH_DIR + '/' + sourceFile.getName();
      List<String> shipped = readClasspath(resource);
      if (shipped == null) {
        problems.add(sourceFile.getName() + ": not present at " + resource
                + " -- a new rule file has to be added to the models jar");
        continue;
      }
      List<String> master = Files.readAllLines(sourceFile.toPath(), StandardCharsets.UTF_8);
      String difference = firstDifference(master, shipped);
      if (difference != null) {
        problems.add(sourceFile.getName() + ": " + difference);
      }
    }

    if ( ! problems.isEmpty()) {
      StringBuilder sb = new StringBuilder(
              "SUTime rules in the source tree do not match the models jar. Rebuild the "
                      + "models jar so the shipped grammar picks up these edits.");
      for (String problem : problems) {
        sb.append("\n  ").append(problem);
      }
      fail(sb.toString());
    }
  }

  /**
   * Read a resource from the classpath only. Deliberately not IOUtils, which falls back
   * to the filesystem and would happily return the source copy we are comparing against.
   */
  private static List<String> readClasspath(String resource) throws IOException {
    try (InputStream in = SUTimeRuleSyncITest.class.getResourceAsStream(resource)) {
      if (in == null) {
        return null;
      }
      List<String> lines = new ArrayList<>();
      try (BufferedReader br = new BufferedReader(
              new InputStreamReader(in, StandardCharsets.UTF_8))) {
        String line;
        while ((line = br.readLine()) != null) {
          lines.add(line);
        }
      }
      return lines;
    }
  }

  /**
   * Describe the first line that differs, or null if the two agree. Line endings are
   * already normalised away by readLine, and trailing whitespace is ignored, so a
   * checkout with different line endings from the jar does not fail here.
   */
  private static String firstDifference(List<String> master, List<String> shipped) {
    int shared = Math.min(master.size(), shipped.size());
    for (int i = 0; i < shared; i++) {
      String a = stripTrailing(master.get(i));
      String b = stripTrailing(shipped.get(i));
      if ( ! a.equals(b)) {
        return "line " + (i + 1) + " differs\n      source: " + a + "\n      jar:    " + b;
      }
    }
    if (master.size() != shipped.size()) {
      return "source has " + master.size() + " lines, jar has " + shipped.size();
    }
    return null;
  }

  private static String stripTrailing(String s) {
    int end = s.length();
    while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) {
      end--;
    }
    return s.substring(0, end);
  }

}
