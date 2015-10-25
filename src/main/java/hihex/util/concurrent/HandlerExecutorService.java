package hihex.util.concurrent;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.AbstractListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public final class HandlerExecutorService extends AbstractListeningExecutorService implements Handler.Callback {
    private final Handler mHandler;
    private final ConcurrentHashMap<Integer, Runnable> mRemainingTasks = new ConcurrentHashMap<>();
    private final SettableFuture<?> mQuitPromise = SettableFuture.create();
    private final AtomicInteger mTaskId = new AtomicInteger();
    private volatile boolean mShouldShutdown = false;

    private static final int RUN_MESSAGE = 1;
    private static final int SHUTDOWN_NOW_MESSAGE = 2;

    public HandlerExecutorService(final @NonNull Looper looper) {
        mHandler = new Handler(looper, this);
    }

    @Override
    public boolean handleMessage(final @NonNull Message msg) {
        switch (msg.what) {
            case RUN_MESSAGE:
                final Runnable task = mRemainingTasks.remove(msg.arg1);
                task.run();
                if (mShouldShutdown && mRemainingTasks.isEmpty()) {
                    terminate();
                }
                break;

            case SHUTDOWN_NOW_MESSAGE:
                terminate();
                break;
        }
        return true;
    }

    @Override
    public void shutdown() {
        mShouldShutdown = true;
    }

    @Override
    @NonNull
    public List<Runnable> shutdownNow() {
        mShouldShutdown = true;
        final ImmutableList<Runnable> remainingTasks = ImmutableList.copyOf(mRemainingTasks.values());
        mRemainingTasks.clear();
        mHandler.getLooper().getThread().interrupt();
        mHandler.sendEmptyMessage(SHUTDOWN_NOW_MESSAGE);
        return remainingTasks;
    }

    @Override
    public boolean isShutdown() {
        return mShouldShutdown;
    }

    private void terminate() {
        mQuitPromise.set(null);
        mHandler.getLooper().quit();
    }

    @Override
    public boolean isTerminated() {
        return mQuitPromise.isDone();
    }

    @Override
    public boolean awaitTermination(final long timeout, final @NonNull TimeUnit unit) throws InterruptedException {
        try {
            mQuitPromise.get(timeout, unit);
            return true;
        } catch (final ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        } catch (final TimeoutException e) {
            return false;
        }
    }

    @Override
    public void execute(final @NonNull Runnable command) {
        if (mShouldShutdown) {
            throw new RejectedExecutionException();
        }
        final int taskId = mTaskId.getAndIncrement();
        mRemainingTasks.put(taskId, command);
        mHandler.sendMessage(mHandler.obtainMessage(RUN_MESSAGE, taskId, 0));
    }
}
