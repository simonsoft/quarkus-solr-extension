package com.fntsoftware.solr.deployment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.QuarkusUnitTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.solr.client.solrj.SolrClient;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SolrNamedClientTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("""
                            quarkus.solr.enabled=true
                            quarkus.solr.devservices.enabled=false
                            quarkus.solr.clients.repositem.url=http://localhost:1/solr/repositem
                            quarkus.solr.clients.reposxml.url=http://localhost:1/solr/reposxml
                            """), "application.properties"));

    @Inject
    @Named("repositem")
    Instance<SolrClient> repositem;

    @Inject
    @Named("reposxml")
    Instance<SolrClient> reposxml;

    @Test
    void shouldResolveNamedSolrClients() {
        assertTrue(repositem.isResolvable());
        assertTrue(reposxml.isResolvable());
    }
}
