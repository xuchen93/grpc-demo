package com.github.xuchen93.grpc.interceptor.client;

import com.github.xuchen93.grpc.interceptor.InterceptorKeys;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import lombok.extern.slf4j.Slf4j;

/**
 * 客户端认证拦截器
 * 负责将认证Token添加到请求Header中
 * 使用 InterceptorKeys 中定义的常量
 */
@Slf4j
public class AuthClientInterceptor implements ClientInterceptor {

	// ThreadLocal存储当前线程的Token
	private static final ThreadLocal<String> TOKEN_HOLDER = new ThreadLocal<>();

	/**
	 * 设置当前线程的认证Token
	 */
	public static void setToken(String token) {
		TOKEN_HOLDER.set(token);
		log.info("[AuthClientInterceptor] Token已设置到当前线程");
	}

	/**
	 * 清除当前线程的Token
	 */
	public static void clearToken() {
		if (TOKEN_HOLDER.get() != null) {
			TOKEN_HOLDER.remove();
			log.info("[AuthClientInterceptor] Token已清除");
		}
	}

	/**
	 * 获取当前线程的Token
	 */
	public static String getToken() {
		return TOKEN_HOLDER.get();
	}

	@Override
	public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
			MethodDescriptor<ReqT, RespT> method,
			CallOptions callOptions,
			Channel next) {

		// 创建包装过的ClientCall
		return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
				next.newCall(method, callOptions)) {

			@Override
			public void start(Listener<RespT> responseListener, Metadata headers) {
				// 从ThreadLocal获取Token并添加到Header
				String token = getToken();
				if (token != null && !token.isEmpty()) {
					String authHeader = token.startsWith("Bearer ") ? token : "Bearer " + token;
					// 使用 InterceptorKeys 中定义的常量
					headers.put(InterceptorKeys.AUTHORIZATION_KEY, authHeader);
					log.info("[AuthClientInterceptor] 已添加Authorization Header: {}", authHeader.substring(0, Math.min(20, authHeader.length())) + "...");
				} else {
					log.warn("[AuthClientInterceptor] 当前线程未设置Token，请求将不包含认证信息");
				}
				super.start(responseListener, headers);
			}
		};
	}
}
