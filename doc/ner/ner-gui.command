#!/bin/sh
java -mx500m -cp `dirname $0`/stanford-ner.jar edu.stanford.nlp.ie.crf.NERGUI
