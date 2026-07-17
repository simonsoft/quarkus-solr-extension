package com.fntsoftware.solr.runtime;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
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
public class SolrClientRegistry {
    private static final String DEFAULT_CLIENT = "default";

    @Inject
    SolrConnectionConfig config;

    @Inject
    ManagedExecutor executor;

    private final Map<String, SolrClient> clients = new HashMap<>();

    public synchronized SolrClient defaultClient() throws SolrServerException, IOException {
        return client(DEFAULT_CLIENT, config.url());
    }

    public synchronized SolrClient namedClient(String name) throws SolrServerException, IOException {
        SolrConnectionConfig.ClientConfig clientConfig = config.clients().get(name);
        if (clientConfig == null) {
            throw new IllegalArgumentException("No Solr client configured with name '" + name + "'");
        }
        return client(name, clientConfig.url());
    }

    private SolrClient client(String name, String url) throws SolrServerException, IOException {
        SolrClient existing = clients.get(name);
        if (existing != null) {
            return existing;
        }
        SolrClient created = createClient(url);
        clients.put(name, created);
        return created;
    }

    private SolrClient createClient(String url) throws SolrServerException, IOException {
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

        SolrClient client = builder.build();
        client.ping();
        return client;
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
