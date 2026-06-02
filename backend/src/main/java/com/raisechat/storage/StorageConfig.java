package com.raisechat.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    public S3Client s3Client(StorageProperties props) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(props.region()));

        if (props.endpoint() != null && !props.endpoint().isBlank()) {
            // dev（LocalStack）: 明示エンドポイント＋静的キー＋パススタイル（endpoint/bucket/key）。
            builder.endpointOverride(URI.create(props.endpoint()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
                    .forcePathStyle(true);
        } else {
            // 本番（実 S3）: endpoint を上書きせず、認証は ECS タスクロール（既定の認証情報チェーン）に委ねる。
            // 静的キーを配布せず、AWS の標準エンドポイント・仮想ホスト形式を使う（infrastructure.md §10）。
            builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
        }

        return builder.build();
    }
}
