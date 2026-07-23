# Solr

Quarkus extension with a provider for
the [Solrj](https://solr.apache.org/guide/solr/latest/deployment-guide/solrj.html) `SolrClient` and a devservice for the
Solr container.

## Configuration

The extension is opt-in. Enable it in `application.properties` when the application should inject Solr clients:

```properties
quarkus.solr.enabled=true
```

For a single-core Dev Service, set the core name. The extension expects the Solr core config in the `solr` directory on
the application classpath:

```properties
quarkus.solr.devservices.enabled=true
quarkus.solr.devservices.core=<your core name>
quarkus.solr.devservices.version=9.6.1
```

For multi-core Dev Services, configure one classpath config directory per core:

```properties
quarkus.solr.devservices.cores.repositem.config-path=se/repos/indexing/solr/repositem
quarkus.solr.devservices.cores.reposxml.config-path=se/simonsoft/cms/indexing/xml/solr/reposxml
```

Each configured core can then be injected as a named client:

```java
@Inject
@Named("repositem")
SolrClient repositem;

@Inject
@Named("reposxml")
SolrClient reposxml;
```

### Prod

For production usage the configurations above do not matter. Only the URL of the Solr instance to connect to needs to be
provided:

```properties
quarkus.solr.enabled=true
quarkus.solr.url=https://mydomain.fun/solr/mycore
quarkus.solr.request-timeout=0
quarkus.solr.idle-timeout=60000
quarkus.solr.connection-timeout=60000
#quarkus.solr.default-collection=
quarkus.solr.follow-redirects=false
quarkus.solr.use-http1-1=false
#quarkus.solr.auth.password=
#quarkus.solr.auth.username=
```

> In development mode this URL configuration is done automatically and filled with the host, port and core name of the
> devservice container

For multiple external cores, configure named clients directly:

```properties
quarkus.solr.enabled=true
quarkus.solr.clients.repositem.url=https://mydomain.fun/solr/repositem
quarkus.solr.clients.reposxml.url=https://mydomain.fun/solr/reposxml
```

## Usage

The extension comes with a provider for a `SolrClient` bean, which is connected to the configured Solr
server. After that, follow
the [usage guide of SolrJ](https://solr.apache.org/guide/solr/latest/deployment-guide/solrj.html).

## Contribute

Fork, push changes to your fork, create PR to upstream (here) :)
