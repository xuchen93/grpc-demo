package com.github.xuchen93.grpc.server.impl;

import cn.hutool.core.thread.ThreadUtil;
import com.github.xuchen93.grpc.api.simple.BidirectionalChatMessage;
import com.github.xuchen93.grpc.api.simple.HelloSimpleRequest;
import com.github.xuchen93.grpc.api.simple.HelloSimpleResponse;
import com.github.xuchen93.grpc.api.simple.HelloSimpleServiceGrpc;
import com.github.xuchen93.grpc.api.simple.StreamRequestChunk;
import com.github.xuchen93.grpc.api.simple.StreamResponseSummary;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@GrpcService
public class HelloSimpleServiceImpl extends HelloSimpleServiceGrpc.HelloSimpleServiceImplBase {

	// 1. 简单RPC：收到一个请求，返回一个响应
	@Override
	public void sayHello(HelloSimpleRequest request, StreamObserver<HelloSimpleResponse> responseObserver) {
		try {
			String name = request.getName();

			// 参数校验
			if (name == null || name.trim().isEmpty()) {
				throw Status.INVALID_ARGUMENT.withDescription("Name cannot be empty").asRuntimeException();
			}

			// 检查特殊字符
			if (name.matches(".*[!@#$%^&*()].*")) {
				throw Status.INVALID_ARGUMENT.withDescription("Name contains invalid characters").asRuntimeException();
			}

			// 检查超长参数
			if (name.length() > 100) {
				throw Status.INVALID_ARGUMENT.withDescription("Name is too long (max 100 characters)").asRuntimeException();
			}

			// 检查以b开头的名称（模拟业务规则）
			if (name.trim().toLowerCase().startsWith("b")) {
				throw Status.FAILED_PRECONDITION.withDescription("Name cannot start with 'b'").asRuntimeException();
			}
			ThreadUtil.sleep(20);
			HelloSimpleResponse response = createHelloResponse(name);

			responseObserver.onNext(response);
			responseObserver.onCompleted();
		} catch (StatusRuntimeException e) {
			log.error("Error handling sayHello request: {}", e.getMessage());
			responseObserver.onError(e);
		} catch (Exception e) {
			log.error("Unexpected error handling sayHello request: {}", e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription("Internal server error").asRuntimeException());
		}
	}

	private HelloSimpleResponse createHelloResponse(String name) {
		return HelloSimpleResponse.newBuilder()
				.setMessage("Hello, " + name + "! This is a Unary RPC.")
				.build();
	}

	// 2. 服务端流式RPC：收到一个请求，通过流返回多个响应
	@Override
	public void streamHello(HelloSimpleRequest request, StreamObserver<HelloSimpleResponse> responseObserver) {
		try {
			String name = request.getName();

			// 参数校验
			if (name == null || name.trim().isEmpty()) {
				throw Status.INVALID_ARGUMENT.withDescription("Name cannot be empty").asRuntimeException();
			}

			for (int i = 1; i <= 5; i++) {
				// 检查线程是否被中断（模拟客户端中断请求）
				if (Thread.currentThread().isInterrupted()) {
					log.info("StreamHello request interrupted by client");
					throw Status.CANCELLED.withDescription("Request cancelled by client").asRuntimeException();
				}

				HelloSimpleResponse response = createStreamResponse(i, name);
				responseObserver.onNext(response);

				// 模拟耗时
				sleep(500);
			}

			responseObserver.onCompleted();
		} catch (StatusRuntimeException e) {
			log.error("Error handling streamHello request: {}", e.getMessage());
			responseObserver.onError(e);
		} catch (Exception e) {
			log.error("Unexpected error handling streamHello request: {}", e.getMessage(), e);
			responseObserver.onError(Status.INTERNAL.withDescription("Internal server error").asRuntimeException());
		}
	}

	private HelloSimpleResponse createStreamResponse(int i, String name) {
		return HelloSimpleResponse.newBuilder()
				.setMessage("Stream Response #" + i + " to " + name)
				.build();
	}

	private void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			log.error("Thread interrupted while sleeping.", e);
			Thread.currentThread().interrupt(); // Restore interrupted status
		}
	}

	// 3. 客户端流式RPC：接收一系列请求，处理完成后返回一个汇总响应
	@Override
	public StreamObserver<StreamRequestChunk> clientStreamHello(StreamObserver<StreamResponseSummary> responseObserver) {
		return new StreamObserver<StreamRequestChunk>() {
			private long count = 0;
			private long total = 0;
			private AtomicBoolean hasError = new AtomicBoolean(false);

			@Override
			public void onNext(StreamRequestChunk value) {
				try {
					// 检查是否已经出错
					if (hasError.get()) {
						return;
					}

					long number = value.getNumber();
					log.info("Received chunk: {}", number);

					// 检查数字是否过大（可能导致溢出）
					if (number > 1000000) {
						throw Status.INVALID_ARGUMENT.withDescription("Number too large: " + number).asRuntimeException();
					}

					// 检查是否会导致溢出
					if (number > Long.MAX_VALUE - total) {
						throw Status.INVALID_ARGUMENT.withDescription("Number would cause overflow: " + number).asRuntimeException();
					}

					count++;
					total += number;

				} catch (StatusRuntimeException e) {
					log.error("Error handling client stream chunk: {}", e.getMessage());
					hasError.set(true);
					responseObserver.onError(e);
				} catch (Exception e) {
					log.error("Unexpected error handling client stream chunk: {}", e.getMessage(), e);
					hasError.set(true);
					responseObserver.onError(Status.INTERNAL.withDescription("Internal server error").asRuntimeException());
				}
			}

			@Override
			public void onError(Throwable t) {
				log.error("Client stream error: {}", t.getMessage(), t);
				hasError.set(true);
				// 客户端取消请求时，服务端也应该取消
				responseObserver.onError(Status.CANCELLED.withDescription("Client cancelled request").asRuntimeException());
			}

			@Override
			public void onCompleted() {
				if (hasError.get()) {
					return;
				}

				try {
					double avg = count > 0 ? (double) total / count : 0;
					StreamResponseSummary summary = StreamResponseSummary.newBuilder()
							.setChunkCount(count)
							.setTotalNumber(total)
							.setAverageNumber(avg)
							.setMessage("Client streaming finished.")
							.build();
					responseObserver.onNext(summary);
					responseObserver.onCompleted();
				} catch (Exception e) {
					log.error("Error creating client stream summary: {}", e.getMessage(), e);
					responseObserver.onError(Status.INTERNAL.withDescription("Internal server error").asRuntimeException());
				}
			}
		};
	}

	// 4. 双向流式RPC：客户端和服务端都可以随时发送消息
	@Override
	public StreamObserver<BidirectionalChatMessage> bidirectionalChat(StreamObserver<BidirectionalChatMessage> responseObserver) {
		return new StreamObserver<BidirectionalChatMessage>() {
			private AtomicBoolean hasError = new AtomicBoolean(false);

			@Override
			public void onNext(BidirectionalChatMessage value) {
				try {
					// 检查是否已经出错
					if (hasError.get()) {
						return;
					}

					String username = value.getUsername();
					String message = value.getMessage();
					boolean isError = value.getIsError();

					log.info("Chat from [{}]: {}", username, message);

					// 参数校验
					if (username == null || username.trim().isEmpty()) {
						throw Status.INVALID_ARGUMENT.withDescription("Username cannot be empty").asRuntimeException();
					}

					if (message == null || message.trim().isEmpty()) {
						// 发送错误响应但不中断连接
						BidirectionalChatMessage errorResponse = BidirectionalChatMessage.newBuilder()
								.setUsername("Server")
								.setMessage("Error: Message cannot be empty")
								.setIsError(true)
								.build();
						responseObserver.onNext(errorResponse);
						return;
					}

					// 检查超长消息
					if (message.length() > 1000) {
						throw Status.INVALID_ARGUMENT.withDescription("Message is too long (max 1000 characters)").asRuntimeException();
					}

					// 检查客户端标记的错误
					if (isError) {
						throw Status.INVALID_ARGUMENT.withDescription("Client reported error in message").asRuntimeException();
					}

					// 特殊命令处理
					if (message.equals("聊天即将结束")) {
						BidirectionalChatMessage exitResponse = BidirectionalChatMessage.newBuilder()
								.setUsername("Server")
								.setMessage("收到退出指令，聊天即将结束")
								.setIsError(false)
								.build();
						responseObserver.onNext(exitResponse);
						return;
					}

					// 模拟业务逻辑：服务端回显并添加前缀
					BidirectionalChatMessage echo = BidirectionalChatMessage.newBuilder()
							.setUsername("Server")
							.setMessage("Echo: " + message)
							.setIsError(false)
							.build();
					responseObserver.onNext(echo);

				} catch (StatusRuntimeException e) {
					log.error("Error handling bidirectional chat message: {}", e.getMessage());
					hasError.set(true);

					// 发送错误响应
					BidirectionalChatMessage errorResponse = BidirectionalChatMessage.newBuilder()
							.setUsername("Server")
							.setMessage("Error: " + e.getStatus().getDescription())
							.setIsError(true)
							.build();
					responseObserver.onNext(errorResponse);

					// 对于严重错误，关闭连接
					if (e.getStatus().getCode() != Status.Code.INVALID_ARGUMENT) {
						responseObserver.onCompleted();
					}
				} catch (Exception e) {
					log.error("Unexpected error handling bidirectional chat message: {}", e.getMessage(), e);
					hasError.set(true);

					BidirectionalChatMessage errorResponse = BidirectionalChatMessage.newBuilder()
							.setUsername("Server")
							.setMessage("Error: Internal server error")
							.setIsError(true)
							.build();
					responseObserver.onNext(errorResponse);
					responseObserver.onCompleted();
				}
			}

			@Override
			public void onError(Throwable t) {
				log.error("Bidirectional chat error: {}", t.getMessage(), t);
				hasError.set(true);

				BidirectionalChatMessage errorResponse = BidirectionalChatMessage.newBuilder()
						.setUsername("Server")
						.setMessage("Error: Client connection error")
						.setIsError(true)
						.build();
				responseObserver.onNext(errorResponse);
				responseObserver.onCompleted();
			}

			@Override
			public void onCompleted() {
				if (hasError.get()) {
					return;
				}

				BidirectionalChatMessage finishResponse = BidirectionalChatMessage.newBuilder()
						.setUsername("Server")
						.setMessage("聊天结束，再见！")
						.setIsError(false)
						.build();
				responseObserver.onNext(finishResponse);
				responseObserver.onCompleted();
			}
		};
	}
}
