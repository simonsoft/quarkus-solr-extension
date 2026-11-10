package com.fntsoftware.solr.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.QuarkusUnitTest;
import jakarta.inject.Inject;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.SolrInputDocument;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SolrSingleCoreDevServiceTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("""
                            quarkus.solr.enabled=true
                            quarkus.solr.devservices.core=single
                            """), "application.properties")
                    // A non-default config filename verifies that core.properties is preserved.
                    .addAsResource(new StringAsset("""
                            name=single
                            config=custom-solrconfig.xml
                            """), "solr/core.properties")
                    .addAsResource(new StringAsset(SolrMultiCoreDevServiceTest.SOLRCONFIG),
                            "solr/conf/custom-solrconfig.xml")
                    .addAsResource(new StringAsset(SolrMultiCoreDevServiceTest.SCHEMA), "solr/conf/schema.xml"));

    @Inject
    SolrClient solr;

    @Test
    void shouldPreserveSingleCoreConfigurationAndAllowWrites() throws Exception {
        assertEquals(0, solr.ping().getStatus());
        SolrInputDocument document = new SolrInputDocument();
        document.addField("id", "single");
        solr.add(document);
        solr.commit();
        assertEquals("single", solr.query(new SolrQuery("*:*")).getResults().get(0).getFieldValue("id"));
    }
}
