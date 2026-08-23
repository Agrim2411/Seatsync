package io.seatsync.gateway;

import java.util.UUID;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

@Configuration
class GatewayConfiguration {
  static final String CORRELATION_ID = "X-Correlation-ID";

  @Bean
  GlobalFilter correlationFilter() {
    return (exchange, chain) -> {
      String id = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID);
      if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
      String finalId = id;
      var request =
          exchange.getRequest().mutate().headers(h -> h.set(CORRELATION_ID, finalId)).build();
      exchange.getResponse().getHeaders().set(CORRELATION_ID, finalId);
      return chain.filter(exchange.mutate().request(request).build());
    };
  }

  @Bean
  KeyResolver customerKeyResolver() {
    return exchange ->
        exchange
            .getPrincipal()
            .map(p -> p.getName())
            .switchIfEmpty(
                Mono.justOrEmpty(
                    exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION)))
            .switchIfEmpty(
                Mono.justOrEmpty(exchange.getRequest().getRemoteAddress())
                    .map(a -> a.getAddress().getHostAddress()))
            .defaultIfEmpty("anonymous");
  }
}
