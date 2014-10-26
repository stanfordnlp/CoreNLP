ENGLISH_EMBEDDINGS=/u/nlp/data/depparser/nn/data/embeddings/en-cw.txt
CHINESE_EMBEDDINGS=/u/nlp/data/depparser/nn/data/embeddings/zh-word2vec.txt

PTB_STANFORD_TRAIN=/u/nlp/data/dependency_treebanks/PTB_Stanford/train.conll
PTB_STANFORD_DEV=/u/nlp/data/dependency_treebanks/PTB_Stanford/dev.conll
PTB_STANFORD_TEST=/u/nlp/data/dependency_treebanks/PTB_Stanford/test.conll
PTB_STANFORD_MODEL=/u/nlp/data/depparser/nn/PTB_Stanford_params.txt

PTB_CONLL_TRAIN=/u/nlp/data/dependency_treebanks/CTB_CoNLL/train.conll
PTB_CONLL_DEV=/u/nlp/data/dependency_treebanks/PTB_CoNLL/dev.conll
PTB_CONLL_TEST=/u/nlp/data/dependency_treebanks/PTB_CoNLL/test.conll
PTB_CONLL_MODEL=/u/nlp/data/depparser/nn/PTB_CoNLL_params.txt

CTB_CONLL_TRAIN=/u/nlp/data/dependency_treebanks/CTB/train.gold.conll
CTB_CONLL_DEV=/u/nlp/data/dependency_treebanks/CTB/dev.gold.conll
CTB_CONLL_TEST=/u/nlp/data/dependency_treebanks/CTB/test.gold.conll
CTB_CONLL_MODEL=/u/nlp/data/depparser/nn/CTB_CoNLL_params.txt

Train_PTB_Stanford:
	java edu.stanford.nlp.parser.nndep.DependencyParser -trainFile $(PTB_STANFORD_TRAIN) -devFile $(PTB_STANFORD_DEV) -embedFile $(ENGLISH_EMBEDDINGS) -model PTB_stanford.txt > $@.log 2>&1

Test_PTB_Stanford:
	java edu.stanford.nlp.parser.nndep.DependencyParser -testFile $(PTB_STANFORD_TEST) -model $(PTB_STANFORD_MODEL) -outFile PTB_Stanford_test.out

Test_PTB_Stanford_dev:
	java edu.stanford.nlp.parser.nndep.DependencyParser -testFile $(PTB_STANFORD_DEV) -model $(PTB_STANFORD_MODEL) -outFile PTB_Stanford_dev.out

Train_PTB_CoNLL:
	java edu.stanford.nlp.parser.nndep.DependencyParser -trainFile $(PTB_CONLL_TRAIN) -devFile $(PTB_CONLL_DEV) -embedFile $(ENGLISH_EMBEDDINGS) -model PTB_CoNLL.txt > $@.log 2>&1

Test_PTB_CoNLL:
	java edu.stanford.nlp.parser.nndep.DependencyParser -testFile $(PTB_CONLL_TEST) -model $(PTB_CONLL_MODEL) -outFile PTB_CoNLL_test.out

Test_PTB_CoNLL_dev:
	java edu.stanford.nlp.parser.nndep.DependencyParser -testFile $(PTB_CONLL_DEV) -model $(PTB_CONLL_MODEL) -outFile PTB_CoNLL_dev.out

Train_CTB_CoNLL:
	java edu.stanford.nlp.parser.nndep.DependencyParser -trainFile $(CTB_CONLL_TRAIN) -language Chinese -devFile $(CTB_CONLL_DEV) -embedFile $(CHINESE_EMBEDDINGS) -model CTB_CoNLL.txt > $@.log 2>&1

Test_CTB_CoNLL:
	java edu.stanford.nlp.parser.nndep.DependencyParser -testFile $(CTB_CONLL_TEST) -language Chinese -model $(CTB_CONLL_MODEL) -outFile CTB_CoNLL_test.out

Test_CTB_CoNLL_dev:
	java edu.stanford.nlp.parser.nndep.DependencyParser -testFile $(CTB_CONLL_DEV) -language Chinese -model $(CTB_CONLL_MODEL) -outFile CTB_CoNLL_dev.out