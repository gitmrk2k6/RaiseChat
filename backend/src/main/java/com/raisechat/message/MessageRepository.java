package com.raisechat.message;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByChannelIdAndDeletedAtIsNullAndParentIsNullOrderByCreatedAtDesc(Long channelId, Pageable pageable);

    List<Message> findByParentIdAndDeletedAtIsNullOrderByCreatedAtAsc(Long parentId);
}
