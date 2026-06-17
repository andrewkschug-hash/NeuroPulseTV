package com.grid.tv.data.network

import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps

/**
 * Tries the device DNS first, then Google DNS-over-HTTPS when resolution fails.
 * Many IPTV hostnames fail on Fire TV / ISP DNS but resolve via public DNS.
 */
object IptvDns : Dns {
    private val bootstrapClient = OkHttpClient.Builder()
        .dns(Dns.SYSTEM)
        .build()

    private val dnsOverHttps: Dns = DnsOverHttps.Builder()
        .client(bootstrapClient)
        .url("https://dns.google/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByName("8.8.8.8"),
            InetAddress.getByName("8.8.4.4")
        )
        .build()

    override fun lookup(hostname: String): List<InetAddress> {
        if (hostname.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))) {
            return listOf(InetAddress.getByName(hostname))
        }
        return try {
            Dns.SYSTEM.lookup(hostname)
        } catch (systemError: UnknownHostException) {
            try {
                dnsOverHttps.lookup(hostname)
            } catch (dohError: UnknownHostException) {
                systemError.addSuppressed(dohError)
                throw systemError
            }
        }
    }
}
