#!/usr/bin/env bash
#
# Training/eval script for edu.stanford.nlp.international.process.ArabicSegmenter.
#
# For l1 regularization, add the parameter: "-l1reg 1.0"
# 

MEM=6g
OPTS="-server -XX:+UseCompressedOops -Xmx$MEM -Xms$MEM -XX:MaxPermSize=2g"

java $OPTS edu.stanford.nlp.international.arabic.process.ArabicSegmenter -trainFile $1 -testFile $2 -serializeTo $3

