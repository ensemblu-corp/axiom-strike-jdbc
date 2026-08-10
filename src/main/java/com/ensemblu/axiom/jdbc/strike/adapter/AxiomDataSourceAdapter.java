package com.ensemblu.axiom.jdbc.strike.adapter;

import com.zaxxer.hikari.HikariDataSource;
import com.ensemblu.axiom.jdbc.provision.SovereignDataSource;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/**
 * The Physical Adapter.
 * It plugs the Hikari 'Metal' into the Axiom 'Socket'.
 */
public final class AxiomDataSourceAdapter implements SovereignDataSource {

    private final HikariDataSource delegate;

    private AxiomDataSourceAdapter(HikariDataSource delegate) {
        this.delegate = delegate;
    }

    public static AxiomDataSourceAdapter of(HikariDataSource delegate) {
        return new AxiomDataSourceAdapter(delegate);
    }

    /**
     * PRIMARY ENGINE: Acquires a connection from the pool.
     * This is the only method Axiom Core actually depends on for logic.
     */
    @Override
    public Connection getConnection() throws SQLException {
        return delegate.getConnection();
    }

    /**
     * LIFECYCLE COMMAND: Physically shuts down the pool.
     * Satisfies AutoCloseable and prevents resource leaks in the Strike.
     */
    @Override
    public void close() {
        delegate.close();
    }

    /* --- THE DIRTY ZONE: LEGACY COMPLIANCE --- */

    /**
     * LEGACY AUTH: Allows requesting a connection with specific credentials.
     * Generally ignored in pooled environments as credentials are set at the pool level.
     */
    @Override
    public Connection getConnection(String u, String p) throws SQLException {
        return delegate.getConnection(u, p);
    }

    /**
     * LEGACY LOGGING: Retrieves the character output stream for database logging.
     * A relic of old Java tracing; Axiom uses its own reactive logging.
     */
    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    /**
     * LEGACY LOGGING: Sets the character output stream for database logging.
     */
    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    /**
     * SECURITY TIMEOUT: Gets the maximum time in seconds the driver waits for a connection.
     */
    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    /**
     * SECURITY TIMEOUT: Sets the maximum wait time for login/connection attempts.
     */
    @Override
    public void setLoginTimeout(int s) throws SQLException {
        delegate.setLoginTimeout(s);
    }

    /**
     * PLATFORM LOGGING: Required for integration with java.util.logging.
     * Allows the driver to participate in the platform's logging hierarchy.
     */
    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }

    /**
     * REFLECTION/WRAPPING: Provides access to the underlying physical driver.
     * Used if a library needs to bypass the Axiom Bridge to hit vendor-specific code.
     */
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return delegate.unwrap(iface);
    }

    /**
     * REFLECTION/WRAPPING: Checks if this object is a wrapper for a specific vendor class.
     */
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return delegate.isWrapperFor(iface);
    }
}