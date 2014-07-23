#!/usr/bin/env python2.7
# -*- coding: utf-8 -*-

import sys
import re

from optparse import OptionParser

from utf8utils import uprint, uopen
from edits import get_edits, SEG_MARKER
from output_to_tedeval import is_deleted


REWRITE_MARKER = "REW"

class FalseOptions(object):
  def __getattr__(self, name): return False

FALSE_OPTIONS = FalseOptions()


class Accumulator(object):
  def __init__(self, callback):
    self.callback = callback
    self.buffer = []

  def add(self, line):
    self.buffer.append(line)

  def flush(self):
    if len(self.buffer) != 0:
      self.callback(self.buffer)
      self.buffer = []


class Tagger(object):
  def __init__(self, tagsfile):
    self.tagsfile = tagsfile
    self.prevline = None
    self.ignored = 0

  def __call__(self, words):
    tagsline = '\n'
    while tagsline == '\n':
      tagsline = tagsfile.readline()
    tags = get_tags(tagsline)
    if len(tags) != len(words):
      # print >> sys.stderr, "Number of tags doesn't match number of words"
      # print >> sys.stderr, ' previous line: ' + self.prevline
      # print >> sys.stderr, (' tags line: %s\n tags: %s\n words: %s' %
      #     (tagsline, ', '.join(tags), ', '.join(words)))
      self.ignored += 1
      # raw_input()
      return

    uprint(' '.join('|||'.join(pair) for pair in zip(words, tags)))
    self.prevline = tagsline



def get_tags(line):
  tags = []
  pairs = [split_pair(token) for token in line.split()] 
  for pair in pairs:
    if not is_deleted(pair[0]):
      # Duplicate a tag several times if splitting numbers from
      # miscellaneous characters would result in that segment
      # turning into several tokens after tokenization.
      tags += [pair[1]] * num_number_splits(pair[0])
  return tags


def split_pair(token):
  pos = token.rfind('|||')
  return token[:pos], token[pos + 3:]


NUMBER_BOUNDARY = re.compile(r'(?<=[^0-9 -])(?=[0-9])|(?<=[0-9])(?=[^0-9 -])')
def num_number_splits(segment):
  num_boundaries = len(NUMBER_BOUNDARY.findall(segment))
  return num_boundaries + 1


def convert(infile, tagsfile):
  tagger = Tagger(tagsfile)
  accum = Accumulator(tagger)
  for line in infile:
    segs_norew, segs_rew = convert_line(line)
    assert len(segs_norew) == len(segs_rew)
    for norew, rew in zip(segs_norew, segs_rew):
      accum.add('%s>>>%s' % (norew, rew))
    if len(segs_norew) == 0:
      accum.flush()
  print >> sys.stderr, ('%d sentences ignored.' % tagger.ignored)


def convert_line(line):
  if '\t' not in line:
    return '', ''

  line = line[:-1]
  edits = get_edits(line, FALSE_OPTIONS, special_noseg=False)
  raw, segmented = line.split('\t')
  if edits is None:
    norew = rew = segmented
  else:
    norew, rew = apply_edits(edits, raw)
  segs_norew = norew.split(SEG_MARKER)
  segs_rew = rew.split(SEG_MARKER)
  return (unescape('- -'.join(segs_norew)).split(),
          unescape('- -'.join(segs_rew)).split())


def unescape(s):
  return (s.replace('#pm#', ':') 
           .replace('#lp#', '(') 
           .replace('#rp#', ')'))


def apply_edits(edits, raw):
  if len(edits) != len(raw):
    print >> sys.stderr, "Number of edits is not equal to number of characters"
    print >> sys.stderr, (' word: %s\n edits: %s' %
        (raw, ', '.join(edits)))
    raise AssertionError

  labels = [crf_label(raw[i], edits[i]) for i in range(len(raw))]
  norew = ''.join(rewrite_with_label(raw[i], labels[i], False)
                  for i in range(len(raw)))
  rew = ''.join(rewrite_with_label(raw[i], labels[i], True)
                for i in range(len(raw)))
  return norew, rew


def crf_label(char, edit):
  if (edit == u'   :+ا ' and char == u'ل'): return 'REW'
  elif SEG_MARKER in edit: return 'BEGIN'
  elif edit.strip() in (u'ي>ى', u'ت>ة', u'ى>ي', u'ه>ة', u'ة>ه'):
    return 'REW'
  else: return 'CONT'


def rewrite_with_label(char, label, apply_rewrites):
  if label == 'BEGIN': return SEG_MARKER + char
  elif label == 'CONT': return char
  elif label == 'REW':
    if char == u'ل':
      return u':ال'
    elif apply_rewrites:
      if char in u'ته':
        return u'ة'
      elif char == u'ة':
        return u'ه'
      elif char == u'ي':
        return u'ى'
      elif char == u'ى':
        return u'ي'
    else:
      return char
  else:
    assert False, 'unrecognized label: ' + label


def parse_args():
  parser = OptionParser(usage='Usage: %prog <segmentation> <tags>')
  (options, args) = parser.parse_args()
  if len(args) != 2:
    parser.error('Please provide a segmentation file and a tags file.')
  return args


if __name__ == '__main__':
  files = parse_args()
  with uopen(files[0], 'r') as infile, uopen(files[1], 'r') as tagsfile:
    convert(infile, tagsfile)
