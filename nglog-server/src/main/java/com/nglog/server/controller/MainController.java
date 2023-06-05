package com.nglog.server.controller;


import com.nglog.core.AbstractClient;
import com.nglog.core.LogMessage;
import com.nglog.core.constant.LogMessageConstant;
import com.nglog.core.dto.WarningRule;
import com.nglog.core.dto.WarningRuleDto;
import com.nglog.core.util.GfJsonUtil;
import com.nglog.server.InitConfig;
import com.nglog.server.cache.AppNameCache;
import com.nglog.server.client.ElasticLowerClient;
import com.nglog.server.controller.vo.LoginVO;
import com.nglog.server.util.IndexUtil;
import org.elasticsearch.client.ResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * className：MainController
 * description：plumelog server 增強功能：1.開放rest去隊列 1.開發接口插入隊列
 *
 * @author Frank.chen
 * @version 1.0.0
 */
@RestController
@CrossOrigin
public class MainController {

    private final Logger logger = LoggerFactory.getLogger(MainController.class);

    @Autowired
    private AbstractClient redisClient;
    @Autowired
    private AbstractClient redisQueueClient;

    @Autowired
    private ElasticLowerClient elasticLowerClient;

    @Value("${admin.password}")
    private String adminPassWord;


    @RequestMapping({"/login", "/plumelogServer/login"})
    public Result login(@RequestBody LoginVO login, HttpServletRequest request) {
        if (StringUtils.isEmpty(InitConfig.loginUsername)) {
            request.getSession().setAttribute("token", new Object());
            return new Result();
        }
        if (InitConfig.loginUsername.equals(login.getUsername()) && InitConfig.loginPassword.equals(login.getPassword())) {
            request.getSession().setAttribute("token", new Object());
            return new Result();
        } else {
            request.getSession().removeAttribute("token");
            return Result.INVALID_LOGIN;
        }
    }

    @RequestMapping({"/logout", "/plumelogServer/logout"})
    public void login(HttpServletRequest request) {
        request.getSession().removeAttribute("token");
    }

    @RequestMapping({"/getlog", "/plumelogServer/getlog"})
    public Result getlog(Integer maxSendSize, String logKey) {
        if (maxSendSize == null) {
            maxSendSize = 500;
        }
        Result result = new Result();
        try {
            List<String> logs = redisClient.getMessage(logKey, maxSendSize);
            if (logs != null && logs.size() > 0) {
                logger.info("get logs success size:" + logs.size());
                result.setCode(200);
                result.setMessage("get logs success!");
                result.setLogs(logs);
                return result;
            }
        } catch (Exception e) {
            logger.error("", e);
            result.setCode(500);
            result.setMessage("get logs error! :" + e.getMessage());
        }
        result.setCode(404);
        result.setMessage("get no logs!");
        return result;
    }
    @RequestMapping({"/sendLog", "/plumelogServer/sendLog"})
    public Result sendLog(@RequestBody List<LogMessage> logs, String logKey) {
        Result result = new Result();
        if ("redis".equals(InitConfig.START_MODEL)) {
            try {
                List<String> logStr = new ArrayList<>();
                logs.forEach(log -> logStr.add(GfJsonUtil.toJSONString(log)));
                redisClient.putMessageList(logKey, logStr);
            } catch (Exception e) {
                result.setCode(500);
                result.setMessage("send logs error! :" + e.getMessage());
            }
            result.setCode(200);
            result.setMessage("send logs! success");
        } else {
            result.setCode(500);
            result.setMessage("send logs error! rest model only support redis model");
        }
        return result;
    }
    @RequestMapping({"/queryAppName", "/plumelog/queryAppName"})
    public String queryAppName(@RequestBody String queryStr) {

        // 查询过去n天的索引
        String[] indexs = new String[InitConfig.keepDays];
        for (int i = 0; i < InitConfig.keepDays; i++) {
            indexs[i] = IndexUtil.getRunLogIndex(
                    System.currentTimeMillis() - i * InitConfig.MILLS_ONE_DAY) + "*";
        }
        // 检查ES索引是否存在
        List<String> reindexs = elasticLowerClient.getExistIndices(indexs);
        String indexStr = String.join(",", reindexs);
        if ("".equals(indexStr)) {
            return "";
        }
        String url = "/" + indexStr + "/_search?from=0&size=0";
        logger.info("queryURL:" + url);
        logger.info("queryStr:" + queryStr);
        try {
            return elasticLowerClient.get(url, queryStr);
        } catch (Exception e) {
            // 为兼容旧的索引如果按照appNameWithEnv查询失败则重新按照appName查询
            if (e instanceof ResponseException && queryStr.contains("appNameWithEnv")) {
                queryStr = queryStr.replaceAll("appNameWithEnv", "appName");
                logger.info("queryURL:" + url);
                logger.info("queryStr:" + queryStr);
                try {
                    return elasticLowerClient.get(url, queryStr);
                } catch (Exception ex) {
                    logger.error("queryAppName fail!", ex);
                    return "";
                }
            }
            logger.error("queryAppName fail!", e);
            return "";
        }
    }
    @RequestMapping({"/clientQuery", "/plumelog/clientQuery"})
    public String clientQuery(@RequestBody String queryStr, String size, String from,
                              String clientStartDate, String clientEndDate, String trace) {

        Long clientStartDateTime = 0L;
        try {
            clientStartDateTime = Long.valueOf(clientStartDate);
        } catch (NumberFormatException e) {
            // ignore
        }
        if (clientStartDateTime <= 0) {
            clientStartDateTime = System.currentTimeMillis();
        }

        Long clientEndDateTime = 0L;
        try {
            clientEndDateTime = Long.valueOf(clientEndDate);
        } catch (NumberFormatException e) {
            // ignore
        }

        if (clientEndDateTime <= 0) {
            clientEndDateTime = System.currentTimeMillis();
        }

        Set<String> indexSet = new LinkedHashSet<>();
        while (clientStartDateTime <= clientEndDateTime) {
            indexSet.add(("true".equalsIgnoreCase(trace) ?
                    IndexUtil.getTraceLogIndex(clientStartDateTime) : IndexUtil.getRunLogIndex(clientStartDateTime)) + "*");
            clientStartDateTime += InitConfig.MILLS_ONE_DAY;
        }
        indexSet.add(("true".equalsIgnoreCase(trace) ?
                IndexUtil.getTraceLogIndex(clientEndDateTime) : IndexUtil.getRunLogIndex(clientEndDateTime)) + "*");

        //检查ES索引是否存在
        List<String> existIndices = elasticLowerClient.getExistIndices(indexSet.toArray(new String[0]));
        String indexStr = String.join(",", existIndices);
        if ("".equals(indexStr)) {
            return "";
        }
        String url = "/" + indexStr + "/_search?from=" + from + "&size=" + size;
        logger.info("queryURL:" + url);
        logger.info("queryStr:" + queryStr);

        try {
            return elasticLowerClient.get(url, queryStr);
        } catch (Exception e) {
            // 为兼容旧的索引如果排序使用seq查询失败则重新按照去掉seq查询
            if (e instanceof ResponseException
                    && (queryStr.contains(",{\"seq\":\"desc\"}") || queryStr.contains(",{\"seq\":\"asc\"}"))) {
                queryStr = queryStr.replace(",{\"seq\":\"desc\"}", "");
                queryStr = queryStr.replace(",{\"seq\":\"asc\"}", "");
                logger.info("queryURL:" + url);
                logger.info("queryStr:" + queryStr);

                try {
                    return elasticLowerClient.get(url, queryStr);
                } catch (Exception ex) {
                    logger.error("clientQuery fail!", ex);
                    return "";
                }
            }
            logger.error("clientQuery fail!", e);
            return "";
        }
    }
    @RequestMapping({"/query", "/plumelog/query"})
    public String query(@RequestBody String queryStr, String index, String size, String from, String range) {

        String message = "";
        String indexStr = "";
        try {
            //检查ES索引是否存在
            String[] indexs = index.split(",");
            Set<String> indexSet = new TreeSet<>();
            if (indexs.length > 0) {
                indexSet.addAll(Arrays.asList(indexs));
            }
            if (!StringUtils.isEmpty(range)) {
                int rangeDays = 0;
                if ("day".equalsIgnoreCase(range)) {
                    rangeDays = 1;
                } else if ("week".equalsIgnoreCase(range)) {
                    rangeDays = 7;
                } else if ("month".equalsIgnoreCase(range)) {
                    rangeDays = 30;
                }
                for (int i = 0; i < rangeDays; i++) {
                    indexSet.add(IndexUtil.getRunLogIndex(System.currentTimeMillis() - i * InitConfig.MILLS_ONE_DAY) + "*");
                }
            }
            List<String> reindexs = elasticLowerClient.getExistIndices(indexSet.toArray(new String[0]));
            indexStr = String.join(",", reindexs);
            if ("".equals(indexStr)) {
                return message;
            }
            String url = "/" + indexStr + "/_search?from=" + from + "&size=" + size;
            logger.info("queryURL:" + url);
            logger.info("queryStr:" + queryStr);
            return elasticLowerClient.get(url, queryStr);
        } catch (Exception e) {
            logger.error("query fail!", e);
            return "";
        }
    }

    /**
     * 根据条件删除
     *
     * @param queryStr
     * @param index
     * @param size
     * @param from
     * @return
     */
    @RequestMapping({"/deleteByQuery", "/plumelog/deleteByQuery"})
    public String deleteByQuery(@RequestBody String queryStr, String index, String size, String from) {

        String message = "";
        String indexStr = "";
        try {
            //检查ES索引是否存在
            String[] indexs = index.split(",");
            List<String> reindexs = elasticLowerClient.getExistIndices(indexs);
            indexStr = String.join(",", reindexs);
            if ("".equals(indexStr)) {
                return message;
            }
            String url = "/" + indexStr + "/_delete_by_query?from=" + from + "&size=" + size;
            logger.info("queryURL:" + url);
            logger.info("queryStr:" + queryStr);
            return elasticLowerClient.get(url, queryStr);
        } catch (Exception e) {
            logger.error("deleteByQuery fail!", e);
            return "";
        }
    }

    @RequestMapping({"/getServerInfo", "/plumelog/getServerInfo"})
    public String query(String index) {
        String res = elasticLowerClient.cat(index);
        BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(res.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
        List<String> list = new ArrayList<>();
        try {
            while (true) {
                String aa = br.readLine();
                if (StringUtils.isEmpty(aa)) {
                    break;
                }
                list.add(aa);
            }
            List<Map<String, String>> listMap = new ArrayList<>();
            if (list.size() > 0) {
                String[] title = list.get(0).split("\\s+");
                for (int i = 1; i < list.size(); i++) {
                    String[] values = list.get(i).split("\\s+");
                    Map<String, String> map = new HashMap<>();
                    for (int j = 0; j < title.length; j++) {
                        map.put(title[j], values[j]);
                    }
                    listMap.add(map);
                }
            }
            return GfJsonUtil.toJSONString(listMap);
        } catch (IOException e) {
            logger.error("", e);
        }
        return "";
    }

    @RequestMapping({"/getQueueCounts", "/plumelog/getQueueCounts"})
    public Map<String, Object> getQueueCounts() {
        Long runSize = 0L;
        Long traceSize = 0L;
        if (redisQueueClient != null) {
            runSize = redisQueueClient.llen(LogMessageConstant.LOG_KEY);
            traceSize = redisQueueClient.llen(LogMessageConstant.LOG_KEY_TRACE);
        }
        Map<String, Object> map = new HashMap<>();
        map.put("runSize", runSize);
        map.put("traceSize", traceSize);
        return map;
    }

    @RequestMapping({"/deleteQueue", "/plumelog/deleteQueue"})
    public Map<String, Object> deleteQueue(String adminPassWord) {
        Map<String, Object> map = new HashMap<>();
        if (adminPassWord.equals(this.adminPassWord)) {
            if (redisQueueClient != null) {
                redisQueueClient.del(LogMessageConstant.LOG_KEY);
                redisQueueClient.del(LogMessageConstant.LOG_KEY_TRACE);
            }
            map.put("acknowledged", true);
        } else {
            map.put("acknowledged", false);
            map.put("message", "管理密码错误！");
        }
        return map;
    }

    @RequestMapping({"/deleteIndex", "/plumelog/deleteIndex"})
    public Map<String, Object> deleteIndex(String index, String adminPassWord) {
        Map<String, Object> map = new HashMap<>();
        if (adminPassWord.equals(this.adminPassWord)) {
            boolean re = elasticLowerClient.deleteIndex(index);
            if (index.startsWith(LogMessageConstant.ES_INDEX + LogMessageConstant.LOG_TYPE_RUN)) {
                creatIndiceLog(index);
            }
            if (index.startsWith(LogMessageConstant.ES_INDEX + LogMessageConstant.LOG_TYPE_TRACE)) {
                creatIndiceTrace(index);
            }
            map.put("acknowledged", re);
        } else {
            map.put("acknowledged", false);
            map.put("message", "管理密码错误！");
        }
        return map;
    }

    private void creatIndiceLog(String index) {
        if (!elasticLowerClient.existIndice(index)) {
            elasticLowerClient.creatIndice(index, LogMessageConstant.ES_TYPE);
        }
    }

    private void creatIndiceTrace(String index) {
        if (!elasticLowerClient.existIndice(index)) {
            elasticLowerClient.creatIndiceTrace(index, LogMessageConstant.ES_TYPE);
        }
    }

    @RequestMapping({"/getWarningRuleList", "/plumelog/getWarningRuleList"})
    public Object getWarningRuleList() {
        List<WarningRuleDto> list = new ArrayList<>();
        Map<String, String> map = redisClient.hgetAll(LogMessageConstant.WARN_RULE_KEY);
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String mapKey = entry.getKey();
            String mapValue = entry.getValue();
            WarningRule warningRule = GfJsonUtil.parseObject(mapValue, WarningRule.class);
            WarningRuleDto warningRuleDto = new WarningRuleDto();
            warningRuleDto.setId(mapKey);
            warningRuleDto.setAppName(warningRule.getAppName());
            warningRuleDto.setEnv(warningRule.getEnv());
            warningRuleDto.setAppCategory(warningRule.getAppCategory());
            warningRuleDto.setClassName(warningRule.getClassName());
            warningRuleDto.setReceiver(warningRule.getReceiver());
            warningRuleDto.setWebhookUrl(warningRule.getWebhookUrl());
            warningRuleDto.setTime(warningRule.getTime());
            warningRuleDto.setErrorCount(warningRule.getErrorCount());
            warningRuleDto.setStatus(warningRule.getStatus());
            warningRuleDto.setHookServe(warningRule.getHookServe());
            list.add(warningRuleDto);
        }
        return list;
    }

    @RequestMapping({"/saveWarningRuleList", "/plumelog/saveWarningRuleList"})
    public Object saveWarningRule(String id, @RequestBody WarningRule warningRule) {
        String warningRuleStr = GfJsonUtil.toJSONString(warningRule);
        redisClient.hset(LogMessageConstant.WARN_RULE_KEY, id, warningRuleStr);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    @RequestMapping({"/deleteWarningRule", "/plumelog/deleteWarningRule"})
    public Object deleteWarningRule(String id) {
        redisClient.hdel(LogMessageConstant.WARN_RULE_KEY, id);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    @RequestMapping({"/getExtendfieldList", "/plumelog/getExtendfieldList"})
    public Object getExtendfieldList(String appName) {
        return redisClient.hgetAll(LogMessageConstant.EXTEND_APP_MAP_KEY + appName);
    }

    @RequestMapping({"/addExtendfield", "/plumelog/addExtendfield"})
    public Object addExtendfield(String appName, String field, String fieldName) {
        redisClient.hset(LogMessageConstant.EXTEND_APP_MAP_KEY + appName, field, fieldName);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    @RequestMapping({"/delExtendfield", "/plumelog/delExtendfield"})
    public Object delExtendfield(String appName, String field) {
        redisClient.hdel(LogMessageConstant.EXTEND_APP_MAP_KEY + appName, field);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    @RequestMapping({"/getAppNames", "/plumelog/getAppNames"})
    public Object getAppNames() {
        return AppNameCache.appName;
    }
}
