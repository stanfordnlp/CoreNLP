
WSJ_TRAIN  = /afs/ir/data/linguistic-data/Treebank/3/parsed/mrg/wsj 200-2199
WSJ_DEV    = /afs/ir/data/linguistic-data/Treebank/3/parsed/mrg/wsj 2200-2219
WSJ_TEST   = /afs/ir/data/linguistic-data/Treebank/3/parsed/mrg/wsj 2300-2399
WSJ_TAGGER = /u/nlp/data/pos-tagger/distrib/wsj-0-18-bidirectional-nodistsim.tagger
WSJ_TLPP   = edu.stanford.nlp.parser.lexparser.EnglishTreebankParserParams


ENGLISH_TRAIN  = /afs/ir/data/linguistic-data/Treebank/Treebank3Stanford/parsed/mrg/wsj 200-2199
ENGLISH_TRAIN2 = /u/nlp/data/lexparser/extraTrain 1-4000,9000-9999
ENGLISH_DEV    = $(WSJ_DEV)
ENGLISH_TEST   = $(WSJ_TEST)
ENGLISH_TAGGER = /u/nlp/data/pos-tagger/distrib/english-left3words-distsim.tagger
ENGLISH_TLPP   = $(WSJ_TLPP)


FRENCH_TRAIN  = /u/nlp/data/lexparser/trees/FrenchCC/FTB-Train.utf8.txt
FRENCH_DEV    = /u/nlp/data/lexparser/trees/FrenchCC/FTB-Dev.utf8.txt
FRENCH_TEST   = /u/nlp/data/lexparser/trees/FrenchCC/FTB-Test.utf8.txt
FRENCH_TAGGER = /u/nlp/data/pos-tagger/distrib-2014-06-09/french.tagger
FRENCH_TLPP   = edu.stanford.nlp.parser.lexparser.FrenchTreebankParserParams


CHINESE_TRAIN  = /u/nlp/data/chinese/ctb7/train.mrg
CHINESE_DEV    = /u/nlp/data/chinese/ctb7/dev_small.mrg
CHINESE_TEST   = /u/nlp/data/chinese/ctb7/test.mrg
CHINESE_TAGGER = /u/nlp/data/pos-tagger/distrib/chinese-nodistsim.tagger
CHINESE_TLPP   = edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams


GERMAN_TRAIN  = /u/nlp/data/GermanACL08/negra/negra_1.mrg
GERMAN_DEV    = /u/nlp/data/GermanACL08/negra/negra_3.mrg
GERMAN_TEST   = /u/nlp/data/GermanACL08/negra/negra_2.mrg
GERMAN_TAGGER = /u/nlp/data/pos-tagger/distrib/german-fast.tagger
GERMAN_TLPP   = edu.stanford.nlp.parser.lexparser.NegraPennTreebankParserParams


ARABIC_TRAIN  = /u/nlp/data/lexparser/trees/Arabic/2-Unvoc-Train.utf8.txt
ARABIC_DEV    = /u/nlp/data/lexparser/trees/Arabic/2-Unvoc-Dev.utf8.txt
ARABIC_TEST   = /u/nlp/data/lexparser/trees/Arabic/2-Unvoc-Test.utf8.txt
ARABIC_TAGGER = /u/nlp/data/pos-tagger/distrib/arabic-train.tagger
ARABIC_TLPP   = edu.stanford.nlp.parser.lexparser.ArabicTreebankParserParams

SPANISH_TRAIN = /u/nlp/data/spanish/ancora/ancora.train
SPANISH_TRAIN2 = /u/nlp/data/spanish/ldc/ldc-NW.train
SPANISH_TRAIN3 = /u/nlp/data/spanish/ldc/ldc-DF.train
SPANISH_DEV   = /u/nlp/data/spanish/ancora/ancora.dev
SPANISH_DEV2 = /u/nlp/data/spanish/ldc/ldc-NW.dev
SPANISH_DEV3 = /u/nlp/data/spanish/ldc/ldc-DF.dev
SPANISH_DEV_TMP = /u/nlp/data/spanish/all.dev.tmp
SPANISH_TEST  = /u/nlp/data/spanish/ancora/ancora.test
SPANISH_TEST2 = /u/nlp/data/spanish/ldc/ldc-NW.train
SPANISH_TEST3 = /u/nlp/data/spanish/ldc/ldc-DF.train
SPANISH_TAGGER= /u/nlp/data/pos-tagger/distrib/spanish-distsim.tagger
SPANISH_TLPP  = edu.stanford.nlp.parser.lexparser.SpanishTreebankParserParams


TRAIN_BEAM = -trainingMethod BEAM -trainBeamSize 4
TEST_BEAM  = -beamSize 4
CUTOFF     = -featureFrequencyCutoff 5

all: wsjSR.ser.gz wsjSR.beam.ser.gz englishSR.ser.gz englishSR.beam.ser.gz frenchSR.ser.gz frenchSR.beam.ser.gz chineseSR.ser.gz germanSR.ser.gz arabicSR.ser.gz spanishSR.ser.gz spanishSR.beam.ser.gz
.PHONY: all

wsjSR.ser.gz:
	@echo Training $@
	@echo Will test on $(WSJ_TEST)
	java -mx10g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank $(WSJ_TRAIN) -devTreebank $(WSJ_DEV) -serializedPath $@ -trainingThreads 4 -batchSize 12 -preTag -taggerSerializedFile $(WSJ_TAGGER) -trainingIterations 100 -stalledIterationLimit 25 -tlpp $(WSJ_TLPP) > $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -testTreebank $(WSJ_TEST) -serializedPath $@ -preTag -taggerSerializedFile $(WSJ_TAGGER) -trainingIterations 100 -stalledIterationLimit 25 >> $@.out 2>&1

wsjSR.beam.ser.gz:
	@echo Training $@
	@echo Will test on $(WSJ_TEST)
	java -mx20g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank $(WSJ_TRAIN) -devTreebank $(WSJ_DEV) -serializedPath $@ -trainingThreads 4 -batchSize 12 -preTag -taggerSerializedFile $(WSJ_TAGGER) -trainingIterations 100 -stalledIterationLimit 25 -tlpp $(WSJ_TLPP) $(TRAIN_BEAM) $(CUTOFF) > $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -testTreebank $(WSJ_TEST) -serializedPath $@ -preTag -taggerSerializedFile $(WSJ_TAGGER) -trainingIterations 100 -stalledIterationLimit 25 $(TEST_BEAM) >> $@.out 2>&1

englishSR.ser.gz:
	@echo Training $@
	@echo Will test on $(ENGLISH_TEST)
	java -mx10g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank $(ENGLISH_TRAIN) -trainTreebank $(ENGLISH_TRAIN2) -devTreebank $(ENGLISH_DEV) -serializedPath $@ -trainingThreads 4 -batchSize 12 -preTag -taggerSerializedFile $(ENGLISH_TAGGER) -trainingIterations 100 -stalledIterationLimit 25 -tlpp $(ENGLISH_TLPP) > $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -testTreebank $(ENGLISH_TEST) -serializedPath $@ -preTag -taggerSerializedFile $(ENGLISH_TAGGER) -trainingIterations 100 -stalledIterationLimit 25 >> $@.out 2>&1

englishSR.beam.ser.gz:
	@echo Training $@
	@echo Will test on $(ENGLISH_TEST)
	java -mx20g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank $(ENGLISH_TRAIN) -trainTreebank $(ENGLISH_TRAIN2) -devTreebank $(ENGLISH_DEV) -serializedPath $@ -trainingThreads 4 -batchSize 12 -preTag -taggerSerializedFile $(ENGLISH_TAGGER) -trainingIterations 100 -stalledIterationLimit 25 -tlpp $(ENGLISH_TLPP) $(TRAIN_BEAM) $(CUTOFF) > $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -testTreebank $(ENGLISH_TEST) -serializedPath $@ -preTag -taggerSerializedFile $(ENGLISH_TAGGER) -trainingIterations 100 -stalledIterationLimit 25 $(TEST_BEAM) >> $@.out 2>&1

frenchSR.ser.gz:
	@echo Training $@
	@echo Will test on $(FRENCH_TEST)
	java -mx10g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank $(FRENCH_TRAIN) -devTreebank $(FRENCH_DEV) -serializedPath $@ -trainingThreads 4 -batchSize 12 -preTag -taggerSerializedFile $(FRENCH_TAGGER) -trainingIterations 100 -stalledIterationLimit 25 -tlpp $(FRENCH_TLPP) > $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -testTreebank $(FRENCH_TEST) -serializedPath $@ -preTag -taggerSerializedFile $(FRENCH_TAGGER) -trainingIterations 100 -stalledIterationLimit 25 >> $@.out 2>&1

frenchSR.beam.ser.gz:
	@echo Training $@
	@echo Will test on $(FRENCH_TEST)
	java -mx20g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank $(FRENCH_TRAIN) -devTreebank $(FRENCH_DEV) -serializedPath $@ -trainingThreads 4 -batchSize 12 -preTag -taggerSerializedFile $(FRENCH_TAGGER) -trainingIterations 100 -stalledIterationLimit 25 -tlpp $(FRENCH_TLPP) $(TRAIN_BEAM) $(CUTOFF) > $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -testTreebank $(FRENCH_TEST) -serializedPath $@ -preTag -taggerSerializedFile $(FRENCH_TAGGER) -trainingIterations 100 -stalledIterationLimit 25 $(TEST_BEAM) >> $@.out 2>&1

chineseSR.ser.gz:
	@echo Training $@
	@echo Will test on $(CHINESE_TEST)
	java -mx10g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank $(CHINESE_TRAIN) -devTreebank $(CHINESE_DEV) -serializedPath $@ -trainingThreads 4 -batchSize 12 -preTag -taggerSerializedFile $(CHINESE_TAGGER) -trainingIterations 100 -stalledIterationLimit 25 -tlpp $(CHINESE_TLPP) > $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -testTreebank $(CHINESE_TEST) -serializedPath $@ -preTag -taggerSerializedFile $(CHINESE_TAGGER) -trainingIterations 100 -stalledIterationLimit 25 >> $@.out 2>&1

germanSR.ser.gz:
	@echo Training $@
	@echo Will test on $(GERMAN_TEST)
	java -mx10g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank $(GERMAN_TRAIN) -devTreebank $(GERMAN_DEV) -serializedPath $@ -trainingThreads 4 -batchSize 12 -preTag -taggerSerializedFile $(GERMAN_TAGGER) -trainingIterations 100 -stalledIterationLimit 25 -tlpp $(GERMAN_TLPP) > $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -testTreebank $(GERMAN_TEST) -serializedPath $@ -preTag -taggerSerializedFile $(GERMAN_TAGGER) -trainingIterations 100 -stalledIterationLimit 25 >> $@.out 2>&1

arabicSR.ser.gz:
	@echo Training $@
	@echo Will test on $(ARABIC_TEST)
	java -mx10g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank $(ARABIC_TRAIN) -devTreebank $(ARABIC_DEV) -serializedPath $@ -trainingThreads 4 -batchSize 12 -preTag -taggerSerializedFile $(ARABIC_TAGGER) -trainingIterations 100 -stalledIterationLimit 25 -tlpp $(ARABIC_TLPP) > $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -testTreebank $(ARABIC_TEST) -serializedPath $@ -preTag -taggerSerializedFile $(ARABIC_TAGGER) -trainingIterations 100 -stalledIterationLimit 25 >> $@.out 2>&1

spanishSR.ser.gz:
	@echo Training $@
	@echo Creating unified Spanish development data file $(SPANISH_DEV_TMP)
	cat $(SPANISH_DEV) $(SPANISH_DEV2) $(SPANISH_DEV3) > $(SPANISH_DEV_TMP)
	@echo Will test on $(SPANISH_TEST)
	java -mx10g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank $(SPANISH_TRAIN) -trainTreebank $(SPANISH_TRAIN2) -trainTreebank $(SPANISH_TRAIN3) -devTreebank $(SPANISH_DEV_TMP) -serializedPath $@ -trainingThreads 4 -batchSize 12 -preTag -taggerSerializedFile $(SPANISH_TAGGER) -trainingIterations 100 -stalledIterationLimit 25 -tlpp $(SPANISH_TLPP) > $@.out 2>&1
	rm $(SPANISH_DEV_TMP)
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -testTreebank $(SPANISH_TEST) -serializedPath $@ -preTag -taggerSerializedFile $(SPANISH_TAGGER) -trainingIterations 100 -stalledIterationLimit 25 >> $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -testTreebank $(SPANISH_TEST2) -serializedPath $@ -preTag -taggerSerializedFile $(SPANISH_TAGGER) -trainingIterations 100 -stalledIterationLimit 25 >> $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -testTreebank $(SPANISH_TEST3) -serializedPath $@ -preTag -taggerSerializedFile $(SPANISH_TAGGER) -trainingIterations 100 -stalledIterationLimit 25 >> $@.out 2>&1

spanishSR.beam.ser.gz:
	@echo Training $@
	@echo Will test on $(SPANISH_TEST)
	java -mx20g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank $(SPANISH_TRAIN) -devTreebank $(SPANISH_DEV) -serializedPath $@ -trainingThreads 4 -batchSize 12 -preTag -taggerSerializedFile $(SPANISH_TAGGER) -trainingIterations 100 -stalledIterationLimit 25 -tlpp $(SPANISH_TLPP) $(TRAIN_BEAM) $(CUTOFF) > $@.out 2>&1
	java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -testTreebank $(SPANISH_TEST) -serializedPath $@ -preTag -taggerSerializedFile $(SPANISH_TAGGER) -trainingIterations 100 -stalledIterationLimit 25 $(TEST_BEAM) >> $@.out 2>&1
