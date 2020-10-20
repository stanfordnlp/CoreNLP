package edu.stanford.nlp.trees;

import junit.framework.TestCase;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unit tests for the GrammaticalStructure family of classes.
 *
 * @author dramage
 * @author mcdm
 */
public class GrammaticalStructureTest extends TestCase {

  /**
   * Turn token string into HashSet to abstract over ordering
   */
  private static HashSet<String> tokenSet(String tokenString) {
    Pattern tokenPattern = Pattern.compile("(\\S+\\(\\S+-\\d+, \\S+-\\d+\\))");
    Matcher tpMatcher = tokenPattern.matcher(tokenString);
    HashSet<String> tokenSet = new HashSet<>();
    while (tpMatcher.find()) {
      tokenSet.add(tpMatcher.group());
    }
    return tokenSet;
  }

  private static HashSet<String> tokenSet(List<TypedDependency> ds) {
    HashSet<String> tokenSet = new HashSet<>();
    for (TypedDependency d: ds) {
      tokenSet.add(d.toString());
    }
    return tokenSet;
  }

  /**
   * Tests that we can extract dependency relations correctly from
   * some hard-coded trees.
   */
  public void testEnglishDependenciesByTree() {
    // the trees to test
    String[] testTrees = new String[]{
         "((S (NP (NNP Sam)) (VP (VBD died) (NP-TMP (NN today)))))",
         "(ROOT (S (NP (PRP I)) (VP (VBD saw) (NP (NP (DT the) (NN book)) (SBAR (WHNP (WDT which)) (S (NP (PRP you)) (VP (VBD bought)))))) (. .)))"
    };

    // the expected dependency answers (basic)
    String[] testAnswers = new String[] {
        "root(ROOT-0, died-2) nsubj(died-2, Sam-1) tmod(died-2, today-3)",
        "nsubj(saw-2, I-1) root(ROOT-0, saw-2) det(book-4, the-3) dobj(saw-2, book-4) dobj(bought-7, which-5) ref(book-4, which-5) dobj(bought-7, which-5) nsubj(bought-7, you-6) rcmod(book-4, bought-7)"
    };

    // the expected dependency answers (collapsed dependencies)
    String[] testAnswersCollapsed = new String[] {
        "root(ROOT-0, died-2) nsubj(died-2, Sam-1) tmod(died-2, today-3)",
        "nsubj(saw-2, I-1) root(ROOT-0, saw-2) det(book-4, the-3) dobj(saw-2, book-4) dobj(bought-7, book-4) nsubj(bought-7, you-6) rcmod(book-4, bought-7)"

    };

    // the expected dependency answers (conjunctions processed)
    String[] testAnswersCCProcessed = new String[] {
        "root(ROOT-0, died-2) nsubj(died-2, Sam-1) tmod(died-2, today-3)",
        "nsubj(saw-2, I-1) root(ROOT-0, saw-2) det(book-4, the-3) dobj(saw-2, book-4) dobj(bought-7, book-4) nsubj(bought-7, you-6) rcmod(book-4, bought-7)"
    };

    for (int i = 0; i < testTrees.length; i++) {
      String testTree = testTrees[i];

      String testAnswer           = testAnswers[i];
      String testAnswerCollapsed  = testAnswersCollapsed[i];
      String testAnswerCCProcessed = testAnswersCCProcessed[i];

      HashSet<String> testAnswerTokens = tokenSet(testAnswer);
      HashSet<String> testAnswerCollapsedTokens = tokenSet(testAnswerCollapsed);
      HashSet<String> testAnswerCCProcessedTokens = tokenSet(testAnswerCCProcessed);

      Tree tree;
      try {
        tree = new PennTreeReader(new StringReader(testTree),
            new LabeledScoredTreeFactory()).readTree();
      } catch (IOException e) {
        // these trees should all parse correctly
        throw new RuntimeException(e);
      }

      GrammaticalStructure gs = new EnglishGrammaticalStructure(tree);

      assertEquals("Unexpected basic dependencies for tree "+testTree,
          testAnswerTokens,  tokenSet(gs.typedDependencies(GrammaticalStructure.Extras.MAXIMAL)));
      assertEquals("Unexpected collapsed dependencies for tree "+testTree,
          testAnswerCollapsedTokens,  tokenSet(gs.typedDependenciesCollapsed(GrammaticalStructure.Extras.MAXIMAL)));
      assertEquals("Unexpected cc-processed dependencies for tree "+testTree,
          testAnswerCCProcessedTokens,  tokenSet(gs.typedDependenciesCCprocessed(GrammaticalStructure.Extras.MAXIMAL)));
    }

  }

}
