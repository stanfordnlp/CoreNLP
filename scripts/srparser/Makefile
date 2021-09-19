
ifndef CONSTITUENCY_HOME
  WSJ_TRAIN  = /afs/ir/data/linguistic-data/Treebank/3/parsed/mrg/wsj 200-2199
  WSJ_DEV    = /afs/ir/data/linguistic-data/Treebank/3/parsed/mrg/wsj 2200-2219
  WSJ_TEST   = /afs/ir/data/linguistic-data/Treebank/3/parsed/mrg/wsj 2300-2399
else
  WSJ_TRAIN  = $(CONSTITUENCY_HOME)/english/train/wsj-train.mrg
  WSJ_DEV    = $(CONSTITUENCY_HOME)/english/dev/wsj-dev.mrg
  WSJ_TEST   = $(CONSTITUENCY_HOME)/english/test/wsj-test.mrg
endif
#WSJ_TAGGER = /u/nlp/data/pos-tagger/distrib/wsj-0-18-bidirectional-nodistsim.tagger
# TODO: technically we want a wsj-only tagger here
WSJ_TAGGER = edu/stanford/nlp/models/pos-tagger/english-left3words-distsim.tagger
WSJ_TLPP   = edu.stanford.nlp.parser.lexparser.EnglishTreebankParserParams


ifndef CONSTITUENCY_HOME
  ENGLISH_TRAIN  = /afs/ir/data/linguistic-data/Treebank/Treebank3Stanford/parsed/mrg/wsj 200-2199
  ENGLISH_TRAIN2 = /u/nlp/data/lexparser/extraTrain 1-4000,9000-9999
  ENGLISH_DEV    = $(WSJ_DEV)
  ENGLISH_TEST   = $(WSJ_TEST)
else
  ENGLISH_TRAIN  = $(CONSTITUENCY_HOME)/english/train
  ENGLISH_DEV    = $(CONSTITUENCY_HOME)/english/dev/wsj-dev.mrg
  ENGLISH_TEST   = $(CONSTITUENCY_HOME)/english/test/wsj-test.mrg
endif
#ENGLISH_TAGGER = /u/nlp/data/pos-tagger/distrib/english-left3words-distsim.tagger
ENGLISH_TAGGER = edu/stanford/nlp/models/pos-tagger/english-left3words-distsim.tagger
ENGLISH_TLPP   = $(WSJ_TLPP)


# French WAS in /u/nlp/data/lexparser/trees/FrenchCC
#  FTB-Train.utf8.txt, FTB-Dev.utf8.txt, FTB-Test.utf8.txt
ifndef CONSTITUENCY_HOME
  FRENCH_TRAIN  = /u/nlp/data/lexparser/trees/FrenchCC/FTB-Train.utf8.txt
  FRENCH_DEV    = /u/nlp/data/lexparser/trees/FrenchCC/FTB-Dev.utf8.txt
  FRENCH_TEST   = /u/nlp/data/lexparser/trees/FrenchCC/FTB-Test.utf8.txt
else
  FRENCH_TRAIN  = $(CONSTITUENCY_HOME)/french/ftb-cc-train.mrg
  FRENCH_DEV    = $(CONSTITUENCY_HOME)/french/ftb-cc-dev.mrg
  FRENCH_TEST   = $(CONSTITUENCY_HOME)/french/ftb-cc-test.mrg
endif
# FRENCH_TAGGER = /u/nlp/data/pos-tagger/distrib-2014-06-09/french.tagger
FRENCH_TAGGER = edu/stanford/nlp/models/pos-tagger/french-ud.tagger
FRENCH_TLPP   = edu.stanford.nlp.parser.lexparser.FrenchTreebankParserParams

ifndef CONSTITUENCY_HOME
  CHINESE_TRAIN  = /u/nlp/data/chinese/ctb7/train.mrg
  CHINESE_DEV    = /u/nlp/data/chinese/ctb7/dev_small.mrg
  CHINESE_TEST   = /u/nlp/data/chinese/ctb7/test.mrg
else
  CHINESE_TRAIN  = $(CONSTITUENCY_HOME)/chinese/ctb7/train.mrg
  CHINESE_DEV    = $(CONSTITUENCY_HOME)/chinese/ctb7/dev_small.mrg
  CHINESE_TEST   = $(CONSTITUENCY_HOME)/chinese/ctb7/test.mrg
endif
#CHINESE_TAGGER = /u/nlp/data/pos-tagger/distrib/chinese-nodistsim.tagger
CHINESE_TAGGER = edu/stanford/nlp/models/pos-tagger/chinese-distsim.tagger
CHINESE_TLPP   = edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams


# German WAS in /u/nlp/data/GermanACL08/negra/
#  train=negra_1.mrg, dev=negra_3.mrg, test=negra_2.mrg
ifndef CONSTITUENCY_HOME
  GERMAN_TRAIN  = /u/nlp/data/stanford-corenlp/models/data/german/negra-train.mrg
  GERMAN_DEV    = /u/nlp/data/stanford-corenlp/models/data/german/negra-dev.mrg
  GERMAN_TEST   = /u/nlp/data/stanford-corenlp/models/data/german/negra-test.mrg
else
  GERMAN_TRAIN  = $(CONSTITUENCY_HOME)/german/negra/negra-train.mrg
  GERMAN_DEV    = $(CONSTITUENCY_HOME)/german/negra/negra-dev.mrg
  GERMAN_TEST   = $(CONSTITUENCY_HOME)/german/negra/negra-test.mrg
endif
#GERMAN_TAGGER = /u/nlp/data/pos-tagger/distrib/german-fast.tagger
GERMAN_TAGGER = edu/stanford/nlp/models/pos-tagger/german-ud.tagger
GERMAN_TLPP   = edu.stanford.nlp.parser.lexparser.NegraPennTreebankParserParams


ifndef CONSTITUENCY_HOME
  ARABIC_TRAIN  = /u/nlp/data/lexparser/trees/Arabic/2-Unvoc-Train.utf8.txt
  ARABIC_DEV    = /u/nlp/data/lexparser/trees/Arabic/2-Unvoc-Dev.utf8.txt
  ARABIC_TEST   = /u/nlp/data/lexparser/trees/Arabic/2-Unvoc-Test.utf8.txt
else
  ARABIC_TRAIN  = $(CONSTITUENCY_HOME)/arabic/2-Unvoc-Train.utf8.txt
  ARABIC_DEV    = $(CONSTITUENCY_HOME)/arabic/2-Unvoc-Dev.utf8.txt
  ARABIC_TEST   = $(CONSTITUENCY_HOME)/arabic/2-Unvoc-Test.utf8.txt
endif
#ARABIC_TAGGER = /u/nlp/data/pos-tagger/distrib/arabic-train.tagger
ARABIC_TAGGER = edu/stanford/nlp/models/pos-tagger/arabic-train.tagger
ARABIC_TLPP   = edu.stanford.nlp.parser.lexparser.ArabicTreebankParserParams

# train was /u/nlp/data/spanish/ancora/ancora.train /u/nlp/data/spanish/ldc/ldc-NW.train /u/nlp/data/spanish/ldc/ldc-DF.train
# dev   was /u/nlp/data/spanish/ancora/ancora.dev   /u/nlp/data/spanish/ldc/ldc-NW.dev   /u/nlp/data/spanish/ldc/ldc-DF.dev
# test  was /u/nlp/data/spanish/ancora/ancora.test  /u/nlp/data/spanish/ldc/ldc-NW.test  /u/nlp/data/spanish/ldc/ldc-DF.test
ifndef CONSTITUENCY_HOME
  SPANISH_TRAIN = /u/nlp/data/stanford-corenlp/models/data/spanish train
  SPANISH_DEV   = /u/nlp/data/stanford-corenlp/models/data/spanish dev
  SPANISH_TEST  = /u/nlp/data/stanford-corenlp/models/data/spanish test
else
  SPANISH_TRAIN = $(CONSTITUENCY_HOME)/spanish train
  SPANISH_DEV   = $(CONSTITUENCY_HOME)/spanish dev
  SPANISH_TEST  = $(CONSTITUENCY_HOME)/spanish test
endif
#SPANISH_TAGGER= /u/nlp/data/pos-tagger/distrib/spanish-distsim.tagger
SPANISH_TAGGER= edu/stanford/nlp/models/pos-tagger/spanish-ud.tagger
SPANISH_TLPP  = edu.stanford.nlp.parser.lexparser.SpanishTreebankParserParams

ifndef CONSTITUENCY_HOME
  HUNGARIAN_TRAIN = /u/nlp/data/constituency-parser/spmrl/SPMRL_SHARED_2014/HUNGARIAN_SPMRL/gold/ptb/train/train.Hungarian.gold.ptb
  HUNGARIAN_DEV   = /u/nlp/data/constituency-parser/spmrl/SPMRL_SHARED_2014/HUNGARIAN_SPMRL/gold/ptb/dev/dev.Hungarian.gold.ptb
  HUNGARIAN_TEST  = /u/nlp/data/constituency-parser/spmrl/SPMRL_SHARED_2014/HUNGARIAN_SPMRL/gold/ptb/test/test.Hungarian.gold.ptb
else
  HUNGARIAN_TRAIN = $(CONSTITUENCY_HOME)/spmrl/SPMRL_SHARED_2014/HUNGARIAN_SPMRL/gold/ptb/train/train.Hungarian.gold.ptb
  HUNGARIAN_DEV   = $(CONSTITUENCY_HOME)/spmrl/SPMRL_SHARED_2014/HUNGARIAN_SPMRL/gold/ptb/dev/dev.Hungarian.gold.ptb
  HUNGARIAN_TEST  = $(CONSTITUENCY_HOME)/spmrl/SPMRL_SHARED_2014/HUNGARIAN_SPMRL/gold/ptb/test/test.Hungarian.gold.ptb
endif
#HUNGARIAN_TAGGER = edu/stanford/nlp/models/pos-tagger/hungarian-ud.tagger
HUNGARIAN_TAGGER = edu/stanford/nlp/models/pos-tagger/hungarian.tagger
HUNGARIAN_TLPP   = edu.stanford.nlp.parser.lexparser.HungarianTreebankParserParams

ifndef CONSTITUENCY_HOME
  ITALIAN_TRAIN = /u/nlp/data/constituency-parser/italian/it_turin/it_turin_train.mrg
  ITALIAN_DEV   = /u/nlp/data/constituency-parser/italian/it_turin/it_turin_dev.mrg
  ITALIAN_TEST  = /u/nlp/data/constituency-parser/italian/it_turin/it_turin_test.mrg
else
  ITALIAN_TRAIN = $(CONSTITUENCY_HOME)/italian/it_turin/it_turin_train.mrg
  ITALIAN_DEV   = $(CONSTITUENCY_HOME)/italian/it_turin/it_turin_dev.mrg
  ITALIAN_TEST  = $(CONSTITUENCY_HOME)/italian/it_turin/it_turin_test.mrg
endif
ITALIAN_TAGGER = edu/stanford/nlp/models/pos-tagger/italian.tagger
ITALIAN_TLPP   = edu.stanford.nlp.parser.lexparser.ItalianTreebankParserParams


DEFAULT_OPTIONS    = -trainingThreads 8 -batchSize 24 -trainingIterations 200 -stalledIterationLimit 40 -featureFrequencyCutoff 5

TRAIN_BEAM         = -featureFrequencyCutoff 10 -trainingMethod BEAM -trainBeamSize 8
TRAIN_ORACLE_BEAM  = -l1Reg 0.10 -featureFrequencyCutoff 10 -trainingMethod REORDER_BEAM -trainBeamSize 8

TRAIN_ORACLE_10    = -l1Reg 0.10 -featureFrequencyCutoff 10 -trainingMethod REORDER_ORACLE
# This is more suitable for larger datasets, such as the English datasets
TRAIN_ORACLE_25    = -l1Reg 0.25 -featureFrequencyCutoff 25 -trainingMethod REORDER_ORACLE

SHARDS_5           = -retrainShards 5
AUGMENT_LESS       = -augmentSubsentences 0.1

TEST_ARGS          =  -quietEvaluation -recordTransitionTypes

all-beam:   wsjSR.beam.ser.gz englishSR.beam.ser.gz frenchSR.beam.ser.gz chineseSR.beam.ser.gz germanSR.beam.ser.gz arabicSR.beam.ser.gz spanishSR.beam.ser.gz  hungarianSR.beam.ser.gz   italianSR.beam.ser.gz
all-nobeam: wsjSR.ser.gz      englishSR.ser.gz      frenchSR.ser.gz      chineseSR.ser.gz      germanSR.ser.gz      arabicSR.ser.gz      spanishSR.ser.gz       hungarianSR.ser.gz        italianSR.ser.gz
all: all-beam all-nobeam
.PHONY: all

wsjSR.ser.gz:
	@echo Training $@
	@echo Will test on $(WSJ_TEST)
	java -mx10g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank     $(WSJ_TRAIN) -devTreebank     $(WSJ_DEV) -serializedPath $@ $(DEFAULT_OPTIONS) -preTag -taggerSerializedFile     $(WSJ_TAGGER) -tlpp     $(WSJ_TLPP) $(TRAIN_ORACLE_25) > $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser $(TEST_ARGS) -testTreebank     $(WSJ_TEST) -serializedPath $@ -preTag -taggerSerializedFile $(WSJ_TAGGER) >> $@.out 2>&1

wsjSR.beam.ser.gz:
	@echo Training $@
	@echo Will test on $(WSJ_TEST)
	java -mx40g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank     $(WSJ_TRAIN) -devTreebank     $(WSJ_DEV) -serializedPath $@ $(DEFAULT_OPTIONS) -preTag -taggerSerializedFile     $(WSJ_TAGGER) -tlpp     $(WSJ_TLPP) $(TRAIN_BEAM) > $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser $(TEST_ARGS) -testTreebank     $(WSJ_TEST) -serializedPath $@ -preTag -taggerSerializedFile $(WSJ_TAGGER) >> $@.out 2>&1

englishSR.ser.gz:
	@echo Training $@
	@echo Will test on $(ENGLISH_TEST)
	java -mx10g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank $(ENGLISH_TRAIN) -devTreebank $(ENGLISH_DEV) -serializedPath $@ $(DEFAULT_OPTIONS) -preTag -taggerSerializedFile $(ENGLISH_TAGGER) -tlpp $(ENGLISH_TLPP) $(TRAIN_ORACLE_25) $(AUGMENT_LESS) > $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser $(TEST_ARGS) -testTreebank $(ENGLISH_TEST) -serializedPath $@ -preTag -taggerSerializedFile $(ENGLISH_TAGGER) >> $@.out 2>&1

englishSR.beam.ser.gz:
	@echo Training $@
	@echo Will test on $(ENGLISH_TEST)
	java -mx80g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank $(ENGLISH_TRAIN) -devTreebank $(ENGLISH_DEV) -serializedPath $@ $(DEFAULT_OPTIONS) -preTag -taggerSerializedFile $(ENGLISH_TAGGER) -tlpp $(ENGLISH_TLPP) $(TRAIN_BEAM) $(AUGMENT_LESS) > $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser $(TEST_ARGS) -testTreebank $(ENGLISH_TEST) -serializedPath $@ -preTag -taggerSerializedFile $(ENGLISH_TAGGER) >> $@.out 2>&1

frenchSR.ser.gz:
	@echo Training $@
	@echo Will test on $(FRENCH_TEST)
	java -mx10g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank  $(FRENCH_TRAIN) -devTreebank  $(FRENCH_DEV) -serializedPath $@ $(DEFAULT_OPTIONS) -preTag -taggerSerializedFile  $(FRENCH_TAGGER) -tlpp  $(FRENCH_TLPP) $(TRAIN_ORACLE_10) > $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser $(TEST_ARGS) -testTreebank  $(FRENCH_TEST) -serializedPath $@ -preTag -taggerSerializedFile $(FRENCH_TAGGER) >> $@.out 2>&1

# French performance was falling with some of the changes, but the
# number of shards increases it back a bit
frenchSR.beam.ser.gz:
	@echo Training $@
	@echo Will test on $(FRENCH_TEST)
	java -mx40g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank  $(FRENCH_TRAIN) -devTreebank  $(FRENCH_DEV) -serializedPath $@ $(DEFAULT_OPTIONS) -preTag -taggerSerializedFile  $(FRENCH_TAGGER) -tlpp  $(FRENCH_TLPP) $(TRAIN_BEAM) $(SHARDS_5) > $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser $(TEST_ARGS) -testTreebank  $(FRENCH_TEST) -serializedPath $@ -preTag -taggerSerializedFile $(FRENCH_TAGGER) >> $@.out 2>&1

chineseSR.ser.gz:
	@echo Training $@
	@echo Will test on $(CHINESE_TEST)
	java -mx10g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank $(CHINESE_TRAIN) -devTreebank $(CHINESE_DEV) -serializedPath $@ $(DEFAULT_OPTIONS) -preTag -taggerSerializedFile $(CHINESE_TAGGER) -tlpp $(CHINESE_TLPP) $(TRAIN_ORACLE_10) > $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser $(TEST_ARGS) -testTreebank $(CHINESE_TEST) -serializedPath $@ -preTag -taggerSerializedFile $(CHINESE_TAGGER) >> $@.out 2>&1

chineseSR.beam.ser.gz:
	@echo Training $@
	@echo Will test on $(CHINESE_TEST)
	java -mx50g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank $(CHINESE_TRAIN) -devTreebank $(CHINESE_DEV) -serializedPath $@ $(DEFAULT_OPTIONS) -preTag -taggerSerializedFile $(CHINESE_TAGGER) -tlpp $(CHINESE_TLPP) $(TRAIN_BEAM) $(SHARDS_5) > $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser $(TEST_ARGS) -testTreebank $(CHINESE_TEST) -serializedPath $@ -preTag -taggerSerializedFile $(CHINESE_TAGGER) >> $@.out 2>&1

germanSR.ser.gz:
	@echo Training $@
	@echo Will test on $(GERMAN_TEST)
	java -mx10g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank  $(GERMAN_TRAIN) -devTreebank  $(GERMAN_DEV) -serializedPath $@ $(DEFAULT_OPTIONS) -preTag -taggerSerializedFile  $(GERMAN_TAGGER) -tlpp  $(GERMAN_TLPP) $(TRAIN_ORACLE_10) > $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser $(TEST_ARGS) -testTreebank  $(GERMAN_TEST) -serializedPath $@ -preTag -taggerSerializedFile $(GERMAN_TAGGER) >> $@.out 2>&1

germanSR.beam.ser.gz:
	@echo Training $@
	@echo Will test on $(GERMAN_TEST)
	java -mx50g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank  $(GERMAN_TRAIN) -devTreebank  $(GERMAN_DEV) -serializedPath $@ $(DEFAULT_OPTIONS) -preTag -taggerSerializedFile  $(GERMAN_TAGGER) -tlpp  $(GERMAN_TLPP) $(TRAIN_BEAM) > $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser $(TEST_ARGS) -testTreebank  $(GERMAN_TEST) -serializedPath $@ -preTag -taggerSerializedFile $(GERMAN_TAGGER) >> $@.out 2>&1

arabicSR.ser.gz:
	@echo Training $@
	@echo Will test on $(ARABIC_TEST)
	java -mx10g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank  $(ARABIC_TRAIN) -devTreebank  $(ARABIC_DEV) -serializedPath $@ $(DEFAULT_OPTIONS) -preTag -taggerSerializedFile  $(ARABIC_TAGGER) -tlpp  $(ARABIC_TLPP) $(TRAIN_ORACLE_10) > $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser $(TEST_ARGS) -testTreebank  $(ARABIC_TEST) -serializedPath $@ -preTag -taggerSerializedFile $(ARABIC_TAGGER) >> $@.out 2>&1

arabicSR.beam.ser.gz:
	@echo Training $@
	@echo Will test on $(ARABIC_TEST)
	java -mx50g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank  $(ARABIC_TRAIN) -devTreebank  $(ARABIC_DEV) -serializedPath $@ $(DEFAULT_OPTIONS) -preTag -taggerSerializedFile  $(ARABIC_TAGGER) -tlpp  $(ARABIC_TLPP) $(TRAIN_BEAM) > $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser $(TEST_ARGS) -testTreebank  $(ARABIC_TEST) -serializedPath $@ -preTag -taggerSerializedFile $(ARABIC_TAGGER) >> $@.out 2>&1

spanishSR.ser.gz:
	@echo Training $@
	@echo Will test on $(SPANISH_TEST)
	java -mx10g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank $(SPANISH_TRAIN) -devTreebank $(SPANISH_DEV) -serializedPath $@ $(DEFAULT_OPTIONS) -preTag -taggerSerializedFile $(SPANISH_TAGGER) -tlpp $(SPANISH_TLPP) $(TRAIN_ORACLE_10) > $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser $(TEST_ARGS) -testTreebank $(SPANISH_TEST) -serializedPath $@ -preTag -taggerSerializedFile $(SPANISH_TAGGER) >> $@.out 2>&1

spanishSR.beam.ser.gz:
	@echo Training $@
	@echo Will test on $(SPANISH_TEST)
	java -mx50g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank $(SPANISH_TRAIN) -devTreebank $(SPANISH_DEV) -serializedPath $@ $(DEFAULT_OPTIONS) -preTag -taggerSerializedFile $(SPANISH_TAGGER) -tlpp $(SPANISH_TLPP) $(TRAIN_BEAM) > $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser $(TEST_ARGS) -testTreebank $(SPANISH_TEST) -serializedPath $@ -preTag -taggerSerializedFile $(SPANISH_TAGGER) >> $@.out 2>&1

hungarianSR.ser.gz:
	@echo Training $@
	@echo Will test on $(HUNGARIAN_TEST)
	java -mx10g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank $(HUNGARIAN_TRAIN) -devTreebank $(HUNGARIAN_DEV) -serializedPath $@ $(DEFAULT_OPTIONS) -preTag -taggerSerializedFile $(HUNGARIAN_TAGGER) -tlpp $(HUNGARIAN_TLPP) $(TRAIN_ORACLE_10) > $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser $(TEST_ARGS) -testTreebank $(HUNGARIAN_TEST) -serializedPath $@ -preTag -taggerSerializedFile $(HUNGARIAN_TAGGER) >> $@.out 2>&1

hungarianSR.beam.ser.gz:
	@echo Training $@
	@echo Will test on $(HUNGARIAN_TEST)
	java -mx50g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank $(HUNGARIAN_TRAIN) -devTreebank $(HUNGARIAN_DEV) -serializedPath $@ $(DEFAULT_OPTIONS) -preTag -taggerSerializedFile $(HUNGARIAN_TAGGER) -tlpp $(HUNGARIAN_TLPP) $(TRAIN_BEAM) > $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser $(TEST_ARGS) -testTreebank $(HUNGARIAN_TEST) -serializedPath $@ -preTag -taggerSerializedFile $(HUNGARIAN_TAGGER) >> $@.out 2>&1


italianSR.ser.gz:
	@echo Training $@
	@echo Will test on $(ITALIAN_TEST)
	java -mx10g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank $(ITALIAN_TRAIN) -devTreebank $(ITALIAN_DEV) -serializedPath $@ $(DEFAULT_OPTIONS) -preTag -taggerSerializedFile $(ITALIAN_TAGGER) -tlpp $(ITALIAN_TLPP) $(TRAIN_ORACLE_10) > $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser $(TEST_ARGS) -testTreebank $(ITALIAN_TEST) -serializedPath $@ -preTag -taggerSerializedFile $(ITALIAN_TAGGER) >> $@.out 2>&1

italianSR.beam.ser.gz:
	@echo Training $@
	@echo Will test on $(ITALIAN_TEST)
	java -mx50g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank $(ITALIAN_TRAIN) -devTreebank $(ITALIAN_DEV) -serializedPath $@ $(DEFAULT_OPTIONS) -preTag -taggerSerializedFile $(ITALIAN_TAGGER) -tlpp $(ITALIAN_TLPP) $(TRAIN_BEAM) > $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser $(TEST_ARGS) -testTreebank $(ITALIAN_TEST) -serializedPath $@ -preTag -taggerSerializedFile $(ITALIAN_TAGGER) >> $@.out 2>&1

