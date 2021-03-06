package com.cloudcredo.cloudfoundry.test;

import com.cloudcredo.cloudfoundry.test.annotation.CassandraCloudFoundryService;
import com.cloudcredo.cloudfoundry.test.annotation.MongoDbCloudFoundryService;
import com.cloudcredo.cloudfoundry.test.annotation.RabbitMQCloudFoundryService;
import com.cloudcredo.cloudfoundry.test.annotation.RedisCloudFoundryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple Object that delegates to an an instance of Cloud Foundry, creates the required service and set the environment
 * variable for the Java process.
 *
 * @author: chris
 * @date: 29/04/2013
 */
public class CloudFoundryServiceProvisioner {

    private static final Logger log = LoggerFactory.getLogger(CloudFoundryServiceProvisioner.class);

    /** Sets the Environment variables that the Spring Cloud Foundry module expect */
    private CloudFoundryEnvironmentAdapter cloudFoundryEnvironmentAdapter = new CloudFoundryEnvironmentAdapter();

    /** Creates the required service in Cloud Foundry and returns the generated Credentials */
    private NatsCloudFoundryServicesClient natsCloudFoundryServicesClient = new NatsCloudFoundryServicesClient();

    /**
     * Looks for the Service annotations on the test class and creates a service for each found.
     *
     * @param clazz Class to look for presence of annotations on.
     * @see com.cloudcredo.cloudfoundry.test.annotation
     */
    void createServicesForClass(Class clazz) {
        Map<CloudFoundryService, Credentials> credentials = new HashMap<CloudFoundryService, Credentials>();

        if (clazz.isAnnotationPresent(RabbitMQCloudFoundryService.class)) {
            credentials.put(CloudFoundryService.RABBITMQ, createService(CloudFoundryService.RABBITMQ));
        }

        if (clazz.isAnnotationPresent(RedisCloudFoundryService.class)) {
            credentials.put(CloudFoundryService.REDIS, createService(CloudFoundryService.REDIS));
        }

        if (clazz.isAnnotationPresent(MongoDbCloudFoundryService.class)) {
            credentials.put(CloudFoundryService.MONGODB, createService(CloudFoundryService.MONGODB));
        }

        if (clazz.isAnnotationPresent(CassandraCloudFoundryService.class)) {
            credentials.put(CloudFoundryService.CASSANDRA, createService(CloudFoundryService.CASSANDRA));
        }

        cloudFoundryEnvironmentAdapter.addVcapServices(credentials);
    }

    private Credentials createService(CloudFoundryService cloudFoundryService) {
        try {
            log.info("Creating new " + cloudFoundryService.serviceName + " Cloud Foundry Service");
            Credentials credentials = natsCloudFoundryServicesClient.getCredentialsForNewService(cloudFoundryService.serviceName + "-test",
                    cloudFoundryService);

            if (credentials == null) {
                //TODO introduce retry
                throw new RuntimeException("Unable to create Credentials for service. Cannot recover");
            } else {
                checkPortAndService(credentials);
                return credentials;
            }


        } catch (InterruptedException e) {
            throw new RuntimeException("Cannot Create " + cloudFoundryService.serviceName + " Service");
        } catch (IOException e) {
            throw new RuntimeException("Cannot Create " + cloudFoundryService.serviceName + " " +
                    "Service. Credentials were created but it is not serving on the provided host and port");
        }
    }

    private void checkPortAndService(Credentials credentials) throws IOException {
        log.info("Checking Host and Port are available");
        int sleepTime = 1000;
        for (int i = 0; i < 5; i++) {
            try {
                Socket socket = new Socket(credentials.getHost(), Integer.valueOf(credentials.getPort()));
                if (socket.isConnected()) {
                    socket.close();
                }
            } catch (Exception e) {
                log.info(String.format("Service not available. Will try again in %s seconds", sleepTime));
                try {
                    Thread.sleep(sleepTime);
                    sleepTime = sleepTime * 2;
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            }
        }
        log.info("Service now available");
    }
}
