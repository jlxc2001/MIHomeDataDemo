package com.jlxc.mihomeclimatetest;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String PREFS = "mihome_device_status_prefs";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_FILTER = "filter";
    private static final String KEY_LIKELY_MIHOME_ONLY = "likely_mihome_only";
    private static final int REFRESH_INTERVAL_MS = 10_000;
    private static final int MAX_SHOW_COUNT = 300;

    private EditText baseUrlInput;
    private EditText tokenInput;
    private EditText filterInput;
    private CheckBox likelyMiHomeOnlyBox;
    private TextView statusText;
    private TextView resultText;
    private Button autoButton;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean autoRefresh = false;

    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!autoRefresh) return;
            fetchAllStates(false);
            mainHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        loadPrefs();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        autoRefresh = false;
        mainHandler.removeCallbacks(autoRefreshRunnable);
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("米家设备状态测试 App");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setPadding(0, dp(8), 0, dp(8));
        root.addView(title, matchWrap());

        TextView desc = new TextView(this);
        desc.setText("用途：一次读取 Home Assistant 中暴露出来的全部实体状态。\n\n" +
                "推荐流程：米家账号 → Home Assistant Xiaomi Home 集成 → 本 App 读取 HA /api/states。\n\n" +
                "如果你的 HA 主要只接入米家，这里显示的基本就是该米家账号下设备状态。" +
                "如果 HA 里还有很多其他设备，可以用关键词过滤，或者勾选“仅显示疑似米家实体”。");
        desc.setTextSize(14);
        desc.setTextColor(Color.rgb(80, 80, 80));
        desc.setLineSpacing(0, 1.15f);
        root.addView(desc, matchWrap());

        baseUrlInput = addInput(root, "Home Assistant 地址", "http://192.168.1.20:8123", false, 1);
        tokenInput = addInput(root, "长期访问令牌 Long-Lived Access Token", "从 HA 个人资料页创建并粘贴", true, 3);
        filterInput = addInput(root, "关键词过滤，可留空", "例如：客厅 / 温度 / xiaomi / lumi / switch", false, 1);

        likelyMiHomeOnlyBox = new CheckBox(this);
        likelyMiHomeOnlyBox.setText("仅显示疑似米家实体，小米/米家/MIoT/lumi/yeelink/aqara 等关键词");
        likelyMiHomeOnlyBox.setTextSize(14);
        likelyMiHomeOnlyBox.setTextColor(Color.rgb(60, 60, 60));
        likelyMiHomeOnlyBox.setPadding(0, dp(8), 0, dp(8));
        root.addView(likelyMiHomeOnlyBox, matchWrap());

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER);
        row1.setPadding(0, dp(10), 0, dp(4));
        root.addView(row1, matchWrap());

        Button saveButton = new Button(this);
        saveButton.setText("保存配置");
        saveButton.setOnClickListener(v -> savePrefs());
        row1.addView(saveButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button fetchButton = new Button(this);
        fetchButton.setText("读取全部状态");
        fetchButton.setOnClickListener(v -> fetchAllStates(true));
        LinearLayout.LayoutParams buttonMargin = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        buttonMargin.leftMargin = dp(8);
        row1.addView(fetchButton, buttonMargin);

        autoButton = new Button(this);
        autoButton.setText("开启10秒刷新");
        autoButton.setOnClickListener(v -> toggleAutoRefresh());
        root.addView(autoButton, matchWrap());

        statusText = new TextView(this);
        statusText.setText("状态：等待配置");
        statusText.setTextSize(14);
        statusText.setTextColor(Color.rgb(40, 90, 150));
        statusText.setPadding(0, dp(12), 0, dp(6));
        root.addView(statusText, matchWrap());

        resultText = new TextView(this);
        resultText.setText("读取结果会显示在这里。\n\n你也可以先不填过滤关键词，直接读取全部状态。实体很多时只显示前 " + MAX_SHOW_COUNT + " 条。");
        resultText.setTextSize(15);
        resultText.setTextColor(Color.rgb(25, 25, 25));
        resultText.setLineSpacing(0, 1.18f);
        resultText.setPadding(dp(12), dp(12), dp(12), dp(12));
        resultText.setBackgroundColor(Color.rgb(245, 247, 250));
        root.addView(resultText, matchWrap());

        setContentView(scrollView);
    }

    private EditText addInput(LinearLayout root, String labelText, String hint, boolean secret, int minLines) {
        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextSize(15);
        label.setTextColor(Color.rgb(45, 45, 45));
        label.setPadding(0, dp(14), 0, dp(4));
        root.addView(label, matchWrap());

        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextSize(15);
        input.setSingleLine(minLines <= 1);
        input.setMinLines(minLines);
        input.setGravity(minLines > 1 ? Gravity.TOP | Gravity.START : Gravity.CENTER_VERTICAL | Gravity.START);
        input.setPadding(dp(10), dp(8), dp(10), dp(8));
        if (secret) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        }
        root.addView(input, matchWrap());
        return input;
    }

    private void loadPrefs() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        baseUrlInput.setText(sp.getString(KEY_BASE_URL, ""));
        tokenInput.setText(sp.getString(KEY_TOKEN, ""));
        filterInput.setText(sp.getString(KEY_FILTER, ""));
        likelyMiHomeOnlyBox.setChecked(sp.getBoolean(KEY_LIKELY_MIHOME_ONLY, false));
    }

    private void savePrefs() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_BASE_URL, clean(baseUrlInput.getText().toString()))
                .putString(KEY_TOKEN, clean(tokenInput.getText().toString()))
                .putString(KEY_FILTER, clean(filterInput.getText().toString()))
                .putBoolean(KEY_LIKELY_MIHOME_ONLY, likelyMiHomeOnlyBox.isChecked())
                .apply();
        setStatus("状态：配置已保存");
    }

    private void toggleAutoRefresh() {
        if (autoRefresh) {
            autoRefresh = false;
            mainHandler.removeCallbacks(autoRefreshRunnable);
            autoButton.setText("开启10秒刷新");
            setStatus("状态：已关闭自动刷新");
        } else {
            savePrefs();
            autoRefresh = true;
            autoButton.setText("关闭自动刷新");
            setStatus("状态：自动刷新已开启，每10秒读取一次");
            fetchAllStates(false);
            mainHandler.postDelayed(autoRefreshRunnable, REFRESH_INTERVAL_MS);
        }
    }

    private void fetchAllStates(boolean manual) {
        String baseUrl = clean(baseUrlInput.getText().toString());
        String token = clean(tokenInput.getText().toString());
        String filter = clean(filterInput.getText().toString());
        boolean likelyOnly = likelyMiHomeOnlyBox.isChecked();

        if (baseUrl.isEmpty()) {
            setStatus("状态：请填写 Home Assistant 地址");
            return;
        }
        if (token.isEmpty()) {
            setStatus("状态：请填写 Home Assistant 长期访问令牌");
            return;
        }

        if (manual) savePrefs();
        setStatus("状态：正在读取全部状态...");

        new Thread(() -> {
            try {
                List<EntityState> allStates = fetchStates(baseUrl, token);
                List<EntityState> filtered = filterStates(allStates, filter, likelyOnly);
                String output = buildOutput(allStates.size(), filtered, filter, likelyOnly);
                runOnUiThread(() -> {
                    resultText.setText(output);
                    setStatus("状态：读取成功，共 " + allStates.size() + " 个实体，显示 " + Math.min(filtered.size(), MAX_SHOW_COUNT) + " 个");
                });
            } catch (Exception e) {
                String msg = "读取失败：" + e.getMessage();
                runOnUiThread(() -> {
                    resultText.setText(msg);
                    setStatus("状态：读取失败");
                });
            }
        }).start();
    }

    private List<EntityState> fetchStates(String baseUrl, String token) throws Exception {
        String cleanBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String apiUrl = cleanBase + "/api/states";

        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8_000);
        conn.setReadTimeout(12_000);
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Accept", "application/json");

        int code = conn.getResponseCode();
        InputStream stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String body = readAll(stream);
        conn.disconnect();

        if (code == 401 || code == 403) {
            throw new Exception("鉴权失败，请检查 Token。HTTP " + code);
        }
        if (code < 200 || code >= 300) {
            throw new Exception("HTTP " + code + "：" + shorten(body, 500));
        }

        JSONArray arr = new JSONArray(body);
        List<EntityState> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null) continue;
            JSONObject attr = obj.optJSONObject("attributes");

            EntityState state = new EntityState();
            state.entityId = obj.optString("entity_id", "");
            state.domain = parseDomain(state.entityId);
            state.state = obj.optString("state", "unknown");
            state.lastChanged = obj.optString("last_changed", "unknown");
            state.lastUpdated = obj.optString("last_updated", "unknown");
            state.friendlyName = attr == null ? state.entityId : attr.optString("friendly_name", state.entityId);
            state.unit = attr == null ? "" : attr.optString("unit_of_measurement", "");
            state.deviceClass = attr == null ? "" : attr.optString("device_class", "");
            state.attributesText = importantAttributes(attr);
            list.add(state);
        }

        Collections.sort(list, new Comparator<EntityState>() {
            @Override
            public int compare(EntityState a, EntityState b) {
                int d = safe(a.domain).compareToIgnoreCase(safe(b.domain));
                if (d != 0) return d;
                return safe(a.friendlyName).compareToIgnoreCase(safe(b.friendlyName));
            }
        });
        return list;
    }

    private List<EntityState> filterStates(List<EntityState> all, String keyword, boolean likelyOnly) {
        List<EntityState> out = new ArrayList<>();
        String k = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        for (EntityState e : all) {
            if (likelyOnly && !isLikelyMiHome(e)) continue;
            if (!k.isEmpty() && !containsKeyword(e, k)) continue;
            out.add(e);
        }
        return out;
    }

    private boolean containsKeyword(EntityState e, String k) {
        String haystack = (safe(e.entityId) + "\n" + safe(e.friendlyName) + "\n" + safe(e.domain) + "\n" +
                safe(e.deviceClass) + "\n" + safe(e.state) + "\n" + safe(e.attributesText)).toLowerCase(Locale.ROOT);
        return haystack.contains(k);
    }

    private boolean isLikelyMiHome(EntityState e) {
        String haystack = (safe(e.entityId) + "\n" + safe(e.friendlyName) + "\n" + safe(e.attributesText)).toLowerCase(Locale.ROOT);
        String[] keys = new String[]{
                "xiaomi", "mihome", "mi_home", "miot", "mijia", "mija", "miio",
                "lumi", "aqara", "yeelight", "yeelink", "chuangmi", "zhimi", "roidmi",
                "小米", "米家", "绿米", "aqara"
        };
        for (String key : keys) {
            if (haystack.contains(key)) return true;
        }
        return false;
    }

    private String buildOutput(int totalCount, List<EntityState> list, String filter, boolean likelyOnly) {
        StringBuilder sb = new StringBuilder();
        sb.append("HA 实体总数：").append(totalCount).append("\n");
        sb.append("当前显示：").append(Math.min(list.size(), MAX_SHOW_COUNT)).append(" / ").append(list.size()).append("\n");
        sb.append("读取时间：").append(nowText()).append("\n");
        if (likelyOnly) sb.append("筛选：仅疑似米家实体\n");
        if (filter != null && !filter.trim().isEmpty()) sb.append("关键词：").append(filter.trim()).append("\n");
        sb.append("\n");

        if (list.isEmpty()) {
            sb.append("没有匹配结果。\n\n");
            sb.append("建议：\n");
            sb.append("1. 取消“仅显示疑似米家实体”再试。\n");
            sb.append("2. 关键词留空读取全部。\n");
            sb.append("3. 在 Home Assistant 里确认 Xiaomi Home 集成已经导入设备。\n");
            return sb.toString();
        }

        int count = Math.min(list.size(), MAX_SHOW_COUNT);
        for (int i = 0; i < count; i++) {
            EntityState e = list.get(i);
            sb.append(i + 1).append(". ").append(e.friendlyName).append("\n");
            sb.append("   状态：").append(e.stateWithUnit()).append("\n");
            sb.append("   实体：").append(e.entityId).append("\n");
            if (!e.deviceClass.isEmpty()) sb.append("   类型：").append(e.deviceClass).append("\n");
            if (!e.attributesText.isEmpty()) sb.append("   属性：").append(e.attributesText).append("\n");
            sb.append("   更新：").append(e.lastUpdated).append("\n\n");
        }

        if (list.size() > MAX_SHOW_COUNT) {
            sb.append("还有 ").append(list.size() - MAX_SHOW_COUNT).append(" 个实体没有显示。请使用关键词进一步过滤。\n");
        }
        return sb.toString();
    }

    private String importantAttributes(JSONObject attr) {
        if (attr == null) return "";
        StringBuilder sb = new StringBuilder();
        appendAttr(sb, attr, "current_temperature", "当前温度");
        appendAttr(sb, attr, "temperature", "温度");
        appendAttr(sb, attr, "humidity", "湿度");
        appendAttr(sb, attr, "battery_level", "电量");
        appendAttr(sb, attr, "illuminance", "照度");
        appendAttr(sb, attr, "pressure", "气压");
        appendAttr(sb, attr, "voltage", "电压");
        appendAttr(sb, attr, "current_power_w", "功率");
        appendAttr(sb, attr, "power", "功率");
        appendAttr(sb, attr, "model", "型号");
        appendAttr(sb, attr, "manufacturer", "厂商");
        appendAttr(sb, attr, "room", "房间");
        appendAttr(sb, attr, "area", "区域");
        return sb.toString();
    }

    private void appendAttr(StringBuilder sb, JSONObject attr, String key, String label) {
        if (!attr.has(key)) return;
        String value = attr.optString(key, "");
        if (value.isEmpty() || "null".equals(value)) return;
        if (sb.length() > 0) sb.append("，");
        sb.append(label).append("=").append(value);
    }

    private String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private void setStatus(String text) {
        runOnUiThread(() -> statusText.setText(text));
    }

    private String clean(String s) {
        return s == null ? "" : s.trim();
    }

    private String nowText() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }

    private String shorten(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }

    private static String parseDomain(String entityId) {
        if (entityId == null) return "";
        int dot = entityId.indexOf('.');
        if (dot <= 0) return "";
        return entityId.substring(0, dot);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private static class EntityState {
        String entityId;
        String domain;
        String state;
        String unit;
        String friendlyName;
        String deviceClass;
        String lastChanged;
        String lastUpdated;
        String attributesText;

        String stateWithUnit() {
            if (unit == null || unit.isEmpty()) return state;
            return state + " " + unit;
        }
    }
}
