# CI workflow update — apply manually

GitHub blocks the automation token from writing `.github/workflows/**`
(`refusing to allow a GitHub App to create or update workflow ... without
'workflows' permission'`), so this one change ships as a patch you apply
yourself. Everything else in the PR is already pushed.

## Apply it

Either replay the commit:

```bash
git checkout arena/019fa43e-securepay
git am ci-workflow-update/0001-ci-branch-builds-and-path-filter.patch
git push origin arena/019fa43e-securepay
```

…or just copy the two finished files over:

```bash
cp ci-workflow-update/build-apks.yml        .github/workflows/build-apks.yml
cp ci-workflow-update/build-single-apk.yml  .github/workflows/build-single-apk.yml
git commit -am "CI: branch builds + path filter"
git push origin arena/019fa43e-securepay
```

---

## 1. Build any branch on demand

`workflow_dispatch` now takes two inputs, so **Actions → Build & Sign SecurePay
APKs → Run workflow** lets you pick:

- **Branch** — the standard GitHub branch picker; any branch, not just `main`.
- **Which apps to build** — `both` / `customer-app` / `agent-app`.
- **Publish to R2** — off by default.

`push` also matches `branches: ['**']` now, so any branch that touches app code
gets an automatic signed build.

### Branch builds can't break production

R2 publishing rewrites `latest.json` / `agent-latest.json`, which is what
offers an update to every installed device — you do not want that from a test
branch. The reusable workflow gained a `publish` input (default `false`) and
both R2 steps are gated on it:

| Trigger | Builds | Publishes to R2 |
|---|---|---|
| push to `main` | yes | **yes** |
| push to any other branch | yes | no |
| pull request | yes | no |
| manual run, publish unticked | yes | no |
| manual run, publish ticked | yes | **yes** |

A branch build still produces a fully signed APK + AAB artifact you can
download and sideload. The job summary now says which mode it ran in.

---

## 2. detect-changes no longer burns a runner for nothing

**The problem:** the workflow ran on every push to `main`. Even for a
dashboard-only commit GitHub still had to allocate a runner, boot it, check out
the repo and run `dorny/paths-filter` — ~20 s — only to conclude there was
nothing to build.

**The fix, in two parts:**

**a) A `paths:` filter on the trigger.** GitHub evaluates `paths:` itself,
*before* any runner is allocated:

```yaml
paths:
  - 'agent-app/**'
  - 'customer-app/**'
  - '.github/workflows/build-apks.yml'
  - '.github/workflows/build-single-apk.yml'
```

A dashboard-only commit now creates **no run at all**. Zero seconds, nothing in
the Actions list.

**b) `detect-changes` got much cheaper** for the runs that do start. It no
longer checks out the repository — cloning it purely to diff it was most of the
job's runtime. One `gh api repos/{repo}/compare/{base}...{head}` call returns
the same file list. A manual run skips the API entirely, since you already told
it what to build.

It still needs to distinguish agent-only from customer-only changes, which is
why the job exists at all rather than being folded into the trigger.

### Fail-safe behaviour

The old version could silently skip a build if the filter misfired. Now
anything ambiguous builds **both** apps:

- no base SHA (brand-new branch, or a force push) → build both
- compare API returns nothing → build both
- diff is ≥ 300 files, i.e. truncated by the API → build both
- a change to either workflow file → build both, so pipeline edits are proven
  against both apps

The old separate `workflow` output that OR'd into both job conditions is gone;
a workflow change just sets `customer=true, agent=true` directly, which is
easier to read.

### Verified

The decision script was extracted and replayed locally against stubbed file
lists:

| Scenario | customer | agent | publish |
|---|---|---|---|
| push, dashboard-only change | false | false | true¹ |
| push to `main`, agent change | false | **true** | **true** |
| push to branch, agent change | false | **true** | false |
| push, workflow file change | **true** | **true** | false |
| new branch, no base SHA | **true** | **true** | false |
| dispatch `agent-app`, publish off | false | **true** | false |
| dispatch `both`, publish on | **true** | **true** | **true** |

¹ Both apps are `false`, so no build job runs and the publish flag is never
read. In reality the `paths:` filter means this run is never created.
