#!/bin/sh
java -mx500m -cp `dirname $0`/stanford-ner.jar:`dirname $0`/lib/* edu.stanford.nlp.ie.crf.NERGUI
