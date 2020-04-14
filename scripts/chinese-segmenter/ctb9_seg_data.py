"""
This script extracts segmentation data from ctb9 in some various hardcoded ways.
For example, each possible file class was individually parsed.

Train/test split is chosen based on the advice given in the readme.
There is no suggested dev split and the test split is quite small, actually.

The results of using this script and some models can be found
in /u/nlp/data/chinese/ctb9, at least as of 2020-01-16.

Models can be built with the make script hopefully still located in
projects/core/scripts/chinese-segmenter/Makefile

A model can be tested with a command line such as:

java edu.stanford.nlp.ie.crf.CRFClassifier -loadClassifier /u/nlp/data/chinese/ctb9/seg/ctb9.train.chris6.ser.gz  -testFile /u/nlp/data/chinese/ctb9/seg/ctb9.test.txt -serDictionary /u/nlp/data/chinese/ctb9/seg/dict-chris6.ser.gz > seg9.out 2>&1
"""

import glob
import re

def parse_xml(filename, lines):
    new_lines = []
    for i, line in enumerate(lines[7:]):
        line = line.strip()
        if line.startswith('<S ID') or line.startswith('<ENDTIME>') or line.startswith('<END_TIME>'):
            continue
        if (line == '</S>' or line == '<HEADLINE>' or line == '</HEADLINE>' or
            line == '<TEXT>' or line == '</TEXT>' or line == '</BODY>' or
            line == '<P>' or line == '</P>' or line == '</DOC>' or
            line == '<TURN>' or line == '</TURN>'):
            continue
        if line[0] == '<':
            raise ValueError("Unexpected XML tag in %s line %d: %s" % (filename, (i+7), line))
        new_lines.append(line)
    return new_lines

# p1su1 occurs in /u/scr/corpora/ldc/2016/LDC2016T13/ctb9.0/data/segmented/chtb_5000.df.seg
# 13suid= occurs in /u/scr/corpora/ldc/2016/LDC2016T13/ctb9.0/data/segmented/chtb_5200.df.seg
# headline_su1 occurs in /u/scr/corpora/ldc/2016/LDC2016T13/ctb9.0/data/segmented/chtb_5336.df.seg
# psu1 occurs in /u/scr/corpora/ldc/2016/LDC2016T13/ctb9.0/data/segmented/chtb_5363.df.seg
# hesu1 occurs in /u/scr/corpora/ldc/2016/LDC2016T13/ctb9.0/data/segmented/chtb_5459.df.seg
# s1 occurs in /u/scr/corpora/ldc/2016/LDC2016T13/ctb9.0/data/segmented/chtb_6000.sc.seg
SU_PATTERN = re.compile("(?:<su id|13suid)=(?:[0-9]+[A-B]|p[0-9]*su[0-9]+|headline_su[0-9]+|hesu[0-9]+|s[0-9]+)(?:>?)")

def parse_su(filename, lines):
    new_lines = []
    for i, line in enumerate(lines):
        line = line.strip()
        if SU_PATTERN.match(line):
            continue
        if line[0] == '<':
            raise ValueError("Unexpected XML tag in %s line %d: %s" % (filename, (i+7), line))            
        new_lines.append(line)
    return new_lines

SEG_PATTERN = re.compile('<seg id="[0-9]+">')
def parse_seg(filename, lines):
    new_lines = []
    for i, line in enumerate(lines):
        line = line.strip()
        if SEG_PATTERN.match(line) or line == '</seg>':
            continue
        if line == '< HEADLINE >' or line == '< DOC >':
            continue
        if line[0] == '<':
            raise ValueError("Unexpected XML tag in %s line %d: %s" % (filename, (i+7), line))            
        new_lines.append(line)
    return new_lines

SEGMENT_PATTERN = re.compile('<segment id="[0-9]+" .+>')
def parse_segment(filename, lines):
    new_lines = []
    for i, line in enumerate(lines):
        line = line.strip()
        if SEGMENT_PATTERN.match(line) or line == '</segment>':
            continue
        if line[0] == '<':
            raise ValueError("Unexpected XML tag in %s line %d: %s" % (filename, (i+7), line))            
        new_lines.append(line)
    return new_lines

MSG_PATTERN = re.compile('<msg id=s[0-9]+m[0-9]+.*>')
def parse_msg(filename, lines):
    new_lines = []
    for i, line in enumerate(lines):
        line = line.strip()
        if MSG_PATTERN.match(line):
            continue
        if line[0] == '<':
            raise ValueError("Unexpected XML tag in %s line %d: %s" % (filename, (i+7), line))            
        new_lines.append(line)
    return new_lines



def parse_raw(filename, lines):
    new_lines = []
    for i, line in enumerate(lines):
        if line.startswith('< QUOTEPREVIOUSPOST') or line.startswith('< QUOTE PREVIOUSPOST'):
            continue
        if line[0] == '<':
            raise ValueError("Unexpected XML tag in %s line %d: %s" % (filename, (i+7), line))            
        new_lines.append(line)
    return new_lines


def read_file(filename):
    lines = open(filename).readlines()
    # /u/scr/corpora/ldc/2016/LDC2016T13/ctb9.0/data/segmented/chtb_0050.nw.seg
    if (lines[0].strip() == '<DOC>' and
        lines[1].startswith('<DOCID>') and
        lines[2].strip() == '<HEADER>' and
        lines[5].strip() == '<BODY>'):
        return parse_xml(filename, lines)
    # /u/scr/corpora/ldc/2016/LDC2016T13/ctb9.0/data/segmented/chtb_3046.bn.seg
    if (lines[0].strip() == '<DOC>' and
        (lines[1].startswith('<DOCID>') or lines[1].startswith('<DOCNO>')) and
        lines[2].startswith('<DOCTYPE') and
        lines[4].strip() == '<BODY>'):
        return parse_xml(filename, lines)
    # /u/scr/corpora/ldc/2016/LDC2016T13/ctb9.0/data/segmented/chtb_7000.cs.seg
    if SU_PATTERN.match(lines[0].strip()):
        return parse_su(filename, lines)
    # /u/scr/corpora/ldc/2016/LDC2016T13/ctb9.0/data/segmented/chtb_4000.nw.seg
    if SEG_PATTERN.match(lines[0].strip()):
        return parse_seg(filename, lines)
    # /u/scr/corpora/ldc/2016/LDC2016T13/ctb9.0/data/segmented/chtb_4051.bn.seg
    if SEGMENT_PATTERN.match(lines[0].strip()):
        return parse_segment(filename, lines)
    # /u/scr/corpora/ldc/2016/LDC2016T13/ctb9.0/data/segmented/chtb_6006.sc.seg
    # <msg id=s0m0000>
    if MSG_PATTERN.match(lines[0].strip()):
        return parse_msg(filename, lines)
    # /u/scr/corpora/ldc/2016/LDC2016T13/ctb9.0/data/segmented/chtb_4009.nw.seg
    return parse_raw(filename, lines)
    # raise ValueError("Unknown format: " + filename)



TEST_FILES = [1018, 1020, 1036, 1044, 1060, 1061, 1072, 1118, 1119, 1132, 1141, 1142, 1148]
# TODO: can extract this list directly from 
# /u/scr/corpora/ldc/2016/LDC2016T13/ctb9.0/docs/ctb9.0-file-list.txt
# there's also dev file names there
def is_test_file(filenum):
    if filenum in TEST_FILES:
        return True
    if filenum >= 1 and filenum <= 43:
        return True
    if filenum >= 144 and filenum <= 169:
        return True
    if filenum >= 900 and filenum <= 931:
        return True
    return False

def output_file(lines, filename):
    repeats = set()
    with open(filename, 'w') as fout:
        for line in lines:
            if line in repeats:
                continue
            fout.write('%s\n' % line)
            repeats.add(line)


filters = [re.compile("p([.]?) [0-9]+"),
           re.compile("[0-9]+ [/] [0-9.]+")]
def filter_bad_lines(lines):
    """
    Filters some of the more common, essentially useless lines:

    p. 55
    2000 / 15
    """
    lines = [x for x in lines if min(f.match(x) is None for f in filters)]
    return lines


def main():
    train_data = []
    test_data = []

    files = sorted(glob.glob('/u/scr/corpora/ldc/2016/LDC2016T13/ctb9.0/data/segmented/*.seg'))
    for filename in files:
        filenum = int(filename.split("_")[-1].split(".")[0])
        new_lines = read_file(filename)
        if is_test_file(filenum):
            test_data.extend(new_lines)
        else:
            train_data.extend(new_lines)

    output_file(filter_bad_lines(train_data), 'ctb9_train.txt')
    output_file(filter_bad_lines(test_data), 'ctb9_test.txt')



if __name__ == '__main__':
    main()



