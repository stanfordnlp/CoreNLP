package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ie.NERClassifierCombiner;
import edu.stanford.nlp.ie.regexp.NumberSequenceClassifier;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.WordToSentenceProcessor;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;

/**
 * A companion to {@link AnnotatorFactory} defining the common annotators.
 * These are primarily used in {@link StanfordCoreNLP#getDefaultAnnotatorPool(java.util.Properties, AnnotatorImplementations)}.
 *
 * @author Gabor Angeli
 */
public class AnnotatorFactories {

  private AnnotatorFactories() {} // static factory class

  public static AnnotatorFactory tokenize(Properties properties, final AnnotatorImplementations annotatorImplementation) {
    return new AnnotatorFactory(properties, annotatorImplementation) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        String extraOptions = null;
        boolean keepNewline = Boolean.valueOf(properties.getProperty(StanfordCoreNLP.NEWLINE_SPLITTER_PROPERTY, "false")); // ssplit.eolonly

        String hasSsplit = properties.getProperty("annotators");
        if (hasSsplit != null && hasSsplit.contains(StanfordCoreNLP.STANFORD_SSPLIT)) { // ssplit
          // Only possibly put in *NL* if not all one (the Boolean method treats null as false)
          if ( ! Boolean.parseBoolean(properties.getProperty("ssplit.isOneSentence"))) {
            // Set to { NEVER, ALWAYS, TWO_CONSECUTIVE } based on  ssplit.newlineIsSentenceBreak
            String nlsbString = properties.getProperty(StanfordCoreNLP.NEWLINE_IS_SENTENCE_BREAK_PROPERTY,
                    StanfordCoreNLP.DEFAULT_NEWLINE_IS_SENTENCE_BREAK);
            WordToSentenceProcessor.NewlineIsSentenceBreak nlsb = WordToSentenceProcessor.stringToNewlineIsSentenceBreak(nlsbString);
            if (nlsb != WordToSentenceProcessor.NewlineIsSentenceBreak.NEVER) {
              keepNewline = true;
            }
          }
        }
        if (keepNewline) {
          extraOptions = "tokenizeNLs,";
        }
        return annotatorImplementation.tokenizer(properties, false, extraOptions);
      }

      @Override
      public String additionalSignature() {
        // keep track of all relevant properties for this annotator here!
        StringBuilder os = new StringBuilder();
        os.append("tokenize.whitespace:").append(properties.getProperty("tokenize.whitespace", "false"));
        if (properties.getProperty("tokenize.options") != null) {
          os.append(":tokenize.options:").append(properties.getProperty("tokenize.options"));
        }
        if (properties.getProperty("tokenize.language") != null) {
          os.append(":tokenize.language:").append(properties.getProperty("tokenize.language"));
        }
        if (properties.getProperty("tokenize.class") != null) {
          os.append(":tokenize.class:").append(properties.getProperty("tokenize.class"));
        }
        if (Boolean.valueOf(properties.getProperty("tokenize.whitespace", "false"))) {
          os.append(TokenizerAnnotator.EOL_PROPERTY + ':').append(properties.getProperty(TokenizerAnnotator.EOL_PROPERTY, "false"));
          os.append(StanfordCoreNLP.NEWLINE_SPLITTER_PROPERTY + ':');
          os.append(properties.getProperty(StanfordCoreNLP.NEWLINE_SPLITTER_PROPERTY, "false"));
        } else {
          os.append(StanfordCoreNLP.NEWLINE_SPLITTER_PROPERTY + ':');
          os.append(properties.getProperty(StanfordCoreNLP.NEWLINE_SPLITTER_PROPERTY, "false"));
          os.append("ssplit.isOneSentence" + ':' + properties.getProperty("ssplit.isOneSentence", "false"));
          os.append(StanfordCoreNLP.NEWLINE_IS_SENTENCE_BREAK_PROPERTY + ':');
          os.append(properties.getProperty(StanfordCoreNLP.NEWLINE_IS_SENTENCE_BREAK_PROPERTY, StanfordCoreNLP.DEFAULT_NEWLINE_IS_SENTENCE_BREAK));
        }
        return os.toString();
      }
    };
  }





  public static AnnotatorFactory cleanXML(Properties properties, final AnnotatorImplementations annotatorImplementation) {
    return new AnnotatorFactory(properties, annotatorImplementation) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        String xmlTags =
            properties.getProperty("clean.xmltags",
                CleanXmlAnnotator.DEFAULT_XML_TAGS);
        String sentenceEndingTags =
            properties.getProperty("clean.sentenceendingtags",
                CleanXmlAnnotator.DEFAULT_SENTENCE_ENDERS);
        String singleSentenceTags =
            properties.getProperty("clean.singlesentencetags",
                CleanXmlAnnotator.DEFAULT_SINGLE_SENTENCE_TAGS);
        String allowFlawedString = properties.getProperty("clean.allowflawedxml");
        boolean allowFlawed = CleanXmlAnnotator.DEFAULT_ALLOW_FLAWS;
        if (allowFlawedString != null)
          allowFlawed = Boolean.valueOf(allowFlawedString);
        String dateTags =
            properties.getProperty("clean.datetags",
                CleanXmlAnnotator.DEFAULT_DATE_TAGS);
        String docIdTags =
            properties.getProperty("clean.docIdtags",
                CleanXmlAnnotator.DEFAULT_DOCID_TAGS);
        String docTypeTags =
            properties.getProperty("clean.docTypetags",
                CleanXmlAnnotator.DEFAULT_DOCTYPE_TAGS);
        String utteranceTurnTags =
            properties.getProperty("clean.turntags",
                CleanXmlAnnotator.DEFAULT_UTTERANCE_TURN_TAGS);
        String speakerTags =
            properties.getProperty("clean.speakertags",
                CleanXmlAnnotator.DEFAULT_SPEAKER_TAGS);
        String docAnnotations =
            properties.getProperty("clean.docAnnotations",
                CleanXmlAnnotator.DEFAULT_DOC_ANNOTATIONS_PATTERNS);
        String tokenAnnotations =
            properties.getProperty("clean.tokenAnnotations",
                CleanXmlAnnotator.DEFAULT_TOKEN_ANNOTATIONS_PATTERNS);
        String sectionTags =
            properties.getProperty("clean.sectiontags",
                CleanXmlAnnotator.DEFAULT_SECTION_TAGS);
        String sectionAnnotations =
            properties.getProperty("clean.sectionAnnotations",
                CleanXmlAnnotator.DEFAULT_SECTION_ANNOTATIONS_PATTERNS);
        String ssplitDiscardTokens =
            properties.getProperty("clean.ssplitDiscardTokens");
        CleanXmlAnnotator annotator = annotatorImplementation.cleanXML(properties, xmlTags,
            sentenceEndingTags,
            dateTags,
            allowFlawed);
        annotator.setSingleSentenceTagMatcher(singleSentenceTags);
        annotator.setDocIdTagMatcher(docIdTags);
        annotator.setDocTypeTagMatcher(docTypeTags);
        annotator.setDiscourseTags(utteranceTurnTags, speakerTags);
        annotator.setDocAnnotationPatterns(docAnnotations);
        annotator.setTokenAnnotationPatterns(tokenAnnotations);
        annotator.setSectionTagMatcher(sectionTags);
        annotator.setSectionAnnotationPatterns(sectionAnnotations);
        annotator.setSsplitDiscardTokensMatcher(ssplitDiscardTokens);
        return annotator;
      }

      @Override
      public String additionalSignature() {
        // keep track of all relevant properties for this annotator here!
        return "clean.xmltags:" +
            properties.getProperty("clean.xmltags",
                CleanXmlAnnotator.DEFAULT_XML_TAGS) +
            "clean.sentenceendingtags:" +
            properties.getProperty("clean.sentenceendingtags",
                CleanXmlAnnotator.DEFAULT_SENTENCE_ENDERS) +
            "clean.sentenceendingtags:" +
            properties.getProperty("clean.singlesentencetags",
                CleanXmlAnnotator.DEFAULT_SINGLE_SENTENCE_TAGS) +
            "clean.allowflawedxml:" +
            properties.getProperty("clean.allowflawedxml", "") +
            "clean.datetags:" +
            properties.getProperty("clean.datetags",
                CleanXmlAnnotator.DEFAULT_DATE_TAGS) +
            "clean.docidtags:" +
            properties.getProperty("clean.docid",
                CleanXmlAnnotator.DEFAULT_DOCID_TAGS) +
            "clean.doctypetags:" +
            properties.getProperty("clean.doctype",
                CleanXmlAnnotator.DEFAULT_DOCTYPE_TAGS) +
            "clean.turntags:" +
            properties.getProperty("clean.turntags",
                CleanXmlAnnotator.DEFAULT_UTTERANCE_TURN_TAGS) +
            "clean.speakertags:" +
            properties.getProperty("clean.speakertags",
                CleanXmlAnnotator.DEFAULT_SPEAKER_TAGS) +
            "clean.docAnnotations:" +
            properties.getProperty("clean.docAnnotations",
                CleanXmlAnnotator.DEFAULT_DOC_ANNOTATIONS_PATTERNS) +
            "clean.tokenAnnotations:" +
            properties.getProperty("clean.tokenAnnotations",
                CleanXmlAnnotator.DEFAULT_TOKEN_ANNOTATIONS_PATTERNS) +
            "clean.sectiontags:" +
            properties.getProperty("clean.sectiontags",
                CleanXmlAnnotator.DEFAULT_SECTION_TAGS) +
            "clean.sectionAnnotations:" +
            properties.getProperty("clean.sectionAnnotations",
                CleanXmlAnnotator.DEFAULT_SECTION_ANNOTATIONS_PATTERNS);
      }
    };
  }

  //
  // Sentence splitter: splits the above sequence of tokens into
  // sentences.  This is required when processing entire documents or
  // text consisting of multiple sentences.
  //
  public static AnnotatorFactory sentenceSplit(Properties properties, final AnnotatorImplementations annotatorImplementation) {
    return new AnnotatorFactory(properties, annotatorImplementation) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        // System.err.println(signature());
        // todo: The above shows that signature is edu.stanford.nlp.pipeline.AnnotatorImplementations: and doesn't reflect what annotator it is! Should fix.
        boolean nlSplitting = Boolean.valueOf(properties.getProperty(StanfordCoreNLP.NEWLINE_SPLITTER_PROPERTY, "false"));
        if (nlSplitting) {
          boolean whitespaceTokenization = Boolean.valueOf(properties.getProperty("tokenize.whitespace", "false"));
          if (whitespaceTokenization) {
            if (System.getProperty("line.separator").equals("\n")) {
              return WordsToSentencesAnnotator.newlineSplitter(false, "\n");
            } else {
              // throw "\n" in just in case files use that instead of
              // the system separator
              return WordsToSentencesAnnotator.newlineSplitter(false, System.getProperty("line.separator"), "\n");
            }
          } else {
            return WordsToSentencesAnnotator.newlineSplitter(false, PTBTokenizer.getNewlineToken());
          }

        } else {
          // Treat as one sentence: You get a no-op sentence splitter that always returns all tokens as one sentence.
          String isOneSentence = properties.getProperty("ssplit.isOneSentence");
          if (Boolean.parseBoolean(isOneSentence)) { // this method treats null as false
            return WordsToSentencesAnnotator.nonSplitter(false);
          }

          // multi token sentence boundaries
          String boundaryMultiTokenRegex = properties.getProperty("ssplit.boundaryMultiTokenRegex");

          // Discard these tokens without marking them as sentence boundaries
          String tokenPatternsToDiscardProp = properties.getProperty("ssplit.tokenPatternsToDiscard");
          Set<String> tokenRegexesToDiscard = null;
          if (tokenPatternsToDiscardProp != null){
            String [] toks = tokenPatternsToDiscardProp.split(",");
            tokenRegexesToDiscard = Generics.newHashSet(Arrays.asList(toks));
          }
          // regular boundaries
          String boundaryTokenRegex = properties.getProperty("ssplit.boundaryTokenRegex");
          Set<String> boundariesToDiscard = null;

          // newline boundaries which are discarded.
          String bounds = properties.getProperty("ssplit.boundariesToDiscard");
          if (bounds != null) {
            String [] toks = bounds.split(",");
            boundariesToDiscard = Generics.newHashSet(Arrays.asList(toks));
          }
          Set<String> htmlElementsToDiscard = null;
          // HTML boundaries which are discarded
          bounds = properties.getProperty("ssplit.htmlBoundariesToDiscard");
          if (bounds != null) {
            String [] elements = bounds.split(",");
            htmlElementsToDiscard = Generics.newHashSet(Arrays.asList(elements));
          }
          String nlsb = properties.getProperty(StanfordCoreNLP.NEWLINE_IS_SENTENCE_BREAK_PROPERTY,
              StanfordCoreNLP.DEFAULT_NEWLINE_IS_SENTENCE_BREAK);

          return annotatorImplementation.wordToSentences(properties,
              false, boundaryTokenRegex, boundariesToDiscard, htmlElementsToDiscard,
              nlsb, boundaryMultiTokenRegex, tokenRegexesToDiscard);
        }
      }

      @Override
      public String additionalSignature() {
        // keep track of all relevant properties for this annotator here!
        StringBuilder os = new StringBuilder();
        if (Boolean.valueOf(properties.getProperty(StanfordCoreNLP.NEWLINE_SPLITTER_PROPERTY, "false"))) {
          os.append(StanfordCoreNLP.NEWLINE_SPLITTER_PROPERTY + '=').append(properties.getProperty(StanfordCoreNLP.NEWLINE_SPLITTER_PROPERTY, "false")).append('\n');
          os.append("tokenize.whitespace=").append(properties.getProperty("tokenize.whitespace", "false")).append('\n');
        } else {
          os.append(baseSignature(properties, StanfordCoreNLP.STANFORD_SSPLIT));
        }
        return os.toString();
      }
    };
  }

  //
  // POS tagger
  //
  public static AnnotatorFactory posTag(Properties properties, final AnnotatorImplementations annotatorImplementation) {
    return new AnnotatorFactory(properties, annotatorImplementation) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        try {
          return annotatorImplementation.posTagger(properties);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public String additionalSignature() {
        // keep track of all relevant properties for this annotator here!
        return POSTaggerAnnotator.signature(properties);
      }
    };
  }

  //
  // Lemmatizer
  //
  public static AnnotatorFactory lemma(Properties properties, final AnnotatorImplementations annotatorImplementation) {
    return new AnnotatorFactory(properties, annotatorImplementation) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        return annotatorImplementation.morpha(properties, false);
      }

      @Override
      public String additionalSignature() {
        // keep track of all relevant properties for this annotator here!
        // nothing for this one
        return "";
      }
    };
  }

  //
  // NER
  //
  public static AnnotatorFactory nerTag(Properties properties, final AnnotatorImplementations annotatorImplementation) {
    return new AnnotatorFactory(properties, annotatorImplementation) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        try {
          return annotatorImplementation.ner(properties);
        } catch (FileNotFoundException e) {
          throw new RuntimeIOException(e);
        }
      }

      @Override
      public String additionalSignature() {
        // keep track of all relevant properties for this annotator here!
        return "ner.model:" +
            properties.getProperty("ner.model", "") +
            NERClassifierCombiner.APPLY_NUMERIC_CLASSIFIERS_PROPERTY + ':' +
            properties.getProperty(NERClassifierCombiner.APPLY_NUMERIC_CLASSIFIERS_PROPERTY,
                Boolean.toString(NERClassifierCombiner.APPLY_NUMERIC_CLASSIFIERS_DEFAULT)) +
            NumberSequenceClassifier.USE_SUTIME_PROPERTY + ':' +
            properties.getProperty(NumberSequenceClassifier.USE_SUTIME_PROPERTY,
                Boolean.toString(NumberSequenceClassifier.USE_SUTIME_DEFAULT));
      }
    };
  }

  //
  // Regex NER
  //
  public static AnnotatorFactory regexNER(Properties properties, final AnnotatorImplementations annotatorImplementation) {
    return new AnnotatorFactory(properties, annotatorImplementation) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        return annotatorImplementation.tokensRegexNER(properties, Annotator.STANFORD_REGEXNER);
      }

      @Override
      public String additionalSignature() {
        // keep track of all relevant properties for this annotator here!
        return PropertiesUtils.getSignature(Annotator.STANFORD_REGEXNER, properties, TokensRegexNERAnnotator.SUPPORTED_PROPERTIES);
      }
    };
  }

  //
  // Mentions annotator
  //
  public static AnnotatorFactory entityMentions(Properties properties, final AnnotatorImplementations annotatorImplementation) {
    return new AnnotatorFactory(properties, annotatorImplementation) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        return annotatorImplementation.mentions(properties, Annotator.STANFORD_ENTITY_MENTIONS);
      }

      @Override
      public String additionalSignature() {
        // keep track of all relevant properties for this annotator here!
        return PropertiesUtils.getSignature(Annotator.STANFORD_ENTITY_MENTIONS, properties, EntityMentionsAnnotator.SUPPORTED_PROPERTIES);
      }
    };
  }

  //
  // Gender Annotator
  //
  public static AnnotatorFactory gender(Properties properties, final AnnotatorImplementations annotatorImplementation) {
    return new AnnotatorFactory(properties, annotatorImplementation) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        return annotatorImplementation.gender(properties, false);
      }

      @Override
      public String additionalSignature() {
        // keep track of all relevant properties for this annotator here!
        return "gender.firstnames:" +
            properties.getProperty("gender.firstnames",
                DefaultPaths.DEFAULT_GENDER_FIRST_NAMES);
      }
    };
  }


  //
  // True caser
  //
  public static AnnotatorFactory truecase(Properties properties, final AnnotatorImplementations annotatorImplementation) {
    return new AnnotatorFactory(properties, annotatorImplementation) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        String model = properties.getProperty("truecase.model", DefaultPaths.DEFAULT_TRUECASE_MODEL);
        String bias = properties.getProperty("truecase.bias", TrueCaseAnnotator.DEFAULT_MODEL_BIAS);
        String mixed = properties.getProperty("truecase.mixedcasefile", DefaultPaths.DEFAULT_TRUECASE_DISAMBIGUATION_LIST);
        return annotatorImplementation.trueCase(properties, model, bias, mixed, false);
      }

      @Override
      public String additionalSignature() {
        // keep track of all relevant properties for this annotator here!
        return "truecase.model:" +
            properties.getProperty("truecase.model",
                DefaultPaths.DEFAULT_TRUECASE_MODEL) +
            "truecase.bias:" +
            properties.getProperty("truecase.bias",
                TrueCaseAnnotator.DEFAULT_MODEL_BIAS) +
            "truecase.mixedcasefile:" +
            properties.getProperty("truecase.mixedcasefile",
                DefaultPaths.DEFAULT_TRUECASE_DISAMBIGUATION_LIST);
      }
    };
  }

  //
  // Parser
  //
  public static AnnotatorFactory parse(Properties properties, final AnnotatorImplementations annotatorImplementation) {
    return new AnnotatorFactory(properties, annotatorImplementation) {
      private static final long serialVersionUID = 1L;

      @Override
      public Annotator create() {
        return annotatorImplementation.parse(properties);
      }

      @Override
      public String additionalSignature() {
        // keep track of all relevant properties for this annotator here!
        String type = properties.getProperty("parse.type", "stanford");
        if (type.equalsIgnoreCase("stanford")) {
          return ParserAnnotator.signature("parse", properties);
        } else if (type.equalsIgnoreCase("charniak")) {
          return "parse.model:" +
              properties.getProperty("parse.model", "") +
              "parse.executable:" +
              properties.getProperty("parse.executable", "") +
              "parse.maxlen:" +
              properties.getProperty("parse.maxlen", "");
        } else {
          throw new RuntimeException("Unknown parser type: " + type +
              " (currently supported: stanford and charniak)");
        }
      }
    };
  }

  //
  // Coreference resolution
  //
  public static AnnotatorFactory coref(Properties properties, final AnnotatorImplementations annotatorImplementation) {
    return new AnnotatorFactory(properties, annotatorImplementation) {
      private static final long serialVersionUID = 1L;

      @Override
      public Annotator create() {
        return annotatorImplementation.coref(properties);
      }

      @Override
      public String additionalSignature() {
        // keep track of all relevant properties for this annotator here!
        return DeterministicCorefAnnotator.signature(properties);
      }
    };
  }


  public static AnnotatorFactory relation(Properties properties, final AnnotatorImplementations annotatorImplementation) {
    return new AnnotatorFactory(properties, annotatorImplementation) {
      private static final long serialVersionUID = 1L;

      @Override
      public Annotator create() {
        return annotatorImplementation.relations(properties);
      }

      @Override
      public String additionalSignature() {
        // keep track of all relevant properties for this annotator here!
        return "sup.relation.verbose:" +
            properties.getProperty("sup.relation.verbose",
                "false") +
            properties.getProperty("sup.relation.model",
                DefaultPaths.DEFAULT_SUP_RELATION_EX_RELATION_MODEL);
      }
    };
  }

  public static AnnotatorFactory sentiment(Properties properties, final AnnotatorImplementations annotatorImplementation) {
    return new AnnotatorFactory(properties, annotatorImplementation) {
      private static final long serialVersionUID = 1L;

      @Override
      public Annotator create() {
        return annotatorImplementation.sentiment(properties, StanfordCoreNLP.STANFORD_SENTIMENT);
      }

      @Override
      public String additionalSignature() {
        return "sentiment.model=" + properties.get("sentiment.model");
      }
    };
  }

  public static AnnotatorFactory columnDataClassifier(Properties properties, final AnnotatorImplementations annotatorImpls) {
    return new AnnotatorFactory(properties, annotatorImpls) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        if(!properties.containsKey("loadClassifier"))
          throw new RuntimeException("Must load a classifier when creating a column data classifier annotator");
        return new ColumnDataClassifierAnnotator(properties);
      }

      @Override
      protected String additionalSignature() {
        return "classifier="+properties.get("loadClassifier="+properties.get("loadClassifier"));
      }
    };
  }

  //
  // Dependency parsing
  //
  public static AnnotatorFactory dependencies(Properties properties, final AnnotatorImplementations annotatorImpl) {
    return new AnnotatorFactory(properties, annotatorImpl) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        return annotatorImpl.dependencies(properties);
      }

      @Override
      protected String additionalSignature() {
        return DependencyParseAnnotator.signature(StanfordCoreNLP.STANFORD_DEPENDENCIES, properties);
      }
    };
  }

  //
  // Monotonicity and Polarity
  //
  public static AnnotatorFactory natlog(Properties properties, final AnnotatorImplementations annotatorImpl) {
    return new AnnotatorFactory(properties, annotatorImpl) {
      private static final long serialVersionUID = 4825870963088507811L;

      @Override
      public Annotator create() {
        return annotatorImpl.natlog(properties);
      }

      @Override
      protected String additionalSignature() {
        return "";
      }
    };
  }

  //
  // Quote Extractor
  //
  public static AnnotatorFactory quote(Properties properties, final AnnotatorImplementations annotatorImpl) {
    return new AnnotatorFactory(properties, annotatorImpl) {
      @Override
      public Annotator create() {
        return annotatorImpl.quote(properties);
      }

      @Override
      protected String additionalSignature() {
        return "";
      }
    };
  }

}
