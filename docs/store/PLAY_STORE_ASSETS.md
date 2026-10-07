# Google Play Store Asset Plan

Nexora Git store assets must be generated from the **actual release-candidate application**. Mocked or AI-generated UI must not be presented as an application screenshot.

## Required assets

- App icon: 32-bit PNG with alpha, 512 × 512 px, maximum 1024 KB.
- Feature graphic: JPEG or 24-bit PNG without alpha, 1024 × 500 px.
- Phone screenshots: at least two are required by Play. Nexora Git targets at least four portrait screenshots at 1080 × 1920 or higher for stronger discovery eligibility.
- Localized screenshots: maintain separate EN and ES-MX sets when visible text differs.

## Required real-app screenshot set

Capture from a signed beta/RC using a clean production-like account and sanitized repositories:

1. Home dashboard.
2. Repository detail/code browser.
3. Mobile editor.
4. Git workbench/status/conflict flow.
5. Pull Request detail/review.
6. GitHub Actions run detail.
7. Settings showing Light/Dark/AMOLED or a second theme screenshot.

No access tokens, private repository names, email addresses, unpublished source, signing details or personal notifications may appear.

## Versioning

Store assets belong under:

```text
fastlane/metadata/android/<locale>/images/
```

and must be updated whenever a UI change makes an existing screenshot materially inaccurate. The release qualification record must identify the candidate version used for capture.

## Accessibility

Provide meaningful alt text in Play Console for every uploaded image. Keep alt text concise and describe the important UI state rather than saying “image of” or “screenshot of”.

## Release gate

P2 repository work may prepare the metadata and capture process, but the store screenshot gate is not complete until screenshots are captured from the actual signed RC and visually reviewed for accuracy.
