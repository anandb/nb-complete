# Troubleshooting

## Common Problems

| Problem | Solution |
| --- | --- |
| Plugin can't find the harness | Use the harness chooser to pick an installed harness, copy its install command, or set the path manually under `Options > BeanBot`. Binaries found on `PATH` are never auto-selected — you always make the choice. |
| Assistant becomes unresponsive | Click **Restart Harness** in the toolbar. |
| Ctrl + L stops working | Close and reopen the assistant panel from the Window menu. If that doesn't work, restart the IDE. |
| Sidebar doesn't open after install/upgrade | The plugin auto-opens the sidebar on version change. If it doesn't appear, open it from `Window > Assistant`. |
| Image paste doesn't work with Wayland on Linux | Install the `wl-clipboard` package (Wayland) or check your clipboard manager. |
| Image paste broken after OpenCode upgrade | Upgrade to OpenCode >= 1.17.17 to resolve the breakage introduced in v1.17.13. |
| Model not appearing after an OpenCode upgrade | Re-select your model via `/models`. An upgrade that changes the `thought_level` split resets model selection. |
| Session config payloads restructured after upgrade | Upgrade to BeanBot >= 1.21.1 and OpenCode >= 1.17.17. Re-select your model and review any custom preamble or session settings. |
| Messages disappear from view | This is display-only — the session still has all messages. Click **Show All Messages** to keep them visible, and use **Reload** to re-fetch from the server. |
| LLM modified files unexpectedly | Keep your project under version control so you can revert changes you don't want. You can also set OpenCode to 'ask' before editing, and review changes with the **Allow Once/Always Allow/Reject** permission prompts. There is also a setting — **Auto-backup uncommitted changes on turn start (git/hg)** (Options > BeanBot) — that stashes a copy of uncommitted changes before each turn, so the AI's edits can be rolled back safely. |
| Panel goes blank during docking or resizing | Close and reopen the docked panel from `Window > Assistant`. NetBeans may not repaint correctly after a drag-dock or undock operation. |

## Known Issues/Limitations

- The plugin supports only one active session at a time; switching sessions or reloading the conversation while awaiting a response will cancel the current request.
- OpenCode doesn't always relay permission requests from subagents (delegated agents) back to the UI. If a subagent makes a tool call that requires permission, the request may hang and eventually time out because you never receive the Accept/Deny prompt. To mitigate this, instruct the agent to perform file modifications or commands directly rather than asking it to delegate those tasks to a subagent. The mini-assistant also shows permission prompts — check whether that is visible if the main sidebar doesn't show one.
- A few harnesses like Pi write directly to files; the pi-permission extension can gate some of those actions.
- A lot of the functionality relies on the harness; some like Gemini don't support changing the model once the session has begun, others like OpenClaw implement a very barebones subset of the specification.

## Diagnostics

- Set `ACP_WIRE_LOG` to a file path template to capture the JSON-RPC/SSE traffic. Each line is
  `<local time> <json message>`; the file name is suffixed with the date and harness.
- Report an Issue from the help menu opens the project tracker.
