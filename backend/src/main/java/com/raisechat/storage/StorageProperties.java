package com.raisechat.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * オブジェクトストレージ（S3 / LocalStack）の接続設定。
 * dev デフォルトは LocalStack（http://localhost:4566）を指す。
 * 本番は環境変数で endpoint / 認証情報を上書きする。
 */
@ConfigurationProperties(prefix = "app.storage.s3")
public record StorageProperties(
        String bucket,
        String region,
        String endpoint,
        String accessKey,
        String secretKey,
        // avatarUrl を組み立てる際の公開ベース URL。未指定なら endpoint/bucket から導出する。
        String publicBaseUrl
) {
    public String resolvePublicBaseUrl() {
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            return stripTrailingSlash(publicBaseUrl);
        }
        return stripTrailingSlash(endpoint) + "/" + bucket;
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
