package com.dlzh.scratchtextview;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 * @ProjectName: eyes3d
 * @ClassName: ExecutorUtils
 * @Description:
 * @Author: yetongjie
 * @CreateDate: 2020/7/16 15:15
 * @UpdateUser: yetongjie
 * @UpdateDate: 2020/7/16 15:15
 * @UpdateRemark:
 * @Version: 1.0
 */
public class ExecutorManager {

    private static final String TAG = "ExecutorManager";
    /**
     * 磁盘IO线程池
     **/
    private final ExecutorService diskIO;
    /**
     * 网络IO线程池
     **/
    private final ExecutorService networkIO;
    /**
     * UI线程
     **/
    private final Executor mainThread;
    /**
     * 定时任务线程池
     **/
    private final ScheduledExecutorService scheduledExecutor;

    private volatile static ExecutorManager executorManager;

    public static ExecutorManager getInstance() {
        if (executorManager == null) {
            synchronized (ExecutorManager.class) {
                if (executorManager == null) {
                    executorManager = new ExecutorManager();
                }
            }
        }
        return executorManager;
    }

    public ExecutorManager(ExecutorService diskIO, ExecutorService networkIO, Executor mainThread,
                           ScheduledExecutorService scheduledExecutor) {
        this.diskIO = diskIO;
        this.networkIO = networkIO;
        this.mainThread = mainThread;
        this.scheduledExecutor = scheduledExecutor;
    }

    public ExecutorManager() {
        this(diskIoExecutor(), networkExecutor(), new MainThreadExecutor(), scheduledThreadPoolExecutor());
    }

    /**
     * 定时(延时)任务线程池
     * <p>
     * 替代Timer,执行定时任务,延时任务
     */
    public ScheduledExecutorService scheduledExecutor() {
        return scheduledExecutor;
    }

    /**
     * 磁盘IO线程池（单线程）
     * <p>
     * 和磁盘操作有关的进行使用此线程(如读写数据库,读写文件)
     * 禁止延迟,避免等待
     * 此线程不用考虑同步问题
     */
    public ExecutorService diskIO() {
        return diskIO;
    }

    /**
     * 网络IO线程池
     * <p>
     * 网络请求,异步任务等适用此线程
     * 不建议在这个线程 sleep 或者 wait
     */
    public ExecutorService networkIO() {
        return networkIO;
    }

    /**
     * UI线程
     * <p>
     * Android 的MainThread
     * UI线程不能做的事情这个都不能做
     */
    public Executor mainThread() {
        return mainThread;
    }

    private static ScheduledExecutorService scheduledThreadPoolExecutor() {
        return new ScheduledThreadPoolExecutor(16, r -> new Thread(r, "scheduled_executor"),
                (r, executor) -> Log.e(TAG, "rejectedExecution: scheduled executor queue overflow"));
    }

    private static ExecutorService diskIoExecutor() {

        int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();
        int KEEP_ALIVE_TIME = 1;
        TimeUnit KEEP_ALIVE_TIME_UNIT = TimeUnit.SECONDS;

        return new ThreadPoolExecutor(NUMBER_OF_CORES, NUMBER_OF_CORES * 2, KEEP_ALIVE_TIME, KEEP_ALIVE_TIME_UNIT,
                new LinkedBlockingQueue<>(1024),
                r -> new Thread(r, "disk_executor"), (r, executor) -> Log.e(TAG, "rejectedExecution: disk io executor" +
                " queue overflow"));
    }

    private static ExecutorService networkExecutor() {
        return new ThreadPoolExecutor(3, 6, 1000, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(6),
                r -> new Thread(r, "network_executor"), (r, executor) -> Log.e(TAG, "rejectedExecution: network " +
                "executor queue overflow"));
    }


    private static class MainThreadExecutor implements Executor {
        private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(@NonNull Runnable command) {
            mainThreadHandler.post(command);
        }
    }
}
