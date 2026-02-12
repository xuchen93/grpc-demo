//package com.github.xuchen93.grpc.client.config;
//
//import com.github.xuchen93.grpc.api.HelloServiceGrpc;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.grpc.client.GrpcChannelFactory;
//
///**
// * @author xuchen.wang
// * @date 2026/1/30
// */
//@Configuration
//public class GrpcClientConfig {
//
//	@Bean
//	public HelloServiceGrpc.HelloServiceBlockingStub helloServiceBlockingStub(GrpcChannelFactory channelFactory) {
//		return HelloServiceGrpc.newBlockingStub(channelFactory.createChannel("0.0.0.0:19090"));
//	}
//
//	@Bean
//	public HelloServiceGrpc.HelloServiceStub helloServiceStub(GrpcChannelFactory channelFactory) {
//		return HelloServiceGrpc.newStub(channelFactory.createChannel("0.0.0.0:19090"));
//	}
//}
