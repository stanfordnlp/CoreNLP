package edu.stanford.nlp.ie;

import junit.framework.TestCase;

import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

public class NumberSequenceClassifierITest extends TestCase {
  public static final boolean VERBOSE = true;

  private static StanfordCoreNLP makeNumericPipeline() {
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize, ssplit, pos, number, qen");
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

    assertTrue(doc.get(CoreAnnotations.SentencesAnnotation.class) != null);
    assertTrue(doc.get(CoreAnnotations.SentencesAnnotation.class).size() > 0);
    CoreMap sent = doc.get(CoreAnnotations.SentencesAnnotation.class).get(0);
    assertTrue(sent.get(CoreAnnotations.TokensAnnotation.class) != null);
    List<CoreLabel> tokens = sent.get(CoreAnnotations.TokensAnnotation.class);
    if(VERBOSE){
      for(CoreLabel token: tokens) {
        System.out.println("\t" + token.word() + " " + 
            token.tag() + " " + 
            token.ner() + " " + 
            (token.containsKey(CoreAnnotations.NumericCompositeTypeAnnotation.class) ? token.get(CoreAnnotations.NumericCompositeValueAnnotation.class) + " " : "") +
            (token.containsKey(TimeAnnotations.TimexAnnotation.class) ? token.get(TimeAnnotations.TimexAnnotation.class) + " " : "")); 
      }
    }
    
    // check NER labels
    assertTrue(tokens.size() == labels.length);
    for(int i = 0; i < labels.length; i ++){
      if(labels[i] == null){
        assertTrue(tokens.get(i).ner() == null);
      } else {
        Pattern p = Pattern.compile(labels[i]);
        System.err.println("COMPARING NER " + labels[i] + " with " + tokens.get(i).ner());
        System.err.flush();
        assertTrue(tokens.get(i).ner() != null);
        assertTrue(p.matcher(tokens.get(i).ner()).matches());
      }
    }
    
    // check normalized values, if gold is given
    if(normed != null){
      assertTrue(tokens.size() == normed.length);
      for(int i = 0; i < normed.length; i ++){
        if(normed[i] == null){
          assertTrue(tokens.get(i).get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class) == null);
        } else {
          Pattern p = Pattern.compile(normed[i]);
          String n = tokens.get(i).get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class);
          System.err.println("COMPARING NORMED \"" + normed[i] + "\" with \"" + n + "\"");
          System.err.flush();
          assertTrue(n != null);
          assertTrue(p.matcher(n).matches());
        }
      }
    }
  }

  private static void run(String header, String [] texts, String [][] answers, String [][] normed) {
    StanfordCoreNLP pipe = makeNumericPipeline();
    for(int i = 0; i < texts.length; i ++) {
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
    "It cost # 1500",
    "It cost \u00A3 1500",
    "It cost \u00A3 .50",
    "It cost # .50",
    "It cost $ 1500",
    "It cost $1500",
    "It cost $ 1,500",
    "It cost $1,500",
    "It cost $48.75",
    "It cost $ 57.60",
    "It cost $8 thousand",
    "It cost $42,33"
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
    { null, null, "MONEY", "MONEY" }
  };
  private static final String [][] moneyNormed = {
    { null, null, "\\$5.0", null },
    { null, null, "\\$0.24", null },
    { null, null, "\\$0.18", null },
    { null, null, "\u00A35.4", null },
    { null, null, "\u00A31.0E10", null, null, null },
    { null, null, "\\$1.0E10", null, null, null },
    { null, null, "\\$4000000.0", null, null },
    { null, null, "\\$1000000.0", null },
    { null, null, "\\$0.5", null },
    { null, null, "\u00A31500.0", null },
    { null, null, "\u00A31500.0", null },
    { null, null, "\u00A30.5", null },
    { null, null, "\u00A30.5", null },
    { null, null, "\\$1500.0", null },
    { null, null, "\\$1500.0", null },
    { null, null, "\\$1500.0", null },
    { null, null, "\\$1500.0", null },
    { null, null, "\\$48.75", null },
    { null, null, "\\$57.6", null },
    { null, null, "\\$8000.0", null, null },
    { null, null, "\\$4233.0", null }
  };
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
    { null, null, null, "ORDINAL|DURATION", null },
    { null, null, null, "ORDINAL|DURATION", null },
    { null, null, null, "ORDINAL", null },
  };
  private static final String [][] ordinalNormed = {
    { null, null, null, "2.0", null },
    { null, null, null, "2.0", null },
    { null, null, null, "22.0", null },
    { null, null, null, "0.0", null },
    { null, null, null, "1000.0", null },
  };
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
    "next year"
  };
  private static final String [][] dateAnswers = {
    { "DATE" , "DATE", "DATE", "DATE" },
    { "DATE" , "DATE", "DATE", "DATE" },
    { "DATE" , "DATE", "DATE" },
    { "DATE" , "DATE", "DATE", "DATE" },
    { "DATE" , "DATE" },
    { "DATE" , "DATE", "DATE" },
    { "DATE" , "DATE", "DATE", "DATE" },
    { "DATE" , "DATE" },
    { "DATE" },
    { "DATE" },
    { "NUMBER", "DATE", "DATE" },
    { "DATE" },
    { "DATE" },
    { "DATE" , "DATE" },
    { "DATE" , "DATE" },    
  };
  private static final String [][] dateNormed = {
    { "2010-01-14" , null, null, null },
    { "2009-07-14" , null, null, null },
    { "2008-06-06" , null, null },
    { "1923-02-05" , null, null, null },
    { "XXXX-03-03" , null },
    { "2005-07-18" , null, null },
    { "XX05-09-18" , null, null, null },
    { "XXXX-01-13" , null },
    { "2009-07-19" },
    { "2007-06-16" },
    { "32.0", "2010-07", null },
    { "OFFSET P-1D" },
    { "OFFSET P+1D" },
    { "THIS P1Y OFFSET P-1Y" , null },
    { "THIS P1Y OFFSET P+1Y" , null }, 
    
  };
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
    { "100000.0", null, null },
    { "1300000.0", null },
    { "1.0E10", null, null },
    { "3.625" },
    { "-15.0" },
    { "117.0 - 111.0" },
    { null, "867.0", null, "5309.0" },
    { null, null, null, null, "867.0 - 5309.0" },
    { "801.0", null, "123.0", null }
  };
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
  public void testTime() {
    run("TIME", timeStrings, timeAnswers, timeNormed);
  }
}
