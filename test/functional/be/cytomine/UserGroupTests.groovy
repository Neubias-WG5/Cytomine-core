package be.cytomine

import be.cytomine.test.BasicInstance
import be.cytomine.test.Infos

import be.cytomine.security.UserGroup
import be.cytomine.test.http.UserGroupAPI

/**
 * Created by IntelliJ IDEA.
 * User: lrollus
 * Date: 16/03/11
 * Time: 16:12
 * To change this template use File | Settings | File Templates.
 */
class UserGroupTests extends functionaltestplugin.FunctionalTestCase {

    void testShowUserGroup() {
        def user = BasicInstance.newUser
        def group =  BasicInstance.getBasicGroupNotExist()
        BasicInstance.saveDomain(group)
        UserGroup userGroup =  new UserGroup(user: user,group : group)
        BasicInstance.saveDomain(userGroup)

        def result = UserGroupAPI.showUserGroupCurrent(user.id,group.id, Infos.GOODLOGIN, Infos.GOODPASSWORD)
        assertEquals(200, result.code)

        result = UserGroupAPI.showUserGroupCurrent(-99,-99, Infos.GOODLOGIN, Infos.GOODPASSWORD)
        assertEquals(404, result.code)
    }

    void testListUserGroup() {
        def user = BasicInstance.newUser

        def result = UserGroupAPI.list(user.id,Infos.GOODLOGIN, Infos.GOODPASSWORD)
        assertEquals(200, result.code)
    }

    void testCreateUserGroup() {
        def user = BasicInstance.newUser
        def group =  BasicInstance.getBasicGroupNotExist()
        BasicInstance.saveDomain(group)
        UserGroup userGroup =  new UserGroup(user: user,group : group)

        def result = UserGroupAPI.create(user.id,userGroup.encodeAsJSON(),Infos.GOODLOGIN, Infos.GOODPASSWORD)
        assertEquals(200, result.code)
    }

    void testDeleteUserGroup() {
        def user = BasicInstance.newUser
        def group =  BasicInstance.getBasicGroupNotExist()
        BasicInstance.saveDomain(group)
        UserGroup userGroup =  new UserGroup(user: user,group : group)
        BasicInstance.saveDomain(userGroup)

        def result = UserGroupAPI.delete(user.id,group.id,Infos.GOODLOGIN, Infos.GOODPASSWORD)
        assertEquals(200, result.code)
    }

}
