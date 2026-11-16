# Module ${name}

${description}

## Documentation

- [File Device Message Storage](./docs/fileDeviceMessageStorage.md): Local filesystem-based storage for `DeviceMessage` events with JSONL partitioning, automatic rotation, background ZIP/DEFLATE compression, and reactive querying.

<#if features?has_content>
## Features

${features}
</#if>

<#if published>
## Usage

${artifact}
</#if>
