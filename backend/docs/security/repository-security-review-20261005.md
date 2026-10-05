# 저장소 보안 점검 — 2026-10-05

대상: `be/feature/#430`의 추적 파일과 커밋 예정 파일. 현재 텍스트 파일 1,009개에서
키·토큰·공인 IP 패턴을 검색했고 인증, 관측성, 프론트 의존성, GitHub Actions와
Discord 도구의 관련 코드를 읽었다. 배포 서버에 접속하거나 침투 테스트를 수행하지 않았다.
전체 Git 이력 비밀값 검사나 모든 의존성의 SCA 검사를 완료했다는 의미는 아니다.

## 미해결 위험

### 1. 높은 우선순위: 프론트 잠금 파일에 취약 버전이 남아 있다

GitHub Dependabot의 Open 알림은 23개(High 14, Medium 6, Low 3)다.
알림은 기본 브랜치 기준이므로 현재 브랜치의 `frontend/pnpm-lock.yaml` 버전도 대조했다.

| 패키지 | 현재 잠금 버전 | 확인된 수정 버전 | 주요 영향·고려 사항 |
| --- | --- | --- | --- |
| axios | 1.19.0 | 1.20.0 | 프록시 우회·SSRF·프로토타입 오염·DoS 등. 상당수는 Node 어댑터 경로이며 브라우저 앱에 동일하게 적용된다고 단정할 수 없다. |
| undici | 7.29.0 | 7.29.1 | TLS 검증 우회·캐시 및 응답 처리. 8.10.0도 존재하므로 취약한 7.x의 의존 경로를 따로 확인해야 한다. |
| webpack-dev-middleware | 8.1.1 | 8.3.0 | 개발 서버 경로 순회. 개발·Storybook 서버 노출 범위 확인이 필요하다. 6.1.3도 별도 존재한다. |
| sharp | 0.35.2 | 0.35.4 | libheif 처리 취약점. 이미지 처리 경로와 입력 신뢰 경계 확인이 필요하다. |
| qs | 6.15.3 | 6.16.0 | 파싱 DoS |
| fast-uri | 3.1.5 | 3.1.6 | URI 파싱·호스트 혼동·SSRF 관련 |

근거: [Dependabot 알림](https://github.com/woowacourse-teams/2026-Knot/security/dependabot),
`frontend/pnpm-lock.yaml:3011,3588,4486,4686,4986,5185`.
이번 변경에서는 의존성을 업그레이드하지 않았다. 실제 사용 경로를 확인하고 프론트 담당자가
잠금 파일 갱신·회귀 테스트를 수행해야 한다. Open 알림 개수를 실제 서비스 공격 가능 개수로
해석하면 안 된다.

### 2. 높은 영향 범위: Alloy 컨테이너가 호스트 Docker socket에 접근한다

`ops/observability/agent/compose.yml:7,22,27`에서 root 사용자, 호스트 루트 읽기 마운트,
Docker socket 마운트를 사용한다. socket의 `:ro`는 Docker API 요청을 읽기 전용으로
제한하지 않는다. 수집기 침해 시 호스트 제어로 확장될 수 있는 권한 경계다.

수집 목적상 필요한 현재 구성을 임의로 제거하지 않았다. 읽기 API만 허용하는 socket proxy,
허용한 로그 소스만 마운트하는 수집 구성, 최소 권한 실행을 비교해야 한다.
프록시 도입 없이 `:ro`만으로 위험이 해소되었다고 판단하면 안 된다.

### 3. 배포 경계 확인 필요: 지표 인가에 전달 헤더가 영향을 줄 수 있다

`SecurityConfig.java:100–118`은 `/actuator/prometheus`를 `getRemoteAddr()`의 loopback 여부로
인가한다. 운영에서 `SERVER_FORWARD_HEADERS_STRATEGY=framework`를 쓰면 전달 헤더가
애플리케이션의 원격 주소에 영향을 줄 수 있다. Nginx의 공개 metrics 차단, 전달 헤더의
재작성·제거, EC2 8080 직접 접근 차단이 모두 유지되는지 배포 호스트에서 확인해야 한다.

이번 검토는 정적 경계 확인이며 실제 외부 우회 성공을 확인한 것은 아니다.
현재 공개 프로메테우스 차단과 실제 peer IP 보호가 어떻게 연결되는지 후속 검증이 필요하다.

### 4. 운영 후속: 과거 공개된 원 서버 주소는 Git 이력에 남는다

문서 및 관측성 설정의 공인 IP·인스턴스 ID·AMI ID를 현재 파일에서 제거했다.
Git 이력은 재작성하지 않았고 IP를 재할당하지 않았다. 주소 비공개화는 보안 그룹과
프록시 접근 제한을 대신하지 않는다. 이전 노출 주소의 재할당 여부는 운영자가 검토해야 한다.

## 이번에 반영한 보호 조치

- 수집 주소를 필수 `KNOT_INGEST_BASE_URL`로 분리하고 mTLS 검증을 유지했다.
- Nginx 원 서버 허용 목록은 비공개 include 파일로 옮겼으며 `deny all`·client CN·POST 제한을 유지했다.
- TLS SAN과 직접 원 서버 점검 주소를 환경변수로 분리했다.
- 로컬 하네스 tarball과 디버그 일지는 Git에서 제외하고 파일 자체는 보존했다.
- 외부 카탈로그 JSON은 명시적인 입력 경로와 고정 SHA-256으로 검증한다.

비공개 설정 준비 방법은 [관측성 설정 문서](../../ops/observability/private-configuration.md)에 있다.
실제 서버의 파일·환경변수를 변경하거나 해당 스크립트를 실행하지 않았다.

## 확인된 범위와 한계

- 검색한 패턴에서 실제 개인키·GitHub 토큰·AWS access key·Discord webhook·JWT 원문을 찾지 못했다.
  개인키 표식 한 곳은 `tools/llm-benchmark/test_benchmark_core.py:28`의 테스트 fixture였다.
  패턴 검색은 비밀값 부재를 보증하지 않는다. ignored 로컬 비밀값을 보고서에 복사하지 않았다.
- JWT 종류·issuer·audience·만료 검증, refresh 원문 대신 해시 저장, CSRF 적용과 쿠키
  HttpOnly·Secure·Path 설정이 코드에 있다. 세션 회전·로그아웃은 로컬 테스트 근거이며
  실제 배포 브라우저 흐름까지 확인한 결과는 아니다.
- GitHub Actions에 `pull_request_target` 사용은 없고 배포 SSH는 host key 검증을 요구한다.
  여러 Action은 SHA 대신 버전 태그로 고정되어 있어 공급망 강화를 위한 후속 검토 여지가 있다.
- Discord Codex 실행기는 read-only sandbox 및 셸·앱·플러그인 도구 비활성화를 사용한다.
  이는 코드상 설정 확인이며 실제 호스트의 설치 버전 동작을 보증하지 않는다.
- 백엔드 `./gradlew check`: 684개 테스트, 실패·오류·skip 없음. 새 인증 문서 테스트 5개 포함.
- 관측성 Node 계약 24개와 배포 모의 4개 통과, 셸·Python·JSON 구문 검사를 실행했다.
  pinned Alloy 이미지의 네트워크 차단 config validate, agent Compose config와 공용
  Compose의 `--no-env-resolution` 검증이 통과했다. 실제 비밀 환경파일을 읽는 공용
  Compose 검증은 로컬에 파일이 없어 수행하지 못했다.
  외부 서버의 Nginx reload, 네트워크 경계나 알림 발송은 수행하지 않았다.
