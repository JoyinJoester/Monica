package takagi.ru.monica.lansync;

import android.app.Application;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class DevicePasswordShapeTest {
    @Test public void portableContentPreservesWifiSshArchiveAndProtectedFields() throws Exception {
        JSONObject payload = new JSONObject().put("password_plain", "synthetic")
            .put("wifi_metadata", new JSONObject().put("ssid", "test-network"))
            .put("ssh_key_data", "synthetic-ssh").put("is_archived", true).put("archived_at", 1234L)
            .put("custom_fields", new JSONArray().put(new JSONObject().put("title", "Secret").put("value", "synthetic-field")
                .put("is_protected", true).put("sort_order", 4)));
        JSONObject shape = DevicePasswordShape.INSTANCE.normalize(payload, "Login", "Folder", "ssh-key");
        assertEquals("test-network", new JSONObject(shape.getString("wifi_metadata")).getString("ssid"));
        assertEquals("ssh-key", shape.getString("entry_type"));
        assertEquals(1234L, shape.getLong("archived_at"));
        JSONObject applied = DevicePasswordShape.INSTANCE.applyNative(shape, "{\"device_only\":\"keep\"}", "entry", "folder");
        assertEquals("keep", applied.getString("device_only"));
        assertEquals("synthetic-ssh", applied.getString("ssh_key_data"));
        assertTrue(applied.getJSONArray("custom_fields").getJSONObject(0).getBoolean("is_protected"));
    }

    @Test public void unexpectedFieldTypesAreRejectedBeforeWriting() throws Exception {
        JSONObject shape = DevicePasswordShape.INSTANCE.normalize(new JSONObject(), "Login", "", "login");
        shape.put("password_plain", new JSONObject().put("malformed", true));
        assertThrows(LanSyncException.class, () -> DevicePasswordShape.INSTANCE.validate(shape));
    }

    @Test public void oldDesktopEnvelopeKeepsItsPasswordAndCustomFields() throws Exception {
        JSONObject legacy = new JSONObject().put("kind", "password").put("schemaVersion", 1)
            .put("data", new JSONObject().put("entry", new JSONObject().put("id", 42).put("password", "synthetic-legacy")
                .put("username", "synthetic-user").put("loginType", 4).put("sshKeyData", "synthetic-ssh").put("creditCardCVV", "synthetic-cvv")
                .put("isArchived", true).put("archivedAt", "2026-10-09T00:00:00+00:00"))
                .put("customFields", new JSONArray().put(new JSONObject().put("title", "Secret").put("value", "synthetic-field")
                    .put("isProtected", true).put("sortOrder", 1))));
        JSONObject flat = DevicePasswordShape.INSTANCE.nativePayload(legacy);
        JSONObject shape = DevicePasswordShape.INSTANCE.normalize(flat, "Legacy", "", "login");
        assertEquals("synthetic-legacy", shape.getString("password_plain"));
        assertEquals("SSH_KEY", shape.getString("login_type"));
        assertEquals("synthetic-ssh", shape.getString("ssh_key_data"));
        assertEquals("synthetic-cvv", shape.getString("credit_card_cvv_plain"));
        assertTrue(shape.getJSONArray("custom_fields").getJSONObject(0).getBoolean("is_protected"));
        shape.put("password_plain", "synthetic-updated");
        JSONObject updated = DevicePasswordShape.INSTANCE.applyNative(shape, legacy.toString(), "entry", null);
        assertFalse(updated.has("data"));
        assertFalse(updated.has("password"));
        assertEquals("synthetic-updated", updated.getString("password_plain"));
    }
}
