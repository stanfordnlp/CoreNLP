package edu.stanford.nlp.trees.ud;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.CoreNLPProtos;
import edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.EnglishPatterns;
import edu.stanford.nlp.trees.UniversalEnglishGrammaticalStructure;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.ProcessProtobufRequest;


public class ProcessUniversalEnhancerRequest extends ProcessProtobufRequest {

  /**
   * Enhance the dependencies on a single sentence, using the given relative pronouns pattern
   */
  public static void enhanceDependencies(Pattern relativePronounsPattern, Annotation annotation) {
    for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
      SemanticGraph basic = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
      SemanticGraph enhanced = UniversalEnhancer.enhanceGraph(basic, null, false, null, relativePronounsPattern);
      sentence.set(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class, enhanced);
    }
  }

  /**
   * Enhance the dependencies on a single sentence, using the English-specific rules for enhancement
   */
  public static void enhanceEnglishDependencies(Annotation annotation) {
    for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
      SemanticGraph basic = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);

      SemanticGraph enhanced = new SemanticGraph(basic);
      UniversalEnglishGrammaticalStructure.addEnhancements(enhanced, UniversalEnglishGrammaticalStructure.ENHANCED_OPTIONS);
      sentence.set(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class, enhanced);

      SemanticGraph plusplus = new SemanticGraph(basic);
      UniversalEnglishGrammaticalStructure.addEnhancements(plusplus, UniversalEnglishGrammaticalStructure.ENHANCED_PLUS_PLUS_OPTIONS);
      sentence.set(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class, plusplus);
    }
  }

  /**
   * Process all sentences in the document, enhancing the basic dependencies on each sentence
   */
  public static CoreNLPProtos.Document processRequest(Pattern relativePronounsPattern, CoreNLPProtos.DependencyEnhancerRequest request) {
    ProtobufAnnotationSerializer serializer = new ProtobufAnnotationSerializer();
    Annotation annotation = serializer.fromProto(request.getDocument());

    if (request.hasLanguage() &&
        (request.getLanguage() == CoreNLPProtos.Language.English || request.getLanguage() == CoreNLPProtos.Language.UniversalEnglish)) {
      enhanceEnglishDependencies(annotation);
    } else {
      enhanceDependencies(relativePronounsPattern, annotation);
    }

    return serializer.toProto(annotation);
  }

  /**
   * Figure out a relative patterns pronoun to use based on the information given in the request
   */
  public static Pattern getRelativePronouns(CoreNLPProtos.DependencyEnhancerRequest request) {
    if (request.hasRelativePronouns()) {
      return Pattern.compile(request.getRelativePronouns());
    }
    if (request.hasLanguage()) {
      switch(request.getLanguage()) {
      case English:
      case UniversalEnglish:
        return EnglishPatterns.RELATIVIZING_WORD_PATTERN;
      case Chinese:
      case UniversalChinese:
        // there are no relative pronouns in Chinese!
        return null;
      default:
        throw new IllegalArgumentException("Relative word pattern not defined for " + request.getLanguage());
      }
    }
    throw new IllegalArgumentException("Could not find Language or predefined relative pronouns pattern in the request");
  }

  /**
   * Process a single request, adding enhanced dependencies to each sentence in the document
   */
  @Override
  public void processInputStream(InputStream in, OutputStream out) throws IOException {
    CoreNLPProtos.DependencyEnhancerRequest request = CoreNLPProtos.DependencyEnhancerRequest.parseFrom(in);

    Pattern relativePronounsPattern = getRelativePronouns(request);

    CoreNLPProtos.Document response = processRequest(relativePronounsPattern, request);
    response.writeTo(out);
  }

  /**
   * The inherited main program will either enhance a single document,
   * or will listen to stdin and enhance every document that comes in
   * until a terminator is sent or the stream closes
   */
  public static void main(String[] args) throws IOException {
    ProcessProtobufRequest.process(new ProcessUniversalEnhancerRequest(), args);
  }
}
