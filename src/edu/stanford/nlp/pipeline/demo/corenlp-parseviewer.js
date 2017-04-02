//'use strict';

//d3 || require('d3');
//var dagreD3 = require('dagre-d3');
//var jquery = require('jquery');
//var $ = jquery;

var ParseViewer = function(params) {
  // Container in which the scene template is displayed
  this.selector = params.selector;
  this.container = $(this.selector);
  this.fitToGraph = true;
  this.onClickNodeCallback = params.onClickNodeCallback;
  this.onHoverNodeCallback = params.onHoverNodeCallback;
  this.init();
  return this;
};

ParseViewer.MIN_WIDTH = 100;
ParseViewer.MIN_HEIGHT = 100;

ParseViewer.prototype.constructor = ParseViewer;

ParseViewer.prototype.getAutoWidth = function () {
  return Math.max(ParseViewer.MIN_WIDTH, this.container.width());
};

ParseViewer.prototype.getAutoHeight = function () {
  return Math.max(ParseViewer.MIN_HEIGHT, this.container.height() - 20);
};

ParseViewer.prototype.init = function () {
  var canvasWidth = this.getAutoWidth();
  var canvasHeight = this.getAutoHeight();
  this.parseElem = d3.select(this.selector)
    .append('svg')
    .attr({'width': canvasWidth, 'height': canvasHeight})
    .style({'width': canvasWidth, 'height': canvasHeight});
  console.log(this.parseElem);
  this.graph = null;
  this.graphRendered = false;

  this.controls = $('<div class="text"></div>');
  this.container.append(this.controls);
};

var GraphBuilder = function(roots) {
  // Create the input graph
  this.graph = new dagreD3.graphlib.Graph()
    .setGraph({})
    .setDefaultEdgeLabel(function () {
      return {};
    });
  this.visitIndex = 0;
  //console.log('building graph', roots);
  for (var i = 0; i < roots.length; i++) {
    this.build(roots[i]);
  }
};

GraphBuilder.prototype.build = function(node) {
  console.log(node);
  // Track my visit index
  this.visitIndex++;
  node.visitIndex = this.visitIndex;

  // Add a node
  var nodeData = node;  // TODO: replace with semantic data
  var nodeLabel = node.label;
  var nodeIndex = node.visitIndex;
  var nodeClass = 'parse-RULE';

  this.graph.setNode(nodeIndex, { label: nodeLabel, class: nodeClass, data: nodeData });
  if (node.parent) {
    this.graph.setEdge(node.parent.visitIndex, nodeIndex, {
      class: 'parse-EDGE'
    });
  }

  if (node.isTerminal) {
    this.visitIndex++;
    nodeIndex = this.visitIndex;
    nodeLabel = node.text;
    nodeClass = 'parse-TERMINAL';

    this.graph.setNode(nodeIndex, { label: nodeLabel, class: nodeClass, data: nodeData });
    this.graph.setEdge(node.visitIndex, nodeIndex, {
      class: 'parse-EDGE'
    });
  } else if (node.children) {
    for (var i = 0; i < node.children.length; i++) {
      this.build(node.children[i]);
    }
  }
};

ParseViewer.prototype.updateGraphPosition = function (svg, g, minWidth, minHeight) {
  if (this.fitToGraph) {
    minWidth = g.graph().width;
    minHeight = this.getAutoHeight();
  }
  adjustGraphPositioning(svg, g, minWidth, minHeight);
};

function adjustGraphPositioning(svg, g, minWidth, minHeight) {
  // Resize svg
  var newWidth = Math.max(minWidth, g.graph().width);
  var newHeight = Math.max(minHeight, g.graph().height + 40);
  svg.attr({'width': newWidth, 'height': newHeight});
  svg.style({'width': newWidth, 'height': newHeight});
  // Center the graph
  var svgGroup = svg.select('g');
  var xCenterOffset = (svg.attr('width') - g.graph().width) / 2;
  svgGroup.attr('transform', 'translate(' + xCenterOffset + ', 20)');
  svg.attr('height', g.graph().height + 40);
  svg.style('height', g.graph().height + 40);
}

ParseViewer.prototype.renderGraph = function (svg, g, parse) {
  // Create the renderer
  var render = new dagreD3.render();
  // Run the renderer. This is what draws the final graph.
  var svgGroup = svg.select('g');
  render(svgGroup, g);

  var scope = this;
  var nodes = svgGroup.selectAll('g.node');
  nodes.on('click',
    function (d) {
      var v = d;
      var node = g.node(v);
      if (scope.onClickNodeCallback) {
        scope.onClickNodeCallback(node.data);
      }
      console.log(g.node(v));
    }
  );

  nodes.on('mouseover',
    function (d) {
      var v = d;
      var node = g.node(v);
      if (scope.onHoverNodeCallback) {
        scope.onHoverNodeCallback(node.data);
      }
    }
  );

  this.updateGraphPosition(svg, g, svg.attr('width'), svg.attr('height'));
  this.graphRendered = true;
};

ParseViewer.prototype.showParse = function (root) {
  this.showParses([root]);
};

ParseViewer.prototype.showParses = function (roots) {
  // Take parse and create a graph
  var gb = new GraphBuilder(roots);
  var g = gb.graph;

  g.nodes().forEach(function (v) {
    var node = g.node(v);
    // Round the corners of the nodes
    node.rx = node.ry = 5;
  });

  var svg = this.parseElem;
  svg.selectAll('*').remove();
  var svgGroup = svg.append('g');
  this.graph = g;
  this.parse = roots;
  if (this.container.is(':visible')) {
    if (roots.length > 0) {
      this.renderGraph(svg, this.graph, this.parse);
    }
  } else {
    this.graphRendered = false;
  }
};

ParseViewer.prototype.showAnnotation = function (annotation) {
  var parses = [];
  for (var i = 0; i < annotation.sentences.length; i++) {
    var s = annotation.sentences[i];
    if (s && s.parseTree) {
      parses.push(s.parseTree);
    }
  }
  this.showParses(parses);
};

ParseViewer.prototype.onResize = function () {
  var canvasWidth = this.getAutoWidth();
  var canvasHeight = this.getAutoHeight();
  var svg = this.parseElem;

  // Center the graph
  var svgGroup = svg.select('g');
  if (svgGroup && this.graph) {
    if (!this.graphRendered) {
      svg.attr({'width': canvasWidth, 'height': canvasHeight});
      svg.style({'width': canvasWidth, 'height': canvasHeight});
      this.renderGraph(svg, this.graph, this.parse);
    } else {
      this.updateGraphPosition(svg, this.graph, canvasWidth, canvasHeight);
    }
  } else {
    svg.attr({'width': canvasWidth, 'height': canvasHeight});
    svg.style({'width': canvasWidth, 'height': canvasHeight});
  }
};

// Exports
//module.exports = ParseViewer;