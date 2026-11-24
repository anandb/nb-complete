# Harlowe 3.3.9 (vendored story format)

The user guide is compiled with this copy of the Harlowe story format, not with
the format bundled inside the installed `tweego` release. Pinning the format in
the repository keeps local, CI and release builds on the same engine: tweego
2.1.1 ships Harlowe 3.1.0 (2019), which is what the guide was originally built
with and is too old for the macros the guide may use.

- **Format:** Harlowe 3.3.9 by Leon Arnott
- **Files:** `format.js`, `icon.svg`
- **Licence:** Zlib (declared in the `license` field at the top of `format.js`;
  copyright Leon Arnott and Harlowe contributors)
- **Source:** the Twine 2 desktop application bundle
  (`resources/app.asar` → `electron-build/renderer/story-formats/harlowe-3.3.9`).
  The format is redistributed unmodified.
- **format.js SHA-256:** `803a0d9d4fb8c3c1c99eaf16f37db6feaad72e36fc7e5f6c5a168465419895f3`

`docs/stitch.py` prepends this directory to `TWEEGO_PATH`, so `tweego -f harlowe-3`
resolves the vendored copy while the tweego binary can still find any other
installed formats.

## Updating

1. Replace `format.js` and `icon.svg` with the new build of the same story
   format (copy them out of the Twine desktop app's format directory).
2. Update `FORMAT_VERSION` in `docs/stitch.py` and `format-version` in
   `docs/passages/002_storydata.txt` to the new version. `stitch.py` refuses to
   compile when the two disagree with the vendored `format.js`.
3. Rebuild the guide and smoke-test it in a browser, especially the parts that
   touch Harlowe's DOM: the theme toggle, the sidebar back/forward mapping in
   `docs/passages/057_storyscript.txt`, and the `tw-story` / `tw-passage`
   selectors in `docs/passages/058_storystylesheet.txt`.
