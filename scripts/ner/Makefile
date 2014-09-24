
all: chinese genia german all.3class nowiki.3class conll.4class muc.7class spanish

chinese: chinese.misc.nodistsim.ser.gz chinese.misc.distsim.ser.gz

chinese.misc.nodistsim.ser.gz:
	java -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop chinese.misc.nodistsim.prop > chinese.misc.nodistsim.out 2>&1

chinese.misc.distsim.ser.gz:
	java -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop chinese.misc.distsim.prop > chinese.misc.distsim.out 2>&1

genia: genia-nlpba-2004.crf.gz

genia-nlpba-2004.crf.gz:
	java -mx10g edu.stanford.nlp.ie.crf.CRFClassifier -prop genia-nlpba-2004.prop > genia-nlpba-2004.out 2>&1

german: german.hgc_175m_600.crf.ser.gz german.dewac_175m_600.crf.ser.gz

german.hgc_175m_600.crf.ser.gz: 
	java -mx10g edu.stanford.nlp.ie.crf.CRFClassifier -prop german.hgc_175m_600.prop > german.hgc_175m_600.out 2>&1

german.dewac_175m_600.crf.ser.gz: 
	java -mx10g edu.stanford.nlp.ie.crf.CRFClassifier -prop german.dewac_175m_600.prop > german.dewac_175m_600.out 2>&1


all.3class:  english.all.3class.nodistsim.crf.ser.gz  english.all.3class.caseless.distsim.crf.ser.gz  english.all.3class.distsim.crf.ser.gz

english.all.3class.nodistsim.crf.ser.gz:
	java -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop english.all.3class.nodistsim.prop > english.all.3class.nodistsim.out 2>&1

english.all.3class.caseless.distsim.crf.ser.gz:
	java -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop english.all.3class.caseless.distsim.prop > english.all.3class.caseless.distsim.out 2>&1

english.all.3class.distsim.crf.ser.gz:
	java -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop english.all.3class.distsim.prop > english.all.3class.distsim.out 2>&1

nowiki.3class:  english.nowiki.3class.nodistsim.crf.ser.gz  english.nowiki.3class.caseless.distsim.crf.ser.gz  english.nowiki.3class.distsim.crf.ser.gz

english.nowiki.3class.nodistsim.crf.ser.gz:
	java -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop english.nowiki.3class.nodistsim.prop > english.nowiki.3class.nodistsim.out 2>&1

english.nowiki.3class.caseless.distsim.crf.ser.gz:
	java -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop english.nowiki.3class.caseless.distsim.prop > english.nowiki.3class.caseless.distsim.out 2>&1

english.nowiki.3class.distsim.crf.ser.gz:
	java -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop english.nowiki.3class.distsim.prop > english.nowiki.3class.distsim.out 2>&1


conll.4class:  english.conll.4class.nodistsim.crf.ser.gz  english.conll.4class.caseless.distsim.crf.ser.gz  english.conll.4class.distsim.crf.ser.gz

english.conll.4class.nodistsim.crf.ser.gz:
	java -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop english.conll.4class.nodistsim.prop > english.conll.4class.nodistsim.out 2>&1

english.conll.4class.caseless.distsim.crf.ser.gz:
	java -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop english.conll.4class.caseless.distsim.prop > english.conll.4class.caseless.distsim.out 2>&1

english.conll.4class.distsim.crf.ser.gz: 
	java -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop english.conll.4class.distsim.prop > english.conll.4class.distsim.out 2>&1


muc.7class:  english.muc.7class.nodistsim.crf.ser.gz  english.muc.7class.caseless.distsim.crf.ser.gz  english.muc.7class.distsim.crf.ser.gz

english.muc.7class.nodistsim.crf.ser.gz: 
	java -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop english.muc.7class.nodistsim.prop > english.muc.7class.nodistsim.out 2>&1

english.muc.7class.caseless.distsim.crf.ser.gz: 
	java -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop english.muc.7class.caseless.distsim.prop > english.muc.7class.caseless.distsim.out 2>&1

english.muc.7class.distsim.crf.ser.gz: 
	java -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop english.muc.7class.distsim.prop > english.muc.7class.distsim.out 2>&1

spanish: spanish.ancora.crf.ser.gz spanish.ancora2.crf.ser.gz spanish.ancora.distsim.s512.crf.ser.gz

spanish.ancora.crf.ser.gz:
	java -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop spanish.ancora.prop > spanish.ancora.out 2>&1

spanish.ancora2.crf.ser.gz:
	java -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop spanish.ancora2.prop > spanish.ancora2.out 2>&1

spanish.ancora.distsim.s512.crf.ser.gz:
	java -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop spanish.ancora.distsim.s512.prop > spanish.ancora.distsim.s512.out 2>&1
