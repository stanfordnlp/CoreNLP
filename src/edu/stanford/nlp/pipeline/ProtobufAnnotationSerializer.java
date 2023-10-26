package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ie.NumberNormalizer;
import edu.stanford.nlp.ie.machinereading.structure.EntityMention;
import edu.stanford.nlp.ie.machinereading.structure.ExtractionObject;
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations.*;
import edu.stanford.nlp.ie.machinereading.structure.RelationMention;
import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.SegmenterCoreAnnotations;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.naturalli.*;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.quoteattribution.ChapterAnnotator;
import edu.stanford.nlp.quoteattribution.QuoteAttributionUtils.EnhancedSentenceAnnotation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.time.Timex;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.LabeledScoredTreeNode;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.trees.ud.CoNLLUFeatures;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.*;
import edu.stanford.nlp.trees.TreeCoreAnnotations.*;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.*;
import edu.stanford.nlp.time.TimeAnnotations.*;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import edu.stanford.nlp.coref.CorefCoreAnnotations.*;

import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.coref.data.SpeakerInfo;

/**
 * <p>
 *   A serializer using Google's protocol buffer format.
 *   The files produced by this serializer, in addition to being language-independent,
 *   are a little over 10% the size and 4x faster to read+write versus the default Java serialization
 *   (see GenericAnnotationSerializer), when both files are compressed with gzip.
 * </p>
 *
 * <p>
 *   Note that this handles only a subset of the possible annotations
 *   that can be attached to a sentence. Nonetheless, it is guaranteed to be
 *   lossless with the default set of named annotators you can create from a
 *   {@link StanfordCoreNLP} pipeline, with default properties defined for each annotator.
 *   Note that the serializer does not gzip automatically -- this must be done by passing in a GZipOutputStream
 *   and calling a GZipInputStream manually. For most Annotations, gzipping provides a notable decrease in size (~2.5x)
 *   due to most of the data being raw Strings.
 * </p>
 *
 * <p>
 *   To allow lossy serialization, use {@link ProtobufAnnotationSerializer#ProtobufAnnotationSerializer(boolean)}.
 *   Otherwise, an exception is thrown if an unknown key appears in the annotation which would not be saved to the
 *   protocol buffer.
 *   If such keys exist, and are a part of the standard CoreNLP pipeline, please let us know!
 *   If you would like to serialize keys in addition to those serialized by default (e.g., you are attaching
 *   your own annotations), then you should do the following:
 * </p>
 *
 * <ol>
 *   <li>
 *     Create a .proto file which extends one or more of Document, Sentence, or Token. Each of these have fields
 *     100-255 left open for user extensions. An example of such an extension is:
 *     <pre>
 *       package edu.stanford.nlp.pipeline;
 *
 *       option java_package = "com.example.my.awesome.nlp.app";
 *       option java_outer_classname = "MyAppProtos";
 *
 *       import "CoreNLP.proto";
 *
 *       extend Sentence {
 *         optional uint32 myNewField    = 101;
 *       }
 *     </pre>
 *   </li>
 *
 *   <li>
 *     Compile your .proto file with protoc. For example (from CORENLP_HOME):
 *     <pre>
 *        protoc -I=src/edu/stanford/nlp/pipeline/:/path/to/folder/contining/your/proto/file --java_out=/path/to/output/src/folder/  /path/to/proto/file
 *     </pre>
 *   </li>
 *
 *   <li>
 *     <p>
 *     Extend {@link ProtobufAnnotationSerializer} to serialize and deserialize your field.
 *     Generally, this entail overriding two functions -- one to write the proto and one to read it.
 *     In both cases, you usually want to call the superclass' implementation of the function, and add on to it
 *     from there.
 *     In our running example, adding a field to the {@link CoreNLPProtos.Sentence} proto, you would overwrite:
 *     </p>
 *
 *     <ul>
 *       <li>{@link ProtobufAnnotationSerializer#toProtoBuilder(edu.stanford.nlp.util.CoreMap, java.util.Set)}</li>
 *       <li>{@link ProtobufAnnotationSerializer#fromProtoNoTokens(edu.stanford.nlp.pipeline.CoreNLPProtos.Sentence)}</li>
 *     </ul>
 *
 *     <p>
 *     Note, importantly, that for the serializer to be able to check for lossless serialization, all annotations added
 *     to the proto must be registered as added by being removed from the set passed to
 *     {@link ProtobufAnnotationSerializer#toProtoBuilder(edu.stanford.nlp.util.CoreMap, java.util.Set)} (and the analogous
 *     functions for documents and tokens).
 *     </p>
 *
 *     <p>
 *       Lastly, the new annotations must be registered in the original .proto file; this can be achieved by including
 *       a static block in the overwritten class:
 *     </p>
 *     <pre>
 *       static {
 *         ExtensionRegistry registry = ExtensionRegistry.newInstance();
 *         registry.add(MyAppProtos.myNewField);
 *         CoreNLPProtos.registerAllExtensions(registry);
 *       }
 *     </pre>
 *   </li>
 * </ol>
 *
 *
 * TODOs
 * <ul>
 *   <li>In CoreNLP, the leaves of a tree are == to the tokens in a sentence. This is not the case for a deserialized proto.</li>
 * </ul>
 *
 * @author Gabor Angeli
 */
public class ProtobufAnnotationSerializer extends AnnotationSerializer {

  /** A global lock; necessary since dependency tree creation is not threadsafe */
  private static final Object globalLock = "I'm a lock :)";

  /**
   * An exception to denote that the serialization would be lossy.
   * This exception is thrown at serialization time.
   *
   * @see ProtobufAnnotationSerializer#enforceLosslessSerialization
   * @see ProtobufAnnotationSerializer#ProtobufAnnotationSerializer(boolean)
   */
  public static class LossySerializationException extends RuntimeException {
    private static final long serialVersionUID = 741506383659886245L;

    private LossySerializationException(String msg) { super(msg); }
  }

  public static class FailedSerializationError extends RuntimeException {
    private static final long serialVersionUID = 8142679843568354709L;

    private FailedSerializationError(String msg) { super(msg); }
  }

  /**
   * If true, serialization is guaranteed to be lossless or else a runtime exception is thrown
   * at serialization time.
   */
  public final boolean enforceLosslessSerialization;

  /**
   * Create a new Annotation serializer outputting to a protocol buffer format.
   * This is guaranteed to either be a lossless compression, or throw an exception at
   * serialization time.
   */
  public ProtobufAnnotationSerializer() { this(true); }

  /**
   * Create a new Annotation serializer outputting to a protocol buffer format.
   *
   * @param enforceLosslessSerialization If set to true, a {@link ProtobufAnnotationSerializer.LossySerializationException}
   *                                     is thrown at serialization
   *                                     time if the serialization would be lossy. If set to false,
   *                                     these exceptions are ignored.
   *
   */
  public ProtobufAnnotationSerializer(boolean enforceLosslessSerialization) { this.enforceLosslessSerialization = enforceLosslessSerialization; }

  /** {@inheritDoc} */
  @Override
  public OutputStream write(Annotation corpus, OutputStream os) throws IOException {
    CoreNLPProtos.Document serialized = toProto(corpus);
    serialized.writeDelimitedTo(os);
    os.flush();
    return os;
  }

  /** {@inheritDoc} */
  @Override
  public Pair<Annotation, InputStream> read(InputStream is) throws IOException, ClassNotFoundException, ClassCastException {
    CoreNLPProtos.Document doc = CoreNLPProtos.Document.parseDelimitedFrom(is);
    return Pair.makePair(fromProto(doc), is);
  }

  /**
   * Read a single protocol buffer, which constitutes the entire stream.
   * This is in contrast to the default, where mutliple buffers may come out of the stream,
   * and therefore each one is prepended by the length of the buffer to follow.
   *
   * @param in The file to read.
   * @return A parsed Annotation.
   * @throws IOException In case the stream cannot be read from.
   */
  @SuppressWarnings({"UnusedDeclaration", "ThrowFromFinallyBlock"})
  public Annotation readUndelimited(File in) throws IOException {
    CoreNLPProtos.Document doc;
    try (FileInputStream delimited = new FileInputStream(in)) {
      doc = CoreNLPProtos.Document.parseFrom(delimited);
    } catch (Exception e) {
      try(FileInputStream undelimited = new FileInputStream(in)) {
        doc = CoreNLPProtos.Document.parseDelimitedFrom(undelimited);
      }
    }
    return fromProto(doc);
  }

  /**
   * Get a particular key from a CoreMap, registering it as being retrieved.
   * @param map The CoreMap to retrieve the key from.
   * @param keysToRegister A set of keys to remove this key from, representing to keys which should be retrieved by the serializer.
   * @param key The key key to retrieve.
   * @param <E> The class of the item which is being retrieved.
   * @return CoreMap.get(key)
   */
  private static <E> E getAndRegister(CoreMap map, Set<Class<?>> keysToRegister, Class<? extends CoreAnnotation<E>> key) {
    keysToRegister.remove(key);
    return map.get(key);
  }

  /**
   * Create a CoreLabel proto from a CoreLabel instance.
   * This is not static, as it optionally throws an exception if the serialization is lossy.
   * @param coreLabel The CoreLabel to convert
   * @return A protocol buffer message corresponding to this CoreLabel
   */
  public CoreNLPProtos.Token toProto(CoreLabel coreLabel) {
    return toProto(coreLabel, Collections.emptySet());
  }

  public CoreNLPProtos.Token toProto(CoreLabel coreLabel, Set<Class<?>> keysToSkip) {
    Set<Class<?>> keysToSerialize = new HashSet<>(coreLabel.keySetNotNull());
    CoreNLPProtos.Token.Builder builder = toProtoBuilder(coreLabel, keysToSerialize, keysToSkip);
    // Completeness check
    if (enforceLosslessSerialization && !keysToSerialize.isEmpty()) {
      throw new LossySerializationException("Keys are not being serialized: " + StringUtils.join(keysToSerialize));
    }
    return builder.build();
  }

  /**
   * <p>
   *   The method to extend by subclasses of the Protobuf Annotator if custom additions are added to Tokens.
   *   In contrast to {@link ProtobufAnnotationSerializer#toProto(edu.stanford.nlp.ling.CoreLabel)}, this function
   *   returns a builder that can be extended.
   * </p>
   *
   * @param coreLabel The sentence to save to a protocol buffer
   * @param keysToSerialize A set tracking which keys have been saved. It's important to remove any keys added to the proto
   *                        from this set, as the code tracks annotations to ensure lossless serialization
   */
  protected CoreNLPProtos.Token.Builder toProtoBuilder(CoreLabel coreLabel, Set<Class<?>> keysToSerialize, Set<Class<?>> keysToSkip) {
    CoreNLPProtos.Token.Builder builder = CoreNLPProtos.Token.newBuilder();
    Set<Class<?>> keySet = coreLabel.keySetNotNull();
    for (Class<?> clazz : keysToSkip) {
      keySet.remove(clazz);
      keysToSerialize.remove(clazz);
    }
    // Remove items serialized elsewhere from the required list
    keysToSerialize.remove(TextAnnotation.class);
    keysToSerialize.remove(SentenceIndexAnnotation.class);
    keysToSerialize.remove(DocIDAnnotation.class);
    keysToSerialize.remove(ParagraphAnnotation.class);
    // Remove items populated by number normalizer
    keysToSerialize.remove(NumericCompositeObjectAnnotation.class);
    //keysToSerialize.remove(NumericCompositeTypeAnnotation.class);
    //keysToSerialize.remove(NumericCompositeValueAnnotation.class);
    //keysToSerialize.remove(NumericTypeAnnotation.class);
    //keysToSerialize.remove(NumericValueAnnotation.class);
    // Remove items which were never supposed to be there in the first place
    keysToSerialize.remove(ForcedSentenceUntilEndAnnotation.class);
    keysToSerialize.remove(ForcedSentenceEndAnnotation.class);
    keysToSerialize.remove(HeadWordLabelAnnotation.class);
    keysToSerialize.remove(HeadTagLabelAnnotation.class);
    // Remove section info
    keysToSerialize.remove(SectionStartAnnotation.class);
    keysToSerialize.remove(SectionEndAnnotation.class);
    // Set the word (this may be null if the CoreLabel is storing a character (as in case of segmenter)
    if (coreLabel.word() != null)
      builder.setWord(coreLabel.word());
    // Optional fields
    if (keySet.contains(PartOfSpeechAnnotation.class)) { builder.setPos(coreLabel.tag()); keysToSerialize.remove(PartOfSpeechAnnotation.class); }
    if (keySet.contains(IndexAnnotation.class)) { builder.setIndex(coreLabel.index()); keysToSerialize.remove(IndexAnnotation.class); }
    if (keySet.contains(EmptyIndexAnnotation.class)) { builder.setEmptyIndex(coreLabel.getEmptyIndex()); keysToSerialize.remove(EmptyIndexAnnotation.class); }
    if (keySet.contains(ValueAnnotation.class)) { builder.setValue(coreLabel.value()); keysToSerialize.remove(ValueAnnotation.class); }
    if (keySet.contains(CategoryAnnotation.class)) { builder.setCategory(coreLabel.category()); keysToSerialize.remove(CategoryAnnotation.class); }
    if (keySet.contains(BeforeAnnotation.class)) { builder.setBefore(coreLabel.before()); keysToSerialize.remove(BeforeAnnotation.class); }
    if (keySet.contains(AfterAnnotation.class)) { builder.setAfter(coreLabel.after()); keysToSerialize.remove(AfterAnnotation.class); }
    if (keySet.contains(OriginalTextAnnotation.class)) { builder.setOriginalText(coreLabel.originalText()); keysToSerialize.remove(OriginalTextAnnotation.class); }
    if (keySet.contains(NamedEntityTagAnnotation.class)) { builder.setNer(coreLabel.ner()); keysToSerialize.remove(NamedEntityTagAnnotation.class); }
    if (keySet.contains(CoarseNamedEntityTagAnnotation.class)) {
      builder.setCoarseNER(coreLabel.get(CoarseNamedEntityTagAnnotation.class));
      keysToSerialize.remove(CoarseNamedEntityTagAnnotation.class); }
    if (keySet.contains(FineGrainedNamedEntityTagAnnotation.class)) {
      builder.setFineGrainedNER(coreLabel.get(FineGrainedNamedEntityTagAnnotation.class));
      keysToSerialize.remove(FineGrainedNamedEntityTagAnnotation.class);
    }
    if (keySet.contains(NamedEntityTagProbsAnnotation.class)) {
      // in case of empty label prob list, add string "empty"
      // this is to differentiate between null and an empty hash map
      if (coreLabel.get(NamedEntityTagProbsAnnotation.class).keySet().size() == 0) {
        builder.addNerLabelProbs("empty");
      } else {
        for (String labelWithProb : coreLabel.get(NamedEntityTagProbsAnnotation.class).keySet()) {
          Double labelProb = coreLabel.get(NamedEntityTagProbsAnnotation.class).get(labelWithProb);
          builder.addNerLabelProbs(labelWithProb+"="+labelProb);
        }
      }
      keysToSerialize.remove(NamedEntityTagProbsAnnotation.class);
    }
    if (keySet.contains(CharacterOffsetBeginAnnotation.class)) { builder.setBeginChar(coreLabel.beginPosition()); keysToSerialize.remove(CharacterOffsetBeginAnnotation.class); }
    if (keySet.contains(CharacterOffsetEndAnnotation.class)) { builder.setEndChar(coreLabel.endPosition()); keysToSerialize.remove(CharacterOffsetEndAnnotation.class); }
    if (keySet.contains(CodepointOffsetBeginAnnotation.class)) {
      builder.setCodepointOffsetBegin(coreLabel.get(CoreAnnotations.CodepointOffsetBeginAnnotation.class));
      keysToSerialize.remove(CodepointOffsetBeginAnnotation.class);
    }
    if (keySet.contains(CodepointOffsetEndAnnotation.class)) {
      builder.setCodepointOffsetEnd(coreLabel.get(CoreAnnotations.CodepointOffsetEndAnnotation.class));
      keysToSerialize.remove(CodepointOffsetEndAnnotation.class);
    }
    if (keySet.contains(LemmaAnnotation.class)) { builder.setLemma(coreLabel.lemma()); keysToSerialize.remove(LemmaAnnotation.class); }
    if (keySet.contains(UtteranceAnnotation.class)) { builder.setUtterance(getAndRegister(coreLabel, keysToSerialize, UtteranceAnnotation.class)); }
    if (keySet.contains(SpeakerAnnotation.class)) { builder.setSpeaker(getAndRegister(coreLabel, keysToSerialize, SpeakerAnnotation.class)); }
    if (keySet.contains(SpeakerTypeAnnotation.class)) { builder.setSpeakerType(getAndRegister(coreLabel, keysToSerialize, SpeakerTypeAnnotation.class)); }
    if (keySet.contains(BeginIndexAnnotation.class)) { builder.setBeginIndex(getAndRegister(coreLabel, keysToSerialize, BeginIndexAnnotation.class)); }
    if (keySet.contains(EndIndexAnnotation.class)) { builder.setEndIndex(getAndRegister(coreLabel, keysToSerialize, EndIndexAnnotation.class)); }
    if (keySet.contains(TokenBeginAnnotation.class)) { builder.setTokenBeginIndex(getAndRegister(coreLabel, keysToSerialize, TokenBeginAnnotation.class)); }
    if (keySet.contains(TokenEndAnnotation.class)) { builder.setTokenEndIndex(getAndRegister(coreLabel, keysToSerialize, TokenEndAnnotation.class)); }
    if (keySet.contains(NormalizedNamedEntityTagAnnotation.class)) { builder.setNormalizedNER(getAndRegister(coreLabel, keysToSerialize, NormalizedNamedEntityTagAnnotation.class)); }
    if (keySet.contains(TimexAnnotation.class)) { builder.setTimexValue(toProto(getAndRegister(coreLabel, keysToSerialize, TimexAnnotation.class))); }
    if (keySet.contains(AnswerAnnotation.class)) { builder.setAnswer(getAndRegister(coreLabel, keysToSerialize, AnswerAnnotation.class)); }
    if (keySet.contains(WikipediaEntityAnnotation.class)) { builder.setWikipediaEntity(getAndRegister(coreLabel, keysToSerialize, WikipediaEntityAnnotation.class)); }
    if (keySet.contains(IsNewlineAnnotation.class)) { builder.setIsNewline(getAndRegister(coreLabel, keysToSerialize, IsNewlineAnnotation.class)); }
    if (keySet.contains(XmlContextAnnotation.class)) {
      builder.setHasXmlContext(true);
      builder.addAllXmlContext(getAndRegister(coreLabel, keysToSerialize, XmlContextAnnotation.class));
    } else {
      builder.setHasXmlContext(false);
    }
    // if there is section info for this token, store it
    if (keySet.contains(SectionStartAnnotation.class)) {
      CoreMap sectionAnnotations = coreLabel.get(SectionStartAnnotation.class);
      // if there is a section name annotation, store it
      if (sectionAnnotations.get(SectionAnnotation.class) != null)
        builder.setSectionName(sectionAnnotations.get(SectionAnnotation.class));
      // if there is a section author annotation, store it
      if (sectionAnnotations.get(AuthorAnnotation.class) != null)
        builder.setSectionAuthor(sectionAnnotations.get(AuthorAnnotation.class));
      // if there is a section date annotation, store it
      if (sectionAnnotations.get(SectionDateAnnotation.class) != null)
        builder.setSectionAuthor(sectionAnnotations.get(SectionDateAnnotation.class));
    }
    // store section end label
    if (keySet.contains(SectionEndAnnotation.class)) {
      builder.setSectionEndLabel(coreLabel.get(SectionEndAnnotation.class));
    }
    if (keySet.contains(CorefClusterIdAnnotation.class)) { builder.setCorefClusterID(getAndRegister(coreLabel, keysToSerialize, CorefClusterIdAnnotation.class)); }
    if (keySet.contains(NaturalLogicAnnotations.OperatorAnnotation.class)) { builder.setOperator(toProto(getAndRegister(coreLabel, keysToSerialize, NaturalLogicAnnotations.OperatorAnnotation.class))); }
    if (keySet.contains(NaturalLogicAnnotations.PolarityAnnotation.class)) { builder.setPolarity(toProto(getAndRegister(coreLabel, keysToSerialize, NaturalLogicAnnotations.PolarityAnnotation.class))); }
    if (keySet.contains(NaturalLogicAnnotations.PolarityDirectionAnnotation.class)) { builder.setPolarityDir(getAndRegister(coreLabel, keysToSerialize, NaturalLogicAnnotations.PolarityDirectionAnnotation.class)); }
    if (keySet.contains(SpanAnnotation.class)) {
      IntPair span = getAndRegister(coreLabel, keysToSerialize, SpanAnnotation.class);
      builder.setSpan(CoreNLPProtos.Span.newBuilder().setBegin(span.getSource()).setEnd(span.getTarget()).build());
    }
    if (keySet.contains(SentimentCoreAnnotations.SentimentClass.class)) { builder.setSentiment(getAndRegister(coreLabel, keysToSerialize, SentimentCoreAnnotations.SentimentClass.class)); }
    if (keySet.contains(QuotationIndexAnnotation.class)) { builder.setQuotationIndex(getAndRegister(coreLabel, keysToSerialize, QuotationIndexAnnotation.class)); }
    if (keySet.contains(CoNLLUFeats.class)) { builder.setConllUFeatures(toMapStringStringProto(getAndRegister(coreLabel, keysToSerialize, CoNLLUFeats.class))); }
    if (keySet.contains(CoNLLUTokenSpanAnnotation.class)) {
      IntPair span = getAndRegister(coreLabel, keysToSerialize, CoNLLUTokenSpanAnnotation.class);
      builder.setConllUTokenSpan(CoreNLPProtos.Span.newBuilder().setBegin(span.getSource()).setEnd(span.getTarget()).build());
    }
    if (keySet.contains(CoNLLUMisc.class)) { builder.setConllUMisc(getAndRegister(coreLabel, keysToSerialize, CoNLLUMisc.class));}
    if (keySet.contains(CoarseTagAnnotation.class)) { builder.setCoarseTag(getAndRegister(coreLabel, keysToSerialize, CoarseTagAnnotation.class));}
    if (keySet.contains(CoNLLUSecondaryDepsAnnotation.class)) { builder.setConllUSecondaryDeps(toMapStringStringProto(getAndRegister(coreLabel, keysToSerialize, CoNLLUSecondaryDepsAnnotation.class)));}

    // Non-default annotators
    if (keySet.contains(edu.stanford.nlp.ling.CoreAnnotations.GenderAnnotation.class)) {
      builder.setGender(getAndRegister(coreLabel, keysToSerialize, CoreAnnotations.GenderAnnotation.class));
    }
    if (keySet.contains(TrueCaseAnnotation.class)) { builder.setTrueCase(getAndRegister(coreLabel, keysToSerialize, TrueCaseAnnotation.class)); }
    if (keySet.contains(TrueCaseTextAnnotation.class)) { builder.setTrueCaseText(getAndRegister(coreLabel, keysToSerialize, TrueCaseTextAnnotation.class)); }

    // Chinese character related stuff
    if (keySet.contains(ChineseCharAnnotation.class)) { builder.setChineseChar(getAndRegister(coreLabel, keysToSerialize, ChineseCharAnnotation.class)); }
    if (keySet.contains(ChineseSegAnnotation.class)) { builder.setChineseSeg(getAndRegister(coreLabel, keysToSerialize, ChineseSegAnnotation.class)); }
    if (keySet.contains(SegmenterCoreAnnotations.XMLCharAnnotation.class)) { builder.setChineseXMLChar(getAndRegister(coreLabel, keysToSerialize, SegmenterCoreAnnotations.XMLCharAnnotation.class)); }

    // Arabic character related stuff
    if (keySet.contains(ArabicSegAnnotation.class)) { builder.setArabicSeg(getAndRegister(coreLabel, keysToSerialize, ArabicSegAnnotation.class)); }

    // French tokens potentially have ParentAnnotation
    if (keySet.contains(ParentAnnotation.class)) { builder.setParent(getAndRegister(coreLabel, keysToSerialize, ParentAnnotation.class)); }

    // indexes into document wide mention lists
    if (keySet.contains(EntityMentionIndexAnnotation.class)) {
      builder.setEntityMentionIndex(getAndRegister(coreLabel, keysToSerialize, EntityMentionIndexAnnotation.class)); }

    // coref mentions that contain this token
    if (keySet.contains(CorefMentionIndexesAnnotation.class)) {
      for (Integer corefMentionIndex : coreLabel.get(CorefMentionIndexesAnnotation.class)) {
        builder.addCorefMentionIndex(corefMentionIndex);
      }
      keysToSerialize.remove(CorefMentionIndexesAnnotation.class);
    }

    // handle MWT stuff
    if (keySet.contains(IsMultiWordTokenAnnotation.class)) {
      builder.setIsMWT(getAndRegister(coreLabel, keysToSerialize, IsMultiWordTokenAnnotation.class));
    }
    if (keySet.contains(IsFirstWordOfMWTAnnotation.class)) {
      builder.setIsFirstMWT(getAndRegister(coreLabel, keysToSerialize, IsFirstWordOfMWTAnnotation.class));
    }
    if (keySet.contains(MWTTokenTextAnnotation.class)) {
      builder.setMwtText(getAndRegister(coreLabel, keysToSerialize, MWTTokenTextAnnotation.class));
    }
    if (keySet.contains(MWTTokenMiscAnnotation.class)) {
      builder.setMwtMisc(getAndRegister(coreLabel, keysToSerialize, MWTTokenMiscAnnotation.class));
    }

    // handle numeric stuff
    if (keySet.contains(NumericValueAnnotation.class)) {
      builder.setNumericValue(getAndRegister(coreLabel, keysToSerialize, NumericValueAnnotation.class).longValue());
    }
    if (keySet.contains(NumericTypeAnnotation.class)) {
      builder.setNumericType(getAndRegister(coreLabel, keysToSerialize, NumericTypeAnnotation.class));
    }
    if (keySet.contains(NumericCompositeValueAnnotation.class)) {
      builder.setNumericCompositeValue(getAndRegister(coreLabel, keysToSerialize, NumericCompositeValueAnnotation.class).longValue());
    }
    if (keySet.contains(NumericCompositeTypeAnnotation.class)) {
      builder.setNumericCompositeType(getAndRegister(coreLabel, keysToSerialize, NumericCompositeTypeAnnotation.class));
    }

    // Return
    return builder;
  }

  /**
   * Create a protobuf builder, rather than a compiled protobuf.
   * Useful for, e.g., the simple CoreNLP interface.
   * @param sentence The sentence to serialize.
   * @return A Sentence builder.
   */
  @SuppressWarnings("unchecked")
  public CoreNLPProtos.Sentence.Builder toProtoBuilder(CoreMap sentence) {
    return toProtoBuilder(sentence, Collections.emptySet());
  }

  /**
   * Create a Sentence proto from a CoreMap instance.
   * This is not static, as it optionally throws an exception if the serialization is lossy.
   * @param sentence The CoreMap to convert. Note that it should not be a CoreLabel or an Annotation,
   *                 and should represent a sentence.
   * @return A protocol buffer message corresponding to this sentence
   * @throws IllegalArgumentException If the sentence is not a valid sentence (e.g., is a document or a word).
   */
  public CoreNLPProtos.Sentence toProto(CoreMap sentence) {
    Set<Class<?>> keysToSerialize = new HashSet<>(sentence.keySet());
    CoreNLPProtos.Sentence.Builder builder = toProtoBuilder(sentence, keysToSerialize);
    // Completeness check
    if (enforceLosslessSerialization && !keysToSerialize.isEmpty()) {
      throw new LossySerializationException("Keys are not being serialized: " + StringUtils.join(keysToSerialize));
    }
    return builder.build();
  }

  /**
   * <p>
   *   The method to extend by subclasses of the Protobuf Annotator if custom additions are added to Tokens.
   *   In contrast to {@link ProtobufAnnotationSerializer#toProto(edu.stanford.nlp.ling.CoreLabel)}, this function
   *   returns a builder that can be extended.
   * </p>
   *
   * @param sentence The sentence to save to a protocol buffer
   * @param keysToSerialize A set tracking which keys have been saved. It's important to remove any keys added to the proto
   *                        from this set, as the code tracks annotations to ensure lossless serialization.
   */
  @SuppressWarnings("deprecation")
  protected CoreNLPProtos.Sentence.Builder toProtoBuilder(CoreMap sentence, Set<Class<?>> keysToSerialize) {
    // Error checks
    if (sentence instanceof CoreLabel) { throw new IllegalArgumentException("CoreMap is actually a CoreLabel"); }
    CoreNLPProtos.Sentence.Builder builder = CoreNLPProtos.Sentence.newBuilder();
    // Remove items serialized elsewhere from the required list
    keysToSerialize.remove(TextAnnotation.class);
    keysToSerialize.remove(NumerizedTokensAnnotation.class);
    // Required fields
    builder.setTokenOffsetBegin(getAndRegister(sentence, keysToSerialize, TokenBeginAnnotation.class));
    builder.setTokenOffsetEnd(getAndRegister(sentence, keysToSerialize, TokenEndAnnotation.class));
    // Get key set of CoreMap
    Set<Class<?>> keySet;
    if (sentence instanceof ArrayCoreMap) {
       keySet = ((ArrayCoreMap) sentence).keySetNotNull();
    } else {
      keySet = new IdentityHashSet<>(sentence.keySet());
    }
    // Tokens
    if (sentence.containsKey(TokensAnnotation.class)) {
      int tokenIndex = 0;
      for (CoreLabel tok : sentence.get(TokensAnnotation.class)) {
        ++tokenIndex;
        if (tok.index() == tokenIndex) {
          builder.addToken(toProto(tok, Collections.singleton(IndexAnnotation.class)));
        } else {
          builder.addToken(toProto(tok));
        }
      }
      keysToSerialize.remove(TokensAnnotation.class);
    }
    // Characters
    if (sentence.containsKey(SegmenterCoreAnnotations.CharactersAnnotation.class)) {
      for (CoreLabel c : sentence.get(SegmenterCoreAnnotations.CharactersAnnotation.class)) {
        builder.addCharacter(toProto(c));
      }
      keysToSerialize.remove(SegmenterCoreAnnotations.CharactersAnnotation.class);
    }
    // Optional fields
    if (keySet.contains(SentenceIndexAnnotation.class)) { builder.setSentenceIndex(getAndRegister(sentence, keysToSerialize, SentenceIndexAnnotation.class)); }
    if (keySet.contains(LineNumberAnnotation.class)) { builder.setLineNumber(getAndRegister(sentence, keysToSerialize, LineNumberAnnotation.class)); }
    if (keySet.contains(CharacterOffsetBeginAnnotation.class)) { builder.setCharacterOffsetBegin(getAndRegister(sentence, keysToSerialize, CharacterOffsetBeginAnnotation.class)); }
    if (keySet.contains(CharacterOffsetEndAnnotation.class)) { builder.setCharacterOffsetEnd(getAndRegister(sentence, keysToSerialize, CharacterOffsetEndAnnotation.class)); }
    if (keySet.contains(TreeAnnotation.class)) { builder.setParseTree(toProto(getAndRegister(sentence, keysToSerialize, TreeAnnotation.class))); }
    if (keySet.contains(BinarizedTreeAnnotation.class)) { builder.setBinarizedParseTree(toProto(getAndRegister(sentence, keysToSerialize, BinarizedTreeAnnotation.class))); }
    if (keySet.contains(KBestTreesAnnotation.class)) {
      for (Tree tree : sentence.get(KBestTreesAnnotation.class)) {
        builder.addKBestParseTrees(toProto(tree));
        keysToSerialize.remove(KBestTreesAnnotation.class);
      }
    }
    if (keySet.contains(SentimentCoreAnnotations.SentimentAnnotatedTree.class)) { builder.setAnnotatedParseTree(toProto(getAndRegister(sentence, keysToSerialize, SentimentCoreAnnotations.SentimentAnnotatedTree.class))); }
    if (keySet.contains(SentimentCoreAnnotations.SentimentClass.class)) { builder.setSentiment(getAndRegister(sentence, keysToSerialize, SentimentCoreAnnotations.SentimentClass.class)); }
    if (keySet.contains(BasicDependenciesAnnotation.class)) { builder.setBasicDependencies(toProto(getAndRegister(sentence, keysToSerialize, BasicDependenciesAnnotation.class))); }
    if (keySet.contains(CollapsedDependenciesAnnotation.class)) { builder.setCollapsedDependencies(toProto(getAndRegister(sentence, keysToSerialize, CollapsedDependenciesAnnotation.class))); }
    if (keySet.contains(CollapsedCCProcessedDependenciesAnnotation.class)) { builder.setCollapsedCCProcessedDependencies(toProto(getAndRegister(sentence, keysToSerialize, CollapsedCCProcessedDependenciesAnnotation.class))); }
    if (keySet.contains(AlternativeDependenciesAnnotation.class)) { builder.setAlternativeDependencies(toProto(getAndRegister(sentence, keysToSerialize, AlternativeDependenciesAnnotation.class))); }
    if (keySet.contains(EnhancedDependenciesAnnotation.class)) { builder.setEnhancedDependencies(toProto(getAndRegister(sentence, keysToSerialize, EnhancedDependenciesAnnotation.class))); }
    if (keySet.contains(EnhancedPlusPlusDependenciesAnnotation.class)) { builder.setEnhancedPlusPlusDependencies(toProto(getAndRegister(sentence, keysToSerialize, EnhancedPlusPlusDependenciesAnnotation.class))); }
    if (keySet.contains(TokensAnnotation.class) && getAndRegister(sentence, keysToSerialize, TokensAnnotation.class).size() > 0 &&
        getAndRegister(sentence, keysToSerialize, TokensAnnotation.class).get(0).containsKey(ParagraphAnnotation.class)) {
      builder.setParagraph(getAndRegister(sentence, keysToSerialize, TokensAnnotation.class).get(0).get(ParagraphAnnotation.class));
    }
    if (keySet.contains(NumerizedTokensAnnotation.class)) { builder.setHasNumerizedTokensAnnotation(true); } else { builder.setHasNumerizedTokensAnnotation(false); }
    if (keySet.contains(NaturalLogicAnnotations.EntailedSentencesAnnotation.class)) {
      for (SentenceFragment entailedSentence : getAndRegister(sentence, keysToSerialize, NaturalLogicAnnotations.EntailedSentencesAnnotation.class)) {
        builder.addEntailedSentence(toProto(entailedSentence));
      }
    }
    if (keySet.contains(NaturalLogicAnnotations.EntailedClausesAnnotation.class)) {
      for (SentenceFragment entailedClause : getAndRegister(sentence, keysToSerialize, NaturalLogicAnnotations.EntailedClausesAnnotation.class)) {
        builder.addEntailedClause(toProto(entailedClause));
      }
    }
    if (keySet.contains(NaturalLogicAnnotations.RelationTriplesAnnotation.class)) {
      builder.setHasOpenieTriplesAnnotation(true);
      for (RelationTriple triple : getAndRegister(sentence, keysToSerialize, NaturalLogicAnnotations.RelationTriplesAnnotation.class)) {
        builder.addOpenieTriple(toProto(triple));
      }
    }
    if (keySet.contains(KBPTriplesAnnotation.class)) {
      // mark that this sentence has kbp triples, potentially empty list
      builder.setHasKBPTriplesAnnotation(true);
      // store each of the kbp triples
      for (RelationTriple triple : getAndRegister(sentence, keysToSerialize, KBPTriplesAnnotation.class)) {
        builder.addKbpTriple(toProto(triple));
      }
    }
    // Non-default annotators
    if (keySet.contains(EntityMentionsAnnotation.class)) {
      builder.setHasRelationAnnotations(true);
      for (EntityMention entity : getAndRegister(sentence, keysToSerialize, EntityMentionsAnnotation.class)) {
        builder.addEntity(toProto(entity));
      }
    } else {
      builder.setHasRelationAnnotations(false);
    }
    if (keySet.contains(RelationMentionsAnnotation.class)) {
      if (!builder.getHasRelationAnnotations()) { throw new IllegalStateException("Registered entity mentions without relation mentions"); }
      for (RelationMention relation : getAndRegister(sentence, keysToSerialize, RelationMentionsAnnotation.class)) {
        builder.addRelation(toProto(relation));
      }
    }
    // add each of the mentions in the List<Mentions> for this sentence
    if (keySet.contains(CorefMentionsAnnotation.class)) {
      builder.setHasCorefMentionsAnnotation(true);
      for (Mention m : sentence.get(CorefMentionsAnnotation.class)) {
        builder.addMentionsForCoref(toProto(m));
      }
      keysToSerialize.remove(CorefMentionsAnnotation.class);
    }
    // Entity mentions
    if (keySet.contains(MentionsAnnotation.class)) {
      for (CoreMap mention : sentence.get(MentionsAnnotation.class)) {
        builder.addMentions(toProtoMention(mention));
      }
      keysToSerialize.remove(MentionsAnnotation.class);
      builder.setHasEntityMentionsAnnotation(true);
    } else {
      builder.setHasEntityMentionsAnnotation(false);
    }
    // add a sentence id if it exists
    if (keySet.contains(SentenceIDAnnotation.class)) builder.setSentenceID(getAndRegister(sentence, keysToSerialize, SentenceIDAnnotation.class));

    // add section index
    if (keySet.contains(SectionIndexAnnotation.class)) builder.setSectionIndex(getAndRegister(sentence, keysToSerialize, SectionIndexAnnotation.class));

    // add section date
    if (keySet.contains(SectionDateAnnotation.class)) builder.setSectionDate(getAndRegister(sentence, keysToSerialize, SectionDateAnnotation.class));

    // add section name
    if (keySet.contains(SectionAnnotation.class)) builder.setSectionName(getAndRegister(sentence, keysToSerialize, SectionAnnotation.class));

    // add section author
    if (keySet.contains(AuthorAnnotation.class)) builder.setSectionAuthor(getAndRegister(sentence, keysToSerialize, AuthorAnnotation.class));

    // add doc id
    if (keySet.contains(DocIDAnnotation.class)) builder.setDocID(getAndRegister(sentence, keysToSerialize, DocIDAnnotation.class));

    // add boolean flag if sentence is quoted
    if (keySet.contains(QuotedAnnotation.class)) builder.setSectionQuoted(getAndRegister(sentence, keysToSerialize, QuotedAnnotation.class));
    // quote annotator can also add an "enhanced sentence" if multiple sentences are treated as a single sentence
    if (keySet.contains(EnhancedSentenceAnnotation.class)) {
      keysToSerialize.remove(EnhancedSentenceAnnotation.class);
      CoreMap enhanced = sentence.get(EnhancedSentenceAnnotation.class);
      builder.setEnhancedSentence(toProto(enhanced));
    }
    
    // add chapter index if there is one
    if (keySet.contains(ChapterAnnotator.ChapterAnnotation.class)) builder.setChapterIndex(getAndRegister(sentence, keysToSerialize, ChapterAnnotator.ChapterAnnotation.class));

    // add paragraph index info
    if (keySet.contains(ParagraphIndexAnnotation.class)) builder.setParagraphIndex(getAndRegister(sentence, keysToSerialize, ParagraphIndexAnnotation.class));

    // add speaker annotaiton
    if (keySet.contains(SpeakerAnnotation.class)) { builder.setSpeaker(getAndRegister(sentence, keysToSerialize, SpeakerAnnotation.class)); }
    if (keySet.contains(SpeakerTypeAnnotation.class)) { builder.setSpeakerType(getAndRegister(sentence, keysToSerialize, SpeakerTypeAnnotation.class)); }

    // Return
    return builder;
  }

  /**
   * Create a Document proto from a CoreMap instance.
   * This is not static, as it optionally throws an exception if the serialization is lossy.
   * @param doc The Annotation to convert.
   * @return A protocol buffer message corresponding to this document
   */
  public CoreNLPProtos.Document toProto(Annotation doc) {
    Set<Class<?>> keysToSerialize = new HashSet<>(doc.keySet());
    keysToSerialize.remove(TokensAnnotation.class);  // note(gabor): tokens are saved in the sentence
    keysToSerialize.remove(UseMarkedDiscourseAnnotation.class);  // this is only used as internal communication between annotators?
    CoreNLPProtos.Document.Builder builder = toProtoBuilder(doc, keysToSerialize);
    // Completeness Check
    if (enforceLosslessSerialization && !keysToSerialize.isEmpty()) {
      throw new LossySerializationException("Keys are not being serialized: " + StringUtils.join(keysToSerialize));
    }
    return builder.build();
  }

  /**
   * Create a protobuf builder, rather than a compiled protobuf.
   * Useful for, e.g., the simple CoreNLP interface.
   * @param doc The document to serialize.
   * @return A Document builder.
   */
  @SuppressWarnings("unchecked")
  public CoreNLPProtos.Document.Builder toProtoBuilder(Annotation doc) {
    return toProtoBuilder(doc, Collections.emptySet());
  }

  /**
   * <p>
   *   The method to extend by subclasses of the Protobuf Annotator if custom additions are added to Tokens.
   *   In contrast to {@link ProtobufAnnotationSerializer#toProto(edu.stanford.nlp.ling.CoreLabel)}, this function
   *   returns a builder that can be extended.
   * </p>
   *
   * @param doc The sentence to save to a protocol buffer
   * @param keysToSerialize A set tracking which keys have been saved. It's important to remove any keys added to the proto
   *                        from this set, as the code tracks annotations to ensure lossless serializationA set tracking which keys have been saved. It's important to remove any keys added to the proto*
   *                        from this set, as the code tracks annotations to ensure lossless serialization.
   */
  protected CoreNLPProtos.Document.Builder toProtoBuilder(Annotation doc, Set<Class<?>> keysToSerialize) {
    CoreNLPProtos.Document.Builder builder = CoreNLPProtos.Document.newBuilder();
    // Required fields
    builder.setText(doc.get(TextAnnotation.class));
    keysToSerialize.remove(TextAnnotation.class);
    // Check if we need to store xml info
    if (doc.containsKey(SectionsAnnotation.class)) {
      builder.setXmlDoc(true);
    } else {
      builder.setXmlDoc(false);
    }
    // Optional fields
    if (doc.containsKey(SentencesAnnotation.class)) {
      for (CoreMap sentence : doc.get(SentencesAnnotation.class)) { builder.addSentence(toProto(sentence)); }
      keysToSerialize.remove(SentencesAnnotation.class);
    } else if (doc.containsKey(TokensAnnotation.class)) {
      for (CoreLabel token : doc.get(TokensAnnotation.class)) { builder.addSentencelessToken(toProto(token)); }
    }
    if (doc.containsKey(DocIDAnnotation.class)) {
      builder.setDocID(doc.get(DocIDAnnotation.class));
      keysToSerialize.remove(DocIDAnnotation.class);
    }
    if (doc.containsKey(DocDateAnnotation.class)) {
      builder.setDocDate(doc.get(DocDateAnnotation.class));
      keysToSerialize.remove(DocDateAnnotation.class);
    }
    if (doc.containsKey(CalendarAnnotation.class)) {
      builder.setCalendar(doc.get(CalendarAnnotation.class).toInstant().toEpochMilli());
      keysToSerialize.remove(CalendarAnnotation.class);
    }
    // add coref info
    if (doc.containsKey(CorefChainAnnotation.class)) {
      // mark that annotation has coref info
      builder.setHasCorefAnnotation(true);
      for (Map.Entry<Integer, CorefChain> chain : doc.get(CorefChainAnnotation.class).entrySet()) {
       builder.addCorefChain(toProto(chain.getValue()));
      }
      keysToSerialize.remove(CorefChainAnnotation.class);
    } else {
      builder.setHasCorefAnnotation(false);
    }
    // add document level coref mentions info
    if (doc.containsKey(CorefMentionsAnnotation.class)) {
      builder.setHasCorefMentionAnnotation(true);
      for (Mention corefMention : doc.get(CorefMentionsAnnotation.class)) {
        builder.addMentionsForCoref(toProto(corefMention));
      }
      keysToSerialize.remove(CorefMentionsAnnotation.class);
    } else {
      builder.setHasCorefMentionAnnotation(false);
    }
    // add quote information
    if (doc.containsKey(QuotationsAnnotation.class)) {
      for (CoreMap quote : doc.get(QuotationsAnnotation.class)) {
        builder.addQuote(toProtoQuote(quote));
      }
      keysToSerialize.remove(QuotationsAnnotation.class);
    }
    // add document level entity mentions info
    if (doc.containsKey(MentionsAnnotation.class)) {
      for (CoreMap mention : doc.get(MentionsAnnotation.class)) {
        builder.addMentions(toProtoMention(mention));
      }
      keysToSerialize.remove(MentionsAnnotation.class);
      builder.setHasEntityMentionsAnnotation(true);
    } else {
      builder.setHasEntityMentionsAnnotation(false);
    }
    // mappings between coref mentions and entity mentions
    if (doc.containsKey(EntityMentionToCorefMentionMappingAnnotation.class)) {
      Map<Integer,Integer> entityMentionToCorefMention =
          doc.get(EntityMentionToCorefMentionMappingAnnotation.class);
      int numEntityMentions = doc.get(MentionsAnnotation.class).size();
      for (int entityMentionIndex = 0 ; entityMentionIndex < numEntityMentions; entityMentionIndex++) {
        if (entityMentionToCorefMention.keySet().contains(entityMentionIndex)) {
          builder.addEntityMentionToCorefMentionMappings(entityMentionToCorefMention.get(entityMentionIndex));
        } else {
          // store a -1 if there is no coref mention corresponding to this entity mention
          builder.addEntityMentionToCorefMentionMappings(-1);
        }
      }
      keysToSerialize.remove(EntityMentionToCorefMentionMappingAnnotation.class);
    }
    if (doc.containsKey(CorefMentionToEntityMentionMappingAnnotation.class)) {
      Map<Integer,Integer> corefMentionToEntityMention =
          doc.get(CorefMentionToEntityMentionMappingAnnotation.class);
      int numCorefMentions = doc.get(CorefMentionsAnnotation.class).size();
      for (int corefMentionIndex = 0 ; corefMentionIndex < numCorefMentions; corefMentionIndex++) {
        if (corefMentionToEntityMention.keySet().contains(corefMentionIndex)) {
          builder.addCorefMentionToEntityMentionMappings(corefMentionToEntityMention.get(corefMentionIndex));
        } else {
          // store a -1 if there is no coref mention corresponding to this entity mention
          builder.addCorefMentionToEntityMentionMappings(-1);
        }
      }
      keysToSerialize.remove(CorefMentionToEntityMentionMappingAnnotation.class);
    }
    // add character info from segmenter
    if (doc.containsKey(SegmenterCoreAnnotations.CharactersAnnotation.class)) {
      for (CoreLabel c : doc.get(SegmenterCoreAnnotations.CharactersAnnotation.class)) {
        builder.addCharacter(toProto(c));
      }
      keysToSerialize.remove(SegmenterCoreAnnotations.CharactersAnnotation.class);
    }
    // add section info
    if (doc.containsKey(SectionsAnnotation.class)) {
      for (CoreMap section : doc.get(SectionsAnnotation.class)) {
        builder.addSections(toProtoSection(section));
      }
      keysToSerialize.remove(SectionsAnnotation.class);
    }
    // Return
    return builder;
  }

  /**
   * Create a ParseTree proto from a Tree. If the Tree is a scored tree, the scores will
   * be preserved.
   * @param parseTree The parse tree to convert.
   * @return A protocol buffer message corresponding to this tree.
   */
  public static CoreNLPProtos.ParseTree toProto(Tree parseTree) {
    CoreNLPProtos.ParseTree.Builder builder = CoreNLPProtos.ParseTree.newBuilder();
    // Required fields
    for (Tree child : parseTree.children()) { builder.addChild(toProto(child)); }
    // Optional fields
    IntPair span = parseTree.getSpan();
    if (span != null) {
      builder.setYieldBeginIndex(span.getSource());
      builder.setYieldEndIndex(span.getTarget());
    }
    if (parseTree.label() != null) {
      builder.setValue(parseTree.label().value());
    }
    if (!Double.isNaN(parseTree.score())) {
      builder.setScore(parseTree.score());
    }
    Integer sentiment;
    if (parseTree.label() instanceof CoreMap && (sentiment = ((CoreMap) parseTree.label()).get(RNNCoreAnnotations.PredictedClass.class)) != null) {
      builder.setSentiment(CoreNLPProtos.Sentiment.forNumber(sentiment));
    }
    // Return
    return builder.build();
  }

  /**
   * Create a compact representation of the semantic graph for this dependency parse.
   * @param graph The dependency graph to save.
   * @return A protocol buffer message corresponding to this parse.
   */
  public CoreNLPProtos.DependencyGraph toProto(SemanticGraph graph) {
    return toProto(graph, false);
  }

  /**
   * Create a compact representation of the semantic graph for this dependency parse.
   * @param graph The dependency graph to save.
   * @return A protocol buffer message corresponding to this parse.
   */
  public CoreNLPProtos.DependencyGraph toProto(SemanticGraph graph, boolean storeTokens) {
    CoreNLPProtos.DependencyGraph.Builder builder = CoreNLPProtos.DependencyGraph.newBuilder();
    // Nodes
    for (IndexedWord node : graph.vertexSet()) {
      // Register node
      CoreNLPProtos.DependencyGraph.Node.Builder nodeBuilder = CoreNLPProtos.DependencyGraph.Node.newBuilder()
        .setSentenceIndex(node.get(SentenceIndexAnnotation.class))
        .setIndex(node.index());
      if (node.copyCount() > 0) {
        nodeBuilder.setCopyAnnotation(node.copyCount());
      }
      if (node.getEmptyIndex() > 0) {
        nodeBuilder.setEmptyIndex(node.getEmptyIndex());
      }
      // register roots
      if (graph.isRoot(node)) {
        builder.addRootNode(builder.getNodeList().size());
        // use the legacy format for roots as well,
        // so that old readers with can still read graphs
        // that don't have empty nodes as roots
        builder.addRoot(node.index());
      }
      builder.addNode(nodeBuilder.build());
      // Nodes, if we want to store them as tokens
      if (storeTokens) {
        builder.addToken(toProto(node.backingLabel()));
      }
    }
    // Edges
    for (SemanticGraphEdge edge : graph.edgeIterable()) {
      // Set edge
      CoreNLPProtos.DependencyGraph.Edge.Builder edgeBuilder = CoreNLPProtos.DependencyGraph.Edge.newBuilder();
      edgeBuilder.setSource(edge.getSource().index())
        .setTarget(edge.getTarget().index())
        .setDep(edge.getRelation().toString())
        .setIsExtra(edge.isExtra())
        .setSourceCopy(edge.getSource().copyCount())
        .setTargetCopy(edge.getTarget().copyCount())
        .setLanguage(toProto(edge.getRelation().getLanguage()));
      if (edge.getSource().getEmptyIndex() > 0) {
        edgeBuilder.setSourceEmpty(edge.getSource().getEmptyIndex());
      }
      if (edge.getTarget().getEmptyIndex() > 0) {
        edgeBuilder.setTargetEmpty(edge.getTarget().getEmptyIndex());
      }
      builder.addEdge(edgeBuilder.build());
    }
    // Return
    return builder.build();
  }
  /**
   * Create a CorefChain protocol buffer from the given coref chain.
   * @param chain The coref chain to convert.
   * @return A protocol buffer message corresponding to this chain.
   */
  public CoreNLPProtos.CorefChain toProto(CorefChain chain) {
    CoreNLPProtos.CorefChain.Builder builder = CoreNLPProtos.CorefChain.newBuilder();
    // Set ID
    builder.setChainID(chain.getChainID());
    // Set mentions
    Map<CorefChain.CorefMention, Integer> mentionToIndex = new IdentityHashMap<>();
    for (Map.Entry<IntPair, Set<CorefChain.CorefMention>> entry : chain.getMentionMap().entrySet()) {
      for (CorefChain.CorefMention mention : entry.getValue()) {
        mentionToIndex.put(mention, mentionToIndex.size());
        builder.addMention(CoreNLPProtos.CorefChain.CorefMention.newBuilder()
            .setMentionID(mention.mentionID)
            .setMentionType(mention.mentionType.name())
            .setNumber(mention.number.name())
            .setGender(mention.gender.name())
            .setAnimacy(mention.animacy.name())
            .setBeginIndex(mention.startIndex - 1)
            .setEndIndex(mention.endIndex - 1)
            .setHeadIndex(mention.headIndex - 1)
            .setSentenceIndex(mention.sentNum - 1)
            .setPosition(mention.position.get(1)) );
      }
    }
    // Set representative mention
    builder.setRepresentative(mentionToIndex.get(chain.getRepresentativeMention()));
    // Return
    return builder.build();
  }

  /**
   * Create a Section CoreMap protocol buffer from the given Section CoreMap
   *
   * @param section The CoreMap representing the section to serialize to a proto.
   *
   * @return The protocol buffer version of the section
   */
  public CoreNLPProtos.Section toProtoSection(CoreMap section) {
    CoreNLPProtos.Section.Builder builder = CoreNLPProtos.Section.newBuilder();
    // Set char start
    builder.setCharBegin(section.get(CharacterOffsetBeginAnnotation.class));
    // Set char end
    builder.setCharEnd(section.get(CharacterOffsetEndAnnotation.class));
    // Set author
    if (section.get(AuthorAnnotation.class) != null)
      builder.setAuthor(section.get(AuthorAnnotation.class));
    // Set date time
    if (section.get(SectionDateAnnotation.class) != null)
      builder.setDatetime(section.get(SectionDateAnnotation.class));
    // add the sentence indexes for the sentences in this section
    for (CoreMap sentence : section.get(SentencesAnnotation.class)) {
      int sentenceIndex = sentence.get(SentenceIndexAnnotation.class);
      builder.addSentenceIndexes(sentenceIndex);
    }
    // add the quotes
    for (CoreMap quote : section.get(QuotesAnnotation.class)) {
      builder.addQuotes(toProtoQuote(quote));
    }
    // add author start character offset if present
    if (section.get(SectionAuthorCharacterOffsetBeginAnnotation.class) != null) {
      builder.setAuthorCharBegin(section.get(SectionAuthorCharacterOffsetBeginAnnotation.class));
    }
    // add author end character offset if present
    if (section.get(SectionAuthorCharacterOffsetEndAnnotation.class) != null) {
      builder.setAuthorCharEnd(section.get(SectionAuthorCharacterOffsetEndAnnotation.class));
    }
    // add original xml tag with all info for section
    builder.setXmlTag(toProto(section.get(SectionTagAnnotation.class)));
    return builder.build();
  }

  public CoreNLPProtos.IndexedWord createIndexedWordProtoFromIW(IndexedWord iw) {
    CoreNLPProtos.IndexedWord.Builder builder = CoreNLPProtos.IndexedWord.newBuilder();
    if (iw == null) {
      builder.setSentenceNum(-1);
      builder.setTokenIndex(-1);
    } else {
      builder.setSentenceNum(iw.get(SentenceIndexAnnotation.class) - 1);
      builder.setTokenIndex(iw.get(IndexAnnotation.class) - 1);
      builder.setCopyCount(iw.copyCount());
    }
    return builder.build();

  }

  public CoreNLPProtos.IndexedWord createIndexedWordProtoFromCL(CoreLabel cl) {
    CoreNLPProtos.IndexedWord.Builder builder = CoreNLPProtos.IndexedWord.newBuilder();
    if (cl == null) {
      builder.setSentenceNum(-1);
      builder.setTokenIndex(-1);
    } else {
      builder.setSentenceNum(cl.get(SentenceIndexAnnotation.class) - 1);
      builder.setTokenIndex(cl.get(IndexAnnotation.class) - 1);
    }
    return builder.build();
  }

  public CoreNLPProtos.Mention toProto(Mention mention) {

    // create the builder
    CoreNLPProtos.Mention.Builder builder = CoreNLPProtos.Mention.newBuilder();

    // set enums
    if (mention.mentionType != null) { builder.setMentionType(mention.mentionType.name()); }
    if (mention.gender != null) { builder.setGender(mention.gender.name()); }
    if (mention.number != null) { builder.setNumber(mention.number.name()); }
    if (mention.animacy != null) { builder.setAnimacy(mention.animacy.name()); }
    if (mention.person != null) { builder.setPerson(mention.person.name()); }

    if (mention.headString != null) {
      builder.setHeadString(mention.headString);
    }
    if (mention.nerString != null) {
      builder.setNerString(mention.nerString);
    }

    builder.setStartIndex(mention.startIndex);
    builder.setEndIndex(mention.endIndex);
    builder.setHeadIndex(mention.headIndex);
    builder.setMentionID(mention.mentionID);
    builder.setOriginalRef(mention.originalRef);
    builder.setGoldCorefClusterID(mention.goldCorefClusterID);
    builder.setCorefClusterID(mention.corefClusterID);
    builder.setMentionNum(mention.mentionNum);
    builder.setSentNum(mention.sentNum);
    builder.setUtter(mention.utter);
    builder.setParagraph(mention.paragraph);
    builder.setIsSubject(mention.isSubject);
    builder.setIsDirectObject(mention.isDirectObject);
    builder.setIsIndirectObject(mention.isIndirectObject);
    builder.setIsPrepositionObject(mention.isPrepositionObject);
    builder.setHasTwin(mention.hasTwin);
    builder.setGeneric(mention.generic);
    builder.setIsSingleton(mention.isSingleton);

    // handle the two sets of Strings
    if (mention.dependents != null) {
      mention.dependents.forEach(builder::addDependents);
    }

    if (mention.preprocessedTerms != null) {
      mention.preprocessedTerms.forEach(builder::addPreprocessedTerms);
    }

    // set IndexedWords by storing (sentence number, token index) pairs
    builder.setDependingVerb(createIndexedWordProtoFromIW(mention.dependingVerb));
    builder.setHeadIndexedWord(createIndexedWordProtoFromIW(mention.headIndexedWord));
    builder.setHeadWord(createIndexedWordProtoFromCL(mention.headWord));
    //CoreLabel headWord = (mention.headWord != null) ? mention.headWord : null;
    //builder.setHeadWord(createCoreLabelPositionProto(mention.headWord));

    // add positions for each CoreLabel in sentence
    if (mention.sentenceWords != null) {
      for (CoreLabel cl : mention.sentenceWords) {
        builder.addSentenceWords(createIndexedWordProtoFromCL(cl));
      }
    }

    if (mention.originalSpan != null) {
      for (CoreLabel cl : mention.originalSpan) {
        builder.addOriginalSpan(createIndexedWordProtoFromCL(cl));
      }
    }

    // flag if this Mention should get basicDependency, collapsedDependency, and contextParseTree or not
    builder.setHasBasicDependency((mention.basicDependency != null));
    builder.setHasEnhancedDependency((mention.enhancedDependency != null));
    builder.setHasContextParseTree((mention.contextParseTree != null));

    // handle the sets of Mentions, just store mentionID
    if (mention.appositions != null) {
      for (Mention m : mention.appositions) {
        builder.addAppositions(m.mentionID);
      }
    }

    if (mention.predicateNominatives != null) {
      for (Mention m : mention.predicateNominatives) {
        builder.addPredicateNominatives(m.mentionID);
      }
    }

    if (mention.relativePronouns != null) {
      for (Mention m : mention.relativePronouns) {
        builder.addRelativePronouns(m.mentionID);
      }
    }

    if (mention.listMembers != null) {
      for (Mention m : mention.listMembers) {
        builder.addListMembers(m.mentionID);
      }
    }

    if (mention.belongToLists != null) {
      for (Mention m : mention.belongToLists) {
        builder.addBelongToLists(m.mentionID);
      }
    }

    if (mention.speakerInfo != null) {
      builder.setSpeakerInfo(toProto(mention.speakerInfo));
    }

    return builder.build();
  }

  public CoreNLPProtos.SpeakerInfo toProto(SpeakerInfo speakerInfo) {
    CoreNLPProtos.SpeakerInfo.Builder builder = CoreNLPProtos.SpeakerInfo.newBuilder();
    builder.setSpeakerName(speakerInfo.getSpeakerName());
    // mentionID's should be set by MentionAnnotator
    for (Mention m : speakerInfo.getMentions()) {
      builder.addMentions(m.mentionID);
    }
    return builder.build();
  }


  /**
   * Convert the given Timex object to a protocol buffer.
   * @param timex The timex to convert.
   * @return A protocol buffer corresponding to this Timex object.
   */
  public CoreNLPProtos.Timex toProto(Timex timex) {
    CoreNLPProtos.Timex.Builder builder = CoreNLPProtos.Timex.newBuilder();
    if (timex.value() != null) { builder.setValue(timex.value()); }
    if (timex.altVal() != null) { builder.setAltValue(timex.altVal()); }
    if (timex.text() != null) { builder.setText(timex.text()); }
    if (timex.timexType() != null) { builder.setType(timex.timexType()); }
    if (timex.tid() != null) { builder.setTid(timex.tid()); }
    if (timex.beginPoint() >= 0) { builder.setBeginPoint(timex.beginPoint()); }
    if (timex.endPoint() >= 0) { builder.setEndPoint(timex.endPoint()); }
    return builder.build();
  }

  /**
   * Serialize the given entity mention to the corresponding protocol buffer.
   * @param ent The entity mention to serialize.
   * @return A protocol buffer corresponding to the serialized entity mention.
   */
  public CoreNLPProtos.Entity toProto(EntityMention ent) {
    CoreNLPProtos.Entity.Builder builder = CoreNLPProtos.Entity.newBuilder();
    // From ExtractionObject
    if (ent.getObjectId() != null) { builder.setObjectID(ent.getObjectId()); }
    if (ent.getExtent() != null) { builder.setExtentStart(ent.getExtent().start()).setExtentEnd(ent.getExtent().end()); }
    if (ent.getType() != null) { builder.setType(ent.getType()); }
    if (ent.getSubType() != null) { builder.setSubtype(ent.getSubType()); }
    // From Entity
    if (ent.getHead() != null) { builder.setHeadStart(ent.getHead().start()); builder.setHeadEnd(ent.getHead().end()); }
    if (ent.getMentionType() != null) { builder.setMentionType(ent.getMentionType()); }
    if (ent.getNormalizedName() != null) { builder.setNormalizedName(ent.getNormalizedName()); }
    if (ent.getSyntacticHeadTokenPosition() >= 0) { builder.setHeadTokenIndex(ent.getSyntacticHeadTokenPosition()); }
    if (ent.getCorefID() != null) { builder.setCorefID(ent.getCorefID()); }
    // Return
    return builder.build();

  }

  /**
   * Serialize the given relation mention to the corresponding protocol buffer.
   * @param rel The relation mention to serialize.
   * @return A protocol buffer corresponding to the serialized relation mention.
   */
  public CoreNLPProtos.Relation toProto(RelationMention rel) {
    CoreNLPProtos.Relation.Builder builder = CoreNLPProtos.Relation.newBuilder();
    // From ExtractionObject
    if (rel.getObjectId() != null) { builder.setObjectID(rel.getObjectId()); }
    if (rel.getExtent() != null) { builder.setExtentStart(rel.getExtent().start()).setExtentEnd(rel.getExtent().end()); }
    if (rel.getType() != null) { builder.setType(rel.getType()); }
    if (rel.getSubType() != null) { builder.setSubtype(rel.getSubType()); }
    // From Relation
    if (rel.getArgNames() != null) {
      rel.getArgNames().forEach(builder::addArgName);
    }
    if (rel.getArgs() != null) { for (ExtractionObject arg : rel.getArgs()) { builder.addArg(toProto((EntityMention) arg)); } }
    // Return
    return builder.build();
  }

  /**
   * Serialize a CoreNLP Language to a Protobuf Language.
   * @param lang The language to serialize.
   * @return The language in a Protobuf enum.
   */
  public static CoreNLPProtos.Language toProto(Language lang) {
    switch (lang) {
      case Arabic:
        return CoreNLPProtos.Language.Arabic;
      case Chinese:
        return CoreNLPProtos.Language.Chinese;
      case UniversalChinese:
        return CoreNLPProtos.Language.UniversalChinese;
      case English:
        return CoreNLPProtos.Language.English;
      case UniversalEnglish:
        return CoreNLPProtos.Language.UniversalEnglish;
      case German:
        return CoreNLPProtos.Language.German;
      case French:
        return CoreNLPProtos.Language.French;
      case Hebrew:
        return CoreNLPProtos.Language.Hebrew;
      case Spanish:
        return CoreNLPProtos.Language.Spanish;
      case Unknown:
        return CoreNLPProtos.Language.Unknown;
      case Any:
        return CoreNLPProtos.Language.Any;
      default:
        throw new IllegalStateException("Unknown language: " + lang);
    }
  }

  /**
   * Return a Protobuf operator from an OperatorSpec (Natural Logic).
   */
  public static CoreNLPProtos.Operator toProto(OperatorSpec op) {
    return CoreNLPProtos.Operator.newBuilder()
        .setName(op.instance.name()).setQuantifierSpanBegin(op.quantifierBegin).setQuantifierSpanEnd(op.quantifierEnd)
        .setSubjectSpanBegin(op.subjectBegin).setSubjectSpanEnd(op.subjectEnd)
        .setObjectSpanBegin(op.objectBegin).setObjectSpanEnd(op.objectEnd).build();
  }

  /**
   * Return a Protobuf polarity from a CoreNLP Polarity (Natural Logic).
   */
  public static CoreNLPProtos.Polarity toProto(Polarity pol) {
    return CoreNLPProtos.Polarity.newBuilder()
        .setProjectEquivalence(CoreNLPProtos.NaturalLogicRelation.forNumber(pol.projectLexicalRelation(NaturalLogicRelation.EQUIVALENT).fixedIndex))
        .setProjectForwardEntailment(CoreNLPProtos.NaturalLogicRelation.forNumber(pol.projectLexicalRelation(NaturalLogicRelation.FORWARD_ENTAILMENT).fixedIndex))
        .setProjectReverseEntailment(CoreNLPProtos.NaturalLogicRelation.forNumber(pol.projectLexicalRelation(NaturalLogicRelation.REVERSE_ENTAILMENT).fixedIndex))
        .setProjectNegation(CoreNLPProtos.NaturalLogicRelation.forNumber(pol.projectLexicalRelation(NaturalLogicRelation.NEGATION).fixedIndex))
        .setProjectAlternation(CoreNLPProtos.NaturalLogicRelation.forNumber(pol.projectLexicalRelation(NaturalLogicRelation.ALTERNATION).fixedIndex))
        .setProjectCover(CoreNLPProtos.NaturalLogicRelation.forNumber(pol.projectLexicalRelation(NaturalLogicRelation.COVER).fixedIndex))
        .setProjectIndependence(CoreNLPProtos.NaturalLogicRelation.forNumber(pol.projectLexicalRelation(NaturalLogicRelation.INDEPENDENCE).fixedIndex))
        .build();
  }

  /**
   * Return a Protobuf RelationTriple from a RelationTriple.
   */
  public static CoreNLPProtos.SentenceFragment toProto(SentenceFragment fragment) {
    return CoreNLPProtos.SentenceFragment.newBuilder()
        .setAssumedTruth(fragment.assumedTruth)
        .setScore(fragment.score)
        .addAllTokenIndex(fragment.words.stream().map(x -> x.index() - 1).collect(Collectors.toList()))
        .setRoot(fragment.parseTree.getFirstRoot().index() - 1)
        .build();
  }


  /**
   * Return a Protobuf RelationTriple from a RelationTriple.
   */
  public CoreNLPProtos.RelationTriple toProto(RelationTriple triple) {
    CoreNLPProtos.RelationTriple.Builder builder = CoreNLPProtos.RelationTriple.newBuilder()
        .setSubject(triple.subjectGloss())
        .setRelation(triple.relationGloss())
        .setObject(triple.objectGloss())
        .setConfidence(triple.confidence)
        .addAllSubjectTokens(triple.subject.stream().map(token ->
            CoreNLPProtos.TokenLocation.newBuilder()
                .setSentenceIndex(token.sentIndex())
                .setTokenIndex(token.index() - 1)
                .build())
            .collect(Collectors.toList()))
        .addAllRelationTokens(
            triple.relation.size() == 1 && triple.relation.get(0).get(IndexAnnotation.class) == null
                ? Collections.emptyList()  // case: this is not a real relation token, but rather a placeholder relation
                : triple.relation.stream().map(token ->
                CoreNLPProtos.TokenLocation.newBuilder()
                    .setSentenceIndex(token.sentIndex())
                    .setTokenIndex(token.index() - 1)
                    .build())
                .collect(Collectors.toList()))
        .addAllObjectTokens(triple.object.stream().map(token ->
            CoreNLPProtos.TokenLocation.newBuilder()
                .setSentenceIndex(token.sentIndex())
                .setTokenIndex(token.index() - 1)
                .build())
            .collect(Collectors.toList()));
    Optional<SemanticGraph> treeOptional = triple.asDependencyTree();
    treeOptional.ifPresent(semanticGraph -> builder.setTree(toProto(semanticGraph)));
    return builder.build();
  }

  /**
   * Serialize a Map (from Strings to Strings) to a proto.
   *
   * @param map The map to serialize.
   *
   * @return A proto representation of the map.
   */
  public static CoreNLPProtos.MapStringString toMapStringStringProto(Map<String,String> map) {
    CoreNLPProtos.MapStringString.Builder proto = CoreNLPProtos.MapStringString.newBuilder();
    for (Map.Entry<String, String> entry : map.entrySet()) {
      proto.addKey(entry.getKey());
      proto.addValue(entry.getValue());
    }
    return proto.build();
  }


  /**
   * Serialize a Map (from Integers to Strings) to a proto.
   *
   * @param map The map to serialize.
   *
   * @return A proto representation of the map.
   */
  @SuppressWarnings("unused")
  public static CoreNLPProtos.MapIntString toMapIntStringProto(Map<Integer,String> map) {
      CoreNLPProtos.MapIntString.Builder proto = CoreNLPProtos.MapIntString.newBuilder();
      for (Map.Entry<Integer, String> entry : map.entrySet()) {
          proto.addKey(entry.getKey());
          proto.addValue(entry.getValue());
      }
      return proto.build();
  }


  /**
   * Convert a quote object to a protocol buffer.
   */
  public CoreNLPProtos.Quote toProtoQuote(CoreMap quote) {
    CoreNLPProtos.Quote.Builder builder = CoreNLPProtos.Quote.newBuilder();
    if (quote.get(TextAnnotation.class) != null) { builder.setText(quote.get(TextAnnotation.class)); }
    if (quote.get(DocIDAnnotation.class) != null) { builder.setDocid(quote.get(DocIDAnnotation.class)); }
    if (quote.get(CharacterOffsetBeginAnnotation.class) != null) { builder.setBegin(quote.get(CharacterOffsetBeginAnnotation.class)); }
    if (quote.get(CharacterOffsetEndAnnotation.class) != null) { builder.setEnd(quote.get(CharacterOffsetEndAnnotation.class)); }
    if (quote.get(SentenceBeginAnnotation.class) != null) { builder.setSentenceBegin(quote.get(SentenceBeginAnnotation.class)); }
    if (quote.get(SentenceEndAnnotation.class) != null) { builder.setSentenceEnd(quote.get(SentenceEndAnnotation.class)); }
    if (quote.get(TokenBeginAnnotation.class) != null) { builder.setTokenBegin(quote.get(TokenBeginAnnotation.class)); }
    if (quote.get(TokenEndAnnotation.class) != null) { builder.setTokenEnd(quote.get(TokenEndAnnotation.class)); }
    if (quote.get(QuotationIndexAnnotation.class) != null) { builder.setIndex(quote.get(QuotationIndexAnnotation.class)); }
    if (quote.get(AuthorAnnotation.class) != null) { builder.setAuthor(quote.get(AuthorAnnotation.class)); }
    // quote attribution info
    if (quote.get(EnhancedPlusPlusDependenciesAnnotation.class) !=  null) {
      builder.setAttributionDependencyGraph(toProto(quote.get(EnhancedPlusPlusDependenciesAnnotation.class)));
    }
    if (quote.get(QuoteAttributionAnnotator.MentionAnnotation.class) != null) {
      builder.setMention(quote.get(QuoteAttributionAnnotator.MentionAnnotation.class));
    }
    if (quote.get(QuoteAttributionAnnotator.MentionBeginAnnotation.class) != null) {
      builder.setMentionBegin(quote.get(QuoteAttributionAnnotator.MentionBeginAnnotation.class));
    }
    if (quote.get(QuoteAttributionAnnotator.MentionEndAnnotation.class) != null) {
      builder.setMentionEnd(quote.get(QuoteAttributionAnnotator.MentionEndAnnotation.class));
    }
    if (quote.get(QuoteAttributionAnnotator.MentionTypeAnnotation.class) != null) {
      builder.setMentionType(quote.get(QuoteAttributionAnnotator.MentionTypeAnnotation.class));
    }
    if (quote.get(QuoteAttributionAnnotator.MentionSieveAnnotation.class) != null) {
      builder.setMentionSieve(quote.get(QuoteAttributionAnnotator.MentionSieveAnnotation.class));
    }
    if (quote.get(QuoteAttributionAnnotator.SpeakerAnnotation.class) != null) {
      builder.setSpeaker(quote.get(QuoteAttributionAnnotator.SpeakerAnnotation.class));
    }
    if (quote.get(QuoteAttributionAnnotator.SpeakerSieveAnnotation.class) != null) {
      builder.setSpeakerSieve(quote.get(QuoteAttributionAnnotator.SpeakerSieveAnnotation.class));
    }
    if (quote.get(QuoteAttributionAnnotator.CanonicalMentionAnnotation.class) != null) {
      builder.setCanonicalMention(quote.get(QuoteAttributionAnnotator.CanonicalMentionAnnotation.class));
    }
    if (quote.get(QuoteAttributionAnnotator.CanonicalMentionBeginAnnotation.class) != null) {
      builder.setCanonicalMentionBegin(quote.get(QuoteAttributionAnnotator.CanonicalMentionBeginAnnotation.class));
    }
    if (quote.get(QuoteAttributionAnnotator.CanonicalMentionEndAnnotation.class) != null) {
      builder.setCanonicalMentionEnd(quote.get(QuoteAttributionAnnotator.CanonicalMentionEndAnnotation.class));
    }

    return builder.build();
  }

  /**
   * Convert a mention object to a protocol buffer.
   */
  public CoreNLPProtos.NERMention toProtoMention(CoreMap mention) {
    CoreNLPProtos.NERMention.Builder builder = CoreNLPProtos.NERMention.newBuilder();
    if (mention.get(SentenceIndexAnnotation.class) != null) { builder.setSentenceIndex(mention.get(SentenceIndexAnnotation.class)); }
    if (mention.get(TokenBeginAnnotation.class) != null) { builder.setTokenStartInSentenceInclusive(mention.get(TokenBeginAnnotation.class)); }
    if (mention.get(TokenEndAnnotation.class) != null) { builder.setTokenEndInSentenceExclusive(mention.get(TokenEndAnnotation.class)); }
    if (mention.get(NamedEntityTagAnnotation.class) != null) { builder.setNer(mention.get(NamedEntityTagAnnotation.class)); }
    if (mention.get(NormalizedNamedEntityTagAnnotation.class) != null) { builder.setNormalizedNER(mention.get(NormalizedNamedEntityTagAnnotation.class)); }
    if (mention.get(EntityTypeAnnotation.class) != null) { builder.setEntityType(mention.get(EntityTypeAnnotation.class)); }
    if (mention.get(TimexAnnotation.class) != null) { builder.setTimex(toProto(mention.get(TimexAnnotation.class))); }
    if (mention.get(WikipediaEntityAnnotation.class) != null) { builder.setWikipediaEntity(mention.get(WikipediaEntityAnnotation.class)); }
    if (mention.get(CoreAnnotations.GenderAnnotation.class) != null) { builder.setGender(mention.get(CoreAnnotations.GenderAnnotation.class)); }
    if (mention.get(CoreAnnotations.EntityMentionIndexAnnotation.class) != null) { builder.setEntityMentionIndex(mention.get(CoreAnnotations.EntityMentionIndexAnnotation.class)); }
    if (mention.get(CoreAnnotations.CanonicalEntityMentionIndexAnnotation.class) != null) { builder.setCanonicalEntityMentionIndex(mention.get(CoreAnnotations.CanonicalEntityMentionIndexAnnotation.class)); }
    if (mention.get(CoreAnnotations.TextAnnotation.class) != null) { builder.setEntityMentionText(mention.get(CoreAnnotations.TextAnnotation.class)); }
    return builder.build();
  }

  /**
   * Create a CoreLabel from its serialized counterpart.
   * Note that this is, by itself, a lossy operation. Fields like the docid (sentence index, etc.) are only known
   * from the enclosing document, and are not tracked in the protobuf.
   * @param proto The serialized protobuf to read the CoreLabel from.
   * @return A CoreLabel, missing the fields that are not stored in the CoreLabel protobuf.
   */
  public CoreLabel fromProto(CoreNLPProtos.Token proto) {
    if (Thread.interrupted()) {
      throw new RuntimeInterruptedException();
    }
    CoreLabel word = new CoreLabel();
    // Required fields
    word.setWord(proto.getWord());
    // Optional fields
    if (proto.hasPos()) { word.setTag(proto.getPos()); }
    // the index will be clobbered if the token was serialized as part of a document
    // this is only going to be useful in cases where the token is serialized in some other manner
    if (proto.hasIndex()) { word.setIndex(proto.getIndex()); }
    if (proto.hasEmptyIndex()) { word.setEmptyIndex(proto.getEmptyIndex()); }
    if (proto.hasValue()) { word.setValue(proto.getValue()); }
    if (proto.hasCategory()) { word.setCategory(proto.getCategory()); }
    if (proto.hasBefore()) { word.setBefore(proto.getBefore()); }
    if (proto.hasAfter()) { word.setAfter(proto.getAfter()); }
    if (proto.hasOriginalText()) { word.setOriginalText(proto.getOriginalText()); }
    if (proto.hasNer()) { word.setNER(proto.getNer()); }
    if (proto.hasCoarseNER()) { word.set(CoreAnnotations.CoarseNamedEntityTagAnnotation.class, proto.getCoarseNER()); }
    if (proto.hasFineGrainedNER()) { word.set(CoreAnnotations.FineGrainedNamedEntityTagAnnotation.class, proto.getFineGrainedNER()); }
    if (proto.hasLemma()) { word.setLemma(proto.getLemma()); }
    if (proto.hasBeginChar()) { word.setBeginPosition(proto.getBeginChar()); }
    if (proto.hasEndChar()) { word.setEndPosition(proto.getEndChar()); }
    if (proto.hasCodepointOffsetBegin()) { word.set(CoreAnnotations.CodepointOffsetBeginAnnotation.class, proto.getCodepointOffsetBegin()); }
    if (proto.hasCodepointOffsetEnd()) { word.set(CoreAnnotations.CodepointOffsetEndAnnotation.class, proto.getCodepointOffsetEnd()); }
    if (proto.hasSpeaker()) { word.set(SpeakerAnnotation.class, proto.getSpeaker()); }
    if (proto.hasSpeakerType()) { word.set(SpeakerTypeAnnotation.class, proto.getSpeakerType()); }
    if (proto.hasUtterance()) { word.set(UtteranceAnnotation.class, proto.getUtterance()); }
    if (proto.hasBeginIndex()) { word.set(BeginIndexAnnotation.class, proto.getBeginIndex()); }
    if (proto.hasEndIndex()) { word.set(EndIndexAnnotation.class, proto.getEndIndex()); }
    if (proto.hasTokenBeginIndex()) { word.set(TokenBeginAnnotation.class, proto.getTokenBeginIndex()); }
    if (proto.hasTokenEndIndex()) { word.set(TokenEndAnnotation.class, proto.getTokenEndIndex()); }
    if (proto.hasNormalizedNER()) { word.set(NormalizedNamedEntityTagAnnotation.class, proto.getNormalizedNER()); }
    if (proto.hasTimexValue()) { word.set(TimexAnnotation.class, fromProto(proto.getTimexValue())); }
    if (proto.hasHasXmlContext() && proto.getHasXmlContext()) { word.set(XmlContextAnnotation.class, proto.getXmlContextList()); }
    if (proto.hasCorefClusterID()) { word.set(CorefClusterIdAnnotation.class, proto.getCorefClusterID()); }
    if (proto.hasAnswer()) { word.set(AnswerAnnotation.class, proto.getAnswer()); }
    if (proto.hasOperator()) { word.set(NaturalLogicAnnotations.OperatorAnnotation.class, fromProto(proto.getOperator())); }
    if (proto.hasPolarity()) { word.set(NaturalLogicAnnotations.PolarityAnnotation.class, fromProto(proto.getPolarity())); }
    if (proto.hasPolarityDir()) { word.set(NaturalLogicAnnotations.PolarityDirectionAnnotation.class, proto.getPolarityDir()); }
    if (proto.hasSpan()) { word.set(SpanAnnotation.class, new IntPair(proto.getSpan().getBegin(), proto.getSpan().getEnd())); }
    if (proto.hasSentiment()) { word.set(SentimentCoreAnnotations.SentimentClass.class, proto.getSentiment()); }
    if (proto.hasQuotationIndex()) { word.set(QuotationIndexAnnotation.class, proto.getQuotationIndex()); }
    if (proto.hasConllUFeatures()) { word.set(CoNLLUFeats.class, new CoNLLUFeatures(fromProto(proto.getConllUFeatures()))); }
    if (proto.hasConllUMisc()) { word.set(CoNLLUMisc.class, proto.getConllUMisc()); }
    if (proto.hasCoarseTag()) { word.set(CoarseTagAnnotation.class, proto.getCoarseTag()); }
    if (proto.hasConllUTokenSpan()) { word.set(CoNLLUTokenSpanAnnotation.class, new IntPair(proto.getConllUTokenSpan().getBegin(), proto.getSpan().getEnd())); }
    if (proto.hasConllUSecondaryDeps()) { word.set(CoNLLUSecondaryDepsAnnotation.class, fromProto(proto.getConllUSecondaryDeps())); }
    if (proto.hasWikipediaEntity()) { word.set(WikipediaEntityAnnotation.class, proto.getWikipediaEntity()); }
    if (proto.hasIsNewline()) { word.set(IsNewlineAnnotation.class, proto.getIsNewline()); }
    // Chinese char info
    if (proto.hasChineseChar()) { word.set(ChineseCharAnnotation.class, proto.getChineseChar()) ; }
    if (proto.hasChineseSeg()) { word.set(ChineseSegAnnotation.class, proto.getChineseSeg()) ; }
    if (proto.hasChineseXMLChar()) { word.set(SegmenterCoreAnnotations.XMLCharAnnotation.class, proto.getChineseXMLChar()); }

    // Arabic char info
    if (proto.hasArabicSeg()) { word.set(ArabicSegAnnotation.class, proto.getArabicSeg()) ; }

    // Non-default annotators
    if (proto.hasGender()) { word.set(CoreAnnotations.GenderAnnotation.class, proto.getGender()); }
    if (proto.hasTrueCase()) { word.set(TrueCaseAnnotation.class, proto.getTrueCase()); }
    if (proto.hasTrueCaseText()) { word.set(TrueCaseTextAnnotation.class, proto.getTrueCaseText()); }

    // section stuff
    // handle section start info
    if (proto.hasSectionName() || proto.hasSectionAuthor() || proto.hasSectionDate()) {
      CoreMap sectionAnnotations = new ArrayCoreMap();
      if (proto.hasSectionName())
        sectionAnnotations.set(SectionAnnotation.class, proto.getSectionName());
      if (proto.hasSectionDate())
        sectionAnnotations.set(SectionDateAnnotation.class, proto.getSectionDate());
      if (proto.hasSectionAuthor())
        sectionAnnotations.set(AuthorAnnotation.class, proto.getSectionAuthor());
      word.set(SectionStartAnnotation.class, sectionAnnotations);
    }
    // handle section end info
    if (proto.hasSectionEndLabel()) {
      word.set(SectionEndAnnotation.class, proto.getSectionEndLabel());
    }

    // get parents for French tokens
    if (proto.hasParent()) {
      word.set(ParentAnnotation.class, proto.getParent());
    }

    // mention info
    if (proto.hasEntityMentionIndex()) {
      word.set(EntityMentionIndexAnnotation.class, proto.getEntityMentionIndex());
    }

    // ner label probs
    if (proto.getNerLabelProbsList().size() != 0) {
      HashMap<String,Double> nerLabelProbs = new HashMap<>();
      // check that the list isn't just the string "empty" indicating empty list
      if (!proto.getNerLabelProbsList().get(0).equals("empty")) {
        for (String nerLabelProb : proto.getNerLabelProbsList()) {
          String[] labelAndProb = nerLabelProb.split("=");
          Double labelProbDouble = Double.parseDouble(labelAndProb[1]);
          nerLabelProbs.put(labelAndProb[0], labelProbDouble);
        }
      }
      word.set(NamedEntityTagProbsAnnotation.class, nerLabelProbs);
    }

    // MWT info
    if (proto.hasMwtText()) {
      word.set(MWTTokenTextAnnotation.class, proto.getMwtText());
    }
    if (proto.hasIsMWT()) {
      word.set(IsMultiWordTokenAnnotation.class, proto.getIsMWT());
    }
    if (proto.hasIsFirstMWT()) {
      word.set(IsFirstWordOfMWTAnnotation.class, proto.getIsFirstMWT());
    }
    if (proto.hasMwtMisc()) {
      word.set(MWTTokenMiscAnnotation.class, proto.getMwtMisc());
    }

    // Numeric info
    if (proto.hasNumericValue()) {
      word.set(NumericValueAnnotation.class, proto.getNumericValue());
    }
    if (proto.hasNumericType()) {
      word.set(NumericTypeAnnotation.class, proto.getNumericType());
    }
    if (proto.hasNumericCompositeValue()) {
      word.set(NumericCompositeValueAnnotation.class, proto.getNumericCompositeValue());
    }
    if (proto.hasNumericCompositeType()) {
      word.set(NumericCompositeTypeAnnotation.class, proto.getNumericCompositeType());
    }

    // Return
    return word;
  }

  /**
   * Create a CoreMap representing a sentence from this protocol buffer.
   * This should not be used if you are reading a whole document, as it populates the tokens independent of the
   * document tokens, which is not the behavior an {@link edu.stanford.nlp.pipeline.Annotation} expects.
   *
   * @param proto The protocol buffer to read from.
   * @return A CoreMap representing the sentence.
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  public CoreMap fromProto(CoreNLPProtos.Sentence proto) {
    if (Thread.interrupted()) {
      throw new RuntimeInterruptedException();
    }
    CoreMap lossySentence = fromProtoNoTokens(proto);
    // Add tokens -- missing by default as they're populated as sublists of the document tokens
    List<CoreLabel> tokens = proto.getTokenList().stream().map(this::fromProto).collect(Collectors.toList());
    lossySentence.set(TokensAnnotation.class, tokens);
    setSentenceTokenAnnotations(lossySentence, proto, tokens, null);
    // Add entailed sentences
    if (proto.getEntailedSentenceCount() > 0) {
      List<SentenceFragment> entailedSentences = proto.getEntailedSentenceList().stream().map(frag -> fromProto(frag, lossySentence.get(CollapsedDependenciesAnnotation.class))).collect(Collectors.toList());
      lossySentence.set(NaturalLogicAnnotations.EntailedSentencesAnnotation.class, entailedSentences);
    }
    // Add entailed clauses
    if (proto.getEntailedClauseCount() > 0) {
      List<SentenceFragment> entailedClauses = proto.getEntailedClauseList().stream().map(frag -> fromProto(frag, lossySentence.get(CollapsedDependenciesAnnotation.class))).collect(Collectors.toList());
      lossySentence.set(NaturalLogicAnnotations.EntailedClausesAnnotation.class, entailedClauses);
    }
    // Add relation triples
    if (proto.getOpenieTripleCount() > 0) {
      throw new IllegalStateException("Cannot deserialize OpenIE triples with this method!");
    }
    if (proto.getKbpTripleCount() > 0) {
      throw new IllegalStateException("Cannot deserialize KBP triples with this method!");
    }
    // Add chinese characters
    if (proto.getCharacterCount() > 0) {
      List<CoreLabel> sentenceCharacters = proto.getCharacterList().stream().map(this::fromProto).collect(Collectors.toList());
      lossySentence.set(SegmenterCoreAnnotations.CharactersAnnotation.class, sentenceCharacters);
    }
    // Add text -- missing by default as it's populated from the Document
    lossySentence.set(TextAnnotation.class, recoverOriginalText(tokens, proto));

    // add section info
    if (proto.hasSectionName())
      lossySentence.set(SectionAnnotation.class, proto.getSectionName());
    if (proto.hasSectionDate())
      lossySentence.set(SectionDateAnnotation.class, proto.getSectionDate());
    if (proto.hasSectionAuthor())
      lossySentence.set(AuthorAnnotation.class, proto.getSectionAuthor());
    if (proto.hasSectionIndex())
      lossySentence.set(SectionIndexAnnotation.class, proto.getSectionIndex());

    // add quote info
    if (proto.hasChapterIndex())
      lossySentence.set(ChapterAnnotator.ChapterAnnotation.class, proto.getChapterIndex());
    if (proto.hasParagraphIndex())
      lossySentence.set(ParagraphIndexAnnotation.class, proto.getParagraphIndex());

    // speaker info
    if (proto.hasSpeaker()) { lossySentence.set(SpeakerAnnotation.class, proto.getSpeaker()); }
    if (proto.hasSpeakerType()) { lossySentence.set(SpeakerTypeAnnotation.class, proto.getSpeakerType()); }

    // Return
    return lossySentence;
  }

  /**
   * Create a CoreMap representing a sentence from this protocol buffer.
   * Note that the sentence is very lossy -- most glaringly, the tokens are missing, awaiting a document
   * to be filled in from.
   * @param proto The serialized protobuf to read the sentence from.
   * @return A CoreMap, representing a sentence as stored in the protocol buffer (and therefore missing some fields)
   */
  protected CoreMap fromProtoNoTokens(CoreNLPProtos.Sentence proto) {
    if (Thread.interrupted()) {
      throw new RuntimeInterruptedException();
    }
    CoreMap sentence = new ArrayCoreMap();
    // Required fields
    sentence.set(TokenBeginAnnotation.class, proto.getTokenOffsetBegin());
    sentence.set(TokenEndAnnotation.class, proto.getTokenOffsetEnd());
    // Optional fields
    if (proto.hasSentenceIndex()) { sentence.set(SentenceIndexAnnotation.class, proto.getSentenceIndex()); }
    if (proto.hasCharacterOffsetBegin()) { sentence.set(CharacterOffsetBeginAnnotation.class, proto.getCharacterOffsetBegin()); }
    if (proto.hasCharacterOffsetEnd()) { sentence.set(CharacterOffsetEndAnnotation.class, proto.getCharacterOffsetEnd()); }
    if (proto.hasAnnotatedParseTree()) { sentence.set(SentimentCoreAnnotations.SentimentAnnotatedTree.class, fromProto(proto.getAnnotatedParseTree())); }
    if (proto.hasSentiment()) { sentence.set(SentimentCoreAnnotations.SentimentClass.class, proto.getSentiment()); }
    // Non-default fields
    if (proto.hasHasRelationAnnotations() && proto.getHasRelationAnnotations()) {
      // set entities
      List<EntityMention> entities = proto.getEntityList().stream().map(entity -> fromProto(entity, sentence)).collect(Collectors.toList());
      sentence.set(EntityMentionsAnnotation.class, entities);
      // set relations
      List<RelationMention> relations = proto.getRelationList().stream().map(relation -> fromProto(relation, sentence)).collect(Collectors.toList());
      sentence.set(RelationMentionsAnnotation.class, relations);
    }

    // add entity mentions for this sentence
    //List<CoreMap> mentions = proto.getMentionsList().stream().map(this::fromProto).collect(Collectors.toList());
    //sentence.set(CoreAnnotations.MentionsAnnotation.class, mentions);

    // if there are mentions for this sentence, add them to the annotation
    loadSentenceMentions(proto, sentence);

    // add section info
    if (proto.hasSectionName())
      sentence.set(SectionAnnotation.class, proto.getSectionName());
    if (proto.hasSectionDate())
      sentence.set(SectionDateAnnotation.class, proto.getSectionDate());
    if (proto.hasSectionAuthor())
      sentence.set(AuthorAnnotation.class, proto.getSectionAuthor());
    if (proto.hasSectionIndex())
      sentence.set(SectionIndexAnnotation.class, proto.getSectionIndex());

    // add quoted info
    if (proto.hasSectionQuoted())
      sentence.set(QuotedAnnotation.class, proto.getSectionQuoted());

    // add quote info
    if (proto.hasChapterIndex())
      sentence.set(ChapterAnnotator.ChapterAnnotation.class, proto.getChapterIndex());
    if (proto.hasParagraphIndex())
      sentence.set(ParagraphIndexAnnotation.class, proto.getParagraphIndex());

    // speaker info
    if (proto.hasSpeaker()) { sentence.set(SpeakerAnnotation.class, proto.getSpeaker()); }
    if (proto.hasSpeakerType()) { sentence.set(SpeakerTypeAnnotation.class, proto.getSpeakerType()); }

    // Return
    return sentence;
  }

  /** On a partially finished deserialized sentence, set some annotations which should reuse the same token objects as the parent sentence */
  protected void setSentenceTokenAnnotations(CoreMap sentence, CoreNLPProtos.Sentence protoSentence, List<CoreLabel> sentenceTokens, String docid) {
    // build the tree annotations, attaching the expected CoreLabels to the leaves
    if (protoSentence.hasParseTree()) { sentence.set(TreeAnnotation.class, fromProto(protoSentence.getParseTree(), sentenceTokens)); }
    if (protoSentence.hasBinarizedParseTree()) { sentence.set(BinarizedTreeAnnotation.class, fromProto(protoSentence.getBinarizedParseTree(), sentenceTokens)); }
    if (protoSentence.getKBestParseTreesCount() > 0) {
      List<Tree> trees = protoSentence.getKBestParseTreesList().stream().map(x -> (fromProto(x, sentenceTokens))).collect(Collectors.toCollection(LinkedList::new));
      sentence.set(KBestTreesAnnotation.class, trees);
    }

    // Set dependency graphs
    if (protoSentence.hasBasicDependencies()) {
      sentence.set(BasicDependenciesAnnotation.class, fromProto(protoSentence.getBasicDependencies(), sentenceTokens, docid));
    }
    if (protoSentence.hasCollapsedDependencies()) {
      sentence.set(CollapsedDependenciesAnnotation.class, fromProto(protoSentence.getCollapsedDependencies(), sentenceTokens, docid));
    }
    if (protoSentence.hasCollapsedCCProcessedDependencies()) {
      sentence.set(CollapsedCCProcessedDependenciesAnnotation.class, fromProto(protoSentence.getCollapsedCCProcessedDependencies(), sentenceTokens, docid));
    }
    if (protoSentence.hasAlternativeDependencies()) {
      sentence.set(AlternativeDependenciesAnnotation.class, fromProto(protoSentence.getAlternativeDependencies(), sentenceTokens, docid));
    }
    if (protoSentence.hasEnhancedDependencies()) {
      sentence.set(EnhancedDependenciesAnnotation.class, fromProto(protoSentence.getEnhancedDependencies(), sentenceTokens, docid));
    }
    if (protoSentence.hasEnhancedPlusPlusDependencies()) {
      sentence.set(EnhancedPlusPlusDependenciesAnnotation.class, fromProto(protoSentence.getEnhancedPlusPlusDependencies(), sentenceTokens, docid));
    }
  }

  protected void loadSentenceMentions(CoreNLPProtos.Sentence proto, CoreMap sentence) {
    // add all Mentions for this sentence
    if (proto.getHasCorefMentionsAnnotation()) {
      sentence.set(CorefMentionsAnnotation.class, new ArrayList<>());
    }
    if (proto.getMentionsForCorefList().size() != 0) {
      HashMap<Integer, Mention> idToMention = new HashMap<>();
      List<Mention> sentenceMentions = sentence.get(CorefMentionsAnnotation.class);
      // initial set up of all mentions
      for (CoreNLPProtos.Mention protoMention : proto.getMentionsForCorefList()) {
        Mention m = fromProtoNoTokens(protoMention);
        sentenceMentions.add(m);
        idToMention.put(m.mentionID, m);
      }
      // populate sets of Mentions for each Mention
      for (CoreNLPProtos.Mention protoMention : proto.getMentionsForCorefList()) {
        Mention m = idToMention.get(protoMention.getMentionID());
        if (protoMention.getAppositionsList().size() != 0) {
          m.appositions = new HashSet<>();
          m.appositions.addAll(protoMention.getAppositionsList().stream()
              .map(idToMention::get)
              .collect(Collectors.toList()));
        }
        if (protoMention.getPredicateNominativesList().size() != 0) {
          m.predicateNominatives = new HashSet<>();
          m.predicateNominatives.addAll(protoMention.getPredicateNominativesList().stream()
              .map(idToMention::get)
              .collect(Collectors.toList()));
        }
        if (protoMention.getRelativePronounsList().size() != 0) {
          m.relativePronouns = new HashSet<>();
          m.relativePronouns.addAll(protoMention.getRelativePronounsList().stream()
              .map(idToMention::get)
              .collect(Collectors.toList()));
        }
        if (protoMention.getListMembersList().size() != 0) {
          m.listMembers = new HashSet<>();
          m.listMembers.addAll(protoMention.getListMembersList().stream()
              .map(idToMention::get)
              .collect(Collectors.toList()));
        }
        if (protoMention.getBelongToListsList().size() != 0) {
          m.belongToLists = new HashSet<>();
          m.belongToLists.addAll(protoMention.getBelongToListsList().stream()
              .map(idToMention::get)
              .collect(Collectors.toList()));
        }
      }
    }
  }

  /**
   * Returns a complete document, intended to mimic a document passes as input to
   * {@link ProtobufAnnotationSerializer#toProto(Annotation)} as closely as possible.
   * That is, most common fields are serialized, but there is not guarantee that custom additions
   * will be saved and retrieved.
   *
   * @param proto The protocol buffer to read the document from.
   * @return An Annotation corresponding to the read protobuf.
   */
  @SuppressWarnings("deprecation")
  public Annotation fromProto(CoreNLPProtos.Document proto) {
    if (Thread.interrupted()) {
      throw new RuntimeInterruptedException();
    }
    // Set text
    Annotation ann = new Annotation(proto.getText());

    // if there are characters, add characters
    if (proto.getCharacterCount() > 0) {
      List<CoreLabel> docChars = new ArrayList<>();
      for (CoreNLPProtos.Token c : proto.getCharacterList()) {
        docChars.add(fromProto(c));
      }
      ann.set(SegmenterCoreAnnotations.CharactersAnnotation.class, docChars);
    }

    boolean hasCorefInfo = proto.getHasCorefAnnotation();

    // Add tokens
    List<CoreLabel> tokens = new ArrayList<>();
    if (proto.getSentenceCount() > 0) {
      // Populate the tokens from the sentence
      for (CoreNLPProtos.Sentence sentence : proto.getSentenceList()) {
        // It's conceivable that the sentences are not contiguous -- pad this with nulls
        while (sentence.hasTokenOffsetBegin() && tokens.size() < sentence.getTokenOffsetBegin()) {
          tokens.add(null);
        }
        // Read the sentence
        for (CoreNLPProtos.Token token : sentence.getTokenList()) {
          // make CoreLabel
          CoreLabel coreLabel = fromProto(token);
          // if there is coref info, set coref mention indexes info for this token
          if (hasCorefInfo) {
            coreLabel.set(CorefMentionIndexesAnnotation.class, new HashSet<Integer>());
            for (Integer corefMentionIndex : token.getCorefMentionIndexList()) {
              coreLabel.get(CorefMentionIndexesAnnotation.class).add(corefMentionIndex);
            }
          }
          // Set docid
          if (proto.hasDocID()) { coreLabel.setDocID(proto.getDocID()); }
          if (token.hasTokenBeginIndex() && token.hasTokenEndIndex()) {
            // This is usually true, if enough annotators are defined
            while (tokens.size() < sentence.getTokenOffsetEnd()) {
              tokens.add(null);
            }
            for (int i = token.getTokenBeginIndex(); i < token.getTokenEndIndex(); ++i) {
              tokens.set(token.getTokenBeginIndex(), coreLabel);
            }
          } else {
            // Assume this token spans a single token, and just add it to the tokens list
            tokens.add(coreLabel);
          }
        }
      }
    } else if (proto.getSentencelessTokenCount() > 0) {
      // Eek -- no sentences. Try to recover tokens directly
      if (proto.getSentencelessTokenCount() > 0) {
        for (CoreNLPProtos.Token token : proto.getSentencelessTokenList()) {
          CoreLabel coreLabel = fromProto(token);
          // Set docid
          if (proto.hasDocID()) { coreLabel.setDocID(proto.getDocID()); }
          tokens.add(coreLabel);
        }
      }
    }
    if (!tokens.isEmpty()) { ann.set(TokensAnnotation.class, tokens); }

    // add entity mentions
    if (proto.getHasEntityMentionsAnnotation()) {
      ann.set(CoreAnnotations.MentionsAnnotation.class, new ArrayList<>());
    }

    // Add sentences
    List<CoreMap> sentences = new ArrayList<>(proto.getSentenceCount());
    for (int sentIndex = 0; sentIndex < proto.getSentenceCount(); ++sentIndex) {
      CoreNLPProtos.Sentence sentence = proto.getSentence(sentIndex);
      CoreMap map = fromProtoNoTokens(sentence);
      if (!tokens.isEmpty() && sentence.hasTokenOffsetBegin() && sentence.hasTokenOffsetEnd() &&
          map.get(TokensAnnotation.class) == null) {
        // Set tokens for sentence
        int tokenBegin = sentence.getTokenOffsetBegin();
        int tokenEnd = sentence.getTokenOffsetEnd();
        assert tokenBegin <= tokens.size() && tokenBegin <= tokenEnd;
        assert tokenEnd <= tokens.size();
        map.set(TokensAnnotation.class, tokens.subList(tokenBegin, tokenEnd));
        // Set sentence index + token index + paragraph index
        for (int i = tokenBegin; i < tokenEnd; ++i) {
          CoreLabel token = tokens.get(i);
          if (token != null) {
            token.setSentIndex(sentIndex);
            token.setIndex(i - sentence.getTokenOffsetBegin() + 1);
            if (sentence.hasParagraph()) {
              token.set(ParagraphAnnotation.class, sentence.getParagraph());
            }
          }
        }
        // Set text
        int characterBegin = sentence.getCharacterOffsetBegin();
        int characterEnd = sentence.getCharacterOffsetEnd();
        if (characterEnd <= proto.getText().length()) {
          // The usual case -- get the text from the document text
          map.set(TextAnnotation.class, proto.getText().substring(characterBegin, characterEnd));
        } else {
          // The document text is wrong -- guess the text from the tokens
          map.set(TextAnnotation.class, recoverOriginalText(tokens.subList(tokenBegin, tokenEnd), sentence));
        }
        // add entity mentions for this sentence
        List<CoreMap> mentions = sentence.getMentionsList().stream().map(this::fromProto).collect(Collectors.toList());
        // add tokens to each entity mention
        for (CoreMap entityMention : mentions) {
          List<CoreLabel> entityMentionTokens = new ArrayList<>();
          for (int tokenIndex = entityMention.get(TokenBeginAnnotation.class) ;
               tokenIndex < entityMention.get(TokenEndAnnotation.class) ; tokenIndex++ ) {
            entityMentionTokens.add(tokens.get(tokenIndex));
          }
          int emCharOffsetBegin = entityMentionTokens.get(0).get(CharacterOffsetBeginAnnotation.class);
          int emCharOffsetEnd =
              entityMentionTokens.get(entityMentionTokens.size()-1).get(CharacterOffsetEndAnnotation.class);
          // set character offsets
          entityMention.set(CharacterOffsetBeginAnnotation.class, emCharOffsetBegin);
          entityMention.set(CharacterOffsetEndAnnotation.class, emCharOffsetEnd);
          entityMention.set(CoreAnnotations.TokensAnnotation.class, entityMentionTokens);
          // set entity mention label prob vals
          HashMap<String,Double> nerLabelConfidences =
              EntityMentionsAnnotator.determineEntityMentionConfidences(entityMention);
          entityMention.set(CoreAnnotations.NamedEntityTagProbsAnnotation.class, nerLabelConfidences);
        }
        if (sentence.getHasEntityMentionsAnnotation()) {
          map.set(CoreAnnotations.MentionsAnnotation.class, mentions);
        }
        // add to document level list of entity mentions
        List<CoreMap> mentionsOnAnnotation = ann.get(MentionsAnnotation.class);
        if (mentionsOnAnnotation != null) {
          for (CoreMap sentenceEM : mentions) {
            ann.get(CoreAnnotations.MentionsAnnotation.class).add(sentenceEM);
          }
        }
      }
      // End iteration
      sentences.add(map);
    }
    if (!sentences.isEmpty()) { ann.set(SentencesAnnotation.class, sentences); }

    // Set DocID
    String docid = null;
    if (proto.hasDocID()) {
      docid = proto.getDocID();
      ann.set(DocIDAnnotation.class, docid);
    }
    // Set reference time
    if (proto.hasDocDate()) {
      ann.set(DocDateAnnotation.class, proto.getDocDate());
    }
    if (proto.hasCalendar()) {
      GregorianCalendar calendar = new GregorianCalendar();
      calendar.setTimeInMillis(proto.getCalendar());
      ann.set(CalendarAnnotation.class, calendar);
    }

    // Set coref chain
    Map<Integer, CorefChain> corefChains = new HashMap<>();
    for (CoreNLPProtos.CorefChain chainProto : proto.getCorefChainList()) {
      CorefChain chain = fromProto(chainProto, ann);
      corefChains.put(chain.getChainID(), chain);
    }
    if (proto.getHasCorefAnnotation()) { ann.set(CorefChainAnnotation.class, corefChains); }

    // Set document coref mentions list ; this gets populated when sentences build CorefMentions below
    if (proto.getHasCorefMentionAnnotation()) {
      ann.set(CorefMentionsAnnotation.class, new ArrayList<>());
    }

    // hashes to access Mentions , later in this method need to add speakerInfo to Mention
    // so we need to create id -> Mention, CoreNLPProtos.Mention maps to do this, since SpeakerInfo could reference
    // any Mention in doc
    HashMap<Integer, Mention> idToMention = new HashMap<>();
    HashMap<Integer, CoreNLPProtos.Mention> idToProtoMention = new HashMap<>();

    // Set things in the sentence that need a document context.
    for (int sentenceIndex = 0; sentenceIndex < proto.getSentenceCount(); ++sentenceIndex) {
      CoreNLPProtos.Sentence sentence = proto.getSentenceList().get(sentenceIndex);
      CoreMap map = sentences.get(sentenceIndex);
      List<CoreLabel> sentenceTokens = map.get(TokensAnnotation.class);
      setSentenceTokenAnnotations(map, sentence, sentenceTokens, docid);
      // Set entailed sentences
      if (sentence.getEntailedSentenceCount() > 0) {
        Set<SentenceFragment> entailedSentences = sentence.getEntailedSentenceList().stream().map(frag -> fromProto(frag, map.get(EnhancedPlusPlusDependenciesAnnotation.class))).collect(Collectors.toSet());
        map.set(NaturalLogicAnnotations.EntailedSentencesAnnotation.class, entailedSentences);
      }
      if (sentence.getEntailedClauseCount() > 0) {
        Set<SentenceFragment> entailedClauses = sentence.getEntailedClauseList().stream().map(frag -> fromProto(frag, map.get(CollapsedDependenciesAnnotation.class))).collect(Collectors.toSet());
        map.set(NaturalLogicAnnotations.EntailedClausesAnnotation.class, entailedClauses);
      }
      // Set relation triples
      ArrayList<RelationTriple> triples = new ArrayList<>();
      if (sentence.getHasOpenieTriplesAnnotation())
        map.set(NaturalLogicAnnotations.RelationTriplesAnnotation.class, triples);
      if (sentence.getOpenieTripleCount() > 0) {
        for (CoreNLPProtos.RelationTriple triple : sentence.getOpenieTripleList()) {
          triples.add(fromProto(triple, ann, sentenceIndex));
        }
        map.set(NaturalLogicAnnotations.RelationTriplesAnnotation.class, triples);
      }
      // Set kbp relation triples
      if (sentence.getHasKBPTriplesAnnotation())
        map.set(KBPTriplesAnnotation.class, new ArrayList<>());
      if (sentence.getKbpTripleCount() > 0) {
        for (CoreNLPProtos.RelationTriple kbpTriple : sentence.getKbpTripleList()) {
          map.get(KBPTriplesAnnotation.class).add(fromProto(kbpTriple, ann, sentenceIndex));
        }
      }
      // Redo some light annotation
      if ( map.containsKey(TokensAnnotation.class) &&
          (!sentence.hasHasNumerizedTokensAnnotation() || sentence.getHasNumerizedTokensAnnotation())) {
        map.set(NumerizedTokensAnnotation.class, NumberNormalizer.findAndMergeNumbers(map));
      }
      // add the CoreLabel and IndexedWord info to each mention
      // when Mentions are serialized, just storing the index in the sentence for CoreLabels and IndexedWords
      // this is the point where the de-serialized sentence has tokens
      int mentionInt = 0;
      for (CoreNLPProtos.Mention protoMention : sentence.getMentionsForCorefList()) {
        // get the mention
        Mention mentionToUpdate = map.get(CorefMentionsAnnotation.class).get(mentionInt);
        // add to document level coref mention list
        List<Mention> mentions = ann.get(CorefMentionsAnnotation.class);
        if (mentions == null) {
          mentions = new ArrayList<>();
          ann.set(CorefMentionsAnnotation.class, mentions);
        }
        mentions.add(mentionToUpdate);
        // store these in hash for more processing later in this method
        idToMention.put(mentionToUpdate.mentionID, mentionToUpdate);
        idToProtoMention.put(mentionToUpdate.mentionID, protoMention);
        // update the values
        int headIndexedWordIndex = protoMention.getHeadIndexedWord().getTokenIndex();
        if (headIndexedWordIndex >= 0) {
          mentionToUpdate.headIndexedWord = new IndexedWord(sentenceTokens.get(protoMention.getHeadIndexedWord().getTokenIndex()));
          mentionToUpdate.headIndexedWord.setCopyCount(protoMention.getHeadIndexedWord().getCopyCount());
        }
        int dependingVerbIndex = protoMention.getDependingVerb().getTokenIndex();
        if (dependingVerbIndex >= 0) {
          mentionToUpdate.dependingVerb = new IndexedWord(sentenceTokens.get(protoMention.getDependingVerb().getTokenIndex()));
          mentionToUpdate.dependingVerb.setCopyCount(protoMention.getDependingVerb().getCopyCount());
        }
        int headWordIndex = protoMention.getHeadWord().getTokenIndex();
        if (headWordIndex >= 0) {
          mentionToUpdate.headWord = sentenceTokens.get(protoMention.getHeadWord().getTokenIndex());
        }
        mentionToUpdate.sentenceWords = new ArrayList<>();
        for (CoreNLPProtos.IndexedWord clp : protoMention.getSentenceWordsList()) {
          int ti = clp.getTokenIndex();
          mentionToUpdate.sentenceWords.add(sentenceTokens.get(ti));
        }
        mentionToUpdate.originalSpan = new ArrayList<>();
        for (CoreNLPProtos.IndexedWord clp : protoMention.getOriginalSpanList()) {
          int ti = clp.getTokenIndex();
          mentionToUpdate.originalSpan.add(sentenceTokens.get(ti));
        }
        if (protoMention.getHasBasicDependency()) {
          mentionToUpdate.basicDependency = map.get(BasicDependenciesAnnotation.class);
        }
        if (protoMention.getHasEnhancedDependency()) {
          mentionToUpdate.enhancedDependency = map.get(EnhancedDependenciesAnnotation.class);
        }
        if (protoMention.getHasContextParseTree()) {
          mentionToUpdate.contextParseTree = map.get(TreeAnnotation.class);
        }
        // move on to next mention
        mentionInt++;
      }

    }

    // set sections if this was an xmlDoc
    if (proto.hasXmlDoc() && proto.getXmlDoc()) {
      // this was an xml doc so set up a list of sections
      List<CoreMap> listOfSections = new ArrayList<>();
      ann.set(SectionsAnnotation.class, listOfSections);
      for (CoreNLPProtos.Section section : proto.getSectionsList()) {
        CoreMap sectionCoreMap = fromProto(section, ann.get(SentencesAnnotation.class));
        ann.get(SectionsAnnotation.class).add(sectionCoreMap);
      }
    }

    // Set quotes
    List<CoreMap> quotes = proto.getQuoteList().stream().map(quote -> fromProto(quote, tokens)).collect(Collectors.toList());
    if (!quotes.isEmpty()) {
      ann.set(QuotationsAnnotation.class, quotes);
      // add the tokens to the quote tokens list
      for (CoreMap quote : quotes) {
        List<CoreLabel> quoteTokens = new ArrayList<>();
        for (int quoteTokenIndex = quote.get(CoreAnnotations.TokenBeginAnnotation.class) ;
             quoteTokenIndex <= quote.get(CoreAnnotations.TokenEndAnnotation.class) ; quoteTokenIndex++) {
          quoteTokens.add(ann.get(CoreAnnotations.TokensAnnotation.class).get(quoteTokenIndex));
        }
        quote.set(CoreAnnotations.TokensAnnotation.class, quoteTokens);
      }
    }

    // Set NERmention
    //List<CoreMap> mentions = proto.getMentionsList().stream().map(this::fromProto).collect(Collectors.toList());
    //ann.set(MentionsAnnotation.class, mentions);
    // add SpeakerInfo stuff to Mentions, this requires knowing all mentions in the document
    // also add all the Set<Mention>
    for (int mentionID : idToMention.keySet()) {
      // this is the Mention message corresponding to this Mention
      Mention mentionToUpdate = idToMention.get(mentionID);
      CoreNLPProtos.Mention correspondingProtoMention = idToProtoMention.get(mentionID);
      if (!correspondingProtoMention.hasSpeakerInfo()) {
        // keep speakerInfo null for this Mention if it didn't store a speakerInfo
        // so just continue to next Mention
        continue;
      }
      // if we're here we know a speakerInfo was stored
      SpeakerInfo speakerInfo = fromProto(correspondingProtoMention.getSpeakerInfo());
      // go through all ids stored for the speakerInfo in its mentions list, and get the Mention
      // Mentions are stored by MentionID , MentionID should be set by MentionAnnotator
      // MentionID is ID in document, 0, 1, 2, etc...
      for (int speakerInfoMentionID : correspondingProtoMention.getSpeakerInfo().getMentionsList()) {
        speakerInfo.addMention(idToMention.get(speakerInfoMentionID));
      }
      // now the SpeakerInfo for this Mention should be fully restored
      mentionToUpdate.speakerInfo = speakerInfo;
    }

    // if there is coref info, add mappings from entity mentions and coref mentions
    if (hasCorefInfo) {
      // restore the entity mention to coref mention mappings
      // entity mentions without a corresponding coref mention have -1 in the serialized mapping
      ann.set(CoreAnnotations.EntityMentionToCorefMentionMappingAnnotation.class,
          new HashMap<Integer,Integer>());
      int entityMentionIndex = 0;
      for (int corefMentionForEntityMentionIndex : proto.getEntityMentionToCorefMentionMappingsList()) {
        if (corefMentionForEntityMentionIndex != -1) {
          ann.get(EntityMentionToCorefMentionMappingAnnotation.class).put(
              entityMentionIndex, corefMentionForEntityMentionIndex);
        }
        entityMentionIndex++;
      }
      // restore the coref mention to entity mention mappings
      // entity mentions without a corresponding coref mention have -1 in the serialized mapping
      ann.set(CoreAnnotations.CorefMentionToEntityMentionMappingAnnotation.class,
          new HashMap<Integer,Integer>());
      int corefMentionIndex = 0;
      for (int entityMentionForCorefMentionIndex : proto.getCorefMentionToEntityMentionMappingsList()) {
        if (entityMentionForCorefMentionIndex != -1) {
          ann.get(CorefMentionToEntityMentionMappingAnnotation.class).put(
              corefMentionIndex, entityMentionForCorefMentionIndex);
        }
        corefMentionIndex++;
      }
    }

    // Return
    return ann;
  }

  public static void toFlattenedTree(Tree tree, CoreNLPProtos.FlattenedParseTree.Builder treeBuilder) {
    CoreNLPProtos.FlattenedParseTree.Node.Builder nodeBuilder = CoreNLPProtos.FlattenedParseTree.Node.newBuilder();
    nodeBuilder.setOpenNode(true);
    treeBuilder.addNodes(nodeBuilder.build());

    if (tree.label() == null) {
      throw new UnsupportedOperationException("Empty label not supported");
    }

    nodeBuilder = CoreNLPProtos.FlattenedParseTree.Node.newBuilder();
    nodeBuilder.setValue(tree.label().value());
    if (!Double.isNaN(tree.score())) {
      nodeBuilder.setScore(tree.score());
    }
    treeBuilder.addNodes(nodeBuilder.build());

    for (Tree child : tree.children()) {
      if (child.numChildren() == 0) {
        nodeBuilder = CoreNLPProtos.FlattenedParseTree.Node.newBuilder();
        nodeBuilder.setValue(child.label().value());
        if (!Double.isNaN(child.score())) {
          nodeBuilder.setScore(child.score());
        }
        treeBuilder.addNodes(nodeBuilder.build());
      } else {
        toFlattenedTree(child, treeBuilder);
      }
    }

    nodeBuilder = CoreNLPProtos.FlattenedParseTree.Node.newBuilder();
    nodeBuilder.setCloseNode(true);
    treeBuilder.addNodes(nodeBuilder.build());
  }

  /**
   * Turn the given tree into a FlattedParseTree object from the proto
   *<br>
   * The new structure is useful because the ParseTree object can't
   * represent trees past a certain depth.  Unfortunately, we can't
   * just replace ParseTree with this as there are existing
   * serializations with the old version
   *<br>
   * This works by recursively calling the toFlattenedTree helper method.
   * In fact, that recursion could be eliminated with a stack object,
   * but presumably it won't be so deep that it kills the Java stack
   */
  public static CoreNLPProtos.FlattenedParseTree toFlattenedTree(Tree tree) {
    if (Thread.interrupted()) {
      throw new RuntimeInterruptedException();
    }
    CoreNLPProtos.FlattenedParseTree.Builder treeBuilder = CoreNLPProtos.FlattenedParseTree.newBuilder();
    toFlattenedTree(tree, treeBuilder);
    return treeBuilder.build();
  }

  /**
   * Retrieve a Tree object from a flattened tree protobuf.
   *
   * @param proto The serialized tree.
   * @return A Tree object corresponding to the saved tree. This will always be a {@link LabeledScoredTreeNode}.
   */
  public static Tree fromProto(CoreNLPProtos.FlattenedParseTree proto) {
    if (Thread.interrupted()) {
      throw new RuntimeInterruptedException();
    }
    if (proto.getNodesList().size() == 0) {
      return null;
    }
    Stack<LabeledScoredTreeNode> stack = new Stack<>();
    LabeledScoredTreeNode finished = null;

    // The incoming data structure is basically a PTB formatted tree
    // with openNode representing ( and closeNode representing )
    // essentially we only need to keep track of the current node and
    // all of its ancestors
    // we do that in a stack.  as we finish a node, we add it to the
    // appropriate parent and forget about it
    for (CoreNLPProtos.FlattenedParseTree.Node next : proto.getNodesList()) {
      if (finished != null) {
        throw new IllegalArgumentException("Tree continued after it was already closed!  Offending proto: " + proto);
      }
      if (next.hasOpenNode()) {
        if (stack.size() > 0 && stack.peek().label() == null) {
          throw new IllegalArgumentException("Tree added a child before a label was added to a node!  Offending proto: " + proto);
        }
        LabeledScoredTreeNode newNode = new LabeledScoredTreeNode();
        stack.push(newNode);
        if (next.hasScore()) {
          newNode.setScore(next.getScore());
        }
      } else if (next.hasCloseNode()) {
        if (stack.size() == 0) {
          // demand that the tree always start with an Open
          throw new IllegalArgumentException("Tree started with a Close, not an Open!  Offending proto: " + proto);
        }
        LabeledScoredTreeNode child = stack.pop();
        if (stack.size() == 0) {
          // Popped off the last node.  Guess we're done.
          // We don't return yet so that we check that the
          // iterator is finished first
          finished = child;
        } else {
          LabeledScoredTreeNode parent = stack.peek();
          // note: this is actually kind of slow if the tree is really wide,
          // but hopefully that's not a common occurrence
          // we could solve that by keeping a stack of list of children as well
          parent.addChild(child);
        }
      } else {
        if (stack.size() == 0) {
          // demand that the tree always start with an Open
          throw new IllegalArgumentException("Tree started with a label, not an Open!  Offending proto: " + proto);
        }
        LabeledScoredTreeNode top = stack.peek();
        if (top.label() == null) {
          // the first label after an Open is the label
          CoreLabel value = new CoreLabel();
          value.setCategory(next.getValue());
          value.setValue(next.getValue());
          top.setLabel(value);
          if (next.hasScore()) {
            top.setScore(next.getScore());
          }
        } else {
          // subsequence labels will be children
          LabeledScoredTreeNode child = new LabeledScoredTreeNode();
          CoreLabel value = new CoreLabel();
          value.setCategory(next.getValue());
          value.setValue(next.getValue());
          child.setLabel(value);
          top.addChild(child);
          if (next.hasScore()) {
            child.setScore(next.getScore());
          }
        }
      }
    }
    if (finished == null) {
      throw new IllegalArgumentException("Tree never finished!  Offending proto: " + proto);
    }
    return finished;
  }

  /**
   * Retrieve a Tree object and then attach the tokens passed in.
   *
   * Useful for keeping the tokens in the tree synchronized with the tokens in a sentence.
   */
  public static Tree fromProto(CoreNLPProtos.ParseTree proto, List<CoreLabel> tokens) {
    Tree tree = fromProto(proto);
    Trees.setLeafLabels(tree, tokens);
    return tree;
  }

  /**
   * Retrieve a Tree object from a saved protobuf.
   * This is not intended to be used on its own, but it is safe (lossless) to do so and therefore it is
   * left visible.
   *
   * @param proto The serialized tree.
   * @return A Tree object corresponding to the saved tree. This will always be a {@link LabeledScoredTreeNode}.
   */
  public static Tree fromProto(CoreNLPProtos.ParseTree proto) {
    if (Thread.interrupted()) {
      throw new RuntimeInterruptedException();
    }
    LabeledScoredTreeNode node = new LabeledScoredTreeNode();
    // Set label
    if (proto.hasValue()) {
      CoreLabel value = new CoreLabel();
      value.setCategory(proto.getValue());
      value.setValue(proto.getValue());
      node.setLabel(value);
      // Set span
      if (proto.hasYieldBeginIndex() && proto.hasYieldEndIndex()) {
        IntPair span = new IntPair(proto.getYieldBeginIndex(), proto.getYieldEndIndex());
        value.set(SpanAnnotation.class, span);
      }
      // Set sentiment
      if (proto.hasSentiment()) {
        value.set(RNNCoreAnnotations.PredictedClass.class, proto.getSentiment().getNumber());
      }
    }
    // Set score
    if (proto.hasScore()) { node.setScore(proto.getScore()); }
    // Set children
    Tree[] children = new LabeledScoredTreeNode[proto.getChildCount()];
    for (int i = 0; i < children.length; ++i) {
      children[i] = fromProto(proto.getChild(i));
    }
    node.setChildren(children);
    // Return
    return node;
  }

  /**
   * Return a CoreNLP language from a Protobuf language
   */
  public static Language fromProto(CoreNLPProtos.Language lang) {
    switch (lang) {
      case Arabic:
        return Language.Arabic;
      case Chinese:
        return Language.Chinese;
      case English:
        return Language.English;
      case German:
        return Language.German;
      case French:
        return Language.French;
      case Hebrew:
        return Language.Hebrew;
      case Spanish:
        return Language.Spanish;
      case UniversalChinese:
        return Language.UniversalChinese;
      case UniversalEnglish:
        return Language.UniversalEnglish;
      case Unknown:
        return Language.Unknown;
      case Any:
        return Language.Any;
      default:
        throw new IllegalStateException("Unknown language: " + lang);
    }
  }

  /**
   * Return a CoreNLP Operator (Natural Logic operator) from a Protobuf operator
   */
  public static OperatorSpec fromProto(CoreNLPProtos.Operator operator) {
    String opName = operator.getName().toLowerCase();
    Operator op = null;
    for (Operator candidate : Operator.values()) {
      if (candidate.name().toLowerCase().equals(opName)) {
        op = candidate;
        break;
      }
    }
    return new OperatorSpec(op, operator.getQuantifierSpanBegin(), operator.getQuantifierSpanEnd(),
        operator.getSubjectSpanBegin(), operator.getSubjectSpanEnd(),
        operator.getObjectSpanBegin(), operator.getObjectSpanEnd());
  }

  /**
   * Return a CoreNLP Polarity (Natural Logic polarity) from a Protobuf operator
   */
  public static Polarity fromProto(CoreNLPProtos.Polarity polarity) {
    byte[] projectionFn = new byte[7];
    projectionFn[0] = (byte) polarity.getProjectEquivalence().getNumber();
    projectionFn[1] = (byte) polarity.getProjectForwardEntailment().getNumber();
    projectionFn[2] = (byte) polarity.getProjectReverseEntailment().getNumber();
    projectionFn[3] = (byte) polarity.getProjectNegation().getNumber();
    projectionFn[4] = (byte) polarity.getProjectAlternation().getNumber();
    projectionFn[5] = (byte) polarity.getProjectCover().getNumber();
    projectionFn[6] = (byte) polarity.getProjectIndependence().getNumber();
    return new Polarity(projectionFn);
  }


  /**
   * Deserialize a dependency tree, allowing for cross-sentence arcs.
   * This is primarily here for deserializing OpenIE triples.
   *
   * @see ProtobufAnnotationSerializer#fromProto(CoreNLPProtos.DependencyGraph, List, String)
   */
  private static SemanticGraph fromProto(CoreNLPProtos.DependencyGraph proto, List<CoreLabel> sentence, String docid, Optional<Annotation> document) {
    SemanticGraph graph = new SemanticGraph();

    // first construct the actual nodes; keep them indexed by their index
    // This block is optimized as one of the places which take noticeable time
    // in datum caching
    int min = Integer.MAX_VALUE;
    int max = Integer.MIN_VALUE;
    for(CoreNLPProtos.DependencyGraph.Node in: proto.getNodeList()){
      min = in.getIndex() < min ? in.getIndex() : min;
      max = in.getIndex() > max ? in.getIndex() : max;
    }
    // useful for keeping track of which nodes are roots
    List<IndexedWord> orderedNodes = new ArrayList<>();
    // map from index, emptyIndex, copy -> IndexedWord
    ThreeDimensionalMap<Integer, Integer, Integer, IndexedWord> nodes = new ThreeDimensionalMap<>();
    // map from index, emptyIndex -> CoreLabel
    TwoDimensionalMap<Integer, Integer, CoreLabel> originalLabels = TwoDimensionalMap.hashMap();
    // assume the code path which uses a Document doesn't use emptyIndex
    // alternatively, we could attach words with emptyIndex to the Document some other way
    if (!document.isPresent()) {
      int index = 0;
      for (CoreLabel token : sentence) {
        ++index;  // indices should start at 1
        Integer tokenIndex = token.get(IndexAnnotation.class);
        if (tokenIndex == null) {
          tokenIndex = index;
        }
        Integer emptyIndex = token.getEmptyIndex();
        if (emptyIndex == null) {
          emptyIndex = 0;
        }
        originalLabels.put(tokenIndex, emptyIndex, token);
      }
    }
    for(CoreNLPProtos.DependencyGraph.Node in: proto.getNodeList()){
      CoreLabel token;
      if (document.isPresent()) {
        token = document.get().get(SentencesAnnotation.class).get(in.getSentenceIndex()).get(TokensAnnotation.class).get(in.getIndex() - 1); // token index starts at 1!
      } else {
        token = originalLabels.get(in.getIndex(), in.getEmptyIndex());
        if (token == null) {
          throw new FailedSerializationError("Could not find the token for index " + in.getIndex() + " empty " + in.getEmptyIndex() + "\n(" + originalLabels.size() + " known labels)");
        }
      }
      IndexedWord word;
      if (in.hasCopyAnnotation() && in.getCopyAnnotation() > 0) {
        // TODO: if we make a copy wrapper CoreLabel, use it here instead
        word = new IndexedWord(new CoreLabel(token));
        word.setCopyCount(in.getCopyAnnotation());
      } else {
        word = new IndexedWord(token);
      }

      // for backwards compatibility - new annotations should have
      // these fields set, but annotations older than August 2014 might not
      if (word.docID() == null && docid != null) {
        word.setDocID(docid);
      }
      if (word.sentIndex() < 0 && in.getSentenceIndex() >= 0) {
        word.setSentIndex(in.getSentenceIndex());
      }
      if (word.index() < 0 && in.getIndex() >= 0) {
        word.setIndex(in.getIndex());
      }

      nodes.put(in.getIndex(), in.getEmptyIndex(), in.getCopyAnnotation(), word);
      graph.addVertex(word);
      orderedNodes.add(word);
    }

    // add all edges to the actual graph
    for(CoreNLPProtos.DependencyGraph.Edge ie: proto.getEdgeList()){
      IndexedWord source = nodes.get(ie.getSource(), ie.getSourceEmpty(), ie.getSourceCopy());
      if (source == null) {
        throw new FailedSerializationError("Source of a dependency was null!\nEdge: " + ie);
      }
      IndexedWord target = nodes.get(ie.getTarget(), ie.getTargetEmpty(), ie.getTargetCopy());
      if (target == null) {
        throw new FailedSerializationError("Target of a dependency was null!\nEdge: " + ie);
      }
      synchronized (globalLock) {
        // this is not thread-safe: there are static fields in GrammaticalRelation
        if (!ie.hasDep()) {
          throw new FailedSerializationError("Protobuf dependency edge was null!\nEdge: " + ie);
        }
        GrammaticalRelation rel = GrammaticalRelation.valueOf(fromProto(ie.getLanguage()), ie.getDep());
        graph.addEdge(source, target, rel, 1.0, ie.hasIsExtra() && ie.getIsExtra());
      }
    }

    if (proto.getRootNodeCount() > 0) {
      Collection<IndexedWord> roots = proto.getRootNodeList().stream().map(idx -> orderedNodes.get(idx)).collect(Collectors.toList());
      graph.setRoots(roots);
    } else if (proto.getRootCount() > 0) {
      // assume empty nodes and copy nodes can't be the root
      // this is actually not true: there are examples in the UD Estonian EWT treebank
      // which have empty nodes as the root of the enhanced graph
      Collection<IndexedWord> roots = proto.getRootList().stream().map(rootI -> nodes.get(rootI, 0, 0)).collect(Collectors.toList());
      graph.setRoots(roots);
    } else {
      // Roots were not saved away
      // compute root nodes if non-empty
      if(!graph.isEmpty()){
        graph.resetRoots();
      }
    }
    return graph;

  }


  /**
   * Voodoo magic to convert a serialized dependency graph into a {@link SemanticGraph}.
   * <br>
   * This method needs the words from the sentence, such as we have
   * when converting an entire document in the
   * {@link ProtobufAnnotationSerializer#fromProto(CoreNLPProtos.Document)} method.
   *
   * @param proto The serialized representation of the graph. This relies heavily on indexing into the original document.
   * @param sentence The raw sentence that this graph was saved from must be provided, as it is not saved in the serialized
   *                 representation.
   * @param docid A docid must be supplied, as it is not saved by the serialized representation.
   * @return A semantic graph corresponding to the saved object, on the provided sentence.
   */
  public static SemanticGraph fromProto(CoreNLPProtos.DependencyGraph proto, List<CoreLabel> sentence, String docid) {
    return fromProto(proto, sentence, docid, Optional.empty());
  }


  /**
   * Return a  {@link RelationTriple} object from the serialized representation.
   * This requires a sentence and a document so that
   * (1) we have a docid for the dependency tree can be accurately rebuilt,
   * and (2) we have references to the tokens to include in the relation triple.
   *
   * @param proto The serialized relation triples.
   * @param doc The document we are deserializing. This document should already
   *            have a docid annotation set, if there is one.
   * @param sentenceIndex The index of the sentence this extraction should be attached to.
   *
   * @return A relation triple as a Java object, corresponding to the seriaized proto.
   */
  public static RelationTriple fromProto(CoreNLPProtos.RelationTriple proto, Annotation doc, int sentenceIndex) {
    if (Thread.interrupted()) {
      throw new RuntimeInterruptedException();
    }
    // Get the spans for the extraction
    List<CoreLabel> subject = proto.getSubjectTokensList().stream().map(loc ->
        doc.get(SentencesAnnotation.class).get(loc.getSentenceIndex()).get(TokensAnnotation.class).get(loc.getTokenIndex())
        ).collect(Collectors.toList());
    List<CoreLabel> relation;
    if (proto.getRelationTokensCount() == 0) {  // If we don't have a real span for the relation, make a dummy word
      relation = Collections.singletonList(new CoreLabel(new Word(proto.getRelation())));
    } else {
      relation = proto.getRelationTokensList().stream().map(loc ->
          doc.get(SentencesAnnotation.class).get(loc.getSentenceIndex()).get(TokensAnnotation.class).get(loc.getTokenIndex())
      ).collect(Collectors.toList());
    }
    List<CoreLabel> object = proto.getObjectTokensList().stream().map(loc ->
        doc.get(SentencesAnnotation.class).get(loc.getSentenceIndex()).get(TokensAnnotation.class).get(loc.getTokenIndex())
    ).collect(Collectors.toList());

    // Create the extraction
    RelationTriple extraction;
    double confidence = proto.getConfidence();
    if (proto.hasTree()) {
      SemanticGraph tree = fromProto(
          proto.getTree(),
          doc.get(SentencesAnnotation.class).get(sentenceIndex).get(TokensAnnotation.class),
          doc.get(DocIDAnnotation.class),
          Optional.of(doc));
      extraction =  new RelationTriple.WithTree(subject, relation, object, tree, confidence);
    } else {
      extraction = new RelationTriple(subject, relation, object, confidence);
    }

    // Tweak the extraction
    if (proto.hasIstmod()) { extraction.istmod(proto.getIstmod()); }
    if (proto.hasPrefixBe()) { extraction.isPrefixBe(proto.getPrefixBe()); }
    if (proto.hasSuffixBe()) { extraction.isSuffixBe(proto.getSuffixBe()); }
    if (proto.hasSuffixOf()) { extraction.isSuffixOf(proto.getSuffixOf()); }

    // Return
    return extraction;
  }

  /**
   * Returns a sentence fragment from a given protocol buffer, and an associated parse tree.
   *
   * @param fragment The saved sentence fragment.
   * @param tree The parse tree for the whole sentence.
   *
   * @return A {@link SentenceFragment} object corresponding to the saved proto.
   */
  public static SentenceFragment fromProto(CoreNLPProtos.SentenceFragment fragment, SemanticGraph tree) {
    if (Thread.interrupted()) {
      throw new RuntimeInterruptedException();
    }
    SemanticGraph fragmentTree = new SemanticGraph(tree);
    // Set the new root
    if (fragment.hasRoot()) {
      fragmentTree.resetRoots();
      fragmentTree.vertexSet().stream()
          .filter(vertex -> vertex.index() - 1 == fragment.getRoot())
          .forEach(fragmentTree::setRoot);
    }
    // Set the new vertices
    Set<Integer> keptIndices = new HashSet<>(fragment.getTokenIndexList());
    tree.vertexSet().stream()
        .filter(vertex -> !keptIndices.contains(vertex.index() - 1))
        .forEach(fragmentTree::removeVertex);
    // Apparently this sometimes screws up the tree
    fragmentTree.vertexSet().stream()
        .filter(vertex -> fragmentTree.getFirstRoot() != vertex &&
            tree.getFirstRoot() != vertex &&
            !fragmentTree.incomingEdgeIterable(vertex).iterator().hasNext())
        .forEach(vertex -> {
          SemanticGraphEdge edge = tree.incomingEdgeIterable(vertex).iterator().next();
          fragmentTree.addEdge(fragmentTree.getFirstRoot(), edge.getDependent(), edge.getRelation(),
              edge.getWeight(), edge.isExtra());
        });
    // Return the fragment
    //noinspection SimplifiableConditionalExpression
    return new SentenceFragment(fragmentTree,
        fragment.hasAssumedTruth() ? fragment.getAssumedTruth() : true,
        false)
        .changeScore(fragment.hasScore() ? fragment.getScore() : 1.0);
  }

  /**
   * Convert a serialized Map back into a Java Map.
   *
   * @param proto The serialized map.
   *
   * @return A Java Map corresponding to the serialized map.
   */
  public static HashMap<String, String> fromProto(CoreNLPProtos.MapStringString proto) {
    // LinkedHashMap so that the pieces stay in the order we receive them
    // (could also use a TreeMap instead to enforce sorted by key)
    HashMap<String, String> map = new LinkedHashMap<>();
    for (int i = 0; i < proto.getKeyCount(); ++i) {
      map.put(proto.getKey(i), proto.getValue(i));
    }
    return map;
  }

  /**
   * Convert a serialized Map back into a Java Map.
   *
   * @param proto The serialized map.
   *
   * @return A Java Map corresponding to the serialized map.
   */
  public static HashMap<Integer, String> fromProto(CoreNLPProtos.MapIntString proto) {
      HashMap<Integer, String> map = new HashMap<>();
      for (int i = 0; i < proto.getKeyCount(); ++i) {
          map.put(proto.getKey(i), proto.getValue(i));
      }
      return map;
  }

  /**
   * Read a CorefChain from its serialized representation.
   * This is private due to the need for an additional partial document. Also, why on Earth are you trying to use
   * this on its own anyways?
   * @param proto The serialized representation of the coref chain, missing information on its mention span string.
   * @param partialDocument A partial document, which must contain {@link SentencesAnnotation} and {@link TokensAnnotation} in
   *                        order to fill in the mention span strings.
   * @return A coreference chain.
   */
  private CorefChain fromProto(CoreNLPProtos.CorefChain proto, Annotation partialDocument) {
    // Get chain ID
    int cid = proto.getChainID();
    // Get mentions
    Map<IntPair, Set<CorefChain.CorefMention>> mentions = new HashMap<>();
    CorefChain.CorefMention representative = null;
    for (int i = 0; i < proto.getMentionCount(); ++i) {
      if (Thread.interrupted()) {
        throw new RuntimeInterruptedException();
      }
      CoreNLPProtos.CorefChain.CorefMention mentionProto = proto.getMention(i);
      // Create mention
      StringBuilder mentionSpan = new StringBuilder();
      List<CoreLabel> sentenceTokens = partialDocument.get(SentencesAnnotation.class).get(mentionProto.getSentenceIndex()).get(TokensAnnotation.class);
      for (int k = mentionProto.getBeginIndex(); k < mentionProto.getEndIndex(); ++k) {
        mentionSpan.append(" ").append(sentenceTokens.get(k).word());
      }
      // Set the coref cluster id for the token
      CorefChain.CorefMention mention = new CorefChain.CorefMention(
          Dictionaries.MentionType.valueOf(mentionProto.getMentionType()),
          Dictionaries.Number.valueOf(mentionProto.getNumber()),
          Dictionaries.Gender.valueOf(mentionProto.getGender()),
          Dictionaries.Animacy.valueOf(mentionProto.getAnimacy()),
          mentionProto.getBeginIndex() + 1,
          mentionProto.getEndIndex() + 1,
          mentionProto.getHeadIndex() + 1,
          cid,
          mentionProto.getMentionID(),
          mentionProto.getSentenceIndex() + 1,
          new IntTuple(new int[]{ mentionProto.getSentenceIndex() + 1, mentionProto.getPosition() }),
          mentionSpan.substring(mentionSpan.length() > 0 ? 1 : 0));
      // Register mention
      IntPair key = new IntPair(mentionProto.getSentenceIndex() - 1, mentionProto.getHeadIndex() - 1);
      if (!mentions.containsKey(key)) { mentions.put(key, new HashSet<>()); }
      mentions.get(key).add(mention);
      // Check for representative
      if (proto.hasRepresentative() && i == proto.getRepresentative()) {
        representative = mention;
      }
    }
    // Return
    return new CorefChain(cid, mentions, representative);
  }

  private Mention fromProtoNoTokens(CoreNLPProtos.Mention protoMention) {
    Mention returnMention = new Mention();
    // set enums
    if (protoMention.getMentionType() != null && !protoMention.getMentionType().equals("")) {
      returnMention.mentionType = Dictionaries.MentionType.valueOf(protoMention.getMentionType());
    }
    if (protoMention.getNumber() != null && !protoMention.getNumber().equals("")) {
      returnMention.number = Dictionaries.Number.valueOf(protoMention.getNumber());
    }
    if (protoMention.getGender() != null && !protoMention.getGender().equals("")) {
      returnMention.gender = Dictionaries.Gender.valueOf(protoMention.getGender());
    }
    if (protoMention.getAnimacy() != null && !protoMention.getAnimacy().equals("")) {
      returnMention.animacy = Dictionaries.Animacy.valueOf(protoMention.getAnimacy());
    }
    if (protoMention.getPerson() != null && !protoMention.getPerson().equals("")) {
      returnMention.person = Dictionaries.Person.valueOf(protoMention.getPerson());
    }

    // TO DO: if the original Mention had "" for this field it will be lost, should deal with this problem
    if (!protoMention.getHeadString().equals("")) {
      returnMention.headString = protoMention.getHeadString();
    }
    // TO DO: if the original Mention had "" for this field it will be lost, should deal with this problem
    if (!protoMention.getNerString().equals("")) {
      returnMention.nerString = protoMention.getNerString();
    }

    returnMention.startIndex = protoMention.getStartIndex();
    returnMention.endIndex = protoMention.getEndIndex();
    returnMention.headIndex = protoMention.getHeadIndex();
    returnMention.mentionID = protoMention.getMentionID();
    returnMention.originalRef = protoMention.getOriginalRef();

    returnMention.goldCorefClusterID = protoMention.getGoldCorefClusterID();
    returnMention.corefClusterID = protoMention.getCorefClusterID();
    returnMention.mentionNum = protoMention.getMentionNum();
    returnMention.sentNum = protoMention.getSentNum();
    returnMention.utter = protoMention.getUtter();
    returnMention.paragraph = protoMention.getParagraph();
    returnMention.isSubject = protoMention.getIsSubject();
    returnMention.isDirectObject = protoMention.getIsDirectObject();
    returnMention.isIndirectObject = protoMention.getIsIndirectObject();
    returnMention.isPrepositionObject = protoMention.getIsPrepositionObject();
    returnMention.hasTwin = protoMention.getHasTwin();
    returnMention.generic = protoMention.getGeneric();
    returnMention.isSingleton = protoMention.getIsSingleton();

    // handle the sets of Strings
    if (protoMention.getDependentsCount() != 0) {
      returnMention.dependents = new HashSet<>();
      returnMention.dependents.addAll(protoMention.getDependentsList());
    }

    if (protoMention.getPreprocessedTermsCount() != 0) {
      returnMention.preprocessedTerms = new ArrayList<>();
      returnMention.preprocessedTerms.addAll(protoMention.getPreprocessedTermsList());
    }

    return returnMention;
  }

  private SpeakerInfo fromProto(CoreNLPProtos.SpeakerInfo speakerInfo) {
    String speakerName = speakerInfo.getSpeakerName();
    return new SpeakerInfo(speakerName);
  }

  /**
   * Create an internal Timex object from the serialized protocol buffer.
   * @param proto The serialized protocol buffer to read from.
   * @return A timex, with as much information filled in as was gleaned from the protocol buffer.
   */
  private static Timex fromProto(CoreNLPProtos.Timex proto) {
    return new Timex(
        proto.hasType() ? proto.getType() : null,
        proto.hasValue() ? proto.getValue() : null,
        proto.hasAltValue() ? proto.getAltValue() : null,
        proto.hasTid() ? proto.getTid() : null,
        proto.hasText() ? proto.getText() : null,
        proto.hasBeginPoint() ? proto.getBeginPoint() : -1,
        proto.hasEndPoint() ? proto.getEndPoint() : -1);
  }

  /**
   * Read a entity mention from its serialized form. Requires the containing sentence to be
   * passed in along with the protocol buffer.
   * @param proto The serialized entity mention.
   * @param sentence The sentence this mention is attached to.
   * @return The entity mention corresponding to the serialized object.
   */
  private EntityMention fromProto(CoreNLPProtos.Entity proto, CoreMap sentence) {
    EntityMention rtn = new EntityMention(
        proto.hasObjectID() ? proto.getObjectID() : null,
        sentence,
        proto.hasHeadStart() ? new Span(proto.getHeadStart(), proto.getHeadEnd()) : null,
        proto.hasHeadEnd() ? new Span(proto.getExtentStart(), proto.getExtentEnd()) : null,
        proto.hasType() ? proto.getType() : null,
        proto.hasSubtype() ? proto.getSubtype() : null,
        proto.hasMentionType() ? proto.getMentionType() : null );
    if (proto.hasNormalizedName()) { rtn.setNormalizedName(proto.getNormalizedName()); }
    if (proto.hasHeadTokenIndex()) { rtn.setHeadTokenPosition(proto.getHeadTokenIndex()); }
    if (proto.hasCorefID()) { rtn.setCorefID(proto.getCorefID()); }
    return rtn;
  }

  /**
   * Read a relation mention from its serialized form. Requires the containing sentence to be
   * passed in along with the protocol buffer.
   * @param proto The serialized relation mention.
   * @param sentence The sentence this mention is attached to.
   * @return The relation mention corresponding to the serialized object.
   */
  private RelationMention fromProto(CoreNLPProtos.Relation proto, CoreMap sentence) {
    List<ExtractionObject> args = proto.getArgList().stream().map(arg -> fromProto(arg, sentence)).collect(Collectors.toList());
    RelationMention rtn = new RelationMention(
        proto.hasObjectID() ? proto.getObjectID() : null,
        sentence,
        proto.hasExtentStart() ? new Span(proto.getExtentStart(), proto.getExtentEnd()) : null,
        proto.hasType() ? proto.getType() : null,
        proto.hasSubtype() ? proto.getSubtype() : null,
        args);
    if (proto.hasSignature()) { rtn.setSignature(proto.getSignature()); }
    if (proto.getArgNameCount() > 0 || proto.getArgCount() == 0) {
      rtn.setArgNames(proto.getArgNameList());
    }
    return rtn;
  }

  /**
   * Convert a quote object to a protocol buffer.
   */
  @SuppressWarnings("UnusedParameters")
  private static Annotation fromProto(CoreNLPProtos.Quote quote, List<CoreLabel> tokens) {
    List<CoreLabel> quotedTokens = null;
    // note[gabor]: This works, but apparently isn't the behavior of the quote annotator?
//    if (quote.hasTokenBegin() && quote.hasTokenEnd() && quote.getTokenBegin() >= 0 && quote.getTokenEnd() >= 0) {
//      quotedTokens = tokens.subList(quote.getTokenBegin(), quote.getTokenEnd());
//    }
    @SuppressWarnings("ConstantConditions")
    Annotation ann = QuoteAnnotator.makeQuote(
        quote.hasText() ? quote.getText() : null,
        quote.hasBegin() ? quote.getBegin() : -1,
        quote.hasEnd() ? quote.getEnd() : -1,
        quotedTokens,
        quote.hasTokenBegin() ? quote.getTokenBegin() : -1,
        quote.hasSentenceBegin() ? quote.getSentenceBegin() : -1,
        quote.hasSentenceEnd() ? quote.getSentenceEnd() : -1,
        quote.hasDocid() ? quote.getDocid() : null);
    if (quote.hasIndex()) { ann.set(QuotationIndexAnnotation.class, quote.getIndex()); }
    if (quote.hasTokenBegin()) { ann.set(TokenBeginAnnotation.class, quote.getTokenBegin()); }
    if (quote.hasTokenEnd()) { ann.set(TokenEndAnnotation.class, quote.getTokenEnd()); }
    if (quote.hasAuthor()) { ann.set(AuthorAnnotation.class, quote.getAuthor()); }

    // quote attribution stuff
    if (quote.hasMention()) {
      ann.set(QuoteAttributionAnnotator.MentionAnnotation.class, quote.getMention());
    }

    if (quote.hasMentionBegin()) {
      ann.set(QuoteAttributionAnnotator.MentionBeginAnnotation.class, quote.getMentionBegin());
    }
    if (quote.hasMentionEnd()) {
      ann.set(QuoteAttributionAnnotator.MentionEndAnnotation.class, quote.getMentionEnd());
    }
    if (quote.hasMentionType()) {
      ann.set(QuoteAttributionAnnotator.MentionTypeAnnotation.class, quote.getMentionType());
    }
    if (quote.hasMentionSieve()) {
      ann.set(QuoteAttributionAnnotator.MentionSieveAnnotation.class, quote.getMentionSieve());
    }
    if (quote.hasSpeaker()) {
      ann.set(QuoteAttributionAnnotator.SpeakerAnnotation.class, quote.getSpeaker());
    }
    if (quote.hasSpeakerSieve()) {
      ann.set(QuoteAttributionAnnotator.SpeakerSieveAnnotation.class, quote.getSpeakerSieve());
    }
    if (quote.hasCanonicalMention()) {
      ann.set(QuoteAttributionAnnotator.CanonicalMentionAnnotation.class, quote.getCanonicalMention());
    }
    if (quote.hasCanonicalMentionBegin()) {
      ann.set(QuoteAttributionAnnotator.CanonicalMentionBeginAnnotation.class, quote.getCanonicalMentionBegin());
    }
    if (quote.hasCanonicalMentionEnd()) {
      ann.set(QuoteAttributionAnnotator.CanonicalMentionEndAnnotation.class, quote.getCanonicalMentionEnd());
    }

    return ann;
  }

  /**
   * Convert a quote object to a protocol buffer.
   */
  @SuppressWarnings("UnusedParameters")
  private CoreMap fromProto(CoreNLPProtos.NERMention mention) {
    CoreMap map = new ArrayCoreMap(12);
    if (mention.hasSentenceIndex()) map.set(SentenceIndexAnnotation.class, mention.getSentenceIndex());
    if (mention.hasTokenStartInSentenceInclusive()) map.set(TokenBeginAnnotation.class, mention.getTokenStartInSentenceInclusive());
    if (mention.hasTokenEndInSentenceExclusive()) map.set(TokenEndAnnotation.class, mention.getTokenEndInSentenceExclusive());
    if (mention.hasNer()) map.set(NamedEntityTagAnnotation.class, mention.getNer());
    if (mention.hasNormalizedNER()) map.set(NormalizedNamedEntityTagAnnotation.class, mention.getNormalizedNER());
    if (mention.hasEntityType()) map.set(EntityTypeAnnotation.class, mention.getEntityType());
    if (mention.hasTimex()) map.set(TimexAnnotation.class, fromProto(mention.getTimex()));
    if (mention.hasWikipediaEntity()) map.set(WikipediaEntityAnnotation.class, mention.getWikipediaEntity());
    if (mention.hasGender()) map.set(CoreAnnotations.GenderAnnotation.class, mention.getGender());
    if (mention.hasEntityMentionIndex())
      map.set(CoreAnnotations.EntityMentionIndexAnnotation.class, mention.getEntityMentionIndex());
    if (mention.hasCanonicalEntityMentionIndex())
      map.set(CoreAnnotations.CanonicalEntityMentionIndexAnnotation.class, mention.getCanonicalEntityMentionIndex());
    if (mention.hasEntityMentionText()) {
      map.set(CoreAnnotations.TextAnnotation.class, mention.getEntityMentionText());
    }
    return map;
  }

  /**
   * Read a section coremap from its serialized form. Requires the containing sentence to be
   * passed in along with the protocol buffer.
   * @param section The serialized section coremap
   * @return The relation mention corresponding to the serialized object.
   */
  private CoreMap fromProto(CoreNLPProtos.Section section, List<CoreMap> annotationSentences) {
    CoreMap map = new ArrayCoreMap();
    map.set(CharacterOffsetBeginAnnotation.class, section.getCharBegin());
    map.set(CharacterOffsetEndAnnotation.class, section.getCharEnd());
    if (section.hasAuthor())
      map.set(AuthorAnnotation.class, section.getAuthor());
    if (section.hasDatetime())
      map.set(SectionDateAnnotation.class, section.getDatetime());
    // go through the list of sentences and add them to this section's sentence list
    ArrayList<CoreMap> sentencesList = new ArrayList<>();
    for (int sentenceIndex : section.getSentenceIndexesList()) {
      sentencesList.add(annotationSentences.get(sentenceIndex));
    }
    map.set(SentencesAnnotation.class, sentencesList);
    // go through the list of quotes and rebuild the quotes
    map.set(QuotesAnnotation.class, new ArrayList<>());
    for (CoreNLPProtos.Quote quote : section.getQuotesList()) {
      int quoteCharStart = quote.getBegin();
      int quoteCharEnd = quote.getEnd();
      String quoteAuthor = null ;
      if (quote.hasAuthor()) {
        quoteAuthor = quote.getAuthor();
      }
      CoreMap quoteCoreMap = new ArrayCoreMap();
      quoteCoreMap.set(CharacterOffsetBeginAnnotation.class, quoteCharStart);
      quoteCoreMap.set(CharacterOffsetEndAnnotation.class, quoteCharEnd);
      quoteCoreMap.set(AuthorAnnotation.class, quoteAuthor);
      map.get(QuotesAnnotation.class).add(quoteCoreMap);
    }
    // if there is an author character start, add it
    if (section.hasAuthorCharBegin()) {
      map.set(SectionAuthorCharacterOffsetBeginAnnotation.class, section.getAuthorCharBegin());
    }
    if (section.hasAuthorCharEnd()) {
      map.set(SectionAuthorCharacterOffsetEndAnnotation.class, section.getAuthorCharEnd());
    }
    // add the original xml tag
    map.set(SectionTagAnnotation.class, fromProto(section.getXmlTag()));
    return map;
  }

  /**
   * Recover the {@link edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation} field of a sentence
   * from the tokens. This is useful if the text was not set in the protocol buffer, and therefore
   * needs to be reconstructed from tokens.
   *
   * @param tokens The list of tokens representing this sentence.
   * @return The original text of the sentence.
   */
  protected String recoverOriginalText(List<CoreLabel> tokens, CoreNLPProtos.Sentence sentence) {
    StringBuilder text = new StringBuilder();
    CoreLabel last = null;
    if (tokens.size() > 0) {
      CoreLabel token = tokens.get(0);
      if (token.originalText() != null) { text.append(token.originalText()); } else { text.append(token.word()); }
      last = tokens.get(0);
    }
    for (int i = 1; i < tokens.size(); ++i) {
      CoreLabel token = tokens.get(i);
      if (token.before() != null) {
        text.append(token.before());
        assert last != null;
        int missingWhitespace = (token.beginPosition() - last.endPosition()) - token.before().length();
        while (missingWhitespace > 0) {
          text.append(' ');
          missingWhitespace -= 1;
        }
      }
      if (token.originalText() != null) { text.append(token.originalText()); } else { text.append(token.word()); }
      last = token;
    }
    return text.toString();
  }

}
