package edu.stanford.nlp.ling;

import java.util.Map;

import edu.stanford.nlp.ling.CoreLabel.GenericAnnotation;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.Generics;

/** Provides a mapping between CoreAnnotation keys, which are classes, and a text String that names them,
 *  which is needed for things like text serializations and the Semgrex query language.
 *
 *  @author Anna Rafferty
 */
public class AnnotationLookup {

  private AnnotationLookup() {}

  private enum KeyLookup {
    VALUE_KEY(CoreAnnotations.ValueAnnotation.class, "value"),
    TAG_KEY(CoreAnnotations.PartOfSpeechAnnotation.class, "tag"),
    WORD_KEY(CoreAnnotations.TextAnnotation.class, "word"),
    LEMMA_KEY(CoreAnnotations.LemmaAnnotation.class, "lemma"),
    CATEGORY_KEY(CoreAnnotations.CategoryAnnotation.class, "cat"),
    //PROJ_CAT_KEY(CoreAnnotations.ProjectedCategoryAnnotation.class, "pcat"),
    //HEAD_WORD_KEY("edu.stanford.nlp.ling.TreeCoreAnnotations.HeadWordAnnotation", "hw"),
    //HEAD_TAG_KEY("edu.stanford.nlp.ling.TreeCoreAnnotations.HeadTagAnnotation", "ht"),
    INDEX_KEY(CoreAnnotations.IndexAnnotation.class, "idx"),
    ARG_KEY(CoreAnnotations.ArgumentAnnotation.class, "arg"),
    MARKING_KEY(CoreAnnotations.MarkingAnnotation.class, "mark"),
    SEMANTIC_HEAD_WORD_KEY(CoreAnnotations.SemanticHeadWordAnnotation.class, "shw"),
    SEMANTIC_HEAD_POS_KEY(CoreAnnotations.SemanticHeadTagAnnotation.class, "shp"),
    VERB_SENSE_KEY(CoreAnnotations.VerbSenseAnnotation.class, "vs"),
    CATEGORY_FUNCTIONAL_TAG_KEY(CoreAnnotations.CategoryFunctionalTagAnnotation.class, "cft"),
    NER_KEY(CoreAnnotations.NamedEntityTagAnnotation.class, "ner"),
    SHAPE_KEY(CoreAnnotations.ShapeAnnotation.class, "shape"),
    LEFT_TERM_KEY(CoreAnnotations.LeftTermAnnotation.class, "LEFT_TERM"), // effectively unused in 2016 (was in PropBank SRL)
    PARENT_KEY(CoreAnnotations.ParentAnnotation.class, "PARENT"),
    SPAN_KEY(CoreAnnotations.SpanAnnotation.class, "SPAN"),
    BEFORE_KEY(CoreAnnotations.BeforeAnnotation.class, "before"),
    AFTER_KEY(CoreAnnotations.AfterAnnotation.class, "after"),
    CURRENT_KEY(CoreAnnotations.OriginalTextAnnotation.class, "current"),
    ANSWER_KEY(CoreAnnotations.AnswerAnnotation.class, "answer"),
    GOLDANSWER_Key(CoreAnnotations.GoldAnswerAnnotation.class, "goldAnswer"),
    FEATURES_KEY(CoreAnnotations.FeaturesAnnotation.class, "features"),
    MORPHOLOGICAL_FEATURES_KEY(CoreAnnotations.CoNLLUFeats.class, "morphofeatures"),
    INTERPRETATION_KEY(CoreAnnotations.InterpretationAnnotation.class, "interpretation"),
    ROLE_KEY(CoreAnnotations.RoleAnnotation.class, "srl"),
    GAZETTEER_KEY(CoreAnnotations.GazetteerAnnotation.class, "gazetteer"),
    STEM_KEY(CoreAnnotations.StemAnnotation.class, "stem"),
    POLARITY_KEY(CoreAnnotations.PolarityAnnotation.class, "polarity"),
    CH_CHAR_KEY(CoreAnnotations.ChineseCharAnnotation.class, "char"),
    CH_ORIG_SEG_KEY(CoreAnnotations.ChineseOrigSegAnnotation.class, "orig_seg"),
    CH_SEG_KEY(CoreAnnotations.ChineseSegAnnotation.class, "seg"),
    BEGIN_POSITION_KEY(CoreAnnotations.CharacterOffsetBeginAnnotation.class, "BEGIN_POS"),
    END_POSITION_KEY(CoreAnnotations.CharacterOffsetEndAnnotation.class, "END_POS"),
    DOCID_KEY(CoreAnnotations.DocIDAnnotation.class, "docID"),
    SENTINDEX_KEY(CoreAnnotations.SentenceIndexAnnotation.class, "sentIndex"),
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
    STACKED_NER_KEY(CoreAnnotations.StackedNamedEntityTagAnnotation.class, "stackedNer"),

    // Thang Sep13: for Genia NER
    HEADWORD_KEY(CoreAnnotations.HeadWordStringAnnotation.class, "headword"),
    GOVERNOR_KEY(CoreAnnotations.GovernorAnnotation.class, "governor"),
    GAZ_KEY(CoreAnnotations.GazAnnotation.class, "gaz"),
    ABBR_KEY(CoreAnnotations.AbbrAnnotation.class, "abbr"),
    ABSTR_KEY(CoreAnnotations.AbstrAnnotation.class, "abstr"),
    FREQ_KEY(CoreAnnotations.FreqAnnotation.class, "freq"),
    WEB_KEY(CoreAnnotations.WebAnnotation.class, "web"),

    // Also have "pos" for PartOfTag (POS is also the TAG_KEY - "tag", but "pos" makes more sense)
    // Still keep "tag" for POS tag so we don't break anything
    POS_TAG_KEY(CoreAnnotations.PartOfSpeechAnnotation.class, "pos"),
    CPOS_TAG_KEY(CoreAnnotations.CoarseTagAnnotation.class, "cpos"),
    DEPREL_KEY(CoreAnnotations.CoNLLDepTypeAnnotation.class, "deprel"),
    HEADIDX_KEY(CoreAnnotations.CoNLLDepParentIndexAnnotation.class, "headidx"),

    // MWT specific annotations
    MWT_TEXT_KEY(CoreAnnotations.MWTTokenTextAnnotation.class, "mwt_text"),
    IS_MWT_KEY(CoreAnnotations.IsMultiWordTokenAnnotation.class, "is_mwt"),
    IS_FIRST_MWT_KEY(CoreAnnotations.IsFirstWordOfMWTAnnotation.class, "is_first_mwt"),
    MWT_MISC_KEY(CoreAnnotations.MWTTokenMiscAnnotation.class, "mwt_misc");

    private final Class<? extends CoreAnnotation<?>> coreKey;
    private final String oldKey;

    <T> KeyLookup(Class<? extends CoreAnnotation<T>> coreKey, String oldKey) {
      this.coreKey = coreKey;
      this.oldKey = oldKey;
    }

    /**
     * This constructor allows us to use reflection for loading old class keys.
     * This is useful because we can then create distributions that do not have
     * all of the classes required for all the old keys (such as trees package classes).
     */
    @SuppressWarnings("unused")
    KeyLookup(String className, String oldKey) {
      Class<?> keyClass;
      try {
       keyClass = Class.forName(className);
      } catch(ClassNotFoundException e) {
        GenericAnnotation<Object> newKey = () -> Object.class;
        keyClass = newKey.getClass();
      }
      this.coreKey = ErasureUtils.uncheckedCast(keyClass);
      this.oldKey = oldKey;
    }

  } // end enum KeyLookup


  /**
   * Returns a CoreAnnotation class key for the given string
   * key if one exists; null otherwise.
   *
   * @param stringKey String form of the key
   * @return A CoreLabel/CoreAnnotation key, or {@code null} if nothing matches
   */
  public static Class<? extends CoreAnnotation<?>> toCoreKey(String stringKey) {
    for (KeyLookup lookup : KeyLookup.values()) {
      if (lookup.oldKey.equals(stringKey)) {
        return lookup.coreKey;
      }
    }
    return null;
  }

  private static final Map<Class<? extends CoreAnnotation<?>>,Class<?>> valueCache = Generics.newHashMap();

  /**
   * Returns the runtime value type associated with the given key.  Caches
   * results in a private Map.
   *
   * @param key The annotation key (non-null)
   * @return The type of the value of that key (non-null)
   */
  @SuppressWarnings("unchecked")
  public static Class<?> getValueType(Class<? extends CoreAnnotation<?>> key) {
    Class type = valueCache.get(key);
    if (type == null) {
      try {
        type = key.newInstance().getType();
      } catch (Exception e) {
        throw new UnsupportedOperationException("Unexpected failure to instantiate - is your key class fancy?", e);
      }
      valueCache.put((Class)key, type);
    }
    return type;
  }

}

