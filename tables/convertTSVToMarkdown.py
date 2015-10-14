import sys

firstLine = sys.stdin.readline()
titles = firstLine[:-1].split("\t")
sys.stdout.write("| "+" | ".join(titles)+" |\n")
sys.stdout.write("| "+"--- | "*len(titles)+"\n")

for line in sys.stdin:
    sys.stdout.write("| "+" | ".join(line[:-1].split("\t"))+" |\n")
