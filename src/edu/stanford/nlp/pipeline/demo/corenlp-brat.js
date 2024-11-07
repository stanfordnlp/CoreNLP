// Takes Stanford CoreNLP JSON output (var data = ... in data.js)
// and uses brat to render everything.

//var serverAddress = 'http://localhost:9000';
var serverAddress = '';

// Load Brat libraries
var bratLocation = 'https://nlp.stanford.edu/js/brat/';
head.js(
  // External libraries
  './jquery.svg.min.js',
  './jquery.svgdom.min.js',

  // brat helper modules
  './configuration.js',
  './util.js',
  './annotation_log.js',
  './webfont.js',

  // brat modules
  './dispatcher.js',
  './url_monitor.js',
  './visualizer.js',

  // parse viewer
  './corenlp-parseviewer.js'
);

// Uses Dagre (https://github.com/cpettitt/dagre) for constinuency parse
// visualization. It works better than the brat visualization.
var useDagre = true;
var currentQuery = 'The quick brown fox jumped over the lazy dog.';
var currentSentences = '';
var currentText = '';

// ----------------------------------------------------------------------------
// HELPERS
// ----------------------------------------------------------------------------

/**
 * Add the startsWith function to the String class
 */
if (typeof String.prototype.startsWith !== 'function') {
  // see below for better implementation!
  String.prototype.startsWith = function (str){
    return this.indexOf(str) === 0;
  };
}

function isInt(value) {
  return !isNaN(value) && (function(x) { return (x | 0) === x; })(parseFloat(value))
}

/**
 * A reverse map of PTB tokens to their original gloss
 */
var tokensMap = {
  '-LRB-': '(',
  '-RRB-': ')',
  '-LSB-': '[',
  '-RSB-': ']',
  '-LCB-': '{',
  '-RCB-': '}',
  '``': '"',
  '\'\'': '"',
};

/**
 * A mapping from part of speech tag to the associated visualization color
 */
function posColor(posTag) {
  if (posTag.startsWith('N')) {
    return '#A4BCED';
  } else if (posTag.startsWith('V') || posTag.startsWith('M')) {
    return '#ADF6A2';
  } else if (posTag.startsWith('P')) {
    return '#CCDAF6';
  } else if (posTag.startsWith('I')) {
    return '#FFE8BE';
  } else if (posTag.startsWith('R') || posTag.startsWith('W')) {
    return '#FFFDA8';
  } else if (posTag.startsWith('D') || posTag === 'CD') {
    return '#CCADF6';
  } else if (posTag.startsWith('J')) {
    return '#FFFDA8';
  } else if (posTag.startsWith('T')) {
    return '#FFE8BE';
  } else if (posTag.startsWith('E') || posTag.startsWith('S')) {
    return '#E4CBF6';
  } else if (posTag.startsWith('CC')) {
    return '#FFFFFF';
  } else if (posTag === 'LS' || posTag === 'FW') {
    return '#FFFFFF';
  } else {
    return '#E3E3E3';
  }
}

/**
 * A mapping from named entity tag to the associated visualization color
 */
function nerColor(nerTag) {
  if (nerTag === 'PERSON') {
    return '#FFCCAA';
  } else if (nerTag === 'ORGANIZATION') {
    return '#8FB2FF';
  } else if (nerTag === 'MISC') {
    return '#F1F447';
  } else if (nerTag === 'LOCATION' || nerTag === 'COUNTRY' || nerTag === 'STATE_OR_PROVINCE' || nerTag === 'CITY') {
    return '#95DFFF';
  } else if (nerTag === 'DATE' || nerTag === 'TIME' || nerTag === 'DURATION' || nerTag === 'SET') {
    return '#9AFFE6';
  } else if (nerTag === 'MONEY') {
    return '#FFFFFF';
  } else if (nerTag === 'PERCENT') {
    return '#FFA22B';
  } else {
    return '#E3E3E3';
  }
}

var d3_category18 = {
  // Just like d3_category20 but no grays!
  name: 'd3_category_18',
  colors: [
   '#aec7e8',
   '#ffbb78',
   '#98df8a',
   '#ff9896',
   '#c5b0d5',
   '#c49c94',
   '#f7b6d2',
   '#dbdb8d',
   '#9edae5',
   '#1f77b4',
   '#ff7f0e',
   '#2ca02c',
   '#d62728',
   '#9467bd',
   '#8c564b',
   '#e377c2',
   '#bcbd22',
   '#17becf',
  ]
};

function generateRandomColor() {
  return "#" + Math.random().toString(16).slice(2, 8);
}

function generateNextColor(i, palette) {
  if (palette && i < palette.colors.length) {
    return palette.colors[i];
  } else {
    return generateRandomColor();
  }
}

function getTagColor(tag, colorIndex, colors) {
  var ci = colorIndex[tag];
  if (ci == null) {
    ci = colors.length;
    colorIndex[tag] = ci;
    colors.push(generateNextColor(ci, d3_category18));
  }
  return colors[ci];
}

/**
 * A mapping from coref values to the associated visualization color
 */
var corefColorIndex = {};
var corefColors = [];
function corefColor(corefTag) {
  if (corefTag === "MENTION") {
    return '#FFE000';
  } else {
    return getTagColor(corefTag, corefColorIndex, corefColors);
  }
}

var speakerColorIndex = {};
var speakerColors = [];
function speakerColor(tag) {
  return getTagColor(tag, speakerColorIndex, speakerColors);
}


/**
 * A mapping from sentiment value to the associated
 * visualization color
 */
function sentimentColor(sentiment) {
  if (sentiment === "VERY POSITIVE") {
    return '#00FF00';
  } else if (sentiment === "POSITIVE") {
    return '#7FFF00';
  } else if (sentiment === "NEUTRAL") {
    return '#FFFF00';
  } else if (sentiment === "NEGATIVE") {
    return '#FF7F00';
  } else if (sentiment === "VERY NEGATIVE") {
    return '#FF0000';
  } else {
    return '#E3E3E3';
  }
}


/**
 * Get a list of annotators, from the annotator option input.
 */
function annotators() {
  var annotators = "tokenize,ssplit";
  if ($('#language').val() === 'de' | $('#language').val() === 'fr' | $('#language').val() === 'es') {
    annotators += ",mwt";
  } else if ($('#language').val() === 'hu' | $('#language').val() === 'it') {
    annotators = "cdc_tokenize";
  }
  $('#annotators').find('option:selected').each(function () {
    annotators += "," + $(this).val();
  });
  return annotators;
}

/**
 * Get the input date
 */
function date() {
  function f(n) {
    return n < 10 ? '0' + n : n;
  }
  var date = new Date();
  var M = date.getMonth() + 1;
  var D = date.getDate();
  var Y = date.getFullYear();
  var h = date.getHours();
  var m = date.getMinutes();
  var s = date.getSeconds();
  return "" + Y + "-" + f(M) + "-" + f(D) + "T" + f(h) + ':' + f(m) + ':' + f(s);
}


//-----------------------------------------------------------------------------
// Constituency parser
//-----------------------------------------------------------------------------
function ConstituencyParseProcessor() {
  var parenthesize = function (input, list) {
    if (list === undefined) {
      return parenthesize(input, []);
    } else {
      var token = input.shift();
      if (token === undefined) {
        return list.pop();
      } else if (token === "(") {
        list.push(parenthesize(input, []));
        return parenthesize(input, list);
      } else if (token === ")") {
        return list;
      } else {
        return parenthesize(input, list.concat(token));
      }
    }
  };

  var toTree = function (list) {
    if (list.length === 2 && typeof list[1] === 'string') {
      return {label: list[0], text: list[1], isTerminal: true};
    } else if (list.length >= 2) {
      var label = list.shift();
      var node = {label: label};
      var rest = list.map(function (x) {
        var t = toTree(x);
        if (typeof t === 'object') {
          t.parent = node;
        }
        return t;
      });
      node.children = rest;
      return node;
    } else {
      return list;
    }
  };

  var indexTree = function (tree, tokens, index) {
    index = index || 0;
    if (tree.isTerminal) {
      tree.token = tokens[index];
      tree.tokenIndex = index;
      tree.tokenStart = index;
      tree.tokenEnd = index + 1;
      return index + 1;
    } else if (tree.children) {
      tree.tokenStart = index;
      for (var i = 0; i < tree.children.length; i++) {
        var child = tree.children[i];
        index = indexTree(child, tokens, index);
      }
      tree.tokenEnd = index;
    }
    return index;
  };

  var tokenize = function (input) {
    return input.split('"')
      .map(function (x, i) {
        if (i % 2 === 0) { // not in string
          return x.replace(/\(/g, ' ( ')
            .replace(/\)/g, ' ) ');
        } else { // in string
          return x.replace(/ /g, "!whitespace!");
        }
      })
      .join('"')
      .trim()
      .split(/\s+/)
      .map(function (x) {
        return x.replace(/!whitespace!/g, " ");
      });
  };

  var convertParseStringToTree = function (input, tokens) {
    var p = parenthesize(tokenize(input));
    if (Array.isArray(p)) {
      var tree = toTree(p);
      // Correlate tree with tokens
      indexTree(tree, tokens);
      return tree;
    }
  };

  this.process = function(annotation) {
    for (var i = 0; i < annotation.sentences.length; i++) {
      var s = annotation.sentences[i];
      if (s.parse) {
        s.parseTree = convertParseStringToTree(s.parse, s.tokens);
      }
    }
  }
}

// ----------------------------------------------------------------------------
// RENDER
// ----------------------------------------------------------------------------

/**
 * Render a given JSON data structure
 */
function render(data, reverse) {
  // Tweak arguments
  if (typeof reverse !== 'boolean') {
    reverse = false;
  }

  // Error checks
  if (typeof data.sentences === 'undefined') { return; }

  /**
   * Register an entity type (a tag) for Brat
   */
  var entityTypesSet = {};
  var entityTypes = [];
  function addEntityType(name, type, coarseType) {
    if (typeof coarseType === "undefined") {
      coarseType = type;
    }
    // Don't add duplicates
    if (entityTypesSet[type]) return;
    entityTypesSet[type] = true;
    // Get the color of the entity type
    color = '#ffccaa';
    if (name === 'POS') {
      color = posColor(type);
    } else if (name === 'NER') {
      color = nerColor(coarseType);
    } else if (name === 'NNER') {
      color = nerColor(coarseType);
    } else if (name === 'SPEAKER') {
      color = speakerColor(coarseType);
    } else if (name === 'COREF') {
      color = corefColor(coarseType);
    } else if (name === 'ENTITY') {
      color = posColor('NN');
    } else if (name === 'RELATION') {
      color = posColor('VB');
    } else if (name === 'LEMMA') {
      color = '#FFFFFF';
    } else if (name === 'SENTIMENT') {
      color = sentimentColor(type);
    } else if (name === 'LINK') {
      color = '#FFFFFF';
    } else if (name === 'KBP_ENTITY') {
      color = '#FFFFFF';
    }
    // Register the type
    entityTypes.push({
      type: type,
      labels : [type],
      bgColor: color,
      borderColor: 'darken'
    });
  }

  /**
   * Register a relation type (an arc) for Brat
   */
  var relationTypesSet = {};
  var relationTypes = [];
  function addRelationType(type, symmetricEdge) {
    // Prevent adding duplicates
    if (relationTypesSet[type]) return;
    relationTypesSet[type] = true;
    // Default arguments
    if (typeof symmetricEdge === 'undefined') { symmetricEdge = false; }
    // Add the type
    relationTypes.push({
      type: type,
      labels: [type],
      dashArray: (symmetricEdge ? '3,3' : undefined),
      arrowHead: (symmetricEdge ? 'none' : undefined),
    });
  }

  //
  // Construct text of annotation
  //
  currentText = [];  // GLOBAL
  currentSentences = data.sentences;  // GLOBAL
  data.sentences.forEach(function(sentence) {
    for (var i = 0; i < sentence.tokens.length; ++i) {
      var token = sentence.tokens[i];
      var word = token.word;
      if (!(typeof tokensMap[word] === "undefined")) {
        word = tokensMap[word];
      }
      if (i > 0) { currentText.push(' '); }
      token.characterOffsetBegin = currentText.length;
      for (var j = 0; j < word.length; ++j) {
        currentText.push(word[j]);
      }
      token.characterOffsetEnd = currentText.length;
    }
    currentText.push('\n');
  });
  currentText = currentText.join('');

  //
  // Shared variables
  // These are what we'll render in BRAT
  //
  // (pos)
  var posEntities = [];
  // (lemma)
  var lemmaEntities = [];
  // (ner)
  var nerEntities = [];
  var nerEntitiesNormalized = [];
  // (sentiment)
  var sentimentEntities = [];
  // (entitylinking)
  var linkEntities = [];
  // (dependencies)
  var depsRelations = [];
  var deps2Relations = [];
  // (openie)
  var openieEntities = [];
  var openieEntitiesSet = {};
  var openieRelations = [];
  var openieRelationsSet = {};
  // (kbp)
  var kbpEntities = [];
  var kbpEntitiesSet = [];
  var kbpRelations = [];
  var kbpRelationsSet = [];

  var cparseEntities = [];
  var cparseRelations = [];

  var speakerEntities = [];
  //
  // Loop over sentences.
  // This fills in the variables above.
  //
  for (var sentI = 0; sentI < data.sentences.length; ++sentI) {
    var sentence = data.sentences[sentI];
    var index = sentence.index;
    var tokens = sentence.tokens;
    var deps = sentence['basicDependencies'];
    var deps2 = sentence['enhancedPlusPlusDependencies'];
    var parseTree = sentence['parseTree'];

    // Speakers
    if (tokens.length > 0 && typeof tokens[0].speaker !== 'undefined') {
      var speaker = tokens[0].speaker;
      var speakerId = 'S(' + speaker + ')';
      addEntityType('SPEAKER', speakerId);
      var begin = parseInt(tokens[0].characterOffsetBegin);
      var end = parseInt(tokens[tokens.length-1].characterOffsetEnd);
      speakerEntities.push(['sent' + sentI, speakerId, [[begin, end]]]);
    }

    // POS tags
    /**
     * Generate a POS tagged token id
     */
    function posID(i) {
      return 'POS_' + sentI + '_' + i;
    }
    if (tokens.length > 0 && typeof tokens[0].pos !== 'undefined') {
      for (var i = 0; i < tokens.length; i++) {
        var token = tokens[i];
        var pos = token.pos;
        var begin = parseInt(token.characterOffsetBegin);
        var end = parseInt(token.characterOffsetEnd);
        addEntityType('POS', pos);
        posEntities.push([posID(i), pos, [[begin, end]]]);
      }
    }

    // Constituency parse
    // Carries the same assumption as NER
    if (parseTree && !useDagre) {
      var parseEntities = [];
      var parseRels = [];
      function processParseTree(tree, index) {
        tree.visitIndex = index;
        index++;
        if (tree.isTerminal) {
          parseEntities[tree.visitIndex] = posEntities[tree.tokenIndex];
          return index;
        } else if (tree.children) {
          addEntityType('PARSENODE', tree.label);
          parseEntities[tree.visitIndex] =
            ['PARSENODE_' + sentI + '_' + tree.visitIndex, tree.label,
              [[tokens[tree.tokenStart].characterOffsetBegin, tokens[tree.tokenEnd-1].characterOffsetEnd]]];
          var parentEnt = parseEntities[tree.visitIndex];
          for (var i = 0; i < tree.children.length; i++) {
            var child = tree.children[i];
            index = processParseTree(child, index);
            var childEnt = parseEntities[child.visitIndex];
            addRelationType('pc');
            parseRels.push(['PARSEEDGE_' + sentI + '_' + parseRels.length, 'pc', [['parent', parentEnt[0]], ['child', childEnt[0]]]]);
          }
        }
        return index;
      }
      processParseTree(parseTree, 0);
      cparseEntities = cparseEntities.concat(cparseEntities, parseEntities);
      cparseRelations = cparseRelations.concat(parseRels);
    }

    // Dependency parsing
    /**
     * Process a dependency tree from JSON to Brat relations
     */
    function processDeps(name, deps) {
      var relations = [];
      // Format: [${ID}, ${TYPE}, [[${ARGNAME}, ${TARGET}], [${ARGNAME}, ${TARGET}]]]
      for (var i = 0; i < deps.length; i++) {
        var dep = deps[i];
        var governor = dep.governor - 1;
        var dependent = dep.dependent - 1;
        if (governor == -1) continue;
        addRelationType(dep.dep);
        relations.push([name + '_' + sentI + '_' + i, dep.dep, [['governor', posID(governor)], ['dependent', posID(dependent)]]]);
      }
      return relations;
    }
    // Actually add the dependencies
    if (typeof deps !== 'undefined') {
      depsRelations = depsRelations.concat(processDeps('dep', deps));
    }
    if (typeof deps2 !== 'undefined') {
      deps2Relations = deps2Relations.concat(processDeps('dep2', deps2));
    }

    // Lemmas
    if (tokens.length > 0 && typeof tokens[0].lemma !== 'undefined') {
      for (var i = 0; i < tokens.length; i++) {
        var token = tokens[i];
        var lemma = token.lemma;
        var begin = parseInt(token.characterOffsetBegin);
        var end = parseInt(token.characterOffsetEnd);
        addEntityType('LEMMA', lemma);
        lemmaEntities.push(['LEMMA_' + sentI + '_' + i, lemma, [[begin, end]]]);
      }
    }

    // NER tags
    // Assumption: contiguous occurrence of one non-O is a single entity
    if (tokens.some(function(token) { return token.ner; })) {
      for (var i = 0; i < tokens.length; i++) {
        var ner = tokens[i].ner || 'O';
        var normalizedNER = tokens[i].normalizedNER;
        if (typeof normalizedNER === "undefined") {
          normalizedNER = ner;
        }
        if (ner == 'O') continue;
        var j = i;
        while (j < tokens.length - 1 && tokens[j+1].ner == ner) j++;
        addEntityType('NER', ner, ner);
        nerEntities.push(['NER_' + sentI + '_' + i, ner, [[tokens[i].characterOffsetBegin, tokens[j].characterOffsetEnd]]]);
        if (ner != normalizedNER) {
          addEntityType('NNER', normalizedNER, ner);
          nerEntities.push(['NNER_' + sentI + '_' + i, normalizedNER, [[tokens[i].characterOffsetBegin, tokens[j].characterOffsetEnd]]]);

        }
        i = j;
      }
    }

    // Sentiment
    if (typeof sentence.sentiment !== "undefined") {
      var sentiment = sentence.sentiment.toUpperCase().replace("VERY", "VERY ");
      addEntityType('SENTIMENT', sentiment);
      sentimentEntities.push(['SENTIMENT_' + sentI, sentiment,
        [[tokens[0].characterOffsetBegin, tokens[tokens.length - 1].characterOffsetEnd]]]);
    }

    // Entity Links
    // Carries the same assumption as NER
    if (tokens.length > 0) {
      for (var i = 0; i < tokens.length; i++) {
        var link = tokens[i].entitylink;
        if (link == 'O' || typeof link === 'undefined') continue;
        var j = i;
        while (j < tokens.length - 1 && tokens[j+1].entitylink == link) j++;
        addEntityType('LINK', link);
        linkEntities.push(['LINK_' + sentI + '_' + i, link, [[tokens[i].characterOffsetBegin, tokens[j].characterOffsetEnd]]]);
        i = j;
      }
    }

    // Open IE
    // Helper Functions
    function openieID(span) {
      return 'OPENIEENTITY' + '_' + sentI + '_' + span[0] + '_' + span[1];
    }
    function addEntity(span, role) {
      // Don't add duplicate entities
      if (openieEntitiesSet[[sentI, span, role]]) return;
      openieEntitiesSet[[sentI, span, role]] = true;
      // Add the entity
      openieEntities.push([openieID(span), role,
        [[tokens[span[0]].characterOffsetBegin,
          tokens[span[1] - 1].characterOffsetEnd ]] ]);
    }
    function addRelation(gov, dep, role) {
      // Don't add duplicate relations
      if (openieRelationsSet[[sentI, gov, dep, role]]) return;
      openieRelationsSet[[sentI, gov, dep, role]] = true;
      // Add the relation
      openieRelations.push(['OPENIESUBJREL_' + sentI + '_' + gov[0] + '_' + gov[1] + '_' + dep[0] + '_' + dep[1],
                           role,
                           [['governor',  openieID(gov)],
                            ['dependent', openieID(dep)]  ] ]);
    }
    // Render OpenIE
    if (typeof sentence.openie !== 'undefined') {
      // Register the entities + relations we'll need
      addEntityType('ENTITY',  'Entity');
      addEntityType('RELATION', 'Relation');
      addRelationType('subject');
      addRelationType('object');
      // Loop over triples
      for (var i = 0; i < sentence.openie.length; ++i) {
        var subjectSpan = sentence.openie[i].subjectSpan;
        var relationSpan = sentence.openie[i].relationSpan;
        var objectSpan = sentence.openie[i].objectSpan;
        if (parseInt(relationSpan[0]) < 0  || parseInt(relationSpan[1]) < 0) {
          continue;  // This is a phantom relation
        }
        var begin = parseInt(token.characterOffsetBegin);
        // Add the entities
        addEntity(subjectSpan, 'Entity');
        addEntity(relationSpan, 'Relation');
        addEntity(objectSpan, 'Entity');
        // Add the relations
        addRelation(relationSpan, subjectSpan, 'subject');
        addRelation(relationSpan, objectSpan, 'object');
      }
    }  // End OpenIE block


    //
    // KBP
    //
    // Helper Functions
    function kbpEntity(span) {
      return 'KBPENTITY' + '_' + sentI + '_' + span[0] + '_' + span[1];
    }
    function addKBPEntity(span, role) {
      // Don't add duplicate entities
      if (kbpEntitiesSet[[sentI, span, role]]) return;
      kbpEntitiesSet[[sentI, span, role]] = true;
      // Add the entity
      kbpEntities.push([kbpEntity(span), role,
        [[tokens[span[0]].characterOffsetBegin,
          tokens[span[1] - 1].characterOffsetEnd ]] ]);
    }
    function addKBPRelation(gov, dep, role) {
      // Don't add duplicate relations
      if (kbpRelationsSet[[sentI, gov, dep, role]]) return;
      kbpRelationsSet[[sentI, gov, dep, role]] = true;
      // Add the relation
      kbpRelations.push(['KBPRELATION_' + sentI + '_' + gov[0] + '_' + gov[1] + '_' + dep[0] + '_' + dep[1],
                           role,
                           [['governor',  kbpEntity(gov)],
                            ['dependent', kbpEntity(dep)]  ] ]);
    }
    if (typeof sentence.kbp !== 'undefined') {
      // Register the entities + relations we'll need
      addRelationType('subject');
      addRelationType('object');
      // Loop over triples
      for (var i = 0; i < sentence.kbp.length; ++i) {
        var subjectSpan = sentence.kbp[i].subjectSpan;
        var subjectLink = 'Entity';
        for (var k = subjectSpan[0]; k < subjectSpan[1]; ++k) {
          if (subjectLink == 'Entity' &&
              typeof tokens[k] !== 'undefined' &&
              tokens[k].entitylink != 'O' &&
              typeof tokens[k].entitylink !== 'undefined') {
            subjectLink = tokens[k].entitylink
          }
        }
        addEntityType('KBP_ENTITY',  subjectLink);
        var objectSpan = sentence.kbp[i].objectSpan;
        var objectLink = 'Entity';
        for (var k = objectSpan[0]; k < objectSpan[1]; ++k) {
          if (objectLink == 'Entity' &&
              typeof tokens[k] !== 'undefined' &&
              tokens[k].entitylink != 'O' &&
              typeof tokens[k].entitylink !== 'undefined') {
            objectLink = tokens[k].entitylink
          }
        }
        addEntityType('KBP_ENTITY',  objectLink);
        var relation = sentence.kbp[i].relation;
        var begin = parseInt(token.characterOffsetBegin);
        // Add the entities
        addKBPEntity(subjectSpan, subjectLink);
        addKBPEntity(objectSpan, objectLink);
        // Add the relations
        addKBPRelation(subjectSpan, objectSpan, relation);
      }
    }  // End KBP block

  }  // End sentence loop

  //
  // Coreference
  //
  var corefEntities = [];
  var corefRelations = [];
  corefColors = [];
  corefColorIndex = {};
  speakerColors = [];
  speakerColorIndex = {};
  if (typeof data.corefs !== 'undefined') {
    addRelationType('coref', true);
    addEntityType('COREF', 'Mention');
    var clusters = Object.keys(data.corefs);
    clusters.forEach( function (clusterId) {
      var chain = data.corefs[clusterId];
      if (chain.length > 1) {
        var entityChainId = 'CorefEntity' + clusterId;
        addEntityType('COREF', entityChainId);
        for (var i = 0; i < chain.length; ++i) {
          var mention = chain[i];
          var id = 'COREF' + mention.id;
          var tokens = data.sentences[mention.sentNum - 1].tokens;
          corefEntities.push([id, entityChainId,
            [[tokens[mention.startIndex - 1].characterOffsetBegin,
              tokens[mention.endIndex - 2].characterOffsetEnd      ]] ]);
          // if (i > 0) {
          //   var lastId = 'COREF' + chain[i - 1].id;
          //   corefRelations.push(['COREF' + chain[i-1].id + '_' + chain[i].id,
          //                        'coref',
          //                        [['governor', lastId],
          //                         ['dependent', id]    ] ]);
          // }
        }
      }
    });
  }  // End coreference block

  //
  // Actually render the elements
  //

  /**
   * Helper function to render a given set of entities / relations
   * to a Div, if it exists.
   */
  function embed(container, entities, relations, reverse) {
    var text = currentText;
    if (reverse) {
      var length = currentText.length;
      for (var i = 0; i < entities.length; ++i) {
        var offsets = entities[i][2][0];
        var tmp = length - offsets[0];
        offsets[0] = length - offsets[1];
        offsets[1] = tmp;
      }
      text = text.split("").reverse().join("");
    }
    if ($('#' + container).length > 0) {
      Util.embed(container,
                 {entity_types: entityTypes, relation_types: relationTypes},
                 {text: text, entities: entities, relations: relations}
                );
    }
  }

  // Render each annotation
  head.ready(function() {
    embed('pos', posEntities);
    embed('lemma', lemmaEntities);
    embed('ner', nerEntities);
    embed('entities', linkEntities);
    if (!useDagre) {
      embed('parse', cparseEntities, cparseRelations);
    }
    embed('deps', posEntities, depsRelations);
    embed('deps2', posEntities, deps2Relations);
    if (speakerEntities.length) {
      embed('speakers', speakerEntities);
    }
    embed('coref', corefEntities, corefRelations);
    embed('openie', openieEntities, openieRelations);
    embed('kbp',    kbpEntities, kbpRelations);
    embed('sentiment', sentimentEntities);

    // Constituency parse
    // Uses d3 and dagre-d3 (not brat)
    if ($('#parse').length > 0 && useDagre) {
      var parseViewer = new ParseViewer({ selector: '#parse' });
      parseViewer.showAnnotation(data);
      $('#parse').addClass('svg').css('display', 'block');
    }
  });

}  // End render function


/**
 * Render a TokensRegex response
 */
function renderTokensregex(data) {
  /**COREF'
   * Register an entity type (a tag) for Brat
   */
  var entityTypesSet = {};
  var entityTypes = [];
  function addEntityType(type, color) {
    // Don't add duplicates
    if (entityTypesSet[type]) return;
    entityTypesSet[type] = true;
    // Set the color
    if (typeof color === 'undefined') {
      color = '#ADF6A2';
    }
    // Register the type
    entityTypes.push({
      type: type,
      labels : [type],
      bgColor: color,
      borderColor: 'darken'
    });
  }

  var entities = [];
  for (var sentI = 0; sentI < data.sentences.length; ++sentI) {
    var tokens = currentSentences[sentI].tokens;
    for (var matchI = 0; matchI < data.sentences[sentI].length; ++matchI) {
      var match = data.sentences[sentI][matchI];
      // Add groups
      for (groupName in match) {
        if (groupName.startsWith("$") || isInt(groupName)) {
          addEntityType(groupName, '#FFFDA8');
          var begin = parseInt(tokens[match[groupName].begin].characterOffsetBegin);
          var end = parseInt(tokens[match[groupName].end - 1].characterOffsetEnd);
          entities.push(['TOK_' + sentI + '_' + matchI + '_' + groupName,
                              groupName,
                              [[begin, end]]]);
        }
      }
      // Add match
      addEntityType('match', '#ADF6A2');
      var begin = parseInt(tokens[match.begin].characterOffsetBegin);
      var end = parseInt(tokens[match.end - 1].characterOffsetEnd);
      entities.push(['TOK_' + sentI + '_' + matchI + '_match',
                          'match',
                          [[begin, end]]]);
    }
  }

  Util.embed('tokensregex',
         {entity_types: entityTypes, relation_types: []},
         {text: currentText, entities: entities, relations: []}
        );
}  // END renderTokensregex()


/**
 * Render a Semgrex response
 */
function renderSemgrex(data) {
  /**
   * Register an entity type (a tag) for Brat
   */
  var entityTypesSet = {};
  var entityTypes = [];
  function addEntityType(type, color) {
    // Don't add duplicates
    if (entityTypesSet[type]) return;
    entityTypesSet[type] = true;
    // Set the color
    if (typeof color === 'undefined') {
      color = '#ADF6A2';
    }
    // Register the type
    entityTypes.push({
      type: type,
      labels : [type],
      bgColor: color,
      borderColor: 'darken'
    });
  }


  relationTypes = [{
    type: 'semgrex',
    labels: ['-'],
    dashArray: '3,3',
    arrowHead: 'none',
  }];

  var entities = [];
  var relations = [];

  for (var sentI = 0; sentI < data.sentences.length; ++sentI) {
    var tokens = currentSentences[sentI].tokens;
    for (var matchI = 0; matchI < data.sentences[sentI].length; ++matchI) {
      var match = data.sentences[sentI][matchI];
      // Add match
      addEntityType('match', '#ADF6A2');
      var begin = parseInt(tokens[match.begin].characterOffsetBegin);
      var end = parseInt(tokens[match.end - 1].characterOffsetEnd);
      entities.push(['SEM_' + sentI + '_' + matchI + '_match',
                          'match',
                          [[begin, end]]]);

      // Add groups
      for (groupName in match) {
        if (groupName.startsWith("$") || isInt(groupName)) {
          // (add node)
          group = match[groupName];
          groupName = groupName.substring(1);
          addEntityType(groupName, '#FFFDA8');
          var begin = parseInt(tokens[group.begin].characterOffsetBegin);
          var end = parseInt(tokens[group.end - 1].characterOffsetEnd);
          entities.push(['SEM_' + sentI + '_' + matchI + '_' + groupName,
                              groupName,
                              [[begin, end]]]);

          // (add relation)
          relations.push(['SEMGREX_' + sentI + '_' + matchI + '_' + groupName,
                          'semgrex',
                          [['governor', 'SEM_' + sentI + '_' + matchI + '_match'],
                           ['dependent', 'SEM_' + sentI + '_' + matchI + '_' + groupName] ] ]);
        }
      }
    }
  }

  Util.embed('semgrex',
         {entity_types: entityTypes, relation_types: relationTypes},
         {text: currentText, entities: entities, relations: relations}
        );
}  // END renderSemgrex

/**
 * Render a Tregex response
 */
function renderTregex(data) {
  $('#tregex').empty();
  $('#tregex').append('<pre>' + JSON.stringify(data, null, 4) + '</pre>');
}  // END renderTregex

// ----------------------------------------------------------------------------
// MAIN
// ----------------------------------------------------------------------------

/**
 * MAIN()
 *
 * The entry point of the page
 */
$(document).ready(function() {
  // Some initial styling
  $('.chosen-select').chosen();
  $('.chosen-container').css('width', '100%');


  // Language-specific changes
  $('#language').on('change', function() {
    $('#text').attr('dir', '');
    if ($('#language').val() === 'ar') {
      $('#text').attr('dir', 'rtl');
      $('#text').attr('placeholder', 'على سبيل المثال، قفز الثعلب البني السريع فوق الكلب الكسول.');
    } else if ($('#language').val() === 'en') {
      $('#text').attr('placeholder', 'e.g., The quick brown fox jumped over the lazy dog.');
    } else if ($('#language').val() === 'zh') {
      $('#text').attr('placeholder', '例如，快速的棕色狐狸跳過了懶惰的狗。');
    } else if ($('#language').val() === 'fr') {
      $('#text').attr('placeholder', 'Par exemple, le renard brun rapide a sauté sur le chien paresseux.');
    } else if ($('#language').val() === 'de') {
      $('#text').attr('placeholder', 'Z. B. sprang der schnelle braune Fuchs über den faulen Hund.');
    } else if ($('#language').val() === 'it') {
      $('#text').attr('placeholder', 'Roma sorge sulle rive del fiume Tevere.');
    } else if ($('#language').val() === 'hu') {
      $('#text').attr('placeholder', 'Budapest az ország politikai, kulturális, kereskedelmi, ipari és közlekedési központja.');
    } else if ($('#language').val() === 'es') {
      $('#text').attr('placeholder', 'Por ejemplo, el rápido zorro marrón saltó sobre el perro perezoso.');
    } else {
      $('#text').attr('placeholder', 'Unknown language for placeholder query: ' + $('#language').val());

    }
  });

  // Submit on shift-enter
  $('#text').keydown(function (event) {
    if (event.keyCode == 13) {
      if(event.shiftKey){
        event.preventDefault();  // don't register the enter key when pressed
        return false;
      }
    }
  });
  $('#text').keyup(function (event) {
    if (event.keyCode == 13) {
      if(event.shiftKey){
        $('#submit').click();  // submit the form when the enter key is released
        event.stopPropagation();
        return false;
      }
    }
  });

  // Submit on clicking the 'submit' button
  $('#submit').click(function() {
    // Get the text to annotate
    currentQuery = $('#text').val();
    if (currentQuery.trim() == '') {
      if ($('#language').val() === 'ar') {
        currentQuery = 'قفز الثعلب البني السريع فوق الكلب الكسول.';
      } else if ($('#language').val() === 'en') {
        currentQuery = 'The quick brown fox jumped over the lazy dog.';
      } else if ($('#language').val() === 'zh') {
        currentQuery = '快速的棕色狐狸跳过了懒惰的狗';
      } else if ($('#language').val() === 'fr') {
        currentQuery = 'Le renard brun rapide a sauté sur le chien paresseux.';
      } else if ($('#language').val() === 'de') {
        currentQuery = 'Sprang der schnelle braune Fuchs über den faulen Hund.';
      } else if ($('#language').val() === 'es') {
        currentQuery = 'El rápido zorro marrón saltó sobre el perro perezoso.';
      } else if ($('#language').val() === 'it') {
        currentQuery = 'Roma sorge sulle rive del fiume Tevere.';
      } else if ($('#language').val() === 'hu') {
        currentQuery = 'Budapest az ország politikai, kulturális, kereskedelmi, ipari és közlekedési központja.';
      } else {
        currentQuery = 'Unknown language for default query: ' + $('#language').val();
      }
      $('#text').val(currentQuery);
    }
    // Update the UI
    $('#submit').prop('disabled', true);
    $('#annotations').hide();
    $('#patterns_row').hide();
    $('#loading').show();

    // Run query
    $.ajax({
      type: 'POST',
      url: serverAddress + '?properties=' + encodeURIComponent(
        '{"annotators": "' + annotators() + '", "date": "' + date() + '"}') +
        '&pipelineLanguage=' + encodeURIComponent($('#language').val()),
      data: encodeURIComponent(currentQuery), //jQuery doesn't automatically URI encode strings
      dataType: 'json',
      contentType: "application/x-www-form-urlencoded;charset=UTF-8",
      success: function(data) {
        $('#submit').prop('disabled', false);
        if (typeof data === 'undefined' || data.sentences == undefined) {
          alert("Failed to reach server!");
        } else {
          // Process constituency parse
          var constituencyParseProcessor = new ConstituencyParseProcessor();
          constituencyParseProcessor.process(data);
          // Empty divs
          $('#annotations').empty();
          // Re-render divs
          function createAnnotationDiv(id, annotator, selector, label) {
            // (make sure we requested that element)
            if (annotators().indexOf(annotator) < 0) {
              return;
            }
            // (make sure the data contains that element)
            ok = false;
            if (typeof data[selector] !== 'undefined') {
              ok = true;
            } else if (typeof data.sentences !== 'undefined' && data.sentences.length > 0) {
              if (typeof data.sentences[0][selector] !== 'undefined') {
                ok = true;
              } else if (typeof data.sentences[0].tokens != 'undefined' && data.sentences[0].tokens.length > 0) {
                // (make sure the annotator select is in at least one of the tokens of any sentence)
                ok = data.sentences.some(function(sentence) {
                  return sentence.tokens.some(function(token) {
                    return typeof token[selector] !== 'undefined';
                  });
                });
              }
            }
            // (render the element)
            if (ok) {
              $('#annotations').append('<h4 class="red">' + label + ':</h4> <div id="' + id + '"></div>');
            }
          }
          // (create the divs)
          //                  div id      annotator     field_in_data                          label
          createAnnotationDiv('pos',      'pos',        'pos',                                 'Part-of-Speech'          );
          createAnnotationDiv('lemma',    'lemma',      'lemma',                               'Lemmas'                  );
          createAnnotationDiv('ner',      'ner',        'ner',                                 'Named Entity Recognition');
          createAnnotationDiv('parse',    'parse',      'parseTree',                           'Constituency Parse'      );
          createAnnotationDiv('deps',     'depparse',   'basicDependencies',                   'Basic Dependencies'      );
          createAnnotationDiv('deps2',    'depparse',   'enhancedPlusPlusDependencies',        'Enhanced++ Dependencies' );
          createAnnotationDiv('openie',   'openie',     'openie',                              'Open IE'                 );
          createAnnotationDiv('speakers', 'coref',      'corefs',                              'Speakers'                );
          createAnnotationDiv('coref',    'coref',      'corefs',                              'Coreference'             );
          createAnnotationDiv('entities', 'entitylink', 'entitylink',                          'Wikidict Entities'       );
          createAnnotationDiv('kbp',      'kbp',        'kbp',                                 'KBP Relations'           );
          createAnnotationDiv('sentiment','sentiment',  'sentiment',                           'Sentiment'               );
          // Update UI
          $('#loading').hide();
          $('.corenlp_error').remove();  // Clear error messages
          $('#annotations').show();
          // Render
          var reverse = $('#language').val() === 'ar';
          render(data, reverse);
          // Render patterns
          $('#annotations').append('<h4 class="red" style="margin-top: 4ex;">CoreNLP Tools:</h4>');  // TODO(gabor) a strange place to add this header to
          $('#patterns_row').show();
        }
      },
      error: function(data) {
        DATA = data;
        var alertDiv = $('<div/>').addClass('alert').addClass('alert-danger').addClass('alert-dismissible').addClass('corenlp_error').attr('role', 'alert')
        var button = $('<button type="button" class="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;</span></button>');
        var message = $('<span/>').text(data.responseText);
        button.appendTo(alertDiv);
        message.appendTo(alertDiv);
        $('#loading').hide();
        alertDiv.appendTo($('#errors'));
        $('#submit').prop('disabled', false);
      }
    });
    event.preventDefault();
    event.stopPropagation();
    return false;
  });


  // Support passing parameters on page launch, via window.location.hash parameters.
  // Example: http://localhost:9000/#text=foo%20bar&annotators=pos,lemma,ner
  (function() {
    var rawParams = window.location.hash.slice(1).split("&");
    var params = {};
    rawParams.forEach(function(paramKV) {
      paramKV = paramKV.split("=");
      if (paramKV.length === 2) {
        var key   = paramKV[0];
        var value = paramKV[1];
        params[key] = value;
      }
    });
    if (params.text) {
      var text = decodeURIComponent(params.text);
      $('#text').val(text);
    }
    if (params.annotators) {
      var annotators = params.annotators.split(",");
      // De-select everything
      $('#annotators').find('option').each(function() {
        $(this).prop('selected', false);
      });
      // Select the specified ones.
      annotators.forEach(function(a) {
        $('#annotators').find('option[value="'+a+'"]').prop('selected', true);
      });
      // Refresh Chosen
      $('#annotators').trigger('chosen:updated');
    }
    if (params.text || params.annotators) {
      // Finally, let's auto-submit.
      $('#submit').click();
    }
  })();


  $('#form_tokensregex').submit( function (e) {
    // Don't actually submit the form
    e.preventDefault();
    // Get text
    if ($('#tokensregex_search').val().trim() == '') {
      $('#tokensregex_search').val('(?$foxtype [{pos:JJ}]+ ) fox');
    }
    var pattern = $('#tokensregex_search').val();
    // Remove existing annotation
    $('#tokensregex').remove();
    // Make ajax call
    // Previously this would escape the + and & in pattern before the
    // call to encodeURIComponent, but the server doesn't double
    // unescape the incoming patterns, so that was not working
    $.ajax({
      type: 'POST',
      url: serverAddress + '/tokensregex?pattern=' + encodeURIComponent(pattern) +
        '&properties=' + encodeURIComponent(
        '{"annotators": "' + annotators() + '", "date": "' + date() + '"}') +
        '&pipelineLanguage=' + encodeURIComponent($('#language').val()),
      data: encodeURIComponent(currentQuery),
      success: function(data) {
        $('.tokensregex_error').remove();  // Clear error messages
        $('<div id="tokensregex" class="pattern_brat"/>').appendTo($('#div_tokensregex'));
        renderTokensregex(data);
      },
      error: function(data) {
        var alertDiv = $('<div/>').addClass('alert').addClass('alert-danger').addClass('alert-dismissible').addClass('tokensregex_error').attr('role', 'alert')
        var button = $('<button type="button" class="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;</span></button>');
        var message = $('<span/>').text(data.responseText);
        button.appendTo(alertDiv);
        message.appendTo(alertDiv);
        alertDiv.appendTo($('#div_tokensregex'));
      }
    });
  });


  $('#form_semgrex').submit( function (e) {
    // Don't actually submit the form
    e.preventDefault();
    // Get text
    if ($('#semgrex_search').val().trim() == '') {
      $('#semgrex_search').val('{pos:/VB.*/} >nsubj {}=subject >/nmod:.*/ {}=prep_phrase');
    }
    var pattern = $('#semgrex_search').val();
    // Remove existing annotation
    $('#semgrex').remove();
    // Add missing required annotators
    var requiredAnnotators = annotators().split(',');
    if (requiredAnnotators.indexOf('depparse') < 0) {
      requiredAnnotators.push('depparse');
    }
    // Make ajax call
    $.ajax({
      type: 'POST',
      url: serverAddress + '/semgrex?pattern=' + encodeURIComponent(pattern) +
        '&properties=' + encodeURIComponent(
        '{"annotators": "' + requiredAnnotators.join(',') + '", "date": "' + date() + '"}') +
        '&pipelineLanguage=' + encodeURIComponent($('#language').val()),
      data: encodeURIComponent(currentQuery),
      success: function(data) {
        $('.semgrex_error').remove();  // Clear error messages
        $('<div id="semgrex" class="pattern_brat"/>').appendTo($('#div_semgrex'));
        renderSemgrex(data);
      },
      error: function(data) {
        var alertDiv = $('<div/>').addClass('alert').addClass('alert-danger').addClass('alert-dismissible').addClass('semgrex_error').attr('role', 'alert')
        var button = $('<button type="button" class="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;</span></button>');
        var message = $('<span/>').text(data.responseText);
        button.appendTo(alertDiv);
        message.appendTo(alertDiv);
        alertDiv.appendTo($('#div_semgrex'));
      }
    });
  });

  $('#form_tregex').submit( function (e) {
    // Don't actually submit the form
    e.preventDefault();
    // Get text
    if ($('#tregex_search').val().trim() == '') {
      $('#tregex_search').val('NP < NN=animal');
    }
    var pattern = $('#tregex_search').val();
    // Remove existing annotation
    $('#tregex').remove();
    // Add missing required annotators
    var requiredAnnotators = annotators().split(',');
    if (requiredAnnotators.indexOf('parse') < 0) {
      requiredAnnotators.push('parse');
    }
    // Make ajax call
    $.ajax({
      type: 'POST',
      url: serverAddress + '/tregex?pattern=' + encodeURIComponent(pattern) +
        '&properties=' + encodeURIComponent(
        '{"annotators": "' + requiredAnnotators.join(',') + '", "date": "' + date() + '"}') +
        '&pipelineLanguage=' + encodeURIComponent($('#language').val()),
      data: encodeURIComponent(currentQuery),
      success: function(data) {
        $('.tregex_error').remove();  // Clear error messages
        $('<div id="tregex" class="pattern_brat"/>').appendTo($('#div_tregex'));
        renderTregex(data);
      },
      error: function(data) {
        var alertDiv = $('<div/>').addClass('alert').addClass('alert-danger').addClass('alert-dismissible').addClass('tregex_error').attr('role', 'alert')
        var button = $('<button type="button" class="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;</span></button>');
        var message = $('<span/>').text(data.responseText);
        button.appendTo(alertDiv);
        message.appendTo(alertDiv);
        alertDiv.appendTo($('#div_tregex'));
      }
    });
  });

});
