package io.github.mundanej.map.io.http.tiles;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

final class CancellingBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private final CompletableFuture<byte[]> body = CompletableFuture.completedFuture(new byte[0]);

    @Override
    public CompletionStage<byte[]> getBody() {
        return body;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        subscription.cancel();
    }

    @Override
    public void onNext(List<ByteBuffer> item) {
        // Cancellation requests no data; a racing delivery is deliberately ignored.
    }

    @Override
    public void onError(Throwable failure) {
        // Missing-tile success is established from validated response metadata.
    }

    @Override
    public void onComplete() {
        // The result is already complete without retaining the response body.
    }
}
