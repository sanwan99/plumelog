package com.nglog.logback.appender;


import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import com.nglog.core.MessageAppenderFactory;
import com.nglog.core.constant.LogMessageConstant;
import com.nglog.core.dto.BaseLogMessage;
import com.nglog.core.dto.RunLogMessage;
import com.nglog.core.kafka.KafkaProducerClient;
import com.nglog.core.util.GfJsonUtil;
import com.nglog.core.util.ThreadPoolUtil;
import com.nglog.logback.util.LogMessageUtil;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * className：KafkaAppender
 * description：KafkaAppender 如果使用kafka作为队列用这个KafkaAppender输出
 *
 * @author Frank.chen
 * @version 1.0.0
 */
public class KafkaAppender extends AppenderBase<ILoggingEvent> {
    private static final ThreadPoolExecutor threadPoolExecutor
            = ThreadPoolUtil.getPool();
    private KafkaProducerClient kafkaClient;
    private String appName;
    private String env = "default";
    private String kafkaHosts;
    private String runModel;
    private String expand;
    private int maxCount = 100;
    private int logQueueSize = 10000;
    private int threadPoolSize = 1;
    private boolean compressor = false;

    public String getExpand() {
        return expand;
    }

    public void setExpand(String expand) {
        this.expand = expand;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public void setKafkaHosts(String kafkaHosts) {
        this.kafkaHosts = kafkaHosts;
    }

    public void setRunModel(String runModel) {
        this.runModel = runModel;
    }

    public void setMaxCount(int maxCount) {
        this.maxCount = maxCount;
    }

    public void setLogQueueSize(int logQueueSize) {
        this.logQueueSize = logQueueSize;
    }

    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public void setCompressor(boolean compressor) {
        this.compressor = compressor;
    }

    @Override
    protected void append(ILoggingEvent event) {
        send(event);
    }

    protected void send(ILoggingEvent event) {
        final BaseLogMessage logMessage = LogMessageUtil.getLogMessage(appName, env, event);
        if (logMessage instanceof RunLogMessage) {
            final String message = LogMessageUtil.getLogMessage(logMessage, event);
            MessageAppenderFactory.pushRundataQueue(message);
        } else {
            MessageAppenderFactory.pushTracedataQueue(GfJsonUtil.toJSONString(logMessage));
        }
    }

    @Override
    public void start() {
        super.start();
        if (this.runModel != null) {
            LogMessageConstant.RUN_MODEL = Integer.parseInt(this.runModel);
        }
        if (this.kafkaClient == null) {
            this.kafkaClient = KafkaProducerClient.getInstance(this.kafkaHosts, this.compressor ? "lz4" : "none");
        }
        if (this.expand != null && LogMessageConstant.EXPANDS.contains(expand)) {
            LogMessageConstant.EXPAND = expand;
        }

        MessageAppenderFactory.initQueue(this.logQueueSize);
        for (int a = 0; a < this.threadPoolSize; a++) {
            threadPoolExecutor.execute(() -> {
                MessageAppenderFactory.startRunLog(this.kafkaClient, this.maxCount);
            });
        }
        for (int a = 0; a < this.threadPoolSize; a++) {
            threadPoolExecutor.execute(() -> {
                MessageAppenderFactory.startTraceLog(this.kafkaClient, this.maxCount);
            });
        }
    }
}
