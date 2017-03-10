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
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;
import org.wso2.carbon.identity.user.store.ws.messaging.JMSConnectionException;
import org.wso2.carbon.identity.user.store.ws.messaging.JMSConnectionFactory;
import org.wso2.carbon.identity.user.store.ws.model.UserOperation;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.UserRealm;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.claim.ClaimManager;
import org.wso2.carbon.user.core.jdbc.JDBCUserStoreManager;
import org.wso2.carbon.user.core.profile.ProfileConfigurationManager;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.sql.DataSource;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

public class WSOutboundUserStoreManager extends JDBCUserStoreManager {

    private static Log LOGGER = LogFactory.getLog(WSOutboundUserStoreManager.class);
    private final static String QUEUE_NAME_REQUEST = "requestQueue";
    private final static String QUEUE_NAME_RESPONSE = "responseQueue";

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
            return processAuthenticationRequest(userName, credential);
        }
        return isAuthenticated;
    }

    private String getAuthenticationRequest(String userName, Object credential) {
        return String.format("{username : '%s', password : '%s'}", userName, credential);
    }

    private boolean processAuthenticationRequest(String userName, Object credential) {

        JMSConnectionFactory connectionFactory = new JMSConnectionFactory();
        Connection connection = null;
        Session requestSession;
        Session responseSession;
        Destination requestQueue;
        Destination responseQueue;
        MessageProducer producer;
        try {
            connectionFactory.createActiveMQConnectionFactory();
            connection = connectionFactory.createConnection();
            connectionFactory.start(connection);
            requestSession = connectionFactory.createSession(connection);
            requestQueue = connectionFactory.createQueueDestination(requestSession, QUEUE_NAME_REQUEST);
            producer = connectionFactory
                    .createMessageProducer(requestSession, requestQueue, DeliveryMode.NON_PERSISTENT);

            String correlationId = UUID.randomUUID().toString();
            responseQueue = connectionFactory.createQueueDestination(requestSession, QUEUE_NAME_RESPONSE);

            addNextOperation(correlationId, OperationsConstants.UM_OPERATION_TYPE_AUTHENTICATE,
                    getAuthenticationRequest(userName, credential), requestSession, producer, responseQueue);

            responseSession = connectionFactory.createSession(connection);

            String filter = String.format("JMSCorrelationID='%s'", correlationId);
            MessageConsumer consumer = responseSession.createConsumer(responseQueue, filter);
            Message rm = consumer.receive(6000);
            UserOperation response = (UserOperation) ((ObjectMessage) rm).getObject();
            return OperationsConstants.UM_OPERATION_AUTHENTICATE_RESULT_SUCCESS.equals(response.getResponseData());
        } catch (JMSConnectionException e) {
            LOGGER.error("Error occurred while adding message to queue");
        } catch (JMSException e) {
            LOGGER.error("Error occurred while adding message to queue");
        } finally {
            try {
                connectionFactory.closeConnection(connection);
            } catch (JMSConnectionException e) {
                e.printStackTrace();
            }
        }

        return true;
    }

    private void addNextOperation(String correlationId, String operationType, String requestData,
            Session requestSession, MessageProducer producer, Destination responseQueue) throws JMSException {

        String tenantDomain = IdentityTenantUtil.getTenantDomain(tenantId);

        UserOperation requestOperation = new UserOperation();
        requestOperation.setCorrelationId(correlationId);
        requestOperation.setRequestData(requestData);
        requestOperation.setTenant(tenantDomain);
        requestOperation.setRequestType(operationType);

        ObjectMessage requestMessage = requestSession.createObjectMessage();
        requestMessage.setObject(requestOperation);
        requestMessage.setJMSCorrelationID(correlationId);

        requestMessage.setJMSReplyTo(responseQueue);
        producer.send(requestMessage);

    }

    //    private boolean getAuthenticationResult(String correlationId) throws UserStoreException {
    //
    //        PreparedStatement prepStmt = null;
    //        Connection dbConnection = null;
    //        ResultSet resultSet = null;
    //        try {
    //
    //            dbConnection = this.getDBConnection();
    //            prepStmt = dbConnection.prepareStatement(QueryConstants.UM_OPERATIONS_GET_BY_CORRELATION_ID);
    //            prepStmt.setString(1, correlationId);
    //            while (true) {
    //                resultSet = prepStmt.executeQuery();
    //                if (resultSet.next()) {
    //                    String state = resultSet.getString("UM_STATE");
    //                    if (OperationsConstants.UM_OPERATION_STATUS_COMPLETED.contains(state)) {
    //                        String responseData = resultSet.getString("UM_RESPONSE_DATA");
    //                        return OperationsConstants.UM_OPERATION_AUTHENTICATE_RESULT_SUCCESS.equals(responseData);
    //                    }
    //                }
    //                try {
    //                    Thread.sleep(200);
    //                } catch (InterruptedException e) {
    //                    LOGGER.error("Error occurred while wait for result.", e);
    //                }
    //            }
    //        } catch (SQLException ex) {
    //            LOGGER.error("Error while reading user authentication result for " + correlationId, ex);
    //            return false;
    //        } finally {
    //            DatabaseUtil.closeAllConnections(dbConnection, resultSet, prepStmt);
    //        }
    //
    //    }

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