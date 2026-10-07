package ir.iau.library.repository;

import ir.iau.library.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long>, JpaSpecificationExecutor<Message> {

    // پیام‌های مکالمه بین دو کاربر
    @Query("SELECT m FROM Message m WHERE " +
            "((m.sender = :senderUsername AND m.recipient = :recipientUsername) OR " +
            "(m.sender = :recipientUsername AND m.recipient = :senderUsername)) " +
            "AND m.isActive = true " +
            "ORDER BY m.createDate DESC")
    Page<Message> findConversationMessages(
            @Param("senderUsername") String senderUsername,
            @Param("recipientUsername") String recipientUsername,
            Pageable pageable
    );

    // پیام‌های ارسال شده توسط کاربر
    @Query("SELECT m FROM Message m WHERE m.sender = :username AND m.isActive = true ORDER BY m.createDate DESC")
    Page<Message> findBySenderAndIsActiveTrue(@Param("username") String username, Pageable pageable);

    // پیام‌های دریافت شده توسط کاربر
    @Query("SELECT m FROM Message m WHERE m.recipient = :username AND m.isActive = true ORDER BY m.createDate DESC")
    Page<Message> findByRecipientAndIsActiveTrue(@Param("username") String username, Pageable pageable);

    // جستجوی متنی در پیام‌ها (با فرض اینکه مشکل CLOB حل شده و ستون از نوع VARCHAR است)
    @Query("SELECT m FROM Message m WHERE " +
            "m.isActive = true AND " +
            "(:query IS NULL OR LOWER(m.message) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
            "(:sender IS NULL OR m.sender = :sender) AND " +
            "(:subject IS NULL OR m.subject = :subject) AND " +
            "(:priority IS NULL OR m.priority = :priority) AND " +
            "(:startDate IS NULL OR m.createDate >= :startDate) AND " +
            "(:endDate IS NULL OR m.createDate <= :endDate) " +
            "ORDER BY m.createDate DESC")
    Page<Message> searchMessages(
            @Param("query") String query,
            @Param("sender") String sender,
            @Param("subject") String subject,
            @Param("priority") String priority,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    // پیدا کردن پیام والد و فرزندان آن
    @Query("SELECT m FROM Message m WHERE m.parentMessage.id = :parentId AND m.isActive = true ORDER BY m.createDate ASC")
    List<Message> findRepliesByParentMessageId(@Param("parentId") Long parentId);

    // پیام‌های خوانده نشده کاربر
    @Query("SELECT COUNT(m) FROM Message m WHERE m.recipient = :username AND m.messageStatus = 'SENT' AND m.isActive = true")
    Long countUnreadMessages(@Param("username") String username);

    // پیام‌های اولویت بالا
    @Query("SELECT m FROM Message m WHERE m.priority IN ('high', 'urgent') AND m.isActive = true ORDER BY m.createDate DESC")
    Page<Message> findHighPriorityMessages(Pageable pageable);

    // شمارش پیام‌های اولویت بالا بدون بارگذاری تمام ردیف‌ها
    @Query("SELECT COUNT(m) FROM Message m WHERE m.priority IN ('high', 'urgent') AND m.isActive = true")
    long countHighPriorityMessages();

    // پیام‌های حذف شده
    @Query("SELECT m FROM Message m WHERE m.deleteDate IS NOT NULL ORDER BY m.deleteDate DESC")
    Page<Message> findDeletedMessages(Pageable pageable);

    // آمار پیام‌ها برای کاربر
    @Query("SELECT " +
            "COUNT(CASE WHEN m.sender = :username THEN 1 END) as sentCount, " +
            "COUNT(CASE WHEN m.recipient = :username THEN 1 END) as receivedCount, " +
            "COUNT(CASE WHEN m.recipient = :username AND m.messageStatus = 'SENT' THEN 1 END) as unreadCount " +
            "FROM Message m WHERE m.isActive = true AND (m.sender = :username OR m.recipient = :username)")
    Object[] getMessageStats(@Param("username") String username);

    // پیام فعال بر اساس ID
    Optional<Message> findByIdAndIsActiveTrue(Long id);

    // ** متد بهینه شده برای پیدا کردن پیام‌ها در یک بازه زمانی **
    @Query("SELECT m FROM Message m WHERE m.createDate >= :startOfDay AND m.createDate < :endOfDay AND m.isActive = true ORDER BY m.createDate DESC")
    List<Message> findMessagesBetween(
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay
    );

    // پیام‌هایی که نیاز به ارسال SMS دارند
    @Query("SELECT m FROM Message m WHERE m.enableSendSms = true AND m.messageStatus = 'SENT' ORDER BY m.createDate DESC")
    List<Message> findMessagesForSms();
}