package org.zhiku.ai.app.controller;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP 白名单匹配器（公开端点访问限制；纯静态可单测）
 * <p>白名单格式：单 IP 或 CIDR 逗号分隔，空=不限。客户端 IP 取 X-Forwarded-For 首跳（反代场景），无头取 remoteAddr。
 *
 * @author zhiku
 */
public final class IpWhitelistMatcher {

    /**
     * 判断客户端 IP 是否被白名单放行
     */
    public static boolean isAllowed(HttpServletRequest request, String whitelist) {
        if (whitelist == null || whitelist.isBlank()) {
            return true;
        }
        String clientIp = clientIp(request);
        for (String segment : whitelist.split("[,，]")) {
            String rule = segment.trim();
            if (rule.isEmpty()) {
                continue;
            }
            if (rule.contains("/")) {
                if (cidrMatch(clientIp, rule)) {
                    return true;
                }
            } else if (rule.equals(clientIp)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析客户端真实 IP（X-Forwarded-For 首跳）
     */
    public static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * IPv4 CIDR 匹配（纯函数）
     */
    static boolean cidrMatch(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            int prefix = Integer.parseInt(parts[1]);
            long cidrBits = ipv4ToLong(parts[0]) >>> (32 - prefix);
            long ipBits = ipv4ToLong(ip) >>> (32 - prefix);
            return cidrBits == ipBits;
        } catch (Exception e) {
            return false;
        }
    }

    private static long ipv4ToLong(String ip) {
        String[] octets = ip.split("\\.");
        if (octets.length != 4) {
            throw new IllegalArgumentException("not ipv4: " + ip);
        }
        long value = 0;
        for (String octet : octets) {
            int v = Integer.parseInt(octet);
            if (v < 0 || v > 255) {
                throw new IllegalArgumentException("bad octet: " + octet);
            }
            value = (value << 8) | v;
        }
        return value;
    }

    private IpWhitelistMatcher() {
    }
}
