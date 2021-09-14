PYTHON = python3
EVAL_SCRIPT = /home/john/stanza/stanza/utils/conll18_ud_eval.py

HU_TRAINING = /home/john/extern_data/ud2/ud-treebanks-v2.8/UD_Hungarian-Szeged/hu_szeged-ud-train.conllu

HU_TEST_INPUT = /home/john/extern_data/ud2/ud-treebanks-v2.8/UD_Hungarian-Szeged/hu_szeged-ud-test.txt
HU_TEST_GOLD = /home/john/extern_data/ud2/ud-treebanks-v2.8/UD_Hungarian-Szeged/hu_szeged-ud-test.conllu

.SECONDEXPANSION:

all: hungarian
.PHONY: all hungarian

hungarian: hu-tokenizer.ser.gz

hu-tokenizer.ser.gz:
	@echo Training $@
	java edu.stanford.nlp.process.stattok.StatTokSentTrainer -trainFile $(HU_TRAINING) -serializeTo $@
	java edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators cdc_tokenize -cdc_tokenize.model $@ -file $(HU_TEST_INPUT) -outputFormat conllu -output.printFakeDeps True -outputDirectory /tmp/$@.out
	$(PYTHON) $(EVAL_SCRIPT) -v $(HU_TEST_GOLD) /tmp/$@.out/$(notdir $(HU_TEST_INPUT)).conllu
