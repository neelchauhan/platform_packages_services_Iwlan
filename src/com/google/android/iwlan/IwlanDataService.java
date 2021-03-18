/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.iwlan;

import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.GuardedBy;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.DataFailCause;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataCallResponse;
import android.telephony.data.DataProfile;
import android.telephony.data.DataService;
import android.telephony.data.DataServiceCallback;
import android.telephony.data.NetworkSliceInfo;
import android.telephony.data.TrafficDescriptor;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import com.google.android.iwlan.epdg.EpdgSelector;
import com.google.android.iwlan.epdg.EpdgTunnelManager;
import com.google.android.iwlan.epdg.TunnelLinkProperties;
import com.google.android.iwlan.epdg.TunnelSetupRequest;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IwlanDataService extends DataService {

    private static final String TAG = IwlanDataService.class.getSimpleName();
    private static Context mContext;
    private IwlanNetworkMonitorCallback mNetworkMonitorCallback;
    private HandlerThread mNetworkCallbackHandlerThread;
    private static boolean sNetworkConnected = false;
    private static Network sNetwork = null;
    // TODO: Change this to a hashmap as there is only one provider per slot
    private static List<IwlanDataServiceProvider> sIwlanDataServiceProviderList =
            new ArrayList<IwlanDataServiceProvider>();

    @VisibleForTesting
    enum Transport {
        UNSPECIFIED_NETWORK,
        MOBILE,
        WIFI;
    }

    private static Transport sDefaultDataTransport = Transport.UNSPECIFIED_NETWORK;

    // TODO: see if network monitor callback impl can be shared between dataservice and
    // networkservice
    static class IwlanNetworkMonitorCallback extends ConnectivityManager.NetworkCallback {

        /** Called when the framework connects and has declared a new network ready for use. */
        @Override
        public void onAvailable(Network network) {
            Log.d(TAG, "onAvailable: " + network);
        }

        /**
         * Called when the network is about to be lost, typically because there are no outstanding
         * requests left for it. This may be paired with a {@link NetworkCallback#onAvailable} call
         * with the new replacement network for graceful handover. This method is not guaranteed to
         * be called before {@link NetworkCallback#onLost} is called, for example in case a network
         * is suddenly disconnected.
         */
        @Override
        public void onLosing(Network network, int maxMsToLive) {
            Log.d(TAG, "onLosing: maxMsToLive: " + maxMsToLive + " network: " + network);
        }

        /**
         * Called when a network disconnects or otherwise no longer satisfies this request or
         * callback.
         */
        @Override
        public void onLost(Network network) {
            Log.d(TAG, "onLost: " + network);
            IwlanDataService.setNetworkConnected(false, network, Transport.UNSPECIFIED_NETWORK);
        }

        /** Called when the network corresponding to this request changes {@link LinkProperties}. */
        @Override
        public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
            Log.d(TAG, "onLinkPropertiesChanged: " + linkProperties);
        }

        /** Called when access to the specified network is blocked or unblocked. */
        @Override
        public void onBlockedStatusChanged(Network network, boolean blocked) {
            // TODO: check if we need to handle this
            Log.d(TAG, "onBlockedStatusChanged: " + network + " BLOCKED:" + blocked);
        }

        @Override
        public void onCapabilitiesChanged(
                Network network, NetworkCapabilities networkCapabilities) {
            // onCapabilitiesChanged is guaranteed to be called immediately after onAvailable per
            // API
            Log.d(TAG, "onCapabilitiesChanged: " + network + " " + networkCapabilities);
            if (networkCapabilities != null) {
                if (networkCapabilities.hasTransport(TRANSPORT_CELLULAR)) {
                    Log.d(TAG, "Network " + network + " connected using transport MOBILE");
                    IwlanDataService.setNetworkConnected(true, network, Transport.MOBILE);
                } else if (networkCapabilities.hasTransport(TRANSPORT_WIFI)) {
                    Log.d(TAG, "Network " + network + " connected using transport WIFI");
                    IwlanDataService.setNetworkConnected(true, network, Transport.WIFI);
                } else {
                    Log.w(TAG, "Network does not have cellular or wifi capability");
                }
            }
        }
    }

    @VisibleForTesting
    class IwlanDataServiceProvider extends DataService.DataServiceProvider {

        private static final int CALLBACK_TYPE_SETUP_DATACALL_COMPLETE = 1;
        private static final int CALLBACK_TYPE_DEACTIVATE_DATACALL_COMPLETE = 2;
        private static final int CALLBACK_TYPE_GET_DATACALL_LIST_COMPLETE = 3;
        private final String SUB_TAG;
        private final IwlanDataService mIwlanDataService;
        private final IwlanTunnelCallback mIwlanTunnelCallback;
        private HandlerThread mHandlerThread;
        @VisibleForTesting Handler mHandler;
        private boolean mWfcEnabled = false;
        private boolean mCarrierConfigReady = false;
        private EpdgSelector mEpdgSelector;

        // apn to TunnelState
        // Lock this at public entry and exit points if:
        // 1) the function changes mTunnelStateForApn
        // 2) Makes decisions based on contents of mTunnelStateForApn
        @GuardedBy("mTunnelStateForApn")
        private Map<String, TunnelState> mTunnelStateForApn = new ConcurrentHashMap<>();

        // Holds the state of a tunnel (for an APN)
        @VisibleForTesting
        class TunnelState {

            // this should be ideally be based on path MTU discovery. 1280 is the minimum packet
            // size ipv6 routers have to handle so setting it to 1280 is the safest approach.
            // ideally it should be 1280 - tunnelling overhead ?
            private static final int LINK_MTU =
                    1280; // TODO: need to substract tunnelling overhead?
            static final int TUNNEL_DOWN = 1;
            static final int TUNNEL_IN_BRINGUP = 2;
            static final int TUNNEL_UP = 3;
            static final int TUNNEL_IN_BRINGDOWN = 4;
            private DataServiceCallback dataServiceCallback;
            private int mState;
            private TunnelLinkProperties mTunnelLinkProperties;
            private boolean mIsHandover;

            public int getProtocolType() {
                return mProtocolType;
            }

            public int getLinkMtu() {
                return LINK_MTU; // TODO: need to substract tunnelling overhead
            }

            public void setProtocolType(int protocolType) {
                mProtocolType = protocolType;
            }

            private int mProtocolType; // from DataProfile

            public TunnelLinkProperties getTunnelLinkProperties() {
                return mTunnelLinkProperties;
            }

            public void setTunnelLinkProperties(TunnelLinkProperties tunnelLinkProperties) {
                mTunnelLinkProperties = tunnelLinkProperties;
            }

            public DataServiceCallback getDataServiceCallback() {
                return dataServiceCallback;
            }

            public void setDataServiceCallback(DataServiceCallback dataServiceCallback) {
                this.dataServiceCallback = dataServiceCallback;
            }

            public TunnelState(DataServiceCallback callback) {
                dataServiceCallback = callback;
                mState = TUNNEL_DOWN;
            }

            public int getState() {
                return mState;
            }

            /** @param state (TunnelState.TUNNEL_DOWN|TUNNEL_UP|TUNNEL_DOWN) */
            public void setState(int state) {
                mState = state;
            }

            public void setIsHandover(boolean isHandover) {
                mIsHandover = isHandover;
            }

            public boolean getIsHandover() {
                return mIsHandover;
            }
        }

        @VisibleForTesting
        class IwlanTunnelCallback implements EpdgTunnelManager.TunnelCallback {

            DataServiceProvider mDataServiceProvider;

            public IwlanTunnelCallback(DataServiceProvider dsp) {
                mDataServiceProvider = dsp;
            }

            // TODO: full implementation

            public void onOpened(String apnName, TunnelLinkProperties linkProperties) {
                Log.d(
                        SUB_TAG,
                        "Tunnel opened!. APN: " + apnName + "linkproperties: " + linkProperties);
                synchronized (mTunnelStateForApn) {
                    TunnelState tunnelState = mTunnelStateForApn.get(apnName);
                    // tunnelstate should not be null, design violation.
                    // if its null, we should crash and debug.
                    tunnelState.setTunnelLinkProperties(linkProperties);
                    tunnelState.setState(TunnelState.TUNNEL_UP);

                    deliverCallback(
                            CALLBACK_TYPE_SETUP_DATACALL_COMPLETE,
                            DataServiceCallback.RESULT_SUCCESS,
                            tunnelState.getDataServiceCallback(),
                            apnTunnelStateToDataCallResponse(apnName));
                }
            }

            public void onClosed(String apnName, IwlanError error) {
                Log.d(SUB_TAG, "Tunnel closed!. APN: " + apnName + " Error: " + error);
                // this is called, when a tunnel that is up, is closed.
                // the expectation is error==NO_ERROR for user initiated/normal close.
                synchronized (mTunnelStateForApn) {
                    TunnelState tunnelState = mTunnelStateForApn.get(apnName);
                    mTunnelStateForApn.remove(apnName);

                    if (tunnelState.getState() == TunnelState.TUNNEL_IN_BRINGUP) {
                        DataCallResponse.Builder respBuilder = new DataCallResponse.Builder();
                        respBuilder
                                .setId(apnName.hashCode())
                                .setProtocolType(tunnelState.getProtocolType());

                        if (tunnelState.getIsHandover()) {
                            respBuilder.setHandoverFailureMode(
                                    DataCallResponse
                                            .HANDOVER_FAILURE_MODE_NO_FALLBACK_RETRY_HANDOVER);
                        } else {
                            respBuilder.setHandoverFailureMode(
                                    DataCallResponse
                                            .HANDOVER_FAILURE_MODE_NO_FALLBACK_RETRY_SETUP_NORMAL);
                        }

                        respBuilder.setCause(
                                ErrorPolicyManager.getInstance(mContext, getSlotIndex())
                                        .getDataFailCause(apnName));
                        respBuilder.setRetryDurationMillis(
                                ErrorPolicyManager.getInstance(mContext, getSlotIndex())
                                        .getCurrentRetryTimeMs(apnName));

                        deliverCallback(
                                CALLBACK_TYPE_SETUP_DATACALL_COMPLETE,
                                DataServiceCallback.RESULT_SUCCESS,
                                tunnelState.getDataServiceCallback(),
                                respBuilder.build());
                        return;
                    }

                    // iwlan service triggered teardown
                    if (tunnelState.getState() == TunnelState.TUNNEL_IN_BRINGDOWN) {

                        // IO exception happens when IKE library fails to retransmit requests.
                        // This can happen for multiple reasons:
                        // 1. Network disconnection due to wifi off.
                        // 2. Epdg server does not respond.
                        // 3. Socket send/receive fails.
                        // Ignore this during tunnel bring down.
                        if (error.getErrorType() != IwlanError.NO_ERROR
                                && error.getErrorType() != IwlanError.IKE_INTERNAL_IO_EXCEPTION) {
                            throw new AssertionError(
                                    "Unexpected error during tunnel bring down: " + error);
                        }

                        deliverCallback(
                                CALLBACK_TYPE_DEACTIVATE_DATACALL_COMPLETE,
                                DataServiceCallback.RESULT_SUCCESS,
                                tunnelState.getDataServiceCallback(),
                                null);

                        return;
                    }

                    // just update list of data calls. No way to send error up
                    notifyDataCallListChanged(getCallList());
                }
            }
        }

        private final class DSPHandler extends Handler {
            private final String TAG =
                    IwlanDataService.class.getSimpleName()
                            + DSPHandler.class.getSimpleName()
                            + getSlotIndex();

            @Override
            public void handleMessage(Message msg) {
                Log.d(TAG, "msg.what = " + msg.what);
                switch (msg.what) {
                    case IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT:
                        Log.d(TAG, "On CARRIER_CONFIG_CHANGED_EVENT");
                        mCarrierConfigReady = true;
                        dnsPrefetchCheck();
                        break;
                    case IwlanEventListener.CARRIER_CONFIG_UNKNOWN_CARRIER_EVENT:
                        Log.d(TAG, "On CARRIER_CONFIG_UNKNOWN_CARRIER_EVENT");
                        mCarrierConfigReady = false;
                        break;
                    case IwlanEventListener.WIFI_CALLING_ENABLE_EVENT:
                        Log.d(TAG, "On WIFI_CALLING_ENABLE_EVENT");
                        mWfcEnabled = true;
                        dnsPrefetchCheck();
                        break;
                    case IwlanEventListener.WIFI_CALLING_DISABLE_EVENT:
                        Log.d(TAG, "On WIFI_CALLING_DISABLE_EVENT");
                        mWfcEnabled = false;
                        break;
                    default:
                        Log.d(TAG, "Unknown message received!");
                        break;
                }
            }

            DSPHandler(Looper looper) {
                super(looper);
            }
        }

        Looper getLooper() {
            mHandlerThread = new HandlerThread("DSPHandlerThread");
            mHandlerThread.start();
            return mHandlerThread.getLooper();
        }

        /**
         * Constructor
         *
         * @param slotIndex SIM slot index the data service provider associated with.
         */
        public IwlanDataServiceProvider(int slotIndex, IwlanDataService iwlanDataService) {
            super(slotIndex);
            SUB_TAG = TAG + "[" + slotIndex + "]";

            // TODO:
            // get reference carrier config for this sub
            // get reference to resolver
            mIwlanDataService = iwlanDataService;
            mIwlanTunnelCallback = new IwlanTunnelCallback(this);
            mEpdgSelector = EpdgSelector.getSelectorInstance(mContext, slotIndex);

            // Register IwlanEventListener
            initHandler();
            List<Integer> events = new ArrayList<Integer>();
            events.add(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT);
            events.add(IwlanEventListener.CARRIER_CONFIG_UNKNOWN_CARRIER_EVENT);
            events.add(IwlanEventListener.WIFI_CALLING_ENABLE_EVENT);
            events.add(IwlanEventListener.WIFI_CALLING_DISABLE_EVENT);
            IwlanEventListener.getInstance(mContext, slotIndex).addEventListener(events, mHandler);
        }

        void initHandler() {
            mHandler = new DSPHandler(getLooper());
        }

        @VisibleForTesting
        EpdgTunnelManager getTunnelManager() {
            return EpdgTunnelManager.getInstance(mContext, getSlotIndex());
        }

        // creates a DataCallResponse for an apn irrespective of state
        private DataCallResponse apnTunnelStateToDataCallResponse(String apn) {
            TunnelState tunnelState = mTunnelStateForApn.get(apn);
            if (tunnelState == null) {
                return null;
            }

            DataCallResponse.Builder responseBuilder = new DataCallResponse.Builder();
            responseBuilder
                    .setId(apn.hashCode())
                    .setProtocolType(tunnelState.getProtocolType())
                    .setCause(DataFailCause.NONE);

            if (tunnelState.getState() != TunnelState.TUNNEL_UP) {
                // no need to fill additional params
                return responseBuilder.setLinkStatus(DataCallResponse.LINK_STATUS_UNKNOWN).build();
            }

            // fill wildcard address for gatewayList (used by DataConnection to add routes)
            List<InetAddress> gatewayList = new ArrayList<>();
            List<LinkAddress> linkAddrList =
                    tunnelState.getTunnelLinkProperties().internalAddresses();
            if (linkAddrList.stream().anyMatch(t -> t.isIpv4())) {
                try {
                    gatewayList.add(Inet4Address.getByName("0.0.0.0"));
                } catch (UnknownHostException e) {
                    // should never happen for static string 0.0.0.0
                }
            }
            if (linkAddrList.stream().anyMatch(t -> t.isIpv6())) {
                try {
                    gatewayList.add(Inet6Address.getByName("::"));
                } catch (UnknownHostException e) {
                    // should never happen for static string ::
                }
            }

            if (tunnelState.getTunnelLinkProperties().sliceInfo().isPresent()) {
                responseBuilder.setSliceInfo(
                        tunnelState.getTunnelLinkProperties().sliceInfo().get());
            }

            return responseBuilder
                    .setAddresses(linkAddrList)
                    .setDnsAddresses(tunnelState.getTunnelLinkProperties().dnsAddresses())
                    .setPcscfAddresses(tunnelState.getTunnelLinkProperties().pcscfAddresses())
                    .setInterfaceName(tunnelState.getTunnelLinkProperties().ifaceName())
                    .setGatewayAddresses(gatewayList)
                    .setLinkStatus(DataCallResponse.LINK_STATUS_ACTIVE)
                    .setMtu(tunnelState.getLinkMtu())
                    .setMtuV4(tunnelState.getLinkMtu())
                    .setMtuV6(tunnelState.getLinkMtu())
                    .build(); // underlying n/w is same
        }

        private List<DataCallResponse> getCallList() {
            List<DataCallResponse> dcList = new ArrayList<>();
            for (String key : mTunnelStateForApn.keySet()) {
                DataCallResponse dcRsp = apnTunnelStateToDataCallResponse(key);
                if (dcRsp != null) {
                    Log.d(SUB_TAG, "Apn: " + key + "Link state: " + dcRsp.getLinkStatus());
                    dcList.add(dcRsp);
                }
            }
            return dcList;
        }

        private void deliverCallback(
                int callbackType, int result, DataServiceCallback callback, DataCallResponse rsp) {
            if (callback == null) {
                Log.d(SUB_TAG, "deliverCallback: callback is null.  callbackType:" + callbackType);
                return;
            }
            Log.d(
                    SUB_TAG,
                    "Delivering callbackType:"
                            + callbackType
                            + " result:"
                            + result
                            + " rsp:"
                            + rsp);
            switch (callbackType) {
                case CALLBACK_TYPE_DEACTIVATE_DATACALL_COMPLETE:
                    callback.onDeactivateDataCallComplete(result);
                    // always update current datacalllist
                    notifyDataCallListChanged(getCallList());
                    break;

                case CALLBACK_TYPE_SETUP_DATACALL_COMPLETE:
                    if (result == DataServiceCallback.RESULT_SUCCESS && rsp == null) {
                        Log.d(SUB_TAG, "Warning: null rsp for success case");
                    }
                    callback.onSetupDataCallComplete(result, rsp);
                    // always update current datacalllist
                    notifyDataCallListChanged(getCallList());
                    break;

                case CALLBACK_TYPE_GET_DATACALL_LIST_COMPLETE:
                    callback.onRequestDataCallListComplete(result, getCallList());
                    // TODO: add code for the rest of the cases
            }
        }

        /**
         * Setup a data connection.
         *
         * @param accessNetworkType Access network type that the data call will be established on.
         *     Must be one of {@link android.telephony.AccessNetworkConstants.AccessNetworkType}.
         * @param dataProfile Data profile used for data call setup. See {@link DataProfile}
         * @param isRoaming True if the device is data roaming.
         * @param allowRoaming True if data roaming is allowed by the user.
         * @param reason The reason for data setup. Must be {@link #REQUEST_REASON_NORMAL} or {@link
         *     #REQUEST_REASON_HANDOVER}.
         * @param linkProperties If {@code reason} is {@link #REQUEST_REASON_HANDOVER}, this is the
         *     link properties of the existing data connection, otherwise null.
         * @param pduSessionId The pdu session id to be used for this data call. The standard range
         *     of values are 1-15 while 0 means no pdu session id was attached to this call.
         *     Reference: 3GPP TS 24.007 section 11.2.3.1b.
         * @param sliceInfo The slice info related to this data call.
         * @param trafficDescriptor TrafficDescriptor for which data connection needs to be
         *     established. It is used for URSP traffic matching as described in 3GPP TS 24.526
         *     Section 4.2.2. It includes an optional DNN which, if present, must be used for
         *     traffic matching; it does not specify the end point to be used for the data call.
         * @param matchAllRuleAllowed Indicates if using default match-all URSP rule for this
         *     request is allowed. If false, this request must not use the match-all URSP rule and
         *     if a non-match-all rule is not found (or if URSP rules are not available) then {@link
         *     DataCallResponse#getCause()} is {@link
         *     android.telephony.DataFailCause#MATCH_ALL_RULE_NOT_ALLOWED}. This is needed as some
         *     requests need to have a hard failure if the intention cannot be met, for example, a
         *     zero-rating slice.
         * @param callback The result callback for this request.
         */
        @Override
        public void setupDataCall(
                int accessNetworkType,
                @NonNull DataProfile dataProfile,
                boolean isRoaming,
                boolean allowRoaming,
                int reason,
                @Nullable LinkProperties linkProperties,
                @IntRange(from = 0, to = 15) int pduSessionId,
                @Nullable NetworkSliceInfo sliceInfo,
                @Nullable TrafficDescriptor trafficDescriptor,
                boolean matchAllRuleAllowed,
                @NonNull DataServiceCallback callback) {

            Log.d(
                    SUB_TAG,
                    "Setup data call with network: "
                            + accessNetworkType
                            + ", DataProfile: "
                            + dataProfile
                            + ", isRoaming:"
                            + isRoaming
                            + ", allowRoaming: "
                            + allowRoaming
                            + ", reason: "
                            + reason
                            + ", linkProperties: "
                            + linkProperties
                            + ", pduSessionId: "
                            + pduSessionId);

            // Framework will never call bringup on the same APN back 2 back.
            // but add a safety check
            if ((accessNetworkType != AccessNetworkType.IWLAN)
                    || (dataProfile == null)
                    || (linkProperties == null && reason == DataService.REQUEST_REASON_HANDOVER)) {

                deliverCallback(
                        CALLBACK_TYPE_SETUP_DATACALL_COMPLETE,
                        DataServiceCallback.RESULT_ERROR_INVALID_ARG,
                        callback,
                        null);
                return;
            }

            synchronized (mTunnelStateForApn) {
                boolean isDDS = IwlanHelper.isDefaultDataSlot(mContext, getSlotIndex());
                boolean isCSTEnabled =
                        IwlanHelper.isCrossSimCallingEnabled(mContext, getSlotIndex());
                boolean networkConnected = isNetworkConnected(isDDS, isCSTEnabled);
                Log.d(
                        SUB_TAG,
                        "isDds: "
                                + isDDS
                                + ", isCstEnabled: "
                                + isCSTEnabled
                                + ", transport: "
                                + sDefaultDataTransport);

                if (networkConnected == false
                        || mTunnelStateForApn.get(dataProfile.getApn()) != null) {
                    deliverCallback(
                            CALLBACK_TYPE_SETUP_DATACALL_COMPLETE,
                            DataServiceCallback.RESULT_ERROR_ILLEGAL_STATE,
                            callback,
                            null);
                    return;
                }

                TunnelSetupRequest.Builder tunnelReqBuilder =
                        TunnelSetupRequest.builder()
                                .setApnName(dataProfile.getApn())
                                .setNetwork(sNetwork)
                                .setIsRoaming(isRoaming)
                                .setPduSessionId(pduSessionId)
                                .setApnIpProtocol(
                                        isRoaming
                                                ? dataProfile.getRoamingProtocolType()
                                                : dataProfile.getProtocolType());

                if (reason == DataService.REQUEST_REASON_HANDOVER) {
                    // for now assume that, at max,  only one address of eachtype (v4/v6).
                    // TODO: Check if multiple ips can be sent in ike tunnel setup
                    for (LinkAddress lAddr : linkProperties.getLinkAddresses()) {
                        if (lAddr.isIpv4()) {
                            tunnelReqBuilder.setSrcIpv4Address(lAddr.getAddress());
                        } else if (lAddr.isIpv6()) {
                            tunnelReqBuilder.setSrcIpv6Address(lAddr.getAddress());
                            tunnelReqBuilder.setSrcIpv6AddressPrefixLength(lAddr.getPrefixLength());
                        }
                    }
                }

                int apnTypeBitmask = dataProfile.getSupportedApnTypesBitmask();
                boolean isIMS = (apnTypeBitmask & ApnSetting.TYPE_IMS) == ApnSetting.TYPE_IMS;
                boolean isEmergency =
                        (apnTypeBitmask & ApnSetting.TYPE_EMERGENCY) == ApnSetting.TYPE_EMERGENCY;
                tunnelReqBuilder.setRequestPcscf(isIMS || isEmergency);
                tunnelReqBuilder.setIsEmergency(isEmergency);

                setTunnelState(
                        dataProfile,
                        callback,
                        TunnelState.TUNNEL_IN_BRINGUP,
                        null,
                        (reason == DataService.REQUEST_REASON_HANDOVER));

                boolean result =
                        getTunnelManager()
                                .bringUpTunnel(tunnelReqBuilder.build(), getIwlanTunnelCallback());
                Log.d(SUB_TAG, "bringup Tunnel with result:" + result);
                if (!result) {
                    deliverCallback(
                            CALLBACK_TYPE_SETUP_DATACALL_COMPLETE,
                            DataServiceCallback.RESULT_ERROR_INVALID_ARG,
                            callback,
                            null);
                    return;
                }
            }
        }

        /**
         * Deactivate a data connection. The data service provider must implement this method to
         * support data connection tear down. When completed or error, the service must invoke the
         * provided callback to notify the platform.
         *
         * @param cid Call id returned in the callback of {@link
         *     DataServiceProvider#setupDataCall(int, DataProfile, boolean, boolean, int,
         *     LinkProperties, DataServiceCallback)}.
         * @param reason The reason for data deactivation. Must be {@link #REQUEST_REASON_NORMAL},
         *     {@link #REQUEST_REASON_SHUTDOWN} or {@link #REQUEST_REASON_HANDOVER}.
         * @param callback The result callback for this request. Null if the client does not care
         */
        @Override
        public void deactivateDataCall(int cid, int reason, DataServiceCallback callback) {
            Log.d(
                    SUB_TAG,
                    "Deactivate data call "
                            + " reason: "
                            + reason
                            + " cid: "
                            + cid
                            + "callback: "
                            + callback);

            synchronized (mTunnelStateForApn) {
                for (String apnName : mTunnelStateForApn.keySet()) {
                    if (apnName.hashCode() == cid) {
                        /*
                        No need to check state since dataconnection in framework serializes
                        setup and deactivate calls using callId/cid.
                        */
                        mTunnelStateForApn.get(apnName).setState(TunnelState.TUNNEL_IN_BRINGDOWN);
                        mTunnelStateForApn.get(apnName).setDataServiceCallback(callback);
                        boolean isConnected =
                                isNetworkConnected(
                                        IwlanHelper.isDefaultDataSlot(mContext, getSlotIndex()),
                                        IwlanHelper.isCrossSimCallingEnabled(
                                                mContext, getSlotIndex()));
                        getTunnelManager().closeTunnel(apnName, !isConnected);
                        return;
                    }
                }

                deliverCallback(
                        CALLBACK_TYPE_DEACTIVATE_DATACALL_COMPLETE,
                        DataServiceCallback.RESULT_ERROR_INVALID_ARG,
                        callback,
                        null);
            }
        }

        public void forceCloseTunnelsInDeactivatingState() {
            synchronized (mTunnelStateForApn) {
                for (Map.Entry<String, TunnelState> entry : mTunnelStateForApn.entrySet()) {
                    TunnelState tunnelState = entry.getValue();
                    if (tunnelState.getState() == TunnelState.TUNNEL_IN_BRINGDOWN) {
                        getTunnelManager().closeTunnel(entry.getKey(), true);
                    }
                }
            }
        }

        void forceCloseTunnels() {
            synchronized (mTunnelStateForApn) {
                for (Map.Entry<String, TunnelState> entry : mTunnelStateForApn.entrySet()) {
                    getTunnelManager().closeTunnel(entry.getKey(), true);
                }
            }
        }

        /**
         * Get the active data call list.
         *
         * @param callback The result callback for this request.
         */
        @Override
        public void requestDataCallList(DataServiceCallback callback) {
            deliverCallback(
                    CALLBACK_TYPE_GET_DATACALL_LIST_COMPLETE,
                    DataServiceCallback.RESULT_SUCCESS,
                    callback,
                    null);
        }

        @VisibleForTesting
        void setTunnelState(
                DataProfile dataProfile,
                DataServiceCallback callback,
                int tunnelStatus,
                TunnelLinkProperties linkProperties,
                boolean isHandover) {
            TunnelState tunnelState = new TunnelState(callback);
            tunnelState.setState(tunnelStatus);
            tunnelState.setProtocolType(dataProfile.getProtocolType());
            tunnelState.setTunnelLinkProperties(linkProperties);
            tunnelState.setIsHandover(isHandover);
            mTunnelStateForApn.put(dataProfile.getApn(), tunnelState);
        }

        @VisibleForTesting
        public IwlanTunnelCallback getIwlanTunnelCallback() {
            return mIwlanTunnelCallback;
        }

        private void dnsPrefetchCheck() {
            boolean networkConnected =
                    mIwlanDataService.isNetworkConnected(
                            IwlanHelper.isDefaultDataSlot(mContext, getSlotIndex()),
                            IwlanHelper.isCrossSimCallingEnabled(mContext, getSlotIndex()));
            /* Check if we need to do prefecting */
            synchronized (mTunnelStateForApn) {
                if (networkConnected == true
                        && mCarrierConfigReady == true
                        && mWfcEnabled == true
                        && mTunnelStateForApn.isEmpty()) {

                    // Get roaming status
                    TelephonyManager telephonyManager =
                            mContext.getSystemService(TelephonyManager.class);
                    telephonyManager =
                            telephonyManager.createForSubscriptionId(
                                    IwlanHelper.getSubId(mContext, getSlotIndex()));
                    boolean isRoaming = telephonyManager.isNetworkRoaming();
                    Log.d(TAG, "Trigger EPDG prefetch. Roaming=" + isRoaming);

                    prefetchEpdgServerList(mIwlanDataService.sNetwork, isRoaming);
                }
            }
        }

        private void prefetchEpdgServerList(Network network, boolean isRoaming) {
            mEpdgSelector.getValidatedServerList(
                    EpdgSelector.PROTO_FILTER_IPV4V6, isRoaming, false, network, null);
            mEpdgSelector.getValidatedServerList(
                    EpdgSelector.PROTO_FILTER_IPV4V6, isRoaming, true, network, null);
        }

        /**
         * Called when the instance of data service is destroyed (e.g. got unbind or binder died) or
         * when the data service provider is removed.
         */
        @Override
        public void close() {
            // TODO: call epdgtunnelmanager.releaseInstance or equivalent
            mIwlanDataService.removeDataServiceProvider(this);
            IwlanEventListener.getInstance(mContext, getSlotIndex()).removeEventListener(mHandler);
            mHandlerThread.quit();
        }
    }

    @VisibleForTesting
    static boolean isNetworkConnected(boolean isDds, boolean isCstEnabled) {
        if (!isDds && isCstEnabled) {
            // Only Non-DDS sub with CST enabled, can use any transport.
            return sNetworkConnected;
        } else {
            // For all other cases, only wifi transport can be used.
            return ((sDefaultDataTransport == Transport.WIFI) && sNetworkConnected);
        }
    }

    @VisibleForTesting
    /* Note: this api should have valid transport if networkConnected==true */
    static void setNetworkConnected(
            boolean networkConnected, Network network, Transport transport) {
        sNetworkConnected = networkConnected;
        sNetwork = network;
        if (networkConnected) {
            if (transport == Transport.UNSPECIFIED_NETWORK) {
                Log.e(TAG, "setNetworkConnected: Network connected but transport unspecified");
                return;
            }
            if (transport != sDefaultDataTransport) {
                Log.d(
                        TAG,
                        "Transport was changed from "
                                + sDefaultDataTransport.name()
                                + " to "
                                + transport.name());
                // Perform forceClose when doing handover between iWLAN and iDDS.
                for (IwlanDataServiceProvider dp : sIwlanDataServiceProviderList) {
                    dp.forceCloseTunnels();
                }
            }
        }
        sDefaultDataTransport = transport;

        if (!networkConnected) {
            for (IwlanDataServiceProvider dp : sIwlanDataServiceProviderList) {
                //once network is disconnect, even NAT KA offload fails
                //so we should force close all tunnels
                dp.forceCloseTunnels();
            }
        } else {
            if (transport == Transport.WIFI) {
                IwlanEventListener.onWifiConnected(mContext);
            }
            for (IwlanDataServiceProvider dp : sIwlanDataServiceProviderList) {
                dp.dnsPrefetchCheck();
            }
        }
    }

    /**
     * Get the DataServiceProvider associated with the slotId
     *
     * @param slotId slot index
     * @return DataService.DataServiceProvider associated with the slot
     */
    public static DataService.DataServiceProvider getDataServiceProvider(int slotId) {
        DataServiceProvider ret = null;
        if (!sIwlanDataServiceProviderList.isEmpty()) {
            for (IwlanDataServiceProvider provider : sIwlanDataServiceProviderList) {
                if (provider.getSlotIndex() == slotId) {
                    ret = provider;
                    break;
                }
            }
        }
        return ret;
    }

    public static Context getContext() {
        return mContext;
    }

    @Override
    public DataServiceProvider onCreateDataServiceProvider(int slotIndex) {
        // TODO: validity check on slot index
        Log.d(TAG, "Creating provider for " + slotIndex);

        if (mNetworkMonitorCallback == null) {
            // start monitoring network
            mNetworkCallbackHandlerThread =
                    new HandlerThread(IwlanNetworkService.class.getSimpleName());
            mNetworkCallbackHandlerThread.start();
            Looper looper = mNetworkCallbackHandlerThread.getLooper();
            Handler handler = new Handler(looper);

            // register for default network callback
            ConnectivityManager connectivityManager =
                    mContext.getSystemService(ConnectivityManager.class);
            mNetworkMonitorCallback = new IwlanNetworkMonitorCallback();
            connectivityManager.registerDefaultNetworkCallback(mNetworkMonitorCallback, handler);
            Log.d(TAG, "Registered with Connectivity Service");
        }

        IwlanDataServiceProvider dp = new IwlanDataServiceProvider(slotIndex, this);
        addIwlanDataServiceProvider(dp);
        return dp;
    }

    public void removeDataServiceProvider(IwlanDataServiceProvider dp) {
        sIwlanDataServiceProviderList.remove(dp);
        if (sIwlanDataServiceProviderList.isEmpty()) {
            // deinit network related stuff
            ConnectivityManager connectivityManager =
                    mContext.getSystemService(ConnectivityManager.class);
            connectivityManager.unregisterNetworkCallback(mNetworkMonitorCallback);
            mNetworkCallbackHandlerThread.quit(); // no need to quitSafely
            mNetworkCallbackHandlerThread = null;
            mNetworkMonitorCallback = null;
        }
    }

    @VisibleForTesting
    void addIwlanDataServiceProvider(IwlanDataServiceProvider dp) {
        sIwlanDataServiceProviderList.add(dp);
    }

    @VisibleForTesting
    void setAppContext(Context appContext) {
        mContext = appContext;
    }

    @VisibleForTesting
    IwlanNetworkMonitorCallback getNetworkMonitorCallback() {
        return mNetworkMonitorCallback;
    }

    @Override
    public void onCreate() {
        setAppContext(getApplicationContext());
        IwlanBroadcastReceiver.startListening(mContext);
    }

    @Override
    public void onDestroy() {
        IwlanBroadcastReceiver.stopListening(mContext);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Iwlanservice onBind");
        return super.onBind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "IwlanService onUnbind");
        // force close all the tunnels when there are no clients
        // active
        for (IwlanDataServiceProvider dp : sIwlanDataServiceProviderList) {
            dp.forceCloseTunnels();
        }
        return super.onUnbind(intent);
    }
}
