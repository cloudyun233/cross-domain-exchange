package com.cde.mqtt;

import com.sun.jna.Library;
import io.sisu.nng.internal.TlsConfigByReference;
import io.sisu.nng.internal.TlsConfigPointer;

public interface NanoSdkNative extends Library {
    int nng_tls_config_alloc(TlsConfigByReference ref, int mode);

    int nng_tls_config_auth_mode(TlsConfigPointer cfg, int mode);

    int nng_tls_config_server_name(TlsConfigPointer cfg, String serverName);

    int nng_tls_config_free(TlsConfigPointer cfg);
}
