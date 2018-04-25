package com.pan.elasticsearch.es.security.plugin.rest.handler;

import static org.elasticsearch.rest.RestRequest.Method.GET;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestFilter;
import org.elasticsearch.rest.RestFilterChain;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;

import com.pan.elasticsearch.es.security.plugin.OplateEsPlugin;
import com.pan.elasticsearch.es.security.plugin.user.User;
import com.pan.elasticsearch.es.security.plugin.util.PluginUtil;

public class OplateRestFilter extends RestFilter {
    private final static Logger LOGGER = LogManager.getLogger(OplateRestFilter.class);
    // 索引
    private static final String INDEXER = ".oplate";
    // 类型
    private static final String TYPE = "user";
    private final ThreadContext threadContext;

    public OplateRestFilter(ThreadContext threadContext) {
        super();
        this.threadContext = threadContext;
    }

    @Override
    public void process(RestRequest request, RestChannel channel, NodeClient client,
                        RestFilterChain filterChain) throws Exception {
        // 只有插件被激活才会生效
        if (OplateEsPlugin.isEnable) {
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
            if (OplateRestHanlder.users.size() == 0 || OplateRestHanlder.users.containsKey("init")) {
                synchronized (LOGGER) {
                    init(client);
                    OplateRestHanlder.users.remove("init");
                    LOGGER.info("初始化完成" + request.path() + " " + OplateRestHanlder.users.size());
                }
            }
            // 不拦截系统安全路径 /_cluster/health /_nodes
            if ((request.path().equals("/") || request.path().startsWith("/_nodes")
                 || request.path().startsWith("/_cluster") || request.path().startsWith("/_stats"))
                && request.method().equals(GET)) {
                filterChain.continueProcessing(request, channel, client);
                return;
            }
            LOGGER.info("token:" + token + " request.path():" + request.path());
            if (token == null) {
                BytesRestResponse response = new BytesRestResponse(channel, RestStatus.UNAUTHORIZED,
                                                                   new ElasticsearchException(""));
                response.addHeader("WWW-authenticate", "Basic realm=\"请输入管理员密码\"");
                channel.sendResponse(response);
                return;
            } else {
                if (OplateRestHanlder.users.get(token) != null) {
                    threadContext.putHeader("token", token);
                    filterChain.continueProcessing(request, channel, client);
                    return;
                }
                throw new Exception("认证失败");
            }
        } else {
            filterChain.continueProcessing(request, channel, client);
        }
    }

    /**
     * 需要对用户进行初始化
     * @param client
     * @param headers
     */
    public static void init(Client client) {
        if (OplateRestHanlder.users.size() == 0 || OplateRestHanlder.users.containsKey("init")) {
            Map<String, String> header = new HashMap<>();
            try {
                IndicesExistsResponse response = client.admin().indices().prepareExists(INDEXER).get();
                if (!response.isExists()) {
                    String id = UUID.randomUUID().toString();
                    String adminId = UUID.randomUUID().toString();
                    Set<String> set = new HashSet<>();
                    set.add("*");
                    User oplate = new User(id, "oplate", "oplate_oplate", set, "", 100000, "admin");
                    User admin = new User(adminId, PluginUtil.ES_ADMIN, PluginUtil.ES_ADMIN, set, "", 100000, "admin");
                    String auth = PluginUtil.creatAuthorization("oplate", "oplate_oplate");
                    String adminAuth = PluginUtil.creatAuthorization(PluginUtil.ES_ADMIN, PluginUtil.ES_ADMIN);
                    client.prepareIndex(INDEXER, TYPE, id).setSource(PluginUtil.UserToJSON(oplate)).execute().get();
                    client.prepareIndex(INDEXER, TYPE, adminId).setSource(PluginUtil.UserToJSON(admin)).execute().get();
                    // 初始oplate用户
                    OplateRestHanlder.users.put(auth, oplate);
                    OplateRestHanlder.users.put(adminAuth, admin);
                    header.put("token", adminAuth);
                }
                SearchRequestBuilder request = null;
                if (header.size() > 0) {
                    request = client.filterWithHeader(header).prepareSearch(INDEXER);
                } else {
                    request = client.prepareSearch(INDEXER);
                }
                // 初始化全部用户
                SearchResponse result = request.setTypes(TYPE).setSize(1000).execute().get();
                result.getHits().forEach(hit -> {
                    User user = PluginUtil.MapToUser(hit.getSource());
                    String name = user.getUsername();
                    String password = user.getPassword();
                    String auth = PluginUtil.creatAuthorization(name, password);
                    OplateRestHanlder.users.put(auth, user);
                });
                LOGGER.info(">>>>>>>users:" + OplateRestHanlder.users);
            } catch (Exception e) {
                LOGGER.info("初始化用户异常", e);
            }
        }
    }
}
