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
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.UserRealm;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.claim.ClaimManager;
import org.wso2.carbon.user.core.jdbc.JDBCUserStoreManager;
import org.wso2.carbon.user.core.profile.ProfileConfigurationManager;
import org.wso2.carbon.user.core.util.DatabaseUtil;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

public class WSOutboundUserStoreManager extends JDBCUserStoreManager {

    private static Log log = LogFactory.getLog(WSOutboundUserStoreManager.class);

    public WSOutboundUserStoreManager() {

    }

    public WSOutboundUserStoreManager(DataSource ds,
            org.wso2.carbon.user.api.RealmConfiguration realmConfig,
            int tenantId, boolean addInitData) throws UserStoreException {
        super(ds, realmConfig, tenantId, addInitData);
    }

    public WSOutboundUserStoreManager(org.wso2.carbon.user.api.RealmConfiguration realmConfig,
            Map<String, Object> properties,
            ClaimManager claimManager,
            ProfileConfigurationManager profileManager,
            UserRealm realm, Integer tenantId)
            throws UserStoreException {
        super(realmConfig, properties, claimManager, profileManager, realm, tenantId);
    }

    @Override
    public boolean doAuthenticate(String userName, Object credential) throws UserStoreException {
        boolean isAuthenticated = false;
        if (userName != null && credential != null) {

            String correlationId = addNextOperation(OperationsConstants.UM_OPERATION_TYPE_AUTHENTICATE,
                    getAuthenticationRequest(userName, credential));
            isAuthenticated = getAuthenticationResult(correlationId);
        }
        return isAuthenticated;
    }

    private String getAuthenticationRequest(String userName, Object credential) {
        return String.format("{username : '%s', password : '%s'}", userName, credential);
    }

    private String addNextOperation(String operationType, String requestData) throws UserStoreException {
        Connection dbConnection = null;
        PreparedStatement prepStmt = null;
        String correlationId = UUID.randomUUID().toString();
        try {
            //String tenantDomain = CarbonContext.getThreadLocalCarbonContext().getTenantDomain();
            String tenantDomain = IdentityTenantUtil.getTenantDomain(tenantId);

            dbConnection = this.getDBConnection();
            prepStmt = dbConnection.prepareStatement(QueryConstants.UM_OPERATIONS_ADD);
            prepStmt.setString(1, correlationId);
            prepStmt.setString(2, operationType);
            prepStmt.setString(3, requestData);
            prepStmt.setString(4, OperationsConstants.UM_OPERATION_STATUS_NEW);
            prepStmt.setString(5, tenantDomain);

            prepStmt.executeUpdate();
            dbConnection.commit();
        } catch (SQLException ex) {
            log.error("Error while adding user authentication request for next operation.", ex);
            throw new UserStoreException("Authentication Failure");
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, prepStmt);
        }
        return correlationId;
    }

    private boolean getAuthenticationResult(String correlationId) throws UserStoreException {

        PreparedStatement prepStmt = null;
        Connection dbConnection = null;
        ResultSet resultSet = null;
        try {

            dbConnection = this.getDBConnection();
            prepStmt = dbConnection.prepareStatement(QueryConstants.UM_OPERATIONS_GET_BY_CORRELATION_ID);
            prepStmt.setString(1, correlationId);
            while (true) {
                resultSet = prepStmt.executeQuery();
                if (resultSet.next()) {
                    String state = resultSet.getString("UM_STATE");
                    if (OperationsConstants.UM_OPERATION_STATUS_COMPLETED.contains(state)) {
                        String responseData = resultSet.getString("UM_RESPONSE_DATA");
                        return OperationsConstants.UM_OPERATION_AUTHENTICATE_RESULT_SUCCESS.equals(responseData);
                    }
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    log.error("Error occurred while wait for result.", e);
                }
            }
        } catch (SQLException ex) {
            log.error("Error while reading user authentication result for " + correlationId, ex);
            return false;
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, resultSet, prepStmt);
        }

    }

    @Override
    public boolean doCheckExistingUser(String userName) throws UserStoreException {
        return true;
    }

    public Date getPasswordExpirationTime(String userName) throws UserStoreException {
        return null;
    }

    public Map<String, String> getUserPropertyValues(String userName, String[] propertyNames,
            String profileName) throws UserStoreException {
        return null;
    }

    public String[] getProfileNames(String userName) throws UserStoreException {
        return new String[] { UserCoreConstants.DEFAULT_PROFILE };
    }

    public boolean isValueExisting(String sqlStmt, Connection dbConnection, Object... params)
            throws UserStoreException {
        return true;
    }

}