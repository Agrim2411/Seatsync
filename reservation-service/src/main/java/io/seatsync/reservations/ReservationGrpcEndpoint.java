package io.seatsync.reservations;

import io.grpc.*;
import io.grpc.stub.StreamObserver;
import io.seatsync.grpc.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Component
class ReservationGrpcEndpoint
    extends ReservationCommandServiceGrpc.ReservationCommandServiceImplBase {
  private final ReservationService reservations;

  ReservationGrpcEndpoint(ReservationService reservations) {
    this.reservations = reservations;
  }

  @Override
  public void confirmHold(ConfirmHoldRequest request, StreamObserver<HoldReply> observer) {
    try {
      var result =
          reservations.confirm(
              UUID.fromString(request.getHoldId()), UUID.fromString(request.getCustomerId()));
      observer.onNext(
          HoldReply.newBuilder()
              .setHoldId(result.holdId().toString())
              .setStatus(result.status())
              .build());
      observer.onCompleted();
    } catch (ReservationException e) {
      observer.onError(
          Status.FAILED_PRECONDITION
              .withDescription(e.getCode() + ":" + e.getMessage())
              .asRuntimeException());
    }
  }

  @Override
  public void releaseHold(ReleaseHoldRequest request, StreamObserver<HoldReply> observer) {
    try {
      reservations.release(
          UUID.fromString(request.getHoldId()), UUID.fromString(request.getCustomerId()));
      observer.onNext(
          HoldReply.newBuilder().setHoldId(request.getHoldId()).setStatus("RELEASED").build());
      observer.onCompleted();
    } catch (ReservationException e) {
      observer.onError(
          Status.FAILED_PRECONDITION
              .withDescription(e.getCode() + ":" + e.getMessage())
              .asRuntimeException());
    }
  }
}

@Configuration
class GrpcServerConfiguration {
  @Bean(initMethod = "start", destroyMethod = "shutdown")
  Server reservationGrpcServer(
      ReservationGrpcEndpoint endpoint, @Value("${seatsync.grpc-port:9082}") int port) {
    return ServerBuilder.forPort(port).addService(endpoint).build();
  }
}
