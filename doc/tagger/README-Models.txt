Stanford POS Tagger, v4.2.0 - 2020-11-17
Copyright (c) 2002-2020 The Board of Trustees of
The Leland Stanford Junior University. All Rights Reserved.

This document contains (some) information about the models included in
this release and that may be downloaded for the POS tagger website at
http://nlp.stanford.edu/software/tagger.html . All of the models mentioned 
in this document are in the downloaded package in the same directory as this 
readme. All taggers are accompanied by the props files used to create
them; please examine these files for more detailed information about the
creation of the taggers.

For English, the bidirectional taggers are slightly more accurate, but
tag much more slowly; choose the appropriate tagger based on your
speed/performance needs.

English taggers
---------------------------
english-left3words-distsim.tagger
Trained on WSJ sections 0-18 and extra parser training data using the
left3words architecture and includes word shape and distributional
similarity features. Penn tagset. UDv2.0 tokenization standard.

english-bidirectional-distsim.tagger
Trained on WSJ sections 0-18 using a bidirectional architecture and
including word shape and distributional similarity features.
Penn Treebank tagset. UDv2.0 tokenization standard.

english-caseless-left3words-distsim.tagger
Trained on WSJ sections 0-18 and extra parser training data using the
left3words architecture and includes word shape and distributional
similarity features. Penn tagset. Ignores case. UDv2.0 tokenization
standard.


Chinese tagger
---------------------------
chinese-nodistsim.tagger
Trained on a combination of CTB7 texts from Chinese and Hong Kong
sources.
LDC Chinese Treebank POS tag set.

chinese-distsim.tagger
Trained on a combination of CTB7 texts from Chinese and Hong Kong
sources with distributional similarity clusters.
LDC Chinese Treebank POS tag set.

Arabic tagger
---------------------------
arabic.tagger
Trained on the *entire* ATB p1-3.
When trained on the train part of the ATB p1-3 split done for the 2005
JHU Summer Workshop (Diab split), using (augmented) Bies tags, it gets

French tagger
---------------------------
french-ud.tagger
Trained on the French GSD (UDv2.2) data set

German tagger
---------------------------
german-ud.tagger
Trained on the German GSD (UDv2.2) data set

Spanish tagger
--------------------------
spanish-ud.tagger
Trained on the Spanish AnCora (UDv2.0) data set
