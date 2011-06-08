package be.cytomine.command.abstractimage

import be.cytomine.image.AbstractImage
import grails.converters.JSON

import be.cytomine.command.UndoRedoCommand
import be.cytomine.command.AddCommand
import org.codehaus.groovy.grails.validation.exceptions.ConstraintException

/**
 * Created by IntelliJ IDEA.
 * User: lrollus
 * Date: 16/02/11
 * Time: 14:56
 * To change this template use File | Settings | File Templates.
 */
class AddAbstractImageCommand extends AddCommand implements UndoRedoCommand {

  def execute() {
    log.info("Execute")
    AbstractImage newImage=null
    try {
      def json = JSON.parse(postData)
      json.user = user.id
      newImage = AbstractImage.createFromData(json)
      return super.validateAndSave(newImage,["#ID#",json.name] as Object[])
    }catch(ConstraintException ex){
      return [data : [image:newImage,errors:newImage.retrieveErrors()], status : 400]
    }catch(IllegalArgumentException ex){
      return [data : [image:null,errors:["Cannot save image:"+ex.toString()]], status : 400]
    }
  }

  def undo() {
    log.info("Undo")
    def imageData = JSON.parse(data)
    AbstractImage image = AbstractImage.get(imageData.id)
    image.delete(flush:true)
   String id = imageData.id
    return super.createUndoMessage(id,[imageData.id,imageData.name] as Object[]);
  }

  def redo() {

    log.info("Redo:"+data.replace("\n",""))
    def imageData = JSON.parse(data)
    AbstractImage image = AbstractImage.createFromData(imageData)
    image.id = imageData.id
    image.save(flush:true)
    log.debug("Save image:"+image.id)
    return super.createRedoMessage(image,[imageData.id,imageData.name] as Object[]);
  }
}