# Change Log
All notable changes to this project will be documented in this file.

## [Unreleased]

## [v0.3.3]
### Fixed
* getEntries and getEntriesPredictive works correctly.
  * Past versions returns prefix search result for getEntries and null for getEntriesPredictive

### Changed
* rewrite test with groovy and drop dependency for kotlin library

## [v0.3.2]
### Fixed
* Exception when cache file does not exist

## [v0.3.1]
### Fixed
* Fix refactoring error in AnalyzeBlock class

### Added
* External data test case that run when data exists
* Add badges on README
* Add install instruction on README

### Changed
* Change PdicElement.PdicElementBuilder setter method names
* Test cases for coverage improvements:
  * Attribution field
  * RTL language
  * Cache file creation

## [v0.3.0]
* Change public API
* Update documents
* Change class names and refactoring.

## [v0.2.0]
* Fix public APIs
* Add spec docs
* Publish github pages
* Publish javadoc to github pages
* Publish coveralls coverage
* Add test cases

## v0.1.0
* First internal release

[Unreleased]: https://github.com/eb4j/pdic4j/compare/v0.3.3...HEAD
[v0.3.3]: https://github.com/eb4j/pdic4j/compare/v0.3.2...v0.3.3
[v0.3.2]: https://github.com/eb4j/pdic4j/compare/v0.3.1...v0.3.2
[v0.3.1]: https://github.com/eb4j/pdic4j/compare/v0.3.0...v0.3.1
[v0.3.0]: https://github.com/eb4j/pdic4j/compare/v0.2.0...v0.3.0
[v0.2.0]: https://github.com/eb4j/pdic4j/compare/v0.1.0...v0.2.0
