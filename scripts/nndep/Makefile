ENGLISH_EMBEDDINGS=/u/nlp/data/depparser/nn/data/embeddings/en-cw.txt
CHINESE_EMBEDDINGS=/u/nlp/data/depparser/nn/data/embeddings/zh-word2vec.txt

PTB_STANFORD_TRAIN=/u/nlp/data/dependency_treebanks/PTB/Stanford_3_3_0/train.conll
PTB_STANFORD_DEV=/u/nlp/data/dependency_treebanks/PTB/Stanford_3_3_0/dev.conll
PTB_STANFORD_TEST=/u/nlp/data/dependency_treebanks/PTB/Stanford_3_3_0/test.conll

PTB_CONLL_TRAIN=/u/nlp/data/dependency_treebanks/PTB/CoNLL/train.conll
PTB_CONLL_DEV=/u/nlp/data/dependency_treebanks/PTB/CoNLL/dev.conll
PTB_CONLL_TEST=/u/nlp/data/dependency_treebanks/PTB/CoNLL/test.conll

CTB_CONLL_TRAIN=/u/nlp/data/dependency_treebanks/CTB/train.gold.conll
CTB_CONLL_DEV=/u/nlp/data/dependency_treebanks/CTB/dev.gold.conll
CTB_CONLL_TEST=/u/nlp/data/dependency_treebanks/CTB/test.gold.conll

PTB_Stanford:
	java edu.stanford.nlp.parser.nndep.DependencyParser -props nndep.properties -trainFile $(PTB_STANFORD_TRAIN) -devFile $(PTB_STANFORD_DEV) -embedFile $(ENGLISH_EMBEDDINGS) -model $@.txt.gz > $@.log 2>&1
	@echo "\nBeginning evaluation (dev set).." >> $@.log
	java edu.stanford.nlp.parser.nndep.DependencyParser -testFile $(PTB_STANFORD_DEV) -model $@.txt.gz -outFile $@.out.dev >> $@.log 2>&1
	@echo "\nBeginning evaluation (test set).." >> $@.log
	java edu.stanford.nlp.parser.nndep.DependencyParser -testFile $(PTB_STANFORD_TEST) -model $@.txt.gz -outFile $@.out.test >> $@.log 2>&1

PTB_CoNLL:
	java edu.stanford.nlp.parser.nndep.DependencyParser -props nndep.properties -trainFile $(PTB_CONLL_TRAIN) -devFile $(PTB_CONLL_DEV) -embedFile $(ENGLISH_EMBEDDINGS) -model $@.txt.gz >> $@.log 2>&1
	java edu.stanford.nlp.parser.nndep.DependencyParser -testFile $(PTB_CONLL_DEV) -model $@.txt.gz -outFile $@.out.dev >> $@.log 2>&1
	java edu.stanford.nlp.parser.nndep.DependencyParser -testFile $(PTB_CONLL_TEST) -model $@.txt.gz -outFile $@.out.test >> $@.log 2>&1

CTB_CoNLL:
	java edu.stanford.nlp.parser.nndep.DependencyParser -props nndep.properties -trainFile $(CTB_CONLL_TRAIN) -language Chinese -devFile $(CTB_CONLL_DEV) -embedFile $(CHINESE_EMBEDDINGS) -model $@.txt.gz >> $@.log 2>&1
	java edu.stanford.nlp.parser.nndep.DependencyParser -testFile $(CTB_CONLL_DEV) -language Chinese -model $@.txt.gz -outFile $@.out.dev >> $@.log 2>&1
	java edu.stanford.nlp.parser.nndep.DependencyParser -testFile $(CTB_CONLL_TEST) -language Chinese -model $@.txt.gz -outFile $@.out.test >> $@.log 2>&1
