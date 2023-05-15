# Changelog

All notable changes to this project will be documented in this file.

## Unreleased

- Deprecate `bin/run` CLI.
- Add new `clojure` (command)-based CLI.
- Add ability to run as a Clojure tool.
- Use `:base-url` rather than `:base-uri` in configuration map.  Old
  keyword is still supported.

## 0.2.1 - 2022-04-07

- Use tools.build for jarring, releasing.

## 0.2.0 - 2022-04-05

- Use `HTTP_CRON_BASE_URL` env var instead of `HTTP_CRON_BASE_URI`.

## 0.1.1 - 2022-04-05

- Upgrade vulnerable logback-classic dependency.

## 0.1.0 - 2022-04-04

- Initial release.
