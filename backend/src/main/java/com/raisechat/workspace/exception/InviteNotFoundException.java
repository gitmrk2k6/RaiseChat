package com.raisechat.workspace.exception;

// 招待が見つからない（不明なトークン / 別ワークスペースの inviteId / ワークスペース削除済み）→ 404
public class InviteNotFoundException extends RuntimeException {
    public InviteNotFoundException() {
        super("invite not found");
    }
}
