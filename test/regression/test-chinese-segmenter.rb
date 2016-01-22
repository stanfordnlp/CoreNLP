#!/bin/env ruby

PROP_FILE="/u/nlp/data/chinese-segmenter/prop/rtest.prop"
TEST_PROP="/u/nlp/data/chinese-segmenter/prop/rtest.test.prop"
TEST_FILE="/u/nlp/data/chinese-segmenter/Sighan2005/official_test/pku_test.utf8"
GOLD_TEST="/u/nlp/data/chinese-segmenter/Sighan2005/official_result/all/pku/pku_test_gold.utf8"
OUT="/tmp/out"
# TODO: make sure this is the right number ==> seems too low now. 
# check the sighan number
MIN_SCORE=0.949

train=`bash -c 'java -mx4g edu.stanford.nlp.ie.crf.CRFClassifier -prop #{PROP_FILE}'`
test=`bash -c 'java -mx4g edu.stanford.nlp.ie.crf.CRFClassifier -prop #{TEST_PROP} -testFile #{TEST_FILE} > #{OUT}'`
# TODO: need to find the correct lexicon..
eval=`bash -c '/u/nlp/data/chinese-segmenter/Sighan2005/eval/score.pl /scr/htseng/gale/seg/corpus/train.lexicon #{GOLD_TEST} #{OUT}'`

eval =~ /=== F MEASURE:\s*([\d\.]+)/ or raise "can't parse output"
fmeasure = $1.to_f

if fmeasure >= MIN_SCORE
  puts "PASS score #{fmeasure} >= min #{MIN_SCORE}"
else
  puts "FAIL score #{fmeasure} < min #{MIN_SCORE}"
end
