#!/bin/sh
scriptdir=`dirname $0`

java -mx700m -cp "$scriptdir/stanford-ner.jar:lib/*" edu.stanford.nlp.ie.crf.NERGUI
