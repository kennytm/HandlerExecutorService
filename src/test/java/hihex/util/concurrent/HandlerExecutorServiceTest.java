package hihex.util.concurrent;

import android.os.HandlerThread;
import android.os.Looper;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.Uninterruptibles;

import junit.framework.TestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants=BuildConfig.class, sdk=21)
public final class HandlerExecutorServiceTest extends TestCase {
    private Thread mThread;
    private Looper mLooper;
    private ListeningExecutorService mService;

    @Before
    @Override
    public void setUp() {
        if (Shadows.shadowOf(Looper.getMainLooper()) != null) {
            // We are in Robolectrics. Use the shadows.
            final SettableFuture<Looper> looper = SettableFuture.create();
            mThread = new Thread("test") {
                @Override
                public void run() {
                    Looper.prepare();
                    final Looper myLooper = Looper.myLooper();
                    assert myLooper != null;

                    final ShadowLooper shadowLooper = Shadows.shadowOf(myLooper);

                    looper.set(myLooper);
                    do {
                        Uninterruptibles.sleepUninterruptibly(20, TimeUnit.MILLISECONDS);
                        shadowLooper.idle(20);
                    } while (!shadowLooper.hasQuit());
                }
            };
            mThread.start();
            mLooper = Futures.getUnchecked(looper);
            mService = new HandlerExecutorService(mLooper);
        } else {
            // We are in Android. Use Android test.
            final HandlerThread thread = new HandlerThread("test");
            mThread = thread;
            thread.start();
            mLooper = thread.getLooper();
            mService = new HandlerExecutorService(mLooper);
        }
    }

    @After
    @Override
    public void tearDown() {
        mLooper.quit();
    }

    private static void yield() {
        // Thread.yield();
        Uninterruptibles.sleepUninterruptibly(10, TimeUnit.MILLISECONDS);
    }

    private void runOrderlyShutdownTest(final ListeningExecutorService service) throws InterruptedException {
        final AtomicInteger a = new AtomicInteger(0);

        final ListenableFuture<?> future2 = service.submit(new Runnable() {
            @Override
            public void run() {
                yield();
                a.getAndAdd(2);
            }
        });
        final ListenableFuture<?> future4 = service.submit(new Runnable() {
            @Override
            public void run() {
                yield();
                a.getAndAdd(4);
            }
        });

        // At this point nothing is run.
        assertEquals(0, a.get());
        assertFalse(future2.isDone());
        assertFalse(future4.isDone());

        service.shutdown();
        assertTrue(service.isShutdown());
        assertFalse(service.isTerminated());

        // This won't run since the service has been shutdown.
        try {
            service.submit(new Runnable() {
                @Override
                public void run() {
                    a.getAndAdd(8);
                }
            });
            fail("Should throw RejectedExecutionException when service is shutdown");
        } catch (final RejectedExecutionException e) {
            // Pass.
        }

        final boolean awaitResult = service.awaitTermination(500, TimeUnit.MILLISECONDS);
        assertTrue(awaitResult);
        assertTrue(service.isShutdown());
        assertTrue(service.isTerminated());

        assertEquals(6, a.get());

        assertTrue(future2.isDone());
        assertTrue(future4.isDone());
    }

    @Test
    public void testSanityNormalOrderlyShutdown() throws InterruptedException {
        final ListeningExecutorService service = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
        runOrderlyShutdownTest(service);
    }

    @Test
    public void testHandlerOrderlyShutdown() throws InterruptedException {
        runOrderlyShutdownTest(mService);
        mThread.join(100); // wait a while to let the thread to shutdown.
        assertFalse(mThread.isAlive());
    }

    @Test
    public void testFutureCancelling() throws InterruptedException {
        final AtomicInteger a = new AtomicInteger(0);

        final ListenableFuture<?> future = mService.submit(new Runnable() {
            @Override
            public void run() {
                yield();
                a.getAndAdd(2);
                try {
                    Thread.sleep(400);
                } catch (final InterruptedException e) {
                    a.getAndAdd(8);
                    return;
                }
                a.getAndAdd(4);
            }
        });

        Thread.sleep(150);

        assertEquals(2, a.get());
        assertFalse(future.isDone());
        assertFalse(future.isCancelled());

        future.cancel(true);
        //assertEquals(2, a.get());
        assertTrue(future.isDone());
        assertTrue(future.isCancelled());

        Thread.sleep(250);
        // This ensures the `a += 4` statement is not run, but the InterruptedException is indeed caught.
        assertEquals(10, a.get());
        assertTrue(future.isDone());
        assertTrue(future.isCancelled());
    }

    private void runIncompleteAwaitTerminationTest(final ListeningExecutorService service) throws InterruptedException {
        final AtomicInteger a = new AtomicInteger(0);

        service.submit(new Runnable() {
            @Override
            public void run() {
                yield();
                a.addAndGet(2);
                try {
                    Thread.sleep(4000);
                } catch (final InterruptedException e) {
                    a.addAndGet(4);
                    return;
                }
                a.addAndGet(8);
            }
        });

        final boolean awaitResult = service.awaitTermination(100, TimeUnit.MILLISECONDS);
        assertEquals(2, a.get());
        assertFalse(awaitResult);
        assertFalse(service.isShutdown());
        assertFalse(service.isTerminated());

        service.shutdown();
        final boolean awaitResult2 = service.awaitTermination(100, TimeUnit.MILLISECONDS);
        assertEquals(2, a.get());
        assertFalse(awaitResult2);
        assertTrue(service.isShutdown());
        assertFalse(service.isTerminated());

        final List<Runnable> lst = service.shutdownNow();
        //assertEquals(2, a.get());
        assertTrue(lst.isEmpty());
        assertTrue(service.isShutdown());
        //assertFalse(service.isTerminated());

        final boolean awaitResult3 = service.awaitTermination(100, TimeUnit.MILLISECONDS);
        assertEquals(6, a.get());
        assertTrue(awaitResult3);
        assertTrue(service.isShutdown());
        assertTrue(service.isTerminated());
    }

    @Test
    public void testSanityNormalIncompleteAwaitTermination() throws InterruptedException {
        final ListeningExecutorService service = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
        runIncompleteAwaitTerminationTest(service);
    }

    @Test
    public void testIncompleteAwaitTermination() throws InterruptedException {
        runIncompleteAwaitTerminationTest(mService);
        mThread.join(100); // wait a while to let the thread to shutdown.
        assertFalse(mThread.isAlive());
    }

    @Test
    public void testShutdownNowTasksList() throws InterruptedException {
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                yield();
            }
        };

        mService.submit(runnable);

        Thread.sleep(50);

        mService.submit(runnable);
        mService.submit(runnable);

        final List<?> list = mService.shutdownNow();
        assertEquals(2, list.size());
    }
}
