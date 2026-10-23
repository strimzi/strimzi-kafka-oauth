/*
 * Copyright 2017-2026, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSocketFactory;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.function.Supplier;

/**
 * An {@link SSLSocketFactory} that picks up changes to a file based truststore at runtime.
 * <p>
 * It wraps a delegate factory created from the truststore file. On every {@code createSocket()} call it checks
 * the file's last modification time, and if the file changed since the current delegate was created, a new
 * delegate is created from the file and swapped in. This allows a rotated CA certificate to take effect
 * without restarting the Kafka broker.
 * <p>
 * If reloading fails (for example because the file is temporarily missing or only partially written), the
 * previously created delegate is kept, and the reload is retried on the next {@code createSocket()} call.
 */
public class ReloadableSSLSocketFactory extends SSLSocketFactory {

    private static final Logger log = LoggerFactory.getLogger(ReloadableSSLSocketFactory.class);

    private final File truststoreFile;
    private final Supplier<SSLSocketFactory> factorySupplier;
    private final Object reloadLock = new Object();

    private volatile SSLSocketFactory delegate;
    private volatile long lastModified;

    /**
     * Create a new instance wrapping a delegate created by the given supplier.
     * The initial delegate is created eagerly, so an unusable truststore fails fast, the same way it does
     * without reloading.
     *
     * @param truststoreFile The truststore file to watch for changes
     * @param factorySupplier A supplier that creates a new delegate factory from the current truststore file content
     */
    public ReloadableSSLSocketFactory(File truststoreFile, Supplier<SSLSocketFactory> factorySupplier) {
        this.truststoreFile = truststoreFile;
        this.factorySupplier = factorySupplier;
        this.delegate = factorySupplier.get();
        this.lastModified = truststoreFile.lastModified();
    }

    private SSLSocketFactory getDelegate() {
        long modified = truststoreFile.lastModified();
        if (modified != lastModified) {
            synchronized (reloadLock) {
                modified = truststoreFile.lastModified();
                if (modified != lastModified) {
                    try {
                        SSLSocketFactory fresh = factorySupplier.get();
                        delegate = fresh;
                        lastModified = modified;
                        log.info("Reloaded truststore: {}", truststoreFile);
                    } catch (RuntimeException e) {
                        log.warn("Failed to reload truststore: {} - keeping the previously loaded truststore", truststoreFile, e);
                    }
                }
            }
        }
        return delegate;
    }

    @Override
    public String toString() {
        // Keep logs stable (Object.toString would include an identity hash).
        return "ReloadableSSLSocketFactory";
    }

    /**
     * Get the delegate factory currently in use. Exposed for tests.
     *
     * @return The current delegate factory
     */
    SSLSocketFactory currentDelegate() {
        return delegate;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket() throws IOException {
        return getDelegate().createSocket();
    }

    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
        return getDelegate().createSocket(socket, host, port, autoClose);
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return getDelegate().createSocket(host, port);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return getDelegate().createSocket(host, port, localHost, localPort);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return getDelegate().createSocket(host, port);
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return getDelegate().createSocket(address, port, localAddress, localPort);
    }
}
