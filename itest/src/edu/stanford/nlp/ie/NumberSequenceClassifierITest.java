package edu.stanford.nlp.ie;

import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

public class NumberSequenceClassifierITest {

  public static final boolean VERBOSE = true;

  private static StanfordCoreNLP makeNumericPipeline() {
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize, ssplit, pos, number, qen");
    props.setProperty("tokenize.options", "splitHyphenated=false");
    props.setProperty("customAnnotatorClass.number",
        "edu.stanford.nlp.pipeline.NumberAnnotator");
    props.setProperty("customAnnotatorClass.qen",
        "edu.stanford.nlp.pipeline.QuantifiableEntityNormalizingAnnotator");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    return pipeline;
  }

  private static void checkLabels(StanfordCoreNLP pipe, String text, String [] labels, String [] normed) {
    Annotation doc = new Annotation(text);
    pipe.annotate(doc);

    Assert.assertNotNull(doc.get(CoreAnnotations.SentencesAnnotation.class));
    Assert.assertFalse(doc.get(CoreAnnotations.SentencesAnnotation.class).isEmpty());
    CoreMap sent = doc.get(CoreAnnotations.SentencesAnnotation.class).get(0);
    Assert.assertNotNull(sent.get(CoreAnnotations.TokensAnnotation.class));
    List<CoreLabel> tokens = sent.get(CoreAnnotations.TokensAnnotation.class);
    if (VERBOSE) {
      for(CoreLabel token: tokens) {
        System.out.println('\t' + token.word() + ' ' +
            token.tag() + ' ' +
            token.ner() + ' ' +
            (token.containsKey(CoreAnnotations.NumericCompositeTypeAnnotation.class) ? token.get(CoreAnnotations.NumericCompositeValueAnnotation.class) + " " : "") +
            (token.containsKey(TimeAnnotations.TimexAnnotation.class) ? token.get(TimeAnnotations.TimexAnnotation.class) + " " : ""));
      }
    }

    // check NER labels
    Assert.assertEquals(tokens.size(), labels.length);
    for (int i = 0; i < labels.length; i ++) {
      if(labels[i] == null){
        Assert.assertNull(tokens.get(i).ner());
      } else {
        Pattern p = Pattern.compile(labels[i]);
        System.err.println("COMPARING NER " + labels[i] + " with " + tokens.get(i).ner());
        System.err.flush();
        Assert.assertNotNull("NER should not be null for token " + tokens.get(i) + " in sentence " + tokens, tokens.get(i).ner());
        Assert.assertTrue(tokens.get(i).ner() + " does not match " + p + " for token " + tokens.get(i) + " in sentence " + tokens, p.matcher(tokens.get(i).ner()).matches());
      }
    }

    // check normalized values, if gold is given
    if (normed != null) {
      Assert.assertEquals(tokens.size(), normed.length);
      for(int i = 0; i < normed.length; i ++){
        if(normed[i] == null){
          Assert.assertNull(tokens.get(i).get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class));
        } else {
          Pattern p = Pattern.compile(normed[i]);
          String n = tokens.get(i).get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class);
          String message = "COMPARING NORMED \"" + normed[i] + "\" with \"" + n + '"';
          Assert.assertNotNull(message + "; latter should not be null", n);
          Assert.assertTrue(message + "; latter should match", p.matcher(n).matches());
        }
      }
    }
  }

  private static void run(String header, String [] texts, String [][] answers, String [][] normed) {
    StanfordCoreNLP pipe = makeNumericPipeline();
    for (int i = 0; i < texts.length; i ++) {
      if(VERBOSE) {
        System.out.println("Running test " + header + " for text: " + texts[i]);
      }
      checkLabels(pipe,
          texts[i],
          answers[i],
          normed != null ? normed[i] : null);
    }
  }

  private static final String [] moneyStrings = {
    "It cost $5",
    "It cost 24 cents",
    "It cost 18\u00A2",
    "It cost \u00A35.40",
    "It cost 10 thousand million pounds",
    "It cost 10 thousand million dollars",
    "It cost four million dollars",
    "It cost $1m",
    "It cost 50 cents",
    "It cost £ 1500",
    "It cost \u00A3 1500",
    "It cost \u00A3 .50",
    "It cost € .50",
    "It cost $ 1500",
    "It cost $1500",
    "It cost $ 1,500",
    "It cost $1,500",
    "It cost $48.75",
    "It cost $ 57.60",
    "It cost $8 thousand",
    "It cost $42,33",
    "It cost ₩1500",  // TODO: Add won symbol to PTBTokenizer
  };

  private static final String [][] moneyAnswers = {
    { null, null, "MONEY", "MONEY" },
    { null, null, "MONEY", "MONEY" },
    { null, null, "MONEY", "MONEY" },
    { null, null, "MONEY", "MONEY" },
    { null, null, "MONEY", "MONEY", "MONEY", "MONEY" },
    { null, null, "MONEY", "MONEY", "MONEY", "MONEY" },
    { null, null, "MONEY", "MONEY", "MONEY" },
    { null, null, "MONEY", "MONEY" },
    { null, null, "MONEY", "MONEY" },
    { null, null, "MONEY", "MONEY" },
    { null, null, "MONEY", "MONEY" },
    { null, null, "MONEY", "MONEY" },
    { null, null, "MONEY", "MONEY" },
    { null, null, "MONEY", "MONEY" },
    { null, null, "MONEY", "MONEY" },
    { null, null, "MONEY", "MONEY" },
    { null, null, "MONEY", "MONEY" },
    { null, null, "MONEY", "MONEY" },
    { null, null, "MONEY", "MONEY" },
    { null, null, "MONEY", "MONEY", "MONEY" },
    { null, null, "MONEY", "MONEY" },
    { null, null, "MONEY", "MONEY" },
  };

  private static final String [][] moneyNormed = {
    { null, null, "\\$5.0", "\\$5.0" },
    { null, null, "\\$0.24", "\\$0.24" },
    { null, null, "\\$0.18", "\\$0.18" },
    { null, null, "\u00A35.4", "\u00A35.4" },
    { null, null, "\u00A31.0E10", "\u00A31.0E10", "\u00A31.0E10", "\u00A31.0E10" },
    { null, null, "\\$1.0E10", "\\$1.0E10", "\\$1.0E10", "\\$1.0E10" },
    { null, null, "\\$4000000.0", "\\$4000000.0", "\\$4000000.0" },
    { null, null, "\\$1000000.0", "\\$1000000.0" },
    { null, null, "\\$0.5", "\\$0.5" },
    { null, null, "\u00A31500.0", "\u00A31500.0" },
    { null, null, "\u00A31500.0", "\u00A31500.0" },
    { null, null, "\u00A30.5", "\u00A30.5" },
    { null, null, "€0.5", "€0.5" },
    { null, null, "\\$1500.0", "\\$1500.0" },
    { null, null, "\\$1500.0", "\\$1500.0" },
    { null, null, "\\$1500.0", "\\$1500.0" },
    { null, null, "\\$1500.0", "\\$1500.0" },
    { null, null, "\\$48.75", "\\$48.75" },
    { null, null, "\\$57.6", "\\$57.6" },
    { null, null, "\\$8000.0", "\\$8000.0", "\\$8000.0" },
    { null, null, "\\$4233.0", "\\$4233.0" },
    { null, null, "₩1500.0", "₩1500.0" },
  };

  @Test
  public void testMoney() {
    run("MONEY", moneyStrings, moneyAnswers, moneyNormed);
  }

  private static final String [] ordinalStrings = {
    "It was the 2nd time",
    "It was the second time",
    "It was the twenty-second time",
    "It was the 0th time",
    "It was the 1000th time"
  };
  private static final String [][] ordinalAnswers = {
    { null, null, null, "ORDINAL", null },
    { null, null, null, "ORDINAL", null },
    { null, null, null, "ORDINAL", null },
    { null, null, null, "ORDINAL", null },
    { null, null, null, "ORDINAL", null },
  };
  private static final String [][] ordinalNormed = {
    { null, null, null, "2.0", null },
    { null, null, null, "2.0", null },
    { null, null, null, "22.0", null },
    { null, null, null, "0.0", null },
    { null, null, null, "1000.0", null },
  };
  @Test
  public void testOrdinal() {
    run("ORDINAL", ordinalStrings, ordinalAnswers, ordinalNormed);
  }

  private static final String [] dateStrings = {
    "January 14, 2010",
    "14 July, 2009",
    "6 June 2008",
    "February 5, 1923",
    "Mar 3",
    "18 July 2005",
    "18 Sep '05",
    "Jan. 13",
    "2009-07-19",
    "2007-06-16",
    "32 July 2010",
    "yesterday",
    "tomorrow",
    "last year",
    "next year",
    "6 June 2008, 7 June 2008",
  };
  private static final String [][] dateAnswers = {
    { "DATE" , "DATE", "DATE", "DATE" },
    { "DATE" , "DATE", "DATE", "DATE" },
    { "DATE" , "DATE", "DATE" },
    { "DATE" , "DATE", "DATE", "DATE" },
    { "DATE" , "DATE" },
    { "DATE" , "DATE", "DATE" },
    { "DATE" , "DATE", "DATE" },
    { "DATE" , "DATE" },
    { "DATE" },
    { "DATE" },
    { "NUMBER", "DATE", "DATE" },
    { "DATE" },
    { "DATE" },
    { "DATE" , "DATE" },
    { "DATE" , "DATE" },
    { "DATE" , "DATE", "DATE", null, "DATE", "DATE", "DATE" },
  };
  private static final String [][] dateNormed = {
    { "2010-01-14" , "2010-01-14", "2010-01-14", "2010-01-14" },
    { "2009-07-14" , "2009-07-14", "2009-07-14", "2009-07-14" },
    { "2008-06-06" , "2008-06-06", "2008-06-06" },
    { "1923-02-05" , "1923-02-05", "1923-02-05", "1923-02-05" },
    { "XXXX-03-03" , "XXXX-03-03" },
    { "2005-07-18" , "2005-07-18", "2005-07-18" },
    { "XX05-09-18" , "XX05-09-18", "XX05-09-18" },
    { "XXXX-01-13" , "XXXX-01-13" },
    { "2009-07-19" },
    { "2007-06-16" },
    { "32.0", "2010-07", "2010-07" },
    { "OFFSET P-1D" },
    { "OFFSET P+1D" },
    { "THIS P1Y OFFSET P-1Y" , "THIS P1Y OFFSET P-1Y" },
    { "THIS P1Y OFFSET P+1Y" , "THIS P1Y OFFSET P+1Y" },
    { "2008-06-06" , "2008-06-06", "2008-06-06", null, "2008-06-07" , "2008-06-07", "2008-06-07" },
  };
  @Test
  public void testDate() {
    run("DATE", dateStrings, dateAnswers, dateNormed);
  }

  private static final String [] numberStrings = {
    "one hundred thousand",
    "1.3 million",
    "10 thousand million",
    "3.625",
    "-15",
    "117-111",
    "<b>867</b>5309",
    "her phone number is 867-5309",
    "801 <b> 123 </b>"
  };
  private static final String [][] numberAnswers = {
    { "NUMBER", "NUMBER", "NUMBER" },
    { "NUMBER", "NUMBER" },
    { "NUMBER", "NUMBER", "NUMBER" },
    { "NUMBER" },
    { "NUMBER" },
    { "NUMBER" },
    { null, "NUMBER", null, "NUMBER" },
    { null, null, null, null, "NUMBER" },
    { "NUMBER", null, "NUMBER", null }
  };
  private static final String [][] numberNormed = {
    { "100000.0", "100000.0", "100000.0" },
    { "1300000.0", "1300000.0" },
    { "1.0E10", "1.0E10", "1.0E10" },
    { "3.625" },
    { "-15.0" },
    { "117.0 - 111.0" },
    { null, "867.0", null, "5309.0" },
    { null, null, null, null, "867.0 - 5309.0" },
    { "801.0", null, "123.0", null }
  };
  @Test
  public void testNumber() {
    run("NUMBER", numberStrings, numberAnswers, numberNormed);
  }

  private static final String [] timeStrings = {
    "the time was 10:20",
    "12:29 p.m.",
    "12:39 AM",
  };
  private static final String [][] timeAnswers = {
    { null, null, null, "TIME" },
    { "TIME", "TIME" },
    { "TIME", "TIME" },
  };
  private static final String [][] timeNormed = {
    { null, null, null, "T10:20" },
    { "T12:29", "T12:29" },
    { "T00:39", "T00:39" },
  };
  @Test
  public void testTime() {
    run("TIME", timeStrings, timeAnswers, timeNormed);
  }

  private static final String [] durationStrings = {
          "the past four days was very sunny",
          "it has been more than seven years",
          "it took one month",
  };
  private static final String [][] durationAnswers = {
          { "DURATION", "DURATION", "DURATION", "DURATION", null, null, null },
          { null, null, null, "DURATION", "DURATION", "DURATION", "DURATION" },
          { null, null, "DURATION", "DURATION" },
  };
  private static final String [][] durationNormed = {
          { "P4D", "P4D", "P4D", "P4D", null, null, null },
          { null, null, null, "P7Y", "P7Y", "P7Y", "P7Y" },
          { null, null, "P1M", "P1M" },
  };
  @Test
  public void testDuration() {
    run("DURATION", durationStrings, durationAnswers, durationNormed);
  }

}
