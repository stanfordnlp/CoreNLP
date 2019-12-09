import argparse
import glob
import random

"""
After a wikiextractor step, this script picks num_lines lines
randomly, with equal probability, from all the lines extracted from
wikipedia.
"""

def parse_args():
    parser = argparse.ArgumentParser(description='Turn the output of wikiextractor into lines which can be tokenized')
    parser.add_argument('--path', default='text',
                        help='Where to find the output of wikiextractor')
    parser.add_argument('--num_lines', type=int, default=2000000,
                        help='Number of lines to keep')
    parser.add_argument('--output', default='wiki.raw.txt',
                        help='Where to output text')
    args = parser.parse_args()
    return args

def main():
    args = parse_args()

    text = []
    files = glob.glob('%s/*/wiki*' % args.path)
    total_seen = 0
    for infile in files:
        with open(infile) as fin:
            for line in fin.readlines():
                line.replace("<br>", " ")
                line = line.strip()
                if not line:
                    continue
                if line.startswith("<"):
                    continue

                if (line.count("|") > 5 or line.count(",") > 20 or
                    line.count(";") > 10 or line.count(":") > 10 or
                    line.count("•") > 5 or line.count("-") > 10):
                    # skip some random lists etc
                    continue

                total_seen = total_seen + 1
                
                if len(text) < args.num_lines:
                    text.append(line)
                elif random.random() < args.num_lines / total_seen:
                    # randomly skip lines so lines have an equal
                    # probability of being accepted
                    index = random.randint(0, args.num_lines - 1)
                    text[index] = line

    with open(args.output, 'w') as fout:
        for line in text:
            fout.write(line)
            fout.write('\n\n')

if __name__ == "__main__":
    main()
