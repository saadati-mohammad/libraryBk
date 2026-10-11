package ir.iau.library.controller;

import ir.iau.library.dto.ApiResponse;
import ir.iau.library.dto.MessageDto;
import ir.iau.library.dto.WebSocketMessage;
import ir.iau.library.service.MessageService;
import ir.iau.library.service.WebSocketService;
import ir.iau.library.websocket.WebSocketController;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.mockito.Mockito.*;

class WebSocketControllerAuthorizationTest {
    @Test
    void onlyRecipientCanMarkMessageReadOverWebSocket() {
        MessageService messages = mock(MessageService.class);
        WebSocketService sockets = mock(WebSocketService.class);
        WebSocketController controller = new WebSocketController(sockets, messages);
        when(messages.getMessageById(42L)).thenReturn(ApiResponse.success(MessageDto.builder()
                .id(42L).sender("alice").recipient("bob").build()));
        SimpMessageHeaderAccessor headers = headersFor("alice");

        controller.markMessageAsRead(WebSocketMessage.builder().originalMessageId("42").build(), headers);

        verify(messages, never()).markAsRead(42L);
        verify(sockets).sendMessageToUser(eq("alice"), argThat(message -> "error".equals(message.getMessageType())));
    }

    @Test
    void recipientCanMarkMessageReadOverWebSocket() {
        MessageService messages = mock(MessageService.class);
        WebSocketService sockets = mock(WebSocketService.class);
        WebSocketController controller = new WebSocketController(sockets, messages);
        when(messages.getMessageById(42L)).thenReturn(ApiResponse.success(MessageDto.builder()
                .id(42L).sender("alice").recipient("bob").build()));
        when(messages.markAsRead(42L)).thenReturn(ApiResponse.success("read"));

        controller.markMessageAsRead(WebSocketMessage.builder().originalMessageId("42").build(), headersFor("bob"));

        verify(messages).markAsRead(42L);
        verify(sockets).sendMessageToUser(eq("alice"), argThat(message -> "read_confirmation".equals(message.getMessageType())));
    }

    private SimpMessageHeaderAccessor headersFor(String username) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setUser(new UsernamePasswordAuthenticationToken(username, null, List.of()));
        return accessor;
    }
}
