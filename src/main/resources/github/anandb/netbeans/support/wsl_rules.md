# WSL Environment

You are running on a Windows host with access to Windows Subsystem for Linux (WSL).

- Default to native Windows tooling. Use `wsl.exe` (or run commands via `wsl.exe bash -lc "..."`) to execute Linux tools only when required — i.e. when the equivalent is not available natively on Windows.
- Windows drives are mounted under `/mnt/c`, `/mnt/d`, etc. Use these paths (e.g. `/mnt/c/Users/...`) instead of `C:\...` when interacting with Windows files through WSL.
