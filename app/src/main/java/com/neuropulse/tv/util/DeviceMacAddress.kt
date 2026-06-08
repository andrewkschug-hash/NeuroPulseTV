package com.neuropulse.tv.util

import java.net.NetworkInterface

object DeviceMacAddress {
    fun resolve(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') == false }
                ?.let { _ ->
                    NetworkInterface.getNetworkInterfaces().toList()
                        .firstOrNull { iface ->
                            iface.isUp && !iface.isLoopback &&
                                iface.hardwareAddress != null &&
                                iface.hardwareAddress!!.isNotEmpty()
                        }
                        ?.hardwareAddress
                        ?.joinToString(":") { byte -> "%02X".format(byte) }
                }
                ?: NetworkInterface.getNetworkInterfaces().toList()
                    .firstOrNull { it.isUp && !it.isLoopback && it.hardwareAddress?.isNotEmpty() == true }
                    ?.hardwareAddress
                    ?.joinToString(":") { byte -> "%02X".format(byte) }
        }.getOrNull()
    }
}
