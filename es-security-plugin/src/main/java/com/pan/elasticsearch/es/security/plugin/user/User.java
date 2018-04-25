package com.pan.elasticsearch.es.security.plugin.user;

import java.io.Serializable;
import java.util.Set;

public class User implements Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 3268588581047727940L;
    // 用户名
    private String username;
    // 密码
    private String password;
    // 管理的索引
    private Set<String> indcies;
    // 所属安全域
    private String realm;
    // 访问频率
    private Integer frequency;
    // 角色
    private String role;
    // id
    private String id;

    public User() {
        super();
    }

    public User(String id, String username, String password, Set<String> indcies, String realm, int frequency,
                String role) {
        super();
        this.id = id;
        this.username = username;
        this.password = password;
        this.indcies = indcies;
        this.realm = realm;
        this.frequency = frequency;
        this.role = role;
    }

    // @Override
    // public boolean equals(Object obj) {
    // if (obj != null && obj instanceof User) {
    // User other = (User) obj;
    // if (this.username != null && this.username.equals(other.getUsername()) && this.password != null
    // && this.password.equals(other.getPassword())) {
    // return true;
    // }
    // }
    // return false;
    // }
    //
    // @Override
    // public int hashCode() {
    // return this.username.hashCode() + this.password.hashCode();
    // }
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Set<String> getIndcies() {
        return indcies;
    }

    public void setIndcies(Set<String> indcies) {
        this.indcies = indcies;
    }

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public Integer getFrequency() {
        return frequency;
    }

    public void setFrequency(Integer frequency) {
        this.frequency = frequency;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "User [username=" + username + ", password=" + password + ", indcies=" + indcies + ", realm=" + realm
               + ", frequency=" + frequency + ", role=" + role + ", id=" + id + "]";
    }
}
