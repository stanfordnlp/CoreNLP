package edu.stanford.nlp;

import java.util.*;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.*;

/**
 * Application to test whether the new version of Stanford works properly
 */
public class StanfordCoreNLPSpanishTestApp {

    public static void main(String[] args) {
        Properties properties = new Properties();
        properties.setProperty("annotators", "StanfordCoreNLP-spanish.properties");
        properties.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,depparse,kbp");

        StanfordCoreNLP pipeline = new StanfordCoreNLP(properties);
        CoreDocument document = pipeline.processToCoreDocument("El presidente Barack Obama fue elegido en 2008. Él hizo campaña en muchos estados incluyen California.");

        for (CoreLabel tok : document.tokens()) {
            System.out.println(tok.word() + ":" + tok.tag() + "\n");
        }
    }
}
