package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.util.*;

import static org.junit.Assert.assertEquals;

import java.util.*;

import org.junit.Test;

/**
 * Basic testing of MWT splitting
 */

public class MWTAnnotatorITest {

    @Test
    public void testFrenchMWTTokenizationBasic() {
        String frenchTextBasic = "Le but des bandes de roulement est d'augmenter la traction. " +
                "Elle est présidente du conseil d'administration.";
        // build a French pipeline, French default is to not preserve casing
        StanfordCoreNLP frenchPipeline = new StanfordCoreNLP("fr");
        // regular casing
        CoreDocument doc = new CoreDocument(frenchPipeline.process(frenchTextBasic));
        // first "des" should be split
        assertEquals("de", doc.tokens().get(2).word());
        assertEquals("les", doc.tokens().get(3).word());
        // "du"  should be split
        assertEquals("de", doc.tokens().get(16).word());
        assertEquals("le", doc.tokens().get(17).word());
        // capitalized
        String frenchTextCapitalized = "Le But Des Bandes De Roulement Est D'augmenter La Traction. " +
                "Elle Est Présidente Du Conseil D'Administration.";
        CoreDocument capitalizedDoc = new CoreDocument(frenchPipeline.process(frenchTextCapitalized));
        // "du"  should be split
        assertEquals("de", capitalizedDoc.tokens().get(15).word());
        assertEquals("le", capitalizedDoc.tokens().get(16).word());
        // all caps
        String frenchTextAllCaps = frenchTextBasic.toUpperCase();
        CoreDocument allCapsDoc = new CoreDocument(frenchPipeline.process(frenchTextAllCaps));
        assertEquals("de", allCapsDoc.tokens().get(15).word());
        assertEquals("le", allCapsDoc.tokens().get(16).word());
        // now test with preserve casing
        Properties props = StringUtils.argsToProperties("-props", "StanfordCoreNLP-french.properties");
        props.setProperty("annotators", "tokenize,ssplit,mwt");
        props.setProperty("mwt.preserveCasing", "true");
        StanfordCoreNLP frenchPipelinePreserveCasing = new StanfordCoreNLP(props);
        CoreDocument capitalizedDocPreserveCasing =
                new CoreDocument(frenchPipelinePreserveCasing.process(frenchTextCapitalized));
        // "du"  should be split
        assertEquals("De", capitalizedDocPreserveCasing.tokens().get(15).word());
        assertEquals("le", capitalizedDocPreserveCasing.tokens().get(16).word());
        // all caps
        CoreDocument allCapsDocPreserveCasing =
                new CoreDocument(frenchPipelinePreserveCasing.process(frenchTextAllCaps));
        assertEquals("DE", allCapsDocPreserveCasing.tokens().get(15).word());
        assertEquals("LE", allCapsDocPreserveCasing.tokens().get(16).word());
        System.err.println("Terminé!");
    }

}
