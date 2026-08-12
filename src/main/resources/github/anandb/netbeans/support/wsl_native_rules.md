# WSL Environment

You are running natively inside Windows Subsystem for Linux (WSL) on a Windows host.

- You are a Linux binary, so Linux is your native environment. Always prefer Linux-flavoured commands (bash, grep, sed, git, etc.); they behave reliably here.
- Run commands directly in the WSL environment — no need to wrap them with `wsl.exe`.
- Windows drives are mounted under `/mnt/c`, `/mnt/d`, etc. Use these paths (e.g. `/mnt/c/Users/...`) instead of `C:\...` when interacting with Windows files.
