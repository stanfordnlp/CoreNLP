package edu.stanford.nlp.ling.tokensregex;


import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Collectors;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.tokensregex.SequenceMatchResult;
import edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.CoreNLPProtos;
import edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.ProcessProtobufRequest;

/**
 * This class contains static methods for processing tokensregex requests on a Document.
 */
public class ProcessTokensRegexRequest extends ProcessProtobufRequest {
  public static CoreNLPProtos.TokensRegexResponse.PatternMatch matchPattern(TokenSequencePattern pattern, List<CoreMap> sentences) {
    CoreNLPProtos.TokensRegexResponse.PatternMatch.Builder resultBuilder = CoreNLPProtos.TokensRegexResponse.PatternMatch.newBuilder();
    for (int sentenceIdx = 0; sentenceIdx < sentences.size(); ++sentenceIdx) {
      CoreMap sentence = sentences.get(sentenceIdx);
      List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);      
      TokenSequenceMatcher matcher = pattern.matcher(tokens);
      while (matcher.find()) {
        CoreNLPProtos.TokensRegexResponse.Match.Builder matchBuilder = CoreNLPProtos.TokensRegexResponse.Match.newBuilder();
        matchBuilder.setSentence(sentenceIdx);

        CoreNLPProtos.TokensRegexResponse.MatchLocation.Builder locationBuilder = CoreNLPProtos.TokensRegexResponse.MatchLocation.newBuilder();
        locationBuilder.setText(matcher.group());
        locationBuilder.setBegin(matcher.start());
        locationBuilder.setEnd(matcher.end());
        matchBuilder.setMatch(locationBuilder.build());

        for (int groupIdx = 0; groupIdx < matcher.groupCount(); ++groupIdx) {
          SequenceMatchResult.MatchedGroupInfo<CoreMap> info = matcher.groupInfo(groupIdx + 1);
          locationBuilder = CoreNLPProtos.TokensRegexResponse.MatchLocation.newBuilder();
          locationBuilder.setText(info.text);
          if ( ! info.nodes.isEmpty()) {
            locationBuilder.setBegin(info.nodes.get(0).get(CoreAnnotations.IndexAnnotation.class) - 1);
            locationBuilder.setEnd(info.nodes.get(info.nodes.size() - 1).get(CoreAnnotations.IndexAnnotation.class));
          }
          matchBuilder.addGroup(locationBuilder.build());
        }

        resultBuilder.addMatch(matchBuilder.build());
      }
    }

    return resultBuilder.build();
  }

  
  public static CoreNLPProtos.TokensRegexResponse processRequest(CoreNLPProtos.TokensRegexRequest request) {
    ProtobufAnnotationSerializer serializer = new ProtobufAnnotationSerializer();
    CoreNLPProtos.TokensRegexResponse.Builder responseBuilder = CoreNLPProtos.TokensRegexResponse.newBuilder();

    List<TokenSequencePattern> patterns = request.getPatternList().stream().map(TokenSequencePattern::compile).collect(Collectors.toList());
    Annotation annotation = serializer.fromProto(request.getDoc());
    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
    for (TokenSequencePattern pattern : patterns) {
      CoreNLPProtos.TokensRegexResponse.PatternMatch match = matchPattern(pattern, sentences);
      responseBuilder.addMatch(match);
    }

    return responseBuilder.build();
  }

  @Override
  public void processInputStream(InputStream in, OutputStream out) throws IOException {
    CoreNLPProtos.TokensRegexRequest request = CoreNLPProtos.TokensRegexRequest.parseFrom(in);
    CoreNLPProtos.TokensRegexResponse response = processRequest(request);
    response.writeTo(out);
  }

  public static void main(String[] args) throws IOException {
    ProcessProtobufRequest.process(new ProcessTokensRegexRequest(), args);
  }
}
