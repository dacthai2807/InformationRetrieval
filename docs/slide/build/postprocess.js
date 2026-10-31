// pptxgenjs emits one <a:pPr> per text run; a paragraph may only have one (first). Keep the first, drop the rest.
const fs = require("fs");
const JSZip = require("jszip");
module.exports = async (file) => {
  const zip = await JSZip.loadAsync(fs.readFileSync(file));
  let fixed = 0;
  for (const name of Object.keys(zip.files)) {
    if (!/^ppt\/(slides\/slide\d+|slideLayouts\/slideLayout\d+|slideMasters\/slideMaster\d+)\.xml$/.test(name)) continue;
    let xml = await zip.file(name).async("string");
    const out = xml.replace(/<a:p>([\s\S]*?)<\/a:p>/g, (m, inner) => {
      let k = 0;
      const re = /<a:pPr\b[^>]*?(?:\/>|>[\s\S]*?<\/a:pPr>)/g;
      const res = inner.replace(re, (pp) => (k++ === 0 ? pp : (fixed++, "")));
      return "<a:p>" + res + "</a:p>";
    });
    zip.file(name, out);
  }
  fs.writeFileSync(file, await zip.generateAsync({ type: "nodebuffer", compression: "DEFLATE" }));
  return fixed;
};
