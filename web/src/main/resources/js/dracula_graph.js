/*
 *  Dracula Graph Layout and Drawing Framework 0.0.3alpha
 *  (c) 2010 Johann Philipp Strathausen <strathausen@gmail.com>
 *  http://strathausen.eu
 *  
 *  Contributions by Jake Stothard <stothardj@gmail.com>.
 *
 *  based on the Graph JavaScript framework, version 0.0.1
 *  (c) 2006 Aslak Hellesoy <aslak.hellesoy@gmail.com>
 *  (c) 2006 Dave Hoover <dave.hoover@gmail.com>
 *
 *  Ported from Graph::Layouter::Spring in
 *    http://search.cpan.org/~pasky/Graph-Layderer-0.02/
 *  The algorithm is based on a spring-style layouter of a Java-based social
 *  network tracker PieSpy written by Paul Mutton <paul@jibble.org>.
 *
 *  This code is freely distributable under the MIT license. Commercial use is
 *  hereby granted without any cost or restriction.
 *
 *  Links:
 *
 *  Graph Dracula JavaScript Framework:
 *      http://graphdracula.net
 *
 /*--------------------------------------------------------------------------*/



/*
 * Dracula
 */
var Dracula = function() {
  this.nodes = {};
  this.edges = [];
  this.snapshots = []; // previous graph states TODO to be implemented
};
var Graph = Dracula;
Dracula.prototype = {
  /*
   * add a node
   * @id          the node's ID (string or number)
   * @content     (optional, dictionary) can contain any information that is
   *              being interpreted by the layout algorithm or the graph
   *              representation
   */
  addNode: function(id, content) {
    /* testing if node is already existing in the graph */
    if(this.nodes[id] === undefined) {
      this.nodes[id] = new Dracula.Node(id, content);
    }
    return this.nodes[id];
  },

  addEdge: function(source, target, style) {
    var s = this.addNode(source);
    var t = this.addNode(target);
    var edge = new Dracula.Edge({ source: s, target: t, style: style });
    s.edges.push(edge);
    this.edges.push(edge);
    // NOTE: Even directed edges are added to both nodes.
    t.edges.push(edge);
    return edge;
  },

  /* TODO to be implemented
   * Preserve a copy of the graph state (nodes, positions, ...)
   * @comment     a comment describing the state
   */
  snapShot: function(comment) {
    // FIXME
    //var graph = new Dracula();
    //graph.nodes = jQuery.extend(true, {}, this.nodes);
    //graph.edges = jQuery.extend(true, {}, this.edges);
    //this.snapshots.push({comment: comment, graph: graph});
  },
  removeNode: function(id) {
    if (this.nodes[id] && this.node[id].shape) {
      this.nodes[id].shape.remove();
      delete this.nodes[id];
    }
    for(var i = 0; i < this.edges.length; i++) {
      if (this.edges[i].source.id == id || this.edges[i].target.id == id) {
        this.edges[i].connection.fg.remove();
        this.edges[i].connection.label.remove();
        this.edges.splice(i, 1);
        i--;
      }
    }
  }
};

/*
 * Edge
 */
Dracula.Edge = function DraculaEdge(opts) {
  this.source = opts.source;
  this.target = opts.target;
  if (opts.style) {
    this.style = jQuery.extend(true, {}, Dracula.Edge.style, opts.style);
  } else {
    this.style = jQuery.extend(true, {}, Dracula.Edge.style);
  }
};
Dracula.Edge.style = {
  directed: false
};
Dracula.Edge.prototype = {
  weight: 0,
  hide: function hideEdge() {
    this.connection.fg.hide();
    this.connection.bg && this.bg.connection.hide();
  }
};

/*
 * Node
 */
Dracula.Node = function DraculaNode(id, node){
  var i;
  node = node || {};
  node.id = id;
  node.edges = [];
  node.hide = function() {
    var i;
    this.hidden = true;
    this.shape && this.shape.hide(); /* FIXME this is representation specific code and should be elsewhere */
    for(i in this.edges)
      (this.edges[i].source.id == id || this.edges[i].target == id) && this.edges[i].hide && this.edges[i].hide();
  };
  node.show = function() {
    var i;
    this.hidden = false;
    this.shape && this.shape.show();
    for(i in this.edges)
      (this.edges[i].source.id == id || this.edges[i].target == id) &&
      this.edges[i].show && this.edges[i].show();
  };
  return node;
};
Dracula.Node.prototype = {
};

/*
 * Renderer Base Class
 */
Dracula.Renderer = {};

/*
 * Renderer implementation using RaphaelJS
 */
Dracula.Renderer.Raphael = function(element, graph, width, height) {
  this.width = width || 400;
  this.height = height || 400;
  this.r = Raphael(element, this.width, this.height);
  this.radius = 40; /* max dimension of a node */
  this.graph = graph;
  this.mouse_in = false;

  /* TODO default node rendering function */
  if(!this.graph.render) {
    this.graph.render = function() {
      return;
    };
  }
  this.draw();
};


/* Moved this default node renderer function out of the main prototype code
 * so it can be override by default */
Dracula.Renderer.defaultRenderFunc = function(r, node) {
  /* the default node drawing */
  var color = Raphael.getColor();
  var ellipse = r.ellipse(0, 0, 30, 20).attr({
    fill: node.fill || color,
    stroke: node.stroke || color,
    "stroke-width": 2
  });
  /* set DOM node ID */
  ellipse.node.id = node.id || node.label;
  if(node.class)ellipse.node.classList.add(node.class);
  shape = r.set().push(ellipse).push(r.text(0, 30, node.label || node.id));
  return shape;
};


Dracula.Renderer.Raphael.prototype = {
  translate: function(point) {
    return [
      (point[0] - this.graph.layoutMinX) * this.factorX + this.radius,
      (point[1] - this.graph.layoutMinY) * this.factorY + this.radius
    ];
  },

  rotate: function(point, length, angle) {
    var dx = length * Math.cos(angle);
    var dy = length * Math.sin(angle);
    return [point[0]+dx, point[1]+dy];
  },

  draw: function() {
    var i;
    this.factorX = (this.width - 2 * this.radius) / (this.graph.layoutMaxX - this.graph.layoutMinX);
    this.factorY = (this.height - 2 * this.radius) / (this.graph.layoutMaxY - this.graph.layoutMinY);
    for (i in this.graph.nodes) {
      this.drawNode(this.graph.nodes[i]);
    }
    for (i = 0; i < this.graph.edges.length; i++) {
      this.drawEdge(this.graph.edges[i]);
    }
  },

  drawNode: function(node) {
    var point = this.translate([node.layoutPosX, node.layoutPosY]);
    node.point = point;
    var r = this.r;
    var graph = this.graph;

    /* if node has already been drawn, move the nodes */
    if(node.shape) {
      var oBBox = node.shape.getBBox();
      var opoint = {
        x: oBBox.x + oBBox.width / 2,
        y: oBBox.y + oBBox.height / 2
      };
      node.shape.translate(
        Math.round(point[0] - opoint.x), Math.round(point[1] - opoint.y)
      );

      this.r.safari();
      return node;
    }/* else, draw new nodes */

    var shape;

    /* if a node renderer function is provided by the user, then use it
       or the default render function instead */
    if(!node.render) {
      node.render = Dracula.Renderer.defaultRenderFunc;
    }
    /* or check for an ajax representation of the nodes */
    if(node.shapes) {
      // TODO ajax representation evaluation
    }

    var selfRef = this;
    shape = node.render(this.r, node).hide();

    shape.attr({"fill-opacity": 0.6});
    /* re-reference to the node an element belongs to, needed for dragging all elements of a node */
    shape.items.forEach(function(item) {
      item.set = shape;
      item.node.style.cursor = "move";
    });
    shape.drag(
      function dragMove(dx, dy, x, y) {
        dx = this.set.ox;
        dy = this.set.oy;
        var bBox = this.set.getBBox();
        var newX = x - dx + (bBox.x + bBox.width / 2);
        var newY = y - dy + (bBox.y + bBox.height / 2);
        var clientX =
          x - (newX < 20 ? newX - 20 : newX > r.width - 20 ? newX - r.width + 20 : 0);
        var clientY =
          y - (newY < 20 ? newY - 20 : newY > r.height - 20 ? newY - r.height + 20 : 0);
        this.set.translate(clientX - Math.round(dx), clientY - Math.round(dy));
        for (var i in selfRef.graph.edges) {
          selfRef.graph.edges[i] &&
            selfRef.graph.edges[i].connection && selfRef.graph.edges[i].connection.draw();
        }
        r.safari();
        this.set.ox = clientX;
        this.set.oy = clientY;
      },
      function dragEnter(x, y) {
        this.set.ox = x;
        this.set.oy = y;
        this.animate({ 'fill-opacity': 0.2 }, 500);
      },
      function dragOut() {
        this.animate({ 'fill-opacity': 0.6 }, 500);
      }
    );

    var box = shape.getBBox();
    shape.translate(
      Math.round(point[0] - (box.x + box.width / 2)),
      Math.round(point[1] - (box.y + box.height / 2))
    );
    node.hidden || shape.show();
    node.shape = shape;
  },
  drawEdge: function(edge) {
    /* if this edge already exists the other way around and is undirected */
    if(edge.backedge)
      return;
    if(edge.source.hidden || edge.target.hidden) {
      edge.connection && edge.connection.fg.hide();
      edge.connection.bg && edge.connection.bg.hide();
      return;
    }
    /* if edge already has been drawn, only refresh the edge */
    if(!edge.connection) {
      edge.style && edge.style.callback && edge.style.callback(edge); // TODO move this somewhere else
      edge.connection = this.r.connection(edge.source.shape, edge.target.shape, edge.style);
      return;
    }
    //FIXME showing doesn't work well
    edge.connection.fg.show();
    edge.connection.bg && edge.connection.bg.show();
    edge.connection.draw();
  }
};
Dracula.Layout = {};
Dracula.Layout.Spring = function(graph) {
  this.graph = graph;
  this.iterations = 500;
  this.maxRepulsiveForceDistance = 6;
  this.k = 2;
  this.c = 0.01;
  this.maxVertexMovement = 0.5;
  this.layout();
};
Dracula.Layout.Spring.prototype = {
  layout: function() {
    this.layoutPrepare();
    for (var i = 0; i < this.iterations; i++) {
      this.layoutIteration();
    }
    this.layoutCalcBounds();
  },

  layoutPrepare: function() {
    var i;
    for (i in this.graph.nodes) {
      var node = this.graph.nodes[i];
      node.layoutPosX = 0;
      node.layoutPosY = 0;
      node.layoutForceX = 0;
      node.layoutForceY = 0;
    }

  },

  layoutCalcBounds: function() {
    var minx = Infinity, maxx = -Infinity,
    miny = Infinity, maxy = -Infinity;
    var i;

    for (i in this.graph.nodes) {
      var x = this.graph.nodes[i].layoutPosX;
      var y = this.graph.nodes[i].layoutPosY;

      if(x > maxx) maxx = x;
      if(x < minx) minx = x;
      if(y > maxy) maxy = y;
      if(y < miny) miny = y;
    }

    this.graph.layoutMinX = minx;
    this.graph.layoutMaxX = maxx;
    this.graph.layoutMinY = miny;
    this.graph.layoutMaxY = maxy;
  },

  layoutIteration: function() {
    // Forces on nodes due to node-node repulsions

    var prev = [];
    for(var c in this.graph.nodes) {
      var node1 = this.graph.nodes[c];
      for (var d in prev) {
        var node2 = this.graph.nodes[prev[d]];
        this.layoutRepulsive(node1, node2);

      }
      prev.push(c);
    }

    // Forces on nodes due to edge attractions
    for (var i = 0; i < this.graph.edges.length; i++) {
      var edge = this.graph.edges[i];
      this.layoutAttractive(edge);
    }

    // Move by the given force
    for (i in this.graph.nodes) {
      var node = this.graph.nodes[i];
      var xmove = this.c * node.layoutForceX;
      var ymove = this.c * node.layoutForceY;

      var max = this.maxVertexMovement;
      if(xmove > max) xmove = max;
      if(xmove < -max) xmove = -max;
      if(ymove > max) ymove = max;
      if(ymove < -max) ymove = -max;

      node.layoutPosX += xmove;
      node.layoutPosY += ymove;
      node.layoutForceX = 0;
      node.layoutForceY = 0;
    }
  },

  layoutRepulsive: function(node1, node2) {
    if (typeof node1 == 'undefined' || typeof node2 == 'undefined')
      return;
    var dx = node2.layoutPosX - node1.layoutPosX;
    var dy = node2.layoutPosY - node1.layoutPosY;
    var d2 = dx * dx + dy * dy;
    if(d2 < 0.01) {
      dx = 0.1 * Math.random() + 0.1;
      dy = 0.1 * Math.random() + 0.1;
      d2 = dx * dx + dy * dy;
    }
    var d = Math.sqrt(d2);
    if(d < this.maxRepulsiveForceDistance) {
      var repulsiveForce = this.k * this.k / d;
      node2.layoutForceX += repulsiveForce * dx / d;
      node2.layoutForceY += repulsiveForce * dy / d;
      node1.layoutForceX -= repulsiveForce * dx / d;
      node1.layoutForceY -= repulsiveForce * dy / d;
    }
  },

  layoutAttractive: function(edge) {
    var node1 = edge.source;
    var node2 = edge.target;

    var dx = node2.layoutPosX - node1.layoutPosX;
    var dy = node2.layoutPosY - node1.layoutPosY;
    var d2 = dx * dx + dy * dy;
    if(d2 < 0.01) {
      dx = 0.1 * Math.random() + 0.1;
      dy = 0.1 * Math.random() + 0.1;
      d2 = dx * dx + dy * dy;
    }
    var d = Math.sqrt(d2);
    if(d > this.maxRepulsiveForceDistance) {
      d = this.maxRepulsiveForceDistance;
      d2 = d * d;
    }
    var attractiveForce = (d2 - this.k * this.k) / this.k;
    if(edge.attraction === undefined) edge.attraction = 1;
    attractiveForce *= Math.log(edge.attraction) * 0.5 + 1;

    node2.layoutForceX -= attractiveForce * dx / d;
    node2.layoutForceY -= attractiveForce * dy / d;
    node1.layoutForceX += attractiveForce * dx / d;
    node1.layoutForceY += attractiveForce * dy / d;
  }
};

Dracula.Layout.Ordered = function(graph, order) {
  this.graph = graph;
  this.order = order;
  this.layout();
};
Dracula.Layout.Ordered.prototype = {
  layout: function() {
    this.layoutPrepare();
    this.layoutCalcBounds();
  },

  layoutPrepare: function(order) {
    var node, i;
    for (i in this.graph.nodes) {
      node = this.graph.nodes[i];
      node.layoutPosX = 0;
      node.layoutPosY = 0;
    }
    var counter = 0;
    for (i in this.order) {
      node = this.order[i];
      node.layoutPosX = counter;
      node.layoutPosY = Math.random();
      counter++;
    }
  },

  layoutCalcBounds: function() {
    var minx = Infinity, maxx = -Infinity,
    miny = Infinity, maxy = -Infinity;
    var i;

    for (i in this.graph.nodes) {
      var x = this.graph.nodes[i].layoutPosX;
      var y = this.graph.nodes[i].layoutPosY;

      if(x > maxx) maxx = x;
      if(x < minx) minx = x;
      if(y > maxy) maxy = y;
      if(y < miny) miny = y;
    }

    this.graph.layoutMinX = minx;
    this.graph.layoutMaxX = maxx;

    this.graph.layoutMinY = miny;
    this.graph.layoutMaxY = maxy;
  }
};


Dracula.Layout.OrderedTree = function(graph, order) {
  this.graph = graph;
  this.order = order;
  this.layout();
};

/*
 * OrderedTree is like Ordered but assumes there is one root
 * This way we can give non random positions to nodes on the Y-axis
 * it assumes the ordered nodes are of a perfect binary tree
 */
Dracula.Layout.OrderedTree.prototype = {
  layout: function() {
    this.layoutPrepare();
    this.layoutCalcBounds();
  },

  layoutPrepare: function(order) {
    var node, i;
    for (i in this.graph.nodes) {
      node = this.graph.nodes[i];
      node.layoutPosX = 0;
      node.layoutPosY = 0;
    }
    //to reverse the order of rendering, we need to find out the
    //absolute number of levels we have. simple log math applies.
    var numNodes = this.order.length;
    var totalLevels = Math.floor(Math.log(numNodes) / Math.log(2));

    var counter = 1;
    for (i in this.order) {
      node = this.order[i];
      //rank aka x coordinate
      var rank = Math.floor(Math.log(counter) / Math.log(2));
      //file relative to top
      var file = counter - Math.pow(rank, 2);

      node.layoutPosX = totalLevels - rank;
      node.layoutPosY = file;
      counter++;
    }
  },

  layoutCalcBounds: function() {
    var minx = Infinity, maxx = -Infinity,
    miny = Infinity, maxy = -Infinity;
    var i;

    for (i in this.graph.nodes) {
      var x = this.graph.nodes[i].layoutPosX;
      var y = this.graph.nodes[i].layoutPosY;

      if(x > maxx) maxx = x;
      if(x < minx) minx = x;
      if(y > maxy) maxy = y;
      if(y < miny) miny = y;
    }

    this.graph.layoutMinX = minx;
    this.graph.layoutMaxX = maxx;

    this.graph.layoutMinY = miny;
    this.graph.layoutMaxY = maxy;
  }
};


Dracula.Layout.TournamentTree = function(graph, order) {
  this.graph = graph;
  this.order = order;
  this.layout();
};


/*
 * TournamentTree looks more like a binary tree
 */
Dracula.Layout.TournamentTree.prototype = {
  layout: function() {
    this.layoutPrepare();
    this.layoutCalcBounds();
  },

  layoutPrepare: function(order) {
    var node, i;
    for (i in this.graph.nodes) {
      node = this.graph.nodes[i];
      node.layoutPosX = 0;
      node.layoutPosY = 0;
    }
    //to reverse the order of rendering, we need to find out the
    //absolute number of levels we have. simple log math applies.
    var numNodes = this.order.length;
    var totalLevels = Math.floor(Math.log(numNodes) / Math.log(2));

    var counter = 1;
    for (i in this.order) {
      node = this.order[i];
      var depth = Math.floor(Math.log(counter) / Math.log(2));
      var xpos = counter - Math.pow(depth, 2);
      var offset = Math.pow(2, totalLevels - depth);
      var final_x = offset + (counter - Math.pow(2, depth)) *
        Math.pow(2, (totalLevels - depth) + 1);
      node.layoutPosX = final_x;
      node.layoutPosY = depth;
      counter++;
    }
  },

  layoutCalcBounds: function() {
    var minx = Infinity, maxx = -Infinity,
    miny = Infinity, maxy = -Infinity;
    var i;

    for (i in this.graph.nodes) {
      var x = this.graph.nodes[i].layoutPosX;
      var y = this.graph.nodes[i].layoutPosY;

      if(x > maxx) maxx = x;
      if(x < minx) minx = x;
      if(y > maxy) maxy = y;
      if(y < miny) miny = y;
    }

    this.graph.layoutMinX = minx;
    this.graph.layoutMaxX = maxx;

    this.graph.layoutMinY = miny;
    this.graph.layoutMaxY = maxy;
  }
};


/*
 * Raphael Tooltip Plugin
 * - attaches an element as a tooltip to another element
 *
 * Usage example, adding a rectangle as a tooltip to a circle:
 *
 *      paper.circle(100,100,10).tooltip(paper.rect(0,0,20,30));
 *
 * If you want to use more shapes, you'll have to put them into a set.
 *
 */
Raphael.el.tooltip = function (tp) {
    this.tp = tp;
    this.tp.o = {x: 0, y: 0};
    this.tp.hide();
    this.hover(
        function(event){
            this.mousemove(function(event){
                this.tp.translate(event.clientX -
                                  this.tp.o.x,event.clientY - this.tp.o.y);
                this.tp.o = {x: event.clientX, y: event.clientY};
            });
            this.tp.show().toFront();
        },
        function(event){
            this.tp.hide();
            this.unmousemove();
        });
    return this;
};

/* For IE */
if (!Array.prototype.forEach)
{
  Array.prototype.forEach = function(fun /*, thisp*/)
  {
    var len = this.length;
    if (typeof fun != "function")
      throw new TypeError();

    var thisp = arguments[1];
    for (var i = 0; i < len; i++)
    {
      if (i in this)
        fun.call(thisp, this[i], i, this);
    }
  };
}
