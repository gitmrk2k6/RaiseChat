package com.raisechat.storage;

/**
 * オブジェクトストレージへのアップロード抽象。
 * 現状は S3（LocalStack）実装のみ。将来 F-10 ファイル添付でも再利用する。
 */
public interface ObjectStorage {

    /**
     * バイト列を指定キーで保存し、公開アクセス可能な URL を返す。
     *
     * @param key         オブジェクトキー（例: avatars/12/uuid.png）
     * @param data        保存するバイト列
     * @param contentType MIME タイプ（例: image/png）
     * @return 保存先の公開 URL
     */
    String upload(String key, byte[] data, String contentType);

    /**
     * 保存済みオブジェクトのキーから公開 URL を復元する。
     * DB にキーのみ保持する添付（F-10）を一覧返却する際に使う。
     *
     * @param key オブジェクトキー
     * @return 公開 URL
     */
    String resolveUrl(String key);
}
