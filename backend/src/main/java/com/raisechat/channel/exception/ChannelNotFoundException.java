package com.raisechat.channel.exception;

public class ChannelNotFoundException extends RuntimeException {
    public ChannelNotFoundException(Long channelId) {
        super("channel not found: id=" + channelId);
    }
}
