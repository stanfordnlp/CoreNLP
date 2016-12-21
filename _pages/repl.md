---
title: Interactive mode (REPL)
keywords: interactive repl
permalink: '/repl.html'
---

## Interactive mode (REPL)

### Built-in interactive mode

You can type or paste sentences or paragraphs into CoreNLP interactively and see
how it analyzes them.
This gives a kind of read-eval-print loop (REPL).

You can use interactive mode with  either `StanfordCoreNLP` or the combination
of running `StanfordCoreNLPServer` and `StanfordCoreNLPClient`.
You can specify whatever annotators and other properties that 
you want. 

If you do not specify any flag that directs CoreNLP 
to process text from a particular file, then after the pipeline
is loaded, you will be placed into an interactive loop.
(That is, if you don't specify either `-file` or `-filelist`.)

You exit the REPL by typing:
```
q RETURN
```
Typing `CTRL-C` also works!

### Java 9 REPL

If you are playing with the REPL for Java 9, you might want to
try out using the CoreNLP [simple](simple.html) API with it.
It works quite well.