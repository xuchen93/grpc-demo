package com.github.xuchen93.grpc.server.config;

import com.github.xuchen93.grpc.interceptor.server.AuthServerInterceptor;
import com.github.xuchen93.grpc.interceptor.server.LoggingServerInterceptor;
import io.grpc.ServerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.server.GlobalServerInterceptor;

/**
 * gRPC服务端拦截器全局配置
 * 引用grpc-api模块中的拦截器，使用@Bean + @Order控制执行顺序
 */
@Configuration
public class GrpcServerInterceptorConfig {

	/**
	 * 认证拦截器（来自grpc-api模块）
	 */
	@Bean
	@Order(0)
	@GlobalServerInterceptor
	public ServerInterceptor authServerInterceptor() {
		return new AuthServerInterceptor();
	}

	/**
	 * 日志拦截器（来自grpc-api模块）
	 */
	@Bean
	@Order(1000)
	@GlobalServerInterceptor
	public ServerInterceptor loggingServerInterceptor() {
		return new LoggingServerInterceptor();
	}
}
