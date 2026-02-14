/*
 * Copyright 2017-2024, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.test.container;

import com.github.dockerjava.api.command.InspectContainerResponse;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Custom Kafka container that extends StrimziKafkaContainer to:
 * <ul>
 *     <li>Prepend SNAPSHOT OAuth JARs to classpath (taking precedence over bundled versions)</li>
 *     <li>Support SCRAM user setup during storage formatting</li>
 *     <li>Support Prometheus JMX agent configuration</li>
 * </ul>
 */
@SuppressFBWarnings("EQ_DOESNT_OVERRIDE_EQUALS")
public class OAuthKafkaContainer extends StrimziKafkaContainer {

    private static final Path START_SCRIPT_PATH = Path.of("../docker/kafka/scripts/start.sh").toAbsolutePath();

    private final List<String> scramUsers = new ArrayList<>();
    private Map<String, String> kafkaConfig = new HashMap<>();
    private int nodeId;

    public OAuthKafkaContainer(String dockerImageName) {
        super(dockerImageName);
        withCopyToContainer(
                MountableFile.forHostPath(START_SCRIPT_PATH),
                "/opt/kafka/scripts/start.sh"
        );
    }

    // ---- Public delegating methods ----
    // StrimziKafkaContainer is package-private, so callers outside this package
    // cannot access inherited methods through it. These overrides provide public
    // access with OAuthKafkaContainer return types.

    @Override
    public OAuthKafkaContainer withNetwork(Network network) {
        super.withNetwork(network);
        return this;
    }

    @Override
    public OAuthKafkaContainer withNetworkAliases(String... aliases) {
        super.withNetworkAliases(aliases);
        return this;
    }

    @Override
    public OAuthKafkaContainer waitingFor(WaitStrategy waitStrategy) {
        super.waitingFor(waitStrategy);
        return this;
    }

    @Override
    public OAuthKafkaContainer withKafkaConfigurationMap(Map<String, String> additionalConfig) {
        this.kafkaConfig = new HashMap<>(additionalConfig);
        super.withKafkaConfigurationMap(additionalConfig);
        return this;
    }

    @Override
    public OAuthKafkaContainer withCopyToContainer(Transferable transferable, String containerPath) {
        super.withCopyToContainer(transferable, containerPath);
        return this;
    }

    @Override
    public OAuthKafkaContainer withNodeId(int nodeId) {
        this.nodeId = nodeId;
        super.withNodeId(nodeId);
        return this;
    }

    /**
     * Expose the protected addFixedExposedPort as public.
     *
     * @param hostPort The host port to bind
     * @param containerPort The container port to expose
     */
    public void addFixedExposedPort(int hostPort, int containerPort) {
        super.addFixedExposedPort(hostPort, containerPort);
    }

    /**
     * Add a SCRAM-SHA-512 user to be created during storage format.
     *
     * @param username The username
     * @param password The password
     * @return this container
     */
    public OAuthKafkaContainer withScramUser(String username, String password) {
        scramUsers.add("SCRAM-SHA-512=[name=" + username + ",password=" + password + "]");
        addEnv("OAUTH_SCRAM_USERS", String.join(";", scramUsers));
        return this;
    }

    /**
     * Enable Prometheus JMX metrics.
     *
     * @param metricsConfigPath The path to the metrics config file inside the container
     * @return this container
     */
    public OAuthKafkaContainer withMetrics(String metricsConfigPath) {
        addEnv("OAUTH_METRICS_CONFIG", metricsConfigPath);
        return this;
    }

    /**
     * Copy the OAuth SNAPSHOT JARs from the build output into the container.
     *
     * @param libsDir The local directory containing the OAuth JARs
     * @return this container
     */
    public OAuthKafkaContainer withOAuthJars(Path libsDir) {
        File dir = libsDir.toFile();
        if (dir.exists() && dir.isDirectory()) {
            File[] jars = dir.listFiles((d, name) -> name.endsWith(".jar"));
            if (jars != null) {
                for (File jar : jars) {
                    withCopyToContainer(MountableFile.forHostPath(jar.getAbsolutePath()),
                            "/opt/kafka/libs/strimzi/" + jar.getName());
                }
            }
        }
        return this;
    }

    /**
     * Copy files from a host directory into the container at the specified path.
     *
     * @param hostDir The local directory whose files to copy
     * @param containerPath The destination directory inside the container
     * @return this container
     */
    public OAuthKafkaContainer withCopyDirToContainer(Path hostDir, String containerPath) {
        File dir = hostDir.toFile();
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        withCopyToContainer(MountableFile.forHostPath(file.getAbsolutePath()),
                                containerPath + "/" + file.getName());
                    }
                }
            }
        }
        return this;
    }

    @Override
    protected void containerIsStarting(InspectContainerResponse containerInfo, boolean reused) {
        // Skip StrimziKafkaContainer's auto-generated listener configuration
        // (PLAINTEXT, BROKER1, etc.) which conflicts with our custom listener setup.
        // Write server.properties directly from the user-provided configuration.
        Properties properties = new Properties();
        properties.setProperty("node.id", String.valueOf(nodeId));
        properties.setProperty("log.dirs", "/tmp/kraft-combined-logs");
        kafkaConfig.forEach(properties::setProperty);

        StringWriter writer = new StringWriter();
        try {
            properties.store(writer, null);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write server properties", e);
        }

        copyFileToContainer(
                Transferable.of(writer.toString().getBytes(StandardCharsets.UTF_8)),
                "/opt/kafka/config/kraft/server.properties");
    }

    @Override
    protected void waitUntilContainerStarted() {
        // Use the configured WaitStrategy instead of StrimziKafkaContainer's default
        // readiness check, which expects the auto-generated proxy listener that our
        // custom listener configuration replaces.
        getWaitStrategy().waitUntilReady(this);
    }

    @Override
    protected String runStarterScript() {
        return "bash /opt/kafka/scripts/start.sh";
    }
}
