
all: truecasing.fast.caseless.qn.ser.gz truecasing.fast.qn.ser.gz truecasing.full.qn.ser.gz
.PHONY: all

truecasing.fast.caseless.qn.ser.gz:
	@echo Training $@
	java -mx400g edu.stanford.nlp.ie.crf.CRFClassifier -prop truecasing.fast.caseless.prop > truecasing.fast.caseless.out 2>&1

truecasing.fast.qn.ser.gz:
	@echo Training $@
	java -mx400g edu.stanford.nlp.ie.crf.CRFClassifier -prop truecasing.fast.prop > truecasing.fast.out 2>&1

truecasing.full.qn.ser.gz:
	@echo Training $@
	java -mx400g edu.stanford.nlp.ie.crf.CRFClassifier -prop truecasing.full.prop > truecasing.full.out 2>&1

