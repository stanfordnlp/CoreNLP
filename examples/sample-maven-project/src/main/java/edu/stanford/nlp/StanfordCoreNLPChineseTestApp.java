package edu.stanford.nlp;

import java.util.*;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.*;

/**
 * Application to test whether the new version of Stanford works properly
 */
public class StanfordCoreNLPChineseTestApp {

    public static void main(String[] args) {
        Properties properties = new Properties();
        properties.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,coref,kbp,entitylink");

        StanfordCoreNLP pipeline = new StanfordCoreNLP(properties);
        CoreDocument document = pipeline.processToCoreDocument("巴拉克·奥巴马是美国总统。他在2008年当选");

        for (CoreLabel tok : document.tokens()) {
            System.out.println(tok.word() + ":" + tok.tag() + "\n");
        }
    }
}
