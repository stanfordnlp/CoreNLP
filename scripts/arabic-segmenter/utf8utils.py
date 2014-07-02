#!/usr/bin/env python2.7

import codecs


def uopen(filename, mode):
    return codecs.open(filename, mode, encoding='utf-8')


def uprint(text):
    print(text.encode('utf-8'))
