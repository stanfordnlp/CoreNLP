package edu.stanford.nlp.pipeline;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.coref.CorefCoreAnnotations;

import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.ie.machinereading.structure.EntityMention;
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations;
import edu.stanford.nlp.ie.machinereading.structure.RelationMention;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.naturalli.OpenIE;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;

/**
 * @author John Bauer
 */
public class TextOutputter extends AnnotationOutputter {

  public TextOutputter() {}

  /** {@inheritDoc} */
  @Override
  public void print(Annotation annotation, OutputStream stream, Options options) throws IOException {
    PrintWriter os = new PrintWriter(IOUtils.encodedOutputStreamWriter(stream, options.encoding));
    print(annotation, os, options);
  }

  /**
   * The meat of the outputter
   */
  private static void print(Annotation annotation, PrintWriter pw, Options options) throws IOException {
    double beam = options.beamPrintingOption;

    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);

    // Display docid if available
    String docId =  annotation.get(CoreAnnotations.DocIDAnnotation.class);
    if (docId != null) {
      List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
      int nSentences = (sentences != null)? sentences.size():0;
      int nTokens = (tokens != null)? tokens.size():0;
      pw.printf("Document: ID=%s (%d sentences, %d tokens)%n", docId, nSentences, nTokens);
    }

    // Display doctitle if available
    String docTitle =  annotation.get(CoreAnnotations.DocTitleAnnotation.class);
    if (docTitle != null) {
      pw.printf("Document Title: %s%n", docTitle);
    }

    // Display docdate if available
    String docDate =  annotation.get(CoreAnnotations.DocDateAnnotation.class);
    if (docDate != null) {
      pw.printf("Document Date: %s%n", docDate);
    }

    // Display doctype if available
    String docType =  annotation.get(CoreAnnotations.DocTypeAnnotation.class);
    if (docType != null) {
      pw.printf("Document Type: %s%n", docType);
    }

    // Display docsourcetype if available
    String docSourceType =  annotation.get(CoreAnnotations.DocSourceTypeAnnotation.class);
    if (docSourceType != null) {
      pw.printf("Document Source Type: %s%n", docSourceType);
    }

    // display each sentence in this annotation
    if (sentences != null) {
      for (int i = 0, sz = sentences.size(); i < sz; i ++) {
        CoreMap sentence = sentences.get(i);
        List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
        String sentiment = sentence.get(SentimentCoreAnnotations.SentimentClass.class);
        if (sentiment == null) {
          sentiment = "";
        } else {
          sentiment = ", sentiment: " + sentiment;
        }
        pw.printf("Sentence #%d (%d tokens%s):%n", (i + 1), tokens.size(), sentiment);

        String text = sentence.get(CoreAnnotations.TextAnnotation.class);
        pw.println(text);

        // display the token-level annotations
        String[] tokenAnnotations = {
                "Text", "PartOfSpeech", "Lemma", "Answer", "NamedEntityTag",
                "CharacterOffsetBegin", "CharacterOffsetEnd", "NormalizedNamedEntityTag",
                "Timex", "TrueCase", "TrueCaseText", "SentimentClass", "WikipediaEntity" };
        for (CoreLabel token: tokens) {
          pw.print(token.toShorterString(tokenAnnotations));
          pw.println();
        }

        // display the parse tree for this sentence
        Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
        if (tree != null) {
          options.constituentTreePrinter.printTree(tree, pw);
        }

        // It is possible to turn off the semantic graphs, in which
        // case we don't want to recreate them using the dependency
        // printer.  This might be relevant if using CoreNLP for a
        // language which doesn't have dependencies, for example.
        if (sentence.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class) != null) {
          pw.print(sentence.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class).toList());
          pw.println();
        }

        // display MachineReading entities and relations
        List<EntityMention> entities = sentence.get(MachineReadingAnnotations.EntityMentionsAnnotation.class);
        if (entities != null) {
          pw.println("Extracted the following MachineReading entity mentions:");
          for (EntityMention e : entities) {
            pw.print('\t');
            pw.println(e);
          }
        }
        List<RelationMention> relations = sentence.get(MachineReadingAnnotations.RelationMentionsAnnotation.class);
        if (relations != null){
          pw.println("Extracted the following MachineReading relation mentions:");
          for (RelationMention r: relations) {
            if (r.printableObject(beam)) {
              pw.println(r);
            }
          }
        }

        // display OpenIE triples
        Collection<RelationTriple> openieTriples = sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
        if (openieTriples != null && openieTriples.size() > 0) {
          pw.println("Extracted the following Open IE triples:");
          for (RelationTriple triple : openieTriples) {
            pw.println(OpenIE.tripleToString(triple, docId, sentence));
          }
        }

        // display KBP triples
        Collection<RelationTriple> kbpTriples = sentence.get(CoreAnnotations.KBPTriplesAnnotation.class);
        if (kbpTriples != null && kbpTriples.size() > 0) {
          pw.println("Extracted the following KBP triples:");
          for (RelationTriple triple : kbpTriples) {
            pw.println(triple.toString());
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
      for (CorefChain chain : corefChains.values()) {
        CorefChain.CorefMention representative =
            chain.getRepresentativeMention();
        boolean outputHeading = false;
        for (CorefChain.CorefMention mention : chain.getMentionsInTextualOrder()) {
          if (mention == representative)
            continue;
          if (!outputHeading) {
            outputHeading = true;
            pw.println("Coreference set:");
          }
          // all offsets start at 1!
          pw.printf("\t(%d,%d,[%d,%d]) -> (%d,%d,[%d,%d]), that is: \"%s\" -> \"%s\"%n",
                  mention.sentNum,
                  mention.headIndex,
                  mention.startIndex,
                  mention.endIndex,
                  representative.sentNum,
                  representative.headIndex,
                  representative.startIndex,
                  representative.endIndex,
                  mention.mentionSpan,
                  representative.mentionSpan);
        }
      }
    }

    // display quotes if available
    if (annotation.get(CoreAnnotations.QuotationsAnnotation.class) != null) {
      pw.println("Extracted quotes: ");
      List<CoreMap> allQuotes = QuoteAnnotator.gatherQuotes(annotation);
      for (CoreMap quote : allQuotes) {
        pw.printf("[QuotationIndexAnnotation=%d, CharacterOffsetBegin=%d, Text=%s]%n",
            quote.get(CoreAnnotations.QuotationIndexAnnotation.class),
            quote.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class),
            quote.get(CoreAnnotations.TextAnnotation.class));
      }
    }

    pw.flush();
  }

  /** Static helper */
  public static void prettyPrint(Annotation annotation, OutputStream stream, StanfordCoreNLP pipeline) {
    prettyPrint(annotation, new PrintWriter(stream), pipeline);
  }

  /** Static helper */
  public static void prettyPrint(Annotation annotation, PrintWriter pw, StanfordCoreNLP pipeline) {
    try {
      TextOutputter.print(annotation, pw, getOptions(pipeline));
      // already flushed
      // don't close, might not want to close underlying stream
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

}
