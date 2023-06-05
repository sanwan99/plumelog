package com.nglog.core;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.nglog.core.constant.LogMessageConstant;
import com.nglog.core.disruptor.LogMessageProducer;
import com.nglog.core.disruptor.LogRingBuffer;
import com.nglog.core.dto.RunLogCompressMessage;
import com.nglog.core.exception.LogQueueConnectException;
import com.nglog.core.dto.BaseLogMessage;
import com.nglog.core.util.GfJsonUtil;
import com.nglog.core.util.LZ4Util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * className：MessageAppenderFactory
 * description： TODO
 * time：2020-05-13.14:18
 *
 * @author Tank
 * @version 1.0.0
 */
public class MessageAppenderFactory {

    /**
     * 当下游异常的时候，状态缓存时间
     */
    private final static Cache<String, Boolean> cache = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.SECONDS).build();
    public static BlockingQueue<String> rundataQueue;
    public static BlockingQueue<String> tracedataQueue;
    public static int queueSize = 10000;
    private static Boolean logOutPut = true;
    private static AtomicLong lastRunPushTime = new AtomicLong(0);
    private static AtomicLong lastTracePushTime = new AtomicLong(0);

    public static void initQueue(int logQueueSize) {
        queueSize = logQueueSize;
        if (rundataQueue == null) {
            rundataQueue = new LinkedBlockingQueue<>(logQueueSize);
        }
        if (tracedataQueue == null) {
            tracedataQueue = new LinkedBlockingQueue<>(logQueueSize);
        }
    }

    public static void push(BaseLogMessage baseLogMessage) {
        LogMessageProducer producer = new LogMessageProducer(LogRingBuffer.ringBuffer);
        producer.send(baseLogMessage);
    }

    public static void pushRundataQueue(String message) {
        if (message != null) {
            if (rundataQueue.size() < queueSize) {
                rundataQueue.add(message);
            }
        }
    }

    public static void pushTracedataQueue(String message) {
        if (message != null) {
            if (tracedataQueue.size() < queueSize) {
                tracedataQueue.add(message);
            }
        }
    }

    public static void pushRundataQueue(String message, int size) {
        if (message != null) {
            rundataQueue.add(message);
        }
    }

    public static void pushTracedataQueue(String message, int size) {
        if (message != null) {
            tracedataQueue.add(message);
        }
    }

    public static void push(String key, List<String> baseLogMessage, AbstractClient client, String logOutPutKey, boolean compress) {
        logOutPut = cache.getIfPresent(logOutPutKey);
        if (logOutPut == null || logOutPut) {
            try {
                client.putMessageList(key, compress(baseLogMessage, compress));
                cache.put(logOutPutKey, true);
            } catch (LogQueueConnectException e) {
                cache.put(logOutPutKey, false);
                e.printStackTrace();
            }
        }
    }

    private static List<String> compress(List<String> baseLogMessage, boolean compress) {

        if (!compress) {
            return baseLogMessage;
        }
        String text = GfJsonUtil.toJSONString(baseLogMessage);
        byte[] textByte = text.getBytes(StandardCharsets.UTF_8);
        byte[] compressedByte = LZ4Util.compressedByte(textByte);
        RunLogCompressMessage message = new RunLogCompressMessage();
        message.setBody(compressedByte);
        message.setLength(textByte.length);
        return Lists.newArrayList(GfJsonUtil.toJSONString(message));
    }

    public static void startRunLog(AbstractClient client, int maxCount) {
        startRunLog(client, maxCount, LogMessageConstant.LOG_KEY);
    }

    public static void startRunLog(AbstractClient client, int maxCount, String key) {
        startRunLog(client, maxCount, key, false);
    }

    public static void startRunLog(AbstractClient client, int maxCount, String key, boolean compress) {
        while (true) {
            try {
                doStartLog(client, maxCount, rundataQueue, key, "plume.log.ack", lastRunPushTime, compress);
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException interruptedException) {
                }
            }
        }
    }

    public static void startTraceLog(AbstractClient client, int maxCount) {
        startTraceLog(client, maxCount, LogMessageConstant.LOG_KEY_TRACE);
    }

    public static void startTraceLog(AbstractClient client, int maxCount, String key) {
        startTraceLog(client, maxCount, key, false);
    }

    public static void startTraceLog(AbstractClient client, int maxCount, String key, boolean compress) {
        while (true) {
            try {
                doStartLog(client, maxCount, tracedataQueue, key, "plume.log.ack", lastTracePushTime, compress);
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException interruptedException) {

                }
            }
        }
    }

    private static void doStartLog(AbstractClient client, int maxCount, BlockingQueue<String> queue, String key, String lock, AtomicLong pushTime, boolean compress) throws InterruptedException {

        List<String> logs = new ArrayList<>();

        int size = queue.size();
        long currentTimeMillis = System.currentTimeMillis();
        long time = currentTimeMillis - pushTime.get();

        if (size >= maxCount || time > 500) {
            queue.drainTo(logs, maxCount);
            push(key, logs, client, lock, compress);
            pushTime.set(currentTimeMillis);
        } else if (size == 0) {
            String log = queue.take();
            logs.add(log);
            push(key, logs, client, lock, compress);
            pushTime.set(currentTimeMillis);
        } else {
            Thread.sleep(100);
        }
    }
}
