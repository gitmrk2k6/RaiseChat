package com.raisechat.message;

import com.raisechat.auth.jwt.AuthenticatedUser;
import com.raisechat.message.dto.SendMessageRequest;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.security.Principal;

@Controller
public class MessageWebSocketController {

    private final MessageService messageService;

    public MessageWebSocketController(MessageService messageService) {
        this.messageService = messageService;
    }

    @MessageMapping("/channels/{channelId}/messages")
    public void sendMessage(
            @DestinationVariable Long channelId,
            @Payload @Validated SendMessageRequest req,
            Principal principal
    ) {
        AuthenticatedUser user =
                (AuthenticatedUser) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
        messageService.sendChannelMessage(user.id(), channelId, req);
    }

    @MessageMapping("/dm/{roomId}/messages")
    public void sendDmMessage(
            @DestinationVariable Long roomId,
            @Payload @Validated SendMessageRequest req,
            Principal principal
    ) {
        AuthenticatedUser user =
                (AuthenticatedUser) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
        messageService.sendDmMessage(user.id(), roomId, req);
    }
}
