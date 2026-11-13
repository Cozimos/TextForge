# TextForge

TextForge is a Markdown novel reader built with Kotlin and Jetpack Compose. It imports novels, indexes chapters by UTF-8 byte offsets, parses Markdown formatting, and stores reading data for restoration.

## Project Structure

```text
TextForge/
├── integrations/
│   └── NovelImport.kt
├── navigation/
├── parser/
│   ├── ChapterIndexer.kt
│   ├── parser.kt
│   └── NovelParser.kt
├── storage/
│   ├── NovelStore.kt
│   └── NovelsModel.kt
├── ui/
│   ├── components/
│   │   ├── HeadingComponent.kt
│   │   ├── HighlightedParagraph.kt
│   │   ├── InlineHighlight.kt
│   │   └── NovelTextRenderer.kt
│   ├── library/
│   │   └── LibraryScreen.kt
│   ├── reader/
│   │   └── ReaderScreen.kt
│   ├── NovelScreens.kt
│   └── NovelColors.kt
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Modules

### integrations
Handles Android document access, file selection, URI permissions, and novel import metadata.

### parser
Reads Markdown content, identifies chapter boundaries, calculates UTF-8 byte offsets, and parses novel formatting into structured data.
                 ### storage
Manages imported novel metadata, chapter indexes, parsed chapter cache, table of contents, and reading progress.

### navigation
Handles navigation between the Library and Reader screens.

### ui
Contains the application interface.

- `library/LibraryScreen.kt` — Displays imported novels.
- `reader/ReaderScreen.kt` — Displays novel content and reading position.
- `components/` — Reusable Markdown rendering components.
- `NovelColors.kt` — Centralized application colors.
- `NovelScreens.kt` — Existing screen definitions; avoid duplicating screens already separated into `library/` and `reader/`.

## Data Flow

```text
Document Picker
      │
      ▼
NovelImport.kt
      │
      ▼
Novel Metadata
      │
      ▼
ChapterIndexer.kt
      │
      ▼
NovelParser.kt
      │
      ▼
NovelStore.kt
      │
      ├── Novel Metadata
      ├── Chapter Byte Index
      ├── Parsed Chapter Cache
      ├── Cached TOC
      └── Reading Progress
             │
       ┌─────┴─────┐
       ▼           ▼
    Library      Reader
READING DIRECTION →

  [ -2 ] [ -1 ] [ CURRENT ] [ +1 ] [ +2 ] [ +3 ] [ +4 ] [ +5 ]
     └──── backward ────┘       └──── forward ─────────────┘

```

## Storage

Persistent application data belongs in private app storage (`filesDir`), not temporary cache storage.

```text
files/
└── novels/
    └── {novelId}/
        ├── source.md
        ├── toc.cache
        └── chapters/
            ├── 000.cache
            ├── 001.cache
            └── ...
```

Reading progress stores the chapter index, block position, and UTF-8 byte offset.

## Reading Restoration

On reopening the application, load the saved novel metadata, chapter cache, TOC, and reading progress. Restore the reader without rebuilding the chapter index or parsing the entire source again.

Save progress when reading position changes and when the application enters the background.
