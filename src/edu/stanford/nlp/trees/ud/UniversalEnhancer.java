package edu.stanford.nlp.trees.ud;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

import java.util.Iterator;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Reads in a treebank in any language annotated according to UD v2
 * and
 *
 * @author Sebastian Schuster
 */

public class UniversalEnhancer {

    public static void main(String args[]) {
        Properties props = StringUtils.argsToProperties(args);

        String conlluFileName = props.getProperty("conlluFile");

        String relativePronounsPatternStr = props.getProperty("relativePronouns");


        Pattern relativePronounPattern = Pattern.compile(relativePronounsPatternStr);

        Iterator<Pair<SemanticGraph, SemanticGraph>> sgIterator; // = null;


        CoNLLUDocumentReader reader = new CoNLLUDocumentReader();
        CoNLLUDocumentWriter writer = new CoNLLUDocumentWriter();
        try {
            sgIterator = reader.getIterator(IOUtils.readerFromString(conlluFileName));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


        while (sgIterator.hasNext()) {
            Pair<SemanticGraph, SemanticGraph> sgs = sgIterator.next();
            SemanticGraph basic = sgs.first();

            SemanticGraph enhanced = new SemanticGraph(basic);

            UniversalGrammaticalStructure.addRef(enhanced, relativePronounPattern);
            UniversalGrammaticalStructure.collapseReferent(enhanced);
            UniversalGrammaticalStructure.propagateConjuncts(enhanced);
            UniversalGrammaticalStructure.addExtraNSubj(enhanced);

            System.out.print(writer.printSemanticGraph(basic, enhanced));

        }

    }

}
