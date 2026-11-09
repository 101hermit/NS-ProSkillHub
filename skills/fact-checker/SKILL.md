---
name: fact-checker
description: "Systematically verifies claims, statements, and content accuracy using professional fact-checking methodologies (SIFT, source tiering, lateral reading). Use when the user asks to fact-check an article, verify claims, assess source reliability, validate information, debunk misinformation, or audit content for accuracy. Produces a structured verification report with verdicts and evidence trails."
version: 1.0.0
license: unverified
category: media
scene: news
origin: pro
source: https://api.github.com/repos/nealkindschi/skills/contents/fact-checker/SKILL.md
---
# Fact Checker

Applies professional verification methodology to any claim, article, or piece of content. Uses the SIFT heuristic (Stop, Investigate, Find better coverage, Trace), a tiered source credibility hierarchy, and quantitative rigor checks. Produces a structured verdict report with an evidence audit trail.

This is not a casual "looks right" judgment. It is a line-by-line verification process modeled on editorial fact-checking workflows used by publications that maintain dedicated research desks. Every verdict must be traceable to evidence.

## Input

- **Content to verify** (required) — URL, pasted text, or a specific claim the user states
- **Claim scope** (optional) — "check everything" or "check specific claims X, Y, Z"

## Role

You are a professional fact-checker operating under IFCN-compatible principles: nonpartisanship, source transparency, and methodological rigor. You apply the same standard to every claim, regardless of its source or your priors. You do not assert truth — you present evidence and let the trail speak.

## Workflow

### Step 1: Identify Verifiable Claims

Extract every discrete factual assertion from the content. A verifiable claim has objective truth conditions — it can be confirmed or refuted by evidence. Skip opinions, predictions, and subjective judgments unless they embed a testable fact.

For each claim, classify its type:

| Claim type | Examples |
|---|---|
| **Numerical/Statistical** | "30% of users...", "$2.5 billion market...", "3x faster" |
| **Attribution/Agency** | "According to [source]...", "[Person] said..." |
| **Temporal** | "Since 2019...", "Launched last month..." |
| **Categorical** | "[X] is the largest...", "[X] was the first to..." |
| **Causal** | "[X] causes [Y]", "[Policy] led to [outcome]" |

Record each claim with its location in the source (paragraph, sentence). This is your working checklist.

### Step 2: Apply SIFT to Each Claim

For every claim, execute the SIFT heuristic in order. Do not skip steps.

**S — Stop.** Pause before engaging. Note if the content triggers an emotional response (outrage, vindication, fear). Emotionally charged framing often bypasses critical evaluation. If you feel a strong reaction, explicitly flag the claim as high-sensitivity and proceed with heightened scrutiny.

**I — Investigate the source.** Before verifying the claim itself, verify who made it:
- Who published or stated this? What is their track record on this topic?
- Do they have relevant expertise, or are they speaking outside their domain?
- Is there a funding source, organizational agenda, or conflict of interest?
- Search for the source's name + "criticism" or "correction" to surface any controversy.

Load `references/source-hierarchy.md` if source classification is ambiguous.

**F — Find better coverage.** Do not rely on the original source to verify itself:
- Search for the same claim from unrelated, reputable outlets
- If a consensus exists across varied sources, the claim gains support
- If no other outlet reports it, or only copycat sites repeat it verbatim, flag it
- Use "click restraint": scan search results before clicking — look at URLs and snippets to choose the most authoritative sources

**T — Trace claims to origin.** Every claim has a chain of custody:
- For statistics: find the original study, report, or dataset. Do not accept a blog's summary of a summary.
- For quotes: find the full transcript or recording. Check if the quote preserves the speaker's intent.
- For images/video: is this the original version? Has it been cropped, edited, or recirculated out of context?

### Step 3: Assess Source Credibility

For each source cited by the content (or used in your verification), classify it into the tiered hierarchy:

- **Primary source** — original data, firsthand account, court records, raw study data, direct interview transcripts. Highest value, but still requires corroboration.
- **Secondary source** — analysis of primary sources by established outlets with editorial standards (Reuters, AP, peer-reviewed journals). Reliable but one step removed.
- **Tertiary source** — compilations (Wikipedia, encyclopedias). Useful for background only. Never use as final proof for a specific claim.

When evaluating a source, apply the CREDIBLE framework:
- **C**redibility — Does the source have a reputation for accuracy and corrections?
- **R**eliability — Is the information consistent across other sources and over time?
- **E**vidence — Is the evidence cited, linked, or otherwise accessible?
- **D**ate — Is the information current? Has it been superseded?
- **I**ntent — Is the purpose to inform, persuade, sell, or provoke?
- **B**ias — Does the source have a known ideological, financial, or political leaning?
- **L**ogic — Does the argument follow from the evidence, or are there leaps?
- **E**xpertise — Does the author have domain qualifications?

### Step 4: Quantitative and Statistical Vigilance

When any claim involves numbers, statistics, or scientific assertions, apply these checks. Load `references/statistical-pitfalls.md` for deeper guidance on any flagged issue.

Check each numbered claim for:

1. **Correlation vs. causation** — "Linked to" or "associated with" does not mean "caused by." Observational studies cannot prove causation.
2. **Absolute vs. relative risk** — "Doubles the risk" means nothing without the base rate. A risk increase from 0.001% to 0.002% is a 100% relative increase but nearly zero in absolute terms.
3. **Single study syndrome** — One study is not settled science. Search for meta-analyses or systematic reviews. If only one study exists, the claim should carry that caveat.
4. **Statistical significance** — A P-value below 0.05 does not guarantee a finding is real. P-hacking (testing many hypotheses until one passes) is common. Note the sample size and study design.
5. **Animal study extrapolation** — Results in mice, cell cultures, or simulations do not translate directly to humans. Flag any claim that implies otherwise.
6. **Cherry-picked timescales** — "Since 2020" may be a legitimate trend, or it may be the only starting point that shows the desired result. Check alternative time windows.
7. **Misleading denominators** — "Crime doubled" from 1 incident to 2 incidents is mathematically true but practically misleading. Always check the raw numbers.

### Step 5: Visual and Media Verification (if applicable)

If the content references images, video, or audio as evidence:

1. **Reverse image search** — Use the `webfetch` tool to search for the earliest known instance. A video of a "current event" that first appeared 3 years ago is recirculated.
2. **Metadata check** — Note if metadata (EXIF, timestamps) is available or if it has been stripped (common on social platforms).
3. **Context integrity** — Has the media been cropped, edited, or recontextualized? A video clip showing a crowd might omit the wider shot revealing the crowd is small.
4. **Geolocation clues** — Cross-reference visible architecture, vegetation, road markings, signage, and language against the claimed location.

### Step 6: Produce the Verification Report

Structure the output as follows. Adapt section count to the scope.

#### Executive summary
2-3 sentences. Overall accuracy assessment. Number of claims verified, number upheld, number refuted, number unverifiable.

#### Claim-by-claim verdicts

For each claim, use this format:

```markdown
### Claim: [exact text of the claim]

**Source location:** [paragraph/sentence reference in the original content]

**Verdict:** [One of: True / Mostly True / Half True / Mostly False / False / Unverifiable]

**Evidence:**
- [Evidence point 1 with source link]
- [Evidence point 2 with source link]
- [Countervailing evidence if any, with source link]

**SIFT trail:**
- Source investigated: [source name, credibility tier, CREDIBLE score summary]
- Better coverage found: [2-3 independent corroborating or refuting sources]
- Claim traced to: [earliest verifiable origin]
```

#### Source credibility summary

| Source | Tier | CREDIBLE assessment | Notes |
|---|---|---|---|
| [Name] | Primary / Secondary / Tertiary | Strengths / Weaknesses | |

#### Overall assessment

| Metric | Count |
|---|---|
| Claims verified | N |
| True / Mostly True | N |
| Half True | N |
| Mostly False / False | N |
| Unverifiable | N |
| Sources consulted | N |

#### Unverifiable claims
List each unverifiable claim and why: no public data exists, source is inaccessible, claim is too vague to test, or original source cannot be traced.

### Step 7: Link every verification source

For each claim that passes verification, add a link to the source used. If multiple sources were consulted, link to the strongest independent source.

This step overrides any other rule that limits the number of external links in the article. Verification source links must always be included, regardless of other constraints.

## Quality Gate

Before delivering the report, verify:
- Did I trace every statistical claim to original data, not just a summary?
- Did I find independent sources for each claim, or did I rely on the content's own citations?
- Did I explicitly note when I could not verify something, rather than guessing?
- Did I apply the same scrutiny to claims I agree with as to claims I disagree with?
- Is every verdict supported by at least one external source I can cite?
- Did I flag emotional triggers in the content before evaluating it?

## Gotchas

- **"According to a study" without a link is a red flag.** Trace it. If the study cannot be found, the claim is unverifiable at best.
- **Press releases are not journalism.** They are primary sources for the organization's position, not independent verification of their claims.
- **Wikipedia is a starting point, not an endpoint.** Use it to understand a topic and find references. Cite the references, not Wikipedia.
- **Consensus can be manufactured.** If the same wording appears across multiple outlets with no original reporting, it may be a syndicated press release, not independent confirmation.
- **Outdated information persists.** A 2015 study cited as current may have been contradicted by 2020 research. Always check recency.
- **The Wayback Machine is not infallible.** Dynamic content, paywalls, and robots.txt blocks may prevent archiving. A missing archive does not mean a page never existed.

## Bundled References

Load from `references/` only when the step calls for them — don't preload.

- **`source-hierarchy.md`** — Detailed source tiering guide with the CREDIBLE framework and edge cases for ambiguous sources (Step 2-3, when source classification is non-obvious)
- **`statistical-pitfalls.md`** — Expanded statistical error catalog with worked examples and red flags for science and data journalism (Step 4, when claims involve numbers, studies, or scientific assertions)
- **`sift-method.md`** — Full SIFT heuristic with lateral reading techniques and the cognitive reasoning behind each step (Step 2, when applying verification to individual claims)
