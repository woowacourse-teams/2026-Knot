# Knot 기여 가이드

이 문서는 Issue와 Pull Request에 적용되는 최소 컨벤션을 설명합니다.

## 제목과 Label

- Issue와 PR 제목은 백엔드 작업이면 `[BE]`, 프론트엔드 작업이면 `[FE]`로 시작합니다.
- 담당 영역 Label은 `BE`, `FE` 중 정확히 하나를 지정합니다.
- 작업 유형 Label은 `Feature`, `BugFix`, `Chore`, `Docs`, `Hotfix`, `Refactor`, `Release` 중 정확히 하나를 지정합니다.
- Issue와 연결된 PR은 같은 담당 영역과 작업 유형을 사용합니다.

## 브랜치

브랜치는 `<area>/<type>/#<issue-number>` 형식을 사용합니다.

```text
be/feature/#42
be/docs/#4
fe/chore/#15
```

- `area`는 `be` 또는 `fe`입니다.
- `type`은 작업 유형 Label을 소문자로 변환한 값입니다.
- 마지막 구간은 관련 Issue 번호입니다.
- 셸에서 `#`이 주석으로 해석되지 않도록 브랜치 이름을 따옴표로 감쌉니다.

```bash
git switch -c 'be/feature/#42'
```

## Pull Request

- `관련 이슈` 섹션에 `#<issue-number>`를 작성합니다.
- 병합 시 Issue를 자동 종료하려면 `Closes #<issue-number>`를 사용합니다.
- `작업 내용` 섹션에 리뷰 가능한 최소 설명을 작성합니다.
- CI와 리뷰가 완료된 뒤 병합합니다.

## 자동 검증

GitHub Actions의 `Governance` 검사는 다음 항목을 자동으로 확인합니다.

- 제목의 `[BE]` 또는 `[FE]` 형식과 담당 영역 Label 일치
- 담당 영역과 작업 유형 Label이 각각 정확히 하나인지
- 브랜치 형식과 Label·관련 Issue 번호 일치
- PR의 `관련 이슈`, `작업 내용` 섹션과 최소 내용

규칙의 단일 설정은 [`.github/knot-conventions.yml`](.github/knot-conventions.yml)입니다.

로컬에서는 다음 명령으로 검증기 테스트와 실제 PR 검사를 실행할 수 있습니다.

```bash
python3 -m unittest discover .github/scripts -p 'test_*.py' -v
python3 .github/scripts/validate_governance.py --repo OWNER/REPO --pr PR_NUMBER
```

설명의 충분성이나 구현 품질은 자동 판정하지 않고 리뷰에서 확인합니다.
