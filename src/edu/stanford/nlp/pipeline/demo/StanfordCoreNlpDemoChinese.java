package edu.stanford.nlp.pipeline.demo;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;

/**
 * @author Christopher Manning
 */
public class StanfordCoreNlpDemoChinese {

  private StanfordCoreNlpDemoChinese() { }  // static main

  public static void main(String[] args) throws IOException {
    // set up optional output files
    PrintWriter out;
    if (args.length > 1) {
      out = new PrintWriter(args[1]);
    } else {
      out = new PrintWriter(System.out);
    }
    Properties props = new Properties();
    props.load(IOUtils.readerFromString("StanfordCoreNLP-chinese.properties"));
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    Annotation document;
    if (args.length > 0) {
      document = new Annotation(IOUtils.slurpFileNoExceptions(args[0]));
    } else {
      document = new Annotation("克林顿说，华盛顿将逐步落实对韩国的经济援助。金大中对克林顿的讲话报以掌声：克林顿总统在会谈中重申，他坚定地支持韩国摆脱经济危机。");
    }

    pipeline.annotate(document);
    List<CoreMap> sentences =  document.get(CoreAnnotations.SentencesAnnotation.class);

    int sentNo = 1;
    for (CoreMap sentence : sentences) {
      out.println("Sentence #" + sentNo + " tokens are:");
      for (CoreMap token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
        out.println(token.toShorterString("Text", "CharacterOffsetBegin", "CharacterOffsetEnd", "Index", "PartOfSpeech", "NamedEntityTag"));
      }

      out.println("Sentence #" + sentNo + " basic dependencies are:");
      out.println(sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class).toString(SemanticGraph.OutputFormat.LIST));
      sentNo++;
    }

    // Access coreference.
    out.println("Coreference information");
    Map<Integer, CorefChain> corefChains =
        document.get(CorefCoreAnnotations.CorefChainAnnotation.class);
    if (corefChains == null) { return; }
    for (Map.Entry<Integer,CorefChain> entry: corefChains.entrySet()) {
      out.println("Chain " + entry.getKey());
      for (CorefChain.CorefMention m : entry.getValue().getMentionsInTextualOrder()) {
        // We need to subtract one since the indices count from 1 but the Lists start from 0
        List<CoreLabel> tokens = sentences.get(m.sentNum - 1).get(CoreAnnotations.TokensAnnotation.class);
        // We subtract two for end: one for 0-based indexing, and one because we want last token of mention not one following.
        out.println("  " + m + ":[" + tokens.get(m.startIndex - 1).beginPosition() + ", " +
                tokens.get(m.endIndex - 2).endPosition() + ')');
      }
    }
    IOUtils.closeIgnoringExceptions(out);
  }

}
