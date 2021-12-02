PYTHON = python3
EVAL_SCRIPT = /u/horatio/stanza/stanza/utils/conll18_ud_eval.py

HU_TRAINING = /u/nlp/software/CoreNLP-models/hu/stattok/4.3.0/hu_szeged-ud-train.conllu

HU_TEST_INPUT = /u/nlp/software/CoreNLP-models/hu/stattok/4.3.0/hu_szeged-ud-test.txt
HU_TEST_GOLD = /u/nlp/software/CoreNLP-models/hu/stattok/4.3.0/hu_szeged-ud-test.conllu


# ignoring twittiro and postwita because this model gets thrown off
# quite a lot by the non-standard sentence endings
IT_TRAINING = /u/nlp/software/CoreNLP-models/it/stattok/4.3.1/it_isdt-ud-train.conllu,/u/nlp/software/CoreNLP-models/it/stattok/4.3.1/it_vit-ud-train.conllu,/u/nlp/software/CoreNLP-models/it/stattok/4.3.1/italian.mwt

IT_TEST_INPUT = /u/nlp/software/CoreNLP-models/it/stattok/4.3.1/it_isdt-ud-test.txt
IT_TEST_GOLD = /u/nlp/software/CoreNLP-models/it/stattok/4.3.1/it_isdt-ud-test.conllu

.SECONDEXPANSION:

all: hungarian italian
.PHONY: all hungarian italian

hungarian: hu-tokenizer.ser.gz

hu-tokenizer.ser.gz:
	@echo Training $@
	java edu.stanford.nlp.process.stattok.StatTokSentTrainer -trainFile $(HU_TRAINING) -serializeTo $@
	java edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators cdc_tokenize -cdc_tokenize.model $@ -file $(HU_TEST_INPUT) -outputFormat conllu -output.printFakeDeps True -outputDirectory /tmp/$@.out
	$(PYTHON) $(EVAL_SCRIPT) -v $(HU_TEST_GOLD) /tmp/$@.out/$(notdir $(HU_TEST_INPUT)).conllu

italian: it-tokenizer.ser.gz

it-multiword.txt:
	@echo Building $@
	java edu.stanford.nlp.process.stattok.BuildMultiWordRules -trainFile $(IT_TRAINING) -multiWordRulesFile $@

it-tokenizer.ser.gz: it-multiword.txt
	@echo Training $@
	java edu.stanford.nlp.process.stattok.StatTokSentTrainer -trainFile $(IT_TRAINING) -multiWordRulesFile $< -serializeTo $@
	java edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators cdc_tokenize -cdc_tokenize.model $@ -cdc_tokenize.multiWordRules $< -file $(IT_TEST_INPUT) -outputFormat conllu -output.printFakeDeps True -outputDirectory /tmp/$@.out
	$(PYTHON) $(EVAL_SCRIPT) -v $(IT_TEST_GOLD) /tmp/$@.out/$(notdir $(IT_TEST_INPUT)).conllu
