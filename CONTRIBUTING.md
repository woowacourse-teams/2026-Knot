# Knot 기여 가이드

이 문서는 Issue, Pull Request와 Java 코드에 적용되는 최소 컨벤션을 설명합니다.

## 제목과 Label

- Issue와 PR 제목은 백엔드 작업이면 `[BE]`, 프론트엔드 작업이면 `[FE]`로 시작합니다.
- Label은 PR 생성 후 지정할 수 있으며 Governance 자동 검증 대상이 아닙니다.

## 브랜치

브랜치는 `<area>/<type>/#<issue-number>` 형식을 사용합니다.

```text
be/feature/#42
be/docs/#4
fe/chore/#15
```

- `area`는 `be` 또는 `fe`입니다.
- `type`은 `feature`, `bugfix`, `chore`, `docs`, `hotfix`, `refactor`, `release` 중 하나입니다.
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

- 제목의 `[BE]` 또는 `[FE]` 형식
- 브랜치 형식과 제목의 담당 영역·관련 Issue 번호 일치
- PR의 `관련 이슈`, `작업 내용` 섹션과 최소 내용

규칙의 단일 설정은 [`.github/knot-conventions.yml`](.github/knot-conventions.yml)입니다.

로컬에서는 다음 명령으로 검증기 테스트와 실제 PR 검사를 실행할 수 있습니다.

```bash
python3 -m unittest discover .github/scripts -p 'test_*.py' -v
python3 .github/scripts/validate_governance.py --repo OWNER/REPO --pr PR_NUMBER
```

설명의 충분성이나 구현 품질은 자동 판정하지 않고 리뷰에서 확인합니다.

## Java 코드 가독성

- 클래스·인터페이스의 첫 선언이 상수이면 여는 중괄호 바로 다음 줄에 둡니다. 일반 필드나 메서드로 시작하면 빈 줄을 하나 둡니다.
- enum의 첫 값은 여는 중괄호 바로 다음 줄에 둡니다.
- 연속된 일반 필드는 붙여 쓰고, 상수와 일반 필드 사이·필드와 메서드 사이·메서드 사이에는 빈 줄을 하나 둡니다. JPA Entity의 애노테이션과 필드는 한 블록으로 묶고 필드 블록 사이에 빈 줄을 둡니다.
- `else`와 삼항 연산자는 사용하지 않습니다. 조기 반환과 규칙별 메서드로 분기합니다.
- 조건문 안에 조건문을 중첩하지 않습니다. 검증, 상태 판정과 결과 변환의 의도를 메서드 이름으로 드러내고, 책임이 커지면 클래스를 분리합니다.
- 메서드 체인은 줄마다 호출 하나씩 작성합니다. 다른 객체의 내부 객체를 연달아 탐색하면 디미터 법칙에 따라 해당 책임을 가진 객체의 메서드나 별도 변환 메서드로 분리합니다.
- production과 test 코드에 같은 규칙을 적용하고, 백엔드의 Spotless 설정으로 포맷을 확인합니다.

```java
public class DocumentGenerationService {
    private static final Pattern BLANK = Pattern.compile("[\\p{javaWhitespace}\\p{Z}]*");

    private final DocumentGenerator generator;
}

public enum DocumentGenerationProcessingStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    NO_CONTENT
}
```
