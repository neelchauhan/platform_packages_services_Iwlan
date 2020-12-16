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

import android.os.PersistableBundle;
import android.support.annotation.IntDef;

/**
 * Configs used for epdg tunnel bring up.
 *
 * @see <a href="https://tools.ietf.org/html/rfc7296">RFC 7296, Internet Key Exchange Protocol
 *     Version 2 (IKEv2)</a>
 * @see <a href="https://www.iana.org/assignments/ipsec-registry/ipsec-registry.xhtml">Constants
 *     values are from IANA's ipsec-registry</a>
 */
public final class IwlanConfigs {
    /** Prefix of all Epdg.KEY_* constants. */
    public static final String KEY_PREFIX = "iwlan.";

    /**
     * Time in seconds after which the child security association session is terminated if rekey
     * procedure is not successful. If not set or set to <= 0, the default value is 3600 seconds.
     */
    public static final String KEY_CHILD_SA_REKEY_HARD_TIMER_SEC_INT =
            KEY_PREFIX + "child_sa_rekey_hard_timer_sec_int";

    /**
     * Time in seconds after which the child session rekey procedure is started. If not set or set
     * to <= 0, default value is 3000 seconds.
     */
    public static final String KEY_CHILD_SA_REKEY_SOFT_TIMER_SEC_INT =
            KEY_PREFIX + "child_sa_rekey_soft_timer_sec_int";

    /**
     * Supported DH groups for IKE negotiation. Possible values are {@link #DH_GROUP_NONE}, {@link
     * #DH_GROUP_1024_BIT_MODP}, {@link #DH_GROUP_1536_BIT_MODP}, {@link #DH_GROUP_2048_BIT_MODP}
     */
    public static final String KEY_DIFFIE_HELLMAN_GROUPS_INT_ARRAY =
            KEY_PREFIX + "diffie_hellman_groups_int_array";

    /**
     * Time in seconds after which a dead peer detection (DPD) request is sent. If not set or set to
     * <= 0, default value is 120 seconds.
     */
    public static final String KEY_DPD_TIMER_SEC_INT = KEY_PREFIX + "dpd_timer_sec_int";

    /**
     * Method used to authenticate epdg server. Possible values are {@link
     * #AUTHENTICATION_METHOD_EAP_ONLY}, {@link #AUTHENTICATION_METHOD_CERT}
     */
    public static final String KEY_EPDG_AUTHENTICATION_METHOD_INT =
            KEY_PREFIX + "epdg_authentication_method_int";

    /**
     * A priority list of ePDG addresses to be used. Possible values are {@link
     * #EPDG_ADDRESS_STATIC}, {@link #EPDG_ADDRESS_PLMN}, {@link #EPDG_ADDRESS_PCO}, {@link
     * #EPDG_ADDRESS_CELLULAR_LOC}
     */
    public static final String KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY =
            KEY_PREFIX + "epdg_address_priority_int_array";

    /** Epdg static IP address or FQDN */
    public static final String KEY_EPDG_STATIC_ADDRESS_STRING =
            KEY_PREFIX + "epdg_static_address_string";

    /** Epdg static IP address or FQDN for roaming */
    public static final String KEY_EPDG_STATIC_ADDRESS_ROAMING_STRING =
            KEY_PREFIX + "epdg_static_address_roaming_string";

    /**
     * List of supported key sizes for AES Cipher Block Chaining (CBC) encryption mode of child
     * session. Possible values are {@link #KEY_LEN_UNUSED}, {@link #KEY_LEN_AES_128}, {@link
     * #KEY_LEN_AES_192}, {@link #KEY_LEN_AES_256}
     */
    public static final String KEY_CHILD_SESSION_AES_CBC_KEY_SIZE_INT_ARRAY =
            KEY_PREFIX + "child_session_aes_cbc_key_size_int_array";

    /**
     * List of supported key sizes for AES Counter (CTR) encryption mode of child session. Possible
     * values are {@link #KEY_LEN_UNUSED}, {@link #KEY_LEN_AES_128}, {@link #KEY_LEN_AES_192},
     * {@link #KEY_LEN_AES_256}
     */
    public static final String KEY_CHILD_SESSION_AES_CTR_KEY_SIZE_INT_ARRAY =
            KEY_PREFIX + "child_session_aes_ctr_key_size_int_array";

    /**
     * List of supported encryption algorithms for child session. Possible values are {@link
     * #ENCRYPTION_ALGORITHM_AES_CBC}
     */
    public static final String KEY_SUPPORTED_CHILD_SESSION_ENCRYPTION_ALGORITHMS_INT_ARRAY =
            KEY_PREFIX + "supported_child_session_encryption_algorithms_int_array";

    /**
     * Time in seconds after which the IKE session is terminated if rekey procedure is not
     * successful. If not set or set to <= 0, default value is 3600 seconds.
     */
    public static final String KEY_IKE_REKEY_HARD_TIMER_SEC_INT =
            KEY_PREFIX + "ike_rekey_hard_timer_in_sec";

    /**
     * Time in seconds after which the IKE session rekey procedure is started. If not set or set to
     * <= 0, default value is 3000 seconds.
     */
    public static final String KEY_IKE_REKEY_SOFT_TIMER_SEC_INT =
            KEY_PREFIX + "ike_rekey_soft_timer_sec_int";

    /**
     * List of supported key sizes for AES Cipher Block Chaining (CBC) encryption mode of IKE
     * session. Possible values - {@link #KEY_LEN_UNUSED}, {@link #KEY_LEN_AES_128}, {@link
     * #KEY_LEN_AES_192}, {@link #KEY_LEN_AES_256}
     */
    public static final String KEY_IKE_SESSION_AES_CBC_KEY_SIZE_INT_ARRAY =
            KEY_PREFIX + "ike_session_encryption_aes_cbc_key_size_int_array";

    /**
     * List of supported key sizes for AES Counter (CTR) encryption mode of IKE session. Possible
     * values - {@link #KEY_LEN_UNUSED}, {@link #KEY_LEN_AES_128}, {@link #KEY_LEN_AES_192}, {@link
     * #KEY_LEN_AES_256}
     */
    public static final String KEY_IKE_SESSION_AES_CTR_KEY_SIZE_INT_ARRAY =
            KEY_PREFIX + "ike_session_encryption_aes_ctr_key_size_int_array";

    /**
     * List of supported encryption algorithms for IKE session. Possible values are {@link
     * #ENCRYPTION_ALGORITHM_AES_CBC}
     */
    public static final String KEY_SUPPORTED_IKE_SESSION_ENCRYPTION_ALGORITHMS_INT_ARRAY =
            KEY_PREFIX + "supported_ike_session_encryption_algorithms_int_array";

    /**
     * List of supported integrity algorithms for IKE session Possible values are {@link
     * #INTEGRITY_ALGORITHM_NONE}, {@link #INTEGRITY_ALGORITHM_HMAC_SHA1_96}, {@link
     * #INTEGRITY_ALGORITHM_AES_XCBC_96}, {@link #INTEGRITY_ALGORITHM_HMAC_SHA2_256_128}, {@link
     * #INTEGRITY_ALGORITHM_HMAC_SHA2_384_192}, {@link #INTEGRITY_ALGORITHM_HMAC_SHA2_512_256}
     */
    public static final String KEY_SUPPORTED_INTEGRITY_ALGORITHMS_INT_ARRAY =
            KEY_PREFIX + "supported_integrity_algorithms_int_array";

    /** Maximum number of retries for tunnel establishment. */
    public static final String KEY_MAX_RETRIES_INT = KEY_PREFIX + "max_retries_int";

    /**
     * Time in seconds after which a NATT keep alive message is sent. If not set or set to <= 0,
     * default value is 20 seconds.
     */
    public static final String KEY_NATT_KEEP_ALIVE_TIMER_SEC_INT =
            KEY_PREFIX + "natt_keep_alive_timer_sec_int";

    /** List of '-' separated MCC/MNCs used to create ePDG FQDN as per 3GPP TS 23.003 */
    public static final String KEY_MCC_MNCS_STRING_ARRAY = KEY_PREFIX + "mcc_mncs_string_array";

    /**
     * List of supported pseudo random function algorithms for IKE session Possible values are
     * {@link #PSEUDORANDOM_FUNCTION_HMAC_SHA1}, {@link #PSEUDORANDOM_FUNCTION_AES128_XCBC}
     */
    public static final String KEY_SUPPORTED_PRF_ALGORITHMS_INT_ARRAY =
            KEY_PREFIX + "supported_prf_algorithms_int_array";

    /**
     * List of IKE message retransmission timeouts in milliseconds. Min list length = 1, Max list
     * length = 10 Min timeout = 500 ms, Max timeout = 1800000 ms
     */
    public static final String KEY_RETRANSMIT_TIMER_MSEC_INT_ARRAY =
            KEY_PREFIX + "retransmit_timer_sec_int_array";

    /** Controls if wifi mac Id should be added to network access identifier(NAI) */
    public static final String KEY_ADD_WIFI_MAC_ADDR_TO_NAI_BOOL =
            KEY_PREFIX + "add_wifi_mac_addr_to_nai_bool";

    /** Specifies the local identity type for IKE negotiations */
    public static final String KEY_IKE_LOCAL_ID_TYPE_INT = KEY_PREFIX + "ike_local_id_type_int";

    /** Specifies the remote identity type for IKE negotiations */
    public static final String KEY_IKE_REMOTE_ID_TYPE_INT = KEY_PREFIX + "ike_remote_id_type_int";

    /** Controls if KE payload should be added during child session local rekey procedure. */
    public static final String KEY_ADD_KE_TO_CHILD_SESSION_REKEY_BOOL =
            KEY_PREFIX + "add_ke_to_child_session_rekey_bool";

    /** Specifies the PCO id for IPv6 Epdg server address */
    public static final String KEY_EPDG_PCO_ID_IPV6_INT = KEY_PREFIX + "epdg_pco_id_ipv6_int";

    /** Specifies the PCO id for IPv4 Epdg server address */
    public static final String KEY_EPDG_PCO_ID_IPV4_INT = KEY_PREFIX + "epdg_pco_id_ipv4_int";

    /** @hide */
    @IntDef({AUTHENTICATION_METHOD_EAP_ONLY, AUTHENTICATION_METHOD_CERT})
    public @interface AuthenticationMethodType {}

    /**
     * Certificate sent from the server is ignored. Only Extensible Authentication Protocol (EAP) is
     * used to authenticate the server. EAP_ONLY_AUTH payload is added to IKE_AUTH request if
     * supported.
     *
     * @see <a href="https://tools.ietf.org/html/rfc5998">RFC 5998</a>
     */
    public static final int AUTHENTICATION_METHOD_EAP_ONLY = 0;
    /** Server is authenticated using its certificate. */
    public static final int AUTHENTICATION_METHOD_CERT = 1;

    /** @hide */
    @IntDef({EPDG_ADDRESS_STATIC, EPDG_ADDRESS_PLMN, EPDG_ADDRESS_PCO, EPDG_ADDRESS_CELLULAR_LOC})
    public @interface EpdgAddressType {}

    /** Use static epdg address. */
    public static final int EPDG_ADDRESS_STATIC = 0;
    /** Construct the epdg address using plmn. */
    public static final int EPDG_ADDRESS_PLMN = 1;
    /** Use the epdg address received in protocol configuration options (PCO) from the network. */
    public static final int EPDG_ADDRESS_PCO = 2;
    /** Use cellular location to chose epdg server */
    public static final int EPDG_ADDRESS_CELLULAR_LOC = 3;

    /** @hide */
    @IntDef({KEY_LEN_UNUSED, KEY_LEN_AES_128, KEY_LEN_AES_192, KEY_LEN_AES_256})
    public @interface EncrpytionKeyLengthType {}

    public static final int KEY_LEN_UNUSED = 0;
    /** AES Encryption/Ciphering Algorithm key length 128 bits. */
    public static final int KEY_LEN_AES_128 = 128;
    /** AES Encryption/Ciphering Algorithm key length 192 bits. */
    public static final int KEY_LEN_AES_192 = 192;
    /** AES Encryption/Ciphering Algorithm key length 256 bits. */
    public static final int KEY_LEN_AES_256 = 256;

    /** @hide */
    @IntDef({
        DH_GROUP_NONE,
        DH_GROUP_1024_BIT_MODP,
        DH_GROUP_1536_BIT_MODP,
        DH_GROUP_2048_BIT_MODP,
        DH_GROUP_3072_BIT_MODP,
        DH_GROUP_4096_BIT_MODP
    })
    public @interface DhGroup {}

    /** None Diffie-Hellman Group. */
    public static final int DH_GROUP_NONE = 0;
    /** 1024-bit MODP Diffie-Hellman Group. */
    public static final int DH_GROUP_1024_BIT_MODP = 2;
    /** 1536-bit MODP Diffie-Hellman Group. */
    public static final int DH_GROUP_1536_BIT_MODP = 5;
    /** 2048-bit MODP Diffie-Hellman Group. */
    public static final int DH_GROUP_2048_BIT_MODP = 14;
    /** 3072-bit MODP Diffie-Hellman Group. */
    public static final int DH_GROUP_3072_BIT_MODP = 15;
    /** 4096-bit MODP Diffie-Hellman Group. */
    public static final int DH_GROUP_4096_BIT_MODP = 16;

    /** @hide */
    @IntDef({ENCRYPTION_ALGORITHM_AES_CBC, ENCRYPTION_ALGORITHM_AES_CTR})
    public @interface EncryptionAlgorithm {}

    /** AES-CBC Encryption/Ciphering Algorithm. */
    public static final int ENCRYPTION_ALGORITHM_AES_CBC = 12;

    /** AES-CTR Encryption/Ciphering Algorithm. */
    public static final int ENCRYPTION_ALGORITHM_AES_CTR = 13;

    /** @hide */
    @IntDef({
        INTEGRITY_ALGORITHM_NONE,
        INTEGRITY_ALGORITHM_HMAC_SHA1_96,
        INTEGRITY_ALGORITHM_AES_XCBC_96,
        INTEGRITY_ALGORITHM_HMAC_SHA2_256_128,
        INTEGRITY_ALGORITHM_HMAC_SHA2_384_192,
        INTEGRITY_ALGORITHM_HMAC_SHA2_512_256
    })
    public @interface IntegrityAlgorithm {}

    /** None Authentication/Integrity Algorithm. */
    public static final int INTEGRITY_ALGORITHM_NONE = 0;
    /** HMAC-SHA1 Authentication/Integrity Algorithm. */
    public static final int INTEGRITY_ALGORITHM_HMAC_SHA1_96 = 2;
    /** AES-XCBC-96 Authentication/Integrity Algorithm. */
    public static final int INTEGRITY_ALGORITHM_AES_XCBC_96 = 5;
    /** HMAC-SHA256 Authentication/Integrity Algorithm with 128-bit truncation. */
    public static final int INTEGRITY_ALGORITHM_HMAC_SHA2_256_128 = 12;
    /** HMAC-SHA384 Authentication/Integrity Algorithm with 192-bit truncation. */
    public static final int INTEGRITY_ALGORITHM_HMAC_SHA2_384_192 = 13;
    /** HMAC-SHA512 Authentication/Integrity Algorithm with 256-bit truncation. */
    public static final int INTEGRITY_ALGORITHM_HMAC_SHA2_512_256 = 14;

    /** @hide */
    @IntDef({
        PSEUDORANDOM_FUNCTION_HMAC_SHA1,
        PSEUDORANDOM_FUNCTION_AES128_XCBC,
        PSEUDORANDOM_FUNCTION_SHA2_256,
        PSEUDORANDOM_FUNCTION_SHA2_384,
        PSEUDORANDOM_FUNCTION_SHA2_512
    })
    public @interface PseudorandomFunction {}

    /** HMAC-SHA1 Pseudorandom Function. */
    public static final int PSEUDORANDOM_FUNCTION_HMAC_SHA1 = 2;
    /** AES128-XCBC Pseudorandom Function. */
    public static final int PSEUDORANDOM_FUNCTION_AES128_XCBC = 4;
    /** HMAC-SHA2-256 Pseudorandom Function. */
    public static final int PSEUDORANDOM_FUNCTION_SHA2_256 = 5;
    /** HMAC-SHA2-384 Pseudorandom Function. */
    public static final int PSEUDORANDOM_FUNCTION_SHA2_384 = 6;
    /** HMAC-SHA2-384 Pseudorandom Function. */
    public static final int PSEUDORANDOM_FUNCTION_SHA2_512 = 7;

    @IntDef({ID_TYPE_FQDN, ID_TYPE_RFC822_ADDR, ID_TYPE_KEY_ID})
    public @interface IkeIdType {}

    public static final int ID_TYPE_FQDN = 2;
    public static final int ID_TYPE_RFC822_ADDR = 3;
    public static final int ID_TYPE_KEY_ID = 11;

    private IwlanConfigs() {}

    private static PersistableBundle getDefaults() {
        PersistableBundle defaults = new PersistableBundle();
        defaults.putInt(KEY_IKE_REKEY_SOFT_TIMER_SEC_INT, 7200);
        defaults.putInt(KEY_IKE_REKEY_HARD_TIMER_SEC_INT, 14400);
        defaults.putInt(KEY_CHILD_SA_REKEY_SOFT_TIMER_SEC_INT, 3600);
        defaults.putInt(KEY_CHILD_SA_REKEY_HARD_TIMER_SEC_INT, 7200);
        defaults.putIntArray(
                KEY_RETRANSMIT_TIMER_MSEC_INT_ARRAY, new int[] {500, 1000, 2000, 4000, 8000});
        defaults.putInt(KEY_DPD_TIMER_SEC_INT, 120);
        defaults.putInt(KEY_MAX_RETRIES_INT, 3);
        defaults.putIntArray(
                KEY_DIFFIE_HELLMAN_GROUPS_INT_ARRAY,
                new int[] {DH_GROUP_1024_BIT_MODP, DH_GROUP_1536_BIT_MODP, DH_GROUP_2048_BIT_MODP});
        defaults.putIntArray(
                KEY_SUPPORTED_IKE_SESSION_ENCRYPTION_ALGORITHMS_INT_ARRAY,
                new int[] {ENCRYPTION_ALGORITHM_AES_CBC});
        defaults.putIntArray(
                KEY_SUPPORTED_CHILD_SESSION_ENCRYPTION_ALGORITHMS_INT_ARRAY,
                new int[] {ENCRYPTION_ALGORITHM_AES_CBC});
        defaults.putIntArray(
                KEY_SUPPORTED_INTEGRITY_ALGORITHMS_INT_ARRAY,
                new int[] {
                    INTEGRITY_ALGORITHM_AES_XCBC_96,
                    INTEGRITY_ALGORITHM_HMAC_SHA1_96,
                    INTEGRITY_ALGORITHM_HMAC_SHA2_256_128,
                    INTEGRITY_ALGORITHM_HMAC_SHA2_384_192,
                    INTEGRITY_ALGORITHM_HMAC_SHA2_512_256,
                });
        defaults.putIntArray(
                KEY_SUPPORTED_PRF_ALGORITHMS_INT_ARRAY,
                new int[] {
                    PSEUDORANDOM_FUNCTION_HMAC_SHA1,
                    PSEUDORANDOM_FUNCTION_AES128_XCBC,
                    PSEUDORANDOM_FUNCTION_SHA2_256,
                    PSEUDORANDOM_FUNCTION_SHA2_384,
                    PSEUDORANDOM_FUNCTION_SHA2_512
                });

        defaults.putInt(KEY_EPDG_AUTHENTICATION_METHOD_INT, AUTHENTICATION_METHOD_EAP_ONLY);
        defaults.putString(KEY_EPDG_STATIC_ADDRESS_STRING, "");
        defaults.putString(KEY_EPDG_STATIC_ADDRESS_ROAMING_STRING, "");
        // will be used after b/158036773 is fixed
        defaults.putInt(KEY_NATT_KEEP_ALIVE_TIMER_SEC_INT, 20);
        defaults.putIntArray(
                KEY_IKE_SESSION_AES_CBC_KEY_SIZE_INT_ARRAY,
                new int[] {KEY_LEN_AES_128, KEY_LEN_AES_192, KEY_LEN_AES_256});
        defaults.putIntArray(
                KEY_CHILD_SESSION_AES_CBC_KEY_SIZE_INT_ARRAY,
                new int[] {KEY_LEN_AES_128, KEY_LEN_AES_192, KEY_LEN_AES_256});
        defaults.putIntArray(
                KEY_IKE_SESSION_AES_CTR_KEY_SIZE_INT_ARRAY,
                new int[] {KEY_LEN_AES_128, KEY_LEN_AES_192, KEY_LEN_AES_256});
        defaults.putIntArray(
                KEY_CHILD_SESSION_AES_CTR_KEY_SIZE_INT_ARRAY,
                new int[] {KEY_LEN_AES_128, KEY_LEN_AES_192, KEY_LEN_AES_256});
        defaults.putIntArray(
                KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                new int[] {EPDG_ADDRESS_PLMN, EPDG_ADDRESS_STATIC});
        defaults.putStringArray(KEY_MCC_MNCS_STRING_ARRAY, new String[] {});
        defaults.putBoolean(KEY_ADD_WIFI_MAC_ADDR_TO_NAI_BOOL, false);
        defaults.putInt(KEY_IKE_LOCAL_ID_TYPE_INT, ID_TYPE_RFC822_ADDR);
        defaults.putInt(KEY_IKE_REMOTE_ID_TYPE_INT, ID_TYPE_FQDN);
        defaults.putBoolean(KEY_ADD_KE_TO_CHILD_SESSION_REKEY_BOOL, false);
        defaults.putInt(KEY_EPDG_PCO_ID_IPV6_INT, 0);
        defaults.putInt(KEY_EPDG_PCO_ID_IPV4_INT, 0);

        return defaults;
    }

    public static PersistableBundle getDefaultConfig() {
        return getDefaults();
    }
}
