package ir.iau.library.service;

import ir.iau.library.dto.*;
import ir.iau.library.entity.Message;
import ir.iau.library.repository.MessageRepository;
import ir.iau.library.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate; // <--- این import اضافه شده
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final MessageMapper messageMapper;

    /**
     * دریافت پیام‌های مکال-مه بین دو کاربر
     * <p>
     * ورودی: ConversationCriteria
     * - senderUsername: نام کاربری فرستنده
     * - recipientUsername: نام کاربری گیرنده
     * - page: شماره صفحه (پیش‌فرض: 0)
     * - size: تعداد آیتم در هر صفحه (پیش‌فرض: 15)
     * <p>
     * خروجی: ApiResponse<Page<MessageDto>>
     * - success: وضعیت موفقیت
     * - data: صفحه‌بندی شده از پیام‌ها
     * - hasMore: آیا صفحه بعدی وجود دارد
     * - totalElements: تعداد کل پیام‌ها
     */
    public ApiResponse<Page<MessageDto>> getConversationMessages(ConversationCriteria criteria) {
        try {
            log.info("Getting conversation messages between {} and {}", criteria.getSenderUsername(), criteria.getRecipientUsername());

            Pageable pageable = PageRequest.of(criteria.getPage(), criteria.getSize());
            Page<Message> messages = messageRepository.findConversationMessages(criteria.getSenderUsername(), criteria.getRecipientUsername(), pageable);

            Page<MessageDto> messageDtos = messages.map(messageMapper::toDto);

            return ApiResponse.<Page<MessageDto>>builder().success(true).data(messageDtos).hasMore(messageDtos.hasNext()).totalElements(messageDtos.getTotalElements()).totalPages(messageDtos.getTotalPages()).message("Messages retrieved successfully").build();

        } catch (Exception e) {
            log.error("Error getting conversation messages: {}", e.getMessage(), e);
            return ApiResponse.error("Failed to retrieve conversation messages");
        }
    }

    /**
     * ارسال پیام جدید
     * <p>
     * ورودی: MessageSendRequest
     * - sender: نام کاربری فرستنده (اجباری)
     * - senderFarsiTitle: نام فارسی فرستنده (اجباری)
     * - recipient: نام کاربری گیرنده (اجباری)
     * - recipientFarsiTitle: نام فارسی گیرنده
     * - subject: موضوع پیام
     * - message: متن پیام (اجباری)
     * - parentMessageId: ID پیام والد (برای Reply)
     * - priority: اولویت (normal, high, urgent)
     * - nationalCode: کد ملی
     * - recipients: گیرندگان اضافی
     * - enableSendSms: فعال‌سازی ارسال SMS
     * <p>
     * خروجی: ApiResponse<MessageDto>
     * - success: وضعیت موفقیت
     * - data: پیام ایجاد شده
     * - message: پیام وضعیت
     */
    @Transactional
    public ApiResponse<MessageDto> sendMessage(MessageSendRequest request) {
        try {
            log.info("Sending message from {} to {}", request.getSender(), request.getRecipient());

            // اعتبارسنجی ورودی
            if (request.getSender() == null || request.getSender().trim().isEmpty()) {
                return ApiResponse.error("Sender is required");
            }
            if (request.getRecipient() == null || request.getRecipient().trim().isEmpty()) {
                return ApiResponse.error("Recipient is required");
            }
            if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
                return ApiResponse.error("Message content is required");
            }

            Message.MessageBuilder messageBuilder = Message.builder().sender(request.getSender().trim()).senderFarsiTitle(request.getSenderFarsiTitle()).recipient(request.getRecipient().trim()).recipientFarsiTitle(request.getRecipientFarsiTitle()).subject(request.getSubject()).message(request.getMessage().trim()).priority(request.getPriority() != null ? request.getPriority() : "normal").nationalCode(request.getNationalCode()).recipients(request.getRecipients()).enableSendSms(request.getEnableSendSms() != null ? request.getEnableSendSms() : false).createUser(request.getSender()).messageStatus("SENT").isActive(true).dataState(1);

            // بررسی پیام والد برای Reply
            if (request.getParentMessageId() != null) {
                Optional<Message> parentMessage = messageRepository.findByIdAndIsActiveTrue(request.getParentMessageId());
                if (parentMessage.isPresent()) {
                    messageBuilder.parentMessage(parentMessage.get());
                } else {
                    log.warn("Parent message not found with ID: {}", request.getParentMessageId());
                }
            }

            Message message = messageBuilder.build();
            Message savedMessage = messageRepository.save(message);

            MessageDto messageDto = messageMapper.toDto(savedMessage);

            log.info("Message sent successfully with ID: {}", savedMessage.getId());
            return ApiResponse.success(messageDto, "Message sent successfully");

        } catch (Exception e) {
            log.error("Error sending message: {}", e.getMessage(), e);
            return ApiResponse.error("Failed to send message");
        }
    }

    /**
     * به‌روزرسانی پیام
     * <p>
     * ورودی: MessageUpdateRequest
     * - id: ID پیام (اجباری)
     * - message: متن جدید پیام (اجباری)
     * <p>
     * خروجی: ApiResponse<MessageDto>
     * - success: وضعیت موفقیت
     * - data: پیام به‌روزرسانی شده
     * - message: پیام وضعیت
     */
    @Transactional
    public ApiResponse<MessageDto> updateMessage(MessageUpdateRequest request) {
        try {
            log.info("Updating message with ID: {}", request.getId());

            if (request.getId() == null) {
                return ApiResponse.error("Message ID is required");
            }
            if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
                return ApiResponse.error("Message content is required");
            }

            Optional<Message> messageOptional = messageRepository.findByIdAndIsActiveTrue(request.getId());
            if (messageOptional.isEmpty()) {
                return ApiResponse.error("Message not found");
            }

            Message message = messageOptional.get();
            message.setMessage(request.getMessage().trim());
            message.setModifyDate(LocalDateTime.now());

            Message savedMessage = messageRepository.save(message);
            MessageDto messageDto = messageMapper.toDto(savedMessage);

            log.info("Message updated successfully with ID: {}", savedMessage.getId());
            return ApiResponse.success(messageDto, "Message updated successfully");

        } catch (Exception e) {
            log.error("Error updating message: {}", e.getMessage(), e);
            return ApiResponse.error("Failed to update message");
        }
    }

    /**
     * حذف پیام (نرم)
     * <p>
     * ورودی: Long messageId
     * <p>
     * خروجی: ApiResponse<String>
     * - success: وضعیت موفقیت
     * - message: پیام وضعیت
     */
    @Transactional
    public ApiResponse<String> deleteMessage(Long messageId) {
        try {
            log.info("Deleting message with ID: {}", messageId);

            if (messageId == null) {
                return ApiResponse.error("Message ID is required");
            }

            Optional<Message> messageOptional = messageRepository.findByIdAndIsActiveTrue(messageId);
            if (messageOptional.isEmpty()) {
                return ApiResponse.error("Message not found");
            }

            Message message = messageOptional.get();
            message.setIsActive(false);
            message.setDeleteDate(LocalDateTime.now());
            message.setMessage(""); // پاک کردن محتوای پیام

            messageRepository.save(message);

            log.info("Message deleted successfully with ID: {}", messageId);
            return ApiResponse.success("Message deleted successfully");

        } catch (Exception e) {
            log.error("Error deleting message: {}", e.getMessage(), e);
            return ApiResponse.error("Failed to delete message");
        }
    }

    /**
     * جستجو در پیام‌ها
     * <p>
     * ورودی: SearchCriteria
     * - query: کلمه کلیدی جستجو
     * - sender: نام کاربری فرستنده
     * - recipient: نام کاربری گیرنده
     * - subject: موضوع
     * - priority: اولویت
     * - isActive: وضعیت فعال بودن
     * - startDate: تاریخ شروع
     * - endDate: تاریخ پایان
     * - page: شماره صفحه
     * - size: تعداد آیتم در صفحه
     * <p>
     * خروجی: ApiResponse<Page<MessageDto>>
     */
    public ApiResponse<Page<MessageDto>> searchMessages(SearchCriteria criteria) {
        try {
            log.info("Searching messages with query: {}", criteria.getQuery());

            Pageable pageable = PageRequest.of(criteria.getPage(), criteria.getSize());
            Page<Message> messages = messageRepository.searchMessages(criteria.getQuery(), criteria.getSender(), criteria.getSubject(), criteria.getPriority(), criteria.getStartDate(), criteria.getEndDate(), pageable);

            Page<MessageDto> messageDtos = messages.map(messageMapper::toDto);

            return ApiResponse.<Page<MessageDto>>builder().success(true).data(messageDtos).hasMore(messageDtos.hasNext()).totalElements(messageDtos.getTotalElements()).totalPages(messageDtos.getTotalPages()).message("Search completed successfully").build();

        } catch (Exception e) {
            log.error("Error searching messages: {}", e.getMessage(), e);
            return ApiResponse.error("Failed to search messages");
        }
    }

    /**
     * دریافت پیام‌های ارسال شده کاربر
     * <p>
     * ورودی:
     * - username: نام کاربری
     * - page: شماره صفحه
     * - size: تعداد آیتم در صفحه
     * <p>
     * خروجی: ApiResponse<Page<MessageDto>>
     */
    public ApiResponse<Page<MessageDto>> getSentMessages(String username, int page, int size) {
        try {
            log.info("Getting sent messages for user: {}", username);
            Pageable pageable = PageRequest.of(page, size);
            Page<Message> messages = messageRepository.findBySenderAndIsActiveTrue(username, pageable);
            Page<MessageDto> messageDtos = messages.map(messageMapper::toDto);

            return ApiResponse.<Page<MessageDto>>builder().success(true).data(messageDtos).hasMore(messageDtos.hasNext()).totalElements(messageDtos.getTotalElements()).totalPages(messageDtos.getTotalPages()).build();
        } catch (Exception e) {
            log.error("Error getting sent messages: {}", e.getMessage(), e);
            return ApiResponse.error("Failed to retrieve sent messages");
        }
    }

    /**
     * دریافت پیام‌های دریافت شده کاربر
     */
    public ApiResponse<Page<MessageDto>> getReceivedMessages(String username, int page, int size) {
        try {
            log.info("Getting received messages for user: {}", username);
            Pageable pageable = PageRequest.of(page, size);
            Page<Message> messages = messageRepository.findByRecipientAndIsActiveTrue(username, pageable);
            Page<MessageDto> messageDtos = messages.map(messageMapper::toDto);

            return ApiResponse.<Page<MessageDto>>builder().success(true).data(messageDtos).hasMore(messageDtos.hasNext()).totalElements(messageDtos.getTotalElements()).totalPages(messageDtos.getTotalPages()).build();
        } catch (Exception e) {
            log.error("Error getting received messages: {}", e.getMessage(), e);
            return ApiResponse.error("Failed to retrieve received messages");
        }
    }

    /**
     * دریافت جزئیات یک پیام
     */
    public ApiResponse<MessageDto> getMessageById(Long messageId) {
        try {
            log.info("Getting message by ID: {}", messageId);
            Optional<Message> messageOptional = messageRepository.findByIdAndIsActiveTrue(messageId);

            if (messageOptional.isEmpty()) {
                return ApiResponse.error("Message not found");
            }

            MessageDto messageDto = messageMapper.toDto(messageOptional.get());
            return ApiResponse.success(messageDto);

        } catch (Exception e) {
            log.error("Error getting message by ID: {}", e.getMessage(), e);
            return ApiResponse.error("Failed to retrieve message");
        }
    }

    /**
     * علامت‌گذاری پیام به عنوان خوانده شده
     */
    @Transactional
    public ApiResponse<String> markAsRead(Long messageId) {
        try {
            log.info("Marking message as read: {}", messageId);
            Optional<Message> messageOptional = messageRepository.findByIdAndIsActiveTrue(messageId);

            if (messageOptional.isEmpty()) {
                return ApiResponse.error("Message not found");
            }

            Message message = messageOptional.get();
            message.setMessageStatus("READ");
            messageRepository.save(message);

            return ApiResponse.success("Message marked as read");

        } catch (Exception e) {
            log.error("Error marking message as read: {}", e.getMessage(), e);
            return ApiResponse.error("Failed to mark message as read");
        }
    }

    /**
     * دریافت آمار پیام‌ها
     */
    public ApiResponse<MessageStats> getMessageStats(String username) {
        try {
            log.info("Getting message stats for user: {}", username);

            Object[] stats = messageRepository.getMessageStats(username);
            // The aggregate query returns one row of COUNT(...) values. Guard against a
            // null/empty result (user with no messages) and against drivers that surface
            // COUNT as Integer/BigInteger rather than Long.
            Long sentCount = stats != null && stats.length > 0 ? toLong(stats[0]) : 0L;
            Long receivedCount = stats != null && stats.length > 1 ? toLong(stats[1]) : 0L;
            Long unreadCount = stats != null && stats.length > 2 ? toLong(stats[2]) : 0L;

            // --- START OF CHANGES ---

            // محاسبه شروع و پایان امروز برای ارسال به کوئری بهینه شده
            LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
            LocalDateTime endOfToday = LocalDate.now().plusDays(1).atStartOfDay();

            // استفاده از متد جدید و بهینه شده findMessagesBetween
            List<Message> todayMessages = messageRepository.findMessagesBetween(startOfToday, endOfToday);

            // --- END OF CHANGES ---

            // Use a bounded page instead of Integer.MAX_VALUE, which forces the DB to
            // materialize every high-priority row just to count them.
            long highPriorityCount = messageRepository.countHighPriorityMessages();

            MessageStats messageStats = MessageStats.builder()
                    .sentCount(sentCount)
                    .receivedCount(receivedCount)
                    .unreadCount(unreadCount)
                    .totalCount(sentCount + receivedCount)
                    .todayCount((long) todayMessages.size())
                    .highPriorityCount(highPriorityCount)
                    .build();

            return ApiResponse.success(messageStats);

        } catch (Exception e) {
            log.error("Error getting message stats: {}", e.getMessage(), e);
            return ApiResponse.error("Failed to retrieve message statistics");
        }
    }

    /**
     * Safely coerces a numeric aggregate result (Long/Integer/BigInteger/Number) to Long.
     */
    private static Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}