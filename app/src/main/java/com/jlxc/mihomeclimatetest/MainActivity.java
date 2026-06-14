package com.jlxc.mihomeclimatetest;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String PREFS = "mihome_climate_test_prefs";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_TEMP_ENTITY = "temp_entity";
    private static final String KEY_HUM_ENTITY = "hum_entity";
    private static final int REFRESH_INTERVAL_MS = 10_000;

    private EditText baseUrlInput;
    private EditText tokenInput;
    private EditText tempEntityInput;
    private EditText humEntityInput;
    private TextView statusText;
    private TextView resultText;
    private Button autoButton;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean autoRefresh = false;

    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!autoRefresh) return;
            fetchClimateOnce(false);
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
        title.setText("米家温湿度测试 App");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setPadding(0, dp(8), 0, dp(8));
        root.addView(title, matchWrap());

        TextView desc = new TextView(this);
        desc.setText("用途：读取 Home Assistant 中的米家温度/湿度实体。先在 Home Assistant 里接入 Xiaomi Home，再把实体 ID 填进来。\n\n例子：\nHA地址：http://192.168.1.20:8123\n温度实体：sensor.living_room_temperature\n湿度实体：sensor.living_room_humidity");
        desc.setTextSize(14);
        desc.setTextColor(Color.rgb(80, 80, 80));
        desc.setLineSpacing(0, 1.15f);
        root.addView(desc, matchWrap());

        baseUrlInput = addInput(root, "Home Assistant 地址", "http://192.168.1.20:8123", false, 1);
        tokenInput = addInput(root, "长期访问令牌 Long-Lived Access Token", "从 HA 个人资料页创建并粘贴", true, 3);
        tempEntityInput = addInput(root, "温度实体 ID", "sensor.xxx_temperature", false, 1);
        humEntityInput = addInput(root, "湿度实体 ID", "sensor.xxx_humidity", false, 1);

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
        fetchButton.setText("读取一次");
        fetchButton.setOnClickListener(v -> fetchClimateOnce(true));
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
        resultText.setText("读取结果会显示在这里。");
        resultText.setTextSize(18);
        resultText.setTextColor(Color.rgb(25, 25, 25));
        resultText.setLineSpacing(0, 1.25f);
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
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        }
        root.addView(input, matchWrap());
        return input;
    }

    private void loadPrefs() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        baseUrlInput.setText(sp.getString(KEY_BASE_URL, ""));
        tokenInput.setText(sp.getString(KEY_TOKEN, ""));
        tempEntityInput.setText(sp.getString(KEY_TEMP_ENTITY, ""));
        humEntityInput.setText(sp.getString(KEY_HUM_ENTITY, ""));
    }

    private void savePrefs() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_BASE_URL, clean(baseUrlInput.getText().toString()))
                .putString(KEY_TOKEN, clean(tokenInput.getText().toString()))
                .putString(KEY_TEMP_ENTITY, clean(tempEntityInput.getText().toString()))
                .putString(KEY_HUM_ENTITY, clean(humEntityInput.getText().toString()))
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
            fetchClimateOnce(false);
            mainHandler.postDelayed(autoRefreshRunnable, REFRESH_INTERVAL_MS);
        }
    }

    private void fetchClimateOnce(boolean manual) {
        String baseUrl = clean(baseUrlInput.getText().toString());
        String token = clean(tokenInput.getText().toString());
        String tempEntity = clean(tempEntityInput.getText().toString());
        String humEntity = clean(humEntityInput.getText().toString());

        if (baseUrl.isEmpty()) {
            setStatus("状态：请填写 Home Assistant 地址");
            return;
        }
        if (token.isEmpty()) {
            setStatus("状态：请填写 Home Assistant 长期访问令牌");
            return;
        }
        if (tempEntity.isEmpty() && humEntity.isEmpty()) {
            setStatus("状态：请至少填写一个实体 ID");
            return;
        }

        if (manual) savePrefs();
        setStatus("状态：正在读取 Home Assistant...");

        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            boolean ok = true;

            if (!tempEntity.isEmpty()) {
                try {
                    EntityState temp = fetchEntityState(baseUrl, token, tempEntity);
                    sb.append("温度：").append(temp.stateWithUnit()).append("\n")
                            .append("实体：").append(temp.entityId).append("\n")
                            .append("名称：").append(temp.friendlyName).append("\n")
                            .append("更新时间：").append(temp.lastChanged).append("\n\n");
                } catch (Exception e) {
                    ok = false;
                    sb.append("温度读取失败：").append(e.getMessage()).append("\n\n");
                }
            }

            if (!humEntity.isEmpty()) {
                try {
                    EntityState hum = fetchEntityState(baseUrl, token, humEntity);
                    sb.append("湿度：").append(hum.stateWithUnit()).append("\n")
                            .append("实体：").append(hum.entityId).append("\n")
                            .append("名称：").append(hum.friendlyName).append("\n")
                            .append("更新时间：").append(hum.lastChanged).append("\n\n");
                } catch (Exception e) {
                    ok = false;
                    sb.append("湿度读取失败：").append(e.getMessage()).append("\n\n");
                }
            }

            sb.append("本机读取时间：").append(nowText());
            boolean finalOk = ok;
            String output = sb.toString();
            runOnUiThread(() -> {
                resultText.setText(output);
                setStatus(finalOk ? "状态：读取成功" : "状态：读取完成，但有项目失败");
            });
        }).start();
    }

    private EntityState fetchEntityState(String baseUrl, String token, String entityId) throws Exception {
        String cleanBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String encodedEntity = URLEncoder.encode(entityId, "UTF-8").replace("+", "%20");
        String apiUrl = cleanBase + "/api/states/" + encodedEntity;

        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8_000);
        conn.setReadTimeout(8_000);
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Accept", "application/json");

        int code = conn.getResponseCode();
        InputStream stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String body = readAll(stream);
        conn.disconnect();

        if (code == 401 || code == 403) {
            throw new Exception("鉴权失败，请检查 Token。HTTP " + code);
        }
        if (code == 404) {
            throw new Exception("实体不存在，请检查实体 ID。HTTP 404");
        }
        if (code < 200 || code >= 300) {
            throw new Exception("HTTP " + code + "：" + body);
        }

        JSONObject obj = new JSONObject(body);
        JSONObject attr = obj.optJSONObject("attributes");

        EntityState state = new EntityState();
        state.entityId = obj.optString("entity_id", entityId);
        state.state = obj.optString("state", "unknown");
        state.unit = attr == null ? "" : attr.optString("unit_of_measurement", "");
        state.friendlyName = attr == null ? state.entityId : attr.optString("friendly_name", state.entityId);
        state.lastChanged = obj.optString("last_changed", "unknown");
        return state;
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
        String state;
        String unit;
        String friendlyName;
        String lastChanged;

        String stateWithUnit() {
            if (unit == null || unit.isEmpty()) return state;
            return state + " " + unit;
        }
    }
}
