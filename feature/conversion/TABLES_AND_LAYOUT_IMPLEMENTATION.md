# PDF-to-DOCX: Tables and Layout Fidelity — Implementation Guide

**Purpose:** Detailed instructions for implementing table detection and improving output similarity to the source PDF.

**Current state:** Text, images, and basic formatting (bold, italic, underline, font size) are preserved. Tables and advanced layout are not yet implemented.

---

## 1. Overview

PDF has no native table structure. Tables are drawn as:
- Text blocks at specific (x, y) positions
- Optional vector graphics (lines, rectangles) for borders
- Multiple lines/rows with aligned x-coordinates

To produce a similar-looking DOCX, we need:
1. **Layout analysis** — infer structure from bboxes and text flow
2. **Table detection** — find aligned blocks that form rows/columns
3. **Page layout** — margins, columns, spacing
4. **Heading detection** — font size ratios for H1/H2/H3

---

## 2. Data Available from MuPDF

The `StructuredTextWalker` provides:

| Callback | Data | Use |
|----------|------|-----|
| `onChar(c, origin, font, size, quad, argb, flags)` | Character + bbox (Quad), font, size | Text content, formatting, position |
| `onImageBlock(bbox, transform, image)` | Image bbox and pixels | Embed images |
| `beginTextBlock(bbox)` / `endTextBlock()` | Block-level bbox | Group lines into blocks |
| `beginLine(bbox, wmode, dir)` | Line bbox | Horizontal/vertical text |
| `onVector(bbox, info, argb)` | Vector/rect bbox | Border lines, table grid |

**Quad** has `ul`, `ur`, `ll`, `lr` (corners) — derive x0, y0, x1, y1 for each character/line.

---

## 3. Table Detection Algorithm

### 3.1 Concept

Tables appear as:
- Multiple text blocks with **aligned left-edges** across rows
- Similar vertical spacing between rows
- Optionally: horizontal/vertical lines (from `onVector`)

### 3.2 Step-by-Step

1. **Collect all text blocks with bboxes**
   - From the walker: group chars by line, then lines by block
   - Store `TextBlock(blocks: List<Line>)` where `Line` has `text`, `x0`, `y0`, `x1`, `y1`

2. **Cluster by y-position**
   - Sort blocks by `y0` (top-to-bottom)
   - Group blocks into “rows” when `|y0 - prevY0| < threshold` (e.g. 3–5 points)

3. **Find column boundaries**
   - For each row, collect left edges (`x0`) and right edges (`x1`)
   - Cluster x-values across all rows: column boundaries = consistent left/right alignments
   - Use tolerance: `|x1 - x2| < 2` points → same column

4. **Build grid**
   - For each row, split text by column boundaries
   - Assign each text segment to cell `(row, col)`
   - Handle merged cells: if a block spans multiple columns, merge cells in DOCX

5. **Create XWPFTable**
   ```kotlin
   val table = wordDoc.createTable(rows.size, numCols)
   for ((r, rowBlocks) in rows.withIndex()) {
       val tableRow = table.getRow(r)
       for ((c, cellText) in rowBlocks.withIndex()) {
           tableRow.getCell(c).setText(cellText)
       }
   }
   ```

### 3.3 Edge Cases

- **Bordered vs borderless tables:** Use `onVector` to detect horizontal/vertical lines. If lines form a grid, use them to refine cell boundaries.
- **Merged cells:** If a block’s bbox spans multiple column boundaries, merge cells with `cell.getCTTc().addNewTcPr().addNewGridSpan().setVal(BigInteger.valueOf(span))`.
- **Nested tables:** Defer; treat as a single cell with nested content.
- **Empty cells:** Preserve column structure even if a cell has no text.

---

## 4. Layout Fidelity: Making DOCX Look Like the PDF

### 4.1 Page Setup

```kotlin
val sect = wordDoc.createParagraph().createRun().getDocument().body.addNewSectPr()
val pgSz = sect.addNewPgSz()
pgSz.setW(BigInteger.valueOf(595 * 20))  // A4 width in twips (1/20 pt)
pgSz.setH(BigInteger.valueOf(842 * 20))  // A4 height
val pgMar = sect.addNewPgMar()
pgMar.setLeft(BigInteger.valueOf(720))   // 1 inch margin in twips
pgMar.setRight(BigInteger.valueOf(720))
```

- Derive page size from first page’s `page.getBounds()`.
- Use PDF media box if available; fallback to A4.

### 4.2 Margins and Indentation

- **Paragraph spacing:** `paragraph.spacingBefore`, `spacingAfter` — derive from vertical gaps between blocks.
- **Left indent:** `paragraph.indentationLeft` — from block `x0` minus page margin.
- **First-line indent:** Detect when first line of a paragraph is indented more than subsequent lines.

### 4.3 Column Detection (Multi-Column Layout)

1. Collect all block x0, x1 across the page.
2. Cluster x-values to find column bands (e.g. 2-column: 0–250pt, 280–530pt).
3. Sort blocks by reading order: `(columnIndex, y)`.
4. Insert column breaks: `paragraph.pageBreak = false` but add `CTPPr.addNewSectPr().addNewType().setVal(STSectionGo.PG) ` or use `Breaks.COLUMN` where applicable.

### 4.4 Heading Detection

- Compute average body font size from blocks.
- If `block.fontSize > bodyAvg * 1.3` → likely heading.
- Mapping:
  - `*1.8` → `StyleID.HEADING_1`
  - `*1.5` → `StyleID.HEADING_2`
  - `*1.3` → `StyleID.HEADING_3`

```kotlin
paragraph.style = "Heading1"  // or Heading2, Heading3
```

### 4.5 List Detection

- Bullet: line starts with "•", "◦", "-", "–", "—"
- Numbered: line starts with regex `^\d+[\.\)]\s` or `^[a-zA-Z][\.\)]\s`

```kotlin
paragraph.setNumFmt(ListNumFormat.BULLET)
// or
paragraph.setNumFmt(ListNumFormat.DECIMAL)
```

### 4.6 Spacing Between Elements

- Vertical gap between blocks → `paragraph.spacingBefore` / `spacingAfter` (in twips).
- Line spacing: `paragraph.spacingLineRule = LineSpacingRule.AUTO` and set line spacing from average line height.

---

## 5. Implementation Phases

### Phase A: Refactor Walker to Output Analyzed Blocks

1. Change `DocxBuilderWalker` to emit a list of `AnalyzedBlock` instead of writing directly.
2. `AnalyzedBlock` = `TextBlock(text, bbox, fontSize, bold, italic, underline)` or `ImageBlock(bbox, bytes)` or `VectorBlock(bbox, isHorizontal)`.
3. Keep bboxes (`Rect` / `Quad`) for layout analysis.

### Phase B: Layout Analyzer

1. Input: `List<AnalyzedBlock>` per page.
2. Output: `List<LayoutElement>` where `LayoutElement` is one of:
   - `Paragraph(content, style, spacing, indent)`
   - `Table(rows, cols, cells)`
   - `Heading(level, text)`
   - `List(items, style)`
   - `Image(data, width, height)`
3. Implement: column detection, reading-order sort, heading detection, list detection.

### Phase C: Table Detector

1. Input: blocks in reading order.
2. Run table detection heuristics (aligned left-edges, row clustering).
3. Output: `Table` elements with grid and cell content.

### Phase D: DOCX Builder from Layout Elements

1. For each `LayoutElement`, create corresponding POI structure.
2. Apply margins, spacing, styles from the analyzed layout.

---

## 6. File Structure Suggestion

```
feature/conversion/
├── TABLES_AND_LAYOUT_IMPLEMENTATION.md  (this file)
└── ...

engine/mupdf/
├── MuPdfPoiConvertTool.kt
└── conversion/
    ├── DocxBuilderWalker.kt         (emit raw blocks)
    ├── LayoutAnalyzer.kt            (blocks → layout elements)
    ├── TableDetector.kt             (blocks → tables)
    ├── LayoutElements.kt            (data classes)
    └── DocxLayoutWriter.kt          (layout elements → XWPFDocument)
```

---

## 7. Expected Quality by PDF Type

| PDF Type | Table Fidelity | Layout Fidelity |
|----------|----------------|-----------------|
| Simple table, clear borders | 75–90% | 80–90% |
| Borderless table | 60–75% | 70–80% |
| Multi-column article | N/A | 65–80% |
| Complex magazine layout | 40–60% | 40–60% |

---

## 8. References

- `PDFForge_Android_System_Design.md` — Section 5.5 (PDF to DOCX)
- MuPDF StructuredText: `page.toStructuredText("preserve-images")` + `walk(walker)`
- Apache POI XWPF: `XWPFTable`, `XWPFParagraph`, `CTPPr`, `CTSectPr`
- PDF coordinate system: origin bottom-left; convert to top-down for DOCX.
