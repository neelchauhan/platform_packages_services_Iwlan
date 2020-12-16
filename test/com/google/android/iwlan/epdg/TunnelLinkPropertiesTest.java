// Copyright 2019-2020 Google Inc. All Rights Reserved.

package com.google.android.iwlan.epdg;

import android.net.LinkAddress;

import java.net.InetAddress;
import java.util.LinkedList;
import java.util.List;

public class TunnelLinkPropertiesTest {

    private static final String IP_ADDRESS = "192.0.2.1";
    private static final String DNS_ADDRESS = "8.8.8.8";
    private static final String PSCF_ADDRESS = "10.159.204.230";
    private static final String INTERFACE_NAME = "ipsec6";
    private static final byte[] SNSSAI = new byte[] {1, 2, 3};

    public static TunnelLinkProperties createTestTunnelLinkProperties() throws Exception {
        List<LinkAddress> mInternalAddressList = new LinkedList<>();
        List<InetAddress> mDNSAddressList = new LinkedList<>();
        List<InetAddress> mPCSFAddressList = new LinkedList<>();

        InetAddress mDNSAddress = InetAddress.getByName(DNS_ADDRESS);
        InetAddress mPCSFAddress = InetAddress.getByName(PSCF_ADDRESS);

        mInternalAddressList.add(new LinkAddress(InetAddress.getByName(IP_ADDRESS), 3));
        mDNSAddressList.add(mDNSAddress);
        mPCSFAddressList.add(mPCSFAddress);

        return TunnelLinkProperties.builder()
                .setInternalAddresses(mInternalAddressList)
                .setDnsAddresses(mDNSAddressList)
                .setPcscfAddresses(mPCSFAddressList)
                .setIfaceName(INTERFACE_NAME)
                .setSNssai(SNSSAI)
                .build();
    }
}
