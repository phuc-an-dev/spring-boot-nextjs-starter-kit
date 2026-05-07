package com.example.backend.s3.service;

import com.example.backend.s3.config.S3Configuration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileUploadServiceTest {

    @Test
    void whenAccessKeyBlank_usesIamRole() {
        S3Configuration config = new S3Configuration();
        config.setBucketName("test-bucket");
        config.setRegion("ap-southeast-1");
        config.setAccessKey("");
        config.setSecretKey("");
        config.setBaseUrl("");
        config.setStorageClass("STANDARD");

        FileUploadService service = new FileUploadService(config);

        assertThat(service.credentialsProviderType).isEqualTo("iam-role");
    }

    @Test
    void whenAccessKeyPresent_usesStaticCredentials() {
        S3Configuration config = new S3Configuration();
        config.setBucketName("test-bucket");
        config.setRegion("ap-southeast-1");
        config.setAccessKey("mykey");
        config.setSecretKey("mysecret");
        config.setBaseUrl("http://s3.tebi.io");
        config.setStorageClass("STANDARD");

        FileUploadService service = new FileUploadService(config);

        assertThat(service.credentialsProviderType).isEqualTo("static");
    }
}
