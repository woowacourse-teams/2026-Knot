/**
 * 인증·로그인 회원 DTO
 *
 * - GET  /api/v1/auth/me
 * - POST /api/v1/auth/nickname
 */

// GET /api/v1/auth/me

/** 로그인한 회원 정보 조회의 서버 응답 모양 */
export interface GetMeResponseRaw {
  memberId: number;
  nickname: string;
  profileImageUrl: string;
}

/** 로그인한 회원 정보 조회 응답 */
export class GetMeResponseDto {
  /** 로그인한 회원의 ID */
  memberId: number;
  /** 회원 닉네임. 최대 20자 */
  nickname: string;
  /** 프로필 이미지의 절대 URL */
  profileImageUrl: string;

  constructor(raw: GetMeResponseRaw) {
    this.memberId = raw.memberId;
    this.nickname = raw.nickname;
    this.profileImageUrl = raw.profileImageUrl;
  }
}

// POST /api/v1/auth/nickname

/** 닉네임 설정 시 앱이 넘기는 값 */
export interface PostNicknameRequestInput {
  nickname: string;
}

/** 첫 로그인 뒤 닉네임을 정해 가입을 마치는 요청 본문 */
export class PostNicknameRequestDto {
  /** 회원 닉네임. 최대 20자 */
  nickname: string;

  constructor({ nickname }: PostNicknameRequestInput) {
    this.nickname = nickname;
  }
}

/** 닉네임 설정 완료의 서버 응답 모양 */
export interface PostNicknameResponseRaw {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

/** 가입을 마친 회원에게 발급된 액세스 토큰 */
export class PostNicknameResponseDto {
  /** 이후 요청의 `Authorization` 헤더에 넣을 JWT */
  accessToken: string;
  /** 토큰 종류. 항상 "Bearer" */
  tokenType: string;
  /** 토큰이 만료되기까지 남은 초 */
  expiresIn: number;

  constructor(raw: PostNicknameResponseRaw) {
    this.accessToken = raw.accessToken;
    this.tokenType = raw.tokenType;
    this.expiresIn = raw.expiresIn;
  }
}
