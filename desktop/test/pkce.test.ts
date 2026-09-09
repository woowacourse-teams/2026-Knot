import { describe, expect, it } from "vitest";
import { computeChallenge, generatePkce, generateState, generateVerifier } from "../src/main/auth/pkce";

const BASE64URL = /^[A-Za-z0-9_-]+$/;

describe("PKCE", () => {
  it("verifier는 base64url 43자다(RFC 7636 §4.1의 43~128자 안)", () => {
    const verifier = generateVerifier();
    expect(verifier).toHaveLength(43);
    expect(verifier).toMatch(BASE64URL);
  });

  it("challenge는 RFC 7636 부록 B의 S256 벡터와 같다", () => {
    expect(computeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")).toBe(
      "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
    );
  });

  it("한 벌은 verifier와 그 S256 challenge다", () => {
    const pair = generatePkce();
    expect(pair.challenge).toBe(computeChallenge(pair.verifier));
    expect(pair.challenge).not.toBe(pair.verifier);
  });

  it("verifier는 매번 다르다", () => {
    expect(generateVerifier()).not.toBe(generateVerifier());
  });
});

describe("state", () => {
  it("base64url 43자이며 매번 다르다", () => {
    const state = generateState();
    expect(state).toHaveLength(43);
    expect(state).toMatch(BASE64URL);
    expect(generateState()).not.toBe(state);
  });
});
