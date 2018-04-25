package com.pan.elasticsearch.es.security.plugin.http;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.http.HttpServer;
import org.elasticsearch.http.HttpServerTransport;
import org.elasticsearch.indices.breaker.CircuitBreakerService;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;

public class HttpBasicServer extends HttpServer {
    private final Logger log = LogManager.getLogger(this.getClass());

    // private final String user;
    // private final String password;
    @Inject
    public HttpBasicServer(Settings settings, HttpServerTransport transport, RestController restController,
                           NodeClient client, CircuitBreakerService circuitBreakerService) {
        super(settings, transport, restController, client, circuitBreakerService);
        // this.user = settings.get("http.basic.user", "admin");
        // this.password = settings.get("http.basic.password", "admin_pw");
        // final boolean whitelistEnabled = settings.getAsBoolean("http.basic.ipwhitelist", true);
        // String[] whitelisted = new String[0];
        // if (whitelistEnabled) {
        // whitelisted = settings.getAsArray("http.basic.ipwhitelist", new String[] { "localhost", "127.0.0.1" });
        // }
    }

    @Override
    public void dispatchRequest(RestRequest request, RestChannel channel, ThreadContext threadContext) {
        super.dispatchRequest(request, channel, threadContext);
        System.out.println("panfaguang server");
        log.info("panfaguang server");
    }
}
