package com.shane.orchestragent.repository.impl;

import com.shane.orchestragent.repository.Repository;
import com.shane.orchestragent.repository.RepositoryReloadCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 仓储抽象基类
 * 统一封装后台定时轮询任务调度、缓存刷新模板生命周期、监听器管理以及异常安全控制
 *
 * @param <T> 仓储实体类型
 * @author Shane
 */
public abstract class AbstractConfigRepository<T> implements Repository<T>, InitializingBean, DisposableBean {

    /** 默认定时刷新时间间隔（秒），支持 application.yml 配置 */
    @Value("${orchestr.repository.refresh-interval-seconds:30}")
    protected long refreshIntervalSeconds = 30;

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /** 后台定时刷新线程池 */
    protected ScheduledExecutorService scheduler = null;

    /** 缓存加载健康状态标记 */
    protected volatile boolean status = false;

    /** 上次成功刷新时间戳 */
    protected volatile long lastRefreshTimestamp = 0;

    /** 刷新变更监听器集合 */
    protected final List<RepositoryReloadCallback> callbacks = new CopyOnWriteArrayList<>();

    public boolean isHealthy() {
        return status;
    }

    public long getLastRefreshTimestamp() {
        return lastRefreshTimestamp;
    }

    public long getRefreshIntervalSeconds() {
        return refreshIntervalSeconds;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // 1. 初始化时先执行一次全量加载
        reload();
        // 2. 启动后台守护定时任务定期巡检刷新
        startScheduler();
    }

    @Override
    public void destroy() throws Exception {
        callbacks.clear();
        stopScheduler();
    }

    @Override
    public void reload() {
        try {
            logger.info("[CONFIG][RELOAD][{}] 开始从远程中心拉取并刷新配置缓存...", name());
            boolean result = doReload();
            if (result) {
                lastRefreshTimestamp = System.currentTimeMillis();
                status = true;
                int count = getAll() != null ? getAll().size() : 0;
                logger.info("[CONFIG][RELOAD][{}] 配置缓存刷新成功！共加载 {} 条实体", name(), count);

                // 触发通知所有注册的监听器
                for (RepositoryReloadCallback callback : callbacks) {
                    try {
                        callback.onReload(name());
                    } catch (Exception ex) {
                        logger.error("[CONFIG][RELOAD][{}] 触发刷新监听回调异常: {}", name(), ex.getMessage(), ex);
                    }
                }
            } else {
                logger.warn("[CONFIG][RELOAD][{}] 配置缓存刷新未变更或失败...", name());
            }
        } catch (Exception ex) {
            logger.error("[CONFIG][RELOAD][{}] 配置缓存刷新发生未知异常: {}", name(), ex.getMessage(), ex);
        }
    }

    @Override
    public void addCallback(RepositoryReloadCallback callback) {
        if (callback != null) {
            callbacks.add(callback);
        }
    }

    @Override
    public void removeCallback(RepositoryReloadCallback callback) {
        if (callback != null) {
            callbacks.remove(callback);
        }
    }

    /**
     * 子类执行具体数据拉取、解析与本地原子缓存替换的核心逻辑
     *
     * @return 加载是否成功
     * @throws Exception 加载异常
     */
    protected abstract boolean doReload() throws Exception;

    /**
     * 是否启用后台守护线程周期性刷新（默认 true）
     *
     * @return true 启用，false 禁用
     */
    protected boolean isAutoRefreshAtFixRate() {
        return true;
    }

    /**
     * 启动守护定时任务
     */
    private void startScheduler() {
        if (!this.isAutoRefreshAtFixRate()) {
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, name() + "-schedule-thread");
            thread.setDaemon(true);
            return thread;
        });

        long intervalSec = Math.max(refreshIntervalSeconds, 1);
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // 若状态不健康，或距上次成功刷新已超期，则触发刷新重试
                if (!status || (System.currentTimeMillis() - lastRefreshTimestamp > intervalSec * 1000L)) {
                    reload();
                }
            } catch (Exception e) {
                logger.error("[{}] 定时刷新任务异常: {}", name(), e.getMessage(), e);
            }
        }, intervalSec, intervalSec, TimeUnit.SECONDS);

        logger.info("[{}] 守护定时刷新调度器已启动！", name());
    }

    /**
     * 关闭定时线程池
     */
    private void stopScheduler() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            logger.info("[{}] 守护定时刷新调度器已停止！", name());
        }
    }
}
