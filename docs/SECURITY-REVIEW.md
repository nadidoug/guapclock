# Security Review

Date: 2026-05-26

Scope: `BoothTimeClock.java` desktop beta code.

## Summary

No network services, database connections, shell execution, credential handling, or remote input paths were found in the Booth Time Clock app.

The app stores local text files and folders selected by the person using the desktop app. The main security risk is local file placement: the app can create session folders and invoice files in the chosen destination.

## Findings

No high-severity security findings were identified.

## Notes

- Artist and producer names are sanitized before being used in file or folder names.
- Invoice files are plain text.
- The app does not upload invoice data.
- The app does not execute invoice contents.
- The app does not open network sockets.

## Residual Risks

- A user can choose a sensitive invoice destination folder. This is expected desktop behavior, but users should choose a normal music/session folder.
- Invoice text may contain names and billing details. Do not include real invoice files in public release packages.

## Release Recommendation

Safe to package as a local desktop beta utility, assuming no real session/invoice records are bundled in the release zip.
