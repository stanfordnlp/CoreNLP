#!/usr/bin/env bash
#
# Runs Stanford CoreNLP.
# Simple uses for xml and plain text output to files are:
#    ./corenlp.sh -file filename
#    ./corenlp.sh -file filename -outputFormat text 
# Split into sentences, run POS tagger and NER, write CoNLL-style TSV file:
#    ./corenlp.sh -annotators tokenize,ssplit,pos,lemma,ner -outputFormat conll -file input.txt
# You can also start a simple shell where you can enter sentences to be processed:
#    ./corenlp.sh

OS=`uname`
# Some machines (older OS X, BSD, Windows environments) don't support readlink -e
if hash readlink 2>/dev/null; then
  scriptdir=`dirname $0`
else
  scriptpath=$(readlink -e "$0") || scriptpath=$0
  scriptdir=$(dirname "$scriptpath")
fi

echo java -mx5g -cp \"$scriptdir/*\" edu.stanford.nlp.pipeline.StanfordCoreNLP $*
java -mx5g -cp "$scriptdir/*" edu.stanford.nlp.pipeline.StanfordCoreNLP $*
