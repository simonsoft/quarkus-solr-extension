package com.fntsoftware.solr.runtime;

import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.SyntheticCreationalContext;
import org.apache.solr.client.solrj.SolrClient;

public class NamedSolrClientCreator implements BeanCreator<SolrClient> {

    public static final String NAME_PARAM = "name";

    @Override
    public SolrClient create(SyntheticCreationalContext<SolrClient> context) {
        String name = (String) context.getParams().get(NAME_PARAM);
        return context.getInjectedReference(SolrClientRegistry.class).namedClientForBean(name);
    }
}
