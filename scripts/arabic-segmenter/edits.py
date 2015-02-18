#!/usr/bin/env python2.7
# -*- coding: utf-8 -*-

import re

from utf8utils import uprint


NOSEG = '<noseg>'
SEG_MARKER = ':'
SEG = '   %s   ' % SEG_MARKER

LONG_VOWELS = u'ايوى'
ALIFS = u'اأإٱآ'
HAAS = u'هح'


def get_edits(line, options, special_noseg=True):
    if '\t' not in line:
        if options.verbose:
            uprint("ignoring line that doesn't have two parts:")
            uprint('    ' + repr(line))
        return
    raw, seg = line.split('\t')

    # Special cases:
    # - an odd edit with no segmentations [e.g. ع -> على]
    if special_noseg and raw != seg and SEG_MARKER not in seg:
        return [u'<other>'] * len(raw)
    # - token deleted
    if seg == '':
        return [u' <del> '] * len(raw)
    # - nothing on the raw side
    if raw == '':
        if options.verbose:
            uprint("ignoring line with empty raw text:")
            uprint('    ' + repr(line))
        return

    edits = []

    last_raw = ''
    last_seg = ''
    while len(raw) != 0:
        # Possible edits, in order that they are searched for:
        #    :+Al // li + definite article + word starting with l
        if raw.endswith(u'لل') and seg.endswith(u'ل%sالل' % SEG_MARKER):
            edits.append(u'   %s+ال' % SEG_MARKER)
            seg = seg[:-3]
        #  +A:+A  // mA + A... verbal negation spelled as just m
        elif is_ma_alif(seg, raw):
            edits.append(u' +ا%s+ا ' % SEG_MARKER)
            seg = seg[:-3]
        #   x:x   // shadda breaking: character duplicated on either side of
        #            segmentation
        #   x>xx  // shadda breaking: character duplicated, no segmentation
        elif is_shadda(seg, raw):
            if seg.endswith(SEG_MARKER + raw[-1]):
                edits.append(u'  x:x  ')
                seg = seg[:-2]
            else:
                assert seg.endswith(raw[-1] * 2), repr(seg + '\t' + raw)
                edits.append(u'  x>xx ')
                seg = seg[:-1]
        #    :+x  // added an letter after segmentation (alif for
        #            li + definite article, noon for recovered first person
        #            prefix or y -> ny in dialect)
        elif is_seg_plus(seg, raw):
            edits.append(u'   %s+%s ' % (SEG_MARKER, seg[-2]))
            seg = seg[:-2]
        #  +x:    // added a letter before segmentation (usually noon, for
        #            plurals, mim~A, Al~A, etc.)
        elif is_plus_seg(seg, raw):
            edits.append(u' +%s%s   ' % (seg[-3], SEG_MARKER))
            seg = seg[:-2]
        #  <del>  // deleted lengthening effect (yAAAAAA -> yA)
        elif is_lengthening(seg, raw, last_raw):
            edits.append(u' <del> ')
            seg += u' '
        #    :    // ordinary segmentation boundary
        elif seg.endswith(SEG_MARKER + raw[-1]):
            edits.append(SEG)
            seg = seg[:-1]
        # <noseg> // character doesn't change, no segmentation added
        elif len(seg) != 0 and seg[-1] == raw[-1]:
            edits.append(NOSEG)
        # <other> // normalized E or El to ElY
        elif is_alaa_normalization(seg, raw):
            edits.append(u'<other>')
            seg = seg[:-2]
            if raw[-1] != u'ع':
                assert raw[-2] == u'ع'
                seg = seg + ' '
        #  +V:    // added a long vowel (verbal or dialect -wA ending, jussive
        #            normalization)
        elif len(seg) >= 2 and seg[-2] == raw[-1] and seg[-1] in LONG_VOWELS:
            if len(seg) >= 3 and seg[-3] == SEG_MARKER:
                edits.append(u'   %s+%s ' % (SEG_MARKER, seg[-1]))
                seg = seg[:-2]
            else:
                edits.append(u'   +%s  ' % seg[-1])
                seg = seg[:-1]
        #   y:+h  // recover dialectal silent haa after segmentation
        elif seg.endswith(u'ي' + SEG_MARKER + u'ه') and raw.endswith(u'ي'):
            edits.append(u'  ي%s+ه ' % SEG_MARKER)
            seg = seg[:-2]
        #  <del>  // deleted a long vowel (dialect ending normalization: mostly
        #            -kwA -> -kw and -kY -> -k) or dialectal silent haa
        elif (len(raw) >= 2 and norm_endswith(seg, raw[-2], HAAS) and
              raw[-1] in LONG_VOWELS + u'ه'):
            edits.append(u' <del> ')
            seg += u' '
        #  <del>  // deleted diacritic
        elif is_diacritic(raw[-1]):
            edits.append(u' <del> ')
            seg += u' '
        # x>y:    // change x to y after a segment boundary
        elif (len(seg) >= 2 and seg[-2] == SEG_MARKER and
              is_common_rewrite(seg, raw)):
            edits.append(u'   %s%s>%s ' % (SEG_MARKER, raw[-1], seg[-1]))
            seg = seg[:-1]
        #   x>y   // change x to y without a segmentation (orthography
        #            normalization)
        elif is_common_rewrite(seg, raw):
            edits.append(u'  %s>%s  ' % (raw[-1], seg[-1]))
        else:
            if options.verbose:
                uprint('ignoring line with unknown edit:')
                uprint('    ' + line)
                uprint('(seg = %s; raw = %s)' % (seg, raw))
                uprint('(edits = %s)' % edits)
            return
        last_raw = raw[-1]
        seg = seg[:-1]
        last_seg = raw[-1]
        raw = raw[:-1]

    if len(seg) != 0:
        if options.verbose:
            uprint('ignoring line with unknown edit:')
            uprint('    ' + line)
            uprint('(extra seg: %s)' % seg)
            uprint('(edits = %s)' % edits)
        return

    edits.reverse()
    return edits


def is_ma_alif(seg, raw):
    return (len(seg) >= 5 and len(raw) >= 2 and
            is_common_rewrite(seg[-1], raw[-1]) and
            raw[-2] == u'م' and
            seg[-5:-1] == u'ما%sا' % SEG_MARKER)


def is_seg_plus(seg, raw):
    return (len(seg) >= 4 and len(raw) >= 2 and
            is_common_rewrite(seg[-1], raw[-1]) and
            seg[-2] != raw[-2] and
            seg[-2] in u'اني' and
            seg[-3] == SEG_MARKER and
            is_common_rewrite(seg[-4], raw[-2]))


def is_plus_seg(seg, raw):
    return (len(seg) >= 4 and len(raw) >= 2 and
            is_common_rewrite(seg[-1], raw[-1]) and
            seg[-2] == SEG_MARKER and
            seg[-3] != raw[-2] and
            seg[-3] in u'ان' and
            is_common_rewrite(seg[-4], raw[-2]))


def is_shadda(seg, raw):
    seg = seg.replace(SEG_MARKER, '')
    if len(raw) == 0 or not seg.endswith(raw[-1]):
        return False
    last = seg[-1]
    for i in range(2, min(len(seg) + 1, len(raw) + 1)):
        if seg[-i] != last: return False
        if seg[-i] != raw[-i]: return True
    # equal through the min of the two lengths, so check if it's
    # a beginning-of-word shadda
    return seg == raw[-1] + raw


def is_lengthening(seg, raw, last):
    seg = seg.replace(SEG_MARKER, '')
    if len(raw) < 2 or len(seg) == 0: return False
    if raw[-1] != raw[-2]: return False
    if raw[-1] != seg[-1]: return False
    if len(seg) >= 2 and raw[-1] == seg[-2]: return False
    return True


DIACRITIC = re.compile(ur'[~_\u0640\u064b-\u065e\u0670]')
#                           tatweel            dagger alif
#                                 most diacritics
def is_diacritic(char):
    return DIACRITIC.match(char) is not None


COMMON_REWRITES = [
    u'تة',       # recovered taa marbuta
    u'يىئ',      # normalized Egyptian yaa
    u'وؤ',       # normalized waw hamza
    u'هةو',      # normalized 3sg ending
    HAAS,        # normalized future particle
    ALIFS,       # normalized alifs
    u'اأإئؤقءي', # normalized various hamzas (written or spoken)
    u'ىهةا',     # normalized words ending in /a/ sound
    u'تثط',      # normalized letters pronounced /t/
    u'دذضظ',     # normalized letters pronounced /d/
    u'سص',       # normalized letters pronounced /s/
    u'زذظ',      # normalized letters pronounced /z/
    u'?–,،؟',    # normalized punctuation
]

def is_common_rewrite(seg, raw):
    if len(seg) == 0 or len(raw) == 0: return False
    if seg == raw: return True
    for group in COMMON_REWRITES:
        if seg[-1] in group and raw[-1] in group:
            return True
    return False


def is_alaa_normalization(seg, raw):
    return ((raw.endswith(u'ع') or raw.endswith(u'عل')) and
            seg.endswith(u'على'))


def norm_endswith(str, target_ending, norm_group):
    '''
    Return True if `str` ends with `target_ending`, ignoring differences
    between characters in `norm_group`. Otherwise return False.
    '''
    if len(str) < len(target_ending): return False
    source_ending = str[-len(target_ending):]
    assert len(source_ending) == len(target_ending)
    for s, t in zip(source_ending, target_ending):
        if s != t and (s not in norm_group or t not in norm_group):
            return False
    return True
