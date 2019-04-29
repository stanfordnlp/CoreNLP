package edu.stanford.nlp;

import java.io.*;
import java.util.*;
import edu.stanford.nlp.io.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.util.*;

/** app for testing if Maven distribution is working properly */

public class StanfordCoreNLPSpanishTestApp
{
    public static void main(String[] args) throws IOException, ClassNotFoundException
    {
        String[] chineseArgs = new String[]{"-props","StanfordCoreNLP-spanish.properties",
                                              "-annotators","tokenize,ssplit,pos,lemma,ner,parse,depparse,kbp",
                                              "-file", "sample-spanish.txt", "-outputFormat", "text"};
        StanfordCoreNLP.main(chineseArgs);
    }
}
