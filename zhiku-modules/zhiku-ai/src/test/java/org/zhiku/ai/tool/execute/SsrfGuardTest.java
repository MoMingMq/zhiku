package org.zhiku.ai.tool.execute;

import org.junit.jupiter.api.Test;
import org.zhiku.common.core.exception.ServiceException;

import java.net.InetAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SSRF 校验器单测（字面量 IP 不触发 DNS，测试稳定）
 *
 * @author zhiku
 */
class SsrfGuardTest {

    private final SsrfGuard guard = new SsrfGuard();

    @Test
    void intranetAddressesAreBlockedByDefault() throws Exception {
        assertTrue(guard.isIntranet(InetAddress.getByName("127.0.0.1")));
        assertTrue(guard.isIntranet(InetAddress.getByName("127.8.8.8")));
        assertTrue(guard.isIntranet(InetAddress.getByName("0.0.0.0")));
        assertTrue(guard.isIntranet(InetAddress.getByName("169.254.1.1")));
        assertTrue(guard.isIntranet(InetAddress.getByName("10.1.2.3")));
        assertTrue(guard.isIntranet(InetAddress.getByName("172.16.0.1")));
        assertTrue(guard.isIntranet(InetAddress.getByName("172.31.255.255")));
        assertTrue(guard.isIntranet(InetAddress.getByName("192.168.0.1")));
        assertTrue(guard.isIntranet(InetAddress.getByName("::1")));
        assertTrue(guard.isIntranet(InetAddress.getByName("fd00::1")));
        assertFalse(guard.isIntranet(InetAddress.getByName("8.8.8.8")));
        assertFalse(guard.isIntranet(InetAddress.getByName("1.2.3.4")));
    }

    @Test
    void nonHttpProtocolRejectedEvenWithIntranetAllowed() {
        assertThrows(ServiceException.class, () -> guard.check(new URI("file:///etc/passwd"), true));
        assertThrows(ServiceException.class, () -> guard.check(new URI("gopher://127.0.0.1:6379/_info"), true));
        assertThrows(ServiceException.class, () -> guard.check(new URI("ftp://127.0.0.1/file"), true));
    }

    @Test
    void intranetBlockedWithoutAllowFlag() throws Exception {
        assertThrows(ServiceException.class, () -> guard.check(new URI("http://127.0.0.1:6055/actuator/health"), false));
        assertThrows(ServiceException.class, () -> guard.check(new URI("http://192.168.1.10/api"), false));
    }

    @Test
    void intranetAllowedWithFlag() {
        assertDoesNotThrow(() -> guard.check(new URI("http://127.0.0.1:6055/actuator/health"), true));
    }

    @Test
    void publicHttpAlwaysAllowed() {
        assertDoesNotThrow(() -> guard.check(new URI("https://api.open-meteo.com/v1/forecast"), false));
    }
}
