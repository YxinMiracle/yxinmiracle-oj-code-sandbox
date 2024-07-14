package com.yxinmiracle.yxinmiracleojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
import com.yxinmiracle.yxinmiracleojcodesandbox.model.*;
import com.yxinmiracle.yxinmiracleojcodesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
public abstract class JavaCodeSandboxTemplate implements CodeSandbox {


    private static final String GLOBAL_CODE_DIR_NAME = "tempCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final long TIME_OUT = 50000L;


    @Override
    public ExecuteCodeResponses executeCode(ExecuteCodeRequest executeCodeRequest) {

        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String languages = executeCodeRequest.getLanguages();

        // 把用户的代码保存问文件
        File userCodeFile = saveCodeToFile(code);
        // 编译代码 得到class文件
        try {
            ExecuteMessage compileFileExecuteMessage = compileFile(userCodeFile);
            System.out.println(compileFileExecuteMessage);
            log.info("编译用户上传代码结果：{}",compileFileExecuteMessage);
        }catch (Exception e){
            ExecuteCodeResponses executeCodeResponses = new ExecuteCodeResponses();
            executeCodeResponses.setStatus(RemoteExecuteStatusEnum.COMPILE_ERROR.getValue());
            boolean b = deleteFile(userCodeFile);
            return executeCodeResponses;
        }

        // 执行代码
        List<ExecuteMessage> executeMessageList = runFile(userCodeFile, inputList);

        // 收集整理执行结果
        ExecuteCodeResponses outputResponse = getOutputResponse(executeMessageList);

        // 文件清理
        boolean b = deleteFile(userCodeFile);
        if(!b){
            log.error("deleteFile error, userCodeFilePath = {}",userCodeFile.getAbsolutePath());
        }
        // 错误处理

        return outputResponse;
    }

    /**
     * 把用户代码保存成文件
     *
     * @param code 用户在OJ中编写的代码
     * @return
     */
    public File saveCodeToFile(String code) {
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
        return userCodeFile;
    }

    /**
     * 编译文件
     *
     * @param userCodeFile 上一步保存得到的代码文件
     * @return
     */
    public ExecuteMessage compileFile(File userCodeFile) {
        String compliedCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
//        String compliedCmd = "/root/oj_web/yxinmiracle-oj-code-sandbox/tempCode/a391146b-96d3-4e9c-8aa0-a0e2b81a6143/Main.java";
        System.out.println(compliedCmd);
        try {
            Process process = Runtime.getRuntime().exec(compliedCmd);
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(process, "编译");
            if (executeMessage.getExitValue() != 0) {
                throw new RuntimeException("编译错误");
            }
            return executeMessage;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 执行用户上传的文件，获取执行结果列表
     *
     * @param userCodeFile
     * @param inputList
     * @return
     */
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();

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
                throw new RuntimeException("用户程序执行异常");
            }
        }
        return executeMessageList;
    }

    public ExecuteCodeResponses getOutputResponse(List<ExecuteMessage> executeMessageList) {
        ExecuteCodeResponses executeCodeResponses = new ExecuteCodeResponses();
        List<String> outputList = new ArrayList<>();
        long maxTime = 0;
        long maxMemory = 0;
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            if (StrUtil.isNotEmpty(errorMessage)) {
                executeCodeResponses.setMessage(errorMessage);
                executeCodeResponses.setStatus(RemoteExecuteStatusEnum.REMOTE_EXECUTION_ERROR.getValue());
                break;
            }
            outputList.add(executeMessage.getMessage().replace("\n",""));
            if (executeMessage.getTime() != null) {
                maxTime = Math.max(maxTime, executeMessage.getTime());
                maxMemory = Math.max(maxMemory, executeMessage.getMemory());
            }
        }
        if (outputList.size() == executeMessageList.size()) {
            executeCodeResponses.setStatus(RemoteExecuteStatusEnum.SUCCESS.getValue());
        }
        executeCodeResponses.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);
        judgeInfo.setMemory(maxMemory);
        executeCodeResponses.setJudgeInfo(judgeInfo);
        return executeCodeResponses;
    }

    /**
     * 删除文件
     * @param userCodeFile
     * @return
     */
    public boolean deleteFile(File userCodeFile){
        if (userCodeFile.getParentFile() != null) {
            String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
            boolean del = FileUtil.del(userCodeParentPath);
            return del;
        }
        return true;
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
