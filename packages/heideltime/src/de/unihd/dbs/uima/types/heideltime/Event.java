

/* First created by JCasGen Thu Sep 20 15:38:13 CEST 2012 */
package de.unihd.dbs.uima.types.heideltime;

import org.apache.uima.jcas.JCas; 
import org.apache.uima.jcas.JCasRegistry;
import org.apache.uima.jcas.cas.TOP_Type;

import org.apache.uima.jcas.tcas.Annotation;


/** 
 * Updated by JCasGen Mon Aug 18 14:39:53 CEST 2014
 * XML source: /home/julian/heideltime/heideltime-kit/desc/type/HeidelTime_TypeSystem.xml
 * @generated */
public class Event extends Annotation {
  /** @generated
   * @ordered 
   */
  public final static int typeIndexID = JCasRegistry.register(Event.class);
  /** @generated
   * @ordered 
   */
  public final static int type = typeIndexID;
  /** @generated  */
  @Override
  public              int getTypeIndexID() {return typeIndexID;}
 
  /** Never called.  Disable default constructor
   * @generated */
  protected Event() {/* intentionally empty block */}
    
  /** Internal - constructor used by generator 
   * @generated */
  public Event(int addr, TOP_Type type) {
    super(addr, type);
    readObject();
  }
  
  /** @generated */
  public Event(JCas jcas) {
    super(jcas);
    readObject();   
  } 

  /** @generated */  
  public Event(JCas jcas, int begin, int end) {
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
  private void readObject() {/*default - does nothing empty block */}
     
 
    
  //*--------------*
  //* Feature: filename

  /** getter for filename - gets 
   * @generated
   * @return value of the feature 
   */
  public String getFilename() {
    if (Event_Type.featOkTst && ((Event_Type)jcasType).casFeat_filename == null)
      jcasType.jcas.throwFeatMissing("filename", "de.unihd.dbs.uima.types.heideltime.Event");
    return jcasType.ll_cas.ll_getStringValue(addr, ((Event_Type)jcasType).casFeatCode_filename);}
    
  /** setter for filename - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setFilename(String v) {
    if (Event_Type.featOkTst && ((Event_Type)jcasType).casFeat_filename == null)
      jcasType.jcas.throwFeatMissing("filename", "de.unihd.dbs.uima.types.heideltime.Event");
    jcasType.ll_cas.ll_setStringValue(addr, ((Event_Type)jcasType).casFeatCode_filename, v);}    
   
    
  //*--------------*
  //* Feature: sentId

  /** getter for sentId - gets 
   * @generated
   * @return value of the feature 
   */
  public int getSentId() {
    if (Event_Type.featOkTst && ((Event_Type)jcasType).casFeat_sentId == null)
      jcasType.jcas.throwFeatMissing("sentId", "de.unihd.dbs.uima.types.heideltime.Event");
    return jcasType.ll_cas.ll_getIntValue(addr, ((Event_Type)jcasType).casFeatCode_sentId);}
    
  /** setter for sentId - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setSentId(int v) {
    if (Event_Type.featOkTst && ((Event_Type)jcasType).casFeat_sentId == null)
      jcasType.jcas.throwFeatMissing("sentId", "de.unihd.dbs.uima.types.heideltime.Event");
    jcasType.ll_cas.ll_setIntValue(addr, ((Event_Type)jcasType).casFeatCode_sentId, v);}    
   
    
  //*--------------*
  //* Feature: tokId

  /** getter for tokId - gets 
   * @generated
   * @return value of the feature 
   */
  public int getTokId() {
    if (Event_Type.featOkTst && ((Event_Type)jcasType).casFeat_tokId == null)
      jcasType.jcas.throwFeatMissing("tokId", "de.unihd.dbs.uima.types.heideltime.Event");
    return jcasType.ll_cas.ll_getIntValue(addr, ((Event_Type)jcasType).casFeatCode_tokId);}
    
  /** setter for tokId - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setTokId(int v) {
    if (Event_Type.featOkTst && ((Event_Type)jcasType).casFeat_tokId == null)
      jcasType.jcas.throwFeatMissing("tokId", "de.unihd.dbs.uima.types.heideltime.Event");
    jcasType.ll_cas.ll_setIntValue(addr, ((Event_Type)jcasType).casFeatCode_tokId, v);}    
   
    
  //*--------------*
  //* Feature: eventId

  /** getter for eventId - gets 
   * @generated
   * @return value of the feature 
   */
  public String getEventId() {
    if (Event_Type.featOkTst && ((Event_Type)jcasType).casFeat_eventId == null)
      jcasType.jcas.throwFeatMissing("eventId", "de.unihd.dbs.uima.types.heideltime.Event");
    return jcasType.ll_cas.ll_getStringValue(addr, ((Event_Type)jcasType).casFeatCode_eventId);}
    
  /** setter for eventId - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setEventId(String v) {
    if (Event_Type.featOkTst && ((Event_Type)jcasType).casFeat_eventId == null)
      jcasType.jcas.throwFeatMissing("eventId", "de.unihd.dbs.uima.types.heideltime.Event");
    jcasType.ll_cas.ll_setStringValue(addr, ((Event_Type)jcasType).casFeatCode_eventId, v);}    
   
    
  //*--------------*
  //* Feature: eventInstanceId

  /** getter for eventInstanceId - gets 
   * @generated
   * @return value of the feature 
   */
  public int getEventInstanceId() {
    if (Event_Type.featOkTst && ((Event_Type)jcasType).casFeat_eventInstanceId == null)
      jcasType.jcas.throwFeatMissing("eventInstanceId", "de.unihd.dbs.uima.types.heideltime.Event");
    return jcasType.ll_cas.ll_getIntValue(addr, ((Event_Type)jcasType).casFeatCode_eventInstanceId);}
    
  /** setter for eventInstanceId - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setEventInstanceId(int v) {
    if (Event_Type.featOkTst && ((Event_Type)jcasType).casFeat_eventInstanceId == null)
      jcasType.jcas.throwFeatMissing("eventInstanceId", "de.unihd.dbs.uima.types.heideltime.Event");
    jcasType.ll_cas.ll_setIntValue(addr, ((Event_Type)jcasType).casFeatCode_eventInstanceId, v);}    
   
    
  //*--------------*
  //* Feature: aspect

  /** getter for aspect - gets 
   * @generated
   * @return value of the feature 
   */
  public String getAspect() {
    if (Event_Type.featOkTst && ((Event_Type)jcasType).casFeat_aspect == null)
      jcasType.jcas.throwFeatMissing("aspect", "de.unihd.dbs.uima.types.heideltime.Event");
    return jcasType.ll_cas.ll_getStringValue(addr, ((Event_Type)jcasType).casFeatCode_aspect);}
    
  /** setter for aspect - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setAspect(String v) {
    if (Event_Type.featOkTst && ((Event_Type)jcasType).casFeat_aspect == null)
      jcasType.jcas.throwFeatMissing("aspect", "de.unihd.dbs.uima.types.heideltime.Event");
    jcasType.ll_cas.ll_setStringValue(addr, ((Event_Type)jcasType).casFeatCode_aspect, v);}    
   
    
  //*--------------*
  //* Feature: modality

  /** getter for modality - gets 
   * @generated
   * @return value of the feature 
   */
  public String getModality() {
    if (Event_Type.featOkTst && ((Event_Type)jcasType).casFeat_modality == null)
      jcasType.jcas.throwFeatMissing("modality", "de.unihd.dbs.uima.types.heideltime.Event");
    return jcasType.ll_cas.ll_getStringValue(addr, ((Event_Type)jcasType).casFeatCode_modality);}
    
  /** setter for modality - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setModality(String v) {
    if (Event_Type.featOkTst && ((Event_Type)jcasType).casFeat_modality == null)
      jcasType.jcas.throwFeatMissing("modality", "de.unihd.dbs.uima.types.heideltime.Event");
    jcasType.ll_cas.ll_setStringValue(addr, ((Event_Type)jcasType).casFeatCode_modality, v);}    
   
    
  //*--------------*
  //* Feature: polarity

  /** getter for polarity - gets 
   * @generated
   * @return value of the feature 
   */
  public String getPolarity() {
    if (Event_Type.featOkTst && ((Event_Type)jcasType).casFeat_polarity == null)
      jcasType.jcas.throwFeatMissing("polarity", "de.unihd.dbs.uima.types.heideltime.Event");
    return jcasType.ll_cas.ll_getStringValue(addr, ((Event_Type)jcasType).casFeatCode_polarity);}
    
  /** setter for polarity - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setPolarity(String v) {
    if (Event_Type.featOkTst && ((Event_Type)jcasType).casFeat_polarity == null)
      jcasType.jcas.throwFeatMissing("polarity", "de.unihd.dbs.uima.types.heideltime.Event");
    jcasType.ll_cas.ll_setStringValue(addr, ((Event_Type)jcasType).casFeatCode_polarity, v);}    
   
    
  //*--------------*
  //* Feature: tense

  /** getter for tense - gets 
   * @generated
   * @return value of the feature 
   */
  public String getTense() {
    if (Event_Type.featOkTst && ((Event_Type)jcasType).casFeat_tense == null)
      jcasType.jcas.throwFeatMissing("tense", "de.unihd.dbs.uima.types.heideltime.Event");
    return jcasType.ll_cas.ll_getStringValue(addr, ((Event_Type)jcasType).casFeatCode_tense);}
    
  /** setter for tense - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setTense(String v) {
    if (Event_Type.featOkTst && ((Event_Type)jcasType).casFeat_tense == null)
      jcasType.jcas.throwFeatMissing("tense", "de.unihd.dbs.uima.types.heideltime.Event");
    jcasType.ll_cas.ll_setStringValue(addr, ((Event_Type)jcasType).casFeatCode_tense, v);}    
   
    
  //*--------------*
  //* Feature: token

  /** getter for token - gets 
   * @generated
   * @return value of the feature 
   */
  public Token getToken() {
    if (Event_Type.featOkTst && ((Event_Type)jcasType).casFeat_token == null)
      jcasType.jcas.throwFeatMissing("token", "de.unihd.dbs.uima.types.heideltime.Event");
    return (Token)(jcasType.ll_cas.ll_getFSForRef(jcasType.ll_cas.ll_getRefValue(addr, ((Event_Type)jcasType).casFeatCode_token)));}
    
  /** setter for token - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setToken(Token v) {
    if (Event_Type.featOkTst && ((Event_Type)jcasType).casFeat_token == null)
      jcasType.jcas.throwFeatMissing("token", "de.unihd.dbs.uima.types.heideltime.Event");
    jcasType.ll_cas.ll_setRefValue(addr, ((Event_Type)jcasType).casFeatCode_token, jcasType.ll_cas.ll_getFSRef(v));}    
  }

    