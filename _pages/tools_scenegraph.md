---
layout: page
title: Scenegraph Parser
keywords: scenegraph
permalink: '/tools_scenegraph.html'
nav_order: 11
toc: true
parent: Additional Tools
---

### About

Scene Graphs are a graph-based semantic representation of image contents. They
encode the objects in an image, their attributes and the relationships between
objects. This system takes a single-sentence image description and parses it
into a scene graph as described in the paper:

> Sebastian Schuster, Ranjay Krishna, Angel Chang, Li Fei-Fei, and Christopher
> D. Manning. 2015. [Generating Semantically Precise Scene Graphs from Textual
> Descriptions for Improved Image
> Retrieval](https://nlp.stanford.edu/pubs/schuster-krishna-chang-feifei-
> manning-vl15.pdf). In _Proceedings of the Fourth Workshop on Vision and
> Language (VL15)_. [[bib](https://nlp.stanford.edu/pubs/schuster-krishna-
> chang-feifei-manning-vl15.bib)]


The system requires Java 1.8+ to be installed.  The current version of SceneGraph is included in [Stanford CoreNLP](https://stanfordnlp.github.io/CoreNLP/).


The system is **licensed under the [GNU General Public License](https://www.gnu.org/licenses/gpl-2.0.html)** (v2 or later). Source is
included. The package includes components for command-line invocation, and a
Java API.

### Download

To run the code, you need the CoreNLP jar and the CoreNLP models jar as well
as the Scene Graph Parser jar in your classpath.

### Older versions

This version is updated to work with [CoreNLP 4.2.0](http://nlp.stanford.edu/software/stanford-corenlp-4.2.0.zip):
[Download the Scene Graph Parser](/projects/scenegraph/scenegraph-2.0.jar)
[0.2 MB]  



This is the original version, which works with [CoreNLP 3.6.0](http://nlp.stanford.edu/software/stanford-corenlp-full-2015-12-09.zip) [404 MB]  
[Download the Scene Graph Parser](/projects/scenegraph/scenegraph-1.0.jar)
[0.2 MB]  
The source code for the Scene Graph Parser is included in the jar file.  
  

### Usage

You can either run the parser programmatically or in interactive mode through
the command line.

To parse sentences interactively, put all the jar files from the CoreNLP
distribution and the Scene Graph Parser jar into one directory and then run
the following command from this directory.

    
    
    java -mx2g -cp "*" edu.stanford.nlp.scenegraph.RuleBasedParser
    

Alternatively, you can also run the parser programmatically as following.

    
    
    import edu.stanford.nlp.scenegraph.RuleBasedParser;
    import edu.stanford.nlp.scenegraph.SceneGraph;
    
    String sentence = "A brown fox chases a white rabbit.";
    
    RuleBasedParser parser = new RuleBasedParser();
    SceneGraph sg = parser.parse(sentence);
    
    //printing the scene graph in a readable format
    System.out.println(sg.toReadableString()); 
    
    //printing the scene graph in JSON form
    System.out.println(sg.toJSON()); 
    

  
  

### Questions

Please email [Sebastian Schuster](http://sebschu.com) if you have any
questions.

  

### Usage

Once downloaded, the code can be invoked either programmatically or through
the command line. The program can be invoked with the following command. This
will read lines from standard in, and produce relation triples in a tab
separated format: (confidence; subject; relation; object).

    
    
      java -mx1g -cp stanford-openie.jar:stanford-openie-models.jar edu.stanford.nlp.naturalli.OpenIE
    

To process files, simply pass them in as arguments to the program. For
example,

    
    
      java -mx1g -cp stanford-openie.jar:stanford-openie-models.jar edu.stanford.nlp.naturalli.OpenIE  /path/to/file1  /path/to/file2 
    

In addition, there are a number of flags you can set to tweak the behavior of
the program.

**Flag** | **Argument** | **Description**  
---|---|---  
-format | {reverb, ollie, default} | Change the output format of the program. Default will produce tab separated columns for confidence, the subject, relation, and the object of a relation. ReVerb will output a TSV in the [ ReVerb format](https://github.com/knowitall/reverb/blob/master/README.md#command-line-interface). Ollie will output relations in the default format returned by [Ollie](https://github.com/knowitall/ollie/blob/master/README.md).   
-filelist | /path/to/filelist | A path to a file, which contains files to annotate. Each file should be on its own line. If this option is set, only these files are annotated and the files passed via bare arguments are ignored.   
-threads | integer | The number of threads to run on. By default, this is the number of threads on the system.  
-max_entailments_per_clause | integer | The maximum number of entailments to produce for each clause extracted in the sentence. The larger this value is, the slower the system will run, but the more relations it can potentially extract. Setting this below 100 is not recommended; setting it above 1000 is likewise not recommended.  
-resolve_coref | boolean | If true, resolve pronouns to their canonical antecedent. This option requires additional CoreNLP annotators not included in the distribution, and therefore only works if used with the [CoreNLP OpenIE annotator](https://stanfordnlp.github.io/CoreNLP/openie.html), or invoked via the command line from the CoreNLP jar.   
-ignore_affinity | boolean | Ignore the affinity model for prepositional attachments.  
-affinity_probability_cap | double | The affinity value above which confidence of the extraction is taken as 1.0. Default is 1/3.  
-triple.strict | boolean | If true (the default), extract triples only if they consume the entire fragment. This is useful for ensuring that only logically warranted triples are extracted, but puts more burden on the entailment system to find minimal phrases (see -max_entailments_per_clause).  
-triple.all_nominals | boolean | If true, extract nominal relations always and not only when a named entity tag warrants it. This greatly overproduces such triples, but can be useful in certain situations.  
-splitter.model | /path/to/model.ser.gz | [rare] You can override the default location of the clause splitting model with this option.  
-splitter.nomodel |  | [rare] Run without a clause splitting model -- that is, split on every clause.  
-splitter.disable |  | [rare] Don't split clauses at all, and only extract relations centered around the root verb.  
-affinity_model | /path/to/model_dir | [rare] A custom location to read the affinity models from.  
  
The code can also be invoked programatically, using [Stanford
CoreNLP](https://nlp.stanford.edu/software/corenlp.html). For this, simply
include the annotators natlog and openie in the annotators property, and add
any of the flags described above to the properties file prepended with the
string "openie." Note that openie depends on the annotators
"tokenize,ssplit,pos,depparse". An example working code snippet is provided
below. This snippet will annotate the text "Obama was born in Hawaii. He is
our president," and print out each extraction from the document to the
console.

  

### Support

1. `java-nlp-user` This is the best list to post to in order to send feature requests, make announcements, or for discussion among JavaNLP users. (Please ask support questions on [Stack Overflow](https://stackoverflow.com) using the `stanford-nlp` tag.) 
You have to subscribe to be able to use this list. Join the list via [this
webpage](https://mailman.stanford.edu/mailman/listinfo/java-nlp-user) or by
emailing `java-nlp-user-join@lists.stanford.edu`. (Leave the subject and
message body empty.) You can also [look at the list
archives](https://mailman.stanford.edu/pipermail/java-nlp-user/).

2. `java-nlp-announce` This list will be used only to announce new versions of Stanford JavaNLP tools. So it will be very low volume (expect 1-3 messages a year). Join the list via [this webpage](https://mailman.stanford.edu/mailman/listinfo/java-nlp-announce) or by emailing `java-nlp-announce-join@lists.stanford.edu`. (Leave the subject and message body empty.)

3. `java-nlp-support` This list goes only to the software maintainers. It's a good address for licensing questions, etc. **For general use and support questions, you're better off joining and using`java-nlp-user`.** You cannot join `java-nlp-support`, but you can mail questions to `java-nlp-support@lists.stanford.edu`. 

< !-- RELEASE HISTORY -- >  

### Release History

  
| Version| Date| Description| Resources  |
|:---:|:---:|---|---  |
| 3.6.0 | 2015-12-09 | First release |  [code](/projects/naturalli/stanford-openie-3.6.0.jar) / [models](/projects/naturalli/stanford-openie-models-3.6.0.jar) / [source](/projects/naturalli/stanford-openie-src-3.6.0.jar) |

