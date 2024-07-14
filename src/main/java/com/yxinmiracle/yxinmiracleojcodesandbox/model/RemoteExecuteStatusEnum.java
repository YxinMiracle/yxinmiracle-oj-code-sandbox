package com.yxinmiracle.yxinmiracleojcodesandbox.model;

import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum RemoteExecuteStatusEnum {

    COMPILE_ERROR("远程代码编译错误", 3),
    REMOTE_EXECUTION_ERROR("远程中执行代码错误",2),
    REMOTE_REQUEST_EXECUTION_ERROR("远程请求错误",1),
    SUCCESS("远程执行代码成功",0);

    private final String text;

    private final Integer value;

    RemoteExecuteStatusEnum(String text, Integer value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 获取值列表
     *
     * @return
     */
    public static List<Integer> getValues() {
        return Arrays.stream(values()).map(item -> item.value).collect(Collectors.toList());
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value
     * @return
     */
    public static RemoteExecuteStatusEnum getEnumByValue(Integer value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        for (RemoteExecuteStatusEnum anEnum : RemoteExecuteStatusEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }

    public Integer getValue() {
        return value;
    }

    public String getText() {
        return text;
    }


}
