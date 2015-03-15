#!/bin/env ruby

MIN_SCORE=87.7
OUT="/tmp/out"

test=`bash -c 'java -mx2g edu.stanford.nlp.parser.lexparser.LexicalizedParser -maxLength 40 -goodPCFG -evals factLB=false,factTA=false -train /scr/nlp/data/Treebank3/parsed/mrg/wsj 200-2199 -testTreebank /scr/nlp/data/Treebank3/parsed/mrg/wsj/22 2200-2219 > /dev/null 2> #{OUT}'`
eval=`bash -c 'tail -40 #{OUT}'`
eval =~ /^pcfg LP\/LR summary evalb.*F1: ([\d.]+).*N: 393$/ or raise "Can't parse output"
fmeasure = $1.to_f

if fmeasure >= MIN_SCORE
  puts "PASS pcfg LP/LR F1 score #{fmeasure} >= min #{MIN_SCORE}"
else
  puts "FAIL pcfg LP/LR F1 score #{fmeasure} < min #{MIN_SCORE}"
end
