package cn.qaiu.parser.impl;

import cn.qaiu.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import cn.qaiu.parser.IPanTool;
import cn.qaiu.parser.PanBase;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;

/**
 * <a href="https://www.wenshushu.cn/">文叔叔</a>
 */
public class WsTool extends PanBase implements IPanTool {

    public static final String SHARE_URL_PREFIX  = "www.wenshushu.cn/f/";
    public static final String SHARE_URL_PREFIX2 = "f.ws59.cn/f/";
    public static final String SHARE_URL_API     = "https://www.wenshushu.cn/ap/";

    public WsTool(String key, String pwd) {
        super(key, pwd);
    }

    @SuppressWarnings("unchecked")
    public Future<String> parse() {

        WebClient httpClient = this.client;

        // 补全链接
        if (!this.key.startsWith("https://" + SHARE_URL_PREFIX) && !this.key.startsWith("https://" + SHARE_URL_PREFIX2)) {
            if (this.key.startsWith(SHARE_URL_PREFIX) || this.key.startsWith(SHARE_URL_PREFIX2)) {
                this.key = "https://" + this.key;
            } else if (this.key.matches("^[A-Za-z0-9]+$")) {
                this.key = "https://" + SHARE_URL_PREFIX + this.key;
            } else {
                throw new UnsupportedOperationException("未知分享类型");
            }
        }

        // 设置基础HTTP头部
        var userAgent2 = "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, " +
            "like " +
            "Gecko) Chrome/111.0.0.0 Mobile Safari/537.36";

        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        headers.set("User-Agent", userAgent2);
        headers.set("sec-ch-ua-platform", "Android");
        headers.set("Accept-Language", "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2");
        headers.set("sec-ch-ua-mobile", "sec-ch-ua-mobile");

        // 获取匿名登录token
        httpClient.postAbs(SHARE_URL_API + "login/anonymous").putHeaders(headers)
            .sendJsonObject(JsonObject.of("dev_info", "{}"))
            .onSuccess(res -> {

                if (res.statusCode() == 200) {
                    try {
                        // 设置匿名登录token
                        String token = res.bodyAsJsonObject().getJsonObject("data").getString("token");
                        headers.set("X-Token", token);

                        // 获取文件夹信息
                        httpClient.postAbs(SHARE_URL_API + "task/mgrtask").putHeaders(headers)
                            .sendJsonObject(JsonObject.of(
                                "tid", StringUtils.StringCutNot(key, this.key.startsWith(SHARE_URL_PREFIX) ? SHARE_URL_PREFIX : SHARE_URL_PREFIX2),
                                "password", ""
                            )).onSuccess(res2 -> {

                                if (res2.statusCode() == 200) {
                                    try {
                                        // 获取文件夹信息
                                        String filetime = res2.bodyAsJsonObject().getJsonObject("data").getString("expire");          // 文件夹剩余时间
                                        String filesize = res2.bodyAsJsonObject().getJsonObject("data").getString("file_size");       // 文件夹大小
                                        String filepid  = res2.bodyAsJsonObject().getJsonObject("data").getString("ufileid");         // 文件夹pid
                                        String filebid  = res2.bodyAsJsonObject().getJsonObject("data").getString("boxid");           // 文件夹bid

                                        // 调试输出文件夹信息
                                        System.out.println("文件夹期限: " + filetime);
                                        System.out.println("文件夹大小: " + filesize);
                                        System.out.println("文件夹pid: " + filepid);
                                        System.out.println("文件夹bid: " + filebid);

                                        // 获取文件信息
                                        httpClient.postAbs(SHARE_URL_API + "ufile/list").putHeaders(headers)
                                            .sendJsonObject(JsonObject.of(
                                                "start", 0,
                                                "sort", JsonObject.of(
                                                    "name", "asc"
                                                ),
                                                "bid", filebid,
                                                "pid", filepid,
                                                "type", 1,
                                                "options", JsonObject.of(
                                                    "uploader", "true"
                                                ),
                                                "size", 50
                                            )).onSuccess(res3 -> {

                                                if (res3.statusCode() == 200) {
                                                    try {
                                                        // 获取文件信息
                                                        String filename = res3.bodyAsJsonObject().getJsonObject("data")
                                                            .getJsonArray("fileList").getJsonObject(0).getString("fname");          // 文件名称
                                                        String filefid  = res3.bodyAsJsonObject().getJsonObject("data")
                                                            .getJsonArray("fileList").getJsonObject(0).getString("fid");            // 文件fid

                                                        // 调试输出文件信息
                                                        System.out.println("文件名称: " + filename);
                                                        System.out.println("文件fid: " + filefid);

                                                        // 检查文件是否失效
                                                        httpClient.postAbs(SHARE_URL_API + "dl/sign").putHeaders(headers)
                                                            .sendJsonObject(JsonObject.of(
                                                                "consumeCode", 0,
                                                                "type", 1,
                                                                "ufileid", filefid
                                                            )).onSuccess(res4 -> {

                                                                if (res4.statusCode() == 200) {
                                                                    try {
                                                                        // 获取直链
                                                                        String fileurl = res4.bodyAsJsonObject().getJsonObject("data").getString("url");

                                                                        // 调试输出文件直链
                                                                        System.out.println("文件直链: " + fileurl);

                                                                        if (!fileurl.equals(""))
                                                                        {
                                                                            try {
                                                                                promise.complete(URLDecoder.decode(fileurl, "UTF-8"));
                                                                            } catch (UnsupportedEncodingException e) {
                                                                                promise.complete(fileurl);
                                                                            }
                                                                        }
                                                                        else
                                                                        {
                                                                            this.fail("文件已失效");
                                                                        }

                                                                    }  catch (DecodeException | NullPointerException e) {
                                                                        this.fail("获取文件信息失败，可能是分享链接的方式已更新，或者对方的文件已失效");
                                                                    }
                                                                } else {
                                                                    this.fail("HTTP状态不正确，可能是分享链接的方式已更新");
                                                                }

                                                            });

                                                    }  catch (DecodeException | NullPointerException e) {
                                                        this.fail("获取文件信息失败，可能是分享链接的方式已更新");
                                                    }
                                                } else {
                                                    this.fail("HTTP状态不正确，可能是分享链接的方式已更新");
                                                }

                                            });

                                    } catch (DecodeException | NullPointerException e) {
                                        this.fail("获取文件夹信息失败，可能是分享链接的方式已更新");
                                    }
                                } else {
                                    this.fail("HTTP状态不正确，可能是分享链接的方式已更新");
                                }

                            }).onFailure(this.handleFail(this.key));

                    } catch (DecodeException | NullPointerException e) {
                        this.fail("token获取失败，可能是分享链接的方式已更新");
                    }
                } else {
                    this.fail("HTTP状态不正确，可能是分享链接的方式已更新");
                }

            }).onFailure(this.handleFail(this.key));

        return promise.future();
    }
}
