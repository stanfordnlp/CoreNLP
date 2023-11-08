---
layout: page
title: Chinese and Arabic Segmenter FAQ
keywords: crf, segmenter
permalink: '/tools_segmenter_faq.html'
nav_order: 17
toc: true
parent: Additional Tools
---

## Questions with answers

### How can I retrain the Chinese Segmenter?

In general you need four things in order to retrain the Chinese Segmenter. You
will need a data set with segmented text, a dictionary with words that the
segmenter should know about, and various small data files for other feature
generators.

The most important thing you need is a data file with text segmented according
to the standard you want to use. For example, for the CTB model we
distribution, which follows the [Penn Chinese
Treebank](http://www.cis.upenn.edu/~chinese/) segmentation standard, we use
the [Chinese Treebank 7.0](http://catalog.ldc.upenn.edu/LDC2010T07) data set.

You will need to convert your data set to text in the following format:  

    
    中国 进出口 银行 与 中国 银行 加强 合作
    新华社 北京 十二月 二十六日 电 （ 记者 周根良 ）
    ...
    

Each individual sentence is on its own line, and spaces are used to denote
word breaks.

Some data sets will come in the format of Penn trees. There are various ways
to convert this to segmented text; one way which uses our tool suite is to use
the `Treebanks` tool:

    
        java edu.stanford.nlp.trees.Treebanks -words ctb7.mrg
    

The `Treebanks` tool is not included in the segmenter download, but it is
available in the [corenlp](corenlp.html) download.

Another useful tool is a dictionary of known words. This should include named
entities such as people, places, companies, etc. which the model might segment
as a single word. This is not actually required, but it will help identity
named entities which the segmenter has not seen before. For example, our file
of named entities includes names such as

    
    吳毅成
    吳浩康
    吳淑珍
    ...
    

To build a dictionary usable by our model, you want to collect lists of words
and then use the `ChineseDictionary` tool to combine them into one serialized
dictionary.

    
        java edu.stanford.nlp.wordseg.ChineseDictionary -inputDicts names.txt,places.txt,words.txt,... -output dict.ser.gz
    

If you want to use our existing dictionary as a starting point, you can
include it as one of the filenames. Words have a maximum lexicon length
(probably 6, see the `ChineseDictionary` source) and words longer than that
will be truncated. There is also handling of words with a "mid dot" character;
this occasionally shows up in machine translation, and if a word with a mid
dot shows up in the dictionary, we accept the word either with or without the
dot.

You will also need a properties file which tells the classifier which features
to use. An example properties file is included in the `data` directory of the
segmenter download.

Finally, some of the features used by the existing models require additional
data files, which are included in the `data/dict` directory of the segmenter
download. To figure out which files correspond to which features, please
search in the source code for the appropriate filename. You can probably just
reuse the existing data files.

Once you have all of these components, you can then train a new model with a
command line such as

    
          java -mx15g edu.stanford.nlp.ie.crf.CRFClassifier -prop ctb.prop -serDictionary dict-chris6.ser.gz -sighanCorporaDict data -trainFile train.txt -serializeTo newmodel.ser.gz > newmodel.log 2> newmodel.err
    

It really does take a lot of memory to train a new classifier. The [NER
FAQ](http://nlp.stanford.edu/software/crf-faq.html#d) presents some tips for
using less memory, if needed.

### How can I add words to the dictionary?

You can add new words to the existing segmentation model without retraining
the entire thing. The tool
[edu.stanford.nlp.wordseg.ChineseDictionary](https://nlp.stanford.edu/nlp/javadoc/javanlp/edu/stanford/nlp/wordseg/ChineseDictionary.html)
has the functionality to add a new file to the existing dictionary (dict-
chris6.ser.gz, for example). Please see the linked javadoc or the source code
itself for more information.

Another easier way is to simply use multiple dictionary files at once. The
`-serDictionary` flag takes a comma separated list of filenames, and it can
also process `.txt` files. Any additional words which should go in the
dictionary can be added to a supplementary file, one word per line. When using
the segmenter as part of CoreNLP, the property to use is
`-segment.serDictionary` (Make sure to include the original value from the
properties file.)

