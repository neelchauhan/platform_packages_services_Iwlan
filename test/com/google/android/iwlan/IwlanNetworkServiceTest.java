// Copyright 2020 Google Inc. All Rights Reserved.

package com.google.android.iwlan;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.content.Context;
import android.net.ConnectivityManager;
import android.telephony.AccessNetworkConstants;
import android.telephony.INetworkService;
import android.telephony.INetworkServiceCallback;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.NetworkServiceCallback;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.google.android.iwlan.IwlanNetworkService.IwlanNetworkServiceProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.Arrays;

public class IwlanNetworkServiceTest {
    private static final String TAG = IwlanNetworkServiceTest.class.getSimpleName();
    private static final int DEFAULT_SLOT_INDEX = 0;

    @Mock private Context mMockContext;
    @Mock private ConnectivityManager mMockConnectivityManager;
    @Mock private SubscriptionManager mMockSubscriptionManager;
    @Mock private SubscriptionInfo mMockSubscriptionInfo;
    @Mock private INetworkServiceCallback mCallback;
    MockitoSession mStaticMockSession;

    IwlanNetworkService mIwlanNetworkService;
    INetworkService mBinder;
    IwlanNetworkServiceProvider mIwlanNetworkServiceProvider;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mStaticMockSession =
                mockitoSession()
                        .mockStatic(IwlanHelper.class)
                        .mockStatic(SubscriptionManager.class)
                        .startMocking();

        when(mMockContext.getSystemService(eq(ConnectivityManager.class)))
                .thenReturn(mMockConnectivityManager);

        when(mMockContext.getSystemService(eq(Context.TELEPHONY_SUBSCRIPTION_SERVICE)))
                .thenReturn(mMockSubscriptionManager);

        when(mMockSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(
                        eq(DEFAULT_SLOT_INDEX)))
                .thenReturn(mMockSubscriptionInfo);

        lenient()
                .when(SubscriptionManager.from(eq(mMockContext)))
                .thenReturn(mMockSubscriptionManager);

        mIwlanNetworkService = new IwlanNetworkService();
        mIwlanNetworkService.setAppContext(mMockContext);
        mIwlanNetworkServiceProvider = null;

        mBinder = mIwlanNetworkService.mBinder;
        mBinder.createNetworkServiceProvider(DEFAULT_SLOT_INDEX);
        mBinder.registerForNetworkRegistrationInfoChanged(DEFAULT_SLOT_INDEX, mCallback);
    }

    @After
    public void cleanUp() throws Exception {
        mBinder.removeNetworkServiceProvider(DEFAULT_SLOT_INDEX);
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testRequestNetworkRegistrationInfo() throws Exception {
        int domain = NetworkRegistrationInfo.DOMAIN_PS;
        boolean mIsSubActive = true;
        long startTime;

        // Wait for IwlanNetworkServiceProvider created and timeout is 1 second.
        startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 1000) {
            mIwlanNetworkServiceProvider =
                    mIwlanNetworkService.getNetworkServiceProvider(DEFAULT_SLOT_INDEX);
            if (mIwlanNetworkServiceProvider != null) {
                break;
            }
        }

        assertTrue(mIwlanNetworkServiceProvider != null);

        // Set Wifi on and verify mCallback should receive onNetworkStateChanged.
        mIwlanNetworkService.setWifiConnected(true);
        verify(mCallback, timeout(1000).times(1)).onNetworkStateChanged();

        // Set Sub active and verify mCallback should receive onNetworkStateChanged.
        mIwlanNetworkServiceProvider.subscriptionChanged();
        verify(mCallback, timeout(1000).times(2)).onNetworkStateChanged();

        // Create expected NetworkRegistrationInfo
        NetworkRegistrationInfo.Builder expectedStateBuilder =
                new NetworkRegistrationInfo.Builder();
        expectedStateBuilder
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_IWLAN)
                .setAvailableServices(Arrays.asList(NetworkRegistrationInfo.SERVICE_TYPE_DATA))
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                .setEmergencyOnly(!mIsSubActive)
                .setDomain(domain)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME);

        mBinder.requestNetworkRegistrationInfo(0, domain, mCallback);

        verify(mCallback, timeout(1000).times(1))
                .onRequestNetworkRegistrationInfoComplete(
                        eq(NetworkServiceCallback.RESULT_SUCCESS),
                        eq(expectedStateBuilder.build()));
    }
}
