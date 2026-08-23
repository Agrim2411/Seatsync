package io.seatsync.events;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Configuration
@EnableWebSocket
class SeatMapWebSocketConfiguration implements WebSocketConfigurer {
  private final SeatMapWebSocket handler;
  private final String[] allowedOrigins;

  SeatMapWebSocketConfiguration(
      SeatMapWebSocket handler,
      @Value("${seatsync.allowed-origins:http://localhost:3000}") String origins) {
    this.handler = handler;
    this.allowedOrigins = origins.split(",");
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(handler, "/ws/events/**").setAllowedOriginPatterns(allowedOrigins);
  }
}

@org.springframework.stereotype.Component
class SeatMapWebSocket extends TextWebSocketHandler {
  private final ConcurrentMap<UUID, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    eventId(session)
        .ifPresent(
            id -> sessions.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet()).add(session));
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    sessions.values().forEach(set -> set.remove(session));
  }

  void publish(UUID eventId, String message) {
    sessions
        .getOrDefault(eventId, Set.of())
        .forEach(
            session -> {
              if (!session.isOpen()) return;
              try {
                synchronized (session) {
                  session.sendMessage(new TextMessage(message));
                }
              } catch (IOException e) {
                try {
                  session.close(CloseStatus.SERVER_ERROR);
                } catch (IOException ignored) {
                }
              }
            });
  }

  private Optional<UUID> eventId(WebSocketSession session) {
    URI uri = session.getUri();
    if (uri == null) return Optional.empty();
    String[] parts = uri.getPath().split("/");
    try {
      return parts.length >= 4 ? Optional.of(UUID.fromString(parts[3])) : Optional.empty();
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
