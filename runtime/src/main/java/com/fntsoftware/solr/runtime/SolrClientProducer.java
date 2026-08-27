package com.fntsoftware.solr.runtime;

import io.quarkus.arc.lookup.LookupUnlessProperty;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.eclipse.microprofile.context.ManagedExecutor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class SolrClientProducer {
    private static final String DEFAULT_CLIENT = "default";

    @Inject
    SolrConnectionConfig config;

    @Inject
    ManagedExecutor executor;

    private final Map<String, SolrClient> clients = new HashMap<>();

    @Produces
    @ApplicationScoped
    @LookupUnlessProperty(name = "quarkus.solr.enabled", stringValue = "false")
    public SolrClient getClient() throws SolrServerException, IOException {
        return client(DEFAULT_CLIENT, config.url().orElseThrow(
                () -> new IllegalStateException("quarkus.solr.url is required for the default Solr client")));
    }

    SolrClient namedClientForBean(String name) {
        SolrConnectionConfig.ClientConfig clientConfig = config.clients().get(name);
        if (clientConfig == null) {
            throw new IllegalArgumentException("No Solr client configured with name '" + name + "'");
        }
        try {
            return client(name, clientConfig.url());
        } catch (SolrServerException | IOException e) {
            throw new IllegalStateException("Failed to create Solr client '" + name + "'", e);
        }
    }

    private synchronized SolrClient client(String name, String url) throws SolrServerException, IOException {
        SolrClient existing = clients.get(name);
        if (existing != null) {
            return existing;
        }

        HttpJdkSolrClient.Builder builder = new HttpJdkSolrClient.Builder(url)
                .withExecutor(executor)
                .useHttp1_1(config.useHttp1_1())
                .withRequestTimeout(config.requestTimeout(), TimeUnit.MILLISECONDS)
                .withConnectionTimeout(config.connectionTimeout(), TimeUnit.MILLISECONDS)
                .withIdleTimeout(config.idleTimeout(), TimeUnit.MILLISECONDS)
                .withFollowRedirects(config.followRedirects());

        if (config.auth().isPresent()) {
            builder.withBasicAuthCredentials(config.auth().get().username(), config.auth().get().password());
        }
        if (config.defaultCollection().isPresent()) {
            builder.withDefaultCollection(config.defaultCollection().get());
        }

        SolrClient created = builder.build();
        created.ping();
        clients.put(name, created);
        return created;
    }

    @PreDestroy
    void close() throws IOException {
        IOException failure = null;
        for (SolrClient client : clients.values()) {
            try {
                client.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        clients.clear();
        if (failure != null) {
            throw failure;
        }
    }
}
