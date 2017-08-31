package com.rewedigital.examples.msintegration.composer.proxy;

import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rewedigital.examples.msintegration.composer.composing.Composer;
import com.rewedigital.examples.msintegration.composer.routing.BackendRouting;
import com.rewedigital.examples.msintegration.composer.routing.BackendRouting.RouteMatch;
import com.spotify.apollo.Request;
import com.spotify.apollo.RequestContext;
import com.spotify.apollo.Response;
import com.spotify.apollo.Status;

import okio.ByteString;

public class ComposingRequestHandler {

    private static final CompletableFuture<Response<String>> OHH_NOOSE = CompletableFuture
            .completedFuture(Response.of(Status.NOT_FOUND, "Ohh.. noose!"));

    private static final Logger LOGGER = LoggerFactory.getLogger(ComposingRequestHandler.class);

    private final BackendRouting routing;
    private final TemplateClient templateClient;
    private final Composer composer;

    public ComposingRequestHandler(final BackendRouting routing, final TemplateClient templateClient,
            final Composer composer) {
        this.routing = Objects.requireNonNull(routing);
        this.templateClient = Objects.requireNonNull(templateClient);
        this.composer = Objects.requireNonNull(composer);
    }

    public CompletionStage<Response<String>> execute(final RequestContext context) {
        final Request request = context.request();
        final Optional<RouteMatch> match = routing.matches(request);

        return match.map(rm -> {
            LOGGER.info("The request {} matched the backend route {}.", request, match);
            return templateClient.getTemplate(rm, request, context).thenCompose(r -> compose(rm, r));
        }).orElse(defaultResponse());
    }

    private CompletionStage<Response<String>> compose(final RouteMatch match, final Response<ByteString> response) {
        if (response.status().code() != Status.OK.code() || !response.payload().isPresent()) {
            // Do whatever suits your environment, retrieve the data from a cache,
            // re-execute the request or just fail.
            return defaultResponse();
        }

        final String responseAsUtf8 = response.payload().get().utf8();
        if (match.shouldProxy()) {
            return CompletableFuture.completedFuture(Response.of(Status.OK, responseAsUtf8));
        }
        return composer.compose(responseAsUtf8)
            .thenApply(r -> Response.forPayload(r).withHeaders(transformHeaders(response.headerEntries())));
    }

    private Map<String, String> transformHeaders(final List<Entry<String, String>> headerEntries) {
        // FIXME: Add only response headers in a whitelist.
        return headerEntries.stream().collect(toMap(k -> k.getKey(), v -> v.getValue(), (a, b) -> a));
    }

    private static CompletableFuture<Response<String>> defaultResponse() {
        return OHH_NOOSE;
    }
}