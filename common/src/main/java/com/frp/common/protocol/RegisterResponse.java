package com.frp.common.protocol;

import lombok.Data;

/**
 * @author Zhidong Zhang
 */ // 注册响应：服务端返回注册结果给客户端
@Data
public class RegisterResponse {
    private ControlType type = ControlType.REGISTER_RESP;
    // 对应注册请求的代理ID
    private String proxyId;
    private boolean success;
    // 成功失败原因
    private String message;

}
