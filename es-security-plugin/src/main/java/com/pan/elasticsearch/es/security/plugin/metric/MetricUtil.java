package com.pan.elasticsearch.es.security.plugin.metric;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.elasticsearch.common.metrics.MeterMetric;
import org.elasticsearch.threadpool.ThreadPool;

public class MetricUtil {
    static ThreadPool threadPool;
    static ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    public static ConcurrentMap<String, MeterMetric> metrics = new ConcurrentHashMap<>();
    public static boolean enableLimit = true;
    public static long time = 10000;

    public MetricUtil(ThreadPool threadPool) {
        this.threadPool = threadPool;
    }

    /**
     * 如果超过频率会被限速
     * @param token
     * @return
     */
    public static boolean isLimited(String token) {
        MeterMetric meter = metrics.get(token);
        if (meter == null) {
            meter = new MeterMetric(executorService, TimeUnit.SECONDS);
            metrics.put(token, meter);
        }
        // 只有启动限速后 才会限速
        if (enableLimit) {
            if (meter.oneMinuteRate() > time) {
                return true;
            }
        }
        return false;
    }

    /**
     * 进行计数
     * @param token
     */
    public static void count(String token) {
        MeterMetric meter = metrics.get(token);
        if (meter == null) {
            meter = new MeterMetric(executorService, TimeUnit.SECONDS);
            metrics.put(token, meter);
        }
        meter.mark();
    }

    public static MeterMetric getMeter(String token) {
        MeterMetric meter = metrics.get(token);
        if (meter == null) {
            meter = new MeterMetric(threadPool.scheduler(), TimeUnit.SECONDS);
            metrics.put(token, meter);
        }
        return meter;
    }
}
