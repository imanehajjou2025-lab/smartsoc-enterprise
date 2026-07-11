package com.smartsoc.api.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Authentifie la trame STOMP CONNECT avec le même JWT que l'API REST
 * (header natif Authorization) : pas de token en query string — il
 * finirait dans les logs des proxys. Un CONNECT sans token valide est
 * rejeté et la connexion fermée ; les SUBSCRIBE héritent ensuite de
 * l'utilisateur posé sur la session.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;
    private final Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authorization = accessor.getFirstNativeHeader("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                throw new AuthenticationCredentialsNotFoundException(
                        "WebSocket CONNECT requires a Bearer token");
            }
            // JwtException si invalide/expiré -> connexion refusée.
            Jwt jwt = jwtDecoder.decode(authorization.substring("Bearer ".length()));
            accessor.setUser(jwtAuthenticationConverter.convert(jwt));
        }
        return message;
    }
}
