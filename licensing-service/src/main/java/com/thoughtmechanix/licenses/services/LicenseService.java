package com.thoughtmechanix.licenses.services;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import com.thoughtmechanix.licenses.client.OrganizationDiscoveryClient;
import com.thoughtmechanix.licenses.client.OrganizationFeignClient;
import com.thoughtmechanix.licenses.client.OrganizationRestTemplateClient;
import com.thoughtmechanix.licenses.config.ServiceConfig;
import com.thoughtmechanix.licenses.model.License;
import com.thoughtmechanix.licenses.model.Organization;
import com.thoughtmechanix.licenses.repository.LicenseRepository;
import com.thoughtmechanix.licenses.utils.UserContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
public class LicenseService {

    private static final Logger logger = LoggerFactory.getLogger(LicenseService.class);

    @Autowired
    private LicenseRepository licenseRepository;

    @Autowired
    ServiceConfig config;

    @Autowired
    OrganizationFeignClient organizationFeignClient;

    @Autowired
    OrganizationRestTemplateClient organizationRestClient;

    @Autowired
    OrganizationDiscoveryClient organizationDiscoveryClient;

    private void sleep() {
        try {
            Thread.sleep(11000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void randomlyRunLong() {
        Random rand = new Random();
        int randomNum = rand.nextInt((3 - 1) + 1) + 1;

       if (randomNum == 3) {
           System.out.println("Going to sleep!");
           sleep();
       }
    }

    private Organization getOrganization(String organizationId) {
        return organizationRestClient.getOrganization(organizationId);
    }

    private Organization retrieveOrgInfo(String organizationId, String clientType) {
        Organization organization = null;

        switch (clientType) {
            case "feign" :
                System.out.println("I am using the feign client");
                organization = organizationFeignClient.getOrganization(organizationId);
                break;
            case "rest":
                System.out.println("I am using the rest client");
                organization = getOrganization(organizationId);
                break;
            case "discovery":
                System.out.println("I am using the discovery client");
                organization = organizationDiscoveryClient.getOrganization(organizationId);
                break;
            default:
                organization = organizationRestClient.getOrganization(organizationId);
        }

        return organization;
    }

    private List<License> buildFallbackLicenseList(String organizationId) {
        List<License> fallbackList = new ArrayList<>();
        License license = new License()
                .withId("0000000-00-00000")
                .withOrganizationId(organizationId)
                .withProductName("No licensing information currently available");
        fallbackList.add(license);
        return fallbackList;
    }

    public License getLicense(String organizationId, String licenseId, String clientType) {
        License license = licenseRepository.findByOrganizationIdAndLicenseId(organizationId, licenseId);

        Organization org = retrieveOrgInfo(organizationId, clientType);
        return license
                .withOrganizationName(org.getName())
                .withContactName(org.getContactName())
                .withContactEmail(org.getContactEmail() )
                .withContactPhone(org.getContactPhone() )
                .withComment(config.getExampleProperty());
    }

    /*
     * The @HystrixCommand annotation marks the class as being managed by a Hystrix circuit breaker.
     */
    @HystrixCommand(commandProperties = {@HystrixProperty(name="execution.isolation.thread.timeoutInMilliseconds", value="3000")},
            fallbackMethod = "buildFallbackLicenseList",
            threadPoolKey = "licenseByOrgThreadPool",
            threadPoolProperties = {@HystrixProperty(name="coreSize", value="30"), @HystrixProperty(name="maxQueueSize", value="10")}
    )
    public List<License> getLicenseByOrg(String organizationId) {
        logger.debug("LicenseService.getLicenseByOrg Correlation id: {}", UserContextHolder.getContext().getCorrelationId());
        randomlyRunLong();

        return licenseRepository.findByOrganizationId(organizationId);
    }

    public void saveLicense(License license) {
        license.withId(UUID.randomUUID().toString());
        licenseRepository.save(license);
    }

    public void updateLicense(License license){
        licenseRepository.save(license);
    }

    public void deleteLicense(License license){
        licenseRepository.delete(license);
    }
}