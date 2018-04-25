package com.pan.elasticsearch.es.security.plugin.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.xcontent.XContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.rest.RestRequest;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import com.pan.elasticsearch.es.security.plugin.user.Config;
import com.pan.elasticsearch.es.security.plugin.user.User;

public class PluginUtil {
    private final static Logger LOGGER = LogManager.getLogger(PluginUtil.class);
    public static final String ES_ADMIN = "es_admin";

    public static String creatAuthorization(String username, String password) {
        try {
            return String.valueOf(Base64Coder.encode((username + ":" + password).getBytes()));
        } catch (Exception e) {
            LOGGER.error("生成token出错", e);
        }
        return null;
    }

    public static XContentBuilder UserToJSON(User user) {
        try {
            XContentBuilder builder = XContentFactory.jsonBuilder().startObject();
            if (user.getUsername() != null) {
                builder.field(Config.USERNAME, user.getUsername());
            }
            if (user.getPassword() != null) {
                builder.field(Config.PASSWORD, user.getPassword());
            }
            if (user.getRole() != null) {
                builder.field(Config.ROLE, user.getRole());
            }
            if (user.getIndcies() != null) {
                builder.field(Config.INDCIES, user.getIndcies());
            }
            if (user.getId() != null) {
                builder.field(Config.ID, user.getId());
            }
            if (user.getRealm() != null) {
                builder.field(Config.REALM, user.getRealm());
            }
            if (user.getFrequency() != null) {
                builder.field(Config.FREQUENCY, user.getFrequency());
            }
            return builder.endObject();
        } catch (IOException e) {
            LOGGER.error("user转换成json出错", e);
        }
        return null;
    }

    public static User requestToUser(RestRequest request) {
        User user = new User();
        try {
            XContent xContent = XContentFactory.xContent(request.content());
            XContentParser parser = xContent.createParser(request.content());
            XContentParser.Token token;
            String currentFieldName = null;
            while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
                if (token == XContentParser.Token.FIELD_NAME) {
                    currentFieldName = parser.currentName();
                } else {
                    if (currentFieldName != null) {
                        LOGGER.info("currentFieldName " + currentFieldName);
                        switch (currentFieldName) {
                            case "username":
                                LOGGER.info("parser.text() " + parser.text());
                                user.setUsername(parser.text());
                                break;
                            case "password":
                                LOGGER.info("parser.text() " + parser.text());
                                user.setPassword(parser.text());
                                break;
                            case "role":
                                LOGGER.info("parser.text() " + parser.text());
                                user.setRole(parser.text());
                                break;
                            case "indcies":
                                user.setIndcies(parser.list().stream().map(value -> value.toString())
                                    .collect(Collectors.toSet()));
                                break;
                            case "id":
                                user.setId(parser.text());
                                break;
                            case "realm":
                                user.setRealm(parser.text());
                                break;
                            case "frequency":
                                user.setFrequency(parser.intValue());
                                break;
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("json转换成user出错", e);
        }
        return user;
    }

    public static String sendGet(String url, String param) {
        BufferedReader in = null;
        String tmp = "";
        try {
            String urlName = url;
            URL realUrl = new URL(urlName);
            // 打开和URL之间的连接
            URLConnection conn = realUrl.openConnection();
            // 设置通用的请求属性
            conn.setRequestProperty("accept", "*/*");
            conn.setRequestProperty("token", param);
            conn.setRequestProperty("connection", "Keep-Alive");
            conn.setRequestProperty("user-agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1; SV1)");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(1000);
            // 建立实际的连接
            conn.connect();
            // 定义BufferedReader输入流来读取URL的响应
            in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                tmp += line;
            }
        } catch (Exception e) {
            LOGGER.error("向es发送get请求过程中出现异常:url=" + url, e);
        }
        // 使用finally块来关闭输入流
        finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException ex) {
                LOGGER.error("关闭流出现异常", ex);
            }
        }
        return tmp.toString();
    }

    public static User MapToUser(Map<String, Object> map) {
        User user = new User();
        user.setUsername((String) map.get(Config.USERNAME));
        user.setPassword((String) map.get(Config.PASSWORD));
        user.setRole((String) map.get(Config.ROLE));
        if (map.get(Config.INDCIES) != null) {
            user.setIndcies(((List<String>) map.get(Config.INDCIES)).stream().collect(Collectors.toSet()));
        }
        user.setId((String) map.get(Config.ID));
        user.setRealm((String) map.get(Config.REALM));
        user.setFrequency((Integer) map.get(Config.FREQUENCY));
        return user;
    }
}
