package com.github.xuchen93.grpc.interceptor.server;

import com.github.xuchen93.grpc.interceptor.InterceptorKeys;
import com.github.xuchen93.grpc.interceptor.util.GrpcCommonUtil;
import com.github.xuchen93.grpc.interceptor.util.GrpcInterceptorUtil;
import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 服务端日志拦截器
 * 记录请求/响应内容、耗时、TraceId、是否成功等信息
 */
@Slf4j
public class LoggingServerInterceptor implements ServerInterceptor {

	@Override
	public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
			ServerCall<ReqT, RespT> call,
			Metadata headers,
			ServerCallHandler<ReqT, RespT> next) {

		long requestId = GrpcInterceptorUtil.generateRequestId();
		String methodName = call.getMethodDescriptor().getFullMethodName();

		// 从Metadata中获取TraceId
		String traceId = headers.get(InterceptorKeys.TRACE_ID_KEY);
		if (traceId == null || traceId.isEmpty()) {
			traceId = GrpcInterceptorUtil.generateTraceId();
		}

		RequestContext context = new RequestContext(requestId, traceId, methodName, System.currentTimeMillis());

		LoggingServerCall<ReqT, RespT> loggingCall = new LoggingServerCall<>(call, context);
		ServerCall.Listener<ReqT> listener = next.startCall(loggingCall, headers);

		return new LoggingServerCallListener<>(listener, context);
	}

	private class LoggingServerCall<ReqT, RespT> extends ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT> {
		private final RequestContext context;

		public LoggingServerCall(ServerCall<ReqT, RespT> delegate, RequestContext context) {
			super(delegate);
			this.context = context;
		}

		@Override
		public void sendMessage(RespT message) {
			context.setResponseBody(GrpcCommonUtil.trimChangeLine(message));
			// 使用 info 级别记录响应内容
			log.info("[{}][{}] 响应内容: {}", context.getTraceId(), context.getRequestId(),
					truncateString(context.getResponseBody(), 500));
			super.sendMessage(message);
		}

		@Override
		public void close(Status status, Metadata trailers) {
			long costTime = System.currentTimeMillis() - context.getStartTime();
			context.setCostTime(costTime);
			context.setSuccess(status.isOk());

			if (status.isOk()) {
				log.info("[{}][{}][{}] [耗时={}ms] 请求处理成功",
						context.getTraceId(), context.getRequestId(),
						context.getMethodName(), costTime);
			} else {
				log.error("[{}][{}][{}] [耗时={}ms] 请求处理失败: [Code={}] [Description={}]",
						context.getTraceId(), context.getRequestId(),
						context.getMethodName(), costTime,
						status.getCode(), status.getDescription());
			}
			super.close(status, trailers);
		}
	}

	private class LoggingServerCallListener<ReqT> extends ForwardingServerCallListener<ReqT> {
		private final ServerCall.Listener<ReqT> delegate;
		private final RequestContext context;

		public LoggingServerCallListener(ServerCall.Listener<ReqT> delegate, RequestContext context) {
			this.delegate = delegate;
			this.context = context;
		}

		@Override
		protected ServerCall.Listener<ReqT> delegate() {
			return delegate;
		}

		@Override
		public void onMessage(ReqT message) {
			context.setRequestBody(GrpcCommonUtil.trimChangeLine(message));
			log.info("[{}][{}][{}] 参数：{}", context.getTraceId(), context.getRequestId()
					, context.getMethodName(), truncateString(context.getRequestBody(), 500));
			super.onMessage(message);
		}

		@Override
		public void onCancel() {
			log.warn("[{}][{}] 请求取消", context.getTraceId(), context.getRequestId());
			super.onCancel();
		}
	}

	@Data
	private static class RequestContext {
		private final long requestId;
		private final String traceId;
		private final String methodName;
		private final long startTime;
		private long costTime;
		private boolean success;
		private String requestBody;
		private String responseBody;

		public RequestContext(long requestId, String traceId, String methodName, long startTime) {
			this.requestId = requestId;
			this.traceId = traceId;
			this.methodName = methodName;
			this.startTime = startTime;
		}
	}

	private String truncateString(String str, int maxLength) {
		if (str == null) {
			return "";
		}
		if (str.length() <= maxLength) {
			return str;
		}
		return str.substring(0, maxLength) + "... (truncated, total " + str.length() + " chars)";
	}
}
