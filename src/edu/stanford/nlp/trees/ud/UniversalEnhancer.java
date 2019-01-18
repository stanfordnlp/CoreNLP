package edu.stanford.nlp.trees.ud;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.neural.Embedding;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

import java.util.Iterator;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Reads in a treebank in any language annotated according to UD v2
 * and adds enhancements according to a rule-based system.
 *
 * @author Sebastian Schuster
 */

public class UniversalEnhancer {

    public static void main(String args[]) {
        Properties props = StringUtils.argsToProperties(args);

        String conlluFileName = props.getProperty("conlluFile");

        String relativePronounsPatternStr = props.getProperty("relativePronouns");

        String embeddingsFilename = props.getProperty("embeddings");


        Pattern relativePronounPattern = Pattern.compile(relativePronounsPatternStr);

        Iterator<Pair<SemanticGraph, SemanticGraph>> sgIterator; // = null;


        CoNLLUDocumentReader reader = new CoNLLUDocumentReader();
        CoNLLUDocumentWriter writer = new CoNLLUDocumentWriter();
        try {
            sgIterator = reader.getIterator(IOUtils.readerFromString(conlluFileName));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


        Embedding embeddings = null;
        if (embeddingsFilename != null) {
            embeddings = new Embedding(embeddingsFilename);
        }

        while (sgIterator.hasNext()) {
            Pair<SemanticGraph, SemanticGraph> sgs = sgIterator.next();
            SemanticGraph basic = sgs.first();

            SemanticGraph enhanced = new SemanticGraph(basic.typedDependencies());
            if (embeddings != null) {
                UniversalGappingEnhancer.addEnhancements(enhanced, embeddings);
            }
            UniversalGrammaticalStructure.addRef(enhanced, relativePronounPattern);
            UniversalGrammaticalStructure.collapseReferent(enhanced);
            UniversalGrammaticalStructure.propagateConjuncts(enhanced);
            UniversalGrammaticalStructure.addExtraNSubj(enhanced);
            UniversalGrammaticalStructure.addCaseMarkerInformation(enhanced);
            UniversalGrammaticalStructure.addCaseMarkerForConjunctions(enhanced);
            UniversalGrammaticalStructure.addConjInformation(enhanced);
            System.out.print(writer.printSemanticGraph(basic, enhanced));

        }

    }

}
