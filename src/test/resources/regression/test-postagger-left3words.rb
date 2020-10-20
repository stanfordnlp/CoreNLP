#!/bin/env ruby

# Unknown words (863) right: 788 (91.309386%); wrong: 75 (8.690614%).

TRAIN_FILE="/u/nlp/data/pos-tagger/train-wsj-0-18"
TRAIN_LINES=5000

TEST_FILE="/u/nlp/data/pos-tagger/test-wsj-19-21"
TEST_LINES=300

MIN_SCORE=96.07

train=`bash -c 'java edu.stanford.nlp.tagger.maxent.Train -arch left3words -file <(head -#{TRAIN_LINES} #{TRAIN_FILE}) -model goat'`
test=`bash -c 'java edu.stanford.nlp.tagger.maxent.Test -arch left3words -file <(head -#{TEST_LINES} #{TEST_FILE}) -model goat'`

test =~ /Total tags right: \d+ \((.+?)\)/ or raise "can't parse output"
score = $1.to_f

if score >= MIN_SCORE
  puts "PASS score #{score} >= min #{MIN_SCORE}"
else
  puts "FAIL score #{score} < min #{MIN_SCORE}"
end

