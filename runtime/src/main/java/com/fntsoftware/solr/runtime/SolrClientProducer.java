package com.fntsoftware.solr.runtime;

import io.quarkus.arc.lookup.LookupUnlessProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;

import java.io.IOException;

@ApplicationScoped
public class SolrClientProducer {
    @Inject
    SolrClientRegistry registry;

    @Produces
    @ApplicationScoped
    @LookupUnlessProperty(name = "quarkus.solr.enabled", stringValue = "false")
    public SolrClient getClient() throws SolrServerException, IOException {
        return registry.defaultClient();
    }
}
