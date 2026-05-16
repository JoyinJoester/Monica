#!/usr/bin/env bash
# Records run_in_terminal and appmod-* tool calls as JSONL for the extension to process.

INPUT=$(cat)

TOOL_NAME="${INPUT#*\"tool_name\":\"}"
TOOL_NAME="${TOOL_NAME%%\"*}"

case "$TOOL_NAME" in
  run_in_terminal|appmod-*) ;;
  *) exit 0 ;;
esac

case "$INPUT" in
  *'"session_id":"'*) ;;
  *) exit 0 ;;
esac

SESSION_ID="${INPUT#*\"session_id\":\"}"
SESSION_ID="${SESSION_ID%%\"*}"
[ -z "$SESSION_ID" ] && exit 0

HOOKS_DIR=".github/java-upgrade/hooks"
mkdir -p "$HOOKS_DIR"

LINE=$(printf '%s' "$INPUT" | tr -d '\r\n')
printf '%s\n' "$LINE" >> "$HOOKS_DIR/${SESSION_ID}.json"
