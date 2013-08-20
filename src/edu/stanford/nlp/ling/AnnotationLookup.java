package edu.stanford.nlp.ling;

import java.util.HashMap;
import java.util.Map;

import edu.stanford.nlp.ling.CoreAnnotations.AfterAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ArgumentAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.BeforeAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetBeginAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CategoryAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CategoryFunctionalTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ChineseCharAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ChineseOrigSegAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ChineseSegAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ChunkAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.OriginalTextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.DocIDAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetEndAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.FeaturesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.GazetteerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.GoldAnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.IDFAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.IndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.InterpretationAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LeftTermAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.MarkingAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.MorphoCaseAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.MorphoGenAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.MorphoNumAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.MorphoPersAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NormalizedNamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ParentAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PolarityAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ProjectedCategoryAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ProtoAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.RoleAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SemanticHeadTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SemanticHeadWordAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentenceIndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ShapeAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SpanAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.StemAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ValueAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.VerbSenseAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.WordnetSynAnnotation;
import edu.stanford.nlp.ling.CoreLabel.GenericAnnotation;
import edu.stanford.nlp.util.ErasureUtils;

/** @author Anna Rafferty */
public class AnnotationLookup {

  private AnnotationLookup() {}

  public enum KeyLookup {
    VALUE_KEY(ValueAnnotation.class, OldFeatureLabelKeys.VALUE_KEY),
    TAG_KEY(PartOfSpeechAnnotation.class, OldFeatureLabelKeys.TAG_KEY),
    WORD_KEY(TextAnnotation.class, OldFeatureLabelKeys.WORD_KEY),
    LEMMA_KEY(LemmaAnnotation.class, OldFeatureLabelKeys.LEMMA_KEY),
    CATEGORY_KEY(CategoryAnnotation.class, OldFeatureLabelKeys.CATEGORY_KEY),
    PROJ_CAT_KEY(ProjectedCategoryAnnotation.class, OldFeatureLabelKeys.PROJ_CAT_KEY),
    HEAD_WORD_KEY("edu.stanford.nlp.ling.TreeCoreAnnotations.HeadWordAnnotation", OldFeatureLabelKeys.HEAD_WORD_KEY),
    HEAD_TAG_KEY("edu.stanford.nlp.ling.TreeCoreAnnotations.HeadTagAnnotation", OldFeatureLabelKeys.HEAD_TAG_KEY),
    INDEX_KEY(IndexAnnotation.class, OldFeatureLabelKeys.INDEX_KEY),
    ARG_KEY(ArgumentAnnotation.class, OldFeatureLabelKeys.ARG_KEY),
    MARKING_KEY(MarkingAnnotation.class, OldFeatureLabelKeys.MARKING_KEY),
    SEMANTIC_HEAD_WORD_KEY(SemanticHeadWordAnnotation.class, OldFeatureLabelKeys.SEMANTIC_HEAD_WORD_KEY),
    SEMANTIC_HEAD_POS_KEY(SemanticHeadTagAnnotation.class, OldFeatureLabelKeys.SEMANTIC_HEAD_POS_KEY),
    VERB_SENSE_KEY(VerbSenseAnnotation.class, OldFeatureLabelKeys.VERB_SENSE_KEY),
    CATEGORY_FUNCTIONAL_TAG_KEY(CategoryFunctionalTagAnnotation.class, OldFeatureLabelKeys.CATEGORY_FUNCTIONAL_TAG_KEY),
    NER_KEY(NamedEntityTagAnnotation.class, OldFeatureLabelKeys.NER_KEY),
    SHAPE_KEY(ShapeAnnotation.class, OldFeatureLabelKeys.SHAPE_KEY),
    LEFT_TERM_KEY(LeftTermAnnotation.class, OldFeatureLabelKeys.LEFT_TERM_KEY),
    PARENT_KEY(ParentAnnotation.class, OldFeatureLabelKeys.PARENT_KEY),
    SPAN_KEY(SpanAnnotation.class, OldFeatureLabelKeys.SPAN_KEY),
    BEFORE_KEY(BeforeAnnotation.class, OldFeatureLabelKeys.BEFORE_KEY),
    AFTER_KEY(AfterAnnotation.class, OldFeatureLabelKeys.AFTER_KEY),
    CURRENT_KEY(OriginalTextAnnotation.class, OldFeatureLabelKeys.CURRENT_KEY),
    ANSWER_KEY(AnswerAnnotation.class, OldFeatureLabelKeys.ANSWER_KEY),
    GOLDANSWER_Key(GoldAnswerAnnotation.class, OldFeatureLabelKeys.GOLDANSWER_KEY),
    FEATURES_KEY(FeaturesAnnotation.class, OldFeatureLabelKeys.FEATURES_KEY),
    INTERPRETATION_KEY(InterpretationAnnotation.class, OldFeatureLabelKeys.INTERPRETATION_KEY),
    ROLE_KEY(RoleAnnotation.class, OldFeatureLabelKeys.ROLE_KEY),
    GAZETTEER_KEY(GazetteerAnnotation.class, OldFeatureLabelKeys.GAZETTEER_KEY),
    STEM_KEY(StemAnnotation.class, OldFeatureLabelKeys.STEM_KEY),
    POLARITY_KEY(PolarityAnnotation.class, OldFeatureLabelKeys.POLARITY_KEY),
    CH_CHAR_KEY(ChineseCharAnnotation.class, OldFeatureLabelKeys.CH_CHAR_KEY),
    CH_ORIG_SEG_KEY(ChineseOrigSegAnnotation.class, OldFeatureLabelKeys.CH_ORIG_SEG_KEY),
    CH_SEG_KEY(ChineseSegAnnotation.class, OldFeatureLabelKeys.CH_SEG_KEY),
    BEGIN_POSITION_KEY(CharacterOffsetBeginAnnotation.class, OldFeatureLabelKeys.BEGIN_POSITION_KEY),
    END_POSITION_KEY(CharacterOffsetEndAnnotation.class, OldFeatureLabelKeys.END_POSITION_KEY),
    DOCID_KEY(DocIDAnnotation.class, OldFeatureLabelKeys.DOCID_KEY),
    SENTINDEX_KEY(SentenceIndexAnnotation.class, OldFeatureLabelKeys.SENTINDEX_KEY),
    IDF_KEY(IDFAnnotation.class, "idf"),
    END_POSITION_KEY2(CharacterOffsetEndAnnotation.class, "endPosition"),
    CHUNK_KEY(ChunkAnnotation.class, "chunk"),
    NORMALIZED_NER_KEY(NormalizedNamedEntityTagAnnotation.class, "normalized"),
    MORPHO_NUM_KEY(MorphoNumAnnotation.class,"num"),
    MORPHO_PERS_KEY(MorphoPersAnnotation.class,"pers"),
    MORPHO_GEN_KEY(MorphoGenAnnotation.class,"gen"),
    MORPHO_CASE_KEY(MorphoCaseAnnotation.class,"case"),
    WORDNET_SYN_KEY(WordnetSynAnnotation.class,"wordnetsyn"),
    PROTO_SYN_KEY(ProtoAnnotation.class,"proto");

    public final Class<? extends CoreAnnotation<?>> coreKey;
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

  private static Map<Class<CoreAnnotation<?>>,Class<?>> valueCache
  = new HashMap<Class<CoreAnnotation<?>>,Class<?>>();

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

