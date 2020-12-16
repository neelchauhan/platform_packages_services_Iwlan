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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import android.content.Context;
import android.content.res.AssetManager;
import android.net.ipsec.ike.exceptions.AuthenticationFailedException;
import android.net.ipsec.ike.exceptions.ChildSaNotFoundException;
import android.net.ipsec.ike.exceptions.UnrecognizedIkeProtocolException;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.DataFailCause;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import androidx.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.InputStream;

@RunWith(JUnit4.class)
public class ErrorPolicyManagerTest {
    private static final String TAG = "ErrorPolicyManagerTest";

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    private ErrorPolicyManager mErrorPolicyManager;
    private static final int DEFAULT_SLOT_INDEX = 0;
    private static final int DEFAULT_SUBID = 0;

    @Mock private Context mMockContext;
    @Mock CarrierConfigManager mMockCarrierConfigManager;
    @Mock SubscriptionManager mMockSubscriptionManager;
    @Mock SubscriptionInfo mMockSubscriptionInfo;

    @Before
    public void setUp() throws Exception {
        AssetManager mockAssetManager = mock(AssetManager.class);
        Context context = InstrumentationRegistry.getTargetContext();
        InputStream is = context.getResources().getAssets().open("defaultiwlanerrorconfig.json");
        doReturn(mockAssetManager).when(mMockContext).getAssets();
        doReturn(is).when(mockAssetManager).open(any());
        setupMockForCarrierConfig(null);
        mErrorPolicyManager = spy(ErrorPolicyManager.getInstance(mMockContext, DEFAULT_SLOT_INDEX));
    }

    @After
    public void tearDown() throws Exception {
        mErrorPolicyManager.releaseInstance();
    }

    @Test
    public void testValidCarrierConfig() throws Exception {
        String apn = "ims";
        String config =
                "[{"
                        + "\"ApnName\": \""
                        + apn
                        + "\","
                        + "\"ErrorTypes\": [{"
                        + getErrorTypeInJSON(
                                "IKE_PROTOCOL_ERROR_TYPE",
                                new String[] {"24", "34"},
                                new String[] {"4", "8", "16"},
                                new String[] {"APM_ENABLE_EVENT", "WIFI_AP_CHANGED_EVENT"})
                        + "}, {"
                        + getErrorTypeInJSON(
                                "GENERIC_ERROR_TYPE",
                                new String[] {"SERVER_SELECTION_FAILED"},
                                new String[] {"0"},
                                new String[] {"APM_ENABLE_EVENT"})
                        + "}]"
                        + "}]";

        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(ErrorPolicyManager.KEY_ERROR_POLICY_CONFIG_STRING, config);
        setupMockForCarrierConfig(bundle);
        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT)
                .sendToTarget();

        sleep(1000);

        // IKE_PROTOCOL_ERROR_TYPE(24) and retryArray = 4,8,16
        IwlanError iwlanError = new IwlanError(new AuthenticationFailedException("fail"));
        long time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(4, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(8, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(16, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(-1, time);

        // GENERIC_PROTOCOL_ERROR_TYPE - SERVER_SELECTION_FAILED and retryArray = 0, 0
        iwlanError = new IwlanError(IwlanError.EPDG_SELECTOR_SERVER_SELECTION_FAILED);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(0, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(-1, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(-1, time);

        // Fallback case GENERIC_PROTOCOL_ERROR_TYPE(44) and retryArray is 5, 10, 15 as in
        // DEFAULT_CONFIG
        iwlanError = new IwlanError(new ChildSaNotFoundException(10));
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(5, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(10, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(10, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(10, time);
    }

    @Test
    public void testInvalidCarrierConfig() throws Exception {
        String apn = "ims";
        String config =
                "[{"
                        + "\"ApnName\": \""
                        + apn
                        + "\","
                        + "\"ErrorTypes\": [{"
                        + getErrorTypeInJSON(
                                "IKE_PROTOCOL_ERROR_TYPE",
                                new String[] {"WRONG_ERROR_DETAIL"},
                                new String[] {"4", "8", "16"},
                                new String[] {"APM_ENABLE_EVENT", "WIFI_AP_CHANGED_EVENT"})
                        + "}]"
                        + "}]";

        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(ErrorPolicyManager.KEY_ERROR_POLICY_CONFIG_STRING, config);
        setupMockForCarrierConfig(bundle);
        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT)
                .sendToTarget();

        sleep(1000);

        // IKE_PROTOCOL_ERROR_TYPE(24) and retryArray = 5, 10, 15 as it will fallback due to failed
        // parsing
        IwlanError iwlanError = new IwlanError(new AuthenticationFailedException("fail"));
        long time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(5, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(10, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(10, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(10, time);
    }

    @Test
    public void testChoosingFallbackPolicy() throws Exception {
        String apn = "ims";
        String config =
                "[{"
                        + "\"ApnName\": \""
                        + apn
                        + "\","
                        + "\"ErrorTypes\": [{"
                        + getErrorTypeInJSON(
                                "IKE_PROTOCOL_ERROR_TYPE",
                                new String[] {"24", "34"},
                                new String[] {"4", "8", "16"},
                                new String[] {"APM_ENABLE_EVENT", "WIFI_AP_CHANGED_EVENT"})
                        + "}, {"
                        + getErrorTypeInJSON(
                                "IKE_PROTOCOL_ERROR_TYPE",
                                new String[] {"*"},
                                new String[] {"0"},
                                new String[] {"APM_ENABLE_EVENT"})
                        + "}]"
                        + "}]";
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(ErrorPolicyManager.KEY_ERROR_POLICY_CONFIG_STRING, config);
        setupMockForCarrierConfig(bundle);
        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT)
                .sendToTarget();

        sleep(1000);

        mErrorPolicyManager.logErrorPolicies();

        // IKE_PROTOCOL_ERROR_TYPE(24) and retryArray = 4, 8, 16
        IwlanError iwlanError = new IwlanError(new AuthenticationFailedException("fail"));
        long time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(4, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(8, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(16, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(-1, time);

        // IKE_PROTOCOL_ERROR_TYPE(44) and retryArray = 0 as it will fallback to
        // IKE_PROTOCOL_ERROR_TYPE generic fallback first.
        iwlanError = new IwlanError(new ChildSaNotFoundException(10));
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(0, time);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(-1, time);
    }

    @Test
    public void testCanBringUpTunnel() throws Exception {
        String apn = "ims";
        String config =
                "[{"
                        + "\"ApnName\": \""
                        + apn
                        + "\","
                        + "\"ErrorTypes\": [{"
                        + getErrorTypeInJSON(
                                "IKE_PROTOCOL_ERROR_TYPE",
                                new String[] {"24", "34"},
                                new String[] {"4", "8", "16"},
                                new String[] {"APM_ENABLE_EVENT", "WIFI_AP_CHANGED_EVENT"})
                        + "}, {"
                        + getErrorTypeInJSON(
                                "GENERIC_ERROR_TYPE",
                                new String[] {"SERVER_SELECTION_FAILED"},
                                new String[] {"0"},
                                new String[] {"APM_ENABLE_EVENT"})
                        + "}]"
                        + "}]";
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(ErrorPolicyManager.KEY_ERROR_POLICY_CONFIG_STRING, config);
        setupMockForCarrierConfig(bundle);
        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT)
                .sendToTarget();

        sleep(1000);

        // IKE_PROTOCOL_ERROR_TYPE(24) and retryArray = 4,8,16
        IwlanError iwlanError = new IwlanError(new AuthenticationFailedException("fail"));
        long time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(4, time);

        boolean bringUpTunnel = mErrorPolicyManager.canBringUpTunnel(apn);
        assertFalse(bringUpTunnel);

        sleep(4000);

        bringUpTunnel = mErrorPolicyManager.canBringUpTunnel(apn);
        assertTrue(bringUpTunnel);

        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(8, time);

        bringUpTunnel = mErrorPolicyManager.canBringUpTunnel(apn);
        assertFalse(bringUpTunnel);
    }

    @Test
    public void testNoErrorScenario() throws Exception {
        String apn = "ims";
        String config =
                "[{"
                        + "\"ApnName\": \""
                        + apn
                        + "\","
                        + "\"ErrorTypes\": [{"
                        + getErrorTypeInJSON(
                                "IKE_PROTOCOL_ERROR_TYPE",
                                new String[] {"24", "34"},
                                new String[] {"4", "8", "16"},
                                new String[] {"APM_ENABLE_EVENT", "WIFI_AP_CHANGED_EVENT"})
                        + "}, {"
                        + getErrorTypeInJSON(
                                "GENERIC_ERROR_TYPE",
                                new String[] {"SERVER_SELECTION_FAILED"},
                                new String[] {"0"},
                                new String[] {"APM_ENABLE_EVENT"})
                        + "}]"
                        + "}]";
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(ErrorPolicyManager.KEY_ERROR_POLICY_CONFIG_STRING, config);
        setupMockForCarrierConfig(bundle);
        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT)
                .sendToTarget();

        sleep(1000);

        // IKE_PROTOCOL_ERROR_TYPE(24) and retryArray = 4,8,16
        IwlanError iwlanError = new IwlanError(new AuthenticationFailedException("fail"));
        long time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(4, time);

        // report no error
        iwlanError = new IwlanError(IwlanError.NO_ERROR);
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(-1, time);

        // Check whether the error is cleared after NO_ERROR is reported
        boolean bringUpTunnel = mErrorPolicyManager.canBringUpTunnel(apn);
        assertTrue(bringUpTunnel);
    }

    @Test
    public void testAPMUnthrottle() throws Exception {
        String apn = "ims";
        String config =
                "[{"
                        + "\"ApnName\": \""
                        + apn
                        + "\","
                        + "\"ErrorTypes\": [{"
                        + getErrorTypeInJSON(
                                "IKE_PROTOCOL_ERROR_TYPE",
                                new String[] {"24", "34"},
                                new String[] {"4", "8", "16"},
                                new String[] {"APM_ENABLE_EVENT", "WIFI_AP_CHANGED_EVENT"})
                        + "}, {"
                        + getErrorTypeInJSON(
                                "GENERIC_ERROR_TYPE",
                                new String[] {"SERVER_SELECTION_FAILED"},
                                new String[] {"0"},
                                new String[] {"APM_DISABLE_EVENT"})
                        + "}]"
                        + "}]";
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(ErrorPolicyManager.KEY_ERROR_POLICY_CONFIG_STRING, config);
        setupMockForCarrierConfig(bundle);
        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT)
                .sendToTarget();
        sleep(1000);

        // IKE_PROTOCOL_ERROR_TYPE(24) and retryArray = 4,8,16
        IwlanError iwlanError = new IwlanError(new AuthenticationFailedException("fail"));
        long time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(4, time);

        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.APM_ENABLE_EVENT)
                .sendToTarget();
        sleep(500);

        boolean bringUpTunnel = mErrorPolicyManager.canBringUpTunnel(apn);
        assertTrue(bringUpTunnel);

        iwlanError = new IwlanError(new AuthenticationFailedException("fail"));
        time = mErrorPolicyManager.reportIwlanError(apn, iwlanError);
        assertEquals(4, time);
    }

    @Test
    public void testGetDataFailCauseRetryTime() throws Exception {
        String apn1 = "ims";
        String apn2 = "mms";
        String config =
                "[{"
                        + "\"ApnName\": \""
                        + apn1
                        + "\","
                        + "\"ErrorTypes\": [{"
                        + getErrorTypeInJSON(
                                "IKE_PROTOCOL_ERROR_TYPE",
                                new String[] {"24", "34"},
                                new String[] {"4", "8", "16"},
                                new String[] {"APM_ENABLE_EVENT", "WIFI_AP_CHANGED_EVENT"})
                        + "}, {"
                        + getErrorTypeInJSON(
                                "GENERIC_ERROR_TYPE",
                                new String[] {"SERVER_SELECTION_FAILED"},
                                new String[] {"0"},
                                new String[] {"APM_ENABLE_EVENT"})
                        + "}]"
                        + "}]";
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(ErrorPolicyManager.KEY_ERROR_POLICY_CONFIG_STRING, config);
        setupMockForCarrierConfig(bundle);
        mErrorPolicyManager
                .mHandler
                .obtainMessage(IwlanEventListener.CARRIER_CONFIG_CHANGED_EVENT)
                .sendToTarget();

        sleep(1000);

        // IKE_PROTOCOL_ERROR_TYPE(24) and retryArray = 4,8,16
        IwlanError iwlanError = new IwlanError(new AuthenticationFailedException("fail"));
        long time = mErrorPolicyManager.reportIwlanError(apn1, iwlanError);
        assertEquals(4, time);

        iwlanError =
                new IwlanError(
                        new UnrecognizedIkeProtocolException(
                                8192 /*PDN_CONNECTION_REJECTION*/, new byte[] {0x00, 0x01}));
        time = mErrorPolicyManager.reportIwlanError(apn2, iwlanError);
        assertEquals(5, time);

        int failCause = mErrorPolicyManager.getDataFailCause(apn1);
        assertEquals(DataFailCause.USER_AUTHENTICATION, failCause);

        failCause = mErrorPolicyManager.getDataFailCause(apn2);
        assertEquals(DataFailCause.IWLAN_PDN_CONNECTION_REJECTION, failCause);

        long retryTime = mErrorPolicyManager.getCurrentRetryTime(apn1);
        assertEquals(4, retryTime);

        retryTime = mErrorPolicyManager.getCurrentRetryTime(apn2);
        assertEquals(5, retryTime);
    }

    private String getErrorTypeInJSON(
            String ErrorType,
            String[] errorDetails,
            String[] retryArray,
            String[] unthrottlingEvents) {
        return "\"ErrorType\": \""
                + ErrorType
                + "\","
                + "\"ErrorDetails\": [\""
                + String.join("\", \"", errorDetails)
                + "\"],"
                + "\"RetryArray\": [\""
                + String.join("\", \"", retryArray)
                + "\"],"
                + "\"UnthrottlingEvents\": [\""
                + String.join("\", \"", unthrottlingEvents)
                + "\"]";
    }

    private void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupMockForCarrierConfig(PersistableBundle bundle) {
        doReturn(mMockCarrierConfigManager)
                .when(mMockContext)
                .getSystemService(eq(CarrierConfigManager.class));
        doReturn(mMockSubscriptionManager)
                .when(mMockContext)
                .getSystemService(eq(SubscriptionManager.class));
        SubscriptionInfo mockSubInfo = mock(SubscriptionInfo.class);
        doReturn(mockSubInfo)
                .when(mMockSubscriptionManager)
                .getActiveSubscriptionInfoForSimSlotIndex(DEFAULT_SLOT_INDEX);
        doReturn(DEFAULT_SUBID).when(mockSubInfo).getSubscriptionId();
        doReturn(bundle).when(mMockCarrierConfigManager).getConfigForSubId(DEFAULT_SLOT_INDEX);
    }
}
