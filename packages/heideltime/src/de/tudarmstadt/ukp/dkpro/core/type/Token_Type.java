
/* First created by JCasGen Mon May 02 14:11:32 CEST 2011 */
package de.tudarmstadt.ukp.dkpro.core.type;

import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.JCasRegistry;
import org.apache.uima.cas.impl.CASImpl;
import org.apache.uima.cas.impl.FSGenerator;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.impl.TypeImpl;
import org.apache.uima.cas.Type;
import org.apache.uima.jcas.tcas.Annotation_Type;

/** 
 * Updated by JCasGen Mon May 02 14:13:32 CEST 2011
 * @generated */
public class Token_Type extends Annotation_Type {
  /** @generated */
  protected FSGenerator getFSGenerator() {return fsGenerator;}
  /** @generated */
  private final FSGenerator fsGenerator = 
    new FSGenerator() {
      public FeatureStructure createFS(int addr, CASImpl cas) {
  			 if (Token_Type.this.useExistingInstance) {
  			   // Return eq fs instance if already created
  		     FeatureStructure fs = Token_Type.this.jcas.getJfsFromCaddr(addr);
  		     if (null == fs) {
  		       fs = new Token(addr, Token_Type.this);
  			   Token_Type.this.jcas.putJfsFromCaddr(addr, fs);
  			   return fs;
  		     }
  		     return fs;
        } else return new Token(addr, Token_Type.this);
  	  }
    };
  /** @generated */
  public final static int typeIndexID = Token.typeIndexID;
  /** @generated 
     @modifiable */
  public final static boolean featOkTst = JCasRegistry.getFeatOkTst("de.tudarmstadt.ukp.dkpro.core.type.Token");



  /** initialize variables to correspond with Cas Type and Features
	* @generated */
  public Token_Type(JCas jcas, Type casType) {
    super(jcas, casType);
    casImpl.getFSClassRegistry().addGeneratorForType((TypeImpl)this.casType, getFSGenerator());

  }
}



    