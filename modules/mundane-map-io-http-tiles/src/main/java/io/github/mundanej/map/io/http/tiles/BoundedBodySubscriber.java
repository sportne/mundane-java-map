package io.github.mundanej.map.io.http.tiles;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private static final int SEGMENT_SIZE = 64 * 1_024;
    private final int maximumBytes;
    private final long expectedLength;
    private final CompletableFuture<byte[]> body = new CompletableFuture<>();
    private final List<byte[]> segments = new ArrayList<>();
    private Flow.Subscription subscription;
    private int size;
    private int segmentOffset;

    BoundedBodySubscriber(int maximumBytes, long expectedLength) {
        this.maximumBytes = maximumBytes;
        this.expectedLength = expectedLength;
    }

    @Override
    public CompletionStage<byte[]> getBody() {
        return body;
    }

    @Override
    public synchronized void onSubscribe(Flow.Subscription value) {
        if (subscription != null) {
            value.cancel();
            return;
        }
        subscription = value;
        if (body.isDone()) {
            value.cancel();
            return;
        }
        value.request(1);
    }

    @Override
    public synchronized void onNext(List<ByteBuffer> buffers) {
        if (body.isDone()) {
            subscription.cancel();
            return;
        }
        try {
            for (ByteBuffer buffer : buffers) {
                append(buffer);
            }
            subscription.request(1);
        } catch (RuntimeException failure) {
            subscription.cancel();
            segments.clear();
            body.completeExceptionally(failure);
        }
    }

    @Override
    public synchronized void onError(Throwable failure) {
        segments.clear();
        body.completeExceptionally(
                expectedLength >= 0 && size != expectedLength
                        ? new BodyLengthException()
                        : failure);
    }

    @Override
    public synchronized void onComplete() {
        if (body.isDone()) {
            return;
        }
        if (expectedLength >= 0 && size != expectedLength) {
            segments.clear();
            body.completeExceptionally(new BodyLengthException());
            return;
        }
        byte[] result = new byte[size];
        int destination = 0;
        for (byte[] segment : segments) {
            int count = Math.min(segment.length, size - destination);
            System.arraycopy(segment, 0, result, destination, count);
            destination += count;
        }
        segments.clear();
        body.complete(result);
    }

    synchronized void cancel() {
        if (subscription != null) {
            subscription.cancel();
        }
        segments.clear();
        body.completeExceptionally(new CancellationException("HTTP tile body cancelled"));
    }

    boolean await(long timeoutNanos) throws InterruptedException {
        try {
            body.get(timeoutNanos, TimeUnit.NANOSECONDS);
            return true;
        } catch (CancellationException | ExecutionException ignored) {
            return true;
        } catch (TimeoutException failure) {
            return false;
        }
    }

    private void append(ByteBuffer buffer) {
        int remaining = buffer.remaining();
        if (remaining > maximumBytes - size) {
            throw new BodyLimitException();
        }
        while (buffer.hasRemaining()) {
            if (segments.isEmpty() || segmentOffset == SEGMENT_SIZE) {
                segments.add(new byte[Math.min(SEGMENT_SIZE, maximumBytes - size)]);
                segmentOffset = 0;
            }
            byte[] segment = segments.getLast();
            int count = Math.min(buffer.remaining(), segment.length - segmentOffset);
            buffer.get(segment, segmentOffset, count);
            segmentOffset += count;
            size += count;
        }
    }

    static final class BodyLimitException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    static final class BodyLengthException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
