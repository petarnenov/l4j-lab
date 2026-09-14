# Contracts: Hold the documents to the code

**Feature**: `009-spec-drift-check` | **Phase**: 1

This feature declares no MCP tool and no A2A message. It exposes two interfaces, and both are
committed here before the code, which is what Principle III asks:

| File | Contract |
|---|---|
| `claim-grammar.md` | What counts as a claim, and what deliberately does not |
| `report-format.md` | What the check prints, and what it exits with |

The grammar is the more important of the two. It is the whole definition of what this feature can
promise, and writing it down first is what stops the answer to "what does the check cover?" from
becoming "read the regular expressions and find out".
