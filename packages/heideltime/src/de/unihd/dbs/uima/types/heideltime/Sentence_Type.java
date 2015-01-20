
/* First created by JCasGen Sat Apr 30 11:35:10 CEST 2011 */
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
public class Sentence_Type extends Annotation_Type {
  /** @generated */
  protected FSGenerator getFSGenerator() {return fsGenerator;}
  /** @generated */
  private final FSGenerator fsGenerator = 
    new FSGenerator() {
      public FeatureStructure createFS(int addr, CASImpl cas) {
  			 if (Sentence_Type.this.useExistingInstance) {
  			   // Return eq fs instance if already created
  		     FeatureStructure fs = Sentence_Type.this.jcas.getJfsFromCaddr(addr);
  		     if (null == fs) {
  		       fs = new Sentence(addr, Sentence_Type.this);
  			   Sentence_Type.this.jcas.putJfsFromCaddr(addr, fs);
  			   return fs;
  		     }
  		     return fs;
        } else return new Sentence(addr, Sentence_Type.this);
  	  }
    };
  /** @generated */
  public final static int typeIndexID = Sentence.typeIndexID;
  /** @generated 
     @modifiable */
  public final static boolean featOkTst = JCasRegistry.getFeatOkTst("de.unihd.dbs.uima.types.heideltime.Sentence");
 
  /** @generated */
  final Feature casFeat_filename;
  /** @generated */
  final int     casFeatCode_filename;
  /** @generated */ 
  public String getFilename(int addr) {
        if (featOkTst && casFeat_filename == null)
      jcas.throwFeatMissing("filename", "de.unihd.dbs.uima.types.heideltime.Sentence");
    return ll_cas.ll_getStringValue(addr, casFeatCode_filename);
  }
  /** @generated */    
  public void setFilename(int addr, String v) {
        if (featOkTst && casFeat_filename == null)
      jcas.throwFeatMissing("filename", "de.unihd.dbs.uima.types.heideltime.Sentence");
    ll_cas.ll_setStringValue(addr, casFeatCode_filename, v);}
    
  
 
  /** @generated */
  final Feature casFeat_sentenceId;
  /** @generated */
  final int     casFeatCode_sentenceId;
  /** @generated */ 
  public int getSentenceId(int addr) {
        if (featOkTst && casFeat_sentenceId == null)
      jcas.throwFeatMissing("sentenceId", "de.unihd.dbs.uima.types.heideltime.Sentence");
    return ll_cas.ll_getIntValue(addr, casFeatCode_sentenceId);
  }
  /** @generated */    
  public void setSentenceId(int addr, int v) {
        if (featOkTst && casFeat_sentenceId == null)
      jcas.throwFeatMissing("sentenceId", "de.unihd.dbs.uima.types.heideltime.Sentence");
    ll_cas.ll_setIntValue(addr, casFeatCode_sentenceId, v);}
    
  



  /** initialize variables to correspond with Cas Type and Features
	* @generated */
  public Sentence_Type(JCas jcas, Type casType) {
    super(jcas, casType);
    casImpl.getFSClassRegistry().addGeneratorForType((TypeImpl)this.casType, getFSGenerator());

 
    casFeat_filename = jcas.getRequiredFeatureDE(casType, "filename", "uima.cas.String", featOkTst);
    casFeatCode_filename  = (null == casFeat_filename) ? JCas.INVALID_FEATURE_CODE : ((FeatureImpl)casFeat_filename).getCode();

 
    casFeat_sentenceId = jcas.getRequiredFeatureDE(casType, "sentenceId", "uima.cas.Integer", featOkTst);
    casFeatCode_sentenceId  = (null == casFeat_sentenceId) ? JCas.INVALID_FEATURE_CODE : ((FeatureImpl)casFeat_sentenceId).getCode();

  }
}



    