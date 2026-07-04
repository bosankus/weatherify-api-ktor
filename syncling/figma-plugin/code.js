// Syncling Figma plugin — main thread.
// Extracts TEXT nodes from the current selection and hands them to the UI,
// which pushes them to the Syncling API (/api/figma/push).

figma.showUI(__html__, { width: 420, height: 560 });

const MAX_NODES = 200;

// Obvious placeholder patterns that must never become app strings.
const PLACEHOLDER_PATTERNS = [
  /^lorem ipsum/i,
  /^\d+([.,]\d+)?$/,          // bare numbers
  /^[$€£₹]\s?\d/,             // prices
  /^https?:\/\//i,
];

function isPlaceholder(text) {
  return PLACEHOLDER_PATTERNS.some((re) => re.test(text.trim()));
}

const MAX_PREVIEW_FRAMES = 20;
const MAX_PREVIEW_BYTES = 350000; // stay under the server's base64 cap

function nearestFrame(node) {
  let p = node.parent;
  while (p && p.type !== "PAGE") {
    if (p.type === "FRAME" || p.type === "COMPONENT" || p.type === "SECTION") return p;
    p = p.parent;
  }
  return null;
}

function collectTextNodes(selection) {
  const out = [];
  const frames = new Map(); // frameId → frame node, for screenshot export
  const visit = (node) => {
    if (out.length >= MAX_NODES) return;
    if (node.visible === false || node.locked === true) return;
    if (node.type === "TEXT") {
      const text = node.characters.trim();
      if (text.length > 0 && !isPlaceholder(text)) {
        const frame = nearestFrame(node);
        if (frame && !frames.has(frame.id)) frames.set(frame.id, frame);
        out.push({
          nodeId: node.id,
          nodeName: node.name,
          text: text,
          pageName: figma.currentPage.name,
          frameName: frame ? frame.name : null,
          frameId: frame ? frame.id : null,
          // Fixed-width text boxes can overflow when translations run longer —
          // the server returns a heads-up for these per target language.
          fixedWidth: node.textAutoResize !== "WIDTH_AND_HEIGHT",
        });
      }
      return;
    }
    if ("children" in node) node.children.forEach(visit);
  };
  selection.forEach(visit);
  return { nodes: out, frames: frames };
}

// Scaled-down PNG per unique frame — shown next to the strings in the Syncling inbox
// so the reviewing dev sees where each string lives. Failures just mean no thumbnail.
async function exportFramePreviews(frames) {
  const previews = [];
  for (const frame of frames.values()) {
    if (previews.length >= MAX_PREVIEW_FRAMES) break;
    try {
      const bytes = await frame.exportAsync({
        format: "PNG",
        constraint: { type: "WIDTH", value: 480 },
      });
      if (bytes.length > 0 && bytes.length <= MAX_PREVIEW_BYTES) {
        previews.push({ frameId: frame.id, png: figma.base64Encode(bytes) });
      }
    } catch (e) {
      // Frame can't be exported (e.g. empty) — skip its preview.
    }
  }
  return previews;
}

figma.ui.onmessage = async (msg) => {
  if (msg.type === "extract") {
    const selection = figma.currentPage.selection;
    if (selection.length === 0) {
      figma.ui.postMessage({ type: "error", message: "Select one or more frames first." });
      return;
    }
    const collected = collectTextNodes(selection);
    const previews = await exportFramePreviews(collected.frames);
    figma.ui.postMessage({
      type: "extracted",
      fileKey: figma.fileKey || "",
      nodes: collected.nodes,
      previews: previews,
    });
  }

  if (msg.type === "save-settings") {
    await figma.clientStorage.setAsync("syncling-settings", msg.settings);
  }

  if (msg.type === "load-settings") {
    const settings = await figma.clientStorage.getAsync("syncling-settings");
    figma.ui.postMessage({ type: "settings", settings: settings || null });
  }

  if (msg.type === "notify") {
    figma.notify(msg.message);
  }

  if (msg.type === "close") {
    figma.closePlugin();
  }
};
