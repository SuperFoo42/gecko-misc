﻿

var width = 1200,
    height = 700;

var dataArray = new Array();
var linksArray = new Array();

var authorDetails=new Map();

var svg = d3.select("#paperGraphArea").append("svg")
    .attr("width", width)
    .attr("height", height);
var menuItems = ["Authorship", "Co-word", "Bibliographic"];
var authorMenuItems=["Co-word", "Bibliographic"];

var force = d3.layout.force()
    .distance(100)
    .linkDistance([100])
    .charge(-300)
    .size([width, height])
    .gravity(0.1)
    .alpha(0);

d3.json("../JSON/QueryData.json", function (error, json) {
    if (error) throw error;

    dataArray = json.nodes;
    linksArray = json.links;

    for (var k = 0; k < linksArray.length; k++) {
        for (l = 0; l < dataArray.length; l++) {
            if (linksArray[k].source === dataArray[l].PaperId) {
                //console.log("replacing source");
                linksArray[k].source = l;
            }
            else if (linksArray[k].target === dataArray[l].PaperId) {
                linksArray[k].target = l;
            }
        }
    }

    createGraph(json.nodes, linksArray,true);
});

function createGraph(nodes, links,check) {

    svg.append("defs").selectAll("marker")
        .data(["arrowhead", "licensing", "resolved"])
        .enter().append("marker")
        .attr("id", function (d) { return d; })
        .attr("viewBox", "0 -5 10 10")
        .attr("refX", 24)
        .attr("refY", 0)
        .attr("markerWidth", 15)
        .attr("markerHeight", 10)
        .attr("orient", "auto")
        .append("path")
        .attr('d', 'M 0,-5 L 10 ,0 L 0,5')
        .attr('fill', '#ccc')
        .attr('stroke', '#ccc');

    force.nodes(nodes)
        .links(links)
        .start();

    var link = svg.selectAll("link")
        .data(links)
        .enter().append("line")
        .attr("class", "link")
        .attr("marker-end", "url(#arrowhead)");

    var node = svg.selectAll("node")
        .data(nodes)
        .enter().append("g")
        .attr("class", "node")
        .call(force.drag);


    node.append("image")
        .attr("xlink:href", function (d) {
            if(d.type==="paper"){
                return "../Images/Document.ico";
            }
            else if (d.type==="author"){
                return "../Images/author.png";
            }
        })
        .attr("x", -8)
        .attr("y", -8)
        .attr("width", 30)
        .attr("height", 25)
        .style("stroke", "black")
        .style("stroke-width", "10")
        //to populate context menu
        .on('contextmenu', function (d, i) {
            // create the div element that will hold the context menu
            d3.selectAll('.context-menu').data([1])
                .enter()
                .append('div')
                .attr('class', 'context-menu');
            // close menu
            d3.select('body').on('click.context-menu', function () {
                d3.select('.context-menu').style('display', 'none');
            });
            // this gets executed when a context menu event occurs
            d3.selectAll('.context-menu')
                .html('')
                .append('ul')
                .selectAll('li')
                .data(function () {
                    if(d.type==="paper")
                        return menuItems;
                    else
                        return authorMenuItems;
                }).enter()
                .append('li')
                .on('click', function (d) {
                    console.log("context menu-" +d);
                    if(d==="Authorship")
                    parseData(paperId);
                })
                .text(function (d) { console.log(d); return d;});
            d3.select('.context-menu').style('display', 'none');
            // show the context menu
            d3.select('.context-menu')
                .style('left',d3.event.pageX - 2 + 'px')
                .style('top', d3.event.pageY - 2 + 'px')
                .style('display', 'block');
            d3.event.preventDefault();
            var paperId = d.PaperId;
        })
        //To display tooltip
        .on("mouseover", function (d) {
            div.transition()
                .duration(200)
                .style("opacity", .9);
            div.html(d.title + "<br/>")
                .style("left", (d3.event.pageX) + "px")
                .style("top", (d3.event.pageY - 30) + "px");
        })
        //to disbale tooltip
        .on("mouseout", function (d) {
            div.transition()
                .duration(500)
                .style("opacity", 0);
        });

    node.append("title")
        .attr("dx", 20)
        .attr("dy", ".35em");

    if (check) {
        node.append("text")
            .attr("dx", 20)
            .attr("dy", ".35em")
            .text(function (d) {
                if(d.type==="paper"){
                    var a = "";
                    if (d.title !== null) {
                        a = d.title.substring(0, 3)
                    }
                    if (d.year !== null) {
                        a = a.concat((d.year).toString().substring(1, 3))
                    }
                    return a
                }
                else if(d.type==="author"){
                    return d.authors;
                }

            });
    }

    //	Define the div for the tooltip
    var div = d3.select("body").append("div")
        .attr("class", "tooltip")
        .style("opacity", 0);

    force.on("tick", function () {
        link.attr("x1", function (d) { return d.source.x; })
            .attr("y1", function (d) { return d.source.y; })
            .attr("x2", function (d) { return d.target.x; })
            .attr("y2", function (d) { return d.target.y; });


        node.attr("transform", function (d) { return "translate(" + d.x + "," + d.y + ")"; });
    });
}


function createAuthorNode(paperId, authorName) {
    this.PaperId = paperId;
    this.authors = authorName;
    this.type="author";
}

function createLinks(source, target) {
    this.source = source;
    this.target = target;
}

function parseData(idToEXpand) {
    var intial_length = dataArray.length;
    for (var i = 0; i < intial_length; i++) {
             if(dataArray[i].PaperId===idToEXpand){
                for (var j = 0; j < dataArray[i].authors.length; j++) {
                 if(!authorDetails.has(dataArray[i].authors[j].name)) {
                     var newNode = new createAuthorNode(dataArray[i].PaperId, dataArray[i].authors[j].name)
                     var index= dataArray.push(newNode);
                     authorDetails.set(dataArray[i].authors[j].name,index-1);
                     var newLink = new createLinks(dataArray.length - 1, i);
                     linksArray.push(newLink);
                 }

                }
            }
    }

    for (var i = 0; i < intial_length; i++) {
        if(dataArray[i].PaperId!==idToEXpand) {
            for (var j = 0; j < dataArray[i].authors.length; j++) {
                if(authorDetails.has(dataArray[i].authors[j].name)){
                    var newLink = new createLinks(authorDetails.get(dataArray[i].authors[j].name), i);
                    linksArray.push(newLink);
                }
            }
        }
    }

    JSON.stringify(linksArray);
    JSON.stringify(dataArray);
    d3.selectAll("svg > *").remove();
    var mydata=new Set(dataArray);
    for(let item of mydata) console.log(item);
    createGraph(dataArray, linksArray,true);
    d3.select('.context-menu').style('display', 'none');
}
