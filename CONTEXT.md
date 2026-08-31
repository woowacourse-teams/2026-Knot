# Knot

Knot은 외부 콘텐츠를 워크스페이스 단위로 연결하고 보존하는 협업 서비스다. 외부 제품은 도메인이 아니라 콘텐츠 공급자로 다룬다.

## Language

**Workspace**:
구성원이 함께 콘텐츠를 관리하는 Knot의 협업 경계다.
_Avoid_: Team, project

**Content Source**:
Knot Workspace가 콘텐츠를 가져오는 외부 원천이다.
_Avoid_: Notion domain, external app

**Content Source Provider**:
Content Source 연결과 인증을 구현하는 외부 서비스의 식별자다. Notion은 첫 번째 Provider다.
_Avoid_: Domain, plugin

**Content Source Authorization**:
Workspace OWNER가 특정 Provider 연결을 승인하기 위해 시작한 만료 가능한 일회성 절차다.
_Avoid_: Login, session

**Content Source Connection**:
하나의 Knot Workspace와 하나의 Provider 사이에서 현재 유효한 연결 관계다.
_Avoid_: Token, integration domain
