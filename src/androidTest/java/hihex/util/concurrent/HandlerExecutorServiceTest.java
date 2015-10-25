package hihex.util.concurrent;

import android.os.HandlerThread;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import junit.framework.TestCase;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class HandlerExecutorServiceTest extends TestCase {
    private HandlerThread mThread;
    private ListeningExecutorService mService;

    @Override
    public void setUp() {
        mThread = new HandlerThread("Test");
        mThread.start();
        mService = new HandlerExecutorService(mThread.getLooper());
    }

    private void runOrderlyShutdownTest(final ListeningExecutorService service) throws InterruptedException {
        final AtomicInteger a = new AtomicInteger(0);

        final ListenableFuture<?> future2 = service.submit(new Runnable() {
            @Override
            public void run() {
                a.getAndAdd(2);
            }
        });
        final ListenableFuture<?> future4 = service.submit(new Runnable() {
            @Override
            public void run() {
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

    public void testSanityNormalOrderlyShutdown() throws InterruptedException {
        final ListeningExecutorService service = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
        runOrderlyShutdownTest(service);
    }

    public void testHandlerOrderlyShutdown() throws InterruptedException {
        runOrderlyShutdownTest(mService);
        mThread.join(100); // wait a while to let the thread to shutdown.
        assertFalse(mThread.isAlive());
    }

    public void testFutureCancelling() throws InterruptedException {
        final AtomicInteger a = new AtomicInteger(0);

        final ListenableFuture<?> future = mService.submit(new Runnable() {
            @Override
            public void run() {
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
        assertEquals(2, a.get());
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
        assertEquals(2, a.get());
        assertTrue(lst.isEmpty());
        assertTrue(service.isShutdown());
        assertFalse(service.isTerminated());

        final boolean awaitResult3 = service.awaitTermination(100, TimeUnit.MILLISECONDS);
        assertEquals(6, a.get());
        assertTrue(awaitResult3);
        assertTrue(service.isShutdown());
        assertTrue(service.isTerminated());
    }

    public void testSanityNormalIncompleteAwaitTermination() throws InterruptedException {
        final ListeningExecutorService service = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
        runIncompleteAwaitTerminationTest(service);
    }

    public void testIncompleteAwaitTermination() throws InterruptedException {
        runIncompleteAwaitTerminationTest(mService);
        mThread.join(100); // wait a while to let the thread to shutdown.
        assertFalse(mThread.isAlive());
    }

    public void testShutdownNowTasksList() throws InterruptedException {
        mService.submit(new Runnable() {
            @Override
            public void run() {
            }
        });

        Thread.sleep(50);

        mService.submit(new Runnable() {
            @Override
            public void run() {
            }
        });
        mService.submit(new Runnable() {
            @Override
            public void run() {
            }
        });

        final List<?> list = mService.shutdownNow();
        assertEquals(2, list.size());
    }
}
