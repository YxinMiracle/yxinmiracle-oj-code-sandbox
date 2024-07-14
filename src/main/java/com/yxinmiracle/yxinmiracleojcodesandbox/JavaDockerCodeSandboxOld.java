package com.yxinmiracle.yxinmiracleojcodesandbox;

/*
 * @author  YxinMiracle
 * @date  2024-07-07 17:06
 * @Gitee: https://gitee.com/yxinmiracle
 */

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.yxinmiracle.yxinmiracleojcodesandbox.model.ExecuteCodeRequest;
import com.yxinmiracle.yxinmiracleojcodesandbox.model.ExecuteCodeResponses;
import com.yxinmiracle.yxinmiracleojcodesandbox.model.ExecuteMessage;
import com.yxinmiracle.yxinmiracleojcodesandbox.model.JudgeInfo;
import com.yxinmiracle.yxinmiracleojcodesandbox.utils.ProcessUtils;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class JavaDockerCodeSandboxOld implements CodeSandbox {

    private static final String GLOBAL_CODE_DIR_NAME = "tempCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final long TIME_OUT = 5000L;

    private static final Boolean FIRST_INIT = false;


    @Override
    public ExecuteCodeResponses executeCode(ExecuteCodeRequest executeCodeRequest) {
        // todo 限制权限
//        System.setSecurityManager(new DefaultSecurityManager());


        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String languages = executeCodeRequest.getLanguages();

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
        // 创建容器，上传编译文件
        // 1) 在已有的基础上进行扩充
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        String image = "openjdk:8-alpine"; // docker hub
        if (FIRST_INIT) {
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("下载镜像" + item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd
                        .exec(pullImageResultCallback)
                        .awaitCompletion();
            } catch (InterruptedException e) {
                System.out.println("拉取镜像失败");
                throw new RuntimeException(e);
            }
            System.out.println("下载完成");
        }
        // 然后开始创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(100 * 1000 * 1000L);
        hostConfig.withMemorySwap(0L);
        hostConfig.withCpuCount(1L);
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)
                .withAttachStdin(true)
                .withAttachStderr(true)
                .withReadonlyRootfs(true) // 限制用户不能向更目录进行写文件
                .withAttachStdout(true)
                .withTty(true)
                .exec();
        String containerId = createContainerResponse.getId();

        // 启动容器
        dockerClient.startContainerCmd(containerId).exec();


        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        //docker exec keen_blackwell java -cp /app Main 1 3
        for (String inputArgs : inputList) {
            StopWatch stopWatch = new StopWatch();
            String[] inputArgsArray = inputArgs.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgsArray);
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStdin(true)
                    .withAttachStderr(true)
                    .withAttachStdout(true)
                    .exec();
            System.out.println("创建执行命令" + execCreateCmdResponse);
            ExecuteMessage executeMessage = new ExecuteMessage();
            final String[] message = {null};
            final String[] errorMessage = {null};
            long time = 0;
            final boolean[] timeout = {true};
            String execId = execCreateCmdResponse.getId();
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                @Override
                public void onComplete() {
                    // 如果执行完成，那就表示没有超时
                    timeout[0] = false;
                    super.onComplete();
                }

                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)) {
                        errorMessage[0] = new String(frame.getPayload());
                        System.out.println("输出错误结果" + new String(frame.getPayload()));
                    } else {
                        message[0] = new String(frame.getPayload());
                        System.out.println("输出结果" + new String(frame.getPayload()));
                    }
                    super.onNext(frame);
                }
            };

            final long[] maxMemory = {0L};
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            ResultCallback<Statistics> statisticsResultCallback = new ResultCallback<Statistics>() {
                @Override
                public void onNext(Statistics statistics) {
                    System.out.println("内存占用" + statistics.getMemoryStats().getUsage());
                    maxMemory[0] = Math.max(maxMemory[0], statistics.getMemoryStats().getUsage());
                }

                @Override
                public void onStart(Closeable closeable) {

                }


                @Override
                public void onError(Throwable throwable) {
                }

                @Override
                public void onComplete() {
                }

                @Override
                public void close() throws IOException {
                }
            };
            statsCmd.exec(statisticsResultCallback);
            try {
                stopWatch.start();
                dockerClient.execStartCmd(execId).exec(execStartResultCallback).awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);
                stopWatch.stop();
                time = stopWatch.getLastTaskTimeMillis();
                statsCmd.close();
            } catch (InterruptedException e) {
                System.out.println("程序执行异常");
                throw new RuntimeException(e);
            }
            executeMessage.setMessage(message[0]);
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setTime(time);
            executeMessage.setMemory(maxMemory[0]);
            executeMessageList.add(executeMessage);
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
        JavaDockerCodeSandboxOld javaNativeCodeSandbox = new JavaDockerCodeSandboxOld();
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
