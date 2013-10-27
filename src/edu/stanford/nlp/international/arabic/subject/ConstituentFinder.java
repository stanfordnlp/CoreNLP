package edu.stanford.nlp.international.arabic.subject;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexParseException;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;


public class ConstituentFinder {
  private TregexPattern subjMatcher;
  private TregexPattern verbMatcher;
  private TregexPattern npMatcher;

  private final boolean VERBOSE;

  public ConstituentFinder(boolean verbose) {
    VERBOSE = verbose;
    try {
      verbMatcher = TregexPattern.compile("/^VB/ > @VP");
      subjMatcher = TregexPattern.compile("/^NP-SBJ/ > @VP");
      npMatcher = TregexPattern.compile("/^NP/ !> /^NP/");      
    } catch (TregexParseException e) {
      System.err.printf("%s: Failed to compile Tregex patterns during initialization\n",this.getClass().getName());
    }
  }

  public static List<Tree> runPattern(Tree t, TregexPattern p) {
    List<Tree> matches = new ArrayList<Tree>();
    TregexMatcher tregexMatcher = p.matcher(t);

    if(tregexMatcher.find()) {
      do {
        matches.add(tregexMatcher.getMatch());
      }
      while(tregexMatcher.findNextMatchingNode());
    }

    return matches;
  }

  public List<Tree> findSubjects(Tree t) {
    List<Tree> subjMatches = runPattern(t,subjMatcher);

    System.err.printf("%s: Found %d subjects\n", this.getClass().getName(), subjMatches.size());

    if(VERBOSE)
      printTreesToDebug(subjMatches);

    return subjMatches;
  }

  public List<Tree> findVerbs(Tree t) {
    List<Tree> verbMatches = runPattern(t,verbMatcher);

    System.err.printf("%s: Found %d verbs\n",this.getClass().getName(), verbMatches.size());

    if(VERBOSE)
      printTreesToDebug(verbMatches);

    return verbMatches;
  }

  public List<Tree> findNPs(Tree t) {
    List<Tree> npMatches = runPattern(t,npMatcher);

    System.err.printf("%s: Found %d NPs\n",this.getClass().getName(), npMatches.size());

    if(VERBOSE)
      printTreesToDebug(npMatches);

    return npMatches;
  }

  private void printTreesToDebug(List<Tree> trees) {
    PrintWriter stderr = new PrintWriter(System.err);
    if(!trees.isEmpty())
      for(Tree t : trees)
        t.indentedListPrint(stderr, true);
  }
}
