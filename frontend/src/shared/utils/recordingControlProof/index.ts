/** 이 탭을 가리키는 ID. 녹음이 끝나도 탭이 살아 있는 동안 유지해요 */
const TAB_ID_KEY = "knot.recording.tabId";

/** 아직 끝나지 않은 녹음 시작 하나에 묶인 요청 ID·제어 증명 */
const START_PROOF_KEY = "knot.recording.startProof";

/** 제어 증명은 32바이트 난수예요 */
const CONTROL_TOKEN_BYTES = 32;

interface StoredStartProof {
  requestId: string;
  controlToken: string;
}

/** 패딩 없는 Base64URL. 32바이트면 43자가 돼요 */
const createControlToken = () => {
  const bytes = crypto.getRandomValues(new Uint8Array(CONTROL_TOKEN_BYTES));

  return btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
};

const readStartProof = (): StoredStartProof | null => {
  const stored = sessionStorage.getItem(START_PROOF_KEY);

  return stored ? JSON.parse(stored) : null;
};

const getTabId = () => {
  const stored = sessionStorage.getItem(TAB_ID_KEY);
  if (stored) return stored;

  const tabId = crypto.randomUUID();
  sessionStorage.setItem(TAB_ID_KEY, tabId);

  return tabId;
};

/**
 * 녹음 시작 요청에 실을 증명(요청 ID·탭 ID·제어 증명)을 돌려줘요. 없으면 만들어 sessionStorage에 보관해요.
 *
 * 서버는 같은 요청 ID로 다시 온 시작을 새 녹음이 아니라 재시도로 보고 기존 세션을 돌려줘요.
 * 그래서 응답을 받지 못한 시작을 다시 보낼 때 같은 값을 쓰도록, 녹음을 끝내거나 버려
 * `clearRecordingStartProof`를 부르기 전까지는 같은 값을 돌려줘요.
 */
export const getRecordingStartProof = () => {
  const tabId = getTabId();
  const stored = readStartProof();
  if (stored) return { ...stored, tabId };

  const startProof: StoredStartProof = {
    requestId: crypto.randomUUID(),
    controlToken: createControlToken(),
  };
  sessionStorage.setItem(START_PROOF_KEY, JSON.stringify(startProof));

  return { ...startProof, tabId };
};

/**
 * 일시정지·재개 요청에 실을 최초 탭 증명(탭 ID·제어 증명)을 돌려줘요. 시작한 적이 없으면 `null`이에요.
 */
export const getRecordingControlProof = () => {
  const stored = readStartProof();
  const tabId = sessionStorage.getItem(TAB_ID_KEY);
  if (!stored || !tabId) return null;

  return { tabId, controlToken: stored.controlToken };
};

/**
 * 끝낸 녹음의 요청 ID·제어 증명을 지워요. 다음 시작은 새 녹음으로 보내야 하기 때문이에요.
 * 탭 ID는 탭이 바뀐 게 아니라 남겨 둬요.
 */
export const clearRecordingStartProof = () => {
  sessionStorage.removeItem(START_PROOF_KEY);
};
