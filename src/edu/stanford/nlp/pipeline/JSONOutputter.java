package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.StringOutputStream;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.time.Timex;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.trees.TreePrint;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Pointer;
import edu.stanford.nlp.util.StringUtils;

import java.io.*;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * Output an Annotation to human readable JSON.
 * This is not a lossless operation; for more strict serialization,
 * see {@link edu.stanford.nlp.pipeline.AnnotationSerializer}; e.g.,
 * {@link edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer}.
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("unused")
public class JSONOutputter extends AnnotationOutputter {

  protected static final String INDENT_CHAR = "  ";


  /** {@inheritDoc} */
  @SuppressWarnings({"RedundantCast", "RedundantSuppression"})
  // It's lying; we need the "redundant" casts (as of 2014-09-08)
  @Override
  public void print(Annotation doc, OutputStream target, Options options) throws IOException {
    PrintWriter writer = new PrintWriter(IOUtils.encodedOutputStreamWriter(target, options.encoding));
    JSONWriter l0 = new JSONWriter(writer, options);

    l0.object(l1 -> {

      // Add annotations attached to a Document
      l1.set("docId", doc.get(CoreAnnotations.DocIDAnnotation.class));
      l1.set("docDate", doc.get(CoreAnnotations.DocDateAnnotation.class));
      l1.set("docSourceType", doc.get(CoreAnnotations.DocSourceTypeAnnotation.class));
      l1.set("docType", doc.get(CoreAnnotations.DocTypeAnnotation.class));
      l1.set("author", doc.get(CoreAnnotations.AuthorAnnotation.class));
      l1.set("location", doc.get(CoreAnnotations.LocationAnnotation.class));
      if (options.includeText) {
        l1.set("text", doc.get(CoreAnnotations.TextAnnotation.class));
      }

      // Add sentences
      if (doc.get(CoreAnnotations.SentencesAnnotation.class) != null) {
        l1.set("sentences", doc.get(CoreAnnotations.SentencesAnnotation.class).stream().map(sentence -> (Consumer<Writer>) (Writer l2) -> {
          // Add a single sentence
          // (metadata)
          l2.set("id", sentence.get(CoreAnnotations.SentenceIDAnnotation.class));
          l2.set("index", sentence.get(CoreAnnotations.SentenceIndexAnnotation.class));
          l2.set("line", sentence.get(CoreAnnotations.LineNumberAnnotation.class));
          l2.set("paragraph", sentence.get(CoreAnnotations.ParagraphIndexAnnotation.class));
          l2.set("speaker", sentence.get(CoreAnnotations.SpeakerAnnotation.class));
          l2.set("speakerType", sentence.get(CoreAnnotations.SpeakerTypeAnnotation.class));
          // (constituency tree)
          StringWriter treeStrWriter = new StringWriter();
          TreePrint treePrinter = options.constituencyTreePrinter;
          if (treePrinter == AnnotationOutputter.DEFAULT_CONSTITUENCY_TREE_PRINTER) {
            // note the '==' -- we're overwriting the default, but only if it was not explicitly set otherwise
            treePrinter = new TreePrint("oneline");
          }
          treePrinter.printTree(sentence.get(TreeCoreAnnotations.TreeAnnotation.class), new PrintWriter(treeStrWriter, true));
          String treeStr = treeStrWriter.toString().trim();  // strip the trailing newline
          if (!"SENTENCE_SKIPPED_OR_UNPARSABLE".equals(treeStr)) {
            l2.set("parse", treeStr);
          }
          // binary tree (if present)
          if (sentence.get(TreeCoreAnnotations.BinarizedTreeAnnotation.class) != null) {
            StringWriter binaryTreeStrWriter = new StringWriter();
            TreePrint binaryTreePrinter = options.constituencyTreePrinter;
            if (binaryTreePrinter == AnnotationOutputter.DEFAULT_CONSTITUENCY_TREE_PRINTER) {
              binaryTreePrinter = new TreePrint("oneline");
            }
            binaryTreePrinter.printTree(sentence.get(TreeCoreAnnotations.BinarizedTreeAnnotation.class),
                    new PrintWriter(binaryTreeStrWriter, true));
            String binaryTreeStr = binaryTreeStrWriter.toString().trim();
            if (!"SENTENCE_SKIPPED_OR_UNPARSABLE".equals(binaryTreeStr)) {
              l2.set("binaryParse", binaryTreeStr);
            }
          }
          // (dependency trees)
          l2.set("basicDependencies", buildDependencyTree(sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class)));
          l2.set("enhancedDependencies", buildDependencyTree(sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class)));
          l2.set("enhancedPlusPlusDependencies", buildDependencyTree(sentence.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class)));
          // (sentiment)
          Tree sentimentTree = sentence.get(SentimentCoreAnnotations.SentimentAnnotatedTree.class);
          if (sentimentTree != null) {
            int sentiment = RNNCoreAnnotations.getPredictedClass(sentimentTree);
            List<Double> sentimentPredictions =
                RNNCoreAnnotations.getPredictionsAsStringList(sentimentTree);
            String sentimentClass = sentence.get(SentimentCoreAnnotations.SentimentClass.class);
            l2.set("sentimentValue", Integer.toString(sentiment));
            l2.set("sentiment", sentimentClass.replaceAll(" ", ""));
            l2.set("sentimentDistribution", sentimentPredictions);
            StringWriter sentimentTreeStringWriter = new StringWriter();
            sentimentTree.pennPrint(new PrintWriter(sentimentTreeStringWriter),
                label -> (label.value() == null) ? "" :
                    (RNNCoreAnnotations.getPredictedClass(label) != -1) ?
                        (label.value() + "|sentiment=" + RNNCoreAnnotations.getPredictedClass(label) + "|prob=" +
                            (String.format("%.3f", RNNCoreAnnotations.getPredictedClassProb(label)))) : label.value());
            String treeString = sentimentTreeStringWriter.toString();
            l2.set("sentimentTree", treeString.trim());
          }
          // (openie)
          Collection<RelationTriple> openIETriples = sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
          writeTriples(l2, "openie", openIETriples);
          // (kbp)
          Collection<RelationTriple> kbpTriples = sentence.get(CoreAnnotations.KBPTriplesAnnotation.class);
          writeTriples(l2, "kbp", kbpTriples);

          // (entity mentions)
          if (sentence.get(CoreAnnotations.MentionsAnnotation.class) != null) {
            Integer sentTokenBegin = sentence.get(CoreAnnotations.TokenBeginAnnotation.class);
            l2.set("entitymentions", sentence.get(CoreAnnotations.MentionsAnnotation.class).stream().map(m -> (Consumer<Writer>) (Writer l3) -> {
              Integer tokenBegin = m.get(CoreAnnotations.TokenBeginAnnotation.class);
              Integer tokenEnd = m.get(CoreAnnotations.TokenEndAnnotation.class);
              l3.set("docTokenBegin", tokenBegin);
              l3.set("docTokenEnd", tokenEnd);
              if (tokenBegin != null && sentTokenBegin != null) {
                l3.set("tokenBegin", tokenBegin - sentTokenBegin);
              }
              if (tokenEnd != null && sentTokenBegin != null) {
                l3.set("tokenEnd", tokenEnd - sentTokenBegin);
              }
              l3.set("text", m.get(CoreAnnotations.TextAnnotation.class));
              //l3.set("originalText", m.get(CoreAnnotations.OriginalTextAnnotation.class));
              //l3.set("lemma", m.get(CoreAnnotations.LemmaAnnotation.class));
              l3.set("characterOffsetBegin", m.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class));
              l3.set("characterOffsetEnd", m.get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
              //l3.set("pos", m.get(CoreAnnotations.PartOfSpeechAnnotation.class));
              l3.set("ner", m.get(CoreAnnotations.NamedEntityTagAnnotation.class));
              l3.set("normalizedNER", m.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class));
              l3.set("entitylink", m.get(CoreAnnotations.WikipediaEntityAnnotation.class));
              // add ner confidence info if there is any to report
              Map<String,Double> nerConfidences = m.get(CoreAnnotations.NamedEntityTagProbsAnnotation.class);
              List<String> nerLabelsWithConfidences =
                      nerConfidences.keySet().stream().filter(x -> !x.equals("O")).collect(
                              Collectors.toList());
              if (nerLabelsWithConfidences.size() > 0) {
                l3.set("nerConfidences", (Consumer<Writer>) l4 -> {
                  for (String nerLabel : nerLabelsWithConfidences) {
                    l4.set(nerLabel, nerConfidences.get(nerLabel));
                  }
                });
              }
              // Timex
              Timex time = m.get(TimeAnnotations.TimexAnnotation.class);
              writeTime(l3, time);
            }));
          }

          // (add tokens)
          if (sentence.get(CoreAnnotations.TokensAnnotation.class) != null) {
            l2.set("tokens", sentence.get(CoreAnnotations.TokensAnnotation.class).stream().map(token -> (Consumer<Writer>) (Writer l3) -> {
              // Add a single token
              l3.set("index", token.index());
              l3.set("word", token.word());
              l3.set("originalText", token.originalText());
              l3.set("lemma", token.lemma());
              l3.set("characterOffsetBegin", token.beginPosition());
              l3.set("characterOffsetEnd", token.endPosition());
              if (token.containsKey(CoreAnnotations.CodepointOffsetBeginAnnotation.class) &&
                  token.containsKey(CoreAnnotations.CodepointOffsetEndAnnotation.class)) {
                l3.set("codepointOffsetBegin", token.get(CoreAnnotations.CodepointOffsetBeginAnnotation.class));
                l3.set("codepointOffsetEnd", token.get(CoreAnnotations.CodepointOffsetEndAnnotation.class));
              }
              l3.set("pos", token.tag());
              l3.set("ner", token.ner());
              l3.set("normalizedNER", token.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class));
              l3.set("speaker", token.get(CoreAnnotations.SpeakerAnnotation.class));
              l3.set("speakerType", token.get(CoreAnnotations.SpeakerTypeAnnotation.class));
              l3.set("truecase", token.get(CoreAnnotations.TrueCaseAnnotation.class));
              l3.set("truecaseText", token.get(CoreAnnotations.TrueCaseTextAnnotation.class));
              l3.set("before", token.get(CoreAnnotations.BeforeAnnotation.class));
              l3.set("after", token.get(CoreAnnotations.AfterAnnotation.class));
              l3.set("entitylink", token.get(CoreAnnotations.WikipediaEntityAnnotation.class));
              // Timex
              Timex time = token.get(TimeAnnotations.TimexAnnotation.class);
              writeTime(l3, time);
            }));
          }
        }));
      } else {
        if (doc.get(CoreAnnotations.TokensAnnotation.class) != null) {
          l1.set("tokens", doc.get(CoreAnnotations.TokensAnnotation.class).stream().map(token ->
              (Consumer<Writer>) (Writer l2) -> {
                l2.set("index", token.index());
                l2.set("word", token.word());
                l2.set("originalText", token.originalText());
                l2.set("characterOffsetBegin", token.beginPosition());
                l2.set("characterOffsetEnd", token.endPosition());
                if (token.containsKey(CoreAnnotations.CodepointOffsetBeginAnnotation.class) &&
                    token.containsKey(CoreAnnotations.CodepointOffsetEndAnnotation.class)) {
                  l2.set("codepointOffsetBegin", token.get(CoreAnnotations.CodepointOffsetBeginAnnotation.class));
                  l2.set("codepointOffsetEnd", token.get(CoreAnnotations.CodepointOffsetEndAnnotation.class));
                }
          }));
        }
      }

      // Add coref values
      if (doc.get(CorefCoreAnnotations.CorefChainAnnotation.class) != null) {
        Map<Integer, CorefChain> corefChains =
            doc.get(CorefCoreAnnotations.CorefChainAnnotation.class);
        if (corefChains != null) {
          l1.set("corefs", (Consumer<Writer>) chainWriter -> {
            for (CorefChain chain : corefChains.values()) {
              CorefChain.CorefMention representative = chain.getRepresentativeMention();
              chainWriter.set(Integer.toString(chain.getChainID()), chain.getMentionsInTextualOrder().stream().map(mention -> (Consumer<Writer>) (Writer mentionWriter) -> {
                mentionWriter.set("id", mention.mentionID);
                mentionWriter.set("text", mention.mentionSpan);
                mentionWriter.set("type", mention.mentionType);
                mentionWriter.set("number", mention.number);
                mentionWriter.set("gender", mention.gender);
                mentionWriter.set("animacy", mention.animacy);
                mentionWriter.set("startIndex", mention.startIndex);
                mentionWriter.set("endIndex", mention.endIndex);
                mentionWriter.set("headIndex", mention.headIndex);
                mentionWriter.set("sentNum", mention.sentNum);
                mentionWriter.set("position", Arrays.stream(mention.position.elems()).boxed().collect(Collectors.toList()));
                mentionWriter.set("isRepresentativeMention", mention == representative);
              }));
            }
          });
        }
      }

      // quotes
      if (doc.get(CoreAnnotations.QuotationsAnnotation.class) != null) {
        List<CoreMap> quotes = QuoteAnnotator.gatherQuotes(doc);
        l1.set("quotes", quotes.stream().map(quote -> (Consumer<Writer>) (Writer l2) -> {
          l2.set("id", quote.get(CoreAnnotations.QuotationIndexAnnotation.class));
          l2.set("text", quote.get(CoreAnnotations.TextAnnotation.class));
          l2.set("beginIndex", quote.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class));
          l2.set("endIndex", quote.get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
          l2.set("beginToken", quote.get(CoreAnnotations.TokenBeginAnnotation.class));
          l2.set("endToken", quote.get(CoreAnnotations.TokenEndAnnotation.class));
          l2.set("beginSentence", quote.get(CoreAnnotations.SentenceBeginAnnotation.class));
          l2.set("endSentence", quote.get(CoreAnnotations.SentenceEndAnnotation.class));
          if (quote.get(QuoteAttributionAnnotator.MentionAnnotation.class) != null) {
              l2.set("mention", quote.get(QuoteAttributionAnnotator.MentionAnnotation.class));
          }
          if (quote.get(QuoteAttributionAnnotator.MentionBeginAnnotation.class) != null) {
              l2.set("mentionBegin", quote.get(QuoteAttributionAnnotator.MentionBeginAnnotation.class));
          }
          if (quote.get(QuoteAttributionAnnotator.MentionEndAnnotation.class) != null) {
              l2.set("mentionEnd", quote.get(QuoteAttributionAnnotator.MentionEndAnnotation.class));
          }
          if (quote.get(QuoteAttributionAnnotator.MentionTypeAnnotation.class) != null) {
              l2.set("mentionType", quote.get(QuoteAttributionAnnotator.MentionTypeAnnotation.class));
          }
          if (quote.get(QuoteAttributionAnnotator.MentionSieveAnnotation.class) != null) {
              l2.set("mentionSieve", quote.get(QuoteAttributionAnnotator.MentionSieveAnnotation.class));
          }
          l2.set("speaker",
              quote.get(QuoteAttributionAnnotator.SpeakerAnnotation.class) != null ?
                  quote.get(QuoteAttributionAnnotator.SpeakerAnnotation.class) :
                  "Unknown");
          if (quote.get(QuoteAttributionAnnotator.SpeakerSieveAnnotation.class) != null) {
              l2.set("speakerSieve", quote.get(QuoteAttributionAnnotator.SpeakerSieveAnnotation.class));
          }
          l2.set("canonicalSpeaker",
              quote.get(QuoteAttributionAnnotator.CanonicalMentionAnnotation.class) != null ?
                  quote.get(QuoteAttributionAnnotator.CanonicalMentionAnnotation.class) :
                  "Unknown");
          if (quote.get(QuoteAttributionAnnotator.CanonicalMentionBeginAnnotation.class) != null) {
              l2.set("canonicalMentionBegin", quote.get(QuoteAttributionAnnotator.CanonicalMentionBeginAnnotation.class));
          }
          if (quote.get(QuoteAttributionAnnotator.CanonicalMentionEndAnnotation.class) != null) {
              l2.set("canonicalMentionEnd", quote.get(QuoteAttributionAnnotator.CanonicalMentionEndAnnotation.class));
          }
        }));
      }

      // sections
      if (doc.get(CoreAnnotations.SectionsAnnotation.class) != null) {
        List<CoreMap> sections = doc.get(CoreAnnotations.SectionsAnnotation.class);
        l1.set("sections", sections.stream().map(section -> (Consumer<Writer>) (Writer l2) -> {
          // Set char start
          l2.set("charBegin", section.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class));
          // Set char end
          l2.set("charEnd", section.get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
          // Set author
          if (section.get(CoreAnnotations.AuthorAnnotation.class) != null) {
            l2.set("author", section.get(CoreAnnotations.AuthorAnnotation.class));
          }
          // Set date time
          if (section.get(CoreAnnotations.SectionDateAnnotation.class) != null) {
            l2.set("dateTime", section.get(CoreAnnotations.SectionDateAnnotation.class));
          }
          // add the sentence indexes for the sentences in this section
          List<CoreMap> sentences = section.get(CoreAnnotations.SentencesAnnotation.class);
          l2.set("sentenceIndexes", sentences.stream().map(sentence -> (Consumer<Writer>) (Writer l3) -> {
            int sentenceIndex = sentence.get(CoreAnnotations.SentenceIndexAnnotation.class);
            l3.set("index", sentenceIndex);
          }));
        }));
      }
    });

    l0.newline();
    l0.flush();  // flush
  }

  private static void writeTriples(Writer l2, String key, Collection<RelationTriple> triples) {
    if (triples != null) {
      l2.set(key, triples.stream().map(triple -> (Consumer<Writer>) (Writer tripleWriter) -> {
        tripleWriter.set("subject", triple.subjectGloss());
        tripleWriter.set("subjectSpan", Span.fromPair(triple.subjectTokenSpan()));
        tripleWriter.set("relation", triple.relationGloss());
        tripleWriter.set("relationSpan", Span.fromPair(triple.relationTokenSpan()));
        tripleWriter.set("object", triple.objectGloss());
        tripleWriter.set("objectSpan", Span.fromPair(triple.objectTokenSpan()));
      }));
    }
  }

  private static void writeTime(Writer l3, Timex time) {
    if (time != null) {
      Timex.Range range = time.range();
      l3.set("timex", (Consumer<Writer>) l4 -> {
        l4.set("tid", time.tid());
        l4.set("type", time.timexType());
        l4.set("value", time.value());
        l4.set("altValue", time.altVal());
        l4.set("range", (range != null)? (Consumer<Writer>) l5 -> {
          l5.set("begin", range.begin);
          l5.set("end", range.end);
          l5.set("duration", range.duration);
        } : null);
      });
    }
  }

  /**
   * Convert a dependency graph to a format expected as input to {@link Writer#set(String, Object)}.
   */
  @SuppressWarnings({"RedundantCast", "RedundantSuppression"})
  // It's lying; we need the "redundant" casts (as of 2014-09-08)
  private static Object buildDependencyTree(SemanticGraph graph) {
    if(graph != null) {
      return Stream.concat(
          // Roots
          graph.getRoots().stream().map( (IndexedWord root) -> (Consumer<Writer>) dep -> {
            dep.set("dep", "ROOT");
            dep.set("governor", 0);
            dep.set("governorGloss", "ROOT");
            dep.set("dependent", root.index());
            dep.set("dependentGloss", root.word());
          }),
          // Regular edges
          graph.edgeListSorted().stream().map( (SemanticGraphEdge edge) -> (Consumer<Writer>) (Writer dep) -> {
            dep.set("dep", edge.getRelation().toString());
            dep.set("governor", edge.getGovernor().index());
            dep.set("governorGloss", edge.getGovernor().word());
            dep.set("dependent", edge.getDependent().index());
            dep.set("dependentGloss", edge.getDependent().word());
          })
      );
    } else {
      return null;
    }
  }

  public static String jsonPrint(Annotation annotation) throws IOException {
    StringOutputStream os = new StringOutputStream();
    new JSONOutputter().print(annotation, os);
    return os.toString();
  }

  public static void jsonPrint(Annotation annotation, OutputStream os) throws IOException {
    new JSONOutputter().print(annotation, os);
  }

  public static void jsonPrint(Annotation annotation, OutputStream os, StanfordCoreNLP pipeline) throws IOException {
    new JSONOutputter().print(annotation, os, pipeline);
  }

  public static void jsonPrint(Annotation annotation, OutputStream os, Options options) throws IOException {
    new JSONOutputter().print(annotation, os, options);
  }


  /**
   * Our very own little JSON writing class.
   * For usage, see the test cases in JSONOutputterTest.
   *
   * For the love of all that is holy, don't try to write JSON multithreaded.
   * It should go without saying that this is not threadsafe.
   */
  public static class JSONWriter {
    protected final PrintWriter writer;
    protected final Options options;
    public JSONWriter(PrintWriter writer, Options options) {
      this.writer = writer;
      this.options = options;
    }

    @SuppressWarnings({"unchecked", "UnnecessaryBoxing", "RawUseOfParameterized"})
    private void routeObject(int indent, Object value) {
      if (value instanceof String) {
        // Case: simple string (this is easy!)
        writer.write("\"");
        writer.write(StringUtils.escapeJsonString(value.toString()));
        writer.write("\"");
      } else if (value instanceof Collection) {
        // Case: collection
        writer.write("["); newline();
        Iterator<Object> elems = ((Collection<Object>) value).iterator();
        while (elems.hasNext()) {
          indent(indent + 1);
          routeObject(indent + 1, elems.next());
          if (elems.hasNext()) {
            writer.write(",");
          }
          newline();
        }
        indent(indent);
        writer.write("]");
      } else if (value instanceof Enum) {
        // Case: enumeration constant
        writer.write("\"");
        writer.write(StringUtils.escapeJsonString(((Enum) value).name()));
        writer.write("\"");
      } else if (value instanceof Pair) {
        routeObject(indent, Arrays.asList(((Pair) value).first, ((Pair) value).second));
      } else if (value instanceof Span) {
        writer.write("[");
        writer.write(Integer.toString(((Span) value).start()));
        writer.write(","); space();
        writer.write(Integer.toString(((Span) value).end()));
        writer.write("]");
      } else if (value instanceof Consumer) {
        object(indent, (Consumer<Writer>) value);
      } else if (value instanceof Stream) {
        routeObject(indent, ((Stream) value).collect(Collectors.toList()));
      } else if (value.getClass().isArray()) {
        // Arrays make life miserable in Java
        Class<?> componentType = value.getClass().getComponentType();
        if (componentType.isPrimitive()) {
          if (int.class.isAssignableFrom(componentType)) {
            ArrayList<Integer> lst = new ArrayList<>();
            //noinspection Convert2streamapi
            for (int elem : ((int[]) value)) {
              lst.add(elem);
            }
            routeObject(indent, lst);
          } else if (short.class.isAssignableFrom(componentType)) {
            ArrayList<Short> lst = new ArrayList<>();
            for (short elem : ((short[]) value)) {
              lst.add(elem);
            }
            routeObject(indent, lst);
          } else if (byte.class.isAssignableFrom(componentType)) {
            ArrayList<Byte> lst = new ArrayList<>();
            for (byte elem : ((byte[]) value)) {
              lst.add(elem);
            }
            routeObject(indent, lst);
          } else if (long.class.isAssignableFrom(componentType)) {
            ArrayList<Long> lst = new ArrayList<>();
            //noinspection Convert2streamapi
            for (long elem : ((long[]) value)) {
              lst.add(elem);
            }
            routeObject(indent, lst);
          } else if (char.class.isAssignableFrom(componentType)) {
            ArrayList<Character> lst = new ArrayList<>();
            for (char elem : ((char[]) value)) {
              lst.add(elem);
            }
            routeObject(indent, lst);
          } else if (float.class.isAssignableFrom(componentType)) {
            ArrayList<Float> lst = new ArrayList<>();
            for (float elem : ((float[]) value)) {
              lst.add(elem);
            }
            routeObject(indent, lst);
          } else if (double.class.isAssignableFrom(componentType)) {
            ArrayList<Double> lst = new ArrayList<>();
            //noinspection Convert2streamapi
            for (double elem : ((double[]) value)) {
              lst.add(elem);
            }
            routeObject(indent, lst);
          } else if (boolean.class.isAssignableFrom(componentType)) {
            ArrayList<Boolean> lst = new ArrayList<>();
            for (boolean elem : ((boolean[]) value)) {
              lst.add(elem);
            }
            routeObject(indent, lst);
          } else {
            throw new IllegalStateException("Unhandled primitive type in array: " + componentType);
          }
        } else {
          routeObject(indent, Arrays.asList((Object[]) value));
        }
      } else if (value instanceof Integer) {
        writer.write(Integer.toString((Integer) value));
      } else if (value instanceof Short) {
        writer.write(Short.toString((Short) value));
      } else if (value instanceof Byte) {
        writer.write(Byte.toString((Byte) value));
      } else if (value instanceof Long) {
        writer.write(Long.toString((Long) value));
      } else if (value instanceof Character) {
        writer.write(Character.toString((Character) value));
      } else if (value instanceof Float) {
        // Use the US Locale so that we can conform with json output format
        // The decimal separator is always supposed to be . for example
        Locale locale = Locale.US;
        NumberFormat formatter = NumberFormat.getInstance(locale);
        formatter.setMaximumFractionDigits(7);
        formatter.setMinimumFractionDigits(0);
        writer.write(formatter.format(value));
      } else if (value instanceof Double) {
        Locale locale = Locale.US;
        NumberFormat formatter = NumberFormat.getInstance(locale);
        formatter.setMaximumFractionDigits(14);
        formatter.setMinimumFractionDigits(0);
        writer.write(formatter.format(value));
      } else if (value instanceof Boolean) {
        writer.write(Boolean.toString((Boolean) value));
      } else if (int.class.isAssignableFrom(value.getClass())) {
        routeObject(indent, Integer.valueOf((int) value));
      } else if (short.class.isAssignableFrom(value.getClass())) {
        routeObject(indent, Short.valueOf((short) value));
      } else if (byte.class.isAssignableFrom(value.getClass())) {
        routeObject(indent, Byte.valueOf((byte) value));
      } else if (long.class.isAssignableFrom(value.getClass())) {
        routeObject(indent, Long.valueOf((long) value));
      } else if (char.class.isAssignableFrom(value.getClass())) {
        routeObject(indent, Character.valueOf((char) value));
      } else if (float.class.isAssignableFrom(value.getClass())) {
        routeObject(indent, Float.valueOf((float) value));
      } else if (double.class.isAssignableFrom(value.getClass())) {
        routeObject(indent, Double.valueOf((double) value));
      } else if (boolean.class.isAssignableFrom(value.getClass())) {
        routeObject(indent, Boolean.valueOf((boolean) value));
      } else {
        throw new RuntimeException("Unknown object to serialize: " + value);
      }
    }

    public void object(int indent, Consumer<Writer> callback) {
      writer.write("{");
      final Pointer<Boolean> firstCall = new Pointer<>(true);
      callback.accept((key, value) -> {
        if (key != null && value != null) {
          // First call overhead
          if (!firstCall.dereference().orElse(false)) {
            writer.write(",");
          }
          firstCall.set(false);
          // Write the key
          newline();
          indent(indent + 1);
          writer.write("\"");
          writer.write(StringUtils.escapeJsonString(key));
          writer.write("\":"); space();
          // Write the value
          routeObject(indent + 1, value);
        }
      });
      newline(); indent(indent); writer.write("}");
    }

    public void object(Consumer<Writer> callback) {
      object(0, callback);
    }

    private void indent(int num) {
      if (options.pretty) {
        for (int i = 0; i < num; ++i) {
          writer.write(INDENT_CHAR);
        }
      }
    }

    public void flush() {
      writer.flush();
    }

    private void space() {
      if (options.pretty) {
        writer.write(" ");
      }
    }

    private void newline() {
      if (options.pretty) {
        writer.write("\n");
      }
    }

    public static String objectToJSON(Consumer<Writer> callback) {
      try {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(new OutputStreamWriter(os, "utf-8"));
        new JSONWriter(out, new Options()).object(callback);
        out.close();
        return os.toString();
      } catch(UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * A tiny little functional interface for writing a (key, value) pair.
   * The key should always be a String, the value can be either a String,
   * a Collection of valid values, or a Callback taking a Writer (this is how
   * we represent objects while creating JSON).
   */
  @FunctionalInterface
  public interface Writer {
    /**
     * Set a (key, value) pair in a JSON object.
     * Note that if either the key or the value is null, nothing will be set.
     * @param key The key of the object.
     * @param value The value of the object.
     */
    void set(String key, Object value);
  }

}
