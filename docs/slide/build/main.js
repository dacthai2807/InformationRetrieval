const ctx = require("./build.js");
require("./build2.js")(ctx);
const out = process.argv[2] || "../HUST_Search.pptx";
ctx.pres.writeFile({ fileName: out }).then(async (f) => {
  const n = await require("./postprocess.js")(f);
  console.log("wrote", f, "| removed duplicate pPr:", n);
});
