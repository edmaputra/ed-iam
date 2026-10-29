package io.github.edmaputra.iam.domain.policy;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Pure Java utility for matching IPv4 and IPv6 addresses against CIDR subnet notations or exact IP addresses.
 *
 * <p>Supports notations such as:
 * <ul>
 *   <li>{@code 192.168.1.0/24} (IPv4 subnet)</li>
 *   <li>{@code 10.0.0.0/8}</li>
 *   <li>{@code 127.0.0.1} (single IPv4)</li>
 *   <li>{@code 127.0.0.1/32}</li>
 *   <li>{@code ::1/128} (IPv6 localhost)</li>
 *   <li>{@code 2001:db8::/32} (IPv6 subnet)</li>
 * </ul>
 *
 * @author edmaputra
 * @since 0.14.0
 */
public final class IpCidrMatcher {

	private IpCidrMatcher() {
	}

	/**
	 * Checks if the given IP address falls within the specified CIDR block or matches the exact IP.
	 *
	 * @param clientIp the client IP address (IPv4 or IPv6)
	 * @param cidrPattern the CIDR subnet expression or exact IP address
	 * @return true if clientIp matches cidrPattern, false otherwise
	 */
	public static boolean matches(String clientIp, String cidrPattern) {
		if (clientIp == null || clientIp.isBlank() || cidrPattern == null || cidrPattern.isBlank()) {
			return false;
		}

		String cleanClientIp = clientIp.trim();
		String cleanCidr = cidrPattern.trim();

		try {
			InetAddress targetAddr = InetAddress.getByName(cleanClientIp);

			int slashIndex = cleanCidr.indexOf('/');
			if (slashIndex == -1) {
				InetAddress exactAddr = InetAddress.getByName(cleanCidr);
				return targetAddr.equals(exactAddr);
			}

			String networkIpStr = cleanCidr.substring(0, slashIndex).trim();
			int prefixLength = Integer.parseInt(cleanCidr.substring(slashIndex + 1).trim());

			InetAddress networkAddr = InetAddress.getByName(networkIpStr);

			byte[] targetBytes = targetAddr.getAddress();
			byte[] networkBytes = networkAddr.getAddress();

			if (targetBytes.length != networkBytes.length) {
				return false;
			}

			int maxPrefix = targetBytes.length * 8;
			if (prefixLength < 0 || prefixLength > maxPrefix) {
				return false;
			}

			for (int i = 0; i < targetBytes.length; i++) {
				int bitsToCheck = Math.min(8, Math.max(0, prefixLength - (i * 8)));
				if (bitsToCheck == 0) {
					break;
				}
				int mask = (0xFF << (8 - bitsToCheck)) & 0xFF;
				if ((targetBytes[i] & mask) != (networkBytes[i] & mask)) {
					return false;
				}
			}

			return true;
		} catch (UnknownHostException | NumberFormatException e) {
			return false;
		}
	}
}
