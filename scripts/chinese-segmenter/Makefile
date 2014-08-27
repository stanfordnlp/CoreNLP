DIR=/u/nlp/data/gale/segtool/stanford-seg/props
SCORE=/u/nlp/data/gale/segtool/stanford-seg/data/Sighan2006/score
SIGHAN2003_TRAIN_DICT=/u/nlp/data/gale/segtool/stanford-seg/test/ctb.sighan.train.utf8.dict
SIGHAN2003_TEST_GOLD=/u/nlp/data/chinese-segmenter/Sighan2005/dev/ctb-testref.txt.utf8
PK_TRAIN_DICT=/u/nlp/data/chinese-segmenter/Sighan2005/train/pku-training.txt.utf8.dict
PK_TEST_GOLD=/u/nlp/data/chinese-segmenter/Sighan2005/dev/pk-testref.txt.utf8 
CTB5_MINUS_SIGHAN2003_TRAIN=/u/nlp/data/chinese-segmenter/gale2007/ctb5minusSighan2003/ctb5minusSighan2003forTrain.utf8
CTB5_MINUS_SIGHAN2003_TRAIN_DICT=/u/nlp/data/chinese-segmenter/gale2007/ctb5minusSighan2003/ctb5minusSighan2003forTrain.utf8.dict

DICT_1024=/u/nlp/data/chinese-dictionaries/plain/adso-1.25-050405-monolingual-clean.utf8.txt,/u/nlp/data/chinese-dictionaries/plain/lexicon_108k_normalized.txt,/u/nlp/data/chinese-dictionaries/plain/lexicon_mandarintools_normalized.txt,/u/nlp/data/chinese-dictionaries/plain/harbin-ChineseNames_utf8.txt,/u/nlp/data/chinese-dictionaries/plain/lexicon_HowNet_normalized.txt,/u/nlp/data/chinese-dictionaries/plain/wikilex-20070908-zh-en.txt

SIGHAN2006_CORPORADICT=/u/nlp/data/chinese-segmenter/gale2007/ctb6minusSighan2006

DICT_CHRIS5=/u/nlp/data/chinese-dictionaries/plain/ne_wikipedia-utf8.txt,/u/nlp/data/chinese-dictionaries/plain/newsexplorer_entities_utf8.txt,/u/nlp/data/chinese-dictionaries/plain/Ch-name-list-utf8.txt,/u/nlp/data/chinese-dictionaries/plain/wikilex-20070908-zh-en.txt,/u/nlp/data/chinese-dictionaries/plain/adso-1.25-050405-monolingual-clean.utf8.txt,/u/nlp/data/chinese-dictionaries/plain/lexicon_108k_normalized.txt,/u/nlp/data/chinese-dictionaries/plain/lexicon_mandarintools_normalized.txt,/u/nlp/data/chinese-dictionaries/plain/harbin-ChineseNames_utf8.txt,/u/nlp/data/chinese-dictionaries/plain/lexicon_HowNet_normalized.txt

# Same as for chris5, currently
DICT_CHRIS6=/u/nlp/data/chinese-dictionaries/plain/ne_wikipedia-utf8.txt,/u/nlp/data/chinese-dictionaries/plain/newsexplorer_entities_utf8.txt,/u/nlp/data/chinese-dictionaries/plain/Ch-name-list-utf8.txt,/u/nlp/data/chinese-dictionaries/plain/wikilex-20070908-zh-en.txt,/u/nlp/data/chinese-dictionaries/plain/adso-1.25-050405-monolingual-clean.utf8.txt,/u/nlp/data/chinese-dictionaries/plain/lexicon_108k_normalized.txt,/u/nlp/data/chinese-dictionaries/plain/lexicon_mandarintools_normalized.txt,/u/nlp/data/chinese-dictionaries/plain/harbin-ChineseNames_utf8.txt,/u/nlp/data/chinese-dictionaries/plain/lexicon_HowNet_normalized.txt

CTB6_PROCESSED=/u/nlp/data/gale/segtool/stanford-seg/data/ctb6.all.processed

CTB6_NOTEST_PROCESSED=/u/nlp/data/gale/segtool/stanford-seg/data/ctb6.notest.processed

CTB7_ALL=/u/nlp/data/chinese/ctb7/seg/ctb7-seg-with-extra.txt

CTB7_TRAIN=/u/nlp/data/chinese/ctb7/seg/ctb7-seg.train.txt

# Special prerelease segmentation data from Bolt.  Do not release publicly!
BOLT=/u/nlp/data/chinese/bolt/combined-seg.txt


dict-chris6.ser.gz:
	time java -mx15g edu.stanford.nlp.wordseg.ChineseDictionary -output $@


# train and test on Sighan 2006 data. No serialized model will be produced
# Revision: 20267..
ctb6.chris6.lex.result: dict-chris6.ser.gz
	# train & test
	time java6 -mx7g edu.stanford.nlp.ie.crf.CRFClassifier -prop $(DIR)/sighan2006-chris6.prop -sighanCorporaDict $(SIGHAN2006_CORPORADICT) -serDictionary $+ -serializeTo sighan2006-chris6.lex.gz -serializeToText sighan2006-chris6.lex.text.gz > sighan2006-chris6.lex.log 2> sighan2006-chris6.lex.err
	# eval
	tail -5117 $(DIR)/05202008-sighan2006-chris6.lex.log > $(DIR)/05202008-sighan2006-chris6.lex.out
	$(SCORE) /u/nlp/data/gale/segtool/stanford-seg/props/sighan2006-train.dict /u/nlp/data/gale/segtool/stanford-seg/data/Sighan2006/CTB_gold/CTB.utf8.simp.gold $(DIR)/05202008-sighan2006-chris6.lex.out > $(DIR)/$@

# train on all CTB6, with all external lexicons, without training lexicon
ctb6.chris6.ser.gz: dict-chris6.ser.gz
	time java6 -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop $(DIR)/ctb6-chris6.prop -serDictionary $+ -sighanCorporaDict /u/nlp/data/chinese-segmenter/gale2007/ctb6/ -trainFile $(CTB6_PROCESSED) -serializeTo $@ > ctb6.chris6.lex.log 2> ctb6.chris6.lex.err

# train on all CTB6, with all external lexicons, without training lexicon
ctb6.notest.chris6.ser.gz: dict-chris6.ser.gz
	time java6 -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop $(DIR)/ctb6-chris6.prop -serDictionary $+ -sighanCorporaDict /u/nlp/data/chinese-segmenter/gale2007/ctb6/ -trainFile $(CTB6_NOTEST_PROCESSED) -serializeTo $@ > ctb6.notest.chris6.lex.log 2> ctb6.notest.chris6.lex.err

# train on all CTB7, with all external lexicons, without training lexicon
ctb7.chris6.ser.gz: dict-chris6.ser.gz
	time java -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop $(DIR)/ctb6-chris6.prop -serDictionary $+ -sighanCorporaDict /u/nlp/data/chinese-segmenter/gale2007/ctb6/ -trainFile $(CTB7_ALL) -serializeTo $@ > $@.log 2> $@.err

# train on train CTB7, with all external lexicons, without training lexicon
ctb7.train.chris6.ser.gz: dict-chris6.ser.gz
	time java -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop $(DIR)/ctb6-chris6.prop -serDictionary $+ -sighanCorporaDict /u/nlp/data/chinese-segmenter/gale2007/ctb6/ -trainFile $(CTB7_TRAIN) -serializeTo $@ > $@.log 2> $@.err

# train on all CTB7, with all external lexicons, without training lexicon
bolt.chris6.ser.gz: dict-chris6.ser.gz
	time java -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop $(DIR)/ctb6-chris6.prop -serDictionary $+ -sighanCorporaDict /u/nlp/data/chinese-segmenter/gale2007/ctb6/ -trainFile $(BOLT) -serializeTo $@ > $@.log 2> $@.err

