package com.jeancalcul.assistant;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String PREFS = "hermes_config";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_TOKEN = "token";
    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:8765";
    private static final String NOTIFICATION_CHANNEL = "hermes_tools";
    private static final int REQUEST_POST_NOTIFICATIONS = 42;

    private EditText baseUrlInput;
    private EditText tokenInput;
    private EditText messageInput;
    private TextView statusView;
    private TextView conversationView;
    private SharedPreferences preferences;
    private String lastTorchCameraId;
    private boolean torchEnabled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        createNotificationChannel();
        setContentView(buildUi());
    }

    private View buildUi() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 48, 40, 48);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(238, 244, 247), Color.rgb(198, 235, 229), Color.rgb(185, 216, 246)});
        root.setBackground(background);
        scrollView.addView(root);

        TextView title = text("Jean Calcul Assistant", 28, Color.rgb(20, 35, 45), true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView subtitle = text("Client Hermes avec outils Android MVP", 16, Color.rgb(55, 75, 85), false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 16, 0, 28);
        root.addView(subtitle, matchWrap());

        baseUrlInput = input("URL Hermes", preferences.getString(KEY_BASE_URL, DEFAULT_BASE_URL), false);
        tokenInput = input("Token optionnel", preferences.getString(KEY_TOKEN, ""), false);
        messageInput = input("Message à envoyer à Hermes", "Ouvre les paramètres", true);
        root.addView(label("Configuration Hermes"));
        root.addView(baseUrlInput, matchWrap());
        root.addView(tokenInput, matchWrap());

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        Button saveButton = button("Enregistrer");
        saveButton.setOnClickListener(v -> saveConfig());
        Button healthButton = button("Tester");
        healthButton.setOnClickListener(v -> runAsync(this::healthCheck));
        buttons.addView(saveButton, weightWrap());
        buttons.addView(healthButton, weightWrap());
        root.addView(buttons, matchWrap());

        root.addView(label("Conversation"));
        root.addView(messageInput, matchWrap());
        Button sendButton = button("Envoyer à Hermes");
        sendButton.setOnClickListener(v -> runAsync(this::sendMessage));
        root.addView(sendButton, matchWrap());

        statusView = text("Prêt", 14, Color.rgb(35, 80, 70), false);
        statusView.setPadding(0, 24, 0, 12);
        conversationView = text("Les capacités exposées : get_volume, set_volume, open_app, toggle_flashlight, send_notification.", 15, Color.rgb(30, 45, 55), false);
        root.addView(statusView, matchWrap());
        root.addView(conversationView, matchWrap());
        return scrollView;
    }

    private void saveConfig() {
        preferences.edit()
                .putString(KEY_BASE_URL, baseUrlInput.getText().toString().trim())
                .putString(KEY_TOKEN, tokenInput.getText().toString().trim())
                .apply();
        Toast.makeText(this, "Configuration enregistrée", Toast.LENGTH_SHORT).show();
    }

    private void healthCheck() throws Exception {
        saveConfig();
        JSONObject response = request("GET", "/api/mobile/health", null);
        setStatus("Hermes disponible : " + response.optString("name", "Hermes") + " " + response.optString("version", ""));
    }

    private void sendMessage() throws Exception {
        saveConfig();
        String requestId = "req_" + UUID.randomUUID();
        JSONObject body = new JSONObject()
                .put("requestId", requestId)
                .put("input", new JSONObject().put("mode", "text").put("text", messageInput.getText().toString()).put("locale", Locale.getDefault().toLanguageTag()))
                .put("client", new JSONObject().put("platform", "android").put("appVersion", "0.1.0"))
                .put("capabilities", capabilities());
        JSONObject response = request("POST", "/api/mobile/request", body);
        appendConversation("Hermes: " + response.optString("message", ""));
        JSONArray actions = response.optJSONArray("actions");
        if (actions == null || actions.length() == 0) {
            setStatus("Réponse reçue sans action.");
            return;
        }
        for (int i = 0; i < actions.length(); i++) {
            JSONObject action = actions.getJSONObject(i);
            JSONObject result = executeAction(requestId, action);
            JSONObject followUp = request("POST", "/api/mobile/tool-result", result);
            appendConversation("Outil " + action.optString("tool") + ": " + result.optString("status") + " — " + followUp.optString("message", "résultat envoyé"));
        }
        setStatus("Actions traitées : " + actions.length());
    }

    private JSONArray capabilities() throws JSONException {
        return new JSONArray()
                .put(capability("get_volume"))
                .put(capability("set_volume"))
                .put(capability("open_app"))
                .put(capability("toggle_flashlight"))
                .put(capability("send_notification"));
    }

    private JSONObject capability(String name) throws JSONException {
        return new JSONObject().put("name", name).put("risk", "low");
    }

    private JSONObject executeAction(String requestId, JSONObject action) throws JSONException {
        String actionId = action.optString("actionId", "act_" + UUID.randomUUID());
        String tool = action.optString("tool");
        JSONObject args = action.optJSONObject("arguments");
        if (args == null) args = new JSONObject();
        try {
            JSONObject payload;
            switch (tool) {
                case "get_volume": payload = getVolume(args); break;
                case "set_volume": payload = setVolume(args); break;
                case "open_app": payload = openApp(args); break;
                case "toggle_flashlight": payload = toggleFlashlight(args); break;
                case "send_notification": payload = sendNotification(args); break;
                default: return toolResult(requestId, actionId, tool, "error", null, "Outil inconnu: " + tool);
            }
            return toolResult(requestId, actionId, tool, "success", payload, null);
        } catch (Exception error) {
            return toolResult(requestId, actionId, tool, "error", null, error.getMessage());
        }
    }

    private JSONObject getVolume(JSONObject args) throws JSONException {
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        int stream = streamType(args.optString("stream", "music"));
        int current = audio.getStreamVolume(stream);
        int max = audio.getStreamMaxVolume(stream);
        return new JSONObject().put("level", Math.round(current * 100f / max)).put("raw", current).put("max", max);
    }

    private JSONObject setVolume(JSONObject args) throws JSONException {
        int level = args.optInt("level", -1);
        if (level < 0 || level > 100) throw new IllegalArgumentException("level doit être entre 0 et 100");
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        int stream = streamType(args.optString("stream", "music"));
        int max = audio.getStreamMaxVolume(stream);
        int raw = Math.round(level * max / 100f);
        audio.setStreamVolume(stream, raw, AudioManager.FLAG_SHOW_UI);
        return new JSONObject().put("level", level).put("raw", raw);
    }

    private int streamType(String stream) {
        if ("alarm".equals(stream)) return AudioManager.STREAM_ALARM;
        if ("ring".equals(stream)) return AudioManager.STREAM_RING;
        if ("notification".equals(stream)) return AudioManager.STREAM_NOTIFICATION;
        return AudioManager.STREAM_MUSIC;
    }

    private JSONObject openApp(JSONObject args) throws JSONException {
        String packageName = args.optString("package", "").trim();
        if (packageName.isEmpty()) throw new IllegalArgumentException("package est obligatoire");
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent == null) throw new IllegalArgumentException("Application introuvable: " + packageName);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launchIntent);
        return new JSONObject().put("opened", true).put("package", packageName);
    }

    private JSONObject toggleFlashlight(JSONObject args) throws CameraAccessException, JSONException {
        CameraManager camera = (CameraManager) getSystemService(CAMERA_SERVICE);
        if (lastTorchCameraId == null) lastTorchCameraId = camera.getCameraIdList()[0];
        torchEnabled = args.has("enabled") ? args.optBoolean("enabled") : !torchEnabled;
        camera.setTorchMode(lastTorchCameraId, torchEnabled);
        return new JSONObject().put("enabled", torchEnabled);
    }

    private JSONObject sendNotification(JSONObject args) throws JSONException {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_POST_NOTIFICATIONS);
            throw new IllegalStateException("Permission POST_NOTIFICATIONS demandée à l'utilisateur");
        }
        String title = args.optString("title", "Hermes");
        String body = args.optString("body", args.optString("message", "Notification Hermes"));
        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new android.app.Notification.Builder(this, NOTIFICATION_CHANNEL)
                : new android.app.Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body).setAutoCancel(true);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify((int) System.currentTimeMillis(), builder.build());
        return new JSONObject().put("sent", true).put("title", title);
    }

    private JSONObject toolResult(String requestId, String actionId, String tool, String status, JSONObject result, String error) throws JSONException {
        JSONObject body = new JSONObject().put("requestId", requestId).put("actionId", actionId).put("tool", tool).put("status", status);
        if (result != null) body.put("result", result);
        if (error != null) body.put("error", error);
        return body;
    }

    private JSONObject request(String method, String path, JSONObject body) throws IOException, JSONException {
        String base = preferences.getString(KEY_BASE_URL, DEFAULT_BASE_URL).replaceAll("/+$", "");
        HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Accept", "application/json");
        String token = preferences.getString(KEY_TOKEN, "");
        if (!token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8))) {
                writer.write(body.toString());
            }
        }
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String text = readAll(stream);
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code + ": " + text);
        return text.isEmpty() ? new JSONObject() : new JSONObject(text);
    }

    private String readAll(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        }
        return builder.toString();
    }

    private void runAsync(ThrowingRunnable runnable) {
        setStatus("Traitement en cours…");
        new Thread(() -> {
            try { runnable.run(); }
            catch (Exception error) { setStatus("Erreur: " + error.getMessage()); }
        }).start();
    }

    private void setStatus(String text) { runOnUiThread(() -> statusView.setText(text)); }
    private void appendConversation(String text) { runOnUiThread(() -> conversationView.append("\n\n" + text)); }

    private TextView label(String text) {
        TextView label = text(text, 16, Color.rgb(20, 35, 45), true);
        label.setPadding(0, 20, 0, 8);
        return label;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private EditText input(String hint, String value, boolean multiLine) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setText(value);
        editText.setSingleLine(!multiLine);
        editText.setMinLines(multiLine ? 2 : 1);
        return editText;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        return button;
    }

    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams weightWrap() { return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL, "Outils Hermes", NotificationManager.IMPORTANCE_DEFAULT);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    interface ThrowingRunnable { void run() throws Exception; }
}
