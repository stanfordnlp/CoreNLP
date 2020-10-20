package edu.stanford.nlp.ie.machinereading.structure;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;

/**
 * Represents any object that can be extracted: entity, relation, event.
 * 
 * @author Andrey Gusev
 * @author Mihai
 * 
 */
public class ExtractionObject implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Unique identifier of the object in its document */
  protected final String objectId;
  
  /** 
   * Sentence that contains this object
   * This assumes that each extraction object is intra-sentential (true in ACE, Roth, BioNLP, MR) 
   */
  protected CoreMap sentence;
  
  /** Type of this mention, e.g., GPE */
  protected String type;
  
  /** Subtype, if available, e.g., GPE.CITY */
  protected final String subType;
  
  /** 
   * Maximal token span relevant for this object, e.g., the largest NP for an entity mention
   * The offsets are relative to the sentence that contains this object 
   */
  protected Span extentTokenSpan;
  
  /** This stores any optional attributes of ExtractionObjects */
  protected CoreMap attributeMap;

  /** 
   * Probabilities associated with this object 
   * We report probability values for each possible type for this object
   */
  protected Counter<String> typeProbabilities;

  public ExtractionObject(String objectId, 
      CoreMap sentence,
      Span span,
      String type,
      String subtype) {
    this.objectId = objectId;
    this.sentence = sentence;
    this.extentTokenSpan = span;
    this.type = type.intern();
    this.subType = (subtype != null ? subtype.intern() : null);
    this.attributeMap = null;
  }

  public String getObjectId() {
    return objectId;
  }

  public String getDocumentId() {
    return sentence.get(CoreAnnotations.DocIDAnnotation.class);
  }
  
  public CoreMap getSentence() {
    return sentence;
  }
  
  public void setSentence(CoreMap sent) {
  	this.sentence = sent;
  }
  
  public int getExtentTokenStart() { return extentTokenSpan.start(); }

  public int getExtentTokenEnd() { return extentTokenSpan.end(); }
  
  public Span getExtent() { return extentTokenSpan; }
  
  public void setExtent(Span s) {
    extentTokenSpan = s;
  }
  
  public String getExtentString() {
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    StringBuilder sb = new StringBuilder();
    for (int i = extentTokenSpan.start(); i < extentTokenSpan.end(); i ++){
      CoreLabel token = tokens.get(i);
      if(i > extentTokenSpan.start()) sb.append(" ");
      sb.append(token.word());
    }
    return sb.toString();
  }
  
  public String getType() { return type; }
  
  public String getSubType() { return subType; }
  
  @Override
  public boolean equals(Object other) {
    if (! (other instanceof ExtractionObject)) return false;
    ExtractionObject o = (ExtractionObject) other;
    return o.objectId.equals(objectId) && o.sentence.get(CoreAnnotations.TextAnnotation.class).equals(sentence.get(CoreAnnotations.TextAnnotation.class));
  }

  static class CompByExtent implements Comparator<ExtractionObject> {
    @Override
    public int compare(ExtractionObject o1, ExtractionObject o2) {
      if (o1.getExtentTokenStart() < o2.getExtentTokenStart()){
        return -1;
      } else if (o1.getExtentTokenStart() > o2.getExtentTokenStart()){
        return 1;
      } else return Integer.compare(o1.getExtentTokenEnd(), o2.getExtentTokenEnd());
    }
  }
  
  public static void sortByExtent(List<ExtractionObject> objects) {
    objects.sort(new CompByExtent());
  }
  
  /**
   * Returns the smallest span that covers the extent of all these objects
   * @param objs
   */
  public static Span getSpan(ExtractionObject ... objs) {
    int left = Integer.MAX_VALUE;
    int right = Integer.MIN_VALUE;
    for (ExtractionObject obj : objs) {
      if (obj.getExtentTokenStart() < left) {
        left = obj.getExtentTokenStart();
      }
      if (obj.getExtentTokenEnd() > right) {
        right = obj.getExtentTokenEnd();
      }
    }
    assert(left < Integer.MAX_VALUE);
    assert(right > Integer.MIN_VALUE);
    return new Span(left, right);
  }
  
  /** 
   * Returns the text corresponding to the extent of this object
   */
  public String getValue() {
    return getFullValue();
  }
  
  /**
   * Always returns the text corresponding to the extent of this object, even when
   * getValue is overridden by subclass.
   */
  final public String getFullValue() {
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    StringBuilder sb = new StringBuilder();
    if(tokens != null && extentTokenSpan != null){
      for(int i = extentTokenSpan.start(); i < extentTokenSpan.end(); i ++){
        if(i > extentTokenSpan.start()) sb.append(' ');
        sb.append(tokens.get(i).word());
      }
    }
    return sb.toString();
  }

  public void setType(String t) {
    this.type = t;
  }
  
  private static final String TYPE_SEP = "/";
  
  /**
   * Concatenates two types
   * @param t1
   * @param t2
   */
  public static String concatenateTypes(String t1, String t2) {
    String [] t1Toks = t1.split(TYPE_SEP);
    String [] t2Toks = t2.split(TYPE_SEP);
    Set<String> uniqueTypes = Generics.newHashSet();
    for(String t: t1Toks) uniqueTypes.add(t);
    for(String t: t2Toks) uniqueTypes.add(t);
    String [] types = new String[uniqueTypes.size()];
    uniqueTypes.toArray(types);
    Arrays.sort(types);
    StringBuilder os = new StringBuilder();
    for(int i = 0; i < types.length; i ++){
      if(i > 0) os.append(TYPE_SEP);
      os.append(types[i]);
    }
    return os.toString();
  }
  
  public CoreMap attributeMap() {
    if(attributeMap == null){
      attributeMap = new ArrayCoreMap();
    }
    return attributeMap;
  }
  
  public void setTypeProbabilities(Counter<String> probs) {
    typeProbabilities = probs;
  }
  public Counter<String> getTypeProbabilities() {
    return typeProbabilities;
  }
  String probsToString() {
    List<Pair<String, Double>> sorted = Counters.toDescendingMagnitudeSortedListWithCounts(typeProbabilities);
    StringBuilder os = new StringBuilder();
    os.append('{');
    boolean first = true;
    for(Pair<String, Double> lv: sorted) {
      if(! first) os.append("; ");
      os.append(lv.first + ", " + lv.second);
      first = false;
    }
    os.append('}');
    return os.toString();
  }
  
  /**
   * Returns true if it's worth saving/printing this object
   * This happens in two cases:
   * 1. The type of the object is not nilLabel
   * 2. The type of the object is nilLabel but the second ranked label is within the given beam (0 -- 100) of the first choice
   * @param beam
   * @param nilLabel
   */
  public boolean printableObject(double beam, String nilLabel) {
    if (typeProbabilities == null) { return false; }
    List<Pair<String, Double>> sorted = Counters.toDescendingMagnitudeSortedListWithCounts(typeProbabilities);
    
    // first choice not nil
    if(sorted.size() > 0 && ! sorted.get(0).first.equals(nilLabel)){
      return true;
    }
    
    // first choice is nil, but second is within beam
    if(sorted.size() > 1 && sorted.get(0).first.equals(nilLabel) && beam > 0 &&
        100.0 * (sorted.get(0).second - sorted.get(1).second) < beam){
      return true;
    }

    return false;
  }

}
