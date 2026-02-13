package com.github.xuchen93.grpc.interceptor.util;

import com.github.xuchen93.grpc.interceptor.InterceptorKeys;
import io.grpc.Context;

/**
 * gRPC上下文工具类
 * 用于在服务实现中获取请求上下文信息（TraceId、用户信息等）
 */
public class GrpcContextUtil {

	/**
	 * 获取当前请求的TraceId
	 *
	 * @return TraceId，如果未设置则返回null
	 */
	public static String getTraceId() {
		return InterceptorKeys.TRACE_ID_CONTEXT_KEY.get(Context.current());
	}

	/**
	 * 获取当前请求的认证Token
	 *
	 * @return Token，如果未设置则返回null
	 */
	public static String getAuthToken() {
		return InterceptorKeys.AUTH_CONTEXT_KEY.get(Context.current());
	}

	/**
	 * 获取当前请求的用户信息
	 *
	 * @return 用户信息，如果未设置则返回null
	 */
	public static String getUserInfo() {
		return InterceptorKeys.USER_CONTEXT_KEY.get(Context.current());
	}

	/**
	 * 检查当前请求是否已认证
	 *
	 * @return true if authenticated
	 */
	public static boolean isAuthenticated() {
		return getAuthToken() != null && !getAuthToken().isEmpty();
	}

	/**
	 * 获取完整的上下文信息（用于日志记录）
	 *
	 * @return 上下文信息字符串
	 */
	public static String getContextInfo() {
		return String.format("[TraceId=%s, User=%s, Auth=%s]",
				getTraceId(),
				getUserInfo(),
				isAuthenticated() ? "YES" : "NO");
	}
}
