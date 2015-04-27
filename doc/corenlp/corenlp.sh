#!/usr/bin/env bash
#
# Runs Stanford CoreNLP.
# Simple uses for xml and plain text output to files are:
#    ./corenlp.sh -file filename
#    ./corenlp.sh -file filename -outputFormat text 
# You can also start a simple shell where you can enter sentences to be processed:
#    ./corenlp.sh

OS=`uname`
# Macs (BSD) don't support readlink -e
if [ "$OS" == "Darwin" ]; then
  scriptdir=`dirname $0`
else
  scriptpath=$(readlink -e "$0") || scriptpath=$0
  scriptdir=$(dirname "$scriptpath")
fi

echo java -mx3g -cp \"$scriptdir/*\" edu.stanford.nlp.pipeline.StanfordCoreNLP $*
java -mx3g -cp "$scriptdir/*" edu.stanford.nlp.pipeline.StanfordCoreNLP $*
