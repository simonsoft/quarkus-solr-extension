package com.fntsoftware.solr.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.QuarkusUnitTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.solr.client.solrj.SolrClient;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SolrMixedCoreDevServiceTest {

    private static final String EXTERNAL_URL = "http://localhost:1/solr/repositem";

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> {
                JavaArchive archive = ShrinkWrap.create(JavaArchive.class)
                        .addAsResource(new StringAsset("""
                                quarkus.solr.enabled=true
                                quarkus.solr.devservices.cores.repositem.config-path=solr/repositem
                                quarkus.solr.devservices.cores.reposxml.config-path=solr/reposxml
                                quarkus.solr.clients.repositem.url=%s
                                """.formatted(EXTERNAL_URL)), "application.properties");
                for (String core : new String[] { "repositem", "reposxml" }) {
                    archive.addAsResource(new StringAsset("name=" + core), "solr/" + core + "/core.properties")
                            .addAsResource(new StringAsset(SolrMultiCoreDevServiceTest.SOLRCONFIG),
                                    "solr/" + core + "/conf/solrconfig.xml")
                            .addAsResource(new StringAsset(SolrMultiCoreDevServiceTest.SCHEMA),
                                    "solr/" + core + "/conf/schema.xml");
                }
                return archive;
            });

    @Inject
    @Named("reposxml")
    SolrClient reposxml;

    @Test
    void shouldPreserveExternalUrlWhileStartingAnotherCore() throws Exception {
        assertEquals(EXTERNAL_URL,
                ConfigProvider.getConfig().getValue("quarkus.solr.clients.repositem.url", String.class));
        assertEquals(0, reposxml.ping().getStatus());
    }
}
