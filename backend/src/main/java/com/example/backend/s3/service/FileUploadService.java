package com.example.backend.s3.service;

import com.example.backend.s3.config.S3Configuration;
import java.net.URI;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class FileUploadService {

  private final S3Client s3Client;
  private final S3Configuration s3Configuration;
  final String credentialsProviderType;

  public FileUploadService(S3Configuration s3Configuration) {
    this.s3Configuration = s3Configuration;

    boolean hasStaticCredentials = s3Configuration.getAccessKey() != null
        && !s3Configuration.getAccessKey().isBlank();

    S3ClientBuilder builder = S3Client.builder()
        .region(Region.of(s3Configuration.getRegion()));

    if (hasStaticCredentials) {
      builder.credentialsProvider(StaticCredentialsProvider.create(
          AwsBasicCredentials.create(s3Configuration.getAccessKey(), s3Configuration.getSecretKey())
      ));
      if (s3Configuration.getBaseUrl() != null && !s3Configuration.getBaseUrl().isBlank()) {
        builder.endpointOverride(URI.create(s3Configuration.getBaseUrl()));
      }
      builder.forcePathStyle(true);
      this.credentialsProviderType = "static";
    } else {
      builder.credentialsProvider(DefaultCredentialsProvider.create());
      this.credentialsProviderType = "iam-role";
    }

    this.s3Client = builder.build();
  }

  public String uploadFile(String filePath, byte[] file) {
    PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
        .bucket(s3Configuration.getBucketName())
        .storageClass(s3Configuration.getStorageClass())
        .key(filePath);

    if ("static".equals(credentialsProviderType)) {
      requestBuilder.acl(ObjectCannedACL.PUBLIC_READ);
    }

    s3Client.putObject(requestBuilder.build(), RequestBody.fromBytes(file));

    try {
      GetUrlRequest getUrlRequest = GetUrlRequest.builder()
          .bucket(s3Configuration.getBucketName())
          .key(filePath)
          .build();
      return s3Client.utilities().getUrl(getUrlRequest).toURI().toString();
    } catch (Exception e) {
      throw new RuntimeException("Failed to get URL of uploaded file", e);
    }
  }
}
