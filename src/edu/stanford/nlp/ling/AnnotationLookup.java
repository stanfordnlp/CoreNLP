package edu.stanford.nlp.ling;

import java.util.Map;

import edu.stanford.nlp.ling.CoreLabel.GenericAnnotation;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.Generics;

/** @author Anna Rafferty */
public class AnnotationLookup {

  private AnnotationLookup() {}

  public enum KeyLookup {
    VALUE_KEY(CoreAnnotations.ValueAnnotation.class, OldFeatureLabelKeys.VALUE_KEY),
    TAG_KEY(CoreAnnotations.PartOfSpeechAnnotation.class, OldFeatureLabelKeys.TAG_KEY),
    WORD_KEY(CoreAnnotations.TextAnnotation.class, OldFeatureLabelKeys.WORD_KEY),
    LEMMA_KEY(CoreAnnotations.LemmaAnnotation.class, OldFeatureLabelKeys.LEMMA_KEY),
    CATEGORY_KEY(CoreAnnotations.CategoryAnnotation.class, OldFeatureLabelKeys.CATEGORY_KEY),
    PROJ_CAT_KEY(CoreAnnotations.ProjectedCategoryAnnotation.class, OldFeatureLabelKeys.PROJ_CAT_KEY),
    HEAD_WORD_KEY("edu.stanford.nlp.ling.TreeCoreAnnotations.HeadWordAnnotation", OldFeatureLabelKeys.HEAD_WORD_KEY),
    HEAD_TAG_KEY("edu.stanford.nlp.ling.TreeCoreAnnotations.HeadTagAnnotation", OldFeatureLabelKeys.HEAD_TAG_KEY),
    INDEX_KEY(CoreAnnotations.IndexAnnotation.class, OldFeatureLabelKeys.INDEX_KEY),
    ARG_KEY(CoreAnnotations.ArgumentAnnotation.class, OldFeatureLabelKeys.ARG_KEY),
    MARKING_KEY(CoreAnnotations.MarkingAnnotation.class, OldFeatureLabelKeys.MARKING_KEY),
    SEMANTIC_HEAD_WORD_KEY(CoreAnnotations.SemanticHeadWordAnnotation.class, OldFeatureLabelKeys.SEMANTIC_HEAD_WORD_KEY),
    SEMANTIC_HEAD_POS_KEY(CoreAnnotations.SemanticHeadTagAnnotation.class, OldFeatureLabelKeys.SEMANTIC_HEAD_POS_KEY),
    VERB_SENSE_KEY(CoreAnnotations.VerbSenseAnnotation.class, OldFeatureLabelKeys.VERB_SENSE_KEY),
    CATEGORY_FUNCTIONAL_TAG_KEY(CoreAnnotations.CategoryFunctionalTagAnnotation.class, OldFeatureLabelKeys.CATEGORY_FUNCTIONAL_TAG_KEY),
    NER_KEY(CoreAnnotations.NamedEntityTagAnnotation.class, OldFeatureLabelKeys.NER_KEY),
    SHAPE_KEY(CoreAnnotations.ShapeAnnotation.class, OldFeatureLabelKeys.SHAPE_KEY),
    LEFT_TERM_KEY(CoreAnnotations.LeftTermAnnotation.class, OldFeatureLabelKeys.LEFT_TERM_KEY),
    PARENT_KEY(CoreAnnotations.ParentAnnotation.class, OldFeatureLabelKeys.PARENT_KEY),
    SPAN_KEY(CoreAnnotations.SpanAnnotation.class, OldFeatureLabelKeys.SPAN_KEY),
    BEFORE_KEY(CoreAnnotations.BeforeAnnotation.class, OldFeatureLabelKeys.BEFORE_KEY),
    AFTER_KEY(CoreAnnotations.AfterAnnotation.class, OldFeatureLabelKeys.AFTER_KEY),
    CURRENT_KEY(CoreAnnotations.OriginalTextAnnotation.class, OldFeatureLabelKeys.CURRENT_KEY),
    ANSWER_KEY(CoreAnnotations.AnswerAnnotation.class, OldFeatureLabelKeys.ANSWER_KEY),
    GOLDANSWER_Key(CoreAnnotations.GoldAnswerAnnotation.class, OldFeatureLabelKeys.GOLDANSWER_KEY),
    FEATURES_KEY(CoreAnnotations.FeaturesAnnotation.class, OldFeatureLabelKeys.FEATURES_KEY),
    INTERPRETATION_KEY(CoreAnnotations.InterpretationAnnotation.class, OldFeatureLabelKeys.INTERPRETATION_KEY),
    ROLE_KEY(CoreAnnotations.RoleAnnotation.class, OldFeatureLabelKeys.ROLE_KEY),
    GAZETTEER_KEY(CoreAnnotations.GazetteerAnnotation.class, OldFeatureLabelKeys.GAZETTEER_KEY),
    STEM_KEY(CoreAnnotations.StemAnnotation.class, OldFeatureLabelKeys.STEM_KEY),
    POLARITY_KEY(CoreAnnotations.PolarityAnnotation.class, OldFeatureLabelKeys.POLARITY_KEY),
    CH_CHAR_KEY(CoreAnnotations.ChineseCharAnnotation.class, OldFeatureLabelKeys.CH_CHAR_KEY),
    CH_ORIG_SEG_KEY(CoreAnnotations.ChineseOrigSegAnnotation.class, OldFeatureLabelKeys.CH_ORIG_SEG_KEY),
    CH_SEG_KEY(CoreAnnotations.ChineseSegAnnotation.class, OldFeatureLabelKeys.CH_SEG_KEY),
    BEGIN_POSITION_KEY(CoreAnnotations.CharacterOffsetBeginAnnotation.class, OldFeatureLabelKeys.BEGIN_POSITION_KEY),
    END_POSITION_KEY(CoreAnnotations.CharacterOffsetEndAnnotation.class, OldFeatureLabelKeys.END_POSITION_KEY),
    DOCID_KEY(CoreAnnotations.DocIDAnnotation.class, OldFeatureLabelKeys.DOCID_KEY),
    SENTINDEX_KEY(CoreAnnotations.SentenceIndexAnnotation.class, OldFeatureLabelKeys.SENTINDEX_KEY),
    IDF_KEY(CoreAnnotations.IDFAnnotation.class, "idf"),
    END_POSITION_KEY2(CoreAnnotations.CharacterOffsetEndAnnotation.class, "endPosition"),
    CHUNK_KEY(CoreAnnotations.ChunkAnnotation.class, "chunk"),
    NORMALIZED_NER_KEY(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class, "normalized"),
    MORPHO_NUM_KEY(CoreAnnotations.MorphoNumAnnotation.class,"num"),
    MORPHO_PERS_KEY(CoreAnnotations.MorphoPersAnnotation.class,"pers"),
    MORPHO_GEN_KEY(CoreAnnotations.MorphoGenAnnotation.class,"gen"),
    MORPHO_CASE_KEY(CoreAnnotations.MorphoCaseAnnotation.class,"case"),
    WORDNET_SYN_KEY(CoreAnnotations.WordnetSynAnnotation.class,"wordnetsyn"),
    PROTO_SYN_KEY(CoreAnnotations.ProtoAnnotation.class,"proto"),
    DOCTITLE_KEY(CoreAnnotations.DocTitleAnnotation.class,"doctitle"),
    DOCTYPE_KEY(CoreAnnotations.DocTypeAnnotation.class,"doctype"),
    DOCDATE_KEY(CoreAnnotations.DocDateAnnotation.class,"docdate"),
    DOCSOURCETYPE_KEY(CoreAnnotations.DocSourceTypeAnnotation.class,"docsourcetype"),
    LINK_KEY(CoreAnnotations.LinkAnnotation.class,"link"),
    SPEAKER_KEY(CoreAnnotations.SpeakerAnnotation.class,"speaker"),
    AUTHOR_KEY(CoreAnnotations.AuthorAnnotation.class,"author"),
    SECTION_KEY(CoreAnnotations.SectionAnnotation.class,"section"),
    SECTIONID_KEY(CoreAnnotations.SectionIDAnnotation.class,"sectionID"),
    SECTIONDATE_KEY(CoreAnnotations.SectionDateAnnotation.class,"sectionDate"),

    // Thang Sep13: for Genia NER
    HEAD_KEY(CoreAnnotations.HeadWordStringAnnotation.class, "head"),
    GOVERNOR_KEY(CoreAnnotations.GovernorAnnotation.class, "governor"),
    GAZ_KEY(CoreAnnotations.GazAnnotation.class, "gaz"),
    ABBR_KEY(CoreAnnotations.AbbrAnnotation.class, "abbr"),
    ABSTR_KEY(CoreAnnotations.AbstrAnnotation.class, "abstr"),
    FREQ_KEY(CoreAnnotations.FreqAnnotation.class, "freq"),
    WEB_KEY(CoreAnnotations.WebAnnotation.class, "web"),

    // Also have "pos" for PartOfTag (POS is also the TAG_KEY - "tag", but "pos" makes more sense)
    // Still keep "tag" for POS tag so we don't break anything
    POS_TAG_KEY(CoreAnnotations.PartOfSpeechAnnotation.class, "pos");


    public final Class coreKey;
    public final String oldKey;

    private <T> KeyLookup(Class<? extends CoreAnnotation<T>> coreKey, String oldKey) {
      this.coreKey = coreKey;
      this.oldKey = oldKey;
    }

    /**
     * This constructor allows us to use reflection for loading old class keys.
     * This is useful because we can then create distributions that do not have
     * all of the classes required for all the old keys (such as trees package classes).
     */
    private KeyLookup(String className, String oldKey) {
      Class<?> keyClass;
      try {
       keyClass = Class.forName(className);
      } catch(ClassNotFoundException e) {
        GenericAnnotation<Object> newKey = new GenericAnnotation<Object>() {
          public Class<Object> getType() { return Object.class;} };
        keyClass = newKey.getClass();
      }
      this.coreKey = ErasureUtils.uncheckedCast(keyClass);
      this.oldKey = oldKey;
    }


  }

  /**
   * Returns a CoreAnnotation class key for the given old-style FeatureLabel
   * key if one exists; null otherwise.
   */
  public static KeyLookup getCoreKey(String oldKey) {
    for (KeyLookup lookup : KeyLookup.values()) {
      if (lookup.oldKey.equals(oldKey)) {
        return lookup;
      }
    }
    return null;
  }

  private static Map<Class<CoreAnnotation<?>>,Class<?>> valueCache = Generics.newHashMap();

  /**
   * Returns the runtime value type associated with the given key.  Caches
   * results.
   */
  @SuppressWarnings("unchecked")
  public static Class<?> getValueType(Class<? extends CoreAnnotation> key) {
    Class type = valueCache.get(key);
    if (type == null) {
      try {
        type = key.newInstance().getType();
      } catch (Exception e) {
        throw new RuntimeException("Unexpected failure to instantiate - is your key class fancy?", e);
      }
      valueCache.put((Class)key, type);
    }
    return type;
  }

  /**
   * Lookup table for mapping between old-style *Label keys and classes
   * the provide comparable backings in the core.
   */
//OLD keys kept around b/c we're kill IndexedFeatureLabel and these keys used to live there
  private static class OldFeatureLabelKeys {

    public static final String DOCID_KEY = "docID";
    public static final String SENTINDEX_KEY = "sentIndex";
    public static final Object WORD_FORMAT = "WORD_FORMAT";
    public static final Object WORD_TAG_FORMAT = "WORD_TAG_FORMAT";
    public static final Object WORD_TAG_INDEX_FORMAT = "WORD_TAG_INDEX_FORMAT";
    public static final Object VALUE_FORMAT = "VALUE_FORMAT";
    public static final Object COMPLETE_FORMAT = "COMPLETE_FORMAT";
    public static final String VALUE_KEY = "value";
    public static final String TAG_KEY = "tag";
    public static final String WORD_KEY = "word";
    public static final String LEMMA_KEY = "lemma";
    public static final String CATEGORY_KEY = "cat";
    public static final String PROJ_CAT_KEY = "pcat";
    public static final String HEAD_WORD_KEY = "hw";
    public static final String HEAD_TAG_KEY = "ht";
    public static final String INDEX_KEY = "idx";
    public static final String ARG_KEY = "arg";
    public static final String MARKING_KEY = "mark";
    public static final String SEMANTIC_HEAD_WORD_KEY = "shw";
    public static final String SEMANTIC_HEAD_POS_KEY = "shp";
    public static final String VERB_SENSE_KEY = "vs";
    public static final String CATEGORY_FUNCTIONAL_TAG_KEY = "cft";
    public static final String NER_KEY = "ner";
    public static final String SHAPE_KEY = "shape";
    public static final String LEFT_TERM_KEY = "LEFT_TERM";
    public static final String PARENT_KEY = "PARENT";
    public static final String SPAN_KEY = "SPAN";
    public static final String BEFORE_KEY = "before";
    public static final String AFTER_KEY = "after";
    public static final String CURRENT_KEY = "current";
    public static final String ANSWER_KEY = "answer";
    public static final String GOLDANSWER_KEY = "goldAnswer";
    public static final String FEATURES_KEY = "features";
    public static final String INTERPRETATION_KEY = "interpretation";
    public static final String ROLE_KEY = "srl";
    public static final String GAZETTEER_KEY = "gazetteer";
    public static final String STEM_KEY = "stem";
    public static final String POLARITY_KEY = "polarity";
    public static final String CH_CHAR_KEY = "char";
    public static final String CH_ORIG_SEG_KEY = "orig_seg"; // the segmentation info existing in the original text
    public static final String CH_SEG_KEY = "seg"; // the segmentation information from the segmenter
    public static final String BEGIN_POSITION_KEY = "BEGIN_POS";
    public static final String END_POSITION_KEY = "END_POS";


    private OldFeatureLabelKeys() {
    }

  } // end static class OldFeatureLabelKeys

}

