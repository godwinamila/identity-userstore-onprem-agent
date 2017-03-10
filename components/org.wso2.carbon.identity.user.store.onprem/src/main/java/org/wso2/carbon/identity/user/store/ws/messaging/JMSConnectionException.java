package org.wso2.carbon.identity.user.store.ws.messaging;


public class JMSConnectionException extends Exception{

    /**
     * Creates a JMS Connector Exception.
     *
     * @param message Relevant exception message
     * @param e       Exception object, that has the details of the relevant exception
     */
    public JMSConnectionException(String message, Exception e) {
        super(message, e);
    }

    /**
     * Creates a JMS Connector Exception.
     *
     * @param message Relevant exception message
     */
    public JMSConnectionException(String message) {
        super(message);
    }

}
