package edu.stanford.nlp.hcoref;

import edu.stanford.nlp.hcoref.CorefCoreAnnotations;
import edu.stanford.nlp.hcoref.data.CorefChain;
import edu.stanford.nlp.hcoref.data.Mention;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;

import java.util.Properties;

/**
 * A simple example of Stanford Chinese coreference resolution
 * 
 * When I use originAPI code, using the properties file in path edu/stanford/nlp/hcoref/properties/zh-dcoref-default.properties
 * the code could not run correctly in Chinese. 
 * 
 * What I did is extracting the right properties file from stanford-chinese-corenlp-2015-12-08-models.jar
 * and replace edu/stanford/nlp/hcoref/properties/zh-coref-default.properties to our originAPI code 
 * which finally run correctly.
 * 
 * @originAPI http://stanfordnlp.github.io/CoreNLP/coref.html 
 * @modify_author zkli
 */
public class HcorefChineseAPIExampleTest {
	public static void main(String[] args) throws Exception {
		long startTime=System.currentTimeMillis();
		
		String text = "小明吃了个冰棍，他很开心。";
		args = new String[] {"-props", "edu/stanford/nlp/hcoref/properties/zh-coref-default.properties" };

		Annotation document = new Annotation(text);
		Properties props = StringUtils.argsToProperties(args);
		StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
		pipeline.annotate(document);
		System.out.println("---");
		System.out.println("coref chains");
		
		for (CorefChain cc : document.get(CorefCoreAnnotations.CorefChainAnnotation.class).values()) {
			System.out.println("\t" + cc);
		}
		for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
			System.out.println("---");
			System.out.println("mentions");
			for (Mention m : sentence.get(CorefCoreAnnotations.CorefMentionsAnnotation.class)) {
				System.out.println("\t" + m);
			}
		}
		
		long endTime=System.currentTimeMillis(); 
		long time = (endTime-startTime)/1000;
		System.out.println("Running time "+time/60+"min "+time%60+"s");
	}
}
