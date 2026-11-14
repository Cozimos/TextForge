# StarDict dictionary support

TextForge uses a native Kotlin StarDict reader inspired by SDCV query behavior. It does not bundle or execute the SDCV command-line program.

## Install one dictionary

1. Long-press a word in the reader.
2. Choose **Choose dictionary folder**.
3. Select the individual dictionary folder containing one matching set of `.ifo`, `.idx` (or `.idx.gz`), and `.dict` (or `.dict.dz`) files.
4. TextForge imports that dictionary into app-private storage. Selecting another folder replaces the active dictionary.

For dictionaries in KOReader's `koreader/data/dict/` folder, select the individual dictionary subfolder, not the parent `dict` folder.

## Lookup behavior

Long-press lookup tries:

1. Exact headword match (case-insensitive).
2. `.syn` synonym lookup, when the dictionary includes a `.syn` file.
3. Fuzzy headword suggestions using bounded edit distance when no exact or synonym match exists.

The user can tap a fuzzy suggestion to look it up. The manager also exposes separate APIs for wildcard headword searches (`*` and `?`) and full-text definition searches. Those search APIs are not yet exposed as a standalone search screen.

## Supported files

- `.ifo` metadata (`bookname` is used for the installed dictionary label).
- `.idx` and gzip-compressed `.idx.gz` indexes.
- `.dict` and gzip/dictzip `.dict.dz` data files (decompressed into app-private storage for random access).
- Optional `.syn` synonym index.

## Storage and privacy

The Android Storage Access Framework folder picker is used. TextForge copies the selected dictionary files into its private app storage for reliable random-access lookup. The original KOReader files are not modified. Only one dictionary is active at a time, and no broad shared-storage permission is requested.

## Current limitations

- Morphological stemming (for example, `running` to `run`) is not implemented.
- Rich HTML styling in definitions is not yet rendered; dictionary entry text is shown in the reader dialog.
- Wildcard and full-text APIs exist but do not yet have a dedicated search UI.
- Fuzzy matching is a simple bounded Levenshtein-distance suggestion pass, not a byte-for-byte reproduction of every SDCV option or ranking rule.
