#!/u/nlp/packages/anaconda/bin/python

from random import randint

def print_examples_for_currency_symbol(curr_symbol):
    # $14.20
    random_small_price = str(randint(0,99))+"."+str(randint(0,9))+str(randint(0,9))
    print("%s_$ %s_CD" % (curr_symbol, random_small_price))
    # 14.20$
    random_small_price = str(randint(0,99))+"."+str(randint(0,9))+str(randint(0,9))
    print("%s_CD %s_$" % (random_small_price, curr_symbol))
    # $2.14
    random_small_price = str(randint(0,9))+"."+str(randint(0,9))+str(randint(0,9))
    print("%s_$ %s_CD" % (curr_symbol, random_small_price))
    # $2.14
    random_small_price = str(randint(0,9))+"."+str(randint(0,9))+str(randint(0,9))
    print("%s_CD %s_$" % (random_small_price, curr_symbol))
    # $10
    print("%s_$ 10_CD" % curr_symbol)
    # 10$
    print("10_CD %s_$" % curr_symbol)
    # random $XXXX
    random_four_digit = randint(1000,9999)
    print("%s_$ %s_CD" % (curr_symbol, str(random_four_digit)))
    # random XXXX$
    random_four_digit = randint(1000,9999)
    print("%s_$ %s_CD" % (curr_symbol, str(random_four_digit)))
    # random $XXXX
    random_four_digit = randint(1000,9999)
    print("%s_CD %s_$" % (str(random_four_digit), curr_symbol))
    # random XXXX$
    random_four_digit = randint(1000,9999)
    print("%s_CD %s_$" % (str(random_four_digit), curr_symbol))
    # $500
    print("%s_$ 500_CD" % curr_symbol)
    # $50.00
    print("%s_$ 50.00_CD" % curr_symbol)
    # 50.00$
    print("50.00_CD %s_$" % curr_symbol)
    # $50
    print("%s_$ 50_CD" % curr_symbol)
    # 50$
    print("50_CD %s_$" % curr_symbol)
    # $1.00
    print("%s_$ 1.00_CD" % curr_symbol)
    # 1.00$
    print("1.00_CD %s_$" % curr_symbol)
    # $1,000
    print("%s_$ 1,000_CD" % curr_symbol)
    # 1,000$
    print("1,000_CD %s_$" % curr_symbol)
    # $1000000
    print("%s_$ 1000000_CD" % curr_symbol)
    # $1,000,000
    print("%s_$ 1,000,000_CD" % curr_symbol)
    # 1000000$
    print("1000000000_CD %s_$" % curr_symbol)
    # 1,000,000$
    print("1,000,000,000_CD %s_$" % curr_symbol)
    # $1000000
    print("%s_$ 1000000000_CD" % curr_symbol)
    # $1,000,000
    print("%s_$ 1,000,000,000_CD" % curr_symbol)
    # 1000000$
    print("1000000000_CD %s_$" % curr_symbol)
    # 1,000,000$
    print("1,000,000,000_CD %s_$" % curr_symbol)

currency_chars = ["¥", "£", "€", "₹", "₪", "₽", "₩", "¢"]

for curr_char in currency_chars:
    print_examples_for_currency_symbol(curr_char)
