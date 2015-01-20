

/* First created by JCasGen Thu Sep 20 15:38:14 CEST 2012 */
package de.unihd.dbs.uima.types.heideltime;

import org.apache.uima.jcas.JCas; 
import org.apache.uima.jcas.JCasRegistry;
import org.apache.uima.jcas.cas.TOP_Type;



/** 
 * Updated by JCasGen Mon Aug 18 14:39:53 CEST 2014
 * XML source: /home/julian/heideltime/heideltime-kit/desc/type/HeidelTime_TypeSystem.xml
 * @generated */
public class Timex3Interval extends Timex3 {
  /** @generated
   * @ordered 
   */
  public final static int typeIndexID = JCasRegistry.register(Timex3Interval.class);
  /** @generated
   * @ordered 
   */
  public final static int type = typeIndexID;
  /** @generated  */
  @Override
  public              int getTypeIndexID() {return typeIndexID;}
 
  /** Never called.  Disable default constructor
   * @generated */
  protected Timex3Interval() {/* intentionally empty block */}
    
  /** Internal - constructor used by generator 
   * @generated */
  public Timex3Interval(int addr, TOP_Type type) {
    super(addr, type);
    readObject();
  }
  
  /** @generated */
  public Timex3Interval(JCas jcas) {
    super(jcas);
    readObject();   
  } 

  /** @generated */  
  public Timex3Interval(JCas jcas, int begin, int end) {
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
  //* Feature: TimexValueEB

  /** getter for TimexValueEB - gets 
   * @generated
   * @return value of the feature 
   */
  public String getTimexValueEB() {
    if (Timex3Interval_Type.featOkTst && ((Timex3Interval_Type)jcasType).casFeat_TimexValueEB == null)
      jcasType.jcas.throwFeatMissing("TimexValueEB", "de.unihd.dbs.uima.types.heideltime.Timex3Interval");
    return jcasType.ll_cas.ll_getStringValue(addr, ((Timex3Interval_Type)jcasType).casFeatCode_TimexValueEB);}
    
  /** setter for TimexValueEB - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setTimexValueEB(String v) {
    if (Timex3Interval_Type.featOkTst && ((Timex3Interval_Type)jcasType).casFeat_TimexValueEB == null)
      jcasType.jcas.throwFeatMissing("TimexValueEB", "de.unihd.dbs.uima.types.heideltime.Timex3Interval");
    jcasType.ll_cas.ll_setStringValue(addr, ((Timex3Interval_Type)jcasType).casFeatCode_TimexValueEB, v);}    
   
    
  //*--------------*
  //* Feature: TimexValueLE

  /** getter for TimexValueLE - gets 
   * @generated
   * @return value of the feature 
   */
  public String getTimexValueLE() {
    if (Timex3Interval_Type.featOkTst && ((Timex3Interval_Type)jcasType).casFeat_TimexValueLE == null)
      jcasType.jcas.throwFeatMissing("TimexValueLE", "de.unihd.dbs.uima.types.heideltime.Timex3Interval");
    return jcasType.ll_cas.ll_getStringValue(addr, ((Timex3Interval_Type)jcasType).casFeatCode_TimexValueLE);}
    
  /** setter for TimexValueLE - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setTimexValueLE(String v) {
    if (Timex3Interval_Type.featOkTst && ((Timex3Interval_Type)jcasType).casFeat_TimexValueLE == null)
      jcasType.jcas.throwFeatMissing("TimexValueLE", "de.unihd.dbs.uima.types.heideltime.Timex3Interval");
    jcasType.ll_cas.ll_setStringValue(addr, ((Timex3Interval_Type)jcasType).casFeatCode_TimexValueLE, v);}    
   
    
  //*--------------*
  //* Feature: TimexValueEE

  /** getter for TimexValueEE - gets 
   * @generated
   * @return value of the feature 
   */
  public String getTimexValueEE() {
    if (Timex3Interval_Type.featOkTst && ((Timex3Interval_Type)jcasType).casFeat_TimexValueEE == null)
      jcasType.jcas.throwFeatMissing("TimexValueEE", "de.unihd.dbs.uima.types.heideltime.Timex3Interval");
    return jcasType.ll_cas.ll_getStringValue(addr, ((Timex3Interval_Type)jcasType).casFeatCode_TimexValueEE);}
    
  /** setter for TimexValueEE - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setTimexValueEE(String v) {
    if (Timex3Interval_Type.featOkTst && ((Timex3Interval_Type)jcasType).casFeat_TimexValueEE == null)
      jcasType.jcas.throwFeatMissing("TimexValueEE", "de.unihd.dbs.uima.types.heideltime.Timex3Interval");
    jcasType.ll_cas.ll_setStringValue(addr, ((Timex3Interval_Type)jcasType).casFeatCode_TimexValueEE, v);}    
   
    
  //*--------------*
  //* Feature: TimexValueLB

  /** getter for TimexValueLB - gets 
   * @generated
   * @return value of the feature 
   */
  public String getTimexValueLB() {
    if (Timex3Interval_Type.featOkTst && ((Timex3Interval_Type)jcasType).casFeat_TimexValueLB == null)
      jcasType.jcas.throwFeatMissing("TimexValueLB", "de.unihd.dbs.uima.types.heideltime.Timex3Interval");
    return jcasType.ll_cas.ll_getStringValue(addr, ((Timex3Interval_Type)jcasType).casFeatCode_TimexValueLB);}
    
  /** setter for TimexValueLB - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setTimexValueLB(String v) {
    if (Timex3Interval_Type.featOkTst && ((Timex3Interval_Type)jcasType).casFeat_TimexValueLB == null)
      jcasType.jcas.throwFeatMissing("TimexValueLB", "de.unihd.dbs.uima.types.heideltime.Timex3Interval");
    jcasType.ll_cas.ll_setStringValue(addr, ((Timex3Interval_Type)jcasType).casFeatCode_TimexValueLB, v);}    
   
    
  //*--------------*
  //* Feature: emptyValue

  /** getter for emptyValue - gets 
   * @generated
   * @return value of the feature 
   */
  public String getEmptyValue() {
    if (Timex3Interval_Type.featOkTst && ((Timex3Interval_Type)jcasType).casFeat_emptyValue == null)
      jcasType.jcas.throwFeatMissing("emptyValue", "de.unihd.dbs.uima.types.heideltime.Timex3Interval");
    return jcasType.ll_cas.ll_getStringValue(addr, ((Timex3Interval_Type)jcasType).casFeatCode_emptyValue);}
    
  /** setter for emptyValue - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setEmptyValue(String v) {
    if (Timex3Interval_Type.featOkTst && ((Timex3Interval_Type)jcasType).casFeat_emptyValue == null)
      jcasType.jcas.throwFeatMissing("emptyValue", "de.unihd.dbs.uima.types.heideltime.Timex3Interval");
    jcasType.ll_cas.ll_setStringValue(addr, ((Timex3Interval_Type)jcasType).casFeatCode_emptyValue, v);}    
   
    
  //*--------------*
  //* Feature: beginTimex

  /** getter for beginTimex - gets 
   * @generated
   * @return value of the feature 
   */
  public String getBeginTimex() {
    if (Timex3Interval_Type.featOkTst && ((Timex3Interval_Type)jcasType).casFeat_beginTimex == null)
      jcasType.jcas.throwFeatMissing("beginTimex", "de.unihd.dbs.uima.types.heideltime.Timex3Interval");
    return jcasType.ll_cas.ll_getStringValue(addr, ((Timex3Interval_Type)jcasType).casFeatCode_beginTimex);}
    
  /** setter for beginTimex - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setBeginTimex(String v) {
    if (Timex3Interval_Type.featOkTst && ((Timex3Interval_Type)jcasType).casFeat_beginTimex == null)
      jcasType.jcas.throwFeatMissing("beginTimex", "de.unihd.dbs.uima.types.heideltime.Timex3Interval");
    jcasType.ll_cas.ll_setStringValue(addr, ((Timex3Interval_Type)jcasType).casFeatCode_beginTimex, v);}    
   
    
  //*--------------*
  //* Feature: endTimex

  /** getter for endTimex - gets 
   * @generated
   * @return value of the feature 
   */
  public String getEndTimex() {
    if (Timex3Interval_Type.featOkTst && ((Timex3Interval_Type)jcasType).casFeat_endTimex == null)
      jcasType.jcas.throwFeatMissing("endTimex", "de.unihd.dbs.uima.types.heideltime.Timex3Interval");
    return jcasType.ll_cas.ll_getStringValue(addr, ((Timex3Interval_Type)jcasType).casFeatCode_endTimex);}
    
  /** setter for endTimex - sets  
   * @generated
   * @param v value to set into the feature 
   */
  public void setEndTimex(String v) {
    if (Timex3Interval_Type.featOkTst && ((Timex3Interval_Type)jcasType).casFeat_endTimex == null)
      jcasType.jcas.throwFeatMissing("endTimex", "de.unihd.dbs.uima.types.heideltime.Timex3Interval");
    jcasType.ll_cas.ll_setStringValue(addr, ((Timex3Interval_Type)jcasType).casFeatCode_endTimex, v);}    
  }

    