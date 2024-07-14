package com.yxinmiracle.yxinmiracleojcodesandbox;

import com.yxinmiracle.yxinmiracleojcodesandbox.model.ExecuteCodeRequest;
import com.yxinmiracle.yxinmiracleojcodesandbox.model.ExecuteCodeResponses;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class JavaNativeCodeSandbox extends JavaCodeSandboxTemplate {


    @Override
    public ExecuteCodeResponses executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }
}
