#!/usr/bin/env python2.7
# -*- coding: utf-8 -*-
import sys
import codecs
import re

def convert(untok_filename, tok_filename):
  with uopen(untok_filename, 'r') as input, \
      uopen(tok_filename, 'r') as output, \
      uopen(tok_filename + '.segmentation', 'w') as seg, \
      uopen(tok_filename + '.ftree', 'w') as tree:
    convert_files(input, output, seg, tree)

def get_filenames(argv):
  if len(argv) != 3:
    print 'Usage: %s <untok> <tok>' % argv[0]
    print '    where'
    print '        <untok>            is the untokenized input file that was fed to the segmenter'
    print '        <tok>              is the existing segmenter output file'
    print '        <tok>.segmentation will be the generated TEDEval seg file'
    print '        <tok>.ftree        will be the generated TEDEval tree file'
    exit(1)
  return argv[1], argv[2]

def uopen(filename, mode):
  return codecs.open(filename, mode, encoding='utf-8')

def convert_files(input, output, seg, tree):
  for input_line, output_line in zip(input, output):
    process_line(input_line, output_line, seg, tree)

def process_line(input_line, output_line, seg, tree):
  tree.write('(root')
  input_words = sanitize(input_line).split(' ')
  output_words = merge_segments(output_line).split(' ')
  input_words = filter_deletions(input_words)
  output_words = filter_deletions(output_words)
  assert len(input_words) == len(output_words), str((input_line, output_line, input_words, output_words))
  for input_word, output_word in zip(input_words, output_words):
    for segment in output_word.split(':'):
      tree.write(' (seg %s)' % segment)
    seg.write('%s\t%s\n' % (input_word, output_word))
  seg.write('\n')
  tree.write(')\n')

def filter_deletions(words):
  '''
  Some tokens (ones consisting solely of a diacritic or tatweel) are deleted
  by one or both segmenters. This deletes all such tokens from the output to
  try to balance out the sentence.
  '''
  return [word for word in words if not is_deleted(word)]

def is_deleted(word):
  return re.match(u'^[~_\u0640\u064b-\u065e\u0670]*$', word) is not None
  #                     tatweel            dagger alif
  #                           most diacritics

def merge_segments(line):
  return re.sub(r'\$(\w+)\$', r'#\1#',
         re.sub(r'\(', r'#lp#',
         re.sub(r'\)', r'#rp#',
         re.sub(r'([^ ])# ', r'\1:',
         re.sub(r' \+([^ ])', r':\1',
         re.sub(r'([^ ])# \+([^ ])', r'\1:\2',
         re.sub(r':', r'$pm$',
         re.sub(r'#(\w+)#', r'$\1$',
                line[:-1]))))))))

def sanitize(line):
  return re.sub(r'\(', r'#lp#',
         re.sub(r'\)', r'#rp#',
         re.sub(r':', r'#pm#',
                line[:-1])))

if __name__ == '__main__':
  untok, tok = get_filenames(sys.argv)
  convert(untok, tok)
