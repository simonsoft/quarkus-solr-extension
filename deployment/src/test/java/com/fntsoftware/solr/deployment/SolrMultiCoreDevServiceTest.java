package com.fntsoftware.solr.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.QuarkusUnitTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.solr.client.solrj.SolrClient;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SolrMultiCoreDevServiceTest {

    private static final String SOLRCONFIG = """
            <config>
              <luceneMatchVersion>9.6.1</luceneMatchVersion>
              <directoryFactory name="DirectoryFactory" class="${solr.directoryFactory:solr.NRTCachingDirectoryFactory}"/>
              <schemaFactory class="ClassicIndexSchemaFactory"/>
              <updateHandler class="solr.DirectUpdateHandler2"/>
              <requestHandler name="/select" class="solr.SearchHandler"/>
              <requestHandler name="/admin/ping" class="solr.PingRequestHandler">
                <lst name="invariants">
                  <str name="q">*:*</str>
                </lst>
              </requestHandler>
            </config>
            """;

    private static final String SCHEMA = """
            <schema name="test" version="1.6">
              <fieldType name="string" class="solr.StrField"/>
              <field name="id" type="string" indexed="true" stored="true" required="true"/>
              <uniqueKey>id</uniqueKey>
            </schema>
            """;

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("""
                            quarkus.solr.enabled=true
                            quarkus.solr.devservices.cores.repositem.config-path=solr/repositem
                            quarkus.solr.devservices.cores.reposxml.config-path=solr/reposxml
                            """), "application.properties")
                    .addAsResource(new StringAsset("""
                            name=repositem
                            """), "solr/repositem/core.properties")
                    .addAsResource(new StringAsset(SOLRCONFIG), "solr/repositem/conf/solrconfig.xml")
                    .addAsResource(new StringAsset(SCHEMA), "solr/repositem/conf/schema.xml")
                    .addAsResource(new StringAsset("""
                            name=reposxml
                            """), "solr/reposxml/core.properties")
                    .addAsResource(new StringAsset(SOLRCONFIG), "solr/reposxml/conf/solrconfig.xml")
                    .addAsResource(new StringAsset(SCHEMA), "solr/reposxml/conf/schema.xml"));

    @Inject
    @Named("repositem")
    SolrClient repositem;

    @Inject
    @Named("reposxml")
    SolrClient reposxml;

    @Test
    void shouldStartSolrWithMultipleCores() throws Exception {
        assertEquals(0, repositem.ping().getStatus());
        assertEquals(0, reposxml.ping().getStatus());
    }
}
