---
title: Arabic CoreNLP
keywords: arabic
permalink: '/arabic.html'
---

## Introduction

Arabic is the largest member of the Semitic language family and varieties of Arabic are spoken by nearly 500 million people worldwide. It is one of the six official UN languages. Despite its cultural, religious, and political significance, Arabic has received comparatively little attention in modern computational linguistics. The Arabic CoreNLP models provide state-of-the-art NLP performance on several language processing tasks on Modern Standard Arabic.

## Input to CoreNLP

Input to Arabic CoreNLP  is expected to be standard Arabic text. Most likely, your text will be encoded in UTF-8. If not, you will either need to first convert it to UTF-8 (try programs such as `iconv`) or else to use the `-encoding charset` property when calling CoreNLP. Here is a sample piece of text that we can work with:

حفظ القرآن الكريم وهو دون العاشرة، وقد التحق بالأزهر الشريف حتى تخرج من الثانوية وكان ترتيبه الثاني على مملكة مصر  حينما كانت تخضع للحكم الملكي ثم التحق الشيخ بكلية أصول الدين بجامعة الأزهر ومنها حصل على العالمية سنة 1953 وكان ترتيبه الأول بين زملائه وعددهم مائة وثمانون طالبًا. حصل على العالمية مع إجازة التدريس من كلية اللغة العربية سنة 1954م وكان ترتيبه الأول بين زملائه من خريجي الكليات الثلاث بالأزهر ، وعددهم خمسمائة. حصل يوسف القرضاوي على دبلوم معهد الدراسات العربية العالية في اللغة والأدب في سنة 1958، لاحقا في سنة 1960 حصل على الدراسة التمهيدية العليا المعادلة للماجستير في شعبة علوم القرآن والسنة من كلية أصول الدين، وفي سنة 1973 م حصل على (الدكتوراة) بامتياز مع مرتبة الشرف الأولى من نفس الكلية، وكان موضوع الرسالة عن "الزكاة وأثرها في حل المشاكل الاجتماعية".

It is from the beginning of [the Arabic Wikipedia page on Yousef Al-Qaradawi](https://ar.wikipedia.org/wiki/%D9%8A%D9%88%D8%B3%D9%81_%D8%A7%D9%84%D9%82%D8%B1%D8%B6%D8%A7%D9%88%D9%8A).  If you want to follow along, you could copy it (when moving your mouse, remember that Arabic text is written right to left!) and put it in a file `arabic.txt`.

## Word segmenter

If you have a terminal window and have cd'ed to the root directory of your CoreNLP download and you have also downloaded the Arabic models jar and you have made the above file arabic.txt, then the following command should work to tokenize the Arabic text:

```
java -cp "$CLASSPATH:*" edu.stanford.nlp.pipeline.StanfordCoreNLP -properties StanfordCoreNLP-arabic.properties -annotators tokenize,ssplit -file arabic.txt -outputFormat CoNLL -output.columns word
```

The output will appear in the file `arabic.txt.conll`.

If by some chance you're trying to read this page even though your Arabic is a little weak, we include a separate utility that can transcode Arabic between standard Arabic script and the [Buckwalter transliteration](https://en.wikipedia.org/wiki/Buckwalter_transliteration), a lossless transcoding that has been used quite a bit in Western computational linguistics circles.  You can convert the input and output files with these two commands:

```
java edu.stanford.nlp.international.arabic.Buckwalter -u2b arabic.txt > arabic.txt.buckwalter
java edu.stanford.nlp.international.arabic.Buckwalter -u2b arabic.txt.conll > arabic.txt.conll.buckwalter
```

What has the segmenter done? Most of what it does is fairly standard stuff: It separates words at whitespace and makes punctuation like commas their own token. But the Arabic word segmenter does a bit more than this. Arabic has a number of _clitics_ which are written attached to other words but syntactically are their own words, acting as pronouns, conjunctions, and prepositions. The CoreNLP segmenter segments following the standard of the LDC Arabic Treebank (ATB). Their decision was to separate off as separate words all Arabic clitics _except_ the definite article clitic ال (Al-). The first example of this you see in our example file is the fourth word, where  وهو (whw) is split into two tokens و هو (w hw), the first of which is the coordinating conjunction particle.

However, the Arabic Word Segmenter does a little more than this by default. There is some variation in the use of diacritics on the letter alif, and by default we remove all diacritics from alif. Secondly, sometimes morphophonological changes occur when clitics join with words, and we attempt to undo these.

Stanford Arabic Word Segmenter - Apply ATB clitic segmentation and orthographic normalization to raw Arabic text. 

The segmenter is based on a conditional random fields (CRF) sequence classifier so segmentation is fast. Various of the normalizations, including the ones mentioned above, can be controlled by using the property `orthoOptions`, but this needs to be done at the time a segmenter is _trained_. If you are interesting in training different models, you'll need to separately download the Stanford Segmenter package and to read the README.txt for that.

## Part of Speech Tagger

Stanford Arabic Part of Speech Tagger - The full distribution comes with a model trained on the ATB.

## Parser

Stanford Arabic Parser - The full distribution includes a model trained on the most recent releases of the first three parts of the Penn Arabic Treebank (ATB). These corpora contain newswire text. Arabic-specific parsing instructions, a FAQ, and a recommended train/dev/test split of the ATB are also available. The parser expects segmented text as input. If you want to parse raw text, then you must pre-process it with the Stanford Arabic Word Segmenter.

## Tregex

Tregex/TregexGUI - A regular expression package for parse trees. Useful for browsing and searching the ATB. Supports Unicode (UTF-8) input and display.

## Relevant papers

Spence Green and Christopher D. Manning. 2010. 
[Better Arabic Parsing: Baselines, Evaluations, and Analysis](http://nlp.stanford.edu/pubs/coling2010-arabic.pdf). In _COLING_.

Will Monroe, Spence Green, and Christopher D. Manning. 2014. 
[Word Segmentation of Informal Arabic with Domain Adaptation](http://www.spencegreen.com/pubs/monroe+green+manning.acl14.pdf). In _ACL_.
