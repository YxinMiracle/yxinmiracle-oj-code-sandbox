package com.yxinmiracle.yxinmiracleojcodesandbox.controller;

/*
 * @author  YxinMiracle
 * @date  2024-07-05 9:20
 * @Gitee: https://gitee.com/yxinmiracle
 */

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("/")
public class MainController {

    @GetMapping("/health")
    public String healthCheck(){
        return "OK";
    }



}
