package com.laf.lostandfound.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Validates and compresses images before upload / AI analysis. */
public final class ImageValidationUtils {

    private ImageValidationUtils() {
    }

    public static class Result {
        private final boolean valid;
        private final String error;
        private final long sizeBytes;
        private final String mimeType;
        private final int width;
        private final int height;

        private Result(boolean valid, String error, long sizeBytes, String mimeType, int width, int height) {
            this.valid = valid;
            this.error = error;
            this.sizeBytes = sizeBytes;
            this.mimeType = mimeType;
            this.width = width;
            this.height = height;
        }

        public boolean isValid() {
            return valid;
        }

        public String getError() {
            return error;
        }

        public long getSizeBytes() {
            return sizeBytes;
        }

        public String getMimeType() {
            return mimeType;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }
    }

    public static Result validate(Context context, Uri uri) {
        return validate(context, uri, Constants.MAX_IMAGE_BYTES);
    }

    public static Result validate(Context context, Uri uri, long maxBytes) {
        if (context == null || uri == null) {
            return new Result(false, "Please select a valid image", 0, "", 0, 0);
        }

        String mimeType = getMimeType(context, uri);
        if (mimeType != null && !mimeType.startsWith("image/")) {
            return new Result(false, "Only image files are supported", 0, mimeType, 0, 0);
        }

        long size = getSize(context, uri);
        if (size > maxBytes) {
            long maxMb = maxBytes / (1024L * 1024L);
            return new Result(false, "Image is too large. Maximum size is " + maxMb + " MB", size, mimeType, 0, 0);
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) {
                return new Result(false, "Unable to read the selected image", 0, mimeType, 0, 0);
            }
            BitmapFactory.decodeStream(is, null, bounds);
        } catch (Exception e) {
            return new Result(false, "Unable to read the selected image", size, mimeType, 0, 0);
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return new Result(false, "The selected file is not a readable image", size, mimeType, 0, 0);
        }

        return new Result(true, "", size, mimeType, bounds.outWidth, bounds.outHeight);
    }

    /**
     * Decodes, downsizes and JPEG-compresses an image. The returned bytes are
     * ready for Firebase Storage or the AI function.
     */
    public static byte[] compress(Context context, Uri uri, int maxDimension, int quality) throws IOException {
        if (context == null || uri == null) throw new IOException("Invalid image");

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) throw new IOException("Unable to open image");
            BitmapFactory.decodeStream(is, null, bounds);
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("The selected file is not a readable image");
        }

        int sample = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / sample > maxDimension * 2) {
            sample *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;

        Bitmap bitmap;
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) throw new IOException("Unable to open image");
            bitmap = BitmapFactory.decodeStream(is, null, options);
        }
        if (bitmap == null) throw new IOException("Unable to decode image");

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width > maxDimension || height > maxDimension) {
            float ratio = Math.min((float) maxDimension / width, (float) maxDimension / height);
            int targetWidth = Math.max(1, Math.round(width * ratio));
            int targetHeight = Math.max(1, Math.round(height * ratio));
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
            if (scaled != bitmap) bitmap.recycle();
            bitmap = scaled;
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output);
        bitmap.recycle();
        return output.toByteArray();
    }

    public static String getMimeType(Context context, Uri uri) {
        String type = null;
        if (context != null && uri != null) {
            ContentResolver resolver = context.getContentResolver();
            type = resolver.getType(uri);
            if (type == null && uri.getScheme() != null && uri.getScheme().equals("file")) {
                String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
                if (extension != null) {
                    type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
                }
            }
        }
        return type == null ? "image/jpeg" : type;
    }

    public static long getSize(Context context, Uri uri) {
        if (context == null || uri == null) return 0;
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0 && !cursor.isNull(index)) {
                    return cursor.getLong(index);
                }
            }
        } catch (Exception ignored) {
            // Fall back to reading the stream length below.
        }

        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) return 0;
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = is.read(buffer)) != -1) {
                total += read;
                if (total > Constants.MAX_IMAGE_BYTES * 2L) break;
            }
            return total;
        } catch (Exception e) {
            return 0;
        }
    }
}
