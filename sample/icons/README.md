# Sample app icons

Android, iOS, and desktop reuse the library's Material `travel_explore` symbol
(Apache 2.0, Google material-design-icons) from `InspectorIcons.kt`, with the
handoff's amber accent (`#E1AD66`) on raised charcoal (`#232221`).

- Android: density-specific legacy icons, adaptive icons, and Android 13 themed icons.
- iOS: opaque 1024px universal AppIcon, with system-applied corner masking.
- Desktop: window PNG plus PNG, ICO, and ICNS native distribution icons.

`app-icon.svg` is the vector reference. To regenerate all assets from the library
symbol, run `python3 sample/icons/generate-icons.py` from any directory with Pillow
installed and Node.js with `sharp` available (set `NODE_PATH` if needed).
The generator also updates the iOS asset catalog filename.
