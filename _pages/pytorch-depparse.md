---
layout: page
title: PyTorch Dependency Parser
keywords: pytorch, dependency parse
permalink: '/pytorch-depparse.html'
nav_order: 5
toc: false
parent: Additional Tools
---

## Overview

CoreNLP dependency parser models are trained with a PyTorch system for speed considerations. The PyTorch models can be converted to the format CoreNLP's dependency parser expects.

The purpose of this library is to train models for the Java code base. If you want a full featured Python dependency parser,
you should look into using [Stanza](https://stanfordnlp.github.io/stanza/).

The code repo can be found [here](https://github.com/stanfordnlp/nn-depparser).

## Example Usage

First train a model. Make sure to have a recent PyTorch and [Stanza](https://stanfordnlp.github.io/stanza/) installed.

Here is an example training command for training and Italian model (run from the `code` directory):

```bash
python train.py -l universal -d /path/to/data --train_file it-train.conllu --dev_file it-dev.conllu --embedding_file /path/to/it-embeddings.txt --embedding_size 100 --random_seed 21 --learning_rate .005 --l2_reg .01 --epsilon .001 --optimizer adamw --save_path /path/to/experiment-dir --job_id experiment-name --corenlp_tags --corenlp_tag_lang italian --n_epoches 2000
```

The data files should be `*.conllu` format.

After the model is trained, it can be converted to a format usable by CoreNLP:

```bash
python gen_model.py -o /path/to/italian-corenlp-parser.txt /path/to/experiment-dir/experiment-name
```

This will save a CoreNLP useable model at `/path/to/italian-corenlp-parser.txt`.

## Where To Get Data

You can find data for training models at the official [Universal Dependencies](https://universaldependencies.org/) site.

