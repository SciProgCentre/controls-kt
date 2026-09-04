# Module ${name}

${description}

<#if features?has_content>
## Features

${features}

</#if>
- [Alarm Device](./docs/alarm.md): Multi-stage virtual alarm device with dynamic threshold settings and reactive status evaluation.
- [Accumulator Device](./docs/accumulator.md): Virtual device that sums numeric samples over a sliding time window.

Create utility components without a source, then connect their default or `value` input with
`bind(ValueState<Meta>)` or a `ConstructorBinding`.

<#if published>
## Usage

${artifact}
</#if>
