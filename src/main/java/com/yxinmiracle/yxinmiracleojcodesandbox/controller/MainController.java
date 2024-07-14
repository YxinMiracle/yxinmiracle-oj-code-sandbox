package com.yxinmiracle.yxinmiracleojcodesandbox.controller;

/*
 * @author  YxinMiracle
 * @date  2024-07-05 9:20
 * @Gitee: https://gitee.com/yxinmiracle
 */

import com.yxinmiracle.yxinmiracleojcodesandbox.JavaDockerCodeSandbox;
import com.yxinmiracle.yxinmiracleojcodesandbox.JavaNativeCodeSandbox;
import com.yxinmiracle.yxinmiracleojcodesandbox.model.ExecuteCodeRequest;
import com.yxinmiracle.yxinmiracleojcodesandbox.model.ExecuteCodeResponses;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController("/")
public class MainController {

    private static final String AUTH_HEADER = "auth";

    private static final String AUTH_REQUEST_SECRET = "secretKey";

    @Resource
    private JavaNativeCodeSandbox javaNativeCodeSandbox;

    @Resource
    private JavaDockerCodeSandbox javaDockerCodeSandbox;


    @GetMapping("/health")
    public String healthCheck() {
        return "OK";
    }

    @PostMapping("executeCode")
    ExecuteCodeResponses execute(@RequestBody ExecuteCodeRequest executeCodeRequest, HttpServletRequest request, HttpServletResponse response) {
        String authHeader = request.getHeader(AUTH_HEADER);
        if (!AUTH_REQUEST_SECRET.equals(authHeader)) {
            response.setStatus(403);
            return null;
        }
        System.out.println("www");
        if (executeCodeRequest == null) {
            throw new RuntimeException("请求参数为空");
        }
        return javaDockerCodeSandbox.executeCode(executeCodeRequest);
    }

}
