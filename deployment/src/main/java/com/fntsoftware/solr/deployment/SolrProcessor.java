package com.fntsoftware.solr.deployment;

import com.fntsoftware.solr.runtime.SolrClientProducer;
import com.fntsoftware.solr.runtime.SolrClientRegistry;
import com.fntsoftware.solr.runtime.SolrDevserviceConfig;
import com.fntsoftware.solr.runtime.NamedSolrClientCreator;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.CuratedApplicationShutdownBuildItem;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.runtime.LaunchMode;
import jakarta.inject.Singleton;
import org.apache.solr.client.solrj.SolrClient;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.images.builder.dockerfile.statement.MultiArgsStatement;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;

class SolrProcessor {
    SolrDevserviceConfig config;
    static volatile DevServicesResultBuildItem.RunningDevService devService;

    private static final String FEATURE = "solr";
    private static final String CLIENT_PREFIX = "quarkus.solr.clients.";
    private static final String CLIENT_URL_SUFFIX = ".url";
    private static final String DEV_SERVICE_CORE_PREFIX = "quarkus.solr.devservices.cores.";
    private static final String DEV_SERVICE_CORE_CONFIG_PATH_SUFFIX = ".config-path";

    @BuildStep
    public AdditionalBeanBuildItem producer() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(SolrClientProducer.class)
                .addBeanClass(SolrClientRegistry.class)
                .setUnremovable()
                .build();
    }

    @BuildStep
    public void namedSolrClients(BuildProducer<SyntheticBeanBuildItem> syntheticBeans) {
        if (!solrEnabled()) {
            return;
        }
        for (String name : configuredNamedClients()) {
            syntheticBeans.produce(SyntheticBeanBuildItem.configure(SolrClient.class)
                    .scope(Singleton.class)
                    .named(name)
                    .unremovable()
                    .creator(NamedSolrClientCreator.class)
                    .param(NamedSolrClientCreator.NAME_PARAM, name)
                    .done());
        }
    }

    @BuildStep(onlyIfNot = IsNormal.class, onlyIf = WantsSolrDevService.class)
    public DevServicesResultBuildItem createContainer(CuratedApplicationShutdownBuildItem closeBuildItem) {
        if (devService != null) {
            return null;
        }
        Runnable closeTask = () -> {
            if (devService != null) {
                try {
                    devService.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            devService = null;
        };
        closeBuildItem.addCloseTask(closeTask, false);
        String core = singleCoreName();
        ImageFromDockerfile image = new ImageFromDockerfile("quarkus/devservices/solr")
                .withFileFromClasspath(".", "solr").withDockerfileFromBuilder(builder -> {
                    builder.from("solr:" + config.version()).withStatement(
                            new MultiArgsStatement("COPY --chown=solr:solr", ".", "/var/solr/data/" + core));
                });
        SolrContainer container = new SolrContainer(image);
        container.start();
        Map<String, String> props = Map.of("quarkus.solr.url", "http://" + container.getHost() + ":"
                + container.getMappedPort(container.getPort()) + "/solr/" + core);
        devService = new DevServicesResultBuildItem.RunningDevService(FEATURE, container.getContainerId(),
                container::close, props);
        return devService.toBuildItem();
    }

    static class WantsSolrDevService implements BooleanSupplier {
        LaunchMode launchMode;
        SolrDevserviceConfig config;

        public boolean getAsBoolean() {
            Boolean devServicesActive = ConfigProvider.getConfig().getValue("quarkus.devservices.enabled",
                    Boolean.class);
            return launchMode.isDevOrTest() && devServicesActive && config.enabled();
        }
    }

    private static class SolrContainer extends GenericContainer<SolrContainer> {
        static final int PORT = 8983;

        public SolrContainer(ImageFromDockerfile image) {
            super(image);
        }

        public int getPort() {
            return PORT;
        }

        @Override
        protected void configure() {
            super.configure();
            addExposedPort(PORT);
            waitingFor(Wait.forLogMessage(".*Started Server.*", 1));
        }
    }

    private static boolean solrEnabled() {
        return ConfigProvider.getConfig().getOptionalValue("quarkus.solr.enabled", Boolean.class).orElse(false);
    }

    private static Set<String> configuredNamedClients() {
        Config config = ConfigProvider.getConfig();
        Set<String> names = new TreeSet<>();
        for (String propertyName : config.getPropertyNames()) {
            clientName(propertyName, CLIENT_PREFIX, CLIENT_URL_SUFFIX).ifPresent(names::add);
            clientName(propertyName, DEV_SERVICE_CORE_PREFIX, DEV_SERVICE_CORE_CONFIG_PATH_SUFFIX).ifPresent(names::add);
        }
        return names;
    }

    private static java.util.Optional<String> clientName(String propertyName, String prefix, String suffix) {
        if (!propertyName.startsWith(prefix) || !propertyName.endsWith(suffix)) {
            return java.util.Optional.empty();
        }
        String name = propertyName.substring(prefix.length(), propertyName.length() - suffix.length());
        return name.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(name);
    }

    private String singleCoreName() {
        return config.core().orElseThrow(
                () -> new IllegalStateException("quarkus.solr.devservices.core is required for single-core Solr Dev Service"));
    }
}
