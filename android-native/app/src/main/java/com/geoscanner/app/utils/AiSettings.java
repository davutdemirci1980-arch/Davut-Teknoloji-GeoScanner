package com.geoscanner.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class AiSettings {
    private static final String PREFS_NAME = "geoscanner_prefs";
    private static final String KEY_API_KEY = "ai_api_key";
    private static final String KEY_MODEL = "ai_model";
    public static final String DEFAULT_MODEL = "claude-sonnet-5";

    public static String getApiKey(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_API_KEY, "");
    }

    public static void setApiKey(Context context, String apiKey) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_API_KEY, apiKey == null ? "" : apiKey.trim()).apply();
    }

    public static String getModel(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_MODEL, DEFAULT_MODEL);
    }

    public static void setModel(Context context, String model) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String value = (model == null || model.trim().isEmpty()) ? DEFAULT_MODEL : model.trim();
        prefs.edit().putString(KEY_MODEL, value).apply();
    }

    public static boolean hasApiKey(Context context) {
        return !getApiKey(context).isEmpty();
    }
}
