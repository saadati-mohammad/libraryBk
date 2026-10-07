package ir.iau.library.controller;

import ir.iau.library.dto.ApiResponse;
import ir.iau.library.dto.FileAttachmentDto;
import ir.iau.library.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {

    private final FileService fileService;

    /**
     * آپلود فایل ضمیمه
     *
     * مثال درخواست:
     * POST /api/v1/files/upload
     * Content-Type: multipart/form-data
     *
     * Form Data:
     * - file: [فایل]
     * - messageId: 123 (اختیاری)
     *
     * مثال پاسخ:
     * {
     *   "success": true,
     *   "message": "File uploaded successfully",
     *   "data": {
     *     "id": 456,
     *     "fileName": "document_20240115_103000.pdf",
     *     "originalFileName": "document.pdf",
     *     "contentType": "application/pdf",
     *     "fileSize": 1024000,
     *     "uploadStatus": "COMPLETED"
     *   }
     * }
     */
    @PostMapping("/upload")
    @Operation(summary = "آپلود فایل", description = "آپلود فایل ضمیمه برای پیام")
    public ResponseEntity<ApiResponse<FileAttachmentDto>> uploadFile(
            @Parameter(description = "فایل برای آپلود", required = true)
            @RequestParam("file") MultipartFile file,

            @Parameter(description = "شناسه پیام (اختیاری)")
            @RequestParam(value = "messageId", required = false) Long messageId) {

        ApiResponse<FileAttachmentDto> response = fileService.uploadFile(file, messageId);
        return ResponseEntity.ok(response);
    }

    /**
     * دانلود فایل ضمیمه
     *
     * مثال درخواست:
     * GET /api/v1/files/download/456
     *
     * پاسخ: فایل باینری با header های مناسب
     */
    @GetMapping("/download/{fileId}")
    @Operation(summary = "دانلود فایل", description = "دانلود فایل ضمیمه بر اساس شناسه")
    public ResponseEntity<Resource> downloadFile(
            @Parameter(description = "شناسه فایل", required = true)
            @PathVariable Long fileId) {

        try {
            Resource resource = fileService.downloadFile(fileId);
            FileAttachmentDto fileInfo = fileService.getFileInfo(fileId);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(fileInfo.getContentType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + fileInfo.getOriginalFileName() + "\"")
                    .body(resource);

        } catch (Exception e) {
            log.error("Error downloading file: {}", e.getMessage(), e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * دریافت اطلاعات فایل
     *
     * مثال درخواست:
     * GET /api/v1/files/info/456
     *
     * مثال پاسخ:
     * {
     *   "success": true,
     *   "data": {
     *     "id": 456,
     *     "fileName": "document_20240115_103000.pdf",
     *     "originalFileName": "document.pdf",
     *     "contentType": "application/pdf",
     *     "fileSize": 1024000,
     *     "uploadStatus": "COMPLETED",
     *     "createDate": "2024-01-15T10:30:00"
     *   }
     * }
     */
    @GetMapping("/info/{fileId}")
    @Operation(summary = "اطلاعات فایل", description = "دریافت اطلاعات کامل فایل")
    public ResponseEntity<ApiResponse<FileAttachmentDto>> getFileInfo(
            @PathVariable Long fileId) {

        try {
            FileAttachmentDto fileInfo = fileService.getFileInfo(fileId);
            return ResponseEntity.ok(ApiResponse.success(fileInfo));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("File not found"));
        }
    }

    /**
     * دریافت فایل‌های ضمیمه یک پیام
     *
     * مثال درخواست:
     * GET /api/v1/files/message/123
     *
     * مثال پاسخ:
     * {
     *   "success": true,
     *   "data": [
     *     {
     *       "id": 456,
     *       "fileName": "document.pdf",
     *       "fileSize": 1024000
     *     },
     *     {
     *       "id": 457,
     *       "fileName": "image.jpg",
     *       "fileSize": 512000
     *     }
     *   ]
     * }
     */
    @GetMapping("/message/{messageId}")
    @Operation(summary = "فایل‌های پیام", description = "دریافت لیست فایل‌های ضمیمه یک پیام")
    public ResponseEntity<ApiResponse<List<FileAttachmentDto>>> getMessageFiles(
            @PathVariable Long messageId) {

        ApiResponse<List<FileAttachmentDto>> response = fileService.getMessageFiles(messageId);
        return ResponseEntity.ok(response);
    }

    /**
     * حذف فایل ضمیمه
     *
     * مثال درخواست:
     * DELETE /api/v1/files/delete/456
     *
     * مثال پاسخ:
     * {
     *   "success": true,
     *   "message": "File deleted successfully"
     * }
     */
    @DeleteMapping("/delete/{fileId}")
    @Operation(summary = "حذف فایل", description = "حذف فایل ضمیمه از سیستم")
    public ResponseEntity<ApiResponse<String>> deleteFile(
            @PathVariable Long fileId) {

        ApiResponse<String> response = fileService.deleteFile(fileId);
        return ResponseEntity.ok(response);
    }

    /**
     * آمار فایل‌ها
     *
     * مثال پاسخ:
     * {
     *   "success": true,
     *   "data": {
     *     "totalFiles": 150,
     *     "totalSize": 52428800,
     *     "averageSize": 349525,
     *     "filesByType": {
     *       "application/pdf": 45,
     *       "image/jpeg": 67,
     *       "image/png": 38
     *     }
     *   }
     * }
     */
    @GetMapping("/stats")
    @Operation(summary = "آمار فایل‌ها", description = "دریافت آمار کلی فایل‌های سیستم")
    public ResponseEntity<ApiResponse<Object>> getFileStats() {

        ApiResponse<Object> response = fileService.getFileStats();
        return ResponseEntity.ok(response);
    }
}