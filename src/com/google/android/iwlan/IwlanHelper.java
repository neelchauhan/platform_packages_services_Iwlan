// Copyright 2019 Google Inc. All Rights Reserved.

package com.google.android.iwlan;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.Network;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class IwlanHelper {

    private static final String TAG = IwlanHelper.class.getSimpleName();

    public static String getNai(Context context, int slotId, boolean addWifiMac) {
        StringBuilder naiBuilder = new StringBuilder();
        TelephonyManager tm =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        tm = tm.createForSubscriptionId(getSubId(context, slotId));

        SubscriptionInfo subInfo = getSubInfo(context, slotId);
        String mnc = subInfo.getMncString();
        mnc = (mnc.length() == 2) ? '0' + mnc : mnc;

        naiBuilder.append('0').append(tm.getSubscriberId()).append('@');

        if (addWifiMac) {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

            if (wifiManager != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                String wifiMacId =
                        (wifiInfo == null || wifiInfo.getBSSID() == null)
                                ? WifiInfo.DEFAULT_MAC_ADDRESS
                                : wifiInfo.getBSSID();
                wifiMacId = wifiMacId.replace(':', '-');
                naiBuilder.append(wifiMacId.toUpperCase());
                naiBuilder.append(':');
            } else {
                throw new IllegalStateException("WifiManager is null.");
            }
        }

        return naiBuilder
                .append("nai.epc.mnc")
                .append(mnc)
                .append(".mcc")
                .append(subInfo.getMccString())
                .append(".3gppnetwork.org")
                .toString();
    }

    public static int getSubId(Context context, int slotId) {
        return getSubInfo(context, slotId).getSubscriptionId();
    }

    private static SubscriptionInfo getSubInfo(Context context, int slotId) {
        SubscriptionManager sm =
                (SubscriptionManager)
                        context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        SubscriptionInfo info = sm.getActiveSubscriptionInfoForSimSlotIndex(slotId);

        if (info == null) {
            throw new IllegalStateException("Subscription info is null.");
        }

        return info;
    }

    public static List<InetAddress> getAddressesForNetwork(Network network, Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        List<InetAddress> gatewayList = new ArrayList<InetAddress>();
        for (LinkAddress laddr :
                connectivityManager.getLinkProperties(network).getLinkAddresses()) {
            InetAddress inetaddr = laddr.getAddress();
            // skip linklocal and loopback addresses
            if (!inetaddr.isLoopbackAddress() && !inetaddr.isLinkLocalAddress()) {
                gatewayList.add(inetaddr);
            }
        }

        return gatewayList;
    }

    public static boolean hasIpv6Address(List<InetAddress> localAddresses) {
        for (InetAddress address : localAddresses) {
            if (address instanceof Inet6Address) {
                return true;
            }
        }

        return false;
    }

    public static boolean hasIpv4Address(List<InetAddress> localAddresses) {
        for (InetAddress address : localAddresses) {
            if (address instanceof Inet4Address) {
                return true;
            }
        }

        return false;
    }

    public static InetAddress getIpv4Address(List<InetAddress> localAddresses) {
        for (InetAddress address : localAddresses) {
            if (address instanceof Inet4Address) {
                return address;
            }
        }

        throw new IllegalStateException("Local address should not be null.");
    }

    public static InetAddress getIpv6Address(List<InetAddress> localAddresses) {
        for (InetAddress address : localAddresses) {
            if (address instanceof Inet6Address) {
                return address;
            }
        }

        throw new IllegalStateException("Local address should not be null.");
    }

    public static <T> T getConfig(String key, Context context, int slotId) {
        CarrierConfigManager carrierConfigManager =
                (CarrierConfigManager) context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (carrierConfigManager == null) {
            throw new IllegalStateException("Carrier config manager is null.");
        }

        PersistableBundle bundle =
                carrierConfigManager.getConfigForSubId(getSubId(context, slotId));

        if (bundle == null || bundle.get(key) == null) {
            bundle = IwlanConfigs.getDefaultConfig();
        }

        return (T) bundle.get(key);
    }

    public static <T> T getDefaultConfig(String key) {
        PersistableBundle bundle = IwlanConfigs.getDefaultConfig();
        return (T) bundle.get(key);
    }
}
