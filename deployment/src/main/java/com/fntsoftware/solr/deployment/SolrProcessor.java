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
import org.jboss.jandex.DotName;
import org.jboss.jandex.Type;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.images.builder.dockerfile.statement.MultiArgsStatement;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;

class SolrProcessor {
    SolrDevserviceConfig config;
    static volatile DevServicesResultBuildItem.RunningDevService devService;

    private static final String FEATURE = "solr";
    private static final String CLIENT_PREFIX = "quarkus.solr.clients.";
    private static final String CLIENT_URL_SUFFIX = ".url";
    private static final String DEV_SERVICE_CORE_PREFIX = "quarkus.solr.devservices.cores.";
    private static final String DEV_SERVICE_CORE_CONFIG_PATH_SUFFIX = ".config-path";
    private static final Type SOLR_CLIENT_REGISTRY_TYPE =
            Type.create(DotName.createSimple(SolrClientRegistry.class.getName()), Type.Kind.CLASS);

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
                    .addInjectionPoint(SOLR_CLIENT_REGISTRY_TYPE)
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

        Map<String, SolrDevserviceConfig.CoreConfig> cores = new TreeMap<>(config.cores());
        if (!cores.isEmpty()) {
            return createMultiCoreContainer(cores);
        }
        return createSingleCoreContainer();
    }

    private DevServicesResultBuildItem createSingleCoreContainer() {
        String core = config.core().orElseThrow(
                () -> new IllegalStateException("quarkus.solr.devservices.core is required for single-core Solr Dev Service"));
        ImageFromDockerfile image = new ImageFromDockerfile("quarkus/devservices/solr")
                .withFileFromClasspath(".", "solr").withDockerfileFromBuilder(builder -> {
                    builder.from("solr:" + config.version()).withStatement(
                            new MultiArgsStatement("COPY --chown=solr:solr", ".", "/var/solr/data/" + core));
                });
        SolrContainer container = new SolrContainer(image, Set.of(core));
        container.start();
        Map<String, String> props = Map.of("quarkus.solr.url", "http://" + container.getHost() + ":"
                + container.getMappedPort(container.getPort()) + "/solr/" + core);
        devService = new DevServicesResultBuildItem.RunningDevService(FEATURE, container.getContainerId(),
                container::close, props);
        return devService.toBuildItem();
    }

    private DevServicesResultBuildItem createMultiCoreContainer(Map<String, SolrDevserviceConfig.CoreConfig> cores) {
        ImageFromDockerfile image = new ImageFromDockerfile("quarkus/devservices/solr");
        for (Map.Entry<String, SolrDevserviceConfig.CoreConfig> core : cores.entrySet()) {
            image.withFileFromClasspath(core.getKey(), core.getValue().configPath());
        }
        image.withDockerfileFromBuilder(builder -> {
            builder.from("solr:" + config.version());
            for (String core : cores.keySet()) {
                builder.withStatement(new MultiArgsStatement(
                        "COPY --chown=solr:solr", core, "/var/solr/data/" + core));
            }
        });

        SolrContainer container = new SolrContainer(image, cores.keySet());
        container.start();
        Map<String, String> props = new LinkedHashMap<>();
        for (String core : cores.keySet()) {
            props.put(CLIENT_PREFIX + core + CLIENT_URL_SUFFIX, solrCoreUrl(container, core));
        }
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
            return launchMode.isDevOrTest() && solrEnabled() && devServicesActive && config.enabled();
        }
    }

    private static class SolrContainer extends GenericContainer<SolrContainer> {
        static final int PORT = 8983;
        private final Set<String> cores;

        public SolrContainer(ImageFromDockerfile image, Set<String> cores) {
            super(image);
            this.cores = Set.copyOf(cores);
        }

        public int getPort() {
            return PORT;
        }

        @Override
        protected void configure() {
            super.configure();
            addExposedPort(PORT);
            waitingFor(waitForCores(cores));
        }
    }

    private static WaitStrategy waitForCores(Set<String> cores) {
        WaitAllStrategy wait = new WaitAllStrategy();
        for (String core : cores) {
            wait.withStrategy(Wait.forHttp("/solr/" + core + "/admin/ping")
                    .forPort(SolrContainer.PORT)
                    .forStatusCode(200));
        }
        return wait;
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

    private static String solrCoreUrl(SolrContainer container, String core) {
        return "http://" + container.getHost() + ":" + container.getMappedPort(container.getPort()) + "/solr/" + core;
    }
}
