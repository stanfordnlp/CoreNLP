---
layout: page
title: POS Tagger FAQ
keywords: pos, tagger
permalink: '/tools_pos_tagger_faq.html'
nav_order: 19
toc: true
parent: Additional Tools
---

### What is the tag set used by the Stanford Tagger?

You can train models for the Stanford POS Tagger with any tag set. For the
models we distribute, the tag set depends on the language, reflecting the
underlying treebanks that models have been built from. That is, the tag set
was wholly or mainly decided by the treebank producers not us). Here are
relevant links:

    * English: the [Penn Treebank site](http://www.cis.upenn.edu/~treebank/). There is an [online copy of its documentation](http://catalog.ldc.upenn.edu/docs/LDC99T42/); in particular, see TAGGUID1.PDF (POS tagging guide). There are also other simpler listings such as the [AMALGAM project page](http://www.comp.leeds.ac.uk/amalgam/tagsets/upenn.html). 
    * Chinese: the [Penn Chinese Treebank](http://www.cis.upenn.edu/~chinese/).
    * German: the [TIGER](http://www.ims.uni-stuttgart.de/forschung/ressourcen/korpora/tiger.en.html) and [NEGRA](http://www.coli.uni-saarland.de/projects/sfb378/negra-corpus/) corpora use [the Stuttgart-Tübingen Tag Set (STTS)](http://www.ims.uni-stuttgart.de/forschung/ressourcen/lexika/TagSets/stts-table.html). [[More info](http://www.sfs.uni-tuebingen.de/Elwis/stts/stts.html)]. However, we use the TIGER variant of STTS. For an accurate list of its tags and how it differs from other versions of STTS, see the appendices of [A Brief Introduction to the TIGER Treebank](http://www.ims.uni-stuttgart.de/forschung/ressourcen/korpora/TIGERCorpus/annotation/tiger_introduction.pdf). [This page](https://www.linguistik.hu-berlin.de/de/institut/professuren/korpuslinguistik/mitarbeiter-innen/hagen/STTS_Tagset_Tiger) summarizes the TIGER version, with examples.
    * French: the [French Treebank](http://www.llf.cnrs.fr/Gens/Abeille/French-Treebank-fr.php)

Please read the documentation for each of these corpora to learn about their
tagsets. You can often also find additional documentation resources by doing
web searches.

### How do I use the API?

A brief demo program included with the download will demonstrate how to load
the tool and start processing text. When using this demo program, be sure to
include all of the appropriate jar files in the classpath.

### Why do I get `Exception in thread "main" java.lang.NoClassDefFoundError:edu/stanford/nlp/tagger/maxent/MaxentTagger`?

This means your Java CLASSPATH isn't set correctly, so the tagger (in
stanford-tagger.jar) isn't being found. See the examples in the `README.txt`
file for how to set the classpath with the `-cp` or `-classpath` option. See,
for example,
[`http://en.wikipedia.org/wiki/Classpath_(Java)`](http://en.wikipedia.org/wiki/Classpath_\(Java\))
for general discussion of the Java classpath.

### How can I lemmatize (reduce to a base, dictionary form) words that have been tagged with the POS tagger?

For English (only), you can do this using the included `Morphology` class. You
can do it with the flag `-outputFormatOptions lemmatize`. For instance:

> ` $ java -cp "*" edu.stanford.nlp.tagger.maxent.MaxentTagger -model
> edu/stanford/nlp/models/pos-tagger/english-left3words/english-left3words-
> distsim.tagger -textFile samsawme.txt -outputFormat inlineXML
> -outputFormatOptions lemmatize  
>  <?xml version="1.0" encoding="UTF-8"?>  
>  <pos>  
>  <sentence id="0">  
>  <word wid="0" pos="NNP" lemma="Sam">Sam</word>  
>  <word wid="1" pos="VBD" lemma="see">saw</word>  
>  <word wid="2" pos="PRP" lemma="I">me</word>  
>  <word wid="3" pos="." lemma=".">.</word>  
>  </sentence>  
>  </pos> `

### How do I tag pre-tokenized and/or one-sentence per line text?

You can tag already tokenized text, with one pre-tokenized sentence per line
with the flags:

> `-sentenceDelimiter newline -tokenize false`

You can tag already tokenized text with the flag:

> `-tokenize false`

You can tag one sentence per line text with the flag:

> `-sentenceDelimiter newline`

### How can I achieve a single jar file deployment of the POS tagger?

You can insert one or more tagger models into the jar file and give options to
load a model from there. Here are detailed instructions.

    1. Start in the home directory of the unpacked tagger download
    2. Make a copy of the jar file, into which we'll insert a tagger model: 

> `cp stanford-postagger.jar stanford-postagger-withModel.jar `

    3. Put the model on a path for inclusion in the jar file: 

> ` mkdir -p edu/stanford/nlp/models/pos-tagger/english-left3words  
>  cp models/english-left3words-distsim.tagger edu/stanford/nlp/models/pos-
> tagger/english-left3words `

    4. Insert one or more models into the jar file - we usually do it under `edu/stanford/nlp/models/`. 

> `jar -uf stanford-postagger-withModel.jar edu/stanford/nlp/models/pos-
> tagger/english-left3words/english-left3words-distsim.tagger `

    5. You can now specify loading this model by loading it directly from the classpath. 

> ` java -mx300m -cp stanford-postagger-withModel.jar
> edu.stanford.nlp.tagger.maxent.MaxentTagger -model
> edu/stanford/nlp/models/pos-tagger/english-left3words/english-left3words-
> distsim.tagger -textFile sample-input.txt `

  7. Or, in code, you can similarly load the tagger like this: 

> ` MaxentTagger tagger = new MaxentTagger("edu/stanford/nlp/models/pos-
> tagger/english-left3words/english-left3words-distsim.tagger"); `

The tagger will load paths in the CLASSPATH in preference to those on the file
system.

### Can I run the tagger as a server?

Yes! (This was added in version 2.0.) We provide MaxentTaggerServer as a
simple example of a socket-based server using the POS tagger. With a bit of
work, we're sure you can adapt this example to work in a REST, SOAP, AJAX, or
whatever system. If not, pay us a lot of money, and we'll work it out for you.

If you're doing this, you may also be interested in single jar deployment.
We'll use a continuation of the answer to the previous question in our example
(but the two features are independent). The commands shown are for a
Unix/Linux/Mac OS X system. For Windows, you reverse the slashes, etc. You
start the server on some host by specifying a model and a port for it to run
on:

> ` java -mx300m -cp stanford-postagger-withModel.jar
> edu.stanford.nlp.tagger.maxent.MaxentTaggerServer -model
> edu/stanford/nlp/models/pos-tagger/english-left3words/english-left3words-
> distsim.tagger -port 2020 & `

The same class then includes a demonstration client, which you'll want to
adapt to your own needs. You can invoke it like this:

> ` $ java -cp stanford-postagger.jar
> edu.stanford.nlp.tagger.maxent.MaxentTaggerServer -client -host
> nlp.stanford.edu -port 2020  
>  Input some text and press RETURN to POS tag it, or just RETURN to finish.  
>  I hope this'll show the server working.  
>  I_PRP hope_VBP this_DT 'll_MD show_VB the_DT server_NN working_VBG ._. `

If you're running the server and client on the same machine, then you can omit
the `-host` argument. You can provide other `MaxentTagger` options to the
_server_ invocation of `MaxentTaggerServer`, such as `-outputFormat tsv`, as
needed.

### Why am I running out of memory, in general?

If you run the tagger without changing how much memory you give to Java, you
may run out of memory. This will be evident when the program terminates with
an OutOfMemoryError.

Running from the command line, you need to supply a flag like `-mx1g`. The
number 1g is just an example; if you do not have that much memory available,
use less so your computer doesn't start paging. For running a tagger,
`-mx500m` should be plenty; for training a complex tagger, you may need more
memory.

When running from within Eclipse, follow [these
instructions](http://stackoverflow.com/questions/4175188/setting-memory-of-
java-programs-that-runs-from-eclipse) to increase the memory given to a
program being run from inside Eclipse. Increasing the amount of memory given
to Eclipse itself won't help.

Note also that the method `tagger.tokenizeText(reader)` will tokenize all the
text in a reader, and put it in memory. This is okay for reasonable-size
files. However, if you have huge files, this can consume an unbounded amount
of memory. You will need to adopt an alternate strategy where you only
tokenize part of the text at a time (e.g., perhaps a paragraph at a time).

### What different output formats are available?

The output tagged text can be produced in several styles. The tags can be
separated from the words by a character, which you can specify (this is the
default, with an underscore as the separator), or you can get two tab-
separated columns (good for spreadsheets or the Unix `cut` command), or you
can get ouptput in XML. An example of each option appears below:

>
>     $ cat > short.txt
>     This is a short sentence.
>     So is this.
>     $ java -cp stanford-postagger.jar
> edu.stanford.nlp.tagger.maxent.MaxentTagger -model models/left3words-
> wsj-0-18.tagger -textFile short.txt -outputFormat slashTags 2> /dev/null
>     This_DT is_VBZ a_DT short_JJ sentence_NN ._.
>     So_RB is_VBZ this_DT ._.
>     $ java -cp stanford-postagger.jar
> edu.stanford.nlp.tagger.maxent.MaxentTagger -model models/left3words-
> wsj-0-18.tagger -textFile short.txt -outputFormat slashTags -tagSeparator \#
> 2> /dev/null
>     This#DT is#VBZ a#DT short#JJ sentence#NN .#.
>     So#RB is#VBZ this#DT .#.
>     $ java -cp stanford-postagger.jar
> edu.stanford.nlp.tagger.maxent.MaxentTagger -model models/left3words-
> wsj-0-18.tagger -textFile short.txt -outputFormat tsv 2> /dev/null
>     This      DT
>     is        VBZ
>     a DT
>     short     JJ
>     sentence  NN
>     . .
>  
>     So        RB
>     is        VBZ
>     this      DT
>     . .
>  
>     $ java -cp stanford-postagger.jar
> edu.stanford.nlp.tagger.maxent.MaxentTagger -model models/left3words-
> wsj-0-18.tagger -textFile short.txt -outputFormat xml 2> /dev/null
>     <sentence id="0">
>       <word wid="0" pos="DT">This</word>
>       <word wid="1" pos="VBZ">is</word>
>       <word wid="2" pos="DT">a</word>
>       <word wid="3" pos="JJ">short</word>
>       <word wid="4" pos="NN">sentence</word>
>       <word wid="5" pos=".">.</word>
>     </sentence>
>     <sentence id="1">
>       <word wid="0" pos="RB">So</word>
>       <word wid="1" pos="VBZ">is</word>
>       <word wid="2" pos="DT">this</word>
>       <word wid="3" pos=".">.</word>
>     </sentence>
>  

### Is your tagger slow?

**No!** Most people who think that the tagger is slow have made the mistake of
running it with the model `wsj-0-18-bidirectional-distsim.tagger`. That model
_is fairly slow_. Essentially, that model is trying to pull out all stops to
maximize tagger accuracy. Speed consequently suffers due to choices like using
4th order bidirectional tag conditioning.

In applications, we nearly always use the `english-left3words-distsim.tagger`
model, and we suggest you do too. It's nearly as accurate (96.97% accuracy vs.
97.32% on [the standard WSJ22-24 test
set](http://aclweb.org/aclwiki/index.php?title=POS_Tagging_\(State_of_the_art\)))
and is an order of magnitude faster. Comparing apples-to-apples, the Stanford
POS tagger isn't slow. For example, the `wsj-0-18-left3words-distsim.tagger`
model is directly comparable to the quite well known MXPOST tagger by Adwait
Ratnaparkhi (both use a second order conditioning model and maximum entropy
classifiers; both are trained on about the same amount of data; both are in
Java). Compared to MXPOST, the Stanford POS Tagger with this model is **both**
_more accurate_ and _considerably faster_. Want a number? It all depends, but
on a 2008 nothing-special Intel server, it tags about 15000 words per second.
This is also about 4 times faster than [Tsuruoka's C++ tagger](http://www-
tsujii.is.s.u-tokyo.ac.jp/~tsuruoka/postagger/) which has an accuracy in
between our left3words and bidirectional-distsim models. The [LTAG-
spinal](http://www.cis.upenn.edu/~xtag/spinal/) POS tagger, another recent
Java POS tagger, is minutely more accurate than our best model (97.33%
accuracy) but it is over 3 times slower than our best model (and hence over 30
times slower than the `wsj-0-18-bidirectional-distsim.tagger` model).

However, if speed is your paramount concern, you might want something still
faster. This can be done by using a cheaper conditioning model class (you can
get another 50% speed up in the Stanford POS tagger, with still little
accuracy loss), using some other classifier type (an HMM-based tagger is just
going to be faster than a discriminative, feature-based model like our maxent
tagger), or doing more code optimization (probably more to be done here, but
the current state is not so bad).

Some people also use [the Stanford
Parser](http://nlp.stanford.edu/software/lex-parser.html) as just a POS
tagger. It's a quite accurate POS tagger, and so this is okay if you don't
care about speed. But, if you do, it's _not_ a good idea. Use the Stanford POS
tagger.

### How do I train a tagger?

You need to start with a `.props` file which contains options for the tagger
to use. The `.props` files we used to create the sample taggers are included
in the models directory; you can start from whichever one seems closest to the
language you want to tag. For example, to train a new English tagger, start
with the left3words tagger props file. To train a tagger for a western
language other than English, you can consider the props files for the German
or the French taggers, which are included in the full distribution. For
languages using a different character set, you can start from the Chinese or
Arabic props files. Or you can use the `-genprops` option to MaxentTagger, and
it will write a sample properties file, with documentation, for you to modify.
It writes it to stdout, so you'll want to save it to some file by redirecting
output (usually with >). The # at the start of the line makes things a
comment, so you'll want to delete the # before properties you wish to specify.

In these props files, there are two parameters you absolutely have to change.
The first is the `model` parameter, which specifies the file which the trained
model is output to (that is, it is created during the tagger training
process). The other is the `trainFile` parameter, which specifies the file to
load the training data from (data that you must provide). So you might have
something like:

> ` model = icelandic.tagger  
>  trainFile = tagged-icelandic.tsv `

You can specify input files in a few different formats. This is part of the
`trainFile` property. To learn more about the formats you can use and what
other the options mean, look at [the javadoc for
`MaxentTagger`](http://nlp.stanford.edu/nlp/javadoc/javanlp/edu/stanford/nlp/tagger/maxent/MaxentTagger.html).

In its most basic format, the training data is sentences of tagged text. The
words should be tagged by having the word and the tag separated by the
tagSeparator parameter. For example, if the tagSeparator is `_`, one of your
training lines might look like

`An_DT avocet_NN is_VBZ a_DT small_JJ ,_, cute_JJ bird_NN ._.`

There are other options available for training files. For example, you can use
tab separated blocks, where each line represents a word/tag pair and sentences
are separated by blank lines. You can also specify PTB-format trees, where the
tags are extracted from the bottom layer of the tree.

If you are training a tagger for a language other than the language used in
the properties file, you also need to change the language setting. Certain
languages have preset definitions, such as English, Chinese, French, German,
and Arabic. For all others, you need to clear the `lang` field and then set
either `openClassTags` or `closedClassTags`. Alternatively, you can make code
changes to `edu.stanford.nlp.tagger.maxent.TTags` to implement defaults for
your new language.

You may want to experiment with other feature architectures for your tagger.
This is the "arch" property. Look at the javadoc for ExtractorFrames and
ExtractorFramesRare to learn what other arch options exist. You might want to
start with a basic tagger with the options
`arch=words(-1,1),unicodeshapes(-1,1),order(2),suffix(4)`. This will create a
tagger with features predicting the current tag from each of the previous,
current and next words (words(-1,1)), features from each of those words
represented in terms of the unicode character classes they contain
(unicodeshapes(-1,1)), bigram and trigram tag sequence features that predict
the current tag from the previous one or two tags (order(2)), and additional
features for trying to predict the tag of rare or unknown words from the last
1, 2, 3, and 4 characters of the word (suffix(4)). Finally, you need to
specify an optimization method with the `search` property. We build many of
our taggers with the `owlqn` optimizer, but we don't distribute that. Good
choices which you can use are the basically equivalent `owlqn2` optimizer or
`qn`. (If using `qn`, set `sigmaSquared` L2 regularization to a non-zero
value, such as 1.0.) You can find the commands for training and testing in
[the `MaxentTagger` class
javadoc](http://nlp.stanford.edu/nlp/javadoc/javanlp/edu/stanford/nlp/tagger/maxent/MaxentTagger.html).

### What model should I use?

If you are tagging English, you should almost certainly choose the model
`english-left3words-distsim.tagger`. Included in the distribution is a file,
`README-Models.txt`, which describes all of the available models. For English,
there are models trained on WSJ PTB, which are useful for the purposes of
academic comparisons. There are also models titled "english" which are trained
on WSJ with additional training data, which are more useful for general
purpose text. There are models for other languages, as well, such as Chinese,
Arabic, etc.

### What is the difference between "english" and "wsj"?

The models with "english" in the name are trained on additional text
corresponding to the same data the ["english" parser
models](http://nlp.stanford.edu/software/parser-faq.html#z) are trained on,
with the exception of instead using WSJ 0-18.

### What are the distsim clusters used by the tagger?

These clusters are a feature extracted from larger, untagged text which
clusters the words into similar classes.

[Here are the clusters currently used for English.](egw4-reut.512.clusters)

### Why does it crash when I try to optimize with search=owlqn? Is owlqn available anywhere?

Unfortunately, we do not have a license to redistribute owlqn. This causes it
to crash if you base your training file off a .props file that used owlqn
internally. We do distribute our own experimental L1-regularized optimizer,
though, which you can use with the option

> `search=owlqn2`

or you can use a different optimizer, such as the L2-regularized L-BFGS
optimizer

> `search=qn`

### How do I output the results to a file?

Use the shell redirect > filename

### How do I fix the Stanford POS Tagger giving a `NoSuchMethodError` or `NoSuchFieldError`, or complaining that it cannot find files with extensions like `tagger.dict`, `tagger.tags` or `tagger.ex`?

If you see an Exception stacktrace message like:

>
>     Exception in thread "main" java.lang.NoSuchFieldError:
> featureFactoryArgs
>         at
> edu.stanford.nlp.ie.AbstractSequenceClassifier.(AbstractSequenceClassifier.java:127)
>         at edu.stanford.nlp.ie.crf.CRFClassifier.(CRFClassifier.java:173)

or

>
>     Exception in thread "main" java.lang.NoSuchMethodError:
> edu.stanford.nlp.tagger.maxent.TaggerConfig.getTaggerDataInputStream(Ljava/lang/String;)Ljava/io/DataInputStream;

or

>
>     Caused by: java.lang.NoSuchMethodError:
> edu.stanford.nlp.util.Generics.newHashMap()Ljava/util/Map;
>         at edu.stanford.nlp.pipeline.AnnotatorPool.(AnnotatorPool.java:27)
>         at
> edu.stanford.nlp.pipeline.StanfordCoreNLP.getDefaultAnnotatorPool(StanfordCoreNLP.java:305)

or you have errors in model loading that look like this (the filename may be
different but note the telltale file extensions):

>
>     edu.stanford.nlp.io.RuntimeIOException:
>     english-left3words-distsim.tagger.dict (The system cannot find the file
> specified)
>     english-left3words-distsim.tagger.tags (The system cannot find the file
> specified)
>     english-left3words-distsim.tagger.ex (The system cannot find the file
> specified)
>  

then this _isn't_ caused by the shiny new Stanford NLP tools that you've just
downloaded. It is because you also have _old_ versions of one or more Stanford
NLP tools on your classpath.

The straightforward case is if you have an older version of a Stanford NLP
tool. For example, you may still have a version of Stanford NER on your
classpath that was released in 2009. In this case, you should upgrade, or at
least use matching versions. For any releases from 2011 on, just use tools
released at the same time -- such as the most recent version of everything :)
-- and they will all be compatible and play nicely together.

The tricky case of this is when people distribute jar files that hide other
people's classes inside them. People think this will make it easy for users,
since they can distribute one jar that has everything you need, but, in
practice, as soon as people are building applications using multiple
components, this results in a particular bad form of _jar hell_. People just
shouldn't do this. The only way to check that other jar files do not contain
conflicting versions of Stanford tools is to look at what is inside them (for
example, with the `jar -tf` command).

In practice, if you're having the `NoSuchMethod` or `NoSuchField` problems,
the most common cause (in 2013-2014) is that you have `ark-tweet-nlp` on your
classpath. The jar file in their github download hides _old_ versions of
_many_ other people's jar files, including Apache commons-codec (v1.4),
commons-lang, commons-math, commons-io, Lucene; Twitter commons; Google Guava
(v10); Jackson; Berkeley NLP code; Percy Liang's fig; GNU trove; _and_ an
outdated version of the Stanford POS tagger (from 2011). You should complain
to them for creating you and us grief. But you can then fix the problem by
using [their jar file from Maven
Central](http://search.maven.org/#browse%7C964929444). It doesn't have all
those other libraries stuffed inside.

Alternatively, if your having it fail to load files with the `.tagger.dict`,
`.tagger.tags` or `.tagger.ex` extensions, the most common cause (in
2013-2014) is that you have (Drexel's) The Dragon Toolkit (from 2008!) on your
classpath. This again contains an (even older) version of the Stanford POS
tagger. You should probably have moved on to something that has been updated
this decade.

You can discuss other topics with [Stanford POS Tagger](tagger.html)
developers and users by [joining the `java-nlp-user` mailing
list](https://mailman.stanford.edu/mailman/listinfo/java-nlp-user) (via a
webpage). Or you can send other questions and feedback to [`java-nlp-
support@lists.stanford.edu`](mailto:java-nlp-support@lists.stanford.edu).