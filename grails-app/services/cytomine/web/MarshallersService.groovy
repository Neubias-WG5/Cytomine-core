package cytomine.web

class MarshallersService {

    def grailsApplication
    static transactional = false

    def initMarshallers() {
        print "initMarshallers"
        String baseUrl = grailsApplication.config.grails.serverURL
        grailsApplication.getDomainClasses().each { domain ->
            domain.metaClass.methods.each { method ->
                if (method.name.equals("registerMarshaller")) {
                    def domainFullName = domain.packageName + "." + domain.name
                    println "Init Marshaller for domain class : " + domainFullName
                    def domainInstance = grailsApplication.getDomainClass(domainFullName).newInstance()
                    domainInstance.registerMarshaller(baseUrl)
                }

            }

        }
    }
}
