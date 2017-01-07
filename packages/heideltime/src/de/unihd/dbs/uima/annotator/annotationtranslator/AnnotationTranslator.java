/*
 * AnnotationTranslator.java
 * 
 * Copyright (c) 2011, Database Research Group, Institute of Computer Science, University of Heidelberg. 
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the GNU General Public License.
 * 
 * author: Jannik Strötgen
 * email:  stroetgen@uni-hd.de
 * 
 * Annotation Translator translates annotations of one type system into another.
 * For details, see http://dbs.ifi.uni-heidelberg.de/heideltime
 */

package de.unihd.dbs.uima.annotator.annotationtranslator;


import java.util.HashMap;
import java.util.HashSet;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.cas.FSIterator;

import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

/**
 * Add an additional annotation type for an existing annotation
 * @author jannik
 *
 */
public class AnnotationTranslator extends JCasAnnotator_ImplBase {
	@SuppressWarnings("unused")
	private String toolname = "de.unihd.dbs.uima.annotator.annotationtranslator";
	
		public static final String PARAM_DKPRO_TO_HEIDELTIME = "DkproToHeideltime";
		public static final String PARAM_HEIDELTIME_TO_DKPRO = "HeideltimeToDkpro";
		public static final String PARAM_IMPROVE_SENTENCE_DE = "ImproveGermanSentences";
		
		public Boolean dkproToHeidel = true;
		public Boolean heidelToDpkro = true;
		public Boolean improveSentDe = true;
	
		public HashSet<String> hsSentenceBeginnings;
	
	/**
	 * @see AnalysisComponent#initialize(UimaContext)
	 */
	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);

		heidelToDpkro = (Boolean) aContext.getConfigParameterValue(PARAM_HEIDELTIME_TO_DKPRO);
		dkproToHeidel = (Boolean) aContext.getConfigParameterValue(PARAM_DKPRO_TO_HEIDELTIME);
		improveSentDe = (Boolean) aContext.getConfigParameterValue(PARAM_IMPROVE_SENTENCE_DE);
		
		hsSentenceBeginnings = new HashSet<String>();
		hsSentenceBeginnings.add("Januar");
		hsSentenceBeginnings.add("Februar");
		hsSentenceBeginnings.add("März");
		hsSentenceBeginnings.add("April");
		hsSentenceBeginnings.add("Mai");
		hsSentenceBeginnings.add("Juni");
		hsSentenceBeginnings.add("Juli");
		hsSentenceBeginnings.add("August");
		hsSentenceBeginnings.add("September");
		hsSentenceBeginnings.add("Oktober");
		hsSentenceBeginnings.add("November");
		hsSentenceBeginnings.add("Dezember");
		hsSentenceBeginnings.add("Jahrhundert");
		hsSentenceBeginnings.add("Jahr");
		hsSentenceBeginnings.add("Monat");
		hsSentenceBeginnings.add("Woche");
	}

	/**
	 * @see JCasAnnotator_ImplBase#process(JCas)
	 */
	public void process(JCas jcas) {
		
		if (heidelToDpkro){
			// translate the HeidelTime sentences and tokens to DKPro Tagset
			FSIndex annoSentHeidel    = jcas.getAnnotationIndex(de.unihd.dbs.uima.types.heideltime.Sentence.type);
			FSIterator iterSentHeidel = annoSentHeidel.iterator();
			FSIndex annoTokHeidel     = jcas.getAnnotationIndex(de.unihd.dbs.uima.types.heideltime.Token.type);
			FSIterator iterTokHeidel  = annoTokHeidel.iterator();
			
			// create DKPro sentences from HeidelTime sentences
			HashSet<de.unihd.dbs.uima.types.heideltime.Sentence> hsRemoveHeidelSent = new HashSet<de.unihd.dbs.uima.types.heideltime.Sentence>();
			while (iterSentHeidel.hasNext()){
				de.unihd.dbs.uima.types.heideltime.Sentence s1 = (de.unihd.dbs.uima.types.heideltime.Sentence) iterSentHeidel.next();
				de.tudarmstadt.ukp.dkpro.core.type.Sentence s2 = new de.tudarmstadt.ukp.dkpro.core.type.Sentence(jcas);
				s2.setBegin(s1.getBegin());
				s2.setEnd(s1.getEnd());
				s2.addToIndexes();
				hsRemoveHeidelSent.add(s1);
			}
			
			// create DKPro tokens from HeidelTime tokens
			HashSet<de.unihd.dbs.uima.types.heideltime.Token> hsRemoveHeidelTok = new HashSet<de.unihd.dbs.uima.types.heideltime.Token>();
			while (iterTokHeidel.hasNext()){
				de.unihd.dbs.uima.types.heideltime.Token t1 = (de.unihd.dbs.uima.types.heideltime.Token) iterTokHeidel.next();
				de.tudarmstadt.ukp.dkpro.core.type.Token t2 = new de.tudarmstadt.ukp.dkpro.core.type.Token(jcas);
				t2.setBegin(t1.getBegin());
				t2.setEnd(t1.getEnd());
				t2.addToIndexes();
				hsRemoveHeidelTok.add(t1);
			}
		}
		
		if (dkproToHeidel){
			// translate the DKPro sentences, tokens, and pos (with all kind of names) to HeidelTime
			FSIndex annoSentDkpro    = jcas.getAnnotationIndex(de.tudarmstadt.ukp.dkpro.core.type.Sentence.type);
			FSIterator iterSentDkpro = annoSentDkpro.iterator();
			
			// get all the HeidelTime sentences, token if they are already available
			FSIndex annoSentHeidel    = jcas.getAnnotationIndex(de.unihd.dbs.uima.types.heideltime.Sentence.type);
			FSIndex annoTokHeidel     = jcas.getAnnotationIndex(de.unihd.dbs.uima.types.heideltime.Token.type);
			FSIterator iterSentHeidel = annoSentHeidel.iterator();
			FSIterator iterTokHeidel  = annoTokHeidel.iterator();
			HashMap<String, de.unihd.dbs.uima.types.heideltime.Sentence> hmOldSent = new HashMap<String, de.unihd.dbs.uima.types.heideltime.Sentence>();
			HashMap<String, de.unihd.dbs.uima.types.heideltime.Token> hmOldTok     = new HashMap<String, de.unihd.dbs.uima.types.heideltime.Token>();
			while (iterSentHeidel.hasNext()){
				de.unihd.dbs.uima.types.heideltime.Sentence s = (de.unihd.dbs.uima.types.heideltime.Sentence) iterSentHeidel.next();
				hmOldSent.put(s.getBegin()+"-"+s.getEnd(), s);
			}
			while (iterTokHeidel.hasNext()){
				de.unihd.dbs.uima.types.heideltime.Token t = (de.unihd.dbs.uima.types.heideltime.Token) iterTokHeidel.next();
				hmOldTok.put(t.getBegin()+"-"+t.getEnd(), t);
			}
			
			// create HeidelTime sentences from DKPro sentences
			HashSet<de.tudarmstadt.ukp.dkpro.core.type.Sentence> hsRemoveDkproSent = new HashSet<de.tudarmstadt.ukp.dkpro.core.type.Sentence>();
			HashSet<de.tudarmstadt.ukp.dkpro.core.type.Token> hsRemoveDkproTok = new HashSet<de.tudarmstadt.ukp.dkpro.core.type.Token>();
			while (iterSentDkpro.hasNext()){
				de.tudarmstadt.ukp.dkpro.core.type.Sentence s1 = (de.tudarmstadt.ukp.dkpro.core.type.Sentence) iterSentDkpro.next();
				de.unihd.dbs.uima.types.heideltime.Sentence s2 = new de.unihd.dbs.uima.types.heideltime.Sentence(jcas);
				if (hmOldSent.containsKey(s1.getBegin()+"-"+s1.getEnd())){
					s2 = hmOldSent.get(s1.getBegin()+"-"+s1.getEnd());
					s2.removeFromIndexes();
				}

				s2.setBegin(s1.getBegin());
				s2.setEnd(s1.getEnd());
				s2.addToIndexes();
				hsRemoveDkproSent.add(s1);
			
				FSIterator iterTokDkpro = jcas.getAnnotationIndex(de.tudarmstadt.ukp.dkpro.core.type.Token.type).subiterator(s1);
				// create HeidelTime tokens (with POS information) from DKPro tokens and DKPro Pos information
				
				while (iterTokDkpro.hasNext()){
					de.tudarmstadt.ukp.dkpro.core.type.Token t1 = (de.tudarmstadt.ukp.dkpro.core.type.Token) iterTokDkpro.next();
					de.unihd.dbs.uima.types.heideltime.Token t2 = new de.unihd.dbs.uima.types.heideltime.Token(jcas);
					if (hmOldTok.containsKey(t1.getBegin()+"-"+t1.getEnd())){
						t2 = hmOldTok.get(t1.getBegin()+"-"+t1.getEnd());
						t2.removeFromIndexes();
					}
						
					t2.setBegin(t1.getBegin());
					t2.setEnd(t1.getEnd());
					// ADD POS TAGS!
					FSIterator iterPosAdj  = jcas.getAnnotationIndex(de.tudarmstadt.ukp.dkpro.core.type.pos.ADJ.type).subiterator(s1);
					FSIterator iterPosAdv  = jcas.getAnnotationIndex(de.tudarmstadt.ukp.dkpro.core.type.pos.ADV.type).subiterator(s1);
					FSIterator iterPosArt  = jcas.getAnnotationIndex(de.tudarmstadt.ukp.dkpro.core.type.pos.ART.type).subiterator(s1);
					FSIterator iterPosCard = jcas.getAnnotationIndex(de.tudarmstadt.ukp.dkpro.core.type.pos.CARD.type).subiterator(s1);
					FSIterator iterPosConj = jcas.getAnnotationIndex(de.tudarmstadt.ukp.dkpro.core.type.pos.CONJ.type).subiterator(s1);
					FSIterator iterPosNn   = jcas.getAnnotationIndex(de.tudarmstadt.ukp.dkpro.core.type.pos.NN.type).subiterator(s1);
					FSIterator iterPosNp   = jcas.getAnnotationIndex(de.tudarmstadt.ukp.dkpro.core.type.pos.NP.type).subiterator(s1);
					FSIterator iterPosO    = jcas.getAnnotationIndex(de.tudarmstadt.ukp.dkpro.core.type.pos.O.type).subiterator(s1);
					FSIterator iterPosPp   = jcas.getAnnotationIndex(de.tudarmstadt.ukp.dkpro.core.type.pos.PP.type).subiterator(s1);
					FSIterator iterPosPr   = jcas.getAnnotationIndex(de.tudarmstadt.ukp.dkpro.core.type.pos.PR.type).subiterator(s1);
					FSIterator iterPosPunc = jcas.getAnnotationIndex(de.tudarmstadt.ukp.dkpro.core.type.pos.PUNC.type).subiterator(s1);
					FSIterator iterPosV    = jcas.getAnnotationIndex(de.tudarmstadt.ukp.dkpro.core.type.pos.V.type).subiterator(s1);
					
					while (iterPosAdj.hasNext()){
						de.tudarmstadt.ukp.dkpro.core.type.pos.ADJ adj = (de.tudarmstadt.ukp.dkpro.core.type.pos.ADJ) iterPosAdj.next();
						if ((adj.getBegin() == t2.getBegin()) && (adj.getEnd() == t2.getEnd())){
							t2.setPos(adj.getValue());
						}
					}
					while (iterPosAdv.hasNext()){
						de.tudarmstadt.ukp.dkpro.core.type.pos.ADV adv = (de.tudarmstadt.ukp.dkpro.core.type.pos.ADV) iterPosAdv.next();
						if ((adv.getBegin() == t2.getBegin()) && (adv.getEnd() == t2.getEnd())){
							t2.setPos(adv.getValue());
						}
					}
					while (iterPosArt.hasNext()){
						de.tudarmstadt.ukp.dkpro.core.type.pos.ART art = (de.tudarmstadt.ukp.dkpro.core.type.pos.ART) iterPosArt.next();
						if ((art.getBegin() == t2.getBegin()) && (art.getEnd() == t2.getEnd())){
							t2.setPos(art.getValue());
						}
					}
					while (iterPosCard.hasNext()){
						de.tudarmstadt.ukp.dkpro.core.type.pos.CARD card = (de.tudarmstadt.ukp.dkpro.core.type.pos.CARD) iterPosCard.next();
						if ((card.getBegin() == t2.getBegin()) && (card.getEnd() == t2.getEnd())){
							t2.setPos(card.getValue());
						}
					}
					while (iterPosConj.hasNext()){
						de.tudarmstadt.ukp.dkpro.core.type.pos.CONJ conj = (de.tudarmstadt.ukp.dkpro.core.type.pos.CONJ) iterPosConj.next();
						if ((conj.getBegin() == t2.getBegin()) && (conj.getEnd() == t2.getEnd())){
							t2.setPos(conj.getValue());
						}
					}
					while (iterPosNn.hasNext()){
						de.tudarmstadt.ukp.dkpro.core.type.pos.NN nn = (de.tudarmstadt.ukp.dkpro.core.type.pos.NN) iterPosNn.next();
						if ((nn.getBegin() == t2.getBegin()) && (nn.getEnd() == t2.getEnd())){
							t2.setPos(nn.getValue());
						}
					}
					while (iterPosNp.hasNext()){
						de.tudarmstadt.ukp.dkpro.core.type.pos.NP np = (de.tudarmstadt.ukp.dkpro.core.type.pos.NP) iterPosNp.next();
						if ((np.getBegin() == t2.getBegin()) && (np.getEnd() == t2.getEnd())){
							t2.setPos(np.getValue());
						}
					}
					while (iterPosO.hasNext()){
						de.tudarmstadt.ukp.dkpro.core.type.pos.O o = (de.tudarmstadt.ukp.dkpro.core.type.pos.O) iterPosO.next();
						if ((o.getBegin() == t2.getBegin()) && (o.getEnd() == t2.getEnd())){
							t2.setPos(o.getValue());
						}
					}
					while (iterPosPp.hasNext()){
						de.tudarmstadt.ukp.dkpro.core.type.pos.PP pp = (de.tudarmstadt.ukp.dkpro.core.type.pos.PP) iterPosPp.next();
						if ((pp.getBegin() == t2.getBegin()) && (pp.getEnd() == t2.getEnd())){
							t2.setPos(pp.getValue());
						}
					}
					while (iterPosPr.hasNext()){
						de.tudarmstadt.ukp.dkpro.core.type.pos.PR pr = (de.tudarmstadt.ukp.dkpro.core.type.pos.PR) iterPosPr.next();
						if ((pr.getBegin() == t2.getBegin()) && (pr.getEnd() == t2.getEnd())){
							t2.setPos(pr.getValue());
						}
					}
					while (iterPosPunc.hasNext()){
						de.tudarmstadt.ukp.dkpro.core.type.pos.PUNC punc = (de.tudarmstadt.ukp.dkpro.core.type.pos.PUNC) iterPosPunc.next();
						if ((punc.getBegin() == t2.getBegin()) && (punc.getEnd() == t2.getEnd())){
							t2.setPos(punc.getValue());
						}
					}
					while (iterPosV.hasNext()){
						de.tudarmstadt.ukp.dkpro.core.type.pos.V v = (de.tudarmstadt.ukp.dkpro.core.type.pos.V) iterPosV.next();
						if ((v.getBegin() == t2.getBegin()) && (v.getEnd() == t2.getEnd())){
							t2.setPos(v.getValue());
						}
					}
					t2.addToIndexes();
					hsRemoveDkproTok.add(t1);
				}
			}
			// remove DKPro sentences finally
			for (de.tudarmstadt.ukp.dkpro.core.type.Sentence s : hsRemoveDkproSent) {
				s.removeFromIndexes();
			}
			// remove DKPro tokens, finally
			for (de.tudarmstadt.ukp.dkpro.core.type.Token t1 : hsRemoveDkproTok) {
				t1.removeFromIndexes();
			}	
		}
		
		// IMPROVE SENTENCE BOUNDARIES (GERMAN SENTENCE SPLITTER)
		HashSet<de.unihd.dbs.uima.types.heideltime.Sentence> hsRemoveAnnotations = new HashSet<de.unihd.dbs.uima.types.heideltime.Sentence>();
		HashSet<de.unihd.dbs.uima.types.heideltime.Sentence> hsAddAnnotations    = new HashSet<de.unihd.dbs.uima.types.heideltime.Sentence>();
		if (improveSentDe){
			Boolean changes = true;
			while (changes){
				changes = false;
				FSIndex annoHeidelSentences = jcas.getAnnotationIndex(de.unihd.dbs.uima.types.heideltime.Sentence.type);
				FSIterator iterHeidelSent   = annoHeidelSentences.iterator();
				while (iterHeidelSent.hasNext()){
					de.unihd.dbs.uima.types.heideltime.Sentence s1 = (de.unihd.dbs.uima.types.heideltime.Sentence) iterHeidelSent.next();
					int substringOffset = java.lang.Math.max(s1.getCoveredText().length()-4,1);
					if (s1.getCoveredText().substring(substringOffset).matches(".*[\\d]+\\.[\\s\\n]*$")){
//						System.err.println("Checking sentence 1: successful: "+s1.getCoveredText());
						if (iterHeidelSent.hasNext()){
							de.unihd.dbs.uima.types.heideltime.Sentence s2 = (de.unihd.dbs.uima.types.heideltime.Sentence) iterHeidelSent.next();
							iterHeidelSent.moveToPrevious();
//							System.err.println("Checking sentence 2: "+s2.getCoveredText());
							for (String beg : hsSentenceBeginnings){
								if (s2.getCoveredText().startsWith(beg)){
//									System.err.println("Checking sentence 2: successful");
									de.unihd.dbs.uima.types.heideltime.Sentence s3 = new de.unihd.dbs.uima.types.heideltime.Sentence(jcas);
									s3.setBegin(s1.getBegin());
									s3.setEnd(s2.getEnd());
									hsAddAnnotations.add(s3);
									hsRemoveAnnotations.add(s1);
									hsRemoveAnnotations.add(s2);
									changes = true;
									break;
								}
							}
						}
					}
				}
				for (de.unihd.dbs.uima.types.heideltime.Sentence s : hsRemoveAnnotations){
					s.removeFromIndexes(jcas);
				}
				hsRemoveAnnotations.clear();
				for (de.unihd.dbs.uima.types.heideltime.Sentence s : hsAddAnnotations){
					s.addToIndexes(jcas);
				}
				hsAddAnnotations.clear();
			}
		}
	}
}