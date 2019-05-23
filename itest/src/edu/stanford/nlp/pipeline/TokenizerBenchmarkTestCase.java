package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.stats.ClassicCounter;

import java.util.*;
import java.util.stream.*;

import junit.framework.TestCase;

/**
 * Utilities for benchmarking tokenizers
 **/

public class TokenizerBenchmarkTestCase extends TestCase {

    // path to eval CoNLL-U
    public String goldFilePath;
    // list of examples
    public List<TestExample> testExamples;
    // pipeline to use for test
    public StanfordCoreNLP pipeline;

    /** nested class for holding test example info such as text and gold tokens **/
    class TestExample {

        private String sentenceID;
        private String sentenceText;
        private List<CoreLabel> goldTokensList;
        private List<CoreLabel> systemTokensList;

        // CoNLL-U files have 3 lines of meta-data before tokens, tokens start at index 3
        public int CONLL_U_TOKEN_START = 3;

        public TestExample(List<String> conllLines) {
            int LENGTH_OF_SENTENCE_ID_PREFIX = "# sent_id = ".length();
            sentenceID = conllLines.get(0).substring(LENGTH_OF_SENTENCE_ID_PREFIX);
            int LENGTH_OF_TEXT_PREFIX = "# text = ".length();
            sentenceText = conllLines.get(1).substring(LENGTH_OF_TEXT_PREFIX);
            goldTokensList = new ArrayList<CoreLabel>();
            int charBegin = 0;
            for (String conllLine : conllLines.subList(CONLL_U_TOKEN_START, conllLines.size())) {
                if (conllLine.split("\t")[0].contains("-")) {
                    continue;
                }
                String tokenText = conllLine.split("\t")[1];
                goldTokensList.add(buildCoreLabel(tokenText, charBegin, charBegin+tokenText.length()));
                charBegin += tokenText.length();
            }
            tokenizeSentenceText();
        }

        /** helper method to build a CoreLabel from String and offsets **/
        public CoreLabel buildCoreLabel(String word, int begin, int end) {
            CoreLabel token = new CoreLabel();
            token.setWord(word);
            token.setBeginPosition(begin);
            token.setEndPosition(end);
            return token;
        }

        /** getter for the sentence id **/
        public String sentenceID() {
            return sentenceID;
        }

        /** getter for the sentence text **/
        public String sentenceText() {
            return sentenceText;
        }

        /** getter for the list of gold tokens **/
        public List<CoreLabel> goldTokensList() {
            return goldTokensList;
        }

        /** return the merged string of all the gold tokens **/
        public String goldTokensString() {
            return String.join("",
                    goldTokensList.stream().map(tok -> tok.word()).collect(Collectors.joining()));
        }

        /** return the merged string of all the system token **/
        public String systemTokensString() {
            return String.join("",
                    systemTokensList.stream().map(tok -> tok.word()).collect(Collectors.joining()));
        }

        /** tokenize text with pipeline, populate systemTokensList **/
        public void tokenizeSentenceText() {
            systemTokensList = new ArrayList<CoreLabel>();
            CoreDocument exampleTokensDoc = new CoreDocument(pipeline.process(sentenceText));
            // iterate through tokens, build CoreLabel objects with char offsets based on token characters only
            // e.g. "ab c" character offsets would refer to the string "abc"
            int charBegin = 0;
            for (CoreLabel tok : exampleTokensDoc.tokens()) {
                systemTokensList.add(buildCoreLabel(tok.word(), charBegin, charBegin + tok.word().length()));
                charBegin += tok.word().length();
            }
        }

        /** return TP, FP, FN stats for this example **/
        public ClassicCounter<String> f1Stats() {
            ClassicCounter<String> f1Stats = new ClassicCounter<>();
            int currStart = 0;
            // match system tokens to gold tokens
            for (CoreLabel cl : systemTokensList) {
                for (int goldIdx = currStart; goldIdx < goldTokensList.size(); goldIdx++, currStart++) {
                    CoreLabel currGoldToken = goldTokensList.get(goldIdx);
                    if (cl.beginPosition() < currGoldToken.beginPosition()) {
                        // pass
                    } else if (cl.beginPosition() == currGoldToken.beginPosition()) {
                        if (cl.endPosition() == currGoldToken.endPosition()) {
                            // score a true positive
                            f1Stats.incrementCount("TP");
                            break;
                        } else {
                            // score a false positive
                            f1Stats.incrementCount("FP");
                            break;
                        }
                    } else {
                        // score a false positive
                        f1Stats.incrementCount("FP");
                        break;
                    }
                }
            }
            f1Stats.setCount("FN", goldTokensList.size() - f1Stats.getCount("TP"));
            return f1Stats;
        }
    }

    /** load all tokenizer test examples **/
    public void loadTokenizerTestExamples() {
        List<String> allLines = IOUtils.linesFromFile(goldFilePath);
        testExamples = new ArrayList<TokenizerBenchmarkTestCase.TestExample>();
        List<String> currSentence = new ArrayList<String>();
        for (String conllLine : allLines) {
            if (conllLine.trim().equals("")) {
                testExamples.add(new TokenizerBenchmarkTestCase.TestExample(currSentence));
                currSentence.clear();
            } else {
                currSentence.add(conllLine);
            }
        }
    }

    /** calculate F1 scores from stats **/
    public static ClassicCounter<String> f1Scores(ClassicCounter<String> f1Stats) {
        ClassicCounter<String> f1Scores = new ClassicCounter<>();
        f1Scores.setCount("precision",
                f1Stats.getCount("TP")/(f1Stats.getCount("TP") + f1Stats.getCount("FP")));
        f1Scores.setCount("recall",
                f1Stats.getCount("TP")/(f1Stats.getCount("TP") + f1Stats.getCount("FN")));
        f1Scores.setCount("f1",
                (2 * f1Scores.getCount("precision") * f1Scores.getCount("recall"))/
                        (f1Scores.getCount("precision") + f1Scores.getCount("recall")));
        return f1Scores;
    }

    /** run the test and display report **/
    public void runTest(String evalSet, String lang, double expectedF1) {
        loadTokenizerTestExamples();
        ClassicCounter<String> allF1Stats = new ClassicCounter<String>();
        for (TokenizerBenchmarkTestCase.TestExample testExample : testExamples)
            allF1Stats.addAll(testExample.f1Stats());
        ClassicCounter<String> f1Scores = f1Scores(allF1Stats);
        System.err.println("---");
        System.err.println("Tokenizer Benchmark");
        System.err.println("language: "+lang);
        System.err.println("eval set: "+evalSet);
        assertTrue("Test failure: System F1 of " + f1Scores.getCount("f1") + " below expected value of " +
                expectedF1,f1Scores.getCount("f1") >= expectedF1);
    }

}
