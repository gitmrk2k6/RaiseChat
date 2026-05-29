package com.raisechat.channel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChannelInviteRepository extends JpaRepository<ChannelInvite, Long> {

    // 受諾時にトークンハッシュから招待を引く。
    Optional<ChannelInvite> findByTokenHash(String tokenHash);

    // 無効化(revoke)時に、招待 ID がそのチャンネルに属することを保証して取得する。
    // 別チャンネルの inviteId を指定された場合は空 → 404 として扱う。
    Optional<ChannelInvite> findByIdAndChannelId(Long id, Long channelId);
}
