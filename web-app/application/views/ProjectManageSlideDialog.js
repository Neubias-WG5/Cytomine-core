/**
 * Created by IntelliJ IDEA.
 * User: lrollus
 * Date: 28/04/11
 * Time: 10:14
 * To change this template use File | Settings | File Templates.
 */
var ProjectManageSlideDialog = Backbone.View.extend({
    checklistChecked : ".checklist input:checked",
    checklistSelected : ".checklist .checkbox-select",
    checklistDeselected : ".checklist .checkbox-deselect",
    selectedClass : "selected",
    checkedAttr : "checked",
    liElem : "projectaddimageitemli",
    ulElem : "#projectaddimagedialoglist",
    allProjectUlElem : "ul[id^=projectaddimagedialoglist]",
    imageDivElem : "#projectaddimageitempict",
    divDialog : "div#projectaddimagedialog",
    projectPanel : null,
    addSlideDialog : null,
    initialize: function(options) {
        this.container = options.container;
        this.projectPanel = options.projectPanel;
        _.bindAll(this, 'render');
    },
    render : function() {
        var self = this;
        console.log("Id project="+this.model.id);

        var dialog = ich.projectaddimagedialog({id:this.model.get('id'),name:this.model.get('name')});
        $(this.el).append(dialog);

        //Build dialog
        self.addSlideDialog = $(self.divDialog+this.model.get('id')).dialog({
            modal : true,
            autoOpen : false,
            closeOnEscape: false,
            buttons : {
                "Close" : function() {
                    $(self.divDialog+self.model.get('id')).dialog("close");
                }
            },
            width : "85%",
            height: "600"
        });

        $(self.divDialog+this.model.get('id')).dialog({
            beforeClose: function(event, ui) {self.projectPanel.refresh();}
        });
        return this;

    },
    open: function() {
        var self = this;
        console.log("open");
        self.renderImageList();
        self.addSlideDialog.dialog("open") ;
    },
    refresh : function(idImage, selected) {
        console.log("ProjectManageSlideDialog: refresh =" + idImage)
        var className = ".image" + idImage;
        var self = this;
        if (selected)
            $(self.ulElem+self.model.get('id')).find(className).addClass("selected");
        else
            $(self.ulElem+self.model.get('id')).find(className).removeClass("selected");
    },
    renderImageList : function() {
        var self = this;

        //Dialog is maybe already in document (but closed)...clear the all list elem
        $(self.ulElem+self.model.get('id')).empty();

        //Get all images from server
        //TODO: filter by user right
        new ImageCollection({project:undefined}).fetch({
            success: function(collection,response){
                //TODO: multi-page print?
                var page = 0;
                var cpt = 0;
                var nb_thumb_by_page = 2000;
                var inf = Math.abs(page) * nb_thumb_by_page;
                var sup = (Math.abs(page) + 1) * nb_thumb_by_page;
                var currentSlide = -1;
                //Get images from project server
                new ImageCollection({project:self.model.get('id')}).fetch({
                    success: function(projectImages,response){

                        console.log("collection size=" + collection.length);
                        console.log("projectImages size=" + projectImages.length);

                        collection.each(function(image) {


                            if ((cpt >= inf) && (cpt < sup)) {

                                var thumb = new ImageSelectView({
                                    model : image
                                }).render();

                                var filename = image.get("filename");
                                if (filename.length > 15)
                                    filename = filename.substring(0,12) + "...";
                                var item = ich.projectaddimageitem({id:image.id,name:filename, slide: image.get('slide'), info : image.get('info')});

                                $(thumb.el).css({"width":30}); //thumb must be smaller

                                $(self.ulElem+self.model.get('id')).append(item);
                                $(self.ulElem+self.model.get('id') + " " + self.imageDivElem+image.id).append(thumb.el);  //get the div elem (img id) which have this project as parent

                                //if image is already in project, selected it
                                if(projectImages.get(image.id))
                                {
                                    //get the li elem (img id) which have this project as parent
                                    $(self.ulElem+self.model.get('id') + " " + "#"+ self.liElem+image.id).addClass(self.selectedClass);
                                    $(self.ulElem+self.model.get('id') + " " + "#"+self.liElem+image.id).find(":checkbox").attr(self.checkedAttr,self.checkedAttr);
                                }
                            }
                            cpt++;
                        });
                        $(self.ulElem+self.model.get('id') + " img").addClass("thumbProject");

                        //build dialog and event
                        self.buildAddImageDialog();
                    },
                    error: function(error){
                        for (property in error) {
                            console.log(property + ":" + error[property]);
                        }
                    }
                });
            },
            error: function(error){
                for (property in error) {
                    console.log(property + ":" + error[property]);
                }
            }
        });
    },
    buildAddImageDialog : function() {
        var self = this;

        /* see if anything is previously checked and reflect that in the view*/
        $(self.checklistChecked).parent().addClass(self.selectedClass);

        $(self.checklistSelected).click(
                                       function(event) {

                                           event.preventDefault();
                                           var slideID = $(this).parent().attr("class");
                                           console.log("CLASS="+slideID);
                                           $(this).parent().addClass(self.selectedClass);
                                           $(this).parent().find(":checkbox").attr(self.checkedAttr,self.checkedAttr);

                                           //Get the id of the selected image....
                                           //TODO: a better way to do that?
                                           var fullId = $(this).parent().attr("id");   //"projectaddimageitemliXXX"

                                           var idImage = fullId.substring(self.liElem.length,fullId.length);  //XXX

                                           //add slide to project

                                           new ImageModel({id:idImage}).fetch({
                                               success : function (image,response) {

                                                   console.log("Image id = " + idImage +  " Project id = " + self.model.get('id'));

                                                   new ImageInstanceModel({}).save({project : self.model.get('id'), user : null, baseImage : idImage},{
                                                       success : function (image,response) {
                                                           self.refresh(idImage, true);
                                                       },
                                                       error: function (model, response) {
                                                           console.log("ERROR:"+response);
                                                       }
                                                   });

                                               }
                                           });
                                       }
                );

        $(self.checklistDeselected).click(
                                         function(event) {
                                             console.log("click b");
                                             event.preventDefault();
                                             $(this).parent().removeClass(self.selectedClass);
                                             $(this).parent().find(":checkbox").removeAttr(self.checkedAttr);

                                             //Get the id of the selected image....a better way to do that?
                                             var fullId = $(this).parent().attr("id");   //"projectaddimageitemliXXX"
                                             var idImage = fullId.substring(self.liElem.length,fullId.length);  //XXX

                                             //delete slide from project
                                             new ImageModel({id:idImage}).fetch({
                                                 success : function (image,response) {

                                                     console.log("Image id = " + idImage +  " Project id = " + self.model.get('id'));

                                                     new ImageInstanceModel({project : self.model.get('id'), user : null, baseImage : idImage}).destroy({
                                                         success : function (image,response) {
                                                             self.refresh(idImage, false);
                                                         },
                                                         error: function (model, response) {
                                                             console.log("ERROR:"+response);
                                                         }
                                                     });

                                                 }
                                             });


                                         });
    }
});