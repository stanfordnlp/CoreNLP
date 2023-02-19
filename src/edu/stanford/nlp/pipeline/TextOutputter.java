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
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.naturalli.OpenIE;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;

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
   * The meat of the outputter.
   */
  private static void print(Annotation annotation, PrintWriter pw, Options options) {
    double beam = options.relationsBeam;

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
        pw.println();
        CoreMap sentence = sentences.get(i);
        List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
        String sentiment = sentence.get(SentimentCoreAnnotations.SentimentClass.class);
        String piece;
        if (sentiment == null) {
          piece = "";
        } else {
          piece = ", sentiment: " + sentiment;
        }
        pw.printf("Sentence #%d (%d tokens%s):%n", (i + 1), tokens.size(), piece);

        String text = sentence.get(CoreAnnotations.TextAnnotation.class);
        pw.println(text);

        // display the token-level annotations
        String[] tokenAnnotations = {
                "Text", "PartOfSpeech", "Lemma", "Answer", "NamedEntityTag",
                "CharacterOffsetBegin", "CharacterOffsetEnd", "NormalizedNamedEntityTag",
                "CodepointOffsetBegin", "CodepointOffsetEnd",
                "Timex", "TrueCase", "TrueCaseText", "SentimentClass", "WikipediaEntity" };

        pw.println();
        pw.println("Tokens:");
        for (CoreLabel token: tokens) {
          pw.print(token.toShorterString(tokenAnnotations));
          pw.println();
        }

        // display the parse tree for this sentence
        Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
        if (tree != null) {
          pw.println();
          pw.println("Constituency parse: ");
          options.constituencyTreePrinter.printTree(tree, pw);
        }

        // display the binary tree for this sentence
        Tree binaryTree = sentence.get(TreeCoreAnnotations.BinarizedTreeAnnotation.class);
        if (binaryTree != null) {
          pw.println();
          pw.println("Binary Constituency parse: ");
          options.constituencyTreePrinter.printTree(binaryTree, pw);
        }

        // display sentiment tree if they asked for sentiment
        if ( ! StringUtils.isNullOrEmpty(sentiment)) {
          pw.println();
          pw.println("Sentiment-annotated binary tree:");
          Tree sTree = sentence.get(SentimentCoreAnnotations.SentimentAnnotatedTree.class);
          if (sTree != null) {
            sTree.pennPrint(pw,
                label -> (label.value() == null) ? "" :
                    (RNNCoreAnnotations.getPredictedClass(label) != -1) ?
                        (label.value() + "|sentiment=" + RNNCoreAnnotations.getPredictedClass(label) + "|prob=" +
                            (String.format("%.3f", RNNCoreAnnotations.getPredictedClassProb(label)))) : label.value());
            pw.println();
          }
        }

        // It is possible to turn off the semantic graphs, in which
        // case we don't want to recreate them using the dependency
        // printer.  This might be relevant if using CoreNLP for a
        // language which doesn't have dependencies, for example.
        final SemanticGraph graph;
        final String graphName;
        switch (options.semanticGraphMode) {
        case BASIC:
          graph = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
          graphName = "basic dependencies";
          break;
        case ENHANCED:
          graph = sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
          graphName = "enhanced dependencies";
          break;
        case ENHANCED_PLUS_PLUS:
          graph = sentence.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);
          graphName = "enhanced plus plus dependencies";
          break;
        case COLLAPSED:
          graph = sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);
          graphName = "collapsed dependencies";
          break;
        case CCPROCESSED:
          graph = sentence.get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);
          graphName = "cc processed dependencies";
          break;
        default:
          throw new RuntimeException("Sorry, but " + options.semanticGraphMode + " dependencies cannot be output as part of the TextOutputter");
        }
        if (graph != null) {
          pw.println();
          pw.println("Dependency Parse (" + graphName + "):");
          pw.print(graph.toList());
        }

        // display the entity mentions
        List<CoreMap> entityMentions = sentence.get(CoreAnnotations.MentionsAnnotation.class);
        if (entityMentions != null) {
          pw.println();
          pw.println("Extracted the following NER entity mentions:");
          for (CoreMap entityMention : entityMentions) {
            String nerConfidenceEntry;
            Map<String,Double> nerConfidences = entityMention.get(CoreAnnotations.NamedEntityTagProbsAnnotation.class);
            String nerConfidenceKey =
                    nerConfidences.keySet().size() > 0 ? (String) nerConfidences.keySet().toArray()[0] : "" ;
            if (!nerConfidenceKey.equals("") && !nerConfidenceKey.equals("O"))
              nerConfidenceEntry = nerConfidenceKey + ":" + nerConfidences.get(nerConfidenceKey);
            else
              nerConfidenceEntry = "-";
            if (entityMention.get(CoreAnnotations.EntityTypeAnnotation.class) != null) {
              pw.println(entityMention.get(CoreAnnotations.TextAnnotation.class) + '\t'
                  + entityMention.get(CoreAnnotations.EntityTypeAnnotation.class) + '\t'
                      + nerConfidenceEntry);
            }
          }
        }

        // display MachineReading entities and relations
        List<EntityMention> entities = sentence.get(MachineReadingAnnotations.EntityMentionsAnnotation.class);
        if (entities != null) {
          pw.println();
          pw.println("Extracted the following MachineReading entity mentions:");
          for (EntityMention e : entities) {
            pw.print('\t');
            pw.println(e);
          }
        }
        List<RelationMention> relations = sentence.get(MachineReadingAnnotations.RelationMentionsAnnotation.class);
        if (relations != null){
          pw.println();
          pw.println("Extracted the following MachineReading relation mentions:");
          for (RelationMention r: relations) {
            if (r.printableObject(beam)) {
              pw.println(r);
            }
          }
        }

        // display OpenIE triples
        Collection<RelationTriple> openieTriples = sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
        if (openieTriples != null && ! openieTriples.isEmpty()) {
          pw.println();
          pw.println("Extracted the following Open IE triples:");
          for (RelationTriple triple : openieTriples) {
            pw.println(OpenIE.tripleToString(triple, docId, sentence));
          }
        }

        // display KBP triples
        Collection<RelationTriple> kbpTriples = sentence.get(CoreAnnotations.KBPTriplesAnnotation.class);
        if (kbpTriples != null && ! kbpTriples.isEmpty()) {
          pw.println();
          pw.println("Extracted the following KBP triples:");
          for (RelationTriple triple : kbpTriples) {
            pw.println(triple);
          }
        }
      }
    } else {
      List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
      pw.println("Tokens:");
      pw.println(annotation.get(CoreAnnotations.TextAnnotation.class));
      for (CoreLabel token : tokens) {
        int tokenCharBegin = token.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
        int tokenCharEnd = token.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
        String extra = "";
        Integer codepoint = token.get(CoreAnnotations.CodepointOffsetBeginAnnotation.class);
        if (codepoint != null) {
          extra = extra + " CodepointOffsetBegin=" + codepoint;
        }
        codepoint = token.get(CoreAnnotations.CodepointOffsetEndAnnotation.class);
        if (codepoint != null) {
          extra = extra + " CodepointOffsetEnd=" + codepoint;
        }
        pw.println("[Text="+token.word()+" CharacterOffsetBegin="+tokenCharBegin+" CharacterOffsetEnd="+tokenCharEnd+extra+']');
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
          if (mention == representative &&
              (!options.printSingletons || chain.getMentionsInTextualOrder().size() > 1))
            continue;
          if (!outputHeading) {
            outputHeading = true;
            pw.println();
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
      outputQuotes(annotation, pw);
    }

    pw.flush();
  }

  /**
   * Prints the quote section from an annotation.
   *<br>
   * Factored out so it can be used elsewhere
   */
  public static void outputQuotes(Annotation annotation, PrintWriter pw) {
    List<CoreMap> allQuotes = QuoteAnnotator.gatherQuotes(annotation);
    if (allQuotes == null || allQuotes.size() == 0) {
      return;
    }
    pw.println();
    pw.println("Extracted quotes:");
    for (CoreMap quote : allQuotes) {
      String speakerString;
      if (quote.get(QuoteAttributionAnnotator.CanonicalMentionAnnotation.class) != null) {
        speakerString = quote.get(QuoteAttributionAnnotator.CanonicalMentionAnnotation.class);
      } else if (quote.get(QuoteAttributionAnnotator.SpeakerAnnotation.class) != null) {
        speakerString = quote.get(QuoteAttributionAnnotator.SpeakerAnnotation.class);
      } else {
        speakerString = "Unknown";
      }
      pw.printf("%s:\t%s\t[index=%d, charOffsetBegin=%d]%n",
                speakerString,
                quote.get(CoreAnnotations.TextAnnotation.class),
                quote.get(CoreAnnotations.QuotationIndexAnnotation.class),
                quote.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class));
    }
    pw.flush();
  }

  /** Static helper */
  public static void prettyPrint(Annotation annotation, OutputStream stream, StanfordCoreNLP pipeline) {
    prettyPrint(annotation, new PrintWriter(stream), pipeline);
  }

  /** Static helper */
  public static void prettyPrint(Annotation annotation, PrintWriter pw, StanfordCoreNLP pipeline) {
    TextOutputter.print(annotation, pw, getOptions(pipeline.getProperties()));
    // already flushed
    // don't close, might not want to close underlying stream
  }

}
