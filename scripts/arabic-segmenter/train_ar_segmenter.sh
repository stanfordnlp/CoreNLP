#!/usr/bin/env bash
#
# Training/eval script for edu.stanford.nlp.international.process.ArabicSegmenter.
#
# For l1 regularization, add the parameters: "-useOWLQN -priorLambda 0.05"
# 

if [ "$#" -lt 2 ]; then
    echo "Usage: `basename $0` <data_file> <output_model_file> <other options...>"
    exit 2
fi

DATAFILE="$1"
MODELFILE="$2"
shift
shift

MEM=12g
OPTS="-server -XX:+UseCompressedOops -Xmx$MEM -Xms$MEM -XX:MaxPermSize=2g"

java $OPTS edu.stanford.nlp.international.arabic.process.ArabicSegmenter -trainFile "$DATAFILE" -serializeTo "$MODELFILE" $@

