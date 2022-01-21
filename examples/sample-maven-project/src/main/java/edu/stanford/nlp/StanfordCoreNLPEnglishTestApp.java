package edu.stanford.nlp;

import java.util.*;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.*;

/**
 * Application to test whether the new version of Stanford works properly
 */

public class StanfordCoreNLPEnglishTestApp {

    public static void main(String[] args) {
        Properties properties = new Properties();
        properties.setProperty("annotators", "tokenize,cleanxml,ssplit,pos,lemma,ner,parse,depparse,coref,natlog,openie,kbp,entitylink");
        
        StanfordCoreNLP pipeline = new StanfordCoreNLP(properties);
        CoreDocument document = pipeline.processToCoreDocument("President Barack Obama was born in Hawaii.  He was elected in 2008.");
        
        for (CoreLabel tok : document.tokens()) {
            System.out.println(tok.word() + ":" + tok.tag() + "\n");
        }
    }
}
