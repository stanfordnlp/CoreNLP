ENGLISH_EMBEDDINGS=/u/nlp/data/depparser/nn/data/embeddings/en-cw.txt
CHINESE_EMBEDDINGS=/u/nlp/data/depparser/nn/data/embeddings/zh-word2vec.txt

PTB_STANFORD_TRAIN=/u/nlp/data/dependency_treebanks/PTB_Stanford/train.conll
PTB_STANFORD_DEV=/u/nlp/data/dependency_treebanks/PTB_Stanford/dev.conll
PTB_STANFORD_TEST=/u/nlp/data/dependency_treebanks/PTB_Stanford/test.conll
PTB_STANFORD_MODEL=/u/nlp/data/depparser/nn/PTB_Stanford_params.txt

PTB_CONLL_TRAIN=/u/nlp/data/dependency_treebanks/PTB_CoNLL/train.conll
PTB_CONLL_DEV=/u/nlp/data/dependency_treebanks/PTB_CoNLL/dev.conll
PTB_CONLL_TEST=/u/nlp/data/dependency_treebanks/PTB_CoNLL/test.conll
PTB_CONLL_MODEL=u/nlp/data/depparser/nn/PTB_CoNLL_params.txt

CTB_CONLL_TRAIN=/u/nlp/data/dependency_treebanks/CTB/train.conll
CTB_CONLL_DEV=/u/nlp/data/dependency_treebanks/CTB/dev.conll
CTB_CONLL_TEST=/u/nlp/data/dependency_treebanks/CTB/test.conll
CTB_CONLL_MODEL=u/nlp/data/depparser/nn/CTB_params.txt

PTB_Stanford:
	@echo Training $@
	java edu.stanford.nlp.parser.nndep.DependencyParser -trainFile $(PTB_STANFORD_TRAIN) -devFile $(PTB_STANFORD_DEV) -embedFile $(ENGLISH_EMBEDDINGS) -model $@.txt > $@.log 2>&1
	@echo Testing $@
	java edu.stanford.nlp.parser.nndep.DependencyParser -testFile $(PTB_STANFORD_DEV) -model $(PTB_STANFORD_MODEL) -outFile $@_dev.out > $@_dev.log 2>&1
	java edu.stanford.nlp.parser.nndep.DependencyParser -testFile $(PTB_STANFORD_TEST) -model $(PTB_STANFORD_MODEL) -outFile $@_test.out > $@_test.log 2>&1