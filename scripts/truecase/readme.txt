train.4_5M.truecase.txt is 4.5M sentences extracted from wikipedia
train.1_5M.truecase.txt is 1.5M sentences extracted from wikipedia (a subset)
test.truecase.txt is 500K sentences for test

truecasing.4_5M.fast.qn.ser.gz   is a model
truecasing.4_5M.fast.caseless.qn.ser.gz is another model, but works on caseless text

1.5M...ser.gz are smaller models if needed

MixDisambiguation.list came from... somewhere.  
Most likely it was generated with
projects/core/src/edu/stanford/nlp/truecaser/MixDisambiguation.java
but the question then is, what data was used?

the fast models mean they have been retrained after pruning (a CRF training feature)
the full model does not have this feature and is huge

prop files, makefile, readme, etc are in git


train.orig.txt is a copy of /scr/nlp/data/gale/NIST09/truecaser/crf/noUN.input
test.orig.txt is a copy of /scr/nlp/data/gale/AE-MT-eval-data/mt06/cased/ref0



WikiExtractor tool from

https://github.com/attardi/wikiextractor

Wikipedia data at:

/u/scr/nlp/data/Wikipedia/enwiki-20190920-pages-articles-multistream.xml.bz2




Steps to rebuild the truecase data:

1) run wikiextractor on the data

nohup python3 wikiextractor/WikiExtractor.py wiki/enwiki-20190920-pages-articles-multistream.xml > text.out 2>&1 &

2) run pick_text to extract a certain # of text, such as 2M sentences.
This script has some decent defaults so you should be able to just run it.

python3 pick_text.py

3) tokenize the text.  Suggestion:

nohup java -mx90g edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit -file ~/truecase/wiki.raw.txt -ssplit.newlineIsSentenceBreak always -outputFormat tagged > wiki.tokenized.txt

If you don't have that much memory lying around, you can first split
the file into 2 or more pieces


To rebuild the models:

use a machine with huge RAM
run make


