package com.yxinmiracle.yxinmiracleojcodesandbox.security;

/*
 * @author  YxinMiracle
 * @date  2024-07-07 22:39
 * @Gitee: https://gitee.com/yxinmiracle
 */

import java.security.Permission;

public class DefaultSecurityManager extends SecurityManager {

    @Override
    public void checkExec(String cmd) {
        super.checkExec(cmd);
    }

    @Override
    public void checkRead(String file) {
        super.checkRead(file);
    }

    @Override
    public void checkWrite(String file) {
        super.checkWrite(file);
    }

    @Override
    public void checkDelete(String file) {
        super.checkDelete(file);
    }

    @Override
    public void checkConnect(String host, int port) {
        super.checkConnect(host, port);
    }

    @Override
    public void checkPermission(Permission perm) {
        System.out.println("默认不做任何权限限制");
        super.checkPermission(perm);
    }
}
