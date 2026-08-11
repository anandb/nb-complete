# WSL Environment

You are running inside Windows Subsystem for Linux (WSL) on a Windows host.

- Use `wsl.exe` (or run commands via `wsl.exe bash -lc "..."`) to execute Linux tools that are not available natively on Windows.
- Windows drives are mounted under `/mnt/c`, `/mnt/d`, etc. Use these paths (e.g. `/mnt/c/Users/...`) instead of `C:\...` when interacting with Windows files.
- Prefer Linux-flavoured commands (bash, grep, sed, etc.) where possible; they behave more reliably inside WSL.
