#!/bin/sh
scriptdir=`dirname $0`

java -mx100m -cp "$scriptdir/stanford-tregex.jar:" edu.stanford.nlp.trees.tregex.TregexPattern "$@"
