# Contracts: Close the findings against the MCP billing server

**Feature**: `010-close-mcp-findings` | **Phase**: 1

This feature declares no new tool and no new method. It changes two things whose shape other people
depend on, and both are committed here before the code:

| File | Contract |
|---|---|
| `empty-and-refused.md` | What an empty result is, what a refusal is, and why a caller must be able to tell them apart |
| `declaration-source.md` | Which side declares a tool, what the other must match, and what proves it |

**The authority for everything else remains feature 007's contracts.** Where this feature corrects one
of them, the correction lands there rather than being restated here — a second copy is how F-001
started.
