package com.github.xuchen93.grpc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.grpc.client.BlockingStubFactory;
import org.springframework.grpc.client.ImportGrpcClients;
import org.springframework.grpc.client.ReactorStubFactory;
import org.springframework.grpc.client.SimpleStubFactory;

//有注解为AnnotationGrpcClientRegistrar，否则为DefaultGrpcClientRegistrations
@ImportGrpcClients(target = "localhost:19090", basePackages = "com.github.xuchen93.grpc.api", factory = BlockingStubFactory.class)
@ImportGrpcClients(target = "localhost:19090", basePackages = "com.github.xuchen93.grpc.api", factory = SimpleStubFactory.class)
@SpringBootApplication
public class GrpcClientApp {

	public static void main(String[] args) {
		SpringApplication.run(GrpcClientApp.class, args);
	}

}
