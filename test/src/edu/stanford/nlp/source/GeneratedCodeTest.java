package edu.stanford.nlp.source;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.Test;

public class GeneratedCodeTest {

  /**
   * Tests that auto-generated code wasn't changed after the original source modifications.
   */
  @Test
  public void generatedCode() throws IOException, NoHeadException, GitAPIException {
    try (Repository repo = new FileRepositoryBuilder().findGitDir().build();
        Git git = new Git(repo)) {

      assertAutoGen(git, 0, "edu/stanford/nlp/ling/tokensregex/parser", "TokenSequenceParser.jj", "ParseException",
          "SimpleCharStream", "Token", "TokenMgrError", "TokenSequenceParser", "TokenSequenceParserConstants",
          "TokenSequenceParserTokenManager");

      assertAutoGen(git, 0, "edu/stanford/nlp/semgraph/semgrex", "SemgrexParser.jj", "ParseException", "SemgrexParser",
          "SemgrexParserConstants", "SimpleCharStream", "SemgrexParserTokenManager", "Token", "TokenMgrError");

      assertAutoGen(git, 1491167706, "edu/stanford/nlp/trees/tregex/tsurgeon", "TsurgeonParser.jjt",
          "TsurgeonParser.jj", "JJTTsurgeonParserState", "ParseException", "SimpleCharStream", "Token", "TokenMgrError",
          "TsurgeonParserTreeConstants");

      assertAutoGen(git, 0, "edu/stanford/nlp/trees/tregex", "TregexParser.jj", "ParseException", "SimpleCharStream",
          "Token", "TokenMgrError", "TregexParser", "TregexParserConstants", "TregexParserTokenManager");
    }
  }

  /**
   * @param lastChangeTime the time of the last accepted manual change of the generated code
   */
  private void assertAutoGen(Git git, int lastChangeTime, String packageName, String sourceFile,
      String... generatedFiles) throws NoHeadException, GitAPIException {
    Path packagePath = Paths.get("src", packageName);
    assertTrue(Files.exists(packagePath));

    RevCommit sourceCommit = getLastCommit(git, packagePath, sourceFile);
    for (String file : generatedFiles) {
      RevCommit generatedCommit = getLastCommit(git, packagePath, file);
      int generatedTime = generatedCommit.getCommitTime();

      assertTrue(
          file + " was committed after " + sourceFile + "\n"
              + "If this is a manual change, then please set the lastChangeTime, with something like:\n"
              + "assertAutoGen(git, " + generatedTime + ", \"" + packageName + "\", ...",
          generatedTime <= sourceCommit.getCommitTime() || generatedTime <= lastChangeTime);
    }
  }

  private static RevCommit getLastCommit(Git git, Path packagePath, String file)
      throws NoHeadException, GitAPIException {
    Path resourcePath = packagePath.resolve(file);
    if (!Files.exists(resourcePath)) {
      resourcePath = packagePath.resolve(file + ".java");
    }
    assertTrue("Resource not found " + resourcePath, Files.exists(resourcePath));
    return git.log().addPath(resourcePath.toString().replace('\\', '/')).call().iterator().next();
  }
}
