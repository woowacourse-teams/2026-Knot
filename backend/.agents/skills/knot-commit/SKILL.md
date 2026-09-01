---
name: knot-commit
description: “커밋해줘”, “작업 단위로 커밋해줘”, “커밋 메시지 작성해줘”처럼 Knot 백엔드의 실제 git diff를 원자적 커밋으로 계획·검토·생성할 때 사용한다. 메시지 초안은 commit 권한이 아니며 실제 commit은 명시적 요청에만 수행한다. push, PR 게시, merge에는 사용하지 않는다.
---

# Knot Commit

Use this skill from the repository's `backend` module directory. Do not rely on a user-specific absolute path. If the skill is invoked from another directory, resolve the checkout dynamically:

~~~bash
repo_root="$(git rev-parse --show-toplevel)"
backend_dir="$repo_root/backend"
cd "$backend_dir"
~~~

Base every commit decision on the actual backend diff. Use the linked Issue or the user's task as context for intent, but never describe code that is not present in the diff.

## Core rules

- Put one change intent in one commit whenever practical.
- Keep implementation, tests, and required configuration for the same intent together when they form one reviewable change.
- Split unrelated features, fixes, refactors, formatting-only changes, documentation, dependency changes, and tests into separate commits when practical.
- For feature work, use behavior/method/schema-concern boundaries rather than the parent Issue as the commit boundary. Prefer many small, independently reviewable and reversible commits over a few broad feature commits.
- When the workflow applies, separate the `test` -> `feat` -> `refactor` relay into distinct commits. A test-only contract, behavior implementation, and behavior-preserving cleanup must not be bundled when they can be reviewed independently.
- Do not combine multiple independent public methods or unrelated layers merely because they belong to the same Issue. Each commit must have one review reason and an explicit file/hunk set.
- Do not stage every changed file blindly.
- Treat `커밋해줘` and `작업 단위로 커밋해줘` as explicit commit creation requests. `커밋 메시지 작성해줘` authorizes only a draft. Do not amend, rebase, or reset unless the user explicitly asks for that operation.
- Preserve existing user changes and report pre-existing staged or dirty files before staging.
- Do not claim a test or formatting check passed without running it and reporting the result.

## 1. Inspect the actual backend diff

Run read-only checks from `backend` before proposing a message:

~~~bash
git status --short --branch
git branch --show-current
git diff --stat -- .
git diff --name-status -- .
git diff -- .
git diff --cached --stat -- .
git diff --cached --name-status -- .
git diff --cached -- .
git diff HEAD --stat -- .
git log --oneline --decorate -15
~~~

Interpret the results as follows:

- `git diff -- .`: unstaged backend changes;
- `git diff --cached -- .`: already staged changes that would enter the next commit;
- `git diff HEAD -- .`: all local backend changes relative to `HEAD`;
- `git status --short -- .`: untracked files, which plain `git diff` does not show.

If the user asks for a commit based on the branch as a whole, also resolve the base branch and inspect `git diff <base>...HEAD -- .` and `git log --oneline <base>..HEAD -- .` from the backend module directory. Keep committed branch history separate from uncommitted changes.

Inspect relevant untracked files separately. Do not include them in a commit proposal merely because they exist.

## 2. Determine atomic intent

Group changed files and hunks by the smallest coherent change that a reviewer can understand independently. For each group, record:

~~~text
변경 의도:
포함 파일/헌크:
변경 이유:
검증:
제안 type/scope:
~~~

Use the Issue or task to check whether the group serves the requested goal. Use the diff to decide what actually belongs in the commit.

Use these grouping rules:

- A feature implementation and its direct tests can share one `feat` commit when they are inseparable and reviewable together.
- A separately meaningful test addition may be a `test` commit.
- A behavior-preserving structural change is `refactor`, not `feat` or `fix`.
- Formatting without behavior is `style`; do not mix it into a feature or bug-fix commit unless the repository requires it for the same touched files and the user accepts that scope.
- Dependency or build-file changes are `build` when they change the build system or dependencies; use `chore` for other setup/configuration work.
- Documentation and CI changes use `docs` and `ci` respectively.
- If one file contains multiple intents, stage hunks with `git add -p` or propose a hunk split rather than assigning the whole file to one commit.

If the diff contains one clear intent, propose one commit. If it contains multiple intents, present the split order and message for each commit before staging anything.

## 3. Choose the commit type and optional scope

Use only these types:

| Type | Use for |
| --- | --- |
| `feat` | New functionality |
| `fix` | Bug correction |
| `docs` | Documentation |
| `style` | Behavior-neutral formatting/style |
| `refactor` | Behavior-preserving structural improvement |
| `perf` | Performance improvement |
| `test` | Adding or changing tests |
| `chore` | Other setup or maintenance |
| `build` | Build system or dependency changes |
| `ci` | CI/CD changes |
| `revert` | Reverting an earlier commit |

Use the format:

~~~text
<type>: <subject>
<type>(<scope>): <subject>
~~~

Use a scope only when it makes the target clearer, such as `notion`, `import`, `parser`, or `global`. Do not invent a scope when the change is repository-wide or the scope adds no information.

For a `revert`, identify the reverted commit or change in the subject and do not disguise it as a normal fix.

## 4. Write the subject

Make the subject:

- clear and short;
- specific about the target and purpose;
- free of a final period;
- free of vague words standing alone, such as `수정`, `변경`, `기능 추가`, or `리팩터링`;
- consistent with the selected type and the actual diff.

Good examples:

~~~text
feat: Import Run 생성 기능 추가
fix: 빈 RichText 처리 오류 수정
refactor: Block 저장 책임 분리
test: 페이지 pagination 테스트 추가
docs: GitHub 협업 가이드 추가
build: Testcontainers 의존성 추가
~~~

Bad examples:

~~~text
fix: 수정
feat: 기능 추가
chore: 변경
refactor: 리팩터링
~~~

Korean is the default subject language for this repository unless the user asks for another language or the established project terminology is clearer in English.

## 5. Add a body only when it adds context

Omit the body for a simple, self-explanatory change. Add it when the reviewer needs the reason or background to understand the change.

Use a blank line after the subject. Focus the body on:

- why the change was needed;
- what problem existed before;
- how the change resolves it.

Do not narrate every method or restate the diff line by line. Prefer a concise reason-focused body:

~~~text
fix: 중복 블록 저장 오류 수정

동일한 Notion Block을 다시 Import할 경우
새로운 Block row가 생성되는 문제가 있었다.

Notion Block ID를 기준으로 기존 데이터를 갱신하도록 변경한다.
~~~

## 6. Review the proposed commit

Before staging or committing, check:

- the message type matches the change;
- the optional scope names the affected area accurately;
- the subject describes what changed and why it matters;
- the body is omitted when unnecessary and reason-focused when present;
- the commit contains one intent;
- the staged diff contains no unrelated files or hunks;
- direct tests and relevant configuration are included or intentionally split;
- `git diff --check` reports no whitespace errors;
- relevant tests or checks have a real, reported result.

Return the proposal in this form:

~~~text
커밋 분리: 필요 없음 / 필요

변경 의도:
포함 파일:
제외 파일:
제안 메시지:

검증:
커밋 준비 상태: 준비됨 / 보완 필요
~~~

When splitting is needed, return one block per commit in dependency order. Explain blockers separately from optional polish.

## 7. Stage and commit only on explicit request

When the user explicitly asks to create the commit:

1. Re-read `git status --short` and the relevant diff so newly changed files are not silently included.
2. Stage only the approved files or hunks using explicit paths or `git add -p`.
3. Inspect `git diff --cached --check` and `git diff --cached`.
4. Run the relevant verification requested by the project or user.
5. Create the commit with the approved message, using `git commit -m` for a subject-only message or `git commit -m` plus `-m` for a body.
6. Verify `git show --stat --oneline HEAD` and `git status --short`.

Never use `git add .` or `git commit -a` as a shortcut when the worktree contains unrelated changes. If the staged diff differs from the approved proposal, stop and report the difference.
