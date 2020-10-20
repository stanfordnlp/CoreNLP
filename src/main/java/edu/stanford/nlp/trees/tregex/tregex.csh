#!/bin/csh -f

if (! $?CLASSPATH) then
  setenv CLASSPATH
endif
setenv CLASSPATH tsurgeon.jar:$CLASSPATH
java -server -mx100m edu.stanford.nlp.trees.tregex.TregexPattern $*
