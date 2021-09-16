# TODO: is there some way to make all of the targets use the same command?

ARABIC_TEST = format=TREES,/u/nlp/data/lexparser/trees/Arabic/2-Unvoc-Test.utf8.txt

CHINESE_TEST = format=TREES,/u/nlp/data/chinese/ctb7/test.mrg

# Use dev set for regression testing
ENGLISH_TEST = /u/nlp/data/pos-tagger/english/test-wsj-19-21

FRENCH_TEST = format=TREES,/u/nlp/data/lexparser/trees/FrenchCC/FTB-Test.utf8.txt

GERMAN_TEST = format=TSV,wordColumn=1,tagColumn=3,comments=True,/u/nlp/software/CoreNLP-models/de/pos/german-ud/4.3.0/de_gsd-ud-test.conllu

HUNGARIAN_TEST = format=TSV,comments=true,wordColumn=1,tagColumn=3,/u/nlp/software/CoreNLP-models/hu/pos/4.3.0/hu_szeged-ud-test.conllu

ITALIAN_TEST = format=TSV,comments=true,wordColumn=1,tagColumn=3,/u/nlp/software/CoreNLP-models/it/pos/4.3.0/it_isdt-ud-test.conllu

SPANISH_TEST = format=TREES,/u/nlp/data/spanish/ancora/ancora.test,/u/nlp/data/spanish/ldc/ldc-NW.test,/u/nlp/data/spanish/ldc/ldc-DF.test

.SECONDEXPANSION:

all: arabic chinese english french german hungarian italian spanish testing wsj
.PHONY: all arabic chinese english french german hungarian italian spanish testing wsj

arabic: arabic.tagger  arabic-train.tagger

# we release an arabic model trained on everything, with a
# corresponding model on train only for testing purposes
arabic.tagger arabic-train.tagger: $$@.props
	@echo Training $@
	@echo Will test on $(ARABIC_TEST)
	java -mx6g edu.stanford.nlp.tagger.maxent.MaxentTagger -props $@.props > $@.out 2>&1
	java -mx6g edu.stanford.nlp.tagger.maxent.MaxentTagger -model $@ -testFile $(ARABIC_TEST) -verboseResults false >> $@.out 2>&1

chinese: chinese-distsim.tagger chinese-nodistsim.tagger

chinese-nodistsim.tagger chinese-distsim.tagger: $$@.props
	@echo Training $@
	@echo Will test on $(CHINESE_TEST)
	java -mx6g edu.stanford.nlp.tagger.maxent.MaxentTagger -props $@.props > $@.out 2>&1
	java -mx6g edu.stanford.nlp.tagger.maxent.MaxentTagger -model $@ -testFile $(CHINESE_TEST)  -verboseResults false >> $@.out 2>&1

english: english-bidirectional-distsim.tagger english-caseless-left3words-distsim.tagger english-left3words-distsim.tagger

english-bidirectional-distsim.tagger english-caseless-left3words-distsim.tagger english-left3words-distsim.tagger: $$@.props
	@echo Training $@
	@echo Will test on $(ENGLISH_TEST)
	java -mx6g edu.stanford.nlp.tagger.maxent.MaxentTagger -props $@.props > $@.out 2>&1
	java -mx2g edu.stanford.nlp.tagger.maxent.MaxentTagger -model $@ -testFile $(ENGLISH_TEST)  -verboseResults false >> $@.out 2>&1

french: french.tagger

french.tagger: $$@.props
	@echo Training $@
	@echo Will test on $(FRENCH_TEST)
	java -mx6g edu.stanford.nlp.tagger.maxent.MaxentTagger -props $@.props > $@.out 2>&1
	java -mx6g edu.stanford.nlp.tagger.maxent.MaxentTagger -model $@ -testFile $(FRENCH_TEST)  -verboseResults false >> $@.out 2>&1

# TODO: people might still appreciate a fast tagger?
german: german-ud.tagger

german-ud.tagger: $$@.props
	@echo Training $@
	@echo Will test on $(GERMAN_TEST)
	java -mx6g edu.stanford.nlp.tagger.maxent.MaxentTagger -props $@.props > $@.out 2>&1
	java -mx6g edu.stanford.nlp.tagger.maxent.MaxentTagger -model $@ -testFile $(GERMAN_TEST)  -verboseResults false >> $@.out 2>&1

hungarian: hungarian.tagger

hungarian.tagger: $$@.props
	@echo Training $@
	@echo Will test on $(HUNGARIAN_TEST)
	java -mx6g edu.stanford.nlp.tagger.maxent.MaxentTagger -props $@.props > $@.out 2>&1
	java -mx6g edu.stanford.nlp.tagger.maxent.MaxentTagger -model $@ -testFile $(HUNGARIAN_TEST)  -verboseResults false >> $@.out 2>&1

italian: italian.tagger

italian.tagger: $$@.props
	@echo Training $@
	@echo Will test on $(ITALIAN_TEST)
	java -mx6g edu.stanford.nlp.tagger.maxent.MaxentTagger -props $@.props > $@.out 2>&1
	java -mx6g edu.stanford.nlp.tagger.maxent.MaxentTagger -model $@ -testFile $(ITALIAN_TEST)  -verboseResults false >> $@.out 2>&1


spanish: spanish.tagger spanish-distsim.tagger

spanish.tagger spanish-distsim.tagger: $$@.props
	@echo Training $@
	@echo Will test on $(SPANISH_TEST)
	java -mx6g edu.stanford.nlp.tagger.maxent.MaxentTagger -props $@.props > $@.out 2>&1
#	java -mx6g edu.stanford.nlp.tagger.maxent.MaxentTagger -model $@ -testFile $(SPANISH_TEST) -verboseResults false >> $@.out 2>&1

testing: testing.tagger

testing.tagger:
	@echo Training $@
	java -mx6g edu.stanford.nlp.tagger.maxent.MaxentTagger -props $@.props > $@.out 2>&1

wsj: wsj-0-18-bidirectional-distsim.tagger  wsj-0-18-bidirectional-nodistsim.tagger  wsj-0-18-caseless-left3words-distsim.tagger  wsj-0-18-left3words-distsim.tagger wsj-0-18-left3words-nodistsim.tagger

wsj-0-18-bidirectional-distsim.tagger wsj-0-18-bidirectional-nodistsim.tagger wsj-0-18-caseless-left3words-distsim.tagger wsj-0-18-left3words-distsim.tagger wsj-0-18-left3words-nodistsim.tagger: $$@.props
	@echo Training $@
	@echo Will test on $(ENGLISH_TEST)
	java -mx6g edu.stanford.nlp.tagger.maxent.MaxentTagger -props $@.props > $@.out 2>&1
	java -mx6g edu.stanford.nlp.tagger.maxent.MaxentTagger -model $@ -testFile $(ENGLISH_TEST) -verboseResults false >> $@.out 2>&1
