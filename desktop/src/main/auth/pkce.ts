/**
 * PKCE(RFC 7636)·state 생성 (기획서 5.2 시퀀스).
 *
 * verifier는 256-bit 난수의 base64url(43자)로 RFC 7636 §4.1의 43~128자 범위 안이며,
 * challenge는 `S256`(SHA-256 → base64url)이다. state는 서버 저장 키로만 쓰는 난수이며
 * URL이나 민감 정보를 담지 않는다(기획서 5.2 "open redirector 방지").
 * 값은 로그에 남기지 않는다.
 */

import { createHash, randomBytes } from "node:crypto";

export interface PkcePair {
  /** 앱이 보관했다가 토큰 교환에 보내는 값 */
  verifier: string;
  /** 인가 요청에 실어 보내는 `S256(verifier)` */
  challenge: string;
}

export function generateVerifier(): string {
  return randomBytes(32).toString("base64url");
}

export function computeChallenge(verifier: string): string {
  return createHash("sha256").update(verifier, "ascii").digest("base64url");
}

export function generatePkce(): PkcePair {
  const verifier = generateVerifier();
  return { verifier, challenge: computeChallenge(verifier) };
}

/** 인가 요청과 콜백을 잇는 값. 앱이 보관한 값과 같을 때만 콜백을 받아들인다 */
export function generateState(): string {
  return randomBytes(32).toString("base64url");
}
