package com.fntsoftware.solr.runtime;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Map;
import java.util.Optional;

@ConfigMapping(prefix = "quarkus.solr")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface SolrConnectionConfig {
    /**
     * Whether the SolrJ client should be available for injection.
     * TODO This config mapping entry is only used to avoid Quarkus warnings when consumers set the lookup property.
     *
     * @return
     */
    @WithDefault("false")
    Boolean enabled();

    /**
     * The URL to the Solr server instance
     *
     * @return
     */
    Optional<String> url();

    /**
     * Whether the SolrJ client should follow redirects or not
     *
     * @return
     */
    @WithDefault("false")
    Boolean followRedirects();

    /**
     * If true, prefer http1.1 over http2. Default is false.
     *
     * @return
     */
    @WithDefault("false")
    Boolean useHttp1_1();

    /**
     * The request timeout in milliseconds for the http client. When <= 0, no timeout is used. Default is 0.
     *
     * @return
     */
    @WithDefault("0")
    Long requestTimeout();

    /**
     * The connection timeout in milliseconds for the http client. Default is 60000.
     *
     * @return
     */
    @WithDefault("60000")
    Long connectionTimeout();

    /**
     * The idle timeout in milliseconds for the http client. Default is 60000.
     *
     * @return
     */
    @WithDefault("600000")
    Long idleTimeout();

    /**
     * Setup basic authentication with username and password. When this is not set, no authentication will be used.
     *
     * @return
     */
    Optional<BasicAuthConfig> auth();

    /**
     * The name of the default collection, the client should use.
     *
     * @return
     */
    Optional<String> defaultCollection();

    /**
     * Named Solr clients for multi-core applications.
     *
     * @return the client configurations keyed by CDI name/core identifier
     */
    Map<String, ClientConfig> clients();

    interface ClientConfig {
        /**
         * The URL to the Solr core.
         *
         * @return
         */
        String url();
    }

    interface BasicAuthConfig {
        /**
         * Username to use for basic authentication.
         *
         * @return
         */
        String username();

        /**
         * Password to use for basic authentication.
         *
         * @return
         */
        String password();
    }
}
