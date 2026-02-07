# Changelog

All notable changes to AI Translation Hub will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-01-22

### Added
- SDK 1.0.0-beta2 desteği
- GoogleCloudTranslationEngine manifest'e eklendi
- onPreferenceChange() callback'leri
- onCreated() callback'leri
- MaterialIcons import desteği
- Türkçe lokalizasyon dosyaları (GeminiTranslate-tr.mtl, GoogleTranslate-tr.mtl)

### Changed
- Versiyon v0.2.0 → v1.0.0
- minMTVersion 26012200 olarak güncellendi
- Manifest ve build.gradle senkronize edildi
- versionCode 300 olarak güncellendi
- pluginSdkVersion 3 olarak güncellendi

### Removed
- Deprecated demo dosyaları (example.mtl, example-zh-CN.mtl)
- Editor demo dosyası (editor.mtl)
- Kullanılmayan case dosyaları (case.svg, case.xml)
- Backup dosyaları

### Fixed
- Manifest sürüm uyumsuzluğu düzeltildi
- Asset klasörleri senkronize edildi (app/src/main/assets ↔ temp_mtp/assets)

## [0.2.0] - 2026-01-15

### Added
- OpenAI provider desteği
- Claude provider desteği
- Multi-provider architecture

### Changed
- GeminiTranslationEngine refactored

## [0.1.0] - 2026-01-01

### Added
- Initial release
- Gemini AI translation engine
- Google Cloud Translation engine
- Basic preference UI
