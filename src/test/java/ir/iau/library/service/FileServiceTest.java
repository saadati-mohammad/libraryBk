package ir.iau.library.service;

import ir.iau.library.dto.ApiResponse;
import ir.iau.library.dto.FileAttachmentDto;
import ir.iau.library.entity.FileAttachment;
import ir.iau.library.repository.FileAttachmentRepository;
import ir.iau.library.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Security-focused coverage for FileService: client filename sanitization on upload,
 * and the upload-root boundary check on delete (a tampered DB row must not let the
 * service delete an arbitrary file outside the configured upload directory).
 */
@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private FileAttachmentRepository fileAttachmentRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private MessageMapper messageMapper;

    private FileService fileService;

    @TempDir
    Path uploadRoot;

    @BeforeEach
    void setUp() {
        fileService = new FileService(fileAttachmentRepository, messageRepository, messageMapper);
        ReflectionTestUtils.setField(fileService, "uploadDir", uploadRoot.toString());
        ReflectionTestUtils.setField(fileService, "maxFileSize", 10_485_760L);
        lenient().when(messageMapper.toDto(any(FileAttachment.class))).thenReturn(new FileAttachmentDto());
    }

    @Test
    void uploadRejectsTraversalFilename() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../etc/evil.png", "image/png", "data".getBytes());

        ApiResponse<FileAttachmentDto> response = fileService.uploadFile(file, null);

        // validateFile rejects separators / ".." outright — no file is written, no record saved.
        assertThat(response.isSuccess()).isFalse();
        verify(fileAttachmentRepository, never()).save(any(FileAttachment.class));
    }

    @Test
    void uploadStoresValidFileInsideUploadRoot() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.png", "image/png", "data".getBytes());

        ApiResponse<FileAttachmentDto> response = fileService.uploadFile(file, null);

        assertThat(response.isSuccess()).isTrue();
        FileAttachment saved = captureSaved();
        assertThat(saved.getFileName()).doesNotContain("/", "\\", "..");
        assertThat(Path.of(uploadRoot.toString(), saved.getFileName()).normalize())
                .startsWith(uploadRoot.toAbsolutePath().normalize());
    }

    @Test
    void deleteRefusesPathOutsideUploadRoot() {
        Path outside = uploadRoot.getParent().resolve("must-not-be-deleted.txt");
        FileAttachment tampered = FileAttachment.builder()
                .id(1L)
                .filePath(outside.toString())
                .build();
        when(fileAttachmentRepository.findById(1L)).thenReturn(Optional.of(tampered));

        ApiResponse<String> response = fileService.deleteFile(1L);

        assertThat(response.isSuccess()).isFalse();
        // The DB record must NOT be deleted when the disk path is rejected.
        verify(fileAttachmentRepository, never()).delete(any(FileAttachment.class));
    }

    @Test
    void deleteRemovesFileInsideUploadRoot() throws Exception {
        Path real = uploadRoot.resolve("inside.txt");
        Files.writeString(real, "hello");
        FileAttachment attachment = FileAttachment.builder()
                .id(2L)
                .filePath(real.toString())
                .build();
        when(fileAttachmentRepository.findById(2L)).thenReturn(Optional.of(attachment));

        ApiResponse<String> response = fileService.deleteFile(2L);

        assertThat(response.isSuccess()).isTrue();
        assertThat(Files.exists(real)).isFalse();
        verify(fileAttachmentRepository).delete(attachment);
    }

    private FileAttachment captureSaved() {
        ArgumentCaptor<FileAttachment> captor = ArgumentCaptor.forClass(FileAttachment.class);
        verify(fileAttachmentRepository).save(captor.capture());
        return captor.getValue();
    }
}
