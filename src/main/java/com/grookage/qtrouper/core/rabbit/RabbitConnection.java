/*
 * Copyright 2019 Koushik R <rkoushik.14@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.grookage.qtrouper.core.rabbit;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.impl.StandardMetricsCollector;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import javax.inject.Singleton;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * @author koushik
 */
@Slf4j
@Singleton
@Getter
@SuppressWarnings("unused")
public class RabbitConnection {

    private static final String TLS = "TLSv1.2";

    private final RabbitConfiguration config;
    private final MetricRegistry metricRegistry;
    private Connection connection;
    private Channel channel;

    public RabbitConnection(RabbitConfiguration rabbitConfiguration,
                            MetricRegistry metricRegistry) {
        this.config = rabbitConfiguration;
        this.metricRegistry = metricRegistry;
    }

    /**
     * Starts the RabbitMQ Connection.
     * Sets the required username, password and other connection settings.
     * Creates both the connection and a default channel which would later be used for publish
     */
    @SneakyThrows
    public void start() {
        log.info("Starting Rabbit Connection");
        final var factory = new ConnectionFactory();
        if (!Strings.isNullOrEmpty(config.getUserName())) {
            factory.setUsername(config.getUserName());
        }
        if (!Strings.isNullOrEmpty(config.getPassword())) {
            factory.setPassword(config.getPassword());
        }
        if (!Strings.isNullOrEmpty(config.getVirtualHost())) {
            factory.setVirtualHost(config.getVirtualHost());
        }
        if (config.isSslEnabled()) {
            configureSsl(factory);
        }
        if (config.isMetricsEnabled() && null != metricRegistry) {
            factory.setMetricsCollector(new StandardMetricsCollector(metricRegistry));
        }
        factory.setAutomaticRecoveryEnabled(true);
        factory.setTopologyRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(3000);
        factory.setRequestedHeartbeat(60);
        connection = factory.newConnection(Executors.newFixedThreadPool(config.getThreadPoolSize()), config.getBrokers()
                .stream()
                .map(broker -> new Address(broker.getHost(), broker.getPort()))
                .toArray(Address[]::new));
        channel = connection.createChannel();
        log.info("Started Rabbit Connection");
    }

    /**
     * Destroys the channel and connection.
     * Gets triggered during shutdown
     */
    @SneakyThrows
    public void stop() {
        if (null != channel && channel.isOpen()) {
            channel.close();
        }
        if (null != connection && connection.isOpen()) {
            connection.close();
        }
    }

    @SneakyThrows
    private void configureSsl(ConnectionFactory factory) {
        final var trustManagers = Strings.isNullOrEmpty(config.getTrustStorePath())
                ? null : buildTrustManagers();
        final var keyManagers = Strings.isNullOrEmpty(config.getKeyStorePath())
                ? null : buildKeyManagers();

        if (trustManagers != null || keyManagers != null) {
            final var protocol = Strings.isNullOrEmpty(config.getTlsProtocol())
                    ? TLS : config.getTlsProtocol();
            final var sslContext = SSLContext.getInstance(protocol);
            sslContext.init(keyManagers, trustManagers, null);
            factory.useSslProtocol(sslContext);
        } else {
            factory.useSslProtocol();
        }

        final var ciphers = config.getCiphers();
        if (ciphers != null && !ciphers.isEmpty()) {
            warnUnsupportedCiphers(ciphers);
            factory.setSocketConfigurator(socket -> {
                if (socket instanceof SSLSocket sslSocket) {
                    sslSocket.setEnabledCipherSuites(ciphers.toArray(new String[0]));
                }
            });
        }
    }

    @SneakyThrows
    private TrustManager[] buildTrustManagers() {
        Preconditions.checkNotNull(config.getTrustStorePassword(),
                "Trust store password is required if trust store path has been provided");
        final var trustStore = KeyStore.getInstance(config.getTrustStoreType());
        try (var stream = new FileInputStream(config.getTrustStorePath())) {
            trustStore.load(stream, config.getTrustStorePassword().toCharArray());
        }
        final var tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        return tmf.getTrustManagers();
    }

    @SneakyThrows
    private KeyManager[] buildKeyManagers() {
        Preconditions.checkNotNull(config.getKeyStorePassword(),
                "Key store password is required if key store path has been provided");
        final var keyStore = KeyStore.getInstance(config.getKeyStoreType());
        try (var stream = new FileInputStream(config.getKeyStorePath())) {
            keyStore.load(stream, config.getKeyStorePassword().toCharArray());
        }
        final var kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, config.getKeyStorePassword().toCharArray());
        return kmf.getKeyManagers();
    }

    private void warnUnsupportedCiphers(List<String> ciphers) {
        try {
            final var supported = Set.of(
                    SSLContext.getDefault().getDefaultSSLParameters()
                            .getCipherSuites());
            ciphers.stream()
                    .filter(c -> !supported.contains(c))
                    .forEach(c -> log.warn(
                            "Configured cipher '{}' is not supported by this JVM. "
                                    + "Ensure you are using Java cipher names "
                                    + "(e.g. TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384), "
                                    + "not OpenSSL names (e.g. ECDHE-RSA-AES256-GCM-SHA384)",
                            c));
        } catch (Exception e) {
            log.warn("Unable to validate configured ciphers", e);
        }
    }

    public Channel channel() {
        return channel;
    }

    @SneakyThrows
    public Channel newChannel() {
        return connection.createChannel();
    }

    public void ensure(final String queueName,
                       final String exchange,
                       final Map<String, Object> rmqOpts) {
        ensure(queueName, queueName, exchange, rmqOpts);
    }

    public void ensure(final String queueName,
                       final String routingQueue,
                       final String exchange) {
        ensure(queueName, routingQueue, exchange, rmqOpts());
    }

    @SneakyThrows
    public void ensure(final String queueName,
                       final String routingQueue,
                       final String exchange,
                       final Map<String, Object> rmqOpts) {
        channel.queueDeclare(queueName, true, false, false, rmqOpts);
        channel.queueBind(queueName, exchange, routingQueue);
        log.info("Created queue: {}", queueName);
    }

    public Map<String, Object> rmqOpts(int maxPriority) {
        final var priorityOpts = rmqOpts();
        if (maxPriority > 0) {
            priorityOpts.put("x-max-priority", maxPriority);
        }
        return priorityOpts;
    }

    public Map<String, Object> rmqOpts() {
        final var opts = new HashMap<String, Object>();
        opts.put("x-ha-policy", "all");
        opts.put("ha-mode", "all");
        return opts;
    }

    public Map<String, Object> rmqOpts(String deadLetterExchange,
                                       String routingKey) {
        final var retryOpts = rmqOpts();
        retryOpts.put("x-dead-letter-exchange", deadLetterExchange);
        retryOpts.put("x-dead-letter-routing-key", routingKey);
        return retryOpts;
    }
}
