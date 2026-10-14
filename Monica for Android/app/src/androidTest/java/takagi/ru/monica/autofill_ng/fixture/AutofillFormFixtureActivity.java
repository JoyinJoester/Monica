package takagi.ru.monica.autofill_ng.fixture;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewStructure;
import android.view.WindowManager;
import android.view.autofill.AutofillManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A separate app process, using only Android APIs. The instrumentation APK does
 * not package the target app's Kotlin runtime. No production data or network is used.
 */
public class AutofillFormFixtureActivity extends Activity {
    public static final String USERNAME = "employee-1042";
    public static final String PASSWORD = "Audit-password-42!";
    public static final String ORIGIN = "https://autofill.example.test";
    private static final int PASSWORD_TYPE = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
    private final Map<String, EditText> fields = new LinkedHashMap<>();
    private LinearLayout layout;
    private TextView status;
    private WebView webView;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String scenario = getIntent().getStringExtra("scenario");
        render(scenario == null ? "standard" : scenario);
    }

    private void render(String scenario) {
        fields.clear();
        layout = new LinearLayout(this) {
            @Override public void onProvideAutofillStructure(ViewStructure structure, int flags) {
                super.onProvideAutofillStructure(structure, flags);
                if (scenario.equals("computed")) structure.setWebDomain(ORIGIN);
            }
        };
        // Preserve this domain-bearing parent in the structure Android sends to the service.
        if (scenario.equals("computed")) layout.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_YES);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24,36,24,24);
        layout.setFocusableInTouchMode(true);
        status = new TextView(this);
        status.setContentDescription("fixture-status");
        TextView title = new TextView(this);
        title.setContentDescription("fixture-instance-" + getIntent().getStringExtra("fixtureInstance"));
        title.setText("Autofill fixture: " + scenario);
        title.setTextSize(20);
        layout.addView(title);
        layout.addView(status);
        setContentView(layout);
        switch (scenario) {
            case "computed":
                field("user", "", InputType.TYPE_CLASS_TEXT, null, "username");
                field("password", "", InputType.TYPE_CLASS_TEXT, null, "current-password");
                break;
            case "wallet_address":
                field("street","Street Address",InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS,View.AUTOFILL_HINT_POSTAL_ADDRESS);
                field("city","City",InputType.TYPE_CLASS_TEXT,"addressLocality");
                field("region","Region",InputType.TYPE_CLASS_TEXT,"addressRegion");
                field("country","Country",InputType.TYPE_CLASS_TEXT,"addressCountry");
                field("zip","Postal Code",InputType.TYPE_CLASS_TEXT,View.AUTOFILL_HINT_POSTAL_CODE);
                break;
            case "wallet_card":
                field("card","Card number",InputType.TYPE_CLASS_NUMBER,View.AUTOFILL_HINT_CREDIT_CARD_NUMBER);
                field("cvv","CVV",InputType.TYPE_CLASS_NUMBER,View.AUTOFILL_HINT_CREDIT_CARD_SECURITY_CODE);
                break;
            case "wallet_document":
                field("document","Passport number",InputType.TYPE_CLASS_TEXT,"identityNumber");
                field("person","Full Name",InputType.TYPE_CLASS_TEXT,View.AUTOFILL_HINT_NAME);
                break;
            case "web": case "web_dynamic": case "web_forms": addWebForm(scenario); break;
            case "unlabelled":
                field("user","",InputType.TYPE_CLASS_TEXT,null);
                button("Request autofill", () -> {
                    EditText input = fields.get("user");
                    input.requestFocus();
                    getSystemService(AutofillManager.class).requestAutofill(input);
                });
                break;
            case "username": case "step":
                field("user","学号",InputType.TYPE_CLASS_TEXT,null);
                if (scenario.equals("step")) button("Next step", () -> render("password"));
                break;
            case "password": field("password","密码",PASSWORD_TYPE,null); break;
            case "otp":
                field("user","工号",InputType.TYPE_CLASS_TEXT,null);
                field("otp","手机验证码",InputType.TYPE_CLASS_NUMBER,null);
                break;
            case "search": field("other","搜索工号",InputType.TYPE_CLASS_TEXT,null); break;
            default:
                boolean standard = scenario.equals("standard") || scenario.equals("inline");
                field("user",standard ? "Username" : "工号",InputType.TYPE_CLASS_TEXT,
                        standard ? View.AUTOFILL_HINT_USERNAME : null);
                field("password",standard ? "Password" : "密码",
                        scenario.equals("plain_chinese") ? InputType.TYPE_CLASS_TEXT : PASSWORD_TYPE,
                        standard ? View.AUTOFILL_HINT_PASSWORD : null);
                if (scenario.equals("mixed")) {
                    webView = new WebView(this);
                    webView.loadDataWithBaseURL("https://help.example.test","<p>Help only</p>","text/html","UTF-8",null);
                    layout.addView(webView,new LinearLayout.LayoutParams(-1,160));
                }
                if (scenario.equals("dialog")) button("Open login dialog", () -> {
                    Dialog dialog = new Dialog(this);
                    EditText input = new EditText(this);
                    input.setHint("Password");
                    input.setInputType(PASSWORD_TYPE);
                    input.setAutofillHints(View.AUTOFILL_HINT_PASSWORD);
                    input.setShowSoftInputOnFocus(false);
                    input.addTextChangedListener(watcher(() -> status.setText("dialog=" + state(input.getText().toString(),PASSWORD))));
                    dialog.setContentView(input);
                    dialog.show();
                    input.requestFocus();
                });
        }
        updateStatus();
        layout.requestFocus();
    }

    private void button(String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setOnClickListener(view -> action.run());
        layout.addView(button);
    }

    private void field(String key, String label, int type, String autofillHint) {
        field(key, label, type, autofillHint, null);
    }

    private void field(String key, String label, int type, String autofillHint, String computedHint) {
        EditText field = new EditText(this) {
            @Override public void onProvideAutofillStructure(ViewStructure structure, int flags) {
                super.onProvideAutofillStructure(structure, flags);
                if (computedHint != null) structure.setHtmlInfo(structure.newHtmlInfoBuilder("input")
                        .addAttribute("computed-autofill-hints", computedHint).build());
            }
        };
        field.setId(View.generateViewId());
        // Neutral marker: it cannot help the production role classifier.
        field.setContentDescription("fixture-field-" + fields.size());
        field.setHint(label);
        field.setInputType(type);
        field.setSingleLine();
        field.setMinHeight(120);
        field.setShowSoftInputOnFocus(true);
        if (autofillHint != null) field.setAutofillHints(autofillHint);
        if (getIntent().getBooleanExtra("accessibilityOnly",false)) field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        fields.put(key,field);
        field.addTextChangedListener(watcher(this::updateStatus));
        layout.addView(field,new LinearLayout.LayoutParams(-1,-2));
    }

    private TextWatcher watcher(Runnable action) {
        return new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) {}
            public void afterTextChanged(Editable value) { action.run(); }
        };
    }

    private static String state(String value, String expected) {
        if (value == null) return "ABSENT";
        if (value.isEmpty()) return "EMPTY";
        return value.equals(expected) ? "OK" : "BAD";
    }

    private String value(String key) { return fields.containsKey(key) ? fields.get(key).getText().toString() : null; }

    private void updateStatus() {
        if (fields.containsKey("street")) {
            status.setText("street=" + state(value("street"), "123 Main Street, Shanghai, Shanghai, 200000, CN")
                + " city=" + state(value("city"), "Shanghai") + " region=" + state(value("region"), "Shanghai")
                + " country=" + state(value("country"), "CN") + " zip=" + state(value("zip"), "200000")); return;
        }
        if (fields.containsKey("card")) {
            status.setText("card=" + state(value("card"), "4242424242424242") + " cvv=" + state(value("cvv"), "123")); return;
        }
        if (fields.containsKey("document")) {
            status.setText("document=" + state(value("document"), "P-12345") + " person=" + state(value("person"), "Test Person")); return;
        }
        status.setText("user=" + state(value("user"),getIntent().getStringExtra("expectedUsername") == null ? USERNAME : getIntent().getStringExtra("expectedUsername")) + " password=" + state(value("password"),getIntent().getStringExtra("expectedPassword") == null ? PASSWORD : getIntent().getStringExtra("expectedPassword"))
                + " otp=" + state(value("otp"),"unused") + " other=" + state(value("other"),"unused"));
    }

    public class WebReporter {
        @JavascriptInterface public void report(String user,String password) {
            runOnUiThread(() -> status.setText("user=" + state(user,USERNAME) + " password=" + state(password,PASSWORD) + " web=READY"));
        }
        @JavascriptInterface public void reportForms(String user,String password,String newsletter) {
            runOnUiThread(() -> status.setText("user=" + state(user,USERNAME) + " password=" + state(password,PASSWORD)
                    + " newsletter=" + state(newsletter,"unused") + " web=READY"));
        }
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void addWebForm(String scenario) {
        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        if (getIntent().getBooleanExtra("accessibilityOnly",false)) webView.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        webView.addJavascriptInterface(new WebReporter(),"Fixture");
        String asset = scenario.equals("web_forms") ? "autofill-forms-fixture.html" : "autofill-fixture.html";
        try (InputStream input = getAssets().open(asset); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer,0,count);
            String html = new String(output.toByteArray(),StandardCharsets.UTF_8).replace("__DYNAMIC__",Boolean.toString(scenario.equals("web_dynamic")));
            webView.loadDataWithBaseURL(ORIGIN,html,"text/html","UTF-8",null);
        } catch (Exception error) { throw new IllegalStateException("Cannot load test form",error); }
        layout.addView(webView,new LinearLayout.LayoutParams(-1,750));
    }

    @Override public void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
