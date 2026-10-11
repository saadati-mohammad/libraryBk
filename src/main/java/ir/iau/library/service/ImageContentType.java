package ir.iau.library.service;

import org.springframework.http.MediaType;

/**
 * Sniffs the media type of an uploaded image from its magic bytes.
 *
 * <p>The stored entity keeps only the raw bytes (there is no content-type column for book
 * covers or member pictures), so the download endpoints must derive the response
 * {@code Content-Type} from the payload itself. Trusting the client-supplied header would let
 * an upload advertise {@code image/png} while carrying arbitrary bytes, and would echo that
 * claim back to the browser.
 */
public final class ImageContentType {

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] GIF_MAGIC = {'G', 'I', 'F', '8'};
    private static final byte[] BMP_MAGIC = {'B', 'M'};
    private static final byte[] TIFF_LE_MAGIC = {'I', 'I', 0x2A, 0x00};
    private static final byte[] TIFF_BE_MAGIC = {'M', 'M', 0x00, 0x2A};

    private ImageContentType() {
    }

    /**
     * @return the detected media type, or {@link MediaType#APPLICATION_OCTET_STREAM} when the
     * payload is not a recognisable image (it is still returned to the caller, but the browser
     * will not treat it as a picture).
     */
    public static MediaType detect(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        if (startsWith(bytes, PNG_MAGIC)) {
            return MediaType.IMAGE_PNG;
        }
        if (startsWith(bytes, JPEG_MAGIC)) {
            return MediaType.IMAGE_JPEG;
        }
        if (startsWith(bytes, GIF_MAGIC)) {
            return MediaType.IMAGE_GIF;
        }
        if (startsWith(bytes, BMP_MAGIC)) {
            return MediaType.parseMediaType("image/bmp");
        }
        if (startsWith(bytes, TIFF_LE_MAGIC) || startsWith(bytes, TIFF_BE_MAGIC)) {
            return MediaType.parseMediaType("image/tiff");
        }
        if (isWebp(bytes)) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    /** WEBP: "RIFF" .... "WEBP". */
    private static boolean isWebp(byte[] bytes) {
        return bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
