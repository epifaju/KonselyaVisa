package com.konselyavisa.document.storage;

import com.konselyavisa.common.exception.BusinessException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class S3ObjectStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectStorage.class);

    private final S3Client s3Client;
    private final StorageProperties properties;
    private volatile boolean bucketReady;

    public S3ObjectStorage(S3Client s3Client, StorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
        this.bucketReady = !properties.isAutoCreateBucket();
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        ensureBucket();
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(properties.getBucket())
                            .key(key)
                            .contentType(contentType)
                            .contentLength((long) content.length)
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (S3Exception ex) {
            log.warn("Object storage put failed");
            throw BusinessException.badRequest("error.document.storage_unavailable");
        }
    }

    @Override
    public byte[] get(String key) {
        ensureBucket();
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                            .bucket(properties.getBucket())
                            .key(key)
                            .build())
                    .asByteArray();
        } catch (NoSuchKeyException ex) {
            throw BusinessException.notFound("error.document.not_found");
        } catch (S3Exception ex) {
            log.warn("Object storage get failed");
            throw BusinessException.badRequest("error.document.storage_unavailable");
        }
    }

    @Override
    public boolean exists(String key) {
        ensureBucket();
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .build());
            return true;
        } catch (NoSuchKeyException ex) {
            return false;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false;
            }
            log.warn("Object storage exists check failed");
            throw BusinessException.badRequest("error.document.storage_unavailable");
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .build());
        } catch (S3Exception ex) {
            log.warn("Object storage delete failed for key after failed persist");
        }
    }

    private void ensureBucket() {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            if (!properties.isAutoCreateBucket()) {
                bucketReady = true;
                return;
            }
            try {
                s3Client.headBucket(HeadBucketRequest.builder().bucket(properties.getBucket()).build());
            } catch (NoSuchBucketException ex) {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(properties.getBucket()).build());
            } catch (S3Exception ex) {
                if (!Set.of(404, 301).contains(ex.statusCode())) {
                    throw ex;
                }
                s3Client.createBucket(CreateBucketRequest.builder().bucket(properties.getBucket()).build());
            }
            bucketReady = true;
        }
    }
}
