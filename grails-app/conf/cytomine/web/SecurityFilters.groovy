package cytomine.web

class SecurityFilters {
  def springSecurityService

  def filters = {
    all(uri:'/api/**') {
      before = {
         if(!springSecurityService.isLoggedIn()) {
            redirect(uri:'/')
            return false
         }
      }
      after = {

      }
      afterView = {

      }
    }
  }

}


