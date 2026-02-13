# Changelog

All notable changes to TranslateKit will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0-alpha] - 2026-02-12

### Added
- BaseBatchTranslationEngine integration for both Gemini and Google Cloud engines
- Gemini batch translation with numbered [N] text format and AI-based batching
- Google Cloud native POST JSON batch translation with retry logic
- Dynamic model catalog with latest AI model support (GPT-4.1, Claude Sonnet 4, Gemini 3.x)
- API key format validation with warnings at engine init time
- Format hints in quick test dialog per provider

### Changed
- Upgraded from BaseTranslationEngine to BaseBatchTranslationEngine (SDK v3 beta3)
- Updated ModelCatalogManager with current OpenAI, Claude, and Gemini model priorities
- Fixed display names in ToolsSubPreference (OpenAI GPT, Claude AI)
- Added .trim() to all API key reads across engines and preference screens

### Fixed
- API keys with leading/trailing whitespace now handled correctly
- Model priority ordering for latest generation models

## [0.1.0] - 2026-02-10

### Added
- Multi-provider translation engine (Gemini, OpenAI, Claude, Google Cloud)
- SDK 3.0.0-beta3 support
- Auto language detection with 38+ target languages
- Context presets and tone controls
- API key validation and retry logic
- Structured debug logging
- Türkçe lokalizasyon desteği
- GitHub Actions CI/CD workflow
