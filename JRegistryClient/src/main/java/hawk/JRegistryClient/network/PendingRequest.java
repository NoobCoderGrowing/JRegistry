package hawk.JRegistryClient.network;

import java.util.concurrent.CompletableFuture;

import hawk.JRegitstryCore.RPC.CLIRequest;

public class PendingRequest {
    private final CLIRequest originalRequest;
    private final CompletableFuture<String> future;
    private int redirectRetries;

    public PendingRequest(CLIRequest originalRequest, CompletableFuture<String> future) {
        this.originalRequest = originalRequest;
        this.future = future;
        this.redirectRetries = 0;
    }

    public CLIRequest getOriginalRequest() {
        return originalRequest;
    }

    public CompletableFuture<String> getFuture() {
        return future;
    }

    public int getRedirectRetries() {
        return redirectRetries;
    }

    public void incrementRedirectRetries() {
        this.redirectRetries++;
    }
}
