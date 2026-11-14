<div align="center">

# LiquidVolume

**Liquid Glass-inspired volume controls for Samsung One UI.**

Native SystemUI · Real background blur · LSPosed API 102

[Download](https://github.com/Vadinyt/LiquidVolume/releases/latest) · [Installation](#installation) · [Changelog](CHANGELOG.md) · [Report an issue](https://github.com/Vadinyt/LiquidVolume/issues)

</div>

## What it does

LiquidVolume restyles Samsung's compact vertical volume panel with a translucent glass capsule, subtle edge highlights and a speaker icon inspired by iOS. It modifies the native SystemUI panel through LSPosed. Samsung continues to handle audio, physical volume buttons, dragging, mute, expansion and accessibility.

The included **Design Studio** provides an interactive visual preview, dark/coloured backgrounds, compact/enlarged sizes and low-volume presets. Studio controls simulate volume without changing device audio.

## Compatibility

| Component | Tested configuration |
| --- | --- |
| Device | Samsung Galaxy S23+ (SM-S916B/DS) |
| System | One UI 9 / Android 17 |
| Framework | LSPosed 2.2.1, modern API 102 |
| Root used during development | KernelSU |
| Module scope | System UI (`com.android.systemui`) |

**Device-specific project.** Other models and firmware builds are unverified. Android minimum SDK 31 is a build setting, not a promise of One UI compatibility. Samsung's internal classes and resources can change between updates.

## Installation

1. Download the APK from [Releases](https://github.com/Vadinyt/LiquidVolume/releases/latest).
2. Install it and enable **LiquidVolume** in LSPosed.
3. Select **System UI** (`com.android.systemui`) as its scope.
4. Restart SystemUI or reboot, then press a volume button.

For updates, install the new APK over the previous official build. Official builds retain the same signing key. To revert, disable the module in LSPosed and restart SystemUI or reboot. A downgrade to an older APK may require uninstalling the newer version first.

## Current version

**0.29.0** fixes the native fill's stale narrow bounds after widening the glass capsule. Low-level drawing retains Samsung's square internal canvas. Automated native-graphics tests cover the rendering regressions; the maintainer has accepted the project on the tested phone. This is not a broad device certification.

## Development

Kotlin 2.2.20 · Android Gradle Plugin 8.13.2 · Gradle 8.14.4 · Java 17 · compile SDK 36.

```sh
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug --stacktrace
```

The official build expects `liquidvolume-signing.jks`, alias `liquidvolume`, and the `LV_KEYSTORE_PASSWORD` / `LV_KEY_PASSWORD` environment variables. Contributors must use their own local signing configuration. Their APKs cannot update an official installation in place. Never commit a private signing key.

- `main`: published project.
- `design/ios-liquid-glass`: development history.
- `backup/0.16-rollback`: preserved recovery source.
- [`docs/`](docs/): versioned technical notes, including historical approaches superseded by later versions.

## Feedback

Include the module version, device, exact One UI/Android build, reproduction steps and expected behaviour. Remove personal information from screenshots and logs. Do not upload Samsung firmware APKs or private signing material.

## Français

Module LSPosed qui donne un aspect verre au panneau de volume compact Samsung, tout en conservant les commandes natives. Installe l'APK depuis **Releases**, active le module pour **System UI**, puis redémarre SystemUI ou le téléphone. Seule la configuration indiquée ci-dessus a été testée. Le Design Studio est une simulation visuelle.

---

Independent project by [Vadinyt](https://github.com/Vadinyt). Not affiliated with Samsung or Apple. No open-source licence has been selected yet; public visibility alone does not grant a general redistribution licence.
