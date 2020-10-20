package edu.stanford.nlp.ling.tokensregex.demo;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.tokensregex.MultiPatternMatcher;
import edu.stanford.nlp.ling.tokensregex.SequenceMatchResult;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;

/** Demo of how to use TokenSequence{Pattern,Matcher}.
 *
 *  @author Christopher Manning
 */
public class TokensRegexMatcher {

  private TokensRegexMatcher() {} // static demo class

  public static void main(String[] args) throws IOException {
    if (args.length < 2) {
      System.err.println("TokensRegexMatcher rules file [outFile]");
      return;
    }
    String rules = args[0];
    PrintWriter out;
    if (args.length > 2) {
      out = new PrintWriter(args[2]);
    } else {
      out = new PrintWriter(System.out);
    }

    StanfordCoreNLP pipeline = new StanfordCoreNLP(
            PropertiesUtils.asProperties("annotators", "tokenize,ssplit,pos,lemma,ner"));
    Annotation annotation = new Annotation(IOUtils.slurpFileNoExceptions(args[1]));
    pipeline.annotate(annotation);

    // Load lines of file as TokenSequencePatterns
    List<TokenSequencePattern> tokenSequencePatterns = new ArrayList<TokenSequencePattern>();
    for (String line : ObjectBank.getLineIterator(rules)) {
      TokenSequencePattern pattern = TokenSequencePattern.compile(line);
      tokenSequencePatterns.add(pattern);
    }

    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);

    int i = 0;
    for (CoreMap sentence : sentences) {
      List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      out.println("Sentence #" + ++i);
      out.print("  Tokens:");
      for (CoreLabel token : tokens) {
        out.print(' ');
        out.print(token.toShortString("Text", "PartOfSpeech", "NamedEntityTag"));
      }
      out.println();

      MultiPatternMatcher<CoreMap> multiMatcher = TokenSequencePattern.getMultiPatternMatcher(tokenSequencePatterns);
      List<SequenceMatchResult<CoreMap>> answers = multiMatcher.findNonOverlapping(tokens);
      int j = 0;
      for (SequenceMatchResult<CoreMap> matched : answers) {
        out.println("  Match #" + ++j);
        for (int k = 0; k <= matched.groupCount(); k++) {
          out.println("    group " + k + " = " + matched.group(k));
        }
      }

    }
    out.flush();
  }

}
