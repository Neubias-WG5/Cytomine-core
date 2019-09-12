package be.cytomine.sql

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

import be.cytomine.AnnotationDomain
import be.cytomine.CytomineDomain
import be.cytomine.Exception.ObjectNotFoundException
import be.cytomine.Exception.WrongArgumentException
import be.cytomine.image.ImageInstance
import be.cytomine.image.SliceInstance
import be.cytomine.ontology.Term
import be.cytomine.ontology.Track
import be.cytomine.project.Project
import be.cytomine.security.SecUser
import com.vividsolutions.jts.io.WKTReader

/**
 * User: lrollus
 * Date: 31/05/13
 *
 *
 */
abstract class AnnotationListing {

    def paramsService
    /**
     *  default property group to show
     */
    static final def availableColumnDefault = ['basic', 'meta', 'term']

    /**
     *  all properties group available, each value is a list of assoc [propertyName, SQL columnName/methodName)
     *  If value start with #, don't use SQL column, its a "trensiant property"
     */
    abstract def availableColumn

    def columnToPrint

    def project = null
    def image = null
    def images = null

    def slice = null
    def slices = null

    def track = null
    def tracks = null
    def beforeSlice = null
    def afterSlice = null
    def sliceDimension = null

    def user = null
    def userForTermAlgo = null
    def usersForTermAlgo = null

    def term = null
    def terms = null

    def suggestedTerm = null
    def suggestedTerms = null

    def users = null //for user that draw annotation
    def usersForTerm = null //for user that add a term to annotation



    def reviewUsers



    def afterThan = null
    def beforeThan = null



    def notReviewedOnly = false
    def noTerm = false
    def noAlgoTerm = false
    def multipleTerm = false

    def bbox = null
    def bboxAnnotation = null

    def baseAnnotation = null
    def maxDistanceBaseAnnotation = null




    def parents

    //not used for search critera (just for specific request
    def avoidEmptyCentroid = false
    def excludedAnnotation = null

    def kmeans = false
    def kmeansValue = 3

    abstract def getFrom()

    abstract def getDomainClass()

    abstract def buildExtraRequest()

    def extraColmun = [:]

    def orderBy = null

    def addExtraColumn(def propName, def column) {
        extraColmun[propName] = column
    }

    /**
     * Get all properties name available
     * If group argument is provieded, just get properties from these groups
     */
    def getAllPropertiesName(List groups = getAvailableColumn().collect { it.key }) {
        def propNames = []
        groups.each { groupName ->
            getAvailableColumn().get(groupName).each { assoc ->
                assoc.each {
                    propNames << it.key
                }
            }
        }
        propNames
    }

    /**
     * Get all properties to print
     */
    def buildColumnToPrint() {
        if (!columnToPrint) {
            columnToPrint = availableColumnDefault.clone()
        }
        columnToPrint.add('basic') //mandatory to have id
        columnToPrint = columnToPrint.unique()

        def columns = []

        getAvailableColumn().each {
            if (columnToPrint.contains(it.key)) {
                it.value.each { columnAssoc ->
                    columns << columnAssoc
                }
            }
        }
        extraColmun.each {
            columns << it
        }
        return columns
    }

    /**
     * Get container for security check
     */
    CytomineDomain container() {
        if (project) return Project.read(project)
        if (image) return ImageInstance.read(image)?.container()
        if (images) {
            def projectList = images.collect { ImageInstance.read(it).project }.unique()
            if (projectList.size() > 1) {
                throw new WrongArgumentException("Images from filter must all be from the same project!")
            }
            return projectList.first()
        }
        if (slice) return SliceInstance.read(slice)?.container()
        if (slices) {
            def projectList = slices.collect { SliceInstance.read(it).project }.unique()
            if (projectList.size() > 1) {
                throw new WrongArgumentException("Slices from filter must all be from the same project!")
            }
            return projectList.first()
        }
        throw new WrongArgumentException("There is no project or image or slice filter. We cannot check acl!")
    }

    /**
     * Generate SQL request string
     */
    def getAnnotationsRequest() {

        buildExtraRequest()

        def columns = buildColumnToPrint()
        def sqlColumns = []
        def postComputedColumns = []

        columns.each {
            if (!it.value.startsWith("#")) {
                sqlColumns << it
            } else {
                postComputedColumns << it
            }
        }

        String whereRequest =
                getProjectConst() +
                        getUserConst() +
                        getUsersConst() +

                        getImageConst() +
                        getImagesConst() +
                        
                        getSliceConst() +
                        getSlicesConst() +

                        getTermConst() +
                        getTermsConst() +

                        getTrackConst() +
                        getTracksConst() +
                        getBeforeOrAfterSliceConst() +

                        getUsersForTermConst() +

                        getUserForTermAlgoConst() +
                        getUsersForTermAlgoConst() +

                        getSuggestedTermConst() +
                        getSuggestedTermsConst() +

                        getNotReviewedOnlyConst() +
                        getParentsConst() +
                        getAvoidEmptyCentroidConst() +
                        getReviewUsersConst() +

                        getIntersectConst() +
                        getIntersectAnnotationConst() +
                        getMaxDistanceAnnotationConst() +
                        getExcludedAnnotationConst() +

                        getBeforeThan() +
                        getAfterThan() +
                        createOrderBy()

        if (term || terms || track || tracks) {
            def request = "SELECT DISTINCT a.*, "

            if (term || terms) {
                sqlColumns = sqlColumns.findAll{it.key != "term" && it.key != "annotationTerms" && it.key != "userTerm"}
                request += "at.term_id as term, at.id as annotationTerms, at.user_id as userTerm "
            }

            if ((term || terms) && (track || tracks))
                request += ", "

            if (track || tracks) {
                sqlColumns = sqlColumns.findAll{it.key != "track" && it.key != "annotationTracks"}
                request += "atr.track_id as track, atr.id as annotationTracks "
            }

            request += "FROM (" + getSelect(sqlColumns) + getFrom() + whereRequest + ") a \n"

            if (term || terms)
                request += "LEFT OUTER JOIN annotation_term at ON a.id = at.user_annotation_id "

            if (track || tracks)
                request += "LEFT OUTER JOIN annotation_track atr ON a.id = atr.annotation_ident "

            request += "ORDER BY a.id DESC"
            request += ((term || terms) ? ", at.term_id " : "")
            request += ((track || tracks) ? ", atr.track_id " : "")
            return request
        }

        return getSelect(sqlColumns) + getFrom() + whereRequest

    }

    /**
     * Generate SQL string for SELECT with only asked properties
     */
    def getSelect(def columns) {
        if (kmeansValue >= 3) {
            def requestHeadList = []
            columns.each {
                requestHeadList << it.value + " as " + it.key
            }
            return "SELECT " + requestHeadList.join(', ') + " \n"
        } else {
            return "SELECT ST_ClusterKMeans(location, 5) OVER () AS kmeans, location\n"
        }

    }
    /**
     * Add property group to show if use in where constraint.
     * E.g: if const with term_id = x, we need to make a join on annotation_term.
     * So its mandatory to add "term" group properties (even if not asked)
     */
    def addIfMissingColumn(def column) {
        if (!columnToPrint.contains(column)) {
            columnToPrint.add(column)
        }
    }

    def getProjectConst() {
        return (project ? "AND a.project_id = $project\n" : "")
    }

    def getUsersConst() {
        return (users ? "AND a.user_id IN (${users.join(",")})\n" : "")
    }

    def getReviewUsersConst() {
        return (reviewUsers ? "AND a.review_user_id IN (${reviewUsers.join(",")})\n" : "")
    }


    def getUsersForTermConst() {
        if (usersForTerm) {
            addIfMissingColumn('term')
            return "AND at.user_id IN (${usersForTerm.join(",")})\n"
        } else {
            return ""
        }
    }

    def getImagesConst() {

        if (images && project && images.size() == Project.read(project).countImages) {
            return "" //images number equals to project image number, no const needed
        } else if (images && images.isEmpty()) {
            throw new ObjectNotFoundException("The image has been deleted!")
        } else {
            return (images ? "AND a.image_id IN (${images.join(",")})\n" : "")
        }

    }

    def getImageConst() {
        if (image) {
            def image = ImageInstance.read(image)
            if (!image || image.checkDeleted()) {
                throw new ObjectNotFoundException("Image $image not exist!")
            }
            return "AND a.image_id = ${image.id}\n"
        } else {
            return ""
        }
    }

    def getSlicesConst() {

//        if (slices && image && slices.size() == Project.read(project).countSlices) {
//            return "" //slices number equals to image slice number, no const needed
//        } else
        if (slices && slices.isEmpty()) {
            throw new ObjectNotFoundException("The slice has been deleted!")
        } else {
            return (slices ? "AND a.slice_id IN (${slices.join(",")})\n" : "")
        }

    }

    def getSliceConst() {
        if (slice) {
            def slice = SliceInstance.read(slice)
            if (!slice || slice.checkDeleted()) {
                throw new ObjectNotFoundException("Slice $slice not exist!")
            }
            return "AND a.slice_id = ${slice.id}\n"
        } else {
            return ""
        }
    }

    def getUserConst() {
        if (user) {
            if (!SecUser.read(user)) {
                throw new ObjectNotFoundException("User $user not exist!")
            }
            return "AND a.user_id = ${user}\n"
        } else {
            return ""
        }
    }

    abstract def getNotReviewedOnlyConst()

    def getIntersectConst() {
        return (bbox ? "AND ST_Intersects(a.location,ST_GeometryFromText('${bbox.toString()}',0))\n" : "")
    }

    def getIntersectAnnotationConst() {
        return (bboxAnnotation ? "AND ST_Intersects(a.location,ST_GeometryFromText('${bboxAnnotation.toString()}',0))\n" : "")
    }

    def getMaxDistanceAnnotationConst() {
        if(maxDistanceBaseAnnotation!=null) {
            if(!baseAnnotation) {
                throw new ObjectNotFoundException("You need to provide a 'baseAnnotation' parameter (annotation id/location = ${baseAnnotation})!")
            }
            try {
                AnnotationDomain baseAnnotation = AnnotationDomain.getAnnotationDomain(baseAnnotation)
                //ST_distance(a.location,ST_GeometryFromText('POINT (0 0)'))
                return "AND ST_distance(a.location,ST_GeometryFromText('${baseAnnotation.wktLocation}')) <= $maxDistanceBaseAnnotation\n"
            } catch (Exception e) {
                return "AND ST_distance(a.location,ST_GeometryFromText('${baseAnnotation}')) <= $maxDistanceBaseAnnotation\n"
            }
        } else {
            return ""
        }
    }
    //

    def getAvoidEmptyCentroidConst() {
        return (avoidEmptyCentroid ? "AND ST_IsEmpty(st_centroid(a.location))=false\n" : "")
    }

    def getTermConst() {
        if (term) {
            if (!Term.read(term)) {
                throw new ObjectNotFoundException("Term $term not exist!")
            }
            addIfMissingColumn('term')
            return " AND at.term_id = ${term}\n"
        } else {
            return ""
        }
    }

    def getParentsConst() {
        if (parents) {
            return " AND a.parent_ident IN (${parents.join(",")})\n"
        } else {
            return ""
        }
    }


    def getTermsConst() {
        if (terms) {
            addIfMissingColumn('term')
            return "AND at.term_id IN (${terms.join(',')})\n"
        } else {
            return ""
        }
    }

    def getTrackConst() {
        if (track) {
            if (!Track.read(track)) {
                throw new ObjectNotFoundException("Track $track not exists !")
            }
            addIfMissingColumn('track')
            return " AND atr.track_id = ${track}\n"
        } else {
            return ""
        }
    }

    def getTracksConst() {
        if (tracks) {
            addIfMissingColumn('track')
            return "AND atr.track_id IN (${tracks.join(',')})\n"
        } else {
            return ""
        }
    }

    def getBeforeOrAfterSliceConst() {
        if ((track || tracks) && (beforeSlice || afterSlice)) {
            if (!sliceDimension || !['C', 'Z', 'T'].contains(sliceDimension)) {
                throw new WrongArgumentException("You need to provide a valid slice dimension (C,Z,T) to use beforeSlice")
            }
            addIfMissingColumn('slice')
            def sliceId = (beforeSlice) ? beforeSlice : afterSlice
            def slice = SliceInstance.read(sliceId)
            if (!slice) {
                throw new ObjectNotFoundException("Slice $sliceId not exists !")
            }

            def constraint = ''
            if (sliceDimension == 'C') {
                constraint = 'channel'
            }
            else if (sliceDimension == 'Z') {
                constraint = 'zStack'
            }
            else if (sliceDimension == 'T') {
                constraint = 'time'
            }
            def equals = ['channel', 'zStack', 'time'] - constraint
            def snakeCase = [channel: 'channel', zStack: 'z_stack', time: 'time']
            def sign = (beforeSlice) ? '<' : '>'

            return "AND asl.${snakeCase[constraint]} ${sign} ${slice.baseSlice[constraint]}\n" +
                    "AND asl.${snakeCase[equals[0]]} = ${slice.baseSlice[equals[0]]}\n" +
                    "AND asl.${snakeCase[equals[1]]} = ${slice.baseSlice[equals[1]]}\n"
        } else {
            return ""
        }
    }

    def getExcludedAnnotationConst() {
        return (excludedAnnotation ? "AND a.id <> ${excludedAnnotation}\n" : "")
    }

    def getSuggestedTermConst() {
        if (suggestedTerm) {
            if (!Term.read(suggestedTerm)) {
                throw new ObjectNotFoundException("Term $suggestedTerm not exist!")
            }
            addIfMissingColumn('algo')
            return "AND aat.term_id = ${suggestedTerm}\n"
        } else {
            return ""
        }
    }

    def getSuggestedTermsConst() {
        if (suggestedTerms) {
            addIfMissingColumn('algo')
            return "AND aat.term_id IN (${suggestedTerms.join(",")})\n"
        } else {
            return ""
        }
    }

    def getUserForTermAlgoConst() {
        if (userForTermAlgo) {
            addIfMissingColumn('term')
            addIfMissingColumn('algo')
            return "AND aat.user_job_id = ${userForTermAlgo}\n"
        } else {
            return ""
        }
    }

    def getUsersForTermAlgoConst() {
        if (usersForTermAlgo) {
            addIfMissingColumn('algo')
            addIfMissingColumn('term')
            return "AND aat.user_job_id IN (${usersForTermAlgo.join(',')})\n"
        } else {
            return ""
        }
    }

    abstract def createOrderBy()

    def getBeforeThan() {
        if (beforeThan) {
            return "AND a.created < '${beforeThan}'\n"
        } else {
            return ""
        }
    }
    def getAfterThan() {
        if (afterThan) {
            return "AND a.created > '${afterThan}'\n"
        } else {
            return ""
        }
    }

    @Override
    public String toString(){
        return """ AnnotationListing
columnToPrint : $columnToPrint
project = $project
user = $user
term = $term
image = $image
slice = $slice
track = $track
suggestedTerm = $suggestedTerm
userForTermAlgo = $userForTermAlgo
users = $users
usersForTerm = $usersForTerm
usersForTermAlgo = $usersForTermAlgo
reviewUsers = $reviewUsers
terms = $terms
images = $images
slices = $slices
tracks = $tracks
afterThan = $afterThan
beforeThan = $beforeThan
suggestedTerms = $suggestedTerms
notReviewedOnly = $notReviewedOnly
noTerm = $noTerm
noAlgoTerm = $noAlgoTerm
multipleTerm = $multipleTerm
bboxAnnotation = $bboxAnnotation
baseAnnotation = $baseAnnotation
maxDistanceBaseAnnotation = $maxDistanceBaseAnnotation
bbox = $bbox
parents=$parents
avoidEmptyCentroid = $avoidEmptyCentroid
excludedAnnotation = $excludedAnnotation
kmeans = $kmeans
"""

    }
}

class UserAnnotationListing extends AnnotationListing {

    def getDomainClass() {
        return "be.cytomine.ontology.UserAnnotation"
    }

    /**
     *  all properties group available, each value is a list of assoc [propertyName, SQL columnName/methodName)
     *  If value start with #, don't use SQL column, its a "trensiant property"
     */
    def availableColumn = [
        basic: [
                id: 'a.id'
        ],
        meta: [
                created: 'extract(epoch from a.created)*1000',
                updated: 'extract(epoch from a.updated)*1000',
                image: 'a.image_id',
                slice: 'a.slice_id',
                project: 'a.project_id',
                user: 'a.user_id',

                nbComments: 'a.count_comments',

                countReviewedAnnotations: 'a.count_reviewed_annotations', // not in single annot marshaller
                reviewed: '(a.count_reviewed_annotations>0)',

                cropURL: '#cropURL',
                smallCropURL: '#smallCropURL',
                url: '#url',
                imageURL: '#imageURL'
        ],
        wkt: [
                location: 'a.wkt_location',
                geometryCompression: 'a.geometry_compression',
        ],
        gis: [
                area: 'area',
                areaUnit: 'area_unit',
                perimeter: 'perimeter',
                perimeterUnit: 'perimeter_unit',
                x: 'ST_X(ST_centroid(a.location))',
                y: 'ST_Y(ST_centroid(a.location))'
        ],
        term: [
                term: 'at.term_id',
                annotationTerms: 'at.id', // not in single annot marshaller
                userTerm: 'at.user_id' // not in single annot marshaller
        ],
        track: [
                track: 'atr.track_id',
                annotationTracks: 'atr.id'
        ],
        image: [
                originalFilename: 'ai.original_filename', // not in single annot marshaller
                instanceFilename: 'ii.instance_filename' // not in single annot marshaller
        ],
        slice: [
                channel: 'asl.channel', // not in single annot marshaller
                zStack: 'asl.z_stack', // not in single annot marshaller
                time: 'asl.time' // not in single annot marshaller
        ],
        algo: [
                id: 'aat.id', // not in single annot marshaller
                rate: 'aat.rate', // not in single annot marshaller
                idTerm: 'aat.term_id', // not in single annot marshaller
                idExpectedTerm: 'aat.expected_term_id' // not in single annot marshaller
        ],
        user: [
                creator: 'u.username', // not in single annot marshaller
                lastname: 'u.lastname', // not in single annot marshaller
                firstname: 'u.firstname' // not in single annot marshaller
        ]
    ]

    /**
     * Generate SQL string for FROM
     * FROM depends on data to print (if image name is aksed, need to join with imageinstance+abstractimage,...)
     */
    def getFrom() {
        def from = "FROM user_annotation a "
        def where = "WHERE true\n"

        if (multipleTerm) {
            from += "LEFT OUTER JOIN annotation_term at ON a.id = at.user_annotation_id "
            from += "LEFT OUTER JOIN annotation_term at2 ON a.id = at2.user_annotation_id "
            where += "AND at.id <> at2.id AND at.term_id <> at2.term_id "
        }
        else if (noTerm) {
            from += "LEFT JOIN (SELECT * from annotation_term x ${users ? "where x.user_id IN (${users.join(",")})" : ""}) at ON a.id = at.user_annotation_id "
            where += "AND at.id IS NULL \n"
        }
        else if (noAlgoTerm) {
            from += "LEFT JOIN (SELECT * from algo_annotation_term x ${users ? "where x.user_id IN (${users.join(",")})" : ""}) aat ON a.id = aat.annotation_ident "
            where += "AND aat.id IS NULL \n"
        }
        else if (columnToPrint.contains('term')) {
            from += "LEFT OUTER JOIN annotation_term at ON a.id = at.user_annotation_id "
        }

        if (columnToPrint.contains('track')) {
            from += "LEFT OUTER JOIN annotation_track atr ON a.id = atr.annotation_ident "
        }

        if (columnToPrint.contains('user')) {
            from += "INNER JOIN sec_user u ON a.user_id = u.id "
        }

        if (columnToPrint.contains('image')) {
            from += "INNER JOIN image_instance ii ON a.image_id = ii.id INNER JOIN abstract_image ai ON ii.base_image_id = ai.id "
        }

        if (columnToPrint.contains('algo')) {
            from += "INNER JOIN algo_annotation_term aat ON aat.annotation_ident = a.id "
        }

        if (columnToPrint.contains('slice')) {
            from += "INNER JOIN slice_instance si ON a.slice_id = si.id INNER JOIN abstract_slice asl ON si.base_slice_id = asl.id "
        }

        return from + "\n" + where
    }

    def buildExtraRequest() {

    }

    def getNotReviewedOnlyConst() {
        return (notReviewedOnly ? "AND a.count_reviewed_annotations=0\n" : "")
    }

    def createOrderBy() {
        if (kmeansValue < 3) return ""
        def orderByRate = (usersForTermAlgo || userForTermAlgo || suggestedTerm || suggestedTerms)
        if (orderByRate) {
            return "ORDER BY aat.rate desc"
        } else if (!orderBy) {
            return "ORDER BY a.id desc " + ((term || terms || columnToPrint.contains("term")) ? ", at.term_id " : "") + ((track || tracks || columnToPrint.contains("track")) ? ", atr.track_id " : "")
        } else {
            return "ORDER BY " + orderBy.collect { it.key + " " + it.value }.join(", ")
        }
    }
}


class AlgoAnnotationListing extends AnnotationListing {
    //parentIdent : 'a.parent_ident',
    //user -> user_job_id?
    //algo rate

    def getDomainClass() {
        return "be.cytomine.ontology.AlgoAnnotation"
    }

    /**
     *  all properties group available, each value is a list of assoc [propertyName, SQL columnName/methodName)
     *  If value start with #, don't use SQL column, its a "trensiant property"
     */
    def availableColumn = [
        basic: [
                id: 'a.id'
        ],
        meta: [
                created: 'extract(epoch from a.created)*1000',
                updated: 'extract(epoch from a.updated)*1000',
                image: 'a.image_id',
                slice: 'a.slice_id',
                project: 'a.project_id',
                user: 'a.user_id',

                nbComments: 'a.count_comments',

                countReviewedAnnotations: 'a.count_reviewed_annotations', // not in single annot marshaller
                reviewed: '(a.count_reviewed_annotations>0)',

                cropURL: '#cropURL',
                smallCropURL: '#smallCropURL',
                url: '#url',
                imageURL: '#imageURL'
        ],
        wkt: [
                location: 'a.wkt_location',
                geometryCompression: 'a.geometry_compression',
        ],
        gis: [
                area: 'area',
                areaUnit: 'area_unit',
                perimeter: 'perimeter',
                perimeterUnit: 'perimeter_unit',
                x: 'ST_X(ST_centroid(a.location))',
                y: 'ST_Y(ST_centroid(a.location))'
        ],
        term: [
                term: 'aat.term_id',
                annotationTerms: 'aat.id',
                userTerm: 'aat.user_job_id',
                rate: 'aat.rate'
        ],
        track: [
                track: 'atr.track_id',
                annotationTracks: 'atr.id'
        ],
        image: [
                originalFilename: 'ai.original_filename', // not in single annot marshaller
                instanceFilename: 'ii.instance_filename' // not in single annot marshaller
        ],
        slice: [
                channel: 'asl.channel', // not in single annot marshaller
                zStack: 'asl.z_stack', // not in single annot marshaller
                time: 'asl.time' // not in single annot marshaller
        ],
        user: [
                creator: 'u.username', // not in single annot marshaller
                software: 's.name', // not in single annot marshaller
                job: 'j.id' // not in single annot marshaller
        ]
    ]

    /**
     * Generate SQL string for FROM
     * FROM depends on data to print (if image name is aksed, need to join with imageinstance+abstractimage,...)
     */
    def getFrom() {
        def from = "FROM algo_annotation a "
        def where = "WHERE true\n"

        if (multipleTerm) {
            from += "LEFT OUTER JOIN algo_annotation_term att ON a.id = att.annotation_ident "
            from += "LEFT OUTER JOIN algo_annotation_term att2 ON a.id = att2.annotation_ident "
            where += "AND att.id <> att2.id AND att.term_id <> att2.term_id "
        }
        else if (noTerm || noAlgoTerm) {
            from = "$from LEFT JOIN (SELECT * from algo_annotation_term x ${users ? "where x.user_job_id IN (${users.join(",")})" : ""}) aat ON a.id = aat.annotation_ident "
            where = "$where AND aat.id IS NULL \n"

        } else if (columnToPrint.contains('term')) {
            from += "LEFT JOIN algo_annotation_term aat ON a.id = aat.annotation_ident "
        }

        if (columnToPrint.contains('track')) {
            from += "LEFT OUTER JOIN annotation_track atr ON a.id = atr.annotation_ident "
        }

        if (columnToPrint.contains('image')) {
            from += "INNER JOIN image_instance ii ON a.image_id = ii.id INNER JOIN abstract_image ai ON ii.base_image_id = ai.id "
        }

        if (columnToPrint.contains('slice')) {
            from += "INNER JOIN slice_instance si ON a.slice_id = si.id INNER JOIN abstract_slice asl ON si.base_slice_id = asl.id "
        }

        if (columnToPrint.contains('user')) {
            from += "INNER JOIN sec_user u ON a.user_id = u.id INNER JOIN job j ON u.job_id = j.id INNER JOIN software s ON j.software_id = s.id "
        }

        return from + "\n" + where
    }

    def buildExtraRequest() {

    }

    def getTermConst() {
        if (term) {
            addIfMissingColumn('term')
            return " AND aat.term_id = ${term}\n"
        } else {
            return ""
        }
    }

    def getTermsConst() {

        if (terms) {
            addIfMissingColumn('term')
            return "AND aat.term_id IN (${terms.join(',')})\n"
        } else {
            return ""
        }
    }


    def getUserConst() {
        return (user ? "AND a.user_id = ${user}\n" : "")
    }

    def getUsersConst() {
        return (users ? "AND a.user_id IN (${users.join(",")})\n" : "")
    }

    def getNotReviewedOnlyConst() {
        return (notReviewedOnly ? "AND a.count_reviewed_annotations=0\n" : "")
    }

    def createOrderBy() {

        if (kmeansValue < 3) return ""
        if (orderBy) {
            return "ORDER BY " + orderBy.collect { it.key + " " + it.value }.join(", ")
        } else {
            def termOrder = (columnToPrint.contains("term") ? "aat.rate desc ," : "")
            def sliceOrder = ""
            if (sliceDimension && (beforeSlice || afterSlice)) {
                if (sliceDimension == 'C') sliceOrder = ' asl.channel asc, '
                else if (sliceDimension == 'Z') sliceOrder = ' asl.z_stack asc, '
                else if (sliceDimension == 'T') sliceOrder = ' asl.time asc, '
            }
            return "ORDER BY " + termOrder + sliceOrder + " a.id desc "
        }
    }
}


class ReviewedAnnotationListing extends AnnotationListing {

    def getDomainClass() {
        return "be.cytomine.ontology.ReviewedAnnotation"
    }

    /**
     *  all properties group available, each value is a list of assoc [propertyName, SQL columnName/methodName)
     *  If value start with #, don't use SQL column, its a "trensiant property"
     */
    def availableColumn = [
        basic: [
                id: 'a.id'
        ],
        meta: [
                created: 'extract(epoch from a.created)*1000',
                updated: 'extract(epoch from a.updated)*1000',
                image: 'a.image_id',
                slice: 'a.slice_id',
                project: 'a.project_id',
                user: 'a.user_id',

                nbComments: 'a.count_comments',

                reviewed: 'true',
                reviewUser: 'a.review_user_id',
                parentIdent: 'parent_ident',

                cropURL: '#cropURL',
                smallCropURL: '#smallCropURL',
                url: '#url',
                imageURL: '#imageURL',

        ],
        wkt: [
                location: 'a.wkt_location',
                geometryCompression: 'a.geometry_compression',
        ],
        gis: [
                area: 'area',
                areaUnit: 'area_unit',
                perimeter: 'perimeter',
                perimeterUnit: 'perimeter_unit',
                x: 'ST_X(ST_centroid(a.location))',
                y: 'ST_Y(ST_centroid(a.location))'
        ],
        term: [
                term: 'at.term_id',
                annotationTerms: "0",
                userTerm: 'a.user_id' //user who add the term, is the user that create reviewedannotation (a.user_id)
        ],
        image: [
                originalFilename: 'ai.original_filename', // not in single annot marshaller
                instanceFilename: 'ii.instance_filename' // not in single annot marshaller
        ],
        slice: [
                channel: 'asl.channel', // not in single annot marshaller
                zStack: 'asl.z_stack', // not in single annot marshaller
                time: 'asl.time' // not in single annot marshaller
        ],
        algo: [
                id: 'aat.id',
                rate: 'aat.rate'
        ],
        user: [
                creator: 'u.username', // not in single annot marshaller
                lastname: 'u.lastname', // not in single annot marshaller
                firstname: 'u.firstname' // not in single annot marshaller
        ]
    ]

    /**
     * Generate SQL string for FROM
     * FROM depends on data to print (if image name is aksed, need to join with imageinstance+abstractimage,...)
     */
    def getFrom() {
        def from = "FROM reviewed_annotation a "
        def where = "WHERE true\n"

        if (multipleTerm) {
            from += "LEFT OUTER JOIN reviewed_annotation_term at ON a.id = at.reviewed_annotation_terms_id "
            from += "LEFT OUTER JOIN reviewed_annotation_term at2 ON a.id = at2.reviewed_annotation_terms_id "
            where += "AND at.term_id <> at2.term_id "
        }
        else if (noTerm) {
            from = "$from LEFT OUTER JOIN reviewed_annotation_term at ON a.id = at.reviewed_annotation_terms_id "
            where = "$where AND at.reviewed_annotation_terms_id IS NULL \n"
        }
        else if (columnToPrint.contains('term')) {
            from = "$from LEFT OUTER JOIN reviewed_annotation_term at ON a.id = at.reviewed_annotation_terms_id"
        }

        if (columnToPrint.contains('image')) {
            from += "INNER JOIN image_instance ii ON a.image_id = ii.id INNER JOIN abstract_image ai ON ii.base_image_id = ai.id "
        }

        if (columnToPrint.contains('slice')) {
            from += "INNER JOIN slice_instance si ON a.slice_id = si.id INNER JOIN abstract_slice asl ON si.base_slice_id = asl.id "
        }

        if (columnToPrint.contains('user')) {
            from += "INNER JOIN sec_user u ON a.user_id = u.id "
        }

        return from + "\n" + where
    }

    @Override
    def getUsersForTermConst() {
        return ""
    }

    def buildExtraRequest() {

        if (kmeansValue == 3 && image && bbox) {
            /**
             * We will sort annotation so that big annotation that covers a lot of annotation comes first (appear behind little annotation so we can select annotation behind other)
             * We compute in 'gc' the set of all other annotation that must be list
             * For each review annotation, we compute the number of other annotation that cover it (ST_CoveredBy => t or f => 0 or 1)
             *
             * ST_CoveredBy will return false if the annotation is not perfectly "under" the compare annotation (if some points are outside)
             * So in gc, we increase the size of each compare annotation just for the check
             * So if an annotation x is under y but x has some point next outside y, x will appear top (if no resize, it will appear top or behind).
             */
            def xfactor = "1.28"
            def yfactor = "1.28"
            def image = ImageInstance.read(image)
            //TODO:: get zoom info from UI client, display with scaling only with hight zoom (< annotations)

            double imageWidth = image.baseImage.width
            def bboxLocal = new WKTReader().read(bbox)
            double bboxWidth = bboxLocal.getEnvelopeInternal().width
            double ratio = bboxWidth / imageWidth * 100

            boolean zoomToLow = ratio > 50

            String subRequest
            if (zoomToLow) {
                subRequest = "(SELECT SUM(ST_CoveredBy(ga.location,gb.location )::integer) "
            } else {
                //too heavy to use with little zoom
                subRequest = "(SELECT SUM(ST_CoveredBy(ga.location,ST_Translate(ST_Scale(gb.location, $xfactor, $yfactor), ST_X(ST_Centroid(gb.location))*(1 - $xfactor), ST_Y(ST_Centroid(gb.location))*(1 - $yfactor) ))::integer) "

            }

            subRequest = subRequest +
                    "FROM reviewed_annotation ga, reviewed_annotation gb " +
                    "WHERE ga.id=a.id " +
                    "AND ga.id<>gb.id " +
                    "AND ga.image_id=gb.image_id " +
                    "AND ST_Intersects(gb.location,ST_GeometryFromText('" + bbox + "',0)))\n"

            //orderBy = ['numberOfCoveringAnnotation':'asc','id':'asc']
            orderBy = ['id': 'desc']
            //addExtraColumn("numberOfCoveringAnnotation",subRequest)
        }
    }

    def getNotReviewedOnlyConst() {
        return ""
    }

    def createOrderBy() {
        if (kmeansValue < 3) return ""
        if (orderBy) {
            return "ORDER BY " + orderBy.collect { it.key + " " + it.value }.join(", ")
        } else {
            return "ORDER BY a.id desc " + ((term || terms) ? ", at.term_id " : "")
        }
    }
}


class RoiAnnotationListing extends AnnotationListing {

    def getDomainClass() {
        return "be.cytomine.processing.RoiAnnotation"
    }

    /**
     *  all properties group available, each value is a list of assoc [propertyName, SQL columnName/methodName)
     *  If value start with #, don't use SQL column, its a "trensiant property"
     */
    def availableColumn = [
            basic: [
                    id: 'a.id'
            ],
            meta: [
                    created: 'extract(epoch from a.created)*1000',
                    updated: 'extract(epoch from a.updated)*1000',
                    image: 'a.image_id',
                    slice: 'a.slice_id',
                    project: 'a.project_id',
                    user: 'a.user_id',

                    cropURL: '#cropURL',
                    smallCropURL: '#smallCropURL',
                    url: '#url',
                    imageURL: '#imageURL'
            ],
            wkt: [
                    location: 'a.wkt_location',
                    geometryCompression: 'a.geometry_compression',
            ],
            gis: [
                    area: 'area',
                    areaUnit: 'area_unit',
                    perimeter: 'perimeter',
                    perimeterUnit: 'perimeter_unit',
                    x: 'ST_X(ST_centroid(a.location))',
                    y: 'ST_Y(ST_centroid(a.location))'
            ],
            image: [
                    originalFilename: 'ai.original_filename', // not in single annot marshaller
                    instanceFilename: 'ii.instance_filename' // not in single annot marshaller
            ],
            slice: [
                    channel: 'asl.channel', // not in single annot marshaller
                    zStack: 'asl.z_stack', // not in single annot marshaller
                    time: 'asl.time' // not in single annot marshaller
            ],
            user: [
                    creator: 'u.username', // not in single annot marshaller
                    lastname: 'u.lastname', // not in single annot marshaller
                    firstname: 'u.firstname' // not in single annot marshaller
            ]
    ]

    /**
     * Generate SQL string for FROM
     * FROM depends on data to print (if image name is aksed, need to join with imageinstance+abstractimage,...)
     */
    def getFrom() {

        def from = "FROM roi_annotation a "
        def where = "WHERE true\n"

        if (columnToPrint.contains('user')) {
            from += "INNER JOIN sec_user u ON a.user_id = u.id "
        }

        if (columnToPrint.contains('image')) {
            from += "INNER JOIN image_instance ii ON a.image_id = ii.id INNER JOIN abstract_image ai ON ii.base_image_id = ai.id "
        }

        if (columnToPrint.contains('slice')) {
            from += "INNER JOIN slice_instance si ON a.slice_id = si.id INNER JOIN abstract_slice asl ON si.base_slice_id = asl.id "
        }

        return from + "\n" + where
    }

    def createOrderBy() {
        if (kmeansValue < 3) return ""
        if (orderBy) {
            return "ORDER BY " + orderBy.collect { it.key + " " + it.value }.join(", ")
        } else {
            return "ORDER BY a.id desc"
        }
    }

    def buildExtraRequest() {
        columnToPrint.remove("term")

    }

    def getNotReviewedOnlyConst() {
        return ""
    }
}