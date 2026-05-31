package com.raisechat.storage;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client s3Client;
    private final StorageProperties props;

    public S3ObjectStorage(S3Client s3Client, StorageProperties props) {
        this.s3Client = s3Client;
        this.props = props;
    }

    @Override
    public String upload(String key, byte[] data, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(props.bucket())
                .key(key)
                .contentType(contentType)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(data));

        return props.resolvePublicBaseUrl() + "/" + key;
    }
}
