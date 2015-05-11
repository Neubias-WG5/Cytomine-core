package be.cytomine.utils.bootstrap

import be.cytomine.image.UploadedFile
import be.cytomine.security.SecRole
import be.cytomine.security.SecUser
import be.cytomine.security.SecUserSecRole
import be.cytomine.utils.Version
import groovy.sql.Sql
import org.apache.commons.lang.RandomStringUtils

/**
 * Cytomine @ GIGA-ULG
 * User: lrollus
 * This class contains all code when you want to change the database dataset.
 * E.g.: add new rows for a specific version, drop a column, ...
 *
 * The main method ("execChangeForOldVersion") is called by the bootstrap.
 * This method automatically run all initYYYYMMDD() methods from this class where YYYYMMDD is lt version number
 *
 * E.g. init20150115() will be call if the current version is init20150201.
 * init20150101() won't be call because: 20150101 < 20150115 < 20150201.
 *
 * At the end of the execChangeForOldVersion, the current version will be set thanks to the grailsApplication.metadata.'app.version' config
 */
class BootstrapOldVersionService {

    def grailsApplication
    def bootstrapUtilsService
    def dataSource

    void execChangeForOldVersion() {
        def methods = this.metaClass.methods*.name.sort().unique()

        println methods

        methods.remove("initChangeForOldVersion")

        println methods

        Version version = Version.getLastVersion()

        methods.each { method ->
            if(method.startsWith("init")) {
                Long methodDate = Long.parseLong(method.replace("init",""))
                if(methodDate>version.number) {
                    log.info "Run code for version > $methodDate"
                    this."init$methodDate"()
                } else {
                    log.info "Skip code for $methodDate"
                }
            }
        }


        Version.setCurrentVersion(Long.parseLong(grailsApplication.metadata.'app.version'))
    }

    void init20150101() {
        if(!SecUser.findByUsername("admin")) {
            bootstrapUtilsService.createUsers([[username : 'admin', firstname : 'Admin', lastname : 'Master', email : 'lrollus@ulg.ac.be', group : [[name : "GIGA"]], password : grailsApplication.config.grails.adminPassword, color : "#FF0000", roles : ["ROLE_USER", "ROLE_ADMIN"]]])
        }
        if(!SecUser.findByUsername("superadmin")) {
            bootstrapUtilsService.createUsers([[username: 'superadmin', firstname: 'Super', lastname: 'Admin', email: 'lrollus@ulg.ac.be', group: [[name: "GIGA"]], password: grailsApplication.config.grails.adminPassword, color: "#FF0000", roles: ["ROLE_USER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN"]]])
        }
        if(!SecUser.findByUsername("monitoring")) {
            bootstrapUtilsService.createUsers([[username : 'monitoring', firstname : 'Monitoring', lastname : 'Monitoring', email : 'lrollus@ulg.ac.be', group : [[name : "GIGA"]], password : RandomStringUtils.random(32,  (('A'..'Z') + ('0'..'0')).join().toCharArray()), color : "#FF0000", roles : ["ROLE_USER","ROLE_SUPER_ADMIN"]]])
        }

        //set public/private keys for special image server user
        //keys regenerated at each deployment
        SecUser imageServerUser = SecUser.findByUsername("ImageServer1")
        imageServerUser.setPrivateKey(grailsApplication.config.grails.ImageServerPrivateKey)
        imageServerUser.setPublicKey(grailsApplication.config.grails.ImageServerPublicKey)
        imageServerUser.save(flush : true)
    }

    void init20140925() {
        bootstrapUtilsService.addMimeVentanaTiff()
    }

    void  init20140717() {
        bootstrapUtilsService.addMimePhilipsTiff()
    }

    void  init20140716() {
        bootstrapUtilsService.addMimePyrTiff()
    }

    void  init20140630() {
        bootstrapUtilsService.transfertProperty()
    }

    void  init20140625() {
        if((UploadedFile.count() == 0 || UploadedFile.findByImageIsNull()?.size > 0)) {
            bootstrapUtilsService.checkImages()
        }
    }

    void  init20140601() {
        //version>2014 05 12
        if(!SecRole.findByAuthority("ROLE_SUPER_ADMIN")) {
            SecRole role = new SecRole(authority:"ROLE_SUPER_ADMIN")
            role.save(flush:true,failOnError: true)
        }

        //version>2014 05 12  OTOD: DO THIS FOR IFRES,...
        if(SecUser.findByUsername("ImageServer1")) {
            def imageUser = SecUser.findByUsername("ImageServer1")
            def superAdmin = SecRole.findByAuthority("ROLE_SUPER_ADMIN")
            if(!SecUserSecRole.findBySecUserAndSecRole(imageUser,superAdmin)) {
                new SecUserSecRole(secUser: imageUser,secRole: superAdmin).save(flush:true)
            }

        }

        if(SecUser.findByUsername("vmartin")) {
            def imageUser = SecUser.findByUsername("vmartin")
            def superAdmin = SecRole.findByAuthority("ROLE_SUPER_ADMIN")
            if(!SecUserSecRole.findBySecUserAndSecRole(imageUser,superAdmin)) {
                new SecUserSecRole(secUser: imageUser,secRole: superAdmin).save(flush:true)
            }
        }
    }


}
