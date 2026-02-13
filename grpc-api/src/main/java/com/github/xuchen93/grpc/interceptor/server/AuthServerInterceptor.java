package com.github.xuchen93.grpc.interceptor.server;

import com.github.xuchen93.grpc.interceptor.InterceptorKeys;
import com.github.xuchen93.grpc.interceptor.util.GrpcInterceptorUtil;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * 服务端认证拦截器
 * 负责验证请求中的JWT Token，保护受保护的服务端点
 */
@Slf4j
public class AuthServerInterceptor implements ServerInterceptor {

	// 白名单路径（不需要认证的方法）
	private final Set<String> whitelistPaths;

	public AuthServerInterceptor() {
		this(Set.of("grpc.health.v1.Health/Check", "grpc.health.v1.Health/Watch", "grpc.reflection.v1alpha.ServerReflection/ServerReflectionInfo"));
	}

	public AuthServerInterceptor(Set<String> whitelistPaths) {
		this.whitelistPaths = whitelistPaths;
	}

	@Override
	public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
			ServerCall<ReqT, RespT> call,
			Metadata headers,
			ServerCallHandler<ReqT, RespT> next) {

		String fullMethodName = call.getMethodDescriptor().getFullMethodName();

		// 从Metadata中提取TraceId
		String traceId = headers.get(InterceptorKeys.TRACE_ID_KEY);
		if (traceId == null || traceId.isEmpty()) {
			traceId = GrpcInterceptorUtil.generateTraceId();
			log.info("[Auth] 请求未包含TraceId，生成新的: method={}, traceId={}", fullMethodName, traceId);
		}

		final String finalTraceId = traceId;

		// 检查是否在白名单中
		if (isWhitelisted(fullMethodName)) {
			log.info("[Auth] 白名单路径，跳过认证: method={}, traceId={}", fullMethodName, finalTraceId);
			Context context = Context.current()
					.withValue(InterceptorKeys.TRACE_ID_CONTEXT_KEY, finalTraceId)
					.withValue(InterceptorKeys.AUTH_CONTEXT_KEY, "anonymous")
					.withValue(InterceptorKeys.USER_CONTEXT_KEY, "anonymous");
			return Contexts.interceptCall(context, call, headers, next);
		}

		// 从Header中提取Authorization
		String authHeader = headers.get(InterceptorKeys.AUTHORIZATION_KEY);

		if (authHeader == null || authHeader.isEmpty()) {
			log.warn("[Auth] 认证失败: 缺少Authorization Header, method={}, traceId={}", fullMethodName, finalTraceId);
			call.close(Status.UNAUTHENTICATED.withDescription("Missing Authorization header"), new Metadata());
			return new ServerCall.Listener<>() {
			};
		}

		// 验证Token格式
		if (!authHeader.startsWith("Bearer ")) {
			log.warn("[Auth] 认证失败: Authorization格式错误, method={}, traceId={}", fullMethodName, finalTraceId);
			call.close(Status.UNAUTHENTICATED.withDescription("Invalid Authorization format, expected 'Bearer <token>'"), new Metadata());
			return new ServerCall.Listener<>() {
			};
		}

		// 提取Token
		String token = authHeader.substring(7);

		// 验证Token
		String userInfo = validateToken(token);

		if (userInfo == null) {
			log.warn("[Auth] 认证失败: Token无效或已过期, method={}, traceId={}", fullMethodName, finalTraceId);
			call.close(Status.UNAUTHENTICATED.withDescription("Invalid or expired token"), new Metadata());
			return new ServerCall.Listener<>() {
			};
		}

		log.info("[Auth] 认证成功: method={}, traceId={}, user={}",
				fullMethodName, finalTraceId, userInfo);

		// 将认证信息存入Context
		Context context = Context.current()
				.withValue(InterceptorKeys.TRACE_ID_CONTEXT_KEY, finalTraceId)
				.withValue(InterceptorKeys.AUTH_CONTEXT_KEY, token)
				.withValue(InterceptorKeys.USER_CONTEXT_KEY, userInfo);

		return Contexts.interceptCall(context, call, headers, next);
	}

	private boolean isWhitelisted(String fullMethodName) {
		return whitelistPaths.contains(fullMethodName);
	}

	private String validateToken(String token) {
		if (token == null || token.isEmpty()) {
			return null;
		}
		// 简单验证：长度>10或以valid_开头的token视为有效
		if (token.startsWith("valid_") || token.length() > 10) {
			return "user_" + token.hashCode();
		}
		return null;
	}
}
