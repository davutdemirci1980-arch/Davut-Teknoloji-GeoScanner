package com.geoscanner.app.utils;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Sends a field-find photo to the Google Gemini API (generateContent, vision-capable model)
 * for analysis. Gemini has a free usage tier via a Google AI Studio API key.
 * Requires an API key configured in Settings (stored via {@link AiSettings}).
 */
public class AiVisionClient {
    private static final String TAG = "AiVisionClient";
    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final int MAX_DIMENSION = 1280;
    private static final int CONNECT_TIMEOUT_MS = 20000;
    private static final int READ_TIMEOUT_MS = 60000;

    public interface Callback {
        void onSuccess(String analysisText);
        void onError(String message);
    }

    public static void analyze(String apiKey, String model, Bitmap photo, String promptText, Callback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                String result = performRequest(apiKey, model, photo, promptText);
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                Log.e(TAG, "AI vision analysis failed", e);
                String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                mainHandler.post(() -> callback.onError(message));
            }
        }).start();
    }

    private static String performRequest(String apiKey, String model, Bitmap photo, String promptText) throws IOException {
        String base64Image = encodeJpeg(photo);

        try {
            JSONObject inlineData = new JSONObject();
            inlineData.put("mime_type", "image/jpeg");
            inlineData.put("data", base64Image);

            JSONObject imagePart = new JSONObject();
            imagePart.put("inline_data", inlineData);

            JSONObject textPart = new JSONObject();
            textPart.put("text", promptText);

            JSONArray parts = new JSONArray();
            parts.put(textPart);
            parts.put(imagePart);

            JSONObject content = new JSONObject();
            content.put("parts", parts);

            JSONArray contents = new JSONArray();
            contents.put(content);

            JSONObject body = new JSONObject();
            body.put("contents", contents);

            return postToApi(apiKey, model, body);
        } catch (org.json.JSONException e) {
            throw new IOException("Request build error: " + e.getMessage());
        }
    }

    private static String postToApi(String apiKey, String model, JSONObject body) throws IOException {
        HttpURLConnection conn = null;
        try {
            String url = API_BASE_URL + URLEncoder.encode(model, "UTF-8")
                    + ":generateContent?key=" + URLEncoder.encode(apiKey, "UTF-8");
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("content-type", "application/json");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
            String responseBody = readStream(stream);

            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status + ": " + extractErrorMessage(responseBody));
            }

            return extractText(responseBody);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String extractText(String responseBody) throws IOException {
        try {
            JSONObject json = new JSONObject(responseBody);
            JSONArray candidates = json.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) {
                throw new IOException("Empty response from AI");
            }
            JSONObject firstCandidate = candidates.getJSONObject(0);
            JSONObject content = firstCandidate.optJSONObject("content");
            JSONArray parts = content != null ? content.optJSONArray("parts") : null;
            if (parts == null || parts.length() == 0) {
                throw new IOException("No text content in AI response");
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) {
                String text = parts.getJSONObject(i).optString("text", "");
                if (!text.isEmpty()) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(text);
                }
            }
            if (sb.length() == 0) throw new IOException("No text content in AI response");
            return sb.toString();
        } catch (org.json.JSONException e) {
            throw new IOException("Could not parse AI response: " + e.getMessage());
        }
    }

    private static String extractErrorMessage(String responseBody) {
        try {
            JSONObject json = new JSONObject(responseBody);
            JSONObject error = json.optJSONObject("error");
            if (error != null) return error.optString("message", responseBody);
        } catch (Exception ignored) {
        }
        return responseBody;
    }

    private static String readStream(InputStream stream) throws IOException {
        if (stream == null) return "";
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int len;
        while ((len = stream.read(chunk)) != -1) {
            buffer.write(chunk, 0, len);
        }
        return buffer.toString("UTF-8");
    }

    private static String encodeJpeg(Bitmap original) {
        Bitmap scaled = scaleDown(original, MAX_DIMENSION);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out);
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    private static Bitmap scaleDown(Bitmap source, int maxDimension) {
        int width = source.getWidth();
        int height = source.getHeight();
        int largest = Math.max(width, height);
        if (largest <= maxDimension) return source;
        float scale = (float) maxDimension / largest;
        int newWidth = Math.round(width * scale);
        int newHeight = Math.round(height * scale);
        return Bitmap.createScaledBitmap(source, newWidth, newHeight, true);
    }
}
