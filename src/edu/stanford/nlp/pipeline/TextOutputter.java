package edu.stanford.nlp.pipeline;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations;
import edu.stanford.nlp.ie.machinereading.structure.EntityMention;
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations;
import edu.stanford.nlp.ie.machinereading.structure.RelationMention;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;

/**
 * @author John Bauer
 */
public class TextOutputter {

  private TextOutputter() {} // currently static. todo: fix this, make implement an interface

  public static void prettyPrint(Annotation annotation, OutputStream stream, StanfordCoreNLP pipeline) {
    try {
      PrintWriter os = new PrintWriter(IOUtils.encodedOutputStreamWriter(stream, pipeline.getEncoding()));
      prettyPrint(annotation, os, pipeline);
      // already flushed
      // don't close, might not want to close underlying stream
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  public static void prettyPrint(Annotation annotation, PrintWriter os, StanfordCoreNLP pipeline) {
    double beam = pipeline.getBeamPrintingOption();

    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);

    // Display docid if available
    String docId =  annotation.get(CoreAnnotations.DocIDAnnotation.class);
    if (docId != null) {
      List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
      int nSentences = (sentences != null)? sentences.size():0;
      int nTokens = (tokens != null)? tokens.size():0;
      os.printf("Document: ID=%s (%d sentences, %d tokens)%n", docId, nSentences, nTokens);
    }

    // Display doctitle if available
    String docTitle =  annotation.get(CoreAnnotations.DocTitleAnnotation.class);
    if (docTitle != null) {
      os.printf("Document Title: %s%n", docTitle);
    }

    // Display docdate if available
    String docDate =  annotation.get(CoreAnnotations.DocDateAnnotation.class);
    if (docDate != null) {
      os.printf("Document Date: %s%n", docDate);
    }

    // Display doctype if available
    String docType =  annotation.get(CoreAnnotations.DocTypeAnnotation.class);
    if (docType != null) {
      os.printf("Document Type: %s%n", docType);
    }

    // Display docsourcetype if available
    String docSourceType =  annotation.get(CoreAnnotations.DocSourceTypeAnnotation.class);
    if (docSourceType != null) {
      os.printf("Document Source Type: %s%n", docSourceType);
    }

    // display each sentence in this annotation
    if (sentences != null) {
      for(int i = 0, sz = sentences.size(); i < sz; i ++) {
        CoreMap sentence = sentences.get(i);
        List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
        os.printf("Sentence #%d (%d tokens):%n", (i + 1), tokens.size());

        String text = sentence.get(CoreAnnotations.TextAnnotation.class);
        os.println(text);

        // display the token-level annotations
        String[] tokenAnnotations = {
                "Text", "PartOfSpeech", "Lemma", "Answer", "NamedEntityTag", "CharacterOffsetBegin", "CharacterOffsetEnd", "NormalizedNamedEntityTag", "Timex", "TrueCase", "TrueCaseText" };
        for (CoreLabel token: tokens) {
          os.print(token.toShorterString(tokenAnnotations));
          os.print(' ');
        }
        os.println();

        // display the parse tree for this sentence
        Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
        if (tree != null){
          pipeline.getConstituentTreePrinter().printTree(tree, os);
          // It is possible turn off the semantic graphs, in which
          // case we don't want to recreate them using the dependency
          // printer.  This might be relevant if using corenlp for a
          // language which doesn't have dependencies, for example.
          if (sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class) != null) {
            os.print(sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class).toList());
            os.printf("%n");
          }
        }

        // display MachineReading entities and relations
        List<EntityMention> entities = sentence.get(MachineReadingAnnotations.EntityMentionsAnnotation.class);
        if (entities != null) {
          System.err.println("Extracted the following MachineReading entity mentions:");
          for (EntityMention e : entities) {
            System.err.println("\t" + e);
          }
        }
        List<RelationMention> relations = sentence.get(MachineReadingAnnotations.RelationMentionsAnnotation.class);
        if(relations != null){
          System.err.println("Extracted the following MachineReading relation mentions:");
          for(RelationMention r: relations){
            if(r.printableObject(beam)){
              System.err.println(r);
            }
          }
        }
      }
    }

    // display the old-style doc-level coref annotations
    // this is not supported anymore!
    //String corefAnno = annotation.get(CorefPLAnnotation.class);
    //if(corefAnno != null) os.println(corefAnno);

    // display the new-style coreference graph
    Map<Integer, CorefChain> corefChains =
      annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class);
    if (corefChains != null && sentences != null) {
      List<List<CoreLabel>> sents = new ArrayList<List<CoreLabel>>();
      for (CoreMap sentence : sentences) {
        List<CoreLabel> tokens =
          sentence.get(CoreAnnotations.TokensAnnotation.class);
        sents.add(tokens);
      }

      for (CorefChain chain : corefChains.values()) {
        CorefChain.CorefMention representative =
          chain.getRepresentativeMention();
        boolean outputHeading = false;
        for (CorefChain.CorefMention mention : chain.getMentionsInTextualOrder()) {
          if (mention == representative)
            continue;
          if (!outputHeading) {
            outputHeading = true;
            os.println("Coreference set:");
          }
          // all offsets start at 1!
          os.println("\t(" + mention.sentNum + "," +
              mention.headIndex + ",[" +
              mention.startIndex + "," +
              mention.endIndex + "]) -> (" +
              representative.sentNum + "," +
              representative.headIndex + ",[" +
              representative.startIndex + "," +
              representative.endIndex + "]), that is: \"" +
              mention.mentionSpan + "\" -> \"" +
              representative.mentionSpan + "\"");
        }
      }
    }

    os.flush();
  }

}
