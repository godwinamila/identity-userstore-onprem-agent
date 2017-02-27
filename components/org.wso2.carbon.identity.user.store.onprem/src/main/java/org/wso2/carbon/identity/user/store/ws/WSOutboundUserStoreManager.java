/*
*  Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.carbon.identity.user.store.ws;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.user.api.RealmConfiguration;
import org.wso2.carbon.user.core.UserRealm;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.claim.ClaimManager;
import org.wso2.carbon.user.core.jdbc.JDBCUserStoreManager;
import org.wso2.carbon.user.core.profile.ProfileConfigurationManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

public class WSOutboundUserStoreManager extends JDBCUserStoreManager {

    private static Log log = LogFactory.getLog(WSOutboundUserStoreManager.class);
    // This instance is used to generate the hash values

    public WSOutboundUserStoreManager() {

    }

    // You must implement at least one constructor
    public WSOutboundUserStoreManager(RealmConfiguration realmConfig, Map<String, Object> properties, ClaimManager
            claimManager, ProfileConfigurationManager profileManager, UserRealm realm, Integer tenantId)
            throws UserStoreException {
        super(realmConfig, properties, claimManager, profileManager, realm, tenantId);
        log.info("WSUserStoreManager initialized...");
    }

    @Override
    public boolean doAuthenticate(String userName, Object credential) throws UserStoreException {
        boolean isAuthenticated = false;
        if (userName != null && credential != null) {
            try {
                int tenantId = CarbonContext.getThreadLocalCarbonContext().getTenantId();
                Connection dbConnection;
                PreparedStatement prepStmt;
                dbConnection = this.getDBConnection();
                dbConnection.setAutoCommit(false);

                prepStmt = dbConnection.prepareStatement(QueryConstant.ADD_UM_OPERATIONS);
                prepStmt.setString(1, "8912812781281281921");
                prepStmt.setString(2, "authenticate");
                prepStmt.setString(3, userName);
                prepStmt.setString(4, "N");
                prepStmt.setInt(5, tenantId);

                int result = prepStmt.executeUpdate();
            } catch (SQLException ex) {
                log.error("Error while adding user authentication request for next operation.", ex);
                throw new UserStoreException("Authentication Failure");
            }
        }
        return isAuthenticated;
    }

}