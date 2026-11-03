# AGENTS.md — Imaginary Space Meteorite Excavation

## 1. Project Identity

- Project: Imaginary Space Meteorite Excavation
- Chinese name: 虚数陨星采矿场
- Mod ID: `imaginaryspacemeteoriteexcavation`
- Group ID: `com.kyou.imaginaryspacemeteoriteexcavation`
- Java package: `com.kyou.imaginaryspacemeteoriteexcavation`
- Minecraft: 1.7.10
- Target: GTNH 2.9.0 stable release
- Java compatibility: Java 8

This is an independent GTNH addon. Do not modify GTNH core code.

## 2. Core Development Principles

1. Prefer reusing existing GTNH, GT5U, Blood Magic, AE2 and StructureLib implementations.
2. Investigate the actual target-version source before using an API or implementing a mechanism.
3. Do not assume APIs or behaviors from other GTNH versions.
4. Do not invent classes, methods, registry names or interfaces.
5. Preserve existing project conventions unless a change is necessary and approved.
6. Keep functionality separated into independently testable components.
7. Prioritize correctness, item conservation, resource consistency and server TPS performance.
8. Do not implement features outside the currently authorized development stage.
9. Keep technical findings, implementation decisions, test results and unresolved issues documented throughout development.

## 3. Mixin and Invasive Technology Policy

**Avoid Mixin and invasive modifications wherever possible.**

Preferred implementation order:

1. Public APIs provided by GTNH and its dependencies.
2. Existing GTNH extension points and interfaces.
3. Inheritance and composition.
4. Forge events and callbacks.
5. Existing machine processing and transaction interfaces.
6. Other non-invasive compatibility mechanisms.

Avoid introducing:

- Mixin
- Coremod modifications
- ASM bytecode transformations
- Runtime bytecode patching
- Access Transformers
- Reflection-based access to private internals

If a required feature appears impossible without invasive modification:

1. Investigate and document the limitation.
2. Explain why public APIs and existing extension points are insufficient.
3. Present non-invasive alternatives and their limitations.
4. Identify affected classes, methods and compatibility risks.
5. Submit a technical proposal for approval.

**Do not introduce or implement Mixin, Coremod, ASM, Access Transformers or bytecode modifications without explicit user approval.**

## 4. Dependency and Build Configuration

- Do not modify Gradle versions, JDK configuration or existing build infrastructure without authorization.
- Do not add new Mod dependencies without explaining their necessity and obtaining approval.
- Do not introduce TST as a dependency without explicit approval.
- Do not change GTNH, GT5U, AE2 or Blood Magic versions independently.
- Maintain Java 8 bytecode and API compatibility.
- Preserve existing build conventions.

## 5. Fixed Functional Requirements

### 5.1 Meteorite Simulation Uses Mathematical Expectation

The machine must not reproduce an individual randomized meteorite generation result.

Instead, calculate the theoretical expected quantity of every generated block/item from the actual probability model and generation rules in GTNH 2.9.0.

Requirements:

- Investigate the original generation algorithm and derive its probability model from source code.
- Calculate expected quantities analytically/deterministically.
- Do not run the original random placement process in the machine.
- Do not generate or destroy meteorite world blocks.
- Do not require a matching random seed, world coordinate or random sequence to produce the machine's output.
- Do not replace source-derived probabilities with guessed or approximate probabilities without explicit approval.
- Account for conditional probabilities, mutually exclusive placements, sequential generation, boundaries, empty positions and any other rules that affect expected quantities.
- Include filling blocks and all other generated blocks in the expected-yield model, not only ores.
- Trace the conversion from generated blocks to actual item drops. The machine output must represent the correct item form and quantity, not merely count placed Block instances.

If expected quantities are fractional but machine outputs require integer ItemStack counts, do not choose a rounding policy silently. Analyze the possible policies, their bias and their effect on long-term output, then document the recommendation for user approval unless the source-derived model naturally produces integers.

### 5.2 Machine GUI Slots

The machine has exactly three dedicated GUI item slots:

1. Bound Blood Orb: required; identifies the player Soul Network used for LP checks and deduction.
2. Filtering Book: optional; when absent, no output filtering is applied.
3. AE2 spatial tower blocks: required; total quantity is used for the recipe radius check.

There is **no fourth dedicated GUI slot**. Do not add one or treat its purpose as unresolved.

The spatial tower slot must support quantities greater than one ordinary stack. Investigate the target-version inventory/container implementation and implement a safe quantity limit and transfer behavior.

The GUI must display relevant machine state, including structure status, operating state, LP status, spatial tower count and supported radius, missing requirements, output-space failures, current recipe, progress and output information.

### 5.3 Spatial Tower Radius Check

For each identified meteorite ritual recipe:

- `r` is the recipe's meteorite radius.
- `n` is the total number of AE2 spatial tower blocks in the machine's spatial tower slot.

Execution condition:

`r <= n / 3 - 1`

For non-negative integer `r` and `n`, use the equivalent comparison:

`3L * (r + 1L) <= n`

Perform this check immediately after identifying the recipe and before LP checks or resource commitment.

If the condition fails:

- Do not execute the recipe.
- Do not deduct LP.
- Do not consume drilling fluid.
- Do not consume recipe inputs.
- Display a clear insufficient spatial tower warning.

Do not assume the exact AE2 registry name until confirmed from the target-version source.

### 5.4 Blood Magic and LP

- The machine requires a bound Blood Orb.
- LP is obtained from the Soul Network of the bound player.
- Ammonk's Blood Orb follows the project rule: default LP cost is zero, without LP checking or deduction.
- Verify actual item identification and Soul Network behavior from the installed Blood Magic version.
- Do not infer APIs or special behavior from unrelated Blood Magic versions.

### 5.5 Drilling Fluid

- Base consumption: 1 L per generated/output block, subject to confirmation of how expected block counts are converted to integer output quantities.
- Insufficient drilling fluid must prevent recipe execution.
- The relationship between filtering, expected output quantities, integer conversion and drilling-fluid calculation must be explicitly analyzed and documented before implementation.

### 5.6 Filtering Book

- Optional machine item.
- Absence means no output filtering.
- Each ritual has independent configuration.
- Default mode is whitelist.
- Default book includes all supported rituals and their generated blocks.
- Do not merge configurations between different rituals.
- Use stable ritual identifiers as configuration keys rather than localized display names.
- Configuration copy must preserve the source book and create a correctly copied destination book.
- Configuration clearing must return a default/unconfigured book according to the approved recipe behavior.

### 5.7 Output Safety

- Ordinary output hatches must be checked before recipe commitment.
- ME output must be evaluated against actual AE2 storage interfaces.
- Prevent partial output, item loss and duplication.
- LP, drilling fluid, input consumption and output commitment must have a consistent execution strategy.
- Do not assume output operations are atomic without verifying their implementation.
- If all-or-nothing output cannot be guaranteed with native APIs, document the limitation and alternatives before choosing an invasive solution.

## 6. Unresolved Design Decisions

Do not independently decide the following items without source investigation and user confirmation:

- Whether multiple A energy hatches are supported.
- Exact spatial tower item identity and its AE2 implementation.
- Whether the original expected-yield probability model varies by world, dimension, coordinates or other context.
- How fractional expected quantities are converted into integer output quantities.
- Whether drilling-fluid consumption is based on pre-filter or post-filter output quantities.
- Whether filling blocks are filtered and counted identically to ores.
- Whether custom Long ME output logic is necessary.
- Whether any invasive modification is unavoidable.

The fourth GUI slot is not an unresolved decision; it has been explicitly removed from the design.

Record findings and recommendations in design documents.

## 7. Development Stages

Development must proceed in the following stages. Do not implement the entire Mod in one task.

1. **Source investigation and technical design**
   - Verify exact GTNH 2.9.0 source/dependency versions.
   - Investigate meteorite ritual registration and the mathematical expected-yield model.
   - Investigate Blood Magic LP and orb behavior.
   - Investigate AE2 spatial tower identification and large-count slot behavior.
   - Investigate IV Mining Facility structure, energy, processing, overclocking and batching.
   - Investigate ordinary and ME output APIs and transaction safety.
   - Investigate filtering book, NBT, GUI and StructureLib patterns.
   - Produce an integrated technical design and risk register.
   - No large-scale feature implementation in this stage.

2. **Basic items and multiblock machine**
   - Register the controller and required items.
   - Implement the multiblock based on the verified IV Mining Facility implementation.
   - Implement exactly three dedicated GUI slots.
   - Implement basic GUI status and structure validation.
   - Verify machine formation and inventory behavior.

3. **Meteorite expected-yield calculation engine**
   - Resolve ritual recipes and their parameters.
   - Apply the spatial tower radius check.
   - Implement deterministic mathematical expected-yield calculations.
   - Handle block-to-item conversion.
   - Implement LP, drilling-fluid, processing-time, energy and batch calculations as authorized by the technical design.
   - Test the calculation engine independently of world modification and output commitment.

4. **Filtering Book**
   - Implement ritual pages, whitelist/blacklist modes and independent NBT configuration.
   - Implement default configuration, per-page reset, configuration copy and clearing.
   - Test persistence and isolation between rituals.

5. **Output system**
   - Implement ordinary item/fluid output handling.
   - Implement ME output using verified AE2 interfaces.
   - Implement output-space preflight and safe commitment.
   - Evaluate Long output support only where native APIs are insufficient.

6. **Full machine integration and performance**
   - Integrate the calculation engine, LP, drilling fluid, filtering and output.
   - Implement scheduling, caching and controlled output processing.
   - Verify pause, stop, chunk unload, save/load and recovery behavior.
   - Evaluate multi-machine TPS impact.

7. **In-game acceptance**
   - Test against GTNH 2.9.0 stable.
   - Test multiple ritual radii, tower quantities, LP balances, orb types, drilling-fluid amounts, filtering settings, output-full cases, batching and save/load.
   - Compare expected-yield calculations against mathematically derived reference cases.
   - Record bugs, fixes and final acceptance results.

For each stage, define scope, implementation tasks, acceptance criteria and known risks before coding.

## 8. Development Process Documentation

Maintaining development process documentation is mandatory.

Create and maintain at least:

- `docs/DEVELOPMENT_PLAN.md`: current staged plan, scope, dependencies and acceptance criteria.
- `docs/DEVELOPMENT_LOG.md`: chronological development history.
- `docs/TECHNICAL_DECISIONS.md`: important decisions, rationale, alternatives and approval status.
- Stage-specific investigation, design, implementation and test reports under `docs/`.

Update `docs/DEVELOPMENT_LOG.md` at the end of every meaningful work session and every Codex task. Do not wait until the entire Mod is finished.

Each log entry should include:

- Date and stage.
- Objective and authorized scope.
- Files/classes investigated or modified.
- Key findings and decisions.
- Commands/builds/tests executed and their results.
- Problems, limitations and unresolved questions.
- Next recommended action.

Do not claim tests were run if they were not run. Distinguish completed work from proposed work. Keep reports consistent with the actual repository state.

## 9. Source Investigation and Evidence

For important technical conclusions, provide:

- Actual source file path.
- Class and method names.
- Relevant code and line numbers where available.
- Exact dependency/runtime version.
- Evidence distinguishing confirmed facts from inferences.

If the required source is unavailable, report the missing source instead of guessing.

Do not use another GTNH version as a substitute for the target version without clearly documenting the mismatch and obtaining approval for any resulting design assumption.

## 10. Performance Requirements

TPS optimization is a core requirement.

- Avoid expensive repeated recipe searches.
- Avoid unnecessary per-Tick world or inventory scans.
- Cache deterministic expected-yield results where appropriate.
- Avoid unnecessary object allocation and repeated conversions.
- Process large outputs in controlled batches.
- Ensure correct state handling during pause, stop, chunk unload and world save/load.
- Optimize for multiple machines operating simultaneously, not just one machine.

## 11. User Approval and Scope Control

The user is the final authority on functional requirements and architecture decisions.

Do not silently change requirements, introduce invasive techniques, add dependencies or expand the authorized development scope.

When a decision materially affects compatibility, performance, resource consumption, expected output quantities or implementation architecture, explain the alternatives and wait for approval.

At the end of each stage, stop and provide the relevant reports, build/test results and unresolved issues. Do not automatically proceed to the next stage.
