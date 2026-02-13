package com.github.xuchen93.grpc.interceptor.util;

public class GrpcCommonUtil {

	public static String trimChangeLine(Object messageObj) {
		if (messageObj == null) {
			return "null";
		}
		String message = messageObj.toString();
		if (message.endsWith("\n")) {
			message = message.substring(0, message.length() - 1);
		}
		return message;
	}
}
