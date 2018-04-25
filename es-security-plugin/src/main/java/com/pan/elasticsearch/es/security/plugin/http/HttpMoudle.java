package com.pan.elasticsearch.es.security.plugin.http;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.settings.Settings;

public class HttpMoudle extends AbstractModule {
    private final Logger log = LogManager.getLogger(this.getClass());

    // 初始化方法 必须是有参构造
    public HttpMoudle(final Settings settings) {
        super();
    }

    @Override
    protected void configure() {
        // bind(HttpServer.class).to(HttpBasicServer.class).asEagerSingleton();
    }
}