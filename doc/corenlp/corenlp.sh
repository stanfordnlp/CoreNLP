#!/usr/bin/env bash
#
# Runs Stanford CoreNLP.
# Simple uses for xml and plain text output to files are:
#    ./corenlp.sh -file filename
#    ./corenlp.sh -file filename -outputFormat text 

scriptpath=$(readlink -e "$0")
scriptdir=$(dirname "$scriptpath")

echo java -mx3g -cp \"$scriptdir/*\" edu.stanford.nlp.pipeline.StanfordCoreNLP $*
java -mx3g -cp "$scriptdir/*" edu.stanford.nlp.pipeline.StanfordCoreNLP $*
