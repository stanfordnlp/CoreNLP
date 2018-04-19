
JAVANLP_HOME=/u/scr/mihais/code/javanlp
export CLASSPATH=$JAVANLP_HOME/projects/mt/classes:$JAVANLP_HOME/projects/core/classes:$JAVANLP_HOME/projects/core/lib/fastutil.jar
DIR=/u/scr/nlp/data/ACE2005/english_test/bn

for i in `find $DIR -name "*.sgm"`
do
	echo $i
	cat $i | java -ea -Xmx5g edu.stanford.nlp.ie.machinereading.domains.ace.reader.NewLineOnlyOnEOS > $i.truecase.eosalign
        cat $i.truecase.eosalign | sh ../mt/scripts/en_crf_truecaser > $i.truecase.orig
        java -ea -Xmx5g edu.stanford.nlp.ie.machinereading.domains.ace.reader.RealignTrueCasedFile $i.truecase.eosalign $i.truecase.orig > $i.truecase
done
