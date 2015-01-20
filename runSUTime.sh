#!/bin/bash

# $1 inputfile or inputdir
# $2 outputfile or outputdir
# $3 timeAnnotator type : sutime, heideltime, gutime

echo "Need three parameter"
version=3.5.0

if [ $# -eq 3 ]; then
    java -Dpos.model=edu/stanford/nlp/models/pos-tagger/english-left3words/english-left3words-distsim.tagger -cp stanford-corenlp-$version.jar:stanford-corenlp-$version-models.jar:lib/xom.jar:lib/joda-time.jar:lib/jollyday.jar -Xmx3g edu.stanford.nlp.time.SUTimeMain -in.type TEMPEVAL3  -i $1 -o $2 -timeAnnotator $3 -heideltime.path ~/git/CoreNLP/packages/heideltime -gutime.path ~/git/CoreNLP/packages/gutime
fi


