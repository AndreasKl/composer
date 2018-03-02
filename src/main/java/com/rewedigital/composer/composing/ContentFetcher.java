package com.rewedigital.composer.composing;

import java.util.concurrent.CompletableFuture;

import com.spotify.apollo.Response;

public interface ContentFetcher {
    CompletableFuture<Response<String>> fetch(String path, String fallback, CompositionStep step);
}
