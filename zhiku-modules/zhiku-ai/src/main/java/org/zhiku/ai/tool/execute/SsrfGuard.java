package org.zhiku.ai.tool.execute;

import org.zhiku.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;

/**
 * SSRF 校验器：协议白名单 + 目标地址全量校验（防多 A 记录绕过）
 * <p>默认拦截环回/0.0.0.0/链路本地/私有段/ULA/组播与保留段；allowIntranet=true 时放行环回与私有段（协议白名单不豁免）。
 * <p>DNS 重绑定残留风险说明：校验与连接之间存在窗口期（JDK HttpClient 不支持自定义解析器）。
 * 攻击面已收窄——URL 模板 scheme+host 由管理员写死且禁止参数占位，模型仅能注入 path/query/body；
 * 如需彻底钉死，升级路径：引入 Apache HttpClient5 自定义 DnsResolver 直连已校验 IP（需过协议评审 + NOTICE 登记）。
 *
 * @author zhiku
 */
@Component
public class SsrfGuard {

    /**
     * 校验目标 URI，不通过抛 ServiceException（消息给模型与管理员都可读）
     */
    public void check(URI uri, boolean allowIntranet) {
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new ServiceException("目标协议不允许访问（仅支持 http/https）: " + scheme);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ServiceException("目标地址缺少主机名");
        }
        List<InetAddress> addresses;
        try {
            addresses = List.of(InetAddress.getAllByName(host));
        } catch (Exception e) {
            throw new ServiceException("目标地址解析失败: " + host);
        }
        for (InetAddress address : addresses) {
            if (isIntranet(address) && !allowIntranet) {
                throw new ServiceException("目标地址不允许访问（内网/保留地址）: " + host);
            }
        }
    }

    /**
     * 是否内网/保留地址（纯函数可单测）
     */
    public boolean isIntranet(InetAddress address) {
        return address.isLoopbackAddress()
            || address.isAnyLocalAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()
            || isUniqueLocalIpv6(address)
            || address.isMulticastAddress();
    }

    /**
     * IPv6 唯一本地地址（fc00::/7，含 fd00::8 前缀）：首字节高 7 位为 1111110
     */
    private boolean isUniqueLocalIpv6(InetAddress address) {
        if (!(address instanceof java.net.Inet6Address)) {
            return false;
        }
        return (address.getAddress()[0] & 0xFF & 0xFE) == 0xFC;
    }

    /**
     * 解析并返回已校验的目标地址（供连接期复用，DnsResolver 升级时替换）
     */
    public InetSocketAddress resolveChecked(URI uri, boolean allowIntranet) {
        check(uri, allowIntranet);
        int port = uri.getPort() < 0 ? (uri.getScheme().equalsIgnoreCase("https") ? 443 : 80) : uri.getPort();
        try {
            return new InetSocketAddress(uri.getHost(), port);
        } catch (Exception e) {
            throw new ServiceException("目标地址解析失败: " + uri.getHost());
        }
    }
}
