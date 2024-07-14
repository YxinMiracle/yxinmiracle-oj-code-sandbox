package com.yxinmiracle.yxinmiracleojcodesandbox;/*
 * @author  YxinMiracle
 * @date  2024-07-07 17:06
 * @Gitee: https://gitee.com/yxinmiracle
 */

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
import com.yxinmiracle.yxinmiracleojcodesandbox.model.ExecuteCodeRequest;
import com.yxinmiracle.yxinmiracleojcodesandbox.model.ExecuteCodeResponses;
import com.yxinmiracle.yxinmiracleojcodesandbox.model.ExecuteMessage;
import com.yxinmiracle.yxinmiracleojcodesandbox.model.JudgeInfo;
import com.yxinmiracle.yxinmiracleojcodesandbox.utils.ProcessUtils;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Deprecated
public class JavaNativeCodeSandboxOld implements CodeSandbox {

    private static final String GLOBAL_CODE_DIR_NAME = "tempCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final long TIME_OUT = 10000L;

    private static final List<String> BLSCK_LIST = Arrays.asList("Files", "exec");

    private static final WordTree WORD_TREE = new WordTree();

    static {
        WORD_TREE.addWords(BLSCK_LIST);
    }

    @Override
    public ExecuteCodeResponses executeCode(ExecuteCodeRequest executeCodeRequest) {
        // todo 限制权限
//        System.setSecurityManager(new DefaultSecurityManager());


        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String languages = executeCodeRequest.getLanguages();

        // todo 危险操作3 有用
        FoundWord foundWord = WORD_TREE.matchWord(code);
        if (foundWord != null) {
            return null;
        }

        // 把用户的代码保存问文件
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;

        // 判断目录是否存在
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        // 把用户的代码隔离存放
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        if (!FileUtil.exist(userCodePath)) {
            FileUtil.touch(userCodePath);
        }
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        // 编译代码 等到class文件
        String compliedCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process process = Runtime.getRuntime().exec(compliedCmd);
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(process, "编译");
            System.out.println(executeMessage);
        } catch (Exception e) {
            return getErrorResponse(e);
        }

        // 执行代码
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {
            // todo 安全方法2 内存
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, inputArgs);

            try {
                Process process = Runtime.getRuntime().exec(runCmd);
                // todo 安全方法1 超时
                new Thread(() -> {
                    try {
                        Thread.sleep(TIME_OUT);
                        process.destroy();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(process, "运行");
                executeMessageList.add(executeMessage);
            } catch (Exception e) {
                return getErrorResponse(e);
            }
        }
        // 收集整理执行结果
        ExecuteCodeResponses executeCodeResponses = new ExecuteCodeResponses();
        List<String> outputList = new ArrayList<>();
        long maxTime = 0;
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            if (StrUtil.isNotEmpty(errorMessage)) {
                executeCodeResponses.setMessage(errorMessage);
                executeCodeResponses.setStatus(3);
                break;
            }
            outputList.add(executeMessage.getMessage());
            if (executeMessage.getTime() != null) {
                maxTime = Math.max(maxTime, executeMessage.getTime());
            }
        }
        if (outputList.size() == executeMessageList.size()) {
            executeCodeResponses.setStatus(1);
        }
        executeCodeResponses.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);

        executeCodeResponses.setJudgeInfo(judgeInfo);

        // 文件清理
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeParentPath);
        }
        // 错误处理

        return executeCodeResponses;
    }

    public static void main(String[] args) {
        JavaNativeCodeSandboxOld javaNativeCodeSandbox = new JavaNativeCodeSandboxOld();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2", "1 3"));

        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguages("java");

        ExecuteCodeResponses executeCodeResponses = javaNativeCodeSandbox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponses);
    }

    private ExecuteCodeResponses getErrorResponse(Throwable e) {
        ExecuteCodeResponses executeCodeResponses = new ExecuteCodeResponses();
        executeCodeResponses.setOutputList(new ArrayList<>());
        executeCodeResponses.setMessage(e.getMessage());
        executeCodeResponses.setStatus(2);
        executeCodeResponses.setJudgeInfo(new JudgeInfo());
        return executeCodeResponses;
    }
}
