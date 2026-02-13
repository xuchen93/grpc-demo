package com.github.xuchen93.grpc.interceptor;

import io.grpc.Context;
import io.grpc.Metadata;

/**
 * gRPC拦截器常量定义类
 * 集中管理所有的Metadata Key和Context Key
 */
public final class InterceptorKeys {

    private InterceptorKeys() {
        // 工具类，禁止实例化
    }

    // ==================== Metadata Keys (用于在请求头中传递信息) ====================

    /**
     * 认证Token的Metadata Key
     * 请求头名称: Authorization
     */
    public static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

    /**
     * 追踪ID的Metadata Key
     * 请求头名称: x-trace-id
     */
    public static final Metadata.Key<String> TRACE_ID_KEY =
            Metadata.Key.of("x-trace-id", Metadata.ASCII_STRING_MARSHALLER);

    // ==================== Context Keys (用于在服务方法中获取信息) ====================

    /**
     * 认证Token的Context Key
     * 用于在服务实现中获取当前请求的Token
     */
    public static final Context.Key<String> AUTH_CONTEXT_KEY = Context.key("auth_token");

    /**
     * 追踪ID的Context Key
     * 用于在服务实现中获取当前请求的TraceId
     */
    public static final Context.Key<String> TRACE_ID_CONTEXT_KEY = Context.key("trace_id");

    /**
     * 用户信息的Context Key
     * 用于在服务实现中获取当前请求的用户信息
     */
    public static final Context.Key<String> USER_CONTEXT_KEY = Context.key("user_info");
}
