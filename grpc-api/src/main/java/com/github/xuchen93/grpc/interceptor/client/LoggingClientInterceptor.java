package com.github.xuchen93.grpc.interceptor.client;

import com.github.xuchen93.grpc.interceptor.InterceptorKeys;
import com.github.xuchen93.grpc.interceptor.util.GrpcCommonUtil;
import com.github.xuchen93.grpc.interceptor.util.GrpcInterceptorUtil;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 客户端日志拦截器
 * 记录请求/响应内容、耗时、TraceId等信息
 * 使用 InterceptorKeys 中定义的常量
 */
@Slf4j
public class LoggingClientInterceptor implements ClientInterceptor {

	// ThreadLocal存储TraceId
	private static final ThreadLocal<String> TRACE_ID_HOLDER = new ThreadLocal<>();

	public static void setTraceId(String traceId) {
		TRACE_ID_HOLDER.set(traceId);
	}

	public static String getOrCreateTraceId() {
		String traceId = TRACE_ID_HOLDER.get();
		if (traceId == null) {
			traceId = GrpcInterceptorUtil.generateTraceId();
			TRACE_ID_HOLDER.set(traceId);
		}
		return traceId;
	}

	public static void clearTraceId() {
		if (TRACE_ID_HOLDER.get() != null) {
			TRACE_ID_HOLDER.remove();
		}
	}

	@Override
	public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
			MethodDescriptor<ReqT, RespT> method,
			CallOptions callOptions,
			Channel next) {

		long requestId = GrpcInterceptorUtil.generateRequestId();
		String traceId = getOrCreateTraceId();
		RequestInfo requestInfo = new RequestInfo(requestId, traceId, method.getFullMethodName());

		return new LoggingClientCall<>(next.newCall(method, callOptions), requestInfo);
	}

	private class LoggingClientCall<ReqT, RespT> extends ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT> {
		private final RequestInfo requestInfo;

		public LoggingClientCall(ClientCall<ReqT, RespT> delegate, RequestInfo requestInfo) {
			super(delegate);
			this.requestInfo = requestInfo;
		}

		@Override
		public void start(ClientCall.Listener<RespT> responseListener, Metadata headers) {
			// 使用 InterceptorKeys 中定义的常量
			headers.put(InterceptorKeys.TRACE_ID_KEY, requestInfo.getTraceId());
			requestInfo.setStartTime(System.currentTimeMillis());

			log.info("[{}][{}][{}] 请求开始",
					requestInfo.getTraceId(), requestInfo.getRequestId(), requestInfo.getMethodName());

			super.start(new LoggingClientCallListener<>(responseListener, requestInfo), headers);
		}

		@Override
		public void sendMessage(ReqT message) {
			requestInfo.setRequestBody(GrpcCommonUtil.trimChangeLine(message));
			// 使用 info 级别记录请求内容
			log.info("[{}][{}] 请求内容: {}",
					requestInfo.getTraceId(), requestInfo.getRequestId(),
					truncateString(requestInfo.getRequestBody(), 500));
			super.sendMessage(message);
		}
	}

	private class LoggingClientCallListener<RespT> extends ForwardingClientCallListener<RespT> {
		private final ClientCall.Listener<RespT> delegate;
		private final RequestInfo requestInfo;

		public LoggingClientCallListener(ClientCall.Listener<RespT> delegate, RequestInfo requestInfo) {
			this.delegate = delegate;
			this.requestInfo = requestInfo;
		}

		@Override
		protected ClientCall.Listener<RespT> delegate() {
			return delegate;
		}

		@Override
		public void onMessage(RespT message) {
			requestInfo.setResponseBody(GrpcCommonUtil.trimChangeLine(message));
			// 使用 info 级别记录响应内容
			log.info("[{}][{}] 响应内容: {}",
					requestInfo.getTraceId(), requestInfo.getRequestId(),
					truncateString(requestInfo.getResponseBody(), 500));
			super.onMessage(message);
		}

		@Override
		public void onClose(Status status, Metadata trailers) {
			long costTime = System.currentTimeMillis() - requestInfo.getStartTime();
			requestInfo.setCostTime(costTime);
			requestInfo.setSuccess(status.isOk());

			if (status.isOk()) {
				log.info("[{}][{}][{}] [耗时={}ms] 请求成功完成",
						requestInfo.getTraceId(), requestInfo.getRequestId(),
						requestInfo.getMethodName(), costTime);
			} else {
				log.error("[{}][{}][{}] [耗时={}ms] 请求处理失败: [Code={}] [Description={}]",
						requestInfo.getTraceId(), requestInfo.getRequestId(),
						requestInfo.getMethodName(), costTime,
						status.getCode(), status.getDescription());
			}
			super.onClose(status, trailers);
		}
	}

	@Data
	private static class RequestInfo {
		private final long requestId;
		private final String traceId;
		private final String methodName;
		private long startTime;
		private long costTime;
		private boolean success;
		private String requestBody;
		private String responseBody;

		public RequestInfo(long requestId, String traceId, String methodName) {
			this.requestId = requestId;
			this.traceId = traceId;
			this.methodName = methodName;
		}
	}

	private String truncateString(String str, int maxLength) {
		if (str == null) {
			return "null";
		}
		if (str.length() <= maxLength) {
			return str;
		}
		return str.substring(0, maxLength) + "... (truncated, total " + str.length() + " chars)";
	}
}
