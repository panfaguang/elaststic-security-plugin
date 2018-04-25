# elaststic-security-plugin
对elasticsearch5.0.0添加用户名和密码,并且进行索引级别的权限管理

<table>
        <tr>
            <th>字段</th>
            <th>类型</th>
            <th>描述</th>
        </tr>
        <tr>
            <th>username</th>
            <th>keyword</th>
            <th>用户名</th>
        </tr>
        <tr>
            <th>password</th>
            <th>keyword</th>
            <th>密码</th>
        </tr>
        <tr>
            <th>indices</th>
            <th>keyword</th>
            <th>能够管理的文档 前缀匹配支持通配符*</th>
        </tr>
        <tr>
            <th>frequency</th>
            <th>integer</th>
            <th>该用户最大查询频率  次/秒</th>
        </tr>
        <tr>
            <th>role</th>
            <th>keyword</th>
            <th>角色 admin user</th>
        </tr>
        <tr>
            <th>realm</th>
            <th>keyword</th>
            <th>安全域</th>
        </tr>
    </table>



## 安装方式
```
//进入es的目录
cd elasticsearch-5.0.0
//创建插件目录
mkdir plguins
//创建elaststic-security-plugin目录
cd plguins
mkdir elaststic-security-plugin
//复制es-security-plugin-1.0.0-SNAPSHOT.zip到当前目录
cd elaststic-security-plugin
cp elaststic-security-plugin/es-security-plugin/target/releases/es-security-plugin-1.0.0-SNAPSHOT.zip ./
unzip es-security-plugin-1.0.0-SNAPSHOT.zip 
重启es

```
## 使用方式
  - 初始用户名密码是es_admin es_admin
  - es启动完成后访问进行初始化
 ```  curl -X GET  --header 'token:ZXNfYWRtaW46ZXNfYWRtaW4=' 'http://127.0.0.1:9200/_oplate' ```
  - 添加用户  
```curl -X POST --header 'token:ZXNfYWRtaW46ZXNfYWRtaW4=' -d ''{"username":"username","password":"password","indcies":["indci1","indci2"],"role":"user","frequency":1000}' 'http://127.0.0.1:9200/_oplate' ```
  - 更新当前用户密码
``` curl -X PUT --header 'token:ZXNfYWRtaW46ZXNfYWRtaW4=' -d '{"password":"新密码"}' 'http://127.0.0.1:9200/_oplate' ```
  - 更新其他用户信息（只有管理员才有权限role=admin） 
```curl -X PUT --header 'token:ZXNfYWRtaW46ZXNfYWRtaW4=' -d '{"username":"username","indcies":["索引1","索引2"]}' 'http://127.0.0.1:9200/_oplate' ```

###java client

```
Map<String,String> header= new HashMap<String,String>();
header.put("token", String.valueOf(Base64Coder.encode(("es_admin:es_admin").getBytes())));
client.filterWithHeader(header).prepareSearch(INDEXER).setTypes(TYPE).execute().get();       
```

