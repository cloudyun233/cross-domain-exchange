package com.cde.mqtt;

import com.sun.jna.Native;
import io.sisu.nng.Nng;
import io.sisu.nng.NngException;
import io.sisu.nng.Socket;
import io.sisu.nng.internal.NngOptions;
import io.sisu.nng.internal.TlsConfigByReference;
import io.sisu.nng.internal.TlsConfigPointer;
import org.springframework.util.StringUtils;

final class NanoSdkTlsConfig implements AutoCloseable {

    private static final int CLIENT_MODE = 0;
    private static final int AUTH_NONE = 0;
    private static final NanoSdkNative NATIVE = Native.load("nng", NanoSdkNative.class);

    private final TlsConfigPointer pointer;
    private boolean closed;

    private NanoSdkTlsConfig(TlsConfigPointer pointer) {
        this.pointer = pointer;
    }

    static NanoSdkTlsConfig insecureClient(String serverName) throws NngException {
        TlsConfigByReference ref = new TlsConfigByReference();
        int rv = NATIVE.nng_tls_config_alloc(ref, CLIENT_MODE);
        if (rv != 0) {
            throw new NngException(Nng.lib().nng_strerror(rv));
        }

        TlsConfigPointer pointer = ref.getTlsConfig();
        rv = NATIVE.nng_tls_config_auth_mode(pointer, AUTH_NONE);
        if (rv != 0) {
            NATIVE.nng_tls_config_free(pointer);
            throw new NngException(Nng.lib().nng_strerror(rv));
        }

        if (StringUtils.hasText(serverName)) {
            rv = NATIVE.nng_tls_config_server_name(pointer, serverName);
            if (rv != 0) {
                NATIVE.nng_tls_config_free(pointer);
                throw new NngException(Nng.lib().nng_strerror(rv));
            }
        }

        return new NanoSdkTlsConfig(pointer);
    }

    void applyTo(Socket socket) throws NngException {
        int rv = Nng.lib().nng_socket_set_ptr(socket.getSocketStruct().byValue(), NngOptions.TLS_CONFIG, pointer.getPointer());
        if (rv != 0) {
            throw new NngException(Nng.lib().nng_strerror(rv));
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        NATIVE.nng_tls_config_free(pointer);
    }
}
