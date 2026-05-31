package com.raisechat.message;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    // メッセージ一覧の添付を 1 クエリでまとめてロードする（メッセージごとの N+1 を避ける）。
    List<Attachment> findByMessageIdInAndDeletedAtIsNullOrderByIdAsc(Collection<Long> messageIds);

    // 単一メッセージの添付（編集後の再構築など）。
    List<Attachment> findByMessageIdAndDeletedAtIsNullOrderByIdAsc(Long messageId);
}
