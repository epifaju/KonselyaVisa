package com.konselyavisa.document.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konselyavisa.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class S3ObjectStorageTest {

    @Mock
    private S3Client s3Client;

    private StorageProperties properties;
    private S3ObjectStorage storage;

    @BeforeEach
    void setUp() {
        properties = new StorageProperties();
        properties.setBucket("docs");
        properties.setAutoCreateBucket(false);
        storage = new S3ObjectStorage(s3Client, properties);
    }

    @Test
    void putUploadsWithoutCreatingBucketWhenAutoCreateDisabled() {
        storage.put("k1", new byte[] {1, 2}, "image/png");

        verify(s3Client, never()).headBucket(any(HeadBucketRequest.class));
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void getThrowsNotFoundWhenKeyMissing() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("missing").build());

        assertThatThrownBy(() -> storage.get("missing"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.document.not_found");
    }

    @Test
    void getReturnsBytesWhenPresent() {
        @SuppressWarnings("unchecked")
        ResponseBytes<GetObjectResponse> responseBytes = org.mockito.Mockito.mock(ResponseBytes.class);
        when(responseBytes.asByteArray()).thenReturn(new byte[] {9});
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

        assertThat(storage.get("k")).containsExactly((byte) 9);
    }

    @Test
    void existsReturnsFalseOnNoSuchKey() {
        doThrow(NoSuchKeyException.builder().message("missing").build())
                .when(s3Client)
                .headObject(any(HeadObjectRequest.class));

        assertThat(storage.exists("missing")).isFalse();
    }

    @Test
    void existsReturnsTrueWhenHeadSucceeds() {
        assertThat(storage.exists("present")).isTrue();
        verify(s3Client).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void ensureBucketCreatesBucketWhenAutoCreateAndHeadFails() {
        properties.setAutoCreateBucket(true);
        storage = new S3ObjectStorage(s3Client, properties);
        doThrow(NoSuchBucketException.builder().message("no bucket").build())
                .when(s3Client)
                .headBucket(any(HeadBucketRequest.class));

        storage.put("k", new byte[] {1}, "text/plain");

        verify(s3Client).createBucket(any(CreateBucketRequest.class));
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void deleteSwallowsS3Exception() {
        doThrow(S3Exception.builder().message("fail").statusCode(500).build())
                .when(s3Client)
                .deleteObject(any(DeleteObjectRequest.class));

        storage.delete("k");

        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void putMarksStorageUnavailableOnS3Error() {
        doThrow(S3Exception.builder().message("fail").statusCode(500).build())
                .when(s3Client)
                .putObject(any(PutObjectRequest.class), any(RequestBody.class));

        assertThatThrownBy(() -> storage.put("k", new byte[] {1}, "text/plain"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.document.storage_unavailable");
    }
}
