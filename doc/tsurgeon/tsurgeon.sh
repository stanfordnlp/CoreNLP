#!/bin/sh

export CLASSPATH=stanford-tregex.jar:$CLASSPATH
java -mx100m edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon "$@"
