package com.github.xuchen93.grpc.client.config;

import com.github.xuchen93.grpc.interceptor.client.AuthClientInterceptor;
import com.github.xuchen93.grpc.interceptor.client.LoggingClientInterceptor;
import io.grpc.ClientInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.client.GlobalClientInterceptor;

/**
 * gRPC客户端拦截器配置
 * 引用grpc-api模块中的拦截器，使用@Bean + @Order控制执行顺序
 */
@Configuration
public class GrpcClientInterceptorConfig {

	/**
	 * 认证拦截器（来自grpc-api模块）
	 */
	@Bean
	@Order(0)
	@GlobalClientInterceptor
	public ClientInterceptor authClientInterceptor() {
		return new AuthClientInterceptor();
	}

	/**
	 * 日志拦截器（来自grpc-api模块）
	 */
	@Bean
	@Order(1000)
	@GlobalClientInterceptor
	public ClientInterceptor loggingClientInterceptor() {
		return new LoggingClientInterceptor();
	}
}
