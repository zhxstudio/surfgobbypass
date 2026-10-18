// zhxstudio — surfgobbypass
package com.zhx.studiotool;

public final class ShellService extends IShellService.Stub {
    public ShellService() { ShellRunner.prepare(false); }

    @Override
    public String execute() {
        return ShellRunner.run(false);
    }

    @Override
    public void destroy() {
        ShellRunner.close();
        System.exit(0);
    }
}
