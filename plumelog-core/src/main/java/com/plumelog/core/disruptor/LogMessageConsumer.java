package com.plumelog.core.disruptor;

import com.lmax.disruptor.WorkHandler;
import com.plumelog.core.AbstractClient;
import com.plumelog.core.constant.LogMessageConstant;
import com.plumelog.core.dto.BaseLogMessage;
import com.plumelog.core.dto.RunLogMessage;
import com.plumelog.core.util.GfJsonUtil;

/**
 * className：LogMessageConsumer
 * description： 日志消费
 * time：2020-05-19.13:59
 *
 * @author Tank
 * @version 1.0.0
 */
public class LogMessageConsumer implements WorkHandler<LogEvent> {

    private String name;

    public LogMessageConsumer(String name) {
        this.name = name;
    }

    @Override
    public void onEvent(LogEvent event) throws Exception {
        BaseLogMessage baseLogMessage = event.getBaseLogMessage();
        final String redisKey =
                baseLogMessage instanceof RunLogMessage
                        ? LogMessageConstant.LOG_KEY
                        : LogMessageConstant.LOG_KEY_TRACE;
        AbstractClient.getClient().pushMessage(redisKey, GfJsonUtil.toJSONString(baseLogMessage));
    }
}
