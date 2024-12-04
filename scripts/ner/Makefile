
all: chinese french genia german hungarian italian all.3class nowiki.3class conll.4class muc.7class spanish

chinese: chinese.misc.nodistsim.ser.gz chinese.misc.distsim.ser.gz

chinese.misc.nodistsim.ser.gz:
	java -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop chinese.misc.nodistsim.prop > chinese.misc.nodistsim.out 2>&1

chinese.misc.distsim.ser.gz:
	java -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop chinese.misc.distsim.prop > chinese.misc.distsim.out 2>&1

french: french-wikiner-4class.crf.ser.gz

french-wikiner-4class.crf.ser.gz:
	java -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop french.wikiner.nodistsim.4class.prop > french.wikiner.nodistsim.out 2>&1

genia: genia-nlpba-2004.crf.gz

genia-nlpba-2004.crf.gz:
	java -mx10g edu.stanford.nlp.ie.crf.CRFClassifier -prop genia-nlpba-2004.prop > genia-nlpba-2004.out 2>&1

# We are No longer building/distributing deWAC model. The data for distributional similarity classes wasn't clean and can't be recovered.
german: german.distsim.crf.ser.gz

german.distsim.crf.ser.gz:
	java -mx10g edu.stanford.nlp.ie.crf.CRFClassifier -prop german.distsim.prop -serializeTo $@ \
		> $(addsuffix .out, $(basename $(basename $(basename $@)))) 2>&1

hungarian: hungarian.crf.ser.gz

hungarian.crf.ser.gz:
	java -mx20g edu.stanford.nlp.ie.crf.CRFClassifier -prop hungarian.prop -serializeTo $@ > hungarian.out 2>&1


italian: italian.crf.ser.gz

italian.crf.ser.gz:
	java -mx20g edu.stanford.nlp.ie.crf.CRFClassifier -prop italian.prop -serializeTo $@ > italian.out 2>&1


# currently we exclude enp_DE.sbb.io, as the data has too many issues, but we could work to include it....
# ,/u/nlp/data/german/ner/2016/Europeana-Newspapers-data/ner-corpora/enp_DE.sbb.bio/enp_DE.sbb.io

# We are No longer building/distributing deWAC model. The data for distributional similarity classes wasn't clean and can't be recovered.
# german.dewac_175m_600.crf.ser.gz:
#	java -mx10g edu.stanford.nlp.ie.crf.CRFClassifier -prop german.dewac_175m_600.prop > german.dewac_175m_600.out 2>&1


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
