package com.yxinmiracle.yxinmiracleojcodesandbox.model;

/*
 * @author  YxinMiracle
 * @date  2024-07-07 18:21
 * @Gitee: https://gitee.com/yxinmiracle
 */

import lombok.Data;

@Data
public class ExecuteMessage {

    private Integer exitValue;

    private String message;

    private String errorMessage;

    private Long time;

}
