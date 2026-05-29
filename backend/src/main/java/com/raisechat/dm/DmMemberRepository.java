package com.raisechat.dm;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DmMemberRepository extends JpaRepository<DmMember, Long> {

    boolean existsByDmRoomIdAndUserId(Long dmRoomId, Long userId);
}
