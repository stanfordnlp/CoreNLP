package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.time.Timex;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.util.*;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reads annotations output put by JSONOutputter
 * As JSONOutputter is not lossless, this reader will only read what is saved.
 * TODO: Also need to read dependency parses and sentiment
 *
 * For more strict serialization,
 * see {@link edu.stanford.nlp.pipeline.AnnotationSerializer}; e.g.,
 * {@link edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer}.
 *
 * @author <a href="mailto:*angel@eloquent.ai">*Angel Chang</a>
 */
public class JSONAnnotationReader {
    private static final CoreLabelTokenFactory tokenFactory = new CoreLabelTokenFactory(true);

    public Annotation read(String text) {
        JsonReader jsonReader = Json.createReader(new StringReader(text));
        JsonObject json = jsonReader.readObject();
        return toAnnotation(json);
    }

    public static <T> List<T> toList(JsonArray array, Function<JsonObject, T> f) {
        List<T> result = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            result.add(f.apply(array.getJsonObject(i)));
        }
        return result;
    }

    public static <T1,T2> Map<T1,T2> toMap(JsonArray array, Function<JsonObject, T1> keyf, Function<JsonObject, T2> valuef) {
        Map<T1,T2> result = new HashMap<>();
        for (int i = 0; i < array.size(); i++) {
            JsonObject obj = array.getJsonObject(i);
            result.put(keyf.apply(obj), valuef.apply(obj));
        }
        return result;
    }

    public static <T1,T2> Map<T1,T2> toMap(JsonObject m, Function<String, T1> keyf, Function<JsonObject, T2> valuef) {
        Map<T1,T2> result = new HashMap<>();
        Set<String> keys = m.keySet();
        for (String key : keys) {
            JsonObject obj = m.getJsonObject(key);
            result.put(keyf.apply(key), valuef.apply(obj));
        }
        return result;
    }

    public static <T1,T2> Map<T1,T2> toMap(JsonObject m, Function<Pair<JsonObject, String>, Pair<T1, T2>> pairf) {
        Map<T1,T2> result = new HashMap<>();
        Set<String> keys = m.keySet();
        for (String key : keys) {
            Pair<T1,T2> p = pairf.apply(Pair.makePair(m, key));
            result.put(p.first, p.second);
        }
        return result;
    }

    public static <T1,T2> T2 toNullable(T1 obj, Function<T1,T2> f) {
        if (obj == null) return null;
        else return f.apply(obj);
    }

    public static <T> List<T> toNullableList(JsonArray array, Function<JsonObject, T> f) {
        if (array == null) return null;
        else return toList(array, f);
    }

    public static <T1,T2> Map<T1,T2> toNullableMap(JsonObject json, Function<Pair<JsonObject, String>, Pair<T1, T2>> pairf) {
        if (json == null) return null;
        else return toMap(json, pairf);
    }

    public SemanticGraph toDependencyParse(JsonArray array) {
        // TODO: get semantic graph
        return null;
    }

    public CorefChain.CorefMention toCorefMention(JsonObject json, int cid) {
        JsonArray position = json.getJsonArray("position");
        IntTuple tuple = null;
        if (position != null) {
            tuple = new IntTuple(position.size());
            for (int i = 0; i < position.size(); i++) {
                tuple.set(i, position.getInt(i));
            }
        }
        String type = json.getString("type", null);
        String number = json.getString("number", null);
        String gender = json.getString("gender", null);
        String animacy = json.getString("animacy", null);
        CorefChain.CorefMention mention = new CorefChain.CorefMention(
                type != null? Dictionaries.MentionType.valueOf(type) : null,
                number != null? Dictionaries.Number.valueOf(number) : null,
                gender != null? Dictionaries.Gender.valueOf(gender) : null,
                animacy != null? Dictionaries.Animacy.valueOf(animacy) : null,
                json.getInt("startIndex"),
                json.getInt("endIndex"),
                json.getInt("headIndex", -1),
                cid,
                json.getInt("id"),
                json.getInt("sentNum"),
                tuple,
                json.getString("text", null)
        );
        //mentionWriter.set("isRepresentativeMention", mention == representative);
        return mention;
    }

    public CorefChain toCorefChain(JsonArray array, int cid) {
        List<Pair<CorefChain.CorefMention, Boolean>> mentions = toList(array,
                item -> Pair.makePair(toCorefMention(item, cid), item.getBoolean("isRepresentativeMention", false)));
        Map<IntPair, Set<CorefChain.CorefMention>> mentionMap = new HashMap<>();
        CorefChain.CorefMention representative = null;
        for (Pair<CorefChain.CorefMention, Boolean> pair : mentions) {
            CorefChain.CorefMention mention = pair.first;
            if (pair.second) {
                representative = mention;
            }
            IntPair key = new IntPair(mention.sentNum, mention.headIndex);
            mentionMap.putIfAbsent(key, new ArraySet<>());
            Set<CorefChain.CorefMention> set = mentionMap.get(key);
            set.add(mention);
        }
        CorefChain corefChain = new CorefChain(cid, mentionMap, representative);
        return corefChain;
    }

    public CoreMap toSection(JsonObject json, List<CoreMap> sentences) {
        CoreMap coremap = new ArrayCoreMap();
        coremap.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, json.getInt("charBegin"));
        coremap.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, json.getInt("charEnd"));
        coremap.set(CoreAnnotations.AuthorAnnotation.class, json.getString("author", null));
        coremap.set(CoreAnnotations.SectionDateAnnotation.class, json.getString("dateTime", null));
        coremap.set(CoreAnnotations.SentencesAnnotation.class,
                toList(json.getJsonArray("sentenceIndexes"), item -> sentences.get(item.getInt("index"))));
        return coremap;
    }

    public CoreMap toQuotation(JsonObject json) {
        CoreMap coremap = new ArrayCoreMap();
        coremap.set(CoreAnnotations.QuotationIndexAnnotation.class, json.getInt("id"));
        coremap.set(CoreAnnotations.TextAnnotation.class, json.getString("text", null));
        coremap.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, json.getInt("beginIndex"));
        coremap.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, json.getInt("endIndex"));
        coremap.set(CoreAnnotations.TokenBeginAnnotation.class, json.getInt("beginToken"));
        coremap.set(CoreAnnotations.TokenEndAnnotation.class, json.getInt("endToken"));
        coremap.set(CoreAnnotations.SentenceBeginAnnotation.class, json.getInt("beginSentence"));
        coremap.set(CoreAnnotations.SentenceEndAnnotation.class, json.getInt("endSentence"));
        String speaker = json.getString("speaker", null);
        if (speaker != null && !"Unknown".equals(speaker)) {
            coremap.set(QuoteAttributionAnnotator.SpeakerAnnotation.class, speaker);
        }
        String canonicalSpeaker = json.getString("canonicalSpeaker", null);
        if (canonicalSpeaker != null && !"Unknown".equals(canonicalSpeaker)) {
            coremap.set(QuoteAttributionAnnotator.CanonicalMentionAnnotation.class, canonicalSpeaker);
        }
        return coremap;
    }

    public <T> List<T> extractSubList(JsonArray array, List<T> list) {
        return list.subList(array.getInt(0), array.getInt(1));
    }

    public RelationTriple toRelationTriple(JsonObject json, List<CoreLabel> tokens) {
        List<CoreLabel> subject = extractSubList(json.getJsonArray("subjectSpan"), tokens);
        List<CoreLabel> relation = extractSubList(json.getJsonArray("relationSpan"), tokens);
        List<CoreLabel> object = extractSubList(json.getJsonArray("objectSpan"), tokens);
        RelationTriple triple = new RelationTriple(subject, relation, object);
        return triple;
    }

    public Timex toTimex(JsonObject json) {
        return null;
    }

    public CoreMap toEntityMention(JsonObject json) {
        CoreMap mention = new ArrayCoreMap();
        mention.set(CoreAnnotations.TokenBeginAnnotation.class, json.getInt("docTokenBegin"));
        mention.set(CoreAnnotations.TokenEndAnnotation.class, json.getInt("docTokenEnd"));
        mention.set(CoreAnnotations.TextAnnotation.class, json.getString("text", null));
        mention.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, json.getInt("characterOffsetBegin"));
        mention.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, json.getInt("characterOffsetEnd"));
        mention.set(CoreAnnotations.NamedEntityTagAnnotation.class, json.getString("ner", null));
        mention.set(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class, json.getString("normalizedNER", null));
        mention.set(CoreAnnotations.WikipediaEntityAnnotation.class, json.getString("entitylink", null));
        mention.set(TimeAnnotations.TimexAnnotation.class, toNullable(json.getJsonObject("timex"), obj -> toTimex(obj)));
        return mention;
    }

    public CoreLabel toToken(JsonObject json) {
        CoreLabel token = tokenFactory.makeToken();
        token.setIndex(json.getInt("index"));
        token.setValue(json.getString("word", null));
        token.setWord(json.getString("word", null));
        token.setOriginalText(json.getString("originalText", null));
        token.setLemma(json.getString("lemma", null));
        token.setBeginPosition(json.getInt("characterOffsetBegin"));
        token.setEndPosition(json.getInt("characterOffsetEnd"));
        token.setTag(json.getString("pos", null));
        token.setNER(json.getString("ner", null));
        token.set(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class, json.getString("normalizedNER", null));
        token.set(CoreAnnotations.SpeakerAnnotation.class, json.getString("speaker", null));
        token.set(CoreAnnotations.TrueCaseAnnotation.class, json.getString("truecase", null));
        token.set(CoreAnnotations.TrueCaseTextAnnotation.class, json.getString("truecaseText", null));
        token.set(CoreAnnotations.BeforeAnnotation.class, json.getString("before", null));
        token.set(CoreAnnotations.AfterAnnotation.class, json.getString("after", null));
        token.set(CoreAnnotations.WikipediaEntityAnnotation.class, json.getString("entitylink", null));
        token.set(TimeAnnotations.TimexAnnotation.class, toNullable(json.getJsonObject("timex"), obj -> toTimex(obj)));
        return token;
    }

    public CoreMap toSentence(JsonObject json) {
        CoreMap sentence = new ArrayCoreMap();
        // metadata
        sentence.set(CoreAnnotations.SentenceIDAnnotation.class, json.getString("id", null));
        if (json.get("index") != null) {
            sentence.set(CoreAnnotations.SentenceIndexAnnotation.class, json.getInt("index"));
        }
        if (json.get("line") != null) {
            sentence.set(CoreAnnotations.LineNumberAnnotation.class, json.getInt("line"));
        }
        if (json.get("paragraph") != null) {
            sentence.set(CoreAnnotations.ParagraphIndexAnnotation.class, json.getInt("paragraph"));
        }
        sentence.set(CoreAnnotations.SpeakerAnnotation.class, json.getString("speaker", null));
        sentence.set(CoreAnnotations.SpeakerTypeAnnotation.class, json.getString("speakerType", null));

        // (tokens)
        List<CoreLabel> tokens = toNullableList(json.getJsonArray("tokens"), item -> toToken(item));
        sentence.set(CoreAnnotations.TokensAnnotation.class, tokens);

        // (constituency parse)
        String treeParse = json.getString("parse", null);
        if (treeParse != null) {
            sentence.set(TreeCoreAnnotations.TreeAnnotation.class, Trees.readTree(treeParse));
        }

        // (dependency trees)
        sentence.set(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class, toNullable(json.getJsonArray("basicDependencies"), item -> toDependencyParse(item)));
        sentence.set(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class, toNullable(json.getJsonArray("enhancedDependencies"), item -> toDependencyParse(item)));
        sentence.set(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class, toNullable(json.getJsonArray("enhancedPlusPlusDependencies"), item -> toDependencyParse(item)));

        // (TODO: sentiment)

        // (openie)
        sentence.set(NaturalLogicAnnotations.RelationTriplesAnnotation.class, toNullableList(json.getJsonArray("openie"), item -> toRelationTriple(item, tokens) ));
        // (kbp)
        sentence.set(CoreAnnotations.KBPTriplesAnnotation.class, toNullableList(json.getJsonArray("kbp"), item -> toRelationTriple(item, tokens)));

        // (entity mentions)
        sentence.set(CoreAnnotations.MentionsAnnotation.class, toNullableList(json.getJsonArray("entitymentions"), item -> toEntityMention(item)));

        return sentence;
    }

    private static boolean hasSpeakerAnnotations(Annotation annotation) {
        for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
            for (CoreLabel t : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                if (t.get(CoreAnnotations.SpeakerAnnotation.class) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    public Annotation toAnnotation(JsonObject json) {
        Annotation annotation = new Annotation(json.getString("text", null));
        annotation.set(CoreAnnotations.DocIDAnnotation.class, json.getString("docId", null));
        annotation.set(CoreAnnotations.DocDateAnnotation.class, json.getString("docDate", null));
        annotation.set(CoreAnnotations.DocSourceTypeAnnotation.class, json.getString("docSourceType", null));
        annotation.set(CoreAnnotations.DocTypeAnnotation.class, json.getString("docType", null));
        annotation.set(CoreAnnotations.AuthorAnnotation.class, json.getString("author", null));
        annotation.set(CoreAnnotations.LocationAnnotation.class, json.getString("location", null));

        // sentences
        List<CoreMap> sentences = toNullableList(json.getJsonArray("sentences"), item -> toSentence(item) );
        annotation.set(CoreAnnotations.SentencesAnnotation.class, sentences);

        // coref chains
        annotation.set(CorefCoreAnnotations.CorefChainAnnotation.class, toNullableMap(
                json.getJsonObject("corefs"), pair -> {
                    int cid = Integer.valueOf(pair.second);
                    CorefChain chain = toCorefChain(pair.first.getJsonArray(pair.second), cid);
                    return Pair.makePair(cid, chain);
                }));

        // quotations
        annotation.set(CoreAnnotations.QuotationsAnnotation.class, toNullableList(json.getJsonArray("quotes"), item -> toQuotation(item) ));

        // sections
        annotation.set(CoreAnnotations.SectionsAnnotation.class, toNullableList(
                json.getJsonArray("sections"), item -> toSection(item, annotation.get(CoreAnnotations.SentencesAnnotation.class)) ));

        // tokens and text
        if (annotation.get(CoreAnnotations.TokensAnnotation.class) == null) {
            List<CoreLabel> allTokens = sentences.stream().flatMap( sentence -> sentence.get(CoreAnnotations.TokensAnnotation.class).stream() ).collect(Collectors.toList());
            annotation.set(CoreAnnotations.TokensAnnotation.class, allTokens);
        }

        if (hasSpeakerAnnotations(annotation)) {
            annotation.set(CoreAnnotations.UseMarkedDiscourseAnnotation.class, true);
        }

        return annotation;
    }
}
