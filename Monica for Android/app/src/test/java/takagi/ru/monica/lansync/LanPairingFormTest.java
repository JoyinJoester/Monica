package takagi.ru.monica.lansync;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class LanPairingFormTest {
    private static final String CODE = new String(new char[43]).replace('\0', 'A');
    private static final String LINK = "monica-lan:1:192.168.1.5:53318:" + CODE;

    @Test
    public void qrFillsBothFieldsAndReplacesAnOldManualTarget() {
        LanPeer oldPeer = new LanPeer("Old device", "192.168.1.2", 2000);
        LanPairingForm old = new LanPairingForm("", "old-code", oldPeer, false);
        LanPairingForm filled = old.withInvitation(LINK, Collections.emptyList());
        assertEquals(LINK, filled.getInvitation());
        assertEquals(CODE, filled.getPairingCode());
        assertEquals("192.168.1.5", filled.getPeer().getAddress());
        assertEquals(53318, filled.getPeer().getPort());
        assertEquals(LINK, filled.getConnectionLink());
        assertTrue(filled.getAutoFilled());
    }

    @Test
    public void automaticSyncQrRetainsProtocolTwoAndFillsEverythingTogether() {
        String link = LINK.replace("monica-lan:1:", "monica-lan:2:");
        LanPairingForm form = new LanPairingForm("", "", null, false).withInvitation(link, Collections.emptyList());
        assertEquals(link, form.getConnectionLink());
        assertEquals(CODE, form.getPairingCode());
        assertEquals("192.168.1.5", form.getPeer().getAddress());
        assertEquals(2, LanPairingInvitation.Companion.parse(link).getVersion());
    }

    @Test
    public void oneQrKeepsAllInterfacesAndUsesTheDiscoveredDeviceName() {
        String link = "monica-lan:2:10.30.250.111,192.168.1.5:53318:" + CODE;
        LanPeer computer = new LanPeer("Computer", "192.168.1.5", 53318);
        LanPairingForm form = new LanPairingForm("", "", null, false).withInvitation(link, Collections.singletonList(computer));
        assertTrue(form.getAutoFilled());
        assertEquals(link, form.getConnectionLink());
        assertEquals(CODE, form.getPairingCode());
        assertSame(computer, form.getPeer());
    }

    @Test
    public void pastedLinkUsesTheDiscoveredDeviceName() {
        LanPeer device = new LanPeer("My computer", "192.168.1.5", 53318);
        LanPairingForm filled = new LanPairingForm("", "", null, false)
            .withInvitation(" \n" + LINK + "\n", Collections.singletonList(device));
        assertSame(device, filled.getPeer());
        assertEquals(LINK, filled.getInvitation());
        assertEquals(CODE, filled.getPairingCode());
    }

    @Test
    public void switchingDeviceClearsThePreviouslyScannedCredentials() {
        LanPairingForm scanned = new LanPairingForm("", "", null, false).withInvitation(LINK, Collections.emptyList());
        LanPeer other = new LanPeer("Other", "10.0.0.2", 5000);
        LanPairingForm manual = scanned.selectPeer(other);
        assertFalse(manual.getAutoFilled());
        assertEquals("", manual.getInvitation());
        assertEquals("", manual.getPairingCode());
        assertEquals("", manual.getConnectionLink());
        assertEquals("monica-lan:1:10.0.0.2:5000:" + CODE, manual.withCode(CODE).getConnectionLink());
    }

    @Test
    public void clearingLinkRemovesDerivedCodeAndQrCodesCannotBeEditedSeparately() {
        LanPairingForm scanned = new LanPairingForm("", "", null, false).withInvitation(LINK, Collections.emptyList());
        assertEquals(CODE, scanned.withCode("unrelated-code").getPairingCode());
        LanPairingForm cleared = scanned.withInvitation("", Collections.emptyList());
        assertNull(cleared.getPeer());
        assertEquals("", cleared.getPairingCode());
        assertFalse(cleared.getAutoFilled());
    }

    @Test
    public void malformedOrNonLocalInvitationsDoNotPopulateTheForm() {
        for (String value : Arrays.asList(
            LINK.replace("192.168.1.5", "8.8.8.8"), LINK.replace("53318", "0"),
            LINK.replace("53318", "65536"), LINK.replace("192.168.1.5", "192.168.01.5"),
            "monica-lan:1:192.168.1.5:53318:bad-code", LINK + "=")) {
            LanPairingForm form = new LanPairingForm("", "", null, false).withInvitation(value, Collections.emptyList());
            assertFalse(form.getAutoFilled());
            assertEquals("", form.getPairingCode());
            assertNull(form.getPeer());
        }
    }

    @Test
    public void multiAddressInvitationsRejectPublicDuplicatesAndLegacyAmbiguity() {
        for (String host : Arrays.asList("10.0.0.1,8.8.8.8", "10.0.0.1,10.0.0.1", "10.0.0.1,", ",10.0.0.1")) {
            assertNull(LanPairingInvitation.Companion.parse("monica-lan:2:" + host + ":53318:" + CODE));
        }
        assertNull(LanPairingInvitation.Companion.parse("monica-lan:1:10.0.0.1,192.168.1.1:53318:" + CODE));
    }
}
