package ir.iau.library.controller;

import ir.iau.library.dto.ApiResponse;
import ir.iau.library.dto.ConversationCriteria;
import ir.iau.library.dto.MessageUpdateRequest;
import ir.iau.library.dto.SearchCriteria;
import ir.iau.library.dto.UpdateMessageRequest;
import ir.iau.library.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@Slf4j
public class MessageController {

    private final MessageService messageService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse> searchMessages(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String sender,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        try {
            log.info("Search request - query: {}, sender: {}, subject: {}, priority: {}",
                    query, sender, subject, priority);

            Pageable pageable = PageRequest.of(page, size);

            SearchCriteria criteria = new SearchCriteria();
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
            @RequestParam String senderUsername,
            @RequestParam String recipientUsername,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size
    ) {
        try {
            log.info("Getting conversation between {} and {}", senderUsername, recipientUsername);

            ConversationCriteria criteria = new ConversationCriteria();
            criteria.setSenderUsername(senderUsername);
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
            @PathVariable Long id,
            @Valid @RequestBody UpdateMessageRequest body) {
        // The raw-body binding previously stored the literal JSON as the message text and
        // skipped validation entirely. Binding the typed record rejects a blank/oversized
        // message with a 400 before it reaches the service.
        log.info("Updating message {} with new content", id);

        MessageUpdateRequest request = new MessageUpdateRequest();
        request.setId(id);
        request.setMessage(body.message());

        ApiResponse response = messageService.updateMessage(request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteMessage(@PathVariable Long id) {
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
    public ResponseEntity<ApiResponse> markAsRead(@PathVariable Long id) {
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