#!/usr/bin/env sh

DATA=/u/nlp/data/lexparser/test/
CLASS="edu.stanford.nlp.process.DocumentPreprocessingTester"
TMP=/tmp/dpt-out.$$

cd $DATA

java $CLASS > $TMP

if [ `grep -v PASSED $TMP | wc -l` -eq 0 ] ; then
  STATUS=PASS ;
else
  STATUS=FAIL ;
fi ;

echo $CLASS
echo $STATUS

rm $TMP

