package com.pan.elasticsearch.es.security.plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.support.ActionFilter;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Setting.Property;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.index.IndexModule;
import org.elasticsearch.plugins.ActionPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.rest.RestHandler;

import com.pan.elasticsearch.es.security.plugin.action.OplateActionFilter;
import com.pan.elasticsearch.es.security.plugin.http.HttpMoudle;
import com.pan.elasticsearch.es.security.plugin.rest.handler.OplateRestHanlder;
import com.pan.elasticsearch.es.security.plugin.search.listener.OplateSearchListener;

public final class OplateEsPlugin extends Plugin implements ActionPlugin {
    private final static Logger LOGGER = LogManager.getLogger(OplateEsPlugin.class);
    private final Settings settings;
    public static volatile boolean isEnable = true;
    public static ThreadContext threadContext;

    // 初始化方法 必须是有参构造
    public OplateEsPlugin(Settings settings) {
        super();
        this.settings = settings;
        LOGGER.warn("Create the Basic Plugin and installed it into elasticsearch");
    }

    // 添加自己的resthandler GET /_oplate
    @Override
    public List<Class<? extends RestHandler>> getRestHandlers() {
        return Collections.singletonList(OplateRestHanlder.class);
    }

    // 拦截器，可以拦截所有的action 在node启动时自动加载拦截器队列
    @Override
    public List<Class<? extends ActionFilter>> getActionFilters() {
        List<Class<? extends ActionFilter>> filters = new ArrayList<>(1);
        filters.add(OplateActionFilter.class);
        return filters;
    }

    // 创建自定义moudle 目前不知道怎么用
    @Override
    public Collection<Module> createGuiceModules() {
        List<Module> modules = new ArrayList<Module>(1);
        modules.add(new HttpMoudle(settings));
        LOGGER.warn("add HttpMoudle");
        return modules;
    }
    // 增加自定义action 查询 增加文档等等都是action 在node启动时会加载自定义的action
    // @Override
    // public List<ActionHandler<? extends ActionRequest<?>, ? extends ActionResponse>> getActions() {
    // List<ActionHandler<? extends ActionRequest<?>, ? extends ActionResponse>> actions = new ArrayList<>(1);
    // if(!tribeNodeClient) {
    // actions.add(new ActionHandler(ConfigUpdateAction.INSTANCE, TransportConfigUpdateAction.class));
    // }
    // return actions;
    // }

    /*
     * 
     * @see org.elasticsearch.plugins.Plugin#onIndexModule(org.elasticsearch.index.IndexModule)
     */
    public void onIndexModule(IndexModule indexModule) {
        indexModule.addSearchOperationListener(new OplateSearchListener(threadContext));
    }

    // @Override
    // public Settings additionalSettings() {
    // final Settings.Builder builder = Settings.builder();
    // builder.put(NetworkModule.TRANSPORT_TYPE_KEY,
    // "com.floragunn.searchguard.ssl.http.netty.SearchGuardSSLNettyTransport");
    // builder.put(NetworkModule.HTTP_TYPE_KEY, "com.floragunn.searchguard.http.SearchGuardHttpServerTransport");
    // return builder.build();
    // }
    // 节点配置 启动时会验证，不能在客户端随意添加
    @Override
    public List<Setting<?>> getSettings() {
        List<Setting<?>> settings = new ArrayList<Setting<?>>();
        settings.add(Setting.simpleString("oplate.es.username", Property.NodeScope, Property.Filtered));
        settings.add(Setting.simpleString("oplate.es.password", Property.NodeScope, Property.Filtered));
        return settings;
    }

    // 作用未知
    @Override
    public List<String> getSettingsFilter() {
        List<String> settingsFilter = new ArrayList<>();
        settingsFilter.add("oplate.es.*");
        return settingsFilter;
    }
}
