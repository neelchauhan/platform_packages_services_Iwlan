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

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.AccessNetworkConstants;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.NetworkService;
import android.telephony.NetworkServiceCallback;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IwlanNetworkService extends NetworkService {
    private static final String TAG = IwlanNetworkService.class.getSimpleName();
    private Context mContext;
    private IwlanWifiMonitorCallback mWifiMonitorCallback;
    private IwlanOnSubscriptionsChangedListener mSubsChangeListener;
    private HandlerThread mWifiCallbackHandlerThread;
    private static boolean sWifiConnected;
    private static List<IwlanNetworkServiceProvider> sIwlanNetworkServiceProviderList =
            new ArrayList<IwlanNetworkServiceProvider>();

    final class IwlanWifiMonitorCallback extends ConnectivityManager.NetworkCallback {
        /** Called when the framework connects and has declared a new network ready for use. */
        @Override
        public void onAvailable(Network network) {
            Log.d(TAG, "onAvailable: " + network);
            IwlanNetworkService.setWifiConnected(true);
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
            Log.d(TAG, "onLosing: maxMsToLive: " + maxMsToLive + " network:" + network);
        }

        /**
         * Called when a network disconnects or otherwise no longer satisfies this request or
         * callback.
         */
        @Override
        public void onLost(Network network) {
            Log.d(TAG, "onLost: " + network);
            IwlanNetworkService.setWifiConnected(false);
        }

        /** Called when the network corresponding to this request changes {@link LinkProperties}. */
        @Override
        public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
            Log.d(TAG, "onLinkPropertiesChanged: " + network + " linkprops:" + linkProperties);
        }

        /** Called when access to the specified network is blocked or unblocked. */
        @Override
        public void onBlockedStatusChanged(Network network, boolean blocked) {
            // TODO: check if we need to handle this
            Log.d(TAG, "onBlockedStatusChanged: " + network + " BLOCKED:" + blocked);
        }
    }

    final class IwlanOnSubscriptionsChangedListener
            extends SubscriptionManager.OnSubscriptionsChangedListener {
        /**
         * Callback invoked when there is any change to any SubscriptionInfo. Typically this method
         * invokes {@link SubscriptionManager#getActiveSubscriptionInfoList}
         */
        @Override
        public void onSubscriptionsChanged() {
            for (IwlanNetworkServiceProvider np : sIwlanNetworkServiceProviderList) {
                np.subscriptionChanged();
            }
        }
    }

    @VisibleForTesting
    class IwlanNetworkServiceProvider extends NetworkServiceProvider {
        private final IwlanNetworkService mIwlanNetworkService;
        private final String SUB_TAG;
        private boolean mIsSubActive = false;

        /**
         * Constructor
         *
         * @param slotIndex SIM slot id the data service provider associated with.
         */
        public IwlanNetworkServiceProvider(int slotIndex, IwlanNetworkService iwlanNetworkService) {
            super(slotIndex);
            SUB_TAG = TAG + "[" + slotIndex + "]";
            mIwlanNetworkService = iwlanNetworkService;
        }

        @Override
        public void requestNetworkRegistrationInfo(int domain, NetworkServiceCallback callback) {
            if (callback == null) {
                Log.d(SUB_TAG, "Error: callback is null. returning");
                return;
            }
            if (domain != NetworkRegistrationInfo.DOMAIN_PS) {
                callback.onRequestNetworkRegistrationInfoComplete(
                        NetworkServiceCallback.RESULT_ERROR_UNSUPPORTED, null);
                return;
            }

            NetworkRegistrationInfo.Builder nriBuilder = new NetworkRegistrationInfo.Builder();
            nriBuilder
                    .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_IWLAN)
                    .setAvailableServices(Arrays.asList(NetworkRegistrationInfo.SERVICE_TYPE_DATA))
                    .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                    .setEmergencyOnly(!mIsSubActive)
                    .setDomain(NetworkRegistrationInfo.DOMAIN_PS);
            if (!IwlanNetworkService.isWifiConnected()) {
                nriBuilder.setRegistrationState(
                        NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING);
                Log.d(SUB_TAG, "reg state REGISTRATION_STATE_NOT_REGISTERED_SEARCHING");
            } else {
                nriBuilder.setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
                Log.d(SUB_TAG, "reg state REGISTRATION_STATE_HOME");
            }

            callback.onRequestNetworkRegistrationInfoComplete(
                    NetworkServiceCallback.RESULT_SUCCESS, nriBuilder.build());
        }

        /**
         * Called when the instance of network service is destroyed (e.g. got unbind or binder died)
         * or when the network service provider is removed. The extended class should implement this
         * method to perform cleanup works.
         */
        @Override
        public void close() {
            mIwlanNetworkService.removeNetworkServiceProvider(this);
        }

        @VisibleForTesting
        void subscriptionChanged() {
            boolean subActive = false;
            SubscriptionManager sm = SubscriptionManager.from(mContext);
            if (sm.getActiveSubscriptionInfoForSimSlotIndex(getSlotIndex()) != null) {
                subActive = true;
            }
            if (subActive == mIsSubActive) {
                return;
            }
            mIsSubActive = subActive;
            if (subActive) {
                Log.d(SUB_TAG, "sub changed from not_ready --> ready");
            } else {
                Log.d(SUB_TAG, "sub changed from ready --> not_ready");
            }

            notifyNetworkRegistrationInfoChanged();
        }
    }

    /**
     * Create the instance of {@link NetworkServiceProvider}. Network service provider must override
     * this method to facilitate the creation of {@link NetworkServiceProvider} instances. The
     * system will call this method after binding the network service for each active SIM slot id.
     *
     * @param slotIndex SIM slot id the network service associated with.
     * @return Network service object. Null if failed to create the provider (e.g. invalid slot
     *     index)
     */
    @Override
    public NetworkServiceProvider onCreateNetworkServiceProvider(int slotIndex) {
        Log.d(TAG, "onCreateNetworkServiceProvider: slotidx:" + slotIndex);

        // TODO: validity check slot index

        if (sIwlanNetworkServiceProviderList.isEmpty()) {
            // first invocation
            mWifiCallbackHandlerThread =
                    new HandlerThread(IwlanNetworkService.class.getSimpleName());
            mWifiCallbackHandlerThread.start();
            Looper looper = mWifiCallbackHandlerThread.getLooper();
            Handler handler = new Handler(looper);

            /* register was wifi network callback */
            NetworkRequest.Builder networkParams = new NetworkRequest.Builder();
            networkParams.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            networkParams.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
            // TODO: check if this may cause issues with callbox if it is a piped in wifi connection
            networkParams.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            NetworkRequest networkRequest = networkParams.build();

            ConnectivityManager connectivityManager =
                    mContext.getSystemService(ConnectivityManager.class);
            mWifiMonitorCallback = new IwlanWifiMonitorCallback();
            connectivityManager.registerNetworkCallback(
                    networkRequest, mWifiMonitorCallback, handler);
            Log.d(TAG, "Registered with Connectivity Service");

            /* register with subscription manager */
            SubscriptionManager subscriptionManager =
                    mContext.getSystemService(SubscriptionManager.class);
            mSubsChangeListener = new IwlanOnSubscriptionsChangedListener();
            subscriptionManager.addOnSubscriptionsChangedListener(mSubsChangeListener);
            Log.d(TAG, "Registered with Subscription Service");
        }

        IwlanNetworkServiceProvider np = new IwlanNetworkServiceProvider(slotIndex, this);
        sIwlanNetworkServiceProviderList.add(np);
        return np;
    }

    public static boolean isWifiConnected() {
        return sWifiConnected;
    }

    public static void setWifiConnected(boolean connected) {
        if (connected == sWifiConnected) {
            return;
        }
        sWifiConnected = connected;

        for (IwlanNetworkServiceProvider np : sIwlanNetworkServiceProviderList) {
            np.notifyNetworkRegistrationInfoChanged();
        }
    }

    public void removeNetworkServiceProvider(IwlanNetworkServiceProvider np) {
        sIwlanNetworkServiceProviderList.remove(np);
        if (sIwlanNetworkServiceProviderList.isEmpty()) {
            // deinit wifi related stuff
            ConnectivityManager connectivityManager =
                    mContext.getSystemService(ConnectivityManager.class);
            connectivityManager.unregisterNetworkCallback(mWifiMonitorCallback);
            mWifiCallbackHandlerThread.quit(); // no need to quitSafely
            mWifiCallbackHandlerThread = null;
            mWifiMonitorCallback = null;

            // deinit subscription manager related stuff
            SubscriptionManager subscriptionManager =
                    mContext.getSystemService(SubscriptionManager.class);
            subscriptionManager.removeOnSubscriptionsChangedListener(mSubsChangeListener);
            mSubsChangeListener = null;
        }
    }

    Context getContext() {
        return getApplicationContext();
    }

    @VisibleForTesting
    void setAppContext(Context appContext) {
        mContext = appContext;
    }

    @VisibleForTesting
    IwlanNetworkServiceProvider getNetworkServiceProvider(int slotIndex) {
        for (IwlanNetworkServiceProvider np : sIwlanNetworkServiceProviderList) {
            if (np.getSlotIndex() == slotIndex) {
                return np;
            }
        }
        return null;
    }

    @Override
    public void onCreate() {
        mContext = getApplicationContext();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "IwlanNetworkService onBind");
        return super.onBind(intent);
    }
}
