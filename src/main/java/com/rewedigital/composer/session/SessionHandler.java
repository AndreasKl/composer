package com.rewedigital.composer.session;

import static com.rewedigital.composer.util.Combiners.throwingCombiner;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import com.spotify.apollo.Request;
import com.spotify.apollo.RequestContext;
import com.spotify.apollo.Response;
import com.typesafe.config.Config;

/**
 * Handles the session lifecycle by
 * <ul>
 * <li>initializing a {@link SessionRoot} from an incoming request</li>
 * <li>processing the session via a chain of {@link SessionHandler.Interceptor}s</li>
 * <li>storing a merged {@link SessionRoot} for an outgoing request</li>
 * </ul>
 */
public abstract class SessionHandler implements SessionRoot.Serializer {

    /**
     * Runs after a session is initializes and is able to modify the session before the request is processed. Instances
     * can be configured via the configuration ({@link SessionConfiguration}). As instances are constructed via
     * reflection, a constructor taking a single {@link Config} object must be present.
     */
    public interface Interceptor {
        CompletionStage<SessionRoot> afterCreation(SessionRoot session, RequestContext context);
    }

    private static final SessionHandler NOOP_HANDLER = new SessionHandler(Collections.emptyList()) {

        @Override
        public <T> Response<T> writeTo(final Response<T> response, final Map<String, String> sessionData,
            final boolean dirty) {
            return response;
        }

        @Override
        protected SessionRoot obtainSession(final Request request) {
            return SessionRoot.empty();
        }

        @Override
        public CompletionStage<SessionRoot> initialize(final RequestContext context) {
            return CompletableFuture.completedFuture(obtainSession(context.request()));
        }
    };


    public static SessionHandler noSession() {
        return NOOP_HANDLER;
    }

    private final List<Interceptor> interceptors;

    protected SessionHandler(final List<Interceptor> interceptors) {
        this.interceptors = new LinkedList<>(interceptors);
    }

    public CompletionStage<SessionRoot> initialize(final RequestContext context) {
        final SessionRoot session = obtainSession(context.request());
        return runInterceptors(session, context);
    }

    public <T> Response<T> store(final ResponseWithSession<T> response) {
        return response.writeSessionToResponse(this);
    }

    protected abstract SessionRoot obtainSession(Request request);


    private CompletionStage<SessionRoot> runInterceptors(final SessionRoot session, final RequestContext context) {
        return interceptors.stream().reduce(CompletableFuture.completedFuture(session),
            (f, i) -> f.thenCompose(s -> i.afterCreation(s, context)), throwingCombiner());
    }
}
