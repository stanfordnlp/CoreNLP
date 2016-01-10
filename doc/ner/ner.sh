#!/bin/sh
scriptdir=`dirname $0`

java -mx700m -cp "$scriptdir/stanford-ner.jar:$scriptdir/lib/*" edu.stanford.nlp.ie.crf.CRFClassifier -loadClassifier $scriptdir/classifiers/english.all.3class.distsim.crf.ser.gz -textFile $1
