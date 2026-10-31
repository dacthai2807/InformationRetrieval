# Build deck HUST_Search.pptx

Sinh `../HUST_Search.pptx` bằng pptxgenjs (nền: ảnh nền của `_template.pptx`; nội dung và ghi chú thuyết trình lấy từ `../slides.md`).

```bash
npm i pptxgenjs jszip            # ngoài repo, rồi:
export NODE_PATH=/đường/dẫn/node_modules
node main.js                      # → ../HUST_Search.pptx
```
- `lib.js` helper (panel, callout, bảng, node, …) · `build.js` slide 1–10 · `build2.js` slide 11–20 + phụ lục · `postprocess.js` gộp `<a:pPr>` trùng (pptxgenjs sinh mỗi run một pPr).
- Ghi chú thuyết trình = PHẦN C trong `../slides.md` (sửa ở đó rồi build lại).
