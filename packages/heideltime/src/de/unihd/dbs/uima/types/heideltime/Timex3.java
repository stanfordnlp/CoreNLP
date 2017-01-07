

/* First created by JCasGen Sat Apr 30 11:35:10 CEST 2011 */
package de.unihd.dbs.uima.types.heideltime;

import org.apache.uima.jcas.JCas; 
import org.apache.uima.jcas.JCasRegistry;
import org.apache.uima.jcas.cas.TOP_Type;

import org.apache.uima.jcas.tcas.Annotation;


/** 
 * Updated by JCasGen Mon Aug 18 14:39:53 CEST 2014
 * XML source: /home/julian/heideltime/heideltime-kit/desc/type/HeidelTime_TypeSystem.xml
 * @generated */
public class Timex3 extends Annotation {
  /** @generated
   * @ordered 
   */
  public final static int typeIndexID = JCasRegistry.register(Timex3.class);
  /** @generated
   * @ordered 
   */
  public final static int type = typeIndexID;
  /** @generated  */
  public              int getTypeIndexID() {return typeIndexID;}
 
  /** Never called.  Disable default constructor
   * @generated */
  protected Timex3() {/* intentionally empty block */}
    
  /** Internal - constructor used by generator 
   * @generated */
  public Timex3(int addr, TOP_Type type) {
    super(addr, type);
    readObject();
  }
  
  /** @generated */
  public Timex3(JCas jcas) {
    super(jcas);
    readObject();   
  } 

  /** @generated */  
  public Timex3(JCas jcas, int begin, int end) {
    super(jcas);
    setBegin(begin);
    setEnd(end);
    readObject();
  }   

  /** 
   * <!-- begin-user-doc -->
    * Write your own initialization here
    * <!-- end-user-doc -->
  *
   * @generated modifiable 
   */
  private void readObject() {}
     
 
    
  //*--------------*
  //* Feature: filename

  /** getter for filename - gets 
   * @generated
   * @return value of the feature 
   */
  public String getFilename() {
    if (Timex3_Type.featOkTst && ((Timex3_Type)jcasType).casFeat_filename == null)
      jcasType.jcas.throwFeatMissing("filename", "de.unihd.dbs.uima.types.heideltime.Timex3");
    return jcasType.ll_cas.ll_getStringValue(addr, ((Timex3_Type)jcasType).casFeatCode_filename);}
    
  /** setter for filename - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setFilename(String v) {
    if (Timex3_Type.featOkTst && ((Timex3_Type)jcasType).casFeat_filename == null)
      jcasType.jcas.throwFeatMissing("filename", "de.unihd.dbs.uima.types.heideltime.Timex3");
    jcasType.ll_cas.ll_setStringValue(addr, ((Timex3_Type)jcasType).casFeatCode_filename, v);}    
   
    
  //*--------------*
  //* Feature: sentId

  /** getter for sentId - gets 
   * @generated
   * @return value of the feature 
   */
  public int getSentId() {
    if (Timex3_Type.featOkTst && ((Timex3_Type)jcasType).casFeat_sentId == null)
      jcasType.jcas.throwFeatMissing("sentId", "de.unihd.dbs.uima.types.heideltime.Timex3");
    return jcasType.ll_cas.ll_getIntValue(addr, ((Timex3_Type)jcasType).casFeatCode_sentId);}
    
  /** setter for sentId - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setSentId(int v) {
    if (Timex3_Type.featOkTst && ((Timex3_Type)jcasType).casFeat_sentId == null)
      jcasType.jcas.throwFeatMissing("sentId", "de.unihd.dbs.uima.types.heideltime.Timex3");
    jcasType.ll_cas.ll_setIntValue(addr, ((Timex3_Type)jcasType).casFeatCode_sentId, v);}    
   
    
  //*--------------*
  //* Feature: firstTokId

  /** getter for firstTokId - gets 
   * @generated
   * @return value of the feature 
   */
  public int getFirstTokId() {
    if (Timex3_Type.featOkTst && ((Timex3_Type)jcasType).casFeat_firstTokId == null)
      jcasType.jcas.throwFeatMissing("firstTokId", "de.unihd.dbs.uima.types.heideltime.Timex3");
    return jcasType.ll_cas.ll_getIntValue(addr, ((Timex3_Type)jcasType).casFeatCode_firstTokId);}
    
  /** setter for firstTokId - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setFirstTokId(int v) {
    if (Timex3_Type.featOkTst && ((Timex3_Type)jcasType).casFeat_firstTokId == null)
      jcasType.jcas.throwFeatMissing("firstTokId", "de.unihd.dbs.uima.types.heideltime.Timex3");
    jcasType.ll_cas.ll_setIntValue(addr, ((Timex3_Type)jcasType).casFeatCode_firstTokId, v);}    
   
    
  //*--------------*
  //* Feature: allTokIds

  /** getter for allTokIds - gets 
   * @generated
   * @return value of the feature 
   */
  public String getAllTokIds() {
    if (Timex3_Type.featOkTst && ((Timex3_Type)jcasType).casFeat_allTokIds == null)
      jcasType.jcas.throwFeatMissing("allTokIds", "de.unihd.dbs.uima.types.heideltime.Timex3");
    return jcasType.ll_cas.ll_getStringValue(addr, ((Timex3_Type)jcasType).casFeatCode_allTokIds);}
    
  /** setter for allTokIds - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setAllTokIds(String v) {
    if (Timex3_Type.featOkTst && ((Timex3_Type)jcasType).casFeat_allTokIds == null)
      jcasType.jcas.throwFeatMissing("allTokIds", "de.unihd.dbs.uima.types.heideltime.Timex3");
    jcasType.ll_cas.ll_setStringValue(addr, ((Timex3_Type)jcasType).casFeatCode_allTokIds, v);}    
   
    
  //*--------------*
  //* Feature: timexId

  /** getter for timexId - gets 
   * @generated
   * @return value of the feature 
   */
  public String getTimexId() {
    if (Timex3_Type.featOkTst && ((Timex3_Type)jcasType).casFeat_timexId == null)
      jcasType.jcas.throwFeatMissing("timexId", "de.unihd.dbs.uima.types.heideltime.Timex3");
    return jcasType.ll_cas.ll_getStringValue(addr, ((Timex3_Type)jcasType).casFeatCode_timexId);}
    
  /** setter for timexId - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setTimexId(String v) {
    if (Timex3_Type.featOkTst && ((Timex3_Type)jcasType).casFeat_timexId == null)
      jcasType.jcas.throwFeatMissing("timexId", "de.unihd.dbs.uima.types.heideltime.Timex3");
    jcasType.ll_cas.ll_setStringValue(addr, ((Timex3_Type)jcasType).casFeatCode_timexId, v);}    
   
    
  //*--------------*
  //* Feature: timexInstance

  /** getter for timexInstance - gets 
   * @generated
   * @return value of the feature 
   */
  public int getTimexInstance() {
    if (Timex3_Type.featOkTst && ((Timex3_Type)jcasType).casFeat_timexInstance == null)
      jcasType.jcas.throwFeatMissing("timexInstance", "de.unihd.dbs.uima.types.heideltime.Timex3");
    return jcasType.ll_cas.ll_getIntValue(addr, ((Timex3_Type)jcasType).casFeatCode_timexInstance);}
    
  /** setter for timexInstance - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setTimexInstance(int v) {
    if (Timex3_Type.featOkTst && ((Timex3_Type)jcasType).casFeat_timexInstance == null)
      jcasType.jcas.throwFeatMissing("timexInstance", "de.unihd.dbs.uima.types.heideltime.Timex3");
    jcasType.ll_cas.ll_setIntValue(addr, ((Timex3_Type)jcasType).casFeatCode_timexInstance, v);}    
   
    
  //*--------------*
  //* Feature: timexType

  /** getter for timexType - gets 
   * @generated
   * @return value of the feature 
   */
  public String getTimexType() {
    if (Timex3_Type.featOkTst && ((Timex3_Type)jcasType).casFeat_timexType == null)
      jcasType.jcas.throwFeatMissing("timexType", "de.unihd.dbs.uima.types.heideltime.Timex3");
    return jcasType.ll_cas.ll_getStringValue(addr, ((Timex3_Type)jcasType).casFeatCode_timexType);}
    
  /** setter for timexType - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setTimexType(String v) {
    if (Timex3_Type.featOkTst && ((Timex3_Type)jcasType).casFeat_timexType == null)
      jcasType.jcas.throwFeatMissing("timexType", "de.unihd.dbs.uima.types.heideltime.Timex3");
    jcasType.ll_cas.ll_setStringValue(addr, ((Timex3_Type)jcasType).casFeatCode_timexType, v);}    
   
    
  //*--------------*
  //* Feature: timexValue

  /** getter for timexValue - gets 
   * @generated
   * @return value of the feature 
   */
  public String getTimexValue() {
    if (Timex3_Type.featOkTst && ((Timex3_Type)jcasType).casFeat_timexValue == null)
      jcasType.jcas.throwFeatMissing("timexValue", "de.unihd.dbs.uima.types.heideltime.Timex3");
    return jcasType.ll_cas.ll_getStringValue(addr, ((Timex3_Type)jcasType).casFeatCode_timexValue);}
    
  /** setter for timexValue - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setTimexValue(String v) {
    if (Timex3_Type.featOkTst && ((Timex3_Type)jcasType).casFeat_timexValue == null)
      jcasType.jcas.throwFeatMissing("timexValue", "de.unihd.dbs.uima.types.heideltime.Timex3");
    jcasType.ll_cas.ll_setStringValue(addr, ((Timex3_Type)jcasType).casFeatCode_timexValue, v);}    
   
    
  //*--------------*
  //* Feature: foundByRule

  /** getter for foundByRule - gets 
   * @generated
   * @return value of the feature 
   */
  public String getFoundByRule() {
    if (Timex3_Type.featOkTst && ((Timex3_Type)jcasType).casFeat_foundByRule == null)
      jcasType.jcas.throwFeatMissing("foundByRule", "de.unihd.dbs.uima.types.heideltime.Timex3");
    return jcasType.ll_cas.ll_getStringValue(addr, ((Timex3_Type)jcasType).casFeatCode_foundByRule);}
    
  /** setter for foundByRule - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setFoundByRule(String v) {
    if (Timex3_Type.featOkTst && ((Timex3_Type)jcasType).casFeat_foundByRule == null)
      jcasType.jcas.throwFeatMissing("foundByRule", "de.unihd.dbs.uima.types.heideltime.Timex3");
    jcasType.ll_cas.ll_setStringValue(addr, ((Timex3_Type)jcasType).casFeatCode_foundByRule, v);}    
   
    
  //*--------------*
  //* Feature: timexQuant

  /** getter for timexQuant - gets 
   * @generated
   * @return value of the feature 
   */
  public String getTimexQuant() {
    if (Timex3_Type.featOkTst && ((Timex3_Type)jcasType).casFeat_timexQuant == null)
      jcasType.jcas.throwFeatMissing("timexQuant", "de.unihd.dbs.uima.types.heideltime.Timex3");
    return jcasType.ll_cas.ll_getStringValue(addr, ((Timex3_Type)jcasType).casFeatCode_timexQuant);}
    
  /** setter for timexQuant - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setTimexQuant(String v) {
    if (Timex3_Type.featOkTst && ((Timex3_Type)jcasType).casFeat_timexQuant == null)
      jcasType.jcas.throwFeatMissing("timexQuant", "de.unihd.dbs.uima.types.heideltime.Timex3");
    jcasType.ll_cas.ll_setStringValue(addr, ((Timex3_Type)jcasType).casFeatCode_timexQuant, v);}    
   
    
  //*--------------*
  //* Feature: timexFreq

  /** getter for timexFreq - gets 
   * @generated
   * @return value of the feature 
   */
  public String getTimexFreq() {
    if (Timex3_Type.featOkTst && ((Timex3_Type)jcasType).casFeat_timexFreq == null)
      jcasType.jcas.throwFeatMissing("timexFreq", "de.unihd.dbs.uima.types.heideltime.Timex3");
    return jcasType.ll_cas.ll_getStringValue(addr, ((Timex3_Type)jcasType).casFeatCode_timexFreq);}
    
  /** setter for timexFreq - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setTimexFreq(String v) {
    if (Timex3_Type.featOkTst && ((Timex3_Type)jcasType).casFeat_timexFreq == null)
      jcasType.jcas.throwFeatMissing("timexFreq", "de.unihd.dbs.uima.types.heideltime.Timex3");
    jcasType.ll_cas.ll_setStringValue(addr, ((Timex3_Type)jcasType).casFeatCode_timexFreq, v);}    
   
    
  //*--------------*
  //* Feature: timexMod

  /** getter for timexMod - gets 
   * @generated
   * @return value of the feature 
   */
  public String getTimexMod() {
    if (Timex3_Type.featOkTst && ((Timex3_Type)jcasType).casFeat_timexMod == null)
      jcasType.jcas.throwFeatMissing("timexMod", "de.unihd.dbs.uima.types.heideltime.Timex3");
    return jcasType.ll_cas.ll_getStringValue(addr, ((Timex3_Type)jcasType).casFeatCode_timexMod);}
    
  /** setter for timexMod - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setTimexMod(String v) {
    if (Timex3_Type.featOkTst && ((Timex3_Type)jcasType).casFeat_timexMod == null)
      jcasType.jcas.throwFeatMissing("timexMod", "de.unihd.dbs.uima.types.heideltime.Timex3");
    jcasType.ll_cas.ll_setStringValue(addr, ((Timex3_Type)jcasType).casFeatCode_timexMod, v);}    
   
    
  //*--------------*
  //* Feature: emptyValue

  /** getter for emptyValue - gets attribute to hold a value for "empty" timex3 tags
   * @generated
   * @return value of the feature 
   */
  public String getEmptyValue() {
    if (Timex3_Type.featOkTst && ((Timex3_Type)jcasType).casFeat_emptyValue == null)
      jcasType.jcas.throwFeatMissing("emptyValue", "de.unihd.dbs.uima.types.heideltime.Timex3");
    return jcasType.ll_cas.ll_getStringValue(addr, ((Timex3_Type)jcasType).casFeatCode_emptyValue);}
    
  /** setter for emptyValue - sets attribute to hold a value for "empty" timex3 tags 
   * @generated
   * @param v value to set into the feature 
   */
  public void setEmptyValue(String v) {
    if (Timex3_Type.featOkTst && ((Timex3_Type)jcasType).casFeat_emptyValue == null)
      jcasType.jcas.throwFeatMissing("emptyValue", "de.unihd.dbs.uima.types.heideltime.Timex3");
    jcasType.ll_cas.ll_setStringValue(addr, ((Timex3_Type)jcasType).casFeatCode_emptyValue, v);}    
  }

    