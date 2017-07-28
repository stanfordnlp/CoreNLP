#!/bin/bash
FILE_TO_EVAL=$1
# Run perl eval script on given file
/u/nlp/data/ner/conll/conlleval -r < $FILE_TO_EVAL
