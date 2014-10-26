ENGLISH_EMBEDDINGS=/u/nlp/data/depparser/nn/data/embeddings/en-cw.txt

PTB_STANFORD_TRAIN=/u/nlp/data/dependency_treebanks/PTB_Stanford/train.conll
PTB_STANFORD_DEV=/u/nlp/data/dependency_treebanks/PTB_Stanford/dev.conll
PTB_STANFORD_TEST=/u/nlp/data/dependency_treebanks/PTB_Stanford/test.conll

PTB_Stanford:
	@echo Training $@
	java edu.stanford.nlp.parser.nndep.DependencyParser -trainFile $(PTB_STANFORD_TRAIN) -devFile $(PTB_STANFORD_DEV) -testFile $(PTB_STANFORD_TEST) -embedFile $(ENGLISH_EMBEDDINGS) -model $@.txt -outFile $@.out > $@.log 2>&1
