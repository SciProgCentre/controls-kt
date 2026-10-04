## General guidelines

* Don't edit README.md files, edit README-TEMPLATE.md in the docs directory for each module instead. Readme files are generated from templates.
* Mark all large fragments of generated code with a comment including "LLM generated code" and a short description of the generation task.
* Update unreleased sections in CHANGELOG.md files on all significant changes.

## Specific code guidelines

* When converting string constant to a DataForge Name, use `parseAsName()` if name string has several tokens separated by dots.
* When creating tests, ensure that minimal number of tests is used to cover functionality to avoid increasing code size and build time.