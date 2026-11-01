package com.eduerp.modules.identity.internal.util;

import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.InvalidAvatarException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Kiểm tra định dạng, giới hạn số điểm ảnh và thu nhỏ/chuẩn hoá ảnh đại diện trước khi đẩy lên
 * MinIO — theo khuôn {@code LogoImage.normalize} của mcp-hub.
 */
public final class AvatarImageNormalizer {

    private static final int WEBP_HEADER_MIN_LENGTH = 12;

    private AvatarImageNormalizer() {
    }

    public record NormalizedAvatar(byte[] content, String fileName, String contentType) {
    }

    public static NormalizedAvatar normalize(byte[] rawBytes, String contentType) {
        validateBasicBounds(rawBytes, contentType);
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(rawBytes));
            if (source == null) {
                return normalizeWebpFallback(rawBytes, contentType);
            }
            validatePixelCount(source.getWidth(), source.getHeight());
            BufferedImage resized = resizeToFit(source, IdentityConstants.Avatar.MAX_DIMENSION);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(resized, IdentityConstants.Avatar.NORMALIZED_FORMAT, output)) {
                throw new InvalidAvatarException();
            }
            return new NormalizedAvatar(
                    output.toByteArray(),
                    IdentityConstants.Avatar.NORMALIZED_FILE_NAME,
                    IdentityConstants.Avatar.CONTENT_TYPE_PNG);
        } catch (IOException | IllegalArgumentException ex) {
            throw new InvalidAvatarException();
        }
    }

    private static void validateBasicBounds(byte[] rawBytes, String contentType) {
        if (rawBytes == null || rawBytes.length == 0 || rawBytes.length > IdentityConstants.Avatar.MAX_BYTES) {
            throw new InvalidAvatarException();
        }
        if (contentType != null && !contentType.isBlank()
                && !IdentityConstants.Avatar.ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidAvatarException();
        }
    }

    private static void validatePixelCount(int width, int height) {
        long pixels = (long) width * (long) height;
        if (width <= 0 || height <= 0 || pixels > IdentityConstants.Avatar.MAX_PIXELS) {
            throw new InvalidAvatarException();
        }
    }

    private static NormalizedAvatar normalizeWebpFallback(byte[] rawBytes, String contentType) {
        if (IdentityConstants.Avatar.CONTENT_TYPE_WEBP.equals(contentType) && isWebpHeader(rawBytes)) {
            return new NormalizedAvatar(
                    rawBytes,
                    IdentityConstants.Avatar.WEBP_FILE_NAME,
                    IdentityConstants.Avatar.CONTENT_TYPE_WEBP);
        }
        throw new InvalidAvatarException();
    }

    private static boolean isWebpHeader(byte[] bytes) {
        if (bytes.length < WEBP_HEADER_MIN_LENGTH) {
            return false;
        }
        boolean riff = bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F';
        boolean webp = bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
        return riff && webp;
    }

    private static BufferedImage resizeToFit(BufferedImage source, int maxDimension) {
        int width = source.getWidth();
        int height = source.getHeight();
        int targetWidth = width;
        int targetHeight = height;
        if (width > maxDimension || height > maxDimension) {
            double scale = Math.min((double) maxDimension / width, (double) maxDimension / height);
            targetWidth = Math.max(1, (int) Math.round(width * scale));
            targetHeight = Math.max(1, (int) Math.round(height * scale));
        }
        BufferedImage canvas = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return canvas;
    }
}
