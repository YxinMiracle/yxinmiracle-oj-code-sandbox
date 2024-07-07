package com.yxinmiracle.yxinmiracleojcodesandbox;

/*
 * @author  YxinMiracle
 * @date  2024-07-04 13:34
 * @Gitee: https://gitee.com/yxinmiracle
 */

import com.yxinmiracle.yxinmiracleojcodesandbox.model.ExecuteCodeRequest;
import com.yxinmiracle.yxinmiracleojcodesandbox.model.ExecuteCodeResponses;

public interface CodeSandbox {

    ExecuteCodeResponses executeCode(ExecuteCodeRequest executeCodeRequest);
}
