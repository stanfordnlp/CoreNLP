// Takes Stanford CoreNLP JSON output (var data = ... in data.js)
// and uses brat to render everything.

// TODO: use different colors for POS tags
// TODO: multiple sentences

var bratLocation = 'http://nlp.stanford.edu/js/brat';
head.js(
  // External libraries
  bratLocation + '/client/lib/jquery.svg.min.js',
  bratLocation + '/client/lib/jquery.svgdom.min.js',

  // brat helper modules
  bratLocation + '/client/src/configuration.js',
  bratLocation + '/client/src/util.js',
  bratLocation + '/client/src/annotation_log.js',
  bratLocation + '/client/lib/webfont.js',

  // brat modules
  bratLocation + '/client/src/dispatcher.js',
  bratLocation + '/client/src/url_monitor.js',
  bratLocation + '/client/src/visualizer.js'
);

var colors = ['red'];

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

function render(data) {


  var entityTypesSet = {};
  var entityTypes = [];
  function addEntityType(name, type) {
    if (entityTypesSet[type]) return;
    entityTypesSet[type] = true;
    entityTypes.push({
      type: type,
      labels : [type],
      bgColor: (name == 'POS' ? '#7fa2ff' : '#ffccaa'),
      borderColor: 'darken'
    });
  }
  
  var relationTypesSet = {};
  var relationTypes = [];
  function addRelationType(type) {
    if (relationTypesSet[type]) return;
    relationTypesSet[type] = true;
    relationTypes.push({
      type: type,
      labels: [type],
      //dashArray: '3,3',
      //color: 'purple',
      args: [{role: 'governor', targets: ['NNP']}, {role: 'dependent', targets: ['NNP']}]
    });
  }
  
  function processDeps(name, deps) {
    var relations = [];
    // Format: [${ID}, ${TYPE}, [[${ARGNAME}, ${TARGET}], [${ARGNAME}, ${TARGET}]]]
    for (var i = 0; i < deps.length; i++) {
      var dep = deps[i];
      var governor = dep.governor - 1;
      var dependent = dep.dependent - 1;
      if (governor == -1) continue;
      addRelationType(dep.dep);
      relations.push([name + i, dep.dep, [['governor', 'POS' + governor], ['dependent', 'POS' + dependent]]]);
    }
    return relations;
  }





  data.sentences.slice(0, 1).forEach(function(sentence) {
    var index = sentence.index;
    var tokens = sentence.tokens;
    var deps = sentence['basic-dependencies'];
    var deps2 = sentence['collapsed-ccprocessed-dependencies'];
  
    // Reconstruct the text from the tokens
    var text = [];
    for (var i = 0; i < tokens.length; i++) {
      var token = tokens[i];
      var word = token.word;
      if (tokensMap[word]) word = tokensMap[word];
      var begin = parseInt(token.characterOffsetBegin);
      var end = parseInt(token.characterOffsetEnd);
      for (var j = begin; j < end; j++)
        text[j] = word[j - begin];
    }
    for (var j = 0; j < text.length; j++)
      if (!text[j]) text[j] = ' ';
    text = text.join('');
  
    // POS tags
    var posEntities = [];
    for (var i = 0; i < tokens.length; i++) {
      var token = tokens[i];
      var pos = token.pos;
      var begin = parseInt(token.characterOffsetBegin);
      var end = parseInt(token.characterOffsetEnd);
      addEntityType('POS', pos);
      posEntities.push(['POS' + i, pos, [[begin, end]]]);
    }
  
    // NER tags
    // Assumption: contiguous occurrence of one non-O is a single entity
    var nerEntities = [];
    for (var i = 0; i < tokens.length; i++) {
      var ner = tokens[i].ner;
      if (ner == 'O') continue;
      var j = i;
      while (tokens[j+1].ner == ner) j++;
      addEntityType('NER', ner);
      nerEntities.push(['NER' + i, ner, [[tokens[i].characterOffsetBegin, tokens[j].characterOffsetEnd]]]);
      i = j;
    }
  
    // Dependency parsing
    var depsRelations = processDeps('dep', deps);
    var deps2Relations = processDeps('dep2', deps2);
  
    head.ready(function() {
      Util.embed('pos', {entity_types: entityTypes}, {text: text, entities: posEntities});
      Util.embed('ner', {entity_types: entityTypes}, {text: text, entities: nerEntities});
      Util.embed('deps', {entity_types: entityTypes, relation_types: relationTypes}, {text: text, entities: posEntities, relations: depsRelations});
      Util.embed('deps2', {entity_types: entityTypes, relation_types: relationTypes}, {text: text, entities: posEntities, relations: deps2Relations});
    });
  });
}


$(document).ready(function() {
  $('#submit').click(function() {
    // Get the text to annotate
    text = $('#text').val();
    if (text == '') {
      text = 'My dog likes to eat sausage.';
      $('#text').val(text);
    }
    // Update the UI
    $('#submit').prop('disabled', true);
    $('#annotations').hide();
    $('#loading').show();

    // Run query
    $.ajax({
      type: 'POST',
      url: '/',
      data: text,
      success: function(data) {
        $('#submit').prop('disabled', false);
        if (typeof data == undefined || data.sentences == undefined) {
          alert("Failed to reach server!");
        } else {
          // Empty divs
          $('#annotations').empty();
          $('#annotations').append('<h4 class="red">Part-of-Speech:</h4> <div id="pos"></div>');
          $('#annotations').append('<h4 class="red">Named Entity Recognition:</h4> <div id="ner"></div>');
          $('#annotations').append('<h4 class="red">Basic Dependencies:</h4> <div id="deps"></div>');
          $('#annotations').append('<h4 class="red">Enhanced Dependencies:</h4> <div id="deps2"></div>');
          // Update UI
          $('#loading').hide();
          $('#annotations').show();
          // Render
          render(data);
        }
      }
    });
  });
});
