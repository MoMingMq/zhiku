package org.zhiku.ai.app.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IP 白名单匹配器单测（CIDR 纯函数）
 *
 * @author zhiku
 */
class IpWhitelistMatcherTest {

    @Test
    void cidrMatchesWithinPrefix() {
        assertTrue(IpWhitelistMatcher.cidrMatch("192.168.4.20", "192.168.0.0/16"));
        assertTrue(IpWhitelistMatcher.cidrMatch("10.1.2.3", "10.1.0.0/12"));
        assertFalse(IpWhitelistMatcher.cidrMatch("192.169.1.1", "192.168.0.0/16"));
    }

    @Test
    void invalidInputsNeverMatch() {
        assertFalse(IpWhitelistMatcher.cidrMatch("not-ip", "192.168.0.0/16"));
        assertFalse(IpWhitelistMatcher.cidrMatch("192.168.1.1", "bad-cidr/xx"));
    }
}
