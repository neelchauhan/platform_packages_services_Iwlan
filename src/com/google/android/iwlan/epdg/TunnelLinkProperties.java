// Copyright 2019 Google Inc. All Rights Reserved.

package com.google.android.iwlan.epdg;

import android.net.LinkAddress;

import com.google.auto.value.AutoValue;

import java.net.InetAddress;
import java.util.List;
import java.util.Optional;

@AutoValue
public abstract class TunnelLinkProperties {
    public abstract List<LinkAddress> internalAddresses();

    public abstract List<InetAddress> dnsAddresses();

    public abstract List<InetAddress> pcscfAddresses();

    public abstract String ifaceName();

    public abstract Optional<byte[]> sNssai();

    static Builder builder() {
        return new AutoValue_TunnelLinkProperties.Builder().setSNssai(Optional.empty());
    }

    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder setInternalAddresses(List<LinkAddress> internalAddresses);

        abstract Builder setDnsAddresses(List<InetAddress> dnsAddresses);

        abstract Builder setPcscfAddresses(List<InetAddress> pcscfAddresses);

        abstract Builder setIfaceName(String ifaceName);

        public Builder setSNssai(byte[] snssai) {
            return setSNssai(Optional.ofNullable(snssai));
        }

        abstract Builder setSNssai(Optional<byte[]> snssai);

        abstract TunnelLinkProperties build();
    }
}
