package edu.stanford.nlp.pipeline;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.ie.regexp.NumberSequenceClassifier;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotator;
import edu.stanford.nlp.naturalli.OpenIE;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.WordToSentenceProcessor;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
// import edu.stanford.nlp.util.logging.Redwood;


/**
 * A companion to {@link AnnotatorFactory} defining the common annotators.
 * These are primarily used in {@link StanfordCoreNLP#getDefaultAnnotatorPool(java.util.Properties, AnnotatorImplementations)}.
 *
 * @author Gabor Angeli
 */
public class AnnotatorFactories  {

  // /** A logger for this class */
  // private static final Redwood.RedwoodChannels log = Redwood.channels(AnnotatorFactories.class);

  private AnnotatorFactories() {} // static factory class

  public static AnnotatorFactory tokenize(Properties properties, final AnnotatorImplementations annotatorImplementation) {
    return new AnnotatorFactory(Annotator.STANFORD_TOKENIZE, TokenizerAnnotator.class, properties) {
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
        // Add in some properties we depend on from the ssplit annotator
        StringBuilder os = new StringBuilder();
        if (Boolean.valueOf(properties.getProperty("tokenize.whitespace", "false"))) {
          os.append(StanfordCoreNLP.NEWLINE_SPLITTER_PROPERTY + ':');
          os.append(properties.getProperty(StanfordCoreNLP.NEWLINE_SPLITTER_PROPERTY, "false"));
        } else {
          os.append(StanfordCoreNLP.NEWLINE_SPLITTER_PROPERTY + ':');
          os.append(properties.getProperty(StanfordCoreNLP.NEWLINE_SPLITTER_PROPERTY, "false"));
          os.append("ssplit.isOneSentence" + ':');
          os.append(properties.getProperty("ssplit.isOneSentence", "false"));
          os.append(StanfordCoreNLP.NEWLINE_IS_SENTENCE_BREAK_PROPERTY + ':');
          os.append(properties.getProperty(StanfordCoreNLP.NEWLINE_IS_SENTENCE_BREAK_PROPERTY, StanfordCoreNLP.DEFAULT_NEWLINE_IS_SENTENCE_BREAK));
        }
        return os.toString();
      }
    };
  }





  public static AnnotatorFactory cleanXML(Properties properties, final AnnotatorImplementations annotatorImplementation) {
    return new AnnotatorFactory(Annotator.STANFORD_CLEAN_XML, CleanXmlAnnotator.class, properties) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        return annotatorImplementation.cleanXML(properties);
      }
    };
  }

  /** Sentence splitter: splits the above sequence of tokens into
   *  sentences.  This is required when processing entire documents or
   * text consisting of multiple sentences.
   */
  public static AnnotatorFactory sentenceSplit(Properties properties, final AnnotatorImplementations annotatorImplementation) {
    return new AnnotatorFactory(Annotator.STANFORD_SSPLIT, WordsToSentencesAnnotator.class, properties) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        // log.info(signature());
        // todo: The above shows that signature is edu.stanford.nlp.pipeline.AnnotatorImplementations: and doesn't reflect what annotator it is! Should fix. Maybe is fixed now [2016]. Test!
        boolean nlSplitting = Boolean.valueOf(properties.getProperty(StanfordCoreNLP.NEWLINE_SPLITTER_PROPERTY, "false"));
        if (nlSplitting) {
          boolean whitespaceTokenization = Boolean.valueOf(properties.getProperty("tokenize.whitespace", "false"));
          if (whitespaceTokenization) {
            if (System.lineSeparator().equals("\n")) {
              return WordsToSentencesAnnotator.newlineSplitter("\n");
            } else {
              // throw "\n" in just in case files use that instead of
              // the system separator
              return WordsToSentencesAnnotator.newlineSplitter(System.lineSeparator(), "\n");
            }
          } else {
            return WordsToSentencesAnnotator.newlineSplitter(PTBTokenizer.getNewlineToken());
          }

        } else {
          // Treat as one sentence: You get a no-op sentence splitter that always returns all tokens as one sentence.
          String isOneSentence = properties.getProperty("ssplit.isOneSentence");
          if (Boolean.parseBoolean(isOneSentence)) { // this method treats null as false
            return WordsToSentencesAnnotator.nonSplitter();
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

          // todo [cdm 2016]: Add support for specifying ssplit.boundaryFollowerRegex here and send down to WordsToSentencesAnnotator

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
    return new AnnotatorFactory(Annotator.STANFORD_POS, POSTaggerAnnotator.class, properties) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        try {
          return annotatorImplementation.posTagger(properties);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  //
  // Lemmatizer
  //
  public static AnnotatorFactory lemma(Properties properties, final AnnotatorImplementations annotatorImplementation) {
    return new AnnotatorFactory(Annotator.STANFORD_LEMMA, MorphaAnnotator.class, properties) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        return annotatorImplementation.morpha(properties, false);
      }
    };
  }

  //
  // NER
  //
  public static AnnotatorFactory nerTag(Properties properties, final AnnotatorImplementations annotatorImplementation) {
    return new AnnotatorFactory(Annotator.STANFORD_NER, NERCombinerAnnotator.class, properties) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        try {
          return annotatorImplementation.ner(properties);
        } catch (IOException e) {
          throw new RuntimeIOException(e);
        }
      }

      @Override
      public String additionalSignature() {
        // keep track of all relevant properties for this annotator here!
        boolean useSUTime = Boolean.parseBoolean(properties.getProperty(
          NumberSequenceClassifier.USE_SUTIME_PROPERTY,
          Boolean.toString(NumberSequenceClassifier.USE_SUTIME_DEFAULT)));
        String nerSig = "";
        if (useSUTime) {
          String sutimeSig = PropertiesUtils.getSignature(NumberSequenceClassifier.SUTIME_PROPERTY, properties);
          if (!sutimeSig.isEmpty()) {
            nerSig = nerSig + sutimeSig;
          }
        }
        return nerSig;
      }
    };
  }

  //
  // Regex NER
  //
  public static AnnotatorFactory regexNER(Properties properties, final AnnotatorImplementations annotatorImplementation) {
    return new AnnotatorFactory(Annotator.STANFORD_REGEXNER, RegexNERAnnotator.class, properties) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        return annotatorImplementation.tokensRegexNER(properties, Annotator.STANFORD_REGEXNER);
      }
    };
  }

  //
  // Mentions annotator
  //
  public static AnnotatorFactory entityMentions(Properties properties, final AnnotatorImplementations annotatorImplementation) {
    return new AnnotatorFactory(Annotator.STANFORD_ENTITY_MENTIONS, EntityMentionsAnnotator.class, properties) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        return annotatorImplementation.mentions(properties, Annotator.STANFORD_ENTITY_MENTIONS);
      }
    };
  }

  //
  // Gender Annotator
  //
  public static AnnotatorFactory gender(Properties properties, final AnnotatorImplementations annotatorImplementation) {
    return new AnnotatorFactory(Annotator.STANFORD_GENDER, GenderAnnotator.class, properties) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        return annotatorImplementation.gender(properties, false);
      }
    };
  }


  //
  // True caser
  //
  public static AnnotatorFactory truecase(Properties properties, final AnnotatorImplementations annotatorImplementation) {
    return new AnnotatorFactory(Annotator.STANFORD_TRUECASE, TrueCaseAnnotator.class, properties) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        return annotatorImplementation.trueCase(properties);
      }
    };
  }

  //
  // Parser
  //
  public static AnnotatorFactory parse(final Properties properties, final AnnotatorImplementations annotatorImplementation) {
    return new AnnotatorFactory(Annotator.STANFORD_PARSE, ParserAnnotator.class, properties) {
      private static final long serialVersionUID = 1L;

      @Override
      public Annotator create() {
        return annotatorImplementation.parse(properties);
      }

      @Override
      public String additionalSignature() {
        if (StanfordCoreNLP.usesBinaryTrees(properties) ||
            PropertiesUtils.getBool(properties, Annotator.STANFORD_PARSE + ".binaryTrees", false)) {
          return "parse.binaryTrees=true";
        }
        return "";
      }
    };
  }

  //
  // Mentions
  //

  public static AnnotatorFactory mention(Properties properties, final AnnotatorImplementations annotatorImplementation) {
    return new AnnotatorFactory(Annotator.STANFORD_MENTION, MentionAnnotator.class, properties) {
      private static final long serialVersionUID = 1L;

      @Override
      public Annotator create() { return annotatorImplementation.mention(properties); }

      @Override
      public String additionalSignature() {
          // TODO: implement this properly
          return "coref.md:" + properties.getProperty("coref.md.type", "rule") + ";" +
              "coref.language:" + properties.getProperty("coref.language", "en");
      }
    };
  }

  //
  // Coreference resolution
  //
  public static AnnotatorFactory coref(Properties properties, final AnnotatorImplementations annotatorImplementation) {
    return new AnnotatorFactory(Annotator.STANFORD_COREF, CorefAnnotator.class, properties) {
      private static final long serialVersionUID = 1L;

      @Override
      public Annotator create() {
        return annotatorImplementation.coref(properties);
      }
    };
  }

  public static AnnotatorFactory dcoref(Properties properties, final AnnotatorImplementations annotatorImplementation) {
    return new AnnotatorFactory(Annotator.STANFORD_DETERMINISTIC_COREF, DeterministicCorefAnnotator.class, properties) {
      private static final long serialVersionUID = 1L;

      @Override
      public Annotator create() {
        return annotatorImplementation.dcoref(properties);
      }
    };
  }


  public static AnnotatorFactory relation(Properties properties, final AnnotatorImplementations annotatorImplementation) {
    return new AnnotatorFactory(Annotator.STANFORD_RELATION, RelationExtractorAnnotator.class, properties) {
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
    return new AnnotatorFactory(Annotator.STANFORD_SENTIMENT, SentimentAnnotator.class, properties) {
      private static final long serialVersionUID = 1L;

      @Override
      public Annotator create() {
        return annotatorImplementation.sentiment(properties, StanfordCoreNLP.STANFORD_SENTIMENT);
      }
    };
  }

  public static AnnotatorFactory columnDataClassifier(Properties properties, final AnnotatorImplementations annotatorImpls) {
    return new AnnotatorFactory(Annotator.STANFORD_COLUMN_DATA_CLASSIFIER, ColumnDataClassifierAnnotator.class, properties) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        if (properties.containsKey("classify.loadClassifier")) {
          properties.setProperty("loadClassifier", properties.getProperty("classify.loadClassifier"));
        }
        if (!properties.containsKey("loadClassifier")) {
          throw new RuntimeException("Must load a classifier when creating a column data classifier annotator");
        }
        return new ColumnDataClassifierAnnotator(properties);
      }

      @Override
      protected String additionalSignature() {
        return "classifier="+ properties.getProperty("loadClassifier=" + properties.getProperty("loadClassifier"));
      }
    };
  }

  //
  // Dependency parsing
  //
  public static AnnotatorFactory dependencies(Properties properties, final AnnotatorImplementations annotatorImpl) {
    return new AnnotatorFactory(Annotator.STANFORD_DEPENDENCIES, DependencyParseAnnotator.class, properties) {
      private static final long serialVersionUID = 1L;
      @Override
      public Annotator create() {
        return annotatorImpl.dependencies(properties);
      }
    };
  }

  //
  // Monotonicity and Polarity
  //
  public static AnnotatorFactory natlog(Properties properties, final AnnotatorImplementations annotatorImpl) {
    return new AnnotatorFactory(Annotator.STANFORD_NATLOG, NaturalLogicAnnotator.class, properties) {
      private static final long serialVersionUID = 4825870963088507811L;

      @Override
      public Annotator create() {
        return annotatorImpl.natlog(properties);
      }
    };
  }

  //
  // RelationTriples
  //
  public static AnnotatorFactory openie(Properties properties, final AnnotatorImplementations annotatorImpl) {
    return new AnnotatorFactory(Annotator.STANFORD_OPENIE, OpenIE.class, properties) {
      private static final long serialVersionUID = -2525567112379296672L;

      @Override
      public Annotator create() {
        return annotatorImpl.openie(properties);
      }
    };
  }

  //
  // Quote Extractor
  //
  public static AnnotatorFactory quote(Properties properties, final AnnotatorImplementations annotatorImpl) {
    return new AnnotatorFactory(Annotator.STANFORD_QUOTE, QuoteAnnotator.class, properties) {
      private static final long serialVersionUID = -2525567112379296672L;

      @Override
      public Annotator create() {
        return annotatorImpl.quote(properties);
      }
    };
  }


  //
  // UD Features Extractor
  //
  public static AnnotatorFactory udfeats(Properties properties, final AnnotatorImplementations annotatorImpl) {
    return new AnnotatorFactory(Annotator.STANFORD_UD_FEATURES, UDFeatureAnnotator.class, properties) {
      private static final long serialVersionUID = -2525567112379296672L;

      @Override
      public Annotator create() {
        return annotatorImpl.udfeats(properties);
      }
    };
  }

  //
  // UD Features Extractor
  //
  public static AnnotatorFactory kbp(Properties properties, final AnnotatorImplementations annotatorImpl) {
    return new AnnotatorFactory(Annotator.STANFORD_KBP, KBPAnnotator.class, properties) {
      private static final long serialVersionUID = -2525567112379296672L;

      @Override
      public Annotator create() {
        return annotatorImpl.kbp(properties);
      }
    };
  }

  public static AnnotatorFactory link(Properties properties, AnnotatorImplementations annotatorImplementations) {

    return new AnnotatorFactory(Annotator.STANFORD_LINK, WikidictAnnotator.class, properties) {
      private static final long serialVersionUID = 42L;

      @Override
      public Annotator create() {
        return annotatorImplementations.link(properties);
      }
    };
  }

}
