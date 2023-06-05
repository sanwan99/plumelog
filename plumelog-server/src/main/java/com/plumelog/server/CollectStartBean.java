package com.plumelog.server;


import com.nglog.core.AbstractClient;
import com.nglog.core.constant.LogMessageConstant;
import com.plumelog.server.client.ElasticLowerClient;
import com.plumelog.server.collect.KafkaLogCollect;
import com.plumelog.server.collect.RedisLogCollect;
import com.plumelog.server.collect.RestLogCollect;
import com.plumelog.server.util.IndexUtil;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * className：CollectStartBean
 * description：日誌搜集spring bean
 * time：2020/6/10  17:44
 *
 * @author frank.chen
 * @version 1.0.0
 */
@Component
@Order(100)
public class CollectStartBean implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(CollectStartBean.class);

    @Autowired
    private ElasticLowerClient elasticLowerClient;

    @Autowired(required = false)
    private AbstractClient redisQueueClient;

    @Autowired(required = false)
    private KafkaConsumer kafkaConsumer;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Value("${plumelog.redis.compressor:false}")
    private Boolean compressor;

    private void serverStart() {
        if (InitConfig.KAFKA_MODE_NAME.equals(InitConfig.START_MODEL)) {
            KafkaLogCollect kafkaLogCollect = new KafkaLogCollect(elasticLowerClient, kafkaConsumer,
                    applicationEventPublisher);
            kafkaLogCollect.kafkaStart();
        }
        if (InitConfig.REDIS_MODE_NAME.equals(InitConfig.START_MODEL) || InitConfig.REDIS_SENTINEL_MODE_NAME
                .equals(InitConfig.START_MODEL) || InitConfig.REDIS_CLUSTER_MODE_NAME.equals(InitConfig.START_MODEL)) {
            RedisLogCollect redisLogCollect = new RedisLogCollect(elasticLowerClient, redisQueueClient,
                    applicationEventPublisher, compressor);
            redisLogCollect.redisStart();
        }
        if (InitConfig.REST_MODE_NAME.equals(InitConfig.START_MODEL)) {
            RestLogCollect restLogCollect = new RestLogCollect(elasticLowerClient, applicationEventPublisher);
            restLogCollect.restStart();
        }
    }

    @Override
    public void afterPropertiesSet() {
        try {
            autoCreatIndice();
            serverStart();
        } catch (Exception e) {
            logger.error("plumelog server starting failed!", e);
        }
    }

    private void autoCreatIndice() {
        long epochMillis = System.currentTimeMillis();
        String runLogIndex = IndexUtil.getRunLogIndex(epochMillis);
        String traceLogIndex = IndexUtil.getTraceLogIndex(epochMillis);
        if ("day".equals(InitConfig.ES_INDEX_MODEL)) {
            creatIndiceLog(runLogIndex);
            creatIndiceTrace(traceLogIndex);
        } else {
            for (int a = 0; a < 24; a++) {
                String hour = String.format("%02d", a);
                creatIndiceLog(runLogIndex + hour);
                creatIndiceTrace(traceLogIndex + hour);
            }
        }
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
}
