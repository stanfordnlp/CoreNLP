---
title: Understanding memory and time usage
keywords: memory time
permalink: '/memory-time.html'
---

## Introduction to memory and time usage

People not infrequently complain that Stanford CoreNLP is slow or takes a ton of memory. In some configurations this is true. In other configurations, this is not true. This section tries to help you understand what you can or can’t do about speed and memory usage. The advice applies regardless of whether you are running CoreNLP from the command-line, from the Java API, from the web service, or from other languages. We show command-line examples here, but the principles are true of all ways of invoking CoreNLP. You will just need to pass in the appropriate properties in different ways.
For these examples we will work with [chapter 13 of _Ulysses_ by James Joyce]({{ site.github.url }}/assets/James-Joyce-Ulysses-ch13.txt). You can download it if you want to follow along.


How slow and memory intensive CoreNLP is depends on the annotators _you_ choose. This is the first rule. In practice many people who have issues are just running CoreNLP out of the box with its default annotators. Not making any explicit choices about annotators or parameters is itself a choice. This page helps you make these choices wisely. Of course, sometimes the choices that are fast and memory efficient aren’t the choices that produce the highest quality annotations. Sometimes you have to make trade-offs.

## CoreNLP doesn’t need much time or space

CoreNLP doesn’t need to use much time or space. It can just tokenize and sentence split text using very little time and space.
It can do this on the sample text while giving Java just 20MB of memory:

```bash
java -mx20m -cp "$STANFORD_CORENLP_HOME/*" edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit -file James-Joyce-Ulysses-ch13.txt -outputFormat text
```

CoreNLP will probably report a speed around 50,000–100,000 tokens a second for running this command. (That’s actually well under its actual speed for doing just these two operations – the text isn’t long enough for the code to be warmed up, and I/O costs, etc. dominate. Likely its real speed on your computer is well over 200,000 tokens a second in this configuration.)

Of course, old timers might complain: “Wait a minute! That file is only 100K and you’re telling me it needs 20MB of heap to process. That’s a 20 times blow-out right there.” It’s true. In that sense, CoreNLP _does_ take a lot of memory. Where is the 20MB going? Well, the big tables for the finite automaton tokenizer – an annotator model – take up about half of it. The rest goes in the usual Java ways. Strings are internally memory-expensive in Java. Each token is represented as an Object, which stores various token attributes, such as token offsets, which are themselves represented as Objects. It all just uses plenty of memory.

A whole document is represented in memory while processing it. Therefore, if you have a large file, like a novel, the first secret to reducing memory usage is to **process a large file a piece, say a chapter, at a time, not all at once**.

The main other thing to know is that **CoreNLP will be slow and take a lot of memory if and only if you choose annotators and annotation options that are slow and use a lot of memory**.

## Avoid creating lots of pipelines

If you’re using lots of annotators, CoreNLP can easily spend 10–40 seconds just loading an annotation pipeline. Pipeline loading time can easily dominate actual annotation time. So, if you load a new pipeline frequently, such as for every sentence, then CoreNLP will be painfully slow. You should load an annotation pipleline – what you get when you call `new StanfordCoreNLP(props)` in code – as infrequently as possible. Often, **you can and should just load _one_ pipeline and use it for everything**. You only need to use multiple pipelines if you simultaneously need different configurations, such as working with multiple human languages or doing processing with different options or annotators.

So, even if at the command-line, if you have a thousand paragraph-long files named `para1.txt`, `para2.txt`, … then you will get much faster processing by doing this:

```bash
ls -1 para*.txt > all-files.txt
java -cp "$STANFORD_CORENLP_HOME/*" edu.stanford.nlp.pipeline.StanfordCoreNLP -filelist all-files.txt -outputFormat json
```

than doing this:

```bash
java -cp "$STANFORD_CORENLP_HOME/*" edu.stanford.nlp.pipeline.StanfordCoreNLP -file para1.txt -outputFormat json
java -cp "$STANFORD_CORENLP_HOME/*" edu.stanford.nlp.pipeline.StanfordCoreNLP -file para2.txt -outputFormat json
...
java -cp "$STANFORD_CORENLP_HOME/*" edu.stanford.nlp.pipeline.StanfordCoreNLP -file para1000.txt -outputFormat json
```

## Don’t run annotators that you don’t need

Many people run Stanford CoreNLP with its default annotators, by just using a simple command-line like:

```bash
java -cp "$STANFORD_CORENLP_HOME/*" edu.stanford.nlp.pipeline.StanfordCoreNLP -file James-Joyce-Ulysses-ch13.txt -outputFormat json
```

However, the default runs a lot of annotators, some of them very expensive. This is a great command if you want to have your text parsed and coreference run on it. However, if really the only things that you are going to use are parts of speech and named entities, you can get your processing done orders of magnitude more quickly by turning off expensive annotators like parsing and coreference. 

If you run the above command in CoreNLP v.3.7.0 or later, your annotation speed is probably about 200 tokens per second. That’s 3 orders of magnitude slower than just tokenizing and sentence splitting, but actually this is the new good news for this version. We’ve changed the default annotator pipeline to make things _faster_.

Because of different default annotator choices, if you you try to process this file with the default annotation pipeline from earlier releases of CoreNLP v.3, most likely, it will just fail from lack of memory or your patience will run out before it finishes. In v.3.6, you need more than 20GB of RAM to run the default model with default options on this text. The default statistical coreference in v.3.6 was just too slow to run fully on large documents like this! 

But, in v.3.6, even if we turned off coreference altogether and ran with:

```bash
java -cp "$STANFORD_CORENLP_v360_HOME/*" edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators "tokenize,ssplit,pos,lemma,ner,parse" -file James-Joyce-Ulysses-ch13.txt -outputFormat json
```

then the annotation speed was still only about 100 tokens per second, because the default parsing model of earlier versions (`englishPCFG.ser.gz`) was also very slow.

Returning to v.3.7.0 and continuing with turning annotators off, if all you need are parts of speech and named entities, you should run a pipeline like this:

```bash
java -cp "$STANFORD_CORENLP_HOME/*" edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators "tokenize,ssplit,pos,lemma,ner" -file James-Joyce-Ulysses-ch13.txt -outputFormat json
```

and the annotation speed is about 1300 tokens per second – about 6 times faster. If really you only need 3 class PERSON, LOCATION, ORGANIZATION NER, then you could turn off more stuff like this:

```bash
java -cp "$STANFORD_CORENLP_HOME/*" edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit,pos,ner -ner.model edu/stanford/nlp/models/ner/english.all.3class.distsim.crf.ser.gz -ner.useSUTime false -ner.applyNumericClassifiers false -file James-Joyce-Ulysses-ch13.txt -outputFormat json
```

and the annotation speed is about 6500 tokens per second – another 5 times faster. **Limiting the number of annotators run can improve speed by orders of magnitude.**

## Where does all the memory go?

There are three big places that memory goes:

1. The large machine learning models (mainly arrays and maps of Strings for features and floats or doubles for parameters) which are stored in memory
2. Large linguistic analysis data structures in memory.
3. The annotated document that is stored in memory.

For 1., the only thing you can do is to either remove annotators that you do not need or to make choices for smaller annotators.  These models are what fills the large models jar. They are even larger when they are uncompressed and represented in memory. Here are some examples.

Currently, the most memory-requiring models in the default pipeline are the neural network or statistical coreference. The shift-reduce constituency parser also has very large models. If you run without them, you can annotate the sample document in 2GB of RAM:

```bash
java -mx2g -cp "$STANFORD_CORENLP_HOME/*" edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators "tokenize,ssplit,pos,lemma,ner,depparse" -file James-Joyce-Ulysses-ch13.txt -outputFormat text
```

But once you include coreference – here, we’ve explicitly listed the annotators, but these are the default options – then the system really needs 4GB of RAM for this document (and if you run constituency parsing and coreference on a large document, you can easily need 5–6GB of RAM).

```bash
java -mx4g -cp "$STANFORD_CORENLP_HOME/*" edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators "tokenize,ssplit,pos,lemma,ner,depparse,mention,coref" -file James-Joyce-Ulysses-ch13.txt -outputFormat text
```

In the other direction, if you turn off parsing as well, then 1GB of RAM is fine:

```bash
java -mx1g -cp "$STANFORD_CORENLP_HOME/*" edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators "tokenize,ssplit,pos,lemma,ner" -file James-Joyce-Ulysses-ch13.txt -outputFormat text
```

For 2., the classic problem case is parsing long sentences with dynamic programmed parsers like the traditional `englishPCFG.ser.gz` constituency parsing. This takes space proportional to the square of the longest sentence length, with a large constant factor. Parsing sentences that are hundreds of words long will take additional gigabytes of memory just for the parser tables. The easiest fix for that is just to not parse super-long sentences. You can do that with a property like: `-parse.maxlen 70`. This can be a fine solution for something like web pages or newswire, where anything over 70 words is likely a table or list or something that isn’t a real sentence. However, it is unappealing for James Joyce: Several of the sentences in Chapter 13 are over 100 words but are well-formed, proper sentences. For example, here is one of the longer sentences in the chapter:

> Her griddlecakes done to a goldenbrown hue and
> queen Ann’s pudding of delightful creaminess had won golden opinions
> from all because she had a lucky hand also for lighting a fire, dredge
> in the fine selfraising flour and always stir in the same direction,
> then cream the milk and sugar and whisk well the white of eggs though
> she didn’t like the eating part when there were any people that made her
> shy and often she wondered why you couldn’t eat something poetical like
> violets or roses and they would have a beautifully appointed drawingroom
> with pictures and engravings and the photograph of grandpapa Giltrap’s
> lovely dog Garryowen that almost talked it was so human and chintz
> covers for the chairs and that silver toastrack in Clery’s summer
> jumble sales like they have in rich houses.

A better way to lessen memory use is to use a different parser. The shift-reduce constitutency parse is much faster, usually more accurate, and uses much less memory for parse structures (though it does have much bigger machine learning models). You can invoke it with `-parse.model edu/stanford/nlp/models/srparser/englishSR.ser.gz`, or as appropriate for the language you are parsing.

Nevertheless, in general, very long sentences blow out processing time and memory. One thing to be aware of is that CoreNLP currently uses simple, heuristic sentence splitting on sentence terminators like ‘.’ and ‘?’. If you are parsing “noisy” text without explicit sentence breaks – this often happens if you parse things like tables or web pages – you can end up with “sentences” more than 500 words long, which it isn’t even useful to try to parse. You should either clean these up manually or limit the sentence length that annotators try to process.

For 3., you should avoid having documents that are two large. Don’t try to parse a whole novel as one CoreNLP document. Parse each chapter as a separate document.

## Where does all the time go?

The slowest annotators are coreference and parsing. Many coreference methods are especially sensitive to the total document length, since they are quadratic or cubic in the number of mentions in the document. The parsing annotators, particularly dynamic-programming constituency parsing, is especially sensitive to maximum sentence length. Your processing will be much faster if you either leave out these annotators or choose options that make them as fast as possible. In v.3.7.0, the fastest, most memory-efficient models are the default: neural network dependency parsing followed by statistical coreference. In earlier versions, you should choose non-default options to maximize speed and memory efficiency. The most time and memory efficient options are neural network dependency parsing followed by statistical coreference if you only need dependency parses, or shift-reduce constituency parsing followed by deterministic coreference if you do need constituency parses.

The graph below is for an older version of CoreNLP (v.3.5.0) on an aging computer, but is maybe nevertheless of interest to give some idea of how the speed of the system varies by orders of magnitude depending on the annotations run.

![Speed Graph]({{ site.github.url }}/images/StanfordCoreNlpSpeed.png)

## Notes for particular annotators

### pos

The POS tagger does support a `pos.maxlen` flag, but this should rarely be needed, since the POS tagger uses memory and time linearly with sentence length. The default `english-left3words-distsim.tagger` is much faster than the `english-bidirectional-distsim.tagger`.

### ner

The rule-based SUTime and tokensregex NER is actually considerably slower than the statistical CRF NER. If you don’t need it, you can turn it off with `-ner.useSUTime false -ner.applyNumericClassifiers false`.

### parse

Until v.3.6.0, the default parser was `englishPCFG.ser.gz`. It was small and quick to load, but takes quadratic space and cubic time with sentence length. Bad news! If you have long sentences, you should either limit the maximum length parsed with a flag like `-parse.maxlen 70` or choose a different parser.

The shift-reduce constituency parser takes space and time linear in sentence length. It still supports `parse.maxlen`, though. If you only need dependency parses, you can get even faster and more memory efficient parsing by using the `depparse` annotator instead.

### depparse

The dependency parser (`depparse`) annotator is faster and uses less space than even the shift-reduce constituency parser. It should be your tool of choice for parsing large amounts of data, unless you need constituency parses, of course. It does not at present support any options to limit parsing time or sentence length.

### coref

If you are working using dependency parses, the fastest choice is to use the fast statistical coreference model, which uses only dependency parse features. If you’re already committed to constituency parsing, the fastest choice for coreference is deterministic coreference (dcoref), but it’s the least accurate.  Neural-english coref is a reasonable choice for higher quality coreference. Several of the coreference models have some properties that will speed up their application to long documents:

- `dcoref` has `dcoref.maxdist` with value the maximum number of tokens back to look for a coreferent mention.
- Both statistical and neural `coref` have `coref.maxMentionDistance` and `coref.maxMentionDistanceWithStringMatch` which provide two variants of the same kind of limiting how far back you look for coreferent mentions. 
