
/* First created by JCasGen Wed May 04 15:59:23 CEST 2011 */
package de.unihd.dbs.uima.types.heideltime;

import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.JCasRegistry;
import org.apache.uima.cas.impl.CASImpl;
import org.apache.uima.cas.impl.FSGenerator;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.impl.TypeImpl;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.impl.FeatureImpl;
import org.apache.uima.cas.Feature;
import org.apache.uima.jcas.tcas.Annotation_Type;

/** 
 * Updated by JCasGen Mon Aug 18 14:39:53 CEST 2014
 * @generated */
public class SourceDocInfo_Type extends Annotation_Type {
  /** @generated */
  protected FSGenerator getFSGenerator() {return fsGenerator;}
  /** @generated */
  private final FSGenerator fsGenerator = 
    new FSGenerator() {
      public FeatureStructure createFS(int addr, CASImpl cas) {
  			 if (SourceDocInfo_Type.this.useExistingInstance) {
  			   // Return eq fs instance if already created
  		     FeatureStructure fs = SourceDocInfo_Type.this.jcas.getJfsFromCaddr(addr);
  		     if (null == fs) {
  		       fs = new SourceDocInfo(addr, SourceDocInfo_Type.this);
  			   SourceDocInfo_Type.this.jcas.putJfsFromCaddr(addr, fs);
  			   return fs;
  		     }
  		     return fs;
        } else return new SourceDocInfo(addr, SourceDocInfo_Type.this);
  	  }
    };
  /** @generated */
  public final static int typeIndexID = SourceDocInfo.typeIndexID;
  /** @generated 
     @modifiable */
  public final static boolean featOkTst = JCasRegistry.getFeatOkTst("de.unihd.dbs.uima.types.heideltime.SourceDocInfo");
 
  /** @generated */
  final Feature casFeat_uri;
  /** @generated */
  final int     casFeatCode_uri;
  /** @generated */ 
  public String getUri(int addr) {
        if (featOkTst && casFeat_uri == null)
      jcas.throwFeatMissing("uri", "de.unihd.dbs.uima.types.heideltime.SourceDocInfo");
    return ll_cas.ll_getStringValue(addr, casFeatCode_uri);
  }
  /** @generated */    
  public void setUri(int addr, String v) {
        if (featOkTst && casFeat_uri == null)
      jcas.throwFeatMissing("uri", "de.unihd.dbs.uima.types.heideltime.SourceDocInfo");
    ll_cas.ll_setStringValue(addr, casFeatCode_uri, v);}
    
  
 
  /** @generated */
  final Feature casFeat_offsetInSource;
  /** @generated */
  final int     casFeatCode_offsetInSource;
  /** @generated */ 
  public int getOffsetInSource(int addr) {
        if (featOkTst && casFeat_offsetInSource == null)
      jcas.throwFeatMissing("offsetInSource", "de.unihd.dbs.uima.types.heideltime.SourceDocInfo");
    return ll_cas.ll_getIntValue(addr, casFeatCode_offsetInSource);
  }
  /** @generated */    
  public void setOffsetInSource(int addr, int v) {
        if (featOkTst && casFeat_offsetInSource == null)
      jcas.throwFeatMissing("offsetInSource", "de.unihd.dbs.uima.types.heideltime.SourceDocInfo");
    ll_cas.ll_setIntValue(addr, casFeatCode_offsetInSource, v);}
    
  



  /** initialize variables to correspond with Cas Type and Features
	* @generated */
  public SourceDocInfo_Type(JCas jcas, Type casType) {
    super(jcas, casType);
    casImpl.getFSClassRegistry().addGeneratorForType((TypeImpl)this.casType, getFSGenerator());

 
    casFeat_uri = jcas.getRequiredFeatureDE(casType, "uri", "uima.cas.String", featOkTst);
    casFeatCode_uri  = (null == casFeat_uri) ? JCas.INVALID_FEATURE_CODE : ((FeatureImpl)casFeat_uri).getCode();

 
    casFeat_offsetInSource = jcas.getRequiredFeatureDE(casType, "offsetInSource", "uima.cas.Integer", featOkTst);
    casFeatCode_offsetInSource  = (null == casFeat_offsetInSource) ? JCas.INVALID_FEATURE_CODE : ((FeatureImpl)casFeat_offsetInSource).getCode();

  }
}



    