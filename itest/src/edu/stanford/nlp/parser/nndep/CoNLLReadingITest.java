package edu.stanford.nlp.parser.nndep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.AnnotationComparator;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.TestPaths;

/**
 * Test that dependency parsing code reads in CoNLL lines correctly.
 */

public class CoNLLReadingITest {

    String exampleCoNLLXPath = String.format("%s/stanford-corenlp/testing/data/conllu/fr_gsd-ud-train.conllu.clean", TestPaths.testHome());
    String exampleCoNLLUPath = "";

    public static void loadConllFileOriginal(String inFile, List<CoreMap> sents, List<DependencyTree> trees, boolean unlabeled, boolean cPOS)
    {
        CoreLabelTokenFactory tf = new CoreLabelTokenFactory(false);

        try (BufferedReader reader = IOUtils.readerFromString(inFile)) {

            List<CoreLabel> sentenceTokens = new ArrayList<>();
            DependencyTree tree = new DependencyTree();

            for (String line : IOUtils.getLineIterable(reader, false)) {
                String[] splits = line.split("\t");
                if (splits.length < 10) {
                    if (sentenceTokens.size() > 0) {
                        trees.add(tree);
                        CoreMap sentence = new CoreLabel();
                        sentence.set(CoreAnnotations.TokensAnnotation.class, sentenceTokens);
                        sents.add(sentence);
                        tree = new DependencyTree();
                        sentenceTokens = new ArrayList<>();
                    }
                } else {
                    String word = splits[1],
                            pos = cPOS ? splits[3] : splits[4],
                            depType = splits[7];

                    int head = -1;
                    try {
                        head = Integer.parseInt(splits[6]);
                    } catch (NumberFormatException e) {
                        continue;
                    }

                    CoreLabel token = tf.makeToken(word, 0, 0);
                    token.setTag(pos);
                    token.set(CoreAnnotations.CoNLLDepParentIndexAnnotation.class, head);
                    token.set(CoreAnnotations.CoNLLDepTypeAnnotation.class, depType);
                    sentenceTokens.add(token);

                    if (!unlabeled)
                        tree.add(head, depType);
                    else
                        tree.add(head, Config.UNKNOWN);
                }
            }
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    public List<String> originalConLLLines(String filePath) {
        List<String> originalLines = new ArrayList<String>();
        try (BufferedReader reader = IOUtils.readerFromString(filePath)) {
            for (String line : IOUtils.getLineIterable(reader, false)) {
                originalLines.add(line);
            }
        } catch (IOException e) {

        }
        return originalLines;
    }

    @Test
    public void testReadingCoNLLXFile() {
        // load CoNLL-X file with original loading
        List<CoreMap> originalSentences = new ArrayList<CoreMap>();
        List<DependencyTree> originalTrees = new ArrayList<DependencyTree>();
        loadConllFileOriginal(exampleCoNLLXPath, originalSentences, originalTrees, false, true);
        // load CoNLL-X file with new loading
        List<CoreMap> currentSentences = new ArrayList<CoreMap>();
        List<DependencyTree> currentTrees = new ArrayList<DependencyTree>();
        Util.loadConllFile(exampleCoNLLXPath, currentSentences, currentTrees, false, true);
        AnnotationComparator.compareTokensLists(originalSentences.get(0), currentSentences.get(0));
        assertEquals(originalSentences, currentSentences);
        for (int i = 0 ; i < currentTrees.size() ; i++) {
            assertTrue(currentTrees.get(i).equal(originalTrees.get(i)));
        }
        System.err.println("Done.");
    }

}
