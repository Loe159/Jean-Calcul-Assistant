package com.jeancalcul.assistant;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
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
import java.util.Random;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String PREFS = "hermes_config";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_TOKEN = "token";
    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:8765";
    private static final String NOTIFICATION_CHANNEL = "hermes_tools";
    private static final int REQUEST_POST_NOTIFICATIONS = 42;
    private static final int GLASS_BACKGROUND = Color.rgb(10, 12, 20);
    private static final int GLASS_SURFACE = Color.rgb(18, 22, 34);
    private static final int GLASS_TEXT = Color.rgb(246, 248, 255);
    private static final int GLASS_MUTED_TEXT = Color.rgb(190, 201, 218);
    private static final int GLASS_CARD_TEXT = Color.rgb(246, 248, 255);
    private static final int GLASS_CARD_MUTED_TEXT = Color.rgb(190, 201, 218);
    private static final int GLASS_PRIMARY = Color.rgb(108, 99, 255);
    private static final int GLASS_SECONDARY = Color.rgb(0, 229, 255);
    private static final int GLASS_ERROR = Color.rgb(255, 82, 82);
    private static final int GLASS_SUCCESS = Color.rgb(80, 118, 82);


    private EditText baseUrlInput;
    private EditText tokenInput;
    private EditText messageInput;
    private TextView statusView;
    private LinearLayout messagesList;
    private LinearLayout chatPage;
    private ScrollView settingsPage;
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
        FrameLayout liquidBackground = new FrameLayout(this);
        liquidBackground.setBackground(new LiquidGlassBackgroundDrawable());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(14));
        liquidBackground.addView(root, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout header = glassCard();
        header.setPadding(dp(16), dp(14), dp(16), dp(14));
        TextView title = text("Jean Calcul", 22, GLASS_CARD_TEXT, true);
        TextView subtitle = text("Assistant Hermes mobile", 13, GLASS_CARD_MUTED_TEXT, false);
        header.addView(title, matchWrap());
        header.addView(subtitle, matchWrap());

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        Button chatTab = button("Chat");
        Button settingsTab = button("Paramètres");
        tabs.addView(chatTab, weightWrap());
        tabs.addView(settingsTab, weightWrap());
        header.addView(tabs, matchWrap());
        root.addView(header, matchWrap());

        FrameLayout pages = new FrameLayout(this);
        root.addView(pages, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        chatPage = buildChatPage();
        settingsPage = buildSettingsPage();
        pages.addView(chatPage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        pages.addView(settingsPage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        chatTab.setOnClickListener(v -> showPage(true));
        settingsTab.setOnClickListener(v -> showPage(false));
        showPage(true);
        return liquidBackground;
    }

    private LinearLayout buildChatPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, dp(8), 0, 0);

        ScrollView messagesScroll = new ScrollView(this);
        messagesScroll.setFillViewport(true);
        LinearLayout messagesCard = glassCard();
        messagesCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        statusView = text("Prêt", 14, Color.rgb(195, 255, 207), false);
        messagesList = new LinearLayout(this);
        messagesList.setOrientation(LinearLayout.VERTICAL);
        messagesCard.addView(statusView, matchWrap());
        messagesCard.addView(messagesList, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        addChatBubble("Les capacités exposées : get_volume, set_volume, open_app, toggle_flashlight, send_notification.", false);
        messagesScroll.addView(messagesCard, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        page.addView(messagesScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout inputBar = new LinearLayout(this);
        inputBar.setOrientation(LinearLayout.HORIZONTAL);
        inputBar.setGravity(Gravity.CENTER_VERTICAL);
        inputBar.setPadding(0, dp(10), 0, 0);
        messageInput = input("Message à envoyer", "Ouvre les paramètres", true);
        Button sendButton = button("Envoyer");
        sendButton.setOnClickListener(v -> runAsync(this::sendMessage));
        inputBar.addView(messageInput, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sendParams.setMargins(dp(8), 0, 0, 0);
        inputBar.addView(sendButton, sendParams);
        page.addView(inputBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return page;
    }

    private ScrollView buildSettingsPage() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setPadding(0, dp(8), 0, 0);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        LinearLayout configCard = glassCard();
        configCard.addView(label("Configuration Hermes"));
        TextView helper = text("Renseigne l'URL et le token, puis teste la connexion avant de revenir au chat.", 14, GLASS_CARD_MUTED_TEXT, false);
        configCard.addView(helper, matchWrap());
        baseUrlInput = input("URL Hermes", preferences.getString(KEY_BASE_URL, DEFAULT_BASE_URL), false);
        tokenInput = input("Token optionnel", preferences.getString(KEY_TOKEN, ""), false);
        configCard.addView(baseUrlInput, matchWrap());
        configCard.addView(tokenInput, matchWrap());

        LinearLayout configButtons = new LinearLayout(this);
        configButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button saveButton = button("Enregistrer");
        saveButton.setOnClickListener(v -> saveConfig());
        Button healthButton = button("Tester");
        healthButton.setOnClickListener(v -> runAsync(this::healthCheck));
        configButtons.addView(saveButton, weightWrap());
        configButtons.addView(healthButton, weightWrap());
        configCard.addView(configButtons, matchWrap());
        content.addView(configCard, cardLayout());

        LinearLayout toolsCard = glassCard();
        toolsCard.addView(label("Outils Android"));
        toolsCard.addView(sidebarItem("Volume : get_volume, set_volume"), matchWrap());
        toolsCard.addView(sidebarItem("Applications : open_app"), matchWrap());
        toolsCard.addView(sidebarItem("Lampe torche : toggle_flashlight"), matchWrap());
        toolsCard.addView(sidebarItem("Notifications : send_notification"), matchWrap());
        content.addView(toolsCard, cardLayout());
        return scrollView;
    }

    private void showPage(boolean showChat) {
        chatPage.setVisibility(showChat ? View.VISIBLE : View.GONE);
        settingsPage.setVisibility(showChat ? View.GONE : View.VISIBLE);
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
        appendConversation("Vous: " + messageInput.getText().toString(), true);
        JSONObject response = request("POST", "/api/mobile/request", body);
        appendConversation("Hermes: " + response.optString("message", ""), false);
        JSONArray actions = response.optJSONArray("actions");
        if (actions == null || actions.length() == 0) {
            setStatus("Réponse reçue sans action.");
            return;
        }
        for (int i = 0; i < actions.length(); i++) {
            JSONObject action = actions.getJSONObject(i);
            JSONObject result = executeAction(requestId, action);
            JSONObject followUp = request("POST", "/api/mobile/tool-result", result);
            appendConversation("Outil " + action.optString("tool") + ": " + result.optString("status") + " — " + followUp.optString("message", "résultat envoyé"), false);
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
    private void appendConversation(String text, boolean isUser) { runOnUiThread(() -> addChatBubble(text, isUser)); }

    private void addChatBubble(String value, boolean isUser) {
        TextView bubble = text(value, 15, GLASS_CARD_TEXT, false);
        bubble.setLineSpacing(4f, 1.05f);
        bubble.setPadding(dp(16), dp(12), dp(16), dp(12));
        bubble.setBackground(glassDrawable(dp(24), isUser ? 0.86f : 0.62f, isUser ? 0.98f : 0.84f));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = isUser ? Gravity.RIGHT : Gravity.LEFT;
        params.setMargins(0, dp(4), 0, dp(8));
        messagesList.addView(bubble, params);
    }

    private TextView label(String text) {
        TextView label = text(text, 16, GLASS_CARD_TEXT, true);
        label.setPadding(0, 4, 0, 12);
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
        editText.setMinLines(multiLine ? 3 : 1);
        editText.setTextColor(GLASS_CARD_TEXT);
        editText.setHintTextColor(GLASS_CARD_MUTED_TEXT);
        editText.setTextSize(15);
        editText.setPadding(30, 20, 30, 20);
        editText.setBackground(glassDrawable(34, 0.78f, 0.96f));
        return editText;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(GLASS_CARD_TEXT);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setPadding(18, 16, 18, 16);
        button.setBackground(glassDrawable(42, 0.74f, 0.96f));
        return button;
    }

    private LinearLayout glassCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(30, 30, 30, 30);
        card.setBackground(glassDrawable(38, 0.82f, 0.96f));
        card.setElevation(18f);
        return card;
    }

    private Drawable glassDrawable(int radius, float startAlpha, float endAlpha) {
        GradientDrawable fill = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{alpha(GLASS_SURFACE, Math.round(255 * startAlpha)), alpha(GLASS_SURFACE, Math.round(255 * endAlpha / 2f))});
        fill.setCornerRadius(radius);

        GradientDrawable border = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{alpha(Color.WHITE, 190), alpha(GLASS_SECONDARY, 105), alpha(GLASS_PRIMARY, 125)});
        border.setCornerRadius(radius);

        LayerDrawable layers = new LayerDrawable(new Drawable[]{border, fill});
        layers.setLayerInset(1, 2, 2, 2, 2);
        return layers;
    }

    private int alpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private LinearLayout.LayoutParams matchWrap() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 8, 0, 8);
        return params;
    }

    private LinearLayout.LayoutParams cardLayout() {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 12, 0, 18);
        return params;
    }

    private LinearLayout.LayoutParams weightWrap() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(6, 0, 6, 0);
        return params;
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(alpha(GLASS_CARD_MUTED_TEXT, 55));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        params.setMargins(0, dp(16), 0, dp(16));
        parent.addView(divider, params);
    }

    private TextView sidebarItem(String value) {
        TextView item = text(value, 15, GLASS_CARD_TEXT, false);
        item.setPadding(dp(14), dp(12), dp(14), dp(12));
        item.setBackground(glassDrawable(dp(22), 0.52f, 0.74f));
        return item;
    }

    private void addSpaceFill(LinearLayout parent) {
        Space space = new Space(this);
        parent.addView(space, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL, "Outils Hermes", NotificationManager.IMPORTANCE_DEFAULT);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    private static class LiquidGlassBackgroundDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random random = new Random(4125);

        @Override
        public void draw(Canvas canvas) {
            int width = getBounds().width();
            int height = getBounds().height();
            paint.setShader(new LinearGradient(0, 0, width, height,
                    new int[]{GLASS_BACKGROUND, Color.rgb(14, 19, 36), Color.rgb(5, 18, 28)},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRect(getBounds(), paint);

            drawBlob(canvas, width * 0.18f, height * 0.10f, width * 0.58f, GLASS_PRIMARY, 95);
            drawBlob(canvas, width * 0.90f, height * 0.28f, width * 0.50f, GLASS_SECONDARY, 82);
            drawBlob(canvas, width * 0.08f, height * 0.83f, width * 0.46f, GLASS_ERROR, 62);
            drawBlob(canvas, width * 0.78f, height * 0.92f, width * 0.66f, GLASS_PRIMARY, 72);
            random.setSeed(4125);
        }

        private void drawBlob(Canvas canvas, float cx, float cy, float radius, int color, int centerAlpha) {
            float driftX = (random.nextFloat() - 0.5f) * 28f;
            float driftY = (random.nextFloat() - 0.5f) * 28f;
            paint.setShader(new RadialGradient(cx + driftX, cy + driftY, radius,
                    new int[]{Color.argb(centerAlpha, Color.red(color), Color.green(color), Color.blue(color)), Color.TRANSPARENT},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx + driftX, cy + driftY, radius, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    interface ThrowingRunnable { void run() throws Exception; }
}
