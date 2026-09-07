export interface MeResponse {
  memberId: number;
  nickname: string;
  profileImageUrl: string;
}

export interface NicknameResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}
