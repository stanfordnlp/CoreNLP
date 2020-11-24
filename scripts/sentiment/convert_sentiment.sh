# Convert five class sentiment data into text files of the type you
# might use a sentence classifier for.

# The default directories are what I set up on my local machine.
# -i and -o change the input and output dirs.

INPUT_DIR=extern_data/sentiment/sentiment-treebank
OUTPUT_DIR=extern_data/sentiment/sst-processed

while getopts "i:o:" OPTION
do
  case $OPTION in 
  i)
    INPUT_DIR=$OPTARG	    
    ;;
  o)
    OUTPUT_DIR=$OPTARG	    
    ;;
  esac
done

  
echo INPUT DIR: $INPUT_DIR
echo OUTPUT DIR: $OUTPUT_DIR

mkdir -p $OUTPUT_DIR/binary
mkdir -p $OUTPUT_DIR/fiveclass
mkdir -p $OUTPUT_DIR/threeclass

echo $OUTPUT_DIR/fiveclass/train-phrases.txt
java edu.stanford.nlp.trees.OutputSubtrees -input $INPUT_DIR/fiveclass/train.txt > $OUTPUT_DIR/fiveclass/train-phrases.txt

echo $OUTPUT_DIR/fiveclass/dev-phrases.txt
java edu.stanford.nlp.trees.OutputSubtrees -input $INPUT_DIR/fiveclass/dev.txt > $OUTPUT_DIR/fiveclass/dev-phrases.txt

echo $OUTPUT_DIR/fiveclass/test-phrases.txt
java edu.stanford.nlp.trees.OutputSubtrees -input $INPUT_DIR/fiveclass/test.txt > $OUTPUT_DIR/fiveclass/test-phrases.txt


echo $OUTPUT_DIR/fiveclass/train-roots.txt
java edu.stanford.nlp.trees.OutputSubtrees -input $INPUT_DIR/fiveclass/train.txt -root_only > $OUTPUT_DIR/fiveclass/train-roots.txt

echo $OUTPUT_DIR/fiveclass/dev-roots.txt
java edu.stanford.nlp.trees.OutputSubtrees -input $INPUT_DIR/fiveclass/dev.txt  -root_only > $OUTPUT_DIR/fiveclass/dev-roots.txt

echo $OUTPUT_DIR/fiveclass/test-roots.txt
java edu.stanford.nlp.trees.OutputSubtrees -input $INPUT_DIR/fiveclass/test.txt -root_only > $OUTPUT_DIR/fiveclass/test-roots.txt


echo $OUTPUT_DIR/binary/train-binary-phrases.txt
java edu.stanford.nlp.trees.OutputSubtrees -input $INPUT_DIR/fiveclass/train.txt -ignore_labels 2 -remap_labels "1=0,2=-1,3=1,4=1" > $OUTPUT_DIR/binary/train-binary-phrases.txt

echo $OUTPUT_DIR/binary/dev-binary-phrases.txt
java edu.stanford.nlp.trees.OutputSubtrees -input $INPUT_DIR/fiveclass/dev.txt -ignore_labels 2 -remap_labels "1=0,2=-1,3=1,4=1" > $OUTPUT_DIR/binary/dev-binary-phrases.txt

echo $OUTPUT_DIR/binary/test-binary-phrases.txt
java edu.stanford.nlp.trees.OutputSubtrees -input $INPUT_DIR/fiveclass/test.txt -ignore_labels 2 -remap_labels "1=0,2=-1,3=1,4=1" > $OUTPUT_DIR/binary/test-binary-phrases.txt

echo $OUTPUT_DIR/binary/dev-binary-roots.txt
java edu.stanford.nlp.trees.OutputSubtrees -input $INPUT_DIR/fiveclass/dev.txt -root_only -ignore_labels 2 -remap_labels "1=0,2=-1,3=1,4=1" > $OUTPUT_DIR/binary/dev-binary-roots.txt

echo $OUTPUT_DIR/binary/test-binary-roots.txt
java edu.stanford.nlp.trees.OutputSubtrees -input $INPUT_DIR/fiveclass/test.txt -root_only -ignore_labels 2 -remap_labels "1=0,2=-1,3=1,4=1" > $OUTPUT_DIR/binary/test-binary-roots.txt



echo $OUTPUT_DIR/threeclass/train-threeclass-phrases.txt
java edu.stanford.nlp.trees.OutputSubtrees -input $INPUT_DIR/fiveclass/train.txt -remap_labels "0=0,1=0,2=1,3=2,4=2" > $OUTPUT_DIR/threeclass/train-threeclass-phrases.txt

echo $OUTPUT_DIR/threeclass/dev-threeclass-phrases.txt
java edu.stanford.nlp.trees.OutputSubtrees -input $INPUT_DIR/fiveclass/dev.txt -remap_labels "0=0,1=0,2=1,3=2,4=2" > $OUTPUT_DIR/threeclass/dev-threeclass-phrases.txt

echo $OUTPUT_DIR/threeclass/test-threeclass-phrases.txt
java edu.stanford.nlp.trees.OutputSubtrees -input $INPUT_DIR/fiveclass/test.txt -remap_labels "0=0,1=0,2=1,3=2,4=2" > $OUTPUT_DIR/threeclass/test-threeclass-phrases.txt

echo $OUTPUT_DIR/threeclass/dev-threeclass-roots.txt
java edu.stanford.nlp.trees.OutputSubtrees -input $INPUT_DIR/fiveclass/dev.txt -root_only -remap_labels "0=0,1=0,2=1,3=2,4=2" > $OUTPUT_DIR/threeclass/dev-threeclass-roots.txt

echo $OUTPUT_DIR/threeclass/test-threeclass-roots.txt
java edu.stanford.nlp.trees.OutputSubtrees -input $INPUT_DIR/fiveclass/test.txt -root_only -remap_labels "0=0,1=0,2=1,3=2,4=2" > $OUTPUT_DIR/threeclass/test-threeclass-roots.txt

