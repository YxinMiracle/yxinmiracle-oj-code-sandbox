package com.yxinmiracle.yxinmiracleojcodesandbox.security;

/*
 * @author  YxinMiracle
 * @date  2024-07-07 22:56
 * @Gitee: https://gitee.com/yxinmiracle
 */

public class MySecurityManager extends SecurityManager{

    @Override
    public void checkExec(String cmd) {
        super.checkExec(cmd);
        throw new SecurityException("checkExec 权限异常"+cmd);

    }

    @Override
    public void checkRead(String file) {
        if (file.contains("hutool")){
            return;
        }
        throw new SecurityException("checkRead 权限异常"+file);
    }

    @Override
    public void checkWrite(String file) {
        throw new SecurityException("checkWrite 权限异常"+file);
    }

    @Override
    public void checkDelete(String file) {
        throw new SecurityException("checkDelete 权限异常"+file);
    }

    @Override
    public void checkConnect(String host, int port) {
        throw new SecurityException("checkConnect 权限异常"+host+":"+port);
    }
}
