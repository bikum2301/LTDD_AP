// Tạo file mới, ví dụ: src/main/java/com/example/streamapp/network/InputStreamRequestBody.java
package com.example.streamapp.network; // Hoặc package utils của bạn

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.OpenableColumns; // Import nếu cần lấy content length
import android.database.Cursor; // Import nếu cần lấy content length

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;

import java.io.IOException;
import java.io.InputStream;

public class InputStreamRequestBody extends RequestBody {
    private final MediaType contentType;
    private final ContentResolver contentResolver;
    private final Uri uri;
    private final long contentLength; // Thêm contentLength

    public InputStreamRequestBody(MediaType contentType, ContentResolver contentResolver, Uri uri) {
        this.contentType = contentType;
        this.contentResolver = contentResolver;
        this.uri = uri;
        this.contentLength = getContentLength(contentResolver, uri); // Lấy content length
    }

    @Override
    public MediaType contentType() {
        return contentType;
    }

    @Override
    public long contentLength() {
        // Nếu bạn không thể xác định chính xác content length, bạn có thể trả về -1.
        // Tuy nhiên, nhiều server yêu cầu Content-Length, đặc biệt là cho multipart uploads.
        // Với ContentResolver và URI, chúng ta thường có thể lấy được.
        return contentLength;
    }

    @Override
    public void writeTo(BufferedSink sink) throws IOException {
        Source source = null;
        InputStream inputStream = null;
        try {
            inputStream = contentResolver.openInputStream(uri);
            if (inputStream == null) {
                throw new IOException("Unable to open InputStream from URI: " + uri);
            }
            source = Okio.source(inputStream);
            sink.writeAll(source);
        } finally {
            if (source != null) {
                try {
                    source.close();
                } catch (IOException e) {
                    // Log error
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    // Log error
                }
            }
        }
    }

    // Helper để lấy content length từ URI
    private long getContentLength(ContentResolver contentResolver, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = contentResolver.query(uri, new String[]{OpenableColumns.SIZE}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                    return cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception e) {
            // Log error
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return -1; // Trả về -1 nếu không lấy được
    }
}