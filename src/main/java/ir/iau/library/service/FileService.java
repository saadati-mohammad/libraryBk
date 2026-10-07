package ir.iau.library.service;

import ir.iau.library.dto.ApiResponse;
import ir.iau.library.dto.FileAttachmentDto;
import ir.iau.library.entity.FileAttachment;
import ir.iau.library.repository.FileAttachmentRepository;
import ir.iau.library.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class FileService {

    private final FileAttachmentRepository fileAttachmentRepository;
    private final MessageRepository messageRepository;
    private final MessageMapper messageMapper;

    @Value("${app.upload.dir:${user.home}/uploads}")
    private String uploadDir;

    @Value("${app.upload.max-file-size:10485760}") // 10MB
    private long maxFileSize;

    private final List<String> allowedContentTypes = List.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain", "text/csv"
    );

    /**
     * آپلود فایل جدید
     */
    @Transactional
    public ApiResponse<FileAttachmentDto> uploadFile(MultipartFile file, Long messageId) {
        try {
            log.info("Starting file upload: {}", file.getOriginalFilename());

            // اعتبارسنجی فایل
            String validationError = validateFile(file);
            if (validationError != null) {
                return ApiResponse.error(validationError);
            }

            // ایجاد دایرکتوری آپلود
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // تولید نام فایل یکتا
            String uniqueFileName = generateUniqueFileName(file.getOriginalFilename());
            Path targetLocation = uploadPath.resolve(uniqueFileName);

            // کپی فایل
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            // ایجاد رکورد در پایگاه داده
            FileAttachment.FileAttachmentBuilder fileBuilder = FileAttachment.builder()
                    .fileName(uniqueFileName)
                    .originalFileName(file.getOriginalFilename())
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .filePath(targetLocation.toString())
                    .uploadStatus("COMPLETED");

            // اتصال به پیام (اختیاری)
            if (messageId != null) {
                messageRepository.findByIdAndIsActiveTrue(messageId)
                        .ifPresent(fileBuilder::message);
            }

            FileAttachment savedFile = fileAttachmentRepository.save(fileBuilder.build());
            FileAttachmentDto fileDto = messageMapper.toDto(savedFile);

            log.info("File uploaded successfully: {} -> {}", file.getOriginalFilename(), uniqueFileName);
            return ApiResponse.success(fileDto, "File uploaded successfully");

        } catch (IOException e) {
            log.error("Error uploading file: {}", e.getMessage(), e);
            return ApiResponse.error("Failed to upload file: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error uploading file: {}", e.getMessage(), e);
            return ApiResponse.error("Unexpected error occurred while uploading file");
        }
    }

    /**
     * دانلود فایل
     */
    public Resource downloadFile(Long fileId) throws Exception {
        log.info("Downloading file with ID: {}", fileId);

        FileAttachment fileAttachment = fileAttachmentRepository.findById(fileId)
                .orElseThrow(() -> new Exception("File not found"));

        // Defense-in-depth boundary check: even though the path is server-generated, resolve it
        // against the configured upload root and refuse anything that escapes it (guards against
        // a tampered DB row or a future bug). Uses the canonical path so symlinks/`..` cannot
        // walk outside the intended storage directory.
        Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path filePath = Paths.get(fileAttachment.getFilePath()).toAbsolutePath().normalize();
        if (!filePath.startsWith(uploadRoot)) {
            log.error("Refusing to serve file {} outside the upload root", fileId);
            throw new Exception("File not found");
        }

        Resource resource = new UrlResource(filePath.toUri());

        if (resource.exists() && resource.isReadable()) {
            return resource;
        } else {
            throw new Exception("File not found or not readable");
        }
    }

    /**
     * دریافت اطلاعات فایل
     */
    public FileAttachmentDto getFileInfo(Long fileId) throws Exception {
        log.info("Getting file info for ID: {}", fileId);

        FileAttachment fileAttachment = fileAttachmentRepository.findById(fileId)
                .orElseThrow(() -> new Exception("File not found"));

        return messageMapper.toDto(fileAttachment);
    }

    /**
     * دریافت فایل‌های ضمیمه یک پیام
     */
    public ApiResponse<List<FileAttachmentDto>> getMessageFiles(Long messageId) {
        try {
            log.info("Getting files for message ID: {}", messageId);

            List<FileAttachment> files = fileAttachmentRepository.findByMessageId(messageId);
            List<FileAttachmentDto> fileDtos = files.stream()
                    .map(messageMapper::toDto)
                    .collect(Collectors.toList());

            return ApiResponse.success(fileDtos);

        } catch (Exception e) {
            log.error("Error getting message files: {}", e.getMessage(), e);
            return ApiResponse.error("Failed to retrieve message files");
        }
    }

    /**
     * حذف فایل
     */
    @Transactional
    public ApiResponse<String> deleteFile(Long fileId) {
        try {
            log.info("Deleting file with ID: {}", fileId);

            FileAttachment fileAttachment = fileAttachmentRepository.findById(fileId)
                    .orElseThrow(() -> new Exception("File not found"));

            // حذف فایل از دیسک
            Path filePath = Paths.get(fileAttachment.getFilePath());
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("File deleted from disk: {}", filePath);
            }

            // حذف رکورد از پایگاه داده
            fileAttachmentRepository.delete(fileAttachment);

            log.info("File deleted successfully with ID: {}", fileId);
            return ApiResponse.success("File deleted successfully");

        } catch (Exception e) {
            log.error("Error deleting file: {}", e.getMessage(), e);
            return ApiResponse.error("Failed to delete file: " + e.getMessage());
        }
    }

    /**
     * دریافت آمار فایل‌ها
     */
    public ApiResponse<Object> getFileStats() {
        try {
            log.info("Getting file statistics");

            List<FileAttachment> allFiles = fileAttachmentRepository.findAll();
            Long totalSize = fileAttachmentRepository.getTotalFileSize();
            List<Object[]> fileCountByType = fileAttachmentRepository.getFileCountByType();

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalFiles", allFiles.size());
            stats.put("totalSize", totalSize);
            stats.put("averageSize", allFiles.isEmpty() ? 0 : totalSize / allFiles.size());

            Map<String, Long> filesByType = new HashMap<>();
            for (Object[] result : fileCountByType) {
                filesByType.put((String) result[0], (Long) result[1]);
            }
            stats.put("filesByType", filesByType);

            return ApiResponse.success(stats);

        } catch (Exception e) {
            log.error("Error getting file stats: {}", e.getMessage(), e);
            return ApiResponse.error("Failed to retrieve file statistics");
        }
    }

    /**
     * اعتبارسنجی فایل
     */
    private String validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            return "File is empty";
        }

        if (file.getSize() > maxFileSize) {
            return "File size exceeds maximum allowed size (" + (maxFileSize / 1024 / 1024) + "MB)";
        }

        String contentType = file.getContentType();
        if (contentType == null || !allowedContentTypes.contains(contentType)) {
            return "File type not allowed. Allowed types: " + String.join(", ", allowedContentTypes);
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty()) {
            return "File name is required";
        }

        // بررسی کاراکترهای غیرمجاز در نام فایل
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return "File name contains invalid characters";
        }

        return null; // فایل معتبر است
    }

    /**
     * تولید نام فایل یکتا
     */
    /**
     * Builds a safe, unique storage name from a client-supplied original filename.
     *
     * <p>Hardened against path traversal and header injection:
     * <ul>
     *   <li>any directory component is discarded ({@code Paths.get(name).getFileName()}),</li>
     *   <li>the base name is reduced to a strict {@code [A-Za-z0-9_-]} charset,</li>
     *   <li>dot-runs are collapsed and leading dots removed, so no {@code ..} survives,</li>
     *   <li>the extension is taken from the sanitized name and reduced to a short,
     *       safe {@code [a-z0-9]} value, so no quotes/CR/LF reach the Content-Disposition header,</li>
     *   <li>an empty result falls back to {@code "file"}.</li>
     * </ul>
     * The timestamp + random suffix guarantee uniqueness regardless of the input.
     */
    private String generateUniqueFileName(String originalFilename) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);

        // Discard any path component the client may have supplied (../, C:\, etc.).
        String bare = "file";
        if (originalFilename != null && !originalFilename.isBlank()) {
            String leaf = Paths.get(originalFilename).getFileName().toString();
            if (!leaf.isBlank()) {
                bare = leaf;
            }
        }

        // Split off the extension from the sanitized leaf, then sanitize each part.
        String rawExtension = "";
        int dot = bare.lastIndexOf('.');
        if (dot >= 0 && dot < bare.length() - 1) {
            rawExtension = bare.substring(dot + 1);
            bare = bare.substring(0, dot);
        }

        String baseName = bare.replaceAll("[^A-Za-z0-9_-]", "_").replaceAll("_+", "_");
        if (baseName.isBlank()) {
            baseName = "file";
        }

        String extension = rawExtension.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
        String suffix = extension.isEmpty() ? "" : "." + extension;

        return baseName + "_" + timestamp + "_" + uuid + suffix;
    }

    /**
     * دریافت آیکون فایل بر اساس نوع
     */
    public String getFileIcon(String contentType) {
        if (contentType == null) return "📎";

        if (contentType.startsWith("image/")) return "🖼️";
        if (contentType.startsWith("video/")) return "🎥";
        if (contentType.startsWith("audio/")) return "🎵";
        if (contentType.contains("pdf")) return "📄";
        if (contentType.contains("word")) return "📝";
        if (contentType.contains("excel")) return "📊";
        if (contentType.contains("powerpoint")) return "📊";
        if (contentType.equals("text/plain")) return "📄";
        if (contentType.equals("text/csv")) return "📊";

        return "📎";
    }

    /**
     * پاک‌سازی فایل‌های موقت و آپلود نشده
     */
    @Transactional
    public void cleanupIncompleteUploads() {
        try {
            log.info("Starting cleanup of incomplete uploads");

            List<FileAttachment> incompleteFiles = fileAttachmentRepository.findIncompleteUploads();

            for (FileAttachment file : incompleteFiles) {
                // حذف فایل از دیسک اگر وجود دارد
                Path filePath = Paths.get(file.getFilePath());
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                }

                // حذف رکورد از پایگاه داده
                fileAttachmentRepository.delete(file);
            }

            log.info("Cleanup completed. Removed {} incomplete files", incompleteFiles.size());

        } catch (Exception e) {
            log.error("Error during cleanup: {}", e.getMessage(), e);
        }
    }
}