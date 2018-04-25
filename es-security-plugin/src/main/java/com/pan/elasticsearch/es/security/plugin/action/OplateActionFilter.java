package com.pan.elasticsearch.es.security.plugin.action;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.ActionFilter;
import org.elasticsearch.action.support.ActionFilterChain;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;

import com.pan.elasticsearch.es.security.plugin.OplateEsPlugin;
import com.pan.elasticsearch.es.security.plugin.metric.MetricUtil;
import com.pan.elasticsearch.es.security.plugin.rest.handler.OplateRestHanlder;
import com.pan.elasticsearch.es.security.plugin.user.User;
import com.pan.elasticsearch.es.security.plugin.util.PluginUtil;

public class OplateActionFilter implements ActionFilter {
    // "internal:*",
    // "indices:monitor/*",
    // "cluster:monitor/*",
    // "cluster:admin/reroute",
    // "indices:admin/mapping/put"
    protected static final Logger log = LogManager.getLogger(OplateActionFilter.class);
    private final Settings settings;
    private final ThreadContext threadContext;
    private final Client client;
    private final ClusterService clusterService;
    private final IndexNameExpressionResolver indexNameExpressionResolver;

    @Inject
    public OplateActionFilter(final Settings settings,
                              ThreadPool threadPool, Client client, ClusterService clusterService,
                              IndexNameExpressionResolver indexNameExpressionResolver) {
        this.settings = settings;
        this.clusterService = clusterService;
        this.threadContext = threadPool.getThreadContext();
        OplateEsPlugin.threadContext = threadPool.getThreadContext();
        this.client = client;
        this.indexNameExpressionResolver = indexNameExpressionResolver;
        new MetricUtil(threadPool);
    }

    @Override
    public int order() {
        return Integer.MIN_VALUE;
    }

    // 请求拦截器 处理所有的请求 searchguard的默认不做过滤 shield会做拦截
    @Override
    public <Request extends ActionRequest<Request>, Response extends ActionResponse> void apply(Task task,
                                                                                                String action,
                                                                                                Request request,
                                                                                                ActionListener<Response> listener,
                                                                                                ActionFilterChain<Request, Response> chain) {
        // + request.remoteAddress());
        if (OplateEsPlugin.isEnable) {
            // es完全启动后会进行初始化
            if (OplateRestHanlder.users.size() == 0 && action.startsWith("cluster:monitor/")) {
                synchronized (log) {
                    if (OplateRestHanlder.users.size() == 0 && action.startsWith("cluster:monitor/")) {
                        try {
                            OplateRestHanlder.users.put("init", new User());
                            PluginUtil.sendGet("http://localhost:"
                                               + settings.get("http.port", "9200") + "/_oplate",
                                               PluginUtil.creatAuthorization(PluginUtil.ES_ADMIN, PluginUtil.ES_ADMIN));
                        } catch (Exception e) {
                            log.error("发生异常", e);
                        }
                    }
                }
            }
            if (action.startsWith("internal:gateway")
                || action.startsWith("cluster:monitor/")
                || action.startsWith("indices:monitor/")
                || action.startsWith("cluster:admin/reroute")
                || action.startsWith("indices:admin/mapping/put")
                || action.startsWith("internal:cluster/nodes/indices/shard/store")
                || action.startsWith("indices:admin/exists")
                || action.startsWith("internal:indices/admin/upgrade")) {
                chain.proceed(task, action, request, listener);
            } else {
                log.info("action " + action + " " + "threadContext.getHeaders()" + threadContext.getHeaders());
                Map<String, String> map = threadContext.getHeaders();
                if (OplateRestHanlder.users.size() == 0 || OplateRestHanlder.users.containsKey("init")) {
                    chain.proceed(task, action, request, listener);
                    return;
                }
                if (map == null || map.get("token") == null || OplateRestHanlder.users.get(map.get("token")) == null) {
                    listener.onFailure(new Exception("认证失败"));
                    return;
                }
                User user = OplateRestHanlder.users.get(map.get("token"));
                if ("admin".equals(user.getRole())) {
                    // 只对查询次数进行限制
                    if (action.startsWith("indices:data/read")) {
                        // 是否超速
                        if (!MetricUtil.isLimited(map.get("token"))) {
                            chain.proceed(task, action, request, listener);
                            // 没有get监听器 get在当前计数
                            if (action.startsWith("indices:data/read/get")) {
                                MetricUtil.getMeter(map.get("token")).mark();
                                log.info("meter=" + MetricUtil.getMeter(map.get("token")).count());
                            }
                            return;
                        } else {
                            listener.onFailure(new Exception("限制速度是" + MetricUtil.time + ",当前速度是"
                                                             + MetricUtil.getMeter(map.get("token")).oneMinuteRate()));
                        }
                    } else {
                        chain.proceed(task, action, request, listener);
                        return;
                    }
                }
                // 是否有权限
                if (processRole(user, request)) {
                    // 只对查询次数进行限制
                    if (action.startsWith("indices:data/read")) {
                        // 是否超速
                        if (!MetricUtil.isLimited(map.get("token"))) {
                            chain.proceed(task, action, request, listener);
                            // 没有get监听器 get在当前计数
                            if (action.startsWith("indices:data/read/get")) {
                                MetricUtil.getMeter(map.get("token")).mark();
                                log.info("meter=" + MetricUtil.getMeter(map.get("token")).count());
                            }
                            return;
                        } else {
                            listener.onFailure(new Exception("限制速度是" + MetricUtil.time + ",当前速度是"
                                                             + MetricUtil.getMeter(map.get("token")).oneMinuteRate()));
                        }
                    } else {
                        chain.proceed(task, action, request, listener);
                        return;
                    }
                }
                listener.onFailure(new Exception("认证失败"));
            }
        } else {
            chain.proceed(task, action, request, listener);
        }
    }

    /**
     * 判断是否有权限管理文档
     * @param user
     * @param request
     * @return
     */
    private <Request extends ActionRequest<Request>, Response extends ActionResponse> boolean processRole(User user,
                                                                                                          Request request) {
        try {
            Method method = request.getClass().getMethod("indices");
            if (method != null && method.getReturnType().isArray()) {
                String[] indcies = (String[]) method.invoke(request);
                if (user.getIndcies() == null || indcies == null || user.getIndcies().isEmpty()) {
                    return false;
                } else
                // 全部权限
                if (user.getIndcies().contains("*")) {
                    return true;
                }
                for (String userIndci : indcies) {
                    boolean flag = false;
                    // 判断权限
                    for (String indci : user.getIndcies()) {
                        // 如果以*结尾 表统配
                        if (indci.endsWith("*")) {
                            indci = indci.substring(0, indci.length() - 1);
                            if (userIndci.startsWith(indci)) {
                                flag = true;
                                break;
                            }
                        } else {
                            // 否则只有相等才能 通过
                            if (userIndci.equals(indci)) {
                                flag = true;
                                break;
                            }
                        }
                    }
                    // 和管理的权限都没匹配
                    if (!flag) {
                        return false;
                    }
                }
                return true;
            }
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    // 响应拦截器 ***据说shield字段权限控制 在该处实现***
    @Override
    public <Response extends ActionResponse> void apply(String action, Response response,
                                                        ActionListener<Response> listener,
                                                        ActionFilterChain<?, Response> chain) {
        chain.proceed(action, response, listener);
    }

    private int getShards(SearchRequest searchRequest) {
        ClusterState clusterState = clusterService.state();
        String[] concreteIndices = indexNameExpressionResolver.concreteIndexNames(clusterState, searchRequest);
        Map<String, Set<String>> routingMap = indexNameExpressionResolver.resolveSearchRouting(clusterState,
                                                                                               searchRequest.routing(),
                                                                                               searchRequest.indices());
        int shardCount = clusterService.operationRouting().searchShardsCount(clusterState, concreteIndices, routingMap);
        return shardCount;
    }
    // protected ShardIterator shards(ClusterState state, InternalRequest request) {
    // return clusterService.operationRouting()
    // .getShards(clusterService.state(), request.concreteIndex(), request.request().id(),
    // request.request().routing(), request.request().preference());
    // }
}
