package ir.iau.library.service;

import ir.iau.library.dto.FileAttachmentDto;
import ir.iau.library.dto.MessageDto;
import ir.iau.library.entity.FileAttachment;
import ir.iau.library.entity.Message;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class MessageMapper {

    public MessageDto toDto(Message message) {
        if (message == null) {
            return null;
        }

        MessageDto.MessageDtoBuilder builder = MessageDto.builder()
                .id(message.getId())
                .sender(message.getSender())
                .senderFarsiTitle(message.getSenderFarsiTitle())
                .recipient(message.getRecipient())
                .recipientFarsiTitle(message.getRecipientFarsiTitle())
                .subject(message.getSubject())
                .message(message.getMessage())
                .isActive(message.getIsActive())
                .parentMessageId(message.getParentMessage() != null ? message.getParentMessage().getId() : null)
                .dataState(message.getDataState())
                .createUser(message.getCreateUser())
                .createDate(message.getCreateDate())
                .modifyUser(message.getModifyUser())
                .modifyDate(message.getModifyDate())
                .deleteUser(message.getDeleteUser())
                .deleteDate(message.getDeleteDate())
                .enableSendSms(message.getEnableSendSms())
                .priority(message.getPriority())
                .nationalCode(message.getNationalCode())
                .recipients(message.getRecipients())
                .messageStatus(message.getMessageStatus());

        // تبدیل پیام والد (برای جلوگیری از حلقه بی‌نهایت، فقط اطلاعات اصلی)
        if (message.getParentMessage() != null) {
            MessageDto parentDto = MessageDto.builder()
                    .id(message.getParentMessage().getId())
                    .sender(message.getParentMessage().getSender())
                    .senderFarsiTitle(message.getParentMessage().getSenderFarsiTitle())
                    .message(message.getParentMessage().getMessage())
                    .createDate(message.getParentMessage().getCreateDate())
                    .build();
            builder.parentMessage(parentDto);
        }

        // تنظیم roomId بر اساس sender و recipient
        MessageDto dto = builder.build();
        if (dto.getSender() != null && dto.getRecipient() != null) {
            dto.setRoomId(dto.getRoomId()); // این باعث تولید خودکار roomId می‌شود
        }

        return dto;
    }

    public Message toEntity(MessageDto messageDto) {
        if (messageDto == null) {
            return null;
        }

        Message.MessageBuilder builder = Message.builder()
                .id(messageDto.getId())
                .sender(messageDto.getSender())
                .senderFarsiTitle(messageDto.getSenderFarsiTitle())
                .recipient(messageDto.getRecipient())
                .recipientFarsiTitle(messageDto.getRecipientFarsiTitle())
                .subject(messageDto.getSubject())
                .message(messageDto.getMessage())
                .isActive(messageDto.getIsActive())
                .dataState(messageDto.getDataState())
                .createUser(messageDto.getCreateUser())
                .createDate(messageDto.getCreateDate())
                .modifyUser(messageDto.getModifyUser())
                .modifyDate(messageDto.getModifyDate())
                .deleteUser(messageDto.getDeleteUser())
                .deleteDate(messageDto.getDeleteDate())
                .enableSendSms(messageDto.getEnableSendSms())
                .priority(messageDto.getPriority())
                .nationalCode(messageDto.getNationalCode())
                .recipients(messageDto.getRecipients())
                .messageStatus(messageDto.getMessageStatus());

        return builder.build();
    }

    public FileAttachmentDto toDto(FileAttachment fileAttachment) {
        if (fileAttachment == null) {
            return null;
        }

        return FileAttachmentDto.builder()
                .id(fileAttachment.getId())
                .fileName(fileAttachment.getFileName())
                .originalFileName(fileAttachment.getOriginalFileName())
                .contentType(fileAttachment.getContentType())
                .fileSize(fileAttachment.getFileSize())
                .uploadStatus(fileAttachment.getUploadStatus())
                .createDate(fileAttachment.getCreateDate())
                .build();
    }

    public FileAttachment toEntity(FileAttachmentDto fileAttachmentDto) {
        if (fileAttachmentDto == null) {
            return null;
        }

        return FileAttachment.builder()
                .id(fileAttachmentDto.getId())
                .fileName(fileAttachmentDto.getFileName())
                .originalFileName(fileAttachmentDto.getOriginalFileName())
                .contentType(fileAttachmentDto.getContentType())
                .fileSize(fileAttachmentDto.getFileSize())
                .uploadStatus(fileAttachmentDto.getUploadStatus())
                .createDate(fileAttachmentDto.getCreateDate())
                .build();
    }

    public List<MessageDto> toDtoList(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        return messages.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<FileAttachmentDto> toFileAttachmentDtoList(List<FileAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return Collections.emptyList();
        }

        return attachments.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * تبدیل MessageDto به WebSocketMessage
     */
    public ir.iau.library.dto.WebSocketMessage toWebSocketMessage(MessageDto messageDto) {
        if (messageDto == null) {
            return null;
        }

        return ir.iau.library.dto.WebSocketMessage.builder()
                .id(messageDto.getId() != null ? messageDto.getId().toString() : null)
                .sender(messageDto.getSender())
                .senderFarsiTitle(messageDto.getSenderFarsiTitle())
                .recipient(messageDto.getRecipient())
                .recipientFarsiTitle(messageDto.getRecipientFarsiTitle())
                .subject(messageDto.getSubject())
                .message(messageDto.getMessage())
                .messageType("chat")
                .parentMessageId(messageDto.getParentMessageId() != null ?
                        messageDto.getParentMessageId().toString() : null)
                .priority(messageDto.getPriority())
                .nationalCode(messageDto.getNationalCode())
                .recipients(messageDto.getRecipients())
                .enableSendSms(messageDto.getEnableSendSms())
                .timestamp(messageDto.getCreateDate() != null ?
                        // Epoch millis of the instant, NOT the zone offset: the old code
                        // sent getOffset(...).getTotalSeconds() * 1000L (3.5h in Tehran),
                        // which every client rendered as a 1970 timestamp.
                        messageDto.getCreateDate()
                                .atZone(java.time.ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli() :
                        System.currentTimeMillis())
                .roomId(messageDto.getRoomId())
                .build();
    }

    /**
     * تبدیل WebSocketMessage به MessageDto
     */
    public MessageDto fromWebSocketMessage(ir.iau.library.dto.WebSocketMessage wsMessage) {
        if (wsMessage == null) {
            return null;
        }

        return MessageDto.builder()
                .id(wsMessage.getId() != null ? Long.parseLong(wsMessage.getId()) : null)
                .sender(wsMessage.getSender())
                .senderFarsiTitle(wsMessage.getSenderFarsiTitle())
                .recipient(wsMessage.getRecipient())
                .recipientFarsiTitle(wsMessage.getRecipientFarsiTitle())
                .subject(wsMessage.getSubject())
                .message(wsMessage.getMessage())
                .parentMessageId(wsMessage.getParentMessageId() != null ?
                        Long.parseLong(wsMessage.getParentMessageId()) : null)
                .priority(wsMessage.getPriority())
                .nationalCode(wsMessage.getNationalCode())
                .recipients(wsMessage.getRecipients())
                .enableSendSms(wsMessage.getEnableSendSms())
                .createDate(wsMessage.getTimestamp() != null ?
                        java.time.LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(wsMessage.getTimestamp()),
                                java.time.ZoneId.systemDefault()) :
                        java.time.LocalDateTime.now())
                .roomId(wsMessage.getRoomId())
                .messageStatus("SENT")
                .isActive(true)
                .dataState(1)
                .build();
    }
}