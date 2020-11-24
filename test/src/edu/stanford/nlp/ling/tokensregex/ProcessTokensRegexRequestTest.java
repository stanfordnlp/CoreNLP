package edu.stanford.nlp.ling.tokensregex;

import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.CoreNLPProtos;
import edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.PropertiesUtils;

public class ProcessTokensRegexRequestTest {
  public static CoreNLPProtos.TokensRegexRequest buildRequest(Annotation ann, String ... patterns) {
    ProtobufAnnotationSerializer serializer = new ProtobufAnnotationSerializer();
    CoreNLPProtos.TokensRegexRequest.Builder builder = CoreNLPProtos.TokensRegexRequest.newBuilder();
    for (String pattern : patterns) {
      builder.addPattern(pattern);
    }
    CoreNLPProtos.Document doc = serializer.toProto(ann);
    builder.setDoc(doc);
    return builder.build();
  }

  static final Properties props = PropertiesUtils.asProperties("annotators", "tokenize,ssplit");
  static final StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

  /** Test a single request that gets a single result */
  @Test
  public void testSimpleRequest() {
    Annotation ann = pipeline.process("This is a small test");
    CoreNLPProtos.TokensRegexRequest request = buildRequest(ann, "/small/");
    CoreNLPProtos.TokensRegexResponse response = ProcessTokensRegexRequest.processRequest(request);

    Assert.assertEquals(response.getMatchList().size(), 1);
    CoreNLPProtos.TokensRegexResponse.PatternMatch patternMatch = response.getMatchList().get(0);
    Assert.assertEquals(patternMatch.getMatchList().size(), 1);
    CoreNLPProtos.TokensRegexResponse.Match match = patternMatch.getMatchList().get(0);
    Assert.assertEquals(match.getSentence(), 0);
    Assert.assertEquals(match.getMatch().getText(), "small");
    Assert.assertEquals(match.getMatch().getBegin(), 3);
    Assert.assertEquals(match.getMatch().getEnd(), 4);
  }

  
  /** Test a single request that gets a two results */
  @Test
  public void testTwoResultRequest() {
    Annotation ann = pipeline.process("This is a small test");
    CoreNLPProtos.TokensRegexRequest request = buildRequest(ann, "/small|test/");
    CoreNLPProtos.TokensRegexResponse response = ProcessTokensRegexRequest.processRequest(request);

    Assert.assertEquals(response.getMatchList().size(), 1);
    CoreNLPProtos.TokensRegexResponse.PatternMatch patternMatch = response.getMatchList().get(0);
    Assert.assertEquals(patternMatch.getMatchList().size(), 2);

    CoreNLPProtos.TokensRegexResponse.Match match = patternMatch.getMatchList().get(0);
    Assert.assertEquals(match.getSentence(), 0);
    Assert.assertEquals(match.getMatch().getText(), "small");
    Assert.assertEquals(match.getMatch().getBegin(), 3);
    Assert.assertEquals(match.getMatch().getEnd(), 4);

    match = patternMatch.getMatchList().get(1);
    Assert.assertEquals(match.getSentence(), 0);
    Assert.assertEquals(match.getMatch().getText(), "test");
    Assert.assertEquals(match.getMatch().getBegin(), 4);
    Assert.assertEquals(match.getMatch().getEnd(), 5);
  }

  
  /** Test two patterns that get one result each */
  @Test
  public void testTwoRequests() {
    Annotation ann = pipeline.process("This is a small test");
    CoreNLPProtos.TokensRegexRequest request = buildRequest(ann, "/small/", "/test/");
    CoreNLPProtos.TokensRegexResponse response = ProcessTokensRegexRequest.processRequest(request);

    Assert.assertEquals(response.getMatchList().size(), 2);
    CoreNLPProtos.TokensRegexResponse.PatternMatch patternMatch = response.getMatchList().get(0);
    Assert.assertEquals(patternMatch.getMatchList().size(), 1);

    CoreNLPProtos.TokensRegexResponse.Match match = patternMatch.getMatchList().get(0);
    Assert.assertEquals(match.getSentence(), 0);
    Assert.assertEquals(match.getMatch().getText(), "small");
    Assert.assertEquals(match.getMatch().getBegin(), 3);
    Assert.assertEquals(match.getMatch().getEnd(), 4);

    patternMatch = response.getMatchList().get(1);
    Assert.assertEquals(patternMatch.getMatchList().size(), 1);

    match = patternMatch.getMatchList().get(0);
    Assert.assertEquals(match.getSentence(), 0);
    Assert.assertEquals(match.getMatch().getText(), "test");
    Assert.assertEquals(match.getMatch().getBegin(), 4);
    Assert.assertEquals(match.getMatch().getEnd(), 5);
  }
}
