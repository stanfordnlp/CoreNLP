

/* First created by JCasGen Mon May 02 14:10:47 CEST 2011 */
package de.tudarmstadt.ukp.dkpro.core.type;

import org.apache.uima.jcas.JCas; 
import org.apache.uima.jcas.JCasRegistry;
import org.apache.uima.jcas.cas.TOP_Type;

import org.apache.uima.jcas.tcas.Annotation;


/** The part of speech of a word or a phrase.
 * Updated by JCasGen Mon May 02 14:13:32 CEST 2011
 * XML source: /home/jstroetgen/workspace/heideltime-kit/desc/annotator/AnnotationTranslater.xml
 * @generated */
public class POS extends Annotation {
  /** @generated
   * @ordered 
   */
  public final static int typeIndexID = JCasRegistry.register(POS.class);
  /** @generated
   * @ordered 
   */
  public final static int type = typeIndexID;
  /** @generated  */
  public              int getTypeIndexID() {return typeIndexID;}
 
  /** Never called.  Disable default constructor
   * @generated */
  protected POS() {}
    
  /** Internal - constructor used by generator 
   * @generated */
  public POS(int addr, TOP_Type type) {
    super(addr, type);
    readObject();
  }
  
  /** @generated */
  public POS(JCas jcas) {
    super(jcas);
    readObject();   
  } 

  /** @generated */  
  public POS(JCas jcas, int begin, int end) {
    super(jcas);
    setBegin(begin);
    setEnd(end);
    readObject();
  }   

  /** <!-- begin-user-doc -->
    * Write your own initialization here
    * <!-- end-user-doc -->
  @generated modifiable */
  private void readObject() {}
     
 
    
  //*--------------*
  //* Feature: value

  /** getter for value - gets The string representation of POS. Mostly it is the POS representation of the wrapped POS tagger.
   * @generated */
  public String getValue() {
    if (POS_Type.featOkTst && ((POS_Type)jcasType).casFeat_value == null)
      jcasType.jcas.throwFeatMissing("value", "de.tudarmstadt.ukp.dkpro.core.type.POS");
    return jcasType.ll_cas.ll_getStringValue(addr, ((POS_Type)jcasType).casFeatCode_value);}
    
  /** setter for value - sets The string representation of POS. Mostly it is the POS representation of the wrapped POS tagger. 
   * @generated */
  public void setValue(String v) {
    if (POS_Type.featOkTst && ((POS_Type)jcasType).casFeat_value == null)
      jcasType.jcas.throwFeatMissing("value", "de.tudarmstadt.ukp.dkpro.core.type.POS");
    jcasType.ll_cas.ll_setStringValue(addr, ((POS_Type)jcasType).casFeatCode_value, v);}    
  }

    