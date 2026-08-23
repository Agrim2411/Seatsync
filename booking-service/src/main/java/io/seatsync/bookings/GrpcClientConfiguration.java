package io.seatsync.bookings;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class GrpcClientConfiguration {
  @Bean(destroyMethod = "shutdown")
  ManagedChannel reservationChannel(
      @Value("${seatsync.reservation-grpc-host:localhost}") String host,
      @Value("${seatsync.reservation-grpc-port:9082}") int port) {
    return NettyChannelBuilder.forAddress(host, port).usePlaintext().build();
  }

  @Bean
  RestClient.Builder restClientBuilder() {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(500);
    factory.setReadTimeout(3000);
    return RestClient.builder().requestFactory(factory);
  }
}
