package com.yxinmiracle.yxinmiracleojcodesandbox.utils;

/*
 * @author  YxinMiracle
 * @date  2024-07-07 18:20
 * @Gitee: https://gitee.com/yxinmiracle
 */

import cn.hutool.core.util.StrUtil;
import com.yxinmiracle.yxinmiracleojcodesandbox.model.ExecuteMessage;
import org.springframework.util.StopWatch;

import java.io.*;

public class ProcessUtils {

    /**
     * 执行进程并获取信息
     *
     * @param runProcess
     * @param opName
     * @return
     */
    public static ExecuteMessage runProcessAndGetMessage(Process runProcess, String opName) {
        ExecuteMessage executeMessage = new ExecuteMessage();


        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            int exitValue = runProcess.waitFor();
            executeMessage.setExitValue(exitValue);

            if (exitValue == 0) {
                System.out.println(opName + "成功");
                // 正常输入流
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
                String compileOutputLine;
                StringBuilder compileOutputBuilder = new StringBuilder();
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    compileOutputBuilder.append(compileOutputLine).append("\n");
                }
                executeMessage.setMessage(compileOutputBuilder.toString());
            } else {
                System.out.println(opName + "失败，错误码：" + exitValue);
                // 报错流
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(runProcess.getErrorStream()));
                String compileOutputLine;
                StringBuilder errorCompileOutputBuilder = new StringBuilder();
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    errorCompileOutputBuilder.append(compileOutputLine).append("\n");
                }
                executeMessage.setErrorMessage(errorCompileOutputBuilder.toString());
            }
            stopWatch.stop();
            executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return executeMessage;
    }

    /**
     * 执行交互式进程
     *
     * @param runProcess
     * @param opName
     * @return
     */
    public static ExecuteMessage runInterProcessAndGetMessage(Process runProcess, String opName, String args) {
        ExecuteMessage executeMessage = new ExecuteMessage();


        try {

            InputStream inputStream = runProcess.getInputStream();
            OutputStream outputStream = runProcess.getOutputStream();

            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream, "utf-8");
            String[] s = args.split(" ");
            String join = StrUtil.join("\n",s)+"\n";
            outputStreamWriter.write(join);
            outputStreamWriter.flush();

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

            String compileOutputLine;
            StringBuilder compileOutputBuilder = new StringBuilder();
            while ((compileOutputLine = bufferedReader.readLine()) != null) {
                compileOutputBuilder.append(compileOutputLine);
            }
            executeMessage.setMessage(compileOutputBuilder.toString());
            outputStreamWriter.close();
            outputStream.close();
            inputStream.close();
            runProcess.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return executeMessage;
    }

}
