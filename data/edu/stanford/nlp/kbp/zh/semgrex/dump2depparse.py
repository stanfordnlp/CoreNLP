import sys
import re

line = sys.stdin.readline()

while True:
    line = line.strip()
    if len(line) == 0:
        break

    if line[0] == '!':
        continue

    line = re.sub(' +', '\t', line)
    line = line.split('\t')

    gloss = line[0].split('/')
    deps = [t.split('\\t') for t in line[1].split('\\n')]
    gloss1 = ['root'] + gloss

    for t in deps:
        tgt = int(t[0])
        src = int(t[1])
        type_ = t[2]
        sys.stdout.write("%s(%d-%s, %d-%s)\n" % (type_, src, gloss1[src], tgt, gloss1[tgt]))

    sys.stdout.write('\n')
    line = sys.stdin.readline()
