Stanford POS Tagger, v3.2.0 - 2013-06-19
Copyright (c) 2002-2012 The Board of Trustees of
The Leland Stanford Junior University. All Rights Reserved.

This document contains (some) information about the models included in
this release and that may be downloaded for the POS tagger website at
http://nlp.stanford.edu/software/tagger.shtml .  If you have downloaded
the full tagger, all of the models mentioned in this document are in the
downloaded package in the same directory as this readme.  Otherwise,
included in the download are two 
English taggers, and the other taggers may be downloaded from the
website.  All taggers are accompanied by the props files used to create
them; please examine these files for more detailed information about the
creation of the taggers.

For English, the bidirectional taggers are slightly more accurate, but
tag much more slowly; choose the appropriate tagger based on your
speed/performance needs.

English taggers
---------------------------
wsj-0-18-bidirectional-distsim.tagger
Trained on WSJ sections 0-18 using a bidirectional architecture and
including word shape and distributional similarity features.
Penn Treebank tagset.
Performance:
97.28% correct on WSJ 19-21
(90.46% correct on unknown words)

wsj-0-18-left3words.tagger
Trained on WSJ sections 0-18 using the left3words architecture and
includes word shape features.  Penn tagset.
Performance:
96.97% correct on WSJ 19-21
(88.85% correct on unknown words)

wsj-0-18-left3words-distsim.tagger
Trained on WSJ sections 0-18 using the left3words architecture and
includes word shape and distributional similarity features. Penn tagset.
Performance:
97.01% correct on WSJ 19-21
(89.81% correct on unknown words)

english-left3words-distsim.tagger
Trained on WSJ sections 0-18 and extra parser training data using the
left3words architecture and includes word shape and distributional
similarity features. Penn tagset.

english-bidirectional-distsim.tagger
Trained on WSJ sections 0-18 using a bidirectional architecture and
including word shape and distributional similarity features.
Penn Treebank tagset.

wsj-0-18-caseless-left3words-distsim.tagger
Trained on WSJ sections 0-18 left3words architecture and includes word
shape and distributional similarity features. Penn tagset.  Ignores case.

english-caseless-left3words-distsim.tagger
Trained on WSJ sections 0-18 and extra parser training data using the
left3words architecture and includes word shape and distributional
similarity features. Penn tagset.  Ignores case.


Chinese tagger
---------------------------
chinese-nodistsim.tagger
Trained on a combination of CTB7 texts from Chinese and Hong Kong
sources.
LDC Chinese Treebank POS tag set.
Performance:
93.46% on a combination of Chinese and Hong Kong texts
(79.40% on unknown words)

chinese-distsim.tagger
Trained on a combination of CTB7 texts from Chinese and Hong Kong
sources with distributional similarity clusters.
LDC Chinese Treebank POS tag set.
Performance:
93.99% on a combination of Chinese and Hong Kong texts
(84.60% on unknown words)

Arabic tagger
---------------------------
arabic.tagger
Trained on the *entire* ATB p1-3.
When trained on the train part of the ATB p1-3 split done for the 2005
JHU Summer Workshop (Diab split), using (augmented) Bies tags, it gets
the following performance:
96.26% on test portion according to Diab split
(80.14% on unknown words)

French tagger
---------------------------
french.tagger
Trained on the French treebank.

German tagger
---------------------------
german-hgc.tagger
Trained on the first 80% of the Negra corpus, which uses the STTS tagset.
The Stuttgart-Tübingen Tagset (STTS) is a set of 54 tags for annotating
German text corpora with part-of-speech labels, which was jointly
developed by the Institut für maschinelle Sprachverarbeitung of the
University of Stuttgart and the Seminar für Sprachwissenschaft of the
University of Tübingen. See: 
http://www.ims.uni-stuttgart.de/projekte/CQPDemos/Bundestag/help-tagset.html
This model uses features from the distributional similarity clusters
built over the HGC.
Performance:
96.90% on the first half of the remaining 20% of the Negra corpus (dev set)
(90.33% on unknown words)

german-dewac.tagger
This model uses features from the distributional similarity clusters
built from the deWac web corpus.

german-fast.tagger
Lacks distributional similarity features, but is several times faster
than the other alternatives.
Performance:
96.61% overall / 86.72% unknown.
