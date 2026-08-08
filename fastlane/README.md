fastlane documentation
----

# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

# Available Actions

## Android

### android upload_metadata

```sh
[bundle exec] fastlane android upload_metadata
```

Upload store listings (metadata only, no APK/AAB)

### android upload_screenshots

```sh
[bundle exec] fastlane android upload_screenshots
```

Upload store listing screenshots (images only, no metadata/APK/AAB)

### android upload_changelogs

```sh
[bundle exec] fastlane android upload_changelogs
```

Upload release notes for the current version (changelogs only)

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
