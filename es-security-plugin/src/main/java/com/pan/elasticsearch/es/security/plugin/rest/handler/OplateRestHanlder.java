package com.pan.elasticsearch.es.security.plugin.rest.handler;

import static org.elasticsearch.rest.RestRequest.Method.DELETE;
import static org.elasticsearch.rest.RestRequest.Method.GET;
import static org.elasticsearch.rest.RestRequest.Method.POST;
import static org.elasticsearch.rest.RestRequest.Method.PUT;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.admin.cluster.stats.ClusterStatsResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.metrics.MeterMetric;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestRequest.Method;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.threadpool.ThreadPool;

import com.pan.elasticsearch.es.security.plugin.OplateEsPlugin;
import com.pan.elasticsearch.es.security.plugin.metric.MetricUtil;
import com.pan.elasticsearch.es.security.plugin.user.User;
import com.pan.elasticsearch.es.security.plugin.util.PluginUtil;

public class OplateRestHanlder extends BaseRestHandler {
    private final static Logger LOGGER = LogManager.getLogger(OplateRestHanlder.class);
    private final ThreadContext threadContext;
    public static ConcurrentMap<String, User> users = new ConcurrentHashMap<>();
    // 索引
    private static final String INDEXER = ".oplate";
    // 类型
    private static final String TYPE = "user";

    @Inject
    public OplateRestHanlder(final Settings settings, final RestController controller, final Client client,
                             ThreadPool threadPool) {
        super(settings);
        this.threadContext = threadPool.getThreadContext();
        controller.registerHandler(GET, "/_xpack", this);
        controller.registerHandler(POST, "/_oplate", this);
        controller.registerHandler(GET, "/_oplate", this);
        controller.registerHandler(DELETE, "/_oplate", this);
        controller.registerHandler(PUT, "/_oplate", this);
        controller.registerHandler(GET, "/_oplate/user", this);
        controller.registerHandler(GET, "/_oplate/metric", this);
        controller.registerHandler(GET, "/_oplate/metric/{enable}", this);
        controller.registerHandler(GET, "/_metric/{time}", this);
        controller.registerHandler(GET, "/_oplate/metric_all", this);
        controller.registerHandler(GET, "/_oplate/plugin/{enable}", this);
        controller.registerHandler(GET, "/_oplate_user/refresh", this);
        controller.registerFilter(new OplateRestFilter(threadContext));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        Method method = request.method();
        String path = request.path();
        String token = null;
        // 支持kibana插件
        String authorization = request.header("Authorization");
        if (authorization != null) {
            String[] temp = authorization.trim().split("\\s+");
            if (temp.length == 2) {
                token = temp[1];
            }
        } else {
            token = request.header("token");
        }
        // oplate表示用户的增删改查
        if ("/_oplate".equals(path) && !GET.equals(method)) {
            User user = null;
            if (request.content() != null) {
                user = PluginUtil.requestToUser(request);
            }
            switch (method) {
                case POST:
                    return addUser(request, token, user, client);
                case PUT:
                    return updateUser(request, token, user, client);
                case DELETE:
                    return deleteUser(request, token, user, client);
                default:
                    return createMessageResponse(request);
            }
        } else if ("/_oplate/user".equals(path)) {
            return getAllUsers(request, token, client);
        } else if ("/_oplate_user/refresh".equals(path)) {
            return refreshUser(token, client);
        } else if ("/_oplate/metric".equals(path)) {
            return getMetrics(request, token, client);
        } else if ("/_oplate/metric_all".equals(path)) {
            return getMetricsAll(request, token, client);
        } else if ("/_xpack".equals(path)) {
            return createXpackResponse(request);
        } else if (path.startsWith("/_oplate/plugin/")) {
            // 开启安全模块
            if (request.hasParam("enable")) {
                String enable = request.param("enable");
                if (enable.equals("true")) {
                    OplateEsPlugin.isEnable = false;
                    OplateRestFilter.init(client);
                    OplateEsPlugin.isEnable = true;
                    return channel -> {
                        XContentBuilder builder = channel.newBuilder();
                        builder.startObject();
                        builder.field("message", "插件被激活");
                        builder.endObject();
                        channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
                    };
                } else {
                    OplateEsPlugin.isEnable = false;
                    users = new ConcurrentHashMap<>();
                    return channel -> {
                        XContentBuilder builder = channel.newBuilder();
                        builder.startObject();
                        builder.field("message", "插件被关闭");
                        builder.endObject();
                        channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
                    };
                }
            }
            return createMessageResponse(request);
        } else if (path.startsWith("/_oplate/metric/")) {
            User user = users.get(token);
            // 开启安全模块
            if (request.hasParam("enable") && "oplate".equals(user.getUsername())) {
                String enable = request.param("enable");
                System.out.println("enable=" + enable);
                if (enable.equals("true")) {
                    MetricUtil.enableLimit = true;
                    return channel -> {
                        XContentBuilder builder = channel.newBuilder();
                        builder.startObject();
                        builder.field("message", "限速模式启用");
                        builder.endObject();
                        channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
                    };
                } else {
                    MetricUtil.enableLimit = false;
                    return channel -> {
                        XContentBuilder builder = channel.newBuilder();
                        builder.startObject();
                        builder.field("message", "限速模式关闭");
                        builder.endObject();
                        channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
                    };
                }
            }
            return createMessageResponse(request);
        } else if (path.startsWith("/_metric/")) {
            User user = users.get(token);
            if (request.hasParam("time") && "admin".equals(user.getRole())) {
                String time = request.param("time");
                try {
                    MetricUtil.time = Long.parseLong(time);
                    return channel -> {
                        XContentBuilder builder = channel.newBuilder();
                        builder.startObject();
                        builder.field("time", MetricUtil.time);
                        builder.endObject();
                        channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
                    };
                } catch (Exception e) {
                    return channel -> {
                        XContentBuilder builder = channel.newBuilder();
                        builder.startObject();
                        builder.field("message", "请填写正确的数字");
                        builder.endObject();
                        channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
                    };
                }
            }
            return createMessageResponse(request);
        } else {
            return createMessageResponse(request);
        }
    }

    private RestChannelConsumer refreshUser(String token, NodeClient client) {
        LOGGER.info("refresh" + token);
        SearchRequestBuilder request = null;
        Map<String, String> map = new HashMap<>();
        map.put("token", token);
        request = client.filterWithHeader(map).prepareSearch(INDEXER);
        // 初始化全部用户
        SearchResponse result = request.setTypes(TYPE).setSize(1000).execute().actionGet();
        result.getHits().forEach(hit -> {
            User user = PluginUtil.MapToUser(hit.getSource());
            String name = user.getUsername();
            String password = user.getPassword();
            String auth = PluginUtil.creatAuthorization(name, password);
            OplateRestHanlder.users.put(auth, user);
        });
        LOGGER.info("refresh:" + users);
        return channel -> {
            XContentBuilder builder = channel.newBuilder();
            builder.startObject();
            builder.field("msg", "用户刷新成功");
            builder.endObject();
            channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
        };
    }

    private RestChannelConsumer getMetricsAll(RestRequest request, String token, NodeClient client) {
        return channel -> {
            XContentBuilder total = channel.newBuilder();
            total.startObject();
            // 返回全部的度量数据
            for (String key : users.keySet()) {
                User user = users.get(key);
                MeterMetric meter = MetricUtil.getMeter(key);
                total.field(user.getUsername() + "_total", meter.count());
                total.field(user.getUsername() + "_one_minute_rate", meter.oneMinuteRate());
                total.field(user.getUsername() + "_five_minute_rate", meter.fiveMinuteRate());
                total.field(user.getUsername() + "_fifteen_minute_rate", meter.fifteenMinuteRate());
            }
            total.endObject();
            channel.sendResponse(new BytesRestResponse(RestStatus.OK, total));
        };
    }

    private RestChannelConsumer createXpackResponse(RestRequest request) {
        return channel -> {
            XContentBuilder builder = channel.newBuilder();
            XContentBuilder build = channel.newBuilder();
            build.startObject().field("hash", "7763f8e").field("date", "2016-10-26T04:51:59.202Z").endObject();
            XContentBuilder license = channel.newBuilder();
            license.startObject().field("uid", "b1bc57e8-3acb-4897-97f6-02f61f11e591").field("type", "trial")
                .field("mode", "trial").field("status", "active").field("expiry_date_in_millis", "1495641473210")
                .endObject();
            XContentBuilder features = channel.newBuilder();
            XContentBuilder graph = channel.newBuilder();
            graph.startObject().field("description", "Graph Data Exploration for the Elastic Stack")
                .field("available", "true")
                .field("enabled", "true").endObject();
            XContentBuilder monitoring = channel.newBuilder();
            monitoring.startObject().field("description", "Monitoring for the Elastic Stack")
                .field("available", "true")
                .field("enabled", "true").endObject();
            XContentBuilder security = channel.newBuilder();
            security.startObject().field("description", "Security for the Elastic Stack")
                .field("available", "true")
                .field("enabled", "true").endObject();
            XContentBuilder watcher = channel.newBuilder();
            watcher.startObject().field("description", "Alerting, Notification and Automation for the Elastic Stack")
                .field("available", "true")
                .field("enabled", "true").endObject();
            features.startObject().field("graph", graph).field("monitoring", monitoring).field("security", security)
                .field("watcher", watcher).endObject();
            builder.startObject();
            builder.field("build", build).field("license", license).field("features", features)
                .field("tagline", "You know, for X");
            builder.endObject();
            channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
        };
    }

    private RestChannelConsumer getAllUsers(RestRequest request, String auth, NodeClient client) {
        Map<String, String> map = new HashMap<String, String>();
        map.put("token", auth);
        SearchResponse response = client.filterWithHeader(map).prepareSearch(INDEXER).setSize(1000).setTypes(TYPE)
            .execute()
            .actionGet();
        return channel -> {
            XContentBuilder builder = channel.newBuilder();
            builder.startObject();
            List<Map<String, Object>> users = new ArrayList<Map<String, Object>>();
            for (SearchHit hit : response.getHits().getHits()) {
                users.add(hit.getSource());
            }
            builder.array("users", users);
            builder.endObject();
            channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
        };
    }

    private RestChannelConsumer getMetrics(RestRequest request, String auth, NodeClient client) {
        return channel -> {
            XContentBuilder builder = channel.newBuilder();
            MeterMetric meter = MetricUtil.getMeter(auth);
            builder.startObject();
            builder.field("total", meter.count());
            builder.field("one_minute_rate", meter.oneMinuteRate());
            builder.field("five_minute_rate", meter.fiveMinuteRate());
            builder.field("fifteen_minute_rate", meter.fifteenMinuteRate());
            builder.endObject();
            channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
        };
    }

    /**
     * 添加用户
     * @param user
     * @param client
     */
    private RestChannelConsumer addUser(RestRequest request, String auth, User user, NodeClient client) {
        LOGGER.info("users:" + users);
        User old = users.get(auth);
        if (old == null || !"admin".equals(old.getRole())) {
            return channel -> {
                XContentBuilder builder = channel.newBuilder();
                builder.startObject();
                builder.field("added", false);
                builder.field("message", "没有权限");
                builder.endObject();
                channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
            };
        }
        if (user == null || user.getUsername() == null
            || user.getPassword() == null) {
            return channel -> {
                XContentBuilder builder = channel.newBuilder();
                builder.startObject();
                builder.field("added", false);
                builder.field("message", "用户名密码不能为空");
                builder.endObject();
                channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
            };
        }
        for (User temp : users.values()) {
            if (temp.getUsername().equals(user.getUsername())) {
                return channel -> {
                    XContentBuilder builder = channel.newBuilder();
                    builder.startObject();
                    builder.field("added", false);
                    builder.field("message", "用户名已存在");
                    builder.endObject();
                    channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
                };
            }
        }
        String id = UUID.randomUUID().toString();
        user.setId(id);
        if (!"admin".equals(user.getRole())) {
            user.setRole("user");
        }
        Map<String, String> map = new HashMap<String, String>();
        map.put("token", auth);
        client.filterWithHeader(map).prepareIndex(INDEXER, TYPE, id).setSource(PluginUtil.UserToJSON(user))
            .setRefreshPolicy(RefreshPolicy.IMMEDIATE)
            .execute().actionGet();
        String name = user.getUsername();
        String password = user.getPassword();
        auth = PluginUtil.creatAuthorization(name, password);
        users.put(auth, user);
        notifyOtherNode(client);
        return channel -> {
            XContentBuilder builder = channel.newBuilder();
            builder.startObject();
            builder.field("added", true);
            builder.endObject();
            channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
        };
    }

    private void notifyOtherNode(Client client) {
        ClusterStatsResponse clusterStatsResponse = client.admin().cluster().prepareClusterStats().execute()
            .actionGet();
        clusterStatsResponse.getNodes().forEach(node -> {
            LOGGER.info("node.nodeInfo().getHostname()" + node.nodeInfo().getHostname());
            if (!settings.get("network.host", "localhost").equals(node.nodeInfo().getHostname())) {
                PluginUtil.sendGet("http://" + node.nodeInfo().getHostname() + ":"
                                   + node.nodeInfo().getSettings().get("http.port", "9200") + "/_oplate_user/refresh",
                                   PluginUtil.creatAuthorization("es_admin", "es_admin"));
            }
        });
    }

    /**
     * 更新用户
     * @param auth
     * @param user
     * @param client
     */
    private RestChannelConsumer updateUser(RestRequest request, String auth, User user, NodeClient client) {
        User old = users.get(auth);
        if (old == null || user == null) {
            return channel -> {
                XContentBuilder builder = channel.newBuilder();
                builder.startObject();
                builder.field("updated", false);
                builder.field("message", "用户不存在");
                builder.endObject();
                channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
            };
        } else if (user.getUsername() == null) {// 普通用户只能更新密码
            String name = old.getUsername();
            String password = user.getPassword();
            users.remove(auth);
            auth = PluginUtil.creatAuthorization(name, password);
            users.put(auth, user);
            User update = new User();
            update.setPassword(password);
            Map<String, String> map = new HashMap<String, String>();
            map.put("token", auth);
            client.filterWithHeader(map).prepareUpdate(INDEXER, TYPE, old.getId()).setDoc(PluginUtil.UserToJSON(update))
                .setRefreshPolicy(RefreshPolicy.IMMEDIATE)
                .execute().actionGet();
            notifyOtherNode(client);
            return channel -> {
                XContentBuilder builder = channel.newBuilder();
                builder.startObject();
                builder.field("updated", true);
                builder.endObject();
                channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
            };
        } else if ("admin".equals(old.getRole())) {// 管理员能够更新全部的用户 但是不能改别人的密码
            String name = user.getUsername();
            for (User temp : users.values()) {
                if (temp.getUsername().equals(name)) {
                    user.setId(temp.getId());
                    user.setPassword(null);
                    break;
                }
            }
            Map<String, String> map = new HashMap<String, String>();
            map.put("token", auth);
            client.filterWithHeader(map).prepareUpdate(INDEXER, TYPE, old.getId()).setDoc(PluginUtil.UserToJSON(user))
                .setRefreshPolicy(RefreshPolicy.IMMEDIATE)
                .execute().actionGet();
            notifyOtherNode(client);
            return channel -> {
                XContentBuilder builder = channel.newBuilder();
                builder.startObject();
                builder.field("updated", true);
                builder.endObject();
                channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
            };
        } else {
            return channel -> {
                XContentBuilder builder = channel.newBuilder();
                builder.startObject();
                builder.field("updated", false);
                builder.endObject();
                channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
            };
        }
    }

    /**
     * 用户
     * @param auth
     * @param user
     * @param client
     */
    private RestChannelConsumer deleteUser(RestRequest request, String auth, User user, NodeClient client) {
        User old = users.get(auth);
        // 只有管理员才能删除用户 用户不能删除自己
        if ("admin".equals(old.getRole()) && !old.getUsername().equals(user.getUsername())) {
            Collection<User> collection = users.values();
            for (User temp : collection) {
                // 删除用户名相同的用户
                if (temp.getUsername().equals(user.getUsername())) {
                    Map<String, String> map = new HashMap<String, String>();
                    map.put("token", auth);
                    client.filterWithHeader(map).prepareDelete(INDEXER, TYPE, temp.getId())
                        .setRefreshPolicy(RefreshPolicy.IMMEDIATE).execute().actionGet();
                    auth = PluginUtil.creatAuthorization(user.getUsername(), temp.getPassword());
                    users.remove(auth);
                }
            }
            notifyOtherNode(client);
            return channel -> {
                XContentBuilder builder = channel.newBuilder();
                builder.startObject();
                builder.field("deleted", true);
                builder.endObject();
                channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
            };
        }
        return channel -> {
            XContentBuilder builder = channel.newBuilder();
            builder.startObject();
            builder.field("deleted", false);
            builder.field("message", "没有权限");
            builder.endObject();
            channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
        };
    }

    private RestChannelConsumer createDoSomethingResponse(final RestRequest request, NodeClient client) {
        return channel -> {
            String action = request.param("action");
            Something message = new Something();
            message.action = action;
            XContentBuilder builder = channel.newBuilder();
            builder.startObject();
            message.toXContent(builder, request);
            builder.endObject();
            channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
        };
    }

    private RestChannelConsumer createMessageResponse(RestRequest request) {
        return channel -> {
            Message message = new Message();
            XContentBuilder builder = channel.newBuilder();
            builder.startObject();
            message.toXContent(builder, request);
            builder.endObject();
            channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
        };
    }

    class Message implements ToXContent {
        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            return builder.field("message", "This is oplate auth plugin");
        }
    }

    class Something implements ToXContent {
        public String action;

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            String strReturn;
            if (action != null) {
                strReturn = "I can do action " + action;
            } else {
                strReturn = "I can do anthing here. you order null";
            }
            return builder.field("some", strReturn);
        }
    }
}