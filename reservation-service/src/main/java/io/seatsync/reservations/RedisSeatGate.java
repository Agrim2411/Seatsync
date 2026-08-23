package io.seatsync.reservations;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
class RedisSeatGate {
  private static final DefaultRedisScript<Long> ACQUIRE =
      new DefaultRedisScript<>(
          "if redis.call('exists', KEYS[1]) == 0 then redis.call('psetex', KEYS[1], ARGV[2],"
              + " ARGV[1]); return 1 else return 0 end",
          Long.class);
  private static final DefaultRedisScript<Long> RELEASE =
      new DefaultRedisScript<>(
          "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else"
              + " return 0 end",
          Long.class);

  private final StringRedisTemplate redis;

  RedisSeatGate(StringRedisTemplate redis) {
    this.redis = redis;
  }

  boolean acquire(UUID eventId, UUID seatId, String token, Duration ttl) {
    Long value =
        redis.execute(ACQUIRE, List.of(key(eventId, seatId)), token, Long.toString(ttl.toMillis()));
    return Long.valueOf(1).equals(value);
  }

  void release(UUID eventId, UUID seatId, String token) {
    redis.execute(RELEASE, List.of(key(eventId, seatId)), token);
  }

  private String key(UUID eventId, UUID seatId) {
    return "seat-hold:" + eventId + ":" + seatId;
  }
}
