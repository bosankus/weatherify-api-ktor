# Syncling Figma Plugin

Pushes UI copy from Figma into a Syncling project's **Figma inbox**, where a dev approves
it into a GitHub PR against the watch branch. Merging that PR triggers the regular
Syncling translation pipeline — designers write copy once, devs get `strings.xml` keys
and all translations without manual work.

## Flow

1. Designer selects finished frames → **Extract from selection**.
   The plugin collects visible `TEXT` nodes, filters obvious placeholders
   (lorem ipsum, bare numbers, prices, URLs), and exports a scaled-down PNG
   of each frame so reviewers see the strings in context.
2. **Send N strings** → `POST /api/figma/push` with a paid-plan API token (`sli_…`).
   The backend suggests snake_case keys (Gemini), flags exact duplicates and
   *semantic* near-duplicates of existing strings (embeddings), and detects copy
   *updates* for nodes synced before (node↔key bindings). The project owner gets
   an in-app notification that strings are waiting.
3. Dev reviews the inbox at **syncling.space/figma** (frame screenshots, editable
   keys, duplicate hints), then approves → Syncling opens a PR adding the strings
   to the project's source file.

## Local development

Figma → Plugins → Development → *Import plugin from manifest…* → pick `manifest.json`.
No build step — plain JS (`code.js` + `ui.html`).

## Settings

API token and project ID are stored via `figma.clientStorage` after the first push.
Tokens are minted at https://syncling.space/tokens (paid plans).
