import { meResponse } from "@api/mock/responses/auth";
import {
  documentConfirmationsResponse,
  documentDetailsResponse,
  documentsResponse,
} from "@api/mock/responses/document";
import type {
  DocumentConfirmationItem,
  DocumentConfirmationsResponse,
  DocumentDetailResponse,
  DocumentMyConfirmationResponse,
  DocumentsResponse,
} from "@api/mock/types/document";

// 내가 확인을 누른 문서의 ID와 그 시각.
// 기본 응답(responses)은 테스트의 기대값으로도 쓰여서 바꾸지 않고, 바뀐 것만 여기에 적어요
const confirmedAtByDocumentId = new Map<number, string>();

/** 확인을 누른 기록을 지워 기본 응답 상태로 되돌려요. 테스트와 스토리 사이에 불러요 */
export const resetDocumentMockState = () => {
  confirmedAtByDocumentId.clear();
};

// 서버 정렬(CONFIRMED → PENDING → EXCLUDED)
const STATE_ORDER = { CONFIRMED: 0, PENDING: 1, EXCLUDED: 2 };

const isMe = ({ memberId }: DocumentConfirmationItem) =>
  memberId === meResponse.memberId;

/** 문서 상세와 목록 항목이 함께 가진, 확인과 관련된 값 */
type DocumentConfirmationFields = Pick<
  DocumentDetailResponse,
  "id" | "myConfirmationState" | "confirmationSummary"
>;

/** 기본 응답에서는 내가 미확인이지만, 그 뒤에 확인을 누른 문서인지 */
const isNewlyConfirmed = (document: DocumentConfirmationFields) =>
  document.myConfirmationState === "PENDING" &&
  confirmedAtByDocumentId.has(document.id);

/** 확인을 누른 문서에 덮어쓸 내 상태와 집계. 상세와 목록 항목이 같은 값을 써요 */
const confirmedFields = ({
  confirmationSummary,
}: DocumentConfirmationFields) => ({
  myConfirmationState: "CONFIRMED" as const,
  confirmationSummary: {
    ...confirmationSummary,
    confirmedCount: confirmationSummary.confirmedCount + 1,
    pendingCount: confirmationSummary.pendingCount - 1,
  },
});

interface FindDocumentsParams {
  /** 앞 페이지의 nextCursor. 첫 페이지면 null */
  cursor: string | null;
  size: number;
}

/**
 * 문서 목록의 한 페이지. 확인을 누른 문서는 내 상태와 집계를 바꿔서 돌려줘요.
 * 커서는 앞 페이지의 마지막 문서 ID예요. 그 ID의 문서가 없으면 undefined예요.
 */
export const findDocuments = ({ cursor, size }: FindDocumentsParams) => {
  const { topics, items } = documentsResponse;
  const cursorIndex = items.findIndex(({ id }) => String(id) === cursor);

  if (cursor !== null && cursorIndex === -1) return undefined;

  // 첫 페이지면 cursorIndex가 -1이라 0번째부터 잘라요
  const start = cursorIndex + 1;
  const end = start + size;
  const hasNextPage = end < items.length;

  return {
    topics,
    items: items
      .slice(start, end)
      .map((item) =>
        isNewlyConfirmed(item) ? { ...item, ...confirmedFields(item) } : item,
      ),
    nextCursor: hasNextPage ? String(items[end - 1].id) : null,
  } satisfies DocumentsResponse;
};

/** 문서 상세. 확인을 누른 문서는 내 상태와 집계를 바꿔서 돌려줘요 */
export const findDocumentDetail = (documentId: number) => {
  const document = documentDetailsResponse.find(({ id }) => id === documentId);

  if (document === undefined || !isNewlyConfirmed(document)) return document;

  return {
    ...document,
    ...confirmedFields(document),
  } satisfies DocumentDetailResponse;
};

/** 확인 대상. 확인을 누른 문서는 내 항목을 확인한 상태로 바꾸고 다시 정렬해서 돌려줘요 */
export const findDocumentConfirmations = (documentId: number) => {
  const confirmations = documentConfirmationsResponse.find(
    (response) => response.documentId === documentId,
  );
  const document = findDocumentDetail(documentId);
  const confirmedAt = confirmedAtByDocumentId.get(documentId);

  if (
    confirmations === undefined ||
    document === undefined ||
    confirmedAt === undefined ||
    confirmations.confirmedByMe
  ) {
    return confirmations;
  }

  return {
    ...confirmations,
    ...document.confirmationSummary,
    confirmedByMe: true,
    items: confirmations.items
      .map((item) =>
        isMe(item)
          ? { ...item, confirmedAt, state: "CONFIRMED" as const }
          : item,
      )
      // map이 새 배열을 만들었으므로 여기서 정렬해도 기본 응답의 순서는 바뀌지 않아요
      .sort((a, b) => STATE_ORDER[a.state] - STATE_ORDER[b.state]),
  } satisfies DocumentConfirmationsResponse;
};

/**
 * 내 확인을 기록하고 PUT 응답을 돌려줘요. 없는 문서면 undefined예요.
 * 이미 확인한 문서는 기록을 바꾸지 않고 처음 확인한 시각을 그대로 돌려줘요(서버와 같은 동작).
 */
export const confirmDocument = (documentId: number) => {
  const confirmations = findDocumentConfirmations(documentId);

  if (confirmations === undefined) return undefined;

  const earlierConfirmedAt = confirmations.items.find(isMe)?.confirmedAt;
  const confirmedAt = earlierConfirmedAt ?? new Date().toISOString();

  if (earlierConfirmedAt == null) {
    confirmedAtByDocumentId.set(documentId, confirmedAt);
  }

  // 방금 적은 기록이 반영된 상세에서 상태와 집계를 가져와요
  const document = findDocumentDetail(documentId);

  if (document === undefined) return undefined;

  return {
    documentId,
    confirmedAt,
    documentStatus: document.status,
    archivedAt: document.archivedAt,
    confirmationSummary: document.confirmationSummary,
  } satisfies DocumentMyConfirmationResponse;
};
