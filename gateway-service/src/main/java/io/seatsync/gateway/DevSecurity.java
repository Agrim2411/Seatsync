package io.seatsync.gateway;

import org.springframework.context.annotation.*;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@Profile("!prod")
class DevSecurity {
  @Bean
  SecurityWebFilterChain devSecurityFilterChain(ServerHttpSecurity http) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .authorizeExchange(a -> a.anyExchange().permitAll())
        .build();
  }
}
