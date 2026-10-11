package ir.iau.library.controller;

import ir.iau.library.dto.ApiResponse;
import ir.iau.library.dto.ConversationCriteria;
import ir.iau.library.dto.MessageDto;
import ir.iau.library.dto.MessageUpdateRequest;
import ir.iau.library.dto.SearchCriteria;
import ir.iau.library.dto.UpdateMessageRequest;
import ir.iau.library.security.AccessControl;
import ir.iau.library.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * Chat/message REST surface.
 *
 * <p>Every operation is scoped to the authenticated principal: the caller must be the
 * sender or the recipient of a message to read, update or delete it, and only the
 * recipient may mark it read. Before this, the endpoints accepted any id with a valid
 * token, so any authenticated caller could read or rewrite another user's messages.
 */
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@Slf4j
public class MessageController {

    private final MessageService messageService;

    /**
     * Loads the message only to authorize the caller. A missing message is left to the
     * service, which owns the not-found response contract.
     */
    private MessageDto messageForAuthorization(Long id) {
        ApiResponse<MessageDto> response = messageService.getMessageById(id);
        return response != null ? response.getData() : null;
    }

    /** Editing/deleting a conversation message is limited to its authenticated sender. */
    private void requireSender(Long id, String caller) {
        MessageDto message = messageForAuthorization(id);
        if (message == null) {
            return;
        }
        AccessControl.requireSelf(caller, message.getSender(), "message " + id);
    }

    /** Only the recipient of a message may mark it read. */
    private void requireRecipient(Long id, String caller) {
        MessageDto message = messageForAuthorization(id);
        if (message == null) {
            return;
        }
        AccessControl.requireSelf(caller, message.getRecipient(), "message " + id);
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse> searchMessages(
            Principal principal,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String sender,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        // Outside the try/catch below: a denial must reach GlobalExceptionHandler (403),
        // not be swallowed into a generic 500 response.
        String caller = AccessControl.requireUsername(principal);

        try {
            log.info("Search request by {} - query: {}, sender: {}, subject: {}, priority: {}",
                    caller, query, sender, subject, priority);

            SearchCriteria criteria = new SearchCriteria();
            criteria.setUsername(caller);
            criteria.setQuery(query);
            criteria.setSender(sender);
            criteria.setSubject(subject);
            criteria.setPriority(priority);
            criteria.setPage(page);
            criteria.setSize(size);

            ApiResponse response = messageService.searchMessages(criteria);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // Log the full detail server-side; never echo internal exception text to clients.
            log.error("Error searching messages: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("خطا در جستجوی پیام‌ها"));
        }
    }

    @GetMapping("/conversation")
    public ResponseEntity<ApiResponse> getConversationMessages(
            Principal principal,
            // Optional and advisory only: the authenticated principal decides which
            // conversation is returned, so a caller can never enumerate someone else's.
            @RequestParam(required = false) String senderUsername,
            @RequestParam String recipientUsername,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size
    ) {
        String caller = AccessControl.requireUsername(principal);
        if (senderUsername != null && !senderUsername.isBlank() && !senderUsername.equals(caller)) {
            log.warn("Ignoring client-supplied senderUsername '{}'; using authenticated caller '{}'",
                    senderUsername, caller);
        }

        try {
            log.info("Getting conversation between {} and {}", caller, recipientUsername);

            ConversationCriteria criteria = new ConversationCriteria();
            criteria.setSenderUsername(caller);
            criteria.setRecipientUsername(recipientUsername);
            criteria.setPage(page);
            criteria.setSize(size);

            ApiResponse response = messageService.getConversationMessages(criteria);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting conversation: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("خطا در دریافت مکالمه"));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse> updateMessage(
            Principal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateMessageRequest body) {
        // The raw-body binding previously stored the literal JSON as the message text and
        // skipped validation entirely. Binding the typed record rejects a blank/oversized
        // message with a 400 before it reaches the service.
        String caller = AccessControl.requireUsername(principal);
        requireSender(id, caller);
        log.info("Updating message {} with new content", id);

        MessageUpdateRequest request = new MessageUpdateRequest();
        request.setId(id);
        request.setMessage(body.message());

        ApiResponse response = messageService.updateMessage(request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteMessage(Principal principal, @PathVariable Long id) {
        String caller = AccessControl.requireUsername(principal);
        requireSender(id, caller);

        try {
            log.info("Deleting message {}", id);

            ApiResponse response = messageService.deleteMessage(id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error deleting message: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("خطا در حذف پیام"));
        }
    }

    @PostMapping("/{id}/mark-read")
    public ResponseEntity<ApiResponse> markAsRead(Principal principal, @PathVariable Long id) {
        String caller = AccessControl.requireUsername(principal);
        requireRecipient(id, caller);

        try {
            log.info("Marking message {} as read", id);

            ApiResponse response = messageService.markAsRead(id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error marking message as read: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("خطا در علامت‌گذاری پیام"));
        }
    }
}
