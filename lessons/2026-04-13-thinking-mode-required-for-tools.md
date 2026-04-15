# Thinking Mode Required for Reliable Tool Calling

## What went wrong
Gemma 4 E2B generated malformed tool call syntax when Thinking Mode was OFF.
The model produced doubled tokens like `"fact"fact"` in the JSON arguments,
causing LiteRT-LM's constrained decoding parser to throw `INVALID_ARGUMENT`
errors and abort the response.

## Why
Without thinking mode, the model doesn't have a reasoning step before emitting
tool calls. This leads to token-level stuttering in structured output, especially
with complex tool schemas (multiple required string parameters). Constrained
decoding (`enableConversationConstrainedDecoding = true`) helps but isn't
sufficient on its own — thinking mode gives the model space to plan the tool
call before committing tokens.

## How it was fixed
1. Enabled `enableConversationConstrainedDecoding = true` in LlmChatTask
   (was already enabled in AgentChatTask but missing from LlmChatTask)
2. Documented that Thinking Mode must be ON for memory tools
3. Both init and reset paths now set constrained decoding

## How to prevent
- Any task that registers tools via `ToolSet` MUST set
  `enableConversationConstrainedDecoding = true`
- User-facing documentation should note that Thinking Mode improves
  tool calling reliability
- Test tool-heavy features with thinking mode both ON and OFF
