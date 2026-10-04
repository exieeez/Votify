---
name: playwright-visual-regression
description: >-
  Runs automated headless browser checks and captures screenshots to visually verify UI layout,
  detect regressions, and fix visual bugs before finishing UI tasks.
  Use when developing web interfaces, checking responsive layouts, or debugging CSS/styling issues.
---

# Playwright Visual Regression & Layout Verifier

This skill enables visual verification of user interfaces using Playwright or Chrome DevTools.

## Workflow

1. **Launch Test Instance**:
   - Start the local dev server (e.g. `npm run dev` or Vite / Next.js server).
   - Ensure the port is reachable (e.g. `http://localhost:3000` or `http://localhost:5173`).

2. **Automated Visual Snapshot**:
   - Run a Playwright screenshot script:
     ```bash
     npx playwright test --update-snapshots
     ```
   - Or capture a direct page screenshot using node script:
     ```javascript
     const { chromium } = require('playwright');
     (async () => {
       const browser = await chromium.launch();
       const page = await browser.newPage({ viewport: { width: 390, height: 844 } }); // Mobile viewport
       await page.goto('http://localhost:3000');
       await page.screenshot({ path: 'screenshot.png', fullPage: true });
       await browser.close();
     })();
     ```

3. **Inspect Screenshot & Diagnose**:
   - Inspect the generated image using `view_file` to review padding, font rendering, text truncation, and alignment.
   - Detect layout shifts, overlapping elements, or overflow cutoffs.

4. **Iterate & Fix**:
   - Adjust styling/markup based on visual feedback until the UI pixel-matches the specification.
