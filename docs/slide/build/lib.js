// Helper functions for the HUST Search deck (pptxgenjs).
const fs = require("fs");
const path = require("path");
const sizeOf = (p) => {
  // minimal PNG size reader (IHDR)
  const b = fs.readFileSync(p);
  return { w: b.readUInt32BE(16), h: b.readUInt32BE(20) };
};

const COL = {
  navy: "003366", red: "C02034", dark: "1B2A41", grey: "5B6B7F", line: "C9D3E0",
  light: "F2F6FB", pink: "FDECEE", blueL: "E6EEF8", white: "FFFFFF",
  green: "2E7D32", greenL: "E8F5E9", orange: "EF6C00", orangeL: "FFF3E0", code: "0F1B2D",
};
const FONT = "Arial";
const MONO = "Courier New";

// ---- inline markup: **bold**, `code`, __red bold__
function runs(str, base = {}) {
  const out = [];
  const re = /(\*\*[^*]+\*\*|`[^`]+`|__[^_]+__|\*[^*\n]+\*)/g;
  let last = 0, m;
  while ((m = re.exec(str))) {
    if (m.index > last) out.push({ text: str.slice(last, m.index), options: { ...base } });
    const tok = m[0];
    if (tok.startsWith("**")) out.push({ text: tok.slice(2, -2), options: { ...base, bold: true } });
    else if (tok.startsWith("`")) out.push({ text: tok.slice(1, -1), options: { ...base, fontFace: MONO, color: COL.navy, bold: base.bold } });
    else if (tok.startsWith("__")) out.push({ text: tok.slice(2, -2), options: { ...base, bold: true, color: COL.red } });
    else out.push({ text: tok.slice(1, -1), options: { ...base, italic: true } });
    last = m.index + tok.length;
  }
  if (last < str.length) out.push({ text: str.slice(last), options: { ...base } });
  // explicit line breaks -> breakLine on the previous piece (keeps paragraph props clean)
  const res = [];
  out.forEach((r) => {
    const parts = r.text.split("\n");
    parts.forEach((pt, i) => {
      const isLast = i === parts.length - 1;
      if (pt === "" && !isLast && res.length) { res[res.length - 1].options = { ...res[res.length - 1].options, breakLine: true }; return; }
      if (pt === "" && isLast) return;
      res.push({ text: pt, options: { ...r.options, breakLine: isLast ? r.options.breakLine : true } });
    });
  });
  return res;
}

// paragraphs: array of strings or {t, lvl, bullet:false, size}
function paras(items, o = {}) {
  const size = o.size || 12, color = o.color || COL.dark, gap = o.gap === undefined ? 4 : o.gap;
  const out = [];
  items.forEach((it, i) => {
    const item = typeof it === "string" ? { t: it } : it;
    const rs = runs(item.t, { fontSize: item.size || size, color: item.color || color, fontFace: FONT, bold: item.bold });
    const isNum = o.numbered && item.bullet !== false;
    const bullet = item.bullet === false || o.bullet === false ? false
      : isNum ? { type: "number", indent: 16 } : { indent: 12 };
    rs[0].options = { ...rs[0].options, bullet, paraSpaceAfter: gap, indentLevel: item.lvl || 0 };
    if (item.align) rs[0].options.align = item.align;
    if (i < items.length - 1) rs[rs.length - 1].options = { ...rs[rs.length - 1].options, breakLine: true };
    out.push(...rs);
  });
  return out;
}

module.exports = (pres) => {
  const S = pres.ShapeType;
  const api = { COL, FONT, MONO, runs, paras };

  api.text = (slide, str, o) =>
    slide.addText(typeof str === "string" ? runs(str, { fontSize: o.size || 12, color: o.color || COL.dark, fontFace: FONT, bold: o.bold }) : str, {
      isTextBox: true, margin: o.margin === undefined ? 0 : o.margin, valign: o.valign || "top",
      align: o.align || "left", ...o, fontFace: FONT,
    });

  api.list = (slide, items, o) =>
    slide.addText(paras(items, o), { isTextBox: true, margin: 0, valign: o.valign || "top", ...stripO(o) });

  // panel with navy header strip (template style)
  api.panel = (slide, x, y, w, h, title, o = {}) => {
    const hh = o.hh || 0.3;
    slide.addShape(S.rect, { x, y, w, h, fill: { color: o.fill || COL.white }, line: { color: o.border || COL.navy, width: 0.75 }, objectName: "panel:" + title });
    slide.addShape(S.rect, { x, y, w, h: hh, fill: { color: o.head || COL.navy }, line: { color: o.head || COL.navy, width: 0.75 }, objectName: "panel-head:" + title });
    slide.addText(title.replace(/`/g, ""), { x: x + 0.1, y, w: w - 0.2, h: hh, fontFace: FONT, fontSize: o.hsize || 12, bold: true, color: COL.white, margin: 0, valign: "middle", isTextBox: true });
    return { x: x + 0.12, y: y + hh + 0.08, w: w - 0.24, h: h - hh - 0.14 };
  };

  // light/red callout
  api.callout = (slide, x, y, w, h, str, o = {}) => {
    const red = o.kind !== "blue" && o.kind !== "green";
    const fill = o.kind === "blue" ? COL.blueL : o.kind === "green" ? COL.greenL : COL.pink;
    const line = o.kind === "blue" ? COL.navy : o.kind === "green" ? COL.green : COL.red;
    slide.addShape(S.rect, { x, y, w, h, fill: { color: fill }, line: { color: line, width: 0.75 }, objectName: "callout" });
    const content = Array.isArray(str) ? paras(str, { size: o.size || 12, gap: 2, ...o }) :
      runs(str, { fontSize: o.size || 12, color: COL.dark, fontFace: FONT });
    slide.addText(content, { x: x + 0.12, y, w: w - 0.24, h, margin: 0, valign: "middle", isTextBox: true, fontFace: FONT });
  };

  // numbered red circle
  api.badge = (slide, x, y, n, d = 0.3, color = COL.red) => {
    slide.addShape(S.ellipse, { x, y, w: d, h: d, fill: { color }, line: { color, width: 0.5 }, objectName: "badge" + n });
    slide.addText(String(n), { x, y, w: d, h: d, align: "center", valign: "middle", fontFace: FONT, fontSize: Math.round(d * 36), bold: true, color: COL.white, margin: 0, isTextBox: true });
  };

  // big-number stat card
  api.stat = (slide, x, y, w, h, num, label, o = {}) => {
    slide.addShape(S.roundRect, { x, y, w, h, rectRadius: 0.06, fill: { color: o.fill || COL.light }, line: { color: COL.line, width: 0.75 }, objectName: "stat:" + label });
    slide.addText(num, { x: x + 0.05, y: y + 0.04, w: w - 0.1, h: h * 0.55, align: "center", valign: "middle", fontFace: FONT, fontSize: o.nsize || 26, bold: true, color: o.color || COL.red, margin: 0, isTextBox: true });
    slide.addText(runs(label, { fontFace: FONT, fontSize: o.lsize || 10.5, color: COL.dark, align: "center" }), { x: x + 0.08, y: y + h * 0.58, w: w - 0.16, h: h * 0.38, align: "center", valign: "top", margin: 0, isTextBox: true });
  };

  // box with centered text (flow nodes)
  api.node = (slide, x, y, w, h, str, o = {}) => {
    slide.addShape(S.roundRect, { x, y, w, h, rectRadius: 0.06, fill: { color: o.fill || COL.blueL }, line: { color: o.line || COL.navy, width: 1 }, objectName: "node" });
    slide.addText(runs(str, { fontSize: o.size || 11, color: o.color || COL.dark, fontFace: FONT, bold: o.bold, align: o.align || "center" }), { x: x + 0.05, y, w: w - 0.1, h, align: o.align || "center", valign: "middle", margin: 0, isTextBox: true, fontFace: FONT });
  };

  api.arrow = (slide, x1, y1, x2, y2, o = {}) => {
    const x = Math.min(x1, x2), y = Math.min(y1, y2);
    slide.addShape(S.line, {
      x, y, w: Math.abs(x2 - x1), h: Math.abs(y2 - y1), flipH: x2 < x1, flipV: y2 < y1,
      line: { color: o.color || COL.navy, width: o.width || 1.5, endArrowType: o.noHead ? undefined : "triangle", dashType: o.dash }, objectName: "arrow",
    });
  };

  // picture fitted (contain) in a box
  api.img = (slide, p, x, y, w, h, o = {}) => {
    const { w: iw, h: ih } = sizeOf(p);
    const s = Math.min(w / iw, h / ih);
    const dw = iw * s, dh = ih * s;
    const ox = o.align === "left" ? 0 : (w - dw) / 2, oy = o.valign === "top" ? 0 : (h - dh) / 2;
    slide.addImage({ path: p, x: x + ox, y: y + oy, w: dw, h: dh, altText: o.alt || path.basename(p), objectName: o.alt || path.basename(p) });
    if (o.border) slide.addShape(S.rect, { x: x + ox, y: y + oy, w: dw, h: dh, fill: { type: "none" }, line: { color: COL.line, width: 0.75 } });
    return { x: x + ox, y: y + oy, w: dw, h: dh };
  };

  api.caption = (slide, str, x, y, w) =>
    slide.addText(str, { x, y, w, h: 0.22, fontFace: FONT, fontSize: 10, italic: true, color: COL.grey, margin: 0, isTextBox: true });

  // table with navy header
  api.table = (slide, head, rows, x, y, w, colW, o = {}) => {
    const size = o.size || 11;
    const hrow = head.map((t) => ({ text: t, options: { bold: true, color: COL.white, fill: { color: COL.navy }, fontFace: FONT, fontSize: size, valign: "middle", align: o.alignHead || "left" } }));
    const body = rows.map((r, ri) => r.map((c, ci) => {
      const cell = typeof c === "object" && !Array.isArray(c) && c.text !== undefined ? c : { text: c };
      const rs = Array.isArray(cell.text) ? cell.text : runs(String(cell.text), { fontSize: size, color: COL.dark, fontFace: FONT, bold: ci === 0 && o.boldFirst });
      return { text: rs, options: { fill: { color: cell.fill || (ri % 2 ? COL.light : COL.white) }, valign: "middle", fontFace: FONT, fontSize: size, align: cell.align || (o.align && o.align[ci]) || "left" } };
    }));
    slide.addTable([hrow, ...body], { x, y, w, colW, border: { type: "solid", pt: 0.5, color: COL.line }, margin: [0.03, 0.06, 0.03, 0.06], rowH: o.rowH, autoPage: false, fontFace: FONT });
  };

  return api;
};

function stripO(o) {
  const { size, color, gap, numbered, bullet, ...rest } = o;
  return rest;
}
