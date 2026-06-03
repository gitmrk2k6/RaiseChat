package com.raisechat.channel.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * チャンネルへメンバーを直接追加するリクエスト（InviteUserModal）。
 * userIds は同ワークスペースのメンバー ID。既にメンバーの場合は冪等にスキップされる。
 */
public record AddChannelMembersRequest(
        @NotEmpty(message = "userIds は 1 件以上必須") List<Long> userIds
) {
}
