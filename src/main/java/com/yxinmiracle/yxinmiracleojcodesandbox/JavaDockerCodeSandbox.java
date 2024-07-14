//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.yxinmiracle.yxinmiracleojcodesandbox;

import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.yxinmiracle.yxinmiracleojcodesandbox.model.ExecuteMessage;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@Component
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate {
    private static final Logger log = LoggerFactory.getLogger(JavaDockerCodeSandbox.class);
    private static final long TIME_OUT = 5000L;
    private static Boolean FIRST_INIT = false;

    public JavaDockerCodeSandbox() {
    }

    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        String image = "openjdk:8-alpine";
        if (FIRST_INIT) {
            FIRST_INIT = false;
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                public void onNext(PullResponseItem item) {
                    System.out.println("下载镜像" + item.getStatus());
                    super.onNext(item);
                }
            };

            try {
                ((PullImageResultCallback) pullImageCmd.exec(pullImageResultCallback)).awaitCompletion();
            } catch (InterruptedException var31) {
                InterruptedException e = var31;
                System.out.println("拉取镜像失败");
                throw new RuntimeException(e);
            }

            System.out.println("下载完成");
        }

        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(100000000L);
        hostConfig.withMemorySwap(0L);
        hostConfig.withCpuCount(1L);
        hostConfig.setBinds(new Bind[]{new Bind(userCodeParentPath, new Volume("/app"))});
        CreateContainerResponse createContainerResponse = containerCmd.withHostConfig(hostConfig).withNetworkDisabled(true).withAttachStdin(true).withAttachStderr(true).withReadonlyRootfs(true).withAttachStdout(true).withTty(true).exec();
        String containerId = createContainerResponse.getId();
        dockerClient.startContainerCmd(containerId).exec();
        List<ExecuteMessage> executeMessageList = new ArrayList();
        Iterator var11 = inputList.iterator();

        while (var11.hasNext()) {
            String inputArgs = (String) var11.next();

            try {
                Thread.sleep(2L, TimeUnit.SECONDS.ordinal());
            } catch (InterruptedException var30) {
                InterruptedException e = var30;
                throw new RuntimeException(e);
            }

            StopWatch stopWatch = new StopWatch();
            String[] inputArgsArray = inputArgs.split(" ");
            String[] cmdArray = (String[]) ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgsArray);
            ExecCreateCmdResponse execCreateCmdResponse = (ExecCreateCmdResponse) dockerClient.execCreateCmd(containerId).withCmd(cmdArray).withAttachStdin(true).withAttachStderr(true).withAttachStdout(true).exec();
            final ExecuteMessage executeMessage = new ExecuteMessage();
            final String[] message = new String[]{null};
            final String[] errorMessage = new String[]{null};
            long time = 0L;
            final boolean[] timeout = new boolean[]{true};
            String execId = execCreateCmdResponse.getId();
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                public void onComplete() {
                    timeout[0] = false;
                    super.onComplete();
                }

                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)) {
                        errorMessage[0] = new String(frame.getPayload());
                        System.out.println("输出错误结果" + new String(frame.getPayload()));
                    } else {
                        String replace = (new String(frame.getPayload())).replace("\n", "");
                        int length = replace.length();
                        if (replace.length() > 0) {
                            executeMessage.setMessage(replace);
                        }

                        message[0] = (new String(frame.getPayload())).replace("\n", "");
                        System.out.println("输出结果" + new String(frame.getPayload()));
                    }

                    super.onNext(frame);
                }
            };
            final long[] maxMemory = new long[]{0L};
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            ResultCallback<Statistics> statisticsResultCallback = new ResultCallback<Statistics>() {
                public void onNext(Statistics statistics) {
                    System.out.println("内存占用" + statistics.getMemoryStats().getUsage());
                    maxMemory[0] = Math.max(maxMemory[0], statistics.getMemoryStats().getUsage());
                }

                public void onStart(Closeable closeable) {
                }

                public void onError(Throwable throwable) {
                }

                public void onComplete() {
                }

                public void close() throws IOException {
                }
            };
            statsCmd.exec(statisticsResultCallback);

            try {
                stopWatch.start();
                ((ExecStartResultCallback) dockerClient.execStartCmd(execId).exec(execStartResultCallback)).awaitCompletion(5000L, TimeUnit.MILLISECONDS);
                stopWatch.stop();
                time = stopWatch.getLastTaskTimeMillis();
                statsCmd.close();
            } catch (InterruptedException var29) {
                InterruptedException e = var29;
                errorMessage[0] = new String("用户程序执行异常");
                System.out.println("程序执行异常");
                throw new RuntimeException(e);
            }

            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setTime(time);
            executeMessage.setMemory(maxMemory[0]);
            executeMessageList.add(executeMessage);
        }

        dockerClient.removeContainerCmd(containerId).withForce(true).exec();
        return executeMessageList;
    }
}
