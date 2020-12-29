DIR=/u/nlp/data/gale/segtool/stanford-seg/props
SCORE=/u/nlp/data/gale/segtool/stanford-seg/data/Sighan2006/score
SIGHAN2003_TRAIN_DICT=/u/nlp/data/gale/segtool/stanford-seg/test/ctb.sighan.train.utf8.dict
SIGHAN2003_TEST_GOLD=/u/nlp/data/chinese-segmenter/Sighan2005/dev/ctb-testref.txt.utf8
PK_TRAIN_DICT=/u/nlp/data/chinese-segmenter/Sighan2005/train/pku-training.txt.utf8.dict
PK_TEST_GOLD=/u/nlp/data/chinese-segmenter/Sighan2005/dev/pk-testref.txt.utf8 
CTB5_MINUS_SIGHAN2003_TRAIN=/u/nlp/data/chinese-segmenter/gale2007/ctb5minusSighan2003/ctb5minusSighan2003forTrain.utf8
CTB5_MINUS_SIGHAN2003_TRAIN_DICT=/u/nlp/data/chinese-segmenter/gale2007/ctb5minusSighan2003/ctb5minusSighan2003forTrain.utf8.dict

DICT_1024=/u/nlp/data/chinese-dictionaries/plain/adso-1.25-050405-monolingual-clean.utf8.txt,/u/nlp/data/chinese-dictionaries/plain/lexicon_108k_normalized.txt,/u/nlp/data/chinese-dictionaries/plain/lexicon_mandarintools_normalized.txt,/u/nlp/data/chinese-dictionaries/plain/harbin-ChineseNames_utf8.txt,/u/nlp/data/chinese-dictionaries/plain/lexicon_HowNet_normalized.txt,/u/nlp/data/chinese-dictionaries/plain/wikilex-20070908-zh-en.txt

SIGHAN2006_CORPORA_DICT=/u/nlp/data/chinese-segmenter/gale2007/ctb6minusSighan2006

ifndef CHINESE_SEGMENTER_HOME
  SIGHAN2007_CORPORA_DICT=/u/nlp/data/chinese-segmenter/gale2007/ctb6
else
  SIGHAN2007_CORPORA_DICT=$(CHINESE_SEGMENTER_HOME)
endif

DICT_CHRIS5=/u/nlp/data/chinese-dictionaries/plain/ne_wikipedia-utf8.txt,/u/nlp/data/chinese-dictionaries/plain/newsexplorer_entities_utf8.txt,/u/nlp/data/chinese-dictionaries/plain/Ch-name-list-utf8.txt,/u/nlp/data/chinese-dictionaries/plain/wikilex-20070908-zh-en.txt,/u/nlp/data/chinese-dictionaries/plain/adso-1.25-050405-monolingual-clean.utf8.txt,/u/nlp/data/chinese-dictionaries/plain/lexicon_108k_normalized.txt,/u/nlp/data/chinese-dictionaries/plain/lexicon_mandarintools_normalized.txt,/u/nlp/data/chinese-dictionaries/plain/harbin-ChineseNames_utf8.txt,/u/nlp/data/chinese-dictionaries/plain/lexicon_HowNet_normalized.txt

# Same as for chris5, currently
DICT_CHRIS6=/u/nlp/data/chinese-dictionaries/plain/ne_wikipedia-utf8.txt,/u/nlp/data/chinese-dictionaries/plain/newsexplorer_entities_utf8.txt,/u/nlp/data/chinese-dictionaries/plain/Ch-name-list-utf8.txt,/u/nlp/data/chinese-dictionaries/plain/wikilex-20070908-zh-en.txt,/u/nlp/data/chinese-dictionaries/plain/adso-1.25-050405-monolingual-clean.utf8.txt,/u/nlp/data/chinese-dictionaries/plain/lexicon_108k_normalized.txt,/u/nlp/data/chinese-dictionaries/plain/lexicon_mandarintools_normalized.txt,/u/nlp/data/chinese-dictionaries/plain/harbin-ChineseNames_utf8.txt,/u/nlp/data/chinese-dictionaries/plain/lexicon_HowNet_normalized.txt

CTB6_PROCESSED=/u/nlp/data/gale/segtool/stanford-seg/data/ctb6.all.processed

CTB6_NOTEST_PROCESSED=/u/nlp/data/gale/segtool/stanford-seg/data/ctb6.notest.processed

CTB7_ALL=/u/nlp/data/chinese/ctb7/seg/ctb7-seg-with-extra.txt

CTB7_TRAIN=/u/nlp/data/chinese/ctb7/seg/ctb7-seg.train.txt

ifndef CHINESE_SEGMENTER_HOME
  CTB9_TRAIN=/u/nlp/data/chinese/ctb9/seg/ctb9-seg-with-extra.txt
  CTB9_TEST=/u/nlp/data/chinese/ctb9/seg/ctb9.test.txt

  GSD_TRAIN=/u/nlp/data/chinese/ctb9/seg/zh_gsdsimp.train.seg.txt
  GSD_TEST=/u/nlp/data/chinese/ctb9/seg/zh_gsdsimp.test.seg.txt
else
  CTB9_TRAIN=$(CHINESE_SEGMENTER_HOME)/ctb9-seg-with-extra.txt
  CTB9_TEST=$(CHINESE_SEGMENTER_HOME)/ctb9.test.txt

  GSD_TRAIN=$(CHINESE_SEGMENTER_HOME)/zh_gsdsimp.train.seg.txt
  GSD_TEST=$(CHINESE_SEGMENTER_HOME)/zh_gsdsimp.test.seg.txt
endif

# Special prerelease segmentation data from Bolt.  Do not release publicly!
BOLT=/u/nlp/data/chinese/bolt/combined-seg.txt


dict-chris6.ser.gz:
	time java -mx15g edu.stanford.nlp.wordseg.ChineseDictionary -output $@


# train and test on Sighan 2006 data. No serialized model will be produced
# Revision: 20267..
ctb6.chris6.lex.result: dict-chris6.ser.gz
	echo "Train and test"
	time java6 -mx7g edu.stanford.nlp.ie.crf.CRFClassifier -prop $(DIR)/sighan2006-chris6.prop -sighanCorporaDict $(SIGHAN2006_CORPORA_DICT) -serDictionary $+ -serializeTo sighan2006-chris6.lex.gz -serializeToText sighan2006-chris6.lex.text.gz > sighan2006-chris6.lex.log 2> sighan2006-chris6.lex.err
	echo "Running eval"
	tail -5117 $(DIR)/05202008-sighan2006-chris6.lex.log > $(DIR)/05202008-sighan2006-chris6.lex.out
	$(SCORE) /u/nlp/data/gale/segtool/stanford-seg/props/sighan2006-train.dict /u/nlp/data/gale/segtool/stanford-seg/data/Sighan2006/CTB_gold/CTB.utf8.simp.gold $(DIR)/05202008-sighan2006-chris6.lex.out > $(DIR)/$@

# train on all CTB6, with all external lexicons, without training lexicon
ctb6.chris6.ser.gz: dict-chris6.ser.gz
	time java6 -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop $(DIR)/ctb6-chris6.prop -serDictionary $+ -sighanCorporaDict $(SIGHAN2007_CORPORA_DICT) -trainFile $(CTB6_PROCESSED) -serializeTo $@ > ctb6.chris6.lex.log 2> ctb6.chris6.lex.err

# train on all CTB6, with all external lexicons, without training lexicon
ctb6.notest.chris6.ser.gz: dict-chris6.ser.gz
	time java6 -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop $(DIR)/ctb6-chris6.prop -serDictionary $+ -sighanCorporaDict $(SIGHAN2007_CORPORA_DICT) -trainFile $(CTB6_NOTEST_PROCESSED) -serializeTo $@ > ctb6.notest.chris6.lex.log 2> ctb6.notest.chris6.lex.err

# train on all CTB7, with all external lexicons, without training lexicon
ctb7.chris6.ser.gz: dict-chris6.ser.gz
	time java -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop $(DIR)/ctb6-chris6.prop -serDictionary $+ -sighanCorporaDict $(SIGHAN2007_CORPORA_DICT) -trainFile $(CTB7_ALL) -serializeTo $@ > $@.log 2> $@.err

# train on train CTB7, with all external lexicons, without training lexicon
ctb7.train.chris6.ser.gz: dict-chris6.ser.gz
	time java -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop $(DIR)/ctb6-chris6.prop -serDictionary $+ -sighanCorporaDict $(SIGHAN2007_CORPORA_DICT) -trainFile $(CTB7_TRAIN) -serializeTo $@ > $@.log 2> $@.err

# train on train CTB9 + extras, with all external lexicons, without training lexicon
ctb9.train.chris6.ser.gz: dict-chris6.ser.gz
	time java -mx60g edu.stanford.nlp.ie.crf.CRFClassifier -prop ctb9-chris6.prop -serDictionary $+ -sighanCorporaDict $(SIGHAN2007_CORPORA_DICT) -trainFile $(CTB9_TRAIN) -serializeTo $@ > $@.log 2> $@.err
	time java -mx5g  edu.stanford.nlp.ie.crf.CRFClassifier  -sighanCorporaDict $(SIGHAN2007_CORPORA_DICT) -loadClassifier $@ -testFile $(CTB9_TEST) -inputEncoding UTF-8 -sighanPostProcessing true -serDictionary $+ -keepAllWhitespaces false >> $@.log 2>> $@.err

# train on train CTB9 + extras, with all external lexicons, without training lexicon, use the threshold to make it smaller
ctb9.train-small.chris6.ser.gz: dict-chris6.ser.gz
	time java -mx60g edu.stanford.nlp.ie.crf.CRFClassifier -prop ctb9-chris6.prop -serDictionary $+ -sighanCorporaDict $(SIGHAN2007_CORPORA_DICT) -featureDiffThresh 0.005 -trainFile $(CTB9_TRAIN) -serializeTo $@ > $@.log 2> $@.err
	time java -mx5g  edu.stanford.nlp.ie.crf.CRFClassifier  -sighanCorporaDict $(SIGHAN2007_CORPORA_DICT) -loadClassifier $@ -testFile $(CTB9_TEST) -inputEncoding UTF-8 -sighanPostProcessing true -serDictionary $+ -keepAllWhitespaces false >> $@.log 2>> $@.err

# train on train GSD, with all external lexicons, without training lexicon
# there is a script in Stanza which converts the UD GSD treebank to a segmenter training file:
#   stanza/utils/datasets/corenlp_segmenter_dataset.py
gsd.ser.gz: dict-chris6.ser.gz
	time java -mx60g edu.stanford.nlp.ie.crf.CRFClassifier -prop ctb9-chris6.prop -serDictionary $+ -sighanCorporaDict $(SIGHAN2007_CORPORA_DICT) -trainFile $(GSD_TRAIN) -serializeTo $@ > $@.log 2> $@.err
	time java -mx5g  edu.stanford.nlp.ie.crf.CRFClassifier  -sighanCorporaDict $(SIGHAN2007_CORPORA_DICT) -loadClassifier $@ -testFile $(GSD_TEST) -inputEncoding UTF-8 -sighanPostProcessing true -serDictionary $+ -keepAllWhitespaces false >> $@.log 2>> $@.err

# train on all CTB7, with all external lexicons, without training lexicon
bolt.chris6.ser.gz: dict-chris6.ser.gz
	time java -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop $(DIR)/ctb6-chris6.prop -serDictionary $+ -sighanCorporaDict $(SIGHAN2007_CORPORA_DICT) -trainFile $(BOLT) -serializeTo $@ > $@.log 2> $@.err

