package com.raisechat.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUserId(String userId);

    boolean existsByUserId(String userId);

    // メンション解決用: 本文から抽出したハンドル群をまとめて引く。
    List<User> findByUserIdIn(Collection<String> userIds);
}
