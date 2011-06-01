var OntologyPanelView = Backbone.View.extend({
    $tree : null,
    $info : null,
    $panel : null,
    $addTerm : null,
    $editTerm : null,
    $deleteTerm : null,
    $buttonAddTerm : null,
    $buttonEditTerm : null,
    $buttonDeleteTerm : null,
    addOntologyTermDialog : null,
    editOntologyTermDialog : null,
    deleteOntologyTermDialog : null,
    expanse : false,
    events: {
        "click .addTerm": "addTerm",
        "click .editTerm": "editTerm",
        "click .deleteTerm": "deleteTerm"
    },
    initialize: function(options) {
        this.container = options.container;
        this.ontologiesPanel = options.ontologiesPanel;
    },
    render: function() {
        console.log("OntologyPanelView.render");
        var self = this;

        self.$panel = $(self.el);
        self.$tree = self.$panel.find("#treeontology-"+self.model.id);
        self.$info = self.$panel.find("#infoontology-"+self.model.id);

        self.$addTerm = self.$panel.find('#dialog-add-ontology-term');
        self.$editTerm = self.$panel.find('#dialog-edit-ontology-term');
        self.$deleteTerm = self.$panel.find('#dialogsTerm');

        self.$buttonAddTerm = self.$panel.find($('#buttonAddTerm'+self.model.id));
        self.$buttonEditTerm = self.$panel.find($('#buttonEditTerm'+self.model.id));
        self.$buttonDeleteTerm = self.$panel.find($('#buttonDeleteTerm'+self.model.id));

        self.buildOntologyTree();
        self.buildButton();
        self.buildInfoPanel();

        return this;
    },

    refresh : function() {
        var self = this;
        self.model.fetch({
            success : function (model, response) {
                self.clear();
                self.render();
            }});

    },

    clear : function() {
        var self = this;
        self.$panel.empty();
        require([
            "text!application/templates/ontology/OntologyTabContent.tpl.html"
        ],
               function(tpl) {
                   console.log("OntologyPanelView.render");
                   self.$panel.html(_.template(tpl, { id : self.model.get("id"), name : self.model.get("name")}));
                   return this;
               });

        return this;
    },

    getCurrentTermId : function() {
        var node = this.$tree.dynatree("getActiveNode");
        console.log("GetActiveNode="+node.data.id);
        if(node==null) return null;
        else return node.data.id;
    },

    addTerm : function() {
        console.log("addTerm");
        var self = this;
        self.$addTerm.remove();

        self.addOntologyTermDialog = new OntologyAddOrEditTermView({
            ontologyPanel:self,
            el:self.el,
            ontology:self.model,
            model:null //add component so no term
        }).render();
    },

    editTerm : function() {
        console.log("editTerm");
        var self = this;
        self.$editTerm.remove();

        var node = self.$tree.dynatree("getActiveNode");

        if(node==null) {
            alert("You must select a term (we must replace this message with a beautiful dialog)!");
            return;
        }

        new TermModel({id:node.data.id}).fetch({
            success : function (model, response) {
                console.log("edit term="+model.id + "name=" +model.get('name'));
                self.editOntologyTermDialog = new OntologyAddOrEditTermView({
                    ontologyPanel:self,
                    el:self.el,
                    model:model,
                    ontology:self.model
                }).render();
            }});
    },


    deleteTerm : function() {
        console.log("deleteTerm");
        var self = this;

        var idTerm = self.getCurrentTermId();
        console.log("idTerm="+idTerm);

        var term = window.app.models.terms.get(idTerm);
        console.log("term.name="+term.get('name'));

        new AnnotationCollection({term:idTerm}).fetch({

            success : function (collection, response) {

                console.log("term:" + idTerm + " is link with " + collection.length + " annotations");

                if(collection.length==0) self.buildDeleteTermConfirmDialog(term);
                else self.buildDeleteTermWithAnnotationConfirmDialog(term,collection.length);
            }});
    },

    selectTerm : function(idTerm) {
         var self = this;
        console.log("OntologyPanelView: select " + idTerm) ;
        self.$tree.dynatree("getTree").selectKey(idTerm);
    },

    buildDeleteTermConfirmDialog : function(term) {
        console.log("term is not linked with annotations");
        var self = this;
        require(["text!application/templates/ontology/OntologyDeleteTermConfirmDialog.tpl.html"], function(tpl) {
            // $('#dialogsTerm').empty();
            console.log("tpl=");
            console.log(tpl);
            var dialog =  new ConfirmDialogView({
                el:'#dialogsTerm',
                template : _.template(tpl, {term : term.get('name'),ontology : self.model.get('name')}),
                dialogAttr : {
                    dialogID : '#dialogsTerm',
                    width : 400,
                    height : 300,
                    buttons: {
                        "Delete term": function() {
                            console.log("delete:"+term.get('name'));
                            new BeginTransactionModel({}).save({}, {
                                success: function (model, response) {
                                    self.deleteTermWithoutAnnotationTerm(term);
                                },
                                error: function (model, response) {
                                    console.log("ERRORR: error transaction begin");
                                }
                            });
                        },
                        "Cancel": function() {
                            console.log("no delete");
                            //doesn't work! :-(
                            $('#dialogsTerm').dialog( "close" ) ;
                        }
                    },
                    close :function (event) {
                    }
                }
            }).render();
        });
    },
    /**
     * TODO: This method can be merge with  buildDeleteTermWithoutAnnotationConfirmDialog
     * But it's now separete to allow modify with delete term with annotation (which is critical)
     * @param term
     * @param numberOfAnnotation
     */
    buildDeleteTermWithAnnotationConfirmDialog : function(term,numberOfAnnotation) {
        //TODO:ask confirmation (and delete term  with annotation? or not...)
        console.log("term is linked with annotations");
        var self = this;
        require(["text!application/templates/ontology/OntologyDeleteTermWithAnnotationConfirmDialog.tpl.html"], function(tpl) {
            // $('#dialogsTerm').empty();
            console.log("tpl=");
            console.log(tpl);
            var dialog =  new ConfirmDialogView({
                el:'#dialogsTerm',
                template : _.template(tpl, {term : term.get('name'),ontology : self.model.get('name'),numberOfAnnotation:numberOfAnnotation}),
                dialogAttr : {
                    dialogID : '#dialogsTerm',
                    width : 400,
                    height : 300,
                    buttons: {
                        "Delete all link and delete term": function() {
                            console.log("delete:"+term.get('name'));
                            new BeginTransactionModel({}).save({}, {
                                success: function (model, response) {
                                    self.deleteTermWithAnnotationTerm(term);
                                },
                                error: function (model, response) {
                                    console.log("ERRORR: error transaction begin");
                                }
                            });
                        },
                        "Cancel": function() {
                            console.log("no delete");
                            //doesn't work! :-(
                            $('#dialogsTerm').dialog( "close" ) ;
                        }
                    },
                    close :function (event) {
                    }
                }
            }).render();
        });
    },
    /**
     * Delete a term which can have annotation and relation
     * @param term  term that must be deleted
     */
    deleteTermWithAnnotationTerm : function(term) {
        var self = this;
        var counter = 0;
        //delete all annotation term
        new AnnotationCollection({term:term.id}).fetch({
            success:function (collection, response){
                if (collection.size() == 0) {
                    self.removeAnnotationTermCallback(0,0, term);
                    return;
                }
                collection.each(function(annotation) {
                    console.log("delete annotation term with" +
                            " term: " + term.id +"and" +
                            " annotation:"+ annotation.get('name'));
                    //delete annotation term
                    new AnnotationTermModel({
                        term:term.id,
                        annotation:annotation.id
                    }).destroy({success : function (model, response) {
                        self.removeAnnotationTermCallback(collection.length, ++counter, term);
                    }});
                });
            }
        });
    },
    /**
     * Delete a term which can have relation but no annotation
     * @param term term that must be deleted
     */
    deleteTermWithoutAnnotationTerm : function(term) {
        var self = this;
        var counter = 0;
        //get all relation with this term and remove all of them
        new RelationTermCollection({term:term.id}).fetch({
            success:function (collection, response){
                if (collection.size() == 0) {
                    self.removeRelationTermCallback(0,0, term);
                    return;
                }
                collection.each(function(item) {
                    console.log("delete" +
                            " relation with relation="+item.get('relation') +
                            " term1="+item.get('term1') +
                            " term2="+item.get('term2'));

                    console.log("relationTerm="+JSON.stringify(item));

                    var json = item.toJSON();

                    new RelationTermModel({
                        relation:json.relation.id,
                        term1:json.term1.id,
                        term2:json.term2.id
                    }).destroy({success : function (model, response) {
                        self.removeRelationTermCallback(collection.length, ++counter, term);
                    }});

                });

            }});
    },
    removeAnnotationTermCallback : function(total, counter, term) {
        var self = this;
        if (counter < total) return;
        //all annotation-term are deleted for this term: delete term like a term with no annotation
        self.deleteTermWithoutAnnotationTerm(term);

    },
    removeRelationTermCallback : function(total, counter, term) {
        var self = this;
        if (counter < total) return;
        //term has no relation, delete term
        new TermModel({id:term.id}).destroy({
            success : function (model, response) {
                new EndTransactionModel({}).save();
                self.refresh();
                $('#dialogsTerm').dialog( "close" ) ;
            },
            error: function (model, response) {

                var json = $.parseJSON(response.responseText);
                console.log("json.project="+json.errors);
                    $("#delete-term-error-message").empty();
                    $("#delete-term-error-label").show();
                    $("#delete-term-error-message").append(json.errors)
                    //console.log();
            }});
    },

    buildButton : function() {
        var self = this;

        self.$buttonAddTerm.button({
            icons : {secondary: "ui-icon-plus" }
        });
        self.$buttonEditTerm.button({
            icons : {secondary: "ui-icon-pencil" }
        });
        self.$buttonDeleteTerm.button({
            icons : {secondary: "ui-icon-trash" }
        });
    },
    buildInfoPanel : function() {

    },

    buildOntologyTree : function() {
        var self = this;
        console.log("buildOntologyTree for ontology " + self.model.id);
        var currentTime = new Date();

        self.$tree.empty();
        self.$tree.dynatree({
            children: self.model.toJSON(),
            onExpand : function() { console.log("expanding/collapsing");},
            onClick: function(node, event) {

                /*var title = node.data.title;
                var color = "black";
                var htmlNode = "<a href='#'><label style='color:{{color}}'>{{title}}</label></a>" ;
                var nodeTpl = _.template(htmlNode, {title : title, color : color});
                node.setTitle(nodeTpl);  */

                if(window.app.models.ontologies.get(node.data.id)==undefined)
                    self.updateInfoPanel(node.data.id,node.data.title);
            },
            onSelect: function(select, node) {
                self.updateInfoPanel(node.data.id,node.data.title);
            },
            onDblClick: function(node, event) {
                console.log("Double click");
            },
            onRender: function(node, nodeSpan) {
                self.$tree.find("a.dynatree-title").css("color", "black");
            },
            //generateIds: true,
            // The following options are only required, if we have more than one tree on one page:
            initId: "treeDataOntology-"+self.model.id + currentTime.getTime(),
            cookieId: "dynatree-Ontology-"+self.model.id+ currentTime.getTime(),
            idPrefix: "dynatree-Ontology-"+self.model.id+ currentTime.getTime()+"-" ,
            debugLevel: 2
        });
        //expand all nodes
        self.$tree.dynatree("getRoot").visit(function(node){
            node.expand(true);
        });
    },

    updateInfoPanel : function(idTerm,name) {
        var self = this;
        console.log("updateInfoPanel");
        //bad code with html but waiting to know what info is needed...
        self.$info.empty();
        console.log("append div");
        self.$info.append("<div id=\"termchart-"+self.model.id +"\"><h3>"+name+"</h3><div id=\"terminfo-"+self.model.id +"\"></div>");
        console.log("get term");
        $("#terminfo-"+self.model.id).append("<input type=\"color\" name=\"color\" id=\"color\" size=\"25\" />");
        $("#terminfo-"+self.model.id).append("<br>");

        new TermModel({id:idTerm}).fetch({
            success : function (model, response) {

                $("#terminfo-"+self.model.id).find("input").css("background-color",model.get('color'));;

                //style=\"background:"+ term.get('color') + "\"

            }});


        var statsCollection = new StatsCollection({term:idTerm});
        var statsCallback = function(collection, response) {

            console.log("stats="+collection.length);



            collection.each(function(stat) {
                console.log(stat.get('key')+" " + stat.get('value'));
                $("#terminfo-"+self.model.id).append("Project "+stat.get('key') + ": " + stat.get('value') + " annotations<br>");
            });

            $("#termchart-"+self.model.id).panel({
                collapsible:false
            });

        }
        statsCollection.fetch({
            success : function(model, response) {
                statsCallback(model, response); //fonctionne mais très bourrin de tout refaire à chaque fois...
            }
        });
    }
});