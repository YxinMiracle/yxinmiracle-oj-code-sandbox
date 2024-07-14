package com.yxinmiracle.yxinmiracleojcodesandbox;

/*
 * @author  YxinMiracle
 * @date  2024-07-07 17:06
 * @Gitee: https://gitee.com/yxinmiracle
 */

import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.yxinmiracle.yxinmiracleojcodesandbox.model.ExecuteMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate {

    private static final long TIME_OUT = 5000L;

    private static Boolean FIRST_INIT = false;

    @Override
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();

        // 创建容器，上传编译文件
        // 1) 在已有的基础上进行扩充
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        String image = "openjdk:8-alpine"; // docker hub
        if (FIRST_INIT) {
            FIRST_INIT = false;
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
        // 拉取镜像然后开始创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(100 * 1000 * 1000L); // 6328320
        hostConfig.withMemorySwap(0L);
        hostConfig.withCpuCount(1L);
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));
        // 创建容器
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)
                .withAttachStdin(true)
                .withAttachStderr(true)
                .withReadonlyRootfs(true) // 限制用户不能向更目录进行写文件
                .withAttachStdout(true)
                .withTty(true)
                .exec();
        // 创建完容器之后获取对应的容器Id
        String containerId = createContainerResponse.getId();

        // 启动容器
        dockerClient.startContainerCmd(containerId).exec();

        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        //docker exec keen_blackwell java -cp /app Main 1 3
        for (String inputArgs : inputList) {
            try {
                Thread.sleep(2, TimeUnit.SECONDS.ordinal());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            StopWatch stopWatch = new StopWatch();
            String[] inputArgsArray = inputArgs.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgsArray);
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStdin(true)
                    .withAttachStderr(true)
                    .withAttachStdout(true)
                    .exec();
            ExecuteMessage executeMessage = new ExecuteMessage();
            final String[] message = {null};
            final String[] errorMessage = {null};
            long time = 0;
            final boolean[] timeout = {true};
            String execId = execCreateCmdResponse.getId();

            // 这里主要是设定为了拿到执行结果和有没有超时的测试用例
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
                        String replace = new String(frame.getPayload()).replace("\n", "");
                        int length = replace.length();
                        if (replace.length()>0){
                            executeMessage.setMessage(replace);
                        }
                        message[0] = new String(frame.getPayload()).replace("\n","");
                        System.out.println("输出结果" + new String(frame.getPayload()));
                    }
                    super.onNext(frame);
                }
            };

            final long[] maxMemory = {0L};

            // 为某个容器的执行设定一个状态
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);

            // 这里主要是设定一个获取内存的操作，设定一个ResultCallback
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
                // 设定超时时间，防止用户写死程序
                dockerClient.execStartCmd(execId).exec(execStartResultCallback).awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);
                stopWatch.stop();
                time = stopWatch.getLastTaskTimeMillis();
                statsCmd.close();
            } catch (InterruptedException e) {
                errorMessage[0] = new String("用户程序执行异常");
                System.out.println("程序执行异常");
                throw new RuntimeException(e);
            }

            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setTime(time);
            executeMessage.setMemory(maxMemory[0]);
            executeMessageList.add(executeMessage);
        }

        // 当全部都执行完之后就可以删除对应的容器
        dockerClient.removeContainerCmd(containerId).withForce(true).exec();

        return executeMessageList;
    }

}
