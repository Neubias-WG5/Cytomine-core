package be.cytomine.api.image

/*
* Copyright (c) 2009-2019. Authors: see NOTICE file.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

import be.cytomine.Exception.CytomineException
import be.cytomine.Exception.ForbiddenException
import be.cytomine.Exception.InvalidRequestException
import be.cytomine.api.RestController
import be.cytomine.image.ImageInstance
import be.cytomine.image.SliceInstance
import be.cytomine.ontology.UserAnnotation
import be.cytomine.project.Project
import be.cytomine.sql.ReviewedAnnotationListing
import be.cytomine.utils.GeometryUtils
import com.vividsolutions.jts.geom.Geometry
import com.vividsolutions.jts.geom.GeometryCollection
import com.vividsolutions.jts.geom.GeometryFactory
import com.vividsolutions.jts.io.WKTReader
import com.vividsolutions.jts.io.WKTWriter
import grails.converters.JSON
import org.codehaus.groovy.grails.web.json.JSONObject
import org.hibernate.FetchMode
import org.restapidoc.annotation.*
import org.restapidoc.pojo.RestApiParamType

@RestApi(name = "Image | image instance services", description = "Methods for managing an image instance : abstract image linked to a project")
class RestImageInstanceController extends RestController {

    def imageInstanceService
    def projectService
    def abstractImageService
    def dataTablesService
    def userAnnotationService
    def algoAnnotationService
    def reviewedAnnotationService
    def secUserService
    def termService
    def annotationListingService
    def cytomineService
    def taskService
    def annotationIndexService
    def descriptionService
    def propertyService
    def securityACLService
    def imageGroupService
    def imageServerService
    def sampleHistogramService

    @RestApiMethod(description="Get an image instance")
    @RestApiParams(params=[
    @RestApiParam(name="id", type="long", paramType = RestApiParamType.PATH, description = "The image instance id")
    ])
    def show() {
        ImageInstance image = imageInstanceService.read(params.long('id'))
        if (image) {
            responseSuccess(image)
        } else {
            responseNotFound("ImageInstance", params.id)
        }
    }

    @RestApiMethod(description="Get all image instance available for the current user", listing = true)
    def listByUser() {
        responseSuccess(imageInstanceService.list(cytomineService.currentUser))
    }

    @RestApiMethod(description="Get the last opened image for the current user", listing = true)
    def listLastOpenImage() {
        def offset = params.long('offset')
        def max =params.long('max')
        params.offset = 0
        params.max = 0
        responseSuccess(imageInstanceService.listLastOpened(cytomineService.currentUser,offset,max))
    }

    @RestApiMethod(description="Get all image instance for a specific project", listing = true)
    @RestApiParams(params=[
    @RestApiParam(name="project", type="long", paramType = RestApiParamType.PATH, description = "The project id"),
    @RestApiParam(name="tree", type="boolean", paramType = RestApiParamType.QUERY, description = "(optional) Get a tree (with parent image as node)"),
    @RestApiParam(name="sortColumn", type="string", paramType = RestApiParamType.QUERY, description = "(optional) Column sort (created by default)"),
    @RestApiParam(name="sortDirection", type="string", paramType = RestApiParamType.QUERY, description = "(optional) Sort direction (desc by default)"),
    @RestApiParam(name="search", type="string", paramType = RestApiParamType.QUERY, description = "(optional) Original filename search filter (all by default)"),
    @RestApiParam(name="withLastActivity", type="boolean", paramType = RestApiParamType.QUERY, description = "(optional) Return the last consultation of current user in each image. Not compatible with tree, excludeimagegroup and datatables parameters "),
    @RestApiParam(name="light", type="boolean", paramType = RestApiParamType.QUERY, description = "(optional, default false) If true, the returned list will only contain id, instanceFilename and blindedName properties. Not compatible with tree, excludeimagegroup, datatables and withLastActivity parameters")
    ])
    def listByProject() {
        Project project = projectService.read(params.long('project'))
        if (params.datatables) {
            def where = "project_id = ${project.id}"
            def fieldFormat = []
            responseSuccess(dataTablesService.process(params, ImageInstance, where, fieldFormat,project))
        }
        else if (project && !params.tree) {
            String sortColumn = params.sortColumn ? params.sortColumn : "created"
            String sortDirection = params.sortDirection ? params.sortDirection : "desc"
            String search = params.search
            def extended = [:]
            if(params.withLastActivity) extended.put("withLastActivity",params.withLastActivity)
            def imageList
            if(extended.isEmpty()) {
                boolean light = params.getBoolean("light")
                imageList = imageInstanceService.list(project, sortColumn, sortDirection, search, light)
            } else {
                imageList = imageInstanceService.listExtended(project, sortColumn, sortDirection, search, extended)
            }

            responseSuccess(imageList)
        }
        else if (project && params.tree && params.boolean("tree"))  {
            responseSuccess(imageInstanceService.listTree(project))
        }
        else {
            responseNotFound("ImageInstance", "Project", params.project)
        }
    }

    @RestApiMethod(description="Get the next project image (first image created before)")
    @RestApiParams(params=[
    @RestApiParam(name="id", type="long", paramType = RestApiParamType.PATH, description = "The current image instance id"),
    ])
    def next() {
        def image = imageInstanceService.read(params.long('id'))
        def next
        if (!image.blindInstanceFilename.contains("_lbl")) {
            next = ImageInstance.createCriteria().list {
                createAlias("baseImage", "ai")
                eq("project", image.project)
                lt("created", image.created)
                isNull("parent")
                isNull("deleted")
                fetchMode 'baseImage', FetchMode.JOIN
                not {
                    ilike("ai.originalFilename", "%_lbl%")
                }
                order("created", "desc")
                maxResults(1)
            }
        } else {
            next = ImageInstance.createCriteria().list {
                createAlias("baseImage", "ai")
                eq("project", image.project)
                lt("created", image.created)
                isNull("parent")
                isNull("deleted")
                fetchMode 'baseImage', FetchMode.JOIN
                ilike("ai.originalFilename", "%_lbl%")
                order("created", "desc")
                maxResults(1)
            }
        }
        if(next && next.size() > 0) {
            responseSuccess(next[0])
        } else {
            responseSuccess([:])
        }
    }

    @RestApiMethod(description="Get the previous project image (first image created after)")
    @RestApiParams(params=[
    @RestApiParam(name="id", type="long", paramType = RestApiParamType.PATH, description = "The current image instance id"),
    ])
    def previous() {
        def image = imageInstanceService.read(params.long('id'))
        def previous
        if (!image.blindInstanceFilename.contains("_lbl")) {
            previous = ImageInstance.createCriteria().list {
                createAlias("baseImage", "ai")
                eq("project", image.project)
                gt("created", image.created)
                isNull("parent")
                isNull("deleted")
                fetchMode 'baseImage', FetchMode.JOIN
                not {
                    ilike("ai.originalFilename", "%_lbl%")
                }
                order("created", "asc")
                maxResults(1)
            }
        } else {
            previous = ImageInstance.createCriteria().list {
                createAlias("baseImage", "ai")
                eq("project", image.project)
                gt("created", image.created)
                isNull("parent")
                isNull("deleted")
                fetchMode 'baseImage', FetchMode.JOIN
                ilike("ai.originalFilename", "%_lbl%")
                order("created", "asc")
                maxResults(1)
            }
        }
        if(previous && previous.size() > 0) {
            responseSuccess(previous[0])
        } else {
            responseSuccess([:])
        }
    }

    @RestApiMethod(description="Add a new image instance in a project. If we add an image previously deleted, all previous information will be restored.")
    def add() {
        try {
            if(!request.JSON.baseImage) throw new InvalidRequestException("abstract image not set")
            if(!request.JSON.project) throw new InvalidRequestException("project not set")

            responseResult(imageInstanceService.add(request.JSON))
        } catch (CytomineException e) {
            log.error(e)
            response([success: false, errors: e.msg], e.code)
        }
    }

    @RestApiMethod(description="Update an image instance")
    @RestApiParams(params=[
    @RestApiParam(name="id", type="long", paramType = RestApiParamType.PATH,description = "The image instance id")
    ])
    def update() {
        update(imageInstanceService, request.JSON)
    }

    @RestApiMethod(description="Delete an image from a project)")
    @RestApiParams(params=[
    @RestApiParam(name="id", type="long", paramType = RestApiParamType.PATH,description = "The image instance id")
    ])
    def delete() {
        delete(imageInstanceService, JSON.parse("{id : $params.id}"),null)
    }

    def dataSource
    /**
     * Get all image id from project
     */
//    public def getInfo(String id) {
//
//        //better perf with sql request
//        String request = "SELECT a.id, a.version,a.deleted FROM image_instance a WHERE id = $id"
//        def data = []
//        def sql = new Sql(dataSource)
//        sql.eachRow(request) {
//            data << it[0] + ", " + it[1] + ", " + it[2]
//        }
//        try {
//            sql.close()
//        }catch (Exception e) {}
//        return data
//    }

//    @RestApiMethod(description="Copy image metadata (description, properties...) from an image to another one")
//    @RestApiParams(params=[
//            @RestApiParam(name="id", type="long", paramType = RestApiParamType.PATH, description = "The image that get the data"),
//            @RestApiParam(name="based", type="long", paramType = RestApiParamType.QUERY, description = "The image source for the data")
//    ])
//    @RestApiResponseObject(objectIdentifier = "empty")
//    def copyMetadata() {
//        try {
//            ImageInstance based = imageInstanceService.read(params.long('based'))
//            ImageInstance image = imageInstanceService.read(params.long('id'))
//            if(image && based) {
//                securityACLService.checkIsAdminContainer(image.project,cytomineService.currentUser)
//
//                Description.findAllByDomainIdent(based.id).each { description ->
//                    def json = JSON.parse(description.encodeAsJSON())
//                    json.domainIdent = image.id
//                    descriptionService.add(json)
//                }
//
//                Property.findAllByDomainIdent(based.id).each { property ->
//                    def json = JSON.parse(property.encodeAsJSON())
//                    json.domainIdent = image.id
//                    propertyService.add(json)
//                }
//
//                responseSuccess([])
//            } else if(!based) {
//                responseNotFound("ImageInstance",params.based)
//            }else if(!image) {
//                responseNotFound("ImageInstance",params.id)
//            }
//        } catch (CytomineException e) {
//            log.error(e)
//            response([success: false, errors: e.msg], e.code)
//        }
//
//    }

    /**
     * Check if an abstract image is already map with one or more projects
     * If true, send an array with item {imageinstanceId,layerId,layerName,projectId, projectName, admin}
     */
//    @RestApiMethod(description="Get, for an image instance, all the project having the same abstract image with the same layer (user)", listing = true)
//    @RestApiResponseObject(objectIdentifier =  "[project_sharing_same_image]")
//    @RestApiParams(params=[
//            @RestApiParam(name="id", type="long", paramType = RestApiParamType.PATH, description = "The image that get the data"),
//            @RestApiParam(name="project", type="long", paramType = RestApiParamType.QUERY, description = "The image source for the data")
//    ])
//    def retrieveSameImageOtherProject() {
//        try {
//            ImageInstance image = imageInstanceService.read(params.long('id'))
//            Project project = projectService.read(params.long('project'))
//            if(image) {
//                securityACLService.checkIsAdminContainer(image.project,cytomineService.currentUser)
//                def layers =  imageInstanceService.getLayersFromAbstractImage(image.baseImage,image, projectService.list(cytomineService.currentUser).collect{it.id},secUserService.listUsers(image.project).collect{it.id},project)
//                responseSuccess(layers)
//            } else {
//                responseNotFound("Abstract Image",params.id)
//            }
//        } catch (CytomineException e) {
//            log.error(e)
//            response([success: false, errors: e.msg], e.code)
//        }
//    }


    /**
     * Copy all annotation (and dedepency: term, description, property,..) to the new image
     * Params must be &layers=IMAGEINSTANCE1_USER1,IMAGE_INSTANCE1_USER2,... which will add annotation
     * from user/image from another project.
     */
//    @RestApiMethod(description="Copy all annotation (and term, desc, property,...) from an image to another image", listing = true)
//    @RestApiResponseObject(objectIdentifier = "[copy_annotation_image]")
//    @RestApiParams(params=[
//            @RestApiParam(name="id", type="long", paramType = RestApiParamType.PATH, description = "The image that get the data"),
//            @RestApiParam(name="task", type="long", paramType = RestApiParamType.QUERY, description = "(Optional) The id of task that will be update during the request processing"),
//            @RestApiParam(name="giveMe", type="boolean", paramType = RestApiParamType.QUERY, description = "If true, copy all annotation on the current user layer. If false or not mentioned, copy all anotation on the same layer as the source image"),
//            @RestApiParam(name="layers", type="list (x1_y1,x2_y2,...)", paramType = RestApiParamType.QUERY, description = "List of couple 'idimage_iduser'")
//    ])
//    def copyAnnotationFromSameAbstractImage() {
//        try {
//            ImageInstance image = imageInstanceService.read(params.long('id'))
//            securityACLService.checkIsAdminContainer(image.project,cytomineService.currentUser)
//            Task task = taskService.read(params.getLong("task"))
//            Boolean giveMe = params.boolean("giveMe")
//            log.info "task ${task} is find for id = ${params.getLong("task")}"
//            def layers = params.layers? params.layers.split(",") : ""
//            if(image && layers) {
//                responseSuccess(imageInstanceService.copyLayers(image,layers,secUserService.listUsers(image.project).collect{it.id},task,cytomineService.currentUser,giveMe))
//            } else {
//                responseNotFound("Abstract Image",params.id)
//            }
//        } catch (CytomineException e) {
//            log.error(e)
//            response([success: false, errors: e.msg], e.code)
//        }
//    }


    @RestApiMethod(description = "Compute histogram for the whole image")
    @RestApiParams(params=[
            @RestApiParam(name="id", type="long", paramType = RestApiParamType.PATH, description = "The image id")
    ])
    @RestApiResponseObject(objectIdentifier = "empty")
    def extractHistogram() {
        ImageInstance imageInstance = imageInstanceService.read(params.long("id"))
        sampleHistogramService.extractHistogram(imageInstance.baseImage)
        responseSuccess([:])
    }

    @RestApiMethod(description = "Get histogram statistics for the whole image")
    @RestApiParams(params=[
            @RestApiParam(name="id", type="long", paramType = RestApiParamType.PATH, description = "The image id")
    ])
    @RestApiResponseObject(objectIdentifier = "empty")
    def showHistogramStats() {
        ImageInstance imageInstance = imageInstanceService.read(params.long("id"))
        responseSuccess(sampleHistogramService.histogramStats(imageInstance.baseImage))
    }

    @RestApiMethod(description="Get a small image (thumb) for a specific image", extensions=["png", "jpg"])
    @RestApiParams(params=[
            @RestApiParam(name="id", type="long", paramType=RestApiParamType.PATH, description="The image id"),
            @RestApiParam(name="refresh", type="boolean", paramType=RestApiParamType.QUERY, description="If true, don't take it from cache and regenerate it", required=false),
            @RestApiParam(name="maxSize", type="int", paramType=RestApiParamType.QUERY,description="The thumb max size", required = false),
            @RestApiParam(name="colormap", type="String", paramType = RestApiParamType.QUERY, description = "The absolute path of a colormap file", required=false),
            @RestApiParam(name="inverse", type="int", paramType = RestApiParamType.QUERY, description = "True if colors have to be inversed", required=false),
            @RestApiParam(name="contrast", type="float", paramType = RestApiParamType.QUERY, description = "Multiply pixels by contrast", required=false),
            @RestApiParam(name="gamma", type="float", paramType = RestApiParamType.QUERY, description = "Apply gamma correction", required=false),
            @RestApiParam(name="bits", type="int", paramType = RestApiParamType.QUERY, description = "Output bit depth per channel", required=false)
    ])
    @RestApiResponseObject(objectIdentifier = "image (bytes)")
    def thumb() {
        ImageInstance imageInstance = imageInstanceService.read(params.long("id"))
        if (imageInstance) {
            def parameters = [:]
            parameters.format = params.format
            parameters.maxSize = params.int('maxSize',  512)
            parameters.colormap = params.colormap
            parameters.inverse = params.boolean('inverse')
            parameters.contrast = params.double('contrast')
            parameters.gamma = params.double('gamma')
            parameters.bits = (params.bits == "max") ? "max" : params.int('bits')
            parameters.refresh = params.boolean('refresh', false)
            responseByteArray(imageServerService.thumb(imageInstance, parameters))
        } else {
            responseNotFound("Image", params.id)
        }
    }

    @RestApiMethod(description="Get an image (preview) for a specific image", extensions=["png", "jpg"])
    @RestApiParams(params=[
            @RestApiParam(name="id", type="long", paramType=RestApiParamType.PATH, description="The image id"),
            @RestApiParam(name="maxSize", type="int", paramType=RestApiParamType.QUERY,description="The thumb max size", required = false),
            @RestApiParam(name="colormap", type="String", paramType = RestApiParamType.QUERY, description = "The absolute path of a colormap file", required=false),
            @RestApiParam(name="inverse", type="int", paramType = RestApiParamType.QUERY, description = "True if colors have to be inversed", required=false),
            @RestApiParam(name="contrast", type="float", paramType = RestApiParamType.QUERY, description = "Multiply pixels by contrast", required=false),
            @RestApiParam(name="gamma", type="float", paramType = RestApiParamType.QUERY, description = "Apply gamma correction", required=false),
            @RestApiParam(name="bits", type="int", paramType = RestApiParamType.QUERY, description = "Output bit depth per channel", required=false)
    ])
    @RestApiResponseObject(objectIdentifier ="image (bytes)")
    def preview() {
        ImageInstance imageInstance = imageInstanceService.read(params.long("id"))
        if (imageInstance) {
            def parameters = [:]
            parameters.format = params.format
            parameters.maxSize = params.int('maxSize',  1024)
            parameters.colormap = params.colormap
            parameters.inverse = params.boolean('inverse')
            parameters.contrast = params.double('contrast')
            parameters.gamma = params.double('gamma')
            parameters.bits = (params.bits == "max") ? "max" : params.int('bits')
            responseByteArray(imageServerService.thumb(imageInstance, parameters))
        } else {
            responseNotFound("Image", params.id)
        }
    }

    @RestApiMethod(description="Get available associated images", listing = true)
    @RestApiParams(params=[
            @RestApiParam(name="id", type="long", paramType = RestApiParamType.PATH,description = "The image id")
    ])
    @RestApiResponseObject(objectIdentifier ="associated image labels")
    def associated() {
        ImageInstance imageInstance = imageInstanceService.read(params.long("id"))
        if (imageInstance) {
            def associated = imageServerService.associated(imageInstance)
            responseSuccess(associated)
        } else {
            responseNotFound("Image", params.id)
        }
    }

    @RestApiMethod(description="Get an associated image of a abstract image (e.g. label, macro, thumbnail)", extensions=["png", "jpg"])
    @RestApiParams(params=[
            @RestApiParam(name="id", type="long", paramType = RestApiParamType.PATH,description = "The image id"),
            @RestApiParam(name="label", type="string", paramType = RestApiParamType.PATH,description = "The associated image label"),
            @RestApiParam(name="maxSize", type="int", paramType=RestApiParamType.QUERY,description="The thumb max size", required = false),
    ])
    @RestApiResponseObject(objectIdentifier = "image (bytes)")
    def label() {
        ImageInstance imageInstance = imageInstanceService.read(params.long("id"))
        if (imageInstance) {
            def parameters = [:]
            parameters.format = params.format
            parameters.label = params.label
            parameters.maxSize = params.int('maxSize', 256)
            def associatedImage = imageServerService.label(imageInstance, parameters)
            responseByteArray(associatedImage)
        } else {
            responseNotFound("Image", params.id)
        }
    }

    def crop() {
        ImageInstance imageInstance = imageInstanceService.read(params.long("id"))
        if (imageInstance) {
            responseByteArray(imageServerService.crop(imageInstance, params))
        } else {
            responseNotFound("Image", params.id)
        }

    }

    def windowUrl() {
        ImageInstance imageInstance = imageInstanceService.read(params.long("id"))
        if (imageInstance) {
            String url = imageServerService.window(imageInstance.baseImage, params, true)
            responseSuccess([url : url])
        } else {
            responseNotFound("Image", params.id)
        }

    }

    def window() {
        ImageInstance imageInstance = imageInstanceService.read(params.long("id"))
        if (imageInstance) {
            if (params.mask || params.alphaMask || params.draw || params.type in ['draw', 'mask', 'alphaMask'])
                params.location = getWKTGeometry(imageInstance, params)
            responseByteArray(imageServerService.window(imageInstance.baseImage, params, false))
        } else {
            responseNotFound("Image", params.id)
        }
    }

    def cameraUrl() {
        ImageInstance imageInstance = imageInstanceService.read(params.long("id"))
        if (imageInstance) {
            params.withExterior = false
            String url = imageServerService.window(imageInstance.baseImage, params, true)
            responseSuccess([url : url])
        } else {
            responseNotFound("Image", params.id)
        }
    }

    def camera() {
        ImageInstance imageInstance = imageInstanceService.read(params.long("id"))
        if (imageInstance) {
            params.withExterior = false
            responseByteArray(imageServerService.window(imageInstance.baseImage, params, false))
        } else {
            responseNotFound("Image", params.id)
        }
    }

    //todo : move into a service
    public String getWKTGeometry(ImageInstance imageInstance, params) {
        def geometries = []
        if (params.annotations && !params.reviewed) {
            def idAnnotations = params.annotations.split(',')
            idAnnotations.each { idAnnotation ->
                geometries << userAnnotationService.read(idAnnotation).location
            }
        }
        else if (params.annotations && params.reviewed) {
            def idAnnotations = params.annotations.split(',')
            idAnnotations.each { idAnnotation ->
                geometries << reviewedAnnotationService.read(idAnnotation).location
            }
        }
        else if (!params.annotations) {
            List<Long> termsIDS = params.terms?.split(',')?.collect {
                Long.parseLong(it)
            }
            if (!termsIDS) { //don't filter by term, take everything
                termsIDS = termService.getAllTermId(imageInstance.getProject())
            }

            List<Long> userIDS = params.users?.split(",")?.collect {
                Long.parseLong(it)
            }
            if (!userIDS) { //don't filter by users, take everything
                userIDS = secUserService.listLayers(imageInstance.getProject()).collect { it.id}
            }
            List<Long> imageIDS = [imageInstance.id]

            log.info params
            //Create a geometry corresponding to the ROI of the request (x,y,w,h)
            int x
            int y
            int w
            int h
            try {
                x = params.int('topLeftX')
                y = params.int('topLeftY')
                w = params.int('width')
                h = params.int('height')
            }catch (Exception e) {
                x = params.int('x')
                y = params.int('y')
                w = params.int('w')
                h = params.int('h')
            }
            Geometry roiGeometry = GeometryUtils.createBoundingBox(
                    x,                                      //minX
                    x + w,                                  //maxX
                    imageInstance.baseImage.getHeight() - (y + h),    //minX
                    imageInstance.baseImage.getHeight() - y           //maxY
            )


            //Fetch annotations with the requested term on the request image

            if (params.review) {
                ReviewedAnnotationListing ral = new ReviewedAnnotationListing(project: imageInstance.getProject().id, terms: termsIDS, reviewUsers: userIDS, images:imageIDS, bbox:roiGeometry, columnToPrint:['basic','meta','wkt','term']  )
                def result = annotationListingService.listGeneric(ral)
                log.info "annotations=${result.size()}"
                geometries = result.collect {
                    new WKTReader().read(it["location"])
                }

            } else {
                log.info "imageInstance=${imageInstance}"
                log.info "roiGeometry=${roiGeometry}"
                log.info "termsIDS=${termsIDS}"
                log.info "userIDS=${userIDS}"
                Collection<UserAnnotation> annotations = userAnnotationService.list(imageInstance, roiGeometry, termsIDS, userIDS)
                log.info "annotations=${annotations.size()}"
                geometries = annotations.collect { geometry ->
                    geometry.getLocation()
                }
            }
        }
        GeometryCollection geometryCollection = new GeometryCollection((Geometry[])geometries, new GeometryFactory())
        return new WKTWriter().write(geometryCollection)
    }

    def download() {
        ImageInstance imageInstance = imageInstanceService.read(params.long("id"))
        if (imageInstance) {
            String url = imageServerService.downloadUri(imageInstance.baseImage)
            redirect(url: url)
        } else {
            responseNotFound("Image", params.id)
        }
    }

    def getReferenceSlice() {
        SliceInstance slice = imageInstanceService.getReferenceSlice(params.long("id"))
        if (slice) {
            responseSuccess(slice)
        }
        else {
            responseNotFound("SliceInstance", "ImageInstance", params.id)
        }
    }

    def metadata() {
        ImageInstance imageInstance = imageInstanceService.read(params.long("id"))
        if (imageInstance) {
            responseSuccess(propertyService.list(imageInstance.baseImage))
        } else {
            responseNotFound("Image", params.id)
        }
    }

    // as I have one field that I override differently if I am a manager, I overrided all the response method until the super method is more flexible
    @Override
    protected def response(data) {
        withFormat {
            json {
                def result = data as JSON

                boolean filterEnabled = false
                Project project

                if(params.project){
                    project = Project.read(params.long("project"))
                    filterEnabled = project.blindMode
                } else if(params.id && !(["windowUrl", "cameraUrl", "getReferenceSlice"].contains(params.action.GET))){
                    project = ImageInstance.read(params.long("id"))?.project
                    if(project) filterEnabled = project.blindMode
                }

                if(filterEnabled){
                    boolean manager = false
                    if(project) {
                        try{
                            securityACLService.checkIsAdminContainer(project, cytomineService.currentUser)
                            manager = true
                        } catch(ForbiddenException e){}
                    }

                    JSONObject json = JSON.parse(result.toString())
                    if(json.containsKey("collection")) {
                        for(JSONObject element : json.collection) {
                            filterOneElement(element, manager)
                        }
                    } else {
                        filterOneElement(json, manager)
                    }

                    result = json as JSON
                }

                render result
            }
            jsonp {
                response.contentType = 'application/javascript'
                render "${params.callback}(${data as JSON})"
            }
        }
    }


    protected void filterOneElement(JSONObject element, boolean manager) {
        if(!manager) {
            element['instanceFilename'] = null
            element['filename'] = null
            element['originalFilename'] = null
            element['path'] = null
            element['fullPath'] = null
        }
    }

}
